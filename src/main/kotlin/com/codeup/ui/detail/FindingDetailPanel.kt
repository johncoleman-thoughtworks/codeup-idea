package com.codeup.ui.detail

import com.codeup.findings.FindingsStore
import com.codeup.knowledge.KnowledgeStore
import com.codeup.model.Finding
import com.codeup.model.Status
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.io.File
import javax.swing.*
import javax.swing.event.HyperlinkEvent

class FindingDetailPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val contentPane = JEditorPane("text/html", "<html><body><p>Select a finding to view details.</p></body></html>")
    private var currentFindingId: String? = null

    init {
        contentPane.isEditable = false
        contentPane.addHyperlinkListener { e ->
            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                handleAction(e.description ?: "")
            }
        }
        add(JBScrollPane(contentPane), BorderLayout.CENTER)

        FindingsStore.getInstance(project).addChangeListener {
            SwingUtilities.invokeLater { refreshCurrent() }
        }
    }

    fun show(finding: Finding) {
        currentFindingId = finding.id
        render(finding)
    }

    private fun refreshCurrent() {
        val id = currentFindingId ?: return
        val updated = FindingsStore.getInstance(project).get(id) ?: return
        render(updated)
    }

    private fun render(f: Finding) {
        contentPane.text = buildHtml(f)
        contentPane.caretPosition = 0
    }

    private fun buildHtml(f: Finding): String {
        val historyRows = f.history.joinToString("") { h ->
            "<tr><td>${esc(h.timestamp)}</td><td>${esc(h.event)}</td><td>${esc(listOfNotNull(h.from, h.to).joinToString(" → "))}</td><td>${esc(h.note ?: "")}</td></tr>"
        }
        val confidencePart = f.confidence?.let { " &nbsp;|&nbsp; <b>Confidence:</b> ${"%.0f%%".format(it * 100)}" } ?: ""
        val remediationSection = if (f.suggestedRemediation != null)
            "<p><b>Remediation:</b> ${md(f.suggestedRemediation)}</p>" else ""
        val historySection = if (historyRows.isNotEmpty()) """
            <h3>History</h3>
            <table border='0' cellpadding='4' cellspacing='0' width='100%' style='font-size:10px;'>
              <tr style='color:gray;'><th align='left'>When</th><th align='left'>Event</th><th align='left'>Change</th><th align='left'>Note</th></tr>
              $historyRows
            </table>
        """ else ""
        return """
            <html><body style='font-family:sans-serif;padding:12px;'>
            <h2>${esc(f.category)}</h2>
            <p>
              <b>Severity:</b> ${f.severity.name} &nbsp;|&nbsp;
              <b>Status:</b> ${f.status.name} &nbsp;|&nbsp;
              <b>Priority:</b> ${f.priority.name}$confidencePart
            </p>
            <p><b>File:</b> ${esc(f.location.file)}${f.location.line?.let { ":$it" } ?: ""}</p>
            <hr>
            <p>${md(f.explanation)}</p>
            $remediationSection
            <hr>
            <p><a href='open'>↗ Open Code</a> &nbsp;&nbsp; ${actionButtons(f.status)}</p>
            $historySection
            <p style='color:gray;font-size:10px;'>Detected by ${esc(f.detectedBy)} at ${esc(f.detectedAt)}</p>
            </body></html>
        """.trimIndent()
    }

    private fun actionButtons(status: Status): String = when (status) {
        Status.unconfirmed -> "<a href='confirm'>✓ Confirm</a> &nbsp; <a href='dismiss'>✗ Dismiss…</a> &nbsp; <a href='fixed'>✔ Mark Fixed</a>"
        Status.confirmed   -> "<a href='dismiss'>✗ Dismiss…</a> &nbsp; <a href='fixed'>✔ Mark Fixed</a> &nbsp; <a href='reopen'>↺ Reopen</a>"
        Status.dismissed   -> "<i style='color:gray;'>✓ Dismissed — see history below.</i> &nbsp; <a href='reopen'>↺ Reopen</a>"
        Status.fixed       -> "<i style='color:gray;'>✓ Marked fixed.</i> &nbsp; <a href='reopen'>↺ Reopen</a>"
    }

    private fun handleAction(action: String) {
        val id = currentFindingId ?: return
        val store = FindingsStore.getInstance(project)
        val f = store.get(id) ?: return
        val knowledge = KnowledgeStore.getInstance(project)
        when (action) {
            "open" -> navigateTo(f)
            "confirm" -> {
                store.updateStatus(id, Status.confirmed)
                knowledge.recordExemplar(f.category, f.location.file, f.explanation, id)
            }
            "dismiss" -> {
                val note = JOptionPane.showInputDialog(this, "Why is this being dismissed?", "Dismiss Finding", JOptionPane.QUESTION_MESSAGE)
                if (note != null) {
                    store.updateStatus(id, Status.dismissed, note)
                    knowledge.recordDismissal(f.category, f.location.file, note, id)
                }
            }
            "fixed" -> store.updateStatus(id, Status.fixed)
            "reopen" -> store.updateStatus(id, Status.unconfirmed, "reopened")
        }
        // store.updateStatus fires change event → refreshCurrent() is called via listener
    }

    private fun navigateTo(finding: Finding) {
        val rootPath = project.basePath ?: return
        val file = LocalFileSystem.getInstance().findFileByIoFile(File(rootPath, finding.location.file)) ?: return
        val line = (finding.location.line ?: 1) - 1
        OpenFileDescriptor(project, file, line, 0).navigate(true)
    }

    private fun md(s: String) = esc(s).replace("\n\n", "</p><p>").replace("\n", "<br/>")
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}