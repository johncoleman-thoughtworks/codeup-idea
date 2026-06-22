package com.codeup.actions

import com.codeup.analyzer.AnthropicClient
import com.codeup.analyzer.LLMAnalyzeRequest
import com.codeup.analyzer.LLMAnalyzeResponse
import com.codeup.analyzer.ToolDefinition
import com.codeup.analyzer.ToolInputSchema
import com.codeup.model.ProjectIndex
import com.codeup.scanner.DependencyGraph
import com.codeup.scanner.GraphBuilder
import com.codeup.scanner.scanWorkspace
import com.codeup.security.ApiKeyManager
import com.codeup.settings.CodeupSettings
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.*
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.runBlocking
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File

private val PROPOSE_LAYER_RULES_TOOL = ToolDefinition(
    name = "propose_layer_rules",
    description = "Propose architectural layer rules for this project. Each layer is identified by a workspace-relative path prefix; cannotDependOn lists the layers this one must not import from. Use 3-6 layers maximum; only include layers that actually exist as paths in the project. Prefer the most conventional naming: domain, application, infrastructure, web/api, ui. If the project has no obvious layered structure, return 1-2 layers covering its dominant source roots and explain in the notes.",
    input_schema = ToolInputSchema(
        type = "object",
        properties = mapOf(
            "layers" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "layer" to mapOf("type" to "string", "description" to "Short, lowercase layer name (e.g. \"domain\", \"infrastructure\")."),
                        "match" to mapOf("type" to "string", "description" to "Workspace-relative path prefix that identifies this layer. Must be a directory that exists in the provided summary."),
                        "cannotDependOn" to mapOf(
                            "type" to "array",
                            "items" to mapOf("type" to "string"),
                            "description" to "List of other layer names (matching the layer field above) this layer must not import from."
                        )
                    ),
                    "required" to listOf("layer", "match", "cannotDependOn")
                )
            ),
            "notes" to mapOf("type" to "string", "description" to "Optional short explanation of the reasoning. Surfaced as a YAML comment in the generated file.")
        ),
        required = listOf("layers")
    )
)

class SuggestIntentAction : AnAction("Suggest Architectural Intent") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val rootPath = project.basePath ?: return
        val rootDir = File(rootPath)
        val apiKey = ApiKeyManager.getApiKey() ?: run {
            Messages.showErrorDialog(project, "Set an Anthropic API key first.", "Codeup")
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Codeup: drafting intent.yaml", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Scanning project structure..."
                val index = scanWorkspace(rootDir, indicator)
                if (indicator.isCanceled) return
                val graph = GraphBuilder.buildGraph(index)

                val client = AnthropicClient({ apiKey }, CodeupSettings.getInstance().state.model)

                indicator.text = "Asking Claude for layer suggestions..."
                val response = runBlocking {
                    client.analyze(LLMAnalyzeRequest(
                        systemPrompt = buildSystemPrompt(),
                        userPrompt = formatForPrompt(index, graph),
                        tool = PROPOSE_LAYER_RULES_TOOL,
                        maxOutputTokens = 1500,
                        cancellationCheck = { indicator.isCanceled },
                    ))
                }
                if (indicator.isCanceled) return

                val intentYaml = parseLayersResponse(response) ?: buildDefaultIntent(index)
                val targetFile = File(rootDir, ".codeup/intent.yaml")
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    if (targetFile.exists()) {
                        val choice = Messages.showYesNoDialog(project, ".codeup/intent.yaml already exists. Overwrite?", "Codeup", Messages.getWarningIcon())
                        if (choice != Messages.YES) return@invokeLater
                    }
                    targetFile.parentFile.mkdirs()
                    targetFile.writeText(intentYaml)
                    val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile)
                    if (vf != null) OpenFileDescriptor(project, vf).navigate(true)
                    Messages.showInfoMessage(project, "Wrote .codeup/intent.yaml. Review and adjust the layer rules.", "Codeup")
                }
            }
        })
    }

    private fun buildSystemPrompt(): String = listOf(
        "You are a software architect. You will be given a compressed summary of a project (top directories by file count + the most-frequent cross-directory imports) and asked to draft architectural layer rules for it.",
        "Goal: a small, useful starting point for the team to edit — not an exhaustive description.",
        "Conventions to recognise (any language):",
        "- Domain / business core (model, domain, core)",
        "- Application / use-cases (application, services, use-cases, usecases, handlers)",
        "- Infrastructure / persistence (infrastructure, persistence, repository, repositories, adapters, dao, db)",
        "- Web / API / transport (web, api, http, controllers, controller, routes, endpoints, rest)",
        "- UI / views (ui, views, components, pages, templates, frontend)",
        "Rules of thumb for cannotDependOn:",
        "- domain should not depend on any of infrastructure, web/api, ui",
        "- application should not depend on web/api or ui",
        "- infrastructure should not depend on web/api or ui",
        "Match strings are minimatch globs against the workspace-relative path. Use plain directory prefixes (e.g. `src/main/java/com/example/domain/`) for normal projects. For monorepos with a `packages/` or `apps/` parent containing peer projects, use a wildcard segment (e.g. `packages/*/src/**/domain/**`) so one rule applies to every package at once.",
        "Only emit layers whose match (interpreted as a glob) covers directories that appear in the provided summary. Use 3-6 layers max.",
        "If the project does not show a clear layered structure, return 1-2 layers and explain in notes that the team should refine manually.",
        "Emit exactly one call to the propose_layer_rules tool. Do not narrate.",
    ).joinToString("\n")

    private fun formatForPrompt(index: ProjectIndex, graph: DependencyGraph): String {
        val byDir = mutableMapOf<String, Pair<Int, MutableSet<String>>>()
        for (f in index.files) {
            val dir = f.path.substringBeforeLast("/", "")
            if (dir.isEmpty()) continue
            val entry = byDir.getOrPut(dir) { 0 to mutableSetOf() }
            byDir[dir] = (entry.first + 1) to entry.second.also { it.add(f.language) }
        }
        val topDirs = byDir.entries.sortedByDescending { it.value.first }.take(50)

        val edgeCounts = mutableMapOf<String, Int>()
        for ((from, tos) in graph.edges) {
            val fromDir = from.substringBeforeLast("/", "")
            for (to in tos) {
                val toDir = to.substringBeforeLast("/", "")
                if (fromDir == toDir || fromDir.isEmpty() || toDir.isEmpty()) continue
                val key = "$fromDir $toDir"
                edgeCounts[key] = (edgeCounts[key] ?: 0) + 1
            }
        }
        val topEdges = edgeCounts.entries.sortedByDescending { it.value }.take(40)

        val lines = mutableListOf<String>()
        lines += "Project totals: ${index.files.size} files indexed, ${edgeCounts.values.sum()} cross-file imports tracked."
        lines += ""
        lines += "## Directories (top by file count)"
        for ((dir, pair) in topDirs) {
            lines += "- $dir — ${pair.first} files (${pair.second.sorted().joinToString(", ")})"
        }
        if (topEdges.isNotEmpty()) {
            lines += ""
            lines += "## Cross-directory imports (top by frequency)"
            for (e in topEdges) {
                val (fromDir, toDir) = e.key.split(" ", limit = 2)
                lines += "- $fromDir → $toDir (${e.value})"
            }
        } else {
            lines += ""
            lines += "## Cross-directory imports: none detected"
        }
        return lines.joinToString("\n")
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseLayersResponse(response: LLMAnalyzeResponse): String? {
        val call = response.toolCalls.firstOrNull { it.name == "propose_layer_rules" } ?: return null
        val input = call.input as? Map<String, Any?> ?: return null
        val layersList = input["layers"] as? List<Map<String, Any?>> ?: return null
        if (layersList.isEmpty()) return null
        val notes = input["notes"] as? String
        val opts = DumperOptions().also { it.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK; it.width = 100 }
        val layers = layersList.mapNotNull { l ->
            val layer = l["layer"] as? String ?: return@mapNotNull null
            val match = l["match"] as? String ?: return@mapNotNull null
            val cannotDependOn = (l["cannotDependOn"] as? List<*>)?.map { it.toString() } ?: emptyList()
            mapOf("layer" to layer, "match" to match, "cannotDependOn" to cannotDependOn)
        }
        return renderYaml(layers, notes, opts)
    }

    private fun renderYaml(layers: List<Map<String, Any>>, notes: String?, opts: DumperOptions): String {
        val header = mutableListOf(
            "# Generated by Codeup as a starting point — edit to match your team's actual",
            "# architectural rules. Layer matches are path prefixes; cannotDependOn lists",
            "# other layer names this layer must not import from.",
        )
        if (!notes.isNullOrBlank()) {
            header += "#"
            for (line in notes.split("\n")) header += "# $line"
        }
        header += ""
        return header.joinToString("\n") + Yaml(opts).dump(mapOf("layers" to layers))
    }

    private fun buildDefaultIntent(index: ProjectIndex): String {
        val opts = DumperOptions().also { it.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK; it.width = 100 }
        val topDirs = index.files.map { it.path.split("/").firstOrNull() ?: "" }
            .filter { it.isNotEmpty() }.groupBy { it }.entries.sortedByDescending { it.value.size }.take(5).map { it.key }
        val layers = topDirs.map { dir ->
            mapOf("layer" to dir, "match" to "$dir/**", "cannotDependOn" to emptyList<String>())
        }
        return renderYaml(layers, null, opts)
    }

    override fun update(e: AnActionEvent) { e.presentation.isEnabled = e.project != null }
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}