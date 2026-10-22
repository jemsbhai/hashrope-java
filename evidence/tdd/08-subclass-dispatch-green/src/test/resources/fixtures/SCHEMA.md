# Python 0.2.2 golden fixture schema

`golden.tsv` and `errors.tsv` are UTF-8, LF-terminated TSV. Ignore comment lines beginning with `#`. Split with preservation of empty columns (Java `line.split("\t", -1)`). All integers are signed decimal and require `BigInteger`; bytes use lowercase hex, with the empty field representing empty bytes. Every line begins with these zero-based columns:

| Index | Value |
|---|---|
| 0 | record kind |
| 1 | unique case identifier |
| 2 | hash prime |
| 3 | hash base |

The remaining columns are:

| Kind | Columns starting at index 4 |
|---|---|
| `hash` | data hex, expected hash |
| `power` | exponent, expected result |
| `phi` | q, alpha, expected result |
| `mod` | a, expected result |
| `mul` | a, b, expected result |
| `concatHash` | hA, lenB, hB, expected result |
| `repeatHash` | hS, d, q, expected result |
| `overlapHash` | hP, d, length, hPrefix, expected result |
| `rope` | rope spec, expected length, weight, hash, height, bytes |
| `split` | rope spec, position, left length, left weight, left hash, left height, left bytes, right length, right weight, right hash, right height, right bytes |
| `substr` | rope spec, start, length, expected hash |
| `sliding` | see indexed table below |
| `error` | operation, arg1, arg2, arg3, arg4, Python exception class, message |

Rope metadata uses weight 0 and height 0 for empty/null. The bytes field is `-` when logical length exceeds 65,536: do not materialize those ropes. All metadata remains exact. Split results have independent materialization limits.

Rope specs are a postfix stack language with comma-separated tokens:

| Token | Operation |
|---|---|
| `E` | push empty/null |
| `L<hex>` | push `Leaf(bytes.fromhex(hex), h)` |
| `C` | pop right, pop left, push `rope_concat(left, right, h)` |
| `I` | pop right, pop left, push direct `Internal(left, right, h)` |
| `R<count>` | pop child, push `rope_repeat(child, count, h)` |
| `N<count>` | pop child, push direct `RepeatNode(child, count, h)` |

There must be exactly one final stack entry. Example `L61,L6263,C,R17` represents `(a + bc)` repeated 17 times. `I` deliberately permits constructing unbalanced trees for validator tests.

Sliding records must be processed in file order. Maintain one window per trace ID; `init` creates it. Every operation is followed by an expected-state record:

| Index | Value |
|---|---|
| 4 | trace ID |
| 5 | step (0 = initialization) |
| 6 | d_max |
| 7 | m_max |
| 8 | operation: `init`, `bytes`, or `copy` |
| 9 | arg1: byte hex for `bytes`, decimal offset for `copy`, empty for init |
| 10 | arg2: decimal length for copy, otherwise empty |
| 11 | expected total position |
| 12 | expected prefix length |
| 13 | expected prefix hash |
| 14 | expected window length |
| 15 | expected window hash |
| 16 | expected window bytes hex |
| 17 | expected current_hash |
| 18 | expected final_hash |
| 19 | expected window tree height |
| 20 | expected window tree weight |

Error records always have eleven fields, with unused arguments empty:

| Operation | Arguments |
|---|---|
| `constructor` | none; use row prime/base |
| `leaf` | bytes hex |
| `repeatNode` | child spec, repetitions |
| `repeat` | child spec, repetitions |
| `internal` | left spec, right spec |
| `power` | exponent |
| `overlapHash` | hash, d, length, prefix hash |
| `validate` | rope spec |
| `slidingConstructor` | d_max, m_max |
| `slidingCopy` | d_max, m_max, initial literal bytes hex, `offset:length` |
| `substr` | rope spec, start, length (reserved if the oracle raises) |

Python error classes require documented Java mappings: `ValueError` → `IllegalArgumentException` (except modular noninvertibility may map to `ArithmeticException`), `AttributeError` on null children → `NullPointerException`, `ZeroDivisionError` → `ArithmeticException`, `AssertionError` → `AssertionError`. Python-specific null/division/inverse exception text is evidence rather than a required Java VM diagnostic; explicit public validation messages can be retained exactly.

The corpus includes four main parameter profiles: default 61-bit/131, 127-bit/257, 31/3, and the 61-bit prime with base p−1. Additional accepted non-Mersenne moduli test the release's exact folding semantics. Negative `phi` counts, negative arithmetic inputs and malformed substring ranges preserve observed release behavior. Nonpositive moduli are not executed because upstream folding can fail to terminate. Sliding negative lengths can make `pos` inconsistent with window contents; those observed behaviors are explicitly marked with `quirks` trace IDs.

Regenerate using the pinned wheel:

```text
python -B tools/generate_fixtures.py --wheel /path/to/hashrope-0.2.2-py3-none-any.whl --output fixtures
```

The generator verifies the wheel SHA-256 before import, imports directly from that wheel, disables bytecode writing, and never imports the original checkout. The seed and all per-file SHA-256 hashes are in `manifest.json`. `SCHEMA.md` is maintained documentation, not a generated file.
