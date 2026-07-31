package com.thalys.catalogosnes.data.sync

import android.content.Context
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Baixa a capa de [url] e salva em armazenamento privado do app (`filesDir/capas/<jogoId>.jpg`).
 * Necessário porque o ScreenScraper manda as capas com `Cache-Control: no-cache,
 * must-revalidate` — o cache em disco do Coil sozinho não é suficiente pra funcionar offline,
 * já que o Coil é obrigado a confirmar com o servidor antes de reusar o arquivo cacheado.
 *
 * Retorna `null` em qualquer falha (rede, HTTP, disco) — o chamador decide o que fazer; o
 * jogo continua sendo salvo com os metadados mesmo sem capa local.
 */
object CapaDownloader {

    fun baixar(context: Context, okHttpClient: OkHttpClient, jogoId: Long, url: String): String? {
        return try {
            val resposta = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
            resposta.use {
                if (!it.isSuccessful) return null
                val bytes = it.body?.bytes() ?: return null
                val diretorio = File(context.filesDir, "capas").apply { mkdirs() }
                val arquivo = File(diretorio, "$jogoId.jpg")
                arquivo.writeBytes(bytes)
                arquivo.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }
}
