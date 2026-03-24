package com.snapledger.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
                onImportClick = { navController.navigate(Routes.IMPORT) }
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

        composable(Routes.IMPORT) {
            ImportScreen(
                viewModel = expenseViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
