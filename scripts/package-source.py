"""Create a public source archive using an explicit allowlist, excluding private/build files."""
import argparse
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = re.search(r'versionName = "([^"]+)"', (ROOT / "app/build.gradle.kts").read_text()).group(1)
FILES = ["README.md", "LICENSE.md", "NOTICE.md", "CONTRIBUTING.md", "SECURITY.md", "CHANGELOG.md", "models.lock.json", "signing.lineage",
         ".gitignore", ".gitattributes", ".editorconfig", "gradlew", "gradlew.bat", "gradle.properties", "settings.gradle.kts", "build.gradle.kts"]
DIRECTORIES = ["app/src", "app/docs", "gradle", ".github", "scripts", "third_party", "docs"]
DENY = {"build", ".cxx", ".git", ".sisyphus", "__pycache__", "piper", "piper-wip", "sherpa-onnx", "screenshots-v0.5", "screenshots-v0.4", "screenshots-v0.3", "screenshots-v0.2", "screenshots"}
EXTENSIONS = {".gguf", ".p12", ".jks", ".keystore", ".pem", ".key", ".pyc", ".apk", ".aab", ".log", ".bin"}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", default=VERSION, help="Archive label override for development snapshots")
    parser.add_argument("--output", type=Path, default=ROOT / f"artifacts/Capri-Android-{VERSION}-source.zip")
    args = parser.parse_args()
    candidates = [ROOT / name for name in FILES]
    candidates += [ROOT / "app/build.gradle.kts", ROOT / "app/proguard-rules.pro"]
    for directory in DIRECTORIES:
        path = ROOT / directory
        if path.is_dir():
            candidates.extend(p for p in path.rglob("*") if p.is_file() and not p.is_symlink())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    with zipfile.ZipFile(args.output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(set(candidates)):
            if not path.is_file():
                continue
            relative = path.relative_to(ROOT)
            if any(part in DENY for part in relative.parts) or path.suffix.lower() in EXTENSIONS:
                continue
            if path.name in {"local.properties", "signing.properties"} or path.name.startswith(".env"):
                continue
            archive.write(path, f"Capri-Android-{args.version}/{relative.as_posix()}")
            count += 1
    print(f"Source: {args.output.name}; files: {count}; bytes: {args.output.stat().st_size}")

if __name__ == "__main__":
    main()
