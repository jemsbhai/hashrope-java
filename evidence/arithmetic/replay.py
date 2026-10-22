"""Replay recorded source/test snapshots without modifying them (Python 3.10+)."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import tempfile


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence", type=Path, default=Path(__file__).parent / "evidence")
    parser.add_argument("--jdk", type=Path, help="JDK home, if java/javac are not on PATH")
    parser.add_argument("--only", help="Replay a single snapshot directory by name")
    args = parser.parse_args()
    extension = ".exe" if __import__("os").name == "nt" else ""
    javac = str(args.jdk / "bin" / ("javac" + extension)) if args.jdk else shutil.which("javac")
    java = str(args.jdk / "bin" / ("java" + extension)) if args.jdk else shutil.which("java")
    if not javac or not java:
        parser.error("Use --jdk or put JDK executables on PATH")
    manifests = sorted(args.evidence.glob("*/manifest.json"))
    if args.only:
        manifests = [path for path in manifests if path.parent.name == args.only]
    if not manifests:
        parser.error("No matching snapshots")
    for path in manifests:
        expected = json.loads(path.read_text(encoding="utf-8-sig"))
        sources = sorted(str(source) for source in (path.parent / "snapshot").rglob("*.java"))
        with tempfile.TemporaryDirectory(prefix="hashrope-arithmetic-replay-") as output:
            compilation = subprocess.run([javac, "-encoding", "UTF-8", "-Xlint:all", "-d", output, *sources], capture_output=True, text=True, timeout=60)
            test_exit = None
            test_output = ""
            if compilation.returncode == 0:
                testing = subprocess.run([java, "-ea", "-cp", output, "hashrope.PolynomialHashTest"], capture_output=True, text=True, timeout=30)
                test_exit = testing.returncode
                test_output = testing.stdout + testing.stderr
            if compilation.returncode != expected["compileExit"] or test_exit != expected["testExit"]:
                raise AssertionError(f"{path.parent.name}: expected compile/test {expected['compileExit']}/{expected['testExit']}, got {compilation.returncode}/{test_exit}\n{compilation.stdout}\n{compilation.stderr}\n{test_output}")
            print(f"PASS {path.parent.name}: compile={compilation.returncode} test={test_exit}")
    print(f"Replayed {len(manifests)} snapshots successfully")


if __name__ == "__main__":
    main()
