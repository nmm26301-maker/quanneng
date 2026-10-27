#!/usr/bin/env python3
"""Recover the release signing ZIP; the password must remain outside git."""
import argparse
import base64
import getpass
import hashlib
import json
import os
from pathlib import Path

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.scrypt import Scrypt

FORMAT = "quanneng-next-signing-backup-v1"
BACKUP_SHA256 = "03195ef3dc609cb75dc0682e3f88ba8ef173fe9dda7aa0e1645fee2f730950bf"
DEFAULT_BACKUP = Path(__file__).resolve().parents[2] / "signing/QuannengNext-release-signing-backup.encrypted.json"


def decrypt_backup(envelope, password):
    if envelope.get("format") != FORMAT:
        raise ValueError("Unsupported backup format")
    salt = base64.b64decode(envelope["salt"], validate=True)
    nonce = base64.b64decode(envelope["nonce"], validate=True)
    ciphertext = base64.b64decode(envelope["ciphertext"], validate=True)
    if len(salt) != 16 or len(nonce) != 12:
        raise ValueError("Invalid backup parameters")
    key = Scrypt(salt=salt, length=32, n=2**17, r=8, p=1).derive(password.encode("utf-8"))
    plaintext = AESGCM(key).decrypt(nonce, ciphertext, FORMAT.encode("ascii"))
    if hashlib.sha256(plaintext).hexdigest() != BACKUP_SHA256:
        raise ValueError("Recovered ZIP checksum mismatch")
    return plaintext


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backup", type=Path, default=DEFAULT_BACKUP)
    parser.add_argument("--password-file", type=Path, help="Private recovery-password JSON; never commit it")
    parser.add_argument("--out", type=Path, required=True, help="New private ZIP path; existing files are never replaced")
    args = parser.parse_args()
    password = (json.loads(args.password_file.read_text(encoding="utf-8"))["recovery_password"]
                if args.password_file else getpass.getpass("Recovery password: "))
    try:
        data = decrypt_backup(json.loads(args.backup.read_text(encoding="utf-8")), password)
    except (InvalidTag, ValueError, KeyError):
        parser.exit(1, "Recovery failed: incorrect password or damaged backup.\n")
    # Exclusive creation and 0600 prevent overwriting or exposing private keys.
    fd = os.open(args.out, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "wb") as target:
        target.write(data)
    print("Signing ZIP recovered. Extract into a private directory and use build-release.py.")


if __name__ == "__main__":
    main()
