package com.codeup.ui.detail

import com.codeup.model.Finding
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.event.HyperlinkEvent

class FindingDetailPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val contentPane = JEditorPane("text/html", "<html><body><p>Select a finding to view details.</p></body></html>")

    init {
        contentPane.isEditable = false
        contentPane.addHyperlinkListener { e ->
            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                handleAction(e.description ?: "")
            }
        }
        add(JBScrollPane(contentPane), BorderLayout.CENTER)
    }

    fun show(finding: Finding) {
        contentPane.text = buildHtml(finding)
        contentPane.caretPosition = 0
    }

    private var currentFinding: Finding? = null

    private fun buildHtml(f: Finding): String {
        currentFinding = f
        return """
            <html><body style='font-family:sans-serif;padding:12px;'>
            <h2>${esc(f.category)}</h2>
            <p><b>Severity:</b> ${f.severity.name} &nbsp;|&nbsp; <b>Status:</b> ${f.status.name} &nbsp;|&nbsp; <b>Confidence:</b> ${f.confidence?.let { "%.0f%%".format(it * 100) } ?: "—"}</p>
            <p><b>File:</b> ${esc(f.location.file)}${f.location.line?.let { ":$it" } ?: ""}</p>
            <hr>
            <p>${esc(f.explanation)}</p>
            ${if (f.suggestedRemediation != null) "<p><b>Remediation:</b> ${esc(f.suggestedRemediation)}</p>" else ""}
            <hr>
            <p>
              <a href='confirm'>✓ Confirm</a> &nbsp;
              <a href='dismiss'>✗ Dismiss</a> &nbsp;
              <a href='fixed'>✔ Mark Fixed</a>
            </p>
            <p style='color:gray;font-size:10px;'>Detected by ${esc(f.detectedBy)} at ${f.detectedAt}</p>
            </body></html>
        """.trimIndent()
    }

    private fun handleAction(action: String) {
        val f = currentFinding ?: return
        val store = com.codeup.findings.FindingsStore.getInstance(project)
        val knowledge = com.codeup.knowledge.KnowledgeStore.getInstance(project)
        when (action) {
            "confirm" -> {
                store.updateStatus(f.id, com.codeup.model.Status.confirmed)
                knowledge.recordExemplar(f.category, f.location.file, f.explanation, f.id)
            }
            "dismiss" -> {
                val note = JOptionPane.showInputDialog(this, "Why is this being dismissed?", "Dismiss Finding", JOptionPane.QUESTION_MESSAGE)
                if (note != null) {
                    store.updateStatus(f.id, com.codeup.model.Status.dismissed, note)
                    knowledge.recordDismissal(f.category, f.location.file, note, f.id)
                }
            }
            "fixed" -> store.updateStatus(f.id, com.codeup.model.Status.fixed)
        }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}