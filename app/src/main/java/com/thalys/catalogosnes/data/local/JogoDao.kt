package com.thalys.catalogosnes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface JogoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodos(jogos: List<JogoEntity>)

    @Transaction
    @Query("SELECT * FROM jogos ORDER BY nome ASC")
    fun observarBibliotecaCompleta(): Flow<List<JogoComPosse>>

    @Transaction
    @Query("SELECT * FROM jogos WHERE id = :jogoId")
    suspend fun buscarPorId(jogoId: Long): JogoComPosse?
}
