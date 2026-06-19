package com.codeup.knowledge

import com.codeup.catalogue.CatalogueLoader
import com.codeup.migrations.*
import com.codeup.model.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.*
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.security.MessageDigest
import java.time.Instant

@Service(Service.Level.PROJECT)
class KnowledgeStore(private val project: Project) : VirtualFileListener {

    private val log = Logger.getInstance(KnowledgeStore::class.java)
    private var rootDir: File? = null
    private var _dismissals = listOf<DismissalEntry>()
    private var _exemplars = listOf<ExemplarEntry>()
    private var _patterns = listOf<CataloguePattern>()

    fun interface ChangeListener { fun onChanged() }
    private val listeners = mutableListOf<ChangeListener>()
    fun addChangeListener(l: ChangeListener) { listeners.add(l) }
    fun removeChangeListener(l: ChangeListener) { listeners.remove(l) }
    private fun fireChanged() { listeners.forEach { runCatching { it.onChanged() } } }

    val allDismissals: List<DismissalEntry> get() = _dismissals
    val allExemplars: List<ExemplarEntry> get() = _exemplars
    val patterns: List<CataloguePattern> get() = _patterns

    fun init(root: File) {
        rootDir = root
        VirtualFileManager.getInstance().addVirtualFileListener(this)
        reload()
    }

    fun dispose() { VirtualFileManager.getInstance().removeVirtualFileListener(this) }

    override fun contentsChanged(event: VirtualFileEvent) = checkAndReload(event.file)
    override fun fileCreated(event: VirtualFileEvent) = checkAndReload(event.file)
    override fun fileDeleted(event: VirtualFileEvent) = checkAndReload(event.file)

    private fun checkAndReload(vf: VirtualFile) {
        val root = rootDir ?: return
        val knowledgeDir = File(root, ".codeup/knowledge").canonicalPath
        if (vf.path.startsWith(knowledgeDir)) reload()
    }

    fun reload() {
        val root = rootDir ?: return
        _dismissals = readYamlEntries<DismissalEntry>(root, ".codeup/knowledge/dismissals.yaml", DISMISSAL_CURRENT_VERSION, DISMISSAL_MIGRATIONS, "entries")
        _exemplars = readYamlEntries<ExemplarEntry>(root, ".codeup/knowledge/exemplars.yaml", EXEMPLAR_CURRENT_VERSION, EXEMPLAR_MIGRATIONS, "entries")
        _patterns = readYamlPatterns(root, ".codeup/knowledge/patterns.yaml")
        fireChanged()
    }

    fun hash(): String {
        val blob = buildString {
            append(_dismissals.joinToString("|") { "${it.category}:${it.filePathPattern}:${it.rationale}" })
            append("||")
            append(_exemplars.joinToString("|") { "${it.category}:${it.filePath}:${it.excerpt}" })
            append("||")
            append(_patterns.joinToString("|") { "${it.id}:${it.hint}" })
        }
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(blob.toByteArray()).joinToString("") { "%02x".format(it) }.substring(0, 16)
    }

    fun recordDismissal(category: String, filePathPattern: String, rationale: String, originalFindingId: String, dismissedBy: String = "developer"): DismissalEntry {
        val now = Instant.now().toString()
        val id = knowledgeStableId("dismissal", "$originalFindingId:$dismissedBy")
        val entry = DismissalEntry(schemaVersion = 1, id = id, category = category,
            filePathPattern = filePathPattern, rationale = rationale,
            dismissedAt = now, dismissedBy = dismissedBy, originalFindingId = originalFindingId)
        _dismissals = upsertById(_dismissals, entry)
        saveDismissals()
        fireChanged()
        return entry
    }

    fun recordExemplar(category: String, filePath: String, excerpt: String, originalFindingId: String, confirmedBy: String = "developer"): ExemplarEntry {
        val now = Instant.now().toString()
        val id = knowledgeStableId("exemplar", "$originalFindingId:$confirmedBy")
        val entry = ExemplarEntry(schemaVersion = 1, id = id, category = category,
            filePath = filePath, excerpt = excerpt,
            confirmedAt = now, confirmedBy = confirmedBy, originalFindingId = originalFindingId)
        _exemplars = upsertById(_exemplars, entry)
        saveExemplars()
        fireChanged()
        return entry
    }

    private fun saveDismissals() {
        val root = rootDir ?: return
        val dir = File(root, ".codeup/knowledge").also { it.mkdirs() }
        val map = mapOf("schemaVersion" to 1, "entries" to _dismissals.map { d ->
            mapOf("schemaVersion" to d.schemaVersion, "id" to d.id, "category" to d.category,
                "filePathPattern" to d.filePathPattern, "rationale" to d.rationale,
                "dismissedAt" to d.dismissedAt, "dismissedBy" to d.dismissedBy,
                "originalFindingId" to d.originalFindingId)
        })
        File(dir, "dismissals.yaml").writeText(yamlDump(map))
    }

    private fun saveExemplars() {
        val root = rootDir ?: return
        val dir = File(root, ".codeup/knowledge").also { it.mkdirs() }
        val map = mapOf("schemaVersion" to 1, "entries" to _exemplars.map { e ->
            mapOf("schemaVersion" to e.schemaVersion, "id" to e.id, "category" to e.category,
                "filePath" to e.filePath, "excerpt" to e.excerpt,
                "confirmedAt" to e.confirmedAt, "confirmedBy" to e.confirmedBy,
                "originalFindingId" to e.originalFindingId)
        })
        File(dir, "exemplars.yaml").writeText(yamlDump(map))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> readYamlEntries(root: File, rel: String, currentVersion: Int, migrations: List<com.codeup.migrations.Migration>, listKey: String): List<T> {
        val file = File(root, rel)
        if (!file.exists()) return emptyList()
        return try {
            val raw = Yaml().load<Any>(file.readText()) ?: return emptyList()
            val mig = runMigrations<Map<String, Any?>>(raw, rel, currentVersion, migrations)
            (mig.value[listKey] as? List<Map<String, Any?>>) as? List<T> ?: emptyList()
        } catch (e: Exception) { log.warn("[knowledge] $rel: ${e.message}"); emptyList() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readYamlPatterns(root: File, rel: String): List<CataloguePattern> {
        val file = File(root, rel)
        if (!file.exists()) return emptyList()
        return try {
            val raw = Yaml().load<Map<String, Any?>>(file.readText()) ?: return emptyList()
            val list = raw["patterns"] as? List<Map<String, Any?>> ?: return emptyList()
            list.map { p ->
                CataloguePattern(id = p["id"] as String, name = p["name"] as? String ?: "",
                    languages = (p["languages"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                    defaultSeverity = p["defaultSeverity"] as? String ?: "medium",
                    hint = (p["hint"] as? String ?: "").trim())
            }
        } catch (e: Exception) { log.warn("[knowledge] $rel: ${e.message}"); emptyList() }
    }

    private fun yamlDump(obj: Any): String {
        val opts = DumperOptions().also { it.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK; it.width = 100 }
        return Yaml(opts).dump(obj)
    }

    companion object {
        fun getInstance(project: Project): KnowledgeStore = project.getService(KnowledgeStore::class.java)
    }
}

private fun <T : Any> upsertById(arr: List<T>, entry: T): List<T> {
    val id = when (entry) {
        is DismissalEntry -> entry.id
        is ExemplarEntry -> entry.id
        else -> return arr + entry
    }
    val result = arr.toMutableList()
    val idx = result.indexOfFirst { e ->
        when (e) {
            is DismissalEntry -> e.id == id
            is ExemplarEntry -> e.id == id
            else -> false
        }
    }
    if (idx == -1) result.add(entry) else result[idx] = entry
    return result
}

private fun knowledgeStableId(kind: String, key: String): String {
    val md = java.security.MessageDigest.getInstance("SHA-1")
    val h = md.digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.substring(0, 12)
    return "$kind-$h"
}