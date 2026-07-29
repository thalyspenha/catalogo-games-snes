package com.thalys.catalogosnes.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class JogoComPosse(
    @Embedded val jogo: JogoEntity,
    @Relation(parentColumn = "id", entityColumn = "jogoId")
    val posse: PosseUsuarioEntity?,
)
