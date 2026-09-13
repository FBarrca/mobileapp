package coredevices.ring.endpoints

import coredevices.indexai.agent.IterativeAgent
import coredevices.indexai.agent.AgentToolCall
import coredevices.indexai.data.entity.*
import coredevices.mcp.SessionContext
import coredevices.mcp.client.McpSession
import coredevices.mcp.client.McpSessionTool
import coredevices.ring.agent.AgentNetworkException
import coredevices.ring.agent.currentTimeContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import kotlin.time.Clock

class CustomEndpointAgent(private val profile: EndpointProfile, history: List<ConversationMessageDocument>) : IterativeAgent(history) {
    override val label = "Custom endpoint: ${profile.model}"
    private val names = mutableMapOf<String, Pair<String, String>>()

    override suspend fun runInference(input: String, history: List<ConversationMessageDocument>, tools: List<McpSessionTool>,
        mcpSession: McpSession, sessionContext: SessionContext, includePromptsFromMcps: Map<String, Set<String>>): ConversationMessageDocument {
        profile.validate()
        val definitions = buildJsonArray {
            tools.forEachIndexed { index, tool ->
                val name = "index_tool_$index"
                names[name] = tool.integrationName to tool.tool.definition.name
                add(buildJsonObject {
                    put("type", "function")
                    put("function", buildJsonObject {
                        put("name", name)
                        put("description", "${tool.integrationName}.${tool.tool.definition.name}: ${tool.tool.definition.description.orEmpty()}")
                        put("parameters", JsonObject(Json.encodeToJsonElement(tool.tool.definition.inputSchema).jsonObject +
                            ("type" to JsonPrimitive("object"))))
                    })
                })
            }
        }
        val request = buildJsonObject {
            put("model", profile.model)
            put("stream", false)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", "You are Index, a personal note and reminder assistant. Use the available tools to fulfill the user's request. Preserve their language. Never claim an action succeeded before its tool succeeds. Ask for clarification when necessary. " + currentTimeContext(sessionContext.timeBase ?: Clock.System.now()))
                })
                history.forEach { message -> add(buildJsonObject {
                    put("role", message.role.name)
                    put("content", message.content?.let(::JsonPrimitive) ?: JsonNull)
                    if (!message.tool_calls.isNullOrEmpty()) put("tool_calls", Json.encodeToJsonElement(message.tool_calls))
                    message.tool_call_id?.let { put("tool_call_id", it) }
                }) }
            })
            if (definitions.isNotEmpty()) { put("tools", definitions); put("tool_choice", "auto") }
        }
        try {
            return parseAssistant(CustomEndpoints.chat(profile, request.toString()), profile.model).also { result ->
                result.tool_calls.orEmpty().forEach { call ->
                    val function = requireNotNull(call.function)
                    require(names.containsKey(function.name)) { "Endpoint returned an unknown tool" }
                    Json.parseToJsonElement(function.arguments).jsonObject
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw AgentNetworkException(e.message ?: "Custom LLM request failed", e) }
    }

    override fun decodeToolCalls(assistantMessage: ConversationMessageDocument): List<AgentToolCall> =
        assistantMessage.tool_calls.orEmpty().map { call ->
            val function = requireNotNull(call.function) { "Missing function" }
            val target = requireNotNull(names[function.name]) { "Endpoint returned an unknown tool" }
            AgentToolCall(call.id, target.first, target.second, Json.parseToJsonElement(function.arguments).jsonObject)
        }
}

internal fun parseAssistant(response: String, model: String): ConversationMessageDocument {
    val root = Json.parseToJsonElement(response).jsonObject
    val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
        ?: error("Endpoint response has no chat message")
    val content = message["content"]?.jsonPrimitive?.contentOrNull
    val calls = message["tool_calls"]?.jsonArray?.map { entry ->
        val call = entry.jsonObject
        val function = call.getValue("function").jsonObject
        ToolCall(id = call.getValue("id").jsonPrimitive.content, type = "function",
            function = FunctionToolCall(function.getValue("name").jsonPrimitive.content, function.getValue("arguments").jsonPrimitive.content))
    }.orEmpty()
    require(!content.isNullOrBlank() || calls.isNotEmpty()) { "Endpoint returned an empty chat response" }
    return ConversationMessageDocument(role = MessageRole.assistant, content = content, tool_calls = calls, language_model_used = "custom:$model")
}
