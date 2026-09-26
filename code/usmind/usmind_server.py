# -*- coding: utf-8 -*-
"""
USMIND 内置服务器: 托管 web 构建产物 + 提供 /api/save(接收网页端保存数据并编译同步) + /api/health。
"""
import os
import sys
import re
import json
import base64
import threading
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from usmind_config import load_config
from usmind_sync import build_repo, git_sync, resource_path
from usmind_log import log

_save_lock = threading.Lock()
_sync_timer = None


class Handler(BaseHTTPRequestHandler):
    server_version = "USMIND/1.0"

    def log_message(self, fmt, *args):  # 关闭默认访问日志噪音
        pass

    def _cors(self):
        # 允许线上页面(Netlify 等)跨域调用本机 EXE 保存接口
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def _json(self, obj, code=200):
        data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self._cors()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def _file(self, rel):
        root = resource_path("web")
        safe = os.path.normpath(os.path.join(root, rel))
        if not safe.startswith(os.path.normpath(root)):
            self._json({"error": "forbidden"}, 403)
            return
        if not os.path.isfile(safe):
            self._json({"error": "not found"}, 404)
            return
        ctype = {
            ".html": "text/html; charset=utf-8",
            ".js": "application/javascript; charset=utf-8",
            ".css": "text/css; charset=utf-8",
            ".png": "image/png",
            ".jpg": "image/jpeg",
            ".svg": "image/svg+xml",
            ".ico": "image/x-icon",
            ".json": "application/json; charset=utf-8",
            ".woff2": "font/woff2",
        }.get(os.path.splitext(safe)[1].lower(), "application/octet-stream")
        try:
            with open(safe, "rb") as f:
                body = f.read()
        except Exception:
            self._json({"error": "read error"}, 500)
            return
        self.send_response(200)
        self._cors()
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(body)

    def _share(self):
        cfg = load_config()
        repo = cfg.get("repo_path", "")
        f = os.path.join(repo, "share.html") if repo else ""
        if f and os.path.isfile(f):
            try:
                with open(f, "rb") as fp:
                    body = fp.read()
            except Exception:
                self._json({"error": "read error"}, 500)
                return
            self.send_response(200)
            self._cors()
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            self.wfile.write(body)
        else:
            self._json({"error": "share not ready, 请先保存一次"}, 404)

    def _source(self, rel):
        cfg = load_config()
        repo = cfg.get("repo_path", "")
        safe = os.path.normpath(os.path.join(repo, rel)) if repo else ""
        if not repo or not safe.startswith(os.path.normpath(repo)):
            self._json({"error": "forbidden"}, 403)
            return
        if not os.path.isfile(safe):
            self._json({"error": "not found"}, 404)
            return
        ctype = {
            ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg",
            ".gif": "image/gif", ".webp": "image/webp", ".svg": "image/svg+xml",
            ".ico": "image/x-icon", ".mp3": "audio/mpeg", ".wav": "audio/wav",
            ".ogg": "audio/ogg", ".m4a": "audio/mp4", ".mp4": "video/mp4",
            ".webm": "video/webm", ".ogv": "video/ogg", ".mov": "video/quicktime",
            ".pdf": "application/pdf", ".json": "application/json; charset=utf-8",
            ".txt": "text/plain; charset=utf-8",
        }.get(os.path.splitext(safe)[1].lower(), "application/octet-stream")
        try:
            with open(safe, "rb") as f:
                body = f.read()
        except Exception:
            self._json({"error": "read error"}, 500)
            return
        self.send_response(200)
        self._cors()
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?")[0]
        if path in ("/", "/index.html"):
            self._file("index.html")
        elif path == "/share.html":
            self._share()
        elif path == "/api/health":
            self._json({"ok": True, "app": "usmind"})
        elif path.startswith("/assets/"):
            self._file(path.lstrip("/"))
        elif path.startswith("/source/"):
            self._source(path.lstrip("/"))
        else:
            self._json({"error": "not found"}, 404)

    def do_OPTIONS(self):
        self.send_response(204)
        self._cors()
        self.send_header("Content-Length", "0")
        self.end_headers()

    def _handle_upload(self, body):
        try:
            ctype = self.headers.get("Content-Type", "")
            m = re.search(r'boundary=(?:"([^"]+)"|([^;]+))', ctype)
            boundary = (m.group(1) or m.group(2) or "").strip()
            if not boundary:
                return None, "缺少 boundary"
            parts = body.split(("--" + boundary).encode())
            for part in parts:
                head_end = part.find(b"\r\n\r\n")
                if head_end < 0:
                    continue
                head = part[:head_end].decode("utf-8", "ignore")
                data = part[head_end + 4:]
                if not re.search(r'name="file"', head):
                    continue
                fname_m = re.search(r'filename="([^"]+)"', head)
                fname = fname_m.group(1) if fname_m else "file"
                if not data:
                    continue
                cfg = load_config()
                repo = cfg.get("repo_path", "")
                if not repo:
                    return None, "未配置仓库路径"
                src_dir = os.path.join(repo, "source")
                os.makedirs(src_dir, exist_ok=True)
                ext = os.path.splitext(fname)[1].lower() or ".bin"
                if len(ext) > 10:
                    ext = ".bin"
                import time as _t
                rel = "source/u_%s_%d%s" % (_t.strftime("%Y%m%d%H%M%S"), int(_t.time() * 1000) % 100000, ext)
                with open(os.path.join(src_dir, os.path.basename(rel)), "wb") as f:
                    f.write(data)
                return rel, None
            return None, "未找到文件字段"
        except Exception as e:
            return None, "上传失败: %s" % e

    def do_POST(self):
        path = self.path.split("?")[0]
        if path == "/api/upload":
            try:
                length = int(self.headers.get("Content-Length", 0))
                body = self.rfile.read(length)
            except Exception as e:
                self._json({"error": "bad body: %s" % e}, 400)
                return
            rel, err = self._handle_upload(body)
            if rel:
                log("已保存资源: %s" % rel)
                self._json({"ok": True, "path": rel})
            else:
                self._json({"error": err}, 400)
            return
        if path != "/api/save":
            self._json({"error": "not found"}, 404)
            return
        try:
            length = int(self.headers.get("Content-Length", 0))
            body = json.loads(self.rfile.read(length).decode("utf-8"))
        except Exception as e:
            self._json({"error": "bad body: %s" % e}, 400)
            return
        ok = handle_save(body)
        self._json({"ok": ok})


def handle_save(payload):
    """接收网页端保存数据: 写文件+编译+推送(推送防抖合并)。"""
    global _sync_timer
    tabs = payload.get("tabs") or []
    doc_index = payload.get("docIndex") or 0
    if not tabs:
        return False
    cfg = load_config()
    repo_path = cfg.get("repo_path") or ""
    if not repo_path:
        log("未配置仓库路径, 仅接收数据", level="warning")
        return False
    prefix = load_config().get("resource_prefix") or ""
    try:
        with _save_lock:
            build_repo(repo_path, tabs, doc_index, resource_prefix=prefix)
    except Exception as e:
        log("编译失败: %s" % e, level="error")
        return False
    # 推送防抖: 5 秒内多次保存合并为一次推送
    def do_push():
        git_sync(repo_path, cfg.get("repo_remote") or "", cfg.get("repo_user") or "", cfg.get("repo_token") or "")
    if _sync_timer and _sync_timer.is_alive():
        _sync_timer.cancel()
    _sync_timer = threading.Timer(5.0, do_push)
    _sync_timer.daemon = True
    _sync_timer.start()
    return True


class Server:
    def __init__(self, port):
        self.port = port
        self.httpd = None
        self.thread = None

    def start(self):
        if self.httpd:
            return True
        try:
            self.httpd = ThreadingHTTPServer(("127.0.0.1", self.port), Handler)
        except OSError as e:
            log("端口 %s 启动失败: %s" % (self.port, e), level="error")
            self.httpd = None
            return False
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()
        log("服务已启动: http://127.0.0.1:%s" % self.port)
        return True

    def stop(self):
        if self.httpd:
            self.httpd.shutdown()
            self.httpd.server_close()
            self.httpd = None
            log("服务已停止")

    def open_browser(self):
        webbrowser.open("http://127.0.0.1:%s/?v=exe" % self.port)
