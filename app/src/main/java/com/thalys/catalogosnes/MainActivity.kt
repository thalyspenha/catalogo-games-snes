package com.thalys.catalogosnes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thalys.catalogosnes.ui.theme.CatalogoSnesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CatalogoSnesTheme {
                TelaBiblioteca()
            }
        }
    }
}

private val jogosDeExemplo = listOf(
    "Super Mario World",
    "The Legend of Zelda: A Link to the Past",
    "Chrono Trigger",
    "Super Metroid",
    "Donkey Kong Country",
    "EarthBound",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaBiblioteca() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catálogo SNES") },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { paddingInterno ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = paddingInterno,
            modifier = Modifier.padding(8.dp)
        ) {
            items(jogosDeExemplo) { nomeJogo ->
                CartaoJogo(nomeJogo)
            }
        }
    }
}

@Composable
private fun CartaoJogo(nome: String) {
    Card(modifier = Modifier.padding(8.dp)) {
        Text(text = nome, modifier = Modifier.padding(12.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewTelaBiblioteca() {
    CatalogoSnesTheme {
        TelaBiblioteca()
    }
}
