package com.codeup.scanner

object ImportExtractor {

    fun extract(language: String, text: String): List<String> = when (language) {
        "java", "kotlin", "scala" -> jvmImports(text)
        "typescript", "typescriptreact", "javascript", "javascriptreact" -> jsImports(text)
        "python" -> pythonImports(text)
        "go" -> goImports(text)
        "csharp" -> csharpImports(text)
        else -> emptyList()
    }

    // import com.example.Foo;  → "com.example.Foo"
    // import static com.x.Y.method; → "com.x.Y"
    private val JVM_RE = Regex("""^\s*import\s+(static\s+)?([a-zA-Z_][\w.]*\*?)\s*;?\s*$""", RegexOption.MULTILINE)
    private fun jvmImports(text: String): List<String> {
        val raw = mutableListOf<String>()
        for (m in JVM_RE.findAll(text)) {
            val isStatic = m.groupValues[1].isNotEmpty()
            var imp = m.groupValues[2]
            if (isStatic && !imp.endsWith(".*")) {
                val lastDot = imp.lastIndexOf('.')
                if (lastDot > 0) imp = imp.substring(0, lastDot)
            }
            raw.add(imp)
        }
        return raw
    }

    // import ... from 'x'  |  require('x')  |  import('x')
    private val JS_RE = Regex("""(?:from|require\(|import\()\s*['"]([^'"]+)['"]""")
    private val JS_BARE_RE = Regex("""^\s*import\s+['"]([^'"]+)['"]\s*;?\s*$""", RegexOption.MULTILINE)
    private fun jsImports(text: String): List<String> {
        val raw = mutableListOf<String>()
        JS_RE.findAll(text).forEach { raw.add(it.groupValues[1]) }
        JS_BARE_RE.findAll(text).forEach { raw.add(it.groupValues[1]) }
        return raw
    }

    private val PY_RE = Regex("""^\s*(?:from\s+([\w.]+)\s+import\s+.+|import\s+([\w. ,]+))$""", RegexOption.MULTILINE)
    private fun pythonImports(text: String): List<String> {
        val raw = mutableListOf<String>()
        for (m in PY_RE.findAll(text)) {
            if (m.groupValues[1].isNotEmpty()) {
                raw.add(m.groupValues[1])
            } else if (m.groupValues[2].isNotEmpty()) {
                for (part in m.groupValues[2].split(",")) {
                    val name = part.trim().split(Regex("""\s+as\s+"""))[0].trim()
                    if (name.isNotEmpty()) raw.add(name)
                }
            }
        }
        return raw
    }

    private val GO_SINGLE = Regex("""^\s*import\s+(?:\w+\s+)?"([^"]+)"\s*$""", RegexOption.MULTILINE)
    private val GO_BLOCK = Regex("""^\s*import\s*\(([\s\S]*?)\)""", RegexOption.MULTILINE)
    private val GO_INSIDE = Regex("""(?:\w+\s+)?"([^"]+)"""")
    private fun goImports(text: String): List<String> {
        val raw = mutableListOf<String>()
        GO_SINGLE.findAll(text).forEach { raw.add(it.groupValues[1]) }
        for (block in GO_BLOCK.findAll(text)) {
            GO_INSIDE.findAll(block.groupValues[1]).forEach { raw.add(it.groupValues[1]) }
        }
        return raw
    }

    private val CS_RE = Regex("""^\s*using\s+(?:static\s+)?([\w.]+)\s*;\s*$""", RegexOption.MULTILINE)
    private fun csharpImports(text: String): List<String> =
        CS_RE.findAll(text).map { it.groupValues[1] }.toList()
}