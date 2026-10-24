"""Compare retained Java APIs with frozen 0.2.2 sources; no publication or network."""
import argparse
import hashlib
import json
import statistics
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASELINE = 'e59124050388fe70097b5a46f9679356677c7fa0'


def run(args):
    return subprocess.check_output(args, cwd=ROOT, text=True, encoding='utf-8', errors='replace', stderr=subprocess.STDOUT)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if output.exists():
        parser.error('output already exists; choose a fresh path')
    output.mkdir(parents=True)
    baseline_ref = run(['git', 'rev-parse', BASELINE]).strip()
    sources = sorted((ROOT / 'java/src/main/java/hashrope').glob('*.java'))
    names = run(['git', 'ls-tree', '-r', '--name-only', baseline_ref, 'java/src/main/java']).splitlines()
    provenance = {'baseline_ref': baseline_ref, 'java': run(['java', '-version']),
                  'harness_sha256': digest((ROOT / 'tools/ReleaseEfficiency.java').read_bytes()),
                  'driver_sha256': digest(Path(__file__).read_bytes()), 'sources': {},
                  'jvm_flags': ['-Xms128m', '-Xmx128m', '-XX:+UseSerialGC'],
                  'note': 'Six alternating fresh-process pairs, same fixed repetitions, seven timed samples; medians and current/baseline ratios. No exact-API speed claim.'}
    for arm in ['baseline', 'current']:
        folder = output / arm
        folder.mkdir()
        package = folder / 'hashrope'
        package.mkdir()
        selected = names if arm == 'baseline' else [str(p.relative_to(ROOT)).replace('\\', '/') for p in sources]
        arm_hashes = {}
        for name in selected:
            data = subprocess.check_output(['git', 'show', baseline_ref + ':' + name], cwd=ROOT) if arm == 'baseline' else (ROOT / name).read_bytes()
            path = package / Path(name).name
            path.write_bytes(data)
            arm_hashes[name] = digest(data)
        provenance['sources'][arm] = arm_hashes
        run(['javac', '--release', '17', '-d', str(folder), *map(str, sorted(package.glob('*.java'))), str(ROOT / 'tools/ReleaseEfficiency.java')])
    workers = []
    for pair in range(6):
        for arm in (['baseline', 'current'] if pair % 2 == 0 else ['current', 'baseline']):
            raw = run(['java', *provenance['jvm_flags'], '-cp', str(output / arm), 'ReleaseEfficiency', str(pair % 4)])
            raw_path = output / f'pair{pair}-{arm}.tsv'
            raw_path.write_text(raw, encoding='utf-8', newline='\n')
            samples = {}
            for line in raw.splitlines():
                name, index, repetitions, elapsed = line.split('\t')
                samples.setdefault(name, []).append(int(elapsed) / int(repetitions) / 1000)
            assert len(samples) == 4 and all(len(values) == 7 for values in samples.values())
            workers.append({'pair': pair, 'arm': arm, 'file': raw_path.name,
                            'sha256': digest(raw_path.read_bytes()),
                            'median_us': {name: statistics.median(values) for name, values in samples.items()}})
    summary = {}
    for name in workers[0]['median_us']:
        baseline = [w['median_us'][name] for w in workers if w['arm'] == 'baseline']
        current = [w['median_us'][name] for w in workers if w['arm'] == 'current']
        summary[name] = {'baseline_median_us': statistics.median(baseline),
                         'current_median_us': statistics.median(current),
                         'paired_current_over_baseline': [b / a for a, b in zip(baseline, current)]}
    (output / 'results.json').write_text(json.dumps({'provenance': provenance, 'workers': workers, 'summary': summary}, indent=2), encoding='utf-8')
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    main()
