package com.hanzi.robot.controller;




import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanzi.robot.dto.ChatResponse;
import com.hanzi.robot.dto.PostmanTestResponse;
import com.hanzi.robot.service.SparkAuthService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import okhttp3.*;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private SparkAuthService authService;

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController() {
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 获取WebSocket连接URL（备用接口）
     */
    @GetMapping("/auth-url")
    public ResponseEntity<ChatResponse> getAuthUrl() {
        try {
            String authUrl = authService.generateAuthUrl();
            return ResponseEntity.ok(ChatResponse.success(authUrl));
        } catch (Exception e) {
            log.error("获取认证URL失败", e);
            return ResponseEntity.ok(ChatResponse.error("获取认证URL失败"));
        }
    }

    /**
     * 测试接口
     */
    @GetMapping("/test")
    public ResponseEntity<ChatResponse> test() {
        return ResponseEntity.ok(ChatResponse.success("服务正常运行"));
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<ChatResponse> health() {
        return ResponseEntity.ok(ChatResponse.success("OK"));
    }

    /**
     * 接收来自用户的问题并返回答案
     * @param question 问题
     * @return 答案
     */
    @PostMapping("/sync-test")
    public ResponseEntity<PostmanTestResponse> syncTest(@RequestBody String question) {
        PostmanTestResponse response = new PostmanTestResponse();
        response.setRequestId(String.valueOf(System.currentTimeMillis()));
        response.setQuestion(question);

        try {
            // 1. 生成认证URL
            String authUrl = authService.generateAuthUrl();
            response.setAuthUrl(authUrl);

            // 2. 使用CountDownLatch等待异步结果
            CountDownLatch latch = new CountDownLatch(1);
            StringBuilder fullResponse = new StringBuilder();

            Request request = new Request.Builder()
                    .url(authUrl)
                    .build();

            okHttpClient.newWebSocket(request, new WebSocketListener() {
                private StringBuilder messageBuffer = new StringBuilder();
                private boolean completed = false;

                @Override
                public void onOpen(WebSocket webSocket, okhttp3.Response resp) {
                    response.setConnectionStatus("CONNECTED");
                    response.setHttpCode(resp.code());
                    log.info("WebSocket连接成功");

                    // 发送消息到星火API
                    String sessionId = "user-" + System.currentTimeMillis();
                    String sparkMessage = authService.buildSparkMessage(question, sessionId);
                    webSocket.send(sparkMessage);
                    response.setSentPayload(sparkMessage);
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    try {
                        JsonNode jsonNode = objectMapper.readTree(text);
                        JsonNode header = jsonNode.get("header");

                        if (header != null) {
                            int code = header.path("code").asInt();
                            if (code != 0) {
                                response.setErrorCode(code);
                                response.setErrorMessage(header.path("message").asText());
                                latch.countDown();
                                return;
                            }

                            // 获取消息内容
                            JsonNode payload = jsonNode.get("payload");
                            if (payload != null && payload.has("choices")) {
                                JsonNode textNode = payload.get("choices").get("text");
                                if (textNode != null && textNode.isArray() && textNode.size() > 0) {
                                    String content = textNode.get(0).path("content").asText();
                                    if (content != null && !content.isEmpty()) {
                                        messageBuffer.append(content);
                                        fullResponse.append(content);
                                    }
                                }
                            }

                            // 检查是否结束
                            int status = header.path("status").asInt(0);
                            if (status == 2) {
                                completed = true;
                                response.setFullResponse(messageBuffer.toString());
                                response.setConnectionStatus("COMPLETED");
                                response.setMessageCount(response.getMessageCount() + 1);
                                latch.countDown();
                                webSocket.close(1000, "正常完成");
                            } else {
                                response.setMessageCount(response.getMessageCount() + 1);
                            }
                        }
                    } catch (Exception e) {
                        log.error("解析消息失败", e);
                        response.setErrorMessage("解析响应失败: " + e.getMessage());
                        latch.countDown();
                    }
                }

                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    log.info("连接关闭中: code={}, reason={}", code, reason);
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    log.info("连接已关闭");
                    if (!completed) {
                        response.setConnectionStatus("CLOSED_UNEXPECTEDLY");
                        latch.countDown();
                    }
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response resp) {
                    log.error("连接失败", t);
                    response.setConnectionStatus("FAILED");
                    response.setErrorMessage(t.getMessage());

                    if (resp != null) {
                        response.setHttpCode(resp.code());
                        try {
                            response.setErrorResponse(resp.body() != null ?
                                    resp.body().string() : "null");
                        } catch (Exception e) {
                            log.error("读取错误响应失败", e);
                        }
                    }
                    latch.countDown();
                }
            });

            // 3. 等待最多10秒
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            if (!completed) {
                response.setConnectionStatus("TIMEOUT");
                response.setErrorMessage("等待响应超时（10秒）");
                response.setPartialResponse(fullResponse.toString());
            }

            response.setTimestamp(System.currentTimeMillis());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("运行过程异常", e);
            response.setConnectionStatus("ERROR");
            response.setErrorMessage("运行过程异常: " + e.getMessage());
            response.setTimestamp(System.currentTimeMillis());
            return ResponseEntity.ok(response);
        }
    }
}