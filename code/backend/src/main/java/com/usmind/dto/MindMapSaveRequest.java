package com.usmind.dto;

import lombok.Data;

@Data
public class MindMapSaveRequest {
    private String name;
    /** 导图数据：接收任意 JSON（对象或字符串），Service 层统一序列化存库 */
    private Object data;
    private String layout;
    private String theme;
    /** 视图数据（缩放/平移）：对象或字符串 */
    private Object viewData;
    /** 手动样式节点 uid 列表（避免彩虹色覆盖） */
    private java.util.List<String> manualStyles;
    /** 乐观锁：前端加载/上次保存时的版本号；不匹配且未强制覆盖时返回 409 冲突 */
    private Integer baseVersion;
    /** 冲突强制覆盖：true 时忽略版本号直接保存 */
    private Boolean force;
    /** 本次会话中删除的节点 uid（含被删子树全部 uid）；并发合并时用于区分「我方删除」与「对方新增」并校验删除权限 */
    private java.util.List<String> deletedUids;
    /** 并发冲突解决方式：mine=冲突节点以我的为准；theirs=冲突节点以服务端（对方）为准；null=返回冲突列表由前端选择 */
    private String resolveConflicts;
}
