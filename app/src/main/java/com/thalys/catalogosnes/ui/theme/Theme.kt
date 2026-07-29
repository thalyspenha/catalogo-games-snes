package com.thalys.catalogosnes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val EsquemaEscuro = darkColorScheme(
    primary = SnesRoxoClaro,
    secondary = SnesVerde,
    tertiary = SnesVermelho,
    background = SnesFundo,
    surface = SnesSuperficie,
)

private val EsquemaClaro = lightColorScheme(
    primary = SnesRoxo,
    secondary = SnesVerde,
    tertiary = SnesVermelho,
)

@Composable
fun CatalogoSnesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) EsquemaEscuro else EsquemaClaro
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
