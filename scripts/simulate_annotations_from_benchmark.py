#!/usr/bin/env python3
"""Create synthetic annotator A/B labels from benchmark positives/hard-negatives."""

from __future__ import annotations

import argparse
import csv
import json
import random
from pathlib import Path
from typing import Dict, Tuple


def load_truth(dataset: dict) -> Dict[Tuple[str, str], str]:
    truth: Dict[Tuple[str, str], str] = {}
    for q in dataset["queries"]:
        qid = q["query_id"]
        for cid in q.get("positives", []):
            truth[(qid, cid)] = "duplicate"
        for cid in q.get("hard_negatives", []):
            truth[(qid, cid)] = "different"
    return truth


def perturb(label: str, rng: random.Random, error_rate: float) -> str:
    if rng.random() > error_rate:
        return label
    if label == "duplicate":
        return "near_duplicate" if rng.random() < 0.5 else "different"
    if label == "near_duplicate":
        return "duplicate" if rng.random() < 0.5 else "different"
    return "near_duplicate" if rng.random() < 0.5 else "duplicate"


def main() -> int:
    p = argparse.ArgumentParser(description="Simulate annotator A/B labels from benchmark ground truth.")
    p.add_argument("--dataset", type=Path, required=True)
    p.add_argument("--annotator-a", type=Path, required=True)
    p.add_argument("--annotator-b", type=Path, required=True)
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--error-rate-a", type=float, default=0.07)
    p.add_argument("--error-rate-b", type=float, default=0.10)
    args = p.parse_args()

    data = json.loads(args.dataset.read_text())
    truth = load_truth(data)
    rng_a = random.Random(args.seed)
    rng_b = random.Random(args.seed + 1)

    def write(path: Path, rng: random.Random, error_rate: float):
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=["query_id", "candidate_id", "label", "confidence", "notes"])
            writer.writeheader()
            for (q, c), label in sorted(truth.items()):
                noisy = perturb(label, rng, error_rate)
                writer.writerow(
                    {
                        "query_id": q,
                        "candidate_id": c,
                        "label": noisy,
                        "confidence": "medium",
                        "notes": "synthetic annotation",
                    }
                )

    write(args.annotator_a, rng_a, args.error_rate_a)
    write(args.annotator_b, rng_b, args.error_rate_b)
    print(
        json.dumps(
            {
                "dataset": str(args.dataset),
                "pairs": len(truth),
                "annotator_a": str(args.annotator_a),
                "annotator_b": str(args.annotator_b),
            },
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
