#!/usr/bin/env python3
"""Evaluate retrieval quality for labeled UI component similarity benchmark."""

from __future__ import annotations

import argparse
import json
import math
import random
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Sequence, Set, Tuple


@dataclass(frozen=True)
class Component:
    id: str
    framework: str
    tokens: Set[str]
    dom_tags: Dict[str, int]
    css_tokens: Dict[str, int]
    behavior_events: Set[str]
    behavior_states: Set[str]


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


def method_score(method: str, q: Component, c: Component) -> float:
    dom = cosine_hist_similarity(q.dom_tags, c.dom_tags)
    css = cosine_hist_similarity(q.css_tokens, c.css_tokens)
    behavior = 0.7 * jaccard_similarity(q.behavior_events, c.behavior_events) + 0.3 * jaccard_similarity(
        q.behavior_states, c.behavior_states
    )
    token = jaccard_similarity(q.tokens, c.tokens)

    if method == "proposed_full":
        return 0.40 * dom + 0.25 * css + 0.25 * behavior + 0.10 * token
    if method == "dom_only":
        return dom
    if method == "css_only":
        return css
    if method == "behavior_only":
        return behavior
    if method == "dom_css":
        return 0.6 * dom + 0.4 * css
    if method == "dom_behavior":
        return 0.6 * dom + 0.4 * behavior
    if method == "css_behavior":
        return 0.5 * css + 0.5 * behavior
    if method == "token_baseline":
        return token
    if method == "random_baseline":
        # deterministic pseudo-random score by ids
        seed = hash((q.id, c.id)) & 0xFFFFFFFF
        rng = random.Random(seed)
        return rng.random()
    raise ValueError(f"Unsupported method: {method}")


def precision_at_k(ranked_ids: Sequence[str], positives: Set[str], k: int) -> float:
    if k <= 0:
        return 0.0
    top = ranked_ids[:k]
    return sum(1 for rid in top if rid in positives) / float(k)


def recall_at_k(ranked_ids: Sequence[str], positives: Set[str], k: int) -> float:
    if not positives:
        return 0.0
    top = ranked_ids[:k]
    found = sum(1 for rid in top if rid in positives)
    return found / float(len(positives))


def reciprocal_rank(ranked_ids: Sequence[str], positives: Set[str]) -> float:
    for i, rid in enumerate(ranked_ids, start=1):
        if rid in positives:
            return 1.0 / float(i)
    return 0.0


def ndcg_at_k(ranked_ids: Sequence[str], positives: Set[str], k: int) -> float:
    top = ranked_ids[:k]
    dcg = 0.0
    for i, rid in enumerate(top, start=1):
        rel = 1.0 if rid in positives else 0.0
        if rel > 0:
            dcg += rel / math.log2(i + 1)
    ideal_hits = min(k, len(positives))
    if ideal_hits == 0:
        return 0.0
    idcg = sum(1.0 / math.log2(i + 1) for i in range(1, ideal_hits + 1))
    if idcg == 0:
        return 0.0
    return dcg / idcg


def bootstrap_ci(values: List[float], iterations: int = 1000, seed: int = 42) -> Tuple[float, float]:
    if not values:
        return (0.0, 0.0)
    rng = random.Random(seed)
    means: List[float] = []
    n = len(values)
    for _ in range(iterations):
        sample = [values[rng.randrange(n)] for _ in range(n)]
        means.append(sum(sample) / len(sample))
    means.sort()
    lo_idx = int(0.025 * (len(means) - 1))
    hi_idx = int(0.975 * (len(means) - 1))
    return (means[lo_idx], means[hi_idx])


def paired_bootstrap_delta_ci(
    a_values: List[float],
    b_values: List[float],
    iterations: int = 1000,
    seed: int = 42,
) -> Tuple[float, float, float]:
    if len(a_values) != len(b_values):
        raise ValueError("Paired vectors must have same length")
    if not a_values:
        return (0.0, 0.0, 0.0)
    deltas = [a - b for a, b in zip(a_values, b_values)]
    rng = random.Random(seed)
    n = len(deltas)
    means: List[float] = []
    for _ in range(iterations):
        sample = [deltas[rng.randrange(n)] for _ in range(n)]
        means.append(sum(sample) / n)
    means.sort()
    lo = means[int(0.025 * (len(means) - 1))]
    hi = means[int(0.975 * (len(means) - 1))]
    return (sum(deltas) / n, lo, hi)


def evaluate_method(
    method: str,
    components: Dict[str, Component],
    queries: List[dict],
    scope: str,
) -> dict:
    per_query: List[dict] = []
    p1_vals: List[float] = []
    p5_vals: List[float] = []
    p10_vals: List[float] = []
    r5_vals: List[float] = []
    r10_vals: List[float] = []
    mrr_vals: List[float] = []
    ndcg10_vals: List[float] = []

    for q in queries:
        q_id = q["query_id"]
        positives = set(q["positives"])
        query = components[q_id]

        candidates = [c for c in components.values() if c.id != q_id]
        if scope == "same":
            positives = {pid for pid in positives if components[pid].framework == query.framework}
            candidates = [c for c in candidates if c.framework == query.framework]
        elif scope == "cross":
            positives = {pid for pid in positives if components[pid].framework != query.framework}
            candidates = [c for c in candidates if c.framework != query.framework]
        if not positives:
            continue

        ranked = sorted(candidates, key=lambda c: method_score(method, query, c), reverse=True)
        ranked_ids = [c.id for c in ranked]

        p1_vals.append(precision_at_k(ranked_ids, positives, 1))
        p5 = precision_at_k(ranked_ids, positives, 5)
        p10 = precision_at_k(ranked_ids, positives, 10)
        r5 = recall_at_k(ranked_ids, positives, 5)
        r10 = recall_at_k(ranked_ids, positives, 10)
        rr = reciprocal_rank(ranked_ids, positives)
        nd10 = ndcg_at_k(ranked_ids, positives, 10)
        p5_vals.append(p5)
        p10_vals.append(p10)
        r5_vals.append(r5)
        r10_vals.append(r10)
        mrr_vals.append(rr)
        ndcg10_vals.append(nd10)
        per_query.append(
            {
                "query_id": q_id,
                "p_at_1": p1_vals[-1],
                "p_at_5": p5,
                "p_at_10": p10,
                "recall_at_5": r5,
                "recall_at_10": r10,
                "rr": rr,
                "ndcg_at_10": nd10,
            }
        )

    def avg(xs: List[float]) -> float:
        return sum(xs) / len(xs) if xs else 0.0

    p5_ci = bootstrap_ci(p5_vals)
    mrr_ci = bootstrap_ci(mrr_vals)
    return {
        "num_queries": len(p1_vals),
        "p_at_1": avg(p1_vals),
        "p_at_5": avg(p5_vals),
        "p_at_10": avg(p10_vals),
        "recall_at_5": avg(r5_vals),
        "recall_at_10": avg(r10_vals),
        "mrr": avg(mrr_vals),
        "ndcg_at_10": avg(ndcg10_vals),
        "p_at_5_ci95": [p5_ci[0], p5_ci[1]],
        "mrr_ci95": [mrr_ci[0], mrr_ci[1]],
        "per_query": per_query,
    }


def build_markdown_table(results: Dict[str, dict], scope: str) -> str:
    lines = []
    lines.append(f"# Retrieval Benchmark Results ({scope})")
    lines.append("")
    lines.append("| method | queries | P@1 | P@5 | R@5 | MRR | nDCG@10 |")
    lines.append("|---|---:|---:|---:|---:|---:|---:|")
    for method, row in sorted(results.items()):
        lines.append(
            f"| {method} | {row['num_queries']} | {row['p_at_1']:.4f} | {row['p_at_5']:.4f} | "
            f"{row['recall_at_5']:.4f} | {row['mrr']:.4f} | {row['ndcg_at_10']:.4f} |"
        )
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description="Evaluate labeled retrieval benchmark.")
    parser.add_argument(
        "--dataset",
        type=Path,
        default=Path("datasets/quality/retrieval-benchmark-v1.json"),
    )
    parser.add_argument(
        "--methods",
        type=str,
        default="proposed_full,dom_only,css_only,behavior_only,dom_css,dom_behavior,css_behavior,token_baseline,random_baseline",
    )
    parser.add_argument("--scope", choices=["all", "same", "cross"], default="all")
    parser.add_argument("--baseline-method", type=str, default="token_baseline")
    parser.add_argument("--output-json", type=Path, default=Path("out/retrieval-benchmark-summary.json"))
    parser.add_argument("--output-md", type=Path, default=Path("out/retrieval-benchmark-summary.md"))
    args = parser.parse_args()

    dataset = json.loads(args.dataset.read_text())
    components: Dict[str, Component] = {}
    for row in dataset["components"]:
        components[row["id"]] = Component(
            id=row["id"],
            framework=row["framework"],
            tokens=set(row.get("tokens", [])),
            dom_tags={k: int(v) for k, v in row.get("dom_tags", {}).items()},
            css_tokens={k: int(v) for k, v in row.get("css_tokens", {}).items()},
            behavior_events=set(row.get("behavior_events", [])),
            behavior_states=set(row.get("behavior_states", [])),
        )
    queries = dataset["queries"]

    methods = [m.strip() for m in args.methods.split(",") if m.strip()]
    results: Dict[str, dict] = {}
    for method in methods:
        results[method] = evaluate_method(method, components, queries, args.scope)

    significance = {}
    baseline = args.baseline_method
    if baseline in results:
        baseline_per_query = {row["query_id"]: row for row in results[baseline]["per_query"]}
        for method in methods:
            if method == baseline:
                continue
            method_per_query = {row["query_id"]: row for row in results[method]["per_query"]}
            common = sorted(set(method_per_query.keys()) & set(baseline_per_query.keys()))
            if not common:
                continue
            method_mrr = [method_per_query[q]["rr"] for q in common]
            base_mrr = [baseline_per_query[q]["rr"] for q in common]
            method_p5 = [method_per_query[q]["p_at_5"] for q in common]
            base_p5 = [baseline_per_query[q]["p_at_5"] for q in common]
            mrr_delta, mrr_lo, mrr_hi = paired_bootstrap_delta_ci(method_mrr, base_mrr)
            p5_delta, p5_lo, p5_hi = paired_bootstrap_delta_ci(method_p5, base_p5)
            significance[method] = {
                "vs_baseline": baseline,
                "common_queries": len(common),
                "mrr_delta_ci95": [mrr_delta, mrr_lo, mrr_hi],
                "p_at_5_delta_ci95": [p5_delta, p5_lo, p5_hi],
            }

    payload = {
        "dataset": str(args.dataset),
        "scope": args.scope,
        "methods": methods,
        "results": results,
        "significance": significance,
    }
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(json.dumps(payload, indent=2) + "\n")
    args.output_md.parent.mkdir(parents=True, exist_ok=True)
    args.output_md.write_text(build_markdown_table(results, args.scope))

    print(json.dumps(payload, indent=2))
    print(f"\nWrote JSON summary to {args.output_json}")
    print(f"Wrote markdown summary to {args.output_md}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
