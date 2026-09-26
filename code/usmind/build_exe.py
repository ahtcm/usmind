# -*- coding: utf-8 -*-
"""
USMIND EXE 打包脚本 (PyInstaller onefile, 目标电脑无需任何环境)
产物: 项目根 release/USMIND.exe
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)  # code/
PROJECT = os.path.dirname(ROOT)  # Desktop/usmind
DIST_WEB = os.path.join(ROOT, "web", "dist")

os.chdir(HERE)
assert os.path.isdir(DIST_WEB), "web 构建产物缺失, 请先 npm run build: %s" % DIST_WEB

import PyInstaller.__main__

PyInstaller.__main__.run([
    os.path.join(HERE, "usmind_app.py"),
    "--onefile",
    "--windowed",
    "--name", "USMIND",
    "--add-data", "%s;web" % DIST_WEB,
    "--distpath", os.path.join(PROJECT, "release"),
    "--workpath", os.path.join(PROJECT, "logs", "pyinstaller"),
    "--specpath", os.path.join(PROJECT, "logs", "pyinstaller"),
    "--clean",
    "--noconfirm",
])
print("打包完成: %s" % os.path.join(PROJECT, "release", "USMIND.exe"))
