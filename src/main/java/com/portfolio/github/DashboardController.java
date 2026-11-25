package com.portfolio.github;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
@RestController
@RequestMapping("/api/v1/github")
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service) { this.service = service; }
    @GetMapping("/dashboard") public DashboardService.Summary dashboard() { return service.dashboard(); }
    @ExceptionHandler({RestClientException.class, IllegalStateException.class})
    public ResponseEntity<Map<String,String>> upstream(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", "GitHub dashboard unavailable"));
    }
}
