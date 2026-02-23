#!/usr/bin/env python3
"""Evaluate scanner and extractor quality on labeled mini-datasets.

Outputs precision/recall/F1 summary to stdout and optionally to a file.
"""

from __future__ import annotations

import argparse
import json
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Set, Tuple


SCANNER_COMMANDS = {
    "react": ["node", "scripts/react-ast-scan.mjs"],
    "angular": ["node", "scripts/angular-ast-scan.mjs"],
    "vue": ["node", "scripts/vue-ast-scan.mjs"],
}

EXTRACTOR_COMMANDS = {
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


@dataclass
class Confusion:
    tp: int = 0
    fp: int = 0
    fn: int = 0

    def precision(self) -> float:
        return self.tp / (self.tp + self.fp) if (self.tp + self.fp) else 0.0

    def recall(self) -> float:
        return self.tp / (self.tp + self.fn) if (self.tp + self.fn) else 0.0

    def f1(self) -> float:
        p = self.precision()
        r = self.recall()
        return 2 * p * r / (p + r) if (p + r) else 0.0

    def add(self, expected: Set[str], actual: Set[str]) -> None:
        self.tp += len(expected & actual)
        self.fp += len(actual - expected)
        self.fn += len(expected - actual)


def run_command(command: List[str], payload: Dict) -> Dict:
    proc = subprocess.run(
        command,
        input=json.dumps(payload),
        text=True,
        capture_output=True,
        check=False,
    )
    if proc.returncode != 0:
        raise RuntimeError(f"Command failed ({' '.join(command)}): {proc.stderr.strip() or proc.stdout.strip()}")
    try:
        return json.loads(proc.stdout.strip() or "{}")
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"Invalid JSON from {' '.join(command)}: {exc}") from exc


def labels_from_extractor_response(layer: str, response: Dict) -> Set[str]:
    labels: Set[str] = set()
    if layer == "dom":
        for tag, count in response.get("tagHistogram", {}).items():
            if int(count) > 0:
                labels.add(f"tag:{tag}")
        for role, count in response.get("roleHistogram", {}).items():
            if int(count) > 0:
                labels.add(f"role:{role}")
        for item in response.get("layoutPatterns", []):
            labels.add(f"layout:{item}")
    elif layer == "css":
        for token, count in response.get("styleTokens", {}).items():
            if int(count) > 0:
                labels.add(f"token:{token}")
        for bucket, count in response.get("fontSizeBuckets", {}).items():
            if int(count) > 0:
                labels.add(f"bucket:{bucket}")
    elif layer == "behavior":
        for item in response.get("eventTypes", []):
            labels.add(f"event:{item}")
        for item in response.get("statePatterns", []):
            labels.add(f"state:{item}")
        for item in response.get("apiSignatures", []):
            labels.add(f"api:{item}")
    return labels


def evaluate_scanner(dataset_path: Path) -> Tuple[Confusion, List[str]]:
    dataset = json.loads(dataset_path.read_text())
    confusion = Confusion()
    notes: List[str] = []
    for case in dataset["cases"]:
        fw = case["framework"]
        command = SCANNER_COMMANDS[fw]
        payload = {
            "repoHost": case["repo_host"],
            "repoOwner": case["repo_owner"],
            "repoName": case["repo_name"],
            "repoRoot": case["repo_root"],
        }
        response = run_command(command, payload)
        if response.get("status") != "ok":
            notes.append(f"[scanner:{case['id']}] status={response.get('status')} error={response.get('error')}")
            confusion.add(set(case["expected_exports"]), set())
            continue
        actual = {c.get("exportName", "") for c in response.get("components", []) if c.get("exportName")}
        expected = set(case["expected_exports"])
        confusion.add(expected, actual)
    return confusion, notes


def evaluate_extractor(dataset_path: Path) -> Tuple[Confusion, List[str]]:
    dataset = json.loads(dataset_path.read_text())
    confusion = Confusion()
    notes: List[str] = []
    for sample in dataset["samples"]:
        key = (sample["framework"], sample["layer"])
        command = EXTRACTOR_COMMANDS[key]
        response = run_command(command, sample["input"])
        if response.get("status") != "ok":
            notes.append(f"[extractor:{sample['id']}] status={response.get('status')} error={response.get('error')}")
            confusion.add(set(sample["expected_labels"]), set())
            continue
        actual = labels_from_extractor_response(sample["layer"], response)
        expected = set(sample["expected_labels"])
        confusion.add(expected, actual)
    return confusion, notes


def format_section(name: str, confusion: Confusion) -> str:
    return (
        f"{name}:\n"
        f"  tp={confusion.tp} fp={confusion.fp} fn={confusion.fn}\n"
        f"  precision={confusion.precision():.4f}\n"
        f"  recall={confusion.recall():.4f}\n"
        f"  f1={confusion.f1():.4f}"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Evaluate scanner + extractor quality on mini-datasets")
    parser.add_argument("--scanner-dataset", default="datasets/quality/scanner-mini-dataset.json", type=Path)
    parser.add_argument("--extractor-dataset", default="datasets/quality/extractor-mini-dataset.json", type=Path)
    parser.add_argument("--output", default=Path("out/quality-mini-dataset-summary.txt"), type=Path)
    args = parser.parse_args()

    scanner_conf, scanner_notes = evaluate_scanner(args.scanner_dataset)
    extractor_conf, extractor_notes = evaluate_extractor(args.extractor_dataset)

    total = Confusion(
        tp=scanner_conf.tp + extractor_conf.tp,
        fp=scanner_conf.fp + extractor_conf.fp,
        fn=scanner_conf.fn + extractor_conf.fn,
    )

    lines: List[str] = []
    lines.append("Mini-dataset quality evaluation")
    lines.append(format_section("Scanner", scanner_conf))
    lines.append(format_section("Extractor", extractor_conf))
    lines.append(format_section("Combined", total))
    if scanner_notes or extractor_notes:
        lines.append("Notes:")
        lines.extend([f"  - {n}" for n in scanner_notes + extractor_notes])

    text = "\n".join(lines)
    print(text)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(text + "\n")
    print(f"\nWrote quality summary to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

