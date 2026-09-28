package launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
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
 *   3. 界面显示各服务状态 / 错误检测 / 日志文件位置，一键启停
 * 关闭窗口 = 优雅停止所有服务并退出。
 * 支持 --smoke：无界面模式，启动全部服务并健康检查后自动停止（用于构建验证）。
 */
public class Main {

    // ---------------- 常量 ----------------
    static final Color BG      = new Color(0x1B, 0x20, 0x28);
    static final Color CARD    = new Color(0x25, 0x2C, 0x37);
    static final Color CARD_HI = new Color(0x2F, 0x38, 0x45);
    static final Color BORDER  = new Color(0x39, 0x43, 0x52);
    static final Color TEXT    = new Color(0xE8, 0xEC, 0xF2);
    static final Color SUB     = new Color(0x9A, 0xA4, 0xB2);
    static final Color ACCENT  = new Color(0x4A, 0x9E, 0xFF);
    static final Color GREEN   = new Color(0x45, 0xD0, 0x8A);
    static final Color RED     = new Color(0xF0, 0x5D, 0x5D);
    static final Color ORANGE  = new Color(0xF0, 0xA5, 0x2E);

    static final String[] SVC_NAMES = {"MySQL", "Redis", "Nginx", "后端"};
    static final String[] SVC_PORTS = {"3306", "6379", "80 / 8866 / 8867", "8899"};
    static final String[] LOG_FILES = {"mysql.log", "redis.log", "nginx.log", "backend.log"};

    // ---------------- 运行状态 ----------------
    static File root, components, dataDir, logsDir;
    static Process mysqlProc, redisProc, nginxProc, backendProc;
    static volatile boolean smoke;
    static volatile boolean started;
    static final AtomicBoolean busy = new AtomicBoolean(false);

    // ---------------- GUI ----------------
    static JFrame frame;
    static JLabel statusDot, statusText, msgBar;
    static JButton btnStart, btnStop, btnBrowser;
    static RoundedCard[] cards = new RoundedCard[4];
    static javax.swing.Timer statusTimer;
    static FileLock singleLock;
    static Map<String, Long> errPos = new HashMap<>();
    static Map<String, Integer> errCount = new HashMap<>();

    public static void main(String[] args) throws Exception {
        smoke = Arrays.asList(args).contains("--smoke");
        locateRoot();
        logsDir = new File(dataDir, "logs");
        logsDir.mkdirs();

        if (smoke) { runSmoke(); return; }

        try {
            FileChannel ch = FileChannel.open(Paths.get(dataDir.getAbsolutePath(), ".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            singleLock = ch.tryLock();
            if (singleLock == null) {
                JOptionPane.showMessageDialog(null, "USMIND 已在运行中，请勿重复启动。", "USMIND", JOptionPane.INFORMATION_MESSAGE);
                System.exit(0);
            }
        } catch (Exception ignored) {}

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
        Image logo = loadLogoIcon();
        if (logo != null) frame.setIconImage(logo);
        // 无系统边框：自绘标题栏，保证头部与身体配色一致
        frame.setUndecorated(true);
        frame.setSize(900, 620);
        frame.setMinimumSize(new Dimension(720, 500));
        frame.setLocationRelativeTo(null);
        frame.setBackground(BG);

        JPanel rootPane = new JPanel(new BorderLayout(0, 0));
        rootPane.setBackground(BG);
        rootPane.setBorder(new EmptyBorder(0, 20, 16, 20));

        // ---------- 自绘标题栏（与身体同色 + logo 图标 + 窗口控制） ----------
        rootPane.add(buildTitleBar(uiFont, logo), BorderLayout.NORTH);

        // ---------- 中部 ----------
        JPanel mid = new JPanel(new BorderLayout(0, 14));
        mid.setOpaque(false);

        // 服务卡片 2x2
        JPanel grid = new JPanel(new GridLayout(2, 2, 10, 10));
        grid.setOpaque(false);
        for (int i = 0; i < 4; i++) {
            cards[i] = new RoundedCard(SVC_NAMES[i], SVC_PORTS[i], LOG_FILES[i]);
            grid.add(cards[i]);
        }
        mid.add(grid, BorderLayout.NORTH);

        // 消息条
        JPanel msgRow = new JPanel(new BorderLayout());
        msgRow.setOpaque(false);
        msgBar = new JLabel("就绪：点击「启动全部服务」开始");
        msgBar.setForeground(SUB);
        msgBar.setFont(uiFont.deriveFont(Font.PLAIN, 13f));
        msgRow.add(msgBar, BorderLayout.WEST);
        mid.add(msgRow, BorderLayout.CENTER);

        // 日志位置提示
        JPanel logBox = new JPanel(new GridLayout(2, 2, 10, 8));
        logBox.setOpaque(false);
        for (int i = 0; i < 4; i++) {
            logBox.add(makeLogPath(SVC_NAMES[i], LOG_FILES[i]));
        }
        mid.add(logBox, BorderLayout.SOUTH);

        rootPane.add(mid, BorderLayout.CENTER);

        // ---------- 底部按钮 ----------
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        btns.setOpaque(false);
        btnStart = makeBtn("启动全部服务", ACCENT);
        btnStop = makeBtn("停止全部服务", new Color(0x3D, 0x45, 0x52));
        btnBrowser = makeBtn("打开浏览器", new Color(0x3D, 0x45, 0x52));
        btnStart.addActionListener(e -> startAll());
        btnStop.addActionListener(e -> stopAllGui());
        btnBrowser.addActionListener(e -> openBrowser("http://127.0.0.1/"));
        btnStop.setEnabled(false);
        btns.add(btnStart); btns.add(btnStop); btns.add(btnBrowser);
        rootPane.add(btns, BorderLayout.SOUTH);

        frame.setContentPane(rootPane);
        frame.setVisible(true);
        guiMsg("就绪：点击「启动全部服务」开始，关闭窗口将停止所有服务");

        statusTimer = new javax.swing.Timer(1500, e -> refresh());
        statusTimer.start();
    }

    /** 自绘标题栏：logo 图标 + 标题 + 运行状态 + 最小化/关闭；支持鼠标拖动 */
    static JPanel buildTitleBar(Font uiFont, Image logo) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(true);
        bar.setBackground(BG);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        bar.setPreferredSize(new Dimension(0, 46));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 11));
        left.setOpaque(false);
        if (logo != null) {
            JLabel ic = new JLabel(new ImageIcon(logo.getScaledInstance(22, 22, Image.SCALE_SMOOTH)));
            left.add(ic);
        }
        JLabel t = new JLabel("USMIND 思维导图 · 便携版");
        t.setForeground(TEXT);
        t.setFont(uiFont.deriveFont(Font.BOLD, 14f));
        left.add(t);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 9));
        right.setOpaque(false);
        statusDot = new JLabel("●");
        statusDot.setFont(uiFont.deriveFont(Font.BOLD, 14f));
        statusDot.setForeground(RED);
        statusText = new JLabel("未启动");
        statusText.setForeground(SUB);
        JLabel gap = new JLabel("   ");
        JButton min = new JButton("_");
        JButton close = new JButton("X");
        styleTitleBtn(min, uiFont);
        styleTitleBtn(close, uiFont);
        close.addActionListener(e -> shutdown());
        min.addActionListener(e -> frame.setExtendedState(JFrame.ICONIFIED));
        right.add(statusDot); right.add(statusText); right.add(gap); right.add(min); right.add(close);

        MouseAdapter drag = new MouseAdapter() {
            int px, py;
            public void mousePressed(MouseEvent e) { px = e.getX(); py = e.getY(); }
            public void mouseDragged(MouseEvent e) {
                Point p = frame.getLocation();
                frame.setLocation(p.x + e.getX() - px, p.y + e.getY() - py);
            }
        };
        bar.addMouseListener(drag); bar.addMouseMotionListener(drag);
        left.addMouseListener(drag); left.addMouseMotionListener(drag);
        t.addMouseListener(drag); t.addMouseMotionListener(drag);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    static void styleTitleBtn(JButton b, Font f) {
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setForeground(SUB);
        b.setFont(f.deriveFont(Font.PLAIN, 13f));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(38, 26));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                b.setForeground(Color.WHITE);
                b.setBackground(new Color(0x3A, 0x44, 0x53));
                b.setOpaque(true);
                b.repaint();
            }
            public void mouseExited(MouseEvent e) {
                b.setForeground(SUB);
                b.setBackground(BG);
                b.setOpaque(false);
                b.repaint();
            }
        });
    }

    /** 圆角卡片：服务名 + 状态点 + 端口 + 错误计数 */
    static class RoundedCard extends JPanel {
        final String name, port, logFile;
        final JLabel nm, info, err;
        RoundedCard(String name, String port, String logFile) {
            this.name = name; this.port = port; this.logFile = logFile;
            setOpaque(false);
            setBackground(CARD);
            setBorder(new EmptyBorder(14, 16, 14, 16));
            setLayout(new BorderLayout(0, 6));
            nm = new JLabel(name);
            nm.setForeground(TEXT);
            nm.setFont(nm.getFont().deriveFont(Font.BOLD, 16f));
            info = new JLabel("● 未启动");
            info.setForeground(RED);
            info.setFont(info.getFont().deriveFont(Font.PLAIN, 14f));
            err = new JLabel("");
            err.setForeground(RED);
            err.setFont(err.getFont().deriveFont(Font.PLAIN, 12f));
            JPanel left = new JPanel(new BorderLayout());
            left.setOpaque(false);
            left.add(nm, BorderLayout.NORTH);
            left.add(info, BorderLayout.CENTER);
            left.add(err, BorderLayout.SOUTH);
            JLabel portLbl = new JLabel(port);
            portLbl.setForeground(SUB);
            portLbl.setFont(portLbl.getFont().deriveFont(Font.PLAIN, 13f));
            add(left, BorderLayout.CENTER);
            add(portLbl, BorderLayout.EAST);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) { openLogFile(); }
                public void mouseEntered(MouseEvent e) { setBackground(CARD_HI); repaint(); }
                public void mouseExited(MouseEvent e) { setBackground(CARD); repaint(); }
            });
        }
        void openLogFile() {
            try {
                File f = new File(logsDir, logFile);
                if (f.exists()) Desktop.getDesktop().open(logsDir);
                else guiMsg("日志 " + logFile + " 尚未生成");
            } catch (Exception ignored) {}
        }
        void setState(boolean ok, boolean startedNow) {
            info.setText((ok ? "● 运行中" : (startedNow ? "● 启动中…" : "● 未启动")));
            info.setForeground(ok ? GREEN : RED);
        }
        void setErrors(int n) {
            err.setText(n > 0 ? "⚠ 检测到错误 " + n + " 条（点击查看日志）" : "");
        }
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 12, 12));
            g2.setColor(BORDER);
            g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 12, 12));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static JPanel makeLogPath(String name, String file) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        p.setOpaque(false);
        JLabel n = new JLabel(name);
        n.setForeground(TEXT);
        n.setFont(n.getFont().deriveFont(Font.BOLD, 12f));
        JLabel path = new JLabel("data/logs/" + file);
        path.setForeground(SUB);
        path.setFont(path.getFont().deriveFont(Font.PLAIN, 12f));
        JLabel open = new JLabel("<html><u>打开</u></html>");
        open.setForeground(ACCENT);
        open.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        open.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                try { if (new File(logsDir, file).exists()) Desktop.getDesktop().open(logsDir);
                      else guiMsg("日志 " + file + " 尚未生成"); } catch (Exception ignored) {}
            }
        });
        p.add(n); p.add(path); p.add(open);
        return p;
    }

    static JButton makeBtn(String text, Color bg) {
        JButton b = new JButton(text) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 13f));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(132, 36));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(brighten(bg)); }
            public void mouseExited(MouseEvent e) { b.setBackground(bg); }
        });
        return b;
    }

    static Color brighten(Color c) {
        return new Color(Math.min(255, c.getRed() + 24), Math.min(255, c.getGreen() + 24), Math.min(255, c.getBlue() + 24));
    }

    /** 从 logo.ico 解析出窗口图标（取其中最大 PNG 帧） */
    static Image loadLogoIcon() {
        try {
            File f = new File(root, "logo.ico");
            if (!f.exists()) f = new File(root, "app/logo.ico");
            if (!f.exists()) return null;
            byte[] ico = Files.readAllBytes(f.toPath());
            if (ico.length < 6) return null;
            int count = (ico[5] & 0xff) << 8 | (ico[4] & 0xff);
            int bestSize = -1, bestOff = -1, bestLen = -1;
            for (int i = 0; i < count; i++) {
                int e = 6 + i * 16;
                if (e + 16 > ico.length) break;
                int w = ico[e] & 0xff;
                int size = (w == 0 ? 256 : w);
                int len = ((ico[e + 11] & 0xff) << 24) | ((ico[e + 10] & 0xff) << 16) |
                          ((ico[e + 9] & 0xff) << 8) | (ico[e + 8] & 0xff);
                int off = ((ico[e + 15] & 0xff) << 24) | ((ico[e + 14] & 0xff) << 16) |
                          ((ico[e + 13] & 0xff) << 8) | (ico[e + 12] & 0xff);
                if (len > 0 && off + len <= ico.length && size >= bestSize) {
                    bestSize = size; bestOff = off; bestLen = len;
                }
            }
            if (bestOff < 0) return null;
            byte[] img = Arrays.copyOfRange(ico, bestOff, bestOff + bestLen);
            return ImageIO.read(new ByteArrayInputStream(img));
        } catch (Exception e) { return null; }
    }

    static void guiMsg(String text) {
        SwingUtilities.invokeLater(() -> {
            if (msgBar != null) msgBar.setText(text);
        });
    }

    // =====================================================
    //  状态刷新 + 错误检测
    // =====================================================
    static void refresh() {
        boolean[] ok = {
            portOpen("127.0.0.1", 3306),
            portOpen("127.0.0.1", 6379),
            httpOk("http://127.0.0.1:8866/"),
            httpOk("http://127.0.0.1:8899/")
        };
        for (int i = 0; i < 4; i++) {
            cards[i].setState(ok[i], started);
        }
        // 未启动时不扫描日志（避免把历史错误算进来）；启动后增量扫描
        if (started) {
            scanErrors(ok);
        } else {
            for (int i = 0; i < 4; i++) cards[i].setErrors(0);
        }
        boolean all = ok[0] && ok[1] && ok[2] && ok[3];
        statusDot.setForeground(all ? GREEN : (started ? ORANGE : RED));
        statusText.setText(all ? "全部服务运行中" : (started ? "启动中…" : "未启动"));
    }

    /** 增量扫描日志错误关键字，更新卡片错误计数 */
    static void scanErrors(boolean[] ok) {
        String[] names = {"mysql", "redis", "nginx", "backend"};
        for (int i = 0; i < 4; i++) {
            File f = new File(logsDir, names[i] + ".log");
            int cnt = errCount.getOrDefault(names[i], 0);
            if (f.exists()) {
                try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                    long len = raf.length();
                    long pos = errPos.getOrDefault(names[i], 0L);
                    if (len < pos) { pos = 0; cnt = 0; }
                    if (len > pos) {
                        raf.seek(pos);
                        byte[] buf = new byte[(int) Math.min(len - pos, 262144)];
                        raf.readFully(buf);
                        String s = new String(buf, StandardCharsets.UTF_8);
                        cnt += countOccur(s, "ERROR") + countOccur(s, "Exception") + countOccur(s, "FATAL");
                        errPos.put(names[i], len);
                        errCount.put(names[i], cnt);
                    }
                } catch (Exception ignored) {}
            }
            cards[i].setErrors(cnt);
        }
    }

    static int countOccur(String s, String key) {
        int c = 0, idx = 0;
        while ((idx = s.indexOf(key, idx)) >= 0) { c++; idx += key.length(); }
        return c;
    }

    // =====================================================
    //  启动 / 停止
    // =====================================================
    static void startAll() {
        if (!busy.compareAndSet(false, true)) return;
        btnStart.setEnabled(false); btnStop.setEnabled(false);
        statusDot.setForeground(ORANGE);
        statusText.setText("启动中…");
        guiMsg("正在启动全部服务…");
        // 重置错误检测：只统计本次运行产生的错误（跳过历史日志）
        errPos.clear();
        errCount.clear();
        for (int i = 0; i < 4; i++) cards[i].setErrors(0);
        for (String n : new String[]{"mysql", "redis", "nginx", "backend"}) {
            File f = new File(logsDir, n + ".log");
            errPos.put(n, f.exists() ? f.length() : 0L);
        }
        new Thread(() -> {
            try {
                // 先清理便携版残留孤儿进程（异常退出遗留的 nginx/java/mysql/redis），避免端口占用导致启动失败
                killUsmindFamily();
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
                    guiMsg("服务全部就绪，已自动打开浏览器 http://127.0.0.1/");
                    openBrowser("http://127.0.0.1/");
                } else {
                    guiMsg("启动超时：请点击对应服务卡片查看日志");
                }
            } catch (Exception ex) {
                started = true;
                guiMsg("启动失败：" + ex.getMessage() + "（详见日志）");
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
        guiMsg("正在停止全部服务…");
        new Thread(() -> {
            try { stopAll(); } catch (Exception ignored) {}
            // 兜底：清理所有便携版残留进程（含非本实例启动的孤儿）
            killUsmindFamily();
            started = false;
            SwingUtilities.invokeLater(() -> {
                statusDot.setForeground(RED);
                statusText.setText("已停止");
                btnStart.setEnabled(true);
                btnStop.setEnabled(false);
            });
            guiMsg("所有服务已停止");
            busy.set(false);
        }).start();
    }

    static void shutdown() {
        guiMsg("关闭窗口，正在优雅停止服务…");
        try { stopAll(); } catch (Exception ignored) {}
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
        guiMsg("[1/5] 启动 MySQL (3306)…");
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
        guiMsg("[2/5] 启动 Redis (6379)…");
        redisProc = new ProcessBuilder(
                new File(redisDir, "redis-server.exe").getAbsolutePath(), "redis.windows.conf")
                .directory(redisDir)
                .redirectErrorStream(true).redirectOutput(log).redirectError(log).start();
    }

    static void startNginx() throws Exception {
        File nginxDir = new File(components, "nginx");
        new File(nginxDir, "logs").mkdirs();
        File log = new File(logsDir, "nginx.log");
        guiMsg("[3/5] 启动 Nginx (80/8866/8867)…");
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
        guiMsg("[4/5] 启动后端服务 (8899)…");
        backendProc = new ProcessBuilder(
                java.getAbsolutePath(),
                "-jar", jar.getAbsolutePath(),
                "--server.port=8899",
                "--file.upload-dir=" + new File(dataDir, "uploads").getAbsolutePath())
                .redirectErrorStream(true).redirectOutput(log).redirectError(log).start();
    }

    static boolean waitReady(int seconds) {
        guiMsg("[5/5] 等待服务就绪…");
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (httpOk("http://127.0.0.1/") && httpOk("http://127.0.0.1:8899/")) return true;
            sleep(1000);
        }
        return false;
    }

    static void stopAll() throws Exception {
        // 后端：优雅 -> 强制 -> taskkill 进程树兜底
        if (backendProc != null) {
            backendProc.destroy();
            if (!backendProc.waitFor(5, TimeUnit.SECONDS)) {
                backendProc.destroyForcibly();
                if (!backendProc.waitFor(5, TimeUnit.SECONDS)) {
                    taskkill(backendProc.pid());
                }
            }
        }
        // Nginx：优先 -s stop（依赖 nginx.pid），未生效则按 master 进程树强制
        if (nginxProc != null) {
            boolean stopped = false;
            try {
                File nginxDir = new File(components, "nginx");
                Process q = new ProcessBuilder(new File(nginxDir, "nginx.exe").getAbsolutePath(),
                        "-p", nginxDir.getAbsolutePath(), "-s", "stop")
                        .directory(nginxDir).redirectErrorStream(true).start();
                q.waitFor(8, TimeUnit.SECONDS);
                stopped = !httpOk("http://127.0.0.1:8866/");
            } catch (Exception ignored) {}
            if (!stopped) taskkill(nginxProc.pid());
        }
        // Redis：优雅 shutdown，未生效则强制
        if (redisProc != null) {
            boolean stopped = false;
            try {
                File redisDir = new File(components, "redis");
                Process q = new ProcessBuilder(new File(redisDir, "redis-cli.exe").getAbsolutePath(), "shutdown")
                        .directory(redisDir).start();
                q.waitFor(5, TimeUnit.SECONDS);
                stopped = !portOpen("127.0.0.1", 6379);
            } catch (Exception ignored) {}
            if (!stopped) {
                redisProc.destroy();
                if (!redisProc.waitFor(3, TimeUnit.SECONDS)) taskkill(redisProc.pid());
            }
        }
        // MySQL：mysqladmin shutdown，未生效则强制
        if (mysqlProc != null) {
            boolean stopped = false;
            try {
                File bin = new File(components, "mysql/bin");
                Process q = new ProcessBuilder(new File(bin, "mysqladmin.exe").getAbsolutePath(),
                        "-uroot", "-proot", "shutdown").start();
                q.waitFor(15, TimeUnit.SECONDS);
                Thread.sleep(4000); // 等待 mysqld 刷盘退出
                stopped = !portOpen("127.0.0.1", 3306);
            } catch (Exception ignored) {}
            if (!stopped) {
                mysqlProc.destroy();
                if (!mysqlProc.waitFor(3, TimeUnit.SECONDS)) taskkill(mysqlProc.pid());
            }
        }
        // 最终兜底：清理 USMIND 家族残留（便携版 + 安装版）
        killUsmindFamily();
    }

    /** 强制结束某 PID 及其子进程树 */
    static void taskkill(long pid) {
        try {
            new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid))
                    .redirectErrorStream(true).start().waitFor(8, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
    }

    /** 清理 USMIND 家族残留进程（便携版 usmind-portable + 安装版 %LOCALAPPDATA%\USMIND / USMIND-online），
     *  避免端口占用导致启动失败；不触碰开发环境（tools 路径）进程 */
    static void killUsmindFamily() {
        try {
            String ps = "Get-CimInstance Win32_Process | Where-Object { ($_.Name -in 'nginx.exe','java.exe','mysqld.exe','redis-server.exe') -and (($_.ExecutablePath -like '*usmind-portable*') -or ($_.ExecutablePath -like '*AppData\\Local\\USMIND*')) } | ForEach-Object { $_.ProcessId }";
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command", ps)
                    .redirectErrorStream(true).start();
            p.waitFor(15, TimeUnit.SECONDS);
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int killed = 0;
            for (String line : out.split("\\r?\\n")) {
                String pid = line.trim();
                if (pid.matches("\\d+")) { taskkill(Long.parseLong(pid)); killed++; }
            }
            if (killed > 0) guiMsg("已清理 " + killed + " 个残留的 USMIND 服务进程");
            Thread.sleep(1500);
        } catch (Exception ignored) {}
    }

    // =====================================================
    //  MySQL 幂等初始化
    // =====================================================
    static void ensureMysqlData() throws Exception {
        File datadir = new File(dataDir, "mysql");
        boolean firstRun = !new File(datadir, "mysql").exists();
        if (firstRun) {
            File mysqlDir = new File(components, "mysql");
            guiMsg("[0/5] 首次运行，初始化 MySQL 数据目录…");
            Process init = new ProcessBuilder(
                    new File(mysqlDir, "bin/mysqld.exe").getAbsolutePath(),
                    "--initialize-insecure",
                    "--basedir=" + mysqlDir.getAbsolutePath(),
                    "--datadir=" + datadir.getAbsolutePath())
                    .redirectErrorStream(true)
                    .redirectOutput(new File(logsDir, "mysql.log")).redirectError(new File(logsDir, "mysql.log")).start();
            init.waitFor(180, TimeUnit.SECONDS);
            guiMsg("[0/5] 数据目录初始化完成");
        }
        startMysql();
        waitMysqlReady(90);

        File bin = new File(components, "mysql/bin");
        File mysql = new File(bin, "mysql.exe");
        boolean withPass = runMysql(mysql, "-proot", "SELECT 1") == 0;
        if (!withPass) {
            guiMsg("检测到 root 无密码，正在设置密码并建库…");
            runMysql(mysql, null, "ALTER USER 'root'@'localhost' IDENTIFIED BY 'root'; " +
                    "CREATE DATABASE IF NOT EXISTS usmind_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
        }
        String hasDb = runMysqlQuery(mysql, "-proot", "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='usmind_db'");
        if (!"1".equals(hasDb)) {
            guiMsg("数据库 usmind_db 不存在，导入表结构…");
            File initSql = new File(components, "init.sql");
            if (initSql.exists()) runMysql(mysql, "-proot", "source " + initSql.getAbsolutePath().replace("\\", "/"));
        }
        String hasCol = runMysqlQuery(mysql, "-proot",
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='usmind_db' AND TABLE_NAME='mind_map' AND COLUMN_NAME='manual_styles'");
        if (!"1".equals(hasCol)) {
            guiMsg("mind_map 表缺 manual_styles 列，自动补齐…");
            runMysql(mysql, "-proot", "ALTER TABLE usmind_db.mind_map ADD COLUMN manual_styles TEXT COMMENT '节点手动样式JSON' AFTER view_data;");
        }
        guiMsg("MySQL 初始化校验完成");
    }

    static void waitMysqlReady(int seconds) {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        File bin = new File(components, "mysql/bin");
        while (System.currentTimeMillis() < deadline) {
            try {
                Process p = new ProcessBuilder(new File(bin, "mysqladmin.exe").getAbsolutePath(),
                        "-uroot", "--skip-password", "ping").redirectErrorStream(true).start();
                p.waitFor(5, TimeUnit.SECONDS);
                if (p.exitValue() == 0) return;
                Process p2 = new ProcessBuilder(new File(bin, "mysqladmin.exe").getAbsolutePath(),
                        "-uroot", "-proot", "ping").redirectErrorStream(true).start();
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
            killUsmindFamily();
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
