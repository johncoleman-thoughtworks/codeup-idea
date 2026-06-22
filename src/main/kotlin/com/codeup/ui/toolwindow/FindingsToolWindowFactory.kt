package com.codeup.ui.toolwindow

import com.codeup.scan.CodeupProjectService
import com.codeup.util.UpdateChecker
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class FindingsToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Ensure project service is initialized
        CodeupProjectService.getInstance(project)
        val panel = FindingsPanel(project)
        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)

        // Start update checker
        Thread {
            UpdateChecker(project, "1.0.3").checkOnActivation()
        }.also { it.isDaemon = true }.start()
    }
}
