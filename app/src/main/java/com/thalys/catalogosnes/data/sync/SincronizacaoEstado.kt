package com.thalys.catalogosnes.data.sync

sealed class SincronizacaoEstado {
    data object Ocioso : SincronizacaoEstado()
    data class EmAndamento(val atual: Int, val total: Int, val nomeJogoAtual: String) : SincronizacaoEstado()
    data class Concluido(val sucesso: Int, val falhas: List<FalhaSincronizacao>) : SincronizacaoEstado()
    data class CotaEsgotada(val sucesso: Int, val restantes: Int) : SincronizacaoEstado()
}

data class FalhaSincronizacao(val nomeExibicao: String, val motivo: String)
