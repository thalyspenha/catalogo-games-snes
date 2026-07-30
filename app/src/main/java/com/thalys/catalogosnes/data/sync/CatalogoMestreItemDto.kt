package com.thalys.catalogosnes.data.sync

import kotlinx.serialization.Serializable

/**
 * Espelha uma entrada de `assets/snes_catalogo_mestre.json` (gerado pelo módulo
 * :ferramentas a partir do DAT No-Intro) — um jogo único de SNES, identificado por
 * nome/crc/tamanho de ROM pra consultar o ScreenScraper via jeuInfos.php.
 */
@Serializable
data class CatalogoMestreItemDto(
    val romNome: String,
    val crc: String,
    val romTamanho: Long,
    val nomeExibicao: String,
)
