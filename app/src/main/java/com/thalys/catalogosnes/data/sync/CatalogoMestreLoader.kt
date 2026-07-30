package com.thalys.catalogosnes.data.sync

import android.content.Context
import kotlinx.serialization.json.Json

/** Lê `assets/snes_catalogo_mestre.json`, mesmo padrão do `SeedLoader` para o seed antigo. */
object CatalogoMestreLoader {

    private const val ARQUIVO_CATALOGO_MESTRE = "snes_catalogo_mestre.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun carregar(context: Context): List<CatalogoMestreItemDto> {
        val conteudo = context.assets.open(ARQUIVO_CATALOGO_MESTRE).bufferedReader().use { it.readText() }
        return parsear(conteudo)
    }

    fun parsear(conteudoJson: String): List<CatalogoMestreItemDto> =
        json.decodeFromString(conteudoJson)
}
