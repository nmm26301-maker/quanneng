"""Resolve a reviewed, SHA-256-pinned subset of the GSY/Media3 dependency graph."""
import hashlib,json,pathlib,urllib.request,zipfile,concurrent.futures,shutil

def prepare(sdk):
    lock=pathlib.Path(__file__).resolve().parents[1]/'dependencies.lock.json'
    artifacts=json.loads(lock.read_text())
    cache=pathlib.Path(sdk)/'quanneng-dependencies';cache.mkdir(parents=True,exist_ok=True)
    def fetch(item):
        archive=cache/(item['name']+'.'+item['extension'])
        if not archive.exists():
            with urllib.request.urlopen(item['url'],timeout=60) as response:data=response.read(32*1024*1024)
            if hashlib.sha256(data).hexdigest()!=item['sha256']:raise RuntimeError('Dependency checksum mismatch: '+item['name'])
            archive.write_bytes(data)
        if hashlib.sha256(archive.read_bytes()).hexdigest()!=item['sha256']:raise RuntimeError('Cached dependency checksum mismatch: '+item['name'])
        directory=cache/item['name'];directory.mkdir(exist_ok=True)
        if item['extension']=='jar':shutil.copyfile(archive,directory/'classes.jar')
        else:
            with zipfile.ZipFile(archive) as source:
                for entry in source.namelist():
                    if entry in ['classes.jar','AndroidManifest.xml'] or entry.startswith('res/'):source.extract(entry,directory)
        return directory
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:return list(pool.map(fetch,artifacts))
