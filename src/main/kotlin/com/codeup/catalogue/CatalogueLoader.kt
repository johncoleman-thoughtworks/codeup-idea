package com.codeup.catalogue

import com.codeup.model.Catalogue
import com.codeup.model.CataloguePattern
import org.yaml.snakeyaml.Yaml
import java.security.MessageDigest

object CatalogueLoader {
    private var defaultRaw: String? = null
    private var defaultPatterns: List<CataloguePattern>? = null

    fun loadCatalogue(workspaceOverrides: List<CataloguePattern> = emptyList()): Catalogue {
        if (defaultRaw == null || defaultPatterns == null) {
            val stream = CatalogueLoader::class.java.classLoader
                .getResourceAsStream("catalogue/default.yaml")
                ?: error("catalogue/default.yaml not found in resources")
            defaultRaw = stream.use { it.readBytes().toString(Charsets.UTF_8) }
            defaultPatterns = parsePatterns(defaultRaw!!)
        }
        val merged = mergePatterns(defaultPatterns!!, workspaceOverrides)
        val overrideBlob = if (workspaceOverrides.isEmpty()) ""
        else workspaceOverrides.joinToString("|") { "${it.id}:${it.hint}:${it.defaultSeverity}:${it.languages}" }
        val hash = sha256((defaultRaw!! + "|" + overrideBlob).toByteArray()).substring(0, 16)
        return Catalogue(merged, hash)
    }

    fun mergePatterns(base: List<CataloguePattern>, overrides: List<CataloguePattern>): List<CataloguePattern> {
        if (overrides.isEmpty()) return base.toList()
        val byId = LinkedHashMap<String, CataloguePattern>(base.associateBy { it.id })
        for (o in overrides) byId[o.id] = o
        return byId.values.toList()
    }

    fun patternsForLanguage(catalogue: Catalogue, language: String): List<CataloguePattern> =
        catalogue.patterns.filter { it.languages.contains(language) }

    @Suppress("UNCHECKED_CAST")
    private fun parsePatterns(yaml: String): List<CataloguePattern> {
        val doc = Yaml().load<Map<String, Any>>(yaml)
        val patterns = doc["patterns"] as? List<Map<String, Any>> ?: emptyList()
        return patterns.map { p ->
            CataloguePattern(
                id = p["id"] as String,
                name = p["name"] as String,
                languages = (p["languages"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                defaultSeverity = p["defaultSeverity"] as? String ?: "medium",
                hint = (p["hint"] as? String ?: "").trim(),
            )
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}