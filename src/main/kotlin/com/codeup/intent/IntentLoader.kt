package com.codeup.intent

import org.yaml.snakeyaml.Yaml
import java.io.File

object IntentLoader {
    @Suppress("UNCHECKED_CAST")
    fun load(rootDir: File): IntentConfig? {
        val file = File(rootDir, ".codeup/intent.yaml")
        if (!file.exists()) return null
        return try {
            val doc = Yaml().load<Map<String, Any?>>(file.readText()) ?: return null
            val layerList = doc["layers"] as? List<Map<String, Any?>> ?: return null
            val layers = layerList.map { l ->
                LayerRule(
                    layer = l["layer"] as? String ?: "",
                    match = l["match"] as? String ?: "",
                    cannotDependOn = (l["cannotDependOn"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                )
            }
            IntentConfig(layers)
        } catch (e: Exception) { null }
    }
}