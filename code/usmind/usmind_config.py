# -*- coding: utf-8 -*-
"""
USMIND 配置持久化
所有配置保存于 EXE 同级目录的 config/config.json, 复制 config 文件夹即可迁移。
"""
import os
import sys
import json

DEFAULTS = {
    "server_port": 8899,   # 编辑服务端口(避开 nginx 8866 部署端口)
    "repo_path": "",  # 启动时若为空则默认 EXE 同级 usmind 目录
    "repo_remote": "https://gitee.com/carwang/usmind.git,https://github.com/ahtcm/usmind.git",
    "repo_user": "",
    "repo_token": "",
    "auto_save_interval": 5,        # web 端自动保存间隔(秒)
    "undo_steps": 60,               # 最大撤销步数
    "resource_prefix": "https://usmind.netlify.app/",  # 线上资源 URL 前缀(保存时 source/ 相对路径改写为线上地址)
    "ollama_enabled": False,
    "ollama_url": "http://127.0.0.1:11434/v1",
    "third_ai_enabled": False,
    "third_ai_url": "",
    "third_ai_key": "",
    "oss_enabled": False,
    "oss_domain": "",
    "oss_key": "",
    "oss_secret": "",
    "oss_path": "",
}


def app_base_dir():
    """EXE 运行时取 EXE 所在目录; 开发时取本文件目录(再上一级为项目根)。"""
    if getattr(sys, "frozen", False):
        return os.path.dirname(os.path.abspath(sys.executable))
    return os.path.dirname(os.path.abspath(__file__))


def config_dir():
    d = os.path.join(app_base_dir(), "config")
    os.makedirs(d, exist_ok=True)
    return d


def logs_dir():
    d = os.path.join(app_base_dir(), "logs")
    os.makedirs(d, exist_ok=True)
    return d


def config_path():
    return os.path.join(config_dir(), "config.json")


def load_config():
    cfg = dict(DEFAULTS)
    try:
        if os.path.exists(config_path()):
            with open(config_path(), encoding="utf-8") as f:
                cfg.update(json.load(f))
    except Exception:
        pass
    # 仓库路径: 空或相对(./xxx) => 解析为 EXE 同级 usmind 目录(换电脑免改配置)
    rp = cfg.get("repo_path") or ""
    if not rp or rp.strip().startswith("."):
        cfg["repo_path"] = os.path.join(app_base_dir(), "usmind")
    return cfg


def save_config(cfg):
    try:
        with open(config_path(), "w", encoding="utf-8") as f:
            json.dump(cfg, f, ensure_ascii=False, indent=2)
        return True
    except Exception:
        return False
