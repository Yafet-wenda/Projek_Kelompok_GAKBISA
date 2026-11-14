package com.gakbisa.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "gakbisa.storage")
public record StorageProperties(
    String baseUrl,
    String serviceKey,
    String bucket,
    long maxFileSizeBytes,
    List<String> allowedMimeTypes
) {
    public boolean configured() {
        return baseUrl != null && !baseUrl.isBlank() && serviceKey != null && !serviceKey.isBlank();
    }
}
