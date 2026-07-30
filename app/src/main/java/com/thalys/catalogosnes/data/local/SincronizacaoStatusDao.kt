package com.thalys.catalogosnes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.thalys.catalogosnes.data.model.StatusSincronizacao

@Dao
interface SincronizacaoStatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(status: SincronizacaoStatusEntity)

    @Query("SELECT crc FROM sincronizacao_status WHERE status = :status")
    suspend fun buscarCrcsPorStatus(status: StatusSincronizacao = StatusSincronizacao.SUCESSO): List<String>

    @Query("SELECT * FROM sincronizacao_status WHERE status = 'FALHA'")
    suspend fun buscarFalhas(): List<SincronizacaoStatusEntity>

    @Query("SELECT COUNT(*) FROM sincronizacao_status")
    suspend fun contarLinhas(): Int
}
