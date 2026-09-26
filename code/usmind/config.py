# -*- coding: utf-8 -*-
"""USMIND 配置管理：读写 EXE 同级 config/config.json，持久化并可迁移"""
import json
import os

CONFIG_DIR_NAME = "config"
CONFIG_FILE_NAME = "config.json"

DEFAULTS = {
    # 必填
    "repo_url": "",          # Gitee/GitHub 仓库地址
    # 本地 AI (Ollama)
    "ai_enabled": True,
    "ollama_url": "http://127.0.0.1:11434",
    "ollama_model": "qwen2.5",
    # 第三方 AI
    "thirdparty_enabled": False,
    "thirdparty_base_url": "",
    "thirdparty_api_key": "",
    "thirdparty_model": "",
    # OSS 对象存储
    "oss_enabled": False,
    "oss_endpoint": "",
    "oss_access_key": "",
    "oss_secret_key": "",
    "oss_bucket": "",
    "oss_upload_path": "",
    "oss_public_prefix": "",
    # 运行参数
    "autosave_interval": 5,   # 分钟
    "max_undo_steps": 100,
    "resource_url_prefix": "",  # 本地存储模式下线上资源前缀
    # 开关
    "autosave_enabled": True,
}


class Config:
    def __init__(self, exe_dir: str):
        self.exe_dir = exe_dir
        self.config_dir = os.path.join(exe_dir, CONFIG_DIR_NAME)
        self.config_file = os.path.join(self.config_dir, CONFIG_FILE_NAME)
        self.data = dict(DEFAULTS)
        self.load()

    def load(self):
        if os.path.isfile(self.config_file):
            try:
                with open(self.config_file, "r", encoding="utf-8") as f:
                    loaded = json.load(f)
                if isinstance(loaded, dict):
                    # 合并: 已有配置优先，缺省字段补默认值
                    merged = dict(DEFAULTS)
                    merged.update(loaded)
                    self.data = merged
            except Exception as e:
                print(f"[配置] 读取失败，使用默认值: {e}")

    def save(self):
        os.makedirs(self.config_dir, exist_ok=True)
        try:
            with open(self.config_file, "w", encoding="utf-8") as f:
                json.dump(self.data, f, ensure_ascii=False, indent=2)
            return True
        except Exception as e:
            print(f"[配置] 保存失败: {e}")
            return False

    def get(self, key: str, default=None):
        return self.data.get(key, default)

    def set(self, key: str, value):
        self.data[key] = value
