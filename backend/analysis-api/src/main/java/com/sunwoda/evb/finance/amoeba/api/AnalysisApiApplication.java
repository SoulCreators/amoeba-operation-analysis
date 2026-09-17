package com.sunwoda.evb.finance.amoeba.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.sunwoda.evb.finance.amoeba")
public class AnalysisApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalysisApiApplication.class, args);
    }
}
