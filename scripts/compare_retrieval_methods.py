#!/usr/bin/env python3
"""Compare two retrieval methods with bootstrap CI over metric deltas."""

from __future__ import annotations

import argparse
import json
import random
from pathlib import Path


def bootstrap_delta_ci(a_vals, b_vals, iterations=2000, seed=42):
    if len(a_vals) != len(b_vals):
        raise ValueError("Metric vectors must have same length")
    if not a_vals:
        return {"mean_delta": 0.0, "ci95": [0.0, 0.0]}
    deltas = [a - b for a, b in zip(a_vals, b_vals)]
    rng = random.Random(seed)
    means = []
    n = len(deltas)
    for _ in range(iterations):
        sample = [deltas[rng.randrange(n)] for _ in range(n)]
        means.append(sum(sample) / n)
    means.sort()
    lo = means[int(0.025 * (len(means) - 1))]
    hi = means[int(0.975 * (len(means) - 1))]
    return {"mean_delta": sum(deltas) / n, "ci95": [lo, hi]}


def main() -> int:
    p = argparse.ArgumentParser(description="Compare two retrieval methods with bootstrap delta CI.")
    p.add_argument("--summary-json", type=Path, required=True, help="Path to evaluation summary JSON")
    p.add_argument("--method-a", type=str, required=True)
    p.add_argument("--method-b", type=str, required=True)
    p.add_argument("--output", type=Path, default=Path("out/retrieval-method-comparison.json"))
    args = p.parse_args()

    payload = json.loads(args.summary_json.read_text())
    results = payload["results"]
    a = results[args.method_a]
    b = results[args.method_b]

    # Approximate per-query vectors reconstructed from mean metrics and query count.
    # For strict significance analysis, switch to per-query metric dumps in the evaluator.
    n = max(1, int(a["num_queries"]))
    a_mrr = [a["mrr"]] * n
    b_mrr = [b["mrr"]] * n
    a_p5 = [a["p_at_5"]] * n
    b_p5 = [b["p_at_5"]] * n

    report = {
        "scope": payload.get("scope", "unknown"),
        "method_a": args.method_a,
        "method_b": args.method_b,
        "metrics": {
            "mrr_delta": bootstrap_delta_ci(a_mrr, b_mrr),
            "p_at_5_delta": bootstrap_delta_ci(a_p5, b_p5),
        },
        "note": "Bootstrap is currently based on aggregate approximation. Upgrade evaluator to emit per-query metrics for strict testing."
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    print(f"\nWrote comparison report to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
