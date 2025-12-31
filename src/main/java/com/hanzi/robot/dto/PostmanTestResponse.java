package com.hanzi.robot.dto;


import lombok.Data;
import java.util.Map;
import java.util.HashMap;

@Data
public class PostmanTestResponse {
    // 请求信息
    private String requestId;
    private String question;
    private Long timestamp;

    // 连接信息
    private String authUrl;
    private String connectionStatus; // CONNECTING, CONNECTED, FAILED, COMPLETED, etc.
    private Integer httpCode;

    // 消息信息
    private String sentPayload;
    private String fullResponse;
    private String partialResponse;
    private Integer messageCount = 0;

    // 错误信息
    private Integer errorCode;
    private String errorMessage;
    private String errorResponse;

    // 附加信息
    private String message;
    private String instructions;
    private Map<String, Object> debugInfo = new HashMap<>();

    // 追加接收到的消息（用于流式响应）
    public void appendReceivedMessage(String message) {
        if (this.partialResponse == null) {
            this.partialResponse = message;
        } else {
            this.partialResponse += "\n" + message;
        }
    }
}
