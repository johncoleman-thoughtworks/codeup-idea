package com.codeup.scan

import com.codeup.analyzer.AnalysisCache
import com.codeup.findings.FindingsStore
import com.codeup.knowledge.KnowledgeStore
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File

@Service(Service.Level.PROJECT)
class CodeupProjectService(val project: Project) {
    val findingsStore: FindingsStore = FindingsStore.getInstance(project)
    val knowledgeStore: KnowledgeStore = KnowledgeStore.getInstance(project)
    var analysisCache: AnalysisCache? = null
        private set

    init {
        val basePath = project.basePath
        if (basePath != null) {
            val root = File(basePath)
            findingsStore.init(root)
            knowledgeStore.init(root)
            analysisCache = AnalysisCache(root).also { it.load() }
        }
    }

    fun dispose() {
        findingsStore.dispose()
        knowledgeStore.dispose()
    }

    companion object {
        fun getInstance(project: Project): CodeupProjectService = project.getService(CodeupProjectService::class.java)
    }
}
