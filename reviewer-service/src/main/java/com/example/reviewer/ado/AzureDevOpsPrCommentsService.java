package com.example.reviewer.ado;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class AzureDevOpsPrCommentsService {

    private static final Logger log = LoggerFactory.getLogger(AzureDevOpsPrCommentsService.class);
    private static final String API_VERSION = "7.1";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String basicAuthHeader;

    public AzureDevOpsPrCommentsService(ObjectMapper objectMapper,
                                        @Value("${ado.pat:}") String pat) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.baseUrl = null;
        String credentials = ":" + (pat == null ? "" : pat);
        this.basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    public void addCommentsToPr(String repoId, long prId, String diffContent,String projectId,String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank() || projectId == null || projectId.isBlank()) {
            log.warn("ADO baseUrl/projectId not configured; skipping PR comments");
            return;
        }
        try {
            addLineCommentsFromDiff(repoId, prId, diffContent, projectId, baseUrl);
        } catch (IOException | InterruptedException e) {
            log.warn("Failed to add PR comments for prId={}", prId, e);
        }
    }

    public void addOverallPrComment(String repoId, long prId, String contentText,String projectId,String baseUrl) throws IOException, InterruptedException {
        ObjectNode comment = objectMapper.createObjectNode();
        comment.set("comments", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                .put("parentCommentId", 0)
                .put("content", contentText)
                .put("commentType", 1)));
        comment.put("status", 1);

        String url = String.format("%s/%s/_apis/git/repositories/%s/pullRequests/%d/threads?api-version=%s",
                baseUrl, projectId, repoId, prId, API_VERSION);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(comment)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.debug("Azure DevOps API call: URL={}, Status={}, Response={}", url, response.statusCode(), response.body());
        if (response.statusCode() == 200 || response.statusCode() == 201) {
            log.info("Added overall PR comment for prId={}", prId);
        } else {
            log.warn("Failed to add overall PR comment: status={} body={}", response.statusCode(), response.body());
        }
    }

    private void addLineCommentsFromDiff(String repoId, long prId, String diffContent,String projectId,String baseUrl) throws IOException, InterruptedException {
        if (diffContent == null || diffContent.isBlank()) {
            log.debug("No diff content provided for line comments");
            return;
        }
        String[] lines = diffContent.split("\r?\n");
        String currentFile = null;
        for (String line : lines) {
            if (line.startsWith("--- a/") || line.startsWith("+++ b/")) {
                if (line.startsWith("+++ b/")) {
                    currentFile = line.substring(6); // Remove "+++ b/"
                    if (currentFile != null && !currentFile.startsWith("/")) currentFile = "/" + currentFile;
                }
                continue;
            }
            if (line.startsWith("@@")) {
                if (currentFile != null) {
                    String[] parts = line.split(" ");
                    if (parts.length >= 3) {
                        String rightPart = parts[2];
                        if (rightPart.startsWith("+")) {
                            String[] rightNumbers = rightPart.substring(1).split(",");
                            int startLine = Integer.parseInt(rightNumbers[0]);
                            // Example placeholder: addLineComment(repoId, prId, currentFile, startLine, "Automated review");
                        }
                    }
                }
                // continue;
            }
        }
    }

    public void addLineCommentsFromDiffWithContent(String repoId, long prId, String diffContent, String content, String projectId, String baseUrl) throws IOException, InterruptedException {
        if (diffContent == null || diffContent.isBlank()) {
            log.debug("No diff content provided for line comments");
            return;
        }
        String[] lines = diffContent.split("\r?\n");
        String currentFile = null;
        for (String line : lines) {
            if (line.startsWith("--- a/") || line.startsWith("+++ b/")) {
                if (line.startsWith("+++ b/")) {
                    currentFile = line.substring(6);
                    if (currentFile != null && !currentFile.startsWith("/")) currentFile = "/" + currentFile;
                }
                continue;
            }
            if (line.startsWith("@@")) {
                if (currentFile != null) {
                    String[] parts = line.split(" ");
                    if (parts.length >= 3) {
                        String rightPart = parts[2];
                        if (rightPart.startsWith("+")) {
                            String[] rightNumbers = rightPart.substring(1).split(",");
                            int startLine = Integer.parseInt(rightNumbers[0]);
                            addLineComment(repoId, prId, currentFile, startLine, content, projectId, baseUrl);
                        }
                    }
                }
            }
        }
    }
    
    private void addLineComment(String repoId, long prId, String filePath, int lineNumber, String content, String projectId, String baseUrl) throws IOException, InterruptedException {
        ObjectNode comment = objectMapper.createObjectNode();
        comment.set("comments", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                .put("parentCommentId", 0)
                .put("content", content)
                .put("commentType", 1)));
        comment.put("status", 1);
        ObjectNode threadContext = objectMapper.createObjectNode();
        threadContext.put("filePath", filePath);
        threadContext.set("rightFileStart", objectMapper.createObjectNode().put("line", lineNumber).put("offset", 1));
        threadContext.set("rightFileEnd", objectMapper.createObjectNode().put("line", lineNumber).put("offset", 1));
        comment.set("threadContext", threadContext);

        String url = String.format("%s/%s/_apis/git/repositories/%s/pullRequests/%d/threads?api-version=%s",
                baseUrl, projectId, repoId, prId, API_VERSION);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(comment)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200 || response.statusCode() == 201) {
            log.info("Added line comment for prId={} on file={} line={}", prId, filePath, lineNumber);
        } else {
            log.warn("Failed to add line comment: status={} body={}", response.statusCode(), response.body());
        }
    }

    public void addSingleLineComment(String repoId, long prId, String filePath, int lineNumber, String content, String projectId, String baseUrl) throws IOException, InterruptedException {
        if (filePath != null && !filePath.startsWith("/")) {
            filePath = "/" + filePath;
        }
        addLineComment(repoId, prId, filePath, lineNumber, content, projectId, baseUrl);
    }
}



