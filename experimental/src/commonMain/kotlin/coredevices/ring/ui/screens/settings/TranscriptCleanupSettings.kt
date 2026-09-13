package coredevices.ring.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coredevices.ring.endpoints.*
import coredevices.ring.ui.theme.IndexTheme

@Composable
internal fun TranscriptCleanupSettings() {
    val settings by EndpointSelection.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var enabled by remember(settings.cleanup) { mutableStateOf(settings.cleanup.enabled) }
    var words by remember(settings.cleanup) { mutableStateOf(settings.cleanup.customWords) }
    var status by remember { mutableStateOf<String?>(null) }
    val colors = IndexTheme.colors
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Text(if (expanded) "Hide transcription cleanup" else "Transcription cleanup")
    }
    if (expanded) Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Clean up with LLM", modifier = Modifier.weight(1f), color = colors.onSurface)
            Switch(checked = enabled, onCheckedChange = { enabled = it; status = null })
        }
        Text("After custom speech recognition, a separate request to ${settings.llm.model} fixes wording before Index processes it. Uses your configured LLM endpoint and key. Adds a request and some delay.", color = colors.onSurfaceVariant)
        OutlinedTextField(value = words, onValueChange = { words = it; status = null },
            label = { Text("Custom words for cleanup") }, placeholder = { Text("Names, products, specialist terms; one per line") },
            modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.onSurface, unfocusedTextColor = colors.onSurface,
                focusedLabelColor = colors.primary, unfocusedLabelColor = colors.onSurfaceVariant,
                focusedBorderColor = colors.primary, unfocusedBorderColor = colors.outline, cursorColor = colors.primary))
        Text("Use intended spellings to help resolve misheard words. The LLM is asked to preserve meaning; review important details. If cleanup fails, processing stops so you can retry or disable it.", color = colors.onSurfaceVariant)
        Button(onClick = {
            runCatching {
                val current = CustomEndpoints.read()
                if (enabled) current.llm.copy(enabled = true).validate()
                CustomEndpoints.save(current.copy(cleanup = coredevices.ring.endpoints.TranscriptCleanupSettings(enabled, words.trim())))
            }.onSuccess { status = "Cleanup settings saved." }
                .onFailure { status = "Could not save. Check your LLM endpoint settings." }
        }) { Text("Save cleanup settings") }
        status?.let { Text(it, color = colors.onSurface) }
    }
}
