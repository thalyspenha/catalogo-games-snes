package com.thalys.catalogosnes.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thalys.catalogosnes.ui.biblioteca.TelaBiblioteca
import com.thalys.catalogosnes.ui.detalhe.TelaDetalheJogo

private const val ROTA_BIBLIOTECA = "biblioteca"
private const val ARGUMENTO_JOGO_ID = "jogoId"
private const val ROTA_DETALHE = "detalhe/{$ARGUMENTO_JOGO_ID}"

/** Grafo de navegação do app: biblioteca (grid principal) -> detalhe/edição de posse de um jogo. */
@Composable
fun CatalogoNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = ROTA_BIBLIOTECA) {
        composable(ROTA_BIBLIOTECA) {
            TelaBiblioteca(
                aoClicarJogo = { jogoId -> navController.navigate("detalhe/$jogoId") },
            )
        }
        composable(
            route = ROTA_DETALHE,
            arguments = listOf(navArgument(ARGUMENTO_JOGO_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val jogoId = backStackEntry.arguments?.getLong(ARGUMENTO_JOGO_ID) ?: return@composable
            TelaDetalheJogo(
                jogoId = jogoId,
                aoVoltar = { navController.popBackStack() },
            )
        }
    }
}
