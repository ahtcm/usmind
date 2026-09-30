#!/usr/bin/env bash
# ============================================================
#  USMIND 数据库备份（Linux）
#  作用同 script/bat/backup-db.bat
#  用法：
#    bash script/shell/backup-db.sh            # 手动备份
#    crontab -e                                # 每天 03:00 自动备份：
#    0 3 * * *  bash /path/to/usmind/script/shell/backup-db.sh >> /path/to/usmind/logs/backup.log 2>&1
#  说明：
#    - 使用系统 PATH 中的 mysqldump（生产服务器请安装 MySQL 客户端）
#    - 可通过环境变量覆盖连接信息：
#        DB_HOST / DB_PORT / DB_USER / DB_PASSWORD / DB_NAME
#    - 备份保留 KEEP_DAYS 天（默认 7），超期自动清理
# ============================================================
set -e
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SELF_DIR/../.." && pwd)"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-root}"
DB_NAME="${DB_NAME:-usmind_db}"
BACKUP_DIR="${BACKUP_DIR:-$ROOT/backup/db}"
KEEP_DAYS="${KEEP_DAYS:-7}"

mkdir -p "$BACKUP_DIR"
STAMP="$(date +%Y%m%d_%H%M%S)"
OUT="$BACKUP_DIR/${DB_NAME}_${STAMP}.sql"

echo "[Backup] dumping $DB_NAME ..."
mysqldump -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASSWORD" \
  --single-transaction --routines --triggers --databases "$DB_NAME" > "$OUT"

echo "[OK] $OUT"

echo "[Clean] removing backups older than ${KEEP_DAYS} days..."
find "$BACKUP_DIR" -name "${DB_NAME}_*.sql" -mtime +"$KEEP_DAYS" -delete

echo "[Done] backup finished"
