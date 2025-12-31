package com.hanzi.robot.websocket;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MessageHandler extends TextWebSocketHandler {

    @Autowired
    private SparkWebSocketClient sparkClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("WebSocket连接建立, sessionId: {}", sessionId);

        // 发送连接成功消息
//        Map<String, Object> response = Map.of(
//                "type", "connected",
//                "sessionId", sessionId,
//                "message", "连接成功"
//        );
        Map<String, Object> response = new HashMap<>(3);
        response.put("type", "connected");
        response.put("sessionId", sessionId);
        response.put("message", "连接成功");

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String payload = message.getPayload();
        log.debug("收到消息: {}", payload);

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            String type = jsonNode.get("type").asText();

            if ("chat".equals(type)) {
                String question = jsonNode.get("content").asText();
                log.info("收到聊天消息, session: {}, 问题: {}", sessionId, question);

                // 连接到星火API并发送问题
                sparkClient.connectToSpark(sessionId, session, question);
            }

        } catch (Exception e) {
            log.error("处理消息失败", e);
//            Map<String, Object> errorResponse = Map.of(
//                    "type", "error",
//                    "content", "消息格式错误",
//                    "sessionId", sessionId
//            );
            Map<String, Object> errorResponse = new HashMap<>(3);
            errorResponse.put("type", "error");
            errorResponse.put("content", "消息格式错误");
            errorResponse.put("sessionId", sessionId);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        sparkClient.closeUserSession(sessionId);
        log.info("WebSocket连接关闭, sessionId: {}, 状态: {}", sessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输错误, sessionId: " + session.getId(), exception);
    }
}