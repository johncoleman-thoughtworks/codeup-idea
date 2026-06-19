package com.codeup.scan

import com.codeup.analyzer.*
import com.codeup.catalogue.CatalogueLoader
import com.codeup.intent.IntentLoader
import com.codeup.model.FileEntry
import com.codeup.model.ProjectIndex
import com.codeup.quality.SizeCheckOptions
import com.codeup.quality.oversizedFiles
import com.codeup.scanner.GraphBuilder
import com.codeup.scanner.scanWorkspace
import com.codeup.settings.CodeupSettings
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.runBlocking
import java.io.File

private const val INPUT_COST_PER_MTOK = 3.0
private const val OUTPUT_COST_PER_MTOK = 15.0
private const val CHARS_PER_TOKEN = 3.6

class ScanRunner(private val project: Project) {

    fun run(opts: ScanOptions) {
        val rootPath = project.basePath ?: run {
            Messages.showWarningDialog(project, "No project root found.", "Codeup")
            return
        }
        val rootDir = File(rootPath)
        val service = CodeupProjectService.getInstance(project)
        val settings = CodeupSettings.getInstance()
        val state = settings.state

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, scanTitle(opts), true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = "Codeup: indexing project..."

                // Phase 1: deterministic checks
                val index = scanWorkspace(rootDir, indicator)
                if (indicator.isCanceled) return

                val graph = GraphBuilder.buildGraph(index)
                val store = service.findingsStore
                val knowledge = service.knowledgeStore
                val cache = service.analysisCache ?: run {
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Codeup: analysis cache failed to initialize. Check the IDE log for details.", "Codeup")
                    }
                    return
                }

                // Rebind / orphan
                val currentFiles = index.files.associate { it.path to it.contentHash }
                val (rebound, orphaned) = store.rebindOrOrphan(currentFiles)
                if (rebound + orphaned > 0) indicator.text2 = "rebind $rebound moved, $orphaned orphaned"

                // Cycle findings
                val cycles = GraphBuilder.findCycles(graph)
                for (f in com.codeup.intent.cycleFindings(cycles)) store.upsertFromAnalysis(f)

                // Layer violations
                val intent = IntentLoader.load(rootDir)
                if (intent != null) {
                    for (f in com.codeup.intent.layerViolations(graph, intent)) store.upsertFromAnalysis(f)
                }

                // Size check
                val sizeOpts = SizeCheckOptions(
                    warnBytes = state.fileSizeWarnBytes.toLong(),
                    criticalBytes = state.fileSizeCriticalBytes.toLong(),
                )
                for (f in oversizedFiles(index, sizeOpts)) store.upsertFromAnalysis(f)

                if (indicator.isCanceled) return

                // Phase 2: LLM pass
                val catalogue = CatalogueLoader.loadCatalogue(knowledge.patterns.toList())
                val targets = targetsFor(opts, index, catalogue, rootDir)
                if (targets.isEmpty()) {
                    indicator.text = "Codeup: no LLM-analyzable files in scope."
                    return
                }

                // Cost confirmation (on EDT)
                val model = state.model
                val uncached = targets.filter { !cache.hasCachedEntry(it.contentHash, catalogue.hash, model) }
                val shouldPrompt = !opts.skipCostPrompt && uncached.isNotEmpty() &&
                    (opts.scope == ScanScope.FULL || (opts.scope == ScanScope.FILES && uncached.size > 1))

                if (shouldPrompt) {
                    val totalChars = uncached.sumOf { it.size.toInt() }
                    val inputTokens = totalChars / CHARS_PER_TOKEN
                    val cost = (inputTokens * INPUT_COST_PER_MTOK + uncached.size * 500 * OUTPUT_COST_PER_MTOK) / 1_000_000
                    val msg = "Codeup: scan ${uncached.size} files via anthropic (~${inputTokens.toInt().format()} input tokens). Estimated cost: \$${"%.2f".format(cost)}. Proceed?"
                    var proceed = false
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait {
                        proceed = Messages.showOkCancelDialog(project, msg, "Codeup Scan", "Proceed", "Cancel", Messages.getWarningIcon()) == Messages.OK
                    }
                    if (!proceed) return
                }

                val apiKey = com.codeup.security.ApiKeyManager.getApiKey()
                if (apiKey == null) {
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Anthropic API key not set. Use Tools > Codeup > Set Anthropic API Key.", "Codeup")
                    }
                    return
                }
                val client = AnthropicClient(apiKeyProvider = { apiKey }, modelId = model)

                var done = 0
                for (entry in targets) {
                    if (indicator.isCanceled) break
                    indicator.fraction = done.toDouble() / targets.size
                    indicator.text2 = "${done + 1}/${targets.size} • ${entry.path}"
                    try {
                        val file = File(rootDir, entry.path)
                        val bytes = file.readBytes()
                        val neighborFiles = gatherNeighbors(rootDir, index, graph, entry)
                        runBlocking {
                            analyzeFile(
                                rootDir = rootDir, entry = entry, bytes = bytes,
                                catalogue = catalogue, client = client, store = store, cache = cache,
                                neighbors = neighborFiles,
                                dismissals = knowledge.allDismissals.toList(),
                                exemplars = knowledge.allExemplars.toList(),
                                cancellationCheck = { indicator.isCanceled },
                            )
                        }
                    } catch (e: CancellationException) {
                        break
                    } catch (e: Exception) {
                        indicator.text2 = "ERROR: ${e.message}"
                    }
                    done++
                }
            }
        })
    }

    private fun targetsFor(opts: ScanOptions, index: ProjectIndex, catalogue: com.codeup.model.Catalogue, rootDir: File): List<FileEntry> {
        val supported = index.files.filter { CatalogueLoader.patternsForLanguage(catalogue, it.language).isNotEmpty() }
        return when (opts.scope) {
            ScanScope.FULL -> supported
            ScanScope.FILE -> {
                val path = opts.fileUri?.path ?: return emptyList()
                val rel = path.removePrefix(rootDir.canonicalPath + "/")
                supported.filter { it.path == rel }
            }
            ScanScope.FILES -> {
                val wanted = opts.fileUris?.map { it.path.removePrefix(rootDir.canonicalPath + "/") }?.toSet() ?: return emptyList()
                supported.filter { it.path in wanted }
            }
        }
    }

    private fun gatherNeighbors(rootDir: File, index: ProjectIndex, graph: com.codeup.scanner.DependencyGraph, entry: FileEntry): List<NeighborFile> {
        val (imports, importedBy) = GraphBuilder.neighborsOf(graph, entry.path)
        val picks = mutableListOf<Pair<String, String>>()
        val iA = imports.take(MAX_NEIGHBORS); val iB = importedBy.take(MAX_NEIGHBORS)
        var ai = 0; var bi = 0
        while (picks.size < MAX_NEIGHBORS && (ai < iA.size || bi < iB.size)) {
            if (ai < iA.size) { picks.add(iA[ai] to "imports"); ai++ }
            if (picks.size < MAX_NEIGHBORS && bi < iB.size) { picks.add(iB[bi] to "importedBy"); bi++ }
        }
        if (picks.size < MAX_NEIGHBORS) {
            val taken = (picks.map { it.first } + entry.path).toSet()
            val dir = entry.path.substringBeforeLast("/", "")
            val siblings = index.files.filter { it.path !in taken && it.path.substringBeforeLast("/", "") == dir && it.language == entry.language }.take(MAX_NEIGHBORS - picks.size)
            siblings.forEach { picks.add(it.path to "samePackage") }
        }
        val byPath = index.files.associateBy { it.path }
        val out = mutableListOf<NeighborFile>()
        for ((path, relation) in picks) {
            val nEntry = byPath[path] ?: continue
            try {
                val text = File(rootDir, path).readText()
                out.add(NeighborFile(path, nEntry.language, text.take(MAX_NEIGHBOR_CHARS), relation))
            } catch (e: Exception) { /* skip */ }
        }
        return out
    }

    private fun scanTitle(opts: ScanOptions) = when (opts.scope) {
        ScanScope.FULL -> "Codeup: scanning workspace"
        ScanScope.FILES -> "Codeup: scanning ${opts.fileUris?.size ?: 0} open tab(s)"
        ScanScope.FILE -> "Codeup: scanning ${opts.fileUri?.name ?: "file"}"
    }

    private fun Int.format() = String.format("%,d", this)
}
