package com.thesmallmarket.arrumacomigo

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thesmallmarket.arrumacomigo.auth.AuthState
import com.thesmallmarket.arrumacomigo.ui.auth.HouseholdSetupScreen
import com.thesmallmarket.arrumacomigo.ui.auth.LoginScreen
import com.thesmallmarket.arrumacomigo.ui.navigation.ArrumaComigoApp
import com.thesmallmarket.arrumacomigo.ui.theme.ArrumaComigoTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as HouseholdApplication
        setContent {
            ArrumaComigoTheme {
                RequestNotificationPermission()
                val windowSize = calculateWindowSizeClass(this)
                val authState by app.container.authManager.state.collectAsStateWithLifecycle()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    when (authState) {
                        is AuthState.SignedOut -> LoginScreen(app.container.authManager)
                        is AuthState.NeedsHousehold -> HouseholdSetupScreen(app.container.authManager)
                        is AuthState.Ready -> {
                            LaunchedEffect(authState) { app.startDataFlow() }
                            ArrumaComigoApp(widthSizeClass = windowSize.widthSizeClass)
                        }
                    }
                }
            }
        }
    }
}

/** Pede a permissão de notificações no Android 13+ uma vez ao abrir o app. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )
    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
