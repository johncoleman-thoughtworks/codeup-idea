package com.codeup.analyzer

import com.codeup.model.CataloguePattern
import java.security.MessageDigest

fun stableId(file: String, category: String, line: Int): String {
    val h = sha1("$file:$category:$line").substring(0, 12)
    return "$category-$h"
}

fun stableIdDeterministic(category: String, key: String): String {
    val h = sha1("$category:$key").substring(0, 12)
    return "$category-$h"
}

fun neighborsCacheKey(neighbors: List<Pair<String, String>>): String {
    if (neighbors.isEmpty()) return ""
    val sorted = neighbors.sortedBy { it.first }
    val blob = sorted.joinToString("|") { (path, text) -> "$path@${sha256(text.toByteArray()).substring(0, 16)}" }
    return sha256(blob.toByteArray()).substring(0, 16)
}

data class ReportedFinding(
    val category: String,
    val severity: String,
    val line: Int,
    val endLine: Int? = null,
    val explanation: String,
    val suggestedRemediation: String? = null,
    val confidence: Double,
)

fun validateReported(input: Any?, patterns: List<CataloguePattern>): ReportedFinding? {
    if (input == null || input !is Map<*, *>) return null
    @Suppress("UNCHECKED_CAST")
    val r = input as Map<String, Any?>
    val category = (r["category"] as? String) ?: return null
    if (patterns.none { it.id == category }) return null
    val severity = r["severity"] as? String
    if (severity != "low" && severity != "medium" && severity != "high") return null
    val line = (r["line"] as? Number)?.toInt() ?: return null
    if (line < 1) return null
    val endLine = (r["endLine"] as? Number)?.toInt()
    val explanation = (r["explanation"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
    val confidence = (r["confidence"] as? Number)?.toDouble() ?: 0.0
    return ReportedFinding(
        category = category,
        severity = severity,
        line = line,
        endLine = if (endLine != null && endLine >= line) endLine else null,
        explanation = explanation,
        suggestedRemediation = (r["suggestedRemediation"] as? String)?.takeIf { it.isNotEmpty() },
        confidence = confidence,
    )
}

fun sha1(input: String): String {
    val md = MessageDigest.getInstance("SHA-1")
    return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}

fun sha256(bytes: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(bytes).joinToString("") { "%02x".format(it) }
}