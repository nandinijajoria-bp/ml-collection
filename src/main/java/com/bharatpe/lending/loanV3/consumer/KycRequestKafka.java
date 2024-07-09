package com.bharatpe.lending.loanV3.consumer;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.common.util.ConfigResolver;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.NameAndDobDetailsDto;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.factory.LenderGatewayFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssignment;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.loanV3.services.INbfcLenderGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.bharatpe.lending.service.LendingDelayedMessagePublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;

@Component
@Slf4j
public class KycRequestKafka {

    @Autowired
    ConfigResolver configResolver;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    ILenderAssignment iLenderAssignment;

    @Autowired
    LendingDelayedMessagePublisher lendingDelayedMessagePublisher;

    @Autowired
    LenderGatewayFactory lenderGatewayFactory;

    @Autowired
    LenderAssociationStageFactory lenderAssociationStageFactory;

    @Lazy
    @Autowired
    NbfcUtils nbfcUtils;

    @Autowired
    KycUtils kycUtils;

    @Autowired
    ConverterUtils converterUtils;

    @Autowired
    @Lazy
    LendingApplicationServiceV2 lendingApplicationServiceV2;
    
    @Autowired
    LoanDetailsV3Service loanDetailsV3Service;

    @KafkaListener(topics= "${abfl.kyc.topic:invoke_kyc}", concurrency = "5")

    @KafkaListener(
            topics="${abfl.kyc.topic:invoke_kyc}",
            concurrency = "5",
            autoStartup = "${kafka.confluent.consumer.new:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void kycRequestListener(String request) {
        Optional<LendingApplication> lendingApplication = Optional.empty();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = null;
        try {
            MDC.put("requestId", UUID.randomUUID().toString());
            log.info("Received kyc request:{}", request);
            Map<String,String> kycRequest = configResolver.getConfig(request, new TypeReference<Map<String, String>>() {
            });
            lendingApplication = lendingApplicationDao.findById(Long.valueOf(kycRequest.get("application_id")));
            if (!lendingApplication.isPresent()) {
                log.info("no application found for id {}", kycRequest.get("application_id"));
            }
            KycRequestApiDto kycRequestDto = createPayload(Long.valueOf(kycRequest.get("application_id")));
            lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(kycRequestDto.getApplicationId(),Status.ACTIVE.name(), Lender.ABFL.name());
            if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails) &&
                    (!kycRequestDto.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) ||
                    !LenderAssociationStages.KYC.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())) {
                log.info("lender or stage mismatch while initiating kyc for application {}", kycRequestDto.getApplicationId());
                return;
            }
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
                lendingApplicationLenderDetails.setApplicationId(kycRequestDto.getApplicationId());
                lendingApplicationLenderDetails.setLender(kycRequestDto.getLender());
                lendingApplicationLenderDetails.setStage(LenderAssociationStages.KYC.name());
                lendingApplicationLenderDetails.setStatus(Status.ACTIVE.name());
                lendingApplicationLenderDetails.setAccountId(lendingApplication.get().getExternalLoanId());
                DecimalFormat df = new DecimalFormat("#.##");
                df.setRoundingMode(RoundingMode.DOWN);
                lendingApplicationLenderDetails.setAnnualRoi(Double.valueOf(df.format(
                        lendingApplicationServiceV2.getApr(lendingApplication.get().getMerchantId(), lendingApplication.get().getId(), lendingApplication.get().getLoanAmount(),
                                LenderOffDays.valueOf(lendingApplication.get().getLender()).getEdiModel().getNoOfEdiDaysInAWeek(), lendingApplication.get().getLender()))));
            }
            lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.KYC_PENDING.name());
            lendingApplicationLenderDetails.setTxnId(kycRequestDto.getPayload().getTransactionId());
            lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
            INbfcLenderGateway apiGatewayV3 = lenderGatewayFactory.getLenderApiGateway(kycRequestDto.getLender());
            KycApiResponseDto kycApiResponseDto = apiGatewayV3.invokeKyc(kycRequestDto);
            log.info("kyc api response {}", kycApiResponseDto);
            if (ObjectUtils.isEmpty(kycApiResponseDto) || !(kycApiResponseDto.getSuccess())
                    || (ObjectUtils.isEmpty(kycApiResponseDto.getData()))
                        || lendingApplicationLenderDetails.getAnnualRoi() > 50) {
                if (LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType())) {
                    log.info("marking kycStatus KYC_FAILED for topup application as kyc resulted in failure for  {}", lendingApplication.get().getId());
                    lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
                    lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                } else {
                    log.info("request resulted in kyc failure, modifying lender for {}", request);
                    nbfcUtils.modifyLender(lendingApplication.get(), lendingApplicationLenderDetails, LenderAssociationStatus.KYC_FAILED);
                }
            }
            else {
                lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.KYC_IN_PROGRESS.name());
                lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                log.info("successfully placed the kyc request at lender for {}", request);

            }
        } catch (Exception ex) {
            log.error("exception occurred while processing kyc request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
//            throw new RuntimeException("unable to ack kyc event" + request);
            nbfcUtils.modifyLender(lendingApplication.get(),lendingApplicationLenderDetails,LenderAssociationStatus.KYC_REQUEST_CLIENT_FAILURE);
        }
    }

    @KafkaListener(
            topics="${abfl.kyc.callback.topic:kyc-callback}",
            autoStartup = "${kafka.confluent.consumer.new:false}",
            containerFactory = "ConfluentKafkaListenerContainer")
    public void kycCallbackListener(String request) {
        Optional<LendingApplication> lendingApplication = Optional.empty();
        LendingApplicationLenderDetails existingLendingApplicationLenderDetails = null;
        try {
            MDC.put("requestId", UUID.randomUUID().toString());
            log.info("Received kyc callback request:{}", request);
            KycCallbackResponseDto kycCallbackResponseDto = configResolver.getConfig(request, KycCallbackResponseDto.class);
            log.info("kycCallbackResponseDto payload: {}", kycCallbackResponseDto);
            lendingApplication = lendingApplicationDao.findById(Long.valueOf(kycCallbackResponseDto.getApplicationId()));
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("no application found for id {}", kycCallbackResponseDto.getApplicationId());
                return;
            }
//            KafkaAudit kafkaAudit = new KafkaAudit(AUDIT_POD, AUDIT_SERVICE,"bre_callback_request", breRequest);
            existingLendingApplicationLenderDetails =
                    lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.get().getId(),Status.ACTIVE.name(), Lender.ABFL.name());
            if (ObjectUtils.isEmpty(existingLendingApplicationLenderDetails) ||
                    !kycCallbackResponseDto.getLender().equalsIgnoreCase(existingLendingApplicationLenderDetails.getLender())
                    || !ObjectUtils.isEmpty(existingLendingApplicationLenderDetails.getKycCompletionTimestamp())) {
                log.info("lender mismatch while callback ack / callback already received for kyc in application {}", lendingApplication.get().getId());
                return;
            }
            if (Boolean.FALSE.equals(kycCallbackResponseDto.getSuccess())) {
                if (LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType())) {
                    if(existingLendingApplicationLenderDetails.getKycRetryCount() < 3) {
                        log.info("marking kycStatus KYC_retry for topup application as kyc callback resulted in failure for  {}", lendingApplication.get().getId());
                        existingLendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.KYC_RETRY.name());
                        existingLendingApplicationLenderDetails.setKycRetryCount(existingLendingApplicationLenderDetails.getKycRetryCount() + 1);
                        loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.get().getId(), LendingViewStates.KYC_PAGE);
                    }
                    else{
                        log.info("marking kycStatus KYC_FAILED for topup application as kyc callback resulted in failure after 3 retry for  {}", lendingApplication.get().getId());
                        existingLendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
                    }
                    lendingApplicationLenderDetailsDao.save(existingLendingApplicationLenderDetails);
                    return;
                }
                log.info("modifying lender as kyc callback resulted in failure for  {}", lendingApplication.get().getId());
                nbfcUtils.modifyLender(lendingApplication.get(), existingLendingApplicationLenderDetails, LenderAssociationStatus.KYC_FAILED);
                return;
            }
            KycCallbackResponseDto.Data data= kycCallbackResponseDto.getData();
            existingLendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.KYC_COMPLETED.name());
            LenderAssociationStages nextStage = LenderAssociationStageFactory.getNextStage(Lender.valueOf(kycCallbackResponseDto.getLender()),LenderAssociationStages.KYC);
            existingLendingApplicationLenderDetails.setStage(nextStage.name());
            existingLendingApplicationLenderDetails.setKycCompletionTimestamp(new Date());
            existingLendingApplicationLenderDetails.setNbfcKycAsyncId(data.getAsyncId());
            existingLendingApplicationLenderDetails.setCkycType(data.getKycType());
            lendingApplicationLenderDetailsDao.save(existingLendingApplicationLenderDetails);

            if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType())){
                loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.get().getId(), LendingViewStates.ENACH_PAGE);
            }

            nbfcUtils.pushApplicationToNextStage(lendingApplication.get().getId(),lendingApplication.get().getLender(), LenderAssociationStages.KYC.name(),
                    LenderAssociationStageFactory.autoInvokeNextStage(Lender.valueOf(lendingApplication.get().getLender()), LenderAssociationStages.KYC));
            log.info("kyc completed for the application {} ", lendingApplication.get().getId());
        } catch (Exception ex) {
            log.error("exception occurred while processing bre callback request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
//            throw new RuntimeException("unable to ack kyc callback event" + request);
            nbfcUtils.modifyLender(lendingApplication.get(),existingLendingApplicationLenderDetails,LenderAssociationStatus.KYC_CALLBACK_CLIENT_FAILURE);
        }
    }

    public KycRequestApiDto createPayload(Long applicationId) {
        try {
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
            if (!lendingApplication.isPresent()) {
                log.error("application not found !! {}", applicationId);
            }
            CKycResponseDto cKycResponseDto = kycUtils.getKycData(lendingApplication.get().getMerchantId());
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(applicationId,Status.ACTIVE.name(), Lender.ABFL.name());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.info("lending application lender details not found for {}", applicationId);
                throw new RuntimeException("unable to generate kyc payload" + applicationId);
            }
            String currDate = String.valueOf(new Date().getTime());
            String txnId = lendingApplication.get().getId() + currDate.substring(currDate.length() - 5);
            String productCode = LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType()) ? "TopupLoan" : "BharatPe";
            NameAndDobDetailsDto nameAndDobDetailsDto = kycUtils.getNameAndDobValues(cKycResponseDto, lendingApplication.get().getMerchantId());
            String name = nameAndDobDetailsDto.getFullName();
            KycRequestApiDto kycRequestApiDto = KycRequestApiDto.builder()
                    .applicationId(applicationId)
                    .lender(lendingApplication.get().getLender())
                    .productName("LENDING")
                    .topup(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType()))
                    .identifier(KycRequestApiDto.Identifier.builder()
                            .accountId(lendingApplication.get().getExternalLoanId())
                            .cccId(lendingApplicationLenderDetails.getCccId())
                            .productCode(productCode).build())
                    .payload(KycRequestApiDto.Payload.builder()
                            .accountId(lendingApplication.get().getExternalLoanId())
                            .declaredAddress((ObjectUtils.isEmpty(cKycResponseDto.getAddress()) ? "" : cKycResponseDto.getAddress()) + ", " +
                                    (ObjectUtils.isEmpty(cKycResponseDto.getCity()) ? "" : cKycResponseDto.getCity()) + ", " +
                                    (ObjectUtils.isEmpty(cKycResponseDto.getState()) ? "" : cKycResponseDto.getState()) + ", " +
                                    (ObjectUtils.isEmpty(cKycResponseDto.getPincode()) ? "" : cKycResponseDto.getPincode())
                            )
                            .declaredCity(cKycResponseDto.getCity())
                            .kycType("2")
                            .transactionId(txnId)
                            .selfie(cKycResponseDto.getSelfieBase64())
                            .okycXml(cKycResponseDto.getPoAXml())
                            .okycDocType("digixmlaadhaar")
                            .declaredName(converterUtils.parseNameData(name).trim())
                            .declaredDob(nameAndDobDetailsDto.getDob())
                            .declaredPan(cKycResponseDto.getPanNumber())
                            .declaredState(ObjectUtils.isEmpty(cKycResponseDto.getState()) ? "" : cKycResponseDto.getState().toUpperCase())
                            .declaredPincode(Integer.valueOf(cKycResponseDto.getPincode()))
                            .cccId(lendingApplicationLenderDetails.getCccId())
                            .mobile(ObjectUtils.isEmpty(cKycResponseDto.getMobile()) ? "" : cKycResponseDto.getMobile().substring(2))
                            .gender("F".equalsIgnoreCase(cKycResponseDto.getGender()) ? "Female" : "Male")
                            .nsdlName(converterUtils.parseNameData(name).trim())
                            .nsdlPan(cKycResponseDto.getPanNumber())
                            .build())
                    .build();
            log.info("kycRequest payload {}", kycRequestApiDto);
            return kycRequestApiDto;
        } catch (Exception e) {
            log.error("exception occurred while initiating kyc workflow for  {}", applicationId);
        }
        return null;
    }

}
