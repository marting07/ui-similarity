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

If you have Kotlin CLI installed, you can run the compiled classes produced by IntelliJ:

```bash
kotlin -classpath out/production/ui-similarity MainKt --repos /data/repos
```

You can also select scanner mode:

```bash
kotlin -classpath out/production/ui-similarity MainKt --repos /data/repos --mode hybrid
```

Current status: `ast`/`hybrid` mode introduces a React AST scanner adapter scaffold.
Until a concrete AST engine is wired, it falls back to the existing React scanner path.

To wire an external React AST parser, set `UI_SIMILARITY_REACT_AST_CMD` to a command
that reads one JSON request from stdin and prints one JSON response to stdout.
The current request/response contract lives in:
`/Users/marting/Documents/Papers/ui-similarity-project/ui-similarity/src/main/kotlin/scanner/ReactAstContract.kt`.
For Angular, use `UI_SIMILARITY_ANGULAR_AST_CMD` with the contract in
`/Users/marting/Documents/Papers/ui-similarity-project/ui-similarity/src/main/kotlin/scanner/AngularAstContract.kt`.

Default behavior now uses the bundled command:
`node scripts/react-ast-scan.mjs`.
This is an offline lightweight parser for React exports/style imports; it is a bridge
step until full TypeScript AST parser tooling is available.
Angular uses `node scripts/angular-ast-scan.mjs` with the same bridge approach.
TODO (when network is available): replace tokenizer logic in `scripts/react-ast-scan.mjs`
with `typescript` AST parsing, keeping the same JSON contract in
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
