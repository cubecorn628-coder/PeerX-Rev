package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.data.local.AccountDataStore
import com.example.ui.navigation.AppNavGraph
import com.example.ui.theme.PeerXTheme

class MainActivity : ComponentActivity() {

    private lateinit var accountDataStore: AccountDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        accountDataStore = (applicationContext as PeerXApp).accountDataStore
        enableEdgeToEdge()
        
        setContent {
            val accountState = accountDataStore.getAccount().collectAsState(initial = null)
            // Default to dark theme if not configured yet
            val isDark = accountState.value?.isDarkTheme != false

            PeerXTheme(darkTheme = isDark) {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AppNavGraph(navController = navController)
                    }
                }
            }
        }
    }
}
