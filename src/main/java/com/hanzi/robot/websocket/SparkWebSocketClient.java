package com.hanzi.robot.websocket;



import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanzi.robot.service.SparkAuthService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SparkWebSocketClient {

    @Autowired
    private SparkAuthService authService;

    private OkHttpClient okHttpClient;
    private Map<String, WebSocket> connections = new ConcurrentHashMap<>();
    private Map<String, StringBuilder> messageBuffers = new ConcurrentHashMap<>();
    private Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * 建立与星火API的WebSocket连接
     */
    public void connectToSpark(String sessionId, WebSocketSession userSession, String question) {
        try {
            userSessions.put(sessionId, userSession);
            messageBuffers.put(sessionId, new StringBuilder());

            String authUrl = authService.generateAuthUrl();
            log.info("Generated auth URL for session: {}", sessionId);

            Request request = new Request.Builder()
                    .url(authUrl)
                    .build();

            okHttpClient.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(okhttp3.WebSocket webSocket, Response response) {
                    log.info("Connected to Spark API, session: {}", sessionId);
                    connections.put(sessionId, webSocket);

                    // 发送用户消息
                    String sparkMessage = authService.buildSparkMessage(question, sessionId);
                    webSocket.send(sparkMessage);
                    log.debug("Sent message to Spark: {}", sparkMessage);
                }

                @Override
                public void onMessage(okhttp3.WebSocket webSocket, String text) {
                    try {
                        JsonNode jsonNode = objectMapper.readTree(text);
                        JsonNode header = jsonNode.get("header");

                        if (header != null) {
                            int code = header.get("code").asInt();
                            if (code != 0) {
                                log.error("Spark API error, code: {}, message: {}",
                                        code, header.get("message").asText());
                                sendErrorToUser(sessionId, "星火API返回错误: " + code);
                                return;
                            }

                            // 检查是否结束
                            boolean end = header.has("status") && header.get("status").asInt() == 2;

                            // 获取回答内容
                            JsonNode payload = jsonNode.get("payload");
                            if (payload != null && payload.has("choices")) {
                                JsonNode choices = payload.get("choices");
                                JsonNode textNode = choices.get("text");
                                if (textNode != null && textNode.isArray() && textNode.size() > 0) {
                                    JsonNode contentNode = textNode.get(0).get("content");
                                    if (contentNode != null) {
                                        String content = contentNode.asText();
                                        messageBuffers.get(sessionId).append(content);

                                        // 实时发送给前端
                                        sendToUser(sessionId, content, false);

                                        if (end) {
                                            // 发送完整回答并关闭连接
                                            String fullResponse = messageBuffers.get(sessionId).toString();
                                            sendToUser(sessionId, fullResponse, true);
                                            cleanup(sessionId);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("处理Spark消息失败", e);
                        sendErrorToUser(sessionId, "处理消息时发生错误");
                    }
                }

                @Override
                public void onClosing(okhttp3.WebSocket webSocket, int code, String reason) {
                    log.info("Closing connection to Spark, session: {}, code: {}", sessionId, code);
                }

                @Override
                public void onFailure(okhttp3.WebSocket webSocket, Throwable t, Response response) {
                    log.error("Spark WebSocket connection failed, session: {}", sessionId, t);
                    sendErrorToUser(sessionId, "连接星火API失败: " + t.getMessage());
                    cleanup(sessionId);
                }

                @Override
                public void onClosed(okhttp3.WebSocket webSocket, int code, String reason) {
                    log.info("Connection closed, session: {}, code: {}, reason: {}", sessionId, code, reason);
                    cleanup(sessionId);
                }
            });

        } catch (Exception e) {
            log.error("连接星火API失败", e);
            sendErrorToUser(sessionId, "建立连接失败: " + e.getMessage());
            cleanup(sessionId);
        }
    }

    /**
     * 发送消息给前端用户
     */
    private void sendToUser(String sessionId, String content, boolean isEnd) {
        try {
            WebSocketSession session = userSessions.get(sessionId);
            if (session != null && session.isOpen()) {
                Map<String, Object> response = new HashMap<>();
                response.put("type", isEnd ? "complete" : "stream");
                response.put("content", content);
                response.put("sessionId", sessionId);
                response.put("timestamp", System.currentTimeMillis());

                String jsonResponse = objectMapper.writeValueAsString(response);
                session.sendMessage(new TextMessage(jsonResponse));
            }
        } catch (IOException e) {
            log.error("发送消息给用户失败", e);
        }
    }

    /**
     * 发送错误信息给前端用户
     */
    private void sendErrorToUser(String sessionId, String error) {
        try {
            WebSocketSession session = userSessions.get(sessionId);
            if (session != null && session.isOpen()) {
                Map<String, Object> response = new HashMap<>();
                response.put("type", "error");
                response.put("content", error);
                response.put("sessionId", sessionId);

                String jsonResponse = objectMapper.writeValueAsString(response);
                session.sendMessage(new TextMessage(jsonResponse));
            }
        } catch (IOException e) {
            log.error("发送错误信息给用户失败", e);
        }
    }

    /**
     * 清理资源
     */
    private void cleanup(String sessionId) {
        WebSocket webSocket = connections.remove(sessionId);
        if (webSocket != null) {
            webSocket.close(1000, "正常关闭");
        }
        messageBuffers.remove(sessionId);
        userSessions.remove(sessionId);
    }

    /**
     * 关闭用户会话
     */
    public void closeUserSession(String sessionId) {
        cleanup(sessionId);
    }

    @PreDestroy
    public void destroy() {
        if (okHttpClient != null) {
            okHttpClient.dispatcher().executorService().shutdown();
        }
    }
}