#!/usr/bin/env python3
"""
启动 3 个轻量 HTTP mock 下游，供 route-sentinel 性能测试使用。
- 9101 -> LEGACY-DOWNSTREAM
- 9102 -> FACE-PAY-DOWNSTREAM
- 9103 -> FEP-APP-DOWNSTREAM
"""
import argparse
import http.server
import socketserver
import threading

PORTS = {
    9101: "LEGACY-DOWNSTREAM",
    9102: "FACE-PAY-DOWNSTREAM",
    9103: "FEP-APP-DOWNSTREAM",
}


class ThreadingReusableTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True
    daemon_threads = True


class MockHandler(http.server.BaseHTTPRequestHandler):
    body: str = "OK"

    def do_GET(self):
        payload = self.body.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, format, *args):
        # 静默访问日志，避免压测时刷屏
        pass


def start_server(port, body):
    handler = type("MockHandler%d" % port, (MockHandler,), {"body": body})
    with ThreadingReusableTCPServer(("127.0.0.1", port), handler) as httpd:
        print(f"Mock downstream listening on 127.0.0.1:{port} -> {body}")
        httpd.serve_forever()


def main():
    parser = argparse.ArgumentParser(description="Start mock downstreams for route-sentinel perf test")
    parser.add_argument("--quiet", action="store_true", help="Suppress startup messages")
    args = parser.parse_args()

    threads = []
    for port, body in PORTS.items():
        t = threading.Thread(target=start_server, args=(port, body), daemon=True)
        t.start()
        threads.append(t)

    if not args.quiet:
        print("Mock downstreams started. Press Ctrl+C to stop.")
    try:
        while True:
            threading.Event().wait(1)
    except KeyboardInterrupt:
        print("\nStopping mock downstreams...")


if __name__ == "__main__":
    main()
