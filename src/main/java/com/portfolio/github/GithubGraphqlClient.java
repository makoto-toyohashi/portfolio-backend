package com.portfolio.github;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GithubGraphqlClient {
    private final RestClient client;
    private final boolean configured;

    public GithubGraphqlClient(@Value("${github.token}") String token) {
        configured = !token.isBlank();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        var builder = RestClient.builder().baseUrl("https://api.github.com")
                .requestFactory(factory).defaultHeader("Accept", "application/json");
        if (configured) builder.defaultHeader("Authorization", "Bearer " + token);
        client = builder.build();
    }

    // 固定クエリのみ実行し、外部から任意のクエリを受け付けない。
    public JsonNode calendar(String owner) {
        if (!configured) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "GitHub contribution token is not configured");
        String query = """
            query ($login: String!) {
              user(login: $login) {
                contributionsCollection {
                  contributionCalendar {
                    totalContributions
                    weeks {
                      contributionDays { date contributionCount contributionLevel color }
                    }
                  }
                }
              }
            }
            """;
        JsonNode result = client.post().uri("/graphql").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", query, "variables", Map.of("login", owner)))
                .retrieve().body(JsonNode.class);
        // GraphQLはHTTP 200でもエラーを返すことがある。
        if (result == null || result.path("errors").size() > 0)
            throw new IllegalStateException("GitHub GraphQL request failed");
        JsonNode calendar = result.path("data").path("user").path("contributionsCollection")
                .path("contributionCalendar");
        if (!calendar.path("weeks").isArray() || !calendar.path("totalContributions").isIntegralNumber())
            throw new IllegalStateException("Invalid contribution calendar");
        return calendar;
    }
}
