#!/usr/bin/env python3
import argparse
import json
import re
import statistics
import subprocess
import tempfile
import time
from pathlib import Path


def run_cmd(cmd, cwd):
    start = time.perf_counter()
    subprocess.run(cmd, cwd=cwd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return time.perf_counter() - start


def make_repo(root: Path):
    repo = root / "repos" / "react" / "acme" / "sample-react"
    (repo / ".git").mkdir(parents=True, exist_ok=True)
    (repo / "src").mkdir(parents=True, exist_ok=True)
    (repo / "package.json").write_text('{"dependencies":{"react":"18.0.0"}}\n', encoding="utf-8")
    (repo / "src" / "Button.tsx").write_text(
        'export function Button(){ return <button className="btn">OK</button> }\n',
        encoding="utf-8",
    )
    (repo / "src" / "Button.css").write_text(".btn { margin: 8px; }\n", encoding="utf-8")
    (repo / "src" / "Card.tsx").write_text(
        'export const Card = () => <div role="region">Card</div>\n',
        encoding="utf-8",
    )
    return root / "repos"


def summarize(values):
    return {
        "min_sec": min(values),
        "max_sec": max(values),
        "avg_sec": statistics.mean(values),
        "p95_sec": sorted(values)[max(0, int(round(0.95 * (len(values) - 1))))],
    }


def main():
    p = argparse.ArgumentParser(description="Benchmark production CLI scan-index/query runtime on small representative subset.")
    p.add_argument("--iterations", type=int, default=3)
    p.add_argument("--budget", type=Path, default=Path("datasets/quality/cli-runtime-budget.json"))
    p.add_argument("--output", type=Path, default=Path("out/cli-runtime-benchmark-summary.json"))
    p.add_argument("--enforce-budget", action="store_true")
    args = p.parse_args()

    workspace = Path(__file__).resolve().parents[1]
    with tempfile.TemporaryDirectory(prefix="ui-sim-cli-bench-") as td:
        tdir = Path(td)
        repos_root = make_repo(tdir)
        snapshot = tdir / "out" / "index.json"
        snapshot.parent.mkdir(parents=True, exist_ok=True)

        scan_times = []
        query_times = []
        query_id = None
        for _ in range(args.iterations):
            scan_times.append(
                run_cmd(
                    [
                        "./gradlew",
                        "-q",
                        "runCli",
                        '-Pargs=scan-index --repos {} --out {} --mode simple --pivot-count 2'.format(
                            repos_root.as_posix(), snapshot.as_posix()
                        ),
                    ],
                    cwd=workspace,
                )
            )
            if query_id is None:
                text = snapshot.read_text(encoding="utf-8")
                m = re.search(r'"componentId"\s*:\s*"([^"]+)"', text)
                if not m:
                    raise SystemExit("Unable to detect componentId from generated snapshot")
                query_id = m.group(1)
            query_times.append(
                run_cmd(
                    [
                        "./gradlew",
                        "-q",
                        "runCli",
                        '-Pargs=query --index-file {} --component-id {} --top-k 2 --top-n 2'.format(
                            snapshot.as_posix(), query_id
                        ),
                    ],
                    cwd=workspace,
                )
            )

    result = {
        "iterations": args.iterations,
        "scan_index": summarize(scan_times),
        "query": summarize(query_times),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")

    print(json.dumps(result, indent=2))

    if args.enforce_budget:
        budget = json.loads(args.budget.read_text(encoding="utf-8"))
        violations = []
        if result["scan_index"]["p95_sec"] > budget["scan_index"]["p95_sec_max"]:
            violations.append(
                f"scan_index p95 {result['scan_index']['p95_sec']:.3f}s > budget {budget['scan_index']['p95_sec_max']:.3f}s"
            )
        if result["query"]["p95_sec"] > budget["query"]["p95_sec_max"]:
            violations.append(
                f"query p95 {result['query']['p95_sec']:.3f}s > budget {budget['query']['p95_sec_max']:.3f}s"
            )
        if violations:
            for v in violations:
                print(f"BUDGET_VIOLATION: {v}")
            raise SystemExit(2)


if __name__ == "__main__":
    main()
