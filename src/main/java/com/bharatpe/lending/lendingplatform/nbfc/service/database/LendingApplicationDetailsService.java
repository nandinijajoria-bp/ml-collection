package com.bharatpe.lending.lendingplatform.nbfc.service.database;

import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.query.entity.LendingApplicationDetailsSlave;
import com.bharatpe.lending.dao.underwriting.CriteriaValidator;
import com.bharatpe.lending.dao.underwriting.DynamicFieldValidator;
import com.bharatpe.lending.dao.underwriting.GenericCriteriaRepository;
import com.bharatpe.lending.dto.underwriting.ApiResponse;
import com.bharatpe.lending.dto.underwriting.SearchRequestDTO;
import com.bharatpe.lending.dto.underwriting.read.LendingApplicationDetailsReadDTO;
import com.bharatpe.lending.exceptions.DataApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.bharatpe.lending.dto.underwriting.AggregateConditionDTO;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LendingApplicationDetailsService {

    private final LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    private GenericCriteriaRepository criteriaRepository;

    @Autowired
    private CriteriaValidator criteriaValidator;

    // Master entity manager (for strong consistency)
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager masterEntityManager;

    // Query entity manager (for eventual consistency)
    @PersistenceContext(unitName = "query")
    private EntityManager queryEntityManager;

    @Transactional
    public void save(LendingApplicationDetails lendingApplicationDetails) {
        try {
            lendingApplicationDetailsDao.save(lendingApplicationDetails);
        } catch (Exception e) {
            log.error("Error saving LendingApplicationDetails: {}", e.getMessage(), e);
        }
    }

    public ApiResponse<Object> searchDynamic(SearchRequestDTO request, boolean useStrongConsistency) {
        log.info("searchDynamic called with request: {}", request);

        if (request == null || request.getCriteriaList() == null || request.getCriteriaList().isEmpty()) {
            throw new DataApiException.InvalidInputException("Invalid request body");
        }

        // Check for aggregate query
        boolean isAggregateQuery = request.getAggregateConditions() != null && !request.getAggregateConditions().isEmpty();

        // Choose entity + entity manager based on consistency mode
        Class<?> entityClass = useStrongConsistency
                ? LendingApplicationDetails.class
                : LendingApplicationDetailsSlave.class;
        EntityManager entityManager = useStrongConsistency
                ? masterEntityManager
                : queryEntityManager;

        log.info("Using {} consistency for LendingApplicationDetails search",
                useStrongConsistency ? "strong" : "eventual");

        // Validate dynamic criteria fields and request parameters
        DynamicFieldValidator fieldValidator = new DynamicFieldValidator(entityManager, entityClass);
        try {
            criteriaValidator.validateAndCoerce(request, fieldValidator);
            fieldValidator.validateRequestExtras(request);
        } catch (IllegalArgumentException ex) {
            throw new DataApiException.InvalidInputException(ex.getMessage());
        }

        // Perform DB search
        List<?> result = criteriaRepository.searchEntities(entityClass, request, useStrongConsistency);

        // Handle aggregate query
        if (isAggregateQuery) {
            List<Object[]> aggregateResults = (List<Object[]>) result;

            Map<String, Object> responseMap = new HashMap<>();
            List<AggregateConditionDTO> conditions = request.getAggregateConditions();

            Object[] row = aggregateResults.get(0);
            for (int i = 0; i < conditions.size(); i++) {
                responseMap.put(conditions.get(i).getAlias(), row[i]);
            }

            return ApiResponse.<Object>builder()
                    .success(true)
                    .message("Aggregate data fetched successfully")
                    .data(responseMap)
                    .build();
        }

        // Handle normal record fetch
        List<LendingApplicationDetailsReadDTO> dtos = mapEntitiesToDto(result, LendingApplicationDetailsReadDTO.class);

        return ApiResponse.<Object>builder()
                .success(true)
                .message("Records fetched successfully")
                .data(dtos)
                .build();
    }

    private <E, D> List<D> mapEntitiesToDto(List<E> entities, Class<D> dtoClass) {
        return entities.stream()
                .map(entity -> {
                    try {
                        D dto = dtoClass.getDeclaredConstructor().newInstance();
                        BeanUtils.copyProperties(entity, dto);
                        return dto;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to map entity to DTO: " + e.getMessage(), e);
                    }
                })
                .collect(Collectors.toList());
    }

}
