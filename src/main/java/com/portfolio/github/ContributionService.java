package com.portfolio.github;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class ContributionService {
    private final GithubGraphqlClient client;
    private final String owner;

    public ContributionService(GithubGraphqlClient client, @Value("${github.owner}") String owner) {
        this.client = client;
        this.owner = owner;
    }

    public record Day(LocalDate date, int count, String level, String color) {}
    public record Week(List<Day> days) {}
    public record Response(String username, int totalContributions, long monthlyContributions,
                           long activeDays, int currentStreak, int longestStreak,
                           List<Week> weeks, Instant fetchedAt) {}

    @Cacheable(value = "contributions", sync = true)
    public Response contributions() {
        var calendar = client.calendar(owner);
        List<Week> weeks = new ArrayList<>();
        NavigableMap<LocalDate, Integer> counts = new TreeMap<>();
        for (var week : calendar.path("weeks")) {
            List<Day> days = new ArrayList<>();
            for (var node : week.path("contributionDays")) {
                LocalDate date = LocalDate.parse(node.path("date").asText());
                int count = node.path("contributionCount").asInt();
                days.add(new Day(date, count, node.path("contributionLevel").asText(),
                        node.path("color").asText()));
                counts.put(date, count);
            }
            weeks.add(new Week(List.copyOf(days)));
        }
        // GitHubから返された最終日を集計基準とする。
        if (counts.isEmpty()) throw new IllegalStateException("Empty contribution calendar");
        LocalDate today = counts.lastKey();
        long monthly = counts.entrySet().stream()
                .filter(e -> YearMonth.from(e.getKey()).equals(YearMonth.from(today)))
                .mapToLong(Map.Entry::getValue).sum();
        long active = counts.values().stream().filter(n -> n > 0).count();
        int longest = 0, run = 0;
        LocalDate previous = null;
        for (var entry : counts.entrySet()) {
            if (previous != null && !previous.plusDays(1).equals(entry.getKey())) run = 0;
            run = entry.getValue() > 0 ? run + 1 : 0;
            longest = Math.max(longest, run);
            previous = entry.getKey();
        }
        // 当日が未活動なら昨日からの連続記録を維持する。
        LocalDate cursor = counts.getOrDefault(today, 0) > 0 ? today : today.minusDays(1);
        int current = 0;
        while (counts.getOrDefault(cursor, 0) > 0) {
            current++;
            cursor = cursor.minusDays(1);
        }
        return new Response(owner, calendar.path("totalContributions").asInt(), monthly,
                active, current, longest, List.copyOf(weeks), Instant.now());
    }
}
