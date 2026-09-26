package com.usmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_resource")
public class FileResource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long mindMapId;
    private String originalName;
    private String storedName;
    private String storageType;
    private String url;
    private Long size;
    private String mimeType;
    private LocalDateTime createdAt;
}
