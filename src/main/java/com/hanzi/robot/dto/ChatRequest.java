package com.hanzi.robot.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String question;
    private String conversationId;
    private Integer maxTokens;
    private Double temperature;
}