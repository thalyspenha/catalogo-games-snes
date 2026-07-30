package com.thalys.catalogosnes.data.local.seed

import android.content.Context
import com.thalys.catalogosnes.data.local.JogoEntity
import kotlinx.serialization.json.Json

/**
 * Lê `assets/jogos_seed.json` (catálogo fixo de ~25 jogos reais de SNES, usado enquanto a
 * integração com o ScreenScraper não tem credenciais) e converte para [JogoEntity].
 *
 * Quando o ScreenScraper entrar em uso, essa carga é substituída pelo fluxo que passa por
 * [com.thalys.catalogosnes.data.remote.screenscraper.ScreenScraperMapper] — Room e o
 * restante do app não mudam.
 */
object SeedLoader {

    private const val ARQUIVO_SEED = "jogos_seed.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun carregarJogosSeed(context: Context): List<JogoEntity> {
        val conteudo = context.assets.open(ARQUIVO_SEED).bufferedReader().use { it.readText() }
        val dtos = json.decodeFromString<List<JogoSeedDto>>(conteudo)
        return dtos.map { it.paraJogoEntity() }
    }

    private fun JogoSeedDto.paraJogoEntity() = JogoEntity(
        id = id,
        nome = nome,
        descricao = descricao,
        anoLancamento = anoLancamento,
        genero = genero,
        desenvolvedora = desenvolvedora,
        publicadora = publicadora,
        urlCapa = urlCapa,
        regiao = regiao,
    )
}
