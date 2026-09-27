package com.usmind.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 数据库备份/恢复：导出全部业务表为 JSON，或从备份 JSON 整体恢复。
 * 覆盖表：sys_user / mind_map / sys_config / sys_theme / sys_icon / oss_config / file_resource
 */
@Service
public class DataBackupService {

    // 备份白名单表（顺序即导出/恢复顺序，恢复时按依赖先用户后业务数据）
    private static final List<String> TABLES = Arrays.asList(
            "sys_user", "mind_map", "sys_config", "sys_theme", "sys_icon", "oss_config", "file_resource"
    );

    private final JdbcTemplate jdbcTemplate;

    public DataBackupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 备份：读取全部业务表数据 */
    public Map<String, Object> backup() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "usmind-backup");
        out.put("version", 1);
        out.put("createdAt", LocalDateTime.now().toString());
        Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();
        for (String t : TABLES) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM `" + t + "`");
            tables.put(t, rows);
        }
        out.put("tables", tables);
        out.put("summary", summaryOf(tables));
        return out;
    }

    /** 恢复：事务内清空并写回备份数据 */
    @Transactional
    public Map<String, Object> restore(Map<String, Object> doc) {
        if (doc == null || !"usmind-backup".equals(doc.get("type"))) {
            throw new IllegalArgumentException("不是有效的 usmind 备份文件");
        }
        Object tablesObj = doc.get("tables");
        if (!(tablesObj instanceof Map)) {
            throw new IllegalArgumentException("备份文件缺少 tables 数据");
        }
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> tables = (Map<String, List<Map<String, Object>>>) tablesObj;

        Map<String, Integer> restored = new LinkedHashMap<>();
        int total = 0;
        for (String table : TABLES) {
            List<Map<String, Object>> rows = tables.get(table);
            if (rows == null) continue; // 老备份可能缺表：跳过不破坏
            jdbcTemplate.update("DELETE FROM `" + table + "`");
            int n = 0;
            for (Map<String, Object> row : rows) {
                if (row == null || row.isEmpty()) continue;
                insertRow(table, row);
                n++;
            }
            restored.put(table, n);
            total += n;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restored", restored);
        result.put("total", total);
        return result;
    }

    /** 单行写入：列名白名单化（仅 [a-zA-Z0-9_]，防注入），显式保留原 id 保证关联不丢 */
    private void insertRow(String table, Map<String, Object> row) {
        List<String> cols = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            String col = e.getKey();
            if (col == null || !col.matches("[A-Za-z0-9_]+")) continue;
            cols.add("`" + col + "`");
            vals.add(e.getValue());
        }
        if (cols.isEmpty()) return;
        String placeholders = String.join(",", Collections.nCopies(cols.size(), "?"));
        String sql = "INSERT INTO `" + table + "` (" + String.join(",", cols) + ") VALUES (" + placeholders + ")";
        jdbcTemplate.update(sql, vals.toArray());
    }

    private Map<String, Integer> summaryOf(Map<String, List<Map<String, Object>>> tables) {
        Map<String, Integer> s = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : tables.entrySet()) {
            s.put(e.getKey(), e.getValue() == null ? 0 : e.getValue().size());
        }
        return s;
    }
}
