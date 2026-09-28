#!/usr/bin/env bash
# ============================================================
#  USMIND 初始化数据库（Linux）
#  作用同 script/bat/init-db.bat
#  前置：MySQL 已启动（start-mysql.sh / start-all.sh）
# ============================================================
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SELF_DIR/../.." && pwd)"
TOOLS="$ROOT/tools"
MYSQL_DIR="$(ls -d "$TOOLS"/mysql-*/ 2>/dev/null | head -1)"
MYSQL_BIN="$MYSQL_DIR/bin/mysql"
SQL_FILE="$ROOT/sql/init.sql"

if [ -z "$MYSQL_DIR" ]; then
  echo "[错误] 未找到 tools/mysql-* 目录，请先放入 Linux 版 MySQL"
  exit 1
fi

# 首次初始化 root 密码为空，先设置为 root
"$MYSQL_BIN" -u root --skip-password -e "ALTER USER 'root'@'localhost' IDENTIFIED BY 'root';" 2>/dev/null || true

echo "[执行] 导入 init.sql ..."
"$MYSQL_BIN" -u root -proot < "$SQL_FILE"

if [ $? -eq 0 ]; then
  echo "[完成] 数据库 usmind_db 初始化成功"
  echo "  - 数据库账号: root / root"
  echo "  - 默认管理员: admin / admin123"
  echo "  - 默认存储: 本地存储"
else
  echo "[错误] 数据库初始化失败，请检查 MySQL 是否已启动"
fi
echo
