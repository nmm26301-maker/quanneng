#!/usr/bin/env python3
"""Build the isolated Android instrumentation APK with the app's test signing key."""
import argparse,os,pathlib,shutil,subprocess,zipfile
p=argparse.ArgumentParser();p.add_argument('--sdk',required=True);p.add_argument('--keystore',required=True);p.add_argument('--out',required=True);p.add_argument('--compiler-jar');a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[2];sdk=pathlib.Path(a.sdk).resolve();tools=sdk/'android-15';android=sdk/'android-35/android.jar';build=root/'android/build/smoke'
if build.exists():shutil.rmtree(build)
for name in ['classes','dex']:(build/name).mkdir(parents=True)
def visible_files(base,pattern):
 return sorted(p for p in base.rglob(pattern) if not any(part.startswith('.') for part in p.relative_to(base).parts))
def run(*cmd,**kwargs):subprocess.run([str(x) for x in cmd],check=True,**kwargs)
run(tools/'aapt2','link','-o',build/'base.apk','-I',android,'--manifest',root/'android/tests/AndroidManifest.xml','-A',root/'android/tests/assets')
compiler=['java','-jar',a.compiler_jar] if a.compiler_jar else ['javac']
run(*compiler,'-encoding','UTF-8','-source','8','-target','8','-classpath',android,'-d',build/'classes',*visible_files(root/'android/tests/java','*.java'))
run(tools/'d8','--lib',android,'--min-api','26','--output',build/'dex',*visible_files(build/'classes','*.class'))
with zipfile.ZipFile(build/'base.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
 for file in (build/'dex').glob('*.dex'):z.write(file,file.name)
run(tools/'zipalign','-f','4',build/'base.apk',build/'aligned.apk')
env=os.environ.copy();env.setdefault('QUANNENG_STORE_PASSWORD','android')
run(tools/'apksigner','sign','--ks',pathlib.Path(a.keystore).resolve(),'--ks-key-alias','quanneng','--ks-pass','env:QUANNENG_STORE_PASSWORD','--key-pass','env:QUANNENG_STORE_PASSWORD','--out',pathlib.Path(a.out).resolve(),build/'aligned.apk',env=env)
print('Instrumentation APK built')
