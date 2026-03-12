package com.lmelp.mobile

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lmelp.mobile.ui.about.AboutScreen
import com.lmelp.mobile.ui.auteurs.AuteurDetailScreen
import com.lmelp.mobile.ui.critiques.CritiquesScreen
import com.lmelp.mobile.ui.emissions.EmissionDetailScreen
import com.lmelp.mobile.ui.emissions.EmissionsScreen
import com.lmelp.mobile.ui.emissions.LivreDetailScreen
import com.lmelp.mobile.ui.home.HomeScreen
import com.lmelp.mobile.ui.palmares.PalmaresScreen
import com.lmelp.mobile.ui.recommendations.RecommendationsScreen
import com.lmelp.mobile.ui.search.SearchScreen

object Routes {
    const val HOME = "home"
    const val ABOUT = "about"
    const val EMISSIONS = "emissions"
    const val EMISSION_DETAIL = "emission/{emissionId}"
    const val LIVRE_DETAIL = "livre/{livreId}"
    const val AUTEUR_DETAIL = "auteur/{auteurId}"
    const val PALMARES = "palmares"
    const val CRITIQUES = "critiques"
    const val SEARCH = "search"
    const val RECOMMENDATIONS = "recommendations"

    fun emissionDetail(emissionId: String) = "emission/$emissionId"
    fun livreDetail(livreId: String) = "livre/$livreId"
    fun auteurDetail(auteurId: String) = "auteur/$auteurId"
}

@Composable
fun LmelpNavHost(
    navController: NavHostController,
    app: LmelpApp,
    swipeDirection: Int = 0,
    modifier: Modifier = Modifier
) {
    val slideEnter = { slideInHorizontally { if (swipeDirection <= 0) it else -it } }
    val slideExit = { slideOutHorizontally { if (swipeDirection <= 0) -it else it } }

    NavHost(navController = navController, startDestination = Routes.HOME, modifier = modifier) {

        composable(
            Routes.HOME,
            enterTransition = { slideEnter() },
            exitTransition = { slideExit() }
        ) {
            HomeScreen(
                repository = app.metadataRepository,
                onNavigate = { route -> navController.navigate(route) },
                onSettingsClick = { navController.navigate(Routes.ABOUT) }
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen()
        }

        composable(
            Routes.EMISSIONS,
            enterTransition = { slideEnter() },
            exitTransition = { slideExit() }
        ) {
            EmissionsScreen(
                repository = app.emissionsRepository,
                onEmissionClick = { navController.navigate(Routes.emissionDetail(it)) }
            )
        }

        composable(
            route = Routes.EMISSION_DETAIL,
            arguments = listOf(navArgument("emissionId") { type = NavType.StringType })
        ) { backStack ->
            val emissionId = backStack.arguments?.getString("emissionId") ?: return@composable
            EmissionDetailScreen(
                emissionId = emissionId,
                repository = app.emissionsRepository,
                onLivreClick = { navController.navigate(Routes.livreDetail(it)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.LIVRE_DETAIL,
            arguments = listOf(navArgument("livreId") { type = NavType.StringType })
        ) { backStack ->
            val livreId = backStack.arguments?.getString("livreId") ?: return@composable
            LivreDetailScreen(
                livreId = livreId,
                repository = app.livresRepository,
                onBack = { navController.popBackStack() },
                onEmissionClick = { navController.navigate(Routes.emissionDetail(it)) },
                onAuteurClick = { navController.navigate(Routes.auteurDetail(it)) }
            )
        }

        composable(
            route = Routes.AUTEUR_DETAIL,
            arguments = listOf(navArgument("auteurId") { type = NavType.StringType })
        ) { backStack ->
            val auteurId = backStack.arguments?.getString("auteurId") ?: return@composable
            AuteurDetailScreen(
                auteurId = auteurId,
                repository = app.auteursRepository,
                onBack = { navController.popBackStack() },
                onLivreClick = { navController.navigate(Routes.livreDetail(it)) }
            )
        }

        composable(
            Routes.PALMARES,
            enterTransition = { slideEnter() },
            exitTransition = { slideExit() }
        ) {
            PalmaresScreen(
                repository = app.palmaresRepository,
                onLivreClick = { navController.navigate(Routes.livreDetail(it)) }
            )
        }

        composable(Routes.CRITIQUES) {
            CritiquesScreen(repository = app.critiquesRepository)
        }

        composable(
            Routes.SEARCH,
            enterTransition = { slideEnter() },
            exitTransition = { slideExit() }
        ) {
            SearchScreen(
                repository = app.searchRepository,
                onResultClick = { type, id ->
                    when (type) {
                        "livre" -> navController.navigate(Routes.livreDetail(id))
                        "emission" -> navController.navigate(Routes.emissionDetail(id))
                        "auteur" -> navController.navigate(Routes.auteurDetail(id))
                        else -> {}
                    }
                }
            )
        }

        composable(
            Routes.RECOMMENDATIONS,
            enterTransition = { slideEnter() },
            exitTransition = { slideExit() }
        ) {
            RecommendationsScreen(
                repository = app.recommendationsRepository,
                onLivreClick = { navController.navigate(Routes.livreDetail(it)) }
            )
        }
    }
}
