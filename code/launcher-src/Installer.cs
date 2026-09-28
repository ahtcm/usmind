// USMIND 在线安装器
// 运行后从 CDN(国内加速) 下载环境分片 -> 拼接解压 -> 组装 -> 启动 USMIND.exe
// 内嵌 manifest.json 作为资源 Installer.manifest
// 用法: USMIND-online.exe [--dir 安装目录]  (默认 %LOCALAPPDATA%\USMIND-online)
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Reflection;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows.Forms;

class OnlineInstaller {
    // 下载源：jsDelivr CDN（GitHub 公开仓库 v1 tag，国内加速）；可用环境变量 USMIND_BASE_URL 覆盖
    const string BASE_URL = "https://cdn.jsdelivr.net/gh/ahtcm/usmind@v1/release/online-dist";

    static string BaseUrl() {
        string v = Environment.GetEnvironmentVariable("USMIND_BASE_URL");
        return string.IsNullOrEmpty(v) ? BASE_URL : v;
    }

    static string targetDir;
    static string tmpDir;
    static long totalBytes = 0, doneBytes = 0;
    static bool cancelled = false;

    static Form form;
    static ProgressBar pb;
    static Label lblTitle;
    static Label lblStatus;
    static Button btnCancel;

    static List<Comp> comps = new List<Comp>();

    class Comp { public string comp; public string dir; public List<Part> parts = new List<Part>(); }
    class Part { public string file; public long size; }

    [STAThread]
    static int Main(string[] args) {
        targetDir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "USMIND-online");
        for (int i = 0; i < args.Length - 1; i++) {
            if (args[i] == "--dir") targetDir = args[i + 1];
        }
        tmpDir = Path.Combine(Path.GetTempPath(), "usmind-online-dl");

        // ---- 已安装则直接启动 ----
        if (File.Exists(Path.Combine(targetDir, "USMIND.exe"))) {
            Process.Start(new ProcessStartInfo(Path.Combine(targetDir, "USMIND.exe")) { WorkingDirectory = targetDir });
            return 0;
        }

        // ---- 读取内嵌清单 ----
        try {
            using (Stream s = Assembly.GetExecutingAssembly().GetManifestResourceStream("Installer.manifest")) {
                if (s == null) { MessageBox.Show("错误：安装包内缺少清单文件。", "USMIND 在线安装器", MessageBoxButtons.OK, MessageBoxIcon.Error); return 1; }
                using (var sr = new StreamReader(s, Encoding.UTF8)) {
                    var json = sr.ReadToEnd();
                    var ser = new JavaScriptSerializer();
                    var arr = ser.Deserialize<Comp[]>(json);
                    foreach (var c in arr) {
                        foreach (var p in c.parts) { totalBytes += p.size; }
                        comps.Add(c);
                    }
                }
            }
        } catch (Exception e) {
            MessageBox.Show("清单解析失败: " + e.Message, "USMIND 在线安装器", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }

        // ---- UI + 后台下载线程 ----
        BuildUI();
        Thread worker = new Thread(DoInstall);
        worker.IsBackground = true;
        worker.Start();
        Application.Run(form);
        return cancelled ? 1 : 0;
    }

    // 后台线程：下载 -> 拼接 -> 解压 -> 组装 -> 启动
    static void DoInstall() {
        try {
            Directory.CreateDirectory(targetDir);
            Directory.CreateDirectory(tmpDir);
            foreach (var c in comps) {
                string zipPath = Path.Combine(tmpDir, c.comp + ".zip");
                using (var fs = new FileStream(zipPath, FileMode.Create, FileAccess.Write)) {
                    foreach (var p in c.parts) {
                        if (cancelled) break;
                        DownloadPart(fs, c.comp, p);
                    }
                }
                if (cancelled) break;
                string destDir = Path.Combine(targetDir, c.dir);
                SetStatus("正在解压 " + c.comp + " ...");
                System.IO.Compression.ZipFile.ExtractToDirectory(zipPath, destDir);
                File.Delete(zipPath);
            }
            if (!cancelled && File.Exists(Path.Combine(targetDir, "USMIND.exe"))) {
                SetStatus("环境准备完成，正在启动 USMIND ...");
                Process.Start(new ProcessStartInfo(Path.Combine(targetDir, "USMIND.exe")) { WorkingDirectory = targetDir });
                form.BeginInvoke(new Action(() => form.Close()));
            } else if (!cancelled) {
                MessageBox.Show("组装失败：未找到 USMIND.exe", "USMIND 在线安装器", MessageBoxButtons.OK, MessageBoxIcon.Error);
                form.BeginInvoke(new Action(() => form.Close()));
            }
        } catch (Exception e) {
            if (!cancelled) {
                MessageBox.Show("安装失败: " + e.Message + "\n\n可稍后重新运行本程序继续下载。", "USMIND 在线安装器",
                    MessageBoxButtons.OK, MessageBoxIcon.Error);
                form.BeginInvoke(new Action(() => form.Close()));
            }
        }
    }

    // 同步下载一个分片（用 WebClient 同步方法，UI 通过事件更新进度）
    static void DownloadPart(FileStream fs, string comp, Part p) {
        string url = BaseUrl() + "/" + p.file;
        string partTmp = Path.Combine(tmpDir, p.file + ".part");
        using (var wc = new WebClient()) {
            wc.Headers[HttpRequestHeader.UserAgent] = "USMIND-Installer/1.0";
            wc.DownloadProgressChanged += (s, e) => {
                long cur = doneBytes + e.BytesReceived;
                int pct = (int)(totalBytes == 0 ? 0 : cur * 100 / totalBytes);
                UpdateProgress(pct, comp, p.file, e.BytesReceived, p.size);
            };
            wc.DownloadFile(new Uri(url), partTmp);
        }
        using (var pf = File.OpenRead(partTmp)) { pf.CopyTo(fs); }
        File.Delete(partTmp);
        doneBytes += p.size;
    }

    // ---------------- UI ----------------
    static void BuildUI() {
        form = new Form();
        form.Text = "USMIND 在线安装器";
        form.FormBorderStyle = FormBorderStyle.FixedSingle;
        form.MaximizeBox = false;
        form.StartPosition = FormStartPosition.CenterScreen;
        form.ClientSize = new Size(460, 170);
        form.BackColor = Color.White;

        lblTitle = new Label();
        lblTitle.Text = "正在从国内 CDN 下载运行环境（首次约 550MB，请耐心等待）";
        lblTitle.SetBounds(20, 18, 420, 28);
        lblTitle.Font = new Font("Microsoft YaHei", 10, FontStyle.Bold);
        lblTitle.ForeColor = Color.FromArgb(51, 65, 85);

        pb = new ProgressBar();
        pb.SetBounds(20, 62, 420, 26);
        pb.Style = ProgressBarStyle.Continuous;

        lblStatus = new Label();
        lblStatus.Text = "准备中...";
        lblStatus.SetBounds(20, 96, 340, 22);
        lblStatus.ForeColor = Color.FromArgb(100, 116, 139);

        btnCancel = new Button();
        btnCancel.Text = "取消";
        btnCancel.SetBounds(384, 96, 56, 26);
        btnCancel.FlatStyle = FlatStyle.Flat;
        btnCancel.BackColor = Color.FromArgb(240, 242, 245);
        btnCancel.Click += (s, e) => { cancelled = true; };

        form.Controls.Add(lblTitle);
        form.Controls.Add(pb);
        form.Controls.Add(lblStatus);
        form.Controls.Add(btnCancel);
        form.FormClosing += (s, e) => { if (!cancelled) cancelled = true; };
    }

    static void UpdateProgress(int pct, string comp, string file, long recv, long total) {
        try {
            if (form.InvokeRequired) {
                form.BeginInvoke(new Action(() => UpdateProgress(pct, comp, file, recv, total)));
                return;
            }
            pb.Value = Math.Min(100, pct);
            lblStatus.Text = string.Format("{0}  {1}  {2:N1}/{3:N1} MB", comp, file, recv / 1048576.0, total / 1048576.0);
        } catch { }
    }

    static void SetStatus(string msg) {
        try {
            if (form.InvokeRequired) { form.BeginInvoke(new Action(() => SetStatus(msg))); return; }
            lblStatus.Text = msg;
        } catch { }
    }
}
