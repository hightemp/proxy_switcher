package com.hightemp.proxy_switcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.hightemp.proxy_switcher.ui.theme.Proxy_switcherTheme

import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.hightemp.proxy_switcher.ui.screens.AddEditProxyScreen
import com.hightemp.proxy_switcher.ui.screens.HomeScreen
import com.hightemp.proxy_switcher.ui.screens.LogsScreen
import com.hightemp.proxy_switcher.ui.screens.ProxyListScreen
import com.hightemp.proxy_switcher.ui.screens.SystemProxyScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Proxy_switcherTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(navController = navController)
                    }
                    composable("proxy_list") {
                        ProxyListScreen(navController = navController)
                    }
                    composable("logs") {
                        LogsScreen(navController = navController)
                    }
                    composable("system_proxy") {
                        SystemProxyScreen(navController = navController)
                    }
                    composable("add_proxy") {
                        AddEditProxyScreen(navController = navController)
                    }
                    composable(
                        "edit_proxy/{proxyId}",
                        arguments = listOf(navArgument("proxyId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val proxyId = backStackEntry.arguments?.getLong("proxyId")
                        AddEditProxyScreen(navController = navController, proxyId = proxyId)
                    }
                }
            }
        }
    }
}