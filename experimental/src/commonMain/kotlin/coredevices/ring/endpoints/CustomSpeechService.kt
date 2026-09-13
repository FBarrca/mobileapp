package coredevices.ring.endpoints

import coredevices.util.AudioEncoding
import coredevices.util.writeWavHeader
import coredevices.util.transcription.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.json.*
import kotlin.time.Duration

class CustomSpeechService(private val profile: EndpointProfile) : TranscriptionService {
    override val onInitialized = Channel<Boolean>(Channel.CONFLATED)
    override suspend fun isAvailable() = profile.enabled
    override suspend fun transcribe(audioStreamFrames: Flow<ByteArray>?, sampleRate: Int, language: STTLanguage,
        conversationContext: STTConversationContext?, dictionaryContext: List<String>?, contentContext: String?,
        encoding: AudioEncoding, initialTimeout: Duration?, totalTimeout: Duration?): Flow<TranscriptionSessionStatus> = flow {
        val model = "custom:${profile.model}"
        try {
            profile.validate()
            require(encoding == AudioEncoding.PCM_16BIT) { "Custom speech requires PCM16 audio" }
            requireNotNull(audioStreamFrames) { "An audio recording is required" }
            emit(TranscriptionSessionStatus.Open)
            val pcm = Buffer()
            audioStreamFrames.collect { chunk ->
                require(pcm.size + chunk.size <= 24_000_000) { "Recording exceeds the 24 MB upload limit" }
                pcm.write(chunk)
            }
            if (pcm.size == 0L) throw TranscriptionException.NoSpeechDetected("empty_audio", model)
            val bytes = pcm.readByteArray()
            val wav = Buffer().apply { writeWavHeader(sampleRate, bytes.size); write(bytes) }.readByteArray()
            val lang = (language as? STTLanguage.Specific)?.languageCodes?.singleOrNull()
            val response = CustomEndpoints.speech(profile, wav, lang)
            val text = Json.parseToJsonElement(response).jsonObject["text"]?.jsonPrimitive?.contentOrNull
                ?: error("Endpoint response has no transcription text")
            if (text.isBlank()) throw TranscriptionException.NoSpeechDetected("empty_transcript", model)
            val settings = CustomEndpoints.read()
            val cleaned = if (settings.cleanup.enabled) TranscriptCleanup.clean(text, settings) else text
            emit(TranscriptionSessionStatus.Transcription(cleaned, model))
        } catch (e: CancellationException) { throw e }
        catch (e: TranscriptionException) { throw e }
        catch (e: Exception) { throw TranscriptionException.TranscriptionServiceError(e.message ?: "Custom speech request failed", e, model) }
    }
}
