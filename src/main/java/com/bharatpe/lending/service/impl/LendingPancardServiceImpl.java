package com.bharatpe.lending.service.impl;

import com.bharatpe.common.dao.LendingPancardDao;
import com.bharatpe.common.entities.LendingPancard;
import com.bharatpe.lending.common.query.entity.LendingPancardSlave;
import com.bharatpe.lending.dto.LendingPancardResponseDTO;
import com.bharatpe.lending.dao.underwriting.CriteriaValidator;
import com.bharatpe.lending.dao.underwriting.DynamicFieldValidator;
import com.bharatpe.lending.dao.underwriting.GenericCriteriaRepository;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.dto.underwriting.ApiResponse;
import com.bharatpe.lending.dto.underwriting.SearchRequestDTO;
import com.bharatpe.lending.dto.underwriting.read.LendingPancardReadDTO;
import com.bharatpe.lending.dto.underwriting.write.LendingPancardWriteDto;
import com.bharatpe.lending.exceptions.DataApiException;
import com.bharatpe.lending.service.ILendingPancardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Map;
import java.util.HashMap;
import com.bharatpe.lending.dto.underwriting.AggregateConditionDTO;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LendingPancardServiceImpl implements ILendingPancardService {

    @Autowired
    private LendingPancardDao lendingPancardDao;

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
        log.info("searchDynamic called with request: {}", request);

        if (request == null || request.getCriteriaList() == null || request.getCriteriaList().isEmpty()) {
            throw new DataApiException.InvalidInputException("Invalid request body");
        }

        // Detect if this is an aggregate query
        boolean isAggregateQuery = request.getAggregateConditions() != null && !request.getAggregateConditions().isEmpty();

        // Choose entity and entity manager based on consistency
        Class<?> entityClass = useStrongConsistency ? LendingPancard.class : LendingPancardSlave.class;
        EntityManager entityManager = useStrongConsistency ? masterEntityManager : queryEntityManager;

        // Validate fields dynamically
        DynamicFieldValidator fieldValidator = new DynamicFieldValidator(entityManager, entityClass);
        try {
            criteriaValidator.validateAndCoerce(request, fieldValidator);
            fieldValidator.validateRequestExtras(request);
        } catch (IllegalArgumentException ex) {
            throw new DataApiException.InvalidInputException(ex.getMessage());
        }

        // Perform dynamic search
        log.info("Using {} consistency for LendingPancard search", useStrongConsistency ? "strong" : "eventual");
        List<?> result = criteriaRepository.searchEntities(entityClass, request, useStrongConsistency);

        // Handle aggregate query scenario
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

        // Otherwise, return normal DTO-mapped results
        List<LendingPancardReadDTO> dtos = mapEntitiesToDto(result, LendingPancardReadDTO.class);

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



    @Override
    public LendingPancardResponseDTO findByMerchantId(Long merchantId) {

        log.info("findByMerchantId for merchantId : {} ", merchantId);

        LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchantId);

        return LendingPancardResponseDTO.from(lendingPancard);
    }

    @Override
    public ApiResponse<Boolean> savePancardDetails(LendingPancardWriteDto lendingPancardWriteDto) {
        log.info("savePancardDetails called for merchantId: {}", lendingPancardWriteDto.getMerchantId());

        if (lendingPancardWriteDto == null) {
            throw new DataApiException.InvalidInputException("Invalid request body");
        }

        LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(lendingPancardWriteDto.getMerchantId());

        if (lendingPancard == null) {
            throw new DataApiException.MerchantNotFoundException("Merchant not found for id: " + lendingPancardWriteDto.getMerchantId());
        }

        try {
            // Update fields from DTO
            lendingPancard.setPancardNumber(lendingPancardWriteDto.getPancardNumber());
            lendingPancard.setName(lendingPancardWriteDto.getName());

            lendingPancardDao.save(lendingPancard);

        } catch (Exception e) {
            log.error("Exception while saving LendingPancard for merchantId : {} error : {}",
                    lendingPancardWriteDto.getMerchantId(), e.getMessage(), e);
            throw new DataApiException.DatabaseException("Error while saving LendingPancard", e);
        }

        return ApiResponse.<Boolean>builder()
                .success(true)
                .message("LendingPancard saved successfully")
                .data(true)
                .build();
    }
}
