"""Local equivalent of Python/data gates in android-ci.yml; no network or publication."""
from pathlib import Path
import json
import subprocess
import sys
from datetime import datetime

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[2]
rows = []

def run(label, args, expected=0):
    proc = subprocess.run([sys.executable, *args], cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")
    (OUT / (label + ".txt")).write_text(proc.stdout + proc.stderr, encoding="utf-8")
    rows.append(dict(step=label, command=["python", *args], expected=expected, exitCode=proc.returncode))
    if proc.returncode != expected:
        raise RuntimeError(f"{label}: {proc.returncode}, expected {expected}")

def equal(a, b):
    a, b = ROOT / a, ROOT / b
    af = {str(p.relative_to(a)): p.read_bytes() for p in a.rglob('*') if p.is_file()}
    bf = {str(p.relative_to(b)): p.read_bytes() for p in b.rglob('*') if p.is_file()}
    assert af == bf, (str(a), str(b))

def absent(path, names):
    for name in names:
        assert not (ROOT / path / name).exists(), name

def main():
    run("source-gate", ["tools/vocabulary/gate_sources.py", "--snapshot", "docs/data/snapshot.json", "--json"])
    for label, folder, pattern in [("vocabulary-tests", "tools/vocabulary", "test_*.py"), ("catalog-tests", "tools/catalog", "test_*.py"), ("contract-tests", "docs/data/vocabulary-contract-v1", "test_contract.py")]:
        run(label, ["-m", "unittest", "discover", "-s", folder, "-p", pattern, "-v"])
    run("credits", ["tools/catalog/check_credits.py"])
    validator = "docs/data/vocabulary-contract-v1/validate_contract.py"
    run("contract", [validator, "docs/data/vocabulary-contract-v1/fixtures/valid/catalog.json"])
    base = "build/vocabulary-ingestion/search01-ci-" + datetime.now().strftime("%Y%m%d%H%M%S")
    quality = base.replace("vocabulary-ingestion", "vocabulary-quality")
    corpus = base.replace("vocabulary-ingestion", "vocabulary-corpus")
    proof = base + "/proof"
    config = "tools/vocabulary/fixtures/ingestion/verified/config.json"
    run("ingestion", ["tools/vocabulary/verify_ingestion.py", "--fixture", "--config", config, "--output", proof])
    run("ingestion-contract", [validator, proof + "/a/canonical-catalog.json"])
    run("fixture-release-rejected", [validator, proof + "/a/canonical-catalog.json", "--release"], 1)
    for suffix in ("a", "b", "rejected"):
        reviews = "pending-reviews.json" if suffix == "rejected" else "reviews.json"
        run("quality-" + suffix, ["tools/vocabulary/quality.py", "--fixture", "--input", proof + "/a", "--config", config,
             "--reviews", "tools/vocabulary/fixtures/quality/" + reviews, "--output", quality + "/quality-" + suffix], 1 if suffix == "rejected" else 0)
    equal(quality + "/quality-a", quality + "/quality-b")
    absent(quality + "/quality-rejected", ["manifest.json", "canonical-catalog.json", "validated_vocab.jsonl"])
    run("quality-contract", [validator, quality + "/quality-a/canonical-catalog.json"])
    for suffix in ("a", "b"):
        run("corpus-" + suffix, ["tools/vocabulary/prepare_corpus.py", "--require-approved", "--output", corpus + "/corpus-" + suffix], 1)
    equal(corpus + "/corpus-a", corpus + "/corpus-b")
    equal("docs/data/voc-data-04/corpus-v1", corpus + "/corpus-a")
    absent(corpus + "/corpus-a", ["manifest.json", "validated_vocab.jsonl"])
    run("editorial-rejected", ["tools/vocabulary/ingest.py", "--config", "docs/data/voc-data-04/corpus-v1/ingestion-proposal.json", "--output", base + "/rejected"], 1)
    absent(base + "/rejected", ["manifest.json"])
    run("accepted-editorial", ["tools/vocabulary/accept_editorial.py", "--output", corpus + "/accepted"])
    equal("docs/data/voc-data-04/accepted-v1", corpus + "/accepted")
    accepted = "docs/data/voc-data-04/accepted-v1/"
    run("accepted-ingestion", ["tools/vocabulary/ingest.py", "--config", accepted + "ingestion-config.json", "--output", base + "/accepted-ingestion"])
    run("accepted-quality", ["tools/vocabulary/quality.py", "--input", base + "/accepted-ingestion", "--config", accepted + "ingestion-config.json", "--reviews", accepted + "reviews.json", "--output", quality + "/accepted-quality"])
    run("accepted-contract", [validator, quality + "/accepted-quality/canonical-catalog.json", "--release"])
    run("accepted-verify", ["tools/vocabulary/accept_editorial.py", "--verify", quality + "/accepted-quality"])
    run("unicode", ["tools/unicode/generate.py"])

try:
    main()
finally:
    (OUT / "python-results.json").write_text(json.dumps(rows, indent=2), encoding="utf-8")
print(f"{len(rows)} Python steps passed (including expected refusals)")
