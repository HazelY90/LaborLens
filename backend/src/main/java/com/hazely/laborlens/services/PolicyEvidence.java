package com.hazely.laborlens.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;

/** Optional citations are read only from committed run IDs selected by the database. */
@Component
public class PolicyEvidence {
    private final Path root;
    private final JsonMapper json = JsonMapper.builder().build();
    public record Citation(String source, int page) {}
    public PolicyEvidence(@Value("${app.data.root:../data}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }
    public Map<Long, Citation> read(long run) {
        if (run < 1) return Map.of();
        try {
            Path base = root.toRealPath();
            Path path = base;
            // Reject redirected artifact directories, including symlinks that remain inside the root.
            for (String part : List.of("runs", Long.toString(run), "evidence.json")) {
                path = path.resolve(part);
                if (Files.isSymbolicLink(path)) return Map.of();
            }
            if (!Files.isRegularFile(path) || !path.toRealPath().startsWith(base)
                    || Files.size(path) > 50_000_000) return Map.of();
            var entries = json.readTree(Files.readString(path));
            if (!entries.isArray()) return Map.of();
            Map<Long, Citation> pages = new HashMap<>();
            for (var entry : entries) {
                var id = entry.path("policy_id");
                var policy = entry.path("policy");
                var page = policy.path("page");
                var source = policy.path("source_file");
                if (id.isIntegralNumber() && page.isIntegralNumber() && id.asLong() > 0
                        && page.canConvertToInt() && page.asInt() > 0 && source.isString()) {
                    pages.put(id.asLong(), new Citation(source.asString(), page.asInt()));
                }
            }
            return Map.copyOf(pages);
        } catch (IOException | RuntimeException error) {
            // Missing or damaged evidence must not hide otherwise valid database policies.
            return Map.of();
        }
    }
}
