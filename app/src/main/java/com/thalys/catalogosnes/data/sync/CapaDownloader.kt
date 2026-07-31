package com.thalys.catalogosnes.data.sync

import android.content.Context
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Baixa a capa de [url] e salva em armazenamento privado do app, fora do Auto Backup
 * (`noBackupFilesDir/capas/<jogoId>.jpg`). Necessário porque o ScreenScraper manda as capas
 * com `Cache-Control: no-cache, must-revalidate` — o cache em disco do Coil sozinho não é
 * suficiente pra funcionar offline, já que o Coil é obrigado a confirmar com o servidor antes
 * de reusar o arquivo cacheado.
 *
 * Usa `enqueue` (assíncrono) em vez de `execute` (bloqueante) pra que o cancelamento da
 * coroutine (ex: usuário sai da tela de sync no meio de um download) interrompa a chamada de
 * verdade via `Call.cancel()`, em vez de travar até o timeout de leitura do OkHttpClient.
 *
 * Retorna `null` em qualquer falha (rede, HTTP, disco) — o chamador decide o que fazer; o
 * jogo continua sendo salvo com os metadados mesmo sem capa local.
 */
object CapaDownloader {

    suspend fun baixar(context: Context, okHttpClient: OkHttpClient, jogoId: Long, url: String): String? {
        return try {
            val chamada = okHttpClient.newCall(Request.Builder().url(url).build())
            val resposta = executarCancelavel(chamada)
            resposta.use {
                if (!it.isSuccessful) return null
                val bytes = it.body?.bytes() ?: return null
                val diretorio = File(context.noBackupFilesDir, "capas").apply { mkdirs() }
                val arquivo = File(diretorio, "$jogoId.jpg")
                arquivo.writeBytes(bytes)
                arquivo.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun executarCancelavel(chamada: Call): Response =
        suspendCancellableCoroutine { continuacao ->
            continuacao.invokeOnCancellation { chamada.cancel() }
            chamada.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuacao.resume(response)
                }
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuacao.isCancelled) continuacao.resumeWithException(e)
                }
            })
        }
}
