package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.IdeaVaultApp
import com.example.ui.VaultViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vaultViewModel: VaultViewModel = viewModel(
                factory = VaultViewModel.provideFactory(applicationContext)
            )
            IdeaVaultApp(viewModel = vaultViewModel)
        }
    }
}
