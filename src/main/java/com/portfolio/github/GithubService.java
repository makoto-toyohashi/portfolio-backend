package com.portfolio.github;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import java.util.*;
import java.time.*;
@Service
public class GithubService {
    private final GithubClient client;
    private final String owner;
    private final Set<String> repos;
    public GithubService(GithubClient client, @Value("${github.owner}") String owner, @Value("${github.repositories}") String names) {
        this.client=client; this.owner=owner;
        if (!owner.matches("[A-Za-z0-9-]+")) throw new IllegalArgumentException("Set GITHUB_OWNER");
        repos=new LinkedHashSet<>();
        for(String name:names.split(",")) { name=name.trim(); if(!name.isEmpty()) { if(!name.matches("[A-Za-z0-9_.-]+") || name.equals(".") || name.equals("..")) throw new IllegalArgumentException("Invalid repository"); repos.add(name); } }
    }
    public record Project(String name,String description,String url,String language,int stars,int forks,String updatedAt) {}
    public record Commit(String sha,String message,String date,String url) {}
    public record Progress(Project project,long openIssues,long closedIssues,Double issueCompletionPercent,long openPullRequests,long mergedPullRequests,long closedUnmergedPullRequests,Map<String,Long> languages,List<Commit> recentCommits,Map<String,Long> dailyCommits,String latestRelease,Instant fetchedAt) {}
    private String path(String name) { if(!repos.contains(name)) throw new ResponseStatusException(HttpStatus.NOT_FOUND); return "/repos/"+owner+"/"+name; }
    private JsonNode publicRepo(String name) { var r=client.get(path(name)); if(r.path("private").asBoolean(true)) throw new ResponseStatusException(HttpStatus.NOT_FOUND); return r; }
    private Project project(JsonNode r) { return new Project(r.path("name").asText(),r.path("description").asText(""),r.path("html_url").asText(),r.path("language").asText(""),r.path("stargazers_count").asInt(),r.path("forks_count").asInt(),r.path("pushed_at").asText("")); }
    @Cacheable("projects") public List<Project> projects() { List<Project> result=new ArrayList<>(); for(String name:repos) { try { result.add(project(publicRepo(name))); } catch(ResponseStatusException e) { if(e.getStatusCode().value()!=404) throw e; } catch(HttpClientErrorException.NotFound e) { /* 非公開・存在しないリポジトリは除外 */ } } return result; }
    @Cacheable("progress") public Progress progress(String name) {
        String p=path(name); Project project=project(publicRepo(name));
        var issues=client.pages(p+"/issues?state=all"); long open=issues.stream().filter(i->!i.has("pull_request")&&i.path("state").asText().equals("open")).count(); long closed=issues.stream().filter(i->!i.has("pull_request")&&i.path("state").asText().equals("closed")).count();
        var pulls=client.pages(p+"/pulls?state=all"); long po=0,pm=0,pc=0; for(var pr:pulls) { if(pr.path("state").asText().equals("open")) po++; else if(pr.hasNonNull("merged_at")) pm++; else pc++; }
        Map<String,Long> languages=new LinkedHashMap<>(); client.get(p+"/languages").fields().forEachRemaining(e->languages.put(e.getKey(),e.getValue().asLong()));
        LocalDate today=LocalDate.now(ZoneOffset.UTC); Map<String,Long> daily=new LinkedHashMap<>(); for(int i=29;i>=0;i--) daily.put(today.minusDays(i).toString(),0L);
        List<Commit> commits=new ArrayList<>(); try { for(var c:client.pages(p+"/commits?since="+today.minusDays(29).atStartOfDay().toInstant(ZoneOffset.UTC))) { var data=c.path("commit"); String date=data.path("committer").path("date").asText(); if(date.length()>=10) daily.computeIfPresent(date.substring(0,10),(k,v)->v+1); if(commits.size()<10) commits.add(new Commit(c.path("sha").asText(),data.path("message").asText(),date,c.path("html_url").asText())); } } catch(HttpClientErrorException.Conflict e) { /* 空リポジトリ */ }
        String release=null; try { release=client.get(p+"/releases/latest").path("tag_name").asText(); } catch(HttpClientErrorException.NotFound e) { /* リリースなし */ }
        return new Progress(project,open,closed,open+closed==0?null:100.0*closed/(open+closed),po,pm,pc,languages,commits,daily,release,Instant.now());
    }
}
