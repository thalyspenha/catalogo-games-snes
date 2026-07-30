package com.thalys.catalogosnes.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thalys.catalogosnes.ui.biblioteca.TelaBiblioteca
import com.thalys.catalogosnes.ui.biblioteca.TelaCategoriaCompleta
import com.thalys.catalogosnes.ui.detalhe.TelaDetalheJogo
import com.thalys.catalogosnes.ui.sincronizacao.TelaSincronizacao

private const val ROTA_BIBLIOTECA = "biblioteca"
private const val ARGUMENTO_JOGO_ID = "jogoId"
private const val ROTA_DETALHE = "detalhe/{$ARGUMENTO_JOGO_ID}"
private const val ROTA_SINCRONIZACAO = "sincronizacao"
private const val ARGUMENTO_TITULO_CATEGORIA = "titulo"
private const val ROTA_CATEGORIA = "categoria/{$ARGUMENTO_TITULO_CATEGORIA}"

/**
 * Grafo de navegação do app: biblioteca (carrosséis por categoria) -> detalhe/edição de
 * posse de um jogo, ou -> grid completo de uma categoria (card "Ver tudo").
 */
@Composable
fun CatalogoNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = ROTA_BIBLIOTECA) {
        composable(ROTA_BIBLIOTECA) {
            TelaBiblioteca(
                aoClicarJogo = { jogoId -> navController.navigate("detalhe/$jogoId") },
                aoClicarSincronizar = { navController.navigate(ROTA_SINCRONIZACAO) },
                aoClicarVerTudo = { titulo -> navController.navigate("categoria/${Uri.encode(titulo)}") },
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
        composable(ROTA_SINCRONIZACAO) {
            TelaSincronizacao(aoVoltar = { navController.popBackStack() })
        }
        composable(
            route = ROTA_CATEGORIA,
            arguments = listOf(navArgument(ARGUMENTO_TITULO_CATEGORIA) { type = NavType.StringType }),
        ) { backStackEntry ->
            val tituloCodificado = backStackEntry.arguments?.getString(ARGUMENTO_TITULO_CATEGORIA)
                ?: return@composable
            TelaCategoriaCompleta(
                titulo = Uri.decode(tituloCodificado),
                aoClicarJogo = { jogoId -> navController.navigate("detalhe/$jogoId") },
                aoVoltar = { navController.popBackStack() },
            )
        }
    }
}
