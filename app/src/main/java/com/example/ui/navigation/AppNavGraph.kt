package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.ui.screen.chat.ChatScreen
import com.example.ui.screen.contacts.ContactsScreen
import com.example.ui.screen.register.RegisterScreen
import com.example.ui.screen.settings.SettingsScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = "register"
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("register") {
            RegisterScreen(
                onNavigateToContacts = {
                    navController.navigate("contacts") {
                        popUpTo("register") { inclusive = true }
                    }
                }
            )
        }
        composable("contacts") {
            ContactsScreen(
                onNavigateToChat = { peerHash ->
                    navController.navigate("chat/$peerHash")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }
        composable("chat/{peerHash}") { backStackEntry ->
            val peerHash = backStackEntry.arguments?.getString("peerHash") ?: ""
            ChatScreen(
                peerHash = peerHash,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLogout = {
                    navController.navigate("register") {
                        popUpTo("contacts") { inclusive = true }
                    }
                }
            )
        }
    }
}
