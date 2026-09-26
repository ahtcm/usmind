# -*- coding: utf-8 -*-
"""
USMIND 本地客户端 (EXE)
无边框沉浸式配置中枢：仓库/AI/OSS/运行参数配置 + nginx 启停 + 网页端入口
"""
import os
import sys
import socket
import subprocess
import webbrowser

from PySide6.QtCore import Qt
from PySide6.QtWidgets import (
    QApplication, QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QLineEdit, QSpinBox, QCheckBox, QGroupBox, QFormLayout, QScrollArea,
    QFrame, QMessageBox, QFileDialog
)

from config import Config

PROJECT_NAME = "USMIND"
WEB_URL = "http://127.0.0.1:8866"
NGINX_PORT = 8866

QSS = """
QWidget { background: #1a1c20; color: #e8eaed; font-size: 13px; }
QLineEdit, QSpinBox {
  background: #262a31; border: 1px solid #353a42; border-radius: 6px;
  padding: 6px 8px; color: #e8eaed;
}
QLineEdit:focus, QSpinBox:focus { border-color: #3f6df4; }
QGroupBox {
  border: 1px solid #353a42; border-radius: 8px; margin-top: 12px;
  padding: 10px 8px 8px 8px; font-weight: 600; color: #9aa3af;
}
QGroupBox::title { subcontrol-origin: margin; left: 10px; padding: 0 4px; }
QPushButton {
  background: #2a2e36; border: 1px solid #3a3f4b; border-radius: 6px;
  padding: 7px 14px; color: #e8eaed;
}
QPushButton:hover { background: #333845; }
QPushButton.primary { background: #3f6df4; border-color: #3f6df4; color: #fff; }
QPushButton.primary:hover { background: #5680f6; }
QPushButton.danger { color: #f26d6d; }
QCheckBox { spacing: 6px; }
QCheckBox::indicator { width: 16px; height: 16px; }
QScrollArea { border: none; }
"""


def find_project_root(exe_dir: str):
    """定位项目根：EXE 同级或上级含 script/ 的目录"""
    for base in (exe_dir, os.path.dirname(exe_dir)):
        if os.path.isdir(os.path.join(base, "script")):
            return base
    return exe_dir


def port_listening(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(0.3)
        return s.connect_ex(("127.0.0.1", port)) == 0


class TitleBar(QWidget):
    """自绘薄标题栏：可拖拽窗口"""

    def __init__(self, parent):
        super().__init__(parent)
        self._drag_pos = None
        lay = QHBoxLayout(self)
        lay.setContentsMargins(12, 0, 6, 0)
        lay.setSpacing(6)
        title = QLabel("USMIND 客户端")
        title.setStyleSheet("font-weight:700; color:#3f6df4; font-size:14px;")
        tip = QLabel("配置中枢 · 一键运行")
        tip.setStyleSheet("color:#8a919c; font-size:12px;")
        lay.addWidget(title)
        lay.addWidget(tip)
        lay.addStretch(1)
        self._make_btn(lay, "—", self._on_minimize)
        self._make_btn(lay, "×", self._on_close)

    def _make_btn(self, lay, text, handler):
        btn = QPushButton(text)
        btn.setFixedSize(30, 24)
        btn.setStyleSheet(
            "QPushButton{background:transparent;border:none;color:#8a919c;}"
            "QPushButton:hover{background:rgba(255,255,255,.08);color:#fff;}"
        )
        btn.clicked.connect(handler)
        lay.addWidget(btn)

    def _on_minimize(self):
        self.parent().showMinimized()

    def _on_close(self):
        self.parent().close()

    def mousePressEvent(self, e):
        if e.button() == Qt.LeftButton:
            self._drag_pos = e.globalPosition().toPoint() - self.parent().frameGeometry().topLeft()
            e.accept()

    def mouseMoveEvent(self, e):
        if self._drag_pos and e.buttons() & Qt.LeftButton:
            self.parent().move(e.globalPosition().toPoint() - self._drag_pos)
            e.accept()

    def mouseReleaseEvent(self, e):
        self._drag_pos = None


class MainWindow(QWidget):
    def __init__(self):
        super().__init__()
        self.exe_dir = os.path.dirname(os.path.abspath(sys.executable if getattr(sys, "frozen", False) else __file__))
        self.project_root = find_project_root(self.exe_dir)
        self.config = Config(self.exe_dir)

        self.setWindowTitle(PROJECT_NAME)
        self.setWindowFlags(Qt.FramelessWindowHint | Qt.Window)
        self.resize(560, 720)
        self.setMinimumSize(500, 600)
        self.setStyleSheet(QSS)

        self._build_ui()
        self._load_config_into_ui()

    # ---------- UI ----------
    def _build_ui(self):
        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)
        root.addWidget(TitleBar(self))

        # 状态条
        self.status_bar = QLabel("● 就绪")
        self.status_bar.setStyleSheet(
            "background:#22252b; color:#8a919c; padding:6px 12px; border-bottom:1px solid #353a42;")
        root.addWidget(self.status_bar)

        # 滚动表单
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        body = QWidget()
        body_lay = QVBoxLayout(body)
        body_lay.setContentsMargins(14, 8, 14, 8)
        body_lay.setSpacing(4)

        # --- 必填：仓库 ---
        g_repo = QGroupBox("① 仓库配置（必填）")
        f = QFormLayout(g_repo)
        self.repo_url = QLineEdit()
        self.repo_url.setPlaceholderText("例: git@gitee.com:carwang/usmind.git")
        f.addRow("仓库地址", self.repo_url)
        body_lay.addWidget(g_repo)

        # --- AI 双数据源 ---
        g_ai = QGroupBox("② AI 数据源（可插拔）")
        f = QFormLayout(g_ai)
        self.ai_enabled = QCheckBox("启用 AI 功能")
        f.addRow("", self.ai_enabled)
        self.ollama_url = QLineEdit()
        self.ollama_model = QLineEdit()
        f.addRow("Ollama 地址", self.ollama_url)
        f.addRow("Ollama 模型", self.ollama_model)
        self.third_enabled = QCheckBox("启用第三方 AI")
        f.addRow("", self.third_enabled)
        self.third_url = QLineEdit()
        self.third_key = QLineEdit()
        self.third_key.setEchoMode(QLineEdit.Password)
        self.third_model = QLineEdit()
        f.addRow("接口地址", self.third_url)
        f.addRow("API Key", self.third_key)
        f.addRow("模型", self.third_model)
        body_lay.addWidget(g_ai)

        # --- OSS ---
        g_oss = QGroupBox("③ 对象存储 OSS（可选）")
        f = QFormLayout(g_oss)
        self.oss_enabled = QCheckBox("启用 OSS 存储模式")
        f.addRow("", self.oss_enabled)
        self.oss_endpoint = QLineEdit()
        self.oss_ak = QLineEdit()
        self.oss_sk = QLineEdit()
        self.oss_sk.setEchoMode(QLineEdit.Password)
        self.oss_bucket = QLineEdit()
        self.oss_path = QLineEdit()
        self.oss_prefix = QLineEdit()
        f.addRow("域名/Endpoint", self.oss_endpoint)
        f.addRow("AccessKey", self.oss_ak)
        f.addRow("SecretKey", self.oss_sk)
        f.addRow("Bucket", self.oss_bucket)
        f.addRow("上传路径", self.oss_path)
        f.addRow("公开访问前缀", self.oss_prefix)
        body_lay.addWidget(g_oss)

        # --- 运行参数 ---
        g_param = QGroupBox("④ 运行参数")
        f = QFormLayout(g_param)
        self.autosave_enabled = QCheckBox("启用定时自动保存")
        f.addRow("", self.autosave_enabled)
        self.autosave_interval = QSpinBox()
        self.autosave_interval.setRange(1, 120)
        self.autosave_interval.setSuffix(" 分钟")
        f.addRow("自动保存间隔", self.autosave_interval)
        self.max_undo = QSpinBox()
        self.max_undo.setRange(10, 1000)
        f.addRow("最大撤销步数", self.max_undo)
        self.res_prefix = QLineEdit()
        self.res_prefix.setPlaceholderText("例: https://your-cdn.com/usmind/")
        f.addRow("线上资源前缀", self.res_prefix)
        body_lay.addWidget(g_param)

        # --- 操作按钮 ---
        row = QHBoxLayout()
        btn_save = QPushButton("保存配置")
        btn_save.setProperty("class", "primary")
        btn_save.clicked.connect(self._save)
        btn_open = QPushButton("打开网页端")
        btn_open.clicked.connect(lambda: webbrowser.open(WEB_URL))
        row.addWidget(btn_save)
        row.addWidget(btn_open)
        body_lay.addLayout(row)

        row2 = QHBoxLayout()
        btn_nginx = QPushButton("启动 nginx")
        btn_nginx.clicked.connect(lambda: self._nginx(True))
        btn_nginx_stop = QPushButton("停止 nginx")
        btn_nginx_stop.clicked.connect(lambda: self._nginx(False))
        btn_build = QPushButton("构建并发布")
        btn_build.clicked.connect(self._build)
        row2.addWidget(btn_nginx)
        row2.addWidget(btn_nginx_stop)
        row2.addWidget(btn_build)
        body_lay.addLayout(row2)

        hint = QLabel(
            "提示：配置自动保存到 EXE 同级 config/ 文件夹，复制该文件夹即可迁移。\n"
            "网页端编辑保存后会自动编译并同步到仓库。")
        hint.setStyleSheet("color:#8a919c; font-size:12px; padding-top:8px;")
        body_lay.addWidget(hint)

        scroll.setWidget(body)
        root.addWidget(scroll)

    # ---------- 逻辑 ----------
    def _load_config_into_ui(self):
        c = self.config
        self.repo_url.setText(c.get("repo_url", ""))
        self.ai_enabled.setChecked(c.get("ai_enabled", True))
        self.ollama_url.setText(c.get("ollama_url", ""))
        self.ollama_model.setText(c.get("ollama_model", ""))
        self.third_enabled.setChecked(c.get("thirdparty_enabled", False))
        self.third_url.setText(c.get("thirdparty_base_url", ""))
        self.third_key.setText(c.get("thirdparty_api_key", ""))
        self.third_model.setText(c.get("thirdparty_model", ""))
        self.oss_enabled.setChecked(c.get("oss_enabled", False))
        self.oss_endpoint.setText(c.get("oss_endpoint", ""))
        self.oss_ak.setText(c.get("oss_access_key", ""))
        self.oss_sk.setText(c.get("oss_secret_key", ""))
        self.oss_bucket.setText(c.get("oss_bucket", ""))
        self.oss_path.setText(c.get("oss_upload_path", ""))
        self.oss_prefix.setText(c.get("oss_public_prefix", ""))
        self.autosave_enabled.setChecked(c.get("autosave_enabled", True))
        self.autosave_interval.setValue(int(c.get("autosave_interval", 5)))
        self.max_undo.setValue(int(c.get("max_undo_steps", 100)))
        self.res_prefix.setText(c.get("resource_url_prefix", ""))

    def _save(self):
        c = self.config
        c.set("repo_url", self.repo_url.text().strip())
        c.set("ai_enabled", self.ai_enabled.isChecked())
        c.set("ollama_url", self.ollama_url.text().strip())
        c.set("ollama_model", self.ollama_model.text().strip())
        c.set("thirdparty_enabled", self.third_enabled.isChecked())
        c.set("thirdparty_base_url", self.third_url.text().strip())
        c.set("thirdparty_api_key", self.third_key.text().strip())
        c.set("thirdparty_model", self.third_model.text().strip())
        c.set("oss_enabled", self.oss_enabled.isChecked())
        c.set("oss_endpoint", self.oss_endpoint.text().strip())
        c.set("oss_access_key", self.oss_ak.text().strip())
        c.set("oss_secret_key", self.oss_sk.text().strip())
        c.set("oss_bucket", self.oss_bucket.text().strip())
        c.set("oss_upload_path", self.oss_path.text().strip())
        c.set("oss_public_prefix", self.oss_prefix.text().strip())
        c.set("autosave_enabled", self.autosave_enabled.isChecked())
        c.set("autosave_interval", self.autosave_interval.value())
        c.set("max_undo_steps", self.max_undo.value())
        c.set("resource_url_prefix", self.res_prefix.text().strip())
        if c.save():
            self._set_status("配置已保存")
            QMessageBox.information(self, PROJECT_NAME, "配置已保存到:\n" + c.config_file)
        else:
            self._set_status("保存失败", error=True)

    def _nginx(self, start: bool):
        nginx_dir = os.path.join(self.project_root, "tools", "nginx-1.30.5")
        if start:
            if port_listening(NGINX_PORT):
                self._set_status("nginx 已在运行 (8866)")
                return
            if not os.path.isfile(os.path.join(nginx_dir, "nginx.exe")):
                QMessageBox.warning(self, PROJECT_NAME, "未找到 nginx:\n" + nginx_dir)
                return
            subprocess.Popen(
                [os.path.join(nginx_dir, "nginx.exe")],
                cwd=nginx_dir, creationflags=subprocess.CREATE_NO_WINDOW)
            self._set_status("nginx 已启动 → " + WEB_URL)
        else:
            if not port_listening(NGINX_PORT):
                self._set_status("nginx 未在运行")
                return
            out = subprocess.run(
                ["netstat", "-ano"], capture_output=True, text=True).stdout
            killed = False
            for line in out.splitlines():
                if ":8866" in line and "LISTENING" in line:
                    pid = line.split()[-1]
                    subprocess.run(["taskkill", "/PID", pid, "/F"],
                                   capture_output=True, text=True)
                    killed = True
            self._set_status("nginx 已停止" if killed else "未找到 nginx 进程")

    def _build(self):
        build_bat = os.path.join(self.project_root, "script", "build.bat")
        if not os.path.isfile(build_bat):
            QMessageBox.warning(self, PROJECT_NAME, "未找到构建脚本:\n" + build_bat)
            return
        self._set_status("构建中... 见 logs/build.log")
        subprocess.Popen(["cmd", "/c", build_bat, "web"], cwd=self.project_root)

    def _set_status(self, text, error=False):
        color = "#f26d6d" if error else "#8a919c"
        self.status_bar.setText(("● " if not error else "● ") + text)
        self.status_bar.setStyleSheet(
            f"background:#22252b; color:{color}; padding:6px 12px; border-bottom:1px solid #353a42;")


def main():
    app = QApplication(sys.argv)
    w = MainWindow()
    w.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
