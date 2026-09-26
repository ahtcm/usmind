# -*- mode: python ; coding: utf-8 -*-
# USMIND EXE 打包配置 (PyInstaller onedir 模式)
from PyInstaller.utils.hooks import collect_all

# PySide6 需收集全部 Qt 插件与翻译
datas, binaries, hiddenimports = collect_all("PySide6", filter_submodules=True)

a = Analysis(
    ["main.py"],
    pathex=[],
    binaries=binaries,
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    runtime_hooks=[],
    excludes=["tkinter"],
    noarchive=False,
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="usmind",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    name="usmind",
)
