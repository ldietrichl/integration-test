"""Compile the selected REST suite with pinned, verified libraries into a fresh directory."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import uuid

def resolve_libraries(lock, roots):
    libraries = []
    identities = set()
    for entry in lock['libraries']:
        identity = (entry['group'], entry['artifact'])
        if identity in identities:
            raise ValueError(f'Duplicate artifact in lock: {identity}')
        identities.add(identity)
        root = roots[entry['source']].resolve()
        candidate = (root / entry['path']).resolve()
        if not candidate.is_relative_to(root):
            raise ValueError('Library path escapes configured root')
        if not candidate.is_file():
            raise FileNotFoundError(f'Missing locked library: {candidate}; supply this version, do not substitute another cached JAR')
        if hashlib.sha256(candidate.read_bytes()).hexdigest() != entry['sha256']:
            raise ValueError(f'Checksum mismatch: {candidate}')
        libraries.append(candidate)
    return libraries

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--work', type=Path, required=True)
    parser.add_argument('--libs', type=Path, required=True)
    parser.add_argument('--cache', type=Path, default=Path.home()/'.gradle/caches/modules-2/files-2.1')
    parser.add_argument('--maven-cache', type=Path, default=Path.home()/'.m2/repository')
    parser.add_argument('--javac', default='javac')
    args = parser.parse_args()
    directory = Path(__file__).resolve().parent
    repo = directory.parents[1]
    lock_file = directory/'test-libraries.lock.json'
    libraries = resolve_libraries(json.loads(lock_file.read_text(encoding='utf-8')),
                                  {'bundle': args.libs, 'cache': args.cache, 'maven': args.maven_cache})
    build = args.work.resolve()/'test-builds'/uuid.uuid4().hex
    classes, resources = build/'classes', build/'resources'
    classes.mkdir(parents=True)
    resources.mkdir()
    (resources/'test.properties').write_text('env=local\n', encoding='utf-8')
    classpath = os.pathsep.join(p.as_posix() for p in libraries)
    lombok = next(p for p in libraries if p.name.startswith('lombok-'))
    # Explicit source roots let Lombok process all participating DTOs in one round.
    # Implicit javac source discovery skips annotation processing on dependent DTOs.
    selection_file = directory/'test-sources.json'
    selection = json.loads(selection_file.read_text(encoding='utf-8'))
    main = repo/'src/main/java'
    sources = sorted({f for root in selection['mainRoots'] for f in (main/root).rglob('*.java')})
    sources += [main/f for f in selection['mainFiles']]
    sources += [repo/'src/test/java'/f for f in selection['testFiles']]
    sources += [directory/'LocalScenarioRunner.java']
    options = ['--release','17','-encoding','UTF-8','-cp',classpath,'-processorpath',lombok.as_posix(),
               '-d',classes.as_posix()]
    options += [p.as_posix() for p in sources]
    argfile = build/'compile.args'
    argfile.write_text('\n'.join('"'+arg+'"' for arg in options), encoding='utf-8')
    with (args.work/'compile-tests.log').open('w',encoding='utf-8') as log:
        result = subprocess.run([args.javac,'@'+str(argfile)],stdout=log,stderr=subprocess.STDOUT)
    if result.returncode:
        raise SystemExit(f'Compilation failed; see {args.work / "compile-tests.log"}')
    runtime = os.pathsep.join([classes.as_posix(),resources.as_posix(),classpath])
    (args.work/'test-classpath.txt').write_text(runtime,encoding='utf-8')
    (args.work/'test-libraries.txt').write_text('\n'.join(p.as_posix() for p in libraries),encoding='utf-8')
    manifest = {'lockSha256':hashlib.sha256(lock_file.read_bytes()).hexdigest(), 'libraryCount':len(libraries),
                'sourceSelectionSha256':hashlib.sha256(selection_file.read_bytes()).hexdigest(),
                'sources':{p.relative_to(repo).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
                'classesDirectory':str(classes), 'classCount':len(list(classes.rglob('*.class'))),
                'javac':subprocess.check_output([args.javac,'-version'],stderr=subprocess.STDOUT,text=True).strip()}
    (args.work/'test-build.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print(f'Compiled {len(sources)} selected sources with {len(libraries)} locked libraries')

if __name__ == '__main__':
    main()
