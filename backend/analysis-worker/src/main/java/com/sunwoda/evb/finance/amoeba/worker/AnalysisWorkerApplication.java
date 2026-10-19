package com.sunwoda.evb.finance.amoeba.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.sunwoda.evb.finance.amoeba.worker",
        "com.sunwoda.evb.finance.analysis.application",
        "com.sunwoda.evb.finance.analysis.infrastructure"
})
@EnableScheduling
public class AnalysisWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalysisWorkerApplication.class, args);
    }
}
