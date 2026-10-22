#!/usr/bin/env python3
"""Run only the tests published in the pinned sdist against the pinned wheel."""
from __future__ import annotations
import argparse
import hashlib
import importlib
import importlib.metadata
import json
import os
from pathlib import Path, PurePosixPath
import re
import sys
import tarfile

WHEEL_SHA256 = "710112e3aa14a991918a15331c1f418ce9d1c9d93c34a9da09e22be33c68a2d2"
SDIST_SHA256 = "881b367afc38b5d32609d3d604e4a19ce27e4eaeb3078e54948bdad69e54b499"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--wheel", type=Path, required=True)
    parser.add_argument("--sdist", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    wheel, sdist, output = args.wheel.resolve(), args.sdist.resolve(), args.output.resolve()
    for artifact, expected in [(wheel, WHEEL_SHA256), (sdist, SDIST_SHA256)]:
        actual = hashlib.sha256(artifact.read_bytes()).hexdigest()
        if actual != expected:
            raise ValueError(f"Artifact SHA256 mismatch: {artifact.name}")
    if output.exists():
        raise FileExistsError(f"Use a new evidence output directory: {output}")
    output.mkdir(parents=True)
    tests_root = output / "published-tests"
    tests_root.mkdir()
    source_hashes = {}
    member_names = []
    with tarfile.open(sdist, "r:gz") as archive:
        for member in archive.getmembers():
            member_names.append(member.name)
            parts = PurePosixPath(member.name).parts
            if len(parts) < 3 or parts[:2] != ("hashrope-0.2.2", "tests") or not member.name.endswith(".py"):
                continue
            if not member.isfile() or any(part in ("..", ".", "") for part in parts):
                raise ValueError(f"Unsafe archive member: {member.name}")
            target = tests_root.joinpath(*parts[1:]).resolve()
            if tests_root not in target.parents:
                raise ValueError(f"Archive target escaped output: {member.name}")
            content = archive.extractfile(member).read()
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
            source_hashes[member.name] = hashlib.sha256(content).hexdigest()
    if not source_hashes:
        raise RuntimeError("Pinned sdist includes no Python tests")
    os.environ["PYTHONDONTWRITEBYTECODE"] = "1"
    os.environ["PYTEST_DISABLE_PLUGIN_AUTOLOAD"] = "1"
    os.environ["PYTEST_ADDOPTS"] = ""
    sys.dont_write_bytecode = True
    for name in list(sys.modules):
        if name == "hashrope" or name.startswith("hashrope."):
            del sys.modules[name]
    sys.path.insert(0, str(wheel))
    reference = importlib.import_module("hashrope")
    if reference.__version__ != "0.2.2" or not reference.__file__.startswith(str(wheel)):
        raise RuntimeError("Oracle module did not originate in the pinned wheel")
    pytest = importlib.import_module("pytest")
    metadata_bytes = args.metadata.read_bytes()
    metadata = json.loads(metadata_bytes.decode("utf-8-sig"))
    all_releases = sorted(metadata["releases"], key=lambda s: tuple(int(x) for x in s.split(".")) if re.fullmatch(r"\d+\.\d+\.\d+", s) else (-1,))
    stable = [v for v in all_releases if re.fullmatch(r"\d+\.\d+\.\d+", v) and any(not file.get("yanked", False) for file in metadata["releases"][v])]
    config = output / "pytest.ini"
    config.write_text("[pytest]\n", encoding="utf-8")
    pytest_args = [str(tests_root / "tests"), "-c", str(config), "-p", "no:cacheprovider",
                   "--import-mode=importlib", "--junitxml", str(output / "pytest-results.xml"), "-q"]
    evidence = {
        "python": sys.version, "pytest_version": importlib.metadata.version("pytest"),
        "wheel_sha256": WHEEL_SHA256, "sdist_sha256": SDIST_SHA256,
        "reference_module_file": reference.__file__, "reference_version": reference.__version__,
        "command": [sys.executable, "-B", *sys.argv], "pytest_args": pytest_args,
        "pytest_plugin_autoload": False, "pytest_cache_provider": False,
        "write_bytecode": False, "source_test_sha256": source_hashes,
        "source_archive_members": member_names,
        "registry_url": "https://pypi.org/pypi/hashrope/json",
        "registry_snapshot_sha256": hashlib.sha256(metadata_bytes).hexdigest(),
        "registry_info_version": metadata["info"]["version"], "registry_release_keys": all_releases,
        "stable_non_yanked_releases": stable, "latest_stable": stable[-1],
    }
    print(json.dumps({"wheel": reference.__file__, "pytest": evidence["pytest_version"],
                      "source_test_files": len(source_hashes), "stable_releases": stable}, indent=2), flush=True)
    result = pytest.main(pytest_args)
    evidence["pytest_exit_code"] = int(result)
    evidence["hashrope_module_origins_after_tests"] = {
        name: module.__file__ for name, module in sys.modules.items()
        if (name == "hashrope" or name.startswith("hashrope.")) and hasattr(module, "__file__")
    }
    if any(not name.startswith(str(wheel)) for name in evidence["hashrope_module_origins_after_tests"].values()):
        raise RuntimeError("Tests imported a Hashrope module outside the pinned wheel")
    (output / "verification.json").write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8", newline="\n")
    return int(result)


if __name__ == "__main__":
    raise SystemExit(main())
