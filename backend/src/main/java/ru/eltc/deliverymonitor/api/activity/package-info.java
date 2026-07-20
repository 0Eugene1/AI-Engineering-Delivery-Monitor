/**
 * Activity Feed Read API (Phase 4.1).
 *
 * <p>Dependency direction:
 *
 * <pre>
 * PostgreSQL -&gt; domain.timeline (+ workstream_type) -&gt; api.activity
 * </pre>
 *
 * <p>Reads the same {@code activity_events} table as Issue Timeline (ADR-008). Does <b>not</b>
 * introduce {@code domain.activity} or a new table. Presentation mapping (summary / actor /
 * payload) is shared via {@link ru.eltc.deliverymonitor.api.ActivityEventMapper}.
 *
 * <ul>
 *   <li>{@link ru.eltc.deliverymonitor.api.activity.ActivityController} /
 *       {@link ru.eltc.deliverymonitor.api.activity.ActivityQueryService} /
 *       {@link ru.eltc.deliverymonitor.api.activity.ActivityFeedResponse} — {@code GET
 *       /api/activity} (always {@code 200}, empty {@code events} allowed).</li>
 *   <li>{@link ru.eltc.deliverymonitor.api.activity.ActivityFeedProperties} —
 *       {@code activity.feed.default-limit} / {@code max-limit}.</li>
 * </ul>
 *
 * <p><b>Out of scope:</b> UI; Risks; new event writers; Kafka/Redis/CQRS/AI.
 */
package ru.eltc.deliverymonitor.api.activity;
