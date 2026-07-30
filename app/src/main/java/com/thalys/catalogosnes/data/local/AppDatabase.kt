package com.thalys.catalogosnes.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.thalys.catalogosnes.data.local.seed.SeedLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [JogoEntity::class, PosseUsuarioEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jogoDao(): JogoDao
    abstract fun posseUsuarioDao(): PosseUsuarioDao

    companion object {
        private const val NOME_BANCO = "catalogo_snes.db"

        @Volatile
        private var instancia: AppDatabase? = null

        /**
         * Singleton simples (sem framework de DI, mesmo padrão do NetworkModule).
         *
         * O seed local é populado via [RoomDatabase.Callback.onCreate], que o Room só
         * dispara uma única vez, no momento em que o arquivo do banco é criado — por isso
         * não é preciso checar "banco vazio" manualmente nem há risco de duplicar o seed
         * em execuções subsequentes do app.
         */
        fun obterInstancia(context: Context): AppDatabase {
            return instancia ?: synchronized(this) {
                instancia ?: construir(context.applicationContext).also { instancia = it }
            }
        }

        private fun construir(context: Context): AppDatabase {
            lateinit var db: AppDatabase
            val callbackDeSeed = object : Callback() {
                override fun onCreate(dbSqlite: SupportSQLiteDatabase) {
                    super.onCreate(dbSqlite)
                    CoroutineScope(Dispatchers.IO).launch {
                        val jogosSeed = SeedLoader.carregarJogosSeed(context)
                        db.jogoDao().inserirTodos(jogosSeed)
                    }
                }
            }
            db = Room.databaseBuilder(context, AppDatabase::class.java, NOME_BANCO)
                .addCallback(callbackDeSeed)
                .build()
            return db
        }
    }
}
