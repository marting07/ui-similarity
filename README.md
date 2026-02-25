# UI Component Similarity Toolkit

This project implements the complete pipeline outlined in the research plan for measuring
similarity between web UI components.  It includes utilities for discovering and cloning
open‑source repositories, scanning those repositories for React/Angular/Vue components,
extracting multi‑layer feature representations (DOM structure, CSS styling and behavioural
information), building a corpus, splitting the corpus into training and query sets,
constructing a proximity index over the components, and running similarity queries.

The code is organised into several modules:

* **`core`** – data models for component signatures and their constituent feature classes,
  together with metric implementations that compute distances between signatures.
* **`extractor`** – framework‑agnostic and framework‑specific feature extractors that map
  raw source files to `ComponentSignature` objects.
* **`scanner`** – repository scanners that traverse cloned repositories and produce
  `ComponentSourceRef` objects describing where each component’s template, logic and
  style live on disk.  Separate scanners exist for React, Angular and Vue projects.
* **`corpus`** – data structures for binding source references to extracted signatures
  (`ComponentRecord`) and grouping them into a corpus (`ComponentCorpus`) for later
  indexing and experimentation.  The corpus layer also provides convenience functions
  for train/query splits.
* **`index`** – a permutation‑based proximity index (`PermutationIndex`) that organises
  components in metric space and supports fast approximate nearest neighbour queries.

The top‑level **`Main.kt`** script demonstrates the full pipeline on a user‑specified
directory of cloned repositories.  It scans each repository, extracts component
signatures, builds a corpus, constructs a permutation index using randomly chosen
pivots, and then performs similarity queries on the query split.  Although network
operations (e.g. cloning from GitHub) are not performed directly in this codebase,
the architecture anticipates an external discovery/cloning step to populate
`/data/repos/…` before running the scanner.

## Running

With Gradle (recommended):

```bash
./gradlew fastTest
./gradlew runCli -Pargs="scan-index --repos /data/repos --out out/index.json --mode hybrid"
```

Production CLI commands:

```bash
./gradlew runCli -Pargs="inspect --index-file out/index.json"
./gradlew runCli -Pargs="validate --index-file out/index.json"
./gradlew runCli -Pargs="query --index-file out/index.json --component-id github.com/org/repo:src/Button.tsx#Button --top-k 10 --top-n 10"
```

Desktop experimentation app (Compose Desktop):

```bash
./gradlew :desktop-app:run
```

Desktop smoke pre-check:

```bash
bash scripts/smoke_desktop_mvp.sh
```

Legacy demo pipeline (`Main.kt`) remains available:

```bash
./gradlew runPipeline -Pargs="--repos /data/repos --mode hybrid"
```

You can also select scanner mode:

```bash
kotlin -classpath out/production/ui-similarity MainKt --repos /data/repos --mode hybrid
```

Default mode is now `hybrid` (AST-first with fallback). Use `--mode simple` to force heuristic extraction only.

To write a scanner parity audit report (simple vs AST IDs/counts for React/Angular):

```bash
kotlin -classpath out/production/ui-similarity MainKt --repos /data/repos --mode hybrid --audit-out out/scanner-parity.csv
```

To summarize that report and prioritize mismatches:

```bash
python3 scripts/summarize_scanner_audit.py --input out/scanner-parity.csv --top 15 --output out/scanner-parity-summary.txt
```

To evaluate scanner/extractor precision-recall on the mini labeled dataset:

```bash
python3 scripts/evaluate_quality.py --output out/quality-mini-dataset-summary.txt
```

To benchmark AST extractor helper scripts against runtime budgets:

```bash
python3 scripts/benchmark_ast_extractors.py --iterations 15 --warmup 3 --budget datasets/quality/benchmark-budget.json --output out/ast-extractor-benchmark-summary.txt
```

To fail CI/local runs when budget thresholds are exceeded, add:

```bash
--enforce-budget
```

To benchmark production CLI runtime budgets on a representative tiny subset:

```bash
python3 scripts/benchmark_cli_runtime.py --iterations 3 --output out/cli-runtime-benchmark-summary.json
```

To enforce runtime budget thresholds:

```bash
python3 scripts/benchmark_cli_runtime.py --iterations 3 --budget datasets/quality/cli-runtime-budget.json --enforce-budget
```

Current status: `ast`/`hybrid` mode introduces a React AST scanner adapter scaffold.
Until a concrete AST engine is wired, it falls back to the existing React scanner path.

To wire an external React AST parser, set `UI_SIMILARITY_REACT_AST_CMD` to a command
that reads one JSON request from stdin and prints one JSON response to stdout.
The current request/response contract lives in:
`/Users/marting/Documents/Papers/ui-similarity-project/ui-similarity/src/main/kotlin/scanner/ReactAstContract.kt`.
For Angular, use `UI_SIMILARITY_ANGULAR_AST_CMD` with the contract in
`/Users/marting/Documents/Papers/ui-similarity-project/ui-similarity/src/main/kotlin/scanner/AngularAstContract.kt`.
For Vue, use `UI_SIMILARITY_VUE_AST_CMD` with the contract in
`/Users/marting/Documents/Papers/ui-similarity-project/ui-similarity/src/main/kotlin/scanner/VueAstContract.kt`.

Default behavior now uses the bundled command:
`node scripts/react-ast-scan.mjs`.
When the `typescript` package is available, this scanner now uses TypeScript AST parsing
for export/import resolution; otherwise it falls back to the built-in tokenizer bridge.
Angular uses `node scripts/angular-ast-scan.mjs`.
When the `typescript` package is available, this scanner uses TypeScript AST parsing
for class/decorator/template/style extraction; otherwise it falls back to the regex bridge.
Vue uses `node scripts/vue-ast-scan.mjs` with the same bridge approach.
When `@vue/compiler-sfc` is available, the Vue scanner uses SFC compiler parsing for
template/style block extraction; otherwise it falls back to lightweight regex parsing.
JSON contract remains unchanged in
`src/main/kotlin/scanner/ReactAstContract.kt`.

If you only have `java`, add the Kotlin standard library to the classpath:

```bash
java -cp "out/production/ui-similarity:/path/to/kotlin-stdlib.jar" MainKt --repos /data/repos
```

You can locate `kotlin-stdlib.jar` on macOS with:

```bash
find /usr/local /opt/homebrew -name "kotlin-stdlib.jar" 2>/dev/null | head -n 1
```

**Note:** This project uses Kotlin for its implementation.  The code files provided here
represent a self‑contained library and CLI, but compilation and execution are not
performed within this environment.  Researchers can download the source, set up a
Gradle project with the Kotlin standard library, and run the CLI on a corpus of
repositories on their own machines.

## Fast Local Tests (Small Fixtures)

To validate scanner, source loading and feature extraction steps quickly with tiny
fixtures, run:

```bash
bash scripts/run-tests.sh
```

This compiles `src/main/kotlin` + `src/test/kotlin` with `kotlinc` and executes
`RunAllTestsKt`.  The suite is intentionally lightweight and does not depend on the
large `data/repos` corpus.

CI runs the same command on every push and pull request via
`.github/workflows/tests.yml`.

## AST Roadmap

The parser/AST migration plan (architecture, rollout phases, and testing strategy)
is documented in `/Users/marting/Documents/Papers/ui-similarity-project/ui-similarity/docs/ast-migration-plan.md`.

## CLI + Desktop Roadmap

The experimentation CLI + Kotlin desktop app implementation plan is documented in
`/Users/marting/Documents/Papers/ui-similarity-project/ui-similarity/docs/experimentation-cli-desktop-plan.md`.

The production CLI command contract is documented in
`/Users/marting/Documents/Papers/ui-similarity-project/ui-similarity/docs/production-cli-contract.md`.

Snapshot v2 compatibility/migration policy is documented in
`/Users/marting/Documents/Papers/ui-similarity-project/ui-similarity/docs/index-snapshot-v2-migration.md`.

Desktop smoke checklist is documented in
`/Users/marting/Documents/Papers/ui-similarity-project/ui-similarity/docs/desktop-smoke-checklist.md`.

Conference-grade similarity evaluation protocol is documented in
`/Users/marting/Documents/Papers/ui-similarity-project/ui-similarity/docs/similarity-evaluation-protocol.md`.

Run the retrieval benchmark starter protocol:

```bash
python3 scripts/evaluate_retrieval_benchmark.py --dataset datasets/quality/retrieval-benchmark-v1.json --scope all
python3 scripts/run_similarity_benchmark_matrix.py --dataset datasets/quality/retrieval-benchmark-v1.json --out-dir out/retrieval-benchmark-matrix
python3 scripts/compare_retrieval_methods.py --summary-json out/retrieval-benchmark-matrix/summary-all.json --method-a proposed_full --method-b random_baseline
python3 scripts/compute_annotation_agreement.py --annotator-a datasets/quality/annotation/annotation-batch-template.csv --annotator-b datasets/quality/annotation/annotation-batch-template.csv --output out/annotation-agreement.json
```

Generate expanded Tier B/Tier C benchmark packs and run full matrix:

```bash
python3 scripts/generate_retrieval_benchmarks.py --seed-dataset datasets/quality/retrieval-benchmark-v1.json --tier-b-out datasets/quality/retrieval-benchmark-tier-b.json --tier-c-out datasets/quality/retrieval-benchmark-tier-c-heldout.json --tier-b-query-target 360 --tier-c-query-target 120 --copies-per-framework 36
python3 scripts/run_similarity_benchmark_matrix.py --dataset datasets/quality/retrieval-benchmark-tier-b.json --out-dir out/retrieval-benchmark-tier-b-matrix
python3 scripts/run_similarity_benchmark_matrix.py --dataset datasets/quality/retrieval-benchmark-tier-c-heldout.json --out-dir out/retrieval-benchmark-tier-c-matrix
```

Annotation workflow utilities:

```bash
python3 scripts/create_annotation_batches.py --dataset datasets/quality/retrieval-benchmark-tier-b.json --out-dir datasets/quality/annotation --max-pairs-per-query 6
python3 scripts/simulate_annotations_from_benchmark.py --dataset datasets/quality/retrieval-benchmark-tier-b.json --annotator-a datasets/quality/annotation/retrieval-benchmark-tier-b-annotator-a.csv --annotator-b datasets/quality/annotation/retrieval-benchmark-tier-b-annotator-b.csv
python3 scripts/compute_annotation_agreement.py --annotator-a datasets/quality/annotation/retrieval-benchmark-tier-b-annotator-a.csv --annotator-b datasets/quality/annotation/retrieval-benchmark-tier-b-annotator-b.csv --output out/annotation-agreement-tier-b.json
python3 scripts/merge_adjudication_labels.py --annotator-a datasets/quality/annotation/retrieval-benchmark-tier-b-annotator-a.csv --annotator-b datasets/quality/annotation/retrieval-benchmark-tier-b-annotator-b.csv --adjudication datasets/quality/annotation/retrieval-benchmark-tier-b-adjudication.csv --output out/final-labeled-pairs-tier-b.json
python3 scripts/build_publication_pack.py --name tier-b --output-root out/publication-packs
```

Automated human-in-the-loop round (minimize manual work to adjudication queue):

```bash
python3 scripts/automate_annotation_round.py prepare --dataset datasets/quality/retrieval-benchmark-tier-b.json --out-dir datasets/quality/annotation --round-name tier-b-round-1 --max-pairs-per-query 6
python3 scripts/automate_annotation_round.py process --annotator-a datasets/quality/annotation/tier-b-round-1-annotator-a.csv --annotator-b datasets/quality/annotation/tier-b-round-1-annotator-b.csv --out-dir out/annotation-rounds --round-name tier-b-round-1 --model-score-threshold 0.90
```

One-command full research round (prepare/process/evaluate/package):

```bash
python3 scripts/run_full_research_round.py --tier tier-b --round-name tier-b-round-auto --workspace . --simulate-annotations
```
