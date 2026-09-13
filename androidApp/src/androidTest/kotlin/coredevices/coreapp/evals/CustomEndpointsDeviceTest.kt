package coredevices.coreapp.evals

import androidx.test.platform.app.InstrumentationRegistry
import coredevices.ring.endpoints.*
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.indexai.database.dao.ConversationMessageDao
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.RecordingEntryStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.io.buffered
import org.junit.Test
import org.junit.Assert.*
import org.koin.mp.KoinPlatform
import java.util.UUID

/** Requires the loopback fixture server on port 8766 and adb reverse tcp:8766 tcp:8766. */
class CustomEndpointsDeviceTest {
    @Test fun liveConfiguredCleanup() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveEndpoints") == "true")
        val settings = CustomEndpoints.read().copy(cleanup = TranscriptCleanupSettings(true, "Celonis"))
        val result = TranscriptCleanup.clean("Make a note: review the Celonis project tomorrow.", settings)
        assertTrue(result.contains("Celonis", ignoreCase = true))
        assertTrue(result.contains("tomorrow", ignoreCase = true))
    }
    @Test fun selectorsPersistIndependentlyAndPreserveCredentials() {
        val previous = CustomEndpoints.read()
        try {
            EndpointSelection.selectLlm(false)
            assertFalse(CustomEndpoints.read().llm.enabled)
            assertEquals(previous.speech, CustomEndpoints.read().speech)
            assertEquals(previous.llm.apiKey, CustomEndpoints.read().llm.apiKey)
            assertFalse(EndpointSelection.state.value.llm.enabled)
            EndpointSelection.selectLlm(true)
            assertTrue(CustomEndpoints.read().llm.enabled)
            assertTrue(EndpointSelection.state.value.llm.enabled)
            EndpointSelection.selectSpeech(false)
            assertFalse(CustomEndpoints.read().speech.enabled)
            assertTrue(CustomEndpoints.read().llm.enabled)
            assertEquals(previous.speech.apiKey, CustomEndpoints.read().speech.apiKey)
            EndpointSelection.selectSpeech(true)
            assertTrue(EndpointSelection.state.value.speech.enabled)
        } finally { CustomEndpoints.save(previous) }
    }
    @Test fun liveConfiguredRecording() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveEndpoints") == "true")
        val settings = CustomEndpoints.read()
        assertTrue(settings.llm.enabled && settings.speech.enabled)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = java.io.File(context.cacheDir, "endpoint-live-test.pcm").readBytes()
        val koin = KoinPlatform.getKoin()
        koin.get<McpSandboxRepository>().seedDatabase()
        val fileId = "endpoint-live-${UUID.randomUUID()}"
        val storage = koin.get<RecordingStorage>()
        storage.openOriginalRecordingSink(fileId, 16000, "audio/raw").buffered().use { it.write(audio) }
        storage.openRecordingSink(fileId, 16000, "audio/raw").buffered().use { it.write(audio) }
        koin.get<RecordingProcessingQueue>().queueLocalAudioProcessing(fileId, "long")
        val entry = withTimeout(90_000) {
            koin.get<RecordingEntryDao>().getAllEntriesFlow().first { rows ->
                rows.any { it.fileName == fileId && it.status == RecordingEntryStatus.completed }
            }.first { it.fileName == fileId }
        }
        assertTrue(entry.transcription.orEmpty().contains("integration", ignoreCase = true))
        assertEquals("custom:${settings.speech.model}", entry.transcribedUsingModel)
        val conversation = koin.get<ConversationMessageDao>().getMessagesForRecording(entry.recordingId).first()
        assertTrue(conversation.any { it.document.language_model_used == "custom:${settings.llm.model}" })
        assertTrue(conversation.any { it.document.role == MessageRole.tool && !it.document.is_forced_tool &&
            it.document.semantic_result is coredevices.mcp.data.SemanticResult.ListItemCreation })
    }

    @Test fun customEndpointsProcessRecordingAndPersistEncryptedSettings() = runBlocking {
        val previous = CustomEndpoints.read()
        val settings = EndpointSettings(
            EndpointProfile(true, "http://127.0.0.1:8766/v1", "qwen/qwen3.8-27b", "fixture-key"),
            EndpointProfile(true, "http://127.0.0.1:8766/v1", "whisper-large-v3-turbo", "fixture-key"),
            cleanup = TranscriptCleanupSettings(true, "Celonis"))
        try {
            CustomEndpoints.save(settings)
            assertEquals(settings, CustomEndpoints.read())
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val stored = java.io.File(context.noBackupFilesDir, "custom-endpoints.bin").readBytes().decodeToString()
            assertFalse(stored.contains("fixture-key"))
            val koin = KoinPlatform.getKoin()
            koin.get<McpSandboxRepository>().seedDatabase()
            val storage = koin.get<RecordingStorage>()
            val fileId = "endpoint-test-${UUID.randomUUID()}"
            val audio = ByteArray(32000)
            storage.openOriginalRecordingSink(fileId, 16000, "audio/raw").buffered().use { it.write(audio) }
            storage.openRecordingSink(fileId, 16000, "audio/raw").buffered().use { it.write(audio) }
            koin.get<RecordingProcessingQueue>().queueLocalAudioProcessing(fileId, "long")
            val entry = withTimeout(60_000) {
                koin.get<RecordingEntryDao>().getAllEntriesFlow().first { rows ->
                    rows.any { it.fileName == fileId && it.status == RecordingEntryStatus.completed }
                }.first { it.fileName == fileId }
            }
            assertEquals("Custom endpoint Celonis integration test", entry.transcription)
            assertEquals("custom:whisper-large-v3-turbo", entry.transcribedUsingModel)
            val conversation = koin.get<ConversationMessageDao>().getMessagesForRecording(entry.recordingId).first()
            assertTrue(conversation.any { it.document.role == MessageRole.assistant && it.document.language_model_used == "custom:qwen/qwen3.8-27b" })
            assertTrue(conversation.any { it.document.role == MessageRole.tool && !it.document.is_forced_tool &&
                it.document.semantic_result is coredevices.mcp.data.SemanticResult.ListItemCreation })
        } finally { CustomEndpoints.save(previous) }
    }

    @Test fun rejectedCredentialsSurfaceWithoutLeakingKey() = runBlocking {
        try {
            CustomEndpoints.chat(EndpointProfile(true, "http://127.0.0.1:8766/v1", "test", "rejected-secret"), "{}")
            fail("Expected authentication rejection")
        } catch (e: Exception) {
            assertTrue(e.message.orEmpty().contains("401"))
            assertFalse(e.message.orEmpty().contains("rejected-secret"))
        }
    }
}
