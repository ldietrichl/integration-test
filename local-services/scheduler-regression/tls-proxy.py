"""Local HTTPS transport only: does not alter scheduler statuses or business responses."""
import http.client
import ssl
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Proxy(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def relay(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        headers = {k: v for k, v in self.headers.items() if k.lower() not in ("host", "connection", "content-length")}
        upstream = http.client.HTTPConnection("scheduler", 8080, timeout=30)
        try:
            upstream.request(self.command, self.path, body or None, headers)
            response = upstream.getresponse()
            data = response.read()
            self.send_response(response.status)
            for key, value in response.getheaders():
                if key.lower() not in ("connection", "transfer-encoding", "content-length"):
                    self.send_header(key, value)
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        finally:
            upstream.close()

    do_GET = relay
    do_POST = relay
    do_PUT = relay
    do_DELETE = relay


def serve(port, mutual):
    server = ThreadingHTTPServer(("0.0.0.0", port), Proxy)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain("/tls/server.pem", "/tls/server.key")
    if mutual:
        context.load_verify_locations("/tls/ca.pem")
        context.verify_mode = ssl.CERT_REQUIRED
    server.socket = context.wrap_socket(server.socket, server_side=True)
    server.serve_forever()


threading.Thread(target=serve, args=(8444, True), daemon=True).start()
serve(8443, False)
