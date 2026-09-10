"""HTTP contract, isolation and persistence checks; no live service required."""
import json
from pathlib import Path
import tempfile
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from dependency_stub import make_server, FixtureState, SOURCE_ROUTE, PARAM_ROUTE, SPLIT_ROUTE, SCHEMA


class DependencyContractsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.state = Path(self.directory.name)/"state.json"
        self.server = make_server(0, self.state)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = "http://127.0.0.1:" + str(self.server.server_port)

    def tearDown(self):
        self.server.shutdown()
        self.thread.join()
        self.server.server_close()
        self.directory.cleanup()

    def call(self, path, method="GET", body=None, expected=200, raw=None):
        data = raw if raw is not None else None if body is None else json.dumps(body).encode()
        request = Request(self.base+path, method=method, data=data, headers={"Content-Type":"application/json"})
        try:
            response = urlopen(request, timeout=3)
        except HTTPError as error:
            response = error
        with response:
            self.assertEqual(expected, response.status)
            self.assertEqual("application/json", response.headers.get_content_type())
            return json.loads(response.read())

    def lease(self):
        return self.call("/__fixtures", "POST", {"dataSet":"D1"}, 201)

    def test_archive_filter_does_not_hide_mapper_only_client(self):
        fixture = self.lease()
        rows = self.call(SOURCE_ROUTE, "POST", {"splittingPoint":"MAPPER"})
        self.assertEqual(["MAPPER"], [row["splittingPoint"] for row in rows])
        self.assertEqual([], self.call(SOURCE_ROUTE, "POST", {"splittingPoint":"unknown"}))
        rows = self.call(SOURCE_ROUTE, "POST", {"splittingPoint":fixture["points"]["SP1"]})
        self.assertEqual(1, len(rows))
        self.assertEqual(SCHEMA, json.loads(rows[0]["dataSchema"]))

    def test_fp_get_has_object_schema_and_array_filter(self):
        fixture = self.lease()
        point = fixture["points"]["SP1"]
        for query in ("MAPPER,"+point, "MAPPER&splittingPoint="+point):
            rows = self.call(SOURCE_ROUTE+"?splittingPoint="+query)
            self.assertEqual({"MAPPER",point}, {row["splittingPoint"] for row in rows})
            self.assertEqual(SCHEMA, rows[0]["dataSchema"])

    def test_fixture_d1_schema_fields_and_keys(self):
        fixture = self.lease()
        rows = self.call(PARAM_ROUTE)
        self.assertEqual(3, len(rows))
        for row in rows:
            self.assertEqual(["id"], [p["code"] for p in row["params"] if p["key"]])
            self.assertEqual(["parentId"], [p["code"] for p in row["params"] if p["parentKey"]])
            self.assertEqual(list(range(1,8)), [p["order"] for p in row["params"]])
        objects = [row for payload in fixture["payloads"].values() for row in payload["cjConfig"]["objects"]]
        self.assertEqual(7, len(objects))
        item_schema = SCHEMA["properties"]["objects"]["items"]
        types = {"integer":int, "number":(int,float), "string":str, "boolean":bool}
        for row in objects:
            self.assertTrue(set(item_schema["required"]).issubset(row))
            self.assertTrue(set(row).issubset(item_schema["properties"]))
            for key,value in row.items():
                self.assertIsInstance(value, types[item_schema["properties"][key]["type"]])

    def test_independent_leases_release_and_restart(self):
        first, second = self.lease(), self.lease()
        self.assertTrue(set(first["points"].values()).isdisjoint(second["points"].values()))
        self.call("/__fixtures/"+first["leaseId"], "DELETE")
        recovered = FixtureState(self.state)
        self.assertEqual({second["leaseId"]}, set(recovered.leases))
        self.assertEqual(3, len(self.call(PARAM_ROUTE)))
        self.call("/__fixtures/"+first["leaseId"], expected=404)

    def test_splits_and_id_filters(self):
        rows = self.call(SPLIT_ROUTE)
        self.assertEqual([101321,101322], [row["id"] for row in rows])
        self.assertEqual(101321, rows[1]["parentId"])
        for query in ("101322", "101322&ids=999999", "101322,999999"):
            self.assertEqual([rows[1]], self.call(SPLIT_ROUTE+"?ids="+query))
        self.assertEqual([], self.call(SPLIT_ROUTE+"?ids=9223372036854775807"))

    def test_reject_invalid_inputs_without_creating_state(self):
        for body in ([], None, {"splittingPoint":123}, {"splittingPoint":True}, {"splittingPoint":[]}, {"splittingPoint":""}, {"typo":"MAPPER"}):
            self.call(SOURCE_ROUTE, "POST", expected=400, raw=json.dumps(body).encode())
        self.call(SOURCE_ROUTE, "POST", expected=400, raw=b'{')
        for ids in ("true", "1.5", "9223372036854775808", ""):
            self.call(SPLIT_ROUTE+"?ids="+ids, expected=400)
        self.call("/__fixtures", "POST", {"dataSet":"invalid"}, 400)
        self.assertEqual(0, self.call("/health")["activeLeases"])

    def test_never_stub_sut_endpoint(self):
        self.call("/api/v2/data-operator/splitting-objects-links", "POST", {}, 404)
        self.call(PARAM_ROUTE, "POST", {}, 405)


if __name__ == "__main__":
    unittest.main()
