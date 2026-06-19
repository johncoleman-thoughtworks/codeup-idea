package com.codeup.analyzer

data class ToolProperty(
    val type: String,
    val description: String,
    val enum: List<String>? = null,
)

data class ToolInputSchema(
    val type: String = "object",
    val properties: Map<String, Any>,
    val required: List<String>,
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val input_schema: ToolInputSchema,
)

val REPORT_FINDING_TOOL = ToolDefinition(
    name = "report_finding",
    description = "Report a single architectural anti-pattern finding in the file under review. Call once per distinct issue. Do not call for stylistic nitpicks, formatting, or generic improvement suggestions — only for issues that match a catalogue pattern with reasonable confidence.",
    input_schema = ToolInputSchema(
        type = "object",
        properties = mapOf(
            "category" to mapOf("type" to "string", "description" to "Pattern id from the provided catalogue (e.g. \"anemic-domain-model\")."),
            "severity" to mapOf("type" to "string", "enum" to listOf("low", "medium", "high"), "description" to "Severity. Default to the catalogue pattern severity unless this instance is meaningfully worse or milder."),
            "line" to mapOf("type" to "integer", "description" to "1-based starting line of the offending region."),
            "endLine" to mapOf("type" to "integer", "description" to "1-based ending line (inclusive). Equal to line if a single line."),
            "explanation" to mapOf("type" to "string", "description" to "Why this is an instance of the pattern, written for a developer reading their own code. 2–5 sentences. No filler."),
            "suggestedRemediation" to mapOf("type" to "string", "description" to "Concrete fix direction. Optional but encouraged."),
            "confidence" to mapOf("type" to "number", "description" to "Your honest confidence in [0, 1] that this is a real instance of the named pattern. 0.9 = textbook example. 0.5 = plausible. 0.3 = worth a look. Always emit — never withhold a finding because confidence is low."),
        ),
        required = listOf("category", "severity", "line", "explanation", "confidence"),
    ),
)