package com.usmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_icon")
public class IconTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** emoji 字符（如 😀） */
    private String name;
    /** 分类（笑脸和情感 / 人类和身体 …） */
    private String category;
    /** 排序值（小在前） */
    private Integer sort;
    /** 是否启用 1/0 */
    private Integer enabled;
    /** 是否内置（内置可删除，但删除仅对单条生效） 1/0 */
    private Integer builtin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
