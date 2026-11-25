package com.portfolio.github;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

@RestController
@RequestMapping("/api/v1/github")
public class ContributionController {
    private final ContributionService service;
    public ContributionController(ContributionService service) { this.service = service; }

    @GetMapping("/contributions")
    public ContributionService.Response contributions() { return service.contributions(); }

    @ExceptionHandler({RestClientException.class, IllegalStateException.class, java.time.DateTimeException.class})
    public ResponseEntity<Map<String, String>> upstream(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("message", "GitHub contribution API is temporarily unavailable"));
    }
}
