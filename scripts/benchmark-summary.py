"""Prints the medians (and frame percentiles) from macrobenchmark result files. Used by baseline-profile.sh.

For frame metrics it also prints the share of janky frames as review 08 counted them (DESIGN 13.1): frames whose
frameOverrunMs is above 0, missing their deadline, over all iterations and the range across iterations, and the number
of frames.
"""
import json
import sys


def janky(runs):
    """The share of frames with an overrun above 0, overall and per iteration (in percent)."""
    per_run = [100 * sum(1 for x in run if x > 0) / len(run) for run in runs if run]
    frames = sum(len(run) for run in runs)
    overall = 100 * sum(1 for run in runs for x in run if x > 0) / frames if frames else 0
    return overall, per_run, frames


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
            if metric == "frameOverrunMs" and m.get("runs"):
                overall, per_run, frames = janky(m["runs"])
                spread = f"{min(per_run):.0f}–{max(per_run):.0f}%" if per_run else "—"
                print(f"{name:32} {'janky frames':26} {overall:5.1f}% ({spread} per iteration), {frames} frames")
