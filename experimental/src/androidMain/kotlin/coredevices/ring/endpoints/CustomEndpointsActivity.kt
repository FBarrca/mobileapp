package coredevices.ring.endpoints

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.*
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

@Composable actual fun CustomEndpointsSettingsEntry() {
    val context = LocalContext.current
    ListItem(headlineContent = { Text("Custom AI endpoints") },
        supportingContent = { Text("Groq or your own LLM and speech API") },
        modifier = Modifier.clickable { context.startActivity(Intent(context, CustomEndpointsActivity::class.java)) })
}

class CustomEndpointsActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var rootLayout: LinearLayout
    private lateinit var status: TextView
    private lateinit var llm: Fields
    private lateinit var speech: Fields
    private data class Fields(val enabled: Switch, val url: EditText, val model: EditText, val key: EditText) {
        fun value() = EndpointProfile(enabled.isChecked, url.text.toString().trim(), model.text.toString().trim(), key.text.toString().trim())
        fun preset(modelName: String) { url.setText("https://api.groq.com/openai/v1"); model.setText(modelName) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        rootLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 40, 32, 40) }
        label("Custom AI endpoints", 25f)
        label("Use Groq or an OpenAI-compatible API. Enabled endpoints receive your recording or text directly. No Pebble account is required. Disabled endpoints use the existing Index settings.")
        val saved = try { CustomEndpoints.read() } catch (e: Exception) { label("Could not read saved settings. Re-enter and save to replace them."); EndpointSettings() }
        llm = fields("LLM", saved.llm)
        button("Use Groq LLM preset") { llm.preset("qwen/qwen3.8-27b") }
        button("Test LLM") { probe {
            val p = llm.value().copy(enabled = true); p.validate()
            parseAssistant(CustomEndpoints.chat(p, buildJsonObject {
                put("model", p.model); put("stream", false)
                put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", "Reply with OK.") }) })
            }.toString()), p.model)
            "LLM endpoint responded successfully."
        } }
        speech = fields("Speech", saved.speech)
        button("Use Groq speech preset") { speech.preset("whisper-large-v3-turbo") }
        button("Copy LLM API key to speech") { speech.key.setText(llm.key.text.toString()) }
        button("Test speech") { probe {
            val p = speech.value().copy(enabled = true); p.validate()
            val wav = java.nio.ByteBuffer.allocate(44 + 16000).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + 16000); put("WAVEfmt ".toByteArray()); putInt(16)
                putShort(1); putShort(1); putInt(16000); putInt(32000); putShort(2); putShort(16)
                put("data".toByteArray()); putInt(16000)
            }.array()
            val result = Json.parseToJsonElement(CustomEndpoints.speech(p, wav, null)).jsonObject
            check(result["text"] is JsonPrimitive) { "Response has no transcription text" }
            "Speech endpoint accepted a short silent WAV test."
        } }
        label("Enter a base URL such as https://api.groq.com/openai/v1. The app adds /chat/completions or /audio/transcriptions. API keys are encrypted on this device and excluded from backups. HTTP sends data without encryption; use it only for a trusted local server.")
        status = label("")
        button("Save endpoints") {
            runCatching { CustomEndpoints.save(CustomEndpoints.read().copy(llm = llm.value(), speech = speech.value())) }
                .onSuccess { status.text = "Saved. New recordings use these settings. Re-run Prepare in Plus Key setup to check the selected routes." }
                .onFailure { status.text = it.message ?: "Could not save settings" }
        }
        button("Back") { finish() }
        setContentView(ScrollView(this).apply { addView(rootLayout) })
    }
    private fun label(value: String, size: Float = 16f) = TextView(this).apply {
        text = value; textSize = size; setPadding(0, 16, 0, 16); rootLayout.addView(this)
    }
    private fun fields(name: String, profile: EndpointProfile): Fields {
        label(name, 21f)
        val enabled = Switch(this).apply { text = "Use custom $name"; isChecked = profile.enabled; contentDescription = text; rootLayout.addView(this) }
        fun field(title: String, value: String, secret: Boolean = false) = EditText(this).apply {
            hint = title; contentDescription = "$name $title"; setSingleLine(true)
            inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
            isSaveEnabled = false; setText(value); rootLayout.addView(this)
        }
        val url = field("Base URL", profile.baseUrl)
        val model = field("Model", profile.model)
        val key = field("API key (optional)", profile.apiKey, true)
        return Fields(enabled, url, model, key)
    }
    private fun button(title: String, action: () -> Unit) { rootLayout.addView(Button(this).apply { text = title; setOnClickListener { action() } }) }
    private fun probe(block: suspend () -> String) {
        status.text = "Testing…"
        scope.launch { try { status.text = block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { status.text = e.message ?: "Endpoint test failed" } }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
