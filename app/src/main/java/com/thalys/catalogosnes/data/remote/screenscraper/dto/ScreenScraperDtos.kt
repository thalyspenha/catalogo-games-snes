package com.thalys.catalogosnes.data.remote.screenscraper.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * DTOs fiéis ao JSON retornado pela API v2 do ScreenScraper (parâmetro output=json).
 *
 * A API do ScreenScraper praticamente não usa números/booleanos no JSON: id, ano, nota
 * etc. vêm como string. Por isso os campos abaixo são String? e a conversão para tipos
 * Kotlin (Long/Int) acontece na camada de mapeamento (ScreenScraperMapper), não aqui.
 *
 * Estrutura confirmada cruzando implementações de referência que consomem essa mesma API
 * publicamente documentada em screenscraper.fr/webapi2.php: Skyscraper (C++), sscraper
 * (Python) e screech (Go). Os campos "joueurs" (nº de jogadores), "note" (nota média) e o
 * corpo de systemesListe.php (SistemaDto) NÃO puderam ser confirmados com uma amostra real
 * de JSON e ficam marcados como TODO — ver relatório da tarefa.
 */

@Serializable
data class HeaderDto(
    @SerialName("APIversion") val apiVersion: String? = null,
    val dateTime: String? = null,
    val commandRequested: String? = null,
    val success: String? = null,
    val error: String? = null,
)

/** Nome/data associado a uma região (ex: "us", "eu", "jp", "wor"). */
@Serializable
data class TextoRegionalDto(
    val region: String? = null,
    val text: String? = null,
)

/** Sinopse/gênero associado a um idioma (ex: "pt", "en", "wor"). */
@Serializable
data class TextoIdiomaDto(
    val langue: String? = null,
    val text: String? = null,
)

/** Padrão {id, text} usado pela API para referências simples (desenvolvedora, publicadora, sistema). */
@Serializable
data class RefComTextoDto(
    val id: String? = null,
    val text: String? = null,
)

@Serializable
data class GeneroDto(
    val id: String? = null,
    val noms: List<TextoIdiomaDto>? = null,
)

@Serializable
data class ClassificacaoDto(
    val type: String? = null,
    val text: String? = null,
)

/** Uma mídia (capa, screenshot, wheel, vídeo etc). Capas costumam vir com type "box-2D" ou "box-3D". */
@Serializable
data class MidiaDto(
    val type: String? = null,
    val parent: String? = null,
    val url: String? = null,
    val region: String? = null,
    val crc: String? = null,
    val md5: String? = null,
    val sha1: String? = null,
    val format: String? = null,
)

@Serializable
data class JeuDto(
    val id: String? = null,
    val romid: String? = null,
    val noms: List<TextoRegionalDto>? = null,
    val synopsis: List<TextoIdiomaDto>? = null,
    val dates: List<TextoRegionalDto>? = null,
    val genres: List<GeneroDto>? = null,
    val classifications: List<ClassificacaoDto>? = null,
    val developpeur: RefComTextoDto? = null,
    val editeur: RefComTextoDto? = null,
    val systeme: RefComTextoDto? = null,
    val medias: List<MidiaDto>? = null,
)

@Serializable
data class JogoInfoBodyDto(
    val jeu: JeuDto? = null,
)

/** Resposta de jeuInfos.php: busca um jogo específico (por id ou por hash de ROM). */
@Serializable
data class JogoInfoRespostaDto(
    val header: HeaderDto? = null,
    val response: JogoInfoBodyDto? = null,
)

@Serializable
data class JogoBuscaBodyDto(
    val jeux: List<JeuDto>? = null,
)

/** Resposta de jeuRecherche.php: busca jogos candidatos por nome. */
@Serializable
data class JogoBuscaRespostaDto(
    val header: HeaderDto? = null,
    val response: JogoBuscaBodyDto? = null,
)

/**
 * TODO: shape não confirmado com amostra real de JSON de systemesListe.php.
 * Estimativa baseada no padrão id/noms observado em outras partes da API
 * (ex: RefComTextoDto). Validar assim que houver credenciais reais.
 */
@Serializable
data class SistemaDto(
    val id: String? = null,
    val noms: List<TextoRegionalDto>? = null,
)

@Serializable
data class SistemasBodyDto(
    val systemes: List<SistemaDto>? = null,
)

/** Resposta de systemesListe.php: lista todos os sistemas/consoles conhecidos pela API. */
@Serializable
data class SistemasRespostaDto(
    val header: HeaderDto? = null,
    val response: SistemasBodyDto? = null,
)
