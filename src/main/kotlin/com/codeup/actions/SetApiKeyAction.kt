package com.codeup.actions

import com.codeup.security.ApiKeyManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ui.Messages

class SetApiKeyAction : AnAction("Set Anthropic API Key") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val key = Messages.showPasswordDialog(project, "Enter your Anthropic API key:", "Set API Key", null) ?: return
        if (key.isNotBlank()) {
            ApiKeyManager.setApiKey(key.trim())
            Messages.showInfoMessage(project, "API key saved.", "Codeup")
        }
    }
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = e.project != null }
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}