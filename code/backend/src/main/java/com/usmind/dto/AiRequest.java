package com.usmind.dto;

import lombok.Data;

@Data
public class AiRequest {
    private String mode;
    private String prompt;
    private String mindMapData;
}
