package com.codeup.actions

import com.codeup.util.UpdateChecker
import com.intellij.openapi.actionSystem.*

class CheckUpdatesAction : AnAction("Check for Updates") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        UpdateChecker(project, "1.0.2").checkNow()
    }
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = e.project != null }
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}