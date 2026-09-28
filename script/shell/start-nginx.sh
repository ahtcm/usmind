#!/usr/bin/env bash
# ============================================================
#  USMIND 启动 Nginx（Linux）
#  作用同 script/bat/start-nginx.bat
#  使用 tools/nginx-*/conf/nginx.conf（相对路径 html/usmind、html/admin）
#  [注意] 若服务器上要开放公网访问，请自行修改 conf 中的 listen 端口与域名；
#         80 端口需要 root 或已授权普通用户绑定
# ============================================================
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SELF_DIR/../.." && pwd)"
TOOLS="$ROOT/tools"
NGINX_DIR="$(ls -d "$TOOLS"/nginx-*/ 2>/dev/null | head -1)"

if [ -z "$NGINX_DIR" ]; then
  echo "[错误] 未找到 tools/nginx-* 目录（请放 Linux 版 Nginx）"
  exit 1
fi

[ -d "$NGINX_DIR/logs" ] || mkdir -p "$NGINX_DIR/logs"

if pgrep -f "$NGINX_DIR/sbin/nginx" >/dev/null 2>&1; then
  echo "[Info] Nginx 运行中，reload..."
  "$NGINX_DIR/sbin/nginx" -p "$NGINX_DIR/" -c conf/nginx.conf -s reload
else
  echo "[Start] Nginx..."
  "$NGINX_DIR/sbin/nginx" -p "$NGINX_DIR/" -c conf/nginx.conf
fi

echo "[Done] Nginx started"
echo "  - User site:  http://127.0.0.1:8866"
echo "  - Admin site: http://127.0.0.1:8867"
echo
