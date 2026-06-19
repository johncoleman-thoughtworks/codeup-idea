package com.codeup.scanner

import com.codeup.model.FileEntry
import com.codeup.model.ProjectIndex
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.*
import java.io.File
import java.security.MessageDigest
import java.time.Instant

private val log = Logger.getInstance("com.codeup.scanner.WorkspaceScanner")

private const val MAX_FILE_BYTES = 512 * 1024L

private val DEFAULT_EXCLUDES = listOf(
    ".git", ".idea", ".vscode-test", "node_modules", "dist", "out", "build",
    ".gradle", ".kotlin", "target", ".mvn", "bin", "*.class", "*.jar", "*.war", "*.ear",
    "vendor", "*.exe", "*.test", "__pycache__", ".venv", "venv", ".tox",
    ".pytest_cache", ".mypy_cache", ".ruff_cache", "*.egg-info", "*.pyc", "*.pyo",
    "obj", "packages", ".vs", "TestResults", "*.dll", "*.pdb", "*.nupkg", "*.suo", "*.user",
    ".codeup",
    "Cargo.lock", "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "npm-shrinkwrap.json",
    "bun.lockb", "Pipfile.lock", "poetry.lock", "uv.lock", "Gemfile.lock", "composer.lock",
    "go.sum", "mix.lock", "Podfile.lock", "packages.lock.json",
)

data class IgnoreStack(
    val defaults: GitignoreMatcher = GitignoreMatcher().also { m -> DEFAULT_EXCLUDES.forEach { m.add(it) } },
    val gitIg: GitignoreMatcher = GitignoreMatcher(),
    val codeupIg: GitignoreMatcher = GitignoreMatcher(),
)

fun scanWorkspace(rootDir: File, indicator: ProgressIndicator? = null): ProjectIndex {
    val stack = IgnoreStack()
    val files = mutableListOf<FileEntry>()
    walk(rootDir, rootDir, "", stack, files, indicator)
    files.sortBy { it.path }
    return ProjectIndex(
        schemaVersion = 1,
        generatedAt = Instant.now().toString(),
        rootName = rootDir.name,
        files = files,
    )
}

private fun walk(rootDir: File, dir: File, rel: String, stack: IgnoreStack, out: MutableList<FileEntry>, indicator: ProgressIndicator?) {
    if (indicator?.isCanceled == true) return

    // Snapshot before loading this directory's ignore files so we can roll back
    // patterns when we return to the parent, preventing them bleeding into siblings.
    val gitSnap = stack.gitIg.snapshot()
    val codeupSnap = stack.codeupIg.snapshot()

    loadIgnoreFile(dir, ".gitignore", rel, stack.gitIg)
    loadIgnoreFile(dir, ".codeupignore", rel, stack.codeupIg)

    val entries = dir.listFiles() ?: return
    for (entry in entries) {
        if (indicator?.isCanceled == true) return
        val childRel = if (rel.isEmpty()) entry.name else "$rel/${entry.name}"
        val checkPath = if (entry.isDirectory) "$childRel/" else childRel
        if (shouldSkip(stack, checkPath)) continue
        if (entry.isDirectory) {
            walk(rootDir, entry, childRel, stack, out, indicator)
        } else if (entry.isFile) {
            toFileEntry(entry, childRel)?.let { out.add(it) }
        }
    }

    stack.gitIg.rollback(gitSnap)
    stack.codeupIg.rollback(codeupSnap)
}

private fun loadIgnoreFile(dir: File, name: String, scopeDir: String, target: GitignoreMatcher) {
    val f = File(dir, name)
    if (!f.exists()) return
    try {
        val patterns = IgnoreLoader.parseIgnoreText(f.readText(), scopeDir)
        patterns.forEach { target.add(it) }
    } catch (e: Exception) { log.warn("Failed to read $name: ${e.message}") }
}

private fun shouldSkip(stack: IgnoreStack, path: String): Boolean {
    if (stack.defaults.ignores(path)) return true
    val ci = stack.codeupIg.test(path)
    if (ci.ignored) return true
    if (ci.unignored) return false
    return stack.gitIg.ignores(path)
}

private fun toFileEntry(file: File, rel: String): FileEntry? {
    if (file.length() > MAX_FILE_BYTES) return null
    val bytes = try { file.readBytes() } catch (e: Exception) { return null }
    val language = LanguageDetector.detect(file.name)
    val rawImports = try {
        val text = bytes.toString(Charsets.UTF_8)
        ImportExtractor.extract(language, text)
    } catch (e: Exception) { emptyList() }
    return FileEntry(
        path = rel,
        language = language,
        size = file.length(),
        contentHash = sha256(bytes),
        mtime = file.lastModified(),
        rawImports = rawImports,
    )
}

private fun sha256(bytes: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(bytes).joinToString("") { "%02x".format(it) }
}
