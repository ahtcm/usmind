# -*- coding: utf-8 -*-
"""
USMIND 日志: 写入 EXE 同级 logs/usmind.log, 同时回调给 UI 文本框。
"""
import os
import sys
import logging
from logging.handlers import RotatingFileHandler

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from usmind_config import logs_dir

_ui_callback = None


def init_logger():
    global logger
    logger = logging.getLogger("usmind")
    logger.setLevel(logging.INFO)
    logger.handlers = []
    fmt = logging.Formatter("%(asctime)s [%(levelname)s] %(message)s", "%H:%M:%S")
    try:
        fh = RotatingFileHandler(
            os.path.join(logs_dir(), "usmind.log"), maxBytes=2 * 1024 * 1024, backupCount=2, encoding="utf-8"
        )
        fh.setFormatter(fmt)
        logger.addHandler(fh)
    except Exception:
        pass
    return logger


def bind_ui(cb):
    global _ui_callback
    _ui_callback = cb


def log(msg, level="info"):
    logger = logging.getLogger("usmind")
    getattr(logger, level, logger.info)(msg)
    if _ui_callback:
        try:
            _ui_callback(msg)
        except Exception:
            pass
