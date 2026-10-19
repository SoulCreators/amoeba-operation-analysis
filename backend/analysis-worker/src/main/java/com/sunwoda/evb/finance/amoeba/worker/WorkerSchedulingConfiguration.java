package com.sunwoda.evb.finance.amoeba.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables periodic cleanup only for the explicitly wired production profiles. */
@Configuration
@Profile("db & filesystem")
@ConditionalOnProperty(name = "analysis.worker.cleanup.scheduling-enabled",
        havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class WorkerSchedulingConfiguration {
}
