package com.example.reviewer.service;

import com.example.reviewer.ado.AzureDevOpsPrChangesService;
import com.example.reviewer.ado.AzureDevOpsPrCommentsService;
import com.example.reviewer.llm.PrReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReviewPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ReviewPipelineService.class);

    private final AzureDevOpsPrChangesService prChangesService;
    private final AzureDevOpsPrCommentsService prCommentsService;
    private final PrReviewService prReviewService;

    public ReviewPipelineService(AzureDevOpsPrChangesService prChangesService,
                                 AzureDevOpsPrCommentsService prCommentsService,
                                 PrReviewService prReviewService) {
        this.prChangesService = prChangesService;
        this.prCommentsService = prCommentsService;
        this.prReviewService = prReviewService;
    }

    @Async("taskExecutor")
    public void reviewPipelineAsync(String repoId,
                                    long prId,
                                    String baseBranch,
                                    String targetBranch,
                                    String baseCommitId,
                                    String targetCommitId,
                                    String projectId,
                                    String baseUrl) {
        try {
            String diffContent = "";
            if (baseBranch != null && targetBranch != null) {
                diffContent = prChangesService.fetchAndStoreBranchDiff(repoId, prId, baseBranch, targetBranch, baseCommitId, targetCommitId, projectId,baseUrl);
            } else {
                log.warn("Missing branch refs; skipping branch diff fetch for prId={}", prId);
            }

            try {
                prCommentsService.addCommentsToPr(repoId, prId, diffContent, projectId,baseUrl);
            } catch (Exception e) {
                log.warn("Failed to add initial PR comments for prId={}", prId, e);
            }
        } catch (Exception e) {
            log.warn("Review pipeline failed for prId={}", prId, e);
        }
    }
}



