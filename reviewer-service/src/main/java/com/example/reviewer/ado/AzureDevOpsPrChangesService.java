package com.example.reviewer.ado;

import com.example.reviewer.io.FileContentWriter;
import com.example.reviewer.io.PrChangesWriter;
import com.example.reviewer.util.DiffUtil;
import com.example.reviewer.llm.PrReviewService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
public class AzureDevOpsPrChangesService {

    private static final Logger log = LoggerFactory.getLogger(AzureDevOpsPrChangesService.class);
    private static final String API_VERSION = "7.1";
    private static final String DIFF_API_VERSION = "7.2-preview.1";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String projectId;
    private final String basicAuthHeader;
    private final PrChangesWriter prChangesWriter;
    private final FileContentWriter fileContentWriter;
    private final PrReviewService prReviewService;
    private String lastDiffContent = "";
    private final java.util.Map<String, String> lastPerFileDiffs = new java.util.concurrent.ConcurrentHashMap<>();

    public AzureDevOpsPrChangesService(ObjectMapper objectMapper,
                                       PrChangesWriter prChangesWriter,
                                       FileContentWriter fileContentWriter,
                                       PrReviewService prReviewService,
                                       @Value("${ado.baseUrl}") String baseUrl,
                                       @Value("${ado.projectId}") String projectId,
                                       @Value("${ado.pat:}") String pat) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.baseUrl = baseUrl != null && baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.projectId = projectId;
        String credentials = ":" + (pat == null ? "" : pat);
        this.basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        this.prChangesWriter = prChangesWriter;
        this.fileContentWriter = fileContentWriter;
        this.prReviewService = prReviewService;
    }

    public void fetchAndStorePrChanges(String repoId, long prId) {
        if (baseUrl == null || baseUrl.isBlank() || projectId == null || projectId.isBlank()) {
            log.warn("ADO baseUrl/projectId not configured; skipping PR changes fetch");
            return;
        }
        try {
            int latestIterationId = fetchLatestIterationId(repoId, prId);
            ObjectNode changeEntriesObject = fetchIterationChangeEntriesObject(repoId, prId, latestIterationId);
            prChangesWriter.write(changeEntriesObject, "pr_" + prId + "_changes");
            log.info("Stored PR changes: prId={} file written", prId);
        } catch (IOException | InterruptedException e) {
            log.warn("Failed to fetch/store PR changes for prId={}", prId, e);
        }
    }

    public String fetchAndStoreBranchDiff(String repoId, long prId, String baseBranchRef, String targetBranchRef,
                                          String baseCommitId, String targetCommitId) {
        if (baseUrl == null || baseUrl.isBlank() || projectId == null || projectId.isBlank()) {
            log.warn("ADO baseUrl/projectId not configured; skipping PR diff fetch");
            return "";
        }
        try {
            this.lastDiffContent = "";
            this.lastPerFileDiffs.clear();
            String base = urlEncodeBranchRef(baseBranchRef);
            String target = urlEncodeBranchRef(targetBranchRef);

            int top = 2000;
            int skip = 0;
            ArrayNode combinedChanges = objectMapper.createArrayNode();
            boolean more = true;
            int page = 0;
            while (more) {
                String url = String.format("%s/%s/_apis/git/repositories/%s/diffs/commits?baseVersionType=branch&baseVersion=%s&targetVersionType=branch&targetVersion=%s&$top=%d&$skip=%d&api-version=%s",
                        baseUrl, projectId, repoId, base, target, top, skip, DIFF_API_VERSION);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", basicAuthHeader)
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                page++;
                if (response.statusCode() != 200) {
                    log.warn("ADO branch diff request failed: status={} body={}", response.statusCode(), response.body());
                    break;
                }

                JsonNode root = objectMapper.readTree(response.body());
                try { prChangesWriter.write(root, String.format("raw_pr_%d_branch_diff_page_%d", prId, page)); } catch (IOException ignored) {}

                JsonNode changes = root.path("changes");
                if (!changes.isArray()) {
                    changes = root.path("value");
                }
                int countThisPage = 0;
                if (changes.isArray()) {
                    for (JsonNode ch : changes) { combinedChanges.add(ch); countThisPage++; }
                }
                if (countThisPage < top) {
                    more = false;
                } else {
                    skip += top;
                }
            }

            ObjectNode result = objectMapper.createObjectNode();
            result.put("repoId", repoId);
            result.put("projectId", projectId);
            result.put("pullRequestId", prId);
            result.put("baseBranch", baseBranchRef);
            result.put("targetBranch", targetBranchRef);
            result.set("changes", combinedChanges);

            prChangesWriter.write(result, "pr_" + prId + "_diff");
            log.info("Stored PR branch diff: prId={} file written", prId);

            // Extract all file paths for LLM filtering
            java.util.List<String> allFilePaths = new java.util.ArrayList<>();
            for (JsonNode ch : combinedChanges) {
                JsonNode item = ch.path("item");
                String path = item.path("path").asText(null);
                String gitObjectType = item.path("gitObjectType").asText("");
                boolean isBlob = "blob".equalsIgnoreCase(gitObjectType);
                if (isBlob && path != null && !path.endsWith("/") && !"/".equals(path)) {
                    allFilePaths.add(path);
                }
            }

            // Use LLM to filter relevant files
            java.util.List<String> relevantFiles = new java.util.ArrayList<>();
            if (!allFilePaths.isEmpty()) {
                try {
                    relevantFiles = prReviewService.filterRelevantFiles(allFilePaths);
                    log.info("LLM filtered {} files down to {} relevant files for prId={}", 
                            allFilePaths.size(), relevantFiles.size(), prId);
                } catch (Exception e) {
                    log.warn("LLM file filtering failed for prId={}, using all files", prId, e);
                    relevantFiles = allFilePaths;
                }
            }

            StringBuilder aggregatedDiff = new StringBuilder();
            int appendedFiles = 0;
            for (JsonNode ch : combinedChanges) {
                JsonNode item = ch.path("item");
                String path = item.path("path").asText(null);
                String gitObjectType = item.path("gitObjectType").asText("");
                boolean isBlob = "blob".equalsIgnoreCase(gitObjectType);
                if (!isBlob) { continue; }
                if (path != null) {
                    if (path.endsWith("/") || "/".equals(path)) { continue; }
                    
                    // Only process files that passed LLM filtering
                    if (!relevantFiles.contains(path)) {
                        log.debug("Skipping file {} as it was filtered out by LLM for prId={}", path, prId);
                        continue;
                    }
                    
                    byte[] baseContent = (baseCommitId == null || baseCommitId.isBlank()) ? null : fetchFileContentByPathAndCommit(repoId, path, baseCommitId);
                    if (baseContent != null && baseContent.length > 0) { fileContentWriter.write(baseContent, "base", path); }
                    byte[] targetContent = (targetCommitId == null || targetCommitId.isBlank()) ? null : fetchFileContentByPathAndCommit(repoId, path, targetCommitId);
                    if (targetContent != null && targetContent.length > 0) { fileContentWriter.write(targetContent, "target", path); }

                    String diffText = DiffUtil.unifiedDiff(baseContent, targetContent, path);
                    if (diffText != null && !diffText.isBlank()) {
                        try { prReviewService.reviewPrAsync(repoId, prId, diffText); } catch (Exception ignored) {}
                    }
                }
            }
            this.lastDiffContent = aggregatedDiff.toString();
            log.info("Aggregated unified diff for prId={} across {} file(s). Size={} bytes", prId, appendedFiles, this.lastDiffContent.length());

        } catch (IOException | InterruptedException e) {
            log.warn("Failed to fetch/store PR branch diff for prId={}", prId, e);
        }
        return lastDiffContent;
    }

    public java.util.Map<String, String> getLastPerFileDiffs() {
        return new java.util.HashMap<>(lastPerFileDiffs);
    }

    private String urlEncodeBranchRef(String ref) {
        String branch = ref == null ? "" : ref.replaceFirst("^refs/heads/", "");
        return java.net.URLEncoder.encode(branch, java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] fetchFileContentByPathAndCommit(String repoId, String path, String commitId) throws IOException, InterruptedException {
        String url = String.format(
                "%s/%s/_apis/git/repositories/%s/items?path=%s&versionType=commit&version=%s&includeContent=true&$format=octetStream&api-version=%s",
                baseUrl, projectId, repoId,
                java.net.URLEncoder.encode(path, java.nio.charset.StandardCharsets.UTF_8),
                java.net.URLEncoder.encode(commitId, java.nio.charset.StandardCharsets.UTF_8),
                API_VERSION);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader)
                .header("Accept", "application/octet-stream")
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 200) { return response.body(); }
        if (response.statusCode() != 404) { log.debug("Failed to fetch file by path+commit path={} commit={} status={}", path, commitId, response.statusCode()); }
        return null;
    }

    private int fetchLatestIterationId(String repoId, long prId) throws IOException, InterruptedException {
        String url = String.format("%s/%s/_apis/git/repositories/%s/pullRequests/%d/iterations?api-version=%s",
                baseUrl, projectId, repoId, prId, API_VERSION);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuthHeader)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("ADO iterations request failed: status={} body={}", response.statusCode(), response.body());
            return -1;
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode value = root.path("value");
        int latestId = -1;
        if (value.isArray()) {
            for (JsonNode it : value) {
                int id = it.path("id").asInt(-1);
                if (id > latestId) { latestId = id; }
            }
        }
        log.debug("Latest iteration id for prId={} is {}", prId, latestId);
        return latestId;
    }

    private ObjectNode fetchIterationChangeEntriesObject(String repoId, long prId, int iterationId) throws IOException, InterruptedException {
        ArrayNode combinedEntries = objectMapper.createArrayNode();
        String continuation = null;
        int page = 0;
        do {
            String url = String.format("%s/%s/_apis/git/repositories/%s/pullRequests/%d/iterations/%d/changes?$top=2000&includeContent=true&api-version=%s",
                    baseUrl, projectId, repoId, prId, iterationId, API_VERSION);
            if (continuation != null && !continuation.isBlank()) { url = url + "&continuationToken=" + continuation; }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", basicAuthHeader)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            page++;
            if (response.statusCode() != 200) { log.warn("ADO iteration changes request failed: status={} body={}", response.statusCode(), response.body()); break; }
            log.debug("Fetched PR iteration changes page {} for prId={} iterationId={}", page, prId, iterationId);
            String nextContinuation = response.headers().firstValue("x-ms-continuationtoken").orElse(null);
            continuation = (nextContinuation != null && !nextContinuation.isBlank()) ? nextContinuation : null;
            JsonNode root = objectMapper.readTree(response.body());
            try { prChangesWriter.write(root, String.format("raw_pr_%d_iter_%d_page_%d", prId, iterationId, page)); } catch (IOException ignored) {}
            JsonNode entriesNode = root.path("changeEntries");
            if (!entriesNode.isArray()) { entriesNode = root.path("changes"); if (!entriesNode.isArray()) { entriesNode = root.path("value"); } }
            if (entriesNode.isArray()) { for (JsonNode entry : entriesNode) { combinedEntries.add(entry); } } else { log.debug("No changeEntries array found for prId={} iterationId={}", prId, iterationId); }
        } while (continuation != null);
        ObjectNode result = objectMapper.createObjectNode();
        result.set("changeEntries", combinedEntries);
        return result;
    }
}



