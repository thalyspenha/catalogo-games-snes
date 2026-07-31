package com.thalys.catalogosnes.data.sync

import android.content.Context
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Baixa a capa de [url] e salva em armazenamento privado do app (`noBackupFilesDir/capas/<jogoId>.jpg`).
 * Necessário porque o ScreenScraper manda as capas com `Cache-Control: no-cache,
 * must-revalidate` — o cache em disco do Coil sozinho não é suficiente pra funcionar offline,
 * já que o Coil é obrigado a confirmar com o servidor antes de reusar o arquivo cacheado.
 *
 * Usa `noBackupFilesDir` (em vez de `filesDir`) porque capas são dado re-baixável e não devem
 * competir com a cota de 25MB do Auto Backup do Android — essa cota precisa sobrar pros dados
 * de posse do usuário (`posse_usuario`: status, completude CIB, nota de condição), que não são
 * re-obteníveis.
 *
 * Registra a `Call` do OkHttp pra ser cancelada junto com a coroutine chamadora: sem isso, um
 * `execute()` bloqueante ignora `Job.cancel()` e só retorna quando o `readTimeout` do
 * `OkHttpClient` estourar (hoje 30s em NetworkModule), travando `sincronizar()`'s reentrância
 * até lá.
 *
 * Retorna `null` em qualquer falha (rede, HTTP, disco) — o chamador decide o que fazer; o
 * jogo continua sendo salvo com os metadados mesmo sem capa local.
 */
object CapaDownloader {

    suspend fun baixar(context: Context, okHttpClient: OkHttpClient, jogoId: Long, url: String): String? {
        return try {
            val chamada = okHttpClient.newCall(Request.Builder().url(url).build())
            coroutineContext[Job]?.invokeOnCompletion { causa ->
                if (causa is CancellationException) chamada.cancel()
            }
            val resposta = chamada.execute()
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
}
