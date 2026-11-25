package com.portfolio.github;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.util.*;
@Component
public class GithubClient {
    private final RestClient client;
    public GithubClient(@Value("${github.token}") String token) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); factory.setReadTimeout(15000);
        var builder = RestClient.builder().baseUrl("https://api.github.com").requestFactory(factory)
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28");
        if (!token.isBlank()) builder.defaultHeader("Authorization", "Bearer " + token);
        client = builder.build();
    }
    public JsonNode get(String path, Object... args) {
        return Objects.requireNonNull(client.get().uri(path, args).retrieve().body(JsonNode.class));
    }
    // 全ページを取得する。上限超過時は不完全な集計を返さない。
    public List<JsonNode> pages(String path) {
        List<JsonNode> result = new ArrayList<>();
        for (int page=1; page<=100; page++) {
            JsonNode data = get(path + (path.contains("?") ? "&" : "?") + "per_page=100&page=" + page);
            if (!data.isArray()) throw new IllegalStateException("Unexpected GitHub response");
            data.forEach(result::add);
            if (data.size()<100) return result;
        }
        throw new IllegalStateException("Pagination limit exceeded");
    }
}
