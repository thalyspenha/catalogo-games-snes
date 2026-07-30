package ferramentas

import kotlinx.serialization.Serializable

/** Um jogo único de SNES no catálogo mestre — o suficiente pra identificar no ScreenScraper. */
@Serializable
data class ItemCatalogoMestre(
    val romNome: String,
    val crc: String,
    val romTamanho: Long,
    val nomeExibicao: String,
)
