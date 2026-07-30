package com.thalys.catalogosnes.data.remote.screenscraper.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * DTOs fiéis ao JSON retornado pela API v2 do ScreenScraper (parâmetro output=json).
 *
 * A API do ScreenScraper majoritariamente não usa números/booleanos no JSON dentro de
 * jeuInfos.php/jeuRecherche.php: id, ano, nota etc. vêm como string. Por isso os campos
 * abaixo são String? e a conversão para tipos Kotlin (Long/Int) acontece na camada de
 * mapeamento (ScreenScraperMapper), não aqui. EXCEÇÃO confirmada: em systemesListe.php o
 * campo "id" de cada sistema vem como número JSON puro (sem aspas) — daí o `isLenient =
 * true` no Json do NetworkModule, para essa inconsistência não quebrar o parsing.
 *
 * Estrutura validada em 2026-07-30 com chamadas reais (curl) contra systemesListe.php e
 * jeuInfos.php (Super Mario World e Chrono Trigger, systemeid=4/SNES), usando credenciais
 * de desenvolvedor reais. Os campos "joueurs" (nº de jogadores) e "note" (nota média), antes
 * marcados como TODO, foram confirmados: ambos são objetos simples `{ "text": "..." }`
 * (ver [TextoSimplesDto]). O corpo de systemesListe.php também foi confirmado: "noms" é um
 * objeto com chaves fixas por variante de nome (nom_eu, nom_us, nom_jp, ...), não uma lista
 * region/text como em jeuInfos.php (ver [NomesSistemaDto]).
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

/**
 * Padrão {text} usado pela API para valores simples sem id associado. Confirmado com JSON
 * real para os campos "joueurs" (ex: {"text": "1-2"}) e "note" (ex: {"text": "17"}) de
 * jeuInfos.php.
 */
@Serializable
data class TextoSimplesDto(
    val text: String? = null,
)

@Serializable
data class GeneroDto(
    val id: String? = null,
    /** Confirmado com JSON real; usado por sscraper/Skyscraper como código curto do gênero. */
    val nomcourt: String? = null,
    /** "1" quando é o gênero principal do jogo, "0" caso contrário. */
    val principale: String? = null,
    val parentid: String? = null,
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
    /** Tamanho do arquivo de mídia em bytes (como string). Confirmado com JSON real. */
    val size: String? = null,
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
    /** Nº de jogadores (ex: "1", "1-2"). TODO do CLAUDE.md resolvido: confirmado com JSON real. */
    val joueurs: TextoSimplesDto? = null,
    /** Nota média da comunidade (ex: "17", numa escala que a API não documenta claramente aqui). TODO do CLAUDE.md resolvido: confirmado com JSON real. */
    val note: TextoSimplesDto? = null,
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
 * Nomes de um sistema em systemesListe.php: ao contrário de [JeuDto.noms] (lista
 * region/text), aqui é um objeto com uma chave fixa por variante de nome. Nem todo sistema
 * preenche todas as chaves (ex.: SNES não tem "nom_us", só "nom_eu"/"nom_jp"); confirmado
 * com JSON real de systemesListe.php.
 */
@Serializable
data class NomesSistemaDto(
    @SerialName("nom_eu") val nomEu: String? = null,
    @SerialName("nom_us") val nomUs: String? = null,
    @SerialName("nom_jp") val nomJp: String? = null,
    @SerialName("nom_recalbox") val nomRecalbox: String? = null,
    @SerialName("nom_retropie") val nomRetropie: String? = null,
    @SerialName("nom_launchbox") val nomLaunchbox: String? = null,
    @SerialName("nom_hyperspin") val nomHyperspin: String? = null,
    @SerialName("noms_commun") val nomsCommun: String? = null,
)

/**
 * Shape confirmado com JSON real de systemesListe.php (2026-07-30, id do sistema SNES = 4).
 * Diferenças em relação à estimativa anterior: "id" vem como número JSON sem aspas (por
 * isso o parser usa `isLenient = true`, ver NetworkModule) e "noms" é um objeto de chaves
 * fixas ([NomesSistemaDto]), não uma lista region/text.
 */
@Serializable
data class SistemaDto(
    val id: String? = null,
    val noms: NomesSistemaDto? = null,
    val compagnie: String? = null,
    val type: String? = null,
    val datedebut: String? = null,
    val datefin: String? = null,
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
