package ferramentas

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Uma entrada `<game>` do DAT No-Intro, antes do filtro de categoria/dedup. */
data class JogoDat(
    val id: String,
    val cloneOfId: String?,
    val categorias: Set<String>,
    val romNome: String,
    val crc: String,
    val romTamanho: Long,
    val nomeExibicao: String,
)

fun parsearDat(arquivo: File): List<JogoDat> {
    val factory = DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()
    val doc = builder.parse(arquivo)
    val gameNodes = doc.getElementsByTagName("game")

    val jogos = mutableListOf<JogoDat>()
    for (i in 0 until gameNodes.length) {
        val gameEl = gameNodes.item(i) as Element
        val id = gameEl.getAttribute("id")
        val cloneOfId = gameEl.getAttribute("cloneofid").ifBlank { null }
        val nomeExibicao = gameEl.getAttribute("name")

        val categorias = mutableSetOf<String>()
        val categoriaNodes = gameEl.getElementsByTagName("category")
        for (j in 0 until categoriaNodes.length) {
            categorias.add(categoriaNodes.item(j).textContent.trim())
        }

        val romNodes = gameEl.getElementsByTagName("rom")
        if (romNodes.length == 0) continue
        val romEl = romNodes.item(0) as Element
        val romNome = romEl.getAttribute("name")
        val crc = romEl.getAttribute("crc")
        val romTamanho = romEl.getAttribute("size").toLongOrNull() ?: continue

        jogos.add(JogoDat(id, cloneOfId, categorias, romNome, crc, romTamanho, nomeExibicao))
    }
    return jogos
}

/**
 * Filtra só entradas cujo conjunto de categorias é exatamente {"Games"} (exclui as que
 * também têm Preproduction/Demos — são beta/protótipo/demo, ver spec), agrupa por
 * cloneofid (ou o próprio id quando não é clone de nada) e escolhe 1 representante por
 * grupo: a raiz (sem cloneofid) se ela sobreviveu ao filtro, senão o de menor id entre
 * os clones elegíveis que sobraram.
 */
fun agruparEDeduplicar(jogos: List<JogoDat>): List<ItemCatalogoMestre> {
    val elegiveis = jogos.filter { it.categorias == setOf("Games") }
    val grupos = elegiveis.groupBy { it.cloneOfId ?: it.id }

    return grupos.values.map { membros ->
        val representante = membros.firstOrNull { it.cloneOfId == null }
            ?: membros.minBy { it.id.toInt() }
        ItemCatalogoMestre(
            romNome = representante.romNome,
            crc = representante.crc,
            romTamanho = representante.romTamanho,
            nomeExibicao = representante.nomeExibicao,
        )
    }.sortedBy { it.nomeExibicao }
}

fun main(args: Array<String>) {
    require(args.size == 2) {
        "Uso: gerarCatalogoMestre <caminho do .dat> <caminho do .json de saida>"
    }
    val jogos = parsearDat(File(args[0]))
    val catalogo = agruparEDeduplicar(jogos)
    val json = Json { prettyPrint = true }
    File(args[1]).writeText(json.encodeToString(ListSerializer(ItemCatalogoMestre.serializer()), catalogo))
    println("Gerado ${catalogo.size} jogos únicos em ${args[1]}")
}
