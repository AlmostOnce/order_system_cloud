"""Exercise legacy URL parsing without touching databases."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('migration', Path(__file__).with_name('migrate-image-references.py'))
migration = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migration)

class ReferenceMigrationTest(unittest.TestCase):
    def test_extracts_only_managed_content_routes(self):
        key = '0123456789abcdef0123456789abcdef'
        for prefix in ('', 'http://localhost:9090', 'https://example.test'):
            self.assertEqual(key, migration.extract_key(prefix + '/api/files/' + key.upper() + '/content'))

    def test_empty_images_use_default(self):
        self.assertIsNone(migration.extract_key(None))
        self.assertIsNone(migration.extract_key(''))

    def test_unknown_urls_are_never_silently_discarded(self):
        for value in ('https://example.test/photo.jpg', '/static/unknown.png', 'javascript:alert(1)',
                      '/api/files/' + 'a' * 32 + '/content?download=1', '/api/files/invalid/content'):
            with self.assertRaises(ValueError):
                migration.extract_key(value)

unittest.main()
