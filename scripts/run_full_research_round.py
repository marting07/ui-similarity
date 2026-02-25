#!/usr/bin/env python3
"""Run a full research round with one command.

Chain:
1) optional benchmark generation
2) annotation round prepare
3) optional annotation simulation
4) annotation round process (auto-accept + adjudication queue)
5) retrieval matrix evaluation
6) publication pack assembly
"""

from __future__ import annotations

import argparse
import json
import shlex
import subprocess
from pathlib import Path
from typing import List


def run(cmd: List[str], cwd: Path) -> None:
    print("$ " + " ".join(shlex.quote(x) for x in cmd))
    subprocess.run(cmd, cwd=cwd, check=True)


def main() -> int:
    p = argparse.ArgumentParser(description="Run full research round workflow.")
    p.add_argument("--tier", choices=["tier-b", "tier-c"], default="tier-b")
    p.add_argument("--round-name", default="tier-b-round-auto")
    p.add_argument("--with-generate-benchmarks", action="store_true")
    p.add_argument("--simulate-annotations", action="store_true")
    p.add_argument("--model-score-threshold", type=float, default=0.90)
    p.add_argument("--copies-per-framework", type=int, default=36)
    p.add_argument("--tier-b-query-target", type=int, default=360)
    p.add_argument("--tier-c-query-target", type=int, default=120)
    p.add_argument("--max-pairs-per-query", type=int, default=6)
    p.add_argument("--workspace", type=Path, default=Path("."))
    args = p.parse_args()

    root = args.workspace.resolve()
    dataset_tier_b = root / "datasets/quality/retrieval-benchmark-tier-b.json"
    dataset_tier_c = root / "datasets/quality/retrieval-benchmark-tier-c-heldout.json"
    dataset = dataset_tier_b if args.tier == "tier-b" else dataset_tier_c
    annotation_dir = root / "datasets/quality/annotation"
    out_matrix = root / f"out/retrieval-benchmark-{args.tier}-matrix"
    out_rounds = root / "out/annotation-rounds"
    out_packs = root / "out/publication-packs"

    if args.with_generate_benchmarks:
        run(
            [
                "python3",
                "scripts/generate_retrieval_benchmarks.py",
                "--seed-dataset",
                "datasets/quality/retrieval-benchmark-v1.json",
                "--tier-b-out",
                str(dataset_tier_b),
                "--tier-c-out",
                str(dataset_tier_c),
                "--tier-b-query-target",
                str(args.tier_b_query_target),
                "--tier-c-query-target",
                str(args.tier_c_query_target),
                "--copies-per-framework",
                str(args.copies_per_framework),
            ],
            cwd=root,
        )

    run(
        [
            "python3",
            "scripts/automate_annotation_round.py",
            "prepare",
            "--dataset",
            str(dataset),
            "--out-dir",
            str(annotation_dir),
            "--round-name",
            args.round_name,
            "--max-pairs-per-query",
            str(args.max_pairs_per_query),
        ],
        cwd=root,
    )

    a_csv = annotation_dir / f"{args.round_name}-annotator-a.csv"
    b_csv = annotation_dir / f"{args.round_name}-annotator-b.csv"

    if args.simulate_annotations:
        run(
            [
                "python3",
                "scripts/simulate_annotations_from_benchmark.py",
                "--dataset",
                str(dataset),
                "--annotator-a",
                str(a_csv),
                "--annotator-b",
                str(b_csv),
            ],
            cwd=root,
        )

    run(
        [
            "python3",
            "scripts/automate_annotation_round.py",
            "process",
            "--annotator-a",
            str(a_csv),
            "--annotator-b",
            str(b_csv),
            "--out-dir",
            str(out_rounds),
            "--round-name",
            args.round_name,
            "--model-score-threshold",
            str(args.model_score_threshold),
        ],
        cwd=root,
    )

    run(
        [
            "python3",
            "scripts/compute_annotation_agreement.py",
            "--annotator-a",
            str(a_csv),
            "--annotator-b",
            str(b_csv),
            "--output",
            str(root / f"out/annotation-agreement-{args.round_name}.json"),
        ],
        cwd=root,
    )

    run(
        [
            "python3",
            "scripts/run_similarity_benchmark_matrix.py",
            "--dataset",
            str(dataset),
            "--out-dir",
            str(out_matrix),
        ],
        cwd=root,
    )

    run(
        [
            "python3",
            "scripts/build_publication_pack.py",
            "--name",
            args.round_name,
            "--output-root",
            str(out_packs),
            "--agreement",
            str(root / f"out/annotation-agreement-{args.round_name}.json"),
            "--final-labels",
            str(out_rounds / f"{args.round_name}-auto-final.json"),
            "--adjudication-csv",
            str(out_rounds / f"{args.round_name}-adjudication-needed.csv"),
            "--matrix-json",
            str(out_matrix / "matrix-summary.json"),
            "--matrix-md",
            str(out_matrix / "matrix-summary.md"),
            "--protocol",
            str(root / "docs/similarity-evaluation-protocol.md"),
            "--annotation-guide",
            str(root / "docs/retrieval-annotation-guide.md"),
        ],
        cwd=root,
    )

    summary = {
        "tier": args.tier,
        "round_name": args.round_name,
        "dataset": str(dataset),
        "agreement_report": str(root / f"out/annotation-agreement-{args.round_name}.json"),
        "auto_final_labels": str(out_rounds / f"{args.round_name}-auto-final.json"),
        "adjudication_queue": str(out_rounds / f"{args.round_name}-adjudication-needed.csv"),
        "matrix_dir": str(out_matrix),
        "publication_packs_root": str(out_packs),
    }
    out_summary = root / f"out/{args.round_name}-run-summary.json"
    out_summary.parent.mkdir(parents=True, exist_ok=True)
    out_summary.write_text(json.dumps(summary, indent=2) + "\n")
    print("\nRound summary:")
    print(json.dumps(summary, indent=2))
    print(f"\nWrote summary to {out_summary}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
