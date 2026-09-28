#!/usr/bin/env bash
# ============================================================
#  USMIND 一键构建（Linux）
#  作用同 script/bat/build-all.bat 中的"在线版/常规版"部分：
#    1. 构建后端 jar
#    2. 构建用户端 dist -> nginx html/usmind
#    3. 构建管理后台 dist -> nginx html/admin
#  [说明] Windows 的 build-all.bat 还包含便携版 exe（jpackage/jlink），
#          Linux 服务器部署不需要便携包，故本脚本不包含该步骤。
# ============================================================
set -e
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "============ [1/3] 构建后端 ============"
bash "$SELF_DIR/build-backend.sh"

echo "============ [2/3] 构建用户端 ============"
bash "$SELF_DIR/build-web-user.sh"

echo "============ [3/3] 构建管理后台 ============"
bash "$SELF_DIR/build-web-admin.sh"

echo
echo "========================================"
echo "  构建完成：后端 jar + 两个前端 dist 已部署到 nginx html"
echo "========================================"
