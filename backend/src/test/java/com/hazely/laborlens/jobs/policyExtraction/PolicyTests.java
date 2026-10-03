package com.hazely.laborlens.jobs.policyExtraction;

import com.hazely.laborlens.entities.enums.PolicyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import java.time.Duration;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PolicyTests {
    private static final PdfSource SOURCE = PdfSource.STRATEGY_2025;
    private static final String QUOTE = "We will expand domestic skills training across the regions.";
    private static final byte[] PDF = "%PDF-1.7\nComplete test attachment\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path root;

    @Test
    void validatesFieldsAndRejectsInvalidPagesSourcesAndPeriods() {
        var valid = policy(SOURCE.fileName(), 2025, 3, QUOTE);
        assertEquals(List.of(valid), PolicyCheck.validate(SOURCE, List.of(valid, valid)));
        for (var invalid : List.of(policy("wrong.pdf", 2025, 3, QUOTE),
                policy(SOURCE.fileName(), 2024, 3, QUOTE), policy(SOURCE.fileName(),
                2025, 0, QUOTE),
                policy(SOURCE.fileName(), 2025, 3, "Too short"))) {
            assertThrows(IllegalArgumentException.class, () -> PolicyCheck.validate(SOURCE,
                    List.of(invalid)));
        }
    }

    @Test
    void extractsStructuredPoliciesAndArchivesModelOutputWithoutNetwork() throws Exception {
        // Exercise the documented JSON contract independently of Java serialization.
        String raw = """
                {"policies": [{
                  "period_start": 2025,
                  "period_end": 2028,
                  "policy": "Expand domestic skills training.",
                  "type": "SKILLS_DEVELOPMENT",
                  "source_file": "statement-of-strategy-2025-2028.pdf",
                  "page": 3,
                  "quote": "We will expand domestic skills training across the regions."
                }]}
                """;
        PolicyAi ai = ai(raw, "stop", "test-v1");
        assertEquals(1, ai.extract(SOURCE, PDF, root).size());
        assertEquals(raw, Files.readString(root.resolve("response.json")));
        assertTrue(Files.exists(root.resolve("model.properties")));
        String prompt = Files.readString(root.resolve("system-prompt.txt"));
        assertTrue(prompt.contains("\"rules\""));
        assertTrue(prompt.contains("\"structure\""));
        assertTrue(prompt.contains("\"period_start\""));
        assertNotEquals(ai.version(), ai(raw, "stop", "test-v2").version());
    }

    @Test
    void rejectsTruncationMalformedOutputAndEmptyExtraction() throws Exception {
        String[] replies = {
            "{broken", "{\"policies\":[]}", "{\"policies\":null}"
        };
        for (int i = 0; i < replies.length; i++) {
            Path dir = Files.createDirectory(root.resolve("case-" + i));
            PolicyAi ai = ai(replies[i], "stop", "test-v1");
            assertThrows(IllegalArgumentException.class, () -> ai.extract(SOURCE, PDF, dir));
        }
        Path dir = Files.createDirectory(root.resolve("truncated"));
        assertThrows(IllegalArgumentException.class,
                () -> ai("{\"policies\":[]}", "length", "test-v1").extract(SOURCE, PDF,
                dir));
    }

    @Test
    void checksPdfBytesWithoutExternalTools() {
        PolicyCheck.validatePdf(PDF);
        assertThrows(IllegalArgumentException.class, () -> PolicyCheck.validatePdf(null));
        assertThrows(IllegalArgumentException.class, () -> PolicyCheck.validatePdf(new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> PolicyCheck.validatePdf("<html>Error</html>".getBytes(StandardCharsets.US_ASCII)));
        byte[] oversized = new byte[50_000_000];
        System.arraycopy(PDF, 0, oversized, 0, 5);
        assertThrows(IllegalArgumentException.class, () -> PolicyCheck.validatePdf(oversized));
    }

    @Test
    void acceptsAllLocalReportsWithoutTextConversion() throws Exception {
        for (PdfSource source : PdfSource.values()) {
            byte[] pdf = Files.readAllBytes(Path.of("../data").resolve(source.path()));
            assertDoesNotThrow(() -> PolicyCheck.validatePdf(pdf), source.fileName());
        }
    }

    private static PolicyDraft policy(String source, int start, int page, String quote) {
        return new PolicyDraft(source, start, 2028, "Expand domestic skills training.",
                PolicyType.SKILLS_DEVELOPMENT, page, quote);
    }

    private static PolicyAi ai(String raw, String finish, String version) {
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = new ChatModel() {
            public ChatResponse call(Prompt prompt) {
                // Verify the per-request deadline overrides the SDK default without losing model options.
                var options = (OpenAiChatOptions) prompt.getOptions();
                assertEquals(Duration.ofSeconds(600), options.getTimeout());
                assertEquals("test-model", options.getModel());
                assertEquals(1234, options.getMaxCompletionTokens());
                // Assert one call with the complete original attachment, not page chunks.
                assertEquals(1, calls.incrementAndGet());
                UserMessage user = (UserMessage) prompt.getInstructions().get(1);
                assertEquals(1, user.getMedia().size());
                var file = user.getMedia().get(0);
                assertEquals("application/pdf", file.getMimeType().toString());
                assertEquals(SOURCE.fileName(), file.getName());
                assertArrayEquals(PDF, file.getDataAsByteArray());
                assertTrue(user.getText().contains("period_start: 2025"));
                // Verify that both packaged JSON configurations reach the model request.
                String system = prompt.getInstructions().get(0).getText();
                assertTrue(system.contains("\"rules\""));
                assertTrue(system.contains("\"structure\""));
                return new ChatResponse(List.of(new Generation(new AssistantMessage(raw),
                        ChatGenerationMetadata.builder().finishReason(finish).build())));
            }
            public ChatOptions getOptions() {
                return OpenAiChatOptions.builder().model("test-model").maxCompletionTokens(1234).build();
            }
        };
        var beans = new StaticListableBeanFactory();
        beans.addBean("model", model);
        return new PolicyAi(beans.getBeanProvider(ChatModel.class), 600, version);
    }
}
