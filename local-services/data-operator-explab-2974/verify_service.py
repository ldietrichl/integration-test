"""Validate the immutable SUT and its local dependency versions before reuse."""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--work', type=Path, required=True)
p.add_argument('--ignite-version', required=True)
args = p.parse_args()
manifest = json.loads((args.work / 'source-manifest.json').read_text(encoding='utf-8'))
source = args.work / 'service'
actual = {f.relative_to(source).as_posix(): hashlib.sha256(f.read_bytes()).hexdigest()
          for f in (source / 'src/main/java').rglob('*.java')}
if actual != manifest['java_sha256']:
    raise SystemExit('Service Java files differ from the archived source manifest')
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
pom = ET.parse(source / 'pom.xml')
deps = {d.findtext('m:artifactId', namespaces=ns): d.findtext('m:version', namespaces=ns)
        for d in pom.findall('m:dependencies/m:dependency', ns)
        if d.findtext('m:groupId', namespaces=ns) == 'org.apache.ignite'}
if deps != {'ignite-core': args.ignite_version, 'ignite-indexing': args.ignite_version}:
    raise SystemExit('Service dependencies do not match the selected stand profile')
print(f'Verified {len(actual)} unchanged service Java files; Ignite {args.ignite_version}')
