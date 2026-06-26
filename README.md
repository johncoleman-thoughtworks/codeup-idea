# Codeup

JetBrains plugin that surfaces anti-patterns in your codebase, powered by the Anthropic API.

Findings are persisted as files under `.codeup/` so they travel with the repo and go through PR review. The `.codeup/` format is byte-for-byte compatible with the [Codeup VS Code extension](https://github.com/johncoleman-thoughtworks/codeup-vscx) — teams on mixed editors share a single findings directory.

## What it detects

107 catalogued patterns spanning:

- **Single-file smells** — god class, anemic domain model, long methods, primitive obsession, deep nesting, high cognitive complexity, error swallowing, function-name mismatches, etc.
- **Inheritance & OO shape** — non-exclusive subtypes (roles modelled as inheritance), procedural shell classes (Manager / Handler / Processor), base classes that depend on their subclasses, parallel inheritance hierarchies.
- **Cross-file structure** — type leakage across boundaries, shotgun surgery, feature envy with neighbor context.
- **Module dependency** — deterministic cyclic-dependency detection (Tarjan SCC), layer-boundary violations driven by an optional `.codeup/intent.yaml`.
- **Service-level** — distributed monolith indicators, shared-database, reach-through reads, god service.
- **Data / persistence** — N+1 queries, lost updates, cache-as-source-of-truth, EAV overuse, god tables.
- **Exception handling** — overbroad catches, lossy translation, exceptions as validation, log-and-rethrow cargo cult, slow-fail / over-recovery.
- **Code security (10 patterns)** — untrusted input in interpreting contexts, resource-locator path traversal, lower-trust config overriding security decisions, external artifacts installed without integrity verification, unsafe deserialization, inconsistent validation across ingress paths, trust-following filesystem operations, persisted state treated as authoritative, credentials scoped to identity rather than destination, untrusted input that can terminate a shared process.
- **Process / judgement** — premature optimisation/abstraction, copy-paste programming, golden hammer, lava flow.

See [`src/main/resources/catalogue/default.yaml`](src/main/resources/catalogue/default.yaml) for the full list.

## Setup

1. **Install** — build from source (`./gradlew buildPlugin`) and install the resulting ZIP via **Settings → Plugins → ⚙ → Install Plugin from Disk…**, or launch a sandboxed IDE with `./gradlew runIde`. Also available on [JETBRAINS Marketplace](https://plugins.jetbrains.com/plugin/32381-codeup).
2. **Set an Anthropic API key** — go to **Tools → Codeup → Set Anthropic API Key**. The key is stored in the IDE's secure credential store (macOS Keychain / Windows DPAPI / Linux libsecret via `PasswordSafe`) — never written to disk in plain text or committed to your repo.
3. **Open a project** and run **Tools → Codeup → Run Full Scan**. The first run shows a cost estimate before making any API calls.

## Anthropic API key

An Anthropic API key is required. [Get one at console.anthropic.com](https://console.anthropic.com/).

**Tools → Codeup → Set Anthropic API Key** stores the key in the IDE's `PasswordSafe` credential store, which is backed by the OS keychain. It is never written to `settings.xml`, to any project file, or to `.codeup/`. To remove it, use **Tools → Codeup → Clear Anthropic API Key**.

The model used for analysis defaults to `claude-sonnet-4-6` and is configurable via **Settings → Tools → Codeup**.

## GitHub Copilot integration

**Copilot integration is not available in the JetBrains plugin.**

The VS Code extension can route through your GitHub Copilot subscription via VS Code's built-in Language Model API (`vscode.lm`). That API is a VS Code-specific platform feature — it has no equivalent in IntelliJ Platform. The GitHub Copilot JetBrains plugin does not expose a programmatic interface that other plugins can use to send requests through Copilot's model proxy.

As a result, the JetBrains plugin only supports **Anthropic direct** — an API key is always required. If you need to keep requests inside a corporate cloud boundary, Anthropic's AWS Bedrock and GCP Vertex deployments are an option, though Codeup does not yet include an adapter for those endpoints (open an issue if this is a blocker).

## Daily flow

1. **Run a scan** — **Tools → Codeup → Run Full Scan** (whole project) or **Tools → Codeup → Scan Current File**. The first full scan shows an estimated cost before making any API calls.
2. **Triage findings** in the Codeup tool window (right side of the IDE):
   - Click a finding to open the file at the right line and the details panel side-by-side.
   - **Confirm** if it is a real issue — the finding becomes a positive exemplar in `.codeup/knowledge/exemplars.yaml`.
   - **Dismiss…** if it is a false positive — Codeup prompts for a rationale, which is saved to `.codeup/knowledge/dismissals.yaml`. Future scans see this and learn from it.
   - **Mark Fixed** once you have made the change.
3. **Rescan** — unchanged files come from cache (no API cost). Changed files re-analyze.

## Data handling — please read before scanning client code

Codeup sends source code to the Anthropic API when it runs the LLM-driven catalogue. **For the avoidance of doubt:** if you point Codeup at a client repository, source from that repository will leave your machine. You are responsible for confirming that this is acceptable under any NDA or data-handling agreement that applies.

**What gets sent over HTTPS to `api.anthropic.com`:**

- The full text of any file Codeup analyzes (verbatim, between code fences).
- Up to 6 *neighbor* files per analyzed file (importers, imported modules, and same-package siblings), each truncated to 8,000 characters.
- The text of any team-authored dismissal rationales and exemplar explanations retrieved from `.codeup/knowledge/` as context for the current analysis.
- Pattern hints from the catalogue and the analyzer's system prompt.

**What never gets sent:**

- Files excluded by your `.gitignore`, by a `.codeupignore` (see below), or by the scanner's default excludes (`node_modules`, `.git`, `dist`, `build`, `target`, `.codeup/` itself, etc.).
- Files over 512 KB on disk or over 60,000 characters at analysis time.
- Files in a language that has no matching catalogue patterns.
- Files whose contents are unchanged since the last scan (cache hit — zero API calls).
- The deterministic findings (`cyclic-dependency`, `layer-violation`) — graph-only, no API call ever.
- Your `.codeup/findings/` records, your `.codeup/cache/`, or your API key (used in the auth header, never as content).

**Anthropic's terms** (commercial API use): inputs and outputs are not used to train models. Retention is for abuse detection and bounded by Anthropic's data retention policy. Customers with stricter requirements can request Zero Data Retention agreements; deployment via AWS Bedrock or GCP Vertex is also an option for keeping requests inside a customer-controlled cloud.

**If you cannot send client source off the machine**, you can still get value from Codeup's deterministic checks (cycles, layer violations) without any API call. A setting that disables the LLM pass entirely is a planned addition — open an issue if you need it sooner.

## What's stored in `.codeup/`

Codeup writes everything under `.codeup/` in your project root.

| Path | Purpose | Commit? |
|---|---|---|
| `.codeup/findings/*.yaml` | One file per finding — category, severity, status, location, history. Reviewed in PRs. | **Yes** |
| `.codeup/knowledge/dismissals.yaml` | Dismissal rationales injected into future analysis prompts. | **Yes** |
| `.codeup/knowledge/exemplars.yaml` | Confirmed findings used as positive examples. | **Yes** |
| `.codeup/knowledge/patterns.yaml` | Team-specific catalogue extensions / overrides. | **Yes** |
| `.codeup/intent.yaml` | Layer rules used for deterministic layer-violation findings. | **Yes** |
| `.codeup/index/` | Generated workspace index + dependency graph. | **No** |
| `.codeup/cache/` | Per-content-hash analysis cache. Local-only optimisation. | **No** |

### `.gitignore`

Codeup drops a `.gitignore` inside each generated directory (`.codeup/index/` and `.codeup/cache/`) automatically, so the contents are ignored even if you do nothing. For belt-and-braces, add these to your project's root `.gitignore`:

```
.codeup/index/
.codeup/cache/
```

Keep `.codeup/findings/`, `.codeup/knowledge/`, and `.codeup/intent.yaml` **tracked** — they're the parts that travel with the repo and accumulate decisions.

### `.codeupignore`

If you want a file or directory tracked by git but excluded from Codeup analysis — generated source, test fixtures, vendored snapshots, large config files — add a `.codeupignore` next to it.

`.codeupignore` uses the **same syntax and semantics as `.gitignore`** (gitignore spec). You can place one at any depth in the project; patterns apply relative to that file's directory exactly as a `.gitignore` would.

**Precedence:** `.codeupignore` rules **override `.gitignore` rules at any depth**. A `!keep.snap` in a `.codeupignore` brings the file back into the scan even when a `.gitignore` (in the same directory or higher) ignores it. A non-overridable set of defaults (`.git`, `node_modules`, `.codeup`, etc.) is always skipped and cannot be un-ignored.

Example — analyse files that git ignores:

```gitignore
# .codeupignore at project root
!**/generated/*.ts
```

Example — skip a directory Codeup would otherwise scan:

```gitignore
# apps/web/.codeupignore
fixtures/
```

## Commands

All commands are available under **Tools → Codeup** in the menu bar:

| Command | Purpose |
|---|---|
| **Run Full Scan** | Scan every supported file in the project. Modal cost estimate first. |
| **Scan Current File** | Scan the file in the active editor only. No cost prompt. |
| **Scan Open Tabs** | Scan every file currently open in an editor tab. Cost prompt only if >1 uncached file. |
| **Suggest Architectural Intent** | Draft a `.codeup/intent.yaml` from the project's directory + dependency layout. |
| **Refresh Findings** | Reload findings from disk (use after editing YAML by hand). |
| **Group Findings by Severity / Category / Status** | Switch the tree's grouping (also available as a dropdown in the Codeup toolbar). |
| **Set Anthropic API Key** | Store / replace the API key in the IDE credential store. |
| **Clear Anthropic API Key** | Forget the stored key. |
| **Check for Updates** | Check GitHub Releases for a newer Codeup version. Auto-runs on startup (throttled to once per 24 h); this command forces an immediate check. |

## Settings

**Settings → Tools → Codeup**

| Setting | Default | Description |
|---|---|---|
| Model | `claude-sonnet-4-6` | Anthropic model used for analysis. |
| Scan on save | `false` | Run an incremental scan on file save. |
| Findings directory | `.codeup/findings` | Where findings YAML files live (project-relative). |
| File size warn (bytes) | `30,000` | File size threshold for medium-severity `oversized-file` findings. |
| File size critical (bytes) | `60,000` | File size threshold for high-severity `oversized-file` findings (matches the analyzer's character cap). |
| Enable update checks | `true` | Poll GitHub Releases for new versions on startup. |
| Update check interval (hours) | `24` | How often to check for updates. |

## Optional: `.codeup/intent.yaml`

Drop a file at `.codeup/intent.yaml` to declare layer rules. Each layer's `match` is a glob against the project-relative path; `cannotDependOn` lists layer names this layer must never import from. Violations are reported as `layer-violation` findings without any API cost.

Trailing-slash patterns (e.g. `src/foo/`) auto-extend to `src/foo/**`, so simple prefix rules still work.

```yaml
layers:
  - layer: domain
    match: 'src/**/domain/**'
    cannotDependOn: [infrastructure, web]
  - layer: application
    match: 'src/**/application/**'
    cannotDependOn: [infrastructure, web]
  - layer: infrastructure
    match: 'src/**/infrastructure/**'
    cannotDependOn: []
```

To get a starting draft, run **Tools → Codeup → Suggest Architectural Intent**. Codeup compresses the directory layout and dependency graph into a small summary, asks Claude to propose layer rules, and opens the result for review. Edit before your next scan — the proposal is a starting point, not the final word.

### How to check intent is being obeyed

Layer-rule enforcement runs as part of every scan — no separate command. After **Run Full Scan** (or **Scan Current File**):

1. Any forbidden import shows up in the findings tree as a `layer-violation` finding (use **Group By Category** to find them at a glance).
2. Each violation is persisted as `.codeup/findings/layer-violation-<hash>.yaml`. Zero API cost — computed from the dependency graph plus your rules.

## Cost expectations

- Scans use `tool_use` for structured output so Claude only emits findings, not prose — keeps output tokens small.
- The pre-flight dialog shows estimated cost before any full scan runs.
- Cached files skip the API entirely. A second scan of unchanged code is free.
- Catalogue + knowledge are part of the cache key — bumping either invalidates relevant entries.

## Development

```bash
./gradlew test          # unit tests (JUnit 5, no IntelliJ dependency)
./gradlew runIde        # launch sandboxed IDE with plugin loaded
./gradlew buildPlugin   # build distributable ZIP → build/distributions/
./gradlew verifyPlugin  # verify plugin structure
```

**Requirements:** JDK 17+, Gradle 8.9 (wrapper included).

The unit test suite covers import extraction, dependency graph, ignore files, schema validation, migrations, analyzer pure logic, cache keys, layer matching, deterministic checks, knowledge retrieval, size checking, update checking, catalogue merging, and intent sampling — all runnable without an IntelliJ installation.

The `src/main/kotlin/com/codeup/` packages that have no IntelliJ API imports (`scanner/`, `analyzer/`, `intent/`, `knowledge/`, `migrations/`, `quality/`, `util/`) can be developed and tested as plain Kotlin.
