package com.codeup.findings

import com.codeup.model.*
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

object FindingYamlMapper {

    private fun dumperOptions() = DumperOptions().also {
        it.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        it.width = 100
        it.isAllowUnicode = true
        it.isPrettyFlow = true
    }

    fun serialize(finding: Finding): String {
        val map = LinkedHashMap<String, Any?>()
        map["schemaVersion"] = finding.schemaVersion
        map["id"] = finding.id
        map["category"] = finding.category
        map["severity"] = finding.severity.name
        map["status"] = finding.status.name
        map["priority"] = finding.priority.name
        val loc = LinkedHashMap<String, Any?>()
        loc["file"] = finding.location.file
        if (finding.location.line != null) loc["line"] = finding.location.line
        if (finding.location.endLine != null) loc["endLine"] = finding.location.endLine
        if (finding.location.astPath != null) loc["astPath"] = finding.location.astPath
        if (finding.location.contentHash != null) loc["contentHash"] = finding.location.contentHash
        map["location"] = loc
        map["explanation"] = finding.explanation
        if (finding.suggestedRemediation != null) map["suggestedRemediation"] = finding.suggestedRemediation
        map["detectedAt"] = finding.detectedAt
        map["detectedBy"] = finding.detectedBy
        if (finding.confidence != null) map["confidence"] = finding.confidence
        map["history"] = finding.history.map { h ->
            val hm = LinkedHashMap<String, Any?>()
            hm["timestamp"] = h.timestamp
            hm["event"] = h.event
            if (h.by != null) hm["by"] = h.by
            if (h.from != null) hm["from"] = h.from
            if (h.to != null) hm["to"] = h.to
            if (h.note != null) hm["note"] = h.note
            hm
        }
        return Yaml(dumperOptions()).dump(map)
    }

    fun deserialize(yamlText: String): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return Yaml().load(yamlText) as? Map<String, Any?> ?: emptyMap()
    }
}