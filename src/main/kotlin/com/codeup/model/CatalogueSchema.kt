package com.codeup.model

data class CataloguePattern(
    val id: String,
    val name: String,
    val languages: List<String>,
    val defaultSeverity: String,
    val hint: String,
)

data class Catalogue(
    val patterns: List<CataloguePattern>,
    val hash: String,
)