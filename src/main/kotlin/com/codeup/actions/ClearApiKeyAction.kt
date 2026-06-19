package com.codeup.actions

import com.codeup.security.ApiKeyManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ui.Messages

class ClearApiKeyAction : AnAction("Clear Anthropic API Key") {
    override fun actionPerformed(e: AnActionEvent) {
        ApiKeyManager.clearApiKey()
        Messages.showInfoMessage(e.project, "API key cleared.", "Codeup")
    }
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = e.project != null }
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}