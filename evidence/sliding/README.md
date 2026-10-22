# Sliding window TDD evidence

Every numbered feature cycle was implemented in this order: write tests, execute them, inspect the expected failure,
make the smallest change covering that cycle, and rerun all accumulated sliding tests. Each `*-red`, `*-green`, and
`*-refactor` directory contains the exact production/dependency/test source snapshot, Maven POM, command, complete
console log, exit code, and SHA-256 file manifest. No intentional production mutations were introduced to manufacture
red results. Early missing-symbol failures concern the new sliding API itself; its actual dependencies already compiled.

| Cycle | Observed red | Green tests |
| --- | --- | ---: |
| 01 state | `SlidingWindow` class absent | 3 |
| 02 public state | eight Python-compatible state fields inaccessible | 4 |
| 03 literal appends | `appendBytes` absent | 6 |
| 04 eviction | retained arrays were 256/6 bytes instead of 4/3 | 8 |
| 05 copies | both `appendCopy` overloads absent | 10 |
| 06 overlapping copies | literal source copied once; huge-copy prefix length remained zero | 12 |
| 07 errors | invalid copy bounds did not throw | 14 |
| 08 convenience constructors | required Java overloads absent | 15 |
| 09 subclass dispatch | class unnecessarily final | 16 |
| 10 final refactor | no new feature; signed division helper and Javadocs clarified, dependencies refreshed | 16 |
| 11 memory documentation | no new feature; distinguishes represented retained bytes from total JVM/cache allocation | 16 |
| 12 current hash callback order | length override changed prefix/hasher, but Java had captured old values; expected 42/73, observed 0 | 18 |
| 13 literal append callback order | hash override changed root, but Java had captured the old root; expected two bytes, observed one | 19 |
| 14 callback maintenance comments | no new feature; records why callback evaluation order is intentional | 19 |

All eleven reds exited 1. All eleven greens and three refactor checks exited 0. Final result: 19 JUnit tests passed,
0 failures, 0 errors, 0 skipped. The independent differential suite additionally verified 616 published-Python
sliding snapshots and 720 generated streaming operations across four profiles with no mismatches.

`python-edge-probes.jsonl` was produced by the checked-in `probe_python.py` against the downloaded wheel, with
bytecode writing disabled. It records null-literal and negative/zero-copy behavior before the Java implementation.
`python-callback-probes.jsonl` and `probe_callbacks.py` independently confirm the Python callback-order expectations
for cycles 12 and 13. Those cycles execute frozen source copies to isolate their evidence from concurrent review fixes.

To replay any snapshot, use `replay-sliding-tdd.ps1 -EvidenceRoot <this-directory> -Cycle 04-eviction-red,10-final-refactor`.
Omit `-Cycle` to replay all 25 runs. Override `-Maven`, `-JavaHome`, and `-Repository` for another machine. Replay
copies saved sources to fresh temporary directories and uses empty Maven settings; it does not edit these snapshots,
the current implementation, the original Python repository, or publishing credentials. Red snapshots are expected
to fail their Maven invocation; the replay script succeeds only if every exit code matches its recorded result.

From the final repository, `./evidence/sliding/tools/replay-sliding-tdd.ps1` locates its evidence by script-relative
path. It uses `mvn` from PATH and the current `JAVA_HOME`, with an isolated temporary Maven cache unless overridden.
The historical capture scripts retain their exact original execution paths; the replay script is portable.
