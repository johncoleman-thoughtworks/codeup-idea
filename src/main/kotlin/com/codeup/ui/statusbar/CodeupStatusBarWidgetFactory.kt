package com.codeup.ui.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class CodeupStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = "com.codeup.statusbar"
    override fun getDisplayName() = "Codeup"
    override fun isAvailable(project: Project) = true
    override fun createWidget(project: Project) = CodeupStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = widget.dispose()
    override fun canBeEnabledOn(statusBar: com.intellij.openapi.wm.StatusBar) = true
}