"""Fail closed for upgrade builds; development key creation must be explicit."""
import hashlib
import os
import pathlib
import subprocess

# Independently extracted from both released v0.3.0 and v0.3.1 APKs.
RELEASE_CERT_SHA256 = 'abe1a59776d2744f969ca727871a2aa805e4dd1ab7819fec404fdc0d28c7c388'


def certificate_sha256(key, env):
    result = subprocess.run([
        'keytool', '-exportcert', '-keystore', str(key), '-alias', 'quanneng',
        '-storepass:env', 'QUANNENG_STORE_PASSWORD',
    ], env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode or not result.stdout:
        raise ValueError('Cannot read signing certificate; check keystore, password and alias.')
    return hashlib.sha256(result.stdout).hexdigest()


def prepare_signing(path, development=False):
    key = pathlib.Path(path).resolve()
    env = os.environ.copy()
    env.setdefault('QUANNENG_STORE_PASSWORD', 'android')
    if not key.is_file():
        if not development:
            raise ValueError('Original release keystore is missing. Refusing to create a replacement key.')
        key.parent.mkdir(parents=True, exist_ok=True)
        result = subprocess.run([
            'keytool', '-genkeypair', '-keystore', str(key),
            '-storepass:env', 'QUANNENG_STORE_PASSWORD',
            '-keypass:env', 'QUANNENG_STORE_PASSWORD', '-alias', 'quanneng',
            '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
            '-dname', 'CN=Quanneng Development, O=Quanneng, C=CN', '-noprompt',
        ], env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode:
            raise ValueError('Unable to create explicitly requested development keystore.')
        os.chmod(key, 0o600)
    fingerprint = certificate_sha256(key, env)
    if not development and fingerprint != RELEASE_CERT_SHA256:
        raise ValueError('Signing certificate differs from v0.3.0/v0.3.1. Refusing incompatible upgrade build.')
    return key, env, fingerprint
