package com.codeup.actions

import com.codeup.scan.ScanOptions
import com.codeup.scan.ScanRunner
import com.codeup.scan.ScanScope
import com.intellij.openapi.actionSystem.*

class ScanFullAction : AnAction("Run Full Scan") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ScanRunner(project).run(ScanOptions(ScanScope.FULL))
    }
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = e.project != null }
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}