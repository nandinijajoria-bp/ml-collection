package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.dao.underwriting.CriteriaValidator;
import com.bharatpe.lending.dao.underwriting.DynamicFieldValidator;
import com.bharatpe.lending.dao.underwriting.GenericCriteriaRepository;
import com.bharatpe.lending.dto.underwriting.ApiResponse;
import com.bharatpe.lending.dto.underwriting.SearchRequestDTO;
import com.bharatpe.lending.dto.underwriting.read.LendingApplicationReadDTO;
import com.bharatpe.lending.dto.underwriting.read.LendingPaymentScheduleReadDTO;
import com.bharatpe.lending.dto.underwriting.AggregateConditionDTO;
import com.bharatpe.lending.exceptions.DataApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.HashMap;
import com.bharatpe.lending.dto.underwriting.AggregateConditionDTO;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LendingPaymentScheduleService {

    @Autowired
    private GenericCriteriaRepository criteriaRepository;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager masterEntityManager;

    @PersistenceContext(unitName = "query")
    private EntityManager queryEntityManager;

    @Autowired
    private CriteriaValidator criteriaValidator;

    public ApiResponse<Object> searchDynamic(SearchRequestDTO request, boolean useStrongConsistency) {
        log.info("searchDynamic called with request: {}", request);

        if (request == null || request.getCriteriaList() == null || request.getCriteriaList().isEmpty()) {
            throw new DataApiException.InvalidInputException("Invalid request body");
        }

        boolean isAggregateQuery = request.getAggregateConditions() != null && !request.getAggregateConditions().isEmpty();

        Class<?> entityClass = useStrongConsistency ? LendingPaymentSchedule.class : LendingPaymentScheduleSlave.class;
        EntityManager entityManager = useStrongConsistency ? masterEntityManager : queryEntityManager;

        DynamicFieldValidator fieldValidator = new DynamicFieldValidator(entityManager, entityClass);

        try {
            criteriaValidator.validateAndCoerce(request, fieldValidator);
            fieldValidator.validateRequestExtras(request);
        } catch (IllegalArgumentException ex) {
            throw new DataApiException.InvalidInputException(ex.getMessage());
        }

        List<?> result = criteriaRepository.searchEntities(entityClass, request, useStrongConsistency);
        log.info("searchDynamic result: {}", result);

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
        } else {
            List<LendingPaymentScheduleReadDTO> dtos = mapEntitiesToDto(result, LendingPaymentScheduleReadDTO.class);
            log.info("searchDynamic called with dtos: {}", dtos);
            return ApiResponse.<Object>builder()
                    .success(true)
                    .message("Records fetched successfully")
                    .data(dtos)
                    .build();
        }
    }

    private <E, D> List<D> mapEntitiesToDto(List<E> entities, Class<D> dtoClass) {
        return entities.stream()
                .map(entity -> {
                    try {
                        D dto = dtoClass.getDeclaredConstructor().newInstance();
                        BeanUtils.copyProperties(entity, dto);

                        if ((entity instanceof LendingPaymentSchedule || entity instanceof LendingPaymentScheduleSlave)
                                && dto instanceof LendingPaymentScheduleReadDTO) {
                            LendingPaymentScheduleReadDTO readDTO = (LendingPaymentScheduleReadDTO) dto;

                            Object loanAppObj = null;

                            if (entity instanceof LendingPaymentSchedule) {
                                loanAppObj = ((LendingPaymentSchedule) entity).getLoanApplication();
                            } else if (entity instanceof LendingPaymentScheduleSlave) {
                                loanAppObj = ((LendingPaymentScheduleSlave) entity).getLoanApplication();
                            }

                            if (loanAppObj != null) {
                                LendingApplicationReadDTO appDTO = new LendingApplicationReadDTO();
                                BeanUtils.copyProperties(loanAppObj, appDTO);
                                readDTO.setLoanApplication(appDTO);
                            }
                        }


                        return dto;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to map entity to DTO: " + e.getMessage(), e);
                    }
                })
                .collect(Collectors.toList());
    }
}
