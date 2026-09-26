# -*- coding: utf-8 -*-
"""
USMIND · 思维导图运行中枢 (EXE 主程序)
功能: 配置持久化(config/) | 内置服务(托管网页编辑) | 一键保存同步(编译+推送) | 日志(logs/)
"""
import os
import sys
import threading

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import ctypes
import tkinter as tk
from tkinter import filedialog

import ttkbootstrap as ttk
from ttkbootstrap.constants import *

from usmind_config import load_config, save_config, DEFAULTS
from usmind_log import init_logger, bind_ui, log
from usmind_server import Server
from usmind_sync import git_sync

APP_TITLE = "USMIND · 思维导图运行中枢"
APP_WIDTH, APP_HEIGHT = 980, 700


def set_dark_titlebar(win):
    """标题栏与深色主体同色(Windows 10/11 DWM 沉浸式深色标题栏)。"""
    def _apply():
        try:
            from ctypes import windll
            hwnd = windll.user32.GetAncestor(win.winfo_id(), 2)  # GA_ROOT: 真实顶层窗口
            if not hwnd:
                hwnd = win.winfo_id()
            val = ctypes.c_int(1)
            for attr in (20, 19):  # DWMWA_USE_IMMERSIVE_DARK_MODE: Win11=20, Win10=19
                try:
                    r = windll.dwmapi.DwmSetWindowAttribute(hwnd, attr, ctypes.byref(val), ctypes.sizeof(val))
                    if r == 0:
                        break
                except Exception:
                    continue
        except Exception:
            pass
    # 窗口显示后再应用, 确保句柄有效
    win.after(300, _apply)


class App:
    def __init__(self):
        self.root = ttk.Window(themename="darkly")
        self.root.title(APP_TITLE)
        self.root.geometry(f"{APP_WIDTH}x{APP_HEIGHT}")
        self.root.minsize(860, 600)
        set_dark_titlebar(self.root)
        self.cfg = load_config()
        self.server = Server(self.cfg.get("server_port", 8866))
        bind_ui(self.log_line)
        init_logger()
        log("USMIND 运行中枢启动")
        self._build_ui()
        self._fill_form()
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)
        # 启动后自动拉起服务
        self.root.after(300, self.on_start_server)
        self.root.after(1500, self.auto_pull)

    # ---------- UI ----------
    def _build_ui(self):
        # 顶部状态条
        head = ttk.Frame(self.root, padding=(16, 10))
        head.pack(fill=X)
        ttk.Label(head, text="USMIND", font=("Microsoft YaHei UI", 17, "bold")).pack(side=LEFT)
        ttk.Label(head, text="思维导图运行中枢 · 本地编辑 / 云端托管 / 一键分享", foreground="#8a93a5").pack(side=LEFT, padx=(10, 0))
        self.status_dot = ttk.Label(head, text="●", foreground="#6c757d", font=("Microsoft YaHei UI", 13))
        self.status_dot.pack(side=RIGHT)
        self.status_lbl = ttk.Label(head, text="服务未启动", foreground="#8a93a5")
        self.status_lbl.pack(side=RIGHT, padx=(0, 8))
        self.port_lbl = ttk.Label(head, text="", foreground="#4caf50")
        self.port_lbl.pack(side=RIGHT, padx=(0, 12))

        nb = ttk.Notebook(self.root, padding=0)
        nb.pack(fill=BOTH, expand=True, padx=12, pady=(4, 8))

        self.f_service = self._tab_service(nb)
        self.f_repo = self._tab_repo(nb)
        self.f_ai = self._tab_ai(nb)
        self.f_oss = self._tab_oss(nb)
        self.f_param = self._tab_param(nb)
        self.f_log = self._tab_log(nb)

    def _row(self, parent, label, make_widget, placeholder=None):
        r = ttk.Frame(parent)
        r.pack(fill=X, pady=4)
        r.columnconfigure(1, weight=1)
        ttk.Label(r, text=label, width=16).grid(row=0, column=0, sticky=W, padx=(0, 8))
        w = make_widget(r)
        try:
            w.configure(bootstyle="light")
        except Exception:
            pass
        if placeholder:
            try:
                w.configure(placeholder=placeholder)
            except Exception:
                pass
        w.grid(row=0, column=1, sticky="ew")
        return w

    def _tab_service(self, nb):
        f = ttk.Frame(nb, padding=18)
        nb.add(f, text="  服务  ")
        ttk.Label(f, text="运行中枢", font=("Microsoft YaHei UI", 14, "bold")).pack(anchor=W)
        ttk.Label(f, text="启动后自动托管网页编辑端, 保存时自动编译+同步到仓库", foreground="#8a93a5").pack(anchor=W, pady=(2, 14))
        self.port_var = tk.StringVar(value=str(self.cfg.get("server_port", 8866)))
        self._row(f, "服务端口", lambda r: ttk.Entry(r, textvariable=self.port_var, width=10))
        btns = ttk.Frame(f)
        btns.pack(fill=X, pady=(14, 4))
        self.btn_start = ttk.Button(btns, text="启动服务", bootstyle="success", command=self.on_start_server)
        self.btn_start.pack(side=LEFT, padx=4)
        self.btn_stop = ttk.Button(btns, text="停止服务", bootstyle="secondary", command=self.on_stop_server)
        self.btn_stop.pack(side=LEFT, padx=4)
        self.btn_open = ttk.Button(btns, text="打开编辑页", bootstyle="info", command=self.on_open_browser)
        self.btn_open.pack(side=LEFT, padx=4)
        ttk.Label(f, text="使用方式: 启动服务 → 浏览器打开编辑页 → 编辑导图 → 自动保存同步至仓库并生成分享页 share.html",
                  foreground="#8a93a5", wraplength=820, justify=LEFT).pack(anchor=W, pady=(10, 0))
        return f

    def _tab_repo(self, nb):
        f = ttk.Frame(nb, padding=18)
        nb.add(f, text="  仓库同步  ")
        ttk.Label(f, text="仓库同步", font=("Microsoft YaHei UI", 14, "bold")).pack(anchor=W)
        ttk.Label(f, text="保存后自动编译静态项目并推送到 Gitee/GitHub 仓库 (内置 git, 无需安装)", foreground="#8a93a5").pack(anchor=W, pady=(2, 14))
        self.repo_path_var = tk.StringVar(value=self.cfg.get("repo_path", ""))
        row = ttk.Frame(f); row.pack(fill=X, pady=4)
        ttk.Label(row, text="仓库路径", width=16).pack(side=LEFT, anchor=W)
        ttk.Entry(row, textvariable=self.repo_path_var).pack(side=LEFT, fill=X, expand=True)
        ttk.Button(row, text="浏览", bootstyle="secondary", command=self._pick_repo).pack(side=LEFT, padx=6)
        self.repo_remote_var = tk.StringVar(value=self.cfg.get("repo_remote", ""))
        self._row(f, "远程地址", lambda r: ttk.Entry(r, textvariable=self.repo_remote_var))
        self.repo_user_var = tk.StringVar(value=self.cfg.get("repo_user", ""))
        self._row(f, "用户名", lambda r: ttk.Entry(r, textvariable=self.repo_user_var))
        self.repo_token_var = tk.StringVar(value=self.cfg.get("repo_token", ""))
        self._row(f, "访问令牌", lambda r: ttk.Entry(r, textvariable=self.repo_token_var, show="*"))
        ttk.Button(f, text="立即同步", bootstyle="primary", command=self.on_sync_now).pack(anchor=W, pady=(12, 0))
        return f

    def _tab_ai(self, nb):
        f = ttk.Frame(nb, padding=18)
        nb.add(f, text="  AI 配置  ")
        ttk.Label(f, text="AI 智能辅助 (可插拔)", font=("Microsoft YaHei UI", 14, "bold")).pack(anchor=W)
        ttk.Label(f, text="本地离线 Ollama 与在线第三方 API 自由切换, 按需启用", foreground="#8a93a5").pack(anchor=W, pady=(2, 14))
        self.ollama_on = tk.BooleanVar(value=bool(self.cfg.get("ollama_enabled")))
        self.ollama_url_var = tk.StringVar(value=self.cfg.get("ollama_url", ""))
        ttk.Checkbutton(f, text="启用本地 Ollama (离线, 数据不出本机)", variable=self.ollama_on).pack(anchor=W, pady=(0, 4))
        self._row(f, "Ollama 地址", lambda r: ttk.Entry(r, textvariable=self.ollama_url_var), "http://127.0.0.1:11434/v1")
        ttk.Separator(f).pack(fill=X, pady=10)
        self.third_on = tk.BooleanVar(value=bool(self.cfg.get("third_ai_enabled")))
        self.third_url_var = tk.StringVar(value=self.cfg.get("third_ai_url", ""))
        self.third_key_var = tk.StringVar(value=self.cfg.get("third_ai_key", ""))
        ttk.Checkbutton(f, text="启用在线第三方 AI (云端大模型)", variable=self.third_on).pack(anchor=W, pady=(0, 4))
        self._row(f, "API 地址", lambda r: ttk.Entry(r, textvariable=self.third_url_var), "https://api.xxx.com/v1")
        self._row(f, "鉴权密钥", lambda r: ttk.Entry(r, textvariable=self.third_key_var, show="*"), "sk-...")
        return f

    def _tab_oss(self, nb):
        f = ttk.Frame(nb, padding=18)
        nb.add(f, text="  OSS 存储  ")
        ttk.Label(f, text="资源存储 (本地/OSS 双模式)", font=("Microsoft YaHei UI", 14, "bold")).pack(anchor=W)
        ttk.Label(f, text="开启后新增资源自动上传至 OSS, 本地预览直接加载线上链接", foreground="#8a93a5").pack(anchor=W, pady=(2, 14))
        self.oss_on = tk.BooleanVar(value=bool(self.cfg.get("oss_enabled")))
        ttk.Checkbutton(f, text="启用私有 OSS 对象存储", variable=self.oss_on).pack(anchor=W, pady=(0, 8))
        self.oss_domain_var = tk.StringVar(value=self.cfg.get("oss_domain", ""))
        self._row(f, "OSS 域名", lambda r: ttk.Entry(r, textvariable=self.oss_domain_var))
        self.oss_key_var = tk.StringVar(value=self.cfg.get("oss_key", ""))
        self._row(f, "AccessKey", lambda r: ttk.Entry(r, textvariable=self.oss_key_var))
        self.oss_secret_var = tk.StringVar(value=self.cfg.get("oss_secret", ""))
        self._row(f, "Secret", lambda r: ttk.Entry(r, textvariable=self.oss_secret_var, show="*"))
        self.oss_path_var = tk.StringVar(value=self.cfg.get("oss_path", ""))
        self._row(f, "上传路径", lambda r: ttk.Entry(r, textvariable=self.oss_path_var))
        return f

    def _tab_param(self, nb):
        f = ttk.Frame(nb, padding=18)
        nb.add(f, text="  运行参数  ")
        ttk.Label(f, text="运行参数 (保存后生效)", font=("Microsoft YaHei UI", 14, "bold")).pack(anchor=W)
        ttk.Label(f, text="自定义编辑端与同步行为", foreground="#8a93a5").pack(anchor=W, pady=(2, 14))
        self.auto_save_var = tk.StringVar(value=str(self.cfg.get("auto_save_interval", 5)))
        self._row(f, "自动保存间隔(秒)", lambda r: ttk.Entry(r, textvariable=self.auto_save_var))
        self.undo_var = tk.StringVar(value=str(self.cfg.get("undo_steps", 60)))
        self._row(f, "最大撤销步数", lambda r: ttk.Entry(r, textvariable=self.undo_var))
        self.prefix_var = tk.StringVar(value=self.cfg.get("resource_prefix", ""))
        self._row(f, "线上资源URL前缀", lambda r: ttk.Entry(r, textvariable=self.prefix_var))
        ttk.Button(f, text="保存配置", bootstyle="success", command=self.on_save_config).pack(anchor=W, pady=(14, 0))
        ttk.Label(f, text="提示: 所有配置保存在程序同级 config/ 文件夹, 复制该文件夹即可迁移到新电脑", foreground="#8a93a5").pack(anchor=W, pady=(10, 0))
        return f

    def _tab_log(self, nb):
        f = ttk.Frame(nb, padding=10)
        nb.add(f, text="  日志  ")
        self.log_text = tk.Text(f, bg="#10131a", fg="#c8d0da", insertbackground="#c8d0da",
                                relief="flat", font=("Consolas", 10), wrap="none", state="disabled")
        self.log_text.pack(fill=BOTH, expand=True)
        ttk.Button(f, text="清空日志", bootstyle="secondary", command=lambda: self._set_log("")).pack(anchor=E, pady=(6, 0))
        return f

    # ---------- 逻辑 ----------
    def _fill_form(self):
        pass  # 表单已绑定 cfg 初值; 端口在 service 页

    def _pick_repo(self):
        d = filedialog.askdirectory(title="选择仓库目录")
        if d:
            self.repo_path_var.set(d)

    def _collect(self):
        cfg = dict(DEFAULTS)
        cfg["server_port"] = int(self.port_var.get() or 8866)
        cfg["repo_path"] = self.repo_path_var.get().strip()
        cfg["repo_remote"] = self.repo_remote_var.get().strip()
        cfg["repo_user"] = self.repo_user_var.get().strip()
        cfg["repo_token"] = self.repo_token_var.get().strip()
        cfg["ollama_enabled"] = bool(self.ollama_on.get())
        cfg["ollama_url"] = self.ollama_url_var.get().strip()
        cfg["third_ai_enabled"] = bool(self.third_on.get())
        cfg["third_ai_url"] = self.third_url_var.get().strip()
        cfg["third_ai_key"] = self.third_key_var.get().strip()
        cfg["oss_enabled"] = bool(self.oss_on.get())
        cfg["oss_domain"] = self.oss_domain_var.get().strip()
        cfg["oss_key"] = self.oss_key_var.get().strip()
        cfg["oss_secret"] = self.oss_secret_var.get().strip()
        cfg["oss_path"] = self.oss_path_var.get().strip()
        try:
            cfg["auto_save_interval"] = int(self.auto_save_var.get() or 5)
            cfg["undo_steps"] = int(self.undo_var.get() or 60)
        except ValueError:
            pass
        cfg["resource_prefix"] = self.prefix_var.get().strip()
        return cfg

    def on_save_config(self):
        self.cfg = self._collect()
        if save_config(self.cfg):
            log("配置已保存 → config/config.json")
        else:
            log("配置保存失败", level="error")

    def auto_pull(self):
        # 启动时自动拉取远程仓库(与云端保持一致, 换电脑/同步前先拉最新)
        try:
            repo = self.cfg.get("repo_path", "")
            if not repo or not os.path.isdir(os.path.join(repo, ".git")):
                return
            import subprocess
            r = subprocess.run(["git", "-C", repo, "pull"], capture_output=True, text=True, timeout=60)
            if r.returncode == 0:
                log("启动时已拉取远程仓库")
            else:
                log("拉取提示: %s" % ((r.stderr or r.stdout).strip()[:120]), level="warning")
        except Exception as e:
            log("自动拉取跳过: %s" % e, level="warning")

    def on_start_server(self):
        port = self.port_var.get().strip() or "8866"
        try:
            port = int(port)
        except ValueError:
            port = 8866
        if self.server.httpd:
            log("服务已在运行")
            return
        self.server = Server(port)
        if self.server.start():
            self.cfg = self._collect()
            self.cfg["server_port"] = port
            save_config(self.cfg)
            self.status_dot.configure(foreground="#4caf50")
            self.status_lbl.configure(text="服务运行中", foreground="#4caf50")
            self.port_lbl.configure(text="http://127.0.0.1:%s" % port)
            self.btn_start.configure(state="disabled")
            self.btn_stop.configure(state="normal")

    def on_stop_server(self):
        if not self.server.httpd:
            log("服务未运行")
            return
        self.server.stop()
        self.status_dot.configure(foreground="#6c757d")
        self.status_lbl.configure(text="服务未启动", foreground="#8a93a5")
        self.port_lbl.configure(text="")
        self.btn_start.configure(state="normal")
        self.btn_stop.configure(state="disabled")
        self._push_async("停止服务后推送")

    def on_open_browser(self):
        if self.server.httpd:
            self.server.open_browser()
        else:
            log("请先启动服务", level="warning")

    def on_sync_now(self):
        self.cfg = self._collect()
        save_config(self.cfg)
        repo = self.cfg.get("repo_path", "")
        if not repo:
            log("未配置仓库路径", level="warning")
            return
        threading.Thread(target=lambda: git_sync(repo, self.cfg.get("repo_remote", ""), self.cfg.get("repo_user", ""), self.cfg.get("repo_token", "")), daemon=True).start()

    def _set_log(self, text):
        self.log_text.configure(state="normal")
        self.log_text.delete("1.0", END)
        self.log_text.insert(END, text)
        self.log_text.configure(state="disabled")

    def log_line(self, msg):
        try:
            self.root.after(0, lambda: self._append_log(msg))
        except Exception:
            pass

    def _append_log(self, msg):
        self.log_text.configure(state="normal")
        self.log_text.insert(END, msg + "\n")
        self.log_text.see(END)
        self.log_text.configure(state="disabled")

    def _push_async(self, reason=""):
        self.cfg = self._collect()
        repo = self.cfg.get("repo_path", "")
        if not repo:
            return
        threading.Thread(
            target=lambda: git_sync(repo, self.cfg.get("repo_remote", ""), self.cfg.get("repo_user", ""), self.cfg.get("repo_token", "")),
            daemon=True,
        ).start()

    def _on_close(self):
        try:
            self.server.stop()
        except Exception:
            pass
        # 关闭时强制推送一次(保证资源URL改写后的最新数据上云): 隐藏窗口->后台推送->完成后再退出
        repo = self.cfg.get("repo_path", "")
        if repo:
            try:
                from usmind_sync import git_sync as _gs
                args = (repo, self.cfg.get("repo_remote", ""), self.cfg.get("repo_user", ""), self.cfg.get("repo_token", ""))

                def _push_then_exit():
                    try:
                        _gs(*args)
                    except Exception:
                        pass
                    try:
                        self.root.after(0, self.root.destroy)
                    except Exception:
                        pass

                threading.Thread(target=_push_then_exit, daemon=True).start()
                self.root.withdraw()          # 立即隐藏窗口(用户感知已关闭)
                self.root.after(45000, self.root.destroy)  # 兜底: 45秒后强制退出
            except Exception:
                self.root.destroy()
        else:
            self.root.destroy()


def main():
    # 单实例保护: 重复启动时提示并退出
    try:
        import ctypes
        mutex = ctypes.windll.kernel32.CreateMutexW(None, False, "Global\\USMIND_RunMutex")
        if ctypes.windll.kernel32.GetLastError() == 183:  # ERROR_ALREADY_EXISTS
            try:
                import tkinter.messagebox as mb
                mb.showwarning(APP_TITLE, "USMIND 已在运行中, 请勿重复启动")
            except Exception:
                pass
            return
    except Exception:
        pass
    a = App()
    a.root.mainloop()


if __name__ == "__main__":
    main()
