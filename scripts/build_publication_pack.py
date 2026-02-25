#!/usr/bin/env python3
"""Assemble publication evidence artifacts into one package directory."""

from __future__ import annotations

import argparse
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List


def copy_if_exists(src: Path, dst_dir: Path) -> bool:
    if not src.exists():
        return False
    dst_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst_dir / src.name)
    return True


def read_json(path: Path) -> Dict:
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text())
    except Exception:
        return {}


def main() -> int:
    p = argparse.ArgumentParser(description="Build publication evidence pack from benchmark artifacts.")
    p.add_argument("--name", default="tier-b", help="Pack label")
    p.add_argument("--output-root", type=Path, default=Path("out/publication-packs"))
    p.add_argument("--agreement", type=Path, default=Path("out/annotation-agreement-tier-b.json"))
    p.add_argument("--final-labels", type=Path, default=Path("out/final-labeled-pairs-tier-b.json"))
    p.add_argument("--adjudication-csv", type=Path, default=Path("datasets/quality/annotation/retrieval-benchmark-tier-b-adjudication.csv"))
    p.add_argument("--matrix-json", type=Path, default=Path("out/retrieval-benchmark-tier-b-matrix/matrix-summary.json"))
    p.add_argument("--matrix-md", type=Path, default=Path("out/retrieval-benchmark-tier-b-matrix/matrix-summary.md"))
    p.add_argument("--protocol", type=Path, default=Path("docs/similarity-evaluation-protocol.md"))
    p.add_argument("--annotation-guide", type=Path, default=Path("docs/retrieval-annotation-guide.md"))
    args = p.parse_args()

    ts = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    pack_dir = args.output_root / f"{args.name}-{ts}"
    pack_dir.mkdir(parents=True, exist_ok=True)

    files_to_copy: List[Path] = [
        args.agreement,
        args.final_labels,
        args.adjudication_csv,
        args.matrix_json,
        args.matrix_md,
        args.protocol,
        args.annotation_guide,
    ]
    copied = []
    missing = []
    artifacts_dir = pack_dir / "artifacts"
    for src in files_to_copy:
        if copy_if_exists(src, artifacts_dir):
            copied.append(str(src))
        else:
            missing.append(str(src))

    agreement = read_json(args.agreement)
    matrix = read_json(args.matrix_json)
    final_labels = read_json(args.final_labels)
    pairs = len(final_labels.get("pairs", [])) if isinstance(final_labels.get("pairs"), list) else 0

    manifest = {
        "pack_name": args.name,
        "generated_at_utc": ts,
        "pack_dir": str(pack_dir),
        "copied_files": copied,
        "missing_files": missing,
        "summary": {
            "pairs_final": pairs,
            "cohen_kappa": agreement.get("cohen_kappa"),
            "kappa_gate_pass": agreement.get("quality_gate_kappa_0_70_pass"),
            "matrix_scopes": list((matrix.get("scopes") or {}).keys()),
        },
    }
    (pack_dir / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")

    lines = [
        f"# Publication Pack: {args.name}",
        "",
        f"- generated_at_utc: `{ts}`",
        f"- pairs_final: `{pairs}`",
        f"- cohen_kappa: `{agreement.get('cohen_kappa')}`",
        f"- kappa_gate_pass: `{agreement.get('quality_gate_kappa_0_70_pass')}`",
        f"- matrix_scopes: `{', '.join(manifest['summary']['matrix_scopes'])}`",
        "",
        "## Included Artifacts",
    ]
    lines += [f"- `{Path(x).name}`" for x in copied]
    if missing:
        lines += ["", "## Missing (not copied)"] + [f"- `{x}`" for x in missing]
    (pack_dir / "README.md").write_text("\n".join(lines) + "\n")

    print(json.dumps({"pack_dir": str(pack_dir), "copied": len(copied), "missing": len(missing)}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
