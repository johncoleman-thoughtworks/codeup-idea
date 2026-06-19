package com.codeup.model

data class FileEntry(
    val path: String,
    val language: String,
    val size: Long,
    val contentHash: String,
    val mtime: Long,
    val rawImports: List<String> = emptyList(),
)

data class ProjectIndex(
    val schemaVersion: Int = 1,
    val generatedAt: String,
    val rootName: String,
    val files: List<FileEntry>,
)