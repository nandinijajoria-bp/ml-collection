package com.bharatpe.lending.dao.underwriting;

import com.bharatpe.lending.dto.underwriting.SearchCriteriaDTO;
import com.bharatpe.lending.dto.underwriting.SearchRequestDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CriteriaValidator {

    @Value("${underwriting.criteria.max-in-list-size:10}")
    private int maxInListSize;

    @Value("${underwriting.criteria.default-limit:1}")
    private int defaultLimit;

    @Value("${underwriting.criteria.max-limit:100}")
    private int maxLimit;

    public void validateAndCoerce(SearchRequestDTO request, DynamicFieldValidator fieldValidator) {
        // Validate limit
        if (request.getLimit() == null) {
            request.setLimit(defaultLimit);
        } else if (request.getLimit() > maxLimit) {
            throw new IllegalArgumentException("Limit exceeds max allowed: " + maxLimit);
        } else if (request.getLimit() <= 0) {
            throw new IllegalArgumentException("Limit must be > 0");
        }

        if (request.getCriteriaList() == null) return;

        for (SearchCriteriaDTO c : request.getCriteriaList()) {
            String field = c.getField();
            String op = c.getOperation();

            // Validate field
            if (!fieldValidator.isAllowed(field)) {
                throw new IllegalArgumentException("Field not allowed: " + field);
            }

            Class<?> expectedType = fieldValidator.getType(field);

            // Explicitly disallow LIKE operation
            if ("LIKE".equalsIgnoreCase(op)) {
                throw new IllegalArgumentException("LIKE operation is not allowed");
            }

            // Handle IN and NOT_IN lists
            if ("IN".equalsIgnoreCase(op) || "NOT_IN".equalsIgnoreCase(op)) {
                Object v = c.getValue();

                // 🔹 Handle case where value came as a stringified list: "[ACTIVE, CLOSED]"
                if (v instanceof String) {
                    String str = ((String) v).trim();

                    if (str.startsWith("[") && str.endsWith("]")) {
                        // Remove square brackets and split by comma
                        List<String> parsed = Arrays.stream(
                                        str.substring(1, str.length() - 1)
                                                .split(",")
                                )
                                .map(s -> s.replaceAll("['\"]", "").trim()) // remove quotes
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList());
                        c.setValue(parsed);
                    } else {
                        throw new IllegalArgumentException(op + " operation requires a list value");
                    }
                }

                // 🔹 Validate value is a List now
                if (!(c.getValue() instanceof List))
                    throw new IllegalArgumentException(op + " operation requires a list value");

                List<?> list = (List<?>) c.getValue();

                // 🔹 Check size limit
                if (list.size() > maxInListSize) {
                    throw new IllegalArgumentException(op + " list too large. Max allowed: " + maxInListSize);
                }

                // 🔹 Coerce each element to the expected type
                List<?> coercedList = list.stream()
                        .map(vItem -> coerceValue(vItem, expectedType))
                        .collect(Collectors.toList());
                c.setValue(coercedList);

                // 🔹 Skip further coercion
                continue;
            }

            // Coerce value to expected type
            c.setValue(coerceValue(c.getValue(), expectedType));
        }
    }

    private static Object coerceValue(Object value, Class<?> expectedType) {
        if (value == null) return null;
        if (expectedType.isInstance(value)) return value;

        try {
            if (expectedType.equals(Long.class)) return Long.valueOf(value.toString());
            if (expectedType.equals(Integer.class)) return Integer.valueOf(value.toString());
            if (expectedType.equals(String.class)) return value.toString();
            if (expectedType.equals(java.time.LocalDateTime.class))
                return java.time.LocalDateTime.parse(value.toString());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to convert value to " + expectedType.getSimpleName());
        }

        throw new IllegalArgumentException("Unsupported target type: " + expectedType.getSimpleName());
    }
}
