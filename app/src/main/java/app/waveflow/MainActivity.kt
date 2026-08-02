package app.waveflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import app.waveflow.ui.library.LibraryScreen
import app.waveflow.ui.library.LibraryViewModel
import app.waveflow.ui.permission.AudioPermissionGate
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaveFlowRoot() {
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("WaveFlow") }) },
    ) { innerPadding ->
        AudioPermissionGate(modifier = Modifier.padding(innerPadding)) {
            // Ne démarre le scan et la connexion au service qu'une fois la
            // permission acquise.
            LaunchedEffect(Unit) { viewModel.onAudioAccessGranted() }

            LibraryScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
