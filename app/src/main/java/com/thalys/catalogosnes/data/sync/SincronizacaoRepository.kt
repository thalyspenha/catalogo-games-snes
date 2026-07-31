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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.OkHttpClient
import retrofit2.HttpException

private const val THROTTLE_MS = 1200L
private const val MAX_TENTATIVAS_REDE = 3
private val BACKOFF_MS = listOf(2000L, 4000L)

/** Nº de falhas de rede (exceção/HTTP) seguidas que faz o sync desistir e parar de martelar a API. */
private const val LIMIAR_FALHAS_REDE_CONSECUTIVAS = 5

/** Códigos HTTP que o ScreenScraper usa para sinalizar limite/cota (visto na documentação da comunidade). */
private val CODIGOS_HTTP_COTA = setOf(429, 430, 431)

class SincronizacaoRepository(
    private val context: Context,
    private val jogoDao: JogoDao,
    private val posseUsuarioDao: PosseUsuarioDao,
    private val sincronizacaoStatusDao: SincronizacaoStatusDao,
    private val screenScraperApi: ScreenScraperApi,
    private val okHttpClient: OkHttpClient,
) {

    private val _estado = MutableStateFlow<SincronizacaoEstado>(SincronizacaoEstado.Ocioso)
    val estado: StateFlow<SincronizacaoEstado> = _estado.asStateFlow()

    suspend fun sincronizar() {
        // Trava de reentrância simples: se já está em andamento, não inicia outra vez. Fecha a
        // maior parte da janela em que a UI poderia mostrar Ocioso e permitir um segundo toque
        // (a checagem em si não é atômica, mas cobre o caso comum de "botão apertado 2x").
        if (_estado.value is SincronizacaoEstado.EmAndamento) return

        if (!ScreenScraperCredenciais.credenciaisDeDesenvolvedorConfiguradas) {
            _estado.value = SincronizacaoEstado.Erro(
                "Credenciais do ScreenScraper não configuradas (devid/devpassword em local.properties)."
            )
            return
        }

        // Fecha a janela visual: já marca "em andamento" antes de qualquer I/O (inclusive antes
        // do primeiro delay de throttle lá dentro de sincronizarInterno).
        _estado.value = SincronizacaoEstado.EmAndamento(atual = 0, total = 0, nomeJogoAtual = "")

        try {
            // CatalogoMestreLoader.carregar faz I/O de asset bloqueante + parse de JSON; sem isso
            // rodando em Dispatchers.IO, uma chamada a partir do Main (ex: viewModelScope) trava a UI.
            withContext(Dispatchers.IO) {
                sincronizarInterno()
            }
        } finally {
            // Se sincronizarInterno saiu sem deixar um estado terminal (Concluido/CotaEsgotada/Erro)
            // — por exemplo, cancelamento — não deixa a UI travada em "em andamento" pra sempre.
            if (_estado.value is SincronizacaoEstado.EmAndamento) {
                _estado.value = SincronizacaoEstado.Ocioso
            }
        }
    }

    private suspend fun sincronizarInterno() {
        val catalogoMestre = CatalogoMestreLoader.carregar(context)
        val totalLinhasStatus = sincronizacaoStatusDao.contarLinhas()
        if (totalLinhasStatus == 0) {
            jogoDao.limparTudo()
            posseUsuarioDao.limparTudo()
        }

        val crcsComSucesso = sincronizacaoStatusDao.buscarCrcsPorStatus(StatusSincronizacao.SUCESSO).toSet()
        val restante = calcularRestante(catalogoMestre, crcsComSucesso)
        val total = catalogoMestre.size
        // Imutável: quantos já estavam com SUCESSO antes desta rodada. `sucesso` (mutável, abaixo)
        // segue crescendo durante o loop e não pode ser usado pra calcular o progresso "atual" —
        // senão o contador soma duas vezes o que já tinha sido feito antes.
        val jaConcluidos = crcsComSucesso.size
        var sucesso = jaConcluidos
        var falhasRedeConsecutivas = 0

        for ((indice, item) in restante.withIndex()) {
            coroutineContext.ensureActive()
            delay(THROTTLE_MS)

            _estado.value = SincronizacaoEstado.EmAndamento(
                atual = jaConcluidos + indice + 1,
                total = total,
                nomeJogoAtual = item.nomeExibicao,
            )

            when (val resultado = buscarComRetry(item)) {
                is ResultadoBusca.Sucesso -> {
                    falhasRedeConsecutivas = 0
                    val jogoEntity = ScreenScraperMapper.paraJogoEntity(resultado.jeu)
                    if (jogoEntity == null) {
                        sincronizacaoStatusDao.salvar(
                            SincronizacaoStatusEntity(item.crc, StatusSincronizacao.FALHA, null, "Resposta sem id/nome válidos")
                        )
                    } else {
                        val caminhoCapa = jogoEntity.urlCapa?.let { url ->
                            CapaDownloader.baixar(context, okHttpClient, jogoEntity.id, url)
                        }
                        jogoDao.inserirTodos(listOf(jogoEntity.copy(caminhoCapaLocal = caminhoCapa)))
                        sincronizacaoStatusDao.salvar(
                            SincronizacaoStatusEntity(item.crc, StatusSincronizacao.SUCESSO, jogoEntity.id, null)
                        )
                        sucesso++
                    }
                }
                is ResultadoBusca.NaoEncontrado -> {
                    falhasRedeConsecutivas = 0
                    sincronizacaoStatusDao.salvar(
                        SincronizacaoStatusEntity(item.crc, StatusSincronizacao.FALHA, null, "Jogo não encontrado no ScreenScraper")
                    )
                }
                is ResultadoBusca.CotaEsgotada -> {
                    _estado.value = SincronizacaoEstado.CotaEsgotada(sucesso = sucesso, restantes = total - sucesso)
                    return
                }
                is ResultadoBusca.ErroDeRede -> {
                    falhasRedeConsecutivas++
                    sincronizacaoStatusDao.salvar(
                        SincronizacaoStatusEntity(item.crc, StatusSincronizacao.FALHA, null, resultado.mensagem)
                    )
                    // Disjuntor: N erros de rede seguidos é sinal de que algo sistêmico está
                    // acontecendo (cota esgotada sem o header sinalizar, IP bloqueado, API fora do
                    // ar) — continuar martelando os itens restantes por horas não ajuda em nada.
                    if (falhasRedeConsecutivas >= LIMIAR_FALHAS_REDE_CONSECUTIVAS) {
                        _estado.value = SincronizacaoEstado.CotaEsgotada(sucesso = sucesso, restantes = total - sucesso)
                        return
                    }
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
            } catch (e: CancellationException) {
                // Nunca engolir cancelamento: precisa propagar pra estruturada concorrência
                // (coroutineScope/viewModelScope) reconhecer que o job foi cancelado de verdade,
                // em vez de retentar 3x um "erro" que na verdade é o usuário saindo da tela.
                throw e
            } catch (e: HttpException) {
                // Sinal direto de cota: a API costuma responder 429/430/431 quando o limite diário
                // é excedido, e isso vinha caindo no catch genérico (retry inútil por 3 tentativas).
                if (e.code() in CODIGOS_HTTP_COTA) return ResultadoBusca.CotaEsgotada
                ultimoErro = "HTTP ${e.code()}: ${e.message()}"
                if (tentativa < MAX_TENTATIVAS_REDE - 1) delay(BACKOFF_MS[tentativa])
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
                        okHttpClient = NetworkModule.okHttpClient,
                    ).also { instancia = it }
                }
            }
        }
    }
}

/**
 * Heurística best-effort pra detectar cota diária esgotada pelo texto de erro do header.
 * O texto exato ainda não foi observado com uma cota real estourada (ver spec) — ajustar
 * aqui se, na prática, a API sinalizar isso de outro jeito. Complementada em buscarComRetry
 * por uma checagem direta de código HTTP (429/430/431) e por um disjuntor de falhas de rede
 * consecutivas, pros casos em que nem o header nem o código HTTP denunciam a cota.
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
