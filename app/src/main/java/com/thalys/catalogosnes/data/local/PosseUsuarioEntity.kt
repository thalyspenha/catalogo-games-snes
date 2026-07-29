package com.thalys.catalogosnes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.thalys.catalogosnes.data.model.StatusPosse

/**
 * Dados pessoais do usuário sobre um jogo do catálogo: se já tem, condição, fotos etc.
 * Separado de [JogoEntity] porque é dado local do usuário, não metadado do catálogo.
 */
@Entity(
    tableName = "posse_usuario",
    foreignKeys = [
        ForeignKey(
            entity = JogoEntity::class,
            parentColumns = ["id"],
            childColumns = ["jogoId"],
        )
    ],
)
data class PosseUsuarioEntity(
    @PrimaryKey val jogoId: Long,
    val status: StatusPosse,
    val temCartucho: Boolean = false,
    val temCaixa: Boolean = false,
    val temManual: Boolean = false,
    val caminhoFoto: String? = null,
    val notaCondicao: String? = null,
    val atualizadoEm: Long,
)
