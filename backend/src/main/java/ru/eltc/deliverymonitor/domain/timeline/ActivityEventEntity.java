package ru.eltc.deliverymonitor.domain.timeline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * JPA entity backing {@code activity_events} (docs/database.md, ADR-008, Phase 3.5).
 *
 * <p>Upsert matching key is {@code (source, sourceRef)} — not {@code issueKey}. {@code issueKey}
 * is nullable (orphan policy: events without a Jira key are still stored).
 */
@Entity
@Table(name = "activity_events", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_activity_events_source_source_ref",
                columnNames = {"source", "source_ref"})
})
public class ActivityEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "issue_key")
    private String issueKey;

    @Column(name = "workstream_type_code")
    private String workstreamTypeCode;

    @Column(name = "actor_username")
    private String actorUsername;

    @Column(name = "actor_display_name")
    private String actorDisplayName;

    @Column(name = "type", nullable = false)
    private String type;

    // LONGVARCHAR → PostgreSQL TEXT (matches Liquibase CLOB→TEXT). Plain @Lob maps to OID and
    // fails schema validate: found text, expecting oid.
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "payload")
    private String payload;

    @Column(name = "source", nullable = false, updatable = false)
    private String source;

    @Column(name = "source_ref", nullable = false, updatable = false)
    private String sourceRef;

    /** JPA. */
    protected ActivityEventEntity() {
    }

    public ActivityEventEntity(String source, String sourceRef) {
        this.source = source;
        this.sourceRef = sourceRef;
    }

    public void applyUpsert(ActivityEventUpsertCommand command) {
        this.occurredAt = command.occurredAt();
        this.issueKey = command.issueKey();
        this.workstreamTypeCode = command.workstreamTypeCode();
        this.actorUsername = command.actorUsername();
        this.actorDisplayName = command.actorDisplayName();
        this.type = command.type();
        this.payload = command.payload();
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public String getWorkstreamTypeCode() {
        return workstreamTypeCode;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getActorDisplayName() {
        return actorDisplayName;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public String getSource() {
        return source;
    }

    public String getSourceRef() {
        return sourceRef;
    }
}
