package com.thalys.catalogosnes.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Metadados do catálogo (biblioteca completa de jogos de SNES), vindos do ScreenScraper.
 */
@Entity(tableName = "jogos")
data class JogoEntity(
    @PrimaryKey val id: Long,
    val nome: String,
    val descricao: String?,
    val anoLancamento: Int?,
    val genero: String?,
    val desenvolvedora: String?,
    val publicadora: String?,
    val urlCapa: String?,
    val regiao: String?,
)
