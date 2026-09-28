#!/usr/bin/env bash
# ============================================================
#  USMIND 停止全部服务（Linux）
#  作用同 script/bat/stop-all.bat
# ============================================================
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SELF_DIR/../.." && pwd)"
TOOLS="$ROOT/tools"

echo "========================================"
echo "  Stop USMIND All Services (Linux)"
echo "========================================"

echo "[1/5] Stopping Nginx (8866/8867)..."
pkill -f "$TOOLS/nginx" 2>/dev/null || true
pkill -f "nginx: master" 2>/dev/null || true

echo "[2/5] Stopping Backend Java (8899)..."
pkill -f "usmind-backend.jar" 2>/dev/null || true

echo "[3/5] Stopping Redis (6379)..."
pkill -f "redis-server" 2>/dev/null || true

echo "[4/5] Stopping MySQL (3306)..."
# 优先优雅关闭，失败再强杀
MYSQLADMIN="$(ls -d "$TOOLS"/mysql-*/bin/mysqladmin 2>/dev/null | head -1)"
if [ -n "$MYSQLADMIN" ]; then
  "$MYSQLADMIN" -uroot -proot shutdown 2>/dev/null || true
  sleep 2
fi
pkill -f "mysqld" 2>/dev/null || true

echo "[5/5] Cleaning Node leftovers (Vite dev)..."
pkill -f "vite" 2>/dev/null || true

echo
echo "[DONE] All USMIND services stopped"
echo
