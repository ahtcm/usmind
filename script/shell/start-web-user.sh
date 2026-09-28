#!/usr/bin/env bash
# ============================================================
#  USMIND 启动前端开发服务器（Linux）
#  作用同 script/bat/start-web-user.bat（Vite dev，端口 5173）
# ============================================================
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SELF_DIR/../.." && pwd)"
TOOLS="$ROOT/tools"
NPM="$(ls -d "$TOOLS"/nodejs/bin/npm 2>/dev/null | head -1)"
[ -z "$NPM" ] && NPM="$(ls -d "$TOOLS"/nodejs/npm 2>/dev/null | head -1)"
PROJECT_DIR="$ROOT/code/web-user"

if [ -z "$NPM" ]; then echo "[错误] 未找到 tools/nodejs（Linux 版 Node.js）"; exit 1; fi
export PATH="$(dirname "$NPM"):$PATH"

cd "$PROJECT_DIR"
[ -d node_modules ] || npm install

echo "[启动] Vite 开发服务器 (端口 5173)..."
npm run dev
