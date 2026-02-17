package com.bharatpe.lending.dao.underwriting;

import com.bharatpe.lending.dto.underwriting.AggregateConditionDTO;
import com.bharatpe.lending.dto.underwriting.SearchCriteriaDTO;
import com.bharatpe.lending.dto.underwriting.SearchRequestDTO;
import com.bharatpe.lending.exceptions.DataApiException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;

@Repository
@Slf4j
public class GenericCriteriaRepository {

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager masterEntityManager;

    @PersistenceContext(unitName = "query")
    private EntityManager slaveEntityManager;

    private EntityManager getEntityManager(boolean useStrongConsistency) {
        return useStrongConsistency ? masterEntityManager : slaveEntityManager;
    }

    /**
     * Unified search handler — supports both normal and aggregate conditions.
     */
    public <T> List<?> searchEntities(Class<T> entityClass, SearchRequestDTO request, boolean useStrongConsistency) {
        if (request.getAggregateConditions() != null && !request.getAggregateConditions().isEmpty()) {
            return executeAggregateQuery(entityClass, request, useStrongConsistency);
        } else {
            return executeSearchQuery(entityClass, request, useStrongConsistency);
        }
    }

    /**
     * Normal dynamic search.
     */
    private <T> List<T> executeSearchQuery(Class<T> entityClass, SearchRequestDTO request, boolean useStrongConsistency) {
        EntityManager entityManager = getEntityManager(useStrongConsistency);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityClass);
        Root<T> root = query.from(entityClass);

        if (hasAttribute(entityClass, "loanApplication")) {
            root.fetch("loanApplication", JoinType.LEFT);
            query.distinct(true);
        }

        List<Predicate> predicates = buildPredicates(cb, root, request.getCriteriaList());

        query.where(cb.and(predicates.toArray(new Predicate[0])));

        // Ordering
        if (request.getOrderBy() != null) {
            if ("DESC".equalsIgnoreCase(request.getOrderDir())) {
                query.orderBy(cb.desc(root.get(request.getOrderBy())));
            } else {
                query.orderBy(cb.asc(root.get(request.getOrderBy())));
            }
        }

        TypedQuery<T> typedQuery = entityManager.createQuery(query);
        if (request.getLimit() != null) {
            typedQuery.setMaxResults(request.getLimit());
        }

        try {
            return typedQuery.getResultList();
        } catch (Exception e) {
            log.info("Error fetching result list", e);
            throw new DataApiException.DatabaseException("Error executing query", e);
        }
    }

    /** Helper: Check if entity has a given field */
    private boolean hasAttribute(Class<?> entityClass, String fieldName) {
        try {
            entityClass.getDeclaredField(fieldName);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    /**
     * Aggregate query handler — builds COUNT(CASE WHEN ...) type queries
     */
    private <T> List<Object[]> executeAggregateQuery(Class<T> entityClass, SearchRequestDTO request, boolean useStrongConsistency) {
        EntityManager entityManager = getEntityManager(useStrongConsistency);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<T> root = query.from(entityClass);

        // WHERE clause from criteriaList
        List<Predicate> predicates = buildPredicates(cb, root, request.getCriteriaList());

        // SELECT fields with COUNT CASE WHEN logic
        List<Selection<?>> selections = new ArrayList<>();
        for (AggregateConditionDTO cond : request.getAggregateConditions()) {
            Predicate whenPredicate = buildPredicate(cb, root, cond);
            Expression<Long> countExpr = cb.count(cb.selectCase()
                    .when(whenPredicate, 1)
                    .otherwise((Integer) null));
            selections.add(countExpr.alias(cond.getAlias()));
        }

        query.multiselect(selections);
        if (!predicates.isEmpty()) {
            query.where(cb.and(predicates.toArray(new Predicate[0])));
        }
        try {
            return entityManager.createQuery(query).getResultList();
        } catch (Exception e) {
            log.info("Error fetching result list", e);
            throw new DataApiException.DatabaseException("Error executing query", e);
        }

    }

    /**
     * Helper: builds predicates for normal criteriaList.
     */
    private List<Predicate> buildPredicates(CriteriaBuilder cb, Root<?> root, List<SearchCriteriaDTO> criteriaList) {
        List<Predicate> predicates = new ArrayList<>();
        if (criteriaList == null) return predicates;

        for (SearchCriteriaDTO criteria : criteriaList) {
            Path<?> path = root.get(criteria.getField());
            switch (criteria.getOperation().toUpperCase()) {
                case "=":
                    predicates.add(cb.equal(path, criteria.getValue()));
                    break;
                case "!=":
                    predicates.add(cb.notEqual(path, criteria.getValue()));
                    break;
                case ">":
                    predicates.add(cb.greaterThan(root.get(criteria.getField()), (Comparable) criteria.getValue()));
                    break;
                case "<":
                    predicates.add(cb.lessThan(root.get(criteria.getField()), (Comparable) criteria.getValue()));
                    break;
                case ">=":
                    predicates.add(cb.greaterThanOrEqualTo(root.get(criteria.getField()), (Comparable) criteria.getValue()));
                    break;
                case "<=":
                    predicates.add(cb.lessThanOrEqualTo(root.get(criteria.getField()), (Comparable) criteria.getValue()));
                    break;
                case "IN":
                    if (criteria.getValue() instanceof List<?>) {
                        predicates.add(path.in((List<?>) criteria.getValue()));
                    } else {
                        throw new DataApiException.InvalidInputException("IN operation requires a list value");
                    }
                    break;
                case "NOT_IN":
                    if (criteria.getValue() instanceof List<?>) {
                        predicates.add(cb.not(path.in((List<?>) criteria.getValue())));
                    } else {
                        throw new DataApiException.InvalidInputException("NOT_IN operation requires a list value");
                    }
                    break;
                case "IS_NULL":
                    predicates.add(cb.isNull(path));
                    break;
                case "IS_NOT_NULL":
                    predicates.add(cb.isNotNull(path));
                    break;
                default:
                    throw new DataApiException.OperationNotAllowedException(
                            "Unsupported operation: " + criteria.getOperation());
            }
        }
        return predicates;
    }


    /**
     * Helper: builds predicate for AggregateCondition.
     */
    private Predicate buildPredicate(CriteriaBuilder cb, Root<?> root, AggregateConditionDTO cond) {
        switch (cond.getOperation()) {
            case ">=":
                return cb.greaterThanOrEqualTo(root.get(cond.getField()), (Comparable) cond.getValue());
            case "=":
                return cb.equal(root.get(cond.getField()), cond.getValue());
            case "!=":
                return cb.notEqual(root.get(cond.getField()), cond.getValue());
            case "<":
                return cb.lessThan(root.get(cond.getField()), (Comparable) cond.getValue());
            case "<=":
                return cb.lessThanOrEqualTo(root.get(cond.getField()), (Comparable) cond.getValue());
            default:
                throw new IllegalArgumentException("Unsupported operation: " + cond.getOperation());
        }
    }
}
