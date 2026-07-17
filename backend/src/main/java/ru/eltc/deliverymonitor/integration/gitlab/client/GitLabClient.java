package ru.eltc.deliverymonitor.integration.gitlab.client;

import reactor.core.publisher.Mono;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabBranchDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabCommitDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabMergeRequestDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabProjectDto;

import java.time.Instant;
import java.util.List;

/**
 * Thin client contract for GitLab API v4. Phase 3.1 scope only: projects, branches,
 * commits, merge requests — no pipelines, no sync orchestration, no persistence
 * (docs/roadmap.md task 3.1, docs/architecture.md Phase 3).
 *
 * <p>Implementations are selected by config ({@code gitlab.mode=rest|mock}):
 * {@link RestGitLabClient} and {@link MockGitLabClient}. Upper layers (future
 * {@code sync.gitlab}) depend on this interface, never on a concrete HTTP client.
 */
public interface GitLabClient {

    /**
     * Resolve a project by numeric id or URL-encoded path
     * ({@code GET /api/v4/projects/:id}), e.g. {@code "2159"} or {@code "mptp/mptp8"}.
     */
    Mono<GitLabProjectDto> getProject(String projectIdOrPath);

    /**
     * List repository branches ({@code GET /api/v4/projects/:id/repository/branches}).
     *
     * @param page    1-based GitLab page
     * @param perPage page size ({@code per_page})
     */
    Mono<List<GitLabBranchDto>> listBranches(String projectIdOrPath, int page, int perPage);

    /**
     * List repository commits ({@code GET /api/v4/projects/:id/repository/commits}).
     *
     * @param since optional lower bound mapped to GitLab's {@code since} query param
     *              (Phase 3 commit-history policy — depth decided by sync layer later)
     * @param page  1-based GitLab page
     * @param perPage page size ({@code per_page})
     */
    Mono<List<GitLabCommitDto>> listCommits(String projectIdOrPath, Instant since, int page, int perPage);

    /**
     * List merge requests ({@code GET /api/v4/projects/:id/merge_requests}).
     *
     * @param state optional GitLab state filter ({@code opened}, {@code merged}, {@code closed},
     *              {@code all}); {@code null}/blank = GitLab default
     * @param page  1-based GitLab page
     * @param perPage page size ({@code per_page})
     */
    Mono<List<GitLabMergeRequestDto>> listMergeRequests(
            String projectIdOrPath, String state, int page, int perPage);

    /**
     * Single merge request detail ({@code GET /api/v4/projects/:id/merge_requests/:iid}).
     */
    Mono<GitLabMergeRequestDto> getMergeRequest(String projectIdOrPath, long mergeRequestIid);
}
