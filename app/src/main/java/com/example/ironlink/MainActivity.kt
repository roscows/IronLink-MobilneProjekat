package com.example.ironlink

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cloudinary.android.MediaManager
import com.example.ironlink.service.NotificationService
import com.example.ironlink.ui.auth.AuthViewModel
import com.example.ironlink.ui.auth.LoginScreen
import com.example.ironlink.ui.auth.RegistrationScreen
import com.example.ironlink.ui.details.DetailsPage
import com.example.ironlink.ui.home.HomePage
import com.example.ironlink.ui.location.MapScreen
import com.example.ironlink.ui.ranking.LeaderboardPage
import java.util.HashMap
import com.example.ironlink.ui.table.ActivityListPage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val authViewModel: AuthViewModel by viewModels()

        val config = HashMap<String, String>().apply {
            put("cloud_name", "dix3mscdw")
            put("api_key", "516612584727356")
            put("api_secret", "kjvh9cbIp2_cgw5IqTLS32KvEw")
        }
        MediaManager.init(this, config)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1002)
        }

        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Navigation(modifier = Modifier.padding(innerPadding), authViewModel = authViewModel)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1002 -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startNotificationService()
            }
        }
    }

    private fun startNotificationService() {
        val intent = Intent(this, NotificationService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}

@Composable
fun Navigation(modifier: Modifier = Modifier, authViewModel: AuthViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") { LoginScreen(navController, authViewModel) }
        composable("register") { RegistrationScreen(navController, authViewModel) }
        composable("main") { HomePage(navController, authViewModel) }

        composable("map") {
            MapScreen(viewModel(), navController, focusPartnerId = null)
        }

        composable("map/{partnerId}") { backStackEntry ->
            val partnerId = backStackEntry.arguments?.getString("partnerId")
            MapScreen(viewModel(), navController, focusPartnerId = partnerId)
        }

        composable("leaderboard") { LeaderboardPage(navController) }
        composable("activities") { ActivityListPage(navController) }
        composable("details/{partnerId}") { backStackEntry ->
            val partnerId = backStackEntry.arguments?.getString("partnerId") ?: ""
            DetailsPage(partnerId, navController)
        }
    }
}