# -*- coding: utf-8 -*-
"""
USMIND 同步: 编译静态项目(复制构建产物 + 注入分享数据) + git 提交推送(内置 dulwich, 目标电脑无需安装 git)。
"""
import os
import sys
import json
import shutil

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from usmind_log import log


def resource_path(name):
    """打包后取 _MEIPASS 内资源; 开发时取 code/web/dist。"""
    if getattr(sys, "frozen", False):
        return os.path.join(sys._MEIPASS, name)
    return os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "web", "dist")


def build_repo(repo_path, tabs, doc_index, resource_prefix=""):
    """复制 web 构建产物到仓库根 + 生成注入数据的 share.html(分享入口)。resource_prefix: 线上资源前缀, 注入时把 source/ 相对路径改写为线上地址。"""
    dist = resource_path("web")
    if not os.path.isdir(dist):
        raise FileNotFoundError("web 构建产物缺失: %s" % dist)
    os.makedirs(repo_path, exist_ok=True)
    # 1. index.html + assets(编辑入口, 不含注入数据)
    shutil.copy2(os.path.join(dist, "index.html"), os.path.join(repo_path, "index.html"))
    dst_assets = os.path.join(repo_path, "assets")
    if os.path.isdir(dst_assets):
        shutil.rmtree(dst_assets, ignore_errors=True)
    shutil.copytree(os.path.join(dist, "assets"), dst_assets)
    # 2. share.html: 复制 index.html 并注入分享数据
    with open(os.path.join(repo_path, "index.html"), encoding="utf-8") as f:
        html = f.read()
    payload = json.dumps({"tabs": tabs, "docIndex": doc_index}, ensure_ascii=False)
    # 保存时把资源相对路径(source/)改写为线上 URL(分享页公网可见)
    prefix = (resource_prefix or "").rstrip("/")
    if prefix:
        payload = payload.replace('"source/', '"%s/source/' % prefix)
        payload = payload.replace('src="source/', 'src="%s/source/' % prefix)
        payload = payload.replace('href="source/', 'href="%s/source/' % prefix)
    inject = "<script>window.__USMIND_INIT__=" + payload + "</script>"
    marker = '<script type="module"'
    if marker in html:
        html = html.replace(marker, inject + "\n" + marker, 1)
    else:
        html = inject + "\n" + html
    # 首页也注入数据: 线上首页直接渲染已保存导图, 本地编辑走 localStorage 优先
    with open(os.path.join(repo_path, "index.html"), "w", encoding="utf-8") as f:
        f.write(html)
    with open(os.path.join(repo_path, "share.html"), "w", encoding="utf-8") as f:
        f.write(html)
    # 3. 数据备份(便于反向导入)
    data_dir = os.path.join(repo_path, "data")
    os.makedirs(data_dir, exist_ok=True)
    with open(os.path.join(data_dir, "usmind.json"), "w", encoding="utf-8") as f:
        json.dump({"tabs": tabs, "docIndex": doc_index}, f, ensure_ascii=False, indent=2)
    log("已编译: index.html + assets + share.html(含数据)")


def git_sync(repo_path, remote_url, user, token):
    """提交并推送到远程仓库(内置 dulwich, 无需系统 git)。"""
    from dulwich import porcelain

    git_dir = os.path.join(repo_path, ".git")
    if not os.path.isdir(git_dir):
        porcelain.init(repo_path)
        log("仓库不存在, 已初始化: %s" % repo_path)
    try:
        porcelain.add(repo_path, ".")
    except Exception as e:
        log("暂存失败: %s" % e, level="warning")
    try:
        porcelain.commit(repo_path, message="USMIND 自动同步", author="USMIND <usmind@local>")
        log("已提交本地变更")
    except Exception:
        log("无新变更, 跳过提交")
    urls = [u.strip() for u in (remote_url or "").split(",") if u.strip()]
    ok, errs = [], []

    # 1) 优先用系统 git 按已配置的 remote 推送(支持 SSH/密钥, 双端 origin+github)
    import subprocess
    try:
        r = subprocess.run(
            ["git", "-C", repo_path, "remote"],
            capture_output=True, text=True, timeout=30,
        )
        remotes = [x.strip() for x in (r.stdout or "").splitlines() if x.strip()]
    except Exception:
        remotes = []
    for rm in remotes:
        try:
            rr = subprocess.run(
                ["git", "-C", repo_path, "push", rm, "master"],
                capture_output=True, text=True, timeout=120,
            )
            if rr.returncode == 0:
                log("已推送到 remote %s" % rm)
                ok.append(rm)
            else:
                errs.append("%s -> %s" % (rm, (rr.stderr or rr.stdout).strip()[-200:]))
        except Exception as e:
            errs.append("%s -> %s" % (rm, e))

    # 2) 系统 git 不可用或全部失败时, 回退 dulwich 按配置 URL 推送(HTTPS+Token)
    if not ok:
        cred = None
        if user and token:
            cred = (user, token)
        elif token:
            cred = (token, token)
        for url in urls:
            try:
                porcelain.push(repo_path, url, "master", credentials=cred)
                log("已推送到远程仓库: %s" % url)
                ok.append(url)
            except Exception as e:
                log("推送失败 %s: %s" % (url, e), level="error")
                errs.append("%s -> %s" % (url, e))

    if ok and not errs:
        return True, "推送成功: " + ", ".join(ok)
    if ok:
        return True, "部分成功: " + ", ".join(ok) + " | 失败: " + "; ".join(errs)
    return False, "推送失败: " + "; ".join(errs)
