package com.bharatpe.lending.loanV2.service;

import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.MerchantStoreDao;
import com.bharatpe.common.dao.MerchantSummaryDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.BharatPeEnachDao;
import com.bharatpe.lending.common.dao.CreditLineMerchantDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.entity.BharatPeEnach;
import com.bharatpe.lending.common.entity.CreditLineMerchant;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.constant.Deeplink;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.dto.EnachErrorMessageDTO;
import com.bharatpe.lending.dto.GlobalLimitResponse;
import com.bharatpe.lending.dto.KycDoc;
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

import java.util.List;

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

    //TODO add syncContacts flag
    public ApiResponse<?> getLoanDetails(LoanDetailsRequest request, Merchant merchant, String token) {
        try {
            LoanDetailsResponse loanDetailsResponse = new LoanDetailsResponse();
            if(isCreditLineMerchant(merchant)) {
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
            loanDetailsResponse.setKycStatus(kycHandler.getKycStatus(merchant.getId()));
            LendingApplication openApplication = lendingApplicationDao.findTopByMerchantIdAndLoanDisbursalStatusNullOrderByIdDesc(merchant.getId());
            if (openApplication != null) {
                log.info("open application for merchant:{}", merchant.getId());
                boolean isIOS = request != null && request.isIOS();
                setApplicationDetails(loanDetailsResponse, openApplication, token, isIOS, experian);
                return new ApiResponse<>(loanDetailsResponse);
            }
            checkEligibility(loanDetailsResponse, request, experian, merchant);
            return new ApiResponse<>(loanDetailsResponse);
        } catch (Exception e) {
            log.error("Exception in loan details service v2 for merchant:{}", merchant.getId(), e);
            return new ApiResponse<>(false, "Something went wrong");
        }
    }

    private void checkEligibility(LoanDetailsResponse loanDetailsResponse, LoanDetailsRequest request, Experian experian, Merchant merchant) {
        if (experian == null && (request == null || request.getPancard() == null || request.getPincode() == null)) {
            log.info("Invalid request to eligibility for merchant:{}", merchant.getId());
            String pancard = kycHandler.getPanNumber(merchant.getId());
            loanDetailsResponse.setPancard(pancard);
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
            experianDao.save(experian);
        }
        loanDetailsResponse.setPancard(experian.getPancardNumber());
        loanDetailsResponse.setPincode(experian.getPincode() != null ? String.valueOf(experian.getPincode()) : null);
        loanDetailsResponse.setHasExperian(true);
        MutableBoolean isDerog = new MutableBoolean(false);
        Eligibility eligibility = getEligibility(merchant, isDerog);
        if (eligibility != null) {
            loanDetailsResponse.setEligibility(eligibility);
            return;
        }
        loanDetailsResponse.setIneligible(getIneligibleReason(merchant.getId(), isDerog, experian.getPincode()));
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

    private String getIneligibleReason(Long merchantId, MutableBoolean isDerog, Integer pincode) {
        log.info("Checking ineligible reason for merchant:{}", merchantId);
        try {
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

    private Eligibility getEligibility(Merchant merchant, MutableBoolean isDerog) {
        log.info("Checking eligibility for merchant:{}", merchant.getId());
        try {
            Double eligibleAmount = 0D;
            GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(merchant.getId());
            if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
                log.info("Global limit for merchant:{} is {}", merchant.getId(), globalLimitResponse.getData().getGlobalLimit());
                eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
                isDerog.setValue(globalLimitResponse.getData().isDerog());
            }
            if (eligibleAmount > 0D) {
                log.info("Eligibility found for merchant:{}", merchant.getId());
                return createEligibility(merchant.getId());
            }
            log.info("Eligibility not found for merchant:{}", merchant.getId());
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
            LoanApplicationDetails applicationDetails = new LoanApplicationDetails();
            applicationDetails.setApplicationId(openApplication.getId());
            applicationDetails.setExternalLoanId(openApplication.getExternalLoanId());
            applicationDetails.setLoanAmount(openApplication.getLoanAmount());
            applicationDetails.setApplicationStatus(openApplication.getStatus());
            applicationDetails.setRejectReason(openApplication.getManualKycReason());
            applicationDetails.setEnachDeeplink(getEnachDeeplink(openApplication, token, isIOS));
            applicationDetails.setAddressDetails(getShopAddress(openApplication));
            applicationDetails.setProfessionalDetails(getProfessionalDetails(openApplication));
            applicationDetails.setAdditionalDetails(new AdditionalDetails(openApplication.getEmail(), openApplication.getAlternateMobile()));
            applicationDetails.setCurrentAddress(getCurrentAddress(openApplication.getMerchant()));
            applicationDetails.setShopPhotoRequired(isShopPhotoRequired(openApplication));
            if (applicationDetails.getEnachDeeplink() == null && (ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(openApplication.getStatus()) || ApplicationStatus.APPROVED.name().equalsIgnoreCase(openApplication.getStatus()))) {
                int tat = loanUtil.getApplicationTAT(openApplication.getId());
                applicationDetails.setTransferDays(tat < 1 ? "Soon" : tat + "-" + (tat+2) + " Days");
            }
            applicationDetails.setReapply(shouldReapply(openApplication));
            if (!StringUtils.isEmpty(applicationDetails.getEnachDeeplink())) {
                applicationDetails.setEnachErrorResponse(getEnachError(openApplication, experian));
            }
            loanDetailsResponse.setLoanApplication(applicationDetails);
        } catch (Exception e) {
            log.error("Exception in setApplicationDetails for merchant:{}", openApplication.getMerchant().getId(), e);
        }
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
        //if cibil rejected then never show reapply
        if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getManualCibil())) {
            return null;
        }
        //if KYC rejected then route to Offers page
        if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(openApplication.getManualKyc())) {
            return "OFFER";
        }
        //TODO if pan number rejected then route to Pan Pin Page
        return null;
    }

    private boolean isShopPhotoRequired(LendingApplication openApplication) {
        if (ApplicationStatus.DRAFT.name().equalsIgnoreCase(openApplication.getStatus())) {
            List<LendingShopDocuments> lendingShopDocumentsList = lendingShopDocumentsDao.findByMerchantIdAndApplicationId(openApplication.getMerchant().getId(),openApplication.getId());
            return lendingShopDocumentsList.size() < 2;
        }
        return false;
    }

    private AddressDetails getCurrentAddress(Merchant merchant) {
        return null;
    }

    private ProfessionalDetails getProfessionalDetails(LendingApplication openApplication) {
        LendingGstDetail lendingGstDetail =lendingGstDao.findByApplicationId(openApplication.getId());
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
        String address1 = lendingApplication.getShopNumber();
        if (!StringUtils.isEmpty(lendingApplication.getArea())) {
            address1 += "," + lendingApplication.getArea();
        }
        return AddressDetails.builder()
                .pincode(String.valueOf(lendingApplication.getPincode()))
                .city(lendingApplication.getCity())
                .state(lendingApplication.getState())
                .address1(address1)
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

}
