package com.bharatpe.lending.loanV2.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.RejectionReason;
import com.bharatpe.lending.common.enums.RejectionStage;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.Deeplink;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.enums.*;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.*;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.EnachErrorHandingService;
import com.bharatpe.lending.service.FosService;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LoanDetailsServiceV2 {

    @Autowired
    ExperianDao experianDao;

    @Autowired
    CreditLineMerchantDao creditLineMerchantDao;

    @Autowired
    MerchantStoreDao merchantStoreDao;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingGstDao lendingGstDao;

    @Autowired
    LendingResubmitTaskDao lendingResubmitTaskDao;

    @Autowired
    MerchantSummaryDao merchantSummaryDao;

    @Autowired
    EligibleLoanDao eligibleLoanDao;

    @Autowired
    LendingCategoryDao lendingCategoryDao;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    LendingShopDocumentsDao lendingShopDocumentsDao;

    @Autowired
    BharatPeEnachDao bharatPeEnachDao;

    @Autowired
    EnachErrorHandingService enachErrorHandingService;

    @Autowired
    LendingDisbursalStageDao lendingDisbursalStageDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    DateTimeUtil dateTimeUtil;

    @Autowired
    LendingMerchantDetailsDao lendingMerchantDetailsDao;

    @Autowired
    FosService fosService;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    public ApiResponse<?> getLoanDetails(LoanDetailsRequest request, Merchant merchant, String token) {
        try {
            LoanDetailsResponse loanDetailsResponse = new LoanDetailsResponse();
            if (isCreditLineMerchant(merchant)) {
                log.info("credit line merchant:{}", merchant.getId());
                loanDetailsResponse.setCreditLineDeeplink("bharatpe://dynamic?key=credit-line");
                return new ApiResponse<>(loanDetailsResponse);
            }
            if (isOrganizedMerchant(merchant)) {
                log.info("organized merchant:{}", merchant.getId());
                return new ApiResponse<>(loanDetailsResponse);
            }
            // dummy merchant flag exposed to FE
            loanDetailsResponse.setDummyMerchant(easyLoanUtil.isDummyMerchant(merchant.getId()));
            loanDetailsResponse.setBankLinked(loanUtil.isBankAccLinked(merchant.getId()));
            loanDetailsResponse.setMerchantName(loanUtil.getBeneficiaryName(merchant.getId()));
            loanDetailsResponse.setBpClubMember(apiGatewayService.eligibleForProcessingFee(merchant.getId()));
            loanDetailsResponse.setRepeatLoan(loanUtil.isRepeatLoan(merchant.getId()));
            loanDetailsResponse.setAccountDetails(loanUtil.getAccountDetails(merchant.getId()));
            populateBusinessDetails(merchant,loanDetailsResponse);
            if (loanUtil.hasActiveLoan(merchant)) {
                log.info("active loan merchant:{}", merchant.getId());
                loanDetailsResponse.setActiveLoan(true);
                return new ApiResponse<>(loanDetailsResponse);
            }
            Experian experian = experianDao.getByMerchantId(merchant.getId());
            if (experian != null) {
                loanDetailsResponse.setPancard(experian.getPancardNumber());
                loanDetailsResponse.setPincode(experian.getPincode() != null ? String.valueOf(experian.getPincode()) : null);
                loanDetailsResponse.setHasExperian(true);
            }
            loanDetailsResponse.setKycStatus(kycHandler.getKycStatus(merchant.getId()).getKycStatus());
            loanDetailsResponse.setEligibleForCallback(checkEligibilityForCallback(merchant.getId()));
            Optional<LendingPaymentSchedule> lendingPaymentSchedule = lendingPaymentScheduleDao.findLatestClosedLoan(merchant.getId());
            LendingApplication openApplication;
            if (!ObjectUtils.isEmpty(lendingPaymentSchedule)) {
                openApplication = lendingApplicationDao.findTopByMerchantIdAndLoanDisbursalStatusNullAndPaymentScheduleStatusClosedOrderByIdDesc(merchant.getId(),lendingPaymentSchedule.get().getCreatedAt());
            } else {
                openApplication = lendingApplicationDao.findTopByMerchantIdAndLoanDisbursalStatusNullOrderByIdDesc(merchant.getId());
            }
            if (openApplication != null) {
                log.info("open application for merchant:{}", merchant.getId());
                updateCkycStatus(openApplication, experian);
                if(!ObjectUtils.isEmpty(openApplication.getAgreementAt())) {
                    log.info("Kyc status for application: {} is {}",openApplication.getId(), loanDetailsResponse.getKycStatus());
                    loanDetailsResponse.setKycStatus(KycStatus.APPROVED);
                }
                boolean isIOS = request != null && request.isIOS();
                setApplicationDetails(loanDetailsResponse, openApplication, token, isIOS, experian);
                if (loanDetailsResponse.getLoanApplication() != null && StringUtils.isEmpty(loanDetailsResponse.getLoanApplication().getReapply())) {
                    //if no reapply then dont check eligibility
                    return new ApiResponse<>(loanDetailsResponse);
                }
            }
            checkEligibility(loanDetailsResponse, request, experian, merchant);
            return new ApiResponse<>(loanDetailsResponse);
        } catch (Exception e) {
            log.error("Exception in loan details service v2 for merchant:{}", merchant.getId(), e);
            return new ApiResponse<>(false, "Something went wrong");
        }
    }

    private void populateBusinessDetails(Merchant merchant, LoanDetailsResponse loanDetailsResponse) {
        LendingMerchantDetails lendingMerchantDetails = lendingMerchantDetailsDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
        if (Objects.nonNull(lendingMerchantDetails)) {
            loanDetailsResponse.setBusinessName(lendingMerchantDetails.getBusinessName());
            loanDetailsResponse.setBusinessCategory(lendingMerchantDetails.getBusinessCategory());
            loanDetailsResponse.setBusinessSubCategory(lendingMerchantDetails.getBusinessSubCategory());
        }
    }

    private void updateCkycStatus(LendingApplication openApplication, Experian experian) {
        if (!StringUtils.isEmpty(openApplication.getCkycId()) && ApplicationStatus.DRAFT.name().equalsIgnoreCase(openApplication.getStatus())) {
            log.info("Checking verified pan for draft application:{}", openApplication.getId());
            try {
                String pancard = kycHandler.getPanNumber(openApplication.getMerchant().getId());
                if (pancard != null && experian != null && !experian.getPancardNumber().equalsIgnoreCase(pancard)) {
                    log.info("pancard mismatch for merchant:{}, kyc pancard:{}, experian pancard:{}, rejecting application", experian.getMerchantId(), pancard, experian.getPancardNumber());
                    openApplication.setCkycStatus(KycStatus.REJECTED.name());
                    openApplication.setCkycRejectionReason("PANCARD MISMATCH");
                    openApplication.setCkycDate(new Date());
                    openApplication.setStatus(KycStatus.REJECTED.name().toLowerCase());
                    lendingApplicationDao.save(openApplication);
                    executorService.execute(() -> apiGatewayService.globalLimitTxn(openApplication.getMerchant().getId(), "CREDIT", openApplication.getLoanAmount()));
                }
            } catch (Exception e) {
                log.error("Exception in updateCkycStatus for application:{}", openApplication.getId());
            }
        }
    }

    private void checkEligibility(LoanDetailsResponse loanDetailsResponse, LoanDetailsRequest request, Experian experian, Merchant merchant) throws Exception {
        String kycPancard = kycHandler.getPanNumber(merchant.getId());
        if (experian == null && (request == null || request.getPancard() == null || request.getPincode() == null)) {
            log.info("Invalid request to eligibility for merchant:{}", merchant.getId());
            loanDetailsResponse.setPancard(kycPancard);
            return;
        }
        MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchant.getId());
        if (experian == null) {
            experian = experianDao.save(new Experian(merchant.getId(), null, merchant.getLatitude() != null && merchant.getLatitude() <= 90 ? merchant.getLatitude() : null, merchant.getLongitude() != null && merchant.getLongitude() <= 90 ? merchant.getLongitude() : null, 0, request.getPancard(), (merchantSummary != null && merchantSummary.getBpScore() != null) ? merchantSummary.getBpScore() : 0D, 0, Integer.valueOf(request.getPincode())));
        } else if (request != null && request.getPancard() != null && request.getPincode() != null && !experian.getPancardNumber().equalsIgnoreCase(request.getPancard())) {
            log.info("Found different pancard for merchant:{}, old pancard:{}, new pancard:{}", merchant.getId(), experian.getPancardNumber(), request.getPancard());
            experian.setPancardNumber(request.getPancard());
            experian.setBpScore((merchantSummary != null && merchantSummary.getBpScore() != null) ? merchantSummary.getBpScore() : 0D);
            experian.setPincode(Integer.valueOf(request.getPincode()));
            experian.setResponse(null);
            experian.setBureau(null);
            experian.setHitId(null);
            experian.setReportDate(null);
            experian.setExperianScore(null);
            experianDao.save(experian);
        } else if (request != null && request.getPincode() != null) {
            log.info("updating experian pincode:{} for merchant:{}", request.getPincode(), merchant.getId());
            experian.setPincode(Integer.valueOf(request.getPincode()));
            experianDao.save(experian);
        }
        if(!easyLoanUtil.isDummyMerchant(merchant.getId())) {
            if (!StringUtils.isEmpty(kycPancard) && !kycPancard.equalsIgnoreCase(experian.getPancardNumber())) {
                log.info("Pancard mismatch for merchant:{}, kyc:{}, experian:{}", merchant.getId(), kycPancard, experian.getPancardNumber());
                experian.setPancardNumber(kycPancard);
                experian.setResponse(null);
                experian.setBureau(null);
                experian.setHitId(null);
                experian.setReportDate(null);
                experian.setExperianScore(null);
                experianDao.save(experian);
            }
        }
        loanDetailsResponse.setPancard(experian.getPancardNumber());
        loanDetailsResponse.setPincode(experian.getPincode() != null ? String.valueOf(experian.getPincode()) : null);
        loanDetailsResponse.setHasExperian(true);
        MutableBoolean isDerog = new MutableBoolean(false);
        GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(merchant.getId(), null, request.getAppVersion());
        Double eligibleAmount = 0D;
        if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
            log.info("Global limit for merchant:{} is {}", merchant.getId(), globalLimitResponse.getData().getGlobalLimit());
            eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
            isDerog.setValue(globalLimitResponse.getData().isDerog());
        }
        Eligibility eligibility = null;
        if (eligibleAmount > 0D) {
            log.info("Eligibility found for merchant:{}", merchant.getId());
            recomputeEligibleLoan(globalLimitResponse, null);
            eligibility = createEligibility(merchant.getId());
        }
        log.info("Eligibility not found for merchant:{}", merchant.getId());

        if (eligibility != null) {
            loanDetailsResponse.setEligibility(eligibility);
            return;
        }
        loanDetailsResponse.setIneligible(getIneligibleReason(merchant.getId(), isDerog, experian.getPincode(),globalLimitResponse));
        loanDetailsResponse.setChangeBankAccount(!loanUtil.isEnachBank(merchant.getId()));
    }



    public void recomputeEligibleLoan(GlobalLimitResponse globalLimitResponse, Double customAmount) {
        if(Objects.isNull(globalLimitResponse) || Objects.isNull(globalLimitResponse.getData())) {
            return;
        }
        Long merchantId = globalLimitResponse.getData().getMerchantId();
        Double finalLimit = globalLimitResponse.getData().getGlobalLimit();
        String loanType = globalLimitResponse.getData().getLoanType();
        String version = globalLimitResponse.getData().getVersion();
        try {
            eligibleLoanDao.deleteByMerchantId(merchantId);
            List<GlobalLimitResponse.TenureDetail> tenureDetails = globalLimitResponse.getData().getTenureDetails();
            for (GlobalLimitResponse.TenureDetail tenureDetail : tenureDetails) {
                if(Objects.nonNull(customAmount) && customAmount < finalLimit && customAmount <= tenureDetail.getMaxLoanAmount()) {
                    loanUtil.calculateLoanBreakup(tenureDetail, merchantId, loanType, customAmount, null, version);
                }
                if(finalLimit <= tenureDetail.getMaxLoanAmount()) {
                    loanUtil.calculateLoanBreakup(tenureDetail, merchantId, loanType, finalLimit, null, version);
                }
            }
            eligibleLoanDao.deleteGreaterOffersByMerchantId(merchantId, finalLimit);
        } catch (Exception e) {
            log.error("Exception while recomputing eligible loan for merchant:{}", merchantId, e);
        }
    }

    private Integer fetchPincode(Long merchantId) {
        log.info("Fetching pincode for merchant:{}", merchantId);
        try {
            MerchantInfoDTO merchantInfoDTO = apiGatewayService.getMerchantAddress(merchantId);
            if (merchantInfoDTO != null && merchantInfoDTO.getData() != null && merchantInfoDTO.getData().get(0).getAddressDetail() != null) {
                for (MerchantInfoDTO.AddressDetail addressDetail : merchantInfoDTO.getData().get(0).getAddressDetail()) {
                    if (!StringUtils.isEmpty(addressDetail.getPinCode()) && !StringUtils.isEmpty(addressDetail.getAddressType()) && addressDetail.getAddressType().equalsIgnoreCase("Shop/Office")) {
                        return Integer.parseInt(addressDetail.getPinCode());
                    }
                }
            }
        } catch (Exception e) {
            log.info("Exception while fetching pincode for merchant:{}", merchantId, e);
        }
        return null;
    }

    private String getIneligibleReason(Long merchantId, MutableBoolean isDerog, Integer pincode, GlobalLimitResponse globalLimitResponse) {
        log.info("Checking ineligible reason for merchant:{}", merchantId);
        try {
            if (Objects.isNull(globalLimitResponse) || Objects.isNull(globalLimitResponse.getData())) {
                log.info("Global limit response is null for merchantId: {} , {}", merchantId, globalLimitResponse);
            }
            if (Objects.nonNull(globalLimitResponse) && Objects.nonNull(globalLimitResponse.getData()) && Objects.nonNull(globalLimitResponse.getData().getRejectionType())) {
                return globalLimitResponse.getData().getRejectionType();
            }
            if (loanUtil.isOGL(pincode)) {
                log.info("OGL merchant:{}", merchantId);
                return IneligibleType.OGL.name();
            }
            if (isDerog.booleanValue()) {
                log.info("Derog merchant:{}", merchantId);
                return IneligibleType.DEROG.name();
            }
        } catch (Exception e) {
            log.error("Exception in getIneligibleReason for merchant:{}", merchantId, e);
        }
        log.info("Ineligible merchant:{}", merchantId);
        return RejectionReason.LOW_TRANSACTION.getReason();
    }

    private GlobalLimitResponse getEligibility(Merchant merchant, Integer appVersion) {
        log.info("Checking eligibility for merchant:{}", merchant.getId());
        try {
            GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(merchant.getId(), null, appVersion);
            return globalLimitResponse;
        } catch (Exception e) {
            log.error("Exception in getEligibility for merchant:{}", merchant.getId(), e);
        }
        return null;
    }

    private Eligibility createEligibility(Long merchantId) {
        log.info("Creating eligibility for merchant:{}", merchantId);
        try {
            EligibleLoan eligibleLoan = eligibleLoanDao.findMaxLoan(merchantId);
            if (ObjectUtils.isEmpty(eligibleLoan)) {
                return null;
            }
            LendingCategories lendingCategories = lendingCategoryDao.getByCategory(eligibleLoan.getCategory());
            return Eligibility.builder()
                    .loanAmount(eligibleLoan.getAmount())
                    .arrangerFee(LoanCalculationUtil.getProcessingFee(eligibleLoan.getAmount(), lendingCategories))
                    .interestRate(lendingCategories.getInterestRate())
                    .repaymentAmount(eligibleLoan.getRepayment())
                    .ediCount(lendingCategories.getPayableDays())
                    .ediAmount(eligibleLoan.getEdi())
                    .tenure(eligibleLoan.getTenure())
                    .category(eligibleLoan.getCategory())
                    .loanType(eligibleLoan.getLoanType()).build();
        } catch (Exception e) {
            log.error("Exception in createEligibility for merchant:{}", merchantId, e);
        }
        return null;
    }

    private void setApplicationDetails(LoanDetailsResponse loanDetailsResponse, LendingApplication openApplication, String token, boolean isIOS, Experian experian) {
        try {
            LoanApplicationDetails applicationDetails = new LoanApplicationDetails();
            applicationDetails.setApplicationId(openApplication.getId());
            applicationDetails.setExternalLoanId(openApplication.getExternalLoanId());
            applicationDetails.setLoanAmount(openApplication.getLoanAmount());
            applicationDetails.setApplicationStatus(openApplication.getStatus());
            if("approved".equalsIgnoreCase(openApplication.getStatus()) || "pending_verification".equalsIgnoreCase(openApplication.getStatus())) {
                LendingResubmitTask lendingResubmitTask = lendingResubmitTaskDao.findTopByApplicationIdAndMerchantId(openApplication.getId(), openApplication.getMerchant().getId());
                if (Objects.nonNull(lendingResubmitTask) && lendingResubmitTask.getResubmit() != null && lendingResubmitTask.getResubmit() && (lendingResubmitTask.getResubmitDone() == null || !lendingResubmitTask.getResubmitDone())) {
                    Date currentRequestTimestamp = dateTimeUtil.getCurrentDate();
                    Date resubmitCreatedAt = lendingResubmitTask.getCreatedAt();
                    Date opsStartTimestamp = dateTimeUtil.getDateAtTime(resubmitCreatedAt, 9, 0, 0, 0);
                    Date opsSameDayProcessTimestamp = dateTimeUtil.getDateAtTime(resubmitCreatedAt, 18, 0, 0, 0);
                    Date opsNextDayProcessTimestamp = dateTimeUtil.getDateAtTime(dateTimeUtil.getDatePlusDays(resubmitCreatedAt, 24), 18, 0, 0, 0);
                    if ((resubmitCreatedAt.before(opsStartTimestamp) && currentRequestTimestamp.after(opsSameDayProcessTimestamp)) || ((resubmitCreatedAt.after(opsStartTimestamp)) && (currentRequestTimestamp.after(opsNextDayProcessTimestamp)))) {
                        applicationDetails.setApplicationStatus("RESUBMIT");
                        applicationDetails.setResubmitReason(lendingResubmitTask.getResubmitReason());
                    }
                }
                if (Objects.nonNull(lendingResubmitTask) && lendingResubmitTask.getDowngrade() != null && lendingResubmitTask.getDowngrade() && (lendingResubmitTask.getDowngradeDone() == null || !lendingResubmitTask.getDowngradeDone())) {
                    applicationDetails.setApplicationStatus("DOWNGRADE");
                }
            }
            applicationDetails.setRejectReason(getRejectionReason(openApplication));
            applicationDetails.setEnachDeeplink(getEnachDeeplink(openApplication, token, isIOS));
            if (LoanType.SMALL_TICKET.name().equalsIgnoreCase(openApplication.getLoanType())) {
                applicationDetails.setSkipEnach(Boolean.TRUE);
            }
            applicationDetails.setAddressDetails(getShopAddress(openApplication));
            applicationDetails.setProfessionalDetails(getProfessionalDetails(openApplication));
            applicationDetails.setAdditionalDetails(new AdditionalDetails(openApplication.getEmail(), openApplication.getAlternateMobile()));
            applicationDetails.setCurrentAddress(getCurrentAddress(openApplication));
            applicationDetails.setShopPhotoRequired(isShopPhotoRequired(openApplication));
            if (applicationDetails.getEnachDeeplink() == null && (ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(openApplication.getStatus()) || ApplicationStatus.APPROVED.name().equalsIgnoreCase(openApplication.getStatus()))) {
                int tat = loanUtil.getApplicationTAT(openApplication.getId());
                applicationDetails.setTransferDays(tat < 1 ? "Soon" : tat + "-" + (tat + 2) + " Days");
            }
            Long reapplyTime = getReapplyTime(openApplication);
            if(Objects.nonNull(reapplyTime)) {
                reapplyTime = reapplyTime > 0 ? reapplyTime : 0;
                applicationDetails.setReapplyTime(reapplyTime);
                applicationDetails.setReapplyTimeEpoch(LoanUtil.addDays(new Date(),reapplyTime).getTime());
            }
            applicationDetails.setReapply(shouldReapply(openApplication,reapplyTime));
            if (!StringUtils.isEmpty(applicationDetails.getEnachDeeplink())) {
                applicationDetails.setEnachErrorResponse(getEnachError(openApplication, experian));
            }
            applicationDetails.setEnachBank(loanUtil.isEnachBank(openApplication.getMerchant().getId()));
            loanDetailsResponse.setLoanApplication(applicationDetails);
        } catch (Exception e) {
            log.error("Exception in setApplicationDetails for merchant:{}", openApplication.getMerchant().getId(), e);
        }
    }

    public Long getReapplyTime(LendingApplication lendingApplication) {
        Long reapplyTime = null;
        if ("rejected".equalsIgnoreCase(lendingApplication.getStatus())) {
            Integer reapplyDayDiff = null;
            if ("REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil())) {
                reapplyDayDiff = easyLoanUtil.getReapplyTime(lendingApplication.getManualCibilReason(), RejectionStage.CIBIL);
            } else if ("REJECTED".equalsIgnoreCase(lendingApplication.getManualKyc())) {
                reapplyDayDiff = easyLoanUtil.getReapplyTime(lendingApplication.getManualKycReason(), RejectionStage.KYC);
            } else if ("REJECTED".equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus())) {
                reapplyDayDiff = easyLoanUtil.getReapplyTime(lendingApplication.getPhysicalReason(), RejectionStage.QC);
            }
            if(Objects.nonNull(reapplyDayDiff)) {
                reapplyTime = reapplyDayDiff - LoanUtil.getDateDiffInDays(lendingApplication.getUpdatedAt(), new Date());
                reapplyTime = reapplyTime > 0 ? reapplyTime : 0;
            }
        }
        return reapplyTime;
    }

    private String getRejectionReason(LendingApplication openApplication) {
        if (!ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getStatus()))
            return null;
        if (!StringUtils.isEmpty(openApplication.getCkycRejectionReason())) {
            return openApplication.getCkycRejectionReason();
        }
        if (!StringUtils.isEmpty(openApplication.getManualKycReason())) {
            return "Please re-apply with correct shop details";
        }
        if (!StringUtils.isEmpty(openApplication.getManualCibilReason())) {
            return "Credit Evaluation Failed";
        }
        if (!StringUtils.isEmpty(openApplication.getPhysicalReason())) {
            return "Incomplete documents submitted during physical visit";
        }
        LendingDisbursalStage lendingDisbursalStage = lendingDisbursalStageDao.findByApplicationId(openApplication.getId());
        boolean disbursalCallingRejected = lendingDisbursalStage != null && ("NO".equalsIgnoreCase(lendingDisbursalStage.getReadyStage()) || "NO".equalsIgnoreCase(lendingDisbursalStage.getCallStage()));
        if (disbursalCallingRejected) {
            return "You were unreachable on " + openApplication.getMerchant().getMobile();
        }
        return null;
    }

    private EnachErrorMessageDTO getEnachError(LendingApplication openApplication, Experian experian) {
        try {
            BharatPeEnach bharatPeEnach = bharatPeEnachDao.findByMerchantIdAndApplicationId(openApplication.getMerchant().getId(), openApplication.getId());
            if (bharatPeEnach != null) {
                return enachErrorHandingService.enachErrorResponse(bharatPeEnach, openApplication.getMerchant(), openApplication, experian);
            }
        } catch (Exception e) {
            log.error("Exception in getEnachError for merchant:{}", openApplication.getMerchant().getId());
        }
        return null;
    }

    private String shouldReapply(LendingApplication openApplication, Long reapplyTime) {

        if(ObjectUtils.isEmpty(reapplyTime)) {
            return null;
        }

        if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getStatus())) {
            if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getManualCibil())) {
                return Reapply.OFFER.name();
            } else if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getManualKyc())) {
                return Reapply.OFFER.name();
            } else if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getCkycStatus())) {
                KycStatusDTO kycStatusDTO = kycHandler.getKycStatus(openApplication.getMerchant().getId());
                if (KycStatus.REJECTED.equals(kycStatusDTO.getKycStatus()) && KycDocType.PAN_NO.equals(kycStatusDTO.getKycDocType())) {
                    return Reapply.PAN.name();
                } else if ("PANCARD MISMATCH".equalsIgnoreCase(openApplication.getCkycRejectionReason())) {
                    return Reapply.PAN.name();
                } else {
                    return Reapply.OFFER.name();
                }
            } else {
                return Reapply.OFFER.name();
            }
        }
        return null;
    }

    private boolean isShopPhotoRequired(LendingApplication openApplication) {
        if (ApplicationStatus.DRAFT.name().equalsIgnoreCase(openApplication.getStatus())) {
            List<LendingShopDocuments> lendingShopDocumentsList = lendingShopDocumentsDao.findByMerchantIdAndApplicationId(openApplication.getMerchant().getId(), openApplication.getId());
            return lendingShopDocumentsList.size() < 2;
        }
        return false;
    }

    private String getCurrentAddress(LendingApplication lendingApplication) {
        LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(lendingApplication.getId());
        return lendingGstDetail != null && !StringUtils.isEmpty(lendingGstDetail.getCurrentAddress()) && "Different".equalsIgnoreCase(lendingGstDetail.getAddressType()) ? lendingGstDetail.getCurrentAddress() : null;
    }

    private ProfessionalDetails getProfessionalDetails(LendingApplication openApplication) {
        LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(openApplication.getId());
        if (lendingGstDetail == null) return null;

        return ProfessionalDetails.builder()
                .profession(lendingGstDetail.getEntityType())
                .gstNumber(lendingGstDetail.getGstNumber())
                .experience(lendingGstDetail.getExperience())
                .salary(String.valueOf(lendingGstDetail.getSalary()))
                .companyName(lendingGstDetail.getCompanyName())
                .addressType(lendingGstDetail.getAddressType())
                .shopType(lendingGstDetail.getShopType())
                .build();
    }

    private AddressDetails getShopAddress(LendingApplication lendingApplication) {
        return AddressDetails.builder()
                .pincode(String.valueOf(lendingApplication.getPincode()))
                .city(lendingApplication.getCity())
                .state(lendingApplication.getState())
                .address1(lendingApplication.getShopNumber())
                .address2(lendingApplication.getStreetAddress())
                .landmark(lendingApplication.getLandmark()).build();
    }

    private String getEnachDeeplink(LendingApplication openApplication, String token, boolean isIOS) {
        if (!ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(openApplication.getStatus())) {
            return null;
        }
        if (easyLoanUtil.isDummyMerchant(openApplication.getMerchant().getId()) || loanUtil.isEnachDone(openApplication.getMerchant())) {
            return null;
        }
        BharatPeEnach bharatPeEnach = bharatPeEnachDao.findByMerchantIdAndApplicationId(openApplication.getMerchant().getId(), openApplication.getId());
        if (bharatPeEnach != null && BooleanUtils.isTrue(bharatPeEnach.getSkip())) {
            return null;
        }
        if (isIOS) return Deeplink.TECHPROCESS;
        return apiGatewayService.getEnachProvider(token, openApplication.getMerchant().getId());
    }

    private boolean isCreditLineMerchant(Merchant merchant) {
        CreditLineMerchant creditLineMerchant = creditLineMerchantDao.findByMerchantId(merchant.getId());
        return creditLineMerchant != null;
    }

    private boolean isOrganizedMerchant(Merchant merchant) {
        List<MerchantStore> stores = merchantStoreDao.findByMerchant(merchant);
        return !stores.isEmpty();
    }

    public ApiResponse<?> getEnachBanks() {
        List<BankList> enachBanks = loanUtil.getEnachBanks();
        if (enachBanks.isEmpty()) {
            return new ApiResponse<>(false, "No Bank Found");
        }
        List<BankAccountDetails> accountDetails = enachBanks.parallelStream().map(b -> BankAccountDetails.builder().bankName(b.getDisplayName()).bankLogo(b.getImageUrl()).build()).collect(Collectors.toList());
        accountDetails.sort(Comparator.comparing(BankAccountDetails::getBankName));
        return new ApiResponse<>(accountDetails);
    }

    public boolean checkEligibilityForCallback(Long merchantId) {
        try {
            ResponseDTO responseDTO = fosService.checkMerchantEligibilty(merchantId);
            if (responseDTO.isSuccess() && responseDTO.getData() != null) {
                FosMerchantEligibilityDto fosMerchantEligibilityDto = (FosMerchantEligibilityDto) responseDTO.getData();
                if (!"ineligible".equalsIgnoreCase(fosMerchantEligibilityDto.getEligibility())) {
                    return Boolean.TRUE;
                }
            }
        } catch (Exception e) {
            log.error("error occurred while fetching eligibility: {}",e);
        }
        return Boolean.FALSE;
    }
}