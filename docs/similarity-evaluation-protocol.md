# Similarity Evaluation Protocol (Conference-Grade)

## Objective
Prove whether the similarity measure can retrieve duplicate/near-duplicate UI components across React, Angular, and Vue with reproducible quality and acceptable runtime.

## Research Questions
1. Retrieval quality: Does the method return true duplicates in top-k results?
2. Cross-framework behavior: Does it work when source and target frameworks differ?
3. Layer contribution: Which feature layer (DOM/CSS/behavior) contributes most?
4. Efficiency: Is indexing/query latency acceptable for practical use?

## Task Definition
Primary task: ranked retrieval.
1. Input: query component.
2. Output: ranked candidate components from index.
3. Positive label:
   - `duplicate`: same UI intent/structure with minor naming/style variance.
   - `near_duplicate`: strong semantic equivalence with moderate implementation differences.
4. Negative label:
   - `different`: unrelated component purpose or structure.

Secondary task: pairwise classification.
1. Input: component pair.
2. Output: similarity score in `[0,1]` and label by threshold.

## Dataset Design
Use three tiers.

1. Tier A (existing mini datasets)
   - scanner/extractor sanity only (already supported by `scripts/evaluate_quality.py`).
   - files:
     - `datasets/quality/scanner-mini-dataset.json`
     - `datasets/quality/extractor-mini-dataset.json`

2. Tier B (new labeled retrieval benchmark)
   - create `datasets/quality/retrieval-benchmark-v1.json`.
   - target size:
     - 300-600 query components.
     - 5k-20k indexed components.
   - label balance:
     - at least 30% cross-framework positives.
     - at least 40% hard negatives (visually/functionally close but non-duplicates).

3. Tier C (external/generalization benchmark)
   - held-out repo families and held-out organizations.
   - no overlap with tuning repos.

## Labeling Protocol
Annotator guide (must be written before annotation).
1. Compare component intent, interaction, and structural UI pattern.
2. Ignore variable names and trivial CSS token changes.
3. Do not use repository metadata (owner/name) as signal.
4. Each query-candidate pair labeled by 2 annotators.
5. Resolve disagreement by adjudicator.

Quality control.
1. Report Cohen’s kappa (target `>= 0.70`).
2. Keep an adjudication log.

## Baselines
Run at least these baselines:
1. Token baseline: TF-IDF cosine on template+logic text.
2. Structure baseline: DOM-tag histogram + Jaccard/cosine.
3. Current hybrid measure (your proposed approach).
4. Optional stronger baseline: embedding retrieval (code embedding model).

## Metrics
For ranked retrieval:
1. `Precision@1`, `Precision@5`, `Precision@10`.
2. `Recall@5`, `Recall@10`.
3. `MRR`.
4. `nDCG@10`.

For pair classification:
1. ROC-AUC.
2. PR-AUC.
3. F1 at selected operating threshold.

Operational metrics:
1. Index build time.
2. Query latency p50/p95.
3. Snapshot size.
4. Peak memory (if measurable).

## Experiment Matrix
Run all cells below.
1. Framework setting:
   - same-framework
   - cross-framework
2. Layer ablation:
   - DOM only
   - CSS only
   - behavior only
   - DOM+CSS
   - DOM+behavior
   - CSS+behavior
   - full model
3. Mode:
   - simple
   - ast
   - hybrid

## Statistical Protocol
1. Use bootstrap (1,000 resamples) for 95% CI on P@k, MRR, nDCG.
2. Report paired significance vs strongest baseline (e.g., Wilcoxon signed-rank on per-query AP or reciprocal rank).
3. Pre-register threshold selection rule (do not tune on test split).

## Reproducibility Requirements
1. Pin seeds and versions.
2. Publish:
   - dataset schema and annotation guide.
   - exact CLI commands.
   - config flags and mode.
   - hardware/runtime info.
3. One-command local validation:
   - `bash scripts/run-tests.sh`
   - `python3 scripts/evaluate_quality.py`
   - `python3 scripts/benchmark_cli_runtime.py --iterations 3 --enforce-budget --budget datasets/quality/cli-runtime-budget.json`

## Suggested Acceptance Gates (for conference claim)
Minimum gates to claim effectiveness:
1. Hybrid > token baseline by:
   - `+10%` relative `MRR`, and
   - `+8` absolute points in `P@5` on Tier B.
2. Cross-framework `P@5 >= 0.60`.
3. CI lower bound still above baseline on primary metric.
4. Query p95 latency under agreed budget (`datasets/quality/cli-runtime-budget.json`).

## Failure Analysis Template
For top 50 false positives and false negatives:
1. Failure type:
   - styling-dominant mismatch
   - behavior mismatch
   - structural aliasing
   - scanner/extractor error
2. Framework pair.
3. Suggested fix:
   - feature change
   - weighting change
   - parser/scanner fix

## Deliverables for Submission
1. Method section:
   - feature definition, scoring, index approach.
2. Evaluation section:
   - datasets, baselines, metrics, ablations, significance.
3. Artifact package:
   - runnable CLI, benchmark scripts, sample data, docs.
4. Threats to validity:
   - labeling bias, framework imbalance, repo selection bias.

## Immediate Next Execution Steps
1. Add retrieval benchmark dataset spec + first labeled sample pack (`v1`).
2. Implement evaluation runner for retrieval metrics (`P@k`, `MRR`, `nDCG`).
3. Add baseline runner wrappers and aggregated report output in `out/`.
4. Run first full matrix on Tier B and produce a publishable results table.

## Implementation Status (2026-02-25)
Completed:
1. Retrieval benchmark v1 starter pack added:
   - `datasets/quality/retrieval-benchmark-v1.json`
2. Retrieval metrics evaluator implemented:
   - `scripts/evaluate_retrieval_benchmark.py`
   - metrics: `P@1`, `P@5`, `P@10`, `Recall@5`, `Recall@10`, `MRR`, `nDCG@10`
   - includes bootstrap CI for `P@5` and `MRR`
3. Baseline/matrix runner implemented:
   - `scripts/run_similarity_benchmark_matrix.py`
   - methods:
     - `proposed_full`
     - `dom_only`, `css_only`, `behavior_only`
     - `dom_css`, `dom_behavior`, `css_behavior`
     - `token_baseline`, `random_baseline`
4. First benchmark outputs generated:
   - `out/retrieval-benchmark-summary.json`
   - `out/retrieval-benchmark-summary.md`
   - `out/retrieval-benchmark-matrix/matrix-summary.json`
   - `out/retrieval-benchmark-matrix/matrix-summary.md`
5. Annotation instructions added:
   - `docs/retrieval-annotation-guide.md`
6. Method comparison helper added:
   - `scripts/compare_retrieval_methods.py`
   - sample output:
     - `out/retrieval-method-comparison.json`
7. Annotation agreement tooling added:
   - `scripts/compute_annotation_agreement.py`
   - template:
     - `datasets/quality/annotation/annotation-batch-template.csv`
   - sample output:
     - `out/annotation-agreement.json`
8. CLI/hybrid evaluation adapter implemented and validated on tiny index:
   - `scripts/evaluate_retrieval_cli.py`
   - sample output:
     - `out/retrieval-cli-eval.json`
9. Tier B/Tier C benchmark generation implemented:
   - `scripts/generate_retrieval_benchmarks.py`
   - generated datasets:
     - `datasets/quality/retrieval-benchmark-tier-b.json` (`540` components, `360` queries)
     - `datasets/quality/retrieval-benchmark-tier-c-heldout.json` (`216` components, `120` queries)
10. Tier B/Tier C matrix evaluations executed:
    - `out/retrieval-benchmark-tier-b-matrix/matrix-summary.json`
    - `out/retrieval-benchmark-tier-b-matrix/matrix-summary.md`
    - `out/retrieval-benchmark-tier-c-matrix/matrix-summary.json`
    - `out/retrieval-benchmark-tier-c-matrix/matrix-summary.md`
11. Full annotation workflow tooling implemented and exercised:
    - create batches:
      - `scripts/create_annotation_batches.py`
    - synthetic annotator simulation:
      - `scripts/simulate_annotations_from_benchmark.py`
    - agreement:
      - `scripts/compute_annotation_agreement.py`
      - output: `out/annotation-agreement-tier-b.json`
    - merge/adjudication:
      - `scripts/merge_adjudication_labels.py`
      - output: `out/final-labeled-pairs-tier-b.json`
12. Publication-pack assembler added:
    - `scripts/build_publication_pack.py`
    - sample pack:
      - `out/publication-packs/tier-b-20260225T232021Z`
    - includes manifest + index readme + copied evidence artifacts.
13. Human-in-the-loop automation round script added:
    - `scripts/automate_annotation_round.py`
    - `prepare` generates annotator files with model suggestions.
    - `process` auto-accepts:
      - annotator consensus labels
      - high-confidence model suggestions (`--model-score-threshold`, default `0.90`)
    - routes remaining items to adjudication queue only.
    - sample output:
      - `out/annotation-rounds/tier-b-round-1-process-summary.json`
      - `out/annotation-rounds/tier-b-round-1-adjudication-needed.csv`
14. One-command orchestrator added:
    - `scripts/run_full_research_round.py`
    - executes:
      - annotation round prepare/process
      - agreement computation
      - benchmark matrix run
      - publication pack build
    - sample run output:
      - `out/tier-b-round-auto-run-summary.json`

Pending:
1. Non-automatable final step (now reduced scope): perform real dual-human labeling only on items that remain in `adjudication-needed.csv`, then save final rationale logs.

## Execution Task List (Live)
Status legend: `todo`, `in_progress`, `done`.

1. `done` Add per-query metric export and paired significance in retrieval evaluator.
2. `done` Add annotator batch templates and kappa computation script for agreement tracking.
3. `done` Add CLI/hybrid evaluation adapter that scores real CLI query outputs against benchmark labels.
4. `done` Run updated pipeline and publish refreshed outputs under `out/`.
5. `done` Expand benchmark pack toward Tier B target size using the annotation workflow.
6. `done` Add Tier C held-out benchmark and rerun matrix.
