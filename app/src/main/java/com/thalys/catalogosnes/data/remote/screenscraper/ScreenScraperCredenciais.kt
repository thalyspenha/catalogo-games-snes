package com.thalys.catalogosnes.data.remote.screenscraper

import com.thalys.catalogosnes.BuildConfig

/**
 * Credenciais do ScreenScraper, configuradas via local.properties -> BuildConfig (nunca
 * hardcoded). Ficam vazias até o usuário obter devid/devpassword junto ao ScreenScraper e
 * preencher local.properties.
 */
object ScreenScraperCredenciais {
    val devId: String get() = BuildConfig.SCREENSCRAPER_DEVID
    val devPassword: String get() = BuildConfig.SCREENSCRAPER_DEVPASSWORD
    val softName: String get() = BuildConfig.SCREENSCRAPER_SOFTNAME
    val usuarioId: String get() = BuildConfig.SCREENSCRAPER_USER_ID
    val usuarioSenha: String get() = BuildConfig.SCREENSCRAPER_USER_PASSWORD

    /** true quando devid/devpassword já foram preenchidos em local.properties. */
    val credenciaisDeDesenvolvedorConfiguradas: Boolean
        get() = devId.isNotBlank() && devPassword.isNotBlank()
}
