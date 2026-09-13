package coredevices.ring.pluskey

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.endpoints.EndpointSelection
import coredevices.ring.ui.screens.settings.CustomEndpointEditor
import coredevices.util.CoreConfigHolder
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun PlusKeySettingsEntry() {
    var open by remember { mutableStateOf(false) }
    val colors = IndexTheme.colors
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Oneplus integration")
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.sheetSurface) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
                Text("Oneplus integration", modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
                coredevices.ring.pluskey.setup.WirelessKeySetup()
                PlusKeyControls()
                HorizontalDivider()
                val endpoints by EndpointSelection.state.collectAsState()
                var endpointError by remember { mutableStateOf<String?>(null) }
                endpointError?.let { Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                var showLlm by remember { mutableStateOf(false) }
                var showSpeech by remember { mutableStateOf(false) }
                TextButton(onClick = { showLlm = !showLlm }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showLlm) "Hide LLM endpoint" else "Configure LLM endpoint")
                }
                if (showLlm) {
                    Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Use custom LLM", modifier = Modifier.weight(1f), color = colors.onSurface)
                        Switch(checked = endpoints.llm.enabled, onCheckedChange = { endpointError = null; runCatching { EndpointSelection.selectLlm(it) }.onFailure { endpointError = it.message ?: "Could not update endpoint" } })
                    }
                    CustomEndpointEditor(endpoints.llm, speech = false)
                }
                TextButton(onClick = { showSpeech = !showSpeech }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showSpeech) "Hide speech endpoint" else "Configure speech endpoint")
                }
                if (showSpeech) {
                    Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Use custom speech", modifier = Modifier.weight(1f), color = colors.onSurface)
                        Switch(checked = endpoints.speech.enabled, onCheckedChange = { endpointError = null; runCatching { EndpointSelection.selectSpeech(it) }.onFailure { endpointError = it.message ?: "Could not update endpoint" } })
                    }
                    CustomEndpointEditor(endpoints.speech, speech = true)
                }
                coredevices.ring.ui.screens.settings.TranscriptCleanupSettings()
            }
        }
    }
}

@Composable
private fun PlusKeyControls() {
    val context = LocalContext.current
    val config = koinInject<CoreConfigHolder>()
    val state by IndexPlusKeyService.status.collectAsState()
    val setup by IndexPlusKeySetup.state.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    val enableKey = {
        error = null
        if (context.checkSelfPermission(Manifest.permission.READ_LOGS) != PackageManager.PERMISSION_GRANTED) {
            error = "Key permission is missing. Open Key setup above to grant it with wireless debugging."
        } else {
            runCatching {
                config.update(config.config.value.copy(enableIndex = true))
                context.startForegroundService(Intent(context, IndexPlusKeyService::class.java))
            }.onFailure { error = "Could not enable the key: ${it.message}" }
        }
        Unit
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enableKey() else error = "Microphone permission is required to record with the key."
    }
    val colors = IndexTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("OnePlus Plus Key", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Text("Hold to record with your phone; release to process in Index.", color = colors.onSurfaceVariant)
        Text(state.message, color = colors.onSurface)
        if (!state.armed) Text(setup.message, color = colors.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!state.armed) {
                OutlinedButton(enabled = !setup.busy, onClick = { error = null; IndexPlusKeySetup.prepareLocal() }) {
                    Text(if (setup.busy) "Preparing..." else "Prepare Index")
                }
            }
            Button(enabled = state.armed || (setup.ready && !setup.busy), onClick = {
                if (state.armed) context.stopService(Intent(context, IndexPlusKeyService::class.java))
                else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    microphone.launch(Manifest.permission.RECORD_AUDIO)
                } else enableKey()
            }) { Text(if (state.armed) "Disable key" else "Enable key") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    }
}
