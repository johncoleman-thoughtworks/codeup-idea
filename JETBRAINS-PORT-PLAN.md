# Codeup — JetBrains Port Plan

## Architectural Overview

**Pure TypeScript logic that ports line-by-line to Kotlin** (no VS Code API dependencies):
- `scanner/imports.ts`, `scanner/graph.ts`, `scanner/ignoreLoader.ts`
- `findings/schema.ts`, `analyzer/pure.ts`, `analyzer/cacheKey.ts`, `analyzer/tools.ts`
- `intent/check.ts`, `intent/layers.ts`, `intent/sampler.ts`
- `knowledge/retrieve.ts`, `migrations/runner.ts`, `quality/sizeCheck.ts`, `util/updateCheckPure.ts`

**Critical compatibility constraint:** `.codeup/` on-disk format must remain byte-for-byte compatible so repos are shareable across both IDEs. This means identical YAML field names/order, same SHA-1 stable IDs, same catalogue hash algorithm, same import extraction regex output.

---

## VS Code → IntelliJ API Mapping

| VS Code | IntelliJ / Kotlin |
|---|---|
| `package.json` contributes | `plugin.xml` extension declarations |
| `registerCommand` | `AnAction` in `<actions>` |
| `TreeDataProvider` / `createTreeView` | `ToolWindowFactory` + `SimpleTreeStructure` / `DefaultTreeModel` |
| `TextEditorDecorationType` | `RangeHighlighter` via `editor.markupModel` |
| Overview ruler | `RangeHighlighterEx.errorStripeMarkColor` |
| `StatusBarItem` | `StatusBarWidget.TextPresentation` |
| `withProgress` | `ProgressManager.run(Task.Backgroundable(..., cancellable=true))` |
| `CancellationToken` | `ProgressIndicator.isCanceled` / `ProcessCanceledException` |
| `OutputChannel` | `Logger.getInstance(...)` + tool window console |
| `workspace.fs` | `VirtualFile` / `VfsUtil` / `LocalFileSystem` |
| File watcher | `VirtualFileManager.addVirtualFileListener(...)` |
| `context.secrets` | `PasswordSafe` + `CredentialAttributes` |
| `workspace.getConfiguration` | `PersistentStateComponent<T>` + `PropertiesComponent` |
| WebviewPanel | `JBCefBrowser` (with `JEditorPane` fallback) |
| `showWarningMessage` | `Notifications.Bus.notify(Notification(...))` |
| `EventEmitter` / `onDidChange` | `MessageBus.Topic` + `EventDispatcher<Listener>` |

---

## Phase 1 — Gradle Skeleton + plugin.xml + Data Models
**Deliverable:** Compiling, installable (empty) plugin with all Kotlin data types.

**New files:**
- `build.gradle.kts` — `org.jetbrains.intellij.platform` plugin 2.x; target `IC 2024.3`; dependencies: `com.squareup.okhttp3:okhttp:4.12.0`, `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml`, bundled SnakeYAML via `compileOnly`
- `src/main/resources/META-INF/plugin.xml` — id `com.codeup`, `<depends>com.intellij.modules.platform</depends>`
- `src/main/kotlin/com/codeup/model/Finding.kt` — direct Kotlin translation of `findings/schema.ts`; `data class Finding(...)`, `enum class Severity/Status/Priority`, `validateFinding()`, `isSafeIdentifier()`, `isSafeRelativePath()`
- `src/main/kotlin/com/codeup/model/ScannerSchema.kt` — `FileEntry`, `ProjectIndex`
- `src/main/kotlin/com/codeup/model/CatalogueSchema.kt` — `CataloguePattern`, `Catalogue`
- `src/main/kotlin/com/codeup/model/KnowledgeSchema.kt` — dismissal/exemplar types

**YAML serialization:** Use Jackson with `jackson-dataformat-yaml`. Write explicit `toMap()`/`fromMap()` functions (not reflection-based) to control field order and match VS Code's YAML output exactly.

---

## Phase 2 — File System Walker + Language Detection + Import Extraction
**Deliverable:** `WorkspaceScanner` producing a `ProjectIndex` via IntelliJ VFS.

**New files:**
- `WorkspaceScanner.kt` — uses `VfsUtilCore.iterateChildrenRecursively(root, filter, processor)`; checks `ProgressManager.checkCanceled()` at each file; runs via `ReadAction.nonBlocking { ... }.submit(AppExecutorUtil.getAppExecutorService())`
- `ImportExtractor.kt` — direct port of `scanner/imports.ts` regex patterns; `Regex.findAll()` replaces `String.matchAll()`
- `LanguageDetector.kt` — copy of `LANGUAGE_BY_EXT` map; uses `virtualFile.extension?.lowercase()`

**Key difference:** `VirtualFile.contentsToByteArray()` loads file; use `virtualFile.length` for the `MAX_FILE_BYTES = 512 * 1024` check before reading. Hashing: `MessageDigest.getInstance("SHA-256")`.

---

## Phase 3 — Dependency Graph + Cycle Detection
**Deliverable:** `DependencyGraph` builder and Tarjan SCC cycle finder.

**New files:**
- `GraphBuilder.kt` — direct port of `scanner/graph.ts`; `DependencyGraph(edges, reverse, unresolved)` as a `data class`
- `CycleFinder.kt` — Tarjan SCC: `ArrayDeque<String>` for stack, `MutableMap<String, Int>` for indexes; use iterative version to avoid JVM stack depth issues on large graphs

**Path normalization:** `Paths.get(...).normalize().toString().replace('\\', '/')` — never `java.io.File` for path manipulation.

---

## Phase 4 — Ignore File Support (.gitignore, .codeupignore)
**Deliverable:** `IgnoreLoader` with same scope-prefix rewriting semantics as the VS Code `ignore` npm package.

**New files:**
- `IgnoreLoader.kt` — `rewritePatternForScope()` and `parseIgnoreText()` port verbatim from `scanner/ignoreLoader.ts`
- `GitignoreMatcher.kt` — ~150-line pure Kotlin implementation of gitignore semantics (negation, anchored patterns, `**` globbing) since no direct npm-`ignore` equivalent exists in JVM. `.codeupignore` wins over `.gitignore`; defaults are non-overridable.

---

## Phase 5 — Findings Persistence (YAML r/w, File Watching, Schema Validation, Migration)
**Deliverable:** `FindingsStore` backed by `.codeup/findings/*.yaml` with live reload.

**New files:**
- `FindingsStore.kt` — `@Volatile var findings: Map<String, Finding>`; reads/writes off-EDT via `ApplicationManager.getApplication().executeOnPooledThread { ... }`; fires `FINDINGS_CHANGED` MessageBus topic on change
- `FindingYamlMapper.kt` — explicit `serialize(Finding): String` / `deserialize(String): Finding` round-trip that preserves field order
- `MigrationRunner.kt` — direct port of `migrations/runner.ts`

**File watching:** `VirtualFileManager.getInstance().addVirtualFileListener(listener, parentDisposable)`; filter to `.codeup/findings/`; also call `LocalFileSystem.getInstance().addRootToWatch(findingsDir, false)`.

**Path safety:** `File(dir, id + ".yaml").canonicalPath.startsWith(dir.canonicalPath + File.separator)` before any read/write.

---

## Phase 6 — Knowledge Store (Dismissals + Exemplars)
**Deliverable:** `KnowledgeStore` backed by `.codeup/knowledge/{dismissals,exemplars,patterns}.yaml`.

**New files:**
- `KnowledgeStore.kt` — same file-watch pattern as Phase 5; `hash()` uses SHA-256 of Jackson-serialized blob
- `KnowledgeRetriever.kt` — direct port of `knowledge/retrieve.ts`; glob matching via `GitignoreMatcher` from Phase 4; `directoryProximity()` uses `Paths.get(path).parent`

---

## Phase 7 — Analysis Cache
**Deliverable:** `AnalysisCache` with in-memory + on-disk JSON entries at `.codeup/cache/entries/<sha256(key)[0..32]>.json`.

**New files:**
- `CacheKey.kt` — direct port of `analyzer/cacheKey.ts`
- `AnalysisCache.kt` — `File(entryPath).readText()` with Jackson deserialization; safe to call synchronously since scan already runs off-EDT; `migrateLegacyIfPresent()` ports unchanged; writes `.gitignore` into cache dir on first use

---

## Phase 8 — Catalogue Loader
**Deliverable:** `CatalogueLoader` reading the bundled `default.yaml` + workspace overrides.

**New files:**
- `src/main/resources/catalogue/default.yaml` — **copy verbatim** from `resources/catalogue/default.yaml`
- `CatalogueLoader.kt` — accesses resource via `CatalogueLoader::class.java.classLoader.getResourceAsStream("catalogue/default.yaml")`; hash uses `MessageDigest("SHA-256")` on `(defaultYaml + "|" + overridesJson)` first 16 hex chars — must match VS Code exactly so caches are cross-IDE compatible

---

## Phase 9 — LLM Client Abstraction + Anthropic HTTP Client
**Deliverable:** `LLMClient` interface + `AnthropicClient` using OkHttp (no JVM Anthropic SDK exists).

**New files:**
- `LLMClient.kt`:
  ```kotlin
  interface LLMClient {
      suspend fun analyze(req: LLMAnalyzeRequest): LLMAnalyzeResponse
      fun model(): String
      fun provider(): String
      fun reset()
  }
  data class LLMAnalyzeRequest(
      val systemPrompt: String,
      val userPrompt: String,
      val tool: ToolDefinition,
      val maxOutputTokens: Int,
      val cancellationCheck: () -> Boolean = { false }
  )
  ```
- `AnthropicClient.kt` — OkHttp POST to `https://api.anthropic.com/v1/messages`; Jackson for request/response serialization; cancellation via `call.cancel()` when `cancellationCheck()` returns true; throws `ProcessCanceledException` on cancel

**API key:** `AnthropicClient` receives a `() -> String?` lambda (`{ ApiKeyManager.getApiKey() }`) rather than holding a reference to context. `reset()` clears any internal cached state.

---

## Phase 10 — Analyzer Logic (Prompts, Tool-Call Parsing, Cache Integration)
**Deliverable:** `FileAnalyzer` — port of `analyzer/analyze.ts`.

**New files:**
- `FileAnalyzer.kt` — `suspend fun analyzeFile(...)`: checks binary (`text.contains(' ')`), builds prompts, calls `LLMClient`, validates results, persists to `FindingsStore`
- `AnalyzerPure.kt` — `stableId()` (SHA-1, hex), `neighborsCacheKey()`, `validateReported()` — must produce identical output to TypeScript versions
- `ReportFindingTool.kt` — `REPORT_FINDING_TOOL` as Kotlin constant; `input_schema` as `Map<String, Any>` serialized via Jackson

`buildSystemPrompt()` and `buildUserPrompt()` port verbatim — pure string construction.

---

## Phase 11 — Deterministic Checks (Size, Cycles, Layer Violations, Intent Loader)
**Deliverable:** Pure services for all non-LLM analysis.

**New files:**
- `SizeChecker.kt` — port of `quality/sizeCheck.ts`
- `IntentLoader.kt` — `File(root, ".codeup/intent.yaml").readText()` + SnakeYAML parse → `IntentConfig?`
- `LayerMatcher.kt` — `layerForFile()` / `matchesRule()` using `FileSystems.getDefault().getPathMatcher("glob:...")` or custom minimatch port
- `DeterministicChecks.kt` — `cycleFindings()` + `layerViolations()` port verbatim
- `IntentSuggester.kt` — prompt building + tool-call parsing; `PROPOSE_LAYER_RULES_TOOL` as Kotlin constant; `renderYaml()` via SnakeYAML with `DumperOptions(BLOCK)`
- `ProjectSummarizer.kt` — port of `intent/sampler.ts`

---

## Phase 12 — Scan Runner (Background Task, Cost Confirmation, Progress, Cancellation)
**Deliverable:** `ScanRunner.run(ScanOptions)` orchestrating all phases.

**New files:**
- `ScanOptions.kt` — `data class ScanOptions(val scope: Scope, val fileUri: VirtualFile? = null, val fileUris: List<VirtualFile>? = null, val skipCostPrompt: Boolean = false)`
- `ScanRunner.kt`:
  - `ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) { override fun run(indicator: ProgressIndicator) { ... } })`
  - `indicator.fraction = done.toDouble() / total`; `indicator.text2 = "${done + 1}/${total} • ${entry.path}"`
  - Checks `ProgressManager.checkCanceled()` after each file
  - `Task.Backgroundable.run()` calls `runBlocking { ... }` to bridge into `suspend fun` scan functions

**Cost confirmation:** `Messages.showOkCancelDialog(project, message, "Codeup", "Proceed", "Cancel", Messages.getWarningIcon())` — returns `Messages.OK` or `Messages.CANCEL`.

**Scoped roots:** Start with `project.basePath!!` as single root. Multi-module: `ModuleManager.getInstance(project).modules.mapNotNull { ModuleRootManager.getInstance(it).contentRoots.firstOrNull()?.path }`.

---

## Phase 13 — Findings Tool Window + Tree Model
**Deliverable:** Sidebar panel with root/group/finding tree, groupBy control, refresh button.

**New files:**
- `FindingsToolWindowFactory.kt` — implements `ToolWindowFactory`; registers via:
  ```xml
  <toolWindow id="Codeup" anchor="right"
      factoryClass="com.codeup.ui.toolwindow.FindingsToolWindowFactory"
      icon="/icons/codeup-icon.svg"/>
  ```
- `FindingsPanel.kt` — `JPanel` with `SimpleTree` + toolbar `ComboBox<GroupBy>` + refresh `ActionButton`
- `FindingsTreeModel.kt` — `DefaultTreeModel(DefaultMutableTreeNode(...))` with 3-level structure matching `FindingsProvider` (root/group/finding nodes)
- `FindingsTreeNode.kt` — sealed class: `Root`, `Group`, `Leaf`

**Cell rendering:** `ColoredTreeCellRenderer`; severity icons: `AllIcons.General.Error` / `Warning` / `Information` or custom SVGs.

**Change listener:** Subscribe to `FINDINGS_CHANGED` MessageBus topic; call `SwingUtilities.invokeLater { treeModel.reload() }`.

**Navigation:** `tree.addMouseListener(...)` on double-click → `OpenFileDescriptor(project, file, line - 1, 0).navigate(true)`.

---

## Phase 14 — Editor Gutter Decorations + Highlights
**Deliverable:** Whole-line background + overview ruler marks + gutter icons with hover tooltips.

**New files:**
- `FindingsHighlighter.kt` — `StartupActivity` that applies `RangeHighlighter`s via `editor.markupModel.addLineHighlighter(textAttributesKey, line - 1, HighlighterLayer.SYNTAX + 1)`; sets `highlighter.errorStripeMarkColor` and `highlighter.gutterIconRenderer`; tracks all added highlighters in `Map<VirtualFile, List<RangeHighlighter>>` for clean removal
- `FindingGutterRenderer.kt` — `GutterIconRenderer` subclass; `getIcon()` → severity icon; `getTooltipText()` → finding explanation; `getClickAction()` → open detail panel
- `FindingsEditorListener.kt` — `FileEditorManagerListener` subscribed via message bus; applies highlights on `fileOpened` / `selectionChanged`

**TextAttributesKey per severity:**
```kotlin
private val HIGH_KEY = TextAttributesKey.createTextAttributesKey(
    "CODEUP_HIGH", TextAttributes(JBColor.RED.withAlpha(40), null, null, null, Font.PLAIN))
```

---

## Phase 15 — Status Bar Widget
**Deliverable:** `[search] Codeup: N (⊘ H)` in the status bar.

**New files:**
- `CodeupStatusBarWidgetFactory.kt` — registered via `<statusBarWidgetFactory id="CodeupStatusBar" implementation="..."/>`
- `CodeupStatusBarWidget.kt` — implements `StatusBarWidget` + `StatusBarWidget.TextPresentation`; `getText()` returns count string; `getClickConsumer()` focuses tool window; `@Volatile var scanning: Boolean`; calls `statusBar?.updateWidget(ID())` on findings change or scan state change

---

## Phase 16 — Finding Detail Panel
**Deliverable:** Panel showing finding details with action buttons (Confirm, Dismiss, Mark Fixed, Reopen).

**New files:**
- `FindingDetailPanel.kt`:
  - **Primary:** `JBCefBrowser` (check `JBCefApp.isSupported()` at runtime)
  - **Fallback:** `JEditorPane("text/html", html)` with `HyperlinkListener` for action buttons
  - HTML template ported from `detailsView.ts`; `var(--vscode-*)` CSS variables replaced with embedded `JBColor` constants

**Opening:** Second content in the Codeup `ToolWindow` content manager; switch via `toolWindow.contentManager.setSelectedContent(content)`.

**Dismiss with note:** `Messages.showInputDialog(project, "Why is this being dismissed?", "Dismiss Finding", Messages.getQuestionIcon())`.

---

## Phase 17 — Actions
**Deliverable:** All user commands registered in `plugin.xml` under `<actions>`.

| Action class | Key IntelliJ API |
|---|---|
| `ScanFullAction` | Delegates to `ScanRunner.run(FULL)` |
| `ScanFileAction` | `e.getData(CommonDataKeys.VIRTUAL_FILE)` |
| `ScanOpenTabsAction` | `FileEditorManager.getInstance(project).openFiles` |
| `SetApiKeyAction` | `Messages.showPasswordDialog(...)` → `ApiKeyManager.setApiKey(...)` |
| `ClearApiKeyAction` | `ApiKeyManager.clearApiKey()` |
| `GroupByAction` (×3) | Update `FindingsTreeModel.groupBy`; `treeModel.reload()` |
| `RefreshAction` | Reload `FindingsStore` + `treeModel.reload()` |
| `SuggestIntentAction` | `Task.Backgroundable` → scan → LLM → open YAML via `ScratchRootType.getInstance().createScratchFile(...)` or write + `OpenFileDescriptor.navigate(true)` |
| `CheckUpdatesAction` | `UpdateChecker.checkNow()` |

All actions: override `update()` with `e.presentation.isEnabled = e.project != null`.

---

## Phase 18 — Settings Page
**Deliverable:** IDE Settings > Tools > Codeup configurable page.

**New files:**
- `CodeupSettingsState.kt` — `@State`-annotated class; fields match VS Code config keys exactly:
  ```kotlin
  class CodeupSettingsState {
      var modelProvider: String = "auto"
      var model: String = "claude-sonnet-4-6"
      var scanOnSave: Boolean = false
      var findingsDir: String = ".codeup/findings"
      var fileSizeWarnBytes: Int = 30_000
      var fileSizeCriticalBytes: Int = 60_000
      var updateCheckEnabled: Boolean = true
      var updateCheckIntervalHours: Int = 24
  }
  ```
- `CodeupSettings.kt` — `PersistentStateComponent<CodeupSettingsState>`; registered as `<applicationService/>`; access via `ApplicationManager.getApplication().getService(CodeupSettings::class.java)`
- `CodeupSettingsConfigurable.kt` — `Configurable`; UI built with `FormBuilder.createFormBuilder()`

---

## Phase 19 — API Key Management
**Deliverable:** `PasswordSafe`-backed secure storage mapping from VS Code `context.secrets`.

**New files:**
- `ApiKeyManager.kt`:
  ```kotlin
  object ApiKeyManager {
      private val ATTRIBUTES = CredentialAttributes("Codeup", "anthropic-api-key")
      fun getApiKey(): String? = PasswordSafe.instance.getPassword(ATTRIBUTES)
      fun setApiKey(key: String) = PasswordSafe.instance.setPassword(ATTRIBUTES, key)
      fun clearApiKey() = PasswordSafe.instance.setPassword(ATTRIBUTES, null)
  }
  ```

---

## Phase 20 — Update Checker
**Deliverable:** Startup GitHub Releases check with balloon notification.

**New files:**
- `UpdateCheckPure.kt` — direct port of `util/updateCheckPure.ts`; `compareSemver()`, `isNewer()`, `dueForCheck()`, `parseRelease()`
- `UpdateChecker.kt` — registered as `StartupActivity`; state in `PropertiesComponent.getInstance()`; HTTP via OkHttp; notification:
  ```kotlin
  NotificationGroupManager.getInstance()
      .getNotificationGroup("Codeup")
      .createNotification("Codeup $tag is available", NotificationType.INFORMATION)
      .addAction(NotificationAction.createSimple("View release") { BrowserUtil.browse(htmlUrl) })
      .notify(project)
  ```

**Important difference from VS Code:** No auto-install. The "install now" VS Code flow becomes "View release" → GitHub URL only; JetBrains Marketplace handles plugin updates through its own channel.

Register in `plugin.xml`: `<notificationGroup id="Codeup" displayType="BALLOON"/>`.

---

## Phase 21 — Test Plan
**Deliverable:** Full test suite mirroring VS Code unit tests + IntelliJ integration tests.

**Unit tests** (no IntelliJ API — run with `./gradlew test`, JUnit 5 + AssertJ):

| Kotlin test class | Source VS Code test |
|---|---|
| `ImportExtractorTest` | `imports.test.ts` |
| `GraphBuilderTest` | `graph.test.ts` |
| `IgnoreLoaderTest` | `scanner-ignore.test.ts` |
| `SchemaTest` | `schema.test.ts` |
| `MigrationRunnerTest` | `migrations.test.ts` |
| `AnalyzerPureTest` | `analyzer-pure.test.ts` |
| `CacheKeyTest` | `cache-key.test.ts` |
| `LayerMatcherTest` | `layers.test.ts` |
| `DeterministicChecksTest` | `intent-check.test.ts` |
| `KnowledgeRetrieverTest` | `knowledge-retrieve.test.ts` |
| `SizeCheckerTest` | `size-check.test.ts` |
| `UpdateCheckPureTest` | `update-check.test.ts` |
| `CatalogueMergeTest` | `catalogue-merge.test.ts` |
| `IntentSamplerTest` | `intent-sampler.test.ts` |

**Critical compatibility test — YAML round-trip:** Load `fixture-1.yaml` from VS Code test fixtures → deserialize to `Finding` → re-serialize → assert output matches original byte-for-byte. This validates cross-IDE format compatibility and is the single most important test in the suite.

**IntelliJ platform tests** (extend `BasePlatformTestCase`):
- `FindingsTreeModelTest` — given fixture `List<Finding>`, assert correct node counts
- `FindingsHighlighterTest` — assert correct `RangeHighlighter` count per severity per file
- `ScanRunnerTest` — run on minimal fixture project; assert no exceptions and findings written to disk

---

## Implementation Sequencing

Phase 1 is a prerequisite for everything. After that, run three streams in parallel:

| Stream | Phases | Notes |
|---|---|---|
| **Pure logic** | 2 → 3 → 4 → 7 → 8 → 10 → 11 | No IntelliJ API; testable standalone with `./gradlew test` |
| **Persistence + backend** | 5 → 6 → 9 → 12 | Needs Phase 1 models; Phase 12 needs Phases 2–11 |
| **UI** | 13 → 14 → 15 → 16 → 17 | Needs Phase 12 producing findings |
| **Infrastructure** | 18 → 19 → 20 → 21 | Settings/key/update checker; tests run throughout |

Critical path: **Phase 1 → Pure logic stream → Persistence stream → UI stream**.

---

## Critical Files for Cross-IDE Compatibility

These files define the compatibility contract between the VS Code and IntelliJ plugins. Any divergence breaks shared `.codeup/` directories:

| VS Code source | What must match in Kotlin |
|---|---|
| `src/findings/schema.ts` | Every YAML field name, type, and default value |
| `src/analyzer/pure.ts` — `stableId()` | Identical SHA-1 + hex encoding so finding IDs are stable across IDEs |
| `src/analyzer/pure.ts` — `neighborsCacheKey()` | Identical cache key composition so LLM cache hits work cross-IDE |
| `src/analyzer/cacheKey.ts` | Identical key string format |
| `src/scanner/imports.ts` | All 7 language extractors must produce identical `rawImports[]` output |
| `resources/catalogue/default.yaml` | Bundled verbatim; hash algorithm must produce identical 16-char prefix |
| `src/migrations/runner.ts` | Identical migration logic so findings written by either IDE upgrade cleanly |