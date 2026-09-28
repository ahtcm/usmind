#!/usr/bin/env bash
# ============================================================
#  USMIND 启动 MySQL（Linux）
#  作用同 script/bat/start-mysql.bat
#  首次运行自动 --initialize-insecure 初始化数据目录
# ============================================================
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SELF_DIR/../.." && pwd)"
TOOLS="$ROOT/tools"
MYSQL_DIR="$(ls -d "$TOOLS"/mysql-*/ 2>/dev/null | head -1)"
MYSQL_DATA="$MYSQL_DIR/data"

if [ -z "$MYSQL_DIR" ]; then
  echo "[错误] 未找到 tools/mysql-* 目录（请放 Linux 版 MySQL，命名风格与 Windows 一致）"
  exit 1
fi

# 已在运行则跳过
if pgrep -f "mysqld" >/dev/null 2>&1; then
  echo "[Info] MySQL 已在运行"
  exit 0
fi

if [ ! -d "$MYSQL_DATA" ]; then
  echo "[Init] 首次运行，初始化数据库..."
  "$MYSQL_DIR/bin/mysqld" --initialize-insecure --basedir="$MYSQL_DIR" --datadir="$MYSQL_DATA" --user="$(id -un)"
  echo "[Init] 完成，root 密码为空"
fi

echo "[Start] MySQL..."
# 后台启动（默认端口 3306），日志输出到 $MYSQL_DIR/mysql.log
nohup "$MYSQL_DIR/bin/mysqld" --basedir="$MYSQL_DIR" --datadir="$MYSQL_DATA" \
      --user="$(id -un)" --port=3306 --bind-address=127.0.0.1 \
      --socket="$MYSQL_DIR/mysql.sock" \
      > "$MYSQL_DIR/mysql.log" 2>&1 &

# 等待端口就绪（最多 30s）
for i in $(seq 1 30); do
  if (echo > /dev/tcp/127.0.0.1/3306) >/dev/null 2>&1; then
    echo "[Done] MySQL started (port 3306)"
    exit 0
  fi
  sleep 1
done
echo "[警告] MySQL 可能未完全就绪，请查看 $MYSQL_DIR/mysql.log"
