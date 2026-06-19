package com.codeup.scanner

object LanguageDetector {
    private val LANGUAGE_BY_EXT = mapOf(
        "ts" to "typescript", "tsx" to "typescriptreact",
        "js" to "javascript", "jsx" to "javascriptreact", "mjs" to "javascript", "cjs" to "javascript",
        "py" to "python", "rb" to "ruby", "go" to "go", "rs" to "rust",
        "java" to "java", "kt" to "kotlin", "scala" to "scala",
        "cs" to "csharp", "cpp" to "cpp", "cc" to "cpp", "h" to "cpp", "hpp" to "cpp", "c" to "c",
        "php" to "php", "swift" to "swift",
        "md" to "markdown", "yaml" to "yaml", "yml" to "yaml", "json" to "json", "toml" to "toml",
        "sh" to "shell", "bash" to "shell", "zsh" to "shell",
        "html" to "html", "css" to "css", "scss" to "scss",
        "sql" to "sql",
    )

    fun detect(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return LANGUAGE_BY_EXT[ext] ?: "plaintext"
    }
}