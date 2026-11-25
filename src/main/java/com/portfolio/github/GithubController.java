package com.portfolio.github;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import java.util.*;
@RestController
@RequestMapping("/api/v1/github")
public class GithubController {
    private final GithubService service;
    public GithubController(GithubService service) { this.service=service; }
    @GetMapping("/projects") public List<GithubService.Project> projects() { return service.projects(); }
    @GetMapping("/projects/{name}/progress") public GithubService.Progress progress(@PathVariable String name) { return service.progress(name); }
    @ExceptionHandler({RestClientException.class,IllegalStateException.class}) public ResponseEntity<Map<String,String>> upstream(Exception e) { return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message","GitHub API is temporarily unavailable")); }
}
