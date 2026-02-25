#!/usr/bin/env python3
"""Summarize scanner parity audit CSV output.

Expected columns (from MainKt --audit-out):
repo_id,framework,simple_count,ast_count,only_simple,only_ast,only_simple_sample,only_ast_sample
"""

import argparse
import csv
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List


@dataclass
class AuditRow:
    repo_id: str
    framework: str
    simple_count: int
    ast_count: int
    only_simple: int
    only_ast: int
    only_simple_sample: str
    only_ast_sample: str

    @property
    def mismatch_total(self) -> int:
        return self.only_simple + self.only_ast


@dataclass
class FrameworkSummary:
    repos: int = 0
    repos_with_mismatch: int = 0
    total_simple: int = 0
    total_ast: int = 0
    total_only_simple: int = 0
    total_only_ast: int = 0


def load_rows(csv_path: Path) -> List[AuditRow]:
    rows: List[AuditRow] = []
    with csv_path.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        required = {
            "repo_id",
            "framework",
            "simple_count",
            "ast_count",
            "only_simple",
            "only_ast",
            "only_simple_sample",
            "only_ast_sample",
        }
        missing = required - set(reader.fieldnames or [])
        if missing:
            raise ValueError(f"Missing required columns: {sorted(missing)}")

        for raw in reader:
            rows.append(
                AuditRow(
                    repo_id=raw["repo_id"],
                    framework=raw["framework"],
                    simple_count=int(raw["simple_count"]),
                    ast_count=int(raw["ast_count"]),
                    only_simple=int(raw["only_simple"]),
                    only_ast=int(raw["only_ast"]),
                    only_simple_sample=raw.get("only_simple_sample", ""),
                    only_ast_sample=raw.get("only_ast_sample", ""),
                )
            )
    return rows


def summarize(rows: List[AuditRow], top_n: int) -> str:
    if not rows:
        return "No rows in audit file."

    by_framework: Dict[str, FrameworkSummary] = {}
    total_mismatch_repos = 0

    for row in rows:
        s = by_framework.setdefault(row.framework, FrameworkSummary())
        s.repos += 1
        s.total_simple += row.simple_count
        s.total_ast += row.ast_count
        s.total_only_simple += row.only_simple
        s.total_only_ast += row.only_ast
        if row.mismatch_total > 0:
            s.repos_with_mismatch += 1
            total_mismatch_repos += 1

    lines: List[str] = []
    lines.append("Scanner Parity Audit Summary")
    lines.append(f"Total repos compared: {len(rows)}")
    lines.append(f"Repos with mismatches: {total_mismatch_repos}")
    lines.append("")
    lines.append("By framework:")

    for fw in sorted(by_framework):
        s = by_framework[fw]
        mismatch_rate = (s.repos_with_mismatch / s.repos * 100.0) if s.repos else 0.0
        lines.append(
            f"- {fw}: repos={s.repos}, mismatch_repos={s.repos_with_mismatch} "
            f"({mismatch_rate:.1f}%), simple_total={s.total_simple}, ast_total={s.total_ast}, "
            f"only_simple_total={s.total_only_simple}, only_ast_total={s.total_only_ast}"
        )

    top = sorted(rows, key=lambda r: r.mismatch_total, reverse=True)
    top = [r for r in top if r.mismatch_total > 0][:top_n]

    lines.append("")
    lines.append(f"Top {top_n} repos by mismatch:")
    if not top:
        lines.append("- none")
    else:
        for row in top:
            lines.append(
                f"- {row.repo_id} ({row.framework}): mismatch={row.mismatch_total}, "
                f"only_simple={row.only_simple}, only_ast={row.only_ast}"
            )
            if row.only_simple_sample:
                lines.append(f"  only_simple_sample: {row.only_simple_sample}")
            if row.only_ast_sample:
                lines.append(f"  only_ast_sample: {row.only_ast_sample}")

    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description="Summarize scanner parity audit CSV")
    parser.add_argument("--input", required=True, type=Path, help="Path to scanner parity CSV")
    parser.add_argument("--top", type=int, default=10, help="How many top mismatch repos to show")
    parser.add_argument("--output", type=Path, help="Optional path to write summary text")
    args = parser.parse_args()

    rows = load_rows(args.input)
    report = summarize(rows, args.top)

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report + "\n", encoding="utf-8")
        print(f"Wrote summary to {args.output}")
    else:
        print(report)


if __name__ == "__main__":
    main()
