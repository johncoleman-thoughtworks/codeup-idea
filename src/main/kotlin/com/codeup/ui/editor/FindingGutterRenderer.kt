package com.codeup.ui.editor

import com.codeup.model.Finding
import com.codeup.model.Severity
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import javax.swing.Icon

class FindingGutterRenderer(val finding: Finding) : GutterIconRenderer() {
    override fun getIcon(): Icon = when (finding.severity) {
        Severity.high -> AllIcons.General.Error
        Severity.medium -> AllIcons.General.Warning
        Severity.low -> AllIcons.General.Information
    }
    override fun getTooltipText() = "[Codeup] ${finding.category}: ${finding.explanation.take(200)}"
    override fun equals(other: Any?) = other is FindingGutterRenderer && other.finding.id == finding.id
    override fun hashCode() = finding.id.hashCode()
    override fun getAlignment() = Alignment.RIGHT
}