package ru.eltc.deliverymonitor.integration.gitlab.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import reactor.core.publisher.Mono;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabBranchDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabCommitDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabMergeRequestDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabProjectDto;
import ru.eltc.deliverymonitor.integration.gitlab.exception.GitLabClientException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Offline {@link GitLabClient}: serves <b>sanitized demo data</b> from bundled fixtures
 * so the application (and the layers built on top of it) can be developed without a real
 * {@code GITLAB_TOKEN}. Active only when {@code gitlab.mode=mock}.
 *
 * <p><b>Never for production.</b> Construction <b>fails fast</b> if a production Spring
 * profile is active — so {@code gitlab.mode=mock} cannot silently ship to prod.
 */
@Component
@ConditionalOnProperty(name = "gitlab.mode", havingValue = "mock")
public class MockGitLabClient implements GitLabClient {

    private static final Logger log = LoggerFactory.getLogger(MockGitLabClient.class);

    private static final Set<String> FORBIDDEN_PROFILES = Set.of("prod", "production");

    static final String PROJECT_FIXTURE = "gitlab/mock/project-2159.json";
    static final String BRANCHES_FIXTURE = "gitlab/mock/branches-2159.json";
    static final String COMMITS_FIXTURE = "gitlab/mock/commits-2159.json";
    static final String MERGE_REQUESTS_FIXTURE = "gitlab/mock/merge-requests-2159.json";

    /** Known demo project id (docs/discovery.md §9.2 — {@code mptp/mptp8}). */
    static final String DEMO_PROJECT_ID = "2159";
    static final String DEMO_PROJECT_PATH = "mptp/mptp8";

    private final GitLabProjectDto project;
    private final List<GitLabBranchDto> branches;
    private final List<GitLabCommitDto> commits;
    private final List<GitLabMergeRequestDto> mergeRequests;

    public MockGitLabClient(ObjectMapper objectMapper, Environment environment) {
        assertNotProduction(environment);
        this.project = load(objectMapper, PROJECT_FIXTURE, GitLabProjectDto.class);
        this.branches = List.copyOf(loadList(objectMapper, BRANCHES_FIXTURE, GitLabBranchDto[].class));
        this.commits = List.copyOf(loadList(objectMapper, COMMITS_FIXTURE, GitLabCommitDto[].class));
        this.mergeRequests = List.copyOf(
                loadList(objectMapper, MERGE_REQUESTS_FIXTURE, GitLabMergeRequestDto[].class));
        log.warn("GitLab is running in MOCK mode: serving SANITIZED DEMO data from classpath:gitlab/mock/ - "
                + "not real GitLab content. Never enable gitlab.mode=mock in production.");
    }

    private static void assertNotProduction(Environment environment) {
        List<String> active = Arrays.asList(environment.getActiveProfiles());
        boolean prod = active.stream().anyMatch(p -> FORBIDDEN_PROFILES.contains(p.toLowerCase()));
        if (prod) {
            throw new IllegalStateException(
                    "gitlab.mode=mock is not allowed with a production profile (active profiles: " + active
                            + "). Mock serves demo data only; use gitlab.mode=rest in production.");
        }
    }

    private static <T> T load(ObjectMapper objectMapper, String path, Class<T> type) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            String json = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load mock GitLab fixture: " + path, e);
        }
    }

    private static <T> List<T> loadList(ObjectMapper objectMapper, String path, Class<T[]> arrayType) {
        return Arrays.asList(load(objectMapper, path, arrayType));
    }

    @Override
    public Mono<GitLabProjectDto> getProject(String projectIdOrPath) {
        if (!isKnownDemoProject(projectIdOrPath)) {
            return Mono.error(new GitLabClientException(
                    "Mock GitLab: project not found: " + projectIdOrPath, 404, List.of("404 Project Not Found")));
        }
        return Mono.just(project);
    }

    @Override
    public Mono<List<GitLabBranchDto>> listBranches(String projectIdOrPath, int page, int perPage) {
        return requireDemoProject(projectIdOrPath).then(Mono.fromSupplier(() -> pageOf(branches, page, perPage)));
    }

    @Override
    public Mono<List<GitLabCommitDto>> listCommits(
            String projectIdOrPath, Instant since, int page, int perPage) {
        return requireDemoProject(projectIdOrPath).then(Mono.fromSupplier(() -> {
            List<GitLabCommitDto> filtered = commits;
            if (since != null) {
                filtered = commits.stream()
                        .filter(c -> parseInstant(c.committedDate()).map(t -> !t.isBefore(since)).orElse(true))
                        .toList();
            }
            return pageOf(filtered, page, perPage);
        }));
    }

    @Override
    public Mono<List<GitLabMergeRequestDto>> listMergeRequests(
            String projectIdOrPath, String state, int page, int perPage) {
        return requireDemoProject(projectIdOrPath).then(Mono.fromSupplier(() -> {
            List<GitLabMergeRequestDto> filtered = mergeRequests;
            if (state != null && !state.isBlank() && !"all".equalsIgnoreCase(state)) {
                filtered = mergeRequests.stream()
                        .filter(mr -> state.equalsIgnoreCase(mr.state()))
                        .toList();
            }
            return pageOf(filtered, page, perPage);
        }));
    }

    @Override
    public Mono<GitLabMergeRequestDto> getMergeRequest(String projectIdOrPath, long mergeRequestIid) {
        return requireDemoProject(projectIdOrPath).then(Mono.fromSupplier(() -> mergeRequests.stream()
                        .filter(mr -> mr.iid() != null && mr.iid() == mergeRequestIid)
                        .findFirst()
                        .orElse(null)))
                .flatMap(mr -> mr != null
                        ? Mono.just(mr)
                        : Mono.error(new GitLabClientException(
                                "Mock GitLab: merge request not found: !" + mergeRequestIid,
                                404,
                                List.of("404 Not found"))));
    }

    private Mono<Void> requireDemoProject(String projectIdOrPath) {
        if (!isKnownDemoProject(projectIdOrPath)) {
            return Mono.error(new GitLabClientException(
                    "Mock GitLab: project not found: " + projectIdOrPath, 404, List.of("404 Project Not Found")));
        }
        return Mono.empty();
    }

    private static boolean isKnownDemoProject(String projectIdOrPath) {
        if (projectIdOrPath == null) {
            return false;
        }
        String normalized = projectIdOrPath.trim();
        return DEMO_PROJECT_ID.equals(normalized) || DEMO_PROJECT_PATH.equalsIgnoreCase(normalized);
    }

    private static <T> List<T> pageOf(List<T> all, int page, int perPage) {
        int safePage = Math.max(page, 1);
        int safePerPage = Math.max(perPage, 0);
        int from = Math.min((safePage - 1) * safePerPage, all.size());
        int to = Math.min(from + safePerPage, all.size());
        return List.copyOf(all.subList(from, to));
    }

    private static Optional<Instant> parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
