package com.bharatpe.lending.service.impl;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.lending.common.query.entity.ExperianSlave;
import com.bharatpe.lending.dto.ExperianResponseDTO;
import com.bharatpe.lending.dto.InsertExperianRequestDTO;
import com.bharatpe.lending.dto.UpdateExperianRequestDTO;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.dao.underwriting.CriteriaValidator;
import com.bharatpe.lending.dao.underwriting.DynamicFieldValidator;
import com.bharatpe.lending.dao.underwriting.GenericCriteriaRepository;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.dto.underwriting.AggregateConditionDTO;
import com.bharatpe.lending.dto.underwriting.ApiResponse;
import com.bharatpe.lending.dto.underwriting.SearchRequestDTO;
import com.bharatpe.lending.dto.underwriting.read.ExperianReadDTO;
import com.bharatpe.lending.dto.underwriting.write.ExperianWriteDto;
import com.bharatpe.lending.exceptions.DataApiException;
import com.bharatpe.lending.service.IExperianService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ExperianServiceImpl implements IExperianService {

    @Autowired
    private ExperianDao experianDao;

    @Autowired
    private GenericCriteriaRepository criteriaRepository;

    @Autowired
    private CriteriaValidator criteriaValidator;

    // Inject both master and query entity managers
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager masterEntityManager;

    @PersistenceContext(unitName = "query")
    private EntityManager queryEntityManager;

    @Override
    public ApiResponse<Object> searchDynamic(SearchRequestDTO request, boolean useStrongConsistency) {
        log.info("searchDynamic called for Experian with request: {}", request);

        if (request == null || request.getCriteriaList() == null || request.getCriteriaList().isEmpty()) {
            throw new DataApiException.InvalidInputException("Invalid request body");
        }

        boolean isAggregateQuery = request.getAggregateConditions() != null && !request.getAggregateConditions().isEmpty();

        // Decide between master and slave entities based on consistency
        Class<?> entityClass = useStrongConsistency ? Experian.class : ExperianSlave.class;
        EntityManager entityManager = useStrongConsistency ? masterEntityManager : queryEntityManager;

        // Validate request
        DynamicFieldValidator fieldValidator = new DynamicFieldValidator(entityManager, entityClass);
        try {
            criteriaValidator.validateAndCoerce(request, fieldValidator);
            fieldValidator.validateRequestExtras(request);
        } catch (IllegalArgumentException ex) {
            throw new DataApiException.InvalidInputException(ex.getMessage());
        }

        // Fetch result
        List<?> result = criteriaRepository.searchEntities(entityClass, request, useStrongConsistency);

        // Handle aggregate queries
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
                    .message("Aggregate data fetched successfully for Experian")
                    .data(responseMap)
                    .build();
        } else {
            // Normal entity-to-DTO mapping
            List<ExperianReadDTO> dtos = mapEntitiesToDto(result, ExperianReadDTO.class);
            return ApiResponse.<Object>builder()
                    .success(true)
                    .message("Experian records fetched successfully")
                    .data(dtos)
                    .build();
        }
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
    public ExperianResponseDTO findByMerchantId(Long merchantId) {

        log.info("findByMerchantId for merchantId : {} ", merchantId);

        Experian experian = experianDao.getByMerchantId(merchantId);

        return ExperianResponseDTO.from(experian);
    }

    @Override
    public ExperianResponseDTO updateExperian(UpdateExperianRequestDTO updateExperianRequestDTO) {

        final Optional<Experian> experianOptional = experianDao.findById(updateExperianRequestDTO.getId());

        if (!experianOptional.isPresent())
            return null;

        Experian experian = experianOptional.get();

        if (!ObjectUtils.isEmpty(updateExperianRequestDTO.getPancardNumber())) {
            experian.setPancardNumber(updateExperianRequestDTO.getPancardNumber());
        }

        if (!ObjectUtils.isEmpty(updateExperianRequestDTO.getPincode())) {
            experian.setPincode(updateExperianRequestDTO.getPincode());
        }

        experianDao.saveAndFlush(experian);

        return ExperianResponseDTO.from(experian);
    }

    @Override
    public ExperianResponseDTO insertExperian(InsertExperianRequestDTO insertExperianRequestDTO) {

        final Experian experian = saveInExperian(insertExperianRequestDTO);

        return ExperianResponseDTO.from(experian);
    }


    @Override
    public ApiResponse<Boolean> saveExperian(ExperianWriteDto experianWriteDto) {

        if (experianWriteDto == null) {
            throw new DataApiException.InvalidInputException("Invalid request body");
        }

        Experian experian = experianDao.getByMerchantId(experianWriteDto.getMerchantId());

        if (experian == null) {
            throw new DataApiException.MerchantNotFoundException(
                    "Merchant not found for id: " + experianWriteDto.getMerchantId()
            );
        }

        try {

            // Update fields from DTO
            experian.setRejected(experianWriteDto.getRejected() != null ? experianWriteDto.getRejected() : false);
            experian.setReason(experianWriteDto.getReason());
            experian.setCategory(experianWriteDto.getCategory());
            experian.setColor(experianWriteDto.getColor());
            experian.setEligibleAmount(experianWriteDto.getEligibleAmount());
            experian.setLoanType(experianWriteDto.getLoanType());
            experian.setRejectedDate(experianWriteDto.getRejectedDate());
            experian.setReportDate(experianWriteDto.getReportDate());
            experian.setBpScore(experianWriteDto.getBpScore());
            experian.setExperianScore(experianWriteDto.getExperianScore());

            experianDao.save(experian);

        } catch (Exception e) {
            log.error("Exception while saving Experian data for merchantId: {} error: {}",
                    experianWriteDto.getMerchantId(), e.getMessage(), e);
            throw new DataApiException.DatabaseException(
                    "Error while saving Experian data", e
            );
        }

        return ApiResponse.<Boolean>builder()
                .success(true)
                .message("Experian data updated successfully")
                .data(true)
                .build();
    }


    private Experian saveInExperian(InsertExperianRequestDTO insertExperianRequestDTO) {

        Experian experian = new Experian();
        experian.setMerchantId(insertExperianRequestDTO.getMerchantId());
        experian.setIp(insertExperianRequestDTO.getIp());
        experian.setLatitude(insertExperianRequestDTO.getLatitude());
        experian.setLongitude(insertExperianRequestDTO.getLongitude());
        experian.setResponse(insertExperianRequestDTO.getResponse());
        experian.setMerchantName(insertExperianRequestDTO.getMerchantName());
        experian.setEmail(insertExperianRequestDTO.getEmail());
        experian.setRejected(ObjectUtils.isEmpty(insertExperianRequestDTO.getRejected()) ? false : insertExperianRequestDTO.getRejected());
        experian.setReason(insertExperianRequestDTO.getReason());
        experian.setRequestedLoanAmount(insertExperianRequestDTO.getRequestedLoanAmount());
        experian.setPancardNumber(insertExperianRequestDTO.getPancardNumber());
        experian.setTnc(ObjectUtils.isEmpty(insertExperianRequestDTO.getTnc()) ? true : insertExperianRequestDTO.getTnc());
        experian.setBpScore(insertExperianRequestDTO.getBpScore());
        experian.setExperianScore(insertExperianRequestDTO.getExperianScore());
        experian.setCategory(insertExperianRequestDTO.getCategory());
        experian.setColor(insertExperianRequestDTO.getColor());
        experian.setRetryCount(ObjectUtils.isEmpty(insertExperianRequestDTO.getRetryCount()) ? 0 : insertExperianRequestDTO.getRetryCount());
        experian.setSkip(ObjectUtils.isEmpty(insertExperianRequestDTO.isSkip()) ? false : insertExperianRequestDTO.isSkip());
        experian.setPincode(insertExperianRequestDTO.getPincode());
        experian.setRejectedDate(insertExperianRequestDTO.getRejectedDate());
        experian.setReportDate(insertExperianRequestDTO.getReportDate());
        experian.setEligibleAmount(insertExperianRequestDTO.getEligibleAmount());
        experian.setEligibleTenure(insertExperianRequestDTO.getEligibleTenure());
        experian.setLoanType(insertExperianRequestDTO.getLoanType());
        experian.setSource(ObjectUtils.isEmpty(insertExperianRequestDTO.getSource()) ? "LOAN" : insertExperianRequestDTO.getSource());
        experian.setBureau(insertExperianRequestDTO.getBureau());
        experian.setHitId(insertExperianRequestDTO.getHitId());

        experianDao.saveAndFlush(experian);

        return experian;
    }

}
