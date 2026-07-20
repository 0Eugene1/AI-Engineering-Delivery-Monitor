package ru.eltc.deliverymonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} turns on Spring's {@code TaskScheduler} infrastructure for the whole
 * application (docs/roadmap.md Phase 2.5 / 3.9). It only activates the scheduling
 * <em>mechanism</em>; whether a sync task actually runs is a separate, env-driven decision made in
 * {@link ru.eltc.deliverymonitor.sync.jira.JiraSyncScheduler} ({@code jira.sync.enabled}, default
 * {@code false}) and {@link ru.eltc.deliverymonitor.sync.gitlab.GitLabSyncScheduler}
 * ({@code gitlab.sync.enabled}, default {@code false}).
 */
@EnableScheduling
@SpringBootApplication
public class DeliveryMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryMonitorApplication.class, args);
    }
}
