package com.usmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mind_map")
public class MindMap {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String data;
    private String layout;
    private String theme;
    private String viewData;
    private String manualStyles;
    /** 协作配置 JSON：{"enableOwnerLock":bool,"approveTimeout1":120,"approveTimeout2":180,"grantMinutes":10} */
    private String collabConfig;
    private Integer isShared;
    private String shareToken;
    private String sharePassword;
    private String sharePasswordPlain;
    private Integer viewCount;
    private Integer version;
    private LocalDateTime createdAt;
    /** 非表字段：是否为机构协作导图（列表标记用） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Boolean isCollab;
    private LocalDateTime updatedAt;
}
