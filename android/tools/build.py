#!/usr/bin/env python3
"""Compile an offline Android APK using official SDK build tools and Java 17+."""
import argparse, os, pathlib, shutil, subprocess, zipfile, xml.etree.ElementTree as ET
from dependencies import prepare
root=pathlib.Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--sdk',required=True);p.add_argument('--keystore',required=True);p.add_argument('--out',required=True);p.add_argument('--compiler-jar');p.add_argument('--abi',choices=['arm64-v8a','x86_64'],default='arm64-v8a');args=p.parse_args()
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
libraries=prepare(sdk,args.abi)
library_resources=[]
library_packages=[]
for library in libraries:
 manifest=library/'AndroidManifest.xml'
 if manifest.is_file():
  namespace=ET.parse(manifest).getroot().get('package')
  if namespace:library_packages.append(namespace)
 if not (library/'res').is_dir():continue
 resource=build/(library.name+'-resources.zip')
 run(tools/'aapt2','compile','--dir',library/'res','-o',resource)
 library_resources.append(resource)
run(tools/'aapt2','compile','--dir',source/'res','-o',build/'resources.zip')
overlay_args=[]
for resource in library_resources+[build/'resources.zip']:overlay_args.extend(['-R',resource])
run(tools/'aapt2','link','--auto-add-overlay','-o',build/'base.apk','-I',platform,'--manifest',source/'AndroidManifest.xml','--java',build/'generated','--extra-packages',':'.join(library_packages),'--min-sdk-version','26','--target-sdk-version','35','-A',web,*overlay_args)
java=sorted((source/'java').rglob('*.java'))+sorted((build/'generated').rglob('*.java'))
compiler=['java','-jar',args.compiler_jar] if args.compiler_jar else ['javac']
classpath=os.pathsep.join(str(x) for x in [platform]+[library/'classes.jar' for library in libraries])
run(*compiler,'-encoding','UTF-8','-source','8','-target','8','-classpath',classpath,'-d',build/'classes',*java)
run(tools/'d8','--lib',platform,'--min-api','26','--output',build/'dex',*sorted((build/'classes').rglob('*.class')),*[library/'classes.jar' for library in libraries])
with zipfile.ZipFile(build/'base.apk','a',compression=zipfile.ZIP_DEFLATED) as apk:
 for dex in (build/'dex').glob('*.dex'):apk.write(dex,dex.name)
 for library in libraries:
  for native in (library/'jni'/args.abi).glob('*.so'):apk.write(native,'lib/'+args.abi+'/'+native.name)
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
