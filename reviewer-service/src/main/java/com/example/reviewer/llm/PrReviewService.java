package com.example.reviewer.llm;

import com.example.reviewer.ado.AzureDevOpsPrCommentsService;
import com.example.reviewer.util.DiffParseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import io.opentelemetry.api.trace.Span;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import com.example.reviewer.util.TracingUtil;
import com.example.reviewer.util.PromptTemplates;
import com.example.reviewer.util.ReviewContext;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class PrReviewService {

    private static final Logger log = LoggerFactory.getLogger(PrReviewService.class);

    private final AzureDevOpsPrCommentsService prCommentsService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String apiVersion;
    private final String deploymentName;
    private final PromptResponseLogger promptResponseLogger;
    private final ObservationRegistry observationRegistry;
    @org.springframework.beans.factory.annotation.Value("${llm.maxLineReviews:20}")
    private int maxLineReviews;
    private final java.util.Set<String> overallPosted = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public PrReviewService(AzureDevOpsPrCommentsService prCommentsService,
                           ObjectMapper objectMapper,
                           PromptResponseLogger promptResponseLogger,
                           ObservationRegistry observationRegistry,
                           @Value("${spring.ai.azure.openai.endpoint}") String endpoint,
                           @Value("${spring.ai.azure.openai.api-key}") String apiKey,
                           @Value("${spring.ai.azure.openai.chat.options.model:gpt-4o-mini}") String deploymentName,
                           @Value("${spring.ai.azure.openai.api-version:2024-12-01-preview}") String apiVersion) {
        this.prCommentsService = prCommentsService;
        this.objectMapper = objectMapper;
        this.promptResponseLogger = promptResponseLogger;
        this.observationRegistry = observationRegistry;
        this.deploymentName = deploymentName;
        this.apiVersion = apiVersion;
        this.restClient = RestClient.builder()
                .baseUrl(endpoint + "/openai/deployments/" + deploymentName + "/chat/completions?api-version=" + apiVersion)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("api-key", apiKey)
                .build();
    }
    @Async("taskExecutor")
    public CompletableFuture<List<String>> filterRelevantFiles(List<String> filePaths) {
        try {
            if (filePaths == null || filePaths.isEmpty()) {
                return CompletableFuture.completedFuture(new ArrayList<>());
            }

            String fileList = String.join("\n", filePaths);
            String filterPrompt = String.join("\n\n",
                PromptTemplates.FILE_FILTER_INSTRUCTIONS,
                "",
                "Here is the list of filenames to review:",
                fileList
            );

            // Add tracing for file filtering
            Observation filterObs = Observation.start("llm.filter.files", observationRegistry)
                    .lowCardinalityKeyValue("llm.provider", "azure-openai")
                    .lowCardinalityKeyValue("llm.deployment", deploymentName)
                    .lowCardinalityKeyValue("llm.apiVersion", apiVersion)
                    .lowCardinalityKeyValue("total.files", String.valueOf(filePaths.size()));
            try (Observation.Scope os = filterObs.openScope()) {
                String userId = ReviewContext.getUserId();
                String sessionId = ReviewContext.getSessionId();
                if (userId != null) filterObs.highCardinalityKeyValue("user.id", userId);
                if (sessionId != null) filterObs.highCardinalityKeyValue("session.id", sessionId);
                log.debug("[trace] start llm.filter.files totalFiles={} model={} apiVersion={}", 
                        filePaths.size(), deploymentName, apiVersion);
                
                // Attach prompt to the Observation so it is bridged to OTel (Langfuse)
                filterObs.highCardinalityKeyValue("gen_ai.prompt", filterPrompt);
                Span cur = Span.current();
                TracingUtil.addAttributes(cur, Map.of(
                        "llm.provider", "azure-openai",
                        "llm.deployment", deploymentName,
                        "llm.apiVersion", apiVersion,
                        "total.files", String.valueOf(filePaths.size())
                ));
                String promptPreview = filterPrompt.length() > 4000 ? filterPrompt.substring(0, 4000) : filterPrompt;
                TracingUtil.addEvent(cur, "llm.prompt", Map.of(
                        "prompt.length", String.valueOf(filterPrompt.length()),
                        "prompt.preview", promptPreview
                ));

            Map<String, Object> responseFormat = Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                    "name", "FileFilter",
                    "schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "relevantFiles", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string")
                            )
                        ),
                        "required", java.util.List.of("relevantFiles"),
                        "additionalProperties", false
                    ),
                    "strict", Boolean.TRUE
                )
            );

            Map<String, Object> body = Map.of(
                "messages", new Object[]{ Map.of("role", "user", "content", filterPrompt) },
                "temperature", 0.1,
                "response_format", responseFormat
            );

            Observation httpObs = Observation.start("azure-openai.chat.completions", observationRegistry)
                    .lowCardinalityKeyValue("http.method", "POST")
                    .lowCardinalityKeyValue("llm.route", "/openai/deployments/" + deploymentName + "/chat/completions")
                    .lowCardinalityKeyValue("llm.model", deploymentName);
            try (Observation.Scope ios = httpObs.openScope()) {
                var resp = restClient.post().body(body).retrieve().toEntity(Map.class);
                if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                    return CompletableFuture.completedFuture(filePaths); // Return all files if filtering fails
                }

                Object content = ((Map<?,?>)((Map<?,?>)((java.util.List<?>)resp.getBody().get("choices")).get(0)).get("message")).get("content");
                String text = content == null ? "" : content.toString();
                
                // Attach completion to the Observation so it is bridged to OTel (Langfuse)
                filterObs.highCardinalityKeyValue("gen_ai.completion", text);
                String responsePreview = text.length() > 4000 ? text.substring(0, 4000) : text;
                TracingUtil.addEvent(Span.current(), "llm.response", Map.of(
                        "response.length", String.valueOf(text.length()),
                        "response.preview", responsePreview
                ));
                
                if (text.isBlank()) return CompletableFuture.completedFuture(filePaths);

                JsonNode json = objectMapper.readTree(text);
                JsonNode relevantFiles = json.path("relevantFiles");
                if (!relevantFiles.isArray()) return CompletableFuture.completedFuture(filePaths);

                List<String> filteredFiles = new ArrayList<>();
                for (JsonNode file : relevantFiles) {
                    String filePath = file.asText(null);
                    if (filePath != null && !filePath.isBlank()) {
                        filteredFiles.add(filePath);
                    }
                }
                
                // Add filtering results to trace
                filterObs.lowCardinalityKeyValue("filtered.files", String.valueOf(filteredFiles.size()));
                TracingUtil.addEvent(Span.current(), "llm.filter.result", Map.of(
                        "original.count", String.valueOf(filePaths.size()),
                        "filtered.count", String.valueOf(filteredFiles.size()),
                        "filtered.files", String.join(",", filteredFiles)
                ));
                
                return CompletableFuture.completedFuture(filteredFiles);
            } finally {
                httpObs.stop();
            }
            } finally {
                filterObs.stop();
                log.debug("[trace] stop llm.filter.files totalFiles={} model={} apiVersion={}", 
                        filePaths.size(), deploymentName, apiVersion);
            }

        } catch (Exception e) {
            log.warn("Failed to filter files using LLM, returning all files", e);
            TracingUtil.recordException(Span.current(), e);
            return CompletableFuture.completedFuture(filePaths); // Return all files if filtering fails
        }
    }

    @Async("taskExecutor")
    public void reviewPrAsync(String repoId, long prId, String diffContent, String projectId,String baseUrl) {
        try {
            if (diffContent == null || diffContent.isBlank()) {
                log.info("No diff content to review for prId={}", prId);
                return;
            }
            String file = parseFilePathFromDiff(diffContent);
            if (file == null) file = "unknown";

            Observation llmObs = Observation.start("llm.review.file", observationRegistry)
                    .lowCardinalityKeyValue("pr.id", String.valueOf(prId))
                    .lowCardinalityKeyValue("file.path", file)
                    .lowCardinalityKeyValue("llm.provider", "azure-openai")
                    .lowCardinalityKeyValue("llm.deployment", deploymentName)
                    .lowCardinalityKeyValue("llm.apiVersion", apiVersion);
            try (Observation.Scope os = llmObs.openScope()) {
                String userId = ReviewContext.getUserId();
                String sessionId = ReviewContext.getSessionId();
                if (userId != null) llmObs.highCardinalityKeyValue("user.id", userId);
                if (sessionId != null) llmObs.highCardinalityKeyValue("session.id", sessionId);
                log.debug("[trace] start llm.review.file prId={} file={} model={} apiVersion={}", prId, file, deploymentName, apiVersion);
                List<com.example.reviewer.util.DiffParseUtil.HunkLine> adds = DiffParseUtil.extractAddedLinesWithContext(diffContent, 3);
                if (adds.isEmpty()) {
                    TracingUtil.addEvent(io.opentelemetry.api.trace.Span.current(), "llm.skip", Map.of("reason", "no_added_lines"));
                    return;
                }

                String perFilePrompt = String.join("\n\n",
                    PromptTemplates.REVIEW_INSTRUCTIONS,
                    "You will receive a file path and the unified diff for this single file. Review ONLY the added lines (right side) present in the diff.",
                    "Return JSON only (no markdown).",
                    "If no concrete issues on a line, omit it from comments. For each line, return at most ONE consolidated comment (merge duplicate or overlapping points into a single concise statement).",
                    "file: " + file,
                    "unified diff (this file only):\n" + diffContent
            );
                promptResponseLogger.writePrompt(repoId, prId, deploymentName, perFilePrompt);
                // Attach prompt to the Observation so it is bridged to OTel (Langfuse)
                llmObs.highCardinalityKeyValue("gen_ai.prompt", perFilePrompt);
                Span cur = Span.current();
                TracingUtil.addAttributes(cur, Map.of(
                        "pr.id", String.valueOf(prId),
                        "file.path", file,
                        "llm.provider", "azure-openai",
                        "llm.deployment", deploymentName,
                        "llm.apiVersion", apiVersion
                ));
                String promptPreview = perFilePrompt.length() > 4000 ? perFilePrompt.substring(0, 4000) : perFilePrompt;
                TracingUtil.addEvent(cur, "llm.prompt", Map.of(
                        "prompt.length", String.valueOf(perFilePrompt.length()),
                        "prompt.preview", promptPreview
                ));

                Map<String, Object> responseFormat = Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of(
                                "name", "PrReview",
                                "schema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "overall", Map.of("type", "string"),
                                                "comments", Map.of(
                                                        "type", "array",
                                                        "items", Map.of(
                                                                "type", "object",
                                                                "properties", Map.of(
                                                                        "file", Map.of("type", "string"),
                                                                        "line", Map.of("type", "integer"),
                                                                        "comment", Map.of("type", "string"),
                                                                        "changed_line_text", Map.of("type", "string")
                                                                ),
                                                                "required", java.util.List.of("file","line","comment","changed_line_text"),
                                                                "additionalProperties", Boolean.FALSE
                                                        )
                                                )
                                        ),
                                        "required", java.util.List.of("overall","comments"),
                                        "additionalProperties", false
                                ),
                                "strict", Boolean.TRUE
                        )
                );

                Map<String, Object> body = Map.of(
                        "messages", new Object[]{ Map.of("role", "user", "content", perFilePrompt) },
                        "temperature", 0.2,
                        "response_format", responseFormat
                );

                Observation httpObs = Observation.start("azure-openai.chat.completions", observationRegistry)
                        .lowCardinalityKeyValue("http.method", "POST")
                        .lowCardinalityKeyValue("llm.route", "/openai/deployments/" + deploymentName + "/chat/completions")
                        .lowCardinalityKeyValue("llm.model", deploymentName);
                try (Observation.Scope ios = httpObs.openScope()) {
                    var resp = restClient.post().body(body).retrieve().toEntity(Map.class);
                    if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) return;
                    Object content = ((Map<?,?>)((Map<?,?>)((java.util.List<?>)resp.getBody().get("choices")).get(0)).get("message")).get("content");
                    String text = content == null ? "" : content.toString();
                    promptResponseLogger.writeResponse(repoId, prId, deploymentName, text);
                    // Attach completion to the Observation so it is bridged to OTel (Langfuse)
                    llmObs.highCardinalityKeyValue("gen_ai.completion", text);
                    String responsePreview = text.length() > 4000 ? text.substring(0, 4000) : text;
                    TracingUtil.addEvent(Span.current(), "llm.response", Map.of(
                            "response.length", String.valueOf(text.length()),
                            "response.preview", responsePreview
                    ));
                    if (text.isBlank()) return;
                    try {
                        JsonNode json = objectMapper.readTree(text);
                        String overall = Optional.ofNullable(json.path("overall").asText(null)).orElse("");
                        if (!overall.isBlank()) {
                            String overallKey = repoId + ":" + prId;
                            if (overallPosted.add(overallKey)) {
                                try { prCommentsService.addOverallPrComment(repoId, prId, overall,projectId,baseUrl); } catch (Exception ignore) {}
                            }
                        }
                        JsonNode arr = json.path("comments");
                        if (!arr.isArray()) return;
                        for (JsonNode c : arr) {
                            String f = Optional.ofNullable(c.path("file").asText(null)).orElse(file);
                            int line = c.path("line").asInt(-1);
                            String comment = c.path("comment").asText(null);
                            if (line <= 0 || comment == null || comment.isBlank()) continue;
                            String prefix="";
                            String normalized = f.startsWith("/") ? f.substring(1) : f;
                            try { prCommentsService.addSingleLineComment(repoId, prId, normalized, line, prefix+comment, projectId, baseUrl); } catch (Exception ignore) {}
                        }
                    } catch (Exception ignore) {}
                } finally {
                    httpObs.stop();
                }
            } finally {
                llmObs.stop();
            }
        } catch (Exception e) {
            log.warn("Failed PR LLM review for prId={}", prId, e);
            TracingUtil.recordException(Span.current(), e);
        }
    }

    private static String parseFilePathFromDiff(String diffContent) {
        String[] lines = diffContent.split("\r?\n");
        for (String l : lines) {
            if (l.startsWith("+++ b/")) {
                String p = l.substring(6);
                return p.startsWith("/") ? p.substring(1) : p;
            }
        }
        return null;
    }

}



