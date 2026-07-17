package ru.eltc.deliverymonitor.api.workstream;

import ru.eltc.deliverymonitor.domain.workstream_type.WorkstreamTypeEntity;

/**
 * External API contract for an active Workstream Type (docs/api.md Conventions —
 * {@code code} + {@code displayName} from the dictionary, not hardcoded platforms).
 *
 * @param code        stable type key ({@code backend}, {@code frontend}, …)
 * @param displayName UI label
 * @param sortOrder   Board / pills order from seed
 */
public record WorkstreamTypeResponse(String code, String displayName, int sortOrder) {

    public static WorkstreamTypeResponse from(WorkstreamTypeEntity entity) {
        return new WorkstreamTypeResponse(
                entity.getCode(),
                entity.getDisplayName(),
                entity.getSortOrder());
    }
}
