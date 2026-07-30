package com.thalys.catalogosnes.data.repository

import android.content.Context
import com.thalys.catalogosnes.data.local.AppDatabase
import com.thalys.catalogosnes.data.local.JogoComPosse
import com.thalys.catalogosnes.data.local.JogoDao
import com.thalys.catalogosnes.data.local.PosseUsuarioDao
import com.thalys.catalogosnes.data.local.PosseUsuarioEntity
import kotlinx.coroutines.flow.Flow

/**
 * Ponto único de acesso aos dados de jogos + posse do usuário, para consumo futuro por
 * ViewModel(s). Fica de propósito desacoplado do ScreenScraper: hoje os dados vêm do seed
 * local (Room populado a partir de `assets/jogos_seed.json`), e quando a integração real
 * entrar em uso, só a origem do [JogoDao] muda — a API deste repositório não precisa mudar.
 */
class JogoRepository(
    private val jogoDao: JogoDao,
    private val posseUsuarioDao: PosseUsuarioDao,
) {

    /** Biblioteca completa (jogo + posse, quando existir), ordenada por nome. */
    fun observarBiblioteca(): Flow<List<JogoComPosse>> = jogoDao.observarBibliotecaCompleta()

    /** Um jogo específico com sua posse, ou null se o id não existir no catálogo. */
    suspend fun buscarJogo(jogoId: Long): JogoComPosse? = jogoDao.buscarPorId(jogoId)

    /** Cria ou substitui a posse de um jogo (status, completude CIB, foto, nota). */
    suspend fun salvarPosse(posse: PosseUsuarioEntity) = posseUsuarioDao.salvar(posse)

    /** Remove a posse registrada de um jogo (volta a ficar sem status definido). */
    suspend fun removerPosse(jogoId: Long) = posseUsuarioDao.remover(jogoId)

    companion object {
        @Volatile
        private var instancia: JogoRepository? = null

        /** Singleton simples (sem framework de DI), construído a partir do [AppDatabase]. */
        fun obterInstancia(context: Context): JogoRepository {
            return instancia ?: synchronized(this) {
                instancia ?: run {
                    val banco = AppDatabase.obterInstancia(context)
                    JogoRepository(banco.jogoDao(), banco.posseUsuarioDao()).also { instancia = it }
                }
            }
        }
    }
}
