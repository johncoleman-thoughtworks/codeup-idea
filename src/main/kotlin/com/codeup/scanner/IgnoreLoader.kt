package com.codeup.scanner

object IgnoreLoader {

    fun rewritePatternForScope(rawLine: String, scopeDir: String): String? {
        val line = rawLine.trimEnd('\r')
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("#")) return null

        var negated = false
        var body = trimmed
        if (body.startsWith("!")) { negated = true; body = body.substring(1) }
        if (body.startsWith("\\#") || body.startsWith("\\!")) body = body.substring(1)

        var anchored = false
        if (body.startsWith("/")) { anchored = true; body = body.substring(1) }
        val trailingSlash = body.endsWith("/")
        val bodyCheck = if (trailingSlash) body.dropLast(1) else body
        if (!anchored && bodyCheck.contains("/")) anchored = true

        val prefix = if (scopeDir.isNotEmpty()) "$scopeDir/" else ""
        val rewritten = if (anchored) "$prefix$body" else "$prefix**/$body"
        return if (negated) "!$rewritten" else rewritten
    }

    fun parseIgnoreText(text: String, scopeDir: String): List<String> {
        val out = mutableListOf<String>()
        for (line in text.split(Regex("""\r?\n"""))) {
            val r = rewritePatternForScope(line, scopeDir)
            if (r != null) out.add(r)
        }
        return out
    }
}