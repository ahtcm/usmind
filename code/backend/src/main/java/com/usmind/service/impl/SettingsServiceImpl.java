package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.usmind.entity.SysConfig;
import com.usmind.mapper.SysConfigMapper;
import com.usmind.service.SettingsService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SettingsServiceImpl implements SettingsService {

    private final SysConfigMapper sysConfigMapper;
    private final JdbcTemplate jdbcTemplate;

    /** 可选：nginx.conf 绝对路径覆盖（默认自动探测便携版/原项目位置） */
    @Value("${nginx.conf-path:}")
    private String nginxConfPath;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SettingsServiceImpl(SysConfigMapper sysConfigMapper, JdbcTemplate jdbcTemplate) {
        this.sysConfigMapper = sysConfigMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        createTableIfNotExists();
        load();
    }

    private void createTableIfNotExists() {
        try {
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS sys_config (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "config_key VARCHAR(64) NOT NULL UNIQUE, " +
                "config_value TEXT, " +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (Exception e) {
            log.warn("创建 sys_config 表失败: {}", e.getMessage());
        }
    }

    public synchronized void load() {
        cache.clear();
        try {
            List<SysConfig> all = sysConfigMapper.selectList(null);
            for (SysConfig c : all) {
                cache.put(c.getConfigKey(), c.getConfigValue());
            }
        } catch (Exception e) {
            log.warn("加载系统配置失败: {}", e.getMessage());
        }
        // 写入默认值（仅当不存在）
        putDefaultIfAbsent("jwtExpireHours", "24");
        putDefaultIfAbsent("uploadMaxSizeMb", "100");
        // 上传类型白名单（后台可配）：常见媒体/文档 + 常见代码后缀
        putDefaultIfAbsent("allowedFileTypes", "jpg,jpeg,png,gif,webp,bmp,ico,avif,svg,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,md,zip,rar,7z,mp3,wav,ogg,m4a,aac,flac,mp4,webm,ogv,mov,mkv,json,js,mjs,cjs,ts,tsx,jsx,vue,html,htm,css,scss,less,xml,yaml,yml,py,python,java,kt,c,h,cpp,cc,cxx,hpp,hh,cs,go,rs,php,sh,bash,zsh,sql,rb,ini,conf,cfg,env,properties,tsv,gitignore,dockerfile,sass,plist");
        // 代码/文本在线预览高亮：扩展名 -> CodeMirror mode（后台可配）
        putDefaultIfAbsent("previewModes", "{\"js\":\"text/javascript\",\"mjs\":\"text/javascript\",\"cjs\":\"text/javascript\",\"ts\":\"text/javascript\",\"jsx\":\"text/javascript\",\"tsx\":\"text/javascript\",\"json\":\"application/json\",\"map\":\"application/json\",\"html\":\"text/html\",\"htm\":\"text/html\",\"vue\":\"text/html\",\"xml\":\"application/xml\",\"svg\":\"application/xml\",\"css\":\"text/css\",\"scss\":\"text/x-scss\",\"less\":\"text/x-less\",\"md\":\"text/x-markdown\",\"markdown\":\"text/x-markdown\",\"py\":\"text/x-python\",\"python\":\"text/x-python\",\"java\":\"text/x-java\",\"kt\":\"text/x-kotlin\",\"c\":\"text/x-csrc\",\"h\":\"text/x-csrc\",\"cpp\":\"text/x-c++src\",\"cc\":\"text/x-c++src\",\"cxx\":\"text/x-c++src\",\"hpp\":\"text/x-c++src\",\"hh\":\"text/x-c++src\",\"cs\":\"text/x-csharp\",\"go\":\"text/x-go\",\"rs\":\"text/x-rust\",\"php\":\"text/x-php\",\"sh\":\"text/x-sh\",\"bash\":\"text/x-sh\",\"zsh\":\"text/x-sh\",\"sql\":\"text/x-sql\",\"rb\":\"text/x-ruby\",\"yml\":\"text/x-yaml\",\"yaml\":\"text/x-yaml\",\"swift\":\"text/x-swift\"}");
        // 纯文本预览（无高亮）扩展名（后台可配）
        putDefaultIfAbsent("textPreviewExts", "[\"txt\",\"log\",\"ini\",\"conf\",\"cfg\",\"csv\",\"env\",\"properties\",\"tsv\",\"gitignore\",\"dockerfile\",\"sass\",\"plist\"]");
        putDefaultIfAbsent("aiApiKey", "");
        // 微信支付（默认关闭）
        putDefaultIfAbsent("wechatPayEnabled", "0");
        putDefaultIfAbsent("wechatMchId", "");
        putDefaultIfAbsent("wechatAppId", "");
        putDefaultIfAbsent("wechatApiKey", "");
        putDefaultIfAbsent("wechatPrivateKey", "");
        putDefaultIfAbsent("wechatNotifyUrl", "");
        // 注册 / 验证码（图形验证码默认开，短信验证码默认关）
        putDefaultIfAbsent("authCaptchaEnabled", "1");
        putDefaultIfAbsent("authSmsEnabled", "0");
        putDefaultIfAbsent("smsProvider", "");
        putDefaultIfAbsent("smsAccessKeyId", "");
        putDefaultIfAbsent("smsAccessKeySecret", "");
        putDefaultIfAbsent("smsSignName", "");
        putDefaultIfAbsent("smsTemplateCode", "");
        // 会员功能开关（空=全部开放）
        putDefaultIfAbsent("memberThemeIds", "");
        putDefaultIfAbsent("memberExportFormats", "");
        // 上传限制：数量 / 单文件大小(MB)（非会员 / 会员）
        putDefaultIfAbsent("uploadFreeCount", "20");
        putDefaultIfAbsent("uploadFreeMaxMb", "20");
        putDefaultIfAbsent("uploadVipCount", "200");
        putDefaultIfAbsent("uploadVipMaxMb", "200");
        // 画布个数限制
        putDefaultIfAbsent("mindmapFreeCount", "5");
        putDefaultIfAbsent("mindmapVipCount", "200");
        // IP 防护
        putDefaultIfAbsent("ipGuardEnabled", "1");
        putDefaultIfAbsent("ipRateLimit", "60");
        putDefaultIfAbsent("ipRateWindow", "60");
        putDefaultIfAbsent("ipAutoBlackSeconds", "3600");
        putDefaultIfAbsent("aiModel", "gpt-3.5-turbo");
        putDefaultIfAbsent("aiEndpoint", "");
        // 附件节点文件名默认文字色（后台主题/项目配置可改）
        putDefaultIfAbsent("attachTextColor", "#e53935");
    }

    private void putDefaultIfAbsent(String key, String value) {
        if (!cache.containsKey(key)) {
            upsert(key, value);
            cache.put(key, value);
        }
    }

    @Override
    public String get(String key, String defaultValue) {
        String v = cache.get(key);
        return (v == null || v.isEmpty()) ? defaultValue : v;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public void set(String key, String value) {
        upsert(key, value);
        cache.put(key, value);
    }

    private void upsert(String key, String value) {
        SysConfig exist = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .last("LIMIT 1"));
        if (exist == null) {
            SysConfig c = new SysConfig();
            c.setConfigKey(key);
            c.setConfigValue(value);
            sysConfigMapper.insert(c);
        } else {
            exist.setConfigValue(value);
            sysConfigMapper.updateById(exist);
        }
    }

    @Override
    public Map<String, Object> getSettings() {
        Map<String, Object> m = new LinkedHashMap<>();
        String aiKey = get("aiApiKey", "");
        m.put("jwtExpireHours", getInt("jwtExpireHours", 24));
        m.put("uploadMaxSizeMb", getInt("uploadMaxSizeMb", 100));
        m.put("allowedFileTypes", get("allowedFileTypes", ""));
        m.put("previewModes", get("previewModes", "{}"));
        m.put("textPreviewExts", get("textPreviewExts", "[]"));
        m.put("aiApiKey", mask(aiKey));
        m.put("aiModel", get("aiModel", ""));
        m.put("aiEndpoint", get("aiEndpoint", ""));
        m.put("attachTextColor", get("attachTextColor", "#e53935"));
        m.put("wechatLoginEnabled", getInt("wechatLoginEnabled", 0));
        m.put("wechatLoginAppId", get("wechatLoginAppId", ""));
        m.put("wechatLoginSecret", get("wechatLoginSecret", ""));
        m.put("wechatLoginRedirectUri", get("wechatLoginRedirectUri", ""));
        return m;
    }

    private String mask(String s) {
        if (s == null || s.length() <= 8) return s;
        return s.substring(0, 4) + "****" + s.substring(s.length() - 4);
    }

    @Override
    public void updateSettings(Map<String, Object> body) {
        if (body.containsKey("jwtExpireHours") && body.get("jwtExpireHours") != null) {
            set("jwtExpireHours", String.valueOf(body.get("jwtExpireHours")));
        }
        if (body.containsKey("uploadMaxSizeMb") && body.get("uploadMaxSizeMb") != null) {
            String v = String.valueOf(body.get("uploadMaxSizeMb"));
            set("uploadMaxSizeMb", v);
            try {
                syncNginxBodySize(Integer.parseInt(v.trim()));
            } catch (NumberFormatException ignored) { }
        }
        if (body.containsKey("allowedFileTypes") && body.get("allowedFileTypes") != null) {
            set("allowedFileTypes", String.valueOf(body.get("allowedFileTypes")));
        }
        if (body.containsKey("previewModes") && body.get("previewModes") != null) {
            set("previewModes", String.valueOf(body.get("previewModes")));
        }
        if (body.containsKey("textPreviewExts") && body.get("textPreviewExts") != null) {
            set("textPreviewExts", String.valueOf(body.get("textPreviewExts")));
        }
        // aiApiKey 传入脱敏值(含****)时不覆盖
        if (body.containsKey("aiApiKey") && body.get("aiApiKey") != null) {
            String v = String.valueOf(body.get("aiApiKey"));
            if (!v.contains("****")) {
                set("aiApiKey", v);
            }
        }
        if (body.containsKey("aiModel") && body.get("aiModel") != null) {
            set("aiModel", String.valueOf(body.get("aiModel")));
        }
        if (body.containsKey("aiEndpoint") && body.get("aiEndpoint") != null) {
            set("aiEndpoint", String.valueOf(body.get("aiEndpoint")));
        }
        if (body.containsKey("attachTextColor") && body.get("attachTextColor") != null) {
            set("attachTextColor", String.valueOf(body.get("attachTextColor")));
        }
        // ===== 会员 / 上传 / 画布限制 / 支付 / 验证码 / 防护键（后台可配，统一以 string 落库）=====
        String[] memberKeys = {
            "memberThemeIds", "memberExportFormats",
            "uploadFreeCount", "uploadFreeMaxMb", "uploadVipCount", "uploadVipMaxMb",
            "mindmapFreeCount", "mindmapVipCount",
            "wechatPayEnabled", "authCaptchaEnabled", "authSmsEnabled",
            "wechatLoginEnabled", "wechatLoginAppId", "wechatLoginSecret", "wechatLoginRedirectUri",
            "ipGuardEnabled", "ipRateLimit", "ipRateWindow", "ipAutoBlackSeconds"
        };
        for (String k : memberKeys) {
            if (body.containsKey(k) && body.get(k) != null) {
                set(k, String.valueOf(body.get(k)));
            }
        }
    }

    @Override
    public long getJwtExpireMs() {
        return getInt("jwtExpireHours", 24) * 3600_000L;
    }

    // =====================================================
    //  nginx 上传大小联动：后台改 uploadMaxSizeMb -> 同步 nginx.conf + 热重载
    // =====================================================

    private void syncNginxBodySize(int mb) {
        File conf = detectNginxConf();
        if (conf == null) {
            log.warn("未找到 nginx.conf，上传大小仅后端生效（可在 application.yml 配置 nginx.conf-path 覆盖）");
            return;
        }
        try {
            String text = new String(Files.readAllBytes(conf.toPath()), StandardCharsets.UTF_8);
            String updated;
            if (text.contains("client_max_body_size")) {
                updated = text.replaceAll("client_max_body_size\\s+\\d+[kKmMgG]?;", "client_max_body_size " + mb + "m;");
            } else {
                updated = text.replaceFirst("(?s)(\\s*)(http\\s*\\{)", "$1$2\n    client_max_body_size " + mb + "m;");
            }
            if (!updated.equals(text)) {
                Files.write(conf.toPath(), updated.getBytes(StandardCharsets.UTF_8));
                log.info("nginx.conf 已同步 client_max_body_size {}m: {}", mb, conf.getAbsolutePath());
                reloadNginx(conf.getParentFile().getParentFile());
            }
        } catch (Exception e) {
            log.warn("同步 nginx client_max_body_size 失败: {}", e.getMessage());
        }
    }

    /** 探测 nginx.conf：优先配置项，其次便携版 components/nginx、原项目 tools/nginx */
    private File detectNginxConf() {
        if (nginxConfPath != null && !nginxConfPath.trim().isEmpty()) {
            File f = new File(nginxConfPath.trim());
            if (f.isFile()) return f;
        }
        File jarParent;
        try {
            jarParent = new File(SettingsServiceImpl.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParentFile();
        } catch (Exception e) {
            jarParent = new File(System.getProperty("user.dir"));
        }
        File[] candidates = {
            new File(jarParent, "nginx/conf/nginx.conf"),                          // 便携版: portable\components\nginx
            new File(jarParent, "../../../tools/nginx-1.30.5/conf/nginx.conf"),    // 原项目: code\backend\target -> 项目根\tools
            new File(System.getProperty("user.dir"), "tools/nginx-1.30.5/conf/nginx.conf"),
            new File(System.getProperty("user.dir"), "components/nginx/conf/nginx.conf"),
        };
        for (File c : candidates) if (c.isFile()) return c;
        return null;
    }

    /** 热重载 nginx（reload 依赖 pid 文件；失败仅告警，不强行杀进程） */
    private void reloadNginx(File nginxDir) {
        try {
            Process p = new ProcessBuilder(
                    new File(nginxDir, "nginx.exe").getAbsolutePath(),
                    "-p", nginxDir.getAbsolutePath(), "-c", "conf/nginx.conf", "-s", "reload")
                    .redirectErrorStream(true).start();
            if (p.waitFor(15, TimeUnit.SECONDS)) {
                log.info("nginx reload 完成 (exit {})", p.exitValue());
            } else {
                p.destroyForcibly();
                log.warn("nginx reload 超时，请手动重启 nginx 使上传配置生效");
            }
        } catch (Exception e) {
            log.warn("nginx reload 失败: {}", e.getMessage());
        }
    }
}
