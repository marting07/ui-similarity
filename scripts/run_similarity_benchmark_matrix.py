#!/usr/bin/env python3
"""Run retrieval benchmark matrix (all/same/cross scopes) and aggregate outputs."""

from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path


def run(cmd):
    subprocess.run(cmd, check=True)


def main() -> int:
    p = argparse.ArgumentParser(description="Run retrieval benchmark matrix and build one aggregated report.")
    p.add_argument("--dataset", type=Path, default=Path("datasets/quality/retrieval-benchmark-v1.json"))
    p.add_argument("--out-dir", type=Path, default=Path("out/retrieval-benchmark-matrix"))
    args = p.parse_args()

    args.out_dir.mkdir(parents=True, exist_ok=True)
    scopes = ["all", "same", "cross"]
    matrix = {}

    for scope in scopes:
        out_json = args.out_dir / f"summary-{scope}.json"
        out_md = args.out_dir / f"summary-{scope}.md"
        run(
            [
                "python3",
                "scripts/evaluate_retrieval_benchmark.py",
                "--dataset",
                str(args.dataset),
                "--scope",
                scope,
                "--output-json",
                str(out_json),
                "--output-md",
                str(out_md),
            ]
        )
        matrix[scope] = json.loads(out_json.read_text())

    aggregate = {"dataset": str(args.dataset), "scopes": matrix}
    aggregate_json = args.out_dir / "matrix-summary.json"
    aggregate_json.write_text(json.dumps(aggregate, indent=2) + "\n")

    lines = ["# Retrieval Benchmark Matrix Summary", ""]
    for scope in scopes:
        lines.append(f"## Scope: {scope}")
        lines.append("| method | P@1 | P@5 | MRR | nDCG@10 |")
        lines.append("|---|---:|---:|---:|---:|")
        for method, row in sorted(matrix[scope]["results"].items()):
            lines.append(
                f"| {method} | {row['p_at_1']:.4f} | {row['p_at_5']:.4f} | {row['mrr']:.4f} | {row['ndcg_at_10']:.4f} |"
            )
        lines.append("")
    aggregate_md = args.out_dir / "matrix-summary.md"
    aggregate_md.write_text("\n".join(lines))

    print(f"Wrote matrix JSON: {aggregate_json}")
    print(f"Wrote matrix markdown: {aggregate_md}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
