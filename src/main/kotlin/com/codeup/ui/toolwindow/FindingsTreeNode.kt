package com.codeup.ui.toolwindow

import com.codeup.model.Finding

sealed class FindingsTreeNode {
    data class Root(val name: String, val children: List<Finding>) : FindingsTreeNode()
    data class Group(val label: String, val children: List<Finding>) : FindingsTreeNode()
    data class Leaf(val finding: Finding) : FindingsTreeNode()
}
