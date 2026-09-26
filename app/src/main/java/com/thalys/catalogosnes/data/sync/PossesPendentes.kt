package com.thalys.catalogosnes.data.sync

import android.content.Context
import com.thalys.catalogosnes.data.local.JogoComPosse
import com.thalys.catalogosnes.data.local.PosseUsuarioEntity
import com.thalys.catalogosnes.data.model.StatusPosse
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.Normalizer

/**
 * Posse marcada pelo usuário sobre um jogo do seed, guardada fora do Room enquanto a primeira
 * sincronização troca o catálogo. Os ids do seed (1..25) não são ids do ScreenScraper, então a
 * posse é reassociada pelo nome normalizado quando o jogo correspondente chega da API.
 */
@Serializable
data class PossePendente(
    val nomeNormalizado: String,
    val status: StatusPosse,
    val temCartucho: Boolean,
    val temCaixa: Boolean,
    val temManual: Boolean,
    val caminhoFoto: String?,
    val notaCondicao: String?,
    val atualizadoEm: Long,
) {
    fun paraEntity(jogoId: Long) = PosseUsuarioEntity(
        jogoId = jogoId,
        status = status,
        temCartucho = temCartucho,
        temCaixa = temCaixa,
        temManual = temManual,
        caminhoFoto = caminhoFoto,
        notaCondicao = notaCondicao,
        atualizadoEm = atualizadoEm,
    )
}

/** Converte os jogos com posse em pendências, indexadas pelo nome normalizado do jogo. */
internal fun extrairPossesPendentes(jogos: List<JogoComPosse>): List<PossePendente> =
    jogos.mapNotNull { jogoComPosse ->
        val posse = jogoComPosse.posse ?: return@mapNotNull null
        PossePendente(
            nomeNormalizado = normalizarNomeParaCasamento(jogoComPosse.jogo.nome),
            status = posse.status,
            temCartucho = posse.temCartucho,
            temCaixa = posse.temCaixa,
            temManual = posse.temManual,
            caminhoFoto = posse.caminhoFoto,
            notaCondicao = posse.notaCondicao,
            atualizadoEm = posse.atualizadoEm,
        )
    }

/**
 * Chave de casamento por nome: minúsculas, sem acento, sem pontuação e sem o artigo "the"
 * (o ScreenScraper às vezes usa "Legend of Zelda, The").
 */
internal fun normalizarNomeParaCasamento(nome: String): String =
    Normalizer.normalize(nome, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotEmpty() && it != "the" }
        .joinToString(" ")

/** Persiste as pendências em arquivo para sobreviverem a uma sync interrompida no meio. */
internal object PossesPendentesStore {
    private const val NOME_ARQUIVO = "posses_pendentes_sync.json"
    private val json = Json { ignoreUnknownKeys = true }

    private fun arquivo(context: Context) = File(context.filesDir, NOME_ARQUIVO)

    fun carregar(context: Context): List<PossePendente> {
        val arquivo = arquivo(context)
        if (!arquivo.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<PossePendente>>(arquivo.readText()) }
            .getOrDefault(emptyList())
    }

    fun salvar(context: Context, pendentes: List<PossePendente>) {
        val arquivo = arquivo(context)
        if (pendentes.isEmpty()) {
            arquivo.delete()
            return
        }
        val temporario = File(arquivo.parentFile, "$NOME_ARQUIVO.tmp")
        temporario.writeText(json.encodeToString(pendentes))
        temporario.renameTo(arquivo)
    }
}
