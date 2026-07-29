package com.thalys.catalogosnes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PosseUsuarioDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(posse: PosseUsuarioEntity)

    @Query("DELETE FROM posse_usuario WHERE jogoId = :jogoId")
    suspend fun remover(jogoId: Long)
}
