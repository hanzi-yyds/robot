package com.hanzi.robot.dto;

import lombok.Data;

@Data
public class ChatResponse<T> {
    private Integer code;
    private String message;
    private T data;
    private Long timestamp;

    public static <T> ChatResponse<T> success(T data) {
        ChatResponse<T> response = new ChatResponse<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(data);
        response.setTimestamp(System.currentTimeMillis());
        return response;
    }

    public static <T> ChatResponse<T> error(String message) {
        ChatResponse<T> response = new ChatResponse<>();
        response.setCode(500);
        response.setMessage(message);
        response.setTimestamp(System.currentTimeMillis());
        return response;
    }
}