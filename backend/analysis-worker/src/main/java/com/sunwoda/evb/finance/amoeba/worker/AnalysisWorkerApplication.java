package com.sunwoda.evb.finance.amoeba.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.sunwoda.evb.finance.amoeba")
public class AnalysisWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalysisWorkerApplication.class, args);
    }
}
