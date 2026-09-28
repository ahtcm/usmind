#!/usr/bin/env bash
# ============================================================
#  USMIND 一键清理（Linux）
#  作用同 script/bat/delete-all.bat：
#    1. 停止全部服务
#    2. 删除日志 logs/
#    3. 删除构建产物（backend/target、前端 dist）
#    4. [可选] 删除 MySQL/Redis 数据（完全重置）
#  源码、release 产物、tools 环境一律保留
# ============================================================
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SELF_DIR/../.." && pwd)"
TOOLS="$ROOT/tools"

echo "========================================"
echo "  USMIND One-Click Delete - Clean Runtime"
echo "  [NOTE] Source code / release / tools KEPT"
echo "========================================"

# ---------- 1. Stop all ----------
echo "[1/4] Stopping all services..."
bash "$SELF_DIR/stop-all.sh" 2>/dev/null || true
sleep 2

# ---------- 2. Clean logs ----------
echo "[2/4] Cleaning logs..."
for d in "$ROOT/logs" "$ROOT/release/logs" "$ROOT/release/usmind/logs" \
         "$ROOT/release/admin/logs" "$ROOT/code/backend/logs"; do
  [ -d "$d" ] && rm -rf "$d"
done
[ -d "$TOOLS"/nginx-*/logs ] && rm -rf "$TOOLS"/nginx-*/logs/* 2>/dev/null || true

# ---------- 3. Clean build outputs ----------
echo "[3/4] Cleaning build outputs..."
rm -rf "$ROOT/code/backend/target" \
       "$ROOT/code/web-user/dist" \
       "$ROOT/code/web-admin/dist"

# ---------- 4. Optional: delete DB data ----------
echo
read -r -p "  Delete MySQL data too (full reset, need init-db.sh again)? [y/N]: " CONFIRM
if [ "$CONFIRM" = "y" ] || [ "$CONFIRM" = "Y" ]; then
  echo "[Deleting] MySQL data..."
  pkill -f mysqld 2>/dev/null || true
  sleep 2
  rm -rf "$TOOLS"/mysql-*/data
  rm -f "$TOOLS"/redis-*/dump.rdb
  echo "[Deleted] MySQL + Redis data cleared"
  echo "          Next start: bash script/shell/init-db.sh first"
else
  echo "[Skipped] Database data kept"
fi

echo
echo "========================================"
echo "  Cleanup done"
echo "========================================"
