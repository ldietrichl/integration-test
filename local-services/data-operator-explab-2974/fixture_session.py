"""Lifecycle for local D1. Objects enter via REST; only missing source metadata/cleanup use Ignite."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
from urllib.request import Request, urlopen
from urllib.error import HTTPError
import uuid
import zipfile

ROOT = Path(__file__).resolve().parent


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def http(output, base, path, method="GET", body=None, expected=200):
    request = Request(base + path, method=method, headers={"Content-Type": "application/json"},
                      data=None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8"))
    try:
        response = urlopen(request, timeout=20)
    except HTTPError as error:
        response = error
    with response:
        text = response.read().decode("utf-8")
        write(output / ("setup-http-" + uuid.uuid4().hex + ".json"),
              {"method": method, "url": base + path, "request": body, "status": response.status, "response": text})
        if response.status not in ((expected,) if isinstance(expected, int) else expected):
            raise RuntimeError(f"{method} {path}: expected {expected}, received {response.status}; see setup HTTP evidence")
        if not text:
            return None
        return json.loads(text) if response.headers.get_content_type() == "application/json" else text


def build_helper(work, output, javac):
    jar = work / "service/target/data-operator-0.0.1-SNAPSHOT.jar"
    stamp = json.loads((work / "service-build.json").read_text(encoding="utf-8"))
    if hashlib.sha256(jar.read_bytes()).hexdigest().lower() != stamp["jarSha256"].lower():
        raise RuntimeError("Service JAR changed since verified build")
    build = output / "fixture-helper"
    build.mkdir()
    # Use the exact running service JAR, not arbitrary cached dependency versions.
    with zipfile.ZipFile(jar) as archive:
        for item in archive.infolist():
            if item.is_dir() or not item.filename.startswith(("BOOT-INF/lib/", "BOOT-INF/classes/")):
                continue
            target = (build / item.filename).resolve()
            if not target.is_relative_to(build.resolve()):
                raise ValueError("Unsafe service archive path")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(item))
    classpath = os.pathsep.join(str(p) for p in [build, build / "BOOT-INF/classes", *sorted((build / "BOOT-INF/lib").glob("*.jar"))])
    with (output / "compile-fixture-helper.log").open("w", encoding="utf-8") as log:
        subprocess.run([javac, "--release", "17", "-encoding", "UTF-8", "-proc:none", "-cp", classpath,
                        "-d", str(build), str(ROOT / "FixtureCacheTool.java")], stdout=log, stderr=subprocess.STDOUT, check=True)
    return classpath


def cache_tool(manifest_file, manifest, mode):
    args = [manifest["java"], "-Dfile.encoding=UTF-8"]
    for package in ("java.nio", "sun.nio.ch", "sun.nio.cs", "java.lang", "java.lang.invoke", "java.lang.reflect", "java.util", "java.io"):
        args.append("--add-opens=java.base/" + package + "=ALL-UNNAMED")
    args += ["-cp", manifest["helperClasspath"], "FixtureCacheTool", mode, str(manifest_file)]
    log = manifest_file.parent / (mode + "-" + uuid.uuid4().hex + ".log")
    try:
        result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", timeout=60)
    except subprocess.TimeoutExpired as error:
        captured = error.stdout or b""
        log.write_text(captured.decode("utf-8", errors="replace") if isinstance(captured, bytes) else captured, encoding="utf-8")
        raise RuntimeError(f"Fixture cache {mode} timed out; manifest retained for cleanup recovery") from None
    log.write_text(result.stdout, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(f"Fixture cache {mode} failed; see its log")
    return json.loads(next(line.removeprefix("FIXTURE_RESULT=") for line in result.stdout.splitlines() if line.startswith("FIXTURE_RESULT=")))


def prepare(args):
    if args.manifest.exists():
        raise RuntimeError("Use a new fixture manifest; never overwrite an existing cleanup record")
    work, output = args.work.resolve(), args.manifest.parent.resolve()
    stand = json.loads((work / "stand.json").read_text(encoding="utf-8"))
    config = stand["config"]
    stub, service = ("http://127.0.0.1:" + str(config[key]) for key in ("dependencyPort", "servicePort"))
    health = http(output, stub, "/health")
    if health.get("version") != "EXPLAB-2974-D1-v2":
        raise RuntimeError("Restart managed dependency stubs to load D1-v2")
    classpath = build_helper(work, output, args.javac)
    fixture = http(output, stub, "/__fixtures", "POST", {"dataSet": "D1"}, 201)
    manifest = {"fixture": fixture, "stubUrl": stub, "serviceUrl": service,
                "igniteAddress": "127.0.0.1:" + str(config["ignitePort"]), "java": args.java,
                "helperClasspath": classpath, "status": "preparing"}
    # Durable cleanup instructions precede every SUT mutation.
    write(args.manifest, manifest)
    fixture["params"] = [row for row in http(output, stub, "/api/v2/dictionaries/so-param-dict")
                         if row["splittingPoint"] in fixture["points"].values()]
    fixture["archiveSources"] = [row for point in fixture["points"].values()
                                 for row in http(output, stub, "/api/v2/dictionaries/so-data-source-dict", "POST", {"splittingPoint": point})]
    fixture["splits"] = http(output, stub, "/api/v1/experiments/splits?ids=101321&ids=101322")
    if len(fixture["splits"]) != 2:
        raise RuntimeError("Both D1 experiment splits must be available")
    write(args.manifest, manifest)
    http(output, service, "/api/v2/data-operator/update-dicts", "PUT")
    cache_tool(args.manifest, manifest, "install-sources")
    for point, payload in fixture["payloads"].items():
        http(output, service, "/api/v2/data-operator/kafka/stub/" + point, "POST", payload)
    state = cache_tool(args.manifest, manifest, "state")
    write(output / "fixture-loaded-state.json", state)
    expected = {"splitting_object_cache": 7, "splitting_field_cache": 46,
                "actualization_cache": 2, "param_cache": 2, "data_source_cache": 2}
    if {key: value["found"] for key, value in state.items()} != expected:
        raise RuntimeError("D1 ingestion incomplete; see fixture-loaded-state.json")
    manifest["status"] = "ready"
    write(args.manifest, manifest)
    print("D1 ready: 7 REST objects in 2 isolated points; source-metadata fallback recorded")


def cleanup(args):
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    removed = cache_tool(args.manifest, manifest, "cleanup")
    remaining = cache_tool(args.manifest, manifest, "state")
    write(args.manifest.parent / "fixture-cleanup.json", {"removed": removed, "remaining": remaining})
    if any(value["found"] for value in remaining.values()):
        raise RuntimeError("Owned fixture rows remain; lease retained for recovery")
    if manifest["status"] != "cleaned":
        released = http(args.manifest.parent, manifest["stubUrl"], "/__fixtures/" + manifest["fixture"]["leaseId"],
                        "DELETE", expected=(200, 404))
        if released.get("released") != manifest["fixture"]["leaseId"] and released.get("message") != "Fixture lease not found":
            raise RuntimeError("Dependency fixture release was not confirmed")
    manifest["status"] = "cleaned"
    write(args.manifest, manifest)
    print("D1 cleaned: zero owned rows in all 5 caches; stub lease released")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("prepare", "cleanup"))
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--work", type=Path)
    parser.add_argument("--java", default="java")
    parser.add_argument("--javac", default="javac")
    args = parser.parse_args()
    (prepare if args.mode == "prepare" else cleanup)(args)
