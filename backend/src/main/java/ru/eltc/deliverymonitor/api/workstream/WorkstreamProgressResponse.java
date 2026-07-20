package ru.eltc.deliverymonitor.api.workstream;

import java.util.List;

/**
 * External contract for {@code GET /api/workstreams/progress} (Phase 4.3 Dashboard).
 *
 * <p>One row per active Workstream Type (dictionary order). {@code percent} =
 * {@code merged / total} rounded, or {@code 0} when there are no workstreams yet.
 *
 * @param items progress rows for the Projects section
 */
public record WorkstreamProgressResponse(List<Item> items) {

    /**
     * @param workstreamType type from {@code workstream_types}
     * @param total          workstreams of this type
     * @param merged         workstreams with {@code derived_status = merged}
     * @param percent        0–100 delivery progress for the type
     */
    public record Item(WorkstreamTypeRef workstreamType, long total, long merged, int percent) {
    }

    public record WorkstreamTypeRef(String code, String displayName) {
    }
}
