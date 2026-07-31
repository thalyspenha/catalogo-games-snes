package com.thalys.catalogosnes.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File

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
    val caminhoCapaLocal: String? = null,
)

/**
 * Modelo pra passar direto ao `AsyncImage` do Coil: arquivo local se a capa já foi baixada
 * (funciona offline), senão a URL remota como melhor esforço (exige rede) — cobre jogos
 * sincronizados antes de [caminhoCapaLocal] existir, sem forçar re-sincronização completa.
 */
fun JogoEntity.modeloCapa(): Any? = caminhoCapaLocal?.let { File(it) } ?: urlCapa
