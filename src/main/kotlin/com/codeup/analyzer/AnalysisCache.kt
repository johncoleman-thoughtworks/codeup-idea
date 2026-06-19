package com.codeup.analyzer

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.security.MessageDigest
import java.time.Instant

data class CacheEntry(
    val key: String,
    val analyzedAt: String,
    val findings: List<ReportedFinding>,
)

class AnalysisCache(private val rootDir: File) {
    private val memory = mutableMapOf<String, CacheEntry>()
    private val mapper = jacksonObjectMapper()
    private var initialized = false

    fun load() {
        if (initialized) return
        initialized = true
        migrateLegacyIfPresent()
        ensureCacheIgnored()
        loadAllEntries()
    }

    /** True if any prior analysis result exists for this content+catalogue+model triple,
     *  regardless of neighbor/knowledge hash. Used for cost estimation in ScanRunner. */
    fun hasCachedEntry(contentHash: String, catalogueHash: String, model: String): Boolean {
        val prefix = "$contentHash:$catalogueHash:$model:"
        return memory.keys.any { it.startsWith(prefix) }
    }

    private fun loadAllEntries() {
        val dir = File(rootDir, ".codeup/cache/entries")
        if (!dir.exists() || !dir.isDirectory) return
        for (file in dir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()) {
            try {
                val entry = mapper.readValue<CacheEntry>(file)
                memory[entry.key] = entry
            } catch (_: Exception) { }
        }
    }

    fun get(key: String): CacheEntry? {
        memory[key]?.let { return it }
        return readEntryFromDisk(key)
    }

    fun put(key: String, findings: List<ReportedFinding>) {
        val entry = CacheEntry(key, Instant.now().toString(), findings)
        memory[key] = entry
        writeEntryToDisk(key, entry)
    }

    private fun filenameFor(key: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.substring(0, 32) + ".json"
    }

    private fun entryFile(key: String) = File(rootDir, ".codeup/cache/entries/${filenameFor(key)}")

    private fun readEntryFromDisk(key: String): CacheEntry? {
        val file = entryFile(key)
        if (!file.exists()) return null
        return try {
            val entry = mapper.readValue<CacheEntry>(file)
            memory[key] = entry
            entry
        } catch (e: Exception) { null }
    }

    private fun writeEntryToDisk(key: String, entry: CacheEntry) {
        val file = entryFile(key)
        file.parentFile.mkdirs()
        runCatching { file.writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(entry)) }
    }

    private fun ensureCacheIgnored() {
        val cacheDir = File(rootDir, ".codeup/cache")
        cacheDir.mkdirs()
        val gitignore = File(cacheDir, ".gitignore")
        if (!gitignore.exists()) runCatching { gitignore.writeText("*\n!.gitignore\n") }
    }

    private fun migrateLegacyIfPresent() {
        val legacy = File(rootDir, ".codeup/cache/analysis.json")
        if (!legacy.exists()) return
        try {
            @Suppress("UNCHECKED_CAST")
            val parsed = mapper.readValue<Map<String, Any?>>(legacy)
            @Suppress("UNCHECKED_CAST")
            val entries = parsed["entries"] as? Map<String, Map<String, Any?>> ?: return
            for ((k, v) in entries) {
                val findings: List<ReportedFinding> = mapper.convertValue(v["findings"] ?: emptyList<Any>(), mapper.typeFactory.constructCollectionType(List::class.java, ReportedFinding::class.java))
                writeEntryToDisk(k, CacheEntry(k, v["analyzedAt"] as? String ?: Instant.now().toString(), findings))
            }
            legacy.delete()
        } catch (e: Exception) { /* best-effort */ }
    }
}