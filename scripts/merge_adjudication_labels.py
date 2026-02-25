#!/usr/bin/env python3
"""Merge annotation labels (A/B + adjudication) into final benchmark labels."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path
from typing import Dict, Tuple


def read_csv(path: Path) -> Dict[Tuple[str, str], dict]:
    out: Dict[Tuple[str, str], dict] = {}
    with path.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            key = (row["query_id"].strip(), row["candidate_id"].strip())
            out[key] = row
    return out


def choose_label(a: str, b: str, adjudicated: str) -> str:
    if adjudicated:
        return adjudicated
    if a and b and a == b:
        return a
    if a:
        return a
    if b:
        return b
    return ""


def main() -> int:
    p = argparse.ArgumentParser(description="Merge A/B/adjudication labels into final labeled pairs.")
    p.add_argument("--annotator-a", type=Path, required=True)
    p.add_argument("--annotator-b", type=Path, required=True)
    p.add_argument("--adjudication", type=Path, required=True)
    p.add_argument("--output", type=Path, default=Path("out/final-labeled-pairs.json"))
    args = p.parse_args()

    a = read_csv(args.annotator_a)
    b = read_csv(args.annotator_b)
    j = read_csv(args.adjudication)
    keys = sorted(set(a.keys()) | set(b.keys()) | set(j.keys()))

    pairs = []
    for key in keys:
        ra = a.get(key, {})
        rb = b.get(key, {})
        rj = j.get(key, {})
        label = choose_label(ra.get("label", "").strip(), rb.get("label", "").strip(), rj.get("label", "").strip())
        pairs.append(
            {
                "query_id": key[0],
                "candidate_id": key[1],
                "label": label,
                "label_a": ra.get("label", "").strip(),
                "label_b": rb.get("label", "").strip(),
                "label_adjudication": rj.get("label", "").strip(),
            }
        )

    payload = {
        "annotator_a": str(args.annotator_a),
        "annotator_b": str(args.annotator_b),
        "adjudication": str(args.adjudication),
        "pairs": pairs,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2) + "\n")
    print(json.dumps({"output": str(args.output), "pairs": len(pairs)}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
