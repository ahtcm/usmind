package com.usmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("request_log")
public class RequestLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String method;
    private String path;
    private String query;
    private Integer statusCode;
    /** 1=正常 0=异常 */
    private Integer success;
    /** info / warn / error */
    private String level;
    private String ip;
    private String ua;
    private Integer costMs;
    private String errorMsg;
    /** backend / frontend */
    private String source;
    /** 附加详情（前端上报 message / 后端异常堆栈等） */
    private String detail;
    /** 请求头（白名单，不含 Authorization/Cookie） */
    private String reqHeaders;
    /** 请求体（文本截断，敏感接口/文件上传不记录） */
    private String reqBody;
    /** 响应体（文本截断，非文本响应不记录） */
    private String respBody;
    private LocalDateTime requestAt;
}
