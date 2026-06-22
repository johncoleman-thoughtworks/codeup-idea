package com.codeup.scanner

/**
 * Minimal gitignore semantics matcher. Supports:
 * - negation (!)
 * - ** globbing
 * - anchored vs unanchored patterns
 * Patterns must already be rewritten for the root scope via IgnoreLoader.
 */
class GitignoreMatcher {
    private data class Rule(val pattern: String, val negated: Boolean)
    private val rules = mutableListOf<Rule>()

    fun add(pattern: String) {
        if (pattern.startsWith("!")) {
            rules.add(Rule(pattern.substring(1), true))
        } else {
            rules.add(Rule(pattern, false))
        }
    }

    fun addAll(patterns: List<String>) = patterns.forEach { add(it) }

    fun snapshot(): Int = rules.size

    fun rollback(savedSize: Int) { if (savedSize < rules.size) rules.subList(savedSize, rules.size).clear() }

    data class TestResult(val ignored: Boolean, val unignored: Boolean)

    fun test(path: String): TestResult {
        var ignored = false
        var unignored = false
        for (rule in rules) {
            if (matchGlob(path, rule.pattern)) {
                if (rule.negated) { ignored = false; unignored = true }
                else { ignored = true; unignored = false }
            }
        }
        return TestResult(ignored, unignored)
    }

    fun ignores(path: String): Boolean = test(path).ignored

    companion object {
        fun matchGlob(path: String, pattern: String): Boolean {
            // Normalize: trailing slash means match directory and everything under it
            val effectivePattern = if (pattern.endsWith("/")) "${pattern}**" else pattern
            // Strip trailing slash from path before matching so that a plain-name
            // pattern like ".git" matches the directory check path ".git/"
            val normalizedPath = path.trimEnd('/')
            return globMatch(normalizedPath, effectivePattern) || globMatch("$normalizedPath/", effectivePattern)
        }

        private fun globMatch(str: String, pattern: String): Boolean {
            return globMatchHelper(str, 0, pattern, 0)
        }

        private fun globMatchHelper(str: String, si: Int, pat: String, pi: Int): Boolean {
            var s = si
            var p = pi
            while (p < pat.length) {
                when {
                    pat[p] == '*' && p + 1 < pat.length && pat[p + 1] == '*' -> {
                        // ** matches zero or more path segments
                        val rest = pat.substring(p + 2).removePrefix("/")
                        if (rest.isEmpty()) return true
                        for (i in s..str.length) {
                            if (globMatchHelper(str, i, rest, 0)) return true
                            if (i < str.length && str[i] == '/') continue
                            if (i < str.length) {
                                // skip to next /
                                while (i < str.length && str[i] != '/') {
                                    // handled by outer loop
                                    break
                                }
                            }
                        }
                        // Try matching ** as zero segments
                        return globMatchHelper(str, s, rest, 0)
                    }
                    pat[p] == '*' -> {
                        // * matches within a single segment (no /)
                        val rest = pat.substring(p + 1)
                        for (i in s..str.length) {
                            if (i > s && s < str.length && str[i - 1] == '/') break
                            if (globMatchHelper(str, i, rest, 0)) return true
                        }
                        return false
                    }
                    pat[p] == '?' -> {
                        if (s >= str.length || str[s] == '/') return false
                        s++; p++
                    }
                    else -> {
                        if (s >= str.length || str[s] != pat[p]) return false
                        s++; p++
                    }
                }
            }
            return s == str.length
        }
    }
}