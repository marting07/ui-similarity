# AST Migration Plan (React / Angular / Vue)

## Objective
Move component discovery and feature extraction from regex/text heuristics to parser/AST-backed extraction while preserving pipeline reliability, observability, and test speed.

## Status Update (2026-02-23)
1. AST migration phases are functionally complete for current rollout goals:
   - scanner bridges + parser-backed paths are in place for React/Angular/Vue,
   - representative-large parity and quality gates are passing in `hybrid` mode.
2. This plan is now in maintenance mode (parity/quality guardrails + optional hardening).
3. New feature planning (distributable experimentation CLI + Kotlin desktop app) is tracked in:
   - `docs/experimentation-cli-desktop-plan.md`

## Current Status Snapshot

| Area | Status | Notes |
|---|---|---|
| Scanner mode flag (`simple|ast|hybrid`) | Done | Implemented in `Main.kt` and scanner factory |
| React AST scanner bridge | Done (bridge + TS AST path) | Uses `typescript` AST when package is available; tokenizer fallback kept for offline reliability |
| Angular AST scanner bridge | Done (bridge + TS AST path) | Uses `typescript` AST when package is available; regex fallback kept for offline reliability |
| Vue AST scanner bridge | Done (bridge + SFC compiler path) | Uses `@vue/compiler-sfc` when package is available; regex fallback kept for offline reliability |
| Hybrid fallback behavior | Done | Strict/Hybrid behavior covered by tests |
| Runtime parser-failure telemetry | Done | Per-framework/reason/repo summary implemented |
| Scanner parity audit + summarizer | Done | CSV output + summary script implemented |
| AST feature extraction (Phase 2) | Done | React/Angular/Vue DOM/CSS/Behavior extractor bridges implemented with fallback + tests |
| Gradle build files | Done | `build.gradle.kts` / `settings.gradle.kts` added |
| Gradle wrapper committed | Done | `gradlew`, `gradlew.bat`, `gradle/wrapper/*` present |

## Why this migration
- Reduce false positives/negatives in scanner output.
- Handle syntax variants (`export { X }`, inline templates/styles, decorators, aliases).
- Improve maintainability versus expanding regex rules.

## Target Architecture

```text
src/main/kotlin/
  extractor/
    ast/
      common/
        AstExtractionModels.kt
        AstExtractor.kt
        AstExtractionCoordinator.kt
      react/
        ReactAstScanner.kt
        ReactAstFeatureExtractor.kt
      angular/
        AngularAstScanner.kt
        AngularAstFeatureExtractor.kt
      vue/
        VueAstScanner.kt
        VueAstFeatureExtractor.kt
    simple/
      ... (heuristic extractors as fallback)
  scanner/
    ... (scanner bridges + mode/fallback routing)
```

## Core Interfaces (Incremental)
1. `AstScanner`: framework-specific scanner returning robust `ComponentSourceRef`.
2. `AstFeatureExtractor`: converts parsed template/style/logic nodes into feature objects.
3. `AstExtractionCoordinator`: AST-first with fallback to simple extractors/scanners.

## Parser Choices
- React/TypeScript: `typescript` (or `ts-morph`) AST for exports, JSX, imports.
- Angular: TypeScript AST + decorator object parsing for `templateUrl/template/styles/styleUrls`.
- Vue: `@vue/compiler-sfc` + template AST.

## Rollout Phases

### Phase 1: Scanner AST Parity
- React/Angular/Vue scanner bridges in `ast|hybrid` mode.
- Keep scanner outputs compatible with `ComponentSourceRef`.
- Maintain strict mode (`ast`) and fallback mode (`hybrid`).

Done criteria:
- Scanner tests pass.
- Parity fixtures for edge cases pass.
- Parity audit CSV + summary are generated and reviewable.

### Phase 2: AST Feature Extraction
- DOM features from JSX/Angular template/Vue template AST.
- CSS features from parsed declarations (not substrings).
- Behavior features from AST call/event/state patterns.

Done criteria:
- Feature extractor tests compare AST vs simple on fixtures.
- Distance inputs remain deterministic and bounded.
- Fallback path works per-layer (DOM/CSS/Behavior).

### Phase 3: Coordinator + Fallback Policy
- Per-layer and per-framework routing:
  - `extract.mode=ast|simple|hybrid`
  - optional layer flags for safe rollout (`dom.ast.enabled`, `css.ast.enabled`, `behavior.ast.enabled`).
- Runtime telemetry for scanner and extractor failures.

Done criteria:
- End-to-end smoke test passes in all modes.
- Failure telemetry includes framework/repo/reason/fallback-used.

### Phase 4: Quality Gates + Performance
- Benchmark fixture set with target runtime budget.
- Precision/recall comparison against labeled mini-dataset.

Done criteria:
- AST path meets quality and performance thresholds.
- Promotion gates met to enable AST-first defaults safely.

## Promotion Gates (Required)
- Scanner parity: thresholds locked below (overall + per-framework).
- Extractor parity: thresholds locked below (layer + framework).
- Hybrid fallback rate: `< 2%` across representative corpus.
- Smoke + fast test suite: `100%` pass.
- Runtime budget: must pass `datasets/quality/benchmark-budget.json` with `--enforce-budget`.

## Locked Thresholds (v1, 2026-02-23)

Baseline sources:
- `out/scanner-parity-sampled-summary.txt`
- `out/extractor-parity-sampled-summary.txt`
- `out/scanner-parity-representative-large-summary.txt`
- `out/extractor-parity-representative-large-summary.txt`
- `out/quality-mini-dataset-summary.txt`
- `out/ast-extractor-benchmark-summary.txt`

### Scanner parity thresholds
- `hybrid` confidence gate (must pass before wider rollout):
  - overall component parity delta rate `<= 10%`
  - per-framework component parity delta rate `<= 15%`
  - fallback failure rate `< 2%`
- AST-first promotion gate (must pass before switching defaults):
  - overall component parity delta rate `<= 2%`
  - per-framework component parity delta rate `<= 5%`
  - no framework with mismatch repo rate `> 10%`

### Extractor parity thresholds
- `hybrid` confidence gate:
  - CSS mismatch rate per framework `<= 5%`
  - Behavior mismatch rate per framework `<= 10%`
  - DOM mismatch rate per framework `<= 100%` (temporary guardrail; DOM parity is known non-parity today)
- AST-first promotion gate:
  - CSS mismatch rate per framework `<= 2%`
  - Behavior mismatch rate per framework `<= 5%`
  - DOM mismatch rate per framework `<= 25%`

### Quality mini-dataset thresholds
- Scanner F1 `>= 0.98`
- Extractor F1 `>= 0.95`
- Combined F1 `>= 0.96`

### Runtime budget thresholds
- Enforced by `datasets/quality/benchmark-budget.json`
- Gate command:
  - `python3 scripts/benchmark_ast_extractors.py --iterations 15 --warmup 3 --budget datasets/quality/benchmark-budget.json --enforce-budget`

### Current Status vs Locked Thresholds (representative-large baseline)
Sample scope note:
- Latest representative-large refresh used `/tmp/ui-similarity-representative-large/repos` (9 repos compared in parity: React + Angular + Vue).
- Pass:
  - fallback scanner failures (`0` on representative-large run)
  - scanner parity gates (React + Angular + Vue: `only_simple_total=0`, `only_ast_total=0`)
  - DOM parity gates (React/Angular/Vue all at `0` mismatches)
  - CSS parity gates (all three frameworks)
  - Behavior parity gates (all three frameworks; Angular has `1/113` mismatch, within thresholds)
  - mini-dataset quality gates (Scanner/Extractor/Combined F1)
  - runtime budget gates (all framework/layer entries pass)
- Fail:
  - none against locked thresholds on representative-large baseline
  - optional full-corpus confirmation still pending

## Required Runtime Artifacts
- Parity report CSV: `out/scanner-parity.csv`
- Parity summary text: `out/scanner-parity-summary.txt`
- Extractor parity summary text: `out/extractor-parity-summary.txt`
- AST failure summary in runtime logs (framework/repo/reason/fallback).

## Testing Strategy
- Keep fast fixture tests as hard gate.
- AST-specific tests by framework:
  - scanner correctness
  - source loading correctness (inline + external)
  - feature extraction consistency
- Keep tiny full-pipeline smoke test.

## Dependency and Integration Strategy
1. Kotlin/Gradle build tasks (`fastTest`, `check`, pipeline task).
2. Node helper scripts with stable JSON contract.
3. Replace tokenizer scripts with true parser libraries when network access allows.

### Offline vs Network-Aware Operation
- Offline: use lightweight tokenizer/parser scripts with strict contracts + fallback.
- Network-enabled: install/pin parser packages and migrate scripts while preserving JSON contracts.

## Risks and Mitigations
- Parser complexity growth:
  - Mitigation: hybrid fallback + strict tests + parity audit.
- Runtime overhead:
  - Mitigation: benchmark gate + optional caching by file hash.
- Cross-language tooling friction:
  - Mitigation: stable JSON contracts, pinned package versions, wrapper scripts.

## Execution Plan

### Completed
1. Add `extract.mode` scanner routing (`simple|ast|hybrid`).
2. Implement scanner bridges for React/Angular/Vue.
3. Add runtime parser-failure telemetry summary.
4. Add scanner parity audit output + summarizer script.
5. Add Gradle build files (`build.gradle.kts`, `settings.gradle.kts`).
6. Implement React AST feature extractors for DOM/CSS/Behavior with fallback telemetry and parity summaries.
7. Add React DOM/CSS/Behavior AST extractor tests to fast suite.
8. Add extractor-layer runtime flags (`dom.ast.enabled`, `css.ast.enabled`, `behavior.ast.enabled`) via CLI toggles.
9. Implement Angular AST feature extractors for DOM/CSS/Behavior with fallback telemetry and tests.
10. Implement Vue AST feature extractors for DOM/CSS/Behavior with fallback telemetry and tests.
11. Add extractor-level parity artifact (`out/extractor-parity-summary.txt`) and cross-framework rollup (React/Angular/Vue, DOM/CSS/Behavior).
12. Add benchmark fixture runner + budget checks (`scripts/benchmark_ast_extractors.py`) with baseline budget file (`datasets/quality/benchmark-budget.json`).
13. Add mini labeled quality datasets + precision/recall evaluator (`scripts/evaluate_quality.py`) for scanner + extractor quality.
14. Include Vue in scanner parity audit and regenerate parity summaries.
15. Resolve scanner parity gaps (React default class + re-export list alignment in AST bridge scanner).
16. Reduce DOM/CSS/Behavior parity drift by aligning AST bridge extractors to simple parity semantics.
17. Promote default CLI extraction mode to `hybrid` (AST-first with fallback) after representative gates passed.
18. Add React/Angular TypeScript-AST scanner paths with parity-preserving fallback behavior.
19. Add Vue SFC-compiler scanner path with parity-preserving regex fallback.
20. Install scanner parser dependencies (`typescript`, `@vue/compiler-sfc`) and validate representative-large parity gates.

### Active Next
1. Do network-enabled parser migration while preserving JSON contracts:
   - keep parity audits green as repos/language variants evolve
   - add scanner parity regression fixtures for known tricky exports/decorators/SFC edge cases
2. Add CI quality gate job to enforce scanner/extractor parity summaries and benchmark budgets.
3. Optional: run one full-corpus confirmation pass before changing from `hybrid` default to strict `ast`.

## Remaining Work (Clearly Defined)
1. Optional performance hardening:
   - cache AST extraction results by file hash
   - enforce benchmark budgets in CI (`--enforce-budget`)
2. Optional rollout hardening:
   - run one full-corpus confirmation pass before switching default mode from `hybrid` to strict `ast`
