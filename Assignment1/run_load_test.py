#!/usr/bin/env python3
"""Convenience wrapper to run the high-throughput load test client and summarize results."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Iterable, Mapping


def run_command(command: Iterable[str], *, cwd: Path, env: Mapping[str, str]) -> None:
    """Execute a subprocess, surfacing failures with a helpful message."""
    command_list = list(command)
    print(f"\n$ {' '.join(command_list)}")
    try:
        subprocess.run(command_list, cwd=cwd, env=env, check=True)
    except subprocess.CalledProcessError as exc:
        raise SystemExit(f"Command {' '.join(command_list)} failed with exit code {exc.returncode}.") from exc


def format_number(value: float | None, suffix: str = "") -> str:
    if isinstance(value, (int, float)):
        return f"{value:.2f}{suffix}"
    return "n/a"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Compile and run the Client2 load test against a target base URL."
    )
    parser.add_argument(
        "base_url",
        help="Base URL for the skier service, e.g. http://localhost:8080/Server2_war",
    )
    parser.add_argument(
        "--skip-build",
        action="store_true",
        help="Assume the project is already compiled and skip `mvn package`.",
    )

    args = parser.parse_args(argv)

    assignment_dir = Path(__file__).resolve().parent
    env = os.environ.copy()
    env["CLIENT2_BASEURL"] = args.base_url

    if not args.skip_build:
        run_command(["mvn", "-q", "-DskipTests", "package"], cwd=assignment_dir, env=env)

    run_command(["mvn", "-q", "-DskipTests", "exec:java"], cwd=assignment_dir, env=env)

    summary_path = assignment_dir / "reports" / "summary.json"
    if not summary_path.exists():
        raise SystemExit(f"Summary file not found at {summary_path}. Did the client run successfully?")

    with summary_path.open(encoding="utf-8") as summary_file:
        summary = json.load(summary_file)

    latency = summary.get("latency", {}) if isinstance(summary, dict) else {}

    print("\n=== Load Test Summary ===")
    print(f"Summary file: {summary_path}")
    print(f"Total requests: {summary.get('totalRequests', 'n/a')}")
    print(f"Successful requests: {summary.get('successfulRequests', 'n/a')}")
    print(f"Failed requests: {summary.get('failedRequests', 'n/a')}")
    throughput = summary.get("throughput")
    print(f"Throughput: {format_number(throughput, ' req/s')}")
    print(f"Mean latency: {format_number(latency.get('mean'), ' ms')}")
    print(f"Median latency: {format_number(latency.get('median'), ' ms')}")
    print(f"P99 latency: {format_number(latency.get('p99'), ' ms')}")
    print(f"Min latency: {format_number(latency.get('min'), ' ms')}")
    print(f"Max latency: {format_number(latency.get('max'), ' ms')}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
