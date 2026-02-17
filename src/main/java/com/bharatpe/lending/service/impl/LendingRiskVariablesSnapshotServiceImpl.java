package com.bharatpe.lending.service.impl;

import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.query.entity.LendingRiskVariablesSnapshotSlave;
import com.bharatpe.lending.dao.underwriting.CriteriaValidator;
import com.bharatpe.lending.dao.underwriting.DynamicFieldValidator;
import com.bharatpe.lending.dao.underwriting.GenericCriteriaRepository;
import com.bharatpe.lending.dto.underwriting.AggregateConditionDTO;
import com.bharatpe.lending.dto.underwriting.ApiResponse;
import com.bharatpe.lending.dto.underwriting.SearchRequestDTO;
import com.bharatpe.lending.dto.underwriting.read.LendingRiskVariablesSnapshotReadDTO;
import com.bharatpe.lending.dto.underwriting.write.LendingRiskVariablesSnapshotWriteDto;
import com.bharatpe.lending.exceptions.DataApiException;
import com.bharatpe.lending.service.ILendingRiskVariablesSnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LendingRiskVariablesSnapshotServiceImpl implements ILendingRiskVariablesSnapshotService {

    @Autowired
    private LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    private GenericCriteriaRepository criteriaRepository;

    @Autowired
    private CriteriaValidator criteriaValidator;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager masterEntityManager;

    @PersistenceContext(unitName = "query")
    private EntityManager queryEntityManager;

    @Override
    public ApiResponse<Object> searchDynamic(SearchRequestDTO request, boolean useStrongConsistency) {
        log.info("searchDynamic called for LendingRiskVariablesSnapshot with request: {}", request);

        if (request == null || request.getCriteriaList() == null || request.getCriteriaList().isEmpty()) {
            throw new DataApiException.InvalidInputException("Invalid request body");
        }

        boolean isAggregateQuery = request.getAggregateConditions() != null && !request.getAggregateConditions().isEmpty();

        Class<?> entityClass = useStrongConsistency ? LendingRiskVariablesSnapshot.class : LendingRiskVariablesSnapshotSlave.class;
        EntityManager entityManager = useStrongConsistency ? masterEntityManager : queryEntityManager;

        DynamicFieldValidator fieldValidator = new DynamicFieldValidator(entityManager, entityClass);
        try {
            criteriaValidator.validateAndCoerce(request, fieldValidator);
            fieldValidator.validateRequestExtras(request);
        } catch (IllegalArgumentException ex) {
            throw new DataApiException.InvalidInputException(ex.getMessage());
        }

        List<?> result = criteriaRepository.searchEntities(entityClass, request, useStrongConsistency);

        if (isAggregateQuery) {
            @SuppressWarnings("unchecked")
            List<Object[]> aggregateResults = (List<Object[]>) result;
            Map<String, Object> responseMap = new HashMap<>();
            List<AggregateConditionDTO> conditions = request.getAggregateConditions();

            Object[] row = aggregateResults.get(0);
            for (int i = 0; i < conditions.size(); i++) {
                responseMap.put(conditions.get(i).getAlias(), row[i]);
            }

            return ApiResponse.<Object>builder()
                    .success(true)
                    .message("Aggregate data fetched successfully for LendingRiskVariablesSnapshot")
                    .data(responseMap)
                    .build();
        }

        List<LendingRiskVariablesSnapshotReadDTO> dtos = mapEntitiesToDto(result, LendingRiskVariablesSnapshotReadDTO.class);
        return ApiResponse.<Object>builder()
                .success(true)
                .message("LendingRiskVariablesSnapshot records fetched successfully")
                .data(dtos)
                .build();
    }

    private <E, D> List<D> mapEntitiesToDto(List<E> entities, Class<D> dtoClass) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream()
                .map(entity -> {
                    try {
                        D dto = dtoClass.getDeclaredConstructor().newInstance();
                        BeanUtils.copyProperties(entity, dto);
                        return dto;
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Failed to map " + entity.getClass().getSimpleName() + " to " + dtoClass.getSimpleName(), e);
                    }
                })
                .collect(Collectors.toList());
    }

    @Override
    public ApiResponse<Boolean> saveRiskVariablesSnapshot(LendingRiskVariablesSnapshotWriteDto writeDto) {
        if (writeDto == null) {
            throw new DataApiException.InvalidInputException("Invalid request body");
        }

        LendingRiskVariablesSnapshot snapshot = null;
        if (writeDto.getId() != null) {
            Optional<LendingRiskVariablesSnapshot> snapshotOptional = lendingRiskVariablesSnapshotDao.findById(writeDto.getId());
            snapshot = snapshotOptional.orElse(null);
        }

        if (snapshot == null && writeDto.getApplicationId() != null) {
            snapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(writeDto.getApplicationId());
        }

        if (snapshot == null) {
            snapshot = new LendingRiskVariablesSnapshot();
        }

        try {
            BeanUtils.copyProperties(writeDto, snapshot);
            lendingRiskVariablesSnapshotDao.save(snapshot);
        } catch (Exception e) {
            log.error("Exception while saving LendingRiskVariablesSnapshot for applicationId: {} error: {}",
                    writeDto.getApplicationId(), e.getMessage(), e);
            throw new DataApiException.DatabaseException("Error while saving LendingRiskVariablesSnapshot", e);
        }

        return ApiResponse.<Boolean>builder()
                .success(true)
                .message("LendingRiskVariablesSnapshot saved successfully")
                .data(true)
                .build();
    }
}
