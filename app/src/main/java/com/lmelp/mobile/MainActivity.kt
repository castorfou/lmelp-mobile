package com.lmelp.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lmelp.mobile.ui.theme.LmelpTheme

data class BottomNavItem(
    val label: String,
    val route: String,
    val icon: ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as LmelpApp

        setContent {
            LmelpTheme {
                val navController = rememberNavController()
                val navBackStack by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStack?.destination?.route

                val bottomNavItems = listOf(
                    BottomNavItem("Accueil", Routes.HOME, Icons.Default.Home),
                    BottomNavItem("Émissions", Routes.EMISSIONS, Icons.AutoMirrored.Filled.List),
                    BottomNavItem("Palmarès", Routes.PALMARES, Icons.Default.Star),
                    BottomNavItem("Conseils", Routes.RECOMMENDATIONS, Icons.Default.Person),
                    BottomNavItem("Recherche", Routes.SEARCH, Icons.Default.Search),
                )

                // La bottom nav s'affiche partout sauf sur la HomeScreen
                val routesWithoutBottomNav = setOf(Routes.HOME)

                Scaffold(
                    contentWindowInsets = WindowInsets(0),
                    bottomBar = {
                        if (currentRoute != null && currentRoute !in routesWithoutBottomNav) {
                            NavigationBar {
                                bottomNavItems.forEach { item ->
                                    NavigationBarItem(
                                        selected = currentRoute == item.route,
                                        onClick = {
                                            if (item.route == Routes.HOME) {
                                                navController.navigate(Routes.HOME) {
                                                    popUpTo(Routes.HOME) { inclusive = true }
                                                }
                                            } else {
                                                navController.navigate(item.route) {
                                                    popUpTo(Routes.HOME) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        icon = { Icon(item.icon, contentDescription = item.label) },
                                        label = { Text(item.label) }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    LmelpNavHost(
                        navController = navController,
                        app = app,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
