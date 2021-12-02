package com.bharatpe.lending.loanV2.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.Constants.SupportApiConstants;
import com.bharatpe.lending.common.dao.BharatPeEnachDao;
import com.bharatpe.lending.common.dao.CreditLineMerchantDao;
import com.bharatpe.lending.common.dao.LendingResubmitTaskDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.entity.BharatPeEnach;
import com.bharatpe.lending.common.entity.CreditLineMerchant;
import com.bharatpe.lending.common.entity.LendingResubmitTask;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.enums.ApplicationStage;
import com.bharatpe.lending.constant.Deeplink;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.dto.EnachErrorMessageDTO;
import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.dto.MerchantInfoDTO;
import com.bharatpe.lending.enums.*;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.*;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.EnachErrorHandingService;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
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
            loanDetailsResponse.setBankLinked(loanUtil.isBankAccLinked(merchant.getId()));
            loanDetailsResponse.setMerchantName(loanUtil.getBeneficiaryName(merchant.getId()));
            loanDetailsResponse.setBpClubMember(apiGatewayService.eligibleForProcessingFee(merchant.getId()));
            loanDetailsResponse.setRepeatLoan(loanUtil.isRepeatLoan(merchant.getId()));
            loanDetailsResponse.setAccountDetails(loanUtil.getAccountDetails(merchant.getId()));
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
            LendingApplication openApplication = lendingApplicationDao.findTopByMerchantIdAndLoanDisbursalStatusNullOrderByIdDesc(merchant.getId());
            if (openApplication != null) {
                log.info("open application for merchant:{}", merchant.getId());
                updateCkycStatus(openApplication, experian);
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

    private void checkEligibility(LoanDetailsResponse loanDetailsResponse, LoanDetailsRequest request, Experian experian, Merchant merchant) {
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
        loanDetailsResponse.setPancard(experian.getPancardNumber());
        loanDetailsResponse.setPincode(experian.getPincode() != null ? String.valueOf(experian.getPincode()) : null);
        loanDetailsResponse.setHasExperian(true);
        MutableBoolean isDerog = new MutableBoolean(false);
        GlobalLimitResponse globalLimitResponse = getEligibility(merchant, isDerog);
        Double eligibleAmount = 0D;
        if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
            log.info("Global limit for merchant:{} is {}", merchant.getId(), globalLimitResponse.getData().getGlobalLimit());
            eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
            isDerog.setValue(globalLimitResponse.getData().isDerog());
        }
        Eligibility eligibility = null;
        if (eligibleAmount > 0D) {
            log.info("Eligibility found for merchant:{}", merchant.getId());
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
            if(Objects.nonNull(globalLimitResponse.getData()) && Objects.nonNull(globalLimitResponse.getData().getRejectionType())) {
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
        return IneligibleType.INELIGIBLE.name();
    }

    private GlobalLimitResponse getEligibility(Merchant merchant, MutableBoolean isDerog) {
        log.info("Checking eligibility for merchant:{}", merchant.getId());
        try {
            Double eligibleAmount = 0D;
            GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(merchant.getId());
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
            LendingResubmitTask lendingResubmitTask = lendingResubmitTaskDao.findTopByApplicationIdAndMerchantId(openApplication.getId(),openApplication.getMerchant().getId());
            LoanApplicationDetails applicationDetails = new LoanApplicationDetails();
            applicationDetails.setApplicationId(openApplication.getId());
            applicationDetails.setExternalLoanId(openApplication.getExternalLoanId());
            applicationDetails.setLoanAmount(openApplication.getLoanAmount());
            applicationDetails.setApplicationStatus(openApplication.getStatus());
            if(Objects.nonNull(lendingResubmitTask) && lendingResubmitTask.getResubmit() !=null && lendingResubmitTask.getResubmit() && ( lendingResubmitTask.getResubmitDone()== null || !lendingResubmitTask.getResubmitDone())){
                applicationDetails.setApplicationStatus("RESUBMIT");
                applicationDetails.setResubmitReason(lendingResubmitTask.getResubmitReason());
            }
            if(Objects.nonNull(lendingResubmitTask) && lendingResubmitTask.getDowngrade() !=null && lendingResubmitTask.getDowngrade() && (lendingResubmitTask.getDowngradeDone() == null || !lendingResubmitTask.getDowngradeDone())){
                applicationDetails.setApplicationStatus("DOWNGRADE");
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
            if(reapplyTime <= 0) {
                applicationDetails.setReapply(shouldReapply(openApplication));
            }
            reapplyTime = reapplyTime > 0 ? reapplyTime : 0;
            applicationDetails.setReapplyTime(reapplyTime);
            if (!StringUtils.isEmpty(applicationDetails.getEnachDeeplink())) {
                applicationDetails.setEnachErrorResponse(getEnachError(openApplication, experian));
            }
            applicationDetails.setEnachBank(loanUtil.isEnachBank(openApplication.getMerchant().getId()));
            loanDetailsResponse.setLoanApplication(applicationDetails);
        } catch (Exception e) {
            log.error("Exception in setApplicationDetails for merchant:{}", openApplication.getMerchant().getId(), e);
        }
    }

    private Long getReapplyTime(LendingApplication lendingApplication) {
        Long reapplyTime = null;
        if ("rejected".equalsIgnoreCase(lendingApplication.getStatus())) {
            if ("REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil())) {
                Integer reapplyDayDiff = Objects.nonNull(SupportApiConstants.cibilRejectionReapplyTimelineMap.get(lendingApplication.getManualCibilReason())) ?
                    SupportApiConstants.cibilRejectionReapplyTimelineMap.get(lendingApplication.getManualCibilReason()) : SupportApiConstants.experianRejectionDefaultReapplyTimeline;
                reapplyTime = reapplyDayDiff - LoanUtil.getDateDiffInDays(lendingApplication.getUpdatedAt(), new Date());
            } else if ("REJECTED".equalsIgnoreCase(lendingApplication.getManualKyc())) {
                reapplyTime = SupportApiConstants.kycRejectionDefaultReapplyTimeline.longValue() - LoanUtil.getDateDiffInDays(lendingApplication.getUpdatedAt(), new Date());
            } else if ("REJECTED".equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus())) {
                reapplyTime = SupportApiConstants.kycRejectionDefaultReapplyTimeline.longValue() - LoanUtil.getDateDiffInDays(lendingApplication.getUpdatedAt(), new Date());
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

    private String shouldReapply(LendingApplication openApplication) {
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
                .shopType(lendingGstDetail.getShopType()).build();
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
        if (loanUtil.isEnachDone(openApplication.getMerchant())) {
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

}
