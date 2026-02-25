#!/usr/bin/env python3
"""Benchmark AST extractor helper scripts on mini-dataset samples."""

from __future__ import annotations

import argparse
import json
import statistics
import subprocess
import time
from pathlib import Path
from typing import Dict, List, Tuple


COMMANDS = {
    ("react", "dom"): ["node", "scripts/react-dom-ast-extract.mjs"],
    ("angular", "dom"): ["node", "scripts/angular-dom-ast-extract.mjs"],
    ("vue", "dom"): ["node", "scripts/vue-dom-ast-extract.mjs"],
    ("react", "css"): ["node", "scripts/react-css-ast-extract.mjs"],
    ("angular", "css"): ["node", "scripts/angular-css-ast-extract.mjs"],
    ("vue", "css"): ["node", "scripts/vue-css-ast-extract.mjs"],
    ("react", "behavior"): ["node", "scripts/react-behavior-ast-extract.mjs"],
    ("angular", "behavior"): ["node", "scripts/angular-behavior-ast-extract.mjs"],
    ("vue", "behavior"): ["node", "scripts/vue-behavior-ast-extract.mjs"],
}


def run_once(command: List[str], payload: Dict) -> float:
    start = time.perf_counter()
    proc = subprocess.run(
        command,
        input=json.dumps(payload),
        text=True,
        capture_output=True,
        check=False,
    )
    end = time.perf_counter()
    if proc.returncode != 0:
        raise RuntimeError(f"Command failed ({' '.join(command)}): {proc.stderr.strip() or proc.stdout.strip()}")
    data = json.loads(proc.stdout.strip() or "{}")
    if data.get("status") != "ok":
        raise RuntimeError(f"Command returned status={data.get('status')} for {' '.join(command)}")
    return (end - start) * 1000.0


def percentile(values: List[float], p: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    k = int(round((p / 100.0) * (len(ordered) - 1)))
    return ordered[k]


def load_budget(path: Path) -> Tuple[Dict[str, float], Dict[str, Dict[str, float]]]:
    raw = json.loads(path.read_text())
    default = raw.get("default", {"mean_ms_max": 250.0, "p95_ms_max": 500.0})
    overrides = raw.get("overrides", {})
    return default, overrides


def main() -> int:
    parser = argparse.ArgumentParser(description="Benchmark AST extractor scripts")
    parser.add_argument("--dataset", default="datasets/quality/extractor-mini-dataset.json", type=Path)
    parser.add_argument("--iterations", default=15, type=int)
    parser.add_argument("--warmup", default=3, type=int)
    parser.add_argument("--budget", default="datasets/quality/benchmark-budget.json", type=Path)
    parser.add_argument("--enforce-budget", action="store_true")
    parser.add_argument("--output", default=Path("out/ast-extractor-benchmark-summary.txt"), type=Path)
    args = parser.parse_args()

    dataset = json.loads(args.dataset.read_text())
    samples = dataset["samples"]
    default_budget, override_budget = load_budget(args.budget)

    by_key: Dict[Tuple[str, str], List[float]] = {}
    for sample in samples:
        key = (sample["framework"], sample["layer"])
        by_key.setdefault(key, [])
        command = COMMANDS[key]
        for _ in range(args.warmup):
            run_once(command, sample["input"])
        for _ in range(args.iterations):
            ms = run_once(command, sample["input"])
            by_key[key].append(ms)

    lines: List[str] = ["AST extractor benchmark summary"]
    failed_keys: List[str] = []
    for key in sorted(by_key.keys()):
        values = by_key[key]
        mean_ms = statistics.mean(values)
        p95_ms = percentile(values, 95)
        k = f"{key[0]}.{key[1]}"
        budget = override_budget.get(k, default_budget)
        mean_limit = float(budget["mean_ms_max"])
        p95_limit = float(budget["p95_ms_max"])
        pass_budget = mean_ms <= mean_limit and p95_ms <= p95_limit
        status = "PASS" if pass_budget else "FAIL"
        lines.append(
            f"{k}: n={len(values)} mean_ms={mean_ms:.2f} p95_ms={p95_ms:.2f} "
            f"budget_mean_ms={mean_limit:.2f} budget_p95_ms={p95_limit:.2f} status={status}"
        )
        if not pass_budget:
            failed_keys.append(k)

    text = "\n".join(lines)
    print(text)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(text + "\n")
    print(f"\nWrote benchmark summary to {args.output}")

    if args.enforce_budget and failed_keys:
        print(f"\nBudget check failed for: {', '.join(failed_keys)}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

