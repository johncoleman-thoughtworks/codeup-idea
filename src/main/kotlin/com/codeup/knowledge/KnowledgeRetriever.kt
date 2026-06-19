package com.codeup.knowledge

import com.codeup.model.DismissalEntry
import com.codeup.model.ExemplarEntry
import com.codeup.scanner.GitignoreMatcher

const val MAX_DISMISSALS = 3
const val MAX_EXEMPLARS = 3

data class RelevantKnowledge(
    val dismissals: List<DismissalEntry>,
    val exemplars: List<ExemplarEntry>,
)

fun relevantFor(
    filePath: String,
    dismissals: List<DismissalEntry>,
    exemplars: List<ExemplarEntry>,
): RelevantKnowledge {
    val matchedDismissals = dismissals.filter { matchesGlob(filePath, it.filePathPattern) }
    val fileDir = filePath.substringBeforeLast("/", "")
    val sortedExemplars = exemplars
        .map { e -> e to directoryProximity(fileDir, e.filePath.substringBeforeLast("/", "")) }
        .sortedByDescending { it.second }
        .map { it.first }
    return RelevantKnowledge(
        dismissals = dedupeByCategory(matchedDismissals, MAX_DISMISSALS),
        exemplars = dedupeByCategory(sortedExemplars, MAX_EXEMPLARS),
    )
}

fun formatForPrompt(k: RelevantKnowledge): String {
    if (k.dismissals.isEmpty() && k.exemplars.isEmpty()) return ""
    val lines = mutableListOf("", "Project conventions (from this team's prior dismissals and confirmations):")
    if (k.dismissals.isNotEmpty()) {
        lines.add(""); lines.add("Patterns previously dismissed as not-applicable in this project:")
        for (d in k.dismissals) {
            lines.add("- ${d.category} (files matching `${d.filePathPattern}`): ${d.rationale.replace(Regex("\\s+"), " ").trim()}")
        }
        lines.add("Take these dismissals seriously — if the case in front of you matches the dismissed pattern's situation, do not report it.")
    }
    if (k.exemplars.isNotEmpty()) {
        lines.add(""); lines.add("Patterns confirmed as real instances in this project (use as positive examples):")
        for (e in k.exemplars) {
            lines.add("- ${e.category} confirmed in ${e.filePath}: ${e.excerpt.replace(Regex("\\s+"), " ").trim().take(300)}")
        }
    }
    return lines.joinToString("\n")
}

fun matchesGlob(filePath: String, pattern: String): Boolean {
    if (pattern == filePath) return true
    return try { GitignoreMatcher.matchGlob(filePath, pattern) } catch (e: Exception) { false }
}

private fun directoryProximity(a: String, b: String): Int {
    if (a == b) return 100
    val aSegs = a.split("/"); val bSegs = b.split("/")
    var shared = 0
    for (i in 0 until minOf(aSegs.size, bSegs.size)) {
        if (aSegs[i] == bSegs[i]) shared++ else break
    }
    return shared * 10
}

private fun <T : Any> dedupeByCategory(arr: List<T>, cap: Int): List<T> {
    val out = mutableListOf<T>()
    val seen = mutableMapOf<String, Int>()
    for (item in arr) {
        val cat = when (item) {
            is DismissalEntry -> item.category
            is ExemplarEntry -> item.category
            else -> continue
        }
        val count = seen.getOrDefault(cat, 0)
        if (count >= cap) continue
        out.add(item)
        seen[cat] = count + 1
    }
    return out
}