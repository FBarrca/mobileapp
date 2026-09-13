package coredevices.ring.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import coredevices.ring.endpoints.EndpointProfile
import coredevices.ring.endpoints.CustomEndpoints
import coredevices.ring.ui.theme.IndexTheme

@Composable
internal fun CustomEndpointChoice(profile: EndpointProfile, onSelect: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = IndexTheme.colors.sheetSurface,
            headlineColor = IndexTheme.colors.onSurface, supportingColor = IndexTheme.colors.onSurfaceVariant),
        headlineContent = { Text("Custom endpoint") },
        supportingContent = { Text("${profile.model}\nUses your configured API and key; no Pebble login") },
        leadingContent = { RadioButton(selected = profile.enabled, onClick = null) },
        modifier = Modifier.clickable {
            runCatching(onSelect).onFailure { error = it.message ?: "Check your custom endpoint settings" }
        },
    )
    error?.let { Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
}

/** Drafts are memory-only; keys are masked and never stored in saved instance state. */
@Composable
internal fun CustomEndpointEditor(profile: EndpointProfile, speech: Boolean) {
    var url by remember(profile) { mutableStateOf(profile.baseUrl) }
    var model by remember(profile) { mutableStateOf(profile.model) }
    var key by remember(profile) { mutableStateOf(profile.apiKey) }
    var status by remember { mutableStateOf<String?>(null) }
    val colors = IndexTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.onSurface, unfocusedTextColor = colors.onSurface,
            focusedLabelColor = colors.primary, unfocusedLabelColor = colors.onSurfaceVariant,
            cursorColor = colors.primary, focusedBorderColor = colors.primary, unfocusedBorderColor = colors.outline,
        )
        OutlinedTextField(value = url, onValueChange = { url = it; status = null }, label = { Text("Endpoint base URL") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(value = model, onValueChange = { model = it; status = null }, label = { Text("Model") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
        OutlinedTextField(value = key, onValueChange = { key = it; status = null }, label = { Text("API key") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Text(if (speech) "Adds /audio/transcriptions to the base URL." else "Adds /chat/completions to the base URL.", color = colors.onSurfaceVariant)
        Button(onClick = {
            runCatching {
                val current = CustomEndpoints.read()
                val edited = profile.copy(enabled = true, baseUrl = url.trim(), model = model.trim(), apiKey = key.trim())
                CustomEndpoints.save(if (speech) current.copy(speech = edited) else current.copy(llm = edited))
            }.onSuccess { status = "Saved. Custom endpoint is active." }
                .onFailure { status = it.message ?: "Could not save endpoint" }
        }) { Text("Save custom endpoint") }
        status?.let { Text(it, color = colors.onSurface) }
    }
}
