package com.lmelp.mobile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lmelp.mobile.ui.critiques.CritiquesScreen
import com.lmelp.mobile.ui.emissions.EmissionDetailScreen
import com.lmelp.mobile.ui.emissions.EmissionsScreen
import com.lmelp.mobile.ui.emissions.LivreDetailScreen
import com.lmelp.mobile.ui.palmares.PalmaresScreen
import com.lmelp.mobile.ui.recommendations.RecommendationsScreen
import com.lmelp.mobile.ui.search.SearchScreen

object Routes {
    const val HOME = "home"
    const val EMISSIONS = "emissions"
    const val EMISSION_DETAIL = "emission/{emissionId}"
    const val LIVRE_DETAIL = "livre/{livreId}"
    const val PALMARES = "palmares"
    const val CRITIQUES = "critiques"
    const val SEARCH = "search"
    const val RECOMMENDATIONS = "recommendations"

    fun emissionDetail(emissionId: String) = "emission/$emissionId"
    fun livreDetail(livreId: String) = "livre/$livreId"
}

@Composable
fun LmelpNavHost(
    navController: NavHostController,
    app: LmelpApp,
    modifier: Modifier = Modifier
) {
    NavHost(navController = navController, startDestination = Routes.EMISSIONS, modifier = modifier) {

        composable(Routes.EMISSIONS) {
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
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PALMARES) {
            PalmaresScreen(
                repository = app.palmaresRepository,
                onLivreClick = { navController.navigate(Routes.livreDetail(it)) }
            )
        }

        composable(Routes.CRITIQUES) {
            CritiquesScreen(repository = app.critiquesRepository)
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                repository = app.searchRepository,
                onResultClick = { type, id ->
                    when (type) {
                        "livre" -> navController.navigate(Routes.livreDetail(id))
                        "emission" -> navController.navigate(Routes.emissionDetail(id))
                        else -> {}
                    }
                }
            )
        }

        composable(Routes.RECOMMENDATIONS) {
            RecommendationsScreen(
                repository = app.recommendationsRepository,
                onLivreClick = { navController.navigate(Routes.livreDetail(it)) }
            )
        }
    }
}
