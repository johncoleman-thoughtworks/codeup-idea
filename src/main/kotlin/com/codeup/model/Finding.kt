package com.codeup.model

enum class Severity { low, medium, high }
enum class Status { unconfirmed, confirmed, dismissed, fixed }
enum class Priority { ignore, low, medium, high }

data class FindingLocation(
    val file: String,
    val line: Int? = null,
    val endLine: Int? = null,
    val astPath: String? = null,
    val contentHash: String? = null,
)

data class HistoryEvent(
    val timestamp: String,
    val event: String, // "detected" | "status_changed" | "priority_changed" | "note" | "rebound"
    val by: String? = null,
    val from: String? = null,
    val to: String? = null,
    val note: String? = null,
)

data class Finding(
    val schemaVersion: Int = 1,
    val id: String,
    val category: String,
    val severity: Severity,
    val status: Status,
    val priority: Priority,
    val location: FindingLocation,
    val explanation: String,
    val suggestedRemediation: String? = null,
    val detectedAt: String,
    val detectedBy: String,
    val confidence: Double? = null,
    val history: List<HistoryEvent> = emptyList(),
)

data class ValidationError(val path: String, val message: String)

sealed class ValidationResult {
    data class Ok(val value: Finding) : ValidationResult()
    data class Err(val errors: List<ValidationError>) : ValidationResult()
}

fun isSafeIdentifier(id: String): Boolean =
    Regex("^[A-Za-z0-9_.\\-]{1,128}$").matches(id) && id != "." && id != ".."

fun isSafeRelativePath(p: String): Boolean {
    if (p.isEmpty() || p.length > 1024) return false
    if (p.startsWith("/") || p.startsWith("\\")) return false
    if (Regex("^[A-Za-z]:").containsMatchIn(p)) return false
    if (p.contains("\\") || p.contains(" ")) return false
    for (seg in p.split("/")) if (seg == "..") return false
    return true
}

fun validateFinding(raw: Any?): ValidationResult {
    val errors = mutableListOf<ValidationError>()
    fun push(path: String, msg: String) { errors.add(ValidationError(path, msg)) }

    if (raw == null || raw !is Map<*, *>) {
        return ValidationResult.Err(listOf(ValidationError("$", "finding must be an object")))
    }
    @Suppress("UNCHECKED_CAST")
    val r = raw as Map<String, Any?>

    val schemaVersion = (r["schemaVersion"] as? Number)?.toInt() ?: 1
    if (schemaVersion != 1) push("schemaVersion", "unsupported schemaVersion: $schemaVersion")

    val id = strField(r["id"], "id", errors)
    if (id.isNotEmpty() && !isSafeIdentifier(id)) push("id", "must match [A-Za-z0-9_.-]{1,128} and contain no path separators")

    val category = strField(r["category"], "category", errors)
    val severity = enumField<Severity>(r["severity"], Severity.values(), "severity", errors)
    val status = enumField<Status>(r["status"], Status.values(), "status", errors)
    val priority = enumField<Priority>(r["priority"] ?: "medium", Priority.values(), "priority", errors)
    val explanation = strField(r["explanation"], "explanation", errors)
    val detectedAt = strField(r["detectedAt"] ?: java.time.Instant.now().toString(), "detectedAt", errors)
    val detectedBy = strField(r["detectedBy"] ?: "human", "detectedBy", errors)

    val loc = r["location"] as? Map<*, *>
    if (loc == null) push("location", "missing location object")
    @Suppress("UNCHECKED_CAST")
    val locMap = loc as? Map<String, Any?>

    val file = if (locMap != null) strField(locMap["file"], "location.file", errors) else ""
    if (file.isNotEmpty() && !isSafeRelativePath(file)) push("location.file", "must be a workspace-relative POSIX path")
    val line = locMap?.get("line")?.let { numField(it, "location.line", errors) }
    val endLine = locMap?.get("endLine")?.let { numField(it, "location.endLine", errors) }

    @Suppress("UNCHECKED_CAST")
    val history = (r["history"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()?.map { h ->
        HistoryEvent(
            timestamp = h["timestamp"] as? String ?: "",
            event = h["event"] as? String ?: "",
            by = h["by"] as? String,
            from = h["from"] as? String,
            to = h["to"] as? String,
            note = h["note"] as? String,
        )
    } ?: emptyList()

    if (errors.isNotEmpty()) return ValidationResult.Err(errors)

    return ValidationResult.Ok(Finding(
        schemaVersion = 1,
        id = id,
        category = category,
        severity = severity ?: Severity.low,
        status = status ?: Status.unconfirmed,
        priority = priority ?: Priority.medium,
        location = FindingLocation(
            file = file,
            line = line,
            endLine = endLine,
            astPath = locMap?.get("astPath") as? String,
            contentHash = locMap?.get("contentHash") as? String,
        ),
        explanation = explanation,
        suggestedRemediation = r["suggestedRemediation"] as? String,
        detectedAt = detectedAt,
        detectedBy = detectedBy,
        confidence = (r["confidence"] as? Number)?.toDouble(),
        history = history,
    ))
}

private fun strField(v: Any?, path: String, errors: MutableList<ValidationError>): String {
    if (v !is String || v.isEmpty()) { errors.add(ValidationError(path, "must be a non-empty string")); return "" }
    return v
}

private fun numField(v: Any?, path: String, errors: MutableList<ValidationError>): Int? {
    val n = (v as? Number)?.toInt() ?: v?.toString()?.toIntOrNull()
    if (n == null) { errors.add(ValidationError(path, "must be a number")) }
    return n
}

private inline fun <reified T : Enum<T>> enumField(v: Any?, values: Array<T>, path: String, errors: MutableList<ValidationError>): T? {
    val s = v as? String
    val match = values.find { it.name == s }
    if (match == null) errors.add(ValidationError(path, "must be one of: ${values.joinToString(", ") { it.name }}"))
    return match
}