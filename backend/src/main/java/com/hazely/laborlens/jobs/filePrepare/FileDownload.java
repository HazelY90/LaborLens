package com.hazely.laborlens.jobs.filePrepare;

import com.hazely.laborlens.jobs.JobFiles;
import com.hazely.laborlens.jobs.SourceSpec;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/** Downloads only approved official hosts and validates redirects before following them. */
@Component
public class FileDownload {
    private static final Set<String> HOSTS = Set.of("ws.cso.ie", "enterprise.gov.ie", "www.enterprise.gov.ie",
            "gov.ie", "www.gov.ie", "assets.gov.ie");
    private final HttpClient client;

    public FileDownload() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    FileDownload(HttpClient client) {
        this.client = client;
    }

    public record Download(JobFiles.Input input, String url, String modified, String retrieved) {}

    public Download fetch(SourceSpec source) throws Exception {
        URI uri = URI.create(source.url());
        for (int attempt = 0; attempt < 6; attempt++) {
            if (!isAllowed(uri)) throw new IOException("Download redirect is outside approved sources");
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(120))
                    .header("User-Agent", "LaborLens/1.0").GET().build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (Set.of(301, 302, 303, 307, 308).contains(response.statusCode())) {
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new IOException("Download redirect has no location"));
                uri = uri.resolve(location);
                continue;
            }
            if (response.statusCode() != 200) throw new IOException("Download returned HTTP " + response.statusCode());
            return new Download(JobFiles.Input.of(response.body()), uri.toString(),
                    response.headers().firstValue("Last-Modified").orElse(""), Instant.now().toString());
        }
        throw new IOException("Too many download redirects");
    }

    static boolean isAllowed(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443) && HOSTS.contains(uri.getHost());
    }
}
