"""Record/replay the bounded extreme-modulus reduction regression in a child JVM."""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import time


def decode(value: str | bytes | None) -> str:
    return value.decode("utf-8", errors="replace") if isinstance(value, bytes) else value or ""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=("red", "green"))
    parser.add_argument("--jdk", type=Path, required=True)
    parser.add_argument("--source", type=Path, required=True, help="PolynomialHash.java source to test")
    parser.add_argument("--probe", type=Path, default=Path(__file__).parent / "probe/hashrope/ExtremeModulusProbe.java")
    parser.add_argument("--output", type=Path, required=True, help="A new evidence/output directory")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    snapshot = args.output / "snapshot/hashrope"
    snapshot.mkdir(parents=True)
    source = snapshot / "PolynomialHash.java"
    probe = snapshot / "ExtremeModulusProbe.java"
    shutil.copyfile(args.source, source)
    shutil.copyfile(args.probe, probe)
    classes = args.output / "classes"
    classes.mkdir()
    suffix = ".exe" if __import__("os").name == "nt" else ""
    compile_cmd = [str(args.jdk / "bin" / ("javac" + suffix)), "-encoding", "UTF-8", "-d", str(classes), str(source), str(probe)]
    compilation = subprocess.run(compile_cmd, capture_output=True, text=True, timeout=60)
    (args.output / "compile.log").write_text(compilation.stdout + compilation.stderr, encoding="utf-8")
    if compilation.returncode:
        raise AssertionError("Probe did not compile; this is not the expected red")
    cmd = [str(args.jdk / "bin" / ("java" + suffix)), "-Xmx512m", "-cp", str(classes), "hashrope.ExtremeModulusProbe"]
    started = datetime.now(timezone.utc).isoformat()
    begin = time.monotonic()
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
        outcome, code = "exited", result.returncode
        output = result.stdout + result.stderr
    except subprocess.TimeoutExpired as error:
        # subprocess.run kills and waits for the child on timeout before raising.
        outcome, code = "watchdog-killed", None
        output = decode(error.stdout) + decode(error.stderr)
    elapsed = time.monotonic() - begin
    (args.output / "probe.log").write_text(output, encoding="utf-8")
    record = {"phase": args.phase, "startedUtc": started, "elapsedSeconds": elapsed,
              "outcome": outcome, "exitCode": code, "timeoutSeconds": 10,
              "maxHeap": "512m", "commands": {"compile": compile_cmd, "probe": cmd},
              "sourceHashes": {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in (source, probe)}}
    (args.output / "probe-manifest.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(record, indent=2), flush=True)
    if "prime.bitLength=1073741824" not in output:
        raise AssertionError("The allocated extreme integer was not reached; no valid overflow evidence")
    if args.phase == "red" and outcome != "watchdog-killed":
        raise AssertionError("Expected the old implementation to hit the watchdog")
    if args.phase == "green" and (outcome != "exited" or code != 0 or "result=0" not in output):
        raise AssertionError("Expected prompt completion with zero")


if __name__ == "__main__":
    main()
