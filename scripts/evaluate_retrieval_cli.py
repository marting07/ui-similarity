#!/usr/bin/env python3
"""Evaluate retrieval quality using real CLI query outputs over a built snapshot."""

from __future__ import annotations

import argparse
import json
import math
import shlex
import subprocess
import tempfile
from pathlib import Path
from typing import List, Sequence, Set


def precision_at_k(ranked_ids: Sequence[str], positives: Set[str], k: int) -> float:
    if k <= 0:
        return 0.0
    top = ranked_ids[:k]
    return sum(1 for rid in top if rid in positives) / float(k)


def recall_at_k(ranked_ids: Sequence[str], positives: Set[str], k: int) -> float:
    if not positives:
        return 0.0
    top = ranked_ids[:k]
    return sum(1 for rid in top if rid in positives) / float(len(positives))


def reciprocal_rank(ranked_ids: Sequence[str], positives: Set[str]) -> float:
    for i, rid in enumerate(ranked_ids, start=1):
        if rid in positives:
            return 1.0 / float(i)
    return 0.0


def ndcg_at_k(ranked_ids: Sequence[str], positives: Set[str], k: int) -> float:
    top = ranked_ids[:k]
    dcg = 0.0
    for i, rid in enumerate(top, start=1):
        if rid in positives:
            dcg += 1.0 / math.log2(i + 1)
    ideal_hits = min(k, len(positives))
    if ideal_hits == 0:
        return 0.0
    idcg = sum(1.0 / math.log2(i + 1) for i in range(1, ideal_hits + 1))
    return dcg / idcg if idcg else 0.0


def run_cli_query(cli_cmd: List[str], index_file: Path, query_id: str, top_k: int, top_n: int, cwd: Path) -> List[str]:
    with tempfile.NamedTemporaryFile(prefix="cli-query-", suffix=".json", delete=False) as tf:
        out_json = Path(tf.name)
    try:
        args_str = (
            f"query --index-file {index_file} --component-id {query_id} "
            f"--top-k {top_k} --top-n {top_n} --json-out {out_json}"
        )
        cmd = cli_cmd + [f"-Pargs={args_str}"]
        subprocess.run(cmd, cwd=cwd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        payload = json.loads(out_json.read_text())
        return [m["component_id"] for m in payload.get("matches", []) if "component_id" in m]
    finally:
        out_json.unlink(missing_ok=True)


def avg(xs: List[float]) -> float:
    return sum(xs) / len(xs) if xs else 0.0


def main() -> int:
    p = argparse.ArgumentParser(description="Evaluate benchmark queries using real CLI outputs.")
    p.add_argument("--dataset", type=Path, default=Path("datasets/quality/retrieval-benchmark-v1.json"))
    p.add_argument("--index-file", type=Path, required=True)
    p.add_argument("--cli-command", type=str, default="./gradlew -q runCli")
    p.add_argument("--top-k", type=int, default=10)
    p.add_argument("--top-n", type=int, default=20)
    p.add_argument("--scope", choices=["all", "same", "cross"], default="all")
    p.add_argument("--output", type=Path, default=Path("out/retrieval-cli-eval.json"))
    args = p.parse_args()

    data = json.loads(args.dataset.read_text())
    framework_by_id = {c["id"]: c["framework"] for c in data["components"]}
    cli_cmd = shlex.split(args.cli_command)

    p1_vals: List[float] = []
    p5_vals: List[float] = []
    r5_vals: List[float] = []
    mrr_vals: List[float] = []
    nd10_vals: List[float] = []
    per_query = []

    cwd = Path(__file__).resolve().parents[1]
    for q in data["queries"]:
        query_id = q["query_id"]
        positives = set(q["positives"])
        if args.scope == "same":
            positives = {pid for pid in positives if framework_by_id.get(pid) == framework_by_id.get(query_id)}
        elif args.scope == "cross":
            positives = {pid for pid in positives if framework_by_id.get(pid) != framework_by_id.get(query_id)}
        if not positives:
            continue
        ranked = run_cli_query(cli_cmd, args.index_file, query_id, args.top_k, args.top_n, cwd)
        p1 = precision_at_k(ranked, positives, 1)
        p5 = precision_at_k(ranked, positives, 5)
        r5 = recall_at_k(ranked, positives, 5)
        rr = reciprocal_rank(ranked, positives)
        nd10 = ndcg_at_k(ranked, positives, 10)
        p1_vals.append(p1)
        p5_vals.append(p5)
        r5_vals.append(r5)
        mrr_vals.append(rr)
        nd10_vals.append(nd10)
        per_query.append(
            {
                "query_id": query_id,
                "p_at_1": p1,
                "p_at_5": p5,
                "recall_at_5": r5,
                "rr": rr,
                "ndcg_at_10": nd10,
            }
        )

    payload = {
        "dataset": str(args.dataset),
        "index_file": str(args.index_file),
        "scope": args.scope,
        "num_queries": len(per_query),
        "p_at_1": avg(p1_vals),
        "p_at_5": avg(p5_vals),
        "recall_at_5": avg(r5_vals),
        "mrr": avg(mrr_vals),
        "ndcg_at_10": avg(nd10_vals),
        "per_query": per_query,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2) + "\n")
    print(json.dumps(payload, indent=2))
    print(f"\nWrote CLI evaluation report to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
