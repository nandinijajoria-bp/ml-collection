package com.bharatpe.lending.service;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.RejectionStage;
import com.bharatpe.common.enums.Status;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.common.objects.Meta;
import com.bharatpe.lending.collection.core.dto.internal.LoanPaymentDetailDTO;
import com.bharatpe.lending.collection.core.service.MandateCancellationService;
import com.bharatpe.lending.collection.core.service.impl.LoanPaymentServiceImpl;
import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.common.bpnewmaster.dao.DocumentsIdProofDaoMaster;
import com.bharatpe.lending.common.bpnewmaster.entity.DocumentsIdProofMaster;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.CollectionTransferTypeEnum;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.enums.MandateType;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.NBFCService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.enums.*;
import com.bharatpe.lending.handlers.BharatPeOtpHandler;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.lendingplatform.lms.dto.response.LoanDetailsResponse;
import com.bharatpe.lending.lendingplatform.lms.service.ForeclosureService;
import com.bharatpe.lending.lendingplatform.lms.service.LmsLoanDetailsService;
import com.bharatpe.lending.lendingplatform.lms.service.PaymentAsynchronousService;
import com.bharatpe.lending.loanV2.dto.KycStatusDTO;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.NachStatus;
import com.bharatpe.lending.loanV3.revamp.scopes.KfsStageHelper;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.loanV3.revamp.util.LoanUtilV3;
import com.bharatpe.lending.loanV3.services.VKycService;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.bharatpe.lending.constant.RejectionReasons.KYC_REJECTED;
import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.ONE_LMS;


@Service
public class VerifyOTPService {
    private Logger logger = LoggerFactory.getLogger(VerifyOTPService.class);

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    BharatPeOtpHandler bharatPeOtpHandler;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    NBFCService nbfcService;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    DocumentsIdProofDaoMaster documentsIdProofDaoMaster;

    @Autowired
    LendingShopDocumentsDao lendingShopDocumentsDao;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingCache lendingCache;

    @Autowired
    @Lazy
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    FunnelService funnelService;

    @Autowired
    LendingCollectionAuditService lendingCollectionAuditService;

    @Autowired
    LendingCollectionExcessDao lendingCollectionExcessDao;

    @Autowired
    LendingRefundAuditDao lendingRefundAuditDao;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    LoanDetailsV3Service loanDetailsV3Service;

    PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Autowired
    CleverTapEventService cleverTapEventService;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Lazy
    @Autowired
    KycUtils kycUtils;

    @Lazy
    @Autowired
    LoanPaymentServiceImpl loanPaymentService;

    @Autowired
    VKycService vkycService;

    @Autowired
    private ForeclosureService foreclosureService;

    @Autowired
    private PaymentAsynchronousService paymentAsynchronousService;

    @Autowired
    private LmsLoanDetailsService lmsLoanDetailsService;

    @Autowired
    AutoPayUPIDao autoPayUPIDao;

    @Autowired
    MandateCancellationService mandateCancellationService;

    @Autowired
    PostAgreementAsyncFlowService postAgreementAsyncFlowService;

    @Autowired
    private KfsStageHelper kfsStageHelper;

    @Value("${constant.pf.for.topup.enabled.lender:}")
    String constantProcessingFeeForTopupEnabledLender;

    private final List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());

    public Map<String, Object> verifyOTP(BasicDetailsDto merchant, CommonAPIRequest commonAPIRequest) {

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("agreement_verified", false);
        response.put("message", "Issue in verifying otp , please try again.");

        Map<String, Object> payload = commonAPIRequest.getPayload();
        Long applicationId = Optional.ofNullable(payload.get("application_id"))
                .map(Object::toString)
                .map(Long::valueOf)
                .orElse(null);

        String otp = Optional.ofNullable(payload.get("otp"))
                .map(Object::toString)
                .orElse(null);

        String uuid = Optional.ofNullable(payload.get("uuid"))
                .map(Object::toString)
                .orElse(null);

        if (ObjectUtils.isEmpty(applicationId) || StringUtils.isEmpty(otp)) {
            logger.info("Invalid applicationId {} or OTP {}", applicationId, otp);
            return response;
        }

        if (merchant.getMobile().length() != 12) {
            logger.info("Invalid mobile length {} for merchant {}", merchant.getMobile().length(), merchant.getId());
            return response;
        }

        // ---------------- Fetch application ----------------
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantIdAndStatus(applicationId, merchant.getId(), ApplicationStatus.DRAFT.name().toLowerCase());
        if (ObjectUtils.isEmpty(lendingApplication)) {
            logger.info("No draft application found for id {}", applicationId);
            return response;
        }

        // ---------------- Cache clear ----------------
        String merchantId = merchant.getId().toString();
        logger.info("Clearing loan cache for merchant {}", merchantId);
        lendingCache.delete("LENDING_LOAN_DETAILS_" + merchantId);
        lendingCache.delete(LoanDetailsConstant.LENDING_DASHBOARD_DETAILS_V3_KEY_PREFIX + merchantId);

        // ---------------- OTP Verification ----------------
        Boolean otpVerified = bharatPeOtpHandler.verifyOtp(merchant, otp, uuid);
        if (Boolean.FALSE.equals(otpVerified)) {
            logger.info("otp verification failed for merchantId {} and applicationId {}", merchant.getId(), applicationId);
            return response;
        }

        // ---------------- Special case: PAYU OTP audit ----------------
        if (Lender.PAYU.name().equalsIgnoreCase(lendingApplication.getLender())) {
            LendingApplicationLenderDetails lenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
            lenderDetails.setAgreementOtp(otp);
            lendingApplicationLenderDetailsDao.save(lenderDetails);
        }

        // ---------------- Update application & send SMS ----------------
        return updateApplicationStatusPostVerifyOtp(merchant, lendingApplication, commonAPIRequest.getMeta());
    }

    private void updateKycStatus(LendingApplication lendingApplication) {
        try {
            KycStatusDTO kycStatus = kycUtils.isEligibleForSkipKycOrLenderKyc(lendingApplication)
                    ? kycHandler.getKycStatusForLenderKycOrSkipKycPipe(lendingApplication.getMerchantId())
                    : kycHandler.getKycStatus(lendingApplication.getMerchantId());

            logger.info("KYC status: {} for application: {}", kycStatus, lendingApplication.getId());

            lendingApplication.setCkycStatus(kycStatus.getKycStatus().name());
            lendingApplication.setCkycDate(new Date());

            if (KycStatus.REJECTED.equals(kycStatus.getKycStatus())) {
                logger.info("Rejecting application as KYC is REJECTED for applicationId {}", lendingApplication.getId());
                lendingApplication.setCkycRejectionReason(kycStatus.getRemarks());
                lendingApplication.setStatus(KycStatus.REJECTED.name().toLowerCase());
                lendingApplication.setRejectionReason(KYC_REJECTED);
                lendingApplication.setRejectionStage(RejectionStage.KYC);
            }

            lendingApplicationDao.save(lendingApplication);

        } catch (Exception e) {
            logger.error("Exception in updateKycStatus for applicationId {}: {}", lendingApplication.getId(), e.getMessage(), e);
        }
    }

    private boolean updateDisbursalAmountAndProcessingFeeDetailsForTopUpLoans(LendingApplication lendingApplication, LendingPaymentSchedule activeLoan) {
        try {
            double previousAmount = loanUtil.getForeClosureAmountForLender(activeLoan);
            if (previousAmount <= 0) {
                throw new RuntimeException("Error fetching foreclosure amount of lender " + activeLoan.getNbfc() + " for applicationId " + activeLoan.getApplicationId());
            }
            //Updating disbursal amount and pf according to foreclosure amount for top-up loan
            double originalDisbursalWithoutPF = lendingApplication.getDisbursalAmount() + lendingApplication.getProcessingFee();
            double processingFeeRate = lendingApplication.getProcessingFee() / originalDisbursalWithoutPF;
            double newDisbursalWithoutPF = lendingApplication.getLoanAmount() - previousAmount;
            double updatedProcessingFee = Math.ceil(newDisbursalWithoutPF * processingFeeRate);
            boolean skipPfUpdate = constantProcessingFeeForTopupEnabledLender.contains(lendingApplication.getLender()) &&
                    !LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(activeLoan.getNbfc());
            if (Boolean.FALSE.equals(skipPfUpdate)) {
                logger.info("Updating processing fee for application {} from {} to {}",
                        lendingApplication.getId(), lendingApplication.getProcessingFee(), updatedProcessingFee);
                lendingApplication.setProcessingFee(updatedProcessingFee);
            }
            logger.info("Updating disbursal amount for application {} from {} to {}", lendingApplication.getId(), lendingApplication.getDisbursalAmount(),
                    newDisbursalWithoutPF - lendingApplication.getProcessingFee());
            lendingApplication.setDisbursalAmount(newDisbursalWithoutPF - lendingApplication.getProcessingFee());
            lendingApplicationDao.save(lendingApplication);
            return true;

        } catch (Exception ex) {
            logger.error("Exception in updating disbursal and processing fee details for application {}: {}", lendingApplication.getId(), ex.getMessage(), ex);
            return false;
        }
    }

    public void ledgerAdjustmentForTopup(LendingPaymentSchedule previousLoan, LendingApplication lendingApplication, double previousAmount) {

        if(LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(previousLoan.getNbfc())) {
            logger.info("NewSettlement# started the settlement of order : {} loanId :{}", "EXCESS", previousLoan.getId());
            loanPaymentService.adjustMoney(previousLoan, LoanPaymentDetailDTO.builder()
                    .adjustExcessNach(true)
                    .otherAmount(0)
                    .source("EXCESS_NACH_ADJUSTED")
                    .transferType(CollectionTransferTypeEnum.DIRECT_TRANSFER_LENDER.name())
                    .build());
            logger.info("NewSettlement# completed the settlement of order : {} loanId :{}", "NACH", previousLoan.getId());
        }

        double duePenalty = Objects.nonNull(previousLoan.getDuePenalty()) ? previousLoan.getDuePenalty() : 0;
        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setMerchantId(previousLoan.getMerchantId());
        lendingLedger.setLendingPaymentSchedule(previousLoan);
        lendingLedger.setTxnType("EDI");
        lendingLedger.setAmount(previousAmount);
        lendingLedger.setDate(new Date());
        lendingLedger.setDescription("TOPUP LOAN ADJUSTMENT");
        lendingLedger.setPenalty(duePenalty);
        lendingLedger.setPrinciple(previousAmount - previousLoan.getDueInterest() - duePenalty);
        lendingLedger.setInterest(previousLoan.getDueInterest());
        lendingLedger.setAdjustmentMode(lendingApplication.getLoanType());
        lendingLedger.setTransferType(CollectionTransferTypeEnum.DIRECT_TRANSFER_LENDER.name());
        lendingLedgerDao.save(lendingLedger);

        LendingLedger negativeEntry = new LendingLedger();
        negativeEntry.setMerchantId(previousLoan.getMerchantId());
        negativeEntry.setLendingPaymentSchedule(previousLoan);
        negativeEntry.setTxnType("EDI");
        negativeEntry.setAmount(-(previousAmount - previousLoan.getDueAmount()));
        negativeEntry.setDate(new Date());
        negativeEntry.setDescription("TOPUP LOAN ADJUSTMENT");
        negativeEntry.setPrinciple(-(previousAmount - previousLoan.getDueAmount()));
        negativeEntry.setInterest(0D);
        negativeEntry.setAdjustmentMode(lendingApplication.getLoanType());
        negativeEntry.setTransferType(CollectionTransferTypeEnum.DIRECT_TRANSFER_LENDER.name());
        lendingLedgerDao.save(negativeEntry);

        previousLoan.setStatus("CLOSED");
        previousLoan.setClosingDate(new Date());
        previousLoan.setPaidAmount(previousLoan.getPaidAmount() + previousAmount - duePenalty);
        previousLoan.setPaidPrinciple(previousLoan.getPaidPrinciple() + previousAmount - previousLoan.getDueInterest() - duePenalty);
        previousLoan.setPaidInterest(previousLoan.getPaidInterest() + previousLoan.getDueInterest());
        previousLoan.setDueAmount(0D);
        previousLoan.setDuePrinciple(0D);
        previousLoan.setDueInterest(0D);
        previousLoan.setDuePenalty(0D);
        previousLoan.setPaidPenalty(ObjectUtils.isEmpty(previousLoan.getPaidPenalty()) ? 0D : previousLoan.getPaidPenalty()  + duePenalty);
        lendingPaymentScheduleDao.save(previousLoan);

        if (duePenalty > 0) {
            PenaltyFeeLedger penaltyFeeLedger = new PenaltyFeeLedger(previousLoan.getMerchantId(), previousLoan.getId(),
                    duePenalty, "TOPUP LOAN ADJUSTMENT", false, previousLoan.getNbfc());
            penaltyFeeLedgerDao.save(penaltyFeeLedger);
        }

        if (previousLoan.getStatus().equalsIgnoreCase(Status.LendingStatus.CLOSED.toString())) {
            if ("LDC".equals(previousLoan.getLoanApplication().getLender())) {
                nbfcService.pushCloseLoanEventToKafka(previousLoan.getApplicationId());
            }
            if(!LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(previousLoan.getNbfc())) {
                putCollectionExcessAmountInRefund(previousLoan);
            }
        }

        lendingCollectionAuditService.sendCollectionAudit(lendingLedger, previousLoan);
    }

    private void putCollectionExcessAmountInRefund(LendingPaymentSchedule lendingPaymentSchedule) {
        logger.info("putting excess amount in refund for loanId : {}", lendingPaymentSchedule.getId());
        try {
            List<LendingCollectionExcess> lendingCollectionExcessList = lendingCollectionExcessDao.findByMerchantIdAndLoanIdAndStatusOrderByIdAsc(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId(), "ACTIVE");
            for (LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList) {
                if (lendingCollectionExcess.getAmount() > 0) {
                    LendingRefundAudit lendingRefundAudit = new LendingRefundAudit();
                    lendingRefundAudit.setDueAmount(0d);
                    lendingRefundAudit.setLoanId(lendingPaymentSchedule.getId());
                    lendingRefundAudit.setMerchantId(lendingPaymentSchedule.getMerchantId());
                    lendingRefundAudit.setMode("EXCESS_NACH");
                    lendingRefundAudit.setBankRefNo(lendingCollectionExcess.getTerminalOrderId());
                    lendingRefundAudit.setRefundAmount(lendingCollectionExcess.getAmount());
                    lendingRefundAuditDao.save(lendingRefundAudit);
                }
                lendingCollectionExcess.setStatus("CLOSED_REFUND_TOPUP");
                lendingCollectionExcessDao.save(lendingCollectionExcess);

            }
        } catch (Exception e) {
            logger.error("Error occurred while putting leftover excess collection amount in refund for loanId : {} {} {}" , lendingPaymentSchedule.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    public Map<String, Object> closePreviousLoanAfterSuccessfulTopupCreation(Long applicationId) {

        logger.info("in close previous Loan flow {}",applicationId);
        Map<String, Object> finalResponse = new LinkedHashMap<>();
        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);

        if (!lendingApplicationOptional.isPresent()) {
            finalResponse.put("message", "lending application not found");
            return finalResponse;
        }

        if (!"TOPUP".equalsIgnoreCase(lendingApplicationOptional.get().getLoanType())) {
            finalResponse.put("message", "loantype for application is not topup");
            return finalResponse;
        }

        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplicationOptional.get().getId());
        if (ObjectUtils.isEmpty(lendingApplicationDetails)) {
            finalResponse.put("message", "lending application details not found");
            return finalResponse;
        }

        logger.info("pervious application id : {} for applicationId : {}", lendingApplicationDetails.getPrevAppId(), applicationId);

        // fetch previous lending application on which topup is created
        Optional<LendingApplication> previousLendingApplicationOptional = lendingApplicationDao.findById(lendingApplicationDetails.getPrevAppId());


        if (!previousLendingApplicationOptional.isPresent()) {
            finalResponse.put("message", "previous lending application not found");
            return finalResponse;
        }

        LendingPaymentSchedule previousLoan = lendingPaymentScheduleDao.findByApplicationId(previousLendingApplicationOptional.get().getId());

        if ("CLOSED".equalsIgnoreCase(previousLoan.getStatus())) {
            finalResponse.put("message", "previous loan is already closed");
            return finalResponse;
        }

        double previousAmount = lendingApplicationOptional.get().getLoanAmount()
                                - lendingApplicationOptional.get().getDisbursalAmount()
                                - lendingApplicationOptional.get().getProcessingFee();

        if( !ObjectUtils.isEmpty(previousLoan) &&  ONE_LMS.equalsIgnoreCase(previousLoan.getLmsSource())){
            logger.info("1LMS Topup: Settling previous 1LMS loanwith loanId : {}" , previousLoan.getId());
            lmsAdjustmentForTopUp(previousLoan, previousAmount, lendingApplicationOptional.get(), previousLendingApplicationOptional.get());
        }
        else {
            ledgerAdjustmentForTopup(previousLoan, lendingApplicationOptional.get(), previousAmount);
        }

        // send loan closure consent to LDC
        if ("LDC".equals(previousLoan.getLoanApplication().getLender())) {
            apiGatewayService.getLdcTopupConsent(previousLendingApplicationOptional.get().getId(), true, previousAmount);
        }

        // deactivate autopay upi if exists for previous loan
        AutoPayUPI autoPayUPI = autoPayUPIDao.findByApplicationIdAndStatus(previousLendingApplicationOptional.get().getId(), AutoPayStatusEnum.ACTIVE.name());
        if(!ObjectUtils.isEmpty(autoPayUPI)) {
            autoPayUPI.setStatus(AutoPayStatusEnum.INACTIVE);
            autoPayUPIDao.save(autoPayUPI);
            logger.info("AutoPay UPI set to INACTIVE for applicationId: {}", previousLendingApplicationOptional.get().getId());

            final LendingPaymentSchedule closedParentLoan = previousLoan;
            executorService.execute(() -> mandateCancellationService.cancelPendingMandateExecutions(closedParentLoan, "Cancelled due to topup parent loan closure"));
        }

        finalResponse.put("message", "successfully settled previous loan");
        return finalResponse;
    }

    public void lmsAdjustmentForTopUp(LendingPaymentSchedule previousLoan, double previousAmount, LendingApplication presentApplication, LendingApplication previousLendingApplication) {

        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(previousLoan.getApplicationId());
        if(!lendingApplication.isPresent()){
            logger.error("Lending application not found for id : " + previousLoan.getApplicationId());
            return;
        }

        LoanDetailsResponse.LoanSummary loanSummary = lmsLoanDetailsService.getLoanSummaryFromOneLms(lendingApplication.get().getExternalLoanId()).getLoanSummary();

        double paidAmount = ObjectUtils.isEmpty(loanSummary.getTotalPaidAmount()) ? previousLoan.getPaidAmount() : loanSummary.getTotalPaidAmount().doubleValue();
        double paidPrincipal = ObjectUtils.isEmpty(loanSummary.getPaidPrincipalAmount()) ? previousLoan.getPaidPrinciple() : loanSummary.getPaidPrincipalAmount().doubleValue();
        double paidInterest = ObjectUtils.isEmpty(loanSummary.getPaidInterestAmount()) ? previousLoan.getPaidInterest() : loanSummary.getPaidInterestAmount().doubleValue();
        double duePenalty = Objects.nonNull(previousLoan.getDuePenalty()) ? previousLoan.getDuePenalty() : 0;

        previousLoan.setStatus("CLOSED");
        previousLoan.setClosingDate(new Date());
        previousLoan.setPaidAmount(paidAmount);
        previousLoan.setPaidPrinciple(paidPrincipal);
        previousLoan.setPaidInterest(paidInterest);
        previousLoan.setDueAmount(0D);
        previousLoan.setDuePrinciple(0D);
        previousLoan.setDueInterest(0D);
        previousLoan.setDuePenalty(0D);
        previousLoan.setPaidPenalty(ObjectUtils.isEmpty(previousLoan.getPaidPenalty()) ? 0D : previousLoan.getPaidPenalty()  + duePenalty);
        lendingPaymentScheduleDao.save(previousLoan);

        logger.info("1LMS Topup: Closed previous 1LMS loan with loanId : {}" , previousLoan.getId());
        int foreClosureAmount=0;
        try {
            logger.info("1LMS Topup: fethcing foreclosure amount for 1LMS loanId : {}" , previousLoan.getId());
            foreClosureAmount = foreclosureService.getForeclosureAmount(previousLoan.getApplicationId(), previousLoan.getMerchantId());
        }
        catch (Exception e){
            logger.error("1LMS Topup: Error in fetching foreclosure amount for 1LMS loanId : {}" , previousLoan.getId());
            return;
        }

        String terminalOrderId = "topup-adjustment-" + presentApplication.getNbfcId();
        paymentAsynchronousService.postPaymentDetails(previousLoan, (double) foreClosureAmount, presentApplication.getLoanType(), terminalOrderId, null, true);
        logger.info("1LMS Topup: Posted foreclosure payment to 1LMS for loanId : {}" , previousLoan.getId());
    }

    public void markParentLoanInActiveTopup(LendingApplication lendingApplication) {
        LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(lendingApplication.getMerchantId(), Collections.singletonList("ACTIVE"));
        if(LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(activeLoan.getNbfc())) {
             logger.info("marking parent loan {} with lender {} status INACTIVE_TOPUP for applicationId {} ", activeLoan.getId(), activeLoan.getNbfc(), lendingApplication.getId());
             activeLoan.setStatus("INACTIVE_TOPUP");
             lendingPaymentScheduleDao.save(activeLoan);
         }
    }

    private Map<String, Object> updateApplicationStatusPostVerifyOtp(BasicDetailsDto merchantBasicDetailsDto, LendingApplication lendingApplication, Meta meta) {
        updateFlagForVerifyOtpRevampFlow(lendingApplication);
        Map<String, Object> finalResponse = new LinkedHashMap<>();
        finalResponse.put("success", false);
        finalResponse.put("agreement_verified", false);
        finalResponse.put("revampVerifyOtpFlow", true);

        // ---------------- Fetching other open application and active loan for merchant ----------------
        LendingApplication openApplication = lendingApplicationDao.findOpenApplication(merchantBasicDetailsDto.getId());
        LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.getOldestActiveLoan(merchantBasicDetailsDto.getId());

        boolean isTopupLoan = topupLoans.contains(lendingApplication.getLoanType());

        // For non-topup loans: delete if openApplication OR activeLoan exists
        // For topup loans: delete only if openApplication exists
        if ((!isTopupLoan && !ObjectUtils.isEmpty(activeLoan)) || !ObjectUtils.isEmpty(openApplication)) {
            logger.info("duplicate application for merchantId:{} and applicationId:{}", merchantBasicDetailsDto.getId(), lendingApplication.getId());
            lendingApplication.setStatus(ApplicationStatus.DELETED.name().toLowerCase());
            lendingApplication.setRejectionReason("DUPLICATE_APPLICATION/OPEN_LOAN_EXISTS");
            lendingApplication.setRejectionStage(RejectionStage.POST_OTP_VERIFICATION);
            lendingApplicationDao.save(lendingApplication);
            return finalResponse;
        }

        // ---------------- Check shop documents for regular loans ----------------
        if (!isTopupLoan && StringUtils.isEmpty(lendingApplication.getCkycId())) {
            List<DocumentsIdProofMaster> documentsIdProofList = documentsIdProofDaoMaster.findByMerchantIdAndLendingApplicationId(merchantBasicDetailsDto.getId(), lendingApplication.getId());
            List<LendingShopDocuments> shopDocuments = lendingShopDocumentsDao.findByMerchantIdAndApplicationId(merchantBasicDetailsDto.getId(), lendingApplication.getId());
            if (ObjectUtils.isEmpty(documentsIdProofList) || shopDocuments.isEmpty()) {
                logger.error("shop documents not found for application:{}", lendingApplication.getId());
                return finalResponse;
            }
        }
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findByApplicationId(lendingApplication.getId());

        if (isTopupLoan) {
            // ---------------- Nach Approved status Check for topUp loans ----------------
            if (!loanUtil.isMandateDone(lendingApplication, lendingApplicationDetails)) {
                logger.error("Failed to submit topup application, as mandate not done for applicationId : {}, nach_status and upi_autopay_status are: {}, {}",
                        lendingApplication.getId(), lendingApplication.getNachStatus(), lendingApplication.getUpiAutopayStatus());
                finalResponse.put("success", false);
                finalResponse.put("agreement_verified", false);
                finalResponse.put("message", "Enach not done");
                return finalResponse;
            }

            // ----------------foreclosure checks for topUp loans ----------------
            if (!updateDisbursalAmountAndProcessingFeeDetailsForTopUpLoans(lendingApplication, activeLoan)) {
                logger.error("Failed to submit topup application as foreclosure checks failed for topup loans for applicationId {}", lendingApplication.getId());
                loanDetailsV3Service.saveApplicationViewState(lendingApplicationDetails, lendingApplication.getId(), LendingViewStates.KEY_FACTOR_STATEMENT_PAGE);
                finalResponse.put("message", "Failed to create TopUp application");
                return finalResponse;
            }
        }

        // ---------------- Kyc Status Check in case ckyc Id is there ----------------
        if (!StringUtils.isEmpty(lendingApplication.getCkycId())) {
            logger.info("Checking kyc status before agreement for application:{}", lendingApplication.getId());
            updateKycStatus(lendingApplication);
            if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getStatus())) {
                return finalResponse;
            }
        }

        // ----------------Update agreement timestamp and status for application ----------------
        DateFormat df = new SimpleDateFormat("ddMMyy");
        Date dateObj = new Date();
        String loanId = "BPL" + df.format(dateObj) + lendingApplication.getId();
        lendingApplication.setExternalLoanId(ObjectUtils.isEmpty(lendingApplication.getExternalLoanId()) ? loanId : lendingApplication.getExternalLoanId());
        lendingApplication.setAgreementAt(new Date());
        lendingApplication.setAgreement(1);
        lendingApplication.setIp(meta.getIp());
        if (!ObjectUtils.isEmpty(meta.getLatitude()) && !meta.getLatitude().equalsIgnoreCase("undefined") && !meta.getLatitude().trim().equalsIgnoreCase("")) {
            lendingApplication.setLatitude(meta.getLatitude());
        }
        if (!ObjectUtils.isEmpty(meta.getLongitude()) && !meta.getLongitude().equalsIgnoreCase("undefined") && !meta.getLongitude().trim().equalsIgnoreCase("")) {
            lendingApplication.setLongitude(meta.getLongitude());
        }
        lendingApplication.setStatus(ApplicationStatus.PENDING_VERIFICATION.name().toLowerCase());
        lendingApplicationDao.save(lendingApplication);
        lendingApplicationServiceV2.saveAuditTrail(lendingApplication, "APP_STATUS", "draft", "pending_verification");

        // ---------------- Nach Approved status Checks for topUp loans ----------------
        if (isTopupLoan) {
            logger.info("mark parent loan in-active if applicable for applicationId {}", lendingApplication.getId());
            markParentLoanInActiveTopup(lendingApplication);
        }
        LendingViewStates nextViewState = kfsStageHelper.getNextViewState(lendingApplication, lendingApplicationDetails);
        loanDetailsV3Service.saveApplicationViewState(lendingApplicationDetails, lendingApplication.getId(), nextViewState);

        // ---------------- Send Funnel event and clevertap events after agreement  ----------------
        funnelService.submitEventV3(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
                FunnelEnums.StageId.APPLICATION, FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
        HashMap<String, String> cleverTapEvtData = new HashMap<>();
        cleverTapEvtData.put("loanAmount", lendingApplication.getLoanAmount().toString());
        cleverTapEvtData.put("beneficiaryName", lendingApplication.getMerchantName());
        cleverTapEvtData.put("businessName", lendingApplication.getBusinessName());
        cleverTapEvtData.put("loanType", lendingApplication.getLoanType());
        executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.LOAN_APPLICATION_COMPLETED_BE.name(), cleverTapEvtData, merchantBasicDetailsDto.getMid()));

        // ---------------- Push application for post agreement async flow ----------------
        pushApplicationForPostAgreementAsyncFlow(merchantBasicDetailsDto.getId(), lendingApplication.getId(), meta);

        finalResponse.put("success", true);
        finalResponse.put("agreement_verified", true);
        return finalResponse;
    }

    public void pushApplicationForPostAgreementAsyncFlow(Long merchantId, Long applicationId, Meta meta) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("merchant_id", merchantId);
            request.put("application_id", applicationId);
            request.put("meta", meta);
            request.put("retry_count", 0);
            postAgreementAsyncFlowService.publishKafkaEvent(request, "post_agreement_async_flow", merchantId, applicationId);
        } catch (Exception e) {
            logger.error("Error occurred while pushing application for post agreement flow for applicationId {} {}", applicationId, Arrays.asList(e.getStackTrace()));
        }
    }

    private void updateFlagForVerifyOtpRevampFlow(LendingApplication application) {
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findByApplicationId(application.getId());
        if(!ObjectUtils.isEmpty(lendingApplicationDetails)) {
            Map<String, Object> metaData = Optional.ofNullable(lendingApplicationDetails.getMetaData()).orElse(new HashMap<>());
            metaData.put("verifyOtpRevampFlow", Boolean.TRUE);
            lendingApplicationDetails.setMetaData(metaData);
            lendingApplicationDetailsDao.save(lendingApplicationDetails);
        }
    }
}
