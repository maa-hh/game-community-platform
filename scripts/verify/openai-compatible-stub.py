#!/usr/bin/env python3
"""启动验证使用的延迟 OpenAI Compatible 模型桩。"""

import argparse
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Handler(BaseHTTPRequestHandler):
    count = 0
    lock = threading.Lock()
    count_file = ""
    delay = 0.0

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self.send_error(404)
            return
        length = int(self.headers.get("Content-Length", "0"))
        self.rfile.read(length)
        with Handler.lock:
            Handler.count += 1
            if Handler.count_file:
                with open(Handler.count_file, "w", encoding="utf-8") as output:
                    output.write(str(Handler.count))
        time.sleep(Handler.delay)
        body = {
            "id": "chatcmpl-verification", "object": "chat.completion", "created": 1,
            "model": "verification-model",
            "choices": [{"index": 0, "message": {"role": "assistant",
                         "content": '{"score":8,"reason":"verification"}'},
                         "finish_reason": "stop"}],
            "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2},
        }
        encoded = json.dumps(body).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, *_args):
        return


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--count-file", required=True)
    parser.add_argument("--delay", type=float, default=2.0)
    args = parser.parse_args()
    Handler.count_file = args.count_file
    Handler.delay = args.delay
    ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
