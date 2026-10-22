#!/usr/bin/env python3
"""Create deterministic differential fixtures from the exact published wheel.

This script imports only the verified wheel, never a checkout. Run with Python
3.10+ and ``python -B tools/generate_fixtures.py --wheel path/to/pinned.whl``.
No third-party dependencies are required.
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import importlib
import json
from pathlib import Path
import random
import sys
import zipfile

VERSION = "0.2.2"
WHEEL_SHA256 = "710112e3aa14a991918a15331c1f418ce9d1c9d93c34a9da09e22be33c68a2d2"
WHEEL_URL = "https://files.pythonhosted.org/packages/38/48/6a779bd0ae7decefff20e58999cb4ab78866eda6f6884874deb5dd149ab6/hashrope-0.2.2-py3-none-any.whl"
SDIST_SHA256 = "881b367afc38b5d32609d3d604e4a19ce27e4eaeb3078e54948bdad69e54b499"
SEED = 0x48415348524F5045


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def load_reference(wheel: Path):
    wheel = wheel.resolve()
    actual = sha256(wheel.read_bytes())
    if actual != WHEEL_SHA256:
        raise ValueError(f"SHA256 mismatch: expected {WHEEL_SHA256}, got {actual}")
    # Prevent a previously loaded checkout from silently supplying the oracle.
    for name in list(sys.modules):
        if name == "hashrope" or name.startswith("hashrope."):
            del sys.modules[name]
    sys.dont_write_bytecode = True
    sys.path.insert(0, str(wheel))
    try:
        reference = importlib.import_module("hashrope")
        if reference.__version__ != VERSION or not reference.__file__.startswith(str(wheel)):
            raise RuntimeError("Reference import did not originate in the pinned wheel")
        return reference
    finally:
        sys.path.pop(0)


def build(spec: str, h, ref):
    stack = []
    for token in spec.split(","):
        if token == "E":
            stack.append(None)
        elif token.startswith("L"):
            stack.append(ref.Leaf(bytes.fromhex(token[1:]), h))
        elif token in ("C", "I"):
            right, left = stack.pop(), stack.pop()
            stack.append(ref.rope_concat(left, right, h) if token == "C" else ref.Internal(left, right, h))
        elif token.startswith("R"):
            stack.append(ref.rope_repeat(stack.pop(), int(token[1:]), h))
        elif token.startswith("N"):
            stack.append(ref.RepeatNode(stack.pop(), int(token[1:]), h))
        else:
            raise ValueError(f"Unknown rope token: {token}")
    if len(stack) != 1:
        raise ValueError(f"Invalid postfix rope: {spec}")
    return stack[0]


def metadata(node, ref):
    length = ref.rope_len(node)
    return [length, 0 if node is None else node.weight, ref.rope_hash(node),
            ref.rope_height(node), ref.rope_to_bytes(node).hex() if length <= 65536 else "-"]


def generate(wheel: Path, output: Path):
    ref = load_reference(wheel)
    rng = random.Random(SEED)
    rows: list[list[object]] = []
    errors: list[list[object]] = []
    counts = collections.Counter()

    def emit(kind, identity, p, base, *fields):
        rows.append([kind, identity, p, base, *fields])
        counts[kind] += 1

    def error(identity, p, base, operation, args, action):
        try:
            action()
        except Exception as exc:
            errors.append(["error", identity, p, base, operation, *(list(args) + [""] * (4 - len(args))),
                           type(exc).__name__, str(exc)])
            counts["error"] += 1
        else:
            raise AssertionError(f"Expected oracle exception: {identity}")

    profiles = [("default", ref.MERSENNE_61, 131), ("wide", ref.MERSENNE_127, 257),
                ("small", 31, 3), ("boundary", ref.MERSENNE_61, ref.MERSENNE_61 - 1)]
    for label, p, base in profiles:
        h = ref.PolynomialHash(p, base)
        data_cases = [("empty", b""), ("zero", b"\0"), ("ff", b"\xff"),
                      ("unsigned", bytes(range(256))), ("hello", b"hello world"),
                      ("unicode", "A\u00e9\U0001f642e\u0301\u6f22\u0000".encode("utf-8")),
                      ("zero-padding", b"\0\0x\0"), ("ones", b"\xff" * 257)]
        data_cases += [(f"random-{i}", rng.randbytes(rng.randrange(0, 513))) for i in range(48)]
        for name, data in data_cases:
            emit("hash", f"{label}-{name}", p, base, data.hex(), h.hash(data))
        for n in [-37, -2, -1, 0, 1, 2, 3, 61, 127, 1024, 2**63 - 1, 2**63, 2**100 + 3]:
            emit("power", f"{label}-power-{n}", p, base, n, h.power(n))
        for q in [-17, -5, -2, -1, 0, 1, 2, 3, 7, 16, 1025, 2**63 + 7, 2**100 + 3]:
            for alpha in [-p - 1, -2, -1, 0, 1, 2, p - 1, p, p + 1]:
                emit("phi", f"{label}-phi-{q}-{alpha}", p, base, q, alpha, ref.phi(q, alpha, p))
        for i, a in enumerate([-2**300, -p - 1, -p, -1, 0, 1, p - 1, p, p + 1, p * p - 1,
                               p * p, p * p + 1, 2**2000 + 12345] + [rng.getrandbits(400) for _ in range(32)]):
            emit("mod", f"{label}-mod-{i}", p, base, a, ref.mersenne_mod(a, p))
        for i in range(64):
            a, b = rng.getrandbits(300), rng.getrandbits(300)
            if i % 11 == 0:
                a = -a
            emit("mul", f"{label}-mul-{i}", p, base, a, b, ref.mersenne_mul(a, b, p))
        for i in range(64):
            left = rng.randbytes(rng.randrange(0, 65))
            right = rng.randbytes(rng.randrange(0, 65))
            emit("concatHash", f"{label}-concat-{i}", p, base, h.hash(left), len(right), h.hash(right),
                 h.hash_concat(h.hash(left), len(right), h.hash(right)))
            pattern = rng.randbytes(rng.randrange(1, 33))
            q = [0, 1, 2, 3, 7, 1024, 2**63 + 1, 2**100 + 17][i % 8]
            emit("repeatHash", f"{label}-repeat-{i}", p, base, h.hash(pattern), len(pattern), q,
                 h.hash_repeat(h.hash(pattern), len(pattern), q))
            total = [0, 1, len(pattern) - 1, len(pattern), len(pattern) + 1, 2**63 + 3, 2**100 + 7][i % 7]
            prefix = h.hash(pattern[:total % len(pattern)])
            emit("overlapHash", f"{label}-overlap-{i}", p, base, h.hash(pattern), len(pattern), total, prefix,
                 h.hash_overlap(h.hash(pattern), len(pattern), total, prefix))

        for i, (h_a, len_b, h_b) in enumerate([(123, -1, 45), (-7, -3, 18), (p + 1, -17, -5)]):
            emit("concatHash", f"{label}-signed-concat-{i}", p, base, h_a, len_b, h_b, h.hash_concat(h_a, len_b, h_b))
        for i, (h_s, d, q) in enumerate([(123, -3, 7), (123, 3, -7), (-123, -3, -7), (p + 7, 0, 0)]):
            emit("repeatHash", f"{label}-signed-repeat-{i}", p, base, h_s, d, q, h.hash_repeat(h_s, d, q))
        for i, (h_p, d, total, prefix) in enumerate([(123, 3, -7, 98), (123, -3, 7, 98), (123, -3, -7, 98),
                                                   (-123, 3, -1, -98), (123, -3, 1, 98)]):
            emit("overlapHash", f"{label}-signed-overlap-{i}", p, base, h_p, d, total, prefix,
                 h.hash_overlap(h_p, d, total, prefix))

        specs = ["E", "E,R0", "E,R1", "L61,L62,I", "L00", "Lff", "L" + bytes(range(256)).hex(),
                 "L" + "A\u00e9\U0001f642e\u0301\u6f22".encode("utf-8").hex(),
                 "E,L61,C", "L61,E,C", "L616263,L646566,C", "L616263,R0", "L616263,R1",
                 "L616263,R2", "L616263,N7", "L616263,R5,R7", "L61,L6263,C,R17",
                 "L61,R1000,L6263,C", "L6263,L61,R1000,C",
                 f"L616263,R{2**100+3}", f"L61,L6263,C,R{2**80+1}"]
        for i in range(48):
            spec = "L" + rng.randbytes(rng.randrange(1, 17)).hex()
            for _ in range(rng.randrange(1, 9)):
                leaf = "L" + rng.randbytes(rng.randrange(1, 17)).hex()
                if rng.randrange(2):
                    leaf += f",R{rng.randrange(2, 13)}"
                spec = f"{spec},{leaf},C" if rng.randrange(2) else f"{leaf},{spec},C"
            if i % 3 == 0:
                spec += f",R{rng.randrange(2, 9)}"
            specs.append(spec)
        for i, spec in enumerate(specs):
            node = build(spec, h, ref)
            length = ref.rope_len(node)
            identity = f"{label}-rope-{i}"
            emit("rope", identity, p, base, spec, *metadata(node, ref))
            ref.validate_rope(node, h)
            for pos in sorted(set([-length - 1, -1, 0, 1, 2, length // 2, max(0, length - 1), length, length + 1])):
                left, right = ref.rope_split(node, pos, h)
                emit("split", f"{identity}-split-{pos}", p, base, spec, pos, *metadata(left, ref), *metadata(right, ref))
                ref.validate_rope(left, h)
                ref.validate_rope(right, h)
            ranges = [(0, 0), (0, length), (0, min(length, 1)), (length, 0),
                      (length // 3, length // 2), (max(0, length - 1), min(length, 1)),
                      (-1, 1), (-3, 5), (0, -1), (1, -3), (length + 1, 2),
                      (max(0, length - 1), 5), (-length - 2, length + 7)]
            if length:
                ranges += [(rng.randrange(length), 0) for _ in range(2)]
                for _ in range(4):
                    start = rng.randrange(length)
                    ranges.append((start, rng.randrange(length - start + 1)))
            for j, (start, size) in enumerate(ranges):
                try:
                    expected = ref.rope_substr_hash(node, start, size, h)
                except Exception:
                    error(f"{identity}-substr-{j}", p, base, "substr", [spec, start, size],
                          lambda: ref.rope_substr_hash(node, start, size, h))
                else:
                    emit("substr", f"{identity}-substr-{j}", p, base, spec, start, size, expected)

        # Boundary profiles and exact error messages are oracle data, not guesses.
        for invalid_base in [-1, 0, 1, p, p + 1]:
            error(f"{label}-base-{invalid_base}", p, invalid_base, "constructor", [],
                  lambda b=invalid_base: ref.PolynomialHash(p, b))
        error(f"{label}-empty-leaf", p, base, "leaf", [""], lambda: ref.Leaf(b"", h))
        for q in [-3, -1, 0, 1]:
            error(f"{label}-node-reps-{q}", p, base, "repeatNode", ["L61", q],
                  lambda q=q: ref.RepeatNode(ref.Leaf(b"a", h), q, h))
        error(f"{label}-repeat-negative", p, base, "repeat", ["L61", -1],
              lambda: ref.rope_repeat(ref.Leaf(b"a", h), -1, h))
        error(f"{label}-repeat-null", p, base, "repeat", ["E", 2], lambda: ref.rope_repeat(None, 2, h))
        error(f"{label}-internal-left-null", p, base, "internal", ["E", "L61"],
              lambda: ref.Internal(None, ref.Leaf(b"a", h), h))
        error(f"{label}-internal-right-null", p, base, "internal", ["L61", "E"],
              lambda: ref.Internal(ref.Leaf(b"a", h), None, h))
        error(f"{label}-overlap-zero", p, base, "overlapHash", [1, 0, 1, 0],
              lambda: h.hash_overlap(1, 0, 1, 0))
        unbalanced = "L61,L62,R100,I"
        error(f"{label}-unbalanced", p, base, "validate", [unbalanced],
              lambda: ref.validate_rope(build(unbalanced, h, ref), h))

        def stream(trace, dmax, mmax, operations):
            window = ref.SlidingWindow(dmax, mmax, p, base)
            all_operations = [("init", "", "")] + operations
            for step, (operation, arg1, arg2) in enumerate(all_operations):
                if operation == "bytes":
                    window.append_bytes(bytes.fromhex(arg1))
                elif operation == "copy":
                    window.append_copy(int(arg1), int(arg2))
                emit("sliding", f"{label}-{trace}-{step}", p, base, f"{label}-{trace}", step,
                     dmax, mmax, operation, arg1, arg2, window.pos, window.l_prefix, window.h_prefix,
                     window.window_len, ref.rope_hash(window.r_window), ref.rope_to_bytes(window.r_window).hex(),
                     window.current_hash(), window.final_hash(), ref.rope_height(window.r_window),
                     0 if window.r_window is None else window.r_window.weight)

        stream("boundaries", 4, 3, [("bytes", "", ""), ("bytes", b"abcd".hex(), ""),
               ("bytes", b"efg".hex(), ""), ("bytes", b"h".hex(), ""), ("copy", 4, 4),
               ("copy", 1, 13), ("bytes", bytes(range(32)).hex(), ""), ("copy", 4, 2),
               ("copy", 6, 0), ("copy", 0, 0)])
        stream("overlap", 16, 6, [("bytes", b"abc".hex(), ""), ("copy", 3, 17),
               ("copy", 8, 7), ("copy", 2, 29), ("copy", 1, 103), ("bytes", "00ff80", "")])
        random_ops = [("bytes", rng.randbytes(32).hex(), "")]
        model = ref.SlidingWindow(32, 11, p, base)
        model.append_bytes(bytes.fromhex(random_ops[0][1]))
        for _ in range(128):
            if rng.randrange(3) == 0:
                data = rng.randbytes(rng.randrange(0, 80))
                random_ops.append(("bytes", data.hex(), ""))
                model.append_bytes(data)
            else:
                offset = rng.randrange(1, model.window_len + 1)
                length = rng.randrange(0, 100)
                random_ops.append(("copy", offset, length))
                model.append_copy(offset, length)
        stream("random", 32, 11, random_ops)
        # Python accepts these inputs and can make pos disagree with window_len.
        stream("quirks-negative-length", 8, 3, [("bytes", "616263", ""), ("copy", 1, -1)])
        stream("quirks-negative-offset", 8, 3, [("bytes", "616263", ""), ("copy", -1, -2)])
        for dmax, mmax in [(0, 1), (-1, 1), (1, 0), (1, -1)]:
            error(f"{label}-sliding-ctor-{dmax}-{mmax}", p, base, "slidingConstructor", [dmax, mmax],
                  lambda d=dmax, m=mmax: ref.SlidingWindow(d, m, p, base))
        for suffix, dmax, mmax, initial, offset, size in [
            ("decoded", 4, 3, "6162", 3, 1), ("window", 4, 3, "6162636465666768", 5, 1),
            ("zero", 4, 3, "6162", 0, 1)]:
            def do_copy(d=dmax, m=mmax, data=initial, off=offset, size=size):
                w = ref.SlidingWindow(d, m, p, base)
                w.append_bytes(bytes.fromhex(data))
                w.append_copy(off, size)
            error(f"{label}-sliding-copy-{suffix}", p, base, "slidingCopy", [dmax, mmax, initial, f"{offset}:{size}"], do_copy)

    # Non-Mersenne moduli are accepted by Python; folding is not mathematical %.
    for p, base in [(12, 5), (15, 3), (17, 2), (1000, 131)]:
        h = ref.PolynomialHash(p, base)
        for i, a in enumerate([-1000, -1, 0, 1, 12, 17, 100, 2**300 + 17]):
            emit("mod", f"nonmersenne-{p}-mod-{i}", p, base, a, ref.mersenne_mod(a, p))
            emit("mul", f"nonmersenne-{p}-mul-{i}", p, base, a, 37, ref.mersenne_mul(a, 37, p))
        for data in [b"", b"abc", bytes(range(256))]:
            emit("hash", f"nonmersenne-{p}-hash-{len(data)}", p, base, data.hex(), h.hash(data))
        for exponent in [0, 1, 2, 37, 2**64]:
            emit("power", f"nonmersenne-{p}-power-{exponent}", p, base, exponent, h.power(exponent))
    error("noninvertible-power", 15, 3, "power", [-1], lambda: ref.PolynomialHash(15, 3).power(-1))
    # Zero/negative moduli can fail to terminate in Python's folding loop and
    # therefore are deliberately never executed by this generator.

    output.mkdir(parents=True, exist_ok=True)
    def write_tsv(path, records):
        content = "# hashrope 0.2.2; schema=1; see SCHEMA.md; UTF-8; decimal integers; hex bytes\n"
        for row in records:
            fields = [str(field) for field in row]
            if any("\t" in value or "\n" in value or "\r" in value for value in fields):
                raise ValueError("Unescaped TSV field")
            content += "\t".join(fields) + "\n"
        path.write_text(content, encoding="utf-8", newline="\n")
    write_tsv(output / "golden.tsv", rows)
    write_tsv(output / "errors.tsv", errors)
    with zipfile.ZipFile(wheel) as archive:
        sources = {name: sha256(archive.read(name)) for name in sorted(archive.namelist())
                   if name.startswith("hashrope/") and name.endswith(".py")}
    manifest = {"schema_version": 1, "python_release": VERSION, "wheel_filename": wheel.name,
                "wheel_url": WHEEL_URL, "wheel_sha256": WHEEL_SHA256, "sdist_sha256": SDIST_SHA256,
                "registry": "https://pypi.org/pypi/hashrope/json", "random_seed": SEED,
                "generator": "tools/generate_fixtures.py", "source_sha256": sources,
                "record_counts": dict(sorted(counts.items())), "total_records": sum(counts.values()),
                "fixture_sha256": {name: sha256((output / name).read_bytes()) for name in ["golden.tsv", "errors.tsv"]}}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--wheel", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "fixtures")
    args = parser.parse_args()
    manifest = generate(args.wheel, args.output)
    print(json.dumps({"release": VERSION, "records": manifest["total_records"], "output": str(args.output)}, sort_keys=True))


if __name__ == "__main__":
    main()
