#!/usr/bin/env bash
# ============================================================
#  USMIND 探活自愈（Linux）
#  作用同 script/bat/start-monitor.bat
#  每 30 秒检查各服务健康状态，挂掉自动拉起并写监控日志。
#  用法：
#    nohup bash script/shell/start-monitor.sh > logs/monitor.out 2>&1 &
#    # 或注册为 systemd 服务长期守护
#  说明：
#    - 服务地址可用环境变量覆盖：BACKEND_URL / MYSQL_HOST / REDIS_HOST / NGINX_URL
#    - 拉起命令按本项目 shell 脚本（script/shell/start-*.sh）
# ============================================================
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SELF_DIR/../.." && pwd)"
LOG="$ROOT/logs/monitor.log"
mkdir -p "$ROOT/logs"

BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8899/api/health}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
NGINX_URL="${NGINX_URL:-http://127.0.0.1:8866}"

log() { echo "[$(date '+%F %T')] $*" >> "$LOG"; }

restart_backend() {
  log "Backend DOWN, restarting"
  nohup bash "$SELF_DIR/start-backend.sh" >> "$ROOT/logs/backend-monitor.out" 2>&1 &
}

restart_service() {
  local name="$1" script="$2"
  log "$name DOWN, restarting"
  nohup bash "$SELF_DIR/$script" >> "$ROOT/logs/${name}-monitor.out" 2>&1 &
}

while true; do
  # Backend
  code="$(curl -s -o /dev/null -w '%{http_code}' -m 5 "$BACKEND_URL" 2>/dev/null || true)"
  if [ "$code" != "200" ]; then restart_backend; fi

  # MySQL（TCP 探测）
  if ! timeout 3 bash -c "</dev/tcp/$MYSQL_HOST/$MYSQL_PORT" 2>/dev/null; then
    restart_service MySQL start-mysql.sh
  fi

  # Redis
  if ! timeout 3 bash -c "</dev/tcp/$REDIS_HOST/$REDIS_PORT" 2>/dev/null; then
    restart_service Redis start-redis.sh
  fi

  # Nginx
  code="$(curl -s -o /dev/null -w '%{http_code}' -m 5 "$NGINX_URL" 2>/dev/null || true)"
  if [ "$code" != "200" ]; then restart_service Nginx start-nginx.sh; fi

  sleep 30
done
