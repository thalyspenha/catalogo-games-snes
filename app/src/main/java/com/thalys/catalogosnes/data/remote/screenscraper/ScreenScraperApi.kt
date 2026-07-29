package com.thalys.catalogosnes.data.remote.screenscraper

import com.thalys.catalogosnes.data.remote.screenscraper.dto.JogoBuscaRespostaDto
import com.thalys.catalogosnes.data.remote.screenscraper.dto.JogoInfoRespostaDto
import com.thalys.catalogosnes.data.remote.screenscraper.dto.SistemasRespostaDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API v2 do ScreenScraper.fr (base: https://www.screenscraper.fr/api2/).
 *
 * Todo endpoint exige devid/devpassword/softname (credenciais de desenvolvedor). ssid e
 * sspassword (conta de usuário do dono do app) são opcionais, mas aumentam a cota diária
 * de requisições. Nenhum valor default é hardcoded aqui — quem chama passa o que vier de
 * [ScreenScraperCredenciais] (lido do BuildConfig / local.properties).
 */
interface ScreenScraperApi {

    /** ID do sistema Super Nintendo / SNES na API do ScreenScraper: 4 (confirmado). */
    companion object {
        const val SISTEMA_SNES = 4
    }

    @GET("systemesListe.php")
    suspend fun listarSistemas(
        @Query("devid") devId: String,
        @Query("devpassword") devPassword: String,
        @Query("softname") softName: String,
        @Query("ssid") ssid: String? = null,
        @Query("sspassword") sspassword: String? = null,
        @Query("output") output: String = "json",
    ): SistemasRespostaDto

    /**
     * Busca um jogo específico. Identificação por [gameId] OU por hash/nome de ROM
     * (crc/md5/sha1/romNome/romTamanho) — a API tenta achar o jogo por qualquer
     * combinação de parâmetros fornecida.
     */
    @GET("jeuInfos.php")
    suspend fun buscarInfoJogo(
        @Query("devid") devId: String,
        @Query("devpassword") devPassword: String,
        @Query("softname") softName: String,
        @Query("ssid") ssid: String? = null,
        @Query("sspassword") sspassword: String? = null,
        @Query("systemeid") systemeId: Int? = null,
        @Query("gameid") gameId: Long? = null,
        @Query("romnom") romNome: String? = null,
        @Query("romtaille") romTamanho: Long? = null,
        @Query("crc") crc: String? = null,
        @Query("md5") md5: String? = null,
        @Query("sha1") sha1: String? = null,
        @Query("output") output: String = "json",
    ): JogoInfoRespostaDto

    /** Busca jogos candidatos por nome (quando não se tem hash de ROM para identificar exato). */
    @GET("jeuRecherche.php")
    suspend fun buscarJogoPorNome(
        @Query("devid") devId: String,
        @Query("devpassword") devPassword: String,
        @Query("softname") softName: String,
        @Query("ssid") ssid: String? = null,
        @Query("sspassword") sspassword: String? = null,
        @Query("systemeid") systemeId: Int? = null,
        @Query("recherche") nomeBusca: String,
        @Query("output") output: String = "json",
    ): JogoBuscaRespostaDto
}
