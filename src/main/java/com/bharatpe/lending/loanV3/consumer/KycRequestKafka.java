package com.bharatpe.lending.loanV3.consumer;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.common.util.ConfigResolver;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.dto.NameAndDobDetailsDto;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.factory.LenderGatewayFactory;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.loanV3.services.INbfcLenderGateway;
import com.bharatpe.lending.loanV3.services.associationsV2.AbflDocGenerateService;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

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
    LenderGatewayFactory lenderGatewayFactory;

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

    @Autowired
    AbflDocGenerateService abflDocGenerateService;

    @Value("${lender.doc.generate.enabled.lenders:}")
    String lenderDocGenerateEnabledLenders;

    @Value("${lender.doc.generate.topup.enabled.lenders:}")
    String lenderDocGenerateTopUpEnabledLenders;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${abfl.eKyc.retry.count:2}")
    Integer abflEKycRetryCount;

    @Value("${abfl.topup.kyc.retry.count:2}")
    Integer abflTopupKycRetryCount;

    @Value("${abfl.kyc.reusability.enabled:false}")
    Boolean abflKycReusabilityEnabled;

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
            Map<String, String> kycRequest = configResolver.getConfig(request, new TypeReference<Map<String, String>>() {
            });
            lendingApplication = lendingApplicationDao.findById(Long.valueOf(kycRequest.get("application_id")));
            if (!lendingApplication.isPresent()) {
                log.info("no application found for id {}", kycRequest.get("application_id"));
            }
            KycRequestApiDto kycRequestDto = createPayload(Long.valueOf(kycRequest.get("application_id")), kycRequest.getOrDefault("poaXml", null));
            lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(kycRequestDto.getApplicationId(), Status.ACTIVE.name(), Lender.ABFL.name());
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
            if (!ObjectUtils.isEmpty(kycApiResponseDto) && kycApiResponseDto.getSuccess()
                    && (!ObjectUtils.isEmpty(kycApiResponseDto.getData()))
                    && lendingApplicationLenderDetails.getAnnualRoi() <= 50) {
                lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.KYC_IN_PROGRESS.name());
                lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                log.info("successfully placed the kyc request at lender for {}", request);
                return;
            }
            if (LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType())) {
                log.info("marking kycStatus KYC_FAILED for topup application as kyc resulted in failure for  {}", lendingApplication.get().getId());
                lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
                lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                return;
            }
            log.info("request resulted in kyc failure, modifying lender for {}", request);
            nbfcUtils.modifyLender(lendingApplication.get(), lendingApplicationLenderDetails, LenderAssociationStatus.KYC_FAILED);
        } catch (Exception ex) {
            log.error("exception occurred while processing kyc request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
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
                    Integer currentKycRetryCount = ObjectUtils.isEmpty(existingLendingApplicationLenderDetails.getKycRetryCount()) ? 0 : existingLendingApplicationLenderDetails.getKycRetryCount();
                    if(currentKycRetryCount < abflTopupKycRetryCount) {
                        log.info("marking kycStatus KYC_retry for topup application as kyc callback resulted in failure for  {}", lendingApplication.get().getId());
                        existingLendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.KYC_RETRY.name());
                        existingLendingApplicationLenderDetails.setKycRetryCount(currentKycRetryCount + 1);
                        lendingApplicationLenderDetailsDao.save(existingLendingApplicationLenderDetails);
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

            boolean generateLenderDoc = "TOPUP".equalsIgnoreCase(lendingApplication.get().getLoanType()) ?
                    lenderDocGenerateTopUpEnabledLenders.contains(lendingApplication.get().getLender()) : lenderDocGenerateEnabledLenders.contains(lendingApplication.get().getLender());
            if (generateLenderDoc) {
                final LendingApplication finalLendingApplication = lendingApplication.get();
                new Thread(() -> abflDocGenerateService.invokeDocGenerate(finalLendingApplication, DocType.LOAN_AGREEMENT, true, false)).start();
            }

            nbfcUtils.pushApplicationToNextStage(lendingApplication.get().getId(),lendingApplication.get().getLender(), LenderAssociationStages.KYC.name(),
                    LenderAssociationStageFactory.autoInvokeNextStage(Lender.valueOf(lendingApplication.get().getLender()), LenderAssociationStages.KYC));
            log.info("kyc completed for the application {} ", lendingApplication.get().getId());
        } catch (Exception ex) {
            log.error("exception occurred while processing kyc callback request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            nbfcUtils.modifyLender(lendingApplication.get(),existingLendingApplicationLenderDetails,LenderAssociationStatus.KYC_CALLBACK_CLIENT_FAILURE);
        }
    }

    public KycRequestApiDto createPayload(Long applicationId, String poaXml) {
        try {
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
            if (!lendingApplication.isPresent()) {
                log.error("application not found !! {}", applicationId);
            }
            CKycResponseDto cKycResponseDto = kycUtils.getKycData(lendingApplication.get().getMerchantId());
            if(kycUtils.isELigibleForLenderKyc(lendingApplication.get().getLender(), lendingApplication.get().getMerchantId()) && ObjectUtils.isEmpty(poaXml)) {
                log.info("poaXml not found for applicationId {}", lendingApplication.get().getId());
                throw new RuntimeException("poaXml not found for application " + lendingApplication.get().getId());
            }
            cKycResponseDto = kycUtils.parsePoaXML(poaXml, lendingApplication.get().getMerchantId(), cKycResponseDto, lendingApplication.get().getId());
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

    public void eKycRequestListener(String request) {
        Optional<LendingApplication> lendingApplication = Optional.empty();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = null;
        try {
            MDC.put("requestId", UUID.randomUUID().toString());
            log.info("Received eKyc request:{}", request);
            Map<String,String> eKycRequest = configResolver.getConfig(request, new TypeReference<Map<String, String>>() {
            });
            lendingApplication = lendingApplicationDao.findById(Long.valueOf(eKycRequest.get("application_id")));
            if (!lendingApplication.isPresent()) {
                log.info("no application found for id {}", eKycRequest.get("application_id"));
            }

            lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.get().getId(),Status.ACTIVE.name(), Lender.ABFL.name());
            if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails) &&
                    (!lendingApplication.get().getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) ||
                    !LenderAssociationStages.KYC.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())) {
                log.info("lender or stage mismatch while initiating eKyc for application {}", lendingApplication.get().getId());
                return;
            }

            if(!kycUtils.isELigibleForLenderKyc(lendingApplication.get().getLender(), lendingApplication.get().getMerchantId())) {
                log.info("skipping digiLocker kyc flow on ABFL for applicationId : {}", lendingApplication.get().getId());
                kycRequestListener(request);
                return;
            }

            if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType())
                    && ObjectUtils.isEmpty(lendingApplicationLenderDetails.getKycStatus())) {
                if(abflKycReusabilityEnabled) {
                    boolean kycValid = invokeKycValidity(lendingApplication.get(), lendingApplicationLenderDetails);
                    if (kycValid) {
                        lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.KYC_COMPLETED.name());
                        LenderAssociationStages nextStage = LenderAssociationStageFactory.getNextStage(Lender.valueOf(lendingApplication.get().getLender()), LenderAssociationStages.KYC);
                        lendingApplicationLenderDetails.setStage(nextStage.name());
                        lendingApplicationLenderDetails.setKycCompletionTimestamp(new Date());
                        lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                        loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.get().getId(), LendingViewStates.ENACH_PAGE);
                        if (lenderDocGenerateTopUpEnabledLenders.contains(lendingApplication.get().getLender())) {
                            final LendingApplication finalLendingApplication = lendingApplication.get();
                            new Thread(() -> abflDocGenerateService.invokeDocGenerate(finalLendingApplication, DocType.LOAN_AGREEMENT, true, false)).start();
                        }
                        nbfcUtils.pushApplicationToNextStage(lendingApplication.get().getId(), lendingApplication.get().getLender(), LenderAssociationStages.KYC.name(),
                                LenderAssociationStageFactory.autoInvokeNextStage(Lender.valueOf(lendingApplication.get().getLender()), LenderAssociationStages.KYC));
                        log.info("kyc completed for the application {} ", lendingApplication.get().getId());
                        return;
                    }
                    log.info("marking kycStatus KYC_RETRY for topup application as kyc validity resulted in failure for  {}", lendingApplication.get().getId());
                }
                lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.KYC_RETRY.name());
                lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.get().getId(), LendingViewStates.KYC_PAGE);
                return;
            }

            EKycRequestApiDto eKycRequestApiDto = EKycRequestApiDto.builder()
                    .applicationId(lendingApplication.get().getId())
                    .lender(lendingApplication.get().getLender())
                    .productName("LENDING")
                    .topup(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType()))
                    .payload(EKycRequestApiDto.Payload.builder()
                            .accountId(lendingApplication.get().getExternalLoanId())
                            .build())
                    .build();

            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
                lendingApplicationLenderDetails.setApplicationId(eKycRequestApiDto.getApplicationId());
                lendingApplicationLenderDetails.setLender(eKycRequestApiDto.getLender());
                lendingApplicationLenderDetails.setStage(LenderAssociationStages.KYC.name());
                lendingApplicationLenderDetails.setStatus(Status.ACTIVE.name());
                lendingApplicationLenderDetails.setAccountId(lendingApplication.get().getExternalLoanId());
                DecimalFormat df = new DecimalFormat("#.##");
                df.setRoundingMode(RoundingMode.DOWN);
                lendingApplicationLenderDetails.setAnnualRoi(Double.valueOf(df.format(
                        lendingApplicationServiceV2.getApr(lendingApplication.get().getMerchantId(), lendingApplication.get().getId(), lendingApplication.get().getLoanAmount(),
                                LenderOffDays.valueOf(lendingApplication.get().getLender()).getEdiModel().getNoOfEdiDaysInAWeek(), lendingApplication.get().getLender()))));
            }
            lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.EKYC_PENDING.name());
            lendingApplicationLenderDetails.setKycMode(LenderAssociationStages.EKYC.name());
            lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStages.EKYC.name());
            lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
            INbfcLenderGateway apiGatewayV3 = lenderGatewayFactory.getLenderApiGateway(eKycRequestApiDto.getLender());
            EKycApiResponseDto eKycApiResponseDto = apiGatewayV3.invokeEKyc(eKycRequestApiDto);
            log.info("eKyc api response {}", eKycApiResponseDto);
            if (!ObjectUtils.isEmpty(eKycApiResponseDto) && eKycApiResponseDto.getSuccess()
                && !ObjectUtils.isEmpty(eKycApiResponseDto.getData())
                && "SUCCESS".equalsIgnoreCase(eKycApiResponseDto.getData().getResponseStatus())
                && !ObjectUtils.isEmpty(eKycApiResponseDto.getData().getData())
                && lendingApplicationLenderDetails.getAnnualRoi() < 50) {
                lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.EKYC_INITIATED.name());
                lendingApplicationLenderDetails.setNbfcKycAsyncId(eKycApiResponseDto.getData().getData().getCaptureLink());
                lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                log.info("successfully placed the eKyc request at lender for {}", request);
                return;
            }
            Integer currentEKycRetryCount = ObjectUtils.isEmpty(lendingApplicationLenderDetails.getKycRetryCount()) ? 0 : lendingApplicationLenderDetails.getKycRetryCount();
            if(currentEKycRetryCount < abflEKycRetryCount) {
                log.info("marking kycStatus EKYC_RETRY for application as eKyc initiation resulted in failure for  {}", lendingApplication.get().getId());
                lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.EKYC_RETRY.name());
                lendingApplicationLenderDetails.setKycRetryCount(currentEKycRetryCount + 1);
                lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                return;
            }
            if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType())) {
                log.info("marking kycStatus KYC_FAILED for topup application as eKyc resulted in failure for  {}", lendingApplication.get().getId());
                lendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
                lendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.EKYC_FAILED.name());
                lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
                return;
            }
            log.info("request resulted in eKyc failure, modifying lender for {}", request);
            nbfcUtils.modifyLender(lendingApplication.get(),lendingApplicationLenderDetails,LenderAssociationStatus.EKYC_FAILED);
        } catch (Exception ex) {
            log.error("exception occurred while processing eKyc request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            nbfcUtils.modifyLender(lendingApplication.get(),lendingApplicationLenderDetails,LenderAssociationStatus.EKYC_FAILED);
        }
    }

    public void eKycCallbackListener(String request) {
        Optional<LendingApplication> lendingApplication = Optional.empty();
        LendingApplicationLenderDetails existingLendingApplicationLenderDetails = null;
        try {
            MDC.put("requestId", UUID.randomUUID().toString());
            log.info("Received eKyc callback request:{}", request);
            EKycCallbackResponseDto eKycCallbackResponseDto = configResolver.getConfig(request, EKycCallbackResponseDto.class);
            if(ObjectUtils.isEmpty(eKycCallbackResponseDto)) {
                log.info("eKyc callback responseDto is incorrect {}", eKycCallbackResponseDto);
                return;
            }
            log.info("eKycCallbackResponseDto payload: {}", eKycCallbackResponseDto);
            lendingApplication = lendingApplicationDao.findById(Long.valueOf(eKycCallbackResponseDto.getApplicationId()));
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("no application found for id {}", eKycCallbackResponseDto.getApplicationId());
                return;
            }
            existingLendingApplicationLenderDetails =
                    lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.get().getId(),Status.ACTIVE.name(), Lender.ABFL.name());
            if (ObjectUtils.isEmpty(existingLendingApplicationLenderDetails) ||
                    !eKycCallbackResponseDto.getLender().equalsIgnoreCase(existingLendingApplicationLenderDetails.getLender())
                    || !Arrays.asList(LenderAssociationStatus.EKYC_IN_PROGRESS.name(), LenderAssociationStatus.EKYC_INITIATED.name()).contains(existingLendingApplicationLenderDetails.getKycStatus())) {
                log.info("lender mismatch while callback ack / callback already received for eKyc in application {}", lendingApplication.get().getId());
                return;
            }
            if (Boolean.FALSE.equals(eKycCallbackResponseDto.getSuccess())
                || ObjectUtils.isEmpty(eKycCallbackResponseDto.getData())
                || (!"SUCCESS".equalsIgnoreCase(eKycCallbackResponseDto.getData().getResponseStatus()) && !(200L == eKycCallbackResponseDto.getData().getStatus()))
                || ObjectUtils.isEmpty(eKycCallbackResponseDto.getData().getData())) {
                Integer currentEKycRetryCount = ObjectUtils.isEmpty(existingLendingApplicationLenderDetails.getKycRetryCount()) ? 0 : existingLendingApplicationLenderDetails.getKycRetryCount();
                if(currentEKycRetryCount < abflEKycRetryCount) {
                    log.info("marking kycStatus EKYC_RETRY for application as eKyc callback resulted in failure for  {}", lendingApplication.get().getId());
                    existingLendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.EKYC_RETRY.name());
                    existingLendingApplicationLenderDetails.setKycRetryCount(currentEKycRetryCount + 1);
                    lendingApplicationLenderDetailsDao.save(existingLendingApplicationLenderDetails);
                    return;
                }
                if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType())) {
                    log.info("marking kycStatus KYC_FAILED for topup application as eKyc callback resulted in failure for  {}", lendingApplication.get().getId());
                    existingLendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
                    existingLendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.EKYC_FAILED.name());
                    lendingApplicationLenderDetailsDao.save(existingLendingApplicationLenderDetails);
                    return;
                }
                log.info("modifying lender as eKyc callback resulted in failure for  {}", lendingApplication.get().getId());
                nbfcUtils.modifyLender(lendingApplication.get(),existingLendingApplicationLenderDetails,LenderAssociationStatus.EKYC_FAILED);
                return;
            }
            String xml = null;
            try {
                InputStream inputStream = URI.create(eKycCallbackResponseDto.getData().getData().getDigixmlaadhaar()).toURL().openConnection().getInputStream();
                xml = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));
            } catch (Exception e) {
                log.info("Exception while fetching aadharXml from digiXmlAadhar url for applicationId {} {}", lendingApplication.get().getId(), Arrays.asList(e.getStackTrace()));
                nbfcUtils.modifyLender(lendingApplication.get(),existingLendingApplicationLenderDetails,LenderAssociationStatus.EKYC_FAILED);
                return;
            }
            existingLendingApplicationLenderDetails.setKycStatus(LenderAssociationStatus.EKYC_COMPLETED.name());
            existingLendingApplicationLenderDetails.setLeadStatus(LenderAssociationStatus.EKYC_COMPLETED.name());
            existingLendingApplicationLenderDetails.setKycRetryCount(0);
            lendingApplicationLenderDetailsDao.save(existingLendingApplicationLenderDetails);
            log.info("eKyc completed for the application {} ", lendingApplication.get().getId());
            Map<String, Object> kycRequest = new HashMap<>();
            kycRequest.put("application_id", lendingApplication.get().getId());
            kycRequest.put("poaXml", xml);
            log.info("invoking kyc request as ekyc completed for applicationId {}", lendingApplication.get().getId());
            kycRequestListener(objectMapper.writeValueAsString(kycRequest));
        } catch (Exception ex) {
            log.error("exception occurred while processing kyc callback request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            nbfcUtils.modifyLender(lendingApplication.get(),existingLendingApplicationLenderDetails,LenderAssociationStatus.EKYC_FAILED);
        }
    }

    public boolean eKycStatusCheck(LendingApplication lendingApplication) {
        try {
            if (ObjectUtils.isEmpty(lendingApplication) ) {
                log.info("request data is not correct {}", lendingApplication);
                return false;
            }
            LendingApplicationLenderDetails existingLendingApplicationLenderDetails =
                    lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), Lender.ABFL.name());
            if (ObjectUtils.isEmpty(existingLendingApplicationLenderDetails)
                    || !Arrays.asList(LenderAssociationStatus.EKYC_IN_PROGRESS.name(), LenderAssociationStatus.EKYC_INITIATED.name()).contains(existingLendingApplicationLenderDetails.getKycStatus())) {
                log.info("Kyc Status is not correct in lender details for eKyc status check for application {}", lendingApplication.getId());
                return false;
            }
            Integer currentEKycRetryCount = ObjectUtils.isEmpty(existingLendingApplicationLenderDetails.getKycRetryCount()) ? 0 : existingLendingApplicationLenderDetails.getKycRetryCount();
            if(currentEKycRetryCount >= abflEKycRetryCount) {
                log.info("skipping status check for last retry of eKyc of {} for {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            EKycStatusCheckRequestApiDto eKycStatusCheckRequestApiDto = EKycStatusCheckRequestApiDto.builder()
                    .applicationId(lendingApplication.getId())
                    .topup(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()))
                    .productName("LENDING")
                    .lender(lendingApplication.getLender())
                    .payload(EKycStatusCheckRequestApiDto.Payload.builder()
                            .accountId(lendingApplication.getExternalLoanId())
                            .build())
                    .build();
            INbfcLenderGateway apiGatewayV3 = lenderGatewayFactory.getLenderApiGateway(eKycStatusCheckRequestApiDto.getLender());
            EKycCallbackResponseDto eKycCallbackResponseDto = apiGatewayV3.invokeEKycStatusCheck(eKycStatusCheckRequestApiDto);
            if(ObjectUtils.isEmpty(eKycCallbackResponseDto)) {
                eKycCallbackResponseDto = EKycCallbackResponseDto.builder()
                        .success(false)
                        .applicationId(String.valueOf(eKycStatusCheckRequestApiDto.getApplicationId()))
                        .lender(eKycStatusCheckRequestApiDto.getLender())
                        .productName(eKycStatusCheckRequestApiDto.getProductName())
                        .build();
            }
            eKycCallbackListener(objectMapper.writeValueAsString(eKycCallbackResponseDto));
            return eKycCallbackResponseDto.getSuccess();
        } catch (Exception ex) {
            log.error("exception occurred while processing eKyc status check request {} {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        return false;
    }

    private boolean invokeKycValidity(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
       try {
           CKycResponseDto cKycResponseDto = kycUtils.getPanData(lendingApplication.getMerchantId());
           KycValidityRequestApiDto payload = KycValidityRequestApiDto.builder()
                   .applicationId(lendingApplication.getId())
                   .topup(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()))
                   .lender(lendingApplication.getLender())
                   .productName("LENDING")
                   .payload(KycValidityRequestApiDto.Payload.builder()
                           .cccId(lendingApplicationLenderDetails.getCccId())
                           .panNo(cKycResponseDto.getPanNumber())
                           .build())
                   .build();
           INbfcLenderGateway apiGatewayV3 = lenderGatewayFactory.getLenderApiGateway(payload.getLender());
           KycValidityApiResponseDto kycValidityApiResponse = apiGatewayV3.invokeKycValidity(payload);
           if(!ObjectUtils.isEmpty(kycValidityApiResponse) && kycValidityApiResponse.getSuccess() && !ObjectUtils.isEmpty(kycValidityApiResponse.getData())) {
              if("SUCCESS".equalsIgnoreCase(kycValidityApiResponse.getData().getResponseStatus()) && kycValidityApiResponse.getData().getData().getKycValidity()) {
                  log.info("Kyc is valid from ABFL's with response {} for applicationId {}", kycValidityApiResponse, lendingApplication.getId());
                  return true;
              }
           }
           log.info("Kyc validity api response {} is false for applicationId {} ", kycValidityApiResponse, lendingApplication.getId());
       } catch (Exception e) {
           log.info("Exception in invoking kyc validity API of ABFL for applicationId {} {}", lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
       }
       return false;
    }
}
