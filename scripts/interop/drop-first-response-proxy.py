#!/usr/bin/env python3
"""Test-only HTTP proxy that loses one selected Hub response after Hub accepted it.

Run inside a local Docker container with host networking. It never logs packet
contents, grants, or response bodies and must not be used as an App endpoint.
"""

from __future__ import annotations

import argparse
import http.client
import json
import socket
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--listen", type=int, default=8790)
    parser.add_argument("--target", type=int, default=8789)
    parser.add_argument("--drop-path", required=True)
    parser.add_argument("--drop-operation", default="")
    args = parser.parse_args()
    guard = threading.Lock()
    dropped = False

    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, *_args: object) -> None:
            pass

        def do_GET(self) -> None:
            self.forward(b"")

        def do_POST(self) -> None:
            length = int(self.headers.get("Content-Length", "0"))
            if length < 0 or length > 256 * 1024:
                self.send_error(413)
                return
            self.forward(self.rfile.read(length))

        def forward(self, body: bytes) -> None:
            nonlocal dropped
            connection = http.client.HTTPConnection("127.0.0.1", args.target, timeout=45)
            try:
                headers = {"Accept": "application/json"}
                if self.command == "POST":
                    headers["Content-Type"] = "application/json"
                connection.request(self.command, self.path, body=body if self.command == "POST" else None,
                                   headers=headers)
                response = connection.getresponse()
                content = response.read()
                operation = ""
                if self.path == args.drop_path and args.drop_operation:
                    try:
                        operation = json.loads(body).get("route", {}).get("operation", "")
                    except (ValueError, AttributeError):
                        operation = ""
                matches = self.path == args.drop_path and operation == args.drop_operation
                with guard:
                    lose = matches and not dropped and response.status in (200, 201)
                    if lose:
                        dropped = True
                if lose:
                    print(f"DROPPED path={self.path} operation={operation or '-'} "
                          f"hub_status={response.status}", flush=True)
                    self.close_connection = True
                    try:
                        self.connection.shutdown(socket.SHUT_RDWR)
                    except OSError:
                        pass
                    return
                self.send_response(response.status)
                self.send_header("Content-Type", response.getheader("Content-Type", "application/json"))
                self.send_header("Content-Length", str(len(content)))
                self.end_headers()
                self.wfile.write(content)
            except (OSError, http.client.HTTPException):
                self.send_error(502)
            finally:
                connection.close()

    server = ThreadingHTTPServer(("127.0.0.1", args.listen), Handler)
    server.daemon_threads = True
    print(f"READY listen={args.listen} target={args.target} drop={args.drop_path} "
          f"operation={args.drop_operation or '-'}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
