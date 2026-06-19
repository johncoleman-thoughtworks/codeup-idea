package com.codeup.ui.editor

import com.codeup.findings.FindingsStore
import com.codeup.model.Finding
import com.codeup.model.Severity
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font

object FindingsHighlighter {

    private val highlightersByFile = mutableMapOf<String, MutableList<RangeHighlighter>>()

    private fun attrsFor(severity: Severity): TextAttributes {
        return when (severity) {
            Severity.high -> TextAttributes(JBColor(Color(255, 0, 0, 30), Color(180, 0, 0, 40)), null, null, null, Font.PLAIN)
                .also { it.errorStripeColor = JBColor.RED }
            Severity.medium -> TextAttributes(JBColor(Color(255, 165, 0, 30), Color(180, 120, 0, 40)), null, null, null, Font.PLAIN)
                .also { it.errorStripeColor = JBColor.ORANGE }
            Severity.low -> TextAttributes(JBColor(Color(0, 100, 255, 20), Color(0, 80, 200, 30)), null, null, null, Font.PLAIN)
                .also { it.errorStripeColor = JBColor.BLUE }
        }
    }

    fun applyToEditor(editor: Editor, findings: List<Finding>) {
        val model = editor.markupModel
        val fileKey = editor.virtualFile?.path ?: return

        highlightersByFile[fileKey]?.forEach { runCatching { model.removeHighlighter(it) } }
        highlightersByFile[fileKey] = mutableListOf()

        val docLineCount = editor.document.lineCount
        for (f in findings) {
            if (f.status.name == "fixed" || f.status.name == "dismissed") continue
            val line = ((f.location.line ?: 1) - 1).coerceIn(0, docLineCount - 1)
            try {
                val h = model.addLineHighlighter(line, HighlighterLayer.SYNTAX + 1, attrsFor(f.severity))
                h.errorStripeTooltip = "${f.category}: ${f.explanation.take(100)}"
                h.gutterIconRenderer = FindingGutterRenderer(f)
                highlightersByFile[fileKey]?.add(h)
            } catch (e: Exception) { /* line out of bounds or disposed */ }
        }
    }

    fun applyToProject(project: Project) {
        val store = FindingsStore.getInstance(project)
        val rootPath = project.basePath ?: return
        for (editor in FileEditorManager.getInstance(project).allEditors) {
            val vf = (editor as? com.intellij.openapi.fileEditor.TextEditor)?.file ?: continue
            applyForFile(project, vf, store, rootPath, editor.editor)
        }
    }

    fun applyForFile(project: Project, vf: VirtualFile, store: FindingsStore, rootPath: String, editor: Editor) {
        val rel = vf.path.removePrefix("$rootPath/")
        val findings = store.all.filter { it.location.file == rel }
        applyToEditor(editor, findings)
    }
}