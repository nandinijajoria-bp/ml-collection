package com.bharatpe.lending.service;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.enums.Status;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.common.objects.Meta;
import com.bharatpe.common.utils.NotificationUtil;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.bpnewmaster.dao.DocumentsIdProofDaoMaster;
import com.bharatpe.lending.common.bpnewmaster.entity.DocumentsIdProofMaster;
import com.bharatpe.lending.common.dao.LendingResubmitTaskDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.dto.NotificationPayloadDto;
import com.bharatpe.lending.common.entity.LendingResubmitTask;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.enums.CollectionTransferTypeEnum;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.LendingNotificationService;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.MetaDTO;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.BharatPeOtpHandler;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.handlers.MerchantSummaryExceptionHandler;
import com.bharatpe.lending.loanV2.dto.KycStatusDTO;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Service
public class VerifyOTPService {
    private Logger logger = LoggerFactory.getLogger(VerifyOTPService.class);

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    BharatPeOtpHandler bharatPeOtpHandler;

    @Autowired
    LendingAuditTrialDao lendingAuditTrialDao;

//    @Autowired
//    SmsServiceHandler smsServiceHandler;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

//    @Autowired
//    MerchantSummaryLendingDao merchantSummaryLendingDao;

//	@Autowired
//	MerchantSummaryDao merchantSummaryDao;

//    @Autowired
//    MerchantDao merchantDao;

    @Autowired
    LendingCategoryDao lendingCategoryDao;

    @Autowired
    LendingPrebookLoansDao lendingPrebookLoansDao;

    ExecutorService notificationExecutor = Executors.newFixedThreadPool(10);

    @Autowired
    LendingPrebookTargetDao lendingPrebookTargetDao;

    @Autowired
    LendingCitiesDao lendingCitiesDao;

    @Autowired
    SignAgreementService signAgreementService;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    RedisNotificationService redisNotificationService;

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    EnachHandler enachHandler;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LendingNotificationService lendingNotificationService;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    DocumentsIdProofDaoMaster documentsIdProofDaoMaster;

    @Autowired
    LendingShopDocumentsDao lendingShopDocumentsDao;

    @Autowired
    LendingResubmitTaskDao lendingResubmitTaskDao;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    NotificationUtil notificationUtil;

    @Autowired
    LendingDisbursalStageDao lendingDisbursalStageDao;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    LendingCache lendingCache;

    @Autowired
    MerchantSummaryHandler merchantSummaryHandler;

    @Autowired
    MerchantService merchantService;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Value("${kafka.topic.postChecks:lending_post_application_submission_checks}")
    String kafkaTopicPostChecks;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    SupportService supportService;

    @Autowired
    FunnelService funnelService;

    List<Long> exemptMerchant = Arrays.asList(2411647L, 1210933L, 4340760L, 2097359L, 7090157L, 6518986L, 1141505L, 3L, 3543643L, 9319451L, 8891247L, 2078363L);

    public Map<String, Object> verifyOTP(BasicDetailsDto merchant, CommonAPIRequest commonAPIRequest) {
        if (Objects.nonNull(merchant.getId())) {
            String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + merchant.getId();
            logger.info("deleting cached key of loan details in verifyOtp flow for merchant: {}", merchant.getId());
            lendingCache.delete(loanDetailsCacheKey);
        } else {
            logger.info("merchant id not found in verifyOtp flow");
        }
        Map<String, Object> finalResponse = new LinkedHashMap<>();
        finalResponse.put("success", false);
        finalResponse.put("agreement_verified", false);

        Long applicationId = commonAPIRequest.getPayload().get("application_id") != null ? Long.parseLong(commonAPIRequest.getPayload().get("application_id").toString()) : null;
        String otp = commonAPIRequest.getPayload().get("otp") != null ? commonAPIRequest.getPayload().get("otp").toString() : null;
        String uuid = commonAPIRequest.getPayload().get("uuid") != null ? commonAPIRequest.getPayload().get("uuid").toString() : null;

        if (applicationId == null || applicationId <= 0 || StringUtils.isEmpty(otp)) {
            logger.info("No application found in draft status for given application id {}", applicationId);
            return finalResponse;
        }
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantIdAndStatus(applicationId,
                merchant.getId(), "draft");
        LendingResubmitTask lendingResubmitTask = lendingResubmitTaskDao.findTopByApplicationId(applicationId);
        if (lendingApplication == null && lendingResubmitTask != null && lendingResubmitTask.getDowngrade() != null && lendingResubmitTask.getDowngrade() && (lendingResubmitTask.getDowngradeDone() == null || !lendingResubmitTask.getDowngradeDone())) {
            lendingApplication = lendingApplicationDao.findById(applicationId).get();
            return verifyOTP(otp, uuid, merchant, lendingApplication, commonAPIRequest.getMeta(), lendingResubmitTask);
        }

        // get resigned  after events like lender change
        if (lendingApplication == null && lendingResubmitTask != null && lendingResubmitTask.getResign() != null && lendingResubmitTask.getResign() && (lendingResubmitTask.getResignDone() == null || !lendingResubmitTask.getResignDone())) {
            lendingApplication = lendingApplicationDao.findById(applicationId).get();
            return verifyOTP(otp, uuid, merchant, lendingApplication, commonAPIRequest.getMeta(), lendingResubmitTask);
        }

        if (lendingApplication == null) {
            logger.info("No application found in draft status for given application id {}", applicationId);
            return finalResponse;
        }
        return verifyOTP(otp, uuid, merchant, lendingApplication, commonAPIRequest.getMeta(), null);
    }

    private Map<String, Object> verifyOTP(String otp, String uuid, BasicDetailsDto merchant, LendingApplication lendingApplication, Meta meta, LendingResubmitTask lendingResubmitTask) {
        Map<String, Object> finalResponse = new LinkedHashMap<>();
        logger.info("Mobile length: {}", merchant.getMobile().length());
        finalResponse.put("success", false);
        finalResponse.put("agreement_verified", false);
        if (lendingResubmitTask != null && lendingResubmitTask.getDowngrade() != null && lendingResubmitTask.getDowngrade() && lendingResubmitTask.getDowngradeDone() != null && !lendingResubmitTask.getDowngradeDone() && merchant.getMobile().length() == 12) {
            Boolean isOTPVerified = bharatPeOtpHandler.verifyOtp(merchant, otp, uuid);
            if (isOTPVerified) {
                try{
                    lendingApplicationServiceV2.storeApplicationDocs(lendingApplication.getId(), lendingApplication, merchant);
                }
                catch(Exception e){
                    logger.error("Exception in storing KFS docs for applicationId : {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
                    return finalResponse;
                }

                lendingResubmitTask.setDowngradeDone(Boolean.TRUE);
                lendingResubmitTask.setDowngradedAt(new Date());
                lendingResubmitTaskDao.save(lendingResubmitTask);
                if (LendingConstants.CUSTOM_OFFER_DOWNGRADE.equalsIgnoreCase(lendingApplication.getLmsStage())) {
                    lendingApplication.setLmsStage(lendingResubmitTask.getLmsLastStage());
                } else {
                    lendingApplication.setLmsStage(LendingConstants.PENDING_DISBURSAL);
                }
                lendingApplicationDao.save(lendingApplication);


                // updating ready timestamp on downgrade
                if (LendingConstants.PENDING_DISBURSAL.equalsIgnoreCase(lendingApplication.getLmsStage())) {
                    LendingDisbursalStage lendingDisbursalStage = lendingDisbursalStageDao.findByApplicationId(lendingApplication.getId());
                    if (Objects.isNull(lendingDisbursalStage)) {
                        lendingDisbursalStage = new LendingDisbursalStage();
                        lendingDisbursalStage.setApplicationId(lendingApplication.getId());
                        lendingDisbursalStage.setMerchantId(lendingApplication.getMerchantId());
                    }
                    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
                    String currentDate = dateFormat.format(new Date());
                    lendingDisbursalStage.setReadyStage("YES");
                    lendingDisbursalStage.setReadyTimestamp(currentDate);
                    lendingDisbursalStage.setCallStage("YES");
                    lendingDisbursalStage.setCallTimestamp(currentDate);
                    lendingDisbursalStageDao.save(lendingDisbursalStage);
                }

                finalResponse.put("success", true);
                finalResponse.put("agreement_verified", true);
                return finalResponse;
            }
        }

        if (lendingResubmitTask != null && lendingResubmitTask.getResign() != null  && lendingResubmitTask.getResign() && lendingResubmitTask.getResignDone() != null && !lendingResubmitTask.getResignDone()  && merchant.getMobile().length() == 12) {
            Boolean isOTPVerified = bharatPeOtpHandler.verifyOtp(merchant, otp, uuid);
            if (isOTPVerified) {
                lendingResubmitTask.setResignDone(Boolean.TRUE);
                lendingResubmitTask.setResignTimestamp(new Date());
                lendingResubmitTaskDao.save(lendingResubmitTask);

                lendingApplication.setAgreementAt(new Date());
                lendingApplicationDao.save(lendingApplication);

                final LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
                lendingKfs.setKfsSignedAt(new Date());
                lendingKfs.setSanctionLoanAgreementSignedAt(new Date());
                lendingKfsDao.save(lendingKfs);

                // create agreement according to the new lender
                final ResponseDTO agreement = supportService.createAgreement(lendingApplication.getId());

                if (!agreement.isSuccess()){
                    logger.error("Error in creating agreement for applicationId : {} ", lendingApplication.getId());
                    return finalResponse;
                }

                // upload new agreement with the resigned new lender
                final Boolean uploadLoanAgreement = apiGatewayService.uploadLoanAgreement(lendingApplication.getId());

                if (!uploadLoanAgreement) {
                    logger.error("Error in uploading agreement for applicationId : {} ", lendingApplication.getId());
                    return finalResponse;
                }

                finalResponse.put("success", true);
                finalResponse.put("agreement_verified", true);
                return finalResponse;
            }
        }


        if (merchant.getMobile().length() == 12) {
            Boolean isOTPVerified = bharatPeOtpHandler.verifyOtp(merchant, otp, uuid);
            if (isOTPVerified) {

                finalResponse = updateApplicationStatusAndSuccessSms(merchant, lendingApplication, meta);
                //createPrebookTarget(lendingApplication, merchant);
            }
        }
        return finalResponse;
    }

    private Map<String, Object> updateApplicationStatusAndSuccessSms(BasicDetailsDto merchantBasicDetailsDto,
                                                                      LendingApplication lendingApplication, Meta meta) {
        Map<String, Object> finalResponse = new LinkedHashMap<>();
        List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());
        finalResponse.put("success", false);
        finalResponse.put("agreement_verified", false);
        LendingApplication openApplication = lendingApplicationDao.findOpenApplication(merchantBasicDetailsDto.getId());
        LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.getOldestActiveLoan(merchantBasicDetailsDto.getId());
//        Merchant merchant = merchantDao.getById(merchantBasicDetailsDto.getId());

        Integer repeatLoan = lendingPaymentScheduleDao.getRepeatLoan(merchantBasicDetailsDto.getId());
        if (!topupLoans.contains(lendingApplication.getLoanType()) && (openApplication != null || activeLoan != null)) {
            logger.info("duplicate application for merchant:{} and applicationId:{}", merchantBasicDetailsDto.getId(), lendingApplication.getId());
            lendingApplication.setStatus("deleted");
            lendingApplicationDao.save(lendingApplication);
            notificationExecutor.execute(() -> apiGatewayService.globalLimitTxn(lendingApplication.getMerchantId(), "CREDIT", lendingApplication.getLoanAmount()));
            return finalResponse;
        }
        if ("TOPUP".equalsIgnoreCase(lendingApplication.getLoanType()) || "IO_TOPUP".equalsIgnoreCase(lendingApplication.getLoanType()) || "HALF_TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())) {
            LendingApplication checkDupe = lendingApplicationDao.findOpenApplication(lendingApplication.getMerchantId());
            if (checkDupe != null) {
                logger.info("duplicate application for Topup Loan For MerchantId:{} and applicationId:{}", merchantBasicDetailsDto.getId(), lendingApplication.getId());
                lendingApplication.setStatus("deleted");
                lendingApplicationDao.save(lendingApplication);
                notificationExecutor.execute(() -> apiGatewayService.globalLimitTxn(lendingApplication.getMerchantId(), "CREDIT", lendingApplication.getLoanAmount()));
                return finalResponse;
            }
        }
        if (!topupLoans.contains(lendingApplication.getLoanType()) && StringUtils.isEmpty(lendingApplication.getCkycId())) {
            List<DocumentsIdProofMaster> documentsIdProofList = documentsIdProofDaoMaster.findByMerchantIdAndLendingApplicationId(merchantBasicDetailsDto.getId(), lendingApplication.getId());
            List<LendingShopDocuments> shopDocuments = lendingShopDocumentsDao.findByMerchantIdAndApplicationId(merchantBasicDetailsDto.getId(), lendingApplication.getId());
            if (documentsIdProofList == null || documentsIdProofList.size() == 0 || shopDocuments.isEmpty()) {
                logger.error("documents not found for application:{}", lendingApplication.getId());
                return finalResponse;
            }
        }

        if (!topupLoans.contains(lendingApplication.getLoanType()) && lendingApplication.getProcessingFee() > 0 && apiGatewayService.eligibleForProcessingFee(lendingApplication.getMerchantId())) {
            logger.info("Merchant is BP CLUB member, so making processing fee zero for applicationID:{}", lendingApplication.getId());
            lendingApplication.setDisbursalAmount(lendingApplication.getDisbursalAmount() + lendingApplication.getProcessingFee());
            lendingApplication.setProcessingFee(0D);
        }

        MerchantNachDetailsResponseDTO enachSuccess = enachHandler.findSuccessEnach(merchantBasicDetailsDto.getId(), lendingApplication.getId());
        final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantBasicDetailsDto.getId());
        BankDetailsDto merchantBankDetail = null;
        if (bankDetailsDtoOptional.isPresent())
            merchantBankDetail = bankDetailsDtoOptional.get();
        if (enachSuccess != null && merchantBankDetail != null && enachSuccess.getAccountNumber() != null && !enachSuccess.getAccountNumber().equals(merchantBankDetail.getAccountNumber())) {
            enachSuccess = null;
        }
        logger.info("enach success status: {}", enachSuccess);
        DateFormat df = new SimpleDateFormat("ddMMyy");
        Date dateobj = new Date();
        String loanId = "BPL" + df.format(dateobj) + lendingApplication.getId();
        lendingApplication.setAgreementAt(new Date());
        lendingApplication.setAgreement(1);
        lendingApplication.setIp(meta.getIp());
        if (meta != null && meta.getLatitude() != null && !meta.getLatitude().equalsIgnoreCase("undefined") && !meta.getLatitude().trim().equalsIgnoreCase("")) {
            lendingApplication.setLatitude(meta.getLatitude());
        }
        if (meta != null && !StringUtils.isEmpty(meta.getLongitude()) && !meta.getLongitude().equalsIgnoreCase("undefined") && !meta.getLongitude().equalsIgnoreCase("null") && !meta.getLongitude().trim().equalsIgnoreCase("")) {
            lendingApplication.setLongitude(meta.getLongitude());
        }
        lendingApplication.setExternalLoanId(loanId);

        //skip nach check for topup loans
        if(!"TOPUP".equals(lendingApplication.getLoanType())){
            if (enachSuccess != null) {
                lendingApplication.setNachType("ENACH");
                if(!ObjectUtils.isEmpty(enachSuccess.getNachLender())){
                    lendingApplication.setNachLender(enachSuccess.getNachLender());
                }else{
                    lendingApplication.setNachLender("BHARATPE");
                }
                lendingApplication.setNachReferenceNumber(enachSuccess.getReferenceNumber());
                lendingApplication.setNachStatus("APPROVED");
            }
        }

        if (topupLoans.contains(lendingApplication.getLoanType())) {
            logger.info("TOPUP loan submitted for merchant {}", merchantBasicDetailsDto.getId());
            updateDocuments(lendingApplication, meta,merchantBasicDetailsDto);
            if (!topUpLoans(lendingApplication)) {
                finalResponse.put("message", "Failed to create TopUp application");
                return finalResponse;
            }
        }
        lendingApplication.setStatus("pending_verification");
        lendingApplicationDao.save(lendingApplication);
        funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
                FunnelEnums.StageId.APPLICATION, FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString());

        try{
            lendingApplicationServiceV2.storeApplicationDocs(lendingApplication.getId(), lendingApplication, merchantBasicDetailsDto);
        }
        catch(Exception e){
            logger.error("Exception in storing KFS docs for applicationId : {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            return finalResponse;
        }

        LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
        lendingAuditTrial.setApplicationId(lendingApplication.getId());
        lendingAuditTrial.setMerchantId(merchantBasicDetailsDto.getId());
        lendingAuditTrial.setLoanId(loanId);
        lendingAuditTrial.setUserId(Long.parseLong("0"));
        lendingAuditTrial.setOldStatus("draft");
        lendingAuditTrial.setNewStatus("pending_verification");
        lendingAuditTrial.setType("APP_STATUS");

        lendingAuditTrialDao.save(lendingAuditTrial);

        if (easyLoanUtil.isDummyMerchant(merchantBasicDetailsDto.getId()) || merchantBasicDetailsDto.getId() == 10407700L) {
            // skipping enach for dummy merchant
            lendingApplication.setNachType("ENACH");
            lendingApplication.setNachLender("BHARATPE");
            lendingApplication.setNachStatus("APPROVED");
            lendingApplication.setCkycStatus("APPROVED");
            lendingApplication.setCkycDate(new Date());
            lendingApplicationDao.save(lendingApplication);
        }
        redisNotificationService.sendPendingEnachNotification(merchantBasicDetailsDto, lendingApplication);
        notificationExecutor.submit(() -> sendNotification(merchantBasicDetailsDto, lendingApplication));
        logger.info("Lending application status for application: {}, : {} and ckycId is: {} and ckyc status: {}", lendingApplication.getId(), lendingApplication.getStatus(), lendingApplication.getCkycId(), lendingApplication.getCkycStatus());
        if (!StringUtils.isEmpty(lendingApplication.getCkycId())) {
            logger.info("Checking kyc status for new flow application:{}", lendingApplication.getId());
            updateKycStatus(lendingApplication);
        }
        logger.info("Lending application status after kyc for application: {}, : {} and ckycId is: {} and ckyc status: {}", lendingApplication.getId(), lendingApplication.getStatus(), lendingApplication.getCkycId(), lendingApplication.getCkycStatus());
        sendLatLong(merchantBasicDetailsDto.getId(), lendingApplication.getId());
        if (Objects.nonNull(enachSuccess)) {
            logger.info("entered before sending to topic for post checks");
            sendDetailsForContactsVerification(merchantBasicDetailsDto.getId(), lendingApplication.getId());
        }
        sendDuplicatePancardCheck(merchantBasicDetailsDto.getId(), lendingApplication.getId());
        loanUtil.publishApplicationEvent(lendingApplication);


//		sendPennyDrop(merchant.getId(), lendingApplication.getId());
//		if (repeatLoan == 0 && !topupLoans.contains(lendingApplication.getLoanType())) {
//			if (lendingApplication.getLoanAmount() <= 200000)
//				sendDetailsForKycVerification(merchant.getId(), lendingApplication.getId(), false);
//		}

        finalResponse.put("success", true);
        finalResponse.put("agreement_verified", true);
        return finalResponse;
    }

    private void updateKycStatus(LendingApplication lendingApplication) {
        try {
            KycStatusDTO kycStatus = kycHandler.getKycStatus(lendingApplication.getMerchantId());
            logger.info("kyc status:{} for application:{}", kycStatus, lendingApplication.getId());
            lendingApplication.setCkycStatus(kycStatus.getKycStatus().name());
            lendingApplication.setCkycDate(new Date());
            if (kycStatus.getKycStatus().equals(KycStatus.REJECTED)) {
                lendingApplication.setCkycRejectionReason(kycStatus.getRemarks());
                lendingApplication.setStatus(KycStatus.REJECTED.name().toLowerCase());
                lendingApplicationDao.save(lendingApplication);
            }
            lendingApplicationDao.save(lendingApplication);

        } catch (Exception e) {
            logger.error("Exception in updateKycStatus for application:{}", lendingApplication.getId());
        }
    }

    public void sendLatLong(Long merchantId, Long applicationId) {
        try {
            Map<String, Long> detailMap = new HashMap<String, Long>() {{
                put("merchantId", merchantId);
                put("applicationId", applicationId);
            }};
            kafkaTemplate.send("find_lat_long", merchantId.toString(), detailMap);
            logger.info("Pushed " + detailMap + " to topic find_lat_long");
        } catch (Exception e) {
            logger.error("Error occured while pushing to topic find_lat_long", e);
        }
    }

    private Boolean topUpLoans(LendingApplication lendingApplication) {
        try {
            LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(lendingApplication.getMerchantId(), "ACTIVE");
            LendingRiskVariablesSnapshot lendingRiskVariables = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
            if (Objects.isNull(activeLoan) || (Objects.nonNull(lendingRiskVariables.getFinalOffer()) && !Objects.equals(lendingApplication.getLoanAmount(), lendingRiskVariables.getFinalOffer()))) {
                logger.info("Rejection in topup flow due to offer value mismatch for application: {}",lendingApplication.getId());
                lendingApplication.setStatus("DELETED");
                lendingApplication.setManualCibilReason("OFFER_MISMATCH");
                lendingApplicationDao.save(lendingApplication);
                LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
                lendingAuditTrial.setApplicationId(lendingApplication.getId());
                lendingAuditTrial.setMerchantId(lendingApplication.getMerchantId());
                lendingAuditTrial.setLoanId(lendingApplication.getExternalLoanId());
                lendingAuditTrial.setUserId(Long.parseLong("0"));
                lendingAuditTrial.setOldStatus("draft");
                lendingAuditTrial.setNewStatus("deleted");
                lendingAuditTrial.setType("APP_STATUS");

                lendingAuditTrialDao.save(lendingAuditTrial);
                return false;
            }
//            Integer age = apiGatewayService.getMerchantAge(lendingApplication);
//            if (age > 65 || age < 21) {
//                String ageRejectReason = age > 65 ? "Age_Reject_65" : "Age Reject";
//                logger.info("Rejection in topUp flow due to age restriction check: {} for id: {}",age, lendingApplication.getId());
//                lendingApplication.setStatus("DELETED");
//                lendingApplication.setManualCibilReason(ageRejectReason);
//                lendingApplicationDao.save(lendingApplication);
//                LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
//                lendingAuditTrial.setApplicationId(lendingApplication.getId());
//                lendingAuditTrial.setMerchantId(lendingApplication.getMerchantId());
//                lendingAuditTrial.setLoanId(lendingApplication.getExternalLoanId());
//                lendingAuditTrial.setUserId(Long.parseLong("0"));
//                lendingAuditTrial.setOldStatus("draft");
//                lendingAuditTrial.setNewStatus("deleted");
//                lendingAuditTrial.setType("APP_STATUS");
//
//                lendingAuditTrialDao.save(lendingAuditTrial);
//                return false;
//            }
            double previousAmount = loanUtil.getForeclosureAmount(activeLoan);
            LendingLedger lendingLedger = new LendingLedger();
            lendingLedger.setMerchantId(activeLoan.getMerchantId());
            lendingLedger.setLendingPaymentSchedule(activeLoan);
            lendingLedger.setTxnType("EDI");
            lendingLedger.setAmount(previousAmount);
            lendingLedger.setDate(new Date());
            lendingLedger.setDescription("TOPUP LOAN ADJUSTMENT");
            lendingLedger.setPrinciple(previousAmount - activeLoan.getDueInterest());
            lendingLedger.setInterest(activeLoan.getDueInterest());
            lendingLedger.setAdjustmentMode(lendingApplication.getLoanType());
            lendingLedger.setTransferType(CollectionTransferTypeEnum.DIRECT_TRANSFER_LENDER.name());
            lendingLedgerDao.save(lendingLedger);

            LendingLedger negativeEntry = new LendingLedger();
            negativeEntry.setMerchantId(activeLoan.getMerchantId());
            negativeEntry.setLendingPaymentSchedule(activeLoan);
            negativeEntry.setTxnType("EDI");
            negativeEntry.setAmount(-(previousAmount - activeLoan.getDueAmount()));
            negativeEntry.setDate(new Date());
            negativeEntry.setDescription("TOPUP LOAN ADJUSTMENT");
            negativeEntry.setPrinciple(-(previousAmount - activeLoan.getDueAmount()));
            negativeEntry.setInterest(0D);
            negativeEntry.setAdjustmentMode(lendingApplication.getLoanType());
            negativeEntry.setTransferType(CollectionTransferTypeEnum.DIRECT_TRANSFER_LENDER.name());
            lendingLedgerDao.save(negativeEntry);

            activeLoan.setStatus("CLOSED");
            activeLoan.setClosingDate(new Date());
            activeLoan.setPaidAmount(activeLoan.getPaidAmount() + previousAmount);
            activeLoan.setPaidPrinciple(activeLoan.getPaidPrinciple() + previousAmount - activeLoan.getDueInterest());
            activeLoan.setPaidInterest(activeLoan.getPaidInterest() + activeLoan.getDueInterest());
            activeLoan.setDueAmount(0D);
            activeLoan.setDuePrinciple(0D);
            activeLoan.setDueInterest(0D);
            lendingPaymentScheduleDao.save(activeLoan);

            lendingApplication.setDisbursalAmount(lendingApplication.getLoanAmount() - previousAmount - lendingApplication.getProcessingFee());
            if (LoanType.IO_TOPUP.name().equals(lendingApplication.getLoanType())) {
                lendingApplication.setLender("LIQUILOANS_NBFC");
            }
            lendingApplicationDao.save(lendingApplication);
            if ("TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())) {
                notificationExecutor.execute(() -> apiGatewayService.globalLimitTxn(lendingApplication.getMerchantId(), "CREDIT", previousAmount));
            }

        } catch (Exception ex) {
            logger.error("Exception IN TOPUP LOANS Ledger for application:{}", lendingApplication.getId(), ex);
        }

        return true;
    }

    public void sendPennyDrop(Long merchantId, Long applicationId) {
        try {
            Map<String, Long> detailMap = new HashMap<String, Long>() {{
                put("merchantId", merchantId);
                put("applicationId", applicationId);
            }};
            kafkaTemplate.send("check_pennydrop", merchantId.toString(), detailMap);
            logger.info("Pushed " + detailMap + " to topic check_pennydrop");
        } catch (Exception e) {
            logger.error("Error occured while pushing to topic check_pennydrop", e);
        }
    }

    public void sendDetailsForKycVerification(Long merchantId, Long applicationId, boolean isCreditLine) {
        if (exemptMerchant.contains(merchantId)) {
            return;
        }
        try {
            Map<String, Long> detailMap = new HashMap<String, Long>() {{
                put("merchantId", merchantId);
                put("applicationId", applicationId);
                put("isCreditLine", isCreditLine ? 1L : 0L);
            }};
            kafkaTemplate.send("auto_kyc", merchantId.toString(), detailMap);
            logger.info("Pushed " + detailMap + " to topic auto_kyc");
        } catch (Exception e) {
            logger.error("Error occured while pushing to toipc auto_kyc", e);
        }
    }

    public void sendDetailsForContactsVerification(Long merchantId, Long applicationId) {
//		if (exemptMerchant.contains(merchantId)) {
//			return;
//		}
        try {
            Map<String, Long> detailMap = new HashMap<>();
            detailMap.put("merchantId", merchantId);
            detailMap.put("applicationId", applicationId);
            kafkaTemplate.send(kafkaTopicPostChecks, merchantId.toString(), detailMap);
            logger.info("Pushed {} to topic verify_contacts_for_application", detailMap);
        } catch (Exception e) {
            logger.error("Error occured while pushing to topic verify_contacts_for_application", e);
        }
    }

    private void updateDocuments(LendingApplication lendingApplication, Meta meta, BasicDetailsDto merchantBasicDetailsDto) {
        try {
            List<LendingPaymentSchedule> lendingPaymentScheduleList = lendingPaymentScheduleDao.findByMerchantIdAndCreditLoanOrderByIdDesc(lendingApplication.getMerchantId(), false);
            LendingPaymentSchedule activeLoan = getActiveLoan(lendingPaymentScheduleList);
            if (lendingPaymentScheduleList == null || lendingPaymentScheduleList.isEmpty() || activeLoan == null || activeLoan.getLoanAmount() <= 5000) {
                logger.info("No previous loan/active loan for merchant ID {}", lendingApplication.getMerchantId());
                return;
            }
            LendingApplication prevApplication =
                    lendingApplicationDao.findByIdAndMerchantId(activeLoan.getApplicationId(),
                            lendingApplication.getMerchantId());
            signAgreementService.replicateDocumentsForNewApplication(prevApplication, lendingApplication, merchantBasicDetailsDto, new MetaDTO(meta));
        } catch (Exception e) {
            logger.error("Exception replicating documents for topup---", e);
        }
    }

//    private void sendTopupSms(Merchant merchant, LendingApplication lendingApplication) {
//        try {
//            MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
//            if (merchantBankDetail == null) {
//                return;
//            }
//            Long docId = documentsIdProofdao.fetchLatestAddressProofDocId(merchant.getId(), lendingApplication.getId(), "LENDING");
//            String proof = "";
//            if (docId != null) {
//                Optional<DocumentsIdProof> documentsIdProof = documentsIdProofdao.findById(docId);
//                if (documentsIdProof.isPresent()) {
//                    proof = documentsIdProof.get().getProofType();
//                }
//            }
//            String sms = "Hi " + merchantBankDetail.getBeneficiaryName() + ",a BharatPe agent will visit you in 72 hrs. to collect the following:\n- PAN\n- Address Proof (" + proof + ")\n- Cheque (" + merchantBankDetail.getIfscCode() + ", " + merchantBankDetail.getAccountNumber() + ")\n- Shop Ownership Doc\n- Business Ownership Proof";
//            smsServiceHandler.sendSMS(new ArrayList<String>() {{
//                add(merchant.getMobile());
//            }}, sms, NotificationProvider.SMS.GUPSHUP);
//        } catch (Exception e) {
//            logger.error("Exception while sending topup sms---", e);
//        }
//    }

//    private void checkPreBook(BasicDetailsDto merchantBasicDetails, LendingApplication lendingApplication) {
//        LendingPrebookLoans lendingPrebookLoans = lendingPrebookLoansDao.findByMerchantId(merchantBasicDetails.getId());
//        if (lendingPrebookLoans != null) {
//            logger.info("Prebook loan already exists for merchant: {}", merchantBasicDetails.getId());
//            notificationExecutor.submit(() -> sendNotification(merchantBasicDetails, lendingApplication));
//            return;
//        }
//        MerchantSummaryLending merchantSummaryLending = merchantSummaryLendingDao.findByMerchantId(merchantBasicDetails.getId());
////		MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchantBasicDetails.getId());
//        MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(merchantBasicDetails.getId());
//        if (ObjectUtils.isEmpty(merchantResponseDTO)) {
//            throw new MerchantSummaryExceptionHandler(merchantBasicDetails.getId().toString());
//        }
//        LendingCategories lendingCategories = lendingCategoryDao.getByCategory(lendingApplication.getCategory());
//        List<String> preBookCategories = Arrays.asList("Grocery", "Medical", "Dairy");
//        List<String> etcCategories = Arrays.asList("S1LG", "S1DG", "S2LG", "S2DG");
//        List<String> cities = Arrays.asList("Bangalore", "Hyderabad", "Pune", "Delhi");
////        Merchant merchant = merchantDao.getById(merchantBasicDetails.getId());
//        if (preBookCategories.contains(merchantBasicDetails.getBussinessCategory()) && merchantSummaryLending != null && merchantSummaryLending.getSegment().equalsIgnoreCase("2") && merchantResponseDTO.getBpScore() > 10 && lendingCategories.getMasterCategory() != null && etcCategories.contains(lendingCategories.getMasterCategory()) && cities.contains(lendingApplication.getCity())) {
//            Calendar c = Calendar.getInstance();
//            c.setTime(lendingApplication.getAgreementAt());
//            c.add(Calendar.DATE, -9);
//            Date startDate = c.getTime();
//            List<Object[]> transactions = paymentTransactionNewDaoSlave.getCountForPreBook(merchantBasicDetails.getId(), startDate, lendingApplication.getAgreementAt());
//            if (transactions != null && transactions.size() >= 8) {
//                Double previousLoanAmount = lendingApplication.getLoanAmount();
//                AvailableLoan availableLoan = new AvailableLoan();
//                availableLoan.setAmount(100000D);
//                LoanCalculationUtil.LoanBreakupDetail breakup = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategories, null);
//                lendingApplication.setEdi(Double.valueOf(breakup.getEdi()));
//                lendingApplication.setIoEdi(Double.valueOf(breakup.getIoEdi()));
//                lendingApplication.setRepayment(Double.valueOf(breakup.getRepayment()));
//                lendingApplication.setDisbursalAmount((double) breakup.getLoanAmount());
//                lendingApplication.setLoanAmount((double) breakup.getLoanAmount());
//                lendingApplicationDao.save(lendingApplication);
//                lendingPrebookLoansDao.save(new LendingPrebookLoans(merchantBasicDetails.getId(), lendingApplication.getId(), previousLoanAmount));
//                logger.info("Updated loan amount to 100000 for merchant: {} with applicationId: {}", merchantBasicDetails.getId(), lendingApplication.getId());
//            }
//        }
//        notificationExecutor.submit(() -> sendNotification(merchantBasicDetails, lendingApplication));
//    }

    private void sendNotification(BasicDetailsDto merchant, LendingApplication lendingApplication) {

        final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchant.getId());
        BankDetailsDto merchantBankDetail = null;
        if (bankDetailsDtoOptional.isPresent())
            merchantBankDetail = bankDetailsDtoOptional.get();
        if (merchantBankDetail == null) {
            return;
        }


        List<String> mobiles = new ArrayList<>();
        mobiles.add(merchant.getMobile());
        Double loanAmount = lendingApplication.getLoanAmount();
        String identifier;

        String deeplink = notificationUtil.getDeeplink(merchant.getSettlementType(), "LOAN_DASHBOARD");
        Map<String, Object> templateParams = new HashMap<>();
        NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();

        if (Objects.nonNull(lendingApplication.getNachStatus())) {
            identifier = "LENDING_NEW_APPLICATION_RECEIVED_PUSH";
            templateParams.put("expected_days", "3-5");
            notificationPayloadDto.setTemplateIdentifier(identifier);
            notificationPayloadDto.setTemplateParams(templateParams);
            notificationPayloadDto.setMobile(merchant.getMobile());
            notificationPayloadDto.setPushDeepLink(deeplink);
            notificationPayloadDto.setPushTitle("Loan Application " + lendingApplication.getExternalLoanId() + " Under Review!");
            notificationPayloadDto.setClientName("LENDING");
            lendingNotificationService.notify(notificationPayloadDto);
        } else {
            identifier = "LENDING_NEW_APPLICATION_RECEIVED_2_PUSH";
            templateParams.put("loan_amount", loanAmount);
            notificationPayloadDto.setTemplateIdentifier(identifier);
            notificationPayloadDto.setTemplateParams(templateParams);
            notificationPayloadDto.setMobile(merchant.getMobile());
            notificationPayloadDto.setPushDeepLink(deeplink);
            notificationPayloadDto.setPushTitle("You are one step away from Loan Transfer!");
            notificationPayloadDto.setClientName("LENDING");
            lendingNotificationService.notify(notificationPayloadDto);
        }

        identifier = "LENDING_AGENT_SMS";
        notificationPayloadDto.setTemplateIdentifier(identifier);
        lendingNotificationService.notify(notificationPayloadDto);
    }

//    private boolean isPaymentBank(Merchant merchant, MerchantBankDetail merchantBankDetail) {
//        try {
//            if (merchantBankDetail == null) {
//                logger.error("No merchnat bank detail found for merchant id {}", merchant.getId());
//                return true;
//            }
//
//            if (StringUtils.isEmpty(merchantBankDetail.getIfscCode())) {
//                logger.error("IFSC is empty for merchant bank detail id {} and merchant ID {}", merchantBankDetail.getId(), merchant.getId());
//                return true;
//            }
//
//            List<BankList> nonPaymentBankList = bankListDao.fetchNonPaymentBankList(merchantBankDetail.getIfscCode().substring(0, 4));
//
//            if (nonPaymentBankList == null || nonPaymentBankList.size() == 0) {
//                return false;
//            } else {
//                logger.info("IFSC {} is of Payment bank, returning true", merchantBankDetail.getIfscCode());
//                return true;
//            }
//        } catch (Exception ex) {
//            logger.error("Exception while checking if merchant's bank is payment bank with merchant id {}, Exception is {}", merchant.getId(), ex);
//        }
//        return true;
//    }

    private LendingPaymentSchedule getActiveLoan(List<LendingPaymentSchedule> lendingPaymentScheduleList) {
        if (lendingPaymentScheduleList == null || lendingPaymentScheduleList.size() == 0) {
            return null;
        }
        for (LendingPaymentSchedule schedule : lendingPaymentScheduleList) {
            if (Status.LendingStatus.ACTIVE.toString().equals(schedule.getStatus())) {
                return schedule;
            }
        }
        return null;
    }

    public void sendDuplicatePancardCheck(Long merchantId, Long applicationId) {
        try {
            Map<String, Long> detailMap = new HashMap<String, Long>() {{
                put("merchantId", merchantId);
                put("applicationId", applicationId);
            }};
            kafkaTemplate.send("check_duplicate_pancard", merchantId.toString(), detailMap);
            logger.info("Pushed " + detailMap + " to topic check_duplicate_pancard");
        } catch (Exception e) {
            logger.error("Error occured while pushing to topic check_duplicate_pancard", e);
        }
    }
}
