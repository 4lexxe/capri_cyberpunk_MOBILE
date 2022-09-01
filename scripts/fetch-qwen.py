"""Download or verify the exact release weights. Python standard library only."""
import argparse
import hashlib
import json
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def valid(path, model):
    if not path.is_file() or path.stat().st_size != model["bytes"]:
        return False
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest() == model["sha256"]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "models/qwen")
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    lock = json.loads((ROOT / "models.lock.json").read_text(encoding="utf-8"))
    args.output.mkdir(parents=True, exist_ok=True)
    for model in lock["files"]:
        target = args.output / model["name"]
        if valid(target, model):
            print(f"Verified: {target.name}")
            continue
        if args.verify_only:
            raise SystemExit(f"Missing or invalid model: {target.name}")
        url = f'https://huggingface.co/{lock["repository"]}/resolve/{lock["revision"]}/{model["name"]}'
        partial = target.with_suffix(".gguf.part")
        print(f"Downloading: {target.name}", flush=True)
        try:
            with urllib.request.urlopen(url, timeout=120) as response, partial.open("wb") as output:
                size = 0
                while chunk := response.read(1024 * 1024):
                    size += len(chunk)
                    if size > model["bytes"]:
                        raise ValueError("Model exceeds locked size")
                    output.write(chunk)
            if not valid(partial, model):
                raise ValueError(f"SHA-256 mismatch: {target.name}")
            partial.replace(target)
        finally:
            partial.unlink(missing_ok=True)
        print(f"Verified: {target.name}")

if __name__ == "__main__":
    main()
