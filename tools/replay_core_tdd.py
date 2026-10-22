"""Replay preserved core Java red/green snapshots in a temporary directory.

Requires Maven and a JDK on PATH (or --maven). It never builds inside evidence.
"""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--maven", default="mvn")
    parser.add_argument("--repository", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    records = []
    with tempfile.TemporaryDirectory(prefix="hashrope-tdd-") as temporary:
        for snapshot in sorted((root / "evidence/tdd").iterdir()):
            if not (snapshot / "exit-code.txt").exists():
                continue
            work = Path(temporary) / snapshot.name
            shutil.copytree(snapshot, work)
            match = re.search(r"-Dtest=([^\s]+)", (work / "command.txt").read_text(encoding="utf-8-sig"))
            cmd = [args.maven, "-B", "-ntp", "-s", str(root / "tools/isolated-settings.xml"),
                   "-gs", str(root / "tools/isolated-settings.xml"), "-f", str(work / "pom.xml"),
                   "test", "-Dtest=" + (match.group(1) if match else "RopeTest")]
            if args.repository:
                cmd.append("-Dmaven.repo.local=" + str(args.repository.resolve()))
            result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            expected = int((snapshot / "exit-code.txt").read_text(encoding="utf-8-sig").strip())
            matched = (expected == 0) == (result.returncode == 0)
            # Red must be a code/test failure, never a network/plugin/toolchain failure.
            if expected != 0:
                matched = matched and any(s in result.stdout for s in ("cannot find symbol", "There are test failures"))
            records.append({"snapshot": snapshot.name, "expected_exit": expected, "exit": result.returncode, "matched": matched})
            print(snapshot.name, "PASS" if matched else "FAIL", flush=True)
            if not matched:
                print(result.stdout)
        report = {"snapshots": records, "matched": all(r["matched"] for r in records)}
        if args.output:
            args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        if not report["matched"]:
            raise SystemExit(1)


if __name__ == "__main__":
    main()
