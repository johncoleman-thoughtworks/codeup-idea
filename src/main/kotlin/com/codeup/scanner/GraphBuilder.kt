package com.codeup.scanner

import com.codeup.model.FileEntry
import com.codeup.model.ProjectIndex
import java.nio.file.Paths

data class DependencyGraph(
    val edges: Map<String, Set<String>>,
    val reverse: Map<String, Set<String>>,
    val unresolved: Map<String, List<String>>,
)

data class Cycle(val files: List<String>)

object GraphBuilder {

    fun buildGraph(index: ProjectIndex): DependencyGraph {
        val byPath = index.files.associateBy { it.path }
        val edges = mutableMapOf<String, MutableSet<String>>()
        val reverse = mutableMapOf<String, MutableSet<String>>()
        val unresolved = mutableMapOf<String, MutableList<String>>()

        for (f in index.files) {
            val resolved = mutableSetOf<String>()
            val stillUnresolved = mutableListOf<String>()
            for (raw in f.rawImports) {
                val target = resolveImport(f, raw, byPath)
                if (target != null && target != f.path) resolved.add(target)
                else if (target == null) stillUnresolved.add(raw)
            }
            if (resolved.isNotEmpty()) edges[f.path] = resolved
            if (stillUnresolved.isNotEmpty()) unresolved[f.path] = stillUnresolved
            for (t in resolved) {
                reverse.getOrPut(t) { mutableSetOf() }.add(f.path)
            }
        }
        return DependencyGraph(edges, reverse, unresolved)
    }

    fun findCycles(graph: DependencyGraph): List<Cycle> {
        val cycles = mutableListOf<Cycle>()
        var idx = 0
        val indexOf = mutableMapOf<String, Int>()
        val lowlink = mutableMapOf<String, Int>()
        val onStack = mutableSetOf<String>()
        val stack = ArrayDeque<String>()
        val nodes = (graph.edges.keys + graph.reverse.keys).toSet()

        fun strongconnect(v: String) {
            indexOf[v] = idx; lowlink[v] = idx; idx++
            stack.addLast(v); onStack.add(v)
            val succ = graph.edges[v] ?: emptySet()
            for (w in succ) {
                if (!indexOf.containsKey(w)) {
                    strongconnect(w)
                    lowlink[v] = minOf(lowlink[v]!!, lowlink[w]!!)
                } else if (onStack.contains(w)) {
                    lowlink[v] = minOf(lowlink[v]!!, indexOf[w]!!)
                }
            }
            if (lowlink[v] == indexOf[v]) {
                val component = mutableListOf<String>()
                var w: String
                do {
                    w = stack.removeLast()
                    onStack.remove(w)
                    component.add(w)
                } while (w != v)
                if (component.size > 1) {
                    cycles.add(Cycle(component.reversed()))
                } else if (succ.contains(v)) {
                    cycles.add(Cycle(listOf(v)))
                }
            }
        }

        for (v in nodes) if (!indexOf.containsKey(v)) strongconnect(v)
        return cycles
    }

    fun neighborsOf(graph: DependencyGraph, file: String): Pair<List<String>, List<String>> =
        (graph.edges[file]?.toList() ?: emptyList()) to (graph.reverse[file]?.toList() ?: emptyList())

    private fun resolveImport(from: FileEntry, raw: String, byPath: Map<String, FileEntry>): String? = when (from.language) {
        "java", "kotlin", "scala" -> resolveJvm(raw, byPath, from.language)
        "typescript", "typescriptreact", "javascript", "javascriptreact" -> resolveJs(from.path, raw, byPath)
        "python" -> resolvePython(raw, byPath)
        "go" -> resolveGo(raw, byPath)
        else -> null
    }

    private fun resolveJvm(raw: String, byPath: Map<String, FileEntry>, lang: String): String? {
        if (raw.endsWith(".*")) return null
        val dotted = raw.replace('.', '/')
        val exts = when (lang) { "kotlin" -> listOf(".kt"); "scala" -> listOf(".scala"); else -> listOf(".java") }
        for (candidate in byPath.keys) {
            for (ext in exts) {
                if (candidate.endsWith("/$dotted$ext") || candidate == "$dotted$ext") return candidate
            }
        }
        return null
    }

    private fun resolveJs(fromPath: String, raw: String, byPath: Map<String, FileEntry>): String? {
        if (!raw.startsWith(".")) return null
        val baseDir = fromPath.substringBeforeLast("/", "")
        val joined = normalizePosix(if (baseDir.isEmpty()) raw else "$baseDir/$raw")
        val exts = listOf(".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs")
        for (e in exts) { if (byPath.containsKey("$joined$e")) return "$joined$e" }
        for (e in exts) { if (byPath.containsKey("$joined/index$e")) return "$joined/index$e" }
        if (byPath.containsKey(joined)) return joined
        return null
    }

    private fun resolvePython(raw: String, byPath: Map<String, FileEntry>): String? {
        if (raw.startsWith(".")) return null
        val dotted = raw.replace('.', '/')
        for (candidate in byPath.keys) {
            if (candidate.endsWith("/$dotted.py") || candidate == "$dotted.py") return candidate
            if (candidate.endsWith("/$dotted/__init__.py") || candidate == "$dotted/__init__.py") return candidate
        }
        return null
    }

    private fun resolveGo(raw: String, byPath: Map<String, FileEntry>): String? {
        val parts = raw.split("/")
        val tail = parts.takeLast(2).joinToString("/")
        for (candidate in byPath.keys) {
            if (!candidate.endsWith(".go")) continue
            val dir = candidate.substringBeforeLast("/", "")
            if (dir.endsWith("/$tail") || dir == tail) return candidate
        }
        return null
    }

    private fun normalizePosix(path: String): String {
        val parts = path.split("/")
        val result = ArrayDeque<String>()
        for (p in parts) {
            when (p) {
                ".", "" -> {}
                ".." -> if (result.isNotEmpty()) result.removeLast()
                else -> result.addLast(p)
            }
        }
        return result.joinToString("/")
    }
}