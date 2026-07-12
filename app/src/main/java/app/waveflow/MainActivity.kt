package app.waveflow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.waveflow.ui.library.LibraryScreen
import app.waveflow.ui.library.LibraryViewModel
import app.waveflow.ui.theme.WaveFlowTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaveFlowTheme {
                WaveFlowRoot()
            }
        }
    }
}

/** Permission de lecture des fichiers audio, selon la version d'Android. */
private val audioPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaveFlowRoot() {
    val viewModel: LibraryViewModel = viewModel()
    val context = androidx.compose.ui.platform.LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (granted) {
            viewModel.connect()
            viewModel.loadSongs()
        }
    }

    // Sur permission déjà accordée, on connecte + charge une seule fois.
    androidx.compose.runtime.LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.connect()
            viewModel.loadSongs()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("WaveFlow") }) },
    ) { innerPadding ->
        if (hasPermission) {
            LibraryScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            PermissionPrompt(
                onRequest = { permissionLauncher.launch(audioPermission) },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("WaveFlow a besoin d'accéder à vos fichiers audio pour lister votre musique.")
        Button(onClick = onRequest) {
            Text("Autoriser l'accès")
        }
    }
}
