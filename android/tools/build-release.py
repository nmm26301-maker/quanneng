#!/usr/bin/env python3
"""Build the new independent release from its privately restored signing backup."""
import argparse
import json
import os
import pathlib
import subprocess
import sys
import xml.etree.ElementTree as ET
from signing import RELEASE_CERT_SHA256

parser=argparse.ArgumentParser()
parser.add_argument('--signing-config',required=True)
args,build_args=parser.parse_known_args()
path=pathlib.Path(args.signing_config).resolve()
config=json.loads(path.read_text())
root=pathlib.Path(__file__).resolve().parents[2]
if config.get('application_id')!='app.quanneng.next' or config.get('certificate_sha256')!=RELEASE_CERT_SHA256 or config.get('alias')!='quanneng':
    raise SystemExit('Signing backup does not match the permanent Quanneng Next release identity.')
if ET.parse(root/'android/app/src/main/AndroidManifest.xml').getroot().get('package')!=config['application_id']:
    raise SystemExit('Manifest application ID does not match the signing backup.')
if any(value=='--keystore' or value.startswith('--keystore=') or value=='--development-signing' for value in build_args):
    raise SystemExit('Release recovery build does not allow overriding its signer or development mode.')
key=path.parent/'quanneng-next-release.p12'
if not key.is_file() or not config.get('store_password'):
    raise SystemExit('Private release key or password is missing; restore the complete backup.')
env=os.environ.copy()
env['QUANNENG_STORE_PASSWORD']=config['store_password']
subprocess.run([sys.executable,str(root/'android/tools/build.py'),'--keystore',str(key),*build_args],env=env,check=True)
