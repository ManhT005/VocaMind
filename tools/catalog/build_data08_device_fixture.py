"""Build synthetic catalog B for DATA-08's isolated APK replacement test."""

from contextlib import closing
import hashlib
import json
from pathlib import Path
import shutil
import sqlite3
import sys


def build(source, output):
    output.mkdir(parents=True, exist_ok=True)
    for path in source.iterdir():
        shutil.copyfile(path, output / path.name)
    database = output / 'catalog.db'
    with closing(sqlite3.connect(database)) as connection, connection:
        connection.execute("UPDATE LearningPack SET description = 'DATA-08 generation B'")
    metadata = output / 'catalog.metadata.json'
    document = json.loads(metadata.read_text(encoding='utf-8'))
    document['sha256'] = hashlib.sha256(database.read_bytes()).hexdigest()
    metadata.write_text(json.dumps(document, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    build(Path(sys.argv[1]), Path(sys.argv[2]))
