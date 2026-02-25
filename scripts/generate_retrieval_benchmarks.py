#!/usr/bin/env python3
"""Generate expanded Tier B and held-out Tier C retrieval benchmark datasets."""

from __future__ import annotations

import argparse
import json
import random
from pathlib import Path
from typing import Dict, List, Tuple


FRAMEWORKS = ["react", "angular", "vue"]


def perturb_count_map(rng: random.Random, base: Dict[str, int], max_delta: int = 1) -> Dict[str, int]:
    out: Dict[str, int] = {}
    for k, v in base.items():
        delta = rng.randint(-max_delta, max_delta)
        out[k] = max(1, v + delta)
    return out


def maybe_add_token(rng: random.Random, values: List[str], pool: List[str], prob: float = 0.25) -> List[str]:
    out = list(values)
    if rng.random() < prob:
        candidate = rng.choice(pool)
        if candidate not in out:
            out.append(candidate)
    return out


def framework_state(framework: str) -> str:
    if framework == "react":
        return "localState"
    if framework == "vue":
        return "refState"
    if framework == "angular":
        return "serviceState"
    return "none"


def normalize_base(base: dict) -> dict:
    return {
        "id": base["id"],
        "framework": base["framework"],
        "tokens": list(base.get("tokens", [])),
        "dom_tags": dict(base.get("dom_tags", {})),
        "css_tokens": dict(base.get("css_tokens", {})),
        "behavior_events": list(base.get("behavior_events", [])),
        "behavior_states": list(base.get("behavior_states", [])),
    }


def generate_family_variants(
    rng: random.Random,
    family_key: str,
    base: dict,
    copies_per_framework: int,
    heldout: bool,
) -> Tuple[List[dict], Dict[str, List[str]]]:
    components: List[dict] = []
    by_framework: Dict[str, List[str]] = {fw: [] for fw in FRAMEWORKS}
    token_pool = ["responsive", "mobile", "desktop", "hover", "focus", "dark", "light"]

    for fw in FRAMEWORKS:
        for i in range(copies_per_framework):
            component = normalize_base(base)
            component["framework"] = fw
            component["id"] = f"{fw}/{family_key}-v{i + 1}" + ("-heldout" if heldout else "")
            component["dom_tags"] = perturb_count_map(rng, component["dom_tags"])
            component["css_tokens"] = perturb_count_map(rng, component["css_tokens"])
            component["tokens"] = maybe_add_token(rng, component["tokens"], token_pool)
            events = list(component["behavior_events"])
            if rng.random() < 0.2 and "hover" not in events:
                events.append("hover")
            component["behavior_events"] = sorted(set(events))
            component["behavior_states"] = [framework_state(fw)]
            components.append(component)
            by_framework[fw].append(component["id"])
    return components, by_framework


def build_queries(
    rng: random.Random,
    families: Dict[str, Dict[str, List[str]]],
    all_ids_by_family: Dict[str, List[str]],
    queries_per_family: int,
) -> List[dict]:
    queries: List[dict] = []
    family_names = sorted(families.keys())
    for family in family_names:
        ids = all_ids_by_family[family]
        for _ in range(min(queries_per_family, len(ids))):
            query_id = rng.choice(ids)
            query_fw = query_id.split("/")[0]
            positives = [cid for cid in ids if cid != query_id]
            # force at least one cross-framework positive by prioritizing other frameworks
            cross = [cid for cid in positives if not cid.startswith(query_fw + "/")]
            same = [cid for cid in positives if cid.startswith(query_fw + "/")]
            ordered_positives = cross + same

            other_families = [f for f in family_names if f != family]
            hard_negatives: List[str] = []
            for of in rng.sample(other_families, min(2, len(other_families))):
                hard_negatives.append(rng.choice(all_ids_by_family[of]))
            queries.append(
                {
                    "query_id": query_id,
                    "positives": ordered_positives[:6],
                    "hard_negatives": hard_negatives,
                }
            )
    return queries


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate Tier B/Tier C retrieval benchmark datasets.")
    parser.add_argument("--seed-dataset", type=Path, default=Path("datasets/quality/retrieval-benchmark-v1.json"))
    parser.add_argument("--tier-b-out", type=Path, default=Path("datasets/quality/retrieval-benchmark-tier-b.json"))
    parser.add_argument("--tier-c-out", type=Path, default=Path("datasets/quality/retrieval-benchmark-tier-c-heldout.json"))
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--tier-b-query-target", type=int, default=360)
    parser.add_argument("--tier-c-query-target", type=int, default=120)
    parser.add_argument("--copies-per-framework", type=int, default=36)
    args = parser.parse_args()

    rng = random.Random(args.seed)
    seed = json.loads(args.seed_dataset.read_text())
    seed_components = seed["components"]

    # Build family seeds by stem after first slash and before -v/heldout markers.
    family_seeds: Dict[str, dict] = {}
    for c in seed_components:
        stem = c["id"].split("/", 1)[1]
        stem = stem.replace("-v2", "")
        family = stem
        family_seeds.setdefault(family, c)

    families = sorted(family_seeds.keys())
    rng.shuffle(families)
    heldout_count = max(2, len(families) // 3)
    heldout_families = set(families[:heldout_count])
    tier_b_families = [f for f in families if f not in heldout_families]

    def build_dataset(selected_families: List[str], heldout: bool, query_target: int) -> dict:
        components: List[dict] = []
        by_family_ids: Dict[str, List[str]] = {}
        per_fw_map: Dict[str, Dict[str, List[str]]] = {}
        for family in selected_families:
            comps, by_fw = generate_family_variants(
                rng=rng,
                family_key=family,
                base=family_seeds[family],
                copies_per_framework=args.copies_per_framework,
                heldout=heldout,
            )
            components.extend(comps)
            by_family_ids[family] = [c["id"] for c in comps]
            per_fw_map[family] = by_fw

        queries_per_family = max(1, query_target // max(1, len(selected_families)))
        queries = build_queries(rng, per_fw_map, by_family_ids, queries_per_family)
        if len(queries) > query_target:
            rng.shuffle(queries)
            queries = queries[:query_target]

        return {
            "version": "v1-expanded",
            "description": "Auto-generated retrieval benchmark dataset",
            "heldout": heldout,
            "components": components,
            "queries": queries,
        }

    tier_b = build_dataset(tier_b_families, heldout=False, query_target=args.tier_b_query_target)
    tier_c = build_dataset(sorted(heldout_families), heldout=True, query_target=args.tier_c_query_target)

    args.tier_b_out.parent.mkdir(parents=True, exist_ok=True)
    args.tier_b_out.write_text(json.dumps(tier_b, indent=2) + "\n")
    args.tier_c_out.parent.mkdir(parents=True, exist_ok=True)
    args.tier_c_out.write_text(json.dumps(tier_c, indent=2) + "\n")

    summary = {
        "tier_b": {
            "file": str(args.tier_b_out),
            "components": len(tier_b["components"]),
            "queries": len(tier_b["queries"]),
        },
        "tier_c": {
            "file": str(args.tier_c_out),
            "components": len(tier_c["components"]),
            "queries": len(tier_c["queries"]),
        },
        "heldout_families": sorted(list(heldout_families)),
        "tier_b_families": tier_b_families,
    }
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
