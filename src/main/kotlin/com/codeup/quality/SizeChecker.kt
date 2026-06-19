package com.codeup.quality

import com.codeup.analyzer.stableIdDeterministic
import com.codeup.model.*
import java.time.Instant

data class SizeCheckOptions(
    val warnBytes: Long = 30_000L,
    val criticalBytes: Long = 60_000L,
)

private val NON_SOURCE_LANGUAGES = setOf(
    "yaml", "json", "toml", "markdown", "plaintext", "html", "css", "scss", "sql",
)

fun oversizedFiles(index: ProjectIndex, options: SizeCheckOptions = SizeCheckOptions()): List<Finding> {
    val findings = mutableListOf<Finding>()
    for (file in index.files) {
        if (file.size < options.warnBytes) continue
        if (NON_SOURCE_LANGUAGES.contains(file.language)) continue
        val isCritical = file.size >= options.criticalBytes
        val severity = if (isCritical) Severity.high else Severity.medium
        val id = stableIdDeterministic("oversized-file", file.path)
        val now = Instant.now().toString()
        findings.add(Finding(
            schemaVersion = 1,
            id = id,
            category = "oversized-file",
            severity = severity,
            status = Status.unconfirmed,
            priority = Priority.valueOf(severity.name),
            location = FindingLocation(file = file.path, line = 1, contentHash = file.contentHash),
            explanation = if (isCritical)
                "This file is ${file.size.toLong().format()} bytes — beyond Codeup's ${options.criticalBytes.toLong().format()}-byte analysis cap. The deep LLM scan was skipped for this file; only deterministic checks ran."
            else
                "This file is ${file.size.toLong().format()} bytes — past the ${options.warnBytes.toLong().format()}-byte warning threshold. Navigation, review, and merge-conflict surface area all grow with file size.",
            suggestedRemediation = "Split along concern boundaries. If this file is generated or large test fixtures, add it to .gitignore or .codeupignore. If the size is deliberate, dismiss with a rationale.",
            detectedAt = now,
            detectedBy = "codeup-deterministic",
            confidence = 1.0,
            history = listOf(HistoryEvent(timestamp = now, event = "detected")),
        ))
    }
    return findings
}

private fun Long.format(): String = String.format("%,d", this)