package ru.eltc.deliverymonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} turns on Spring's {@code TaskScheduler} infrastructure for the whole
 * application (docs/roadmap.md Phase 2.5 "Scheduler"). It only activates the scheduling
 * <em>mechanism</em>; whether the Jira sync task itself actually runs is a separate, env-driven
 * decision made in {@link ru.eltc.deliverymonitor.sync.jira.JiraSyncScheduler} via {@code
 * jira.sync.enabled} (default {@code false}).
 */
@EnableScheduling
@SpringBootApplication
public class DeliveryMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryMonitorApplication.class, args);
    }
}
