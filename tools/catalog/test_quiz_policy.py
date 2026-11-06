import copy
import json
from pathlib import Path
import unittest
import build_quiz_policy as policy

ROOT = Path(__file__).resolve().parents[2]
ACCEPTED = ROOT / 'docs/data/voc-data-04/accepted-v1'


class QuizPolicyExportTests(unittest.TestCase):
    def test_accepted_and_fixture_companions_match_checked_in_files(self):
        for canonical, source, output in (
            (ACCEPTED / 'canonical-preview.json', ACCEPTED / 'quiz-policy.json',
             ROOT / 'app/src/test/resources/quiz/accepted-policy.json'),
            (ROOT / 'docs/data/vocabulary-contract-v1/fixtures/valid/catalog.json', None,
             ROOT / 'app/src/main/assets/database/quiz-policy.json'),
        ):
            data = json.loads(canonical.read_text(encoding='utf-8'))
            restrictions = json.loads(source.read_text(encoding='utf-8')) if source else None
            self.assertEqual(policy.companion(data, restrictions), json.loads(output.read_text(encoding='utf-8')))

    def test_real_content_cannot_lose_policy_or_use_a_different_dataset(self):
        data = json.loads((ACCEPTED / 'canonical-preview.json').read_text(encoding='utf-8'))
        source = json.loads((ACCEPTED / 'quiz-policy.json').read_text(encoding='utf-8'))
        with self.assertRaises(ValueError):
            policy.companion(data)
        changed = copy.deepcopy(data)
        changed['entries'][0]['meaningVi'] += ' changed'
        with self.assertRaises(ValueError):
            policy.companion(changed, source)
        source['forbiddenDistractorPairs'][0][0] = '0000000000000000'
        with self.assertRaises(ValueError):
            policy.companion(data, source)

    def test_projection_binds_all_quiz_fields_but_not_input_order(self):
        data = json.loads((ACCEPTED / 'canonical-preview.json').read_text(encoding='utf-8'))
        entries = data['entries']
        expected = policy.projection_digest(entries)
        self.assertEqual(expected, policy.projection_digest(list(reversed(entries))))
        for field in ('id', 'lemma', 'pos', 'meaningVi', 'exampleEn'):
            changed = copy.deepcopy(entries)
            changed[0][field] += 'x'
            self.assertNotEqual(expected, policy.projection_digest(changed))


if __name__ == '__main__':
    unittest.main()
