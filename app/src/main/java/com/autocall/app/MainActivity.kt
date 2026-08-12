package com.autocall.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autocall.app.ui.AutoCallApp
import com.autocall.app.ui.AutoCallViewModel
import com.autocall.app.ui.AutoCallViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(),
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: AutoCallViewModel = viewModel(
                        factory = AutoCallViewModelFactory(application),
                    )
                    AutoCallApp(viewModel = viewModel)
                }
            }
        }
    }
}
