package ferramentas

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class AgrupamentoClonesTest {

    private val xmlFixture = """
        <?xml version="1.0"?>
        <datafile>
          <game name="Standalone Game (World)" id="0001">
            <category>Games</category>
            <rom name="Standalone Game (World).sfc" size="1048576" crc="aaaaaaaa"/>
          </game>
          <game name="Grouped Game (Japan)" id="0002">
            <category>Games</category>
            <rom name="Grouped Game (Japan).sfc" size="2097152" crc="bbbbbbbb"/>
          </game>
          <game name="Grouped Game (USA)" id="0003" cloneofid="0002">
            <category>Games</category>
            <rom name="Grouped Game (USA).sfc" size="2097152" crc="cccccccc"/>
          </game>
          <game name="Beta Game (Europe) (Beta)" id="0004">
            <category>Games</category>
            <category>Preproduction</category>
            <rom name="Beta Game (Europe) (Beta).sfc" size="524288" crc="dddddddd"/>
          </game>
          <game name="Root Filtered (Prototype)" id="0005">
            <category>Preproduction</category>
            <rom name="Root Filtered (Prototype).sfc" size="1048576" crc="eeeeeeee"/>
          </game>
          <game name="Root Filtered (Release)" id="0006" cloneofid="0005">
            <category>Games</category>
            <rom name="Root Filtered (Release).sfc" size="1048576" crc="ffffffff"/>
          </game>
        </datafile>
    """.trimIndent()

    @Test
    fun `agrupa por cloneofid, escolhe raiz quando elegivel, exclui beta duplo-categorizado`() {
        val arquivoTemporario = File.createTempFile("fixture", ".dat")
        arquivoTemporario.writeText(xmlFixture)
        arquivoTemporario.deleteOnExit()

        val jogos = parsearDat(arquivoTemporario)
        val catalogo = agruparEDeduplicar(jogos)

        assertEquals(3, catalogo.size)
        assertEquals(
            listOf("Grouped Game (Japan)", "Root Filtered (Release)", "Standalone Game (World)"),
            catalogo.map { it.nomeExibicao },
        )
        assertEquals("bbbbbbbb", catalogo.first { it.nomeExibicao == "Grouped Game (Japan)" }.crc)
        assertEquals("ffffffff", catalogo.first { it.nomeExibicao == "Root Filtered (Release)" }.crc)
        assertEquals("aaaaaaaa", catalogo.first { it.nomeExibicao == "Standalone Game (World)" }.crc)
    }
}
