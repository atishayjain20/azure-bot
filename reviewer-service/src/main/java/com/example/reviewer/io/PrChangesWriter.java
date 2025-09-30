package com.example.reviewer.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Objects;

@Component
public class PrChangesWriter {

    private static final Logger log = LoggerFactory.getLogger(PrChangesWriter.class);
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Path directoryPath;

    public PrChangesWriter(ObjectMapper objectMapper,
                           @Value("${ado.changesDump.enabled:true}") boolean enabled,
                           @Value("${ado.changesDump.dir:ado-pr-changes}") String directory) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.directoryPath = Path.of(Objects.requireNonNullElse(directory, "ado-pr-changes"));
    }

    public Path write(JsonNode json, String filenamePrefix) throws IOException {
        if (!enabled) {
            return null;
        }
        Files.createDirectories(directoryPath);
        String safePrefix = (filenamePrefix == null || filenamePrefix.isBlank()) ? "pr_changes" : filenamePrefix;
        String timestamp = FILE_TS.format(Instant.now());
        String fileName = safePrefix + "_" + timestamp + ".json";
        Path target = directoryPath.resolve(fileName);
        String content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        Files.writeString(target, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        log.info("Wrote PR changes JSON to {}", target.toAbsolutePath());
        return target;
    }
}



