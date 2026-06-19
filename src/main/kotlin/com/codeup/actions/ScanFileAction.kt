package com.codeup.actions

import com.codeup.scan.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileEditorManager

class ScanFileAction : AnAction("Scan Current File") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
            ?: return
        ScanRunner(project).run(ScanOptions(ScanScope.FILE, fileUri = file, skipCostPrompt = true))
    }
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = e.project != null }
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}