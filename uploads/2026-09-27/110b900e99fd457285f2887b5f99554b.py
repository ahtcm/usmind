# -*- coding: utf-8 -*-
"""
USMIND 静态站点生成脚本
功能: 将前端编译产物(code/web/dist) 同步到部署根(usmind/),
      保护 git 仓库文件(.git/.gitignore), 预留资源路径适配与加密接口。
用法: python build_site.py [--password PASSWORD]
"""
import os
import re
import shutil
import sys
import argparse
import glob

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
CODE_WEB = os.path.join(ROOT, "code", "web")
DIST_SRC = os.path.join(CODE_WEB, "dist")
DEPLOY = os.path.join(ROOT, "release", "usmind")

PROTECTED = {".git", ".gitignore", ".github", "share.html", "data"}


def clean_deploy_dir(deploy: str) -> None:
    """清空部署目录(保护 git 相关文件)"""
    if not os.path.isdir(deploy):
        os.makedirs(deploy, exist_ok=True)
        return
    for name in os.listdir(deploy):
        if name in PROTECTED:
            continue
        path = os.path.join(deploy, name)
        if os.path.isdir(path):
            shutil.rmtree(path, ignore_errors=True)
        else:
            os.remove(path)


def copy_dist(src: str, deploy: str) -> int:
    """复制构建产物到部署目录, 返回文件数"""
    if not os.path.isdir(src):
        print(f"[错误] 未找到前端构建产物: {src}")
        sys.exit(1)
    count = 0
    for root_dir, dirs, files in os.walk(src):
        rel = os.path.relpath(root_dir, src)
        target_dir = deploy if rel == "." else os.path.join(deploy, rel)
        os.makedirs(target_dir, exist_ok=True)
        for f in files:
            shutil.copy2(os.path.join(root_dir, f), os.path.join(target_dir, f))
            count += 1
    return count


def check_relative_paths(deploy: str) -> list:
    """校验静态文件引用均为相对路径(不出现盘符/绝对URL, 排除外链协议)"""
    problems = []
    abs_pattern = re.compile(r'(src|href)\s*=\s*["\'](/[^"\']*|https?://[^"\']*|file://[^"\']*)["\']', re.I)
    for f in glob.glob(os.path.join(deploy, "**", "*.html"), recursive=True):
        with open(f, "r", encoding="utf-8", errors="ignore") as fp:
            text = fp.read()
        for m in abs_pattern.finditer(text):
            problems.append(f"{os.path.relpath(f, deploy)}: {m.group(0)}")
    return problems


def main():
    parser = argparse.ArgumentParser(description="USMIND 静态站点生成")
    parser.add_argument("--password", default=None, help="分享密码(留空不加密, 后续版本支持)")
    args = parser.parse_args()

    print(f"[1/2] 清空部署目录(保护 git): {DEPLOY}")
    clean_deploy_dir(DEPLOY)

    print(f"[2/2] 复制构建产物: {DIST_SRC} -> {DEPLOY}")
    count = copy_dist(DIST_SRC, DEPLOY)
    print(f"[完成] 已复制 {count} 个文件")

    problems = check_relative_paths(DEPLOY)
    if problems:
        print("[警告] 存在非相对路径引用:")
        for p in problems[:20]:
            print("  -", p)
    else:
        print("[通过] 全部资源引用为相对路径")

    if args.password:
        print("[提示] 加密功能将在后续版本实现, 当前未加密")

    print(f"[完成] 站点已就绪: http://127.0.0.1:8866")


if __name__ == "__main__":
    main()
