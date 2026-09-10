"""Prepare a separate local build; archived Java sources remain byte-for-byte unchanged."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument("--source", type=Path, required=True)
p.add_argument("--work", type=Path, required=True)
p.add_argument("--ignite-version", required=True)
args = p.parse_args()
source, work = args.source.resolve(), args.work.resolve()
if source == work or source in work.parents:
    raise SystemExit("Use a separate work directory outside the reference source")
build = work / "service"
if not build.exists():
    shutil.copytree(source, build)
else:
    raise SystemExit(f"Build directory already exists: {build}")

ns = {"m": "http://maven.apache.org/POM/4.0.0"}
ET.register_namespace("", ns["m"])
tree = ET.parse(build / "pom.xml")
dependencies = tree.find("m:dependencies", ns)
changes = []
for dependency in list(dependencies):
    group = dependency.find("m:groupId", ns)
    artifact = dependency.find("m:artifactId", ns).text
    if group.text == "com.sbt.ignite":
        group.text = "org.apache.ignite"
        dependency.find("m:version", ns).text = args.ignite_version
        changes.append(f"com.sbt.ignite:{artifact}:17.6.0 -> org.apache.ignite:{artifact}:{args.ignite_version}")
    elif group.text == "com.sbt.security.ignite":
        dependencies.remove(dependency)
        changes.append(f"Removed unavailable SE-only {group.text}:{artifact}; local Ignite has no authentication")
tree.write(build / "pom.xml", encoding="utf-8", xml_declaration=True)
manifest = {
    "source": str(source), "java_sources_modified": False,
    "ignite_version": args.ignite_version,
    "local_dependency_changes": changes,
    "java_sha256": {f.relative_to(source).as_posix(): hashlib.sha256(f.read_bytes()).hexdigest()
                    for f in sorted((source / "src/main/java").rglob("*.java"))},
}
(work / "source-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
(work / "maven-settings.xml").write_text('''<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <mirrors><mirror><id>local-public-central</id><mirrorOf>*</mirrorOf>
    <url>https://repo.maven.apache.org/maven2</url>
  </mirror></mirrors>
</settings>''', encoding="utf-8")
print(f"Prepared {build}; {len(manifest['java_sha256'])} unchanged Java files")
for change in changes:
    print(change)
