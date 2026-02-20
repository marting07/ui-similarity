# AST Migration Plan (React / Angular / Vue)

## Objective
Move component discovery and feature extraction from regex/text heuristics to parser/AST-backed extraction while preserving the current pipeline and test speed.

## Why this migration
- Reduces false positives/negatives in scanner output.
- Handles syntax variants (`export { X }`, inline templates/styles, decorators, aliases).
- Improves maintainability versus expanding regex rules.

## Proposed target architecture

### Folder structure

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
      ... (existing heuristic extractors as fallback)
  scanner/
    ... (existing scanners; migrated gradually or wrapped by AST scanners)
```

### Interfaces (incremental)

1. `AstScanner`: framework-specific scanner that returns robust `ComponentSourceRef` from AST metadata.
2. `AstFeatureExtractor`: converts parsed template/style/logic nodes into feature objects.
3. `AstExtractionCoordinator`: tries AST path first, falls back to existing simple extractors/scanners when parser is unavailable or fails.

## Parser choices

- React/TypeScript: TypeScript compiler API (`ts-morph` or `typescript`) to parse exports, JSX return trees, imports.
- Angular: TypeScript AST + decorator object parsing (`@Component`) for `templateUrl`, `template`, `styles`, `styleUrls`.
- Vue: Vue SFC compiler (`@vue/compiler-sfc`) + template AST for `<template>` and styles blocks.

Note: these parser libraries are JS ecosystem tools. We can integrate via a small Node helper layer and keep Kotlin orchestration, or introduce Kotlin-native parser alternatives where practical.

## Rollout phases

### Phase 1: Scanner AST parity (highest ROI)
- Replace regex export detection in React scanner with AST export resolution.
- Replace Angular decorator regex parsing with AST property extraction.
- Parse Vue SFC blocks with compiler instead of plain file assumptions.
- Keep existing scanner outputs compatible (`ComponentSourceRef`).

Done criteria:
- Current scanner test suite passes.
- Add parity tests for known edge cases:
  - React default/class exports and re-exports.
  - Angular inline template/styles and class names without `Component` suffix.
  - Vue multi-style block SFCs.

### Phase 2: Template/style/logic AST feature extraction
- DOM features from JSX/Angular template/Vue template AST nodes.
- CSS features from parsed declarations (instead of substring checks).
- Behavior features from AST call graphs and handler references.

Done criteria:
- Feature extractor tests compare old vs new on fixtures.
- Deterministic outputs and bounded distance metrics retained.

### Phase 3: Coordinator + fallback policy
- Introduce runtime flag/config:
  - `extract.mode=ast|simple|hybrid`.
- `hybrid` runs AST first and fallback on parser failure.
- Log parser failure reasons by repo/framework.

Done criteria:
- End-to-end smoke test passes in all three modes.
- Failure telemetry available for confidence rollout.

### Phase 4: Quality gates + performance
- Add benchmark fixture set with target runtime budget.
- Compare extraction precision/recall against a curated labeled mini-dataset.

Done criteria:
- AST path meets agreed quality threshold and acceptable runtime.

## Testing strategy

- Keep existing fast fixture tests as hard gate.
- Add AST-specific tests by framework:
  - scanner correctness
  - source loading correctness (inline + external)
  - feature extraction consistency
- Keep one tiny full-pipeline smoke test for regressions.

## Dependency and integration strategy

Because current project is not Gradle-managed, use this order:
1. Add Gradle build with Kotlin/JVM and test tasks.
2. Add Node helper package (if JS parsers are used) with pinned versions and CLI contract.
3. Call helper from Kotlin through a stable JSON interface.

CLI contract sketch:
- Input: repo root + framework + file list.
- Output: JSON array of normalized component descriptors (paths + inline blocks + export/class names).

## Risks and mitigations

- Risk: parser stack complexity increases.
  - Mitigation: `hybrid` mode fallback and strict fixture tests.
- Risk: runtime cost increases.
  - Mitigation: cache AST results per file hash and benchmark phase gate.
- Risk: cross-language toolchain friction.
  - Mitigation: stable JSON contracts and version pinning.

## Immediate next implementation tasks

1. Introduce Gradle build (`build.gradle.kts`) with Kotlin and test tasks.
2. Add `extract.mode` configuration and coordinator wiring in `Main.kt`.
3. Implement React AST scanner first (smallest scope, highest return).
4. Add parity fixtures for re-export/default export cases.
5. Promote React AST scanner to default in `hybrid` mode once tests are green.
6. When network access is available, replace tokenizer logic in `scripts/react-ast-scan.mjs` with TypeScript AST parsing (`typescript` package) while keeping the same JSON contract in `src/main/kotlin/scanner/ReactAstContract.kt`.
