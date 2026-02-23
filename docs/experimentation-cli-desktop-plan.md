# Experimentation CLI + Desktop App Plan

## Objective
Build a distributable experimentation platform for UI similarity research with:
1. A robust CLI for batch experiments and reproducible runs.
2. A Kotlin desktop app for interactive exploration.
3. Persistent index storage/load from file.
4. Corpus sampling controls (percentage-based selection from large repo corpus).

## Product Outcomes
1. Researchers can run reproducible experiments from terminal with saved configs.
2. Users can open the desktop app, sample a corpus subset, build/load an index, and query similar components.
3. Index build cost is amortized by saving/loading index snapshots.

## Scope

### In Scope
1. CLI packaging/distribution.
2. Desktop app MVP (single-user, local files).
3. Index persistence format and versioning.
4. Percentage sampling by framework and/or global corpus.
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
3. `cli` (new entrypoint): command parsing + run execution + reporting.
4. `desktop` (new entrypoint): Kotlin desktop UI (Compose Desktop recommended).
5. `persistence` (new): index snapshot serialization and metadata versioning.

## CLI Plan

### Feature Set (MVP)
1. `scan-index` command:
   - inputs: `--repos`, `--mode`, `--sample-percent`, optional framework filters.
   - outputs: index snapshot file + summary report.
2. `query` command:
   - inputs: `--index-file`, `--component-id|--query-file`, `--top-k`.
   - outputs: ranked neighbors with scores and optional JSON export.
3. `inspect` command:
   - shows index metadata (version, corpus size, framework counts, created time).
4. `audit` command:
   - scanner/extractor parity run wrappers and summary output paths.

### Packaging
1. Gradle `application` distribution (`installDist`, `distZip`).
2. Optional fat jar task for easy sharing.
3. Versioned CLI release artifacts.

## Desktop App Plan

### UI Stack
1. Kotlin Compose Desktop (preferred):
   - modern UI, good Kotlin integration, packaged desktop builds.

### MVP Screens
1. Workspace/Corpus screen:
   - select repos root.
   - set sample percentage (1-100).
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
1. `sample.percent` (1-100).
2. `sample.seed` (default fixed seed for reproducibility).
3. `sample.mode`:
   - `global` (uniform across all repos),
   - `stratified-framework` (same percentage within each framework).

## Rollout Phases

### Phase 1: Foundation (shared services + persistence schema)
1. Create `pipeline` service API consumed by CLI/UI.
2. Implement snapshot schema v1 and save/load for current index model.
3. Add deterministic sampling service.

Done criteria:
1. Unit tests for save/load roundtrip.
2. Unit tests for deterministic sampling.
3. Existing pipeline tests remain green.

### Phase 2: CLI productization
1. Introduce structured CLI commands (`scan-index`, `query`, `inspect`, `audit`).
2. Add machine-readable output (`--json-out`) and human summary.
3. Add distribution tasks and usage docs.

Done criteria:
1. CLI E2E smoke tests.
2. Artifact build succeeds from clean checkout.
3. Reproducible run metadata generated.

### Phase 3: Desktop MVP
1. Add Compose Desktop app module/entrypoint.
2. Implement corpus/index/query/log panels.
3. Connect UI actions to shared pipeline services.

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
3. Implement deterministic repo sampling service (`global` + `stratified-framework`).
4. Add CLI command structure around current `Main.kt` behavior.
5. Bootstrap Compose Desktop app with Workspace/Index/Query skeleton.

## Tracking
Use this document as the source of truth for status updates. Add a short "Status Update" section per sprint/iteration with:
1. Completed items.
2. In-progress items.
3. Blockers and decisions.
