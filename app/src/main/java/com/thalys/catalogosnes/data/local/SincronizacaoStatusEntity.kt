package com.thalys.catalogosnes.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.thalys.catalogosnes.data.model.StatusSincronizacao

/**
 * Registra a tentativa de sincronizar um jogo do catálogo mestre com o ScreenScraper. Uma
 * linha só existe depois de uma tentativa real — "pendente" é a ausência de linha para
 * aquele crc. É o checkpoint que permite retomar o sync e re-tentar só as falhas.
 */
@Entity(tableName = "sincronizacao_status")
data class SincronizacaoStatusEntity(
    @PrimaryKey val crc: String,
    val status: StatusSincronizacao,
    val jogoId: Long?,
    val mensagemErro: String?,
)
