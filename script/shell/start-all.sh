#!/usr/bin/env bash
# ============================================================
#  USMIND 一键启动（Linux）
#  作用同 script/bat/start-all.bat
#  环境目录约定（放在项目 tools/ 下，命名风格与 Windows 一致）：
#    tools/jdk-17.0.2/            JDK 17 (linux x64)
#    tools/apache-maven-3.9.6/    Maven 3.9.6 (linux)
#    tools/nodejs/                Node.js + npm (linux)
#    tools/mysql-8.0.x-linux*/    MySQL 8.0 (linux, 目录名自动探测)
#    tools/redis-*/               Redis (linux, 目录名自动探测)
#    tools/nginx-*/               Nginx (linux, 目录名自动探测)
#  用法：
#    bash script/shell/start-all.sh
#  生产服务器建议：只跑 start-mysql / start-redis / start-nginx / start-backend，
#  前端 dist 由 build-web-user.sh / build-web-admin.sh 部署到 nginx html。
# ============================================================
set -e
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SELF_DIR/../.." && pwd)"
TOOLS="$ROOT/tools"

echo "========================================"
echo "  USMIND One-Click Start (Linux)"
echo "  ROOT: $ROOT"
echo "========================================"
echo

# ---------- 环境探测 ----------
find_tool_dir() {
  ls -d "$TOOLS"/$1 2>/dev/null | head -1
}
JAVA_BIN="$(find_tool_dir 'jdk-17.0.2/bin/java')"
if [ -z "$JAVA_BIN" ]; then JAVA_BIN="$(find_tool_dir 'jdk*/bin/java')"; fi
export JAVA_HOME="$(dirname "$(dirname "$JAVA_BIN")")"
MAVEN_CMD="$(find_tool_dir 'apache-maven-*/bin/mvn')"
NPM_CMD="$(find_tool_dir 'nodejs/bin/npm')"
MYSQL_DIR="$(find_tool_dir 'mysql-*/')"
REDIS_DIR="$(find_tool_dir 'redis-*/')"
NGINX_DIR="$(find_tool_dir 'nginx-*/')"

# ---------- 1. MySQL ----------
echo "[1/5] Starting MySQL (3306)..."
bash "$SELF_DIR/start-mysql.sh"

# ---------- 2. Redis ----------
echo "[2/5] Starting Redis (6379)..."
bash "$SELF_DIR/start-redis.sh"

# ---------- 3. Nginx ----------
echo "[3/5] Starting Nginx (80 / 8866 / 8867)..."
bash "$SELF_DIR/start-nginx.sh"

# ---------- 4. Backend ----------
echo "[4/5] Building + Starting Backend (8899)..."
bash "$SELF_DIR/start-backend.sh" &
BACKEND_PID=$!
sleep 3

# ---------- 5. Frontends ----------
echo "[5/5] Building frontends and deploying to nginx html..."
bash "$SELF_DIR/build-web-user.sh"
bash "$SELF_DIR/build-web-admin.sh"

echo
echo "========================================"
echo "  ALL SERVICES STARTED"
echo "  - User site:   http://127.0.0.1:8866"
echo "  - Admin site:  http://127.0.0.1:8867"
echo "  - Backend API: http://127.0.0.1:8899/api"
echo "  - Default admin: admin / admin123"
echo "  Backend PID: $BACKEND_PID"
echo "========================================"
