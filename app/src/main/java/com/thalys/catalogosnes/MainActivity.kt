package com.thalys.catalogosnes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.thalys.catalogosnes.ui.navigation.CatalogoNavHost
import com.thalys.catalogosnes.ui.theme.CatalogoSnesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CatalogoSnesTheme {
                CatalogoNavHost()
            }
        }
    }
}
