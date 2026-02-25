#!/usr/bin/env python3
"""Create annotator A/B batches and adjudication template from a retrieval dataset."""

from __future__ import annotations

import argparse
import csv
import json
import random
from pathlib import Path
from typing import List, Tuple


def enumerate_pairs(dataset: dict, max_pairs_per_query: int) -> List[Tuple[str, str]]:
    pairs: List[Tuple[str, str]] = []
    for q in dataset["queries"]:
        query_id = q["query_id"]
        candidates = list(q.get("positives", [])) + list(q.get("hard_negatives", []))
        for candidate_id in candidates[:max_pairs_per_query]:
            if candidate_id != query_id:
                pairs.append((query_id, candidate_id))
    return pairs


def write_csv(path: Path, rows: List[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=["query_id", "candidate_id", "label", "confidence", "notes"],
        )
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def main() -> int:
    p = argparse.ArgumentParser(description="Create dual-annotator batches and adjudication template.")
    p.add_argument("--dataset", type=Path, required=True)
    p.add_argument("--out-dir", type=Path, default=Path("datasets/quality/annotation"))
    p.add_argument("--max-pairs-per-query", type=int, default=6)
    p.add_argument("--seed", type=int, default=42)
    args = p.parse_args()

    data = json.loads(args.dataset.read_text())
    pairs = enumerate_pairs(data, max_pairs_per_query=args.max_pairs_per_query)
    rng = random.Random(args.seed)
    rng.shuffle(pairs)

    rows = [{"query_id": q, "candidate_id": c, "label": "", "confidence": "", "notes": ""} for (q, c) in pairs]
    annotator_a = args.out_dir / f"{args.dataset.stem}-annotator-a.csv"
    annotator_b = args.out_dir / f"{args.dataset.stem}-annotator-b.csv"
    adjudication = args.out_dir / f"{args.dataset.stem}-adjudication.csv"
    write_csv(annotator_a, rows)
    write_csv(annotator_b, rows)
    write_csv(adjudication, rows)

    summary = {
        "dataset": str(args.dataset),
        "pairs": len(rows),
        "annotator_a": str(annotator_a),
        "annotator_b": str(annotator_b),
        "adjudication": str(adjudication),
    }
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
