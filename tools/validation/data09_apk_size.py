"""Measure an APK and its database assets; never authorize a release."""
import argparse
import hashlib
import json
import sys
import sqlite3
import tempfile
from contextlib import closing
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/catalog"))
import package_qa

LIMIT_BYTES = 25_000_000  # Conservative decimal MB interpretation; report MiB separately.


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def inspect_apk(path, variant, asset_profile):
    path = Path(path)
    # Reuse the project's exact asset-set, checksum and catalog QA checks.
    verification = package_qa.verify_apk(path, fixture=asset_profile == "fixture")
    size = path.stat().st_size
    with ZipFile(path) as archive:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        if len(names) != len(set(names)):
            raise ValueError("Duplicate APK ZIP entries")
        if any("catalog-5000" in name or name.startswith("benchmark/") for name in names):
            raise ValueError("Synthetic benchmark fixture leaked into application APK")
        assets = []
        for entry in entries:
            if entry.filename.startswith("assets/database/") and not entry.is_dir():
                assets.append({"path": entry.filename, "compressedBytes": entry.compress_size,
                               "uncompressedBytes": entry.file_size,
                               "sha256": hashlib.sha256(archive.read(entry)).hexdigest()})
        with tempfile.TemporaryDirectory(prefix="data09-catalog-") as directory:
            catalog = Path(directory) / "catalog.db"
            catalog.write_bytes(archive.read("assets/database/catalog.db"))
            with closing(sqlite3.connect(catalog.as_uri() + "?mode=ro", uri=True)) as db:
                dataset = {"uniqueWords": db.execute("SELECT COUNT(DISTINCT id) FROM VocabularyEntry").fetchone()[0],
                           "packs": db.execute("SELECT COUNT(*) FROM LearningPack").fetchone()[0],
                           "catalogSha256": sha256(catalog)}
        abis = sorted({name.split("/")[1] for name in names
                       if name.startswith("lib/") and name.endswith(".so") and len(name.split("/")) == 3})
    return {"schemaVersion": 1, "metric": "apk-size", "variant": variant,
            "assetProfile": asset_profile, "dataset": dataset, "apkSha256": sha256(path),
            "apkBytes": size, "apkMB": size / 1_000_000, "apkMiB": size / 1_048_576,
            "limitBytes": LIMIT_BYTES, "nativeAbis": abis, "databaseAssets": assets,
            "compressedDatabaseShare": sum(x["compressedBytes"] for x in assets) / size,
            "assetVerification": verification,
            "observedGate": "PASS" if size <= LIMIT_BYTES else "FAIL",
            "srsGate": "PENDING",
            "reason": "Requires measurement of the final release artifact; fixture/internal QA is diagnostic.",
            "releaseAuthorized": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--variant", required=True)
    parser.add_argument("--asset-profile", choices=("fixture", "internal-qa"), required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.output.exists():
        parser.error("Output already exists; retain evidence and choose a new path")
    try:
        report = inspect_apk(args.apk, args.variant, args.asset_profile)
    except Exception as error:
        report = {"schemaVersion": 1, "metric": "apk-size", "observedGate": "PENDING",
                  "srsGate": "PENDING", "error": str(error), "releaseAuthorized": False}
        code = 1
    else:
        code = int(report["observedGate"] == "FAIL")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
