"""Generate graphs for similarity experiments.

This script reads evaluation results from JSON or CSV files and produces
graphs illustrating precision, recall and F1 for various configurations
(e.g. different weight combinations or index parameters).  The plots
are created using matplotlib and saved as PNG files.

Example usage:
    python plot_results.py --input results.json --output-dir plots
"""
import json
import csv
import argparse
from pathlib import Path
import matplotlib.pyplot as plt


def load_results(path: Path):
    """Load evaluation results from a JSON or CSV file.  The expected
    format is a list of records with fields `experiment`, `metric`,
    `value`.  For CSV input the columns should be `experiment,metric,value`.
    """
    if path.suffix.lower() == ".json":
        with path.open() as f:
            return json.load(f)
    elif path.suffix.lower() == ".csv":
        rows = []
        with path.open(newline="") as f:
            reader = csv.DictReader(f)
            for row in reader:
                rows.append({
                    "experiment": row["experiment"],
                    "metric": row["metric"],
                    "value": float(row["value"]),
                })
        return rows
    else:
        raise ValueError(f"Unsupported file format: {path}")


def plot_metrics(results, output_dir: Path):
    """Group results by experiment and plot precision, recall and F1 for each.
    """
    experiments = {}
    for row in results:
        exp = row["experiment"]
        metric = row["metric"]
        value = row["value"]
        experiments.setdefault(exp, {})[metric] = value
    # Plot for each metric across experiments
    metrics = {metric for row in results for metric in [row["metric"]]}
    for metric in metrics:
        names = []
        values = []
        for exp, metrics_map in experiments.items():
            if metric in metrics_map:
                names.append(exp)
                values.append(metrics_map[metric])
        plt.figure()
        plt.bar(names, values)
        plt.title(f"{metric} across experiments")
        plt.ylabel(metric)
        plt.xticks(rotation=45, ha='right')
        plt.tight_layout()
        output_file = output_dir / f"{metric}.png"
        plt.savefig(output_file)
        print(f"Saved plot to {output_file}")


def main():
    parser = argparse.ArgumentParser(description="Plot evaluation metrics")
    parser.add_argument("--input", required=True, type=Path, help="Path to JSON or CSV results file")
    parser.add_argument("--output-dir", required=True, type=Path, help="Directory to save plots")
    args = parser.parse_args()
    output_dir = args.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    results = load_results(args.input)
    plot_metrics(results, output_dir)


if __name__ == "__main__":
    main()