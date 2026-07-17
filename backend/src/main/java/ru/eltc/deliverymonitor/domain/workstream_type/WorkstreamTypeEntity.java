package ru.eltc.deliverymonitor.domain.workstream_type;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity backing the {@code workstream_types} table (docs/database.md, Phase 3.3).
 *
 * <p>{@code code} is the stable primary key ({@code backend}, {@code frontend}, …) — ADR-002.
 * Display labels and sort order are data; disabling a type uses {@code isActive} without a
 * code migration.
 */
@Entity
@Table(name = "workstream_types")
public class WorkstreamTypeEntity {

    @Id
    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    /** JPA. */
    protected WorkstreamTypeEntity() {
    }

    public WorkstreamTypeEntity(String code, String displayName, int sortOrder, boolean active) {
        this.code = code;
        this.displayName = displayName;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }
}
