package coredevices.ring.endpoints

import kotlin.test.*

class CustomEndpointsTest {
    @Test fun removedDictionaryDoesNotResetSavedEndpointCredentials() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val settings = json.decodeFromString<EndpointSettings>("""{"speech":{"model":"whisper-large-v3","apiKey":"saved-key","vocabulary":"old hints"}}""")
        assertEquals("whisper-large-v3", settings.speech.model)
        assertEquals("saved-key", settings.speech.apiKey)
        assertFalse(json.encodeToString(EndpointSettings.serializer(), settings).contains("vocabulary"))
    }
    @Test fun cleanupRequestSeparatesDataAndNeverSuppliesTools() {
        val settings = EndpointSettings(cleanup = TranscriptCleanupSettings(true, "Celonis"))
        val request = kotlinx.serialization.json.Json.parseToJsonElement(TranscriptCleanup.request("Note salon is", settings)) as kotlinx.serialization.json.JsonObject
        assertFalse(request.containsKey("tools"))
        assertEquals(kotlinx.serialization.json.JsonPrimitive(settings.llm.model), request["model"])
        val messages = request["messages"] as kotlinx.serialization.json.JsonArray
        assertEquals(2, messages.size)
        assertTrue(messages[1].toString().contains("Celonis"))
        assertTrue(messages[1].toString().contains("Note salon is"))
    }
    @Test fun cleanupAcceptsStructuredTextAndRejectsBrokenResponses() {
        assertEquals("Note Celonis", TranscriptCleanup.parse("""{"choices":[{"finish_reason":"stop","message":{"content":"{\"text\":\"Note Celonis\"}"}}]}"""))
        for (response in listOf("{}", """{"choices":[{"finish_reason":"length","message":{"content":"{}"}}]}""",
            """{"choices":[{"message":{"content":"{\"text\":\"\"}"}}]}""")) {
            assertFails { TranscriptCleanup.parse(response) }
        }
    }
    @Test fun defaultsMatchRequestedModels() {
        val settings = EndpointSettings()
        assertEquals("qwen/qwen3.8-27b", settings.llm.model)
        assertEquals("whisper-large-v3-turbo", settings.speech.model)
        assertEquals("https://api.groq.com/openai/v1/chat/completions", settings.llm.url("chat/completions"))
        assertFalse(settings.llm.enabled)
        assertEquals("", settings.llm.apiKey)
    }
    @Test fun rejectEmbeddedCredentialsAndQuery() {
        for (url in listOf("file:///tmp/model", "https://user:secret@example.com/v1", "https://example.com/v1?key=secret", "https://")) {
            assertFailsWith<IllegalArgumentException> { EndpointProfile(true, url, "model").validate() }
        }
    }
    @Test fun toolCallsAndModelLabelSurviveResponseParsing() {
        val result = parseAssistant("""{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"index_tool_0","arguments":"{\"text\":\"Buy oranges\"}"}}]}}]}""", "test-model")
        assertEquals("custom:test-model", result.language_model_used)
        assertEquals("call_1", result.tool_calls!!.single().id)
        assertEquals("{\"text\":\"Buy oranges\"}", result.tool_calls!!.single().function!!.arguments)
    }
    @Test fun malformedAndEmptyResponsesFail() {
        assertFails { parseAssistant("{}", "model") }
        assertFails { parseAssistant("""{"choices":[{"message":{"content":null}}]}""", "model") }
    }
    @Test fun keysAreNotIncludedInProfileDiagnostics() {
        assertFalse(EndpointProfile(apiKey = "secret-key").toString().contains("secret-key"))
    }
}
