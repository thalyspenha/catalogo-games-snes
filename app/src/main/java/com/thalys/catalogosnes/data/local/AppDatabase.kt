package com.thalys.catalogosnes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [JogoEntity::class, PosseUsuarioEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jogoDao(): JogoDao
    abstract fun posseUsuarioDao(): PosseUsuarioDao
}
