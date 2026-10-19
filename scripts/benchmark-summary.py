"""Prints the medians (and frame percentiles) from macrobenchmark result files. Used by baseline-profile.sh."""
import json
import sys

for path in sys.argv[1:]:
    with open(path) as f:
        data = json.load(f)
    for bench in data["benchmarks"]:
        name = bench["name"]
        for metric, m in bench["metrics"].items():
            runs = ", ".join(str(round(x)) for x in m["runs"])
            print(f"{name:32} {metric:26} median {m['median']:8.1f}   runs: {runs}")
        for metric, m in bench.get("sampledMetrics", {}).items():
            print(f"{name:32} {metric:26} P50 {m['P50']:6.1f}  P90 {m['P90']:6.1f}  P99 {m['P99']:6.1f}")
