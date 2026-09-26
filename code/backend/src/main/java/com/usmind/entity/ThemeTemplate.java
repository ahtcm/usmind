package com.usmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_theme")
public class ThemeTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 主题名称（展示名，如：晨曦） */
    private String name;
    /** 主题唯一标识（defineTheme 注册 key） */
    private String themeKey;
    /** 分支彩虹配色顺序 JSON 数组 */
    private String branchColors;
    /** simple-mind-map 主题配置 JSON */
    private String configJson;
    /** 排序值（小在前） */
    private Integer sort;
    /** 是否启用 1/0 */
    private Integer enabled;
    /** 是否内置（内置不可删除） 1/0 */
    private Integer builtin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
