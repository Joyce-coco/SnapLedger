package com.snapledger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.snapledger.app.ui.navigation.AppNavGraph
import com.snapledger.app.ui.theme.SnapLedgerTheme
import com.snapledger.app.ui.update.UpdateDialog
import com.snapledger.app.update.UpdateChecker
import com.snapledger.app.update.UpdateInfo
import com.snapledger.app.viewmodel.ExpenseViewModel
import com.snapledger.app.viewmodel.StatsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnapLedgerTheme {
                var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    scope.launch {
                        updateInfo = UpdateChecker.checkForUpdate()
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val expenseViewModel: ExpenseViewModel = hiltViewModel()
                    val statsViewModel: StatsViewModel = hiltViewModel()
                    AppNavGraph(navController, expenseViewModel, statsViewModel)

                    updateInfo?.let { info ->
                        UpdateDialog(
                            updateInfo = info,
                            onDismiss = { updateInfo = null }
                        )
                    }
                }
            }
        }
    }
}
