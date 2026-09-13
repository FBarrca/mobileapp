package coredevices.ring.endpoints

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.koin.mp.KoinPlatform
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual object CustomEndpoints {
    private const val ALIAS = "index_custom_endpoints_v1"
    private val context: Context get() = KoinPlatform.getKoin().get()
    private val file get() = AtomicFile(File(context.noBackupFilesDir, "custom-endpoints.bin"))
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).callTimeout(70, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized actual fun read(): EndpointSettings {
        if (!file.baseFile.exists()) return EndpointSettings()
        val bytes = file.readFully()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return json.decodeFromString(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString())
    }
    @Synchronized actual fun save(settings: EndpointSettings) {
        settings.llm.validate(); settings.speech.validate()
        listOf(settings.llm, settings.speech).filter { it.enabled }.forEach { it.baseUrl.toHttpUrl() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.iv + cipher.doFinal(json.encodeToString(settings).toByteArray())
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output); EndpointSelection.publish(settings) }
        catch (e: Exception) { file.failWrite(output); throw e }
    }
    private suspend fun execute(profile: EndpointProfile, path: String, body: RequestBody): String = withContext(Dispatchers.IO) {
        profile.validate()
        val url = profile.url(path).toHttpUrl()
        require(url.username.isEmpty() && url.password.isEmpty()) { "Use the API key field for credentials" }
        val request = Request.Builder().url(url).post(body).header("Accept", "application/json")
        if (profile.apiKey.isNotBlank()) request.header("Authorization", "Bearer ${profile.apiKey}")
        val call = client.newCall(request.build())
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("Cannot reach custom endpoint. Check address and connection."))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            check(response.isSuccessful) { when (response.code) {
                                401, 403 -> "Endpoint rejected authentication (${response.code}). Check API key and model access."
                                404 -> "Endpoint or model not found (404). Check base URL and model."
                                429 -> "Endpoint rate or quota limit reached (429). Try again later."
                                else -> "Endpoint returned HTTP ${response.code}. Check model and API compatibility."
                            } }
                            val source = requireNotNull(response.body).source()
                            val buffer = okio.Buffer()
                            while (true) {
                                val count = source.read(buffer, 8192)
                                if (count == -1L) break
                                check(buffer.size <= 2_000_000) { "Endpoint response is too large" }
                            }
                            if (continuation.isActive) continuation.resume(buffer.readUtf8())
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                }
            })
        }
    }
    actual suspend fun chat(profile: EndpointProfile, json: String) =
        execute(profile, "chat/completions", json.toRequestBody("application/json".toMediaType()))
    actual suspend fun speech(profile: EndpointProfile, wav: ByteArray, language: String?): String {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", profile.model).addFormDataPart("response_format", "json")
            .addFormDataPart("file", "recording.wav", wav.toRequestBody("audio/wav".toMediaType()))
        language?.let { body.addFormDataPart("language", it) }
        return execute(profile, "audio/transcriptions", body.build())
    }
}
