package com.codeup.knowledge

import com.codeup.model.DismissalEntry
import com.codeup.model.ExemplarEntry
import java.security.MessageDigest

fun KnowledgeRetriever_hash(dismissals: List<DismissalEntry>, exemplars: List<ExemplarEntry>): String {
    val blob = dismissals.joinToString("|") { "${it.category}:${it.filePathPattern}:${it.rationale}" } +
        "||" + exemplars.joinToString("|") { "${it.category}:${it.filePath}:${it.excerpt}" }
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(blob.toByteArray()).joinToString("") { "%02x".format(it) }.substring(0, 16)
}