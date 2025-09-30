package com.example.reviewer.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

@Component
public class FileContentWriter {
    private static final Logger log = LoggerFactory.getLogger(FileContentWriter.class);

    private final boolean enabled;
    private final Path baseDir;

    public FileContentWriter(@Value("${ado.filesDump.enabled:true}") boolean enabled,
                             @Value("${ado.filesDump.dir:ado-pr-files}") String baseDir) {
        this.enabled = enabled;
        this.baseDir = Path.of(Objects.requireNonNullElse(baseDir, "ado-pr-files"));
    }

    public Path write(byte[] content, String folder, String relativePath) throws IOException {
        if (!enabled) return null;
        String safeRel = relativePath == null ? "unknown" : relativePath.replaceFirst("^/+", "");
        Path folderPath = baseDir.resolve(folder);
        Files.createDirectories(folderPath);
        Path target = folderPath.resolve(safeRel);
        Files.createDirectories(target.getParent());
        Files.write(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        log.info("Wrote file {} ({} bytes)", target.toAbsolutePath(), content == null ? 0 : content.length);
        return target;
    }

    public Path writeText(String text, String folder, String relativePath) throws IOException {
        if (!enabled) return null;
        String safeRel = relativePath == null ? "unknown" : relativePath.replaceFirst("^/+", "");
        Path folderPath = baseDir.resolve(folder);
        Files.createDirectories(folderPath);
        Path target = folderPath.resolve(safeRel);
        Files.createDirectories(target.getParent());
        Files.writeString(target, text == null ? "" : text, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        log.info("Wrote text file {} ({} chars)", target.toAbsolutePath(), text == null ? 0 : text.length());
        return target;
    }
}



