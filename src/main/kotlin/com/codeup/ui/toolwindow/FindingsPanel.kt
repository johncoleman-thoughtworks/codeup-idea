package com.codeup.ui.toolwindow

import com.codeup.findings.FindingsStore
import com.codeup.model.Finding
import com.codeup.model.Severity
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.SimpleTree
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*
import javax.swing.tree.*

class FindingsPanel(private val project: Project) : JPanel(BorderLayout()) {

    val treeModel = FindingsTreeModel()
    private val tree = SimpleTree(treeModel)

    init {
        tree.isRootVisible = false
        tree.cellRenderer = FindingsCellRenderer()
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) onDoubleClick()
            }
        })
        add(buildToolbar(), BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)
        refresh()

        // Subscribe to store changes
        FindingsStore.getInstance(project).addChangeListener { SwingUtilities.invokeLater { refresh() } }
    }

    fun refresh() {
        val findings = FindingsStore.getInstance(project).all
        treeModel.update(findings)
        expandAll()
    }

    private fun expandAll() {
        for (i in 0 until tree.rowCount) tree.expandRow(i)
    }

    private fun buildToolbar(): JToolBar {
        val toolbar = JToolBar()
        toolbar.isFloatable = false

        val refreshBtn = JButton(AllIcons.Actions.Refresh)
        refreshBtn.toolTipText = "Refresh Findings"
        refreshBtn.addActionListener { refresh() }
        toolbar.add(refreshBtn)

        toolbar.addSeparator()

        val groupByLabel = JLabel("Group by:")
        toolbar.add(groupByLabel)
        val combo = JComboBox(arrayOf("Severity", "Category", "Status"))
        combo.addActionListener {
            treeModel.groupBy = when (combo.selectedIndex) { 1 -> GroupBy.CATEGORY; 2 -> GroupBy.STATUS; else -> GroupBy.SEVERITY }
        }
        toolbar.add(combo)

        return toolbar
    }

    private fun onDoubleClick() {
        val path = tree.selectionPath ?: return
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val nodeObj = node.userObject as? FindingsTreeNode.Leaf ?: return
        navigateTo(nodeObj.finding)
    }

    private fun navigateTo(finding: Finding) {
        val rootPath = project.basePath ?: return
        val file = LocalFileSystem.getInstance().findFileByIoFile(File(rootPath, finding.location.file)) ?: return
        val line = (finding.location.line ?: 1) - 1
        OpenFileDescriptor(project, file, line, 0).navigate(true)
    }
}

class FindingsCellRenderer : DefaultTreeCellRenderer() {
    override fun getTreeCellRendererComponent(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): java.awt.Component {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
        val node = (value as? DefaultMutableTreeNode)?.userObject ?: return this
        when (node) {
            is FindingsTreeNode.Group -> {
                text = "${node.label} (${node.children.size})"
                icon = null
            }
            is FindingsTreeNode.Leaf -> {
                val f = node.finding
                text = "${f.category} — ${f.location.file}${f.location.line?.let { ":$it" } ?: ""}"
                icon = when (f.severity) {
                    Severity.high -> AllIcons.General.Error
                    Severity.medium -> AllIcons.General.Warning
                    Severity.low -> AllIcons.General.Information
                }
                toolTipText = f.explanation
            }
        }
        return this
    }
}
