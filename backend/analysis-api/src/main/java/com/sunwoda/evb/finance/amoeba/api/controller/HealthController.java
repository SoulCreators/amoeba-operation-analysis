package com.sunwoda.evb.finance.amoeba.api.controller;

import com.sunwoda.evb.finance.amoeba.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    @GetMapping
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(Collections.singletonMap("status", "UP"));
    }
}
