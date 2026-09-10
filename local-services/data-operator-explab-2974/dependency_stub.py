"""Local dependency contracts + isolated fixture leases. Never stub the SUT's links API."""
import argparse
from copy import deepcopy
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import re
import threading
from urllib.parse import parse_qs, urlsplit
import uuid

VERSION = "EXPLAB-2974-D1-v2"
SOURCE_ROUTE = "/api/v2/dictionaries/so-data-source-dict"
PARAM_ROUTE = "/api/v2/dictionaries/so-param-dict"
SPLIT_ROUTE = "/api/v1/experiments/splits"
FIELDS = [("id", "INTEGER"), ("parentId", "INTEGER"), ("productName", "STRING"),
          ("channelId", "INTEGER"), ("amount", "NUMBER"), ("enabled", "BOOLEAN"), ("note", "STRING")]
SCHEMA = {"$schema": "http://json-schema.org/draft-07/schema#", "type": "object",
          "required": ["objects"], "additionalProperties": False, "properties": {
              "objects": {"type": "array", "items": {"type": "object", "additionalProperties": False,
                  "required": ["id", "productName", "channelId", "amount", "enabled"],
                  "properties": {code: {"type": {"INTEGER": "integer", "STRING": "string",
                      "NUMBER": "number", "BOOLEAN": "boolean"}[kind]} for code, kind in FIELDS}}}}}


def dictionaries(points):
    points = list(points)
    params = [{"splittingPoint": point, "params": [
        {"paramPath": "$.objects[]." + code, "code": code, "name": code, "type": kind,
         "order": order, "key": code == "id", "parentKey": code == "parentId"}
        for order, (code, kind) in enumerate(FIELDS, 1)]} for point in points]
    sources = [{"id": 2974 if point == "MAPPER" else int(uuid.uuid5(uuid.NAMESPACE_URL, point).hex[:15], 16),
                "splittingPoint": point, "sourceType": "KAFKA", "dataUpdateType": "ON_UPDATE",
                "universalClass": True, "refillTopic": point, "updateTopic": point,
                "dataSchema": deepcopy(SCHEMA), "updateDataSchema": deepcopy(SCHEMA)} for point in points]
    return params, sources


def archive_sources(sources):
    """06fd6acb646 uses POST and JSON-encoded String schemas; FP GET uses objects."""
    result = deepcopy(sources)
    for source in result:
        for field in ("dataSchema", "updateDataSchema"):
            source[field] = json.dumps(source[field], ensure_ascii=False)
    return result


class FixtureState:
    def __init__(self, state_file=None, journal_file=None):
        self.lock = threading.RLock()
        self.state_file, self.journal_file = state_file, journal_file
        self.leases = {}
        self.data = json.loads(Path(__file__).with_name("fixture-d1.json").read_text(encoding="utf-8"))
        if state_file and state_file.exists():
            saved = json.loads(state_file.read_text(encoding="utf-8"))
            if saved["version"] != VERSION:
                raise ValueError("Fixture state version mismatch; preserve and inspect existing leases")
            self.leases = saved["leases"]

    def save(self):
        if self.state_file:
            temporary = self.state_file.with_suffix(".tmp")
            temporary.write_text(json.dumps({"version": VERSION, "leases": self.leases}, ensure_ascii=False), encoding="utf-8")
            temporary.replace(self.state_file)

    def create(self):
        lease = uuid.uuid4().hex
        points = {name: f"EXPLAB2974_{lease}_{name}" for name in ("SP1", "SP2")}
        params, sources = dictionaries(points.values())
        fixture = {"version": VERSION, "leaseId": lease, "dataSet": "D1", "points": points,
                   "params": params, "archiveSources": archive_sources(sources),
                   "payloads": {points[name]: {"cjConfig": {"objects": deepcopy(rows)}}
                                for name, rows in self.data["objects"].items()},
                   "splits": deepcopy(self.data["splits"]), "expectedPairs": self.data["expectedPairs"]}
        self.leases[lease] = fixture
        try:
            self.save()
        except Exception:
            del self.leases[lease]
            raise
        return fixture

    def record(self, method, path, status):
        # No bodies/headers: evidence of Feign calls without arbitrary secrets.
        if self.journal_file:
            with self.lock, self.journal_file.open("a", encoding="utf-8") as stream:
                stream.write(json.dumps({"at": datetime.now(timezone.utc).isoformat(), "method": method,
                                         "path": path, "status": status}) + "\n")


class Handler(BaseHTTPRequestHandler):
    def reply(self, status, body):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)
        self.server.fixtures.record(self.command, self.path, status)

    def error(self, status, message):
        self.reply(status, {"id": str(uuid.uuid4()), "message": message})

    def body(self):
        if self.headers.get_content_type() != "application/json":
            raise ValueError("Content-Type must be application/json")
        size = int(self.headers.get("Content-Length", "0"))
        if size <= 0 or size > 65536:
            raise ValueError("Expected JSON object (maximum 64 KiB)")
        value = json.loads(self.rfile.read(size), parse_constant=lambda _: (_ for _ in ()).throw(ValueError("Non-JSON number")))
        if not isinstance(value, dict):
            raise ValueError("Expected JSON object")
        return value

    def dispatch(self):
        state = self.server.fixtures
        parsed = urlsplit(self.path)
        path, query = parsed.path, parse_qs(parsed.query, keep_blank_values=True)
        with state.lock:
            if path == "/health" and self.command == "GET":
                self.reply(200, {"status": "UP", "kind": "dependency-stub", "version": VERSION,
                                 "activeLeases": len(state.leases)})
                return
            if path == "/__fixtures" and self.command == "POST":
                if query or self.body() != {"dataSet": "D1"}:
                    raise ValueError('Use {"dataSet":"D1"}; fixture API is local test administration')
                self.reply(201, state.create())
                return
            if path.startswith("/__fixtures/") and self.command in ("GET", "DELETE"):
                lease = path.removeprefix("/__fixtures/")
                if query or not re.fullmatch("[0-9a-f]{32}", lease):
                    raise ValueError("Invalid fixture lease ID")
                if lease not in state.leases:
                    self.error(404, "Fixture lease not found")
                elif self.command == "GET":
                    self.reply(200, state.leases[lease])
                else:
                    saved = state.leases.pop(lease)
                    try:
                        state.save()
                    except Exception:
                        state.leases[lease] = saved
                        raise
                    self.reply(200, {"released": lease, "scope": "dependency stub only"})
                return
            points = ["MAPPER"] + [point for fixture in state.leases.values() for point in fixture["points"].values()]
            params, sources = dictionaries(points)
            if path == PARAM_ROUTE and self.command == "GET":
                if query:
                    raise ValueError("This client contract has no query parameters")
                self.reply(200, params)
                return
            if path == SOURCE_ROUTE and self.command in ("GET", "POST"):
                if self.command == "POST":
                    body = self.body()
                    if query or set(body) - {"splittingPoint"}:
                        raise ValueError("Only splittingPoint is supported")
                    point = body.get("splittingPoint")
                    if point is not None and (not isinstance(point, str) or not point.strip()):
                        raise ValueError("splittingPoint must be a nonblank string or null")
                    selected = [point] if point is not None else None
                else:
                    if set(query) - {"splittingPoint"}:
                        raise ValueError("Only splittingPoint is supported")
                    selected = [point for group in query.get("splittingPoint", []) for point in group.split(",")]
                    if any(not point.strip() for point in selected):
                        raise ValueError("Empty splittingPoint")
                rows = [row for row in sources if not selected or row["splittingPoint"] in selected]
                self.reply(200, archive_sources(rows) if self.command == "POST" else rows)
                return
            if path == SPLIT_ROUTE and self.command == "GET":
                if set(query) - {"ids"}:
                    raise ValueError("Only ids is supported")
                ids = [item for group in query.get("ids", []) for item in group.split(",")]
                if any(not re.fullmatch(r"-?\d+", item) or not -(2**63) <= int(item) < 2**63 for item in ids):
                    raise ValueError("ids must be int64 values")
                rows = self.server.fixtures.data["splits"]
                self.reply(200, [row for row in rows if not ids or row["id"] in {int(item) for item in ids}])
                return
            known = path in ("/health", "/__fixtures", SOURCE_ROUTE, PARAM_ROUTE, SPLIT_ROUTE) or path.startswith("/__fixtures/")
            self.error(405 if known else 404, "Method not allowed" if known else "No dependency stub for this route")

    def handle_request(self):
        try:
            self.dispatch()
        except (ValueError, UnicodeError) as error:
            self.error(400, str(error))

    do_GET = do_POST = do_PUT = do_DELETE = do_PATCH = handle_request


def make_server(port, state_file=None, journal_file=None):
    state = FixtureState(state_file, journal_file)
    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    server.fixtures = state
    return server


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=18075)
    parser.add_argument("--state", type=Path)
    parser.add_argument("--journal", type=Path)
    args = parser.parse_args()
    server = make_server(args.port, args.state, args.journal)
    print(f"{VERSION} listening on 127.0.0.1:{args.port}", flush=True)
    server.serve_forever()
