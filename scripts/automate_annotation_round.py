#!/usr/bin/env python3
"""Automate human-in-the-loop annotation workflow.

Two stages:
1) prepare: generate annotator A/B files with model suggestions
2) process: compute agreement, auto-accept consensus, create adjudication queue
"""

from __future__ import annotations

import argparse
import csv
import json
import math
from pathlib import Path
from typing import Dict, List, Sequence, Set, Tuple


def cosine_hist_similarity(a: Dict[str, int], b: Dict[str, int]) -> float:
    if not a and not b:
        return 1.0
    keys = set(a.keys()) | set(b.keys())
    dot = sum(float(a.get(k, 0) * b.get(k, 0)) for k in keys)
    na = math.sqrt(sum(float(v * v) for v in a.values()))
    nb = math.sqrt(sum(float(v * v) for v in b.values()))
    if na == 0.0 or nb == 0.0:
        return 0.0
    return max(0.0, min(1.0, dot / (na * nb)))


def jaccard_similarity(a: Set[str], b: Set[str]) -> float:
    if not a and not b:
        return 1.0
    union = a | b
    if not union:
        return 1.0
    return len(a & b) / len(union)


def proposed_score(q: dict, c: dict) -> float:
    dom = cosine_hist_similarity(q.get("dom_tags", {}), c.get("dom_tags", {}))
    css = cosine_hist_similarity(q.get("css_tokens", {}), c.get("css_tokens", {}))
    behavior = 0.7 * jaccard_similarity(set(q.get("behavior_events", [])), set(c.get("behavior_events", []))) + 0.3 * jaccard_similarity(
        set(q.get("behavior_states", [])), set(c.get("behavior_states", []))
    )
    token = jaccard_similarity(set(q.get("tokens", [])), set(c.get("tokens", [])))
    return 0.40 * dom + 0.25 * css + 0.25 * behavior + 0.10 * token


def suggestion_from_score(score: float) -> Tuple[str, str]:
    if score >= 0.82:
        return "duplicate", "high"
    if score >= 0.62:
        return "near_duplicate", "medium"
    return "different", "medium"


def load_csv(path: Path) -> Dict[Tuple[str, str], dict]:
    out: Dict[Tuple[str, str], dict] = {}
    with path.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            q = (row.get("query_id") or "").strip()
            c = (row.get("candidate_id") or "").strip()
            if q and c:
                out[(q, c)] = row
    return out


def write_csv(path: Path, rows: List[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=[
                "query_id",
                "candidate_id",
                "suggested_label",
                "suggested_confidence",
                "suggested_score",
                "label",
                "confidence",
                "notes",
            ],
        )
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def cmd_prepare(args) -> int:
    data = json.loads(args.dataset.read_text())
    by_id = {c["id"]: c for c in data["components"]}
    rows: List[dict] = []
    for q in data["queries"]:
        qid = q["query_id"]
        qcomp = by_id.get(qid)
        if not qcomp:
            continue
        candidates = list(dict.fromkeys(list(q.get("positives", [])) + list(q.get("hard_negatives", []))))
        for cid in candidates[: args.max_pairs_per_query]:
            ccomp = by_id.get(cid)
            if not ccomp or cid == qid:
                continue
            score = proposed_score(qcomp, ccomp)
            label, conf = suggestion_from_score(score)
            rows.append(
                {
                    "query_id": qid,
                    "candidate_id": cid,
                    "suggested_label": label,
                    "suggested_confidence": conf,
                    "suggested_score": f"{score:.6f}",
                    "label": "",
                    "confidence": "",
                    "notes": "",
                }
            )
    round_name = args.round_name
    out_dir = args.out_dir
    a_path = out_dir / f"{round_name}-annotator-a.csv"
    b_path = out_dir / f"{round_name}-annotator-b.csv"
    adjud_path = out_dir / f"{round_name}-adjudication.csv"
    write_csv(a_path, rows)
    write_csv(b_path, rows)
    write_csv(adjud_path, rows)
    print(
        json.dumps(
            {
                "round": round_name,
                "pairs": len(rows),
                "annotator_a": str(a_path),
                "annotator_b": str(b_path),
                "adjudication": str(adjud_path),
            },
            indent=2,
        )
    )
    return 0


def cmd_process(args) -> int:
    a = load_csv(args.annotator_a)
    b = load_csv(args.annotator_b)
    common = sorted(set(a.keys()) & set(b.keys()))
    auto_final = []
    adjudication_needed = []

    agree = 0
    for key in common:
        ra = a[key]
        rb = b[key]
        la = (ra.get("label") or "").strip()
        lb = (rb.get("label") or "").strip()
        ca = (ra.get("confidence") or "").strip()
        cb = (rb.get("confidence") or "").strip()
        suggested = (ra.get("suggested_label") or rb.get("suggested_label") or "").strip()
        suggested_conf = (ra.get("suggested_confidence") or rb.get("suggested_confidence") or "").strip().lower()
        score = (ra.get("suggested_score") or rb.get("suggested_score") or "").strip()
        try:
            score_value = float(score) if score else 0.0
        except ValueError:
            score_value = 0.0
        if la and lb and la == lb:
            agree += 1
            auto_final.append(
                {
                    "query_id": key[0],
                    "candidate_id": key[1],
                    "final_label": la,
                    "source": "annotator_consensus",
                    "label_a": la,
                    "label_b": lb,
                    "confidence_a": ca,
                    "confidence_b": cb,
                    "suggested_label": suggested,
                    "suggested_score": score,
                }
            )
        elif args.auto_accept_model_high and (not la and not lb) and suggested and suggested_conf == "high" and score_value >= args.model_score_threshold:
            auto_final.append(
                {
                    "query_id": key[0],
                    "candidate_id": key[1],
                    "final_label": suggested,
                    "source": "model_high_confidence",
                    "label_a": la,
                    "label_b": lb,
                    "confidence_a": ca,
                    "confidence_b": cb,
                    "suggested_label": suggested,
                    "suggested_score": score,
                }
            )
        else:
            adjudication_needed.append(
                {
                    "query_id": key[0],
                    "candidate_id": key[1],
                    "suggested_label": suggested,
                    "suggested_score": score,
                    "label_a": la,
                    "confidence_a": ca,
                    "label_b": lb,
                    "confidence_b": cb,
                    "final_label": "",
                    "notes": "",
                }
            )

    out_dir = args.out_dir
    out_dir.mkdir(parents=True, exist_ok=True)
    auto_json = out_dir / f"{args.round_name}-auto-final.json"
    auto_json.write_text(json.dumps({"pairs": auto_final}, indent=2) + "\n")

    adjud_csv = out_dir / f"{args.round_name}-adjudication-needed.csv"
    with adjud_csv.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=[
                "query_id",
                "candidate_id",
                "suggested_label",
                "suggested_score",
                "label_a",
                "confidence_a",
                "label_b",
                "confidence_b",
                "final_label",
                "notes",
            ],
        )
        writer.writeheader()
        for row in adjudication_needed:
            writer.writerow(row)

    summary = {
        "round": args.round_name,
        "pairs_common": len(common),
        "consensus_auto_accepted": len(auto_final),
        "adjudication_required": len(adjudication_needed),
        "observed_consensus_rate": (agree / len(common)) if common else 0.0,
        "auto_final_json": str(auto_json),
        "adjudication_queue_csv": str(adjud_csv),
    }
    (out_dir / f"{args.round_name}-process-summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Automate annotation rounds.")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_prepare = sub.add_parser("prepare", help="Create annotator files with suggested labels")
    p_prepare.add_argument("--dataset", type=Path, required=True)
    p_prepare.add_argument("--out-dir", type=Path, default=Path("datasets/quality/annotation"))
    p_prepare.add_argument("--round-name", type=str, default="tier-b-round-1")
    p_prepare.add_argument("--max-pairs-per-query", type=int, default=6)
    p_prepare.set_defaults(func=cmd_prepare)

    p_process = sub.add_parser("process", help="Auto-accept consensus and generate adjudication queue")
    p_process.add_argument("--annotator-a", type=Path, required=True)
    p_process.add_argument("--annotator-b", type=Path, required=True)
    p_process.add_argument("--out-dir", type=Path, default=Path("out/annotation-rounds"))
    p_process.add_argument("--round-name", type=str, default="tier-b-round-1")
    p_process.add_argument("--auto-accept-model-high", action="store_true", default=True)
    p_process.add_argument("--model-score-threshold", type=float, default=0.90)
    p_process.set_defaults(func=cmd_process)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
