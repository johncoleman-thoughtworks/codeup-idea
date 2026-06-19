package com.codeup.actions

import com.codeup.findings.FindingsStore
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.wm.ToolWindowManager

class RefreshAction : AnAction("Refresh Findings") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        FindingsStore.getInstance(project).reloadAll()
    }
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = e.project != null }
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}