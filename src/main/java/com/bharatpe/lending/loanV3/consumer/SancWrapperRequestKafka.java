package com.bharatpe.lending.loanV3.consumer;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dto.BharatPeEnachResponseDTO;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.StatusCheckResponse;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.ConfigResolver;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.loanV3.dto.SanctionCallbackResponseDto;
import com.bharatpe.lending.loanV3.dto.SanctionWrapperApiRequestDto;
import com.bharatpe.lending.loanV3.dto.SanctionWrapperApiResponse;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.factory.LenderGatewayFactory;
import com.bharatpe.lending.loanV3.services.INbfcLenderGateway;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Component
@Slf4j
public class SancWrapperRequestKafka {

    @Autowired
    ConfigResolver configResolver;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LenderAssociationStageFactory lenderAssociationStageFactory;

    @Autowired
    LenderGatewayFactory lenderGatewayFactory;

    @Autowired
    NbfcUtils nbfcUtils;

//    @Autowired
//    EnachHandler enachHandler;

    @Autowired
    MerchantService merchantService;

    @KafkaListener(topics = "${abfl.sanction.topic:invoke_sanction}", concurrency = "5",autoStartup = "false")
    public void sanctionRequestInvoke(String request) {
        MDC.put("requestId", UUID.randomUUID().toString());
        log.info("Received sanction request:{}", request);
        Optional<LendingApplication> lendingApplication = Optional.empty();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = null;
        try {
            Map<String,String> sanctionRequest = configResolver.getConfig(request, new TypeReference<Map<String, String>>() {
            });
            lendingApplication = lendingApplicationDao.findById(Long.valueOf(sanctionRequest.get("application_id")));
            if (!lendingApplication.isPresent()) {
                log.info("no application found for id {}", sanctionRequest);
            }
            SanctionWrapperApiRequestDto sanctionWrapperApiRequestDto = createPayload(Long.valueOf(sanctionRequest.get("application_id")));
            lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(sanctionWrapperApiRequestDto.getApplicationId(), Status.ACTIVE.name());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)  ||
                    (!sanctionWrapperApiRequestDto.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())
                            || !LenderAssociationStages.ASSC_COMPLETED.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())
                    )
            ) {
                log.info("lender/stage mismatch while initiating sanction for application {}", sanctionWrapperApiRequestDto.getApplicationId());
                return;
            }
            lendingApplicationLenderDetails.setStage(LenderAssociationStages.SANCTION_WRAPPER.name());
            lendingApplicationLenderDetails.setSanctionStatus(LenderAssociationStatus.SANCTION_PENDING.name());
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
            INbfcLenderGateway apiGatewayV3 = lenderGatewayFactory.getLenderApiGateway(sanctionWrapperApiRequestDto.getLender());
            SanctionWrapperApiResponse sanctionWrapperApiResponse = apiGatewayV3.invokeSanction(sanctionWrapperApiRequestDto);
            if (ObjectUtils.isEmpty(sanctionWrapperApiResponse) ||
//                    ObjectUtils.isEmpty(sanctionWrapperApiResponse.getData()) ||
//                    ObjectUtils.isEmpty(sanctionWrapperApiResponse.getData().getData()) ||
                    !sanctionWrapperApiResponse.getSuccess()
//            || !StatusCheckResponse.SUCCESS.name().equalsIgnoreCase(sanctionWrapperApiResponse.getData().getResponseStatus())
        ) {
                log.info("request resulted in sanction failure, modifying lender for {}", request);
                // TODO: 24/11/22 rollback stage in case of failure
                nbfcUtils.modifyLender(lendingApplication.get(), lendingApplicationLenderDetails, LenderAssociationStatus.SANCTION_FAILED);
            } else {
                // todo final this is for async response
                lendingApplicationLenderDetails.setSanctionStatus(LenderAssociationStatus.SANCTION_IN_PROGRESS.name());
                lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                log.info("successfully placed the sanction request at lender for {}", request);
                // async block complete

                // sync block
//                SanctionWrapperApiResponse.SanctionResponseData data = sanctionWrapperApiResponse.getData().getData();
//                LenderAssociationStages nextStage = LenderAssociationStageFactory.getNextStage(Lender.valueOf(sanctionWrapperApiResponse.getLender()),LenderAssociationStages.SANCTION_WRAPPER);
//                lendingApplicationLenderDetails.setStage(nextStage.name());
//                lendingApplicationLenderDetails.setAccountState(data.getAccountState());
//                lendingApplicationLenderDetails.setKycMode(data.getKycMode());
//                lendingApplicationLenderDetails.setDealGenerationTimestamp(new Date());
//                lendingApplicationLenderDetails.setApprovedOfferLimit(Double.valueOf(data.getApprovedOfferLimit()));
//                lendingApplicationLenderDetails.setDealId(data.getDealId());
//                lendingApplicationLenderDetails.setDealNo(data.getDealNo());
//                lendingApplicationLenderDetails.setSanctionStatus(LenderAssociationStatus.SANCTION_COMPLETED.name());
//                lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
//                nbfcUtils.pushApplicationToNextStage(lendingApplication.get().getId(), lendingApplication.get().getLender(), LenderAssociationStages.SANCTION_WRAPPER.name(),
//                        LenderAssociationStageFactory.autoInvokeNextStage(Lender.valueOf(lendingApplication.get().getLender()), LenderAssociationStages.SANCTION_WRAPPER));
//                log.info("successfully sanctioned loan for {}", request);
                // sync block ends
            }
        } catch (Exception e) {
            log.error("Exception occurred while invoking sanction for {}", request, e);
            nbfcUtils.modifyLender(lendingApplication.get(), lendingApplicationLenderDetails, LenderAssociationStatus.SANC_REQUEST_CLIENT_FAILURE);
        }
    }

    @KafkaListener(topics = "${abfl.sanction.callback.topic:sanction-callback}", concurrency = "5",autoStartup = "false")
    public void sanctionCallbackListener(String request) {
        Optional<LendingApplication> lendingApplication = Optional.empty();
        LendingApplicationLenderDetails existingLendingApplicationLenderDetails = null;
        try {
            MDC.put("requestId", UUID.randomUUID().toString());
            log.info("Received sanction callback request:{}", request);
            SanctionCallbackResponseDto sanctionCallbackResponseDto = configResolver.getConfig(request, SanctionCallbackResponseDto.class);
            lendingApplication = lendingApplicationDao.findById(Long.valueOf(sanctionCallbackResponseDto.getApplicationId()));
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("no application found for id {}", sanctionCallbackResponseDto.getApplicationId());
                return;
            }
            existingLendingApplicationLenderDetails =
                    lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(lendingApplication.get().getId(),Status.ACTIVE.name());
            if (ObjectUtils.isEmpty(existingLendingApplicationLenderDetails) ||
                    !sanctionCallbackResponseDto.getLender().equalsIgnoreCase(existingLendingApplicationLenderDetails.getLender())
                    || !ObjectUtils.isEmpty(existingLendingApplicationLenderDetails.getDealId())) {
                log.info("lender mismatch while callback ack / callback already received for sanction in application {}", lendingApplication.get().getId());
                return;
            }
            if (Boolean.FALSE.equals(sanctionCallbackResponseDto.getSuccess())) {
                log.info("modifying lender as sanction callback resulted in failure for  {}", lendingApplication.get().getId());
                nbfcUtils.modifyLender(lendingApplication.get(),existingLendingApplicationLenderDetails,LenderAssociationStatus.SANCTION_FAILED);
                return;
            }
            SanctionCallbackResponseDto.Data data = sanctionCallbackResponseDto.getData().getData();
            LenderAssociationStages nextStage = LenderAssociationStageFactory.getNextStage(Lender.valueOf(sanctionCallbackResponseDto.getLender()),LenderAssociationStages.SANCTION_WRAPPER);
            existingLendingApplicationLenderDetails.setStage(nextStage.name());
            existingLendingApplicationLenderDetails.setAccountState(data.getAccountState());
            existingLendingApplicationLenderDetails.setKycMode(data.getKycMode());
            existingLendingApplicationLenderDetails.setDealGenerationTimestamp(new Date());
            existingLendingApplicationLenderDetails.setApprovedOfferLimit(Double.valueOf(data.getApprovedOfferLimit()));
            existingLendingApplicationLenderDetails.setDealId(data.getDealId());
            existingLendingApplicationLenderDetails.setDealNo(data.getDealNo());
            existingLendingApplicationLenderDetails.setSanctionStatus(LenderAssociationStatus.SANCTION_COMPLETED.name());
            lendingApplicationLenderDetailsDao.save(existingLendingApplicationLenderDetails);
            nbfcUtils.pushApplicationToNextStage(lendingApplication.get().getId(), lendingApplication.get().getLender(), LenderAssociationStages.SANCTION_WRAPPER.name(),
                    LenderAssociationStageFactory.autoInvokeNextStage(Lender.valueOf(lendingApplication.get().getLender()), LenderAssociationStages.SANCTION_WRAPPER));
            log.info("successfully sanctioned loan for {}", request);
        } catch (Exception ex) {
            log.error("exception occurred while processing bre callback request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
//            throw new RuntimeException("unable to ack kyc callback event" + request);
            nbfcUtils.modifyLender(lendingApplication.get(),existingLendingApplicationLenderDetails,LenderAssociationStatus.SANC_CALLBACK_CLIENT_FAILURE);
        }
    }

    public SanctionWrapperApiRequestDto createPayload(Long applicationId) {
        try {
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
            if (!lendingApplication.isPresent()) {
                log.error("application not found !! {}", applicationId);
            }
//            BharatPeEnachResponseDTO bharatPeEnachResponseDTO = enachHandler.findByMerchantIdAndApplicationId(lendingApplication.get().getMerchantId(), lendingApplication.get().getId());
            final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(lendingApplication.get().getMerchantId());
            // TODO: 11/11/22 todo final operation and creditflow type values
            SanctionWrapperApiRequestDto sanctionWrapperApiRequestDto = SanctionWrapperApiRequestDto.builder()
                    .applicationId(lendingApplication.get().getId())
                    .lender(lendingApplication.get().getLender())
                    .productName("LENDING")
                    .payload(SanctionWrapperApiRequestDto.Payload.builder()
                            .accountId(lendingApplication.get().getExternalLoanId())
                            .loanAmount(lendingApplication.get().getLoanAmount().intValue())
                            .kyc(SanctionWrapperApiRequestDto.Kyc.builder()
                                    .operationsFlowType(16)
                                    .build())
                            .bankDetails(SanctionWrapperApiRequestDto.BankDetails.builder()
                                    .accountNumber(bankDetailsDtoOptional.get().getAccountNumber())
                                    .IFSCCode(bankDetailsDtoOptional.get().getIfsc())
                                    .build())
//                            .mandateDetails(SanctionWrapperApiRequestDto.MandateDetails.builder()
//                                    .referenceNo(bharatPeEnachResponseDTO.getMandateId())
//                                    .status(bharatPeEnachResponseDTO.getSuccess())
//                                    .vendor(bharatPeEnachResponseDTO.getEnachProvider())
//                                    .build())
                            .build())
                    .build();
            log.info("sanction request payload {}", sanctionWrapperApiRequestDto);
            return sanctionWrapperApiRequestDto;
        } catch (Exception e) {
            log.error("exception occurred while initiating sanction workflow for  {}", applicationId, e);
        }
        return  null;
    }
}
