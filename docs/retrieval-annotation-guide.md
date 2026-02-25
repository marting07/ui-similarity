# Retrieval Annotation Guide (v1)

## Goal
Label query-candidate component pairs as `duplicate`, `near_duplicate`, or `different` for similarity benchmark evaluation.

## Definitions
1. `duplicate`
   - Same user-facing intent and very similar structure/interaction.
   - Differences limited to naming, minor styling, framework syntax.
2. `near_duplicate`
   - Same core intent, moderate implementation differences (layout/event nuances).
3. `different`
   - Different intent or substantially different structural/behavioral pattern.

## Decision Rules
1. Evaluate in this order:
   - intent
   - interaction behavior
   - structural UI pattern
2. Ignore:
   - variable/function names
   - import style
   - trivial formatting
3. Do not use repo owner/name/path as a labeling signal.

## Annotation Form Fields
1. `query_id`
2. `candidate_id`
3. `label` (`duplicate|near_duplicate|different`)
4. `confidence` (`low|medium|high`)
5. `notes`

## Dual-Annotator Protocol
1. Every pair labeled independently by Annotator A and B.
2. Compute agreement (Cohen’s kappa) after each batch.
3. Disagreements resolved by adjudicator.
4. Keep adjudication log with final rationale.

## Quality Gates
1. Target kappa: `>= 0.70`.
2. If kappa < 0.70:
   - recalibrate using 20-example review session.
   - re-run on a fresh batch before continuing.
