package com.bharatpe.lending.dao.underwriting;

import com.bharatpe.lending.dto.underwriting.SearchRequestDTO;

import javax.persistence.EntityManager;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.SingularAttribute;
import java.util.HashMap;
import java.util.Map;

public class DynamicFieldValidator {

    private final Map<String, Class<?>> allowedFields;

    public DynamicFieldValidator(EntityManager em, Class<?> entityClass) {
        this.allowedFields = buildAllowedFields(em, entityClass);
    }

    private Map<String, Class<?>> buildAllowedFields(EntityManager em, Class<?> entityClass) {
        Map<String, Class<?>> map = new HashMap<>();
        EntityType<?> entityType = em.getMetamodel().entity(entityClass);
        for (SingularAttribute<?, ?> attr : entityType.getSingularAttributes()) {
            map.put(attr.getName(), attr.getJavaType());
        }
        return map;
    }

    public boolean isAllowed(String field) {
        return allowedFields.containsKey(field);
    }

    public Class<?> getType(String field) {
        return allowedFields.get(field);
    }

    // 🔎 New: Validate orderBy, orderDir, and limit
    public void validateRequestExtras(SearchRequestDTO request) {
        // orderBy validation
        if (request.getOrderBy() != null && !isAllowed(request.getOrderBy())) {
            throw new IllegalArgumentException("Invalid orderBy field: " + request.getOrderBy());
        }

        // orderDir validation
        if (request.getOrderDir() != null) {
            String dir = request.getOrderDir().toUpperCase();
            if (!(dir.equals("ASC") || dir.equals("DESC"))) {
                throw new IllegalArgumentException("Invalid orderDir value: " + request.getOrderDir() +
                        ". Allowed values are ASC or DESC.");
            }
        }

        // limit validation
        if (request.getLimit() != null) {
            String limitStr = String.valueOf(request.getLimit());
            if (!limitStr.matches("^[0-9]+$")) {  // ✅ only digits allowed
                throw new IllegalArgumentException("Invalid limit: " + request.getLimit() +
                        ". Limit must be a positive integer without special characters.");
            }
        }
    }
}
