package com.bharatpe.lending.loanV2.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.entity.BharatPeEnach;
import com.bharatpe.lending.common.entity.BpEnach;
import com.bharatpe.lending.common.entity.LendingApplicationPriority;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.ApplicationDTO;
import com.bharatpe.lending.dto.ApplicationStatusResponseDTO;
import com.bharatpe.lending.dto.LendingApplicationRequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.enums.KycDocType;
import com.bharatpe.lending.enums.KycStatus;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.*;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.LenderMappingService;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestAttribute;

import java.util.*;
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
    OrderStickerDao orderStickerDao;

    @Autowired
    MerchantSummaryDao merchantSummaryDao;

    @Autowired
    LendingDisbursalStageDao lendingDisbursalStageDao;

    @Autowired
    Environment env;

    @Autowired
    LendingShopDocumentsDao lendingShopDocumentsDao;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    public ApiResponse<?> initiateKyc(Merchant merchant, InitiateKycRequest initiateKycRequest) {
        Experian experian = experianDao.getByMerchantId(merchant.getId());
        if (experian == null || experian.getPancardNumber() == null) {
            return new ApiResponse<>(false, "Pancard does not exist");
        }
        String callBackURL = env.getProperty("kyc.loan.deeplink");
        if (!StringUtils.isEmpty(initiateKycRequest.getWroute())) {
            callBackURL += "&wroute=" + initiateKycRequest.getWroute();
        }
        InitiateKycDTO initiateKycDTO = InitiateKycDTO.builder()
                .referenceId(initiateKycRequest.getApplicationId() != null ? String.valueOf(initiateKycRequest.getApplicationId()) : String.valueOf(merchant.getId()))
                .panNumber(experian.getPancardNumber())
                .callBackUrl(callBackURL)
                .merchantId(String.valueOf(merchant.getId())).build();
        List<KycDocType> docTypes = new ArrayList<>(Arrays.asList(KycDocType.PAN_CARD, KycDocType.PAN_NO, KycDocType.SELFIE, KycDocType.EKYC));
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
            List<EligibleLoan> eligibleLoans = fetchEligibleLoansForCreateApplication(merchant.getId(), applicationRequest.getCategory(), applicationRequest.getOfferType());
            LendingCategories lendingCategory = lendingCategoryDao.getByCategory(applicationRequest.getCategory());
            if (eligibleLoans.isEmpty() || Objects.isNull(lendingCategory)) {
                log.info("eligible loan not available for merchant:{} and category:{}", merchant.getId(), applicationRequest.getCategory());
                return new ApiResponse<>(false, "eligible loan not found");
            }
            LendingApplication lendingApplication = saveLendingApplication(merchant, eligibleLoans.get(0), applicationRequest, lendingCategory);
            loanUtil.createApplicationSnapshot(lendingApplication);
            createStatusAuditTrail(lendingApplication);
            loanUtil.publishApplicationEvent(lendingApplication);
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
        lendingApplication.setCkycId(String.valueOf(merchant.getId()));
        lendingApplication.setLatitude(!StringUtils.isEmpty(lendingApplicationRequest.getLatitude()) ? lendingApplicationRequest.getLatitude() : null);
        lendingApplication.setLongitude(!StringUtils.isEmpty(lendingApplicationRequest.getLongitude()) ? lendingApplicationRequest.getLongitude() : null);
        lendingApplication.setBusinessName(lendingApplicationRequest.getBusinessName());
        lendingApplication = lendingApplicationDao.save(lendingApplication);
        lenderMappingService.lenderMapping(lendingApplication);
        updateApplicationData(lendingApplication, lendingApplicationRequest);
        replicateApplicationData(lendingApplication);
        executorService.execute(() -> apiGatewayService.globalLimitTxn(merchant.getId(), "DEBIT", eligibleLoan.getAmount()));
        executorService.execute(() -> {
            JsonNode smsAnalysisData = apiGatewayService.getMerchantSmsAnalysisData(merchant);
            if (smsAnalysisData == null) {
                loanUtil.publishSmsAnalysisData(merchant);
            }
        });
        return lendingApplication;
    }

    private void replicateApplicationData(LendingApplication lendingApplication) {
        try {
            LendingApplication prevApplication = lendingApplicationDao.getLastDisbursedLoan(lendingApplication.getMerchant().getId());
            if (prevApplication != null) {
                log.info("Replicating application for merchant:{} and previous application:{}", lendingApplication.getMerchant().getId(), prevApplication.getId());
                LendingGstDetail lendingGstDetail =lendingGstDao.findByApplicationId(prevApplication.getId());
                if(lendingGstDetail != null){
                    LendingGstDetail replicateGst = new LendingGstDetail();
                    replicateGst.setApplicationId(lendingApplication.getId());
                    replicateGst.setMerchantId(lendingApplication.getMerchant().getId());
                    replicateGst.setGst(lendingGstDetail.getGst());
                    replicateGst.setBusinessCategory(lendingGstDetail.getBusinessCategory());
                    replicateGst.setExperience(lendingGstDetail.getExperience());
                    replicateGst.setGstNumber(lendingGstDetail.getGstNumber());
                    replicateGst.setSalary(lendingGstDetail.getSalary());
                    replicateGst.setEntityType(lendingGstDetail.getEntityType());
                    replicateGst.setShopType(lendingGstDetail.getShopType());
                    replicateGst.setCompanyName(lendingGstDetail.getCompanyName());
                    replicateGst.setAddressType(lendingGstDetail.getAddressType());
                    replicateGst.setCurrentAddress(lendingGstDetail.getCurrentAddress());
                    lendingGstDao.save(replicateGst);
                }
                List<LendingShopDocuments> lendingShopDocuments = lendingShopDocumentsDao.findByMerchantIdAndLendingApplicationId(prevApplication.getMerchant().getId(),prevApplication.getId());
                if(!lendingShopDocuments.isEmpty()){
                    for(LendingShopDocuments shopDocuments : lendingShopDocuments){
                        LendingShopDocuments replicateShopDocument = new LendingShopDocuments();
                        replicateShopDocument.setApplicationId(lendingApplication.getId());
                        replicateShopDocument.setMerchantId(lendingApplication.getMerchant().getId());
                        replicateShopDocument.setIp(shopDocuments.getIp());
                        replicateShopDocument.setProofType(shopDocuments.getProofType());
                        replicateShopDocument.setProofFrontSide(shopDocuments.getProofFrontSide());
                        replicateShopDocument.setProofBackSide(shopDocuments.getProofBackSide());
                        replicateShopDocument.setLongitude(shopDocuments.getLongitude());
                        replicateShopDocument.setLatitude(shopDocuments.getLatitude());
                        replicateShopDocument.setStatus(shopDocuments.getStatus());
                        lendingShopDocumentsDao.save(replicateShopDocument);
                    }
                }
                lendingApplication.setEmail(prevApplication.getEmail());
                lendingApplication.setAlternateMobile(prevApplication.getAlternateMobile());
                lendingApplicationDao.save(lendingApplication);
            }
        } catch (Exception e) {
            log.error("Exception in replicateApplicationData for application:{}", lendingApplication.getId(), e);
        }
    }

    private void updateApplicationData(LendingApplication lendingApplication, CreateApplicationRequest applicationRequest) {
        try {
            if (applicationRequest.getAddressDetails() != null) {
                AddressDetails addressDetails = applicationRequest.getAddressDetails();
                lendingApplication.setPincode(!StringUtils.isEmpty(addressDetails.getPincode()) ? Long.valueOf(addressDetails.getPincode()) : lendingApplication.getPincode());
                lendingApplication.setCity(!StringUtils.isEmpty(addressDetails.getCity()) ? addressDetails.getCity() : lendingApplication.getCity());
                lendingApplication.setState(!StringUtils.isEmpty(addressDetails.getState()) ? addressDetails.getState() : lendingApplication.getState());
                lendingApplication.setShopNumber(!StringUtils.isEmpty(addressDetails.getAddress1()) ? addressDetails.getAddress1() : lendingApplication.getShopNumber());
                lendingApplication.setStreetAddress(!StringUtils.isEmpty(addressDetails.getAddress2()) ? addressDetails.getAddress2() : lendingApplication.getStreetAddress());
                lendingApplication.setLandmark(!StringUtils.isEmpty(addressDetails.getLandmark()) ? addressDetails.getLandmark() : lendingApplication.getLandmark());
            }
            if (applicationRequest.getAdditionalDetails() != null) {
                AdditionalDetails additionalDetails = applicationRequest.getAdditionalDetails();
                lendingApplication.setEmail(!StringUtils.isEmpty(additionalDetails.getEmail()) ? additionalDetails.getEmail() : lendingApplication.getEmail());
                lendingApplication.setAlternateMobile(!StringUtils.isEmpty(additionalDetails.getAlternateContact()) ? additionalDetails.getAlternateContact() : lendingApplication.getAlternateMobile());
            }
            if (applicationRequest.getProfessionalDetails() != null) {
                saveGstDetails(lendingApplication, applicationRequest.getProfessionalDetails());
            }
            lendingApplication.setBusinessName(applicationRequest.getBusinessName());
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
            lendingGstDetail.setCompanyName(!StringUtils.isEmpty(professionalDetails.getCompanyName()) ? professionalDetails.getCompanyName() : lendingGstDetail.getCompanyName());
            lendingGstDetail.setAddressType(!StringUtils.isEmpty(professionalDetails.getAddressType()) ? professionalDetails.getAddressType() : lendingGstDetail.getAddressType());
            lendingGstDetail.setCurrentAddress(!StringUtils.isEmpty(professionalDetails.getCurrentAddress()) ? professionalDetails.getCurrentAddress() : lendingGstDetail.getCurrentAddress());
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
        Experian experian = experianDao.getByMerchantId(merchant.getId());
        if (experian != null && experian.getPincode() != null && !pincode.equals(experian.getPincode())) {
            log.info("pincode mismatch for merchant:{}", merchant.getId());
            return "pincode mismatch";
        }
        return null;
    }

    public ApiResponse<?> getAgreement(Long applicationId, Merchant merchant) {
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantAndStatus(applicationId, merchant, "draft");
        if (lendingApplication == null) {
            log.info("Draft application not found for id:{}", applicationId);
            return new ApiResponse<>(false, "Draft application not found");
        }
        LendingCategories lendingCategories = lendingCategoryDao.getByCategory(lendingApplication.getCategory());
        AgreementResponse agreementResponse =  AgreementResponse.builder()
                .applicationId(lendingApplication.getId())
                .lender(lendingApplication.getLender())
                .loanAmount(lendingApplication.getLoanAmount())
                .interestRate(lendingApplication.getInterestRate())
                .arrangerFee(LoanCalculationUtil.getProcessingFee(lendingApplication.getLoanAmount(), lendingCategories))
                .disbursalAmount(lendingApplication.getDisbursalAmount())
                .tenure(lendingApplication.getTenure())
                .ediAmount(lendingApplication.getEdi().intValue())
                .ediCount(lendingApplication.getPayableDays().intValue())
                .bpClubMember(apiGatewayService.eligibleForProcessingFee(merchant.getId()))
                .repayment(AgreementResponse.Repayment.builder()
                        .principal(lendingApplication.getLoanAmount())
                        .interest(lendingApplication.getRepayment() - lendingApplication.getLoanAmount())
                        .total(lendingApplication.getRepayment())
                        .build())
                .accountDetails(loanUtil.getAccountDetails(lendingApplication.getMerchant().getId())).build();
        return new ApiResponse<>(agreementResponse);
    }

    private List<EligibleLoan> fetchEligibleLoansForCreateApplication(Long merchantId, String category, String offerType){
        if("CUSTOM".equalsIgnoreCase(offerType)){
            return eligibleLoanDao.findByMerchantIdAndCategoryAndOfferType(merchantId, category, offerType);
        }
        return eligibleLoanDao.findByMerchantIdAndCategory(merchantId, category);
    }

    public ApiResponse<ApplicationStatusResponseDTO> getApplicationStatus(Long applicationId, Merchant merchant, Boolean isIOS, String token) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchant(applicationId, merchant);
            if (lendingApplication == null) {
                return new ApiResponse<>(false, "application not found");
            }
            if (ApplicationStatus.DRAFT.name().equalsIgnoreCase(lendingApplication.getStatus()) || ApplicationStatus.DELETED.name().equalsIgnoreCase(lendingApplication.getStatus())) {
                return new ApiResponse<>(false, "Application not in pending state");
            }
            ApplicationStatusResponseDTO applicationStatusResponseDTO = new ApplicationStatusResponseDTO();
            applicationStatusResponseDTO.setBpClubMember(apiGatewayService.eligibleForProcessingFee(merchant.getId()));
            LendingCategories lendingCategories = lendingCategoryDao.getByCategory(lendingApplication.getCategory());
            BpEnach successEnach = loanUtil.getSuccessNach(merchant);
            OrderSticker orderSticker = orderStickerDao.findByMerchantId(merchant.getId());
            MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchant.getId());
            boolean diy = loanUtil.isDIY(merchant);
            boolean showOrderQr = (orderSticker == null && diy);
            boolean isLowPriority = loanUtil.isLowPriority(lendingApplication.getId());
            int tat = loanUtil.getApplicationTAT(lendingApplication.getId());
            List<ApplicationDTO> applicationDTO = new ArrayList<>();
            ApplicationStatusResponseDTO.ApplicationLoanDetailsDTO applicationLoanDetailsDTO = new ApplicationStatusResponseDTO.ApplicationLoanDetailsDTO();
            applicationLoanDetailsDTO.setAmount(lendingApplication.getLoanAmount());
            applicationLoanDetailsDTO.setFailedMsg("");
            applicationLoanDetailsDTO.setOrderID(lendingApplication.getExternalLoanId());
            applicationLoanDetailsDTO.setTransferDays(tat < 1 ? "Next Few Days" : tat + "-" + (tat + 2) + " Days");
            applicationLoanDetailsDTO.setLender(lendingApplication.getLender());
            applicationLoanDetailsDTO.setStatus(lendingApplication.getStatus());
            applicationLoanDetailsDTO.setCovid(false);
            applicationLoanDetailsDTO.setTenure(lendingApplication.getTenure());
            applicationLoanDetailsDTO.setInterestRate(lendingApplication.getInterestRate());
            applicationLoanDetailsDTO.setEdiAmount(lendingApplication.getEdi());
            applicationLoanDetailsDTO.setArrangerFee(LoanCalculationUtil.getProcessingFee(lendingApplication.getLoanAmount(), lendingCategories));
            String modalType = null;
            if (isLowPriority && showOrderQr && ("NTB".equals(lendingApplication.getLoanType()) || "NTB_SMS_1".equals(lendingApplication.getLoanType()))) {
                modalType = "QR";
            } else if (isLowPriority && merchantSummary != null && (merchantSummary.getTxnDayCount1Mon() == null || merchantSummary.getTxnDayCount1Mon() < 5)) {
                modalType = "TXNS";
            } else if (isLowPriority) {
                modalType = "PAGE";
            }
            if (lendingApplication.getStatus().equalsIgnoreCase("rejected")) {
                modalType = null;
            }
            applicationLoanDetailsDTO.setModalType(modalType);
            if ("1".equals(String.valueOf(lendingApplication.getAgreement()))) {
                ApplicationDTO applicationDTO1 = new ApplicationDTO();
                applicationDTO1.setStatus("APPROVED");
                applicationDTO1.setText("Application Submitted");
                ApplicationDTO.DateDTO dateDTO = new ApplicationDTO.DateDTO();
                dateDTO.setDay(lendingApplication.getAgreementAt().toString());
                dateDTO.setTime(lendingApplication.getAgreementAt().toString());
                applicationDTO1.setDateDTO(dateDTO);
                applicationDTO.add(applicationDTO1);
            }

            ApplicationDTO applicationDTO2 = new ApplicationDTO();
            if (successEnach != null) {
                applicationDTO2.setStatus(successEnach.getStatus());
                applicationDTO2.setText("e-NACH Done");
                applicationDTO2.setButtonContextDTO(null);
                ApplicationDTO.DateDTO dateDTO = new ApplicationDTO.DateDTO();
                dateDTO.setDay(successEnach.getCreatedAt().toString());
                dateDTO.setTime(successEnach.getCreatedAt().toString());
                applicationDTO2.setDateDTO(dateDTO);
                applicationDTO.add(applicationDTO2);
            } else if ("pending_verification".equalsIgnoreCase(lendingApplication.getStatus()) && loanUtil.isEnachBank(merchant.getId())) {
                applicationDTO2.setStatus("PENDING");
                applicationDTO2.setText("e-NACH Pending");
                applicationDTO2.setComment("Register eNACH for Instant Loan Approval. Get Rs100 cashback");
                ApplicationDTO.ButtonContextDTO buttonContextDTO = new ApplicationDTO.ButtonContextDTO();
                buttonContextDTO.setAction("Enach");
                buttonContextDTO.setText("Do eNACH");
                if (BooleanUtils.isTrue(isIOS)) {
                    buttonContextDTO.setDeeplink("bharatpe://enachtp");
                } else {
                    buttonContextDTO.setDeeplink(apiGatewayService.getEnachProvider(token, merchant.getId()));
                }
                applicationDTO2.setButtonContextDTO(buttonContextDTO);
                applicationDTO.add(applicationDTO2);
            }
            boolean enachMandatory = true; //TODO when enach skip is true then uncomment below code
            boolean enachSkipped = loanUtil.isNachSkipped(merchant.getId(), lendingApplication.getId());
            if (successEnach != null) {
                enachMandatory = false;
            }
//        else if (lendingApplication.getAgreementAt() != null && "REGULAR".equals(lendingApplication.getLoanType()) && lendingApplication.getLoanAmount() > 50000 && LoanUtil.getDateDiffInDays(lendingApplication.getAgreementAt(), new Date()) > 3) {
//            enachMandatory = false;
//        }
            else if (enachSkipped) {
                enachMandatory = false;
            }
            String kycStatus = lendingApplication.getManualKyc() != null && (lendingApplication.getManualKyc().equalsIgnoreCase("APPROVED") || lendingApplication.getManualKyc().equalsIgnoreCase("REJECTED")) ? lendingApplication.getManualKyc() : "PENDING";
            String kycComment = null;
            if (lendingApplication.getManualKycReason() != null) {
                kycComment = lendingApplication.getManualKycReason();
            } else if ("PENDING".equalsIgnoreCase(kycStatus)) {
                kycComment = "(We're verifying documents submitted by you)";
            }
            if ("REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil())) {
                kycStatus = "REJECTED";
                kycComment = lendingApplication.getManualCibilReason();
            }

            ApplicationDTO kycDTO = new ApplicationDTO();
            kycDTO.setText("KYC Verification");
            kycDTO.setDisabled(enachMandatory);
            kycDTO.setStatus(lendingApplication.getCkycStatus());
            kycDTO.setComment(lendingApplication.getCkycRejectionReason());
            if (lendingApplication.getCkycDate() != null) {
                kycDTO.setDateDTO(new ApplicationDTO.DateDTO(lendingApplication.getCkycDate()));
            }
            applicationDTO.add(kycDTO);

            ApplicationDTO applicationDTO3 = new ApplicationDTO();
            applicationDTO3.setText("Document Verification");
            applicationDTO3.setDisabled(enachMandatory);
            if (kycStatus.equalsIgnoreCase("APPROVED") || kycStatus.equalsIgnoreCase("REJECTED")) {
                applicationDTO3.setDisabled(false);
            }
            applicationDTO3.setStatus(kycStatus);
            if (kycComment != null && kycComment.equals("eNACH Failure")) {
                applicationDTO3.setComment("Your application is rejected due to enach failure");
            }
            if (lendingApplication.getManualKyc() != null && !"null".equalsIgnoreCase(lendingApplication.getManualKyc()) && lendingApplication.getKycApprovedDate() != null) {
                ApplicationDTO.DateDTO dateDTO = new ApplicationDTO.DateDTO();
                dateDTO.setDay(lendingApplication.getKycApprovedDate().toString());
                dateDTO.setTime(lendingApplication.getKycApprovedDate().toString());
                applicationDTO3.setDateDTO(dateDTO);
            } else if ("REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil()) && lendingApplication.getCibilApprovedDate() != null) {
                ApplicationDTO.DateDTO dateDTO = new ApplicationDTO.DateDTO();
                dateDTO.setDay(lendingApplication.getCibilApprovedDate().toString());
                dateDTO.setTime(lendingApplication.getCibilApprovedDate().toString());
                applicationDTO3.setDateDTO(dateDTO);
            }
            applicationDTO.add(applicationDTO3);
            boolean cpvRequired = loanUtil.cpvRequired(lendingApplication);
            LendingDisbursalStage lendingDisbursalStage = lendingDisbursalStageDao.findByApplicationId(lendingApplication.getId());
            String cpvStatus = lendingApplication.getPhysicalVerificationStatus() != null && (lendingApplication.getPhysicalVerificationStatus().equalsIgnoreCase("APPROVED") || lendingApplication.getPhysicalVerificationStatus().equalsIgnoreCase("REJECTED")) ? lendingApplication.getPhysicalVerificationStatus() : "PENDING";
            if ((cpvRequired && !"REJECTED".equalsIgnoreCase(kycStatus)) || "REJECTED".equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus())) {
                String cpvComment;
                if (lendingApplication.getPhysicalVerificationStatus() == null || lendingApplication.getPhysicalVerificationStatus().equalsIgnoreCase("null") || lendingApplication.getPhysicalVerificationStatus().equalsIgnoreCase("ASSIGNED")) {
                    cpvComment = "(Our agent will be visiting your shop in the next 3-4 days to verify & collect documents)";
                } else if (lendingApplication.getPhysicalVerificationStatus() != null && !lendingApplication.getPhysicalVerificationStatus().equalsIgnoreCase("APPROVED") && !lendingApplication.getPhysicalVerificationStatus().equalsIgnoreCase("REJECTED")) {
                    cpvComment = "(Documents collected from your shop by our agent are being verified by us)";
                } else {
                    cpvComment = lendingApplication.getPhysicalReason();
                }
                ApplicationDTO applicationDTO4 = new ApplicationDTO();
                applicationDTO4.setStatus(lendingApplication.getPhysicalVerificationStatus() != null && (lendingApplication.getPhysicalVerificationStatus().equalsIgnoreCase("APPROVED") || lendingApplication.getPhysicalVerificationStatus().equalsIgnoreCase("REJECTED")) ? lendingApplication.getPhysicalVerificationStatus() : "PENDING");
//			applicationDTO4.setComment(cpvComment);
                applicationDTO4.setText("Physical verification");
                applicationDTO4.setDisabled(!"APPROVED".equalsIgnoreCase(kycStatus));
                if (lendingApplication.getPhysicalVerificationStatus() != null && !"null".equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus()) && lendingApplication.getPhysicalApprovedDate() != null) {
                    ApplicationDTO.DateDTO dateDTO = new ApplicationDTO.DateDTO();
                    dateDTO.setTime(lendingApplication.getPhysicalApprovedDate().toString());
                    dateDTO.setDay(lendingApplication.getPhysicalApprovedDate().toString());
                    applicationDTO4.setDateDTO(dateDTO);
                }
                applicationDTO.add(applicationDTO4);
            }
            String applicationStatus = lendingApplication.getStatus();
            String callingStatus = null;
            if (("NTB".equalsIgnoreCase(lendingApplication.getLoanType()) || "NTB_SMS_1".equalsIgnoreCase(lendingApplication.getLoanType())) && (!"rejected".equalsIgnoreCase(lendingApplication.getStatus()) || lendingDisbursalStage != null)) {
                ApplicationDTO applicationDTO5 = new ApplicationDTO();
                applicationDTO5.setDisabled(!"approved".equalsIgnoreCase(lendingApplication.getStatus()));
                applicationDTO5.setText("Disbursal Review & Calling");
                ApplicationDTO.DateDTO dateDTO = null;
                if (lendingDisbursalStage != null) {
                    if ("YES".equalsIgnoreCase(lendingDisbursalStage.getCallStage())) {
                        callingStatus = "APPROVED";
                        dateDTO = new ApplicationDTO.DateDTO();
                        dateDTO.setDay(lendingDisbursalStage.getCallTimestamp());
                        dateDTO.setTime(lendingDisbursalStage.getCallTimestamp());
                    } else if ("NO".equalsIgnoreCase(lendingDisbursalStage.getReadyStage())) {
                        callingStatus = "REJECTED";
                        dateDTO = new ApplicationDTO.DateDTO();
                        dateDTO.setDay(lendingDisbursalStage.getReadyTimestamp());
                        dateDTO.setTime(lendingDisbursalStage.getReadyTimestamp());
//					applicationDTO5.setComment("Credit Review failed");
                    } else if ("NO".equalsIgnoreCase(lendingDisbursalStage.getCallStage())) {
                        callingStatus = "REJECTED";
                        dateDTO = new ApplicationDTO.DateDTO();
                        dateDTO.setDay(lendingDisbursalStage.getCallTimestamp());
                        dateDTO.setTime(lendingDisbursalStage.getCallTimestamp());
//					applicationDTO5.setComment("Call not picked");
                    } else if ("rejected".equalsIgnoreCase(lendingApplication.getStatus())) {
                        callingStatus = "REJECTED";
                    } else {
                        callingStatus = "PENDING";
                        applicationStatus = "PENDING";
                    }
                    applicationDTO5.setDateDTO(dateDTO);
                    applicationDTO5.setStatus(callingStatus);
                    applicationDTO5.setDisabled(Boolean.FALSE);
                } else if ("approved".equalsIgnoreCase(lendingApplication.getStatus())) {
                    applicationDTO5.setStatus("PENDING");
                    applicationStatus = "PENDING";
                }
                applicationLoanDetailsDTO.setStatus(applicationDTO5.getStatus());
                applicationDTO.add(applicationDTO5);
            }

            if (!"rejected".equalsIgnoreCase(lendingApplication.getStatus())) {
                ApplicationDTO applicationDTO6 = new ApplicationDTO();
                applicationDTO6.setDisabled(!applicationStatus.equalsIgnoreCase("approved"));
                applicationDTO6.setText("Disbursal!");
                applicationDTO.add(applicationDTO6);
                if (!applicationDTO6.isDisabled()) {
                    applicationDTO6.setStatus("PENDING");
                }
            }
            applicationLoanDetailsDTO.setStatus(applicationStatus);
            ApplicationStatusResponseDTO.HeaderDTO headerDTO = new ApplicationStatusResponseDTO.HeaderDTO();
            if (successEnach == null && ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(lendingApplication.getStatus())) {
                headerDTO.setTitle("Bank A/c Linking Pending");
                headerDTO.setComment("Complete eNACH to process you loan");
            } else if (lendingApplication.getCkycStatus() != null && lendingApplication.getCkycStatus().equalsIgnoreCase(KycStatus.PENDING.name())) {
                headerDTO.setTitle("KYC Verification Pending");
                headerDTO.setComment("We are verifying your kyc documents");
            } else if (lendingApplication.getCkycStatus() != null && lendingApplication.getCkycStatus().equalsIgnoreCase(KycStatus.REJECTED.name())) {
                headerDTO.setTitle("KYC Verification Failed");
                headerDTO.setComment(lendingApplication.getCkycRejectionReason());
            } else if (KycStatus.PENDING.name().equalsIgnoreCase(kycStatus)) {
                headerDTO.setTitle("Document Verification Pending");
                headerDTO.setComment("We are reviewing your shop documents");
            } else if (KycStatus.REJECTED.name().equalsIgnoreCase(kycStatus)) {
                headerDTO.setTitle("Document Verification Failed");
                headerDTO.setComment("Please re-apply with correct shop details");
            } else if (KycStatus.PENDING.name().equalsIgnoreCase(cpvStatus)) {
                headerDTO.setTitle("Physical Verification Pending");
                headerDTO.setComment("Our agents will visit your shop to collect business documents");
            } else if (KycStatus.REJECTED.name().equalsIgnoreCase(cpvStatus)) {
                headerDTO.setTitle("Physical Verification Failed");
                headerDTO.setComment("Incomplete documents submitted during physical visit");
            } else if (KycStatus.PENDING.name().equalsIgnoreCase(callingStatus)) {
                headerDTO.setTitle("Verification Call Pending");
                headerDTO.setComment("Our agents will call you on " + merchant.getMobile() + " in 1-2 days for verification");
            } else if (KycStatus.REJECTED.name().equalsIgnoreCase(callingStatus)) {
                headerDTO.setTitle("Verification Call Failed");
                headerDTO.setComment("You were unreachable on " + merchant.getMobile());
            } else {
                headerDTO = null;
            }
            applicationStatusResponseDTO.setApplicationLoanDetailsDTO(applicationLoanDetailsDTO);
            applicationStatusResponseDTO.setHeader(headerDTO);
            applicationStatusResponseDTO.setApplicationDTOList(applicationDTO);
            return new ApiResponse<>(applicationStatusResponseDTO);
        } catch (Exception e) {
            log.error("Exception in applicationStatus v2 for application:{}", applicationId, e);
        }
        return new ApiResponse<>(false, "Something went wrong");
    }
}
