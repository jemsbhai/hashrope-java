# Retained Java API diagnostic

This bounded check compares published 0.2.2 source at `e59124050388fe70097b5a46f9679356677c7fa0` with the 0.3.3 candidate. Both copies were compiled with `--release 17` using the same compiler; source and harness hashes, runtime version, raw samples and per-process medians are in `efficiency/results.json`.

There are six fresh-process pairs, alternating which arm runs first. Each pair uses the same deterministic data, workload order and fixed repetitions, with 128 warmup calls and seven samples per cell. Runtime flags are `-Xms128m -Xmx128m -XX:+UseSerialGC`; affinity is unpinned and frequency/thermal state is not controlled. The exclusive agent CPU slot contained no concurrent builds or tests. Results are a short diagnostic, not a broad speed guarantee.

Values below are medians of the six process medians in microseconds per operation. The last column is the minimum/maximum of the six matched process ratios, current divided by baseline; it is not a confidence interval.

| Workload | 0.2.2 | 0.3.3 | Paired ratio range |
|---|---:|---:|---:|
| Hash 4 KiB | 239.417 | 235.880 | 0.943–1.052 |
| Construct/hash a 4 KiB leaf | 226.580 | 221.114 | 0.909–1.131 |
| Split/rejoin a 4 KiB leaf | 224.031 | 226.789 | 0.988–1.132 |
| Stream 256 bytes in 32 appends | 109.797 | 102.975 | 0.659–1.259 |

Split/rejoin is 1.2% slower by the ratio of these medians, with five of six paired process ratios above one. The other median values are lower, but the spread—especially streaming—does not support a general speedup claim. Even the unchanged raw hash implementation varies across processes. This check did not reveal a large consistent retained-API regression; it cannot exclude smaller overheads, other workloads or runtime-specific effects.

All timed answers contribute to a checked aggregate after each block. The expected answer is that implementation's direct hash of the fixture bytes; independent pinned numerical fixtures establish compatibility separately. Timings include the aggregate BigInteger additions in both arms. Compilation, setup and oracle equality checks are outside each block; operation allocations and GC are included. Constructor costs include defensive copying; split/rejoin includes new slices, hashes and nodes; the stream cell includes constructing a new stream/hasher and all 32 appends.

These workloads do not measure the new exact APIs against an old equivalent, because 0.2.2 had none. Exact-path tests separately cover native byte-range scanning and nonexpansion of giant repeats/shared graphs. No cross-language runtime parity, universal logarithmic comparison bound or unchanged object-memory layout is inferred.

Reproduction from the repository root (use a new output directory):

```text
python tools/bench_release_efficiency.py --output evidence/release-0.3.3/efficiency-new
```
