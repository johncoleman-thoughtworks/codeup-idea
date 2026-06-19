package com.codeup.model

data class DismissalEntry(
    val schemaVersion: Int = 1,
    val id: String,
    val category: String,
    val filePathPattern: String,
    val rationale: String,
    val dismissedAt: String,
    val dismissedBy: String,
    val originalFindingId: String,
)

data class ExemplarEntry(
    val schemaVersion: Int = 1,
    val id: String,
    val category: String,
    val filePath: String,
    val excerpt: String,
    val confirmedAt: String,
    val confirmedBy: String,
    val originalFindingId: String,
)