"""Regression checks for the fixture builder's file lifetime and failed replacement."""
import contextlib
import io
from pathlib import Path
import sqlite3
import tempfile
import unittest
from unittest.mock import patch

import build_fixture


class FixtureBuildTest(unittest.TestCase):
    def run_build(self, output, fail_replace=False):
        connections = []
        connect = sqlite3.connect
        replace = build_fixture.os.replace

        def tracked_connect(*args, **kwargs):
            db = connect(*args, **kwargs)
            connections.append(db)
            return db

        def checked_replace(src, dst):
            # Windows disallows replacing an open SQLite file. Check the same
            # prerequisite on every OS instead of depending on Linux rename rules.
            self.assertTrue(connections)
            for db in connections:
                with self.assertRaises(sqlite3.ProgrammingError):
                    db.execute('SELECT 1')
            if fail_replace:
                raise PermissionError('simulated replacement failure')
            replace(src, dst)

        with patch.object(build_fixture, 'ASSET', output), \
                patch.object(build_fixture.sqlite3, 'connect', tracked_connect), \
                patch.object(build_fixture.os, 'replace', checked_replace), \
                contextlib.redirect_stdout(io.StringIO()):
            build_fixture.build()

    def test_connection_closed_before_replace_and_rebuild_is_repeatable(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'catalog.db'
            self.run_build(output)
            first = output.read_bytes()
            metadata = output.with_suffix('.metadata.json').read_bytes()
            self.run_build(output)
            self.assertEqual(first, output.read_bytes())
            self.assertEqual(metadata, output.with_suffix('.metadata.json').read_bytes())

    def test_failed_replace_preserves_existing_asset_and_cleans_temp(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'catalog.db'
            self.run_build(output)
            first = output.read_bytes()
            metadata = output.with_suffix('.metadata.json').read_bytes()
            with self.assertRaises(PermissionError):
                self.run_build(output, fail_replace=True)
            self.assertEqual(first, output.read_bytes())
            self.assertEqual(metadata, output.with_suffix('.metadata.json').read_bytes())
            self.assertEqual({'catalog.db', 'catalog.metadata.json'},
                             {p.name for p in Path(directory).iterdir()})


if __name__ == '__main__':
    unittest.main()
