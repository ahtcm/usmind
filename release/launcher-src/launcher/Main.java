package launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * USMIND 便携版可视化启动器（GUI）
 * 双击 USMIND.exe：
 *   1. 幂等初始化并启动 MySQL(3306) -> Redis(6379) -> Nginx(80/8866/8867) -> 后端(8899)
 *   2. 自动打开浏览器 http://127.0.0.1/（80 统一入口）
 *   3. 窗口内可一键停止、切换查看各服务日志、查看服务连接状态
 * 关闭窗口 = 优雅停止所有服务并退出。
 * 支持 --smoke：无界面模式，启动全部服务并健康检查后自动停止（用于构建验证）。
 */
public class Main {

    // ---------------- 常量 ----------------
    static final String[] LOG_NAMES = {"launcher", "backend", "mysql", "redis", "nginx"};
    static final String[] LOG_LABELS = {"启动器", "后端", "MySQL", "Redis", "Nginx"};
    static final Color BG      = new Color(0x1E, 0x1E, 0x1E);
    static final Color CARD    = new Color(0x2A, 0x2A, 0x2A);
    static final Color BORDER  = new Color(0x3A, 0x3A, 0x3A);
    static final Color TEXT    = new Color(0xE0, 0xE0, 0xE0);
    static final Color SUB     = new Color(0x9A, 0x9A, 0x9A);
    static final Color ACCENT  = new Color(0x4A, 0x9E, 0xFF);
    static final Color GREEN   = new Color(0x3F, 0xC9, 0x6B);
    static final Color RED     = new Color(0xE5, 0x4D, 0x4D);

    // ---------------- 运行状态 ----------------
    static File root, components, dataDir, logsDir;
    static Process mysqlProc, redisProc, nginxProc, backendProc;
    static volatile boolean smoke;
    static volatile boolean started;
    static final AtomicBoolean busy = new AtomicBoolean(false);

    // ---------------- GUI ----------------
    static JFrame frame;
    static JLabel statusDot, statusText;
    static JButton btnStart, btnStop, btnBrowser;
    static JComboBox<String> logCombo;
    static JTextArea logArea;
    static JLabel[] cards = new JLabel[4];       // MySQL/Redis/Nginx/后端 状态卡片
    static javax.swing.Timer tailTimer, statusTimer;
    static Map<String, StringBuilder> logCache = new LinkedHashMap<>();
    static Map<String, Long> logPos = new LinkedHashMap<>();
    static String currentLog = "backend";
    static FileLock singleLock;

    public static void main(String[] args) throws Exception {
        smoke = Arrays.asList(args).contains("--smoke");
        locateRoot();
        logsDir = new File(dataDir, "logs");
        logsDir.mkdirs();
        for (String n : LOG_NAMES) { logCache.put(n, new StringBuilder()); logPos.put(n, 0L); }

        if (smoke) { runSmoke(); return; }

        // 单实例锁（防止重复双击起多个服务）
        try {
            FileChannel ch = FileChannel.open(Paths.get(dataDir.getAbsolutePath(), ".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            singleLock = ch.tryLock();
            if (singleLock == null) {
                JOptionPane.showMessageDialog(null, "USMIND 已在运行中，请勿重复启动。", "USMIND", JOptionPane.INFORMATION_MESSAGE);
                System.exit(0);
            }
        } catch (Exception e) { /* 锁获取失败不阻塞启动 */ }

        SwingUtilities.invokeLater(Main::buildUI);
    }

    // =====================================================
    //  GUI
    // =====================================================
    static void buildUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        Font uiFont = new Font("Microsoft YaHei UI", Font.PLAIN, 13);
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object k = keys.nextElement();
            Object v = UIManager.get(k);
            if (v instanceof Font) UIManager.put(k, uiFont);
        }

        frame = new JFrame("USMIND 思维导图 便携版");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { shutdown(); }
        });
        frame.setBackground(BG);
        frame.setSize(960, 640);
        frame.setMinimumSize(new Dimension(760, 480));
        frame.setLocationRelativeTo(null);

        JPanel rootPane = new JPanel(new BorderLayout(0, 0));
        rootPane.setBackground(BG);
        rootPane.setBorder(new EmptyBorder(14, 16, 10, 16));

        // ---------- 顶部：标题 + 状态 ----------
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JPanel titleBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleBox.setOpaque(false);
        JLabel logo = new JLabel("USMIND");
        logo.setFont(uiFont.deriveFont(Font.BOLD, 20f));
        logo.setForeground(ACCENT);
        JLabel sub = new JLabel("思维导图 · 便携版");
        sub.setForeground(SUB);
        titleBox.add(logo); titleBox.add(sub);

        JPanel statusBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        statusBox.setOpaque(false);
        statusDot = new JLabel("●");
        statusDot.setFont(uiFont.deriveFont(Font.BOLD, 16f));
        statusDot.setForeground(RED);
        statusText = new JLabel("未启动");
        statusText.setForeground(TEXT);
        statusBox.add(statusDot); statusBox.add(statusText);
        top.add(titleBox, BorderLayout.WEST);
        top.add(statusBox, BorderLayout.EAST);
        rootPane.add(top, BorderLayout.NORTH);

        // ---------- 中部：服务卡片 + 地址 + 按钮 ----------
        JPanel mid = new JPanel(new BorderLayout(0, 10));
        mid.setOpaque(false);

        // 服务卡片
        JPanel svcRow = new JPanel(new GridLayout(1, 4, 8, 0));
        svcRow.setOpaque(false);
        String[][] svcs = {
            {"MySQL", "3306"}, {"Redis", "6379"}, {"Nginx", "80/8866/8867"}, {"后端", "8899"}
        };
        for (int i = 0; i < 4; i++) {
            cards[i] = makeCard(svcs[i][0], svcs[i][1]);
            svcRow.add(cards[i]);
        }
        mid.add(svcRow, BorderLayout.NORTH);

        // 连接地址
        JPanel addr = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 6));
        addr.setOpaque(false);
        addr.add(makeLink("用户端  http://127.0.0.1/", "http://127.0.0.1/"));
        addr.add(makeLink("管理后台  http://127.0.0.1/admin/", "http://127.0.0.1/admin/"));
        addr.add(makeLink("后端API  http://127.0.0.1/api/", "http://127.0.0.1/api/"));
        mid.add(addr, BorderLayout.CENTER);

        // 按钮
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        btns.setOpaque(false);
        btnStart = makeBtn("启动全部服务", ACCENT);
        btnStop = makeBtn("停止全部服务", new Color(0x55, 0x5F, 0x6E));
        btnBrowser = makeBtn("打开浏览器", new Color(0x55, 0x5F, 0x6E));
        btnStart.addActionListener(e -> startAll());
        btnStop.addActionListener(e -> stopAllGui());
        btnBrowser.addActionListener(e -> openBrowser("http://127.0.0.1/"));
        btnStop.setEnabled(false);
        btns.add(btnStart); btns.add(btnStop); btns.add(btnBrowser);
        mid.add(btns, BorderLayout.SOUTH);

        rootPane.add(mid, BorderLayout.CENTER);

        // ---------- 日志区 ----------
        JPanel logPanel = new JPanel(new BorderLayout(0, 6));
        logPanel.setOpaque(false);
        logPanel.setBorder(new EmptyBorder(6, 0, 0, 0));
        JPanel logBar = new JPanel(new BorderLayout());
        logBar.setOpaque(false);
        JLabel l1 = new JLabel("服务日志");
        l1.setForeground(TEXT);
        logCombo = new JComboBox<>(LOG_LABELS);
        logCombo.setBackground(CARD);
        logCombo.setForeground(TEXT);
        logCombo.setFocusable(false);
        logCombo.addActionListener(e -> switchLog(logCombo.getSelectedIndex()));
        logBar.add(l1, BorderLayout.WEST);
        logBar.add(logCombo, BorderLayout.EAST);
        logPanel.add(logBar, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(0x14, 0x14, 0x14));
        logArea.setForeground(new Color(0xC8, 0xC8, 0xC8));
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane sp = new JScrollPane(logArea);
        sp.setBorder(BorderFactory.createLineBorder(BORDER));
        sp.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            protected void configureScrollBarColors() {
                thumbColor = new Color(0x55, 0x5F, 0x6E);
                trackColor = new Color(0x1E, 0x1E, 0x1E);
            }
        });
        logPanel.add(sp, BorderLayout.CENTER);
        rootPane.add(logPanel, BorderLayout.SOUTH);

        frame.setContentPane(rootPane);
        frame.setVisible(true);
        appendLog("launcher", "USMIND 便携版启动器 v1.0 就绪\n点击「启动全部服务」开始，或关闭窗口退出。\n");
        switchLog(1);

        tailTimer = new javax.swing.Timer(600, e -> tailLogs());
        tailTimer.start();
        statusTimer = new javax.swing.Timer(1500, e -> refreshStatus());
        statusTimer.start();
    }

    static JLabel makeCard(String name, String port) {
        JLabel c = new JLabel();
        c.setOpaque(true);
        c.setBackground(CARD);
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(8, 12, 8, 12)));
        c.setLayout(new BorderLayout(0, 2));
        JLabel nm = new JLabel(name);
        nm.setForeground(TEXT);
        nm.setFont(nm.getFont().deriveFont(Font.BOLD, 14f));
        JLabel info = new JLabel("● 未启动  (" + port + ")");
        info.setForeground(RED);
        c.add(nm, BorderLayout.NORTH);
        c.add(info, BorderLayout.CENTER);
        c.putClientProperty("info", info);
        c.putClientProperty("port", port);
        return c;
    }

    static JLabel makeLink(String text, String url) {
        JLabel l = new JLabel("<html><u>" + text + "</u></html>");
        l.setForeground(ACCENT);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { openBrowser(url); }
        });
        return l;
    }

    static JButton makeBtn(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 13f));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(130, 34));
        return b;
    }

    static void switchLog(int idx) {
        currentLog = LOG_NAMES[idx];
        logArea.setText(logCache.get(currentLog).toString());
        logArea.setCaretPosition(Math.max(0, logArea.getDocument().getLength() - 1));
    }

    static void appendLog(String name, String text) {
        StringBuilder sb = logCache.get(name);
        if (sb == null) return;
        synchronized (sb) {
            sb.append(text);
            if (sb.length() > 400_000) sb.delete(0, sb.length() / 2);
        }
        if (name.equals(currentLog)) {
            SwingUtilities.invokeLater(() -> {
                logArea.setText(logCache.get(currentLog).toString());
                logArea.setCaretPosition(Math.max(0, logArea.getDocument().getLength() - 1));
            });
        }
    }

    static void tailLogs() {
        for (String n : LOG_NAMES) {
            if ("launcher".equals(n)) continue;
            File f = new File(logsDir, n + ".log");
            if (!f.exists()) continue;
            try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                long len = raf.length();
                long pos = logPos.get(n);
                if (len < pos) pos = 0;
                if (len > pos) {
                    raf.seek(pos);
                    byte[] buf = new byte[(int) Math.min(len - pos, 65536)];
                    raf.readFully(buf);
                    appendLog(n, new String(buf, StandardCharsets.UTF_8));
                    logPos.put(n, len);
                }
            } catch (Exception ignored) {}
        }
    }

    static void refreshStatus() {
        boolean[] ok = {
            portOpen("127.0.0.1", 3306),
            portOpen("127.0.0.1", 6379),
            httpOk("http://127.0.0.1:8866/"),
            httpOk("http://127.0.0.1:8899/")
        };
        for (int i = 0; i < 4; i++) {
            JLabel info = (JLabel) cards[i].getClientProperty("info");
            info.setText((ok[i] ? "● 运行中  (" : "● 未启动  (") + cards[i].getClientProperty("port") + ")");
            info.setForeground(ok[i] ? GREEN : RED);
        }
        boolean all = ok[0] && ok[1] && ok[2] && ok[3];
        statusDot.setForeground(all ? GREEN : (started ? new Color(0xF0, 0xA5, 0x2E) : RED));
        statusText.setText(all ? "全部服务运行中" : (started ? "启动中…" : "未启动"));
    }

    // =====================================================
    //  启动 / 停止
    // =====================================================
    static void startAll() {
        if (!busy.compareAndSet(false, true)) return;
        btnStart.setEnabled(false); btnStop.setEnabled(false);
        statusDot.setForeground(new Color(0xF0, 0xA5, 0x2E));
        statusText.setText("启动中…");
        appendLog("launcher", "开始启动全部服务...\n");
        new Thread(() -> {
            try {
                ensureMysqlData();
                startRedis();
                startNginx();
                startBackend();
                boolean ok = waitReady(240);
                started = true;
                SwingUtilities.invokeLater(() -> {
                    statusDot.setForeground(ok ? GREEN : RED);
                    statusText.setText(ok ? "全部服务运行中" : "启动超时，请查看日志");
                    btnStop.setEnabled(true);
                    btnStart.setEnabled(false);
                });
                if (ok) {
                    appendLog("launcher", "服务全部就绪，自动打开浏览器 http://127.0.0.1/ \n");
                    openBrowser("http://127.0.0.1/");
                } else {
                    appendLog("launcher", "[错误] 服务启动超时，请查看各服务日志\n");
                }
            } catch (Exception ex) {
                started = true;
                appendLog("launcher", "[错误] 启动失败: " + ex.getMessage() + "\n");
                SwingUtilities.invokeLater(() -> {
                    statusDot.setForeground(RED);
                    statusText.setText("启动失败");
                    btnStart.setEnabled(true);
                    btnStop.setEnabled(false);
                });
            } finally {
                busy.set(false);
            }
        }).start();
    }

    static void stopAllGui() {
        if (!busy.compareAndSet(false, true)) return;
        btnStart.setEnabled(false); btnStop.setEnabled(false);
        appendLog("launcher", "正在停止全部服务...\n");
        new Thread(() -> {
            try { stopAll(); } catch (Exception ignored) {}
            started = false;
            SwingUtilities.invokeLater(() -> {
                statusDot.setForeground(RED);
                statusText.setText("已停止");
                btnStart.setEnabled(true);
                btnStop.setEnabled(false);
            });
            busy.set(false);
        }).start();
    }

    static void shutdown() {
        appendLog("launcher", "关闭窗口，正在优雅停止服务...\n");
        try { stopAll(); } catch (Exception ignored) {}
        if (tailTimer != null) tailTimer.stop();
        if (statusTimer != null) statusTimer.stop();
        try { if (singleLock != null) singleLock.release(); } catch (Exception ignored) {}
        frame.dispose();
        System.exit(0);
    }

    // =====================================================
    //  服务控制
    // =====================================================
    static void locateRoot() throws Exception {
        File javaHome = new File(System.getProperty("java.home"));
        File candidate = javaHome.getParentFile();
        if (new File(candidate, "components").exists()) {
            root = candidate;
        } else {
            File jarLoc = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            root = jarLoc.getParentFile().getParentFile();
        }
        components = new File(root, "components");
        dataDir = new File(root, "data");
    }

    static void startMysql() throws Exception {
        File mysqlDir = new File(components, "mysql");
        File datadir = new File(dataDir, "mysql");
        File log = new File(logsDir, "mysql.log");
        appendLog("launcher", "[1/5] 启动 MySQL (3306)...\n");
        mysqlProc = new ProcessBuilder(
                new File(mysqlDir, "bin/mysqld.exe").getAbsolutePath(),
                "--console",
                "--basedir=" + mysqlDir.getAbsolutePath(),
                "--datadir=" + datadir.getAbsolutePath(),
                "--lc-messages-dir=" + new File(mysqlDir, "share").getAbsolutePath())
                .redirectErrorStream(true).redirectOutput(log).redirectError(log).start();
    }

    static void startRedis() throws Exception {
        File redisDir = new File(components, "redis");
        File log = new File(logsDir, "redis.log");
        appendLog("launcher", "[2/5] 启动 Redis (6379)...\n");
        redisProc = new ProcessBuilder(
                new File(redisDir, "redis-server.exe").getAbsolutePath(), "redis.windows.conf")
                .directory(redisDir)
                .redirectErrorStream(true).redirectOutput(log).redirectError(log).start();
    }

    static void startNginx() throws Exception {
        File nginxDir = new File(components, "nginx");
        new File(nginxDir, "logs").mkdirs();
        File log = new File(logsDir, "nginx.log");
        appendLog("launcher", "[3/5] 启动 Nginx (80/8866/8867)...\n");
        nginxProc = new ProcessBuilder(
                new File(nginxDir, "nginx.exe").getAbsolutePath(),
                "-p", nginxDir.getAbsolutePath(), "-c", "conf/nginx.conf")
                .directory(nginxDir)
                .redirectErrorStream(true).redirectOutput(log).redirectError(log).start();
    }

    static void startBackend() throws Exception {
        File java = new File(System.getProperty("java.home"), "bin/java.exe");
        File jar = new File(components, "usmind-backend.jar");
        File log = new File(logsDir, "backend.log");
        appendLog("launcher", "[4/5] 启动后端服务 (8899)...\n");
        backendProc = new ProcessBuilder(
                java.getAbsolutePath(),
                "-jar", jar.getAbsolutePath(),
                "--server.port=8899",
                "--file.upload-dir=" + new File(dataDir, "uploads").getAbsolutePath())
                .redirectErrorStream(true).redirectOutput(log).redirectError(log).start();
    }

    static boolean waitReady(int seconds) {
        appendLog("launcher", "[5/5] 等待服务就绪...\n");
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (httpOk("http://127.0.0.1/") && httpOk("http://127.0.0.1:8899/")) return true;
            sleep(1000);
        }
        return false;
    }

    static void stopAll() throws Exception {
        appendLog("launcher", "正在停止服务...\n");
        if (backendProc != null) { backendProc.destroy(); backendProc.waitFor(10, TimeUnit.SECONDS); }
        if (nginxProc != null) {
            try {
                File nginxDir = new File(components, "nginx");
                new ProcessBuilder(new File(nginxDir, "nginx.exe").getAbsolutePath(),
                        "-p", nginxDir.getAbsolutePath(), "-s", "stop")
                        .directory(nginxDir).start().waitFor(8, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
        if (redisProc != null) {
            try {
                File redisDir = new File(components, "redis");
                new ProcessBuilder(new File(redisDir, "redis-cli.exe").getAbsolutePath(), "shutdown")
                        .directory(redisDir).start().waitFor(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
            redisProc.destroy();
        }
        if (mysqlProc != null) {
            try {
                File bin = new File(components, "mysql/bin");
                new ProcessBuilder(new File(bin, "mysqladmin.exe").getAbsolutePath(),
                        "-uroot", "-proot", "shutdown").start().waitFor(15, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
            mysqlProc.destroy();
        }
        appendLog("launcher", "所有服务已停止。\n");
    }

    // =====================================================
    //  MySQL 幂等初始化（防止初始化被打断/表结构落后）
    // =====================================================
    static void ensureMysqlData() throws Exception {
        File datadir = new File(dataDir, "mysql");
        boolean firstRun = !new File(datadir, "mysql").exists();
        if (firstRun) {
            File mysqlDir = new File(components, "mysql");
            appendLog("launcher", "[0/5] 首次运行，初始化 MySQL 数据目录...\n");
            Process init = new ProcessBuilder(
                    new File(mysqlDir, "bin/mysqld.exe").getAbsolutePath(),
                    "--initialize-insecure",
                    "--basedir=" + mysqlDir.getAbsolutePath(),
                    "--datadir=" + datadir.getAbsolutePath())
                    .redirectErrorStream(true)
                    .redirectOutput(new File(logsDir, "mysql.log")).redirectError(new File(logsDir, "mysql.log")).start();
            init.waitFor(180, TimeUnit.SECONDS);
            appendLog("launcher", "[0/5] 数据目录初始化完成\n");
        }
        startMysql();
        waitMysqlReady(90);

        // 幂等：root 密码 = root；库存在；字段齐全
        File bin = new File(components, "mysql/bin");
        File mysql = new File(bin, "mysql.exe");
        boolean withPass = runMysql(mysql, "-proot", "SELECT 1") == 0;
        if (!withPass) {
            appendLog("launcher", "检测到 root 无密码，正在设置密码并建库...\n");
            runMysql(mysql, null, "ALTER USER 'root'@'localhost' IDENTIFIED BY 'root'; " +
                    "CREATE DATABASE IF NOT EXISTS usmind_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
        }
        String hasDb = runMysqlQuery(mysql, "-proot", "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='usmind_db'");
        if (!"1".equals(hasDb)) {
            appendLog("launcher", "数据库 usmind_db 不存在，导入表结构...\n");
            File initSql = new File(components, "init.sql");
            if (initSql.exists()) runMysql(mysql, "-proot", "source " + initSql.getAbsolutePath().replace("\\", "/"));
        }
        String hasCol = runMysqlQuery(mysql, "-proot",
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='usmind_db' AND TABLE_NAME='mind_map' AND COLUMN_NAME='manual_styles'");
        if (!"1".equals(hasCol)) {
            appendLog("launcher", "mind_map 表缺 manual_styles 列，自动补齐...\n");
            runMysql(mysql, "-proot", "ALTER TABLE usmind_db.mind_map ADD COLUMN manual_styles TEXT COMMENT '节点手动样式JSON' AFTER view_data;");
        }
        appendLog("launcher", "MySQL 初始化校验完成 (root/root, usmind_db)\n");
    }

    static void waitMysqlReady(int seconds) {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        File bin = new File(components, "mysql/bin");
        while (System.currentTimeMillis() < deadline) {
            try {
                Process p = new ProcessBuilder(new File(bin, "mysqladmin.exe").getAbsolutePath(),
                        "-uroot", "--skip-password", "ping")
                        .redirectErrorStream(true).start();
                p.waitFor(5, TimeUnit.SECONDS);
                if (p.exitValue() == 0) return;
                Process p2 = new ProcessBuilder(new File(bin, "mysqladmin.exe").getAbsolutePath(),
                        "-uroot", "-proot", "ping")
                        .redirectErrorStream(true).start();
                p2.waitFor(5, TimeUnit.SECONDS);
                if (p2.exitValue() == 0) return;
            } catch (Exception ignored) {}
            sleep(1000);
        }
    }

    static int runMysql(File mysql, String passArg, String sql) {
        try {
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(mysql.getAbsolutePath());
            cmd.add("-uroot");
            if (passArg != null) cmd.add(passArg);
            cmd.add("--default-character-set=utf8mb4");
            cmd.add("-e");
            cmd.add(sql);
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.waitFor(60, TimeUnit.SECONDS);
            return p.exitValue();
        } catch (Exception e) { return 1; }
    }

    static String runMysqlQuery(File mysql, String passArg, String sql) {
        try {
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(mysql.getAbsolutePath());
            cmd.add("-uroot");
            if (passArg != null) cmd.add(passArg);
            cmd.add("-N");
            cmd.add("-e");
            cmd.add(sql);
            Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
            p.waitFor(20, TimeUnit.SECONDS);
            byte[] b = p.getInputStream().readAllBytes();
            return new String(b, StandardCharsets.UTF_8).trim();
        } catch (Exception e) { return ""; }
    }

    // =====================================================
    //  Smoke 模式（构建验证）
    // =====================================================
    static void runSmoke() throws Exception {
        System.out.println("== USMIND SMOKE ==");
        try {
            ensureMysqlData();
            startRedis();
            startNginx();
            startBackend();
            boolean ok = waitReady(120);
            System.out.println(ok ? "[SMOKE] OK" : "[SMOKE] FAIL");
            stopAll();
            System.exit(ok ? 0 : 1);
        } catch (Exception e) {
            System.out.println("[SMOKE] FAIL: " + e.getMessage());
            try { stopAll(); } catch (Exception ignored) {}
            System.exit(1);
        }
    }

    // =====================================================
    //  工具
    // =====================================================
    static boolean httpOk(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(1500);
            c.setReadTimeout(1500);
            int code = c.getResponseCode();
            c.disconnect();
            return code >= 200 && code < 500;
        } catch (Exception e) { return false; }
    }

    static boolean portOpen(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 700);
            return true;
        } catch (Exception e) { return false; }
    }

    static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                new ProcessBuilder("cmd", "/c", "start", "", url).start();
            }
        } catch (Exception ignored) {}
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
