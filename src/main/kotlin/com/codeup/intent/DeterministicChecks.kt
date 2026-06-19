package com.codeup.intent

import com.codeup.analyzer.stableIdDeterministic
import com.codeup.model.*
import com.codeup.scanner.Cycle
import com.codeup.scanner.DependencyGraph
import java.time.Instant

fun cycleFindings(cycles: List<Cycle>): List<Finding> = cycles.map { cycle ->
    val head = cycle.files.first()
    val id = stableIdDeterministic("cyclic-dependency", cycle.files.joinToString("|"))
    val isSelf = cycle.files.size == 1
    val explanation = if (isSelf)
        "$head imports from itself (transitive self-loop in the module graph)."
    else
        "Cyclic import chain across ${cycle.files.size} files:\n\n${(cycle.files + cycle.files.first()).joinToString(" → ")}\n\nCycles make these files impossible to reason about or test in isolation; usually signals a missing abstraction that wants to live in a separate module."
    baseFinding(
        id = id, category = "cyclic-dependency", severity = Severity.high, file = head,
        explanation = explanation,
        remediation = "Extract the shared concept into a third module that both can depend on, or invert the dependency direction.",
    )
}

fun layerViolations(graph: DependencyGraph, intent: IntentConfig): List<Finding> {
    val findings = mutableListOf<Finding>()
    for ((from, targets) in graph.edges) {
        val fromLayer = layerForFile(from, intent) ?: continue
        val rule = intent.layers.find { it.layer == fromLayer } ?: continue
        if (rule.cannotDependOn.isEmpty()) continue
        for (to in targets) {
            val toLayer = layerForFile(to, intent) ?: continue
            if (!rule.cannotDependOn.contains(toLayer)) continue
            val id = stableIdDeterministic("layer-violation", "$from->$to")
            findings.add(baseFinding(
                id = id, category = "layer-violation", severity = Severity.high, file = from,
                explanation = "Layer \"$fromLayer\" ($from) imports from layer \"$toLayer\" ($to). Configured intent in .codeup/intent.yaml prohibits this direction.",
                remediation = "Move the shared abstraction down into a layer that \"$fromLayer\" is allowed to depend on, or invert the call via an interface.",
            ))
        }
    }
    return findings
}

private fun baseFinding(id: String, category: String, severity: Severity, file: String, explanation: String, remediation: String): Finding {
    val now = Instant.now().toString()
    return Finding(
        schemaVersion = 1, id = id, category = category, severity = severity,
        status = Status.unconfirmed, priority = Priority.valueOf(severity.name),
        location = FindingLocation(file = file),
        explanation = explanation, suggestedRemediation = remediation,
        detectedAt = now, detectedBy = "codeup-deterministic", confidence = 1.0,
        history = listOf(HistoryEvent(timestamp = now, event = "detected")),
    )
}