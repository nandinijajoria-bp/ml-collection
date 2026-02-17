package com.bharatpe.lending.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class AutoPayUPIErrorConfig {

    @Value("${auto.pay.upi.consecutive.error.days.default:1000}")
    private int defaultConsecutiveDays;

    // Format: "ERROR1:DAYS,ERROR2:DAYS,ERROR3:DAYS"
    // Example: "ACCOUNT_BLOCKED:1,INSUFFICIENT_FUNDS:3,TIMEOUT:5"
    @Value("${auto.pay.upi.consecutive.error.config:}")
    private String errorConfig;

    private final Map<String, Integer> errorDescriptionToDaysMap = new HashMap<>();

    @PostConstruct
    public void init() {
        loadErrorConfig();
        log.info("AutoPay UPI error config loaded with {} entries: {}", errorDescriptionToDaysMap.size(), errorDescriptionToDaysMap);
    }

    private void loadErrorConfig() {
        if (errorConfig == null || errorConfig.trim().isEmpty()) {
            log.warn("No error config provided, will use default days: {}", defaultConsecutiveDays);
            return;
        }

        for (String entry : errorConfig.split(",")) {
            try {
                String trimmedEntry = entry.trim();
                if (trimmedEntry.isEmpty()) continue;

                String[] parts = trimmedEntry.split(":");
                if (parts.length != 2) {
                    log.warn("Invalid error config entry: {}, expected format ERROR:DAYS", trimmedEntry);
                    continue;
                }

                String error = parts[0].trim();
                int days = Integer.parseInt(parts[1].trim());

                if (error.isEmpty() || days <= 0) {
                    log.warn("Invalid error config entry: {}, error cannot be empty and days must be > 0", trimmedEntry);
                    continue;
                }

                // Store original, normalized, and uppercase versions
                errorDescriptionToDaysMap.put(error, days);
                String normalized = normalize(error);
                errorDescriptionToDaysMap.put(normalized, days);
                errorDescriptionToDaysMap.put(normalized.toUpperCase(), days);

                log.info("Loaded error config: {} -> {} days", error, days);
            } catch (NumberFormatException e) {
                log.error("Invalid days value in error config entry: {}", entry, e);
            }
        }
    }

    public Integer getConsecutiveDaysForError(String errorDescription) {
        if (errorDescription == null) {
            return defaultConsecutiveDays;
        }
        String normalized = normalize(errorDescription);
        String normalizedUpperCase = normalized.toUpperCase();
        // exact match
        Integer days = errorDescriptionToDaysMap.get(normalizedUpperCase);
        if (days != null) {
            return days;
        }
        // substring match
        for (Map.Entry<String, Integer> entry : errorDescriptionToDaysMap.entrySet()) {
            String mapKeyUpperCase = entry.getKey().toUpperCase();
            if (normalizedUpperCase.contains(mapKeyUpperCase)) {
                return entry.getValue();
            }
        }
        return defaultConsecutiveDays;
    }

    private String normalize(String s) {
        if (s == null) {
            return null;
        }
        return s.trim()
                .replaceAll("\\s*\\|\\s*", " ")
                .replaceAll("\\s+", " ");
    }
}