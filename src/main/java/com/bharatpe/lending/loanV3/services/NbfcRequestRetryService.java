package com.bharatpe.lending.loanV3.services;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.mongo.NBFCRetryRepository;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.mongo.NBFCRetry;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.NbfcRetryStatus;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.consumer.KycRequestKafka;
import com.bharatpe.lending.loanV3.dto.EKycCallbackResponseDto;
import com.bharatpe.lending.loanV3.dto.EKycStatusCheckRequestApiDto;
import com.bharatpe.lending.loanV3.dto.KycCallbackResponseDto;
import com.bharatpe.lending.loanV3.factory.LenderGatewayFactory;
import com.bharatpe.lending.service.RedisNotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.bharatpe.lending.enums.Lender.ABFL;

@Service
@Slf4j
public class NbfcRequestRetryService {

    private final ObjectMapper objectMapper;
    private final NBFCRetryRepository nbfcRetryRepository;
    private final KycRequestKafka kycRequestKafka;
    private final LenderGatewayFactory lenderGatewayFactory;

    @Value("#{${nbfc.ekyc-status.retry.timeout:{0:10, 1:300, 2:300}}}")
    private Map<Integer, Long> ekycStatusRetryTimeoutsMap = new HashMap<>();

    @Value("${nbfc.retry.max-retries-count:3}")
    private int maxRetriesCount;

    public NbfcRequestRetryService(ObjectMapper objectMapper, LendingApplicationDao lendingApplicationDao, LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao, NBFCRetryRepository nbfcRetryRepository, KycRequestKafka kycRequestKafka, LenderGatewayFactory lenderGatewayFactory, RedisNotificationService redisNotificationService) {
        this.objectMapper = objectMapper;
        this.nbfcRetryRepository = nbfcRetryRepository;
        this.kycRequestKafka = kycRequestKafka;
        this.lenderGatewayFactory = lenderGatewayFactory;
    }

    /**
     * Processes the retry request for a given lending application.
     *
     * @param lendingApplication the lending application
     * @param lendingApplicationLenderDetails the lender details of the lending application
     * @param nbfcRetryRequestDto the retry request details
     */
    public void processRetryRequest(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, NBFCRetry nbfcRetryRequestDto) {
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.warn("Lender {} not found for application {}", nbfcRetryRequestDto.getApplicationId(), nbfcRetryRequestDto.getLender());
            return;
        }
        if (nbfcRetryRequestDto.getRequestType().equals(LenderAssociationStages.EKYC_STATUS.name())) {
            switch (LenderAssociationStatus.valueOf(lendingApplicationLenderDetails.getKycStatus())) {
                /*
                * TODO: To explore creating common interface for status retry
                *  and use patterns to implement each type of retry
                */
                case EKYC_IN_PROGRESS:
                    processEkycStatusRetry(lendingApplication, lendingApplicationLenderDetails, nbfcRetryRequestDto);
                    break;
                case KYC_IN_PROGRESS:
                    processKycStatusRetry(lendingApplication, lendingApplicationLenderDetails, nbfcRetryRequestDto);
                    break;
                default:
                    log.info("Request Type currently not supported in delayed nbfc retry {}", nbfcRetryRequestDto);
            }
        } else {
            log.info("Request Type currently not supported in delayed nbfc retry {}", nbfcRetryRequestDto);
        }
    }

    private void processKycStatusRetry(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, NBFCRetry nbfcRetryRequest) {
        //TODO: Add polling logic once needed.
        log.info("Skipping retry as KYC is in progress for application {}", lendingApplicationLenderDetails.getApplicationId());
        if (NbfcRetryStatus.INIT.equals(nbfcRetryRequest.getStatus())) {
            nbfcRetryRequest.setRetriesRemaining(nbfcRetryRequest.getRetriesRemaining() - 1);
            if (nbfcRetryRequest.getRetriesRemaining() <= 0) {
                log.error("Retry count exhausted for KYC Status check for application {}", lendingApplicationLenderDetails.getApplicationId());
                handleKycFailureCallback(lendingApplicationLenderDetails);
                nbfcRetryRequest.setStatus(NbfcRetryStatus.FAILED);
            }
            nbfcRetryRequest.setUpdatedAt(new Date());
            nbfcRetryRepository.save(nbfcRetryRequest);
        }
    }

    private void processEkycStatusRetry(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, NBFCRetry nbfcRetryRequest) {
        if (lendingApplicationLenderDetails.getLender().equals(ABFL.name())) {
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)
                    || !Arrays.asList(LenderAssociationStatus.EKYC_IN_PROGRESS.name()).contains(lendingApplicationLenderDetails.getKycStatus())) {
                log.info("Kyc Status is not correct in lender details for eKyc status check for application {}", lendingApplicationLenderDetails.getApplicationId());
                nbfcRetryRequest.setStatus(NbfcRetryStatus.SUCCESS);
                nbfcRetryRequest.setUpdatedAt(new Date());
                nbfcRetryRepository.save(nbfcRetryRequest);
                return;
            }
            try {
                EKycStatusCheckRequestApiDto eKycStatusCheckRequestApiDto = EKycStatusCheckRequestApiDto.builder()
                        .applicationId(lendingApplication.getId())
                        .topup(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()))
                        .productName("LENDING")
                        .lender(lendingApplication.getLender())
                        .payload(EKycStatusCheckRequestApiDto.Payload.builder()
                                .accountId(lendingApplication.getExternalLoanId())
                                .build())
                        .skipTermination(!(nbfcRetryRequest.getRetriesRemaining() <= 1))
                        .build();
                INbfcLenderGateway apiGatewayV3 = lenderGatewayFactory.getLenderApiGateway(eKycStatusCheckRequestApiDto.getLender());
                EKycCallbackResponseDto eKycCallbackResponseDto = apiGatewayV3.invokeEKycStatusCheck(eKycStatusCheckRequestApiDto);

                log.info("EKYC status check API response received for Application ID {} with status {}", lendingApplication.getId(), eKycCallbackResponseDto.getSuccess());
                nbfcRetryRequest.getRemarks().put(maxRetriesCount - nbfcRetryRequest.getRetriesRemaining(), objectMapper.writeValueAsString(eKycCallbackResponseDto));
                if (!ObjectUtils.isEmpty(eKycCallbackResponseDto) && Boolean.TRUE.equals(eKycCallbackResponseDto.getSuccess())) {
                    kycRequestKafka.eKycCallbackListener(objectMapper.writeValueAsString(eKycCallbackResponseDto));
                    nbfcRetryRequest.setStatus(NbfcRetryStatus.SUCCESS);
                    nbfcRetryRequest.setUpdatedAt(new Date());
                    nbfcRetryRepository.save(nbfcRetryRequest);
                } else {
                    nbfcRetryRequest.setRetriesRemaining(nbfcRetryRequest.getRetriesRemaining() - 1);
                }
            } catch (Exception e) {
                log.error("Exception occurred while processing eKyc status check for application {}", lendingApplicationLenderDetails.getApplicationId(), e);
                nbfcRetryRequest.getRemarks().put(maxRetriesCount - nbfcRetryRequest.getRetriesRemaining(), e.getMessage());
                nbfcRetryRequest.setRetriesRemaining(nbfcRetryRequest.getRetriesRemaining() - 1);
            }
            if (NbfcRetryStatus.INIT.equals(nbfcRetryRequest.getStatus())) {
                if (nbfcRetryRequest.getRetriesRemaining() <= 0) {
                    log.error("Retry count exhausted for EKYC Status check for application {}", lendingApplicationLenderDetails.getApplicationId());
                    handleEkycFailureCallback(lendingApplicationLenderDetails);
                    nbfcRetryRequest.setStatus(NbfcRetryStatus.FAILED);
                }
                nbfcRetryRequest.setUpdatedAt(new Date());
                nbfcRetryRepository.save(nbfcRetryRequest);
            }
        } else {
            log.warn("Lender {} not supported for ekyc status retry", lendingApplicationLenderDetails.getLender());
        }
    }

    private void handleEkycFailureCallback(LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        EKycCallbackResponseDto eKycCallbackResponseDto = EKycCallbackResponseDto.builder()
                .success(false)
                .applicationId(String.valueOf(lendingApplicationLenderDetails.getApplicationId()))
                .lender(lendingApplicationLenderDetails.getLender())
                .productName("LENDING")
                .build();
        try {
            kycRequestKafka.eKycCallbackListener(objectMapper.writeValueAsString(eKycCallbackResponseDto));
        } catch (JsonProcessingException e) {
            log.error("Json processing exception while triggering failure ekyc callback for application {}", lendingApplicationLenderDetails.getApplicationId(), e);
        }
    }

    private void handleKycFailureCallback(LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        KycCallbackResponseDto kycCallbackResponseDto = KycCallbackResponseDto.builder()
                .success(false)
                .applicationId(String.valueOf(lendingApplicationLenderDetails.getApplicationId()))
                .lender(lendingApplicationLenderDetails.getLender())
                .productName("LENDING")
                .build();
        try {
            kycRequestKafka.kycCallbackListener(objectMapper.writeValueAsString(kycCallbackResponseDto));
        } catch (JsonProcessingException e) {
            log.error("Json processing exception while triggering failure ekyc callback for application {}", lendingApplicationLenderDetails.getApplicationId(), e);
        }
    }

    /**
     * Forcefully updates the retry status of the given NBFC retry object.
     *
     * @param nbfcRetryObj the NBFC retry object to update
     * @param nbfcRetryStatus the new status to set
     */
    public void forceUpdateRetryStatus(NBFCRetry nbfcRetryObj, NbfcRetryStatus nbfcRetryStatus) {
        if (ObjectUtils.isEmpty(nbfcRetryObj)) {
            return;
        }
        nbfcRetryObj.setStatus(nbfcRetryStatus);
        nbfcRetryObj.setUpdatedAt(new Date());
        nbfcRetryRepository.save(nbfcRetryObj);
    }
}
