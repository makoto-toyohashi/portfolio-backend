package com.portfolio.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.portfolio.ai.config.GeminiProperties;
import com.portfolio.ai.service.ApiBudget;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

/** 外部APIへの通信とレスポンス検証を担当する。 */
@Component
public class GeminiClient {
    private final RestClient client;
    private final GeminiProperties properties;
    private final ApiBudget budget;
    public GeminiClient(@Qualifier("geminiRestClient") RestClient client,
                        GeminiProperties properties, ApiBudget budget) {
        this.client = client;
        this.properties = properties;
        this.budget = budget;
    }
    private JsonNode post(String operation, String model, Object body) {
        if (!properties.enabled() || properties.apiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI disabled");
        }
        budget.acquire();
        try {
            JsonNode result = client.post().uri("/models/{model}:" + operation, model)
                    .header("x-goog-api-key", properties.apiKey())
                    .body(body).retrieve().body(JsonNode.class);
            if (result == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
            return result;
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(e.getStatusCode().value() == 429
                    ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY, "Gemini request failed");
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini unavailable");
        }
    }
    private Map<String, Object> content(String text) {
        return Map.of("parts", List.of(Map.of("text", text)));
    }
    public String generate(String instruction, String input) {
        var body = Map.of("systemInstruction", content(instruction),
                "contents", List.of(content(input)),
                "generationConfig", Map.of("temperature", properties.generation().temperature(),
                        "maxOutputTokens", properties.generation().maxOutputTokens()));
        JsonNode result = post("generateContent", properties.model(), body);
        StringBuilder answer = new StringBuilder();
        for (JsonNode part : result.path("candidates").path(0).path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) answer.append(part.path("text").asText(""));
        }
        if (answer.toString().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No AI answer");
        return answer.toString();
    }
    /** 複数チャンクを一度のHTTPリクエストでベクトル化する。 */
    public List<double[]> embed(List<String> texts, boolean query) {
        var requests = texts.stream().map(text -> Map.of(
                "model", "models/" + properties.embeddingModel(), "content", content(text),
                "taskType", query ? "RETRIEVAL_QUERY" : "RETRIEVAL_DOCUMENT")).toList();
        JsonNode result = post("batchEmbedContents", properties.embeddingModel(), Map.of("requests", requests));
        List<double[]> vectors = new ArrayList<>();
        for (JsonNode embedding : result.path("embeddings")) {
            JsonNode values = embedding.path("values");
            if (!values.isArray() || values.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
            double[] vector = new double[values.size()];
            for (int i = 0; i < vector.length; i++) vector[i] = values.get(i).asDouble();
            vectors.add(vector);
        }
        if (vectors.size() != texts.size()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
        return vectors;
    }
}
