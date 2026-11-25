package com.portfolio.github;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Service
public class DashboardService {
    private final GithubClient client;
    private final GithubService github;
    private final String owner;
    public DashboardService(GithubClient client, GithubService github, @Value("${github.owner}") String owner) {
        this.client = client; this.github = github; this.owner = owner;
    }
    public record Project(String name, String url, String updatedAt, long stars, long forks,
                          long openIssues, long closedIssues, long openPullRequests, long closedPullRequests,
                          Double issueCompletionPercent) {}
    public record Month(String month, long count) {}
    public record Summary(int repositories, long stars, long forks, long openIssues, long closedIssues,
                          long openPullRequests, long closedPullRequests, Map<String, Long> languages,
                          List<Month> monthlyCommits, List<Project> projects, Instant fetchedAt) {}

    @Cacheable(value="dashboard", sync=true)
    public Summary dashboard() {
        var now = Instant.now();
        var current = YearMonth.from(now.atZone(ZoneOffset.UTC));
        Map<String, Long> months = new LinkedHashMap<>();
        for (int i=11; i>=0; i--) months.put(current.minusMonths(i).toString(), 0L);
        String since = current.minusMonths(11).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toString();
        Map<String, Long> languages = new TreeMap<>();
        List<Project> projects = new ArrayList<>();
        long stars=0, forks=0, open=0, closed=0, po=0, pc=0;
        // 既存の許可リストと公開リポジトリ制限を再利用する。
        for (var repo : github.projects()) {
            String path = "/repos/" + owner + "/" + repo.name();
            var metadata = client.get(path);
            if (metadata.path("private").asBoolean(true)) continue;
            long ro=0, rc=0, rpo=0, rpc=0;
            // Issues APIにはPRも含まれるので、区別して集計する。
            for (var issue : client.pages(path + "/issues?state=all")) {
                boolean isOpen = "open".equals(issue.path("state").asText());
                if (issue.has("pull_request")) { if (isOpen) rpo++; else rpc++; }
                else { if (isOpen) ro++; else rc++; }
            }
            client.get(path + "/languages").fields().forEachRemaining(e -> languages.merge(e.getKey(), e.getValue().asLong(), Long::sum));
            // デフォルトブランチ上の全作者のコミットをリポジトリ単位で集計。
            try {
                for (var commit : client.pages(path + "/commits?since=" + since + "&until=" + now)) {
                    String date = commit.path("commit").path("committer").path("date").asText();
                    if (date.length() >= 7) months.computeIfPresent(date.substring(0,7), (k,v) -> v+1);
                }
            } catch (HttpClientErrorException.Conflict e) {
                // 空のリポジトリにはコミットがない。
            }
            long rs=metadata.path("stargazers_count").asLong(), rf=metadata.path("forks_count").asLong();
            stars+=rs; forks+=rf; open+=ro; closed+=rc; po+=rpo; pc+=rpc;
            projects.add(new Project(repo.name(), metadata.path("html_url").asText(), metadata.path("pushed_at").asText(""), rs, rf, ro, rc, rpo, rpc, ro+rc==0 ? null : 100.0*rc/(ro+rc)));
        }
        return new Summary(projects.size(), stars, forks, open, closed, po, pc, languages,
            months.entrySet().stream().map(e -> new Month(e.getKey(), e.getValue())).toList(), List.copyOf(projects), Instant.now());
    }
}
