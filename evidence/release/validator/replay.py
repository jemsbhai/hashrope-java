"""Replay bundle-verifier TDD snapshots in temporary directories."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

root=Path(__file__).resolve().parent
manifest=json.loads((root/'manifest.json').read_text(encoding='utf-8'))
results=[]
for snapshot in manifest['snapshots']:
    source=root/snapshot['name']
    for relative,expected in snapshot['sha256'].items():
        assert hashlib.sha256((source/relative).read_bytes()).hexdigest()==expected,relative
    with tempfile.TemporaryDirectory(prefix='hashrope-validator-replay-') as temporary:
        target=Path(temporary)
        for directory in ('tests','tools'):
            if (source/directory).exists():
                shutil.copytree(source/directory,target/directory)
        process=subprocess.run([sys.executable,'-B','-m','unittest','discover','-s','tests','-p','test_verify_central_bundle.py','-v'],cwd=target,capture_output=True,text=True)
        combined=process.stdout+process.stderr
        matched=process.returncode==snapshot['exit_code'] and snapshot['expected_marker'] in combined
        results.append(dict(snapshot=snapshot['name'],expected=snapshot['exit_code'],actual=process.returncode,matched=matched))
        if not matched:
            print(combined)
            raise SystemExit('Snapshot replay failed: '+snapshot['name'])
print(json.dumps(dict(snapshots=results,matched=True),indent=2))
