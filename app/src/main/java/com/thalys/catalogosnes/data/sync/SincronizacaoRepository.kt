package com.thalys.catalogosnes.data.sync

import android.content.Context
import com.thalys.catalogosnes.data.local.AppDatabase
import com.thalys.catalogosnes.data.local.JogoDao
import com.thalys.catalogosnes.data.local.PosseUsuarioDao
import com.thalys.catalogosnes.data.local.SincronizacaoStatusDao
import com.thalys.catalogosnes.data.local.SincronizacaoStatusEntity
import com.thalys.catalogosnes.data.model.StatusSincronizacao
import com.thalys.catalogosnes.data.remote.screenscraper.NetworkModule
import com.thalys.catalogosnes.data.remote.screenscraper.ScreenScraperApi
import com.thalys.catalogosnes.data.remote.screenscraper.ScreenScraperCredenciais
import com.thalys.catalogosnes.data.remote.screenscraper.ScreenScraperMapper
import com.thalys.catalogosnes.data.remote.screenscraper.dto.JeuDto
import com.thalys.catalogosnes.data.remote.screenscraper.dto.JogoInfoRespostaDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.coroutineContext

private const val THROTTLE_MS = 1200L
private const val MAX_TENTATIVAS_REDE = 3
private val BACKOFF_MS = listOf(2000L, 4000L)

class SincronizacaoRepository(
    private val context: Context,
    private val jogoDao: JogoDao,
    private val posseUsuarioDao: PosseUsuarioDao,
    private val sincronizacaoStatusDao: SincronizacaoStatusDao,
    private val screenScraperApi: ScreenScraperApi,
) {

    private val _estado = MutableStateFlow<SincronizacaoEstado>(SincronizacaoEstado.Ocioso)
    val estado: StateFlow<SincronizacaoEstado> = _estado.asStateFlow()

    suspend fun sincronizar() {
        val catalogoMestre = CatalogoMestreLoader.carregar(context)
        val totalLinhasStatus = sincronizacaoStatusDao.contarLinhas()
        if (totalLinhasStatus == 0) {
            jogoDao.limparTudo()
            posseUsuarioDao.limparTudo()
        }

        val crcsComSucesso = sincronizacaoStatusDao.buscarCrcsPorStatus(StatusSincronizacao.SUCESSO).toSet()
        val restante = calcularRestante(catalogoMestre, crcsComSucesso)
        val total = catalogoMestre.size
        var sucesso = crcsComSucesso.size

        for ((indice, item) in restante.withIndex()) {
            coroutineContext.ensureActive()
            delay(THROTTLE_MS)

            _estado.value = SincronizacaoEstado.EmAndamento(
                atual = sucesso + indice + 1,
                total = total,
                nomeJogoAtual = item.nomeExibicao,
            )

            when (val resultado = buscarComRetry(item)) {
                is ResultadoBusca.Sucesso -> {
                    val jogoEntity = ScreenScraperMapper.paraJogoEntity(resultado.jeu)
                    if (jogoEntity == null) {
                        sincronizacaoStatusDao.salvar(
                            SincronizacaoStatusEntity(item.crc, StatusSincronizacao.FALHA, null, "Resposta sem id/nome válidos")
                        )
                    } else {
                        jogoDao.inserirTodos(listOf(jogoEntity))
                        sincronizacaoStatusDao.salvar(
                            SincronizacaoStatusEntity(item.crc, StatusSincronizacao.SUCESSO, jogoEntity.id, null)
                        )
                        sucesso++
                    }
                }
                is ResultadoBusca.NaoEncontrado -> {
                    sincronizacaoStatusDao.salvar(
                        SincronizacaoStatusEntity(item.crc, StatusSincronizacao.FALHA, null, "Jogo não encontrado no ScreenScraper")
                    )
                }
                is ResultadoBusca.CotaEsgotada -> {
                    _estado.value = SincronizacaoEstado.CotaEsgotada(sucesso = sucesso, restantes = total - sucesso)
                    return
                }
                is ResultadoBusca.ErroDeRede -> {
                    sincronizacaoStatusDao.salvar(
                        SincronizacaoStatusEntity(item.crc, StatusSincronizacao.FALHA, null, resultado.mensagem)
                    )
                }
            }
        }

        val statusFalhas = sincronizacaoStatusDao.buscarFalhas()
        val nomesPorCrc = catalogoMestre.associateBy { it.crc }
        val falhasDetalhadas = statusFalhas.map { falha ->
            FalhaSincronizacao(
                nomeExibicao = nomesPorCrc[falha.crc]?.nomeExibicao ?: falha.crc,
                motivo = falha.mensagemErro ?: "Erro desconhecido",
            )
        }
        _estado.value = SincronizacaoEstado.Concluido(sucesso = sucesso, falhas = falhasDetalhadas)
    }

    private suspend fun buscarComRetry(item: CatalogoMestreItemDto): ResultadoBusca {
        var ultimoErro: String? = null
        repeat(MAX_TENTATIVAS_REDE) { tentativa ->
            try {
                val resposta = screenScraperApi.buscarInfoJogo(
                    devId = ScreenScraperCredenciais.devId,
                    devPassword = ScreenScraperCredenciais.devPassword,
                    softName = ScreenScraperCredenciais.softName,
                    ssid = ScreenScraperCredenciais.usuarioId.ifBlank { null },
                    sspassword = ScreenScraperCredenciais.usuarioSenha.ifBlank { null },
                    systemeId = ScreenScraperApi.SISTEMA_SNES,
                    romNome = item.romNome,
                    romTamanho = item.romTamanho,
                    crc = item.crc,
                )
                if (cotaEsgotada(resposta)) return ResultadoBusca.CotaEsgotada
                val jeu = resposta.response?.jeu ?: return ResultadoBusca.NaoEncontrado
                return ResultadoBusca.Sucesso(jeu)
            } catch (e: Exception) {
                ultimoErro = e.message ?: e.javaClass.simpleName
                if (tentativa < MAX_TENTATIVAS_REDE - 1) delay(BACKOFF_MS[tentativa])
            }
        }
        return ResultadoBusca.ErroDeRede(ultimoErro ?: "Erro de rede desconhecido")
    }

    companion object {
        @Volatile
        private var instancia: SincronizacaoRepository? = null

        fun obterInstancia(context: Context): SincronizacaoRepository {
            return instancia ?: synchronized(this) {
                instancia ?: run {
                    val banco = AppDatabase.obterInstancia(context.applicationContext)
                    SincronizacaoRepository(
                        context = context.applicationContext,
                        jogoDao = banco.jogoDao(),
                        posseUsuarioDao = banco.posseUsuarioDao(),
                        sincronizacaoStatusDao = banco.sincronizacaoStatusDao(),
                        screenScraperApi = NetworkModule.screenScraperApi,
                    ).also { instancia = it }
                }
            }
        }
    }
}

/**
 * Heurística best-effort pra detectar cota diária esgotada pelo texto de erro do header.
 * O texto exato ainda não foi observado com uma cota real estourada (ver spec) — ajustar
 * aqui se, na prática, a API sinalizar isso de outro jeito.
 */
internal fun cotaEsgotada(resposta: JogoInfoRespostaDto): Boolean {
    val erro = resposta.header?.error?.lowercase() ?: return false
    return "quota" in erro || "limite" in erro
}

private sealed class ResultadoBusca {
    data class Sucesso(val jeu: JeuDto) : ResultadoBusca()
    data object NaoEncontrado : ResultadoBusca()
    data object CotaEsgotada : ResultadoBusca()
    data class ErroDeRede(val mensagem: String) : ResultadoBusca()
}
