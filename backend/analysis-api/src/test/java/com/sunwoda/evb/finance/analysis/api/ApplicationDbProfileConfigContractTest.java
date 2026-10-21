package com.sunwoda.evb.finance.analysis.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API 数据库 Profile 配置兼容性契约。
 *
 * <p>本次修复：锁定 Spring Boot 2.7 对 profile 专属配置的约束，防止
 * application-db.yml 再次引入会导致应用在环境准备阶段直接退出的 spring.profiles.include。</p>
 */
class ApplicationDbProfileConfigContractTest {
    @Test
    void databaseProfileResourceUsesActiveProfileSelectionWithoutInvalidInclude() throws IOException {
        String config = read("/application-db.yml");
        String executableConfig = config.replaceAll("(?m)#.*$", "");

        // 本次修复：数据库 Profile 通过 SPRING_PROFILES_ACTIVE=db 选择，专属文件不得再次嵌套 include。
        assertFalse(executableConfig.contains("spring.profiles.include"));
        assertFalse(executableConfig.contains("profiles:\n    include:"));
        assertTrue(executableConfig.contains("driver-class-name: org.postgresql.Driver"));
        assertTrue(executableConfig.contains("AMOEBA_DB_URL"));
    }

    private String read(String resource) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("配置资源不存在: " + resource);
            return new String(readAll(stream), StandardCharsets.UTF_8);
        }
    }

    private byte[] readAll(InputStream stream) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = stream.read(buffer)) >= 0) {
            if (length > 0) output.write(buffer, 0, length);
        }
        return output.toByteArray();
    }
}
