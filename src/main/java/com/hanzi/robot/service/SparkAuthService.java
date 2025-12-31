package com.hanzi.robot.service;

import com.hanzi.robot.config.SparkConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Service
public class SparkAuthService {

    @Autowired
    private SparkConfig sparkConfig;

    /**
     * 生成WebSocket认证URL
     */
    public String generateAuthUrl() {
        try {
            // 1. 生成RFC1123格式日期
            String date = getRfc1123Date();

            // 2. 生成签名原始串
            String signatureOrigin = String.format("host: %s\ndate: %s\nGET %s HTTP/1.1",
                    sparkConfig.getHost(),
                    date,
                    sparkConfig.getPath());

            // 3. 计算HMAC-SHA256签名
            String signature = calculateHmacSha256(sparkConfig.getApiSecret(), signatureOrigin);

            // 4. 生成authorization
            String authorization = String.format(
                    "api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
                    sparkConfig.getApiKey(),
                    signature);

            // 5. 编码参数并构建URL
            String encodedAuth = Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8));
            String encodedDate = URLEncoder.encode(date, StandardCharsets.UTF_8.toString());
            String encodedHost = URLEncoder.encode(sparkConfig.getHost(), StandardCharsets.UTF_8.toString());

            return String.format("wss://%s%s?authorization=%s&date=%s&host=%s",
                    sparkConfig.getHost(),
                    sparkConfig.getPath(),
                    encodedAuth,
                    encodedDate,
                    encodedHost);

        } catch (Exception e) {
            log.error("生成认证URL失败", e);
            throw new RuntimeException("认证URL生成失败", e);
        }
    }

    /**
     * 计算HMAC-SHA256签名
     */
    private String calculateHmacSha256(String secret, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] bytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * 获取RFC1123格式日期
     */
    private String getRfc1123Date() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date());
    }

    /**
     * 生成请求星火API的消息体
     */
    public String buildSparkMessage(String question, String conversationId) {
        Map<String, Object> message = new LinkedHashMap<>();

        // Header
        Map<String, Object> header = new HashMap<>();
        header.put("app_id", sparkConfig.getAppId());
        header.put("uid", conversationId != null ? conversationId : UUID.randomUUID().toString());
        message.put("header", header);

        // Parameter
        Map<String, Object> parameter = new HashMap<>();
        Map<String, Object> chatParam = new HashMap<>();
        chatParam.put("domain", sparkConfig.getDomain());
        chatParam.put("temperature", sparkConfig.getTemperature());
        chatParam.put("max_tokens", sparkConfig.getMaxTokens());
        parameter.put("chat", chatParam);
        message.put("parameter", parameter);

        // Payload
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> messageMap = new HashMap<>();
        List<Map<String, String>> textList = new ArrayList<>();

        // 用户消息
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", question);
        textList.add(userMsg);

        messageMap.put("text", textList);
        payload.put("message", messageMap);
        message.put("payload", payload);

        // 转换为JSON
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("构建消息JSON失败", e);
            return "{}";
        }
    }
}