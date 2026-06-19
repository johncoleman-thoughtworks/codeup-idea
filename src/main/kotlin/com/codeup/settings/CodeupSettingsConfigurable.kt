package com.codeup.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.*
import com.intellij.util.ui.FormBuilder
import javax.swing.*

class CodeupSettingsConfigurable : Configurable {

    private val modelField = JBTextField()
    private val scanOnSaveBox = JBCheckBox("Run incremental scan on file save")
    private val findingsDirField = JBTextField()
    private val warnBytesField = JBTextField()
    private val criticalBytesField = JBTextField()
    private val updateCheckBox = JBCheckBox("Check GitHub Releases for new versions on startup")
    private val updateIntervalField = JBTextField()

    override fun getDisplayName() = "Codeup"

    override fun createComponent(): JComponent {
        val settings = CodeupSettings.getInstance().state
        modelField.text = settings.model
        scanOnSaveBox.isSelected = settings.scanOnSave
        findingsDirField.text = settings.findingsDir
        warnBytesField.text = settings.fileSizeWarnBytes.toString()
        criticalBytesField.text = settings.fileSizeCriticalBytes.toString()
        updateCheckBox.isSelected = settings.updateCheckEnabled
        updateIntervalField.text = settings.updateCheckIntervalHours.toString()

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Anthropic model:", modelField)
            .addComponent(scanOnSaveBox)
            .addLabeledComponent("Findings directory (relative to project root):", findingsDirField)
            .addLabeledComponent("File size warn threshold (bytes):", warnBytesField)
            .addLabeledComponent("File size critical threshold (bytes):", criticalBytesField)
            .addComponent(updateCheckBox)
            .addLabeledComponent("Update check interval (hours):", updateIntervalField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val s = CodeupSettings.getInstance().state
        return modelField.text != s.model ||
            scanOnSaveBox.isSelected != s.scanOnSave ||
            findingsDirField.text != s.findingsDir ||
            warnBytesField.text != s.fileSizeWarnBytes.toString() ||
            criticalBytesField.text != s.fileSizeCriticalBytes.toString() ||
            updateCheckBox.isSelected != s.updateCheckEnabled ||
            updateIntervalField.text != s.updateCheckIntervalHours.toString()
    }

    override fun apply() {
        val s = CodeupSettings.getInstance().state
        s.model = modelField.text.trim()
        s.scanOnSave = scanOnSaveBox.isSelected
        s.findingsDir = findingsDirField.text.trim()
        s.fileSizeWarnBytes = warnBytesField.text.trim().toIntOrNull() ?: 30_000
        s.fileSizeCriticalBytes = criticalBytesField.text.trim().toIntOrNull() ?: 60_000
        s.updateCheckEnabled = updateCheckBox.isSelected
        s.updateCheckIntervalHours = updateIntervalField.text.trim().toIntOrNull() ?: 24
    }

    override fun reset() {
        val s = CodeupSettings.getInstance().state
        modelField.text = s.model
        scanOnSaveBox.isSelected = s.scanOnSave
        findingsDirField.text = s.findingsDir
        warnBytesField.text = s.fileSizeWarnBytes.toString()
        criticalBytesField.text = s.fileSizeCriticalBytes.toString()
        updateCheckBox.isSelected = s.updateCheckEnabled
        updateIntervalField.text = s.updateCheckIntervalHours.toString()
    }
}