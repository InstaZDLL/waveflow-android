package app.waveflow.ui.permission

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Permission de lecture des fichiers audio, selon la version d'Android.
 *
 * Depuis Android 13, lire les fichiers audio créés par d'autres applications
 * exige `READ_MEDIA_AUDIO` ; avant, c'était `READ_EXTERNAL_STORAGE`.
 */
val AUDIO_PERMISSION: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private enum class AudioPermissionStatus {
    /** Accordée : la bibliothèque est lisible. */
    Granted,

    /** Refusée, mais on peut encore afficher la boîte de dialogue système. */
    Denied,

    /** Refusée définitivement : seul un passage par les paramètres débloque. */
    PermanentlyDenied,
}

/**
 * Affiche [content] uniquement lorsque la permission audio est accordée, et
 * gère sinon tout le parcours : demande initiale, refus simple, refus
 * définitif (avec renvoi vers les paramètres) et retour depuis ceux-ci.
 */
@Composable
fun AudioPermissionGate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }

    var status by remember {
        mutableStateOf(
            if (context.hasAudioPermission()) {
                AudioPermissionStatus.Granted
            } else {
                AudioPermissionStatus.Denied
            },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        status = when {
            granted -> AudioPermissionStatus.Granted
            // Plus de rationale possible après un refus = case « ne plus demander ».
            activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, AUDIO_PERMISSION) ->
                AudioPermissionStatus.PermanentlyDenied
            else -> AudioPermissionStatus.Denied
        }
    }

    // L'utilisateur peut accorder la permission depuis les paramètres système :
    // on revalide à chaque retour au premier plan.
    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && context.hasAudioPermission()) {
                status = AudioPermissionStatus.Granted
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    when (status) {
        AudioPermissionStatus.Granted -> content()

        AudioPermissionStatus.Denied -> AudioPermissionPrompt(
            message = "WaveFlow a besoin d'accéder à vos fichiers audio pour lister votre musique.",
            actionLabel = "Autoriser l'accès",
            onAction = { permissionLauncher.launch(AUDIO_PERMISSION) },
            modifier = modifier,
        )

        AudioPermissionStatus.PermanentlyDenied -> AudioPermissionPrompt(
            message = "L'accès aux fichiers audio est bloqué. Activez l'autorisation " +
                "« Musique et audio » dans les paramètres pour afficher votre bibliothèque.",
            actionLabel = "Ouvrir les paramètres",
            onAction = { context.openAppSettings() },
            modifier = modifier,
        )
    }
}

@Composable
private fun AudioPermissionPrompt(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

private fun Context.hasAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, AUDIO_PERMISSION) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )
    startActivity(intent)
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
