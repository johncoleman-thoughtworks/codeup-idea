package com.codeup.scan

import com.intellij.openapi.vfs.VirtualFile

enum class ScanScope { FULL, FILE, FILES }

data class ScanOptions(
    val scope: ScanScope,
    val fileUri: VirtualFile? = null,
    val fileUris: List<VirtualFile>? = null,
    val skipCostPrompt: Boolean = false,
)