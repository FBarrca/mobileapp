package coredevices.ring.endpoints

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Serializable
data class EndpointProfile(val enabled: Boolean = false, val baseUrl: String = "https://api.groq.com/openai/v1",
    val model: String = "", val apiKey: String = "") {
    fun validate() {
        if (!enabled) return
        require(baseUrl.startsWith("https://") || baseUrl.startsWith("http://")) { "Use an http:// or https:// base URL" }
        require(!baseUrl.contains('?') && !baseUrl.contains('#') && !baseUrl.substringAfter("://").substringBefore('/').contains('@')) { "Base URL cannot contain credentials, query or fragment" }
        require(baseUrl.substringAfter("://").substringBefore('/').isNotBlank()) { "Endpoint host is required" }
        require(model.isNotBlank()) { "Model name is required" }
        require(!apiKey.contains('\n') && !apiKey.contains('\r')) { "Invalid API key" }
    }
    fun url(path: String) = baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    override fun toString() = "EndpointProfile(enabled=$enabled, model=$model, apiKey=[redacted])"
}

@Serializable
data class TranscriptCleanupSettings(val enabled: Boolean = false, val customWords: String = "")

@Serializable
data class EndpointSettings(
    val llm: EndpointProfile = EndpointProfile(model = "qwen/qwen3.8-27b"),
    val speech: EndpointProfile = EndpointProfile(model = "whisper-large-v3-turbo"),
    val cleanup: TranscriptCleanupSettings = TranscriptCleanupSettings(),
)

expect object CustomEndpoints {
    fun read(): EndpointSettings
    fun save(settings: EndpointSettings)
    suspend fun chat(profile: EndpointProfile, json: String): String
    suspend fun speech(profile: EndpointProfile, wav: ByteArray, language: String?): String
}

@Composable expect fun CustomEndpointsSettingsEntry()

/** The native endpoint form and Compose mode selectors share one persisted selection. */
object EndpointSelection {
    private val mutable by lazy { MutableStateFlow(CustomEndpoints.read()) }
    val state get() = mutable.asStateFlow()
    fun publish(settings: EndpointSettings) { mutable.value = settings }
    fun selectLlm(enabled: Boolean) {
        val current = CustomEndpoints.read()
        CustomEndpoints.save(current.copy(llm = current.llm.copy(enabled = enabled)))
    }
    fun selectSpeech(enabled: Boolean) {
        val current = CustomEndpoints.read()
        CustomEndpoints.save(current.copy(speech = current.speech.copy(enabled = enabled)))
    }
}
