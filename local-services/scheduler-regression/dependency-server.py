"""Source-derived local contracts, NOT implementations of the corporate services."""
import json
import os
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

LOCK = threading.Lock()
JOURNAL = os.environ.get("JOURNAL", "/runtime/dependency-journal.jsonl")


def user(identity):
    identity = int(identity)
    return dict(id=identity, employeeId="LOCAL" + str(identity), username="local" + str(identity),
                firstName="Test", lastName="Scheduler", patronymic="Local", email="local%d@example.test" % identity,
                positionName="QA fixture", roles=["ADMIN"])


def dictionary():
    fields = [("taskNumber", "INTEGER"), ("status", "ENUM"), ("scheduleDatetime", "DATETIME"),
              ("createdByFIO", "STRING"), ("createdByEmailSigma", "STRING"), ("createdAt", "DATETIME"),
              ("action", "ENUM"), ("objectType", "ENUM"), ("objectId", "INTEGER"), ("objectName", "STRING")]
    common = ["equal", "not_equal", "in", "not_in"]
    result = []
    for index, (code, kind) in enumerate(fields, 1):
        operators = common + (["like", "not_like", "like_any", "not_like_any"] if kind == "STRING" else
                              ["more", "more_equal", "less", "less_equal"] if kind in ("INTEGER", "DATETIME") and code != "objectId" else [])
        result.append(dict(id=index, formCode="AUTOSTART_TASK_LIST", paramCode=code, paramName=code,
                           paramDescription="Local source-derived contract fixture", dataType=kind,
                           filterFlag=True, orderFlag=code != "createdByEmailSigma", disableRepeat=False,
                           sourceSearch=False, sourceRowCount=100, enumValues=[], addInfo={},
                           validOperators=[dict(id=i + 1, code=op, name=op, description=op,
                                                isMultiple=op in ("in", "not_in", "like_any", "not_like_any"))
                                           for i, op in enumerate(operators)]))
    return result


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def handle_request(self):
        path = urlparse(self.path)
        raw = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        try:
            body = json.loads(raw) if raw else None
        except ValueError:
            body = None
        status, response = 501, {"error": "Unmodeled dependency contract", "path": path.path}
        if path.path == "/health":
            status, response = 200, {"status": "UP", "kind": "contract-fixture"}
        elif self.command == "GET" and path.path == "/api/v2/dictionaries/splitting-points":
            status, response = 200, [dict(id=1, code="MAPPER", name="Local mapper point")]
        elif self.command == "POST" and path.path == "/api/v2/dictionaries/expression-parameter-dict":
            if body == {"formCode": "AUTOSTART_TASK_LIST"}:
                status, response = 200, dictionary()
            else:
                status, response = 400, {"error": "Unknown formCode"}
        elif self.command == "GET" and path.path.startswith("/api/v2/users/audit"):
            identity = parse_qs(path.query).get("id", ["101"])[0]
            if path.path.endswith("local102"):
                identity = "102"
            if identity in ("101", "102"):
                status, response = 200, user(identity)
            else:
                status, response = 404, {"error": "Unknown local identity"}
        elif self.command == "GET" and path.path == "/api/v1/front/users/by/users":
            values = parse_qs(path.query).get("userIds", ["101"])
            ids = [v for item in values for v in item.split(",") if v in ("101", "102")]
            status, response = 200, {"users": [user(i) for i in ids], "total": len(ids)}
        elif self.command == "GET" and path.path == "/api/v1/communications/criteria/operators":
            status, response = 200, []
        elif self.command == "POST" and path.path == "/api/v1/back/experiments/chain/actual" and isinstance(body, dict):
            status, response = 200, {"id": body.get("startExpId"), "name": "LOCAL_ONLY", "salt": "local"}
        elif self.command == "POST" and path.path == "/api/v2/experiments/status" and isinstance(body, dict):
            status, response = 200, {"warnings": [], "experiment": {"id": body.get("expId"), "status": body.get("status")}}
        elif self.command == "PUT" and re.fullmatch(r"/api/v1/experiments/\d+/status", path.path) and isinstance(body, dict):
            status, response = 200, {"warnings": [], "experiment": {"id": int(path.path.split("/")[4]), "status": body.get("status")}}
        with LOCK:
            with open(JOURNAL, "a", encoding="utf-8") as stream:
                stream.write(json.dumps(dict(timeMillis=int(time.time() * 1000), method=self.command,
                                            path=self.path, body=body, status=status)) + "\n")
        encoded = json.dumps(response).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    do_GET = handle_request
    do_POST = handle_request
    do_PUT = handle_request


ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
