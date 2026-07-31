package com.thalys.catalogosnes.data.remote.screenscraper

import com.thalys.catalogosnes.data.local.JogoEntity
import com.thalys.catalogosnes.data.remote.screenscraper.dto.JeuDto
import com.thalys.catalogosnes.data.remote.screenscraper.dto.MidiaDto

/**
 * Converte o [JeuDto] retornado pelo ScreenScraper (jeuInfos.php / jeuRecherche.php) para
 * o [JogoEntity] persistido no Room.
 *
 * A API devolve nomes, sinopses, datas e mídias em várias regiões/idiomas ao mesmo tempo
 * (ex: "us", "eu", "jp", "wor" para região; "pt", "en", "wor" para idioma). As listas de
 * prioridade abaixo são uma escolha de produto — não vêm da API — e servem de ponto de
 * partida; ajustar quando o usuário definir preferência final de região/idioma.
 */
object ScreenScraperMapper {

    private val PRIORIDADE_REGIAO = listOf("us", "wor", "eu", "ss", "jp")
    private val PRIORIDADE_IDIOMA = listOf("pt", "en", "wor")

    private const val TIPO_CAPA_2D = "box-2D"
    private const val TIPO_CAPA_3D = "box-3D"

    /**
     * Largura máxima pedida ao ScreenScraper pra capa (parâmetro `maxwidth`, respeitado
     * server-side). 600px dá folga confortável pra thumbnail de 120dp mesmo em tela de
     * densidade alta (3x × 120dp ≈ 360px), cortando bastante o tamanho do arquivo em relação
     * ao original em resolução plena (medido: média de 511KB/capa sem limite, ~880MB pro
     * catálogo completo de 1763 jogos).
     */
    private const val LARGURA_MAXIMA_CAPA = 600

    /** Retorna null se o DTO não tiver o mínimo necessário (id e nome). */
    fun paraJogoEntity(jeu: JeuDto): JogoEntity? {
        val id = jeu.id?.toLongOrNull() ?: return null

        val nome = escolherPorChave(jeu.noms?.map { it.region to it.text }, PRIORIDADE_REGIAO)
            ?: jeu.noms?.firstOrNull()?.text
            ?: return null

        val descricao = escolherPorChave(jeu.synopsis?.map { it.langue to it.text }, PRIORIDADE_IDIOMA)
            ?: jeu.synopsis?.firstOrNull()?.text

        val anoLancamento = (
            escolherPorChave(jeu.dates?.map { it.region to it.text }, PRIORIDADE_REGIAO)
                ?: jeu.dates?.firstOrNull()?.text
            )?.let { extrairAno(it) }

        val genero = jeu.genres?.firstOrNull()?.noms?.let { noms ->
            escolherPorChave(noms.map { it.langue to it.text }, PRIORIDADE_IDIOMA) ?: noms.firstOrNull()?.text
        }

        val regiaoEscolhida = jeu.noms
            ?.mapNotNull { it.region }
            ?.let { regioes -> PRIORIDADE_REGIAO.firstOrNull { it in regioes } }

        return JogoEntity(
            id = id,
            nome = nome,
            descricao = descricao,
            anoLancamento = anoLancamento,
            genero = genero,
            desenvolvedora = jeu.developpeur?.text,
            publicadora = jeu.editeur?.text,
            urlCapa = escolherCapa(jeu.medias),
            regiao = regiaoEscolhida,
        )
    }

    private fun escolherPorChave(itens: List<Pair<String?, String?>>?, prioridades: List<String>): String? {
        if (itens.isNullOrEmpty()) return null
        for (chave in prioridades) {
            itens.firstOrNull { it.first == chave }?.second?.let { return it }
        }
        return null
    }

    private fun extrairAno(data: String): Int? {
        // ScreenScraper costuma devolver datas como "dd/mm/yyyy" ou só "yyyy".
        return Regex("(\\d{4})").find(data)?.value?.toIntOrNull()
    }

    private fun escolherCapa(medias: List<MidiaDto>?): String? {
        if (medias.isNullOrEmpty()) return null
        val capas = medias.filter { it.type == TIPO_CAPA_2D || it.type == TIPO_CAPA_3D }
        for (regiao in PRIORIDADE_REGIAO) {
            capas.firstOrNull { it.region == regiao && it.type == TIPO_CAPA_2D }?.url?.let { return comMaxWidth(it) }
        }
        for (regiao in PRIORIDADE_REGIAO) {
            capas.firstOrNull { it.region == regiao }?.url?.let { return comMaxWidth(it) }
        }
        return capas.firstOrNull()?.url?.let { comMaxWidth(it) }
    }

    private fun comMaxWidth(url: String): String = "$url&maxwidth=$LARGURA_MAXIMA_CAPA"
}
