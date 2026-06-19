package com.codeup.ui.editor

import com.codeup.findings.FindingsStore
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project

class FindingsEditorListener(private val project: Project) : FileEditorManagerListener {

    init {
        FindingsStore.getInstance(project).addChangeListener {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                FindingsHighlighter.applyToProject(project)
            }
        }
    }

    override fun fileOpened(source: FileEditorManager, file: com.intellij.openapi.vfs.VirtualFile) {
        val rootPath = project.basePath ?: return
        val store = FindingsStore.getInstance(project)
        for (editor in source.getEditors(file)) {
            val textEditor = editor as? TextEditor ?: continue
            FindingsHighlighter.applyForFile(project, file, store, rootPath, textEditor.editor)
        }
    }
}