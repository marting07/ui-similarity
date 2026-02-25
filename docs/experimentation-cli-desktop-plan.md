# Production CLI + Experimentation Desktop Plan

## Objective
Build a production-ready similarity platform with:
1. A distributable CLI for real similarity search workflows.
2. A Kotlin desktop app dedicated to experimentation and analysis.
3. Persistent index storage/load from file.
4. Corpus sampling controls (percentage-based selection from large repo corpus).

## Product Outcomes
1. Engineering teams can use the CLI in real environments to build/search indexes reliably.
2. Researchers can use the desktop app to run experiments, compare behavior, and inspect quality.
3. Index build cost is amortized by saving/loading index snapshots.

## Scope

### In Scope
1. Production CLI packaging/distribution and stable command contract.
2. Desktop experimentation app MVP (single-user, local files).
3. Index persistence format and versioning.
4. Percentage sampling by framework and/or global corpus (desktop experimentation workflows).
5. Run metadata and result export.

### Out of Scope (for MVP)
1. Multi-user collaboration.
2. Cloud index hosting.
3. Online repo cloning orchestration UI.
4. Real-time incremental indexing across filesystem watchers.

## Architecture Direction

### Core Separation
1. Keep extraction/index/similarity logic in reusable core modules.
2. Add an application service layer consumed by both CLI and desktop UI.
3. Avoid duplicating pipeline logic between CLI and UI.

### Proposed Modules
1. `core` (existing): models + distances.
2. `pipeline` (new): scan/extract/index/query orchestration API.
3. `cli` (new entrypoint): production command parsing + run execution + reporting.
4. `desktop` (new entrypoint): Kotlin experimentation UI using Compose Desktop (Compose Multiplatform).
5. `persistence` (new): index snapshot serialization and metadata versioning.

## CLI Plan

### Feature Set (MVP)
1. `scan-index` command:
   - inputs: `--repos`, `--mode`, optional framework filters.
   - outputs: index snapshot file + summary report.
2. `query` command:
   - inputs: `--index-file`, `--component-id|--query-file`, `--top-k`.
   - outputs: ranked neighbors with scores and optional JSON export.
3. `inspect` command:
   - shows index metadata (version, corpus size, framework counts, created time).
4. `validate` command:
   - validates snapshot file integrity and compatibility.

### Packaging
1. Gradle `application` distribution (`installDist`, `distZip`).
2. Optional fat jar task for easy sharing.
3. Versioned CLI release artifacts.

## Desktop App Plan

### UI Stack
1. Compose Desktop (Compose Multiplatform):
   - most common modern desktop UI framework in the Kotlin ecosystem.
   - good Kotlin integration and packaging for macOS/Windows/Linux.

### MVP Screens
1. Workspace/Corpus screen:
   - select repos root.
   - set sample percentage (1-100), this should also display the translation to number of repos.
   - optional framework toggles.
2. Index screen:
   - build index from selected sample.
   - save index snapshot to file.
   - load index snapshot from file.
   - show index metadata and status.
3. Query screen:
   - choose component/query id.
   - run similarity search.
   - show top results and layer distances.
4. Run logs/telemetry panel:
   - scanner failures, fallback counts, extractor summaries.

### Persistence UX
1. `Save Index As...` writes index + metadata JSON (or binary + JSON header).
2. `Open Index...` loads and validates schema version.
3. Friendly error states for incompatible versions/corrupt files.

## Index Persistence Design

### Snapshot Contents
1. Snapshot version.
2. Build config (mode, flags, pivots, seed, sample settings).
3. Corpus identifiers and signatures needed for query.
4. Permutation index internals.
5. Summary stats (framework counts, createdAt, source repos hash/checksum optional).

### Format
1. Start with JSON for readability and easier debugging.
2. Add optional compressed/binary format later if size/performance requires it.
3. Enforce backward compatibility policy by version field.

## Sampling Strategy

### Requirements
1. Deterministic sampling with explicit seed.
2. Percentage sampling across full corpus and optional per-framework stratified mode.
3. Persist sampled repo list in run metadata for reproducibility.

### Proposed CLI/UI Parameters
1. `sample.percent` (1-100, desktop-first).
2. `sample.seed` (default fixed seed for reproducibility).
3. `sample.mode`:
   - `global` (uniform across all repos),
   - `stratified-framework` (same percentage within each framework).

## Rollout Phases

### Execution Rule
1. Complete all Phase 1 deliverables and pass Phase 1 done criteria before starting any Phase 2 CLI command implementation.
2. Do not implement desktop UI features (Phase 3) until Phase 2 production CLI contract is stable.

### Phase 1: Foundation (shared services + persistence schema)
1. Create `pipeline` service API consumed by CLI/UI.
2. Implement snapshot schema v1 and save/load for current index model.
3. Add deterministic sampling service.

Done criteria:
1. Unit tests for save/load roundtrip.
2. Unit tests for deterministic sampling.
3. Existing pipeline tests remain green.

### Phase 2: CLI productization
1. Introduce structured CLI commands (`scan-index`, `query`, `inspect`, `validate`).
2. Add machine-readable output (`--json-out`) and human summary.
3. Add distribution tasks and usage docs.

Done criteria:
1. CLI E2E smoke tests.
2. Artifact build succeeds from clean checkout.
3. Reproducible run metadata generated.

### Phase 3: Desktop MVP
1. Add Compose Desktop app module/entrypoint.
2. Implement corpus/index/query/log panels.
3. Add experimentation controls (sampling percentage/seed/mode) and connect to shared pipeline services.

Done criteria:
1. Build index from sampled corpus via UI.
2. Save/load snapshot works.
3. Query results shown with distances and metadata.

### Phase 4: Hardening
1. Performance tuning for large corpus sampling/index load.
2. Snapshot compatibility checks and migration path for v2.
3. UX polish and error handling improvements.

Done criteria:
1. Defined performance budget met on representative corpus subset.
2. Robust failure handling without app crashes.

## Testing Plan
1. Unit:
   - sampling determinism and stratification.
   - snapshot serialization/deserialization.
2. Integration:
   - CLI `scan-index` -> save -> load -> query.
3. UI smoke:
   - basic navigation, build/load/query happy path.
   - sampling controls and experiment run flow.
4. Regression:
   - existing `scripts/run-tests.sh` remains required gate.

## Risks and Mitigations
1. Risk: Snapshot format drift breaks old files.
   - Mitigation: explicit versioning + compatibility checks.
2. Risk: Desktop app duplicates pipeline logic.
   - Mitigation: strict shared service layer.
3. Risk: Large corpus operations are slow.
   - Mitigation: sampling defaults + progress feedback + background tasks.

## Immediate Next Tasks
1. Add `pipeline` service layer and snapshot model classes.
2. Implement snapshot save/load for current permutation index.
3. Implement deterministic repo sampling service (`global` + `stratified-framework`) for desktop experimentation controls.
4. Add/confirm Phase 1 fast tests:
   - snapshot roundtrip
   - sampling determinism/stratification
5. Phase-gate check:
   - only after items 1-4 are complete and green, begin Phase 2 (`scan-index`, `query`, `inspect`, `validate`).

## Tracking
Use this document as the source of truth for status updates. Add a short "Status Update" section per sprint/iteration with:
1. Completed items.
2. In-progress items.
3. Blockers and decisions.

## Status Update (2026-02-25)
1. Completed:
   - Phase 1, step 1 completed with shared API contracts:
     - `src/main/kotlin/pipeline/SimilarityPipelineService.kt`
     - `src/main/kotlin/persistence/IndexSnapshotModels.kt`
   - Phase 1, step 2 completed with snapshot IO + validation:
     - `src/main/kotlin/persistence/IndexSnapshotIO.kt`
   - Added fast tests for snapshot save/load and version validation:
     - `src/test/kotlin/tests/IndexSnapshotIOTests.kt`
     - `src/test/kotlin/RunAllTests.kt`
   - Test gate currently green: `91/91` via `scripts/run-tests.sh`.
   - Phase 1, step 3 completed with deterministic repo sampling service:
     - `src/main/kotlin/pipeline/RepoSamplingService.kt`
   - Added sampling determinism/stratification tests:
     - `src/test/kotlin/tests/RepoSamplingServiceTests.kt`
     - `src/test/kotlin/RunAllTests.kt`
   - Test gate currently green: `94/94` via `scripts/run-tests.sh`.
   - Phase 2 CLI contract implemented:
     - New production CLI entrypoint:
       - `src/main/kotlin/cli/SimilarityCliMain.kt`
     - Commands:
       - `scan-index` (build + snapshot save)
       - `query` (`--component-id` or `--query-file`)
       - `inspect`
       - `validate`
     - Shared orchestration service:
       - `src/main/kotlin/pipeline/DefaultSimilarityPipelineService.kt`
     - Snapshot schema v1 extended with persisted signatures (backward-compatible optional field):
       - `src/main/kotlin/persistence/IndexSnapshotModels.kt`
       - `src/main/kotlin/persistence/IndexSnapshotIO.kt`
       - `src/main/kotlin/persistence/SignatureSnapshotMapper.kt`
     - CLI integration tests:
       - `src/test/kotlin/tests/ProductionCliTests.kt`
       - `src/test/kotlin/RunAllTests.kt`
     - Build/distribution wiring:
       - `build.gradle.kts` now points default application main class to `cli.SimilarityCliMainKt`.
   - Phase 3 desktop MVP scaffold started with Compose Desktop module:
     - `desktop-app/build.gradle.kts`
     - `desktop-app/src/main/kotlin/desktop/DesktopApp.kt`
     - `settings.gradle.kts` includes `desktop-app`.
   - Desktop Gradle wiring verified:
     - `./gradlew :desktop-app:tasks --all` passes.
   - Phase 3 completion delivered:
     - Desktop smoke automation script:
       - `scripts/smoke_desktop_mvp.sh`
     - Desktop manual smoke checklist:
       - `docs/desktop-smoke-checklist.md`
     - Verified:
       - `bash scripts/smoke_desktop_mvp.sh` passes.
   - Phase 4 hardening delivered:
     - Runtime benchmark script + budget enforcement:
       - `scripts/benchmark_cli_runtime.py`
       - `datasets/quality/cli-runtime-budget.json`
     - Measured baseline generated:
       - `out/cli-runtime-benchmark-summary.json`
     - Budget enforcement verified:
       - `python3 scripts/benchmark_cli_runtime.py --iterations 2 --budget datasets/quality/cli-runtime-budget.json --enforce-budget` passes.
     - Snapshot compatibility checks and migration path:
       - `src/main/kotlin/persistence/IndexSnapshotCompatibility.kt`
       - `docs/index-snapshot-v2-migration.md`
     - CLI `validate` now reports compatibility metadata (`detected_version`, `load_supported`, `migration_required`).
     - Error handling hardening:
       - CLI top-level failure handling exits cleanly with non-zero status and message.
   - Regression gate status:
     - `scripts/run-tests.sh` passes with `98/98`.
   - Conference-grade evaluation protocol added:
     - `docs/similarity-evaluation-protocol.md`
     - defines benchmark, baselines, metrics, ablations, statistical tests, and acceptance gates.
2. In-progress:
   - No blockers; remaining work is incremental UX polish and optional scale tuning beyond MVP scope.
3. Blockers/decisions:
   - Snapshot schema v1 remains JSON-first for readability and inspect/validate CLI support.
