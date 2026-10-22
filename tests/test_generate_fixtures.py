import hashlib
import importlib.util
import json
import os
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("generator", ROOT / "tools" / "generate_fixtures.py")
GEN = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GEN)
WHEEL = Path(os.environ.get("HASHROPE_REFERENCE_WHEEL", str(ROOT.parent / "upstream" / "hashrope-0.2.2-py3-none-any.whl")))


class FixtureGeneratorTest(unittest.TestCase):
    def test_rejects_unpinned_artifact(self):
        with tempfile.TemporaryDirectory() as tmp:
            wrong = Path(tmp) / "wrong.whl"
            wrong.write_bytes(b"not the pinned wheel")
            with self.assertRaisesRegex(ValueError, "SHA256 mismatch"):
                GEN.generate(wrong, Path(tmp) / "fixtures")

    def test_pinned_generation_is_deterministic_and_complete(self):
        with tempfile.TemporaryDirectory() as tmp:
            first, second = Path(tmp) / "a", Path(tmp) / "b"
            GEN.generate(WHEEL, first)
            GEN.generate(WHEEL, second)
            self.assertEqual(sorted(p.name for p in first.iterdir()), sorted(p.name for p in second.iterdir()))
            for left in first.iterdir():
                self.assertEqual(left.read_bytes(), (second / left.name).read_bytes(), left.name)
            manifest = json.loads((first / "manifest.json").read_text())
            self.assertEqual("0.2.2", manifest["python_release"])
            self.assertEqual("710112e3aa14a991918a15331c1f418ce9d1c9d93c34a9da09e22be33c68a2d2", manifest["wheel_sha256"])
            self.assertGreater(manifest["total_records"], 3000)
            expected = {"hash", "power", "phi", "mod", "mul", "concatHash", "repeatHash", "overlapHash", "rope", "split", "substr", "sliding", "error"}
            self.assertTrue(expected.issubset(set(manifest["record_counts"])))
            for filename, expected_hash in manifest["fixture_sha256"].items():
                self.assertEqual(expected_hash, hashlib.sha256((first / filename).read_bytes()).hexdigest())
            records = [line.split("\t") for line in (first / "golden.tsv").read_text().splitlines() if not line.startswith("#")]
            empty = next(row for row in records if row[1] == "default-empty")
            self.assertEqual(["hash", "default-empty", "2305843009213693951", "131", "", "0"], empty)
            byte_ff = next(row for row in records if row[1] == "default-ff")
            self.assertEqual("256", byte_ff[-1])
            self.assertTrue(any(row[0] == "rope" and int(row[5]) > 2**63 and row[-1] == "-" for row in records))
            self.assertTrue(any(row[0] == "substr" and int(row[5]) < 0 for row in records))

    def test_signed_edge_cases_and_empty_repeat_identities_are_represented(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "fixtures"
            GEN.generate(WHEEL, output)
            rows = [line.split("\t") for line in (output / "golden.tsv").read_text().splitlines() if not line.startswith("#")]
            self.assertTrue(any(row[0] == "phi" and int(row[5]) < 0 for row in rows))
            self.assertTrue(any(row[0] == "overlapHash" and int(row[5]) < 0 for row in rows))
            self.assertTrue(any(row[0] == "overlapHash" and int(row[6]) < 0 for row in rows))
            self.assertTrue(any(row[0] == "repeatHash" and int(row[6]) < 0 for row in rows))
            self.assertTrue(any(row[0] == "concatHash" and int(row[5]) < 0 for row in rows))
            specs = {row[4] for row in rows if row[0] == "rope"}
            self.assertTrue({"E,R0", "E,R1", "L61,L62,I"}.issubset(specs))


if __name__ == "__main__":
    unittest.main()
