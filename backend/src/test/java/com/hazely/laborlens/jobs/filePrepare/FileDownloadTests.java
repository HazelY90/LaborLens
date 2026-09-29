package com.hazely.laborlens.jobs.filePrepare;

import com.hazely.laborlens.jobs.metricsExtraction.CsvSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileDownloadTests {
    @Test
    void rejectsUnapprovedOrInsecureRedirects() throws Exception {
        HttpClient client = mock(HttpClient.class);
        var response = response(302, "", Map.of("Location", List.of("http://example.org/file.csv")));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        assertThrows(IOException.class, () -> new FileDownload(client).fetch(CsvSource.ALF01));
        verify(client, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertFalse(FileDownload.isAllowed(URI.create("https://ws.cso.ie:444/file")));
        assertFalse(FileDownload.isAllowed(URI.create("https://user@ws.cso.ie/file")));
        assertTrue(FileDownload.isAllowed(URI.create(CsvSource.ALF01.url())));
    }

    @Test
    void retainsDownloadMetadataAndRejectsHttpFailures() throws Exception {
        HttpClient client = mock(HttpClient.class);
        var success = response(200, "CSV data", Map.of("Last-Modified", List.of("Test date")));
        var failed = response(503, "Unavailable", Map.of());
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(
                success, failed);
        var download = new FileDownload(client);
        var result = download.fetch(CsvSource.ALF01);
        assertEquals("CSV data", new String(result.input().bytes(), StandardCharsets.UTF_8));
        assertEquals("Test date", result.modified());
        assertThrows(IOException.class, () -> download.fetch(CsvSource.ALF01));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<byte[]> response(int status, String body, Map<String, List<String>> headers) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (key, value) -> true));
        return response;
    }
}
