# Reproducible test-first evidence

Features were developed by writing tests, executing and inspecting a failure, implementing the behavior, then rerunning the accumulated tests. Independent review findings followed the same sequence. The evidence retains failures, source/test snapshots, commands and outcomes; red runs are expected historical failures, not outstanding failures in the final implementation.

## Core rope cycles

Each directory under [evidence/tdd](../evidence/tdd/) contains its exact Maven project sources, command, output and exit code.

| Cycle | Observed failure | Resulting behavior |
|---|---|---|
| 01 | Missing Leaf | Nonempty immutable byte leaf, unsigned bytes, equality and ownership |
| 02 | Missing Node/Internal/RepeatNode/Rope operations | Stored metadata, empty identity, structural equality and arbitrary-size repeats |
| 03 | Missing concat/split | Persistent splits, UTF-8 byte boundaries and huge repeat positions |
| 04 API | Missing validator | Explicit structural invariant checks |
| 04 behavior | Weights 3/4 and 1/4 violate BB[2/7] | Checked join/rotations and repeat decomposition |
| 05 | Missing substring hash | Metadata queries, boundary traversal and malformed-range compatibility |
| 06 | Missing release/balance constants | Complete public metadata and Maven arithmetic adapter |
| 07 | Passing refactor | Avoid whole-leaf copies during partial queries; document API |
| 08 | Three failing subclass/ownership regressions | Exact repeat primitive dispatch and no leaf-owned array exposure |

Replay all saved core outcomes from a fresh temporary build tree:

```text
python -B tools/replay_core_tdd.py --repository /path/to/maven-cache
```

Use `--maven /path/to/mvn` if Maven is not on PATH. JAVA_HOME must select a supported JDK. Replay checks that red runs fail for code/test reasons, not missing network dependencies. [The final replay report](../evidence/core-tdd-replay-final.json) records every matched outcome.

## Independent modules and review corrections

- [Arithmetic evidence](../evidence/arithmetic/README.md): 23 standalone javac/java snapshots, four Maven snapshots and two isolated watchdog snapshots. The latter prove the extreme-modulus regression without putting a 128 MiB modulus in the ordinary test suite. The documented initial hand-calculated test typo was corrected against Python and rerun red before implementation.
- [Sliding evidence](../evidence/sliding/README.md): 25 snapshots covering eleven feature red/green pairs and three passing refactors. Includes public mutable state, eviction, copies, error ordering, subclass dispatch and callback evaluation order.
- [Fixture generator evidence](../evidence/fixtures/README.md): generator absence and missing signed-edge coverage were observed red before implementation; deterministic generation and artifact checks pass.
- [Independent review](../evidence/FINAL-REVIEW.md): all four concrete findings were repaired with regressions, including the extreme `2 * bitLength` overflow.
- [Differential evidence](../evidence/differential/TEST-REPORT.md): test sources were authored independently from the implementation and use the pinned wheel as oracle. The initial 10,562-test suite found no ordinary-domain mismatch; subsequent review tests broadened extension coverage.

The main Maven suite runs all 10,544 oracle rows, deterministic property scenarios, arithmetic assertions through a JUnit adapter, and explicit node, range, streaming and extension contracts. The separate consumer resolves only the locally installed artifact. Final outcomes and artifact hashes are recorded in [VERIFICATION.md](VERIFICATION.md).

Historical evidence is intentionally immutable. Some capture commands contain original machine paths; portable replay tools build temporary copies and do not require the original Python checkout. Red snapshots must fail to reproduce the original evidence. No production mutations were applied after implementation merely to manufacture red evidence.
