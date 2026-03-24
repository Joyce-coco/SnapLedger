package com.snapledger.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.snapledger.app.ui.add.AddExpenseScreen
import com.snapledger.app.ui.camera.CameraScreen
import com.snapledger.app.ui.category.CategoryScreen
import com.snapledger.app.ui.home.HomeScreen
import com.snapledger.app.ui.`import`.ImportScreen
import com.snapledger.app.ui.stats.StatsScreen
import com.snapledger.app.viewmodel.ExpenseViewModel
import com.snapledger.app.viewmodel.StatsViewModel

object Routes {
    const val HOME = "home"
    const val ADD = "add"
    const val CAMERA = "camera"
    const val STATS = "stats"
    const val CATEGORIES = "categories"
    const val IMPORT = "import"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    expenseViewModel: ExpenseViewModel,
    statsViewModel: StatsViewModel
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = expenseViewModel,
                onAddClick = { navController.navigate(Routes.ADD) },
                onCameraClick = { navController.navigate(Routes.CAMERA) },
                onStatsClick = { navController.navigate(Routes.STATS) },
                onCategoryClick = { navController.navigate(Routes.CATEGORIES) },
                onImportClick = { navController.navigate(Routes.IMPORT) },
                onEditClick = { expense ->
                    navController.navigate(
                        "edit/${expense.id}/${expense.amount}/${expense.categoryName}/${expense.type}/${expense.timestamp}?note=${java.net.URLEncoder.encode(expense.note, "UTF-8")}"
                    )
                }
            )
        }

        composable(Routes.ADD) {
            AddExpenseScreen(
                viewModel = expenseViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "add?amount={amount}&category={category}&isIncome={isIncome}",
        ) { backStackEntry ->
            val amount = backStackEntry.arguments?.getString("amount")
            val category = backStackEntry.arguments?.getString("category")
            val isIncome = backStackEntry.arguments?.getString("isIncome") == "true"
            AddExpenseScreen(
                viewModel = expenseViewModel,
                onBack = { navController.popBackStack() },
                prefilledAmount = amount,
                prefilledCategory = category,
                prefilledIsIncome = isIncome
            )
        }

        composable(Routes.CAMERA) {
            CameraScreen(
                viewModel = expenseViewModel,
                onNavigateToAdd = { amount, category, isIncome ->
                    navController.navigate("add?amount=$amount&category=$category&isIncome=$isIncome") {
                        popUpTo(Routes.CAMERA) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.STATS) {
            StatsScreen(
                viewModel = statsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CATEGORIES) {
            CategoryScreen(
                viewModel = expenseViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "edit/{id}/{amount}/{category}/{type}/{timestamp}?note={note}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("amount") { type = NavType.StringType },
                navArgument("category") { type = NavType.StringType },
                navArgument("type") { type = NavType.IntType },
                navArgument("timestamp") { type = NavType.LongType },
                navArgument("note") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: 0
            val amount = backStackEntry.arguments?.getString("amount") ?: ""
            val categoryName = backStackEntry.arguments?.getString("category") ?: ""
            val type = backStackEntry.arguments?.getInt("type") ?: 0
            val timestamp = backStackEntry.arguments?.getLong("timestamp") ?: 0
            val note = try {
                java.net.URLDecoder.decode(backStackEntry.arguments?.getString("note") ?: "", "UTF-8")
            } catch (_: Exception) { "" }

            AddExpenseScreen(
                viewModel = expenseViewModel,
                onBack = { navController.popBackStack() },
                prefilledAmount = amount,
                prefilledCategory = categoryName,
                prefilledIsIncome = type == 1,
                editExpenseId = id,
                editNote = note,
                editTimestamp = timestamp
            )
        }

        composable(Routes.IMPORT) {
            ImportScreen(
                viewModel = expenseViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
