package com.codeup.ui.statusbar

import com.codeup.findings.FindingsStore
import com.codeup.model.Severity
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.*
import java.awt.event.MouseEvent

class CodeupStatusBarWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.TextPresentation {

    private var statusBar: StatusBar? = null
    @Volatile var scanning = false
    private val listener = FindingsStore.ChangeListener { update() }

    override fun ID() = "com.codeup.statusbar"
    override fun getPresentation() = this
    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        FindingsStore.getInstance(project).addChangeListener(listener)
        update()
    }
    override fun dispose() {
        FindingsStore.getInstance(project).removeChangeListener(listener)
    }

    override fun getText(): String {
        val findings = FindingsStore.getInstance(project).all.filter { it.status.name != "fixed" && it.status.name != "dismissed" }
        val high = findings.count { it.severity == Severity.high }
        val icon = if (scanning) "⟳" else "⌕"
        return "$icon Codeup: ${findings.size}${if (high > 0) " (⊘ $high)" else ""}"
    }

    override fun getTooltipText() = "${FindingsStore.getInstance(project).all.filter { it.status.name != "fixed" && it.status.name != "dismissed" }.size} open findings • click to open panel"

    override fun getClickConsumer() = com.intellij.util.Consumer<MouseEvent> {
        com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("Codeup")?.show()
    }

    override fun getAlignment() = 0.0f

    fun update() {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            statusBar?.updateWidget(ID())
        }
    }
}