package com.usmind.dto;

import lombok.Data;

@Data
public class OssConfigRequest {
    private String name;
    private String type;
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private String region;
    private String domain;
    private String configJson;
}
