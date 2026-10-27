"""Exercise real keytool certificates and the upgrade signing preflight."""
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'android/tools'))
import signing


class SigningTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.key = pathlib.Path(cls.temp.name) / 'development.p12'
        with mock.patch.dict(os.environ, {'QUANNENG_STORE_PASSWORD': 'android'}):
            _, cls.env, cls.fingerprint = signing.prepare_signing(cls.key, development=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_missing_release_key_is_not_created(self):
        missing = pathlib.Path(self.temp.name) / 'missing' / 'release.p12'
        with self.assertRaisesRegex(ValueError, 'missing'):
            signing.prepare_signing(missing)
        self.assertFalse(missing.parent.exists())

    def test_different_certificate_is_rejected_without_changing_key(self):
        before = self.key.read_bytes()
        with self.assertRaisesRegex(ValueError, 'differs'):
            signing.prepare_signing(self.key)
        self.assertEqual(before, self.key.read_bytes())

    def test_same_pinned_certificate_is_accepted_without_regeneration(self):
        before = self.key.read_bytes()
        with mock.patch.object(signing, 'RELEASE_CERT_SHA256', self.fingerprint):
            key, _, fingerprint = signing.prepare_signing(self.key)
        self.assertEqual(key, self.key)
        self.assertEqual(fingerprint, self.fingerprint)
        self.assertEqual(before, key.read_bytes())

    def test_release_cli_stops_before_sdk_or_output_creation(self):
        output = pathlib.Path(self.temp.name) / 'release.apk'
        result = subprocess.run([
            sys.executable, str(ROOT / 'android/tools/build.py'),
            '--sdk', str(pathlib.Path(self.temp.name) / 'missing-sdk'),
            '--keystore', str(self.key), '--out', str(output),
        ], capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Signing certificate differs', result.stdout + result.stderr)
        self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main()
