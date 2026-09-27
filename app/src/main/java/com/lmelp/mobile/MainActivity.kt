package com.lmelp.mobile

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lmelp.mobile.ui.theme.LmelpTheme

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
                var swipeDirection by remember { mutableStateOf(0) }
                val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

                // Ordre circulaire pour la navigation par swipe (inclut Home)
                val swipeRoutes = bottomNavItems.map { it.route }

                // La bottom nav s'affiche partout sauf sur la HomeScreen
                val routesWithoutBottomNav = setOf(Routes.HOME)

                val swipeThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }

                fun navigateBySwipe(direction: Int) {
                    val currentIndex = swipeRoutes.indexOf(currentRoute)
                    if (currentIndex == -1) return
                    swipeDirection = direction
                    val targetIndex = (currentIndex - direction + swipeRoutes.size) % swipeRoutes.size
                    val targetRoute = swipeRoutes[targetIndex]
                    if (targetRoute == Routes.HOME) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    } else {
                        navController.navigate(targetRoute) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                Scaffold(
                    contentWindowInsets = WindowInsets(0),
                    bottomBar = {
                        if (currentRoute != null && currentRoute !in routesWithoutBottomNav) {
                            LmelpBottomBar(
                                currentRoute = currentRoute,
                                showLabels = shouldShowLabel(isLandscape),
                                onItemClick = { route ->
                                    if (route == Routes.HOME) {
                                        navController.navigate(Routes.HOME) {
                                            popUpTo(Routes.HOME) { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate(route) {
                                            popUpTo(Routes.HOME) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    LmelpNavHost(
                        navController = navController,
                        app = app,
                        swipeDirection = swipeDirection,
                        modifier = Modifier
                            .padding(innerPadding)
                            .pointerInput(currentRoute) {
                                if (currentRoute == Routes.HOME) return@pointerInput
                                var totalDragX = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { totalDragX = 0f },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        totalDragX += dragAmount
                                    },
                                    onDragEnd = {
                                        when {
                                            totalDragX < -swipeThresholdPx -> navigateBySwipe(-1)
                                            totalDragX > swipeThresholdPx -> navigateBySwipe(+1)
                                        }
                                        totalDragX = 0f
                                    },
                                    onDragCancel = { totalDragX = 0f }
                                )
                            }
                    )
                }
            }
        }
    }
}
