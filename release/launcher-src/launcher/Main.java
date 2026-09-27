package launcher;

import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * USMIND Portable 启动器
 * 双击 USMIND.exe 后：初始化/启动 MySQL -> Redis -> Nginx -> 后端 -> 打开浏览器
 * 按回车(或关闭窗口) 优雅停止所有服务。
 * 支持 --smoke 参数：启动全部服务并健康检查后自动停止（用于构建验证）。
 */
public class Main {

    static File root, components, dataDir;
    static Process mysqlProc, redisProc, nginxProc, backendProc;
    static boolean smoke;

    public static void main(String[] args) throws Exception {
        smoke = Arrays.asList(args).contains("--smoke");
        locateRoot();

        System.out.println("========================================");
        System.out.println("  USMIND 思维导图 便携版 v1.0");
        System.out.println("========================================");
        System.out.println("程序目录: " + root);

        new File(dataDir, "logs").mkdirs();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { stopAll(); } catch (Exception ignored) {}
        }));

        try {
            startMysql();
            startRedis();
            startNginx();
            startBackend();

            boolean ok = waitReady(240);
            if (smoke) {
                System.out.println(ok ? "[SMOKE] OK" : "[SMOKE] FAIL");
                stopAll();
                System.exit(ok ? 0 : 1);
            }
            if (ok) {
                openBrowser();
                System.out.println("\n服务已启动：");
                System.out.println("  用户端   http://127.0.0.1:8866");
                System.out.println("  管理端   http://127.0.0.1:8867");
                System.out.println("\n按回车键停止所有服务并退出...");
                new BufferedReader(new InputStreamReader(System.in)).readLine();
            } else {
                System.out.println("[ERROR] 服务启动超时，请查看 data/logs 下的日志");
                new BufferedReader(new InputStreamReader(System.in)).readLine();
            }
        } finally {
            stopAll();
        }
    }

    static void locateRoot() throws Exception {
        // jpackage 布局: <root>/USMIND.exe  <root>/app/launcher.jar  <root>/runtime/
        File javaHome = new File(System.getProperty("java.home")); // <root>/runtime
        File candidate = javaHome.getParentFile();
        if (new File(candidate, "components").exists()) {
            root = candidate;
        } else {
            File jarLoc = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            root = jarLoc.getParentFile().getParentFile(); // app -> <root>
        }
        components = new File(root, "components");
        dataDir = new File(root, "data");
    }

    static void log(String tag) throws IOException {
        System.out.println(tag);
    }

    static void startMysql() throws Exception {
        File mysqlDir = new File(components, "mysql");
        File bin = new File(mysqlDir, "bin");
        File datadir = new File(dataDir, "mysql");
        File log = new File(dataDir, "logs/mysql.log");
        log.getParentFile().mkdirs();

        boolean firstRun = !new File(datadir, "mysql").exists();
        if (firstRun) {
            System.out.println("[1/5] 首次运行，初始化 MySQL 数据目录...");
            Process init = new ProcessBuilder(
                    new File(bin, "mysqld.exe").getAbsolutePath(),
                    "--initialize-insecure",
                    "--basedir=" + mysqlDir.getAbsolutePath(),
                    "--datadir=" + datadir.getAbsolutePath())
                    .redirectErrorStream(true).redirectOutput(log).redirectError(log).start();
            init.waitFor(180, TimeUnit.SECONDS);
            System.out.println("[1/5] 数据目录初始化完成");
        }
        System.out.println("[1/5] 启动 MySQL (3306)...");
        mysqlProc = new ProcessBuilder(
                new File(bin, "mysqld.exe").getAbsolutePath(),
                "--console",
                "--basedir=" + mysqlDir.getAbsolutePath(),
                "--datadir=" + datadir.getAbsolutePath(),
                "--lc-messages-dir=" + new File(mysqlDir, "share").getAbsolutePath())
                .redirectErrorStream(true).redirectOutput(log).redirectError(log).start();

        if (firstRun) {
            waitMysqlReady(90);
            // root 设置密码 + 建库
            runMysqlCmd(null, "ALTER USER 'root'@'localhost' IDENTIFIED BY 'root'; CREATE DATABASE IF NOT EXISTS usmind_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
            // 导入表结构（初始化数据）
            File initSql = new File(components, "init.sql");
            if (initSql.exists()) {
                System.out.println("[1/5] 导入数据库表结构...");
                runMysqlCmd("root", "source " + initSql.getAbsolutePath().replace("\\", "/"));
            }
        }
    }

    /** 等待 mysqld 可连接 */
    static void waitMysqlReady(int seconds) throws Exception {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                Process p = new ProcessBuilder(new File(components, "mysql/bin/mysqladmin.exe").getAbsolutePath(),
                        "-uroot", "--skip-password", "ping")
                        .redirectErrorStream(true).start();
                p.waitFor(5, TimeUnit.SECONDS);
                if (p.exitValue() == 0) return;
            } catch (Exception ignored) {}
            sleep(1000);
        }
        throw new IllegalStateException("MySQL 未在限定时间内就绪");
    }

    /** 执行 mysql 客户端命令 */
    static void runMysqlCmd(String password, String sql) throws Exception {
        File mysql = new File(components, "mysql/bin/mysql.exe");
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(mysql.getAbsolutePath());
        cmd.add("-uroot");
        if (password != null) cmd.add("-p" + password);
        cmd.add("--default-character-set=utf8mb4");
        cmd.add("-e");
        cmd.add(sql);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.waitFor(60, TimeUnit.SECONDS);
    }

    static void startRedis() throws Exception {
        File redisDir = new File(components, "redis");
        File log = new File(dataDir, "logs/redis.log");
        System.out.println("[2/5] 启动 Redis (6379)...");
        redisProc = new ProcessBuilder(
                new File(redisDir, "redis-server.exe").getAbsolutePath(),
                "redis.windows.conf")
                .directory(redisDir)
                .redirectErrorStream(true).redirectOutput(log).redirectError(log).start();
    }

    static void startNginx() throws Exception {
        File nginxDir = new File(components, "nginx");
        new File(nginxDir, "logs").mkdirs();
        File log = new File(dataDir, "logs/nginx.log");
        System.out.println("[3/5] 启动 Nginx (8866/8867)...");
        nginxProc = new ProcessBuilder(
                new File(nginxDir, "nginx.exe").getAbsolutePath(),
                "-p", nginxDir.getAbsolutePath(),
                "-c", "conf/nginx.conf")
                .directory(nginxDir)
                .redirectErrorStream(true).redirectOutput(log).redirectError(log).start();
    }

    static void startBackend() throws Exception {
        File java = new File(System.getProperty("java.home"), "bin/java.exe");
        File jar = new File(components, "usmind-backend.jar");
        File log = new File(dataDir, "logs/backend.log");
        System.out.println("[4/5] 启动后端服务 (8899)...");
        backendProc = new ProcessBuilder(
                java.getAbsolutePath(),
                "-jar", jar.getAbsolutePath(),
                "--server.port=8899",
                "--file.upload-dir=" + new File(dataDir, "uploads").getAbsolutePath())
                .redirectErrorStream(true).redirectOutput(log).redirectError(log).start();
    }

    /** 轮询 8866/8899 直到可访问 */
    static boolean waitReady(int seconds) {
        System.out.println("[5/5] 等待服务就绪...");
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (httpOk("http://127.0.0.1:8866/") && httpOk("http://127.0.0.1:8899/")) {
                return true;
            }
            sleep(1000);
        }
        return false;
    }

    static boolean httpOk(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(1500);
            c.setReadTimeout(1500);
            int code = c.getResponseCode();
            c.disconnect();
            return code >= 200 && code < 500;
        } catch (Exception e) {
            return false;
        }
    }

    static void openBrowser() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI("http://127.0.0.1:8866"));
            } else {
                new ProcessBuilder("cmd", "/c", "start", "", "http://127.0.0.1:8866").start();
            }
        } catch (Exception ignored) {}
    }

    static void stopAll() throws Exception {
        System.out.println("\n正在停止服务...");
        // 后端
        if (backendProc != null) {
            backendProc.destroy();
            backendProc.waitFor(10, TimeUnit.SECONDS);
        }
        // nginx
        if (nginxProc != null) {
            try {
                File nginxDir = new File(components, "nginx");
                new ProcessBuilder(new File(nginxDir, "nginx.exe").getAbsolutePath(),
                        "-p", nginxDir.getAbsolutePath(), "-s", "stop")
                        .directory(nginxDir).start().waitFor(8, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
        // redis
        if (redisProc != null) {
            try {
                File redisDir = new File(components, "redis");
                new ProcessBuilder(new File(redisDir, "redis-cli.exe").getAbsolutePath(), "shutdown")
                        .directory(redisDir).start().waitFor(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
            redisProc.destroy();
        }
        // mysql 优雅停机
        if (mysqlProc != null) {
            try {
                File bin = new File(components, "mysql/bin");
                new ProcessBuilder(new File(bin, "mysqladmin.exe").getAbsolutePath(),
                        "-uroot", "-proot", "shutdown")
                        .start().waitFor(15, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
            mysqlProc.destroy();
        }
        System.out.println("所有服务已停止。");
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
