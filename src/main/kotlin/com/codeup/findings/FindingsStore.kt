package com.codeup.findings

import com.codeup.migrations.*
import com.codeup.model.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.*
import java.io.File
import java.nio.file.Files
import java.time.Instant

@Service(Service.Level.PROJECT)
class FindingsStore(private val project: Project) : VirtualFileListener {

    private val log = Logger.getInstance(FindingsStore::class.java)
    private var findings = mutableMapOf<String, Finding>()
    private var findingsDir: File? = null

    fun interface ChangeListener { fun onChanged() }
    private val listeners = mutableListOf<ChangeListener>()

    fun addChangeListener(listener: ChangeListener) { listeners.add(listener) }
    fun removeChangeListener(listener: ChangeListener) { listeners.remove(listener) }
    private fun fireChanged() { listeners.forEach { runCatching { it.onChanged() } } }

    fun init(rootDir: File, findingsDirRelPath: String = ".codeup/findings") {
        findingsDir = File(rootDir, findingsDirRelPath)
        VirtualFileManager.getInstance().addVirtualFileListener(this)
        reloadAll()
    }

    fun dispose() {
        VirtualFileManager.getInstance().removeVirtualFileListener(this)
    }

    val all: List<Finding> get() = findings.values.toList()
    fun get(id: String): Finding? = findings[id]

    // VirtualFileListener — reload when .codeup/findings/ changes
    override fun contentsChanged(event: VirtualFileEvent) = checkAndReload(event.file)
    override fun fileCreated(event: VirtualFileEvent) = checkAndReload(event.file)
    override fun fileDeleted(event: VirtualFileEvent) = checkAndReload(event.file)

    private fun checkAndReload(vf: VirtualFile) {
        val dir = findingsDir ?: return
        if (vf.path.startsWith(dir.canonicalPath)) reloadAll()
    }

    fun reloadAll() {
        val dir = findingsDir ?: return
        val next = mutableMapOf<String, Finding>()
        if (dir.exists() && dir.isDirectory) {
            for (file in dir.listFiles { f -> f.name.endsWith(".yaml") || f.name.endsWith(".yml") } ?: emptyArray()) {
                try {
                    val text = file.readText()
                    val raw = FindingYamlMapper.deserialize(text)
                    val migrated = runMigrations<Map<String, Any?>>(raw, file.name, FINDING_CURRENT_VERSION, FINDING_MIGRATIONS)
                    val result = validateFinding(migrated.value)
                    when (result) {
                        is ValidationResult.Ok -> {
                            val f = result.value
                            if (!f.location.file.startsWith(".codeup/") && f.location.file != ".codeup") {
                                next[f.id] = f
                            }
                        }
                        is ValidationResult.Err -> log.warn("[findings] ${file.name}: ${result.errors.joinToString("; ") { "${it.path}: ${it.message}" }}")
                    }
                } catch (e: Exception) {
                    log.warn("[findings] ${file.name}: ${e.message}")
                }
            }
        }
        findings = next
        fireChanged()
    }

    fun save(finding: Finding) {
        if (!isSafeIdentifier(finding.id)) throw IllegalArgumentException("unsafe finding id: ${finding.id}")
        val dir = findingsDir ?: throw IllegalStateException("FindingsStore not initialized")
        dir.mkdirs()
        val file = File(dir, "${finding.id}.yaml")
        // Containment check
        if (!file.canonicalPath.startsWith(dir.canonicalPath + File.separator)) {
            throw SecurityException("finding path escapes findings directory: ${file.path}")
        }
        file.writeText(FindingYamlMapper.serialize(finding))
        findings[finding.id] = finding
        fireChanged()
        // Refresh VFS so IntelliJ tracks the new file
        runCatching { LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file) }
    }

    fun updateStatus(id: String, status: Status, note: String? = null) {
        val f = findings[id] ?: return
        if (f.status == status) return
        val event = HistoryEvent(
            timestamp = Instant.now().toString(),
            event = "status_changed",
            from = f.status.name,
            to = status.name,
            note = note,
        )
        save(f.copy(status = status, history = f.history + event))
    }

    fun upsertFromAnalysis(partial: Finding): Finding {
        val existing = findings[partial.id]
        val finding = if (existing != null) {
            existing.copy(
                category = partial.category, severity = partial.severity,
                location = partial.location, explanation = partial.explanation,
                suggestedRemediation = partial.suggestedRemediation,
                detectedBy = partial.detectedBy, confidence = partial.confidence,
            )
        } else {
            partial.copy(
                status = Status.unconfirmed,
                priority = Priority.valueOf(partial.severity.name),
                history = listOf(HistoryEvent(timestamp = partial.detectedAt, event = "detected")),
            )
        }
        save(finding)
        return finding
    }

    fun rebindOrOrphan(currentFiles: Map<String, String>): Pair<Int, Int> {
        var rebound = 0; var orphaned = 0
        for (f in findings.values.toList()) {
            if (currentFiles.containsKey(f.location.file)) continue
            val fromHash = f.location.contentHash
            val target = fromHash?.let { h -> currentFiles.entries.find { it.value == h }?.key }
            val now = Instant.now().toString()
            if (target != null) {
                save(f.copy(location = f.location.copy(file = target),
                    history = f.history + HistoryEvent(now, "rebound", from = f.location.file, to = target)))
                rebound++
            } else if (!f.location.file.startsWith("__orphan__/")) {
                val orphanPath = "__orphan__/${f.location.file}"
                save(f.copy(location = f.location.copy(file = orphanPath),
                    history = f.history + HistoryEvent(now, "rebound", from = f.location.file, to = orphanPath,
                        note = "source file no longer present and no content-hash match found")))
                orphaned++
            }
        }
        return rebound to orphaned
    }

    companion object {
        fun getInstance(project: Project): FindingsStore = project.getService(FindingsStore::class.java)
    }
}