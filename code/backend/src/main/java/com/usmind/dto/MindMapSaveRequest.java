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
}
