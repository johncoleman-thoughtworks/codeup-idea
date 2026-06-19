package com.codeup.ui.toolwindow

import com.codeup.model.Finding
import com.codeup.model.Severity
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

enum class GroupBy { SEVERITY, CATEGORY, STATUS }

class FindingsTreeModel : DefaultTreeModel(DefaultMutableTreeNode("Codeup")) {

    var groupBy: GroupBy = GroupBy.SEVERITY
        set(value) { field = value; rebuildTree(lastFindings) }

    private var lastFindings: List<Finding> = emptyList()

    private val SEVERITY_ORDER = mapOf(Severity.high to 0, Severity.medium to 1, Severity.low to 2)

    fun update(findings: List<Finding>) {
        lastFindings = findings
        rebuildTree(findings)
    }

    private fun rebuildTree(findings: List<Finding>) {
        val rootNode = root as DefaultMutableTreeNode
        rootNode.removeAllChildren()

        val active = findings.filter { it.status.name != "fixed" && it.status.name != "dismissed" }
        val orphans = active.filter { it.location.file.startsWith("__orphan__/") }
        val live = active.filter { !it.location.file.startsWith("__orphan__/") }

        val groups = live.groupBy { f ->
            when (groupBy) {
                GroupBy.SEVERITY -> f.severity.name
                GroupBy.CATEGORY -> f.category
                GroupBy.STATUS -> f.status.name
            }
        }
        val sorted = groups.entries.sortedWith(Comparator { a, b ->
            if (groupBy == GroupBy.SEVERITY) {
                val ao = SEVERITY_ORDER[Severity.values().find { it.name == a.key }] ?: 99
                val bo = SEVERITY_ORDER[Severity.values().find { it.name == b.key }] ?: 99
                ao.compareTo(bo)
            } else a.key.compareTo(b.key)
        })
        for ((label, children) in sorted) {
            val groupNode = DefaultMutableTreeNode(FindingsTreeNode.Group(label, children))
            val sortedChildren = children.sortedBy { SEVERITY_ORDER[it.severity] ?: 99 }
            for (f in sortedChildren) groupNode.add(DefaultMutableTreeNode(FindingsTreeNode.Leaf(f)))
            rootNode.add(groupNode)
        }
        if (orphans.isNotEmpty()) {
            val orphanGroup = DefaultMutableTreeNode(FindingsTreeNode.Group("orphaned", orphans))
            for (f in orphans) orphanGroup.add(DefaultMutableTreeNode(FindingsTreeNode.Leaf(f)))
            rootNode.add(orphanGroup)
        }
        reload()
    }
}
