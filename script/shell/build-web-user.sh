#!/usr/bin/env bash
# ============================================================
#  USMIND 构建用户端并部署到 nginx html（Linux）
#  作用同 script/bat/build-web-user.bat（部署目标改为 nginx html/usmind，
#  与 Windows 下 start-all 后 nginx 服务的目录保持一致）
# ============================================================
set -e
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SELF_DIR/../.." && pwd)"
TOOLS="$ROOT/tools"
NPM="$(ls -d "$TOOLS"/nodejs/bin/npm 2>/dev/null | head -1)"
[ -z "$NPM" ] && NPM="$(ls -d "$TOOLS"/nodejs/npm 2>/dev/null | head -1)"
NGINX_HTML="$(ls -d "$TOOLS"/nginx-*/html 2>/dev/null | head -1)"
PROJECT_DIR="$ROOT/code/web-user"

if [ -z "$NPM" ]; then echo "[错误] 未找到 tools/nodejs（Linux 版 Node.js）"; exit 1; fi
if [ -z "$NGINX_HTML" ]; then echo "[错误] 未找到 tools/nginx-*/html"; exit 1; fi
export PATH="$(dirname "$NPM"):$PATH"

cd "$PROJECT_DIR"
[ -d node_modules ] || npm install

echo "[Build] Vite build..."
npm run build

echo "[Deploy] Copy to nginx html/usmind ..."
rm -rf "$NGINX_HTML/usmind"
mkdir -p "$NGINX_HTML/usmind"
cp -r dist/* "$NGINX_HTML/usmind/"

echo "[Done] User site deployed to $NGINX_HTML/usmind"
