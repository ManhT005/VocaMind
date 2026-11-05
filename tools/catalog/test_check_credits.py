import unittest
from check_credits import validate


class CreditCheckTest(unittest.TestCase):
    def row(self, id='a', date='date', authors=None):
        return dict(sourceId=id, snapshotDate=date, authors=['Synthetic A'] if authors is None else authors)

    def check(self, rows, release=False):
        return validate(dict(schemaVersion=1, sources=rows), {('a', 'date')}, release)

    def test_release_missing_is_blocked_but_never_approves_license(self):
        self.assertFalse(self.check([self.row()], True)['licenseApprovalVerified'])
        self.assertFalse(self.check([self.row(authors=[])])['releaseCreditComplete'])
        for rows in [[], [self.row(authors=[])]]:
            with self.assertRaises(ValueError):
                self.check(rows, True)

    def test_bad_pair_duplicate_and_fields_are_rejected(self):
        for rows in [[self.row('other')], [self.row(date='stale')], [self.row(), self.row()],
                     [self.row(authors=[' '])], [self.row(authors='author')]]:
            with self.assertRaises(ValueError):
                self.check(rows)


if __name__ == '__main__':
    unittest.main()
