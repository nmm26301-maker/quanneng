#!/usr/bin/env python3
"""Compile an offline Android APK using official SDK build tools and Java 17+."""
import argparse, os, pathlib, shutil, subprocess, zipfile
root=pathlib.Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--sdk',required=True);p.add_argument('--keystore',required=True);p.add_argument('--out',required=True);p.add_argument('--compiler-jar');args=p.parse_args()
sdk=pathlib.Path(args.sdk).resolve();tools=sdk/'android-15';platform=sdk/'android-35'/'android.jar'
for f in [tools/'aapt2',tools/'zipalign',tools/'apksigner',tools/'d8',platform]:
 if not f.is_file():raise SystemExit(f'Missing SDK component: {f}')
web=root/'frontend'/'dist'
if not (web/'index.html').is_file():raise SystemExit('Run npm ci --prefix frontend && npm run build --prefix frontend first')
build=root/'android'/'build';build.mkdir(parents=True,exist_ok=True)
for name in ['classes','dex','generated']:
 directory=build/name
 if directory.exists():shutil.rmtree(directory)
 directory.mkdir()
def run(*cmd):subprocess.run([str(x) for x in cmd],check=True)
source=root/'android'/'app'/'src'/'main'
run(tools/'aapt2','compile','--dir',source/'res','-o',build/'resources.zip')
run(tools/'aapt2','link','-o',build/'base.apk','-I',platform,'--manifest',source/'AndroidManifest.xml','--java',build/'generated','--min-sdk-version','26','--target-sdk-version','35','-A',web,build/'resources.zip')
java=sorted((source/'java').rglob('*.java'))+sorted((build/'generated').rglob('*.java'))
compiler=['java','-jar',args.compiler_jar] if args.compiler_jar else ['javac']
run(*compiler,'-encoding','UTF-8','-source','8','-target','8','-classpath',platform,'-d',build/'classes',*java)
run(tools/'d8','--lib',platform,'--min-api','26','--output',build/'dex',*sorted((build/'classes').rglob('*.class')))
with zipfile.ZipFile(build/'base.apk','a',compression=zipfile.ZIP_DEFLATED) as apk:
 for dex in (build/'dex').glob('*.dex'):apk.write(dex,dex.name)
run(tools/'zipalign','-f','4',build/'base.apk',build/'aligned.apk')
key=pathlib.Path(args.keystore).resolve();key.parent.mkdir(parents=True,exist_ok=True)
password=os.environ.get('QUANNENG_STORE_PASSWORD','android')
if not key.exists():
 run('keytool','-genkeypair','-keystore',key,'-storepass',password,'-keypass',password,'-alias','quanneng','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Quanneng Development, O=Quanneng, C=CN','-noprompt')
 os.chmod(key,0o600)
out=pathlib.Path(args.out).resolve();out.parent.mkdir(parents=True,exist_ok=True)
# Password uses the standard development default unless supplied by the caller.
env=os.environ.copy();env['QUANNENG_STORE_PASSWORD']=password
subprocess.run([str(tools/'apksigner'),'sign','--ks',str(key),'--ks-key-alias','quanneng','--ks-pass','env:QUANNENG_STORE_PASSWORD','--key-pass','env:QUANNENG_STORE_PASSWORD','--out',str(out),str(build/'aligned.apk')],check=True,env=env)
run(tools/'apksigner','verify','--verbose',out)
run(tools/'aapt2','dump','badging',out)
print(f'APK ready: {out}')
