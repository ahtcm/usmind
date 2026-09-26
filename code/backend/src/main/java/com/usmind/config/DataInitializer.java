package com.usmind.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usmind.entity.OssConfig;
import com.usmind.entity.SysUser;
import com.usmind.entity.ThemeTemplate;
import com.usmind.mapper.OssConfigMapper;
import com.usmind.mapper.SysUserMapper;
import com.usmind.mapper.ThemeTemplateMapper;
import com.usmind.mapper.IconTemplateMapper;
import com.usmind.entity.IconTemplate;
import com.usmind.storage.StorageFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final OssConfigMapper ossConfigMapper;
    private final ThemeTemplateMapper themeTemplateMapper;
    private final IconTemplateMapper iconTemplateMapper;
    private final PasswordEncoder passwordEncoder;
    private final StorageFactory storageFactory;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DataInitializer(SysUserMapper userMapper, OssConfigMapper ossConfigMapper,
                           ThemeTemplateMapper themeTemplateMapper,
                           IconTemplateMapper iconTemplateMapper,
                           PasswordEncoder passwordEncoder, StorageFactory storageFactory,
                           JdbcTemplate jdbcTemplate) {
        this.userMapper = userMapper;
        this.ossConfigMapper = ossConfigMapper;
        this.themeTemplateMapper = themeTemplateMapper;
        this.iconTemplateMapper = iconTemplateMapper;
        this.passwordEncoder = passwordEncoder;
        this.storageFactory = storageFactory;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        initAdmin();
        initLocalOss();
        initThemes();
        initIcons();
    }

    private void initAdmin() {
        SysUser admin = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, "admin"));
        if (admin == null) {
            SysUser u = new SysUser();
            u.setUsername("admin");
            u.setPassword(passwordEncoder.encode("admin123"));
            u.setNickname("系统管理员");
            u.setRole("ADMIN");
            u.setStatus(1);
            userMapper.insert(u);
            log.info("已创建默认管理员 admin/admin123");
        } else if (!passwordEncoder.matches("admin123", admin.getPassword())) {
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole("ADMIN");
            admin.setStatus(1);
            userMapper.updateById(admin);
            log.info("已重置 admin 密码为 admin123");
        }
    }

    private void initLocalOss() {
        Long activeCount = ossConfigMapper.selectCount(new LambdaQueryWrapper<OssConfig>()
                .eq(OssConfig::getIsActive, 1));
        if (activeCount == null || activeCount == 0) {
            OssConfig local = ossConfigMapper.selectOne(new LambdaQueryWrapper<OssConfig>()
                    .eq(OssConfig::getType, "local")
                    .last("LIMIT 1"));
            if (local == null) {
                local = new OssConfig();
                local.setName("本地存储");
                local.setType("local");
                local.setIsActive(1);
                ossConfigMapper.insert(local);
            } else {
                local.setIsActive(1);
                ossConfigMapper.updateById(local);
            }
            log.info("已初始化本地兜底OSS配置");
        }
        storageFactory.refresh();
    }

    /** 主题模板表：建表 + 初始化默认主题 */
    private void initThemes() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_theme ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "name VARCHAR(100) NOT NULL,"
                    + "theme_key VARCHAR(100) NOT NULL,"
                    + "branch_colors TEXT,"
                    + "config_json LONGTEXT,"
                    + "sort INT DEFAULT 99,"
                    + "enabled INT DEFAULT 1,"
                    + "builtin INT DEFAULT 0,"
                    + "created_at DATETIME,"
                    + "updated_at DATETIME"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (Exception e) {
            log.warn("建表 sys_theme 失败: {}", e.getMessage());
        }
        Long count = themeTemplateMapper.selectCount(null);
        if (count != null && count > 0) return;
        initDefaultThemes();
        log.info("已初始化 {} 套默认主题模板", DEFAULT_THEMES.size());
    }

    /** 图标表：建表 + 从 emoji-seed.json 播种（全量 1200+ 表情） */
    private void initIcons() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_icon ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "name VARCHAR(32) NOT NULL,"
                    + "category VARCHAR(64) DEFAULT '默认',"
                    + "sort INT DEFAULT 99,"
                    + "enabled INT DEFAULT 1,"
                    + "builtin INT DEFAULT 1,"
                    + "created_at DATETIME,"
                    + "updated_at DATETIME,"
                    + "KEY idx_icon_cat (category)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (Exception e) {
            log.warn("建表 sys_icon 失败: {}", e.getMessage());
        }
        Long count = iconTemplateMapper.selectCount(null);
        if (count != null && count > 0) return;
        try (var in = getClass().getResourceAsStream("/emoji-seed.json")) {
            if (in == null) { log.warn("emoji-seed.json 未找到，跳过图标播种"); return; }
            Map<String, Map<String, Object>> data = objectMapper.readValue(in, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Map<String, Object>>>() {});
            int total = 0;
            int catSort = 0;
            for (Map.Entry<String, Map<String, Object>> e : data.entrySet()) {
                String category = String.valueOf(e.getValue().get("name"));
                List<String> emojis = (List<String>) e.getValue().get("emojis");
                catSort++;
                int idx = 0;
                for (String em : emojis) {
                    IconTemplate i = new IconTemplate();
                    i.setName(em);
                    i.setCategory(category);
                    i.setSort(catSort * 1000 + idx++);
                    i.setEnabled(1);
                    i.setBuiltin(1);
                    i.setCreatedAt(LocalDateTime.now());
                    i.setUpdatedAt(LocalDateTime.now());
                    iconTemplateMapper.insert(i);
                    total++;
                }
            }
            log.info("已初始化 {} 个内置图标", total);
        } catch (Exception e) {
            log.warn("图标播种失败: {}", e.getMessage());
        }
    }

    // [name, themeKey, rootColor, lineColor, branchColors...]
    private static final List<Object[]> DEFAULT_THEMES = new ArrayList<>();

    static {
        // 配色主题（浅色底，branchColors 各不相同）
        DEFAULT_THEMES.add(new Object[]{"晨曦", "usmind-dawn", "#FF9F43", "#FF9F43", new String[]{"#FF9F43", "#FECB33", "#FF6B6B", "#F08BB4", "#C084FC", "#60A5FA", "#34D399", "#22D3EE"}});
        DEFAULT_THEMES.add(new Object[]{"彩虹", "usmind-rainbow", "#E8684A", "#E8684A", new String[]{"#E8684A", "#F6A609", "#5B8FF9", "#9270CA", "#2FB36B", "#22A7C9", "#F08BB4", "#FF9F43"}});
        DEFAULT_THEMES.add(new Object[]{"活力", "usmind-vibrant", "#FF5D5D", "#FF5D5D", new String[]{"#FF5D5D", "#FF9F43", "#FECA57", "#48DBFB", "#0ABDE3", "#10AC84", "#A29BFE", "#FD79A8"}});
        DEFAULT_THEMES.add(new Object[]{"舞动", "usmind-dance", "#F368E0", "#F368E0", new String[]{"#F368E0", "#FF9F43", "#FECA57", "#48DBFB", "#10AC84", "#A29BFE", "#FF6B6B", "#5F27CD"}});
        DEFAULT_THEMES.add(new Object[]{"代码", "usmind-code", "#569CD6", "#569CD6", new String[]{"#4EC9B0", "#569CD6", "#C586C0", "#DCDCAA", "#CE9178", "#9CDCFE", "#6A9955", "#D4D4D4"}});
        DEFAULT_THEMES.add(new Object[]{"和风", "usmind-breeze", "#F4A7B9", "#A8D8B9", new String[]{"#F4A7B9", "#A8D8B9", "#F5D6A8", "#A3C6C4", "#D4B8D0", "#B8C0E0", "#F0C4A8", "#C4C4A8"}});
        DEFAULT_THEMES.add(new Object[]{"岛屿", "usmind-island", "#00B4D8", "#00B4D8", new String[]{"#00B4D8", "#48CAE4", "#90E0EF", "#00A896", "#02C39A", "#0077B6", "#023E8A", "#35A7FF"}});
        DEFAULT_THEMES.add(new Object[]{"玫瑰", "usmind-rose", "#FF758F", "#FF758F", new String[]{"#FF758F", "#FF8C9B", "#F7B2B7", "#FAD0C4", "#FDA7DF", "#F78FB3", "#FFD3E0", "#E0A4B7"}});
        DEFAULT_THEMES.add(new Object[]{"薄荷", "usmind-mint", "#00B894", "#00B894", new String[]{"#55EFC4", "#00D2FF", "#81ECEC", "#18DCAA", "#00B894", "#55E6C1", "#A8E6CF", "#7ED321"}});
        // 原有内置主题（深色为主）
        DEFAULT_THEMES.add(new Object[]{"XMind 浅色", "usmind-light", "#4A5CF7", "#94a3b8", new String[]{"#2FB36B", "#22A7C9", "#F6A609", "#E8684A", "#F08BB4", "#9270CA", "#5B8FF9", "#FF9F43"}});
        DEFAULT_THEMES.add(new Object[]{"深空黑", "usmind-dark", "#3b82f6", "#64748b", new String[]{"#4ADE80", "#22D3EE", "#FACC15", "#FB7185", "#C084FC", "#818CF8", "#F97316", "#2DD4BF"}});
        DEFAULT_THEMES.add(new Object[]{"极简白", "usmind-white", "#1e293b", "#cbd5e1", new String[]{"#2FB36B", "#22A7C9", "#F6A609", "#E8684A", "#F08BB4", "#9270CA", "#5B8FF9", "#FF9F43"}});
        DEFAULT_THEMES.add(new Object[]{"暮色紫", "usmind-purple", "#7c3aed", "#8b5cf6", new String[]{"#C084FC", "#F472B6", "#FB923C", "#4ADE80", "#22D3EE", "#FACC15", "#818CF8", "#F87171"}});
        DEFAULT_THEMES.add(new Object[]{"森林绿", "usmind-green", "#22c55e", "#4ade80", new String[]{"#4ADE80", "#22D3EE", "#FACC15", "#FB7185", "#C084FC", "#F97316", "#34D399", "#60A5FA"}});
        DEFAULT_THEMES.add(new Object[]{"暖阳橙", "usmind-orange", "#f97316", "#fb923c", new String[]{"#FB923C", "#F87171", "#FACC15", "#4ADE80", "#22D3EE", "#C084FC", "#FBBF24", "#F472B6"}});
        DEFAULT_THEMES.add(new Object[]{"黑曜石", "usmind-blackgold", "#ffffff", "#666666", new String[]{"#ffffff", "#E63946", "#F4C542", "#3A86FF", "#4ADE80", "#C084FC", "#F97316", "#22D3EE"}});
    }

    private void initDefaultThemes() {
        int sort = 1;
        for (Object[] item : DEFAULT_THEMES) {
            String name = (String) item[0];
            String key = (String) item[1];
            String rootColor = (String) item[2];
            String lineColor = (String) item[3];
            String[] branchColors = (String[]) item[4];
            ThemeTemplate t = new ThemeTemplate();
            t.setName(name);
            t.setThemeKey(key);
            try {
                t.setBranchColors(objectMapper.writeValueAsString(branchColors));
                t.setConfigJson(objectMapper.writeValueAsString(buildConfig(name, rootColor, lineColor)));
            } catch (Exception e) {
                log.warn("主题 {} 配置序列化失败: {}", name, e.getMessage());
            }
            t.setSort(sort++);
            t.setEnabled(1);
            t.setBuiltin(1);
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            themeTemplateMapper.insert(t);
        }
    }

    /** 生成整套主题配置（浅色底 + 白框节点，根节点用主题主色） */
    private Map<String, Object> buildConfig(String name, String rootColor, String lineColor) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("paddingX", 18);
        cfg.put("paddingY", 8);
        cfg.put("lineWidth", 4);
        cfg.put("lineColor", lineColor);
        cfg.put("lineStyle", "curve");
        cfg.put("rootLineKeepSameInCurve", true);
        cfg.put("rootLineStartPositionKeepSameInCurve", true);
        cfg.put("backgroundColor", "#fafbff");
        cfg.put("backgroundImage", "none");
        cfg.put("backgroundOpacity", 1);
        cfg.put("backgroundRepeat", "no-repeat");
        cfg.put("backgroundPosition", "center center");
        cfg.put("backgroundSize", "cover");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("shape", "roundedRectangle");
        root.put("fillColor", rootColor);
        root.put("color", "#ffffff");
        root.put("fontSize", 36);
        root.put("fontWeight", "bold");
        root.put("borderColor", "transparent");
        root.put("borderWidth", 0);
        root.put("borderRadius", 20);
        root.put("paddingX", 44);
        root.put("paddingY", 26);
        cfg.put("root", root);

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("shape", "roundedRectangle");
        second.put("fillColor", "#ffffff");
        second.put("color", "#334155");
        second.put("fontSize", 25);
        second.put("fontWeight", "600");
        second.put("borderColor", "#cbd5e1");
        second.put("borderWidth", 1);
        second.put("borderRadius", 16);
        second.put("paddingX", 30);
        second.put("paddingY", 18);
        second.put("marginX", 80);
        second.put("marginY", 72);
        cfg.put("second", second);

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("shape", "roundedRectangle");
        node.put("fillColor", "#ffffff");
        node.put("color", "#475569");
        node.put("fontSize", 22);
        node.put("fontWeight", "500");
        node.put("borderColor", "#e2e8f0");
        node.put("borderWidth", 1);
        node.put("borderRadius", 14);
        node.put("paddingX", 24);
        node.put("paddingY", 14);
        node.put("marginX", 60);
        node.put("marginY", 34);
        cfg.put("node", node);
        return cfg;
    }
}
