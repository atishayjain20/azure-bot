package com.example.reviewer.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class PromptResponseLogger {

    private static final Logger log = LoggerFactory.getLogger(PromptResponseLogger.class);
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);

    private final boolean promptEnabled;
    private final boolean responseEnabled;
    private final Path promptDir;
    private final Path responseDir;

    public PromptResponseLogger(
            @Value("${llm.promptLog.enabled:true}") boolean promptEnabled,
            @Value("${llm.responseLog.enabled:true}") boolean responseEnabled,
            @Value("${llm.promptLog.dir:prompt-logs}") String promptDir,
            @Value("${llm.responseLog.dir:response-logs}") String responseDir
    ) {
        this.promptEnabled = promptEnabled;
        this.responseEnabled = responseEnabled;
        this.promptDir = Path.of(promptDir);
        this.responseDir = Path.of(responseDir);
    }

    public void writePrompt(String repoId, long prId, String model, String content) {
        if (!promptEnabled || content == null) return;
        String ts = TS.format(Instant.now());
        String file = String.format("pr_%d_%s_%s_prompt.txt", prId, sanitize(repoId), ts);
        writeFile(this.promptDir, file, header("PROMPT", repoId, prId, model) + content);
    }

    public void writeResponse(String repoId, long prId, String model, String content) {
        if (!responseEnabled || content == null) return;
        String ts = TS.format(Instant.now());
        String file = String.format("pr_%d_%s_%s_response.txt", prId, sanitize(repoId), ts);
        writeFile(this.responseDir, file, header("RESPONSE", repoId, prId, model) + content);
    }

    private static String header(String kind, String repoId, long prId, String model) {
        return String.format("[%s] repoId=%s prId=%d model=%s%n%n", kind, repoId, prId, model);
    }

    private static String sanitize(String s) {
        if (s == null) return "repo";
        return s.replaceAll("[^A-Za-z0-9_-]", "");
    }

    private void writeFile(Path dir, String filename, String content) {
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            Files.writeString(target, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            log.info("Wrote LLM {} to {}", filename.contains("prompt") ? "prompt" : "response", target.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Failed writing LLM {} file: {}", filename.contains("prompt") ? "prompt" : "response", filename, e);
        }
    }
}



