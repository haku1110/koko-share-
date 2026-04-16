package com.example.myapplication.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun KokoNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val viewModel: MainViewModel = viewModel()

    if (viewModel.isAuthLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        val startDestination = if (viewModel.currentUserProfile == null) "login" else "map"

        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable("login") {
                LoginScreen(
                    onLoginSuccess = { idToken ->
                        viewModel.signInWithGoogle(
                            idToken = idToken,
                            onSuccess = {
                                navController.navigate("map") {
                                    popUpTo("login") { inclusive = true }
                                }
                            },
                            onFailure = {
                                // Handle failure if needed, e.g., show a snackbar
                            }
                        )
                    }
                )
            }
            composable("map") {
                MainMapScreen(
                    viewModel = viewModel,
                    onNavigateToCamera = { navController.navigate("camera") },
                    onNavigateToPhotoList = { navController.navigate("photo_list") }
                )
            }
            composable("camera") {
                KokoCameraScreen(
                    onPhotoCaptured = { uri, location ->
                        viewModel.addPhotoPost(uri, location)
                        navController.popBackStack()
                    },
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
            composable("photo_list") {
                PhotoListScreen(
                    viewModel = viewModel,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
