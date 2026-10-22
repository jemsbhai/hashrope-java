"""Replay the hasher extension/traversal Maven red/green snapshots in temporary copies."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence", type=Path, default=Path(__file__).parent)
    parser.add_argument("--maven", default="mvn")
    parser.add_argument("--jdk", type=Path)
    parser.add_argument("--repository", type=Path)
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    env = os.environ.copy()
    if args.jdk:
        env["JAVA_HOME"] = str(args.jdk)
    manifests = sorted(args.evidence.glob("*/maven-manifest.json"))
    if not manifests:
        parser.error("No Maven snapshots found")
    records = []
    for manifest_path in manifests:
        source = manifest_path.parent
        manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
        for entry in manifest["sourceHashes"]:
            actual = hashlib.sha256((source / Path(entry["path"].replace("\\", "/"))).read_bytes()).hexdigest()
            if actual != entry["sha256"]:
                raise AssertionError(f"Snapshot hash mismatch: {source.name}/{entry['path']}")
        with tempfile.TemporaryDirectory(prefix="hashrope-extension-replay-") as directory:
            work = Path(directory)
            shutil.copytree(source / "snapshot", work / "project", ignore=shutil.ignore_patterns("target"))
            settings = work / "settings.xml"
            shutil.copyfile(source / "isolated-settings.xml", settings)
            cmd = [args.maven, "-B", "-ntp", "-s", str(settings), "-gs", str(settings),
                   "-f", str(work / "project/pom.xml"), "test", "-Dtest=" + manifest["test"]]
            if args.offline:
                cmd.append("-o")
            if args.repository:
                cmd.append("-Dmaven.repo.local=" + str(args.repository.resolve()))
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=90, env=env)
            output = result.stdout + result.stderr
            matched = (result.returncode == 0) == (manifest["exitCode"] == 0)
            if manifest["exitCode"]:
                marker = manifest.get("expectedFailure", "cannot inherit from final hashrope.PolynomialHash")
                matched = matched and marker in output
                tests = None
            else:
                report = ET.parse(work / "project/target/surefire-reports" / ("TEST-hashrope." + manifest["test"] + ".xml")).getroot()
                tests = int(report.attrib["tests"])
                matched = matched and tests > 0 and all(int(report.attrib[name]) == 0 for name in ("errors", "failures", "skipped"))
            record = {"snapshot": source.name, "expectedExit": manifest["exitCode"], "actualExit": result.returncode, "tests": tests, "matched": matched}
            records.append(record)
            print(json.dumps(record), flush=True)
            if not matched:
                raise AssertionError(output)
    if args.output:
        args.output.write_text(json.dumps({"snapshots": records, "matched": True}, indent=2) + "\n", encoding="utf-8")
    print(f"Replayed {len(records)} Maven snapshots successfully", flush=True)


if __name__ == "__main__":
    main()
