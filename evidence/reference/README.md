# Published Python reference suite verification

Result: **146 passed, 0 failures, 0 errors, 0 skipped**, in 15.65 seconds. Python 3.10.9 and pytest 9.1.1 were already installed; no packages were installed or configuration changed.

The tests are the eight Python files (six test modules plus `conftest.py` and `__init__.py`) published inside `hashrope-0.2.2.tar.gz`, SHA-256 `881b367afc38b5d32609d3d604e4a19ce27e4eaeb3078e54948bdad69e54b499`. Only test files were extracted, with validated paths and regular-file checks, to `run-001/published-tests/tests`.

The implementation under test came directly from `hashrope-0.2.2-py3-none-any.whl`, SHA-256 `710112e3aa14a991918a15331c1f418ce9d1c9d93c34a9da09e22be33c68a2d2`. Before collection and after the run, every imported `hashrope` module's location was verified to originate inside that wheel. The original checkout was never imported or executed.

Evidence:

- `pytest-run.log`: captured complete console result.
- `run-001/pytest-results.xml`: pytest's JUnit result with individual test names and durations.
- `run-001/verification.json`: exact command, runtime, pytest version, artifact hashes, test-source hashes, archive members, module import locations and exit code.
- `run-001/published-tests/tests`: exact test files as published, without modification.
- `pypi-live.json`: fresh direct response from `https://pypi.org/pypi/hashrope/json`.
- `registry-verification.json`: fresh registry snapshot hash, release keys, selected stable version and confirmed artifact hashes.

The fresh registry response lists release keys `0.1.0`, `0.2.0`, `0.2.1`, `0.2.2`; `info.version` is `0.2.2`. Both current artifacts are not yanked, and their hashes match the artifacts used for this run.

Reproduce from an isolated working directory, choosing a new output directory:

```text
python -B run_reference_suite.py --wheel /path/to/hashrope-0.2.2-py3-none-any.whl --sdist /path/to/hashrope-0.2.2.tar.gz --metadata pypi-live.json --output run-reproduced
```

The runner invokes pytest with `-p no:cacheprovider`, `--import-mode=importlib`, an empty local pytest configuration, and a local JUnit output path. Plugin autoload is disabled; bytecode writing is disabled. No `__pycache__` or `.pytest_cache` directory was produced. The runner refuses to overwrite an existing output directory. It reads no user publishing configuration or credentials.
