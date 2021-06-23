package com.bharatpe.lending.loanV2.service;

import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.dto.LendingApplicationRequestDTO;
import com.bharatpe.lending.enums.KycDocType;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.*;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.LenderMappingService;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class LendingApplicationServiceV2 {

    @Autowired
    KycHandler kycHandler;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    EligibleLoanDao eligibleLoanDao;

    @Autowired
    LendingCategoryDao lendingCategoryDao;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LendingGstDao lendingGstDao;

    @Autowired
    LenderMappingService lenderMappingService;

    @Autowired
    LendingAuditTrialDao lendingAuditTrialDao;

    @Autowired
    Environment env;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    public ApiResponse<?> initiateKyc(Merchant merchant, InitiateKycRequest initiateKycRequest) {
        Experian experian = experianDao.getByMerchantId(merchant.getId());
        if (experian == null || experian.getPancardNumber() == null) {
            return new ApiResponse<>(false, "Pancard does not exist");
        }
        String callBackURL = env.getProperty("new.loan.deeplink");
        if (!StringUtils.isEmpty(initiateKycRequest.getWroute())) {
            callBackURL += "&wroute=" + initiateKycRequest.getWroute();
        }
        InitiateKycDTO initiateKycDTO = InitiateKycDTO.builder()
                .referenceId(initiateKycRequest.getApplicationId() != null ? String.valueOf(initiateKycRequest.getApplicationId()) : UUID.randomUUID().toString())
                .panNumber(experian.getPancardNumber())
                .callBackUrl(callBackURL)
                .merchantId(String.valueOf(merchant.getId())).build();
        List<KycDocType> docTypes = Arrays.asList(KycDocType.PAN_CARD, KycDocType.PAN_NO, KycDocType.SELFIE, KycDocType.EKYC);
        if (initiateKycRequest.getApplicationId() != null) {
            docTypes.add(KycDocType.AADHAAR);
        }
        String ckycId = kycHandler.initiateKyc(merchant.getId(), initiateKycDTO, docTypes);
        if (ckycId != null) {
            if (initiateKycRequest.getApplicationId() != null) {
               lendingApplicationDao.updateKycId(initiateKycRequest.getApplicationId(), ckycId, merchant.getId());
            }
            return new ApiResponse<>(env.getProperty("kyc.deeplink"));
        }
        log.info("Unable to initiate kyc for merchant:{}", merchant.getId());
        return new ApiResponse<>(false, "Something went wrong");
    }

    public ApiResponse<?> createApplication(Merchant merchant, CreateApplicationRequest applicationRequest) {
        if (applicationRequest.getApplicationId() == null) {
            return createNewApplication(merchant, applicationRequest);
        } else {
            return updateApplication(merchant, applicationRequest);
        }
    }

    private ApiResponse<?> updateApplication(Merchant merchant, CreateApplicationRequest applicationRequest) {
        log.info("updating existing application:{} for merchant:{}", applicationRequest.getApplicationId(), merchant.getId());
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantAndStatus(applicationRequest.getApplicationId(), merchant, "draft");
            if (lendingApplication == null) {
                log.info("Draft application not found for id:{}", applicationRequest.getApplicationId());
                return new ApiResponse<>(false, "Draft application not found");
            }
            updateApplicationData(lendingApplication, applicationRequest);
            return new ApiResponse<>(CreateApplicationResponse.builder().applicationId(lendingApplication.getId()).build());
        } catch (Exception e) {
            log.error("Exception in updateApplication for merchant:{}", merchant.getId(), e);
            return new ApiResponse<>(false, "Something went wrong");
        }
    }

    private ApiResponse<?> createNewApplication(Merchant merchant, CreateApplicationRequest applicationRequest) {
        log.info("creating new application for merchant:{}", merchant.getId());
        try {
            String error = baseChecks(merchant, applicationRequest);
            if (error != null) return new ApiResponse<>(false, error);
            List<EligibleLoan> eligibleLoans = eligibleLoanDao.findByMerchantIdAndCategory(merchant.getId(), applicationRequest.getCategory());
            LendingCategories lendingCategory = lendingCategoryDao.getByCategory(applicationRequest.getCategory());
            if (eligibleLoans.isEmpty() || Objects.isNull(lendingCategory)) {
                log.info("eligible loan not available for merchant:{} and category:{}", merchant.getId(), applicationRequest.getCategory());
                return new ApiResponse<>(false, "eligible loan not found");
            }
            LendingApplication lendingApplication = saveLendingApplication(merchant, eligibleLoans.get(0), applicationRequest, lendingCategory);
            loanUtil.createApplicationSnapshot(lendingApplication);
            createStatusAuditTrail(lendingApplication);
            return new ApiResponse<>(CreateApplicationResponse.builder().applicationId(lendingApplication.getId()).build());
        } catch (Exception e) {
            log.error("Exception in createNewApplication for merchant:{}", merchant.getId(), e);
            return new ApiResponse<>(false, "Something went wrong");
        }
    }

    private void createStatusAuditTrail(LendingApplication lendingApplication) {
        LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
        lendingAuditTrial.setMerchantId(lendingApplication.getMerchant().getId());
        lendingAuditTrial.setApplicationId(lendingApplication.getId());
        lendingAuditTrial.setLoanId("");
        lendingAuditTrial.setUserId(Long.parseLong("0"));
        lendingAuditTrial.setNewStatus("draft");
        lendingAuditTrial.setType("APP_STATUS");
        lendingAuditTrialDao.save(lendingAuditTrial);
    }

    private LendingApplication saveLendingApplication(Merchant merchant, EligibleLoan eligibleLoan, CreateApplicationRequest lendingApplicationRequest, LendingCategories lendingCategory) {
        LendingApplication lendingApplication = new LendingApplication();
        int processingFee;
        if(apiGatewayService.eligibleForProcessingFee(merchant.getId())){
            processingFee = 0;
        }else {
            processingFee = (int) Math.ceil(eligibleLoan.getAmount() * Double.parseDouble(lendingCategory.getProcessingFee()));
        }
        lendingApplication.setEdi(Double.valueOf(eligibleLoan.getEdi()));
        lendingApplication.setIoEdi(eligibleLoan.getIoEdi() != null ? Double.valueOf(eligibleLoan.getIoEdi()) : 0D);
        lendingApplication.setRepayment(Double.valueOf(eligibleLoan.getRepayment()));
        lendingApplication.setInterestRate(lendingCategory.getInterestRate());
        lendingApplication.setProcessingFee((double) processingFee);
        lendingApplication.setDisbursalAmount(eligibleLoan.getAmount() - processingFee);
        lendingApplication.setStatus("draft");
        lendingApplication.setMode("AUTO");
        lendingApplication.setMerchant(merchant);
        lendingApplication.setLoanAmount(eligibleLoan.getAmount());
        lendingApplication.setCategory(eligibleLoan.getCategory());
        lendingApplication.setTenure(lendingCategory.getPayableConverter());
        lendingApplication.setTenureInMonths(lendingCategory.getTenureMonths().intValue());
        lendingApplication.setPayableDays((long) lendingCategory.getPayableDays());
        lendingApplication.setEdiFreeDays(lendingCategory.getEdiFreeDays());
        lendingApplication.setIoPayableDays(lendingCategory.getIoPayableDays());
        lendingApplication.setLoanConstruct(eligibleLoan.getLoanConstruct());
        lendingApplication.setLoanType(eligibleLoan.getLoanType());
        lendingApplication.setTotalLoansCount(loanUtil.getPreviousLoans(merchant.getId()).size());
        lendingApplication.setCkycId(UUID.randomUUID().toString());
        lendingApplication = lendingApplicationDao.save(lendingApplication);
        lenderMappingService.lenderMapping(lendingApplication);
        updateApplicationData(lendingApplication, lendingApplicationRequest);
        executorService.execute(() -> apiGatewayService.globalLimitTxn(merchant.getId(), "DEBIT", eligibleLoan.getAmount()));
        executorService.execute(() -> {
            JsonNode smsAnalysisData = apiGatewayService.getMerchantSmsAnalysisData(merchant);
            if (smsAnalysisData == null) {
                loanUtil.publishSmsAnalysisData(merchant);
            }
        });
        return lendingApplication;
    }

    private void updateApplicationData(LendingApplication lendingApplication, CreateApplicationRequest applicationRequest) {
        try {
            if (applicationRequest.getAddressDetails() != null) {
                AddressDetails addressDetails = applicationRequest.getAddressDetails();
                lendingApplication.setPincode(!StringUtils.isEmpty(addressDetails.getPincode()) ? Long.valueOf(addressDetails.getPincode()) : lendingApplication.getPincode());
                lendingApplication.setCity(!StringUtils.isEmpty(addressDetails.getCity()) ? addressDetails.getCity() : lendingApplication.getCity());
                lendingApplication.setState(!StringUtils.isEmpty(addressDetails.getState()) ? addressDetails.getState() : lendingApplication.getState());
                lendingApplication.setShopNumber(!StringUtils.isEmpty(addressDetails.getAddress1()) ? addressDetails.getAddress1() : lendingApplication.getShopNumber());
                lendingApplication.setArea(!StringUtils.isEmpty(addressDetails.getAddress2()) ? addressDetails.getAddress2() : lendingApplication.getArea());
                lendingApplication.setLandmark(!StringUtils.isEmpty(addressDetails.getLandmark()) ? addressDetails.getLandmark() : lendingApplication.getLandmark());
                String streetAddress = "";
                if (!StringUtils.isEmpty(addressDetails.getAddress1())) streetAddress += addressDetails.getAddress1();
                if (!StringUtils.isEmpty(addressDetails.getAddress2())) streetAddress += addressDetails.getAddress2();
                lendingApplication.setStreetAddress(!StringUtils.isEmpty(streetAddress) ? streetAddress : lendingApplication.getStreetAddress());
            }
            if (applicationRequest.getAdditionalDetails() != null) {
                AdditionalDetails additionalDetails = applicationRequest.getAdditionalDetails();
                lendingApplication.setEmail(!StringUtils.isEmpty(additionalDetails.getEmail()) ? additionalDetails.getEmail() : lendingApplication.getEmail());
                lendingApplication.setAlternateMobile(!StringUtils.isEmpty(additionalDetails.getAlternateContact()) ? additionalDetails.getAlternateContact() : lendingApplication.getAlternateMobile());
            }
            if (applicationRequest.getProfessionalDetails() != null) {
                saveGstDetails(lendingApplication, applicationRequest.getProfessionalDetails());
            }
            lendingApplicationDao.save(lendingApplication);
        } catch (Exception e) {
            log.error("Exception in updateApplicationData for application:{}", lendingApplication.getId(), e);
        }
    }

    private void saveGstDetails(LendingApplication lendingApplication, ProfessionalDetails professionalDetails) {
        try {
            LendingGstDetail lendingGstDetail =lendingGstDao.findByApplicationId(lendingApplication.getId());
            if(lendingGstDetail == null){
                lendingGstDetail = new LendingGstDetail();
                lendingGstDetail.setMerchantId(lendingApplication.getMerchant().getId());
                lendingGstDetail.setApplicationId(lendingApplication.getId());
                lendingGstDetail.setGst(false);
            }
            lendingGstDetail.setEntityType(!StringUtils.isEmpty(professionalDetails.getProfession()) ? professionalDetails.getProfession() : lendingGstDetail.getEntityType());
            lendingGstDetail.setExperience(!StringUtils.isEmpty(professionalDetails.getExperience()) ? professionalDetails.getExperience() : lendingGstDetail.getExperience());
            lendingGstDetail.setGst(!StringUtils.isEmpty(professionalDetails.getGstNumber()) || (lendingGstDetail.getGst() != null && lendingGstDetail.getGst()));
            lendingGstDetail.setGstNumber(!StringUtils.isEmpty(professionalDetails.getGstNumber()) ? professionalDetails.getGstNumber() : lendingGstDetail.getGstNumber());
            lendingGstDetail.setShopType(!StringUtils.isEmpty(professionalDetails.getShopType()) ? professionalDetails.getShopType() : lendingGstDetail.getShopType());
            lendingGstDetail.setSalary(!StringUtils.isEmpty(professionalDetails.getSalary()) ? Double.valueOf(professionalDetails.getSalary()) : lendingGstDetail.getSalary());
            lendingGstDao.save(lendingGstDetail);
        } catch (Exception e) {
            log.error("Exception in saveGstDetails for application:{}", lendingApplication.getId(), e);
        }
    }

    private String baseChecks(Merchant merchant, CreateApplicationRequest applicationRequest) {
        if (applicationRequest.getCategory() == null) {
            log.info("category not found in createNewApplication for merchant:{}", merchant.getId());
            return  "category not found";
        }
        if (Objects.isNull(applicationRequest.getAddressDetails()) || Objects.isNull(applicationRequest.getAddressDetails().getPincode())) {
            log.info("pincode not found in createNewApplication for merchant:{}", merchant.getId());
            return "pincode not found";
        }
        LendingApplication openApplication = lendingApplicationDao.getLatestPendingApplication(merchant.getId());
        if (openApplication != null) {
            log.info("Already open application found for merchant:{}", merchant.getId());
            return "found existing application";
        }
        Integer pincode = Integer.valueOf(applicationRequest.getAddressDetails().getPincode());
        if (loanUtil.isOGL(pincode)) {
            log.info("OGL pincode found for merchant:{}", merchant.getId());
            return "OGL pincode";
        }
        return null;
    }

    public ApiResponse<?> getAgreement(Long applicationId, Merchant merchant) {
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantAndStatus(applicationId, merchant, "draft");
        if (lendingApplication == null) {
            log.info("Draft application not found for id:{}", applicationId);
            return new ApiResponse<>(false, "Draft application not found");
        }
        AgreementResponse agreementResponse =  AgreementResponse.builder()
                .applicationId(lendingApplication.getId())
                .lender(lendingApplication.getLender())
                .loanAmount(lendingApplication.getLoanAmount())
                .interestRate(lendingApplication.getInterestRate())
                .arrangerFee(lendingApplication.getProcessingFee().intValue())
                .disbursalAmount(lendingApplication.getDisbursalAmount())
                .tenure(lendingApplication.getTenure())
                .ediAmount(lendingApplication.getEdi().intValue())
                .ediCount(lendingApplication.getPayableDays().intValue())
                .repayment(AgreementResponse.Repayment.builder()
                        .principal(lendingApplication.getLoanAmount())
                        .interest(lendingApplication.getRepayment() - lendingApplication.getLoanAmount())
                        .total(lendingApplication.getRepayment())
                        .build())
                .accountDetails(loanUtil.getAccountDetails(lendingApplication.getMerchant().getId())).build();
        return new ApiResponse<>(agreementResponse);
    }
}
