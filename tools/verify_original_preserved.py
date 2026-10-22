"""Read-only comparison against this task's original-source preservation baseline."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args=parser.parse_args()
    baseline=json.loads(args.baseline.read_text(encoding="utf-8-sig"))
    environment=dict(os.environ, GIT_OPTIONAL_LOCKS="0")
    result={"repositories": [], "unchanged": True}
    for label in ("original", "nested_repository"):
        saved=baseline[label]
        root=Path(saved["path"])
        mismatches=[]
        def git(*command):
            return subprocess.check_output(["git", "--no-optional-locks", "-C", str(root), *command], env=environment, text=True)
        for relative, expected in saved["files"].items():
            path=root/relative
            digest=hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None
            if digest!=expected["sha256"]:
                mismatches.append(relative)
        gitdir=Path(git("rev-parse", "--absolute-git-dir").strip())
        for relative, expected in saved["git_metadata_hashes"].items():
            path=gitdir/relative
            digest=hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None
            if digest!=expected["sha256"]:
                mismatches.append("git-metadata:"+relative)
        for field, command in (("head", ("rev-parse","HEAD")),("branch",("branch","--show-current"))):
            if git(*command).strip()!=saved[field]:
                mismatches.append(field)
        if git("status", "--porcelain=v1")!=saved["status"]:
            mismatches.append("status")
        row={"repository":label,"path":str(root),"head":saved["head"],
             "file_count":len(saved["files"]),"git_metadata_count":len(saved["git_metadata_hashes"]),
             "mismatches":mismatches,"unchanged":not mismatches}
        result["repositories"].append(row)
        result["unchanged"]=result["unchanged"] and row["unchanged"]
    args.output.write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(result,indent=2))
    if not result["unchanged"]:
        raise SystemExit(1)


if __name__=="__main__":
    main()
