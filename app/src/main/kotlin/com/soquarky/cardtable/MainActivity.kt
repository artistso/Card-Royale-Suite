package com.soquarky.cardtable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.soquarky.cardtable.ui.CardTableApp
import com.soquarky.cardtable.ui.theme.CardTableTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CardTableTheme {
                CardTableApp()
            }
        }
    }
}
