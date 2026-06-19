package com.codeup.intent

import com.codeup.scanner.GitignoreMatcher

data class LayerRule(
    val layer: String,
    val match: String,
    val cannotDependOn: List<String> = emptyList(),
)

data class IntentConfig(val layers: List<LayerRule>)

fun layerForFile(file: String, intent: IntentConfig): String? {
    var best: LayerRule? = null
    var bestLen = -1
    for (rule in intent.layers) {
        if (!matchesRule(file, rule.match)) continue
        if (rule.match.length > bestLen) { best = rule; bestLen = rule.match.length }
    }
    return best?.layer
}

fun matchesRule(file: String, pattern: String): Boolean {
    val effective = if (pattern.endsWith("/")) "${pattern}**" else pattern
    return try {
        GitignoreMatcher.matchGlob(file, effective)
    } catch (e: Exception) { false }
}