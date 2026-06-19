package com.codeup.settings

data class CodeupSettingsState(
    var model: String = "claude-sonnet-4-6",
    var scanOnSave: Boolean = false,
    var findingsDir: String = ".codeup/findings",
    var fileSizeWarnBytes: Int = 30_000,
    var fileSizeCriticalBytes: Int = 60_000,
    var updateCheckEnabled: Boolean = true,
    var updateCheckIntervalHours: Int = 24,
)