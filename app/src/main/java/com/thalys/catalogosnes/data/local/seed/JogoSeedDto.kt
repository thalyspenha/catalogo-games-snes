package com.thalys.catalogosnes.data.local.seed

import kotlinx.serialization.Serializable

/**
 * Espelha os campos de [com.thalys.catalogosnes.data.local.JogoEntity] para desserializar
 * `assets/jogos_seed.json`. O JSON traz um "id" fixo por jogo (em vez de gerar um id
 * sequencial no parse) para manter o id estável entre execuções e futuras edições do
 * arquivo — importante porque [com.thalys.catalogosnes.data.local.PosseUsuarioEntity]
 * referencia esse id via chave estrangeira.
 */
@Serializable
data class JogoSeedDto(
    val id: Long,
    val nome: String,
    val descricao: String? = null,
    val anoLancamento: Int? = null,
    val genero: String? = null,
    val desenvolvedora: String? = null,
    val publicadora: String? = null,
    val urlCapa: String? = null,
    val regiao: String? = null,
)
