package com.portfolio.ai.service;

import com.portfolio.ai.client.GeminiClient;
import com.portfolio.ai.config.GeminiProperties;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** 公開用Markdownのみを検索対象とする。初回質問時に索引を生成する。 */
@Service
public class RagService {
    public record Source(String name, String text) {}
    private record Entry(Source source, double[] vector) {}
    private record Match(Source source, double score) {}
    private final GeminiClient client;
    private final GeminiProperties properties;
    private List<Entry> index;
    public RagService(GeminiClient client, GeminiProperties properties) {
        this.client = client;
        this.properties = properties;
    }
    private synchronized List<Entry> index() {
        if (index != null) return index;
        List<Source> sources = new ArrayList<>();
        int size = properties.rag().chunkSize();
        int overlap = properties.rag().chunkOverlap();
        if (size <= overlap || overlap < 0 || size > 4000) throw new IllegalStateException("Invalid chunk settings");
        try {
            for (var resource : new PathMatchingResourcePatternResolver().getResources("classpath*:knowledge/*.md")) {
                String text;
                try (var stream = resource.getInputStream()) {
                    text = new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
                }
                int[] points = text.codePoints().toArray();
                for (int start = 0; start < points.length; start += size - overlap) {
                    int length = Math.min(size, points.length - start);
                    sources.add(new Source(resource.getFilename(), new String(points, start, length)));
                    if (start + length == points.length) break;
                }
            }
        } catch (IOException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Knowledge read failed"); }
        if (sources.isEmpty() || sources.size() > 100) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Knowledge must contain 1–100 chunks");
        var vectors = client.embed(sources.stream().map(Source::text).toList(), false);
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) entries.add(new Entry(sources.get(i), vectors.get(i)));
        index = List.copyOf(entries);
        return index;
    }
    public List<Source> search(String question) {
        var entries = index();
        double[] query = client.embed(List.of(question), true).get(0);
        return entries.stream().map(e -> new Match(e.source(), cosine(e.vector(), query)))
                .filter(m -> m.score() > 0.35)
                .sorted(Comparator.comparingDouble(Match::score).reversed())
                .limit(properties.rag().topK()).map(Match::source).toList();
    }
    static double cosine(double[] a, double[] b) {
        if (a.length != b.length) throw new IllegalStateException("Embedding dimension mismatch");
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i]*b[i]; aa += a[i]*a[i]; bb += b[i]*b[i]; }
        return aa == 0 || bb == 0 ? 0 : dot / Math.sqrt(aa*bb);
    }
}
