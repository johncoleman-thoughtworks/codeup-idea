package com.codeup.analyzer

interface LLMClient {
    suspend fun analyze(req: LLMAnalyzeRequest): LLMAnalyzeResponse
    fun model(): String
    fun provider(): String
    fun reset()
}

data class LLMAnalyzeRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val tool: ToolDefinition,
    val maxOutputTokens: Int,
    val cancellationCheck: () -> Boolean = { false },
)

data class LLMAnalyzeResponse(val toolCalls: List<ReportedToolCall>)

data class ReportedToolCall(val name: String, val input: Any?)