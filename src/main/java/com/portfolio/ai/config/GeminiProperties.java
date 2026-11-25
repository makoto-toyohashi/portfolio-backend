package com.portfolio.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Geminiの設定。APIキーは環境変数から取得する。 */
@ConfigurationProperties("gemini")
public record GeminiProperties(
        String apiKey, String projectId, String projectNumber,
        String baseUrl, String model, String embeddingModel,
        Generation generation, Rag rag, RateLimit rateLimit,
        Timeout timeout, boolean enabled, String accessKey) {
    public record Generation(double temperature, int maxOutputTokens) {}
    public record Rag(int chunkSize, int chunkOverlap, int topK) {}
    public record RateLimit(int requestsPerMinute, int requestsPerDay) {}
    public record Timeout(int connectSeconds, int readSeconds) {}
}
