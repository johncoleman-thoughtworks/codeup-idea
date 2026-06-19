package com.codeup.actions

import com.codeup.ui.toolwindow.GroupBy
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.wm.ToolWindowManager

private fun setGroupBy(e: AnActionEvent, groupBy: GroupBy) {
    val project = e.project ?: return
    val tw = ToolWindowManager.getInstance(project).getToolWindow("Codeup") ?: return
    val content = tw.contentManager.selectedContent ?: return
    val panel = content.component as? com.codeup.ui.toolwindow.FindingsPanel ?: return
    panel.treeModel.groupBy = groupBy
}

class GroupBySeverityAction : AnAction("Severity") {
    override fun actionPerformed(e: AnActionEvent) = setGroupBy(e, GroupBy.SEVERITY)
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

class GroupByCategoryAction : AnAction("Category") {
    override fun actionPerformed(e: AnActionEvent) = setGroupBy(e, GroupBy.CATEGORY)
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

class GroupByStatusAction : AnAction("Status") {
    override fun actionPerformed(e: AnActionEvent) = setGroupBy(e, GroupBy.STATUS)
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}