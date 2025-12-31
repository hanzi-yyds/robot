package com.hanzi.robot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "spark")
public class SparkConfig {
    private String appId;
    private String apiKey;
    private String apiSecret;
    private String host;
    private String path;
    private String domain;
    private Integer maxTokens;
    private Double temperature;
}