package com.bharatpe.lending.loanV3.consumer;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.mongo.NbfcRetryRepository;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.mongo.NbfcRetry;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.NbfcRetryStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.NbfcRetryRequestDto;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.EKycCallbackResponseDto;
import com.bharatpe.lending.loanV3.dto.EKycStatusCheckRequestApiDto;
import com.bharatpe.lending.loanV3.factory.LenderGatewayFactory;
import com.bharatpe.lending.loanV3.services.INbfcLenderGateway;
import com.bharatpe.lending.service.RedisNotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.bharatpe.lending.enums.Lender.ABFL;

@Component
@Slf4j
public class NbfcRetryRequestListener {

    private final ObjectMapper objectMapper;
    private final LendingApplicationDao lendingApplicationDao;
    private final LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
    private final NbfcRetryRepository nbfcRetryRepository;
    private final KycRequestKafka kycRequestKafka;
    private final LenderGatewayFactory lenderGatewayFactory;
    private final RedisNotificationService redisNotificationService;

    @Value("#{${nbfc.ekyc-status.retry.timeout:{0:10, 1:300, 2:300}}}")
    private Map<Integer, Long> ekycStatusRetryTimeoutsMap = new HashMap<>();

    @Value("${nbfc.retry.max-retries-count:3}")
    private int maxRetriesCount;

    public NbfcRetryRequestListener(ObjectMapper objectMapper,
                                    LendingApplicationDao lendingApplicationDao,
                                    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao,
                                    NbfcRetryRepository nbfcRetryRepository, KycRequestKafka kycRequestKafka, LenderGatewayFactory lenderGatewayFactory, RedisNotificationService redisNotificationService) {
        this.objectMapper = objectMapper;
        this.lendingApplicationDao = lendingApplicationDao;
        this.lendingApplicationLenderDetailsDao = lendingApplicationLenderDetailsDao;
        this.nbfcRetryRepository = nbfcRetryRepository;
        this.kycRequestKafka = kycRequestKafka;
        this.lenderGatewayFactory = lenderGatewayFactory;
        this.redisNotificationService = redisNotificationService;
    }


    @KafkaListener(
            topics="${nbfc.retry.topic:retry_nbfc_requests}",
            concurrency = "1",
            autoStartup = "true",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void nbfcRetryRequestListener(String message) {
        log.info("Processing the nbfc retry request message: {}", message);
        if (StringUtils.isEmpty(message)) {
            log.warn("Empty message payload received for processing the nbfc retry request: {}", message);
            return;
        }
        try {
            NbfcRetryRequestDto nbfcRetryRequestDto = objectMapper.readValue(message, NbfcRetryRequestDto.class);
            if (ObjectUtils.isEmpty(nbfcRetryRequestDto) || ObjectUtils.isEmpty(nbfcRetryRequestDto.getMerchantId()) || ObjectUtils.isEmpty(nbfcRetryRequestDto.getApplicationId())) {
                log.warn("Invalid message payload received for processing the nbfc retry request: {}", message);
                return;
            }
            Optional<NbfcRetry> nbfcRetryRequest = nbfcRetryRepository.findById(nbfcRetryRequestDto.getRetryId());
            if (!nbfcRetryRequest.isPresent()) {
                log.warn("Unable to find nbfc retry request for application ID: {}", nbfcRetryRequestDto.getApplicationId());
                return;
            }
            Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(nbfcRetryRequestDto.getApplicationId());
            if (!lendingApplicationOptional.isPresent()) {
                log.warn("Unable to find lending application for message payload received for ID: {}", message);
                return;
            }

            LendingApplication lendingApplication = lendingApplicationOptional.get();;
            LendingApplicationLenderDetails lendingApplicationLenderDetails =
                    lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), ABFL.name());

            processRetryRequest(lendingApplication, lendingApplicationLenderDetails, nbfcRetryRequest.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private void processRetryRequest(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, NbfcRetry nbfcRetryRequestDto) {
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.warn("Lender {} not found for application {}", nbfcRetryRequestDto.getApplicationId(), nbfcRetryRequestDto.getLender());
            return;
        }
        if (nbfcRetryRequestDto.getRequestType().equals("EKYC_STATUS")) {
            processEkycStatusRetry(lendingApplication, lendingApplicationLenderDetails, nbfcRetryRequestDto);
        } else {
            log.info("Request Type currently not supported in delayed nbfc retry {}", nbfcRetryRequestDto);
        }
    }

    private void processEkycStatusRetry(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, NbfcRetry nbfcRetryRequest) {
        if (lendingApplicationLenderDetails.getLender().equals(ABFL.name())) {
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)
                    || !Arrays.asList(LenderAssociationStatus.EKYC_IN_PROGRESS.name(), LenderAssociationStatus.EKYC_INITIATED.name()).contains(lendingApplicationLenderDetails.getKycStatus())) {
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

                if (!ObjectUtils.isEmpty(eKycCallbackResponseDto) && Boolean.TRUE.equals(eKycCallbackResponseDto.getSuccess())) {
                    kycRequestKafka.eKycCallbackListener(objectMapper.writeValueAsString(eKycCallbackResponseDto));
                    nbfcRetryRequest.setStatus(NbfcRetryStatus.SUCCESS);
                    nbfcRetryRequest.setUpdatedAt(new Date());
                    nbfcRetryRepository.save(nbfcRetryRequest);
                } else {
                    nbfcRetryRequest.setRetriesRemaining(nbfcRetryRequest.getRetriesRemaining() - 1);
                }
                nbfcRetryRequest.getRemarks().put(maxRetriesCount - nbfcRetryRequest.getRetriesRemaining(), objectMapper.writeValueAsString(eKycCallbackResponseDto));
            } catch (Exception e) {
                log.error("Exception occurred while processing eKyc status check for application {}", lendingApplicationLenderDetails.getApplicationId(), e);
                nbfcRetryRequest.setRetriesRemaining(nbfcRetryRequest.getRetriesRemaining() - 1);
                nbfcRetryRequest.getRemarks().put(maxRetriesCount - nbfcRetryRequest.getRetriesRemaining(), e.getMessage());
            }
            if (NbfcRetryStatus.INIT.equals(nbfcRetryRequest.getStatus())) {
                if (nbfcRetryRequest.getRetriesRemaining() <= 0) {
                    log.error("Retry count exhausted for EKYC Status check for application {}", lendingApplicationLenderDetails.getApplicationId());
                    handleEkycFailureCallback(lendingApplicationLenderDetails);
                    nbfcRetryRequest.setStatus(NbfcRetryStatus.FAILED);
                } else {
                    redisNotificationService.sendNbfcRetryRequestMessage(nbfcRetryRequest,  ekycStatusRetryTimeoutsMap.getOrDefault(maxRetriesCount - nbfcRetryRequest.getRetriesRemaining(), 300L));
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
}
