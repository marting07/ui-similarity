#!/usr/bin/env python3
"""Compute agreement metrics (including Cohen's kappa) between two annotator files."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path
from typing import Dict, List, Tuple


def read_labels(path: Path) -> Dict[Tuple[str, str], str]:
    out: Dict[Tuple[str, str], str] = {}
    with path.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        required = {"query_id", "candidate_id", "label"}
        if not required.issubset(set(reader.fieldnames or [])):
            raise ValueError(f"{path} missing required columns: {sorted(required)}")
        for row in reader:
            qid = (row.get("query_id") or "").strip()
            cid = (row.get("candidate_id") or "").strip()
            label = (row.get("label") or "").strip()
            key = (qid, cid)
            if key[0] and key[1] and label:
                out[key] = label
    return out


def cohen_kappa(labels_a: List[str], labels_b: List[str]) -> float:
    assert len(labels_a) == len(labels_b)
    n = len(labels_a)
    if n == 0:
        return 0.0
    categories = sorted(set(labels_a) | set(labels_b))
    agreement = sum(1 for a, b in zip(labels_a, labels_b) if a == b) / n
    p_a = {c: labels_a.count(c) / n for c in categories}
    p_b = {c: labels_b.count(c) / n for c in categories}
    expected = sum(p_a[c] * p_b[c] for c in categories)
    if expected >= 1.0:
        return 1.0
    return (agreement - expected) / (1.0 - expected)


def main() -> int:
    p = argparse.ArgumentParser(description="Compute annotation agreement between two annotator CSV files.")
    p.add_argument("--annotator-a", type=Path, required=True)
    p.add_argument("--annotator-b", type=Path, required=True)
    p.add_argument("--output", type=Path, default=Path("out/annotation-agreement.json"))
    args = p.parse_args()

    a = read_labels(args.annotator_a)
    b = read_labels(args.annotator_b)
    common_keys = sorted(set(a.keys()) & set(b.keys()))
    only_a = sorted(set(a.keys()) - set(b.keys()))
    only_b = sorted(set(b.keys()) - set(a.keys()))
    labels_a = [a[k] for k in common_keys]
    labels_b = [b[k] for k in common_keys]
    observed = sum(1 for x, y in zip(labels_a, labels_b) if x == y) / len(common_keys) if common_keys else 0.0
    kappa = cohen_kappa(labels_a, labels_b) if common_keys else 0.0

    payload = {
        "annotator_a": str(args.annotator_a),
        "annotator_b": str(args.annotator_b),
        "pairs_common": len(common_keys),
        "pairs_only_a": len(only_a),
        "pairs_only_b": len(only_b),
        "observed_agreement": observed,
        "cohen_kappa": kappa,
        "quality_gate_kappa_0_70_pass": kappa >= 0.70,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(payload, indent=2))
    print(f"\nWrote agreement report to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
