package ru.eltc.deliverymonitor.domain.workstream;

import java.util.List;

/**
 * Persistence seam for {@code workstreams}. Callers ({@code sync.gitlab}) depend on this port;
 * {@code domain.workstream} does not depend on sync.
 */
public interface WorkstreamPersistencePort {

    /**
     * Upserts workstreams matched by {@code (issueKey, workstreamTypeCode)}. Empty / null list →
     * empty outcome (no DB access).
     */
    WorkstreamUpsertOutcome upsertAll(List<WorkstreamUpsertCommand> commands);
}
