package com.hazely.laborlens.jobs.policyExtraction;

import com.hazely.laborlens.jobs.JobFiles;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;

import java.io.IOException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Submits one complete PDF attachment and validates its full policy response. */
@Component
public class PolicyAi {
    private final ObjectProvider<ChatModel> models;
    private final int timeout;
    private final String configVersion;
    private final JsonMapper json = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();
    private final String prompt;

    /** Maps the model's JSON envelope while keeping parsing inside this adapter. */
    private record Output(List<PolicyDraft> policies) {
    }

    public PolicyAi(ObjectProvider<ChatModel> models,
            @Value("${app.jobs.pdf.timeout-seconds:600}") int timeout,
            @Value("${app.jobs.pdf.config-version:policy-pdf-v2}") String configVersion) {
        if (timeout < 1 || configVersion.isBlank()) {
            throw new IllegalArgumentException("Invalid policy extraction settings");
        }
        this.models = models;
        this.timeout = timeout;
        this.configVersion = configVersion;
        // Load JSON from the promts subdirectory for calls, history and versioning.
        this.prompt = "Extraction rules (JSON):\n" + read("promts/prompt.json")
                + "\nRequired output format (JSON):\n" + read("promts/outputStructure.json");
    }

    public String version() {
        String config = json.writeValueAsString(settings());
        return "policy-" + JobFiles.hash((prompt + config).getBytes(StandardCharsets.UTF_8));
    }

    public List<PolicyDraft> extract(PdfSource source, byte[] pdf, Path artifact) throws Exception {
        PolicyCheck.validatePdf(pdf);
        ChatModel model = model();
        JobFiles.properties(artifact.resolve("model.properties"), settings());
        Files.writeString(artifact.resolve("system-prompt.txt"), prompt, StandardOpenOption.CREATE_NEW);
        String input = "source_file: " + source.fileName()
                + "\nperiod_start: " + source.start() + "\nperiod_end: " + source.end()
                + "\nExtract all qualifying policies from the entire attached PDF.\n";
        Files.writeString(artifact.resolve("request.txt"), input, StandardOpenOption.CREATE_NEW);
        // Spring AI serializes the original PDF bytes as a Base64 file attachment.
        Media file = Media.builder().mimeType(Media.Format.DOC_PDF)
                .name(source.fileName()).data(pdf).build();
        UserMessage user = UserMessage.builder().text(input).media(file).build();
        // Spring AI 2.0.1 request options otherwise override the client timeout with 60 seconds.
        var options = model.getOptions();
        if (options instanceof OpenAiChatOptions openAi) {
            options = openAi.mutate().timeout(Duration.ofSeconds(timeout)).build();
        }
        ChatResponse response = call(model, new Prompt(List.of(new SystemMessage(prompt), user),
                options));
        if (response == null || response.getResult() == null || response.hasToolCalls()) {
            throw new IllegalArgumentException("AI returned no usable policy output");
        }
        String raw = response.getResult().getOutput().getText();
        if (raw == null) throw new IllegalArgumentException("AI returned empty policy output");
        Files.writeString(artifact.resolve("response.json"), raw, StandardOpenOption.CREATE_NEW);
        var metadata = response.getMetadata();
        JobFiles.properties(artifact.resolve("response.properties"), Map.of(
                "model", metadata.getModel(), "response_id", metadata.getId(),
                "prompt_tokens", String.valueOf(metadata.getUsage().getPromptTokens()),
                "completion_tokens", String.valueOf(metadata.getUsage().getCompletionTokens()),
                "finished_at", Instant.now().toString()));
        if (!response.hasFinishReasons(Set.of("stop"))) {
            throw new IllegalArgumentException("AI output was truncated, refused or incomplete");
        }
        Output output;
        try {
            output = json.readValue(raw, Output.class);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("AI output does not match the policy schema",
                    error);
        }
        if (output == null) throw new IllegalArgumentException("AI returned no policy output");
        List<PolicyDraft> result = PolicyCheck.validate(source, output.policies());
        if (result.isEmpty()) throw new IllegalArgumentException("Unexpectedly empty policy extraction");
        return result;
    }

    /** Classpath streams also work when these JSON files are packaged in the application JAR. */
    private String read(String name) {
        try (var input = PolicyAi.class.getResourceAsStream(name)) {
            if (input == null) throw new IllegalStateException("Missing policy configuration: " + name);
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            var value = json.readTree(content);
            if (value == null || !value.isObject() || value.isEmpty()) {
                throw new IllegalStateException("Policy configuration must be a nonempty JSON object: " + name);
            }
            return content;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read policy configuration: " + name,
                    error);
        }
    }

    private ChatResponse call(ChatModel model, Prompt prompt) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "policy-model-call");
            thread.setDaemon(true);
            return thread;
        });
        Future<ChatResponse> future = executor.submit(() -> model.call(prompt));
        try {
            return future.get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException | InterruptedException error) {
            future.cancel(true);
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            throw error;
        } finally {
            executor.shutdownNow();
        }
    }

    private ChatModel model() {
        ChatModel model = models.getIfAvailable();
        if (model == null || model.getOptions().getModel() == null || model.getOptions().getModel().isBlank()) {
            throw new IllegalStateException("Configure a Spring AI chat model before running PDF processing");
        }
        return model;
    }

    private Map<String, String> settings() {
        var options = model().getOptions();
        Map<String, String> values = new TreeMap<>();
        values.put("config_version", configVersion);
        values.put("input_format", "whole-pdf-base64-v1");
        values.put("model", options.getModel());
        values.put("temperature", String.valueOf(options.getTemperature()));
        values.put("max_tokens", String.valueOf(options.getMaxTokens()));
        values.put("top_p", String.valueOf(options.getTopP()));
        values.put("top_k", String.valueOf(options.getTopK()));
        values.put("frequency_penalty", String.valueOf(options.getFrequencyPenalty()));
        values.put("presence_penalty", String.valueOf(options.getPresencePenalty()));
        values.put("stop_sequences", String.valueOf(options.getStopSequences()));
        values.put("timeout_seconds", Integer.toString(timeout));
        return values;
    }
}
