package com.codeup.actions

import com.codeup.scan.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileEditorManager

class ScanOpenTabsAction : AnAction("Scan Open Tabs") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = FileEditorManager.getInstance(project).openFiles.toList()
        if (files.isEmpty()) return
        ScanRunner(project).run(ScanOptions(ScanScope.FILES, fileUris = files))
    }
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = e.project != null }
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}