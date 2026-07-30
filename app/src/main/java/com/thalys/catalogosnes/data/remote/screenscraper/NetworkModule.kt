package com.thalys.catalogosnes.data.remote.screenscraper

import com.thalys.catalogosnes.BuildConfig
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Monta OkHttpClient + Retrofit para a API do ScreenScraper. Sem framework de DI: acesso
 * via [NetworkModule.screenScraperApi] (singleton simples, construído sob demanda).
 */
object NetworkModule {

    private const val BASE_URL = "https://www.screenscraper.fr/api2/"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        // A API é inconsistente quanto a aspas em campos numéricos: em jeuInfos.php os ids
        // vêm como string ("id": "2144"), mas em systemesListe.php o id do sistema vem como
        // número puro ("id": 4). Confirmado com JSON real em 2026-07-30. isLenient permite
        // decodificar um literal numérico não citado em um campo String? sem lançar exceção.
        isLenient = true
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val screenScraperApi: ScreenScraperApi by lazy {
        retrofit.create(ScreenScraperApi::class.java)
    }
}
