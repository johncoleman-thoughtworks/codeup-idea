package com.codeup.analyzer

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class AnthropicClient(
    private val apiKeyProvider: () -> String?,
    private val modelId: String = "claude-sonnet-4-6",
    private val baseUrl: String = "https://api.anthropic.com",
) : LLMClient {

    private val log = Logger.getInstance(AnthropicClient::class.java)
    private val mapper = jacksonObjectMapper()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun model() = modelId
    override fun provider() = "anthropic"
    override fun reset() { /* no cached session state */ }

    override suspend fun analyze(req: LLMAnalyzeRequest): LLMAnalyzeResponse = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider() ?: throw IllegalStateException("Anthropic API key not set. Use Tools > Codeup > Set Anthropic API Key.")

        val toolSchema = mapOf(
            "name" to req.tool.name,
            "description" to req.tool.description,
            "input_schema" to mapOf(
                "type" to req.tool.input_schema.type,
                "properties" to req.tool.input_schema.properties,
                "required" to req.tool.input_schema.required,
            ),
        )

        val body = mapOf(
            "model" to modelId,
            "max_tokens" to req.maxOutputTokens,
            "system" to req.systemPrompt,
            "tools" to listOf(toolSchema),
            "messages" to listOf(mapOf("role" to "user", "content" to req.userPrompt)),
        )

        val requestBody = mapper.writeValueAsString(body).toRequestBody("application/json".toMediaType())
        val httpReq = Request.Builder()
            .url("$baseUrl/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(requestBody)
            .build()

        val call = http.newCall(httpReq)

        // Honour cancellation
        if (req.cancellationCheck()) { call.cancel(); throw CancellationException("scan cancelled") }

        val response = try {
            call.execute()
        } catch (e: IOException) {
            if (req.cancellationCheck()) throw CancellationException("scan cancelled")
            throw e
        }

        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            throw IOException("Anthropic API error ${response.code}: $errBody")
        }

        @Suppress("UNCHECKED_CAST")
        val parsed = mapper.readValue(response.body?.string() ?: "{}", Map::class.java) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val content = parsed["content"] as? List<Map<String, Any?>> ?: emptyList()
        val toolCalls = content.filter { it["type"] == "tool_use" }.map { block ->
            ReportedToolCall(
                name = block["name"] as? String ?: "",
                input = block["input"],
            )
        }
        LLMAnalyzeResponse(toolCalls)
    }
}

class CancellationException(message: String) : Exception(message)
