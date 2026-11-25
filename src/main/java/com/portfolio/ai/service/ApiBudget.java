package com.portfolio.ai.service;

import com.portfolio.ai.config.GeminiProperties;
import java.time.*;
import java.util.ArrayDeque;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** 全Gemini呼び出しを対象にした単一プロセスのローリング制限。 */
@Component
public class ApiBudget {
    private final GeminiProperties properties;
    private final ArrayDeque<Instant> calls = new ArrayDeque<>();
    public ApiBudget(GeminiProperties properties) { this.properties = properties; }
    public synchronized void acquire() {
        Instant now = Instant.now();
        while (!calls.isEmpty() && !calls.peek().isAfter(now.minusSeconds(86400))) {
            calls.remove();
        }
        long minute = calls.stream().filter(t -> t.isAfter(now.minusSeconds(60))).count();
        if (minute >= properties.rateLimit().requestsPerMinute()
                || calls.size() >= properties.rateLimit().requestsPerDay()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI request limit reached");
        }
        calls.add(now);
    }
}
