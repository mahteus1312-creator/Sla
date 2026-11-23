package com.hibernador.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hibernador.app.shizuku.ShizukuManager
import com.hibernador.app.ui.HibernationScreen
import com.hibernador.app.ui.HomeScreen
import com.hibernador.app.ui.RulesScreen
import com.hibernador.app.ui.ShizukuSetupScreen
import com.hibernador.app.ui.theme.HibernadorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            HibernadorTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNav()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ShizukuManager.refresh()
    }
}

private object Routes {
    const val HOME = "home"
    const val HIBERNATE = "hibernate"
    const val SHIZUKU = "shizuku"
    const val RULES = "rules"
}

@androidx.compose.runtime.Composable
private fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onHibernate = { nav.goToHibernate() },
                onOpenRules = { nav.navigate(Routes.RULES) { launchSingleTop = true } },
                onOpenShizuku = { nav.navigate(Routes.SHIZUKU) { launchSingleTop = true } },
            )
        }
        composable(Routes.HIBERNATE) {
            HibernationScreen(onExit = { nav.popBackStack(Routes.HOME, inclusive = false) })
        }
        composable(Routes.SHIZUKU) {
            ShizukuSetupScreen(
                onBack = { nav.popBackStack() },
                onReady = {
                    nav.navigate(Routes.HIBERNATE) {
                        popUpTo(Routes.HOME)
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.RULES) { RulesScreen(onBack = { nav.popBackStack() }) }
    }
}

private fun NavHostController.goToHibernate() {
    ShizukuManager.refresh()
    val route = if (ShizukuManager.status.value == ShizukuManager.Status.READY) Routes.HIBERNATE else Routes.SHIZUKU
    navigate(route) { launchSingleTop = true }
}
