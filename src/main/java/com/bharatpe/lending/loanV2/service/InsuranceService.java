package com.bharatpe.lending.loanV2.service;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingConsentDao;
import com.bharatpe.lending.common.dao.LendingLoanInsuranceDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingConsent;
import com.bharatpe.lending.common.entity.LendingLoanInsurance;
import com.bharatpe.lending.common.enums.EdiModel;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.query.dao.LendingApplicationDaoSlave;
import com.bharatpe.lending.common.query.dao.LendingApplicationLenderDetailsDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.config.InsuranceConfig;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.dto.UpdateInsuranceDetailsRequestDTO;
import com.bharatpe.lending.loanV2.dto.UpdateInsuranceDetailsResponseDTO;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.services.associationsV2.AssociationServiceUtil;
import com.bharatpe.lending.loanV3.services.associationsV2.payu.impl.PayUInsuranceService;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.service.APIGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static com.bharatpe.lending.constant.InsuranceConstant.*;
import static com.bharatpe.lending.constant.KfsConstants.INSURANCE_POLICY_DOC_PREFIX;

@Service
@Slf4j
public class InsuranceService {

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LendingApplicationDaoSlave lendingApplicationDaoSlave;

    @Lazy
    @Autowired
    KycUtils kycUtils;

    @Lazy
    @Autowired
    AssociationServiceUtil associationServiceUtil;

    @Autowired
    LendingConsentDao lendingConsentDao;

    @Autowired
    LendingLoanInsuranceDao lendingLoanInsuranceDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Lazy
    @Autowired
    FunnelService funnelService;

    @Lazy
    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    InsuranceConfig insuranceConfig;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    PayUInsuranceService payUInsuranceService;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Value("${aws.s3.bucket:loan-document}")
    private String bucket;

    @Value("${piramal.insurance.max.apr.threshold:48}")
    Double piramalInsuranceMaxAprThreshold;

    private static final List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());

    public InsuranceEligibilityResponseDTO.ResponseData checkInsuranceEligibility(LendingPaymentScheduleSlave activeLoan) {
        log.info("check insurance eligibility for merchant {}", activeLoan.getMerchantId());
        LendingApplicationSlave lendingApplicationSlave = lendingApplicationDaoSlave.findByIdAndMerchantId(activeLoan.getApplicationId(), activeLoan.getMerchantId());
        if(!ObjectUtils.isEmpty(lendingApplicationSlave)) {
            Map<String, String> businessCategories = kycUtils.getBusinessCategoryAndSubCategoryByApplicationId(activeLoan.getApplicationId());
            InsuranceEligibilityRequestDTO insuranceEligibilityRequest = InsuranceEligibilityRequestDTO.builder()
                    .customerId(lendingApplicationSlave.getMerchantId())
                    .amount(lendingApplicationSlave.getLoanAmount())
                    .tenure(lendingApplicationSlave.getTenureInMonths())
                    .pinCode(lendingApplicationSlave.getPincode())
                    .businessCategory(businessCategories.getOrDefault("businessCategory", null))
                    .businessSubCategory(businessCategories.getOrDefault("businessSubcategory", null))
                    .build();
            return apiGatewayService.fetchInsuranceEligibility(insuranceEligibilityRequest);
        }
        return null;
    }

    public LoanInsuranceDTO fetchLenderInsurancePremiumDetails(LendingApplication lendingApplication) {
        LoanInsuranceDTO loanInsurance = new LoanInsuranceDTO();
        log.info("fetch insurance premium details for application {}, loan type {}", lendingApplication.getId(), lendingApplication.getLoanType());
        try {
                List<LoanInsuranceDTO.InsuranceDetails> previousInsuredDetails = fetchPreviousInsuredDetails(lendingApplication);
                if (!ObjectUtils.isEmpty(previousInsuredDetails)) {
                    log.info("Merchant: {} is insured before for the application id: {} and insurance: {}", lendingApplication.getMerchantId(), lendingApplication.getId(), previousInsuredDetails);
                    loanInsurance.setInsurances(previousInsuredDetails);
                    loanInsurance.setSelected(true);
                    return loanInsurance;
                }
                LoanInsuranceDTO insuranceDetails = associationServiceUtil.fetchInsurancePremiums(lendingApplication);
                LendingApplicationLenderDetails lendingApplicationLenderDetails =
                        lendingApplicationLenderDetailsDao.findApplicationIdByBreStatus(lendingApplication.getId(), lendingApplication.getLender());
                if(!ObjectUtils.isEmpty((lendingApplicationLenderDetails))){
                    log.info("Accept offer api already called for application {}", lendingApplicationLenderDetails.getApplicationId());
                }

                if(ObjectUtils.isEmpty(lendingApplicationLenderDetails) && !ObjectUtils.isEmpty(insuranceDetails) && !ObjectUtils.isEmpty(insuranceDetails.getInsurances())) {
                    Double insurancePremium = insuranceDetails.getInsurances().get(0).getInsurancePremium();
                    if(baseChecksFailed(lendingApplication, insurancePremium)) {
                        log.info("base checks failed for insurance for application {}", lendingApplication.getId());
                        throw new RuntimeException("Base checks failed for insurance eligibility for application " + lendingApplication.getId());
                    }
                    updateApplicationInsuranceEligibilityFlag(lendingApplication, true);
                    return insuranceDetails;
                }
        } catch (Exception e) {
            log.info("Exception in fetching insurance premium details for {} {}", lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
        }
        updateApplicationInsuranceEligibilityFlag(lendingApplication, false);
        return loanInsurance;
    }

    private void updateApplicationInsuranceEligibilityFlag(LendingApplication lendingApplication, Boolean insuranceAvailable) {
        try {
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findByApplicationId(lendingApplication.getId());
            if(!ObjectUtils.isEmpty(lendingApplicationDetails)) {
                Map<String, Object> metaData = ObjectUtils.isEmpty(lendingApplicationDetails.getMetaData()) ? new HashMap<>() : lendingApplicationDetails.getMetaData();
                metaData.put("loan_insurance_available", insuranceAvailable);
                lendingApplicationDetails.setMetaData(metaData);
            }
            lendingApplicationDetailsDao.save(lendingApplicationDetails);
        } catch (Exception e) {
            log.info("Exception in updating insurance eligibility flag for application {}", lendingApplication.getId());
        }
    }

    private List<LoanInsuranceDTO.InsuranceDetails> fetchPreviousInsuredDetails(LendingApplication lendingApplication) {
        LendingLoanInsurance loanInsurance = getInsuranceDetails(lendingApplication.getId(), lendingApplication.getLender(), SELECTED);
        if (!ObjectUtils.isEmpty(loanInsurance)) {
            return Collections.singletonList(LoanInsuranceDTO.InsuranceDetails.builder()
                    .insurancePremium(loanInsurance.getInsurancePremium())
                    .sumInsured(loanInsurance.getSumInsured())
                    .policyTermsInMonths(loanInsurance.getPolicyTermsInMonths())
                    .provider(loanInsurance.getProvider())
                    .product(loanInsurance.getProduct())
                    .build());
        }
        return null;
    }

    private Boolean baseChecksFailed(LendingApplication lendingApplication, Double insurancePremium) {
        if(Lender.PIRAMAL.name().equalsIgnoreCase(lendingApplication.getLender())) {
            if(isMaxAprCheckFailed(lendingApplication, insurancePremium)) {
                log.info("max APR check failed after insurance premium {} for application {}", insurancePremium, lendingApplication.getId());
                return true;
            }
            InsuranceActiveApplicationResponseDTO response = fetchActiveInsuranceApplicationsDetails(lendingApplication.getMerchantId());
            if(!ObjectUtils.isEmpty(response) && !ObjectUtils.isEmpty(response.getData()) && !ObjectUtils.isEmpty(response.getData().getApplications())) {
                log.info("Insurance active applications {} found for merchant {}", response.getData().getApplications(), lendingApplication.getMerchantId());
                return true;
            }
        }
        return false;
    }

    private Boolean isMaxAprCheckFailed(LendingApplication lendingApplication, Double insurancePremium) {
        Double updatedAprAfterInsurancePremium = lendingApplicationServiceV2.getApr(lendingApplication.getMerchantId(), lendingApplication.getId(), lendingApplication.getDisbursalAmount() - insurancePremium, EdiModel.SEVEN_DAY_MODEL.getNoOfEdiDaysInAWeek(), lendingApplication.getLender());
        log.info("updated apr after insurance premium is {} for application {}", updatedAprAfterInsurancePremium, lendingApplication.getId());
        switch(Lender.valueOf(lendingApplication.getLender())) {
            case PIRAMAL:
                 return updatedAprAfterInsurancePremium > piramalInsuranceMaxAprThreshold;
            default:
                return false;
        }
    }


    public ApiResponse<?> updateInsuranceDetails(UpdateInsuranceDetailsRequestDTO updateInsuranceDetailsRequest) {
        UpdateInsuranceDetailsResponseDTO response = new UpdateInsuranceDetailsResponseDTO();
        response.setSuccess(Boolean.FALSE);
        try {
            if (ObjectUtils.isEmpty(updateInsuranceDetailsRequest) || ObjectUtils.isEmpty(updateInsuranceDetailsRequest.getApplicationId()) || ObjectUtils.isEmpty(updateInsuranceDetailsRequest.getInsuranceDetails())) {
                log.info("ApplicationId / insurance details not provided for update insurance details");
                response.setMessage("Invalid request for insurance details update");
                return new ApiResponse<>(response);
            }
            Long applicationId = updateInsuranceDetailsRequest.getApplicationId();
            LoanInsuranceDTO insuranceDetails = updateInsuranceDetailsRequest.getInsuranceDetails();
            LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("No application found for given id {}", applicationId);
                response.setMessage("application not found for given applicationId");
                return new ApiResponse<>(response);
            }
            lendingApplication = insuranceDetails.isSelected() ?
                    saveInsurancePremiumDetailsAndUpdateDisbursalAmount(lendingApplication, insuranceDetails) :
                    markedPreviousInsuranceDetailsInvalid(lendingApplication);
            updateConsent(lendingApplication, insuranceDetails.isSelected());
            log.info("Insurance details added successfully for application {}", applicationId);
            response.setSuccess(Boolean.TRUE);
            response.setMessage("Insurance details updated successfully");
            return new ApiResponse<>(response);

        } catch (Exception e) {
            log.error("Exception in updating insurance details for applicationId {} {}", updateInsuranceDetailsRequest.getApplicationId(), Arrays.asList(e.getStackTrace()));
            response.setMessage("Something went wrong while updating insurance details");
            return new ApiResponse<>(response);
        }
    }

    private LendingApplication saveInsurancePremiumDetailsAndUpdateDisbursalAmount(LendingApplication lendingApplication, LoanInsuranceDTO insuranceDetails) {
        log.info("Saving Insurance Premiums details {} for application {}", insuranceDetails, lendingApplication.getId());
        Double insurancePremium = insuranceDetails.getInsurances().get(0).getInsurancePremium();
        lendingApplication.setDisbursalAmount(lendingApplication.getDisbursalAmount() - insurancePremium);
        lendingApplication = lendingApplicationDao.save(lendingApplication);
        LoanInsuranceDTO.InsuranceDetails insurance = insuranceDetails.getInsurances().get(0);
        LendingLoanInsurance loanInsurance = LendingLoanInsurance.builder()
                .applicationId(lendingApplication.getId())
                .lender(lendingApplication.getLender())
                .insurancePremium(insurance.getInsurancePremium())
                .sumInsured(insurance.getSumInsured())
                .policyTermsInMonths(insurance.getPolicyTermsInMonths())
                .provider(insurance.getProvider())
                .product(insurance.getProduct())
                .status(SELECTED)
                .build();
        lendingLoanInsuranceDao.save(loanInsurance);
        return lendingApplication;
    }

    private LendingApplication markedPreviousInsuranceDetailsInvalid(LendingApplication lendingApplication) {
        LendingLoanInsurance loanInsurance = getInsuranceDetails(lendingApplication.getId(), lendingApplication.getLender(), SELECTED);
        if (!ObjectUtils.isEmpty(loanInsurance)) {
            lendingApplication.setDisbursalAmount(lendingApplication.getDisbursalAmount() + loanInsurance.getInsurancePremium());
            lendingApplicationDao.save(lendingApplication);
            loanInsurance.setStatus(INVALID);
            lendingLoanInsuranceDao.save(loanInsurance);
        }
        return lendingApplication;
    }

    private void updateConsent(LendingApplication lendingApplication, Boolean isInsured) {
        LendingConsent lendingConsent = lendingConsentDao.findLendingConsentByApplicationIdAndMerchantIdAndConsentType(lendingApplication.getId(), lendingApplication.getMerchantId(), INSURANCE_CONSENT_TYPE);
        if (ObjectUtils.isEmpty(lendingConsent)) {
            lendingConsent = LendingConsent.builder()
                        .applicationId(lendingApplication.getId())
                        .merchantId(lendingApplication.getMerchantId())
                        .consentType(INSURANCE_CONSENT_TYPE)
                        .build();
        }
        lendingConsent.setIsAccepted(isInsured);
        lendingConsentDao.save(lendingConsent);
    }

    private InsuranceActiveApplicationResponseDTO fetchActiveInsuranceApplicationsDetails(Long merchantId) {
        log.info("fetch insurance active applications for merchant {}", merchantId);
        InsuranceActiveApplicationRequestDTO insuranceActiveApplicationRequest = InsuranceActiveApplicationRequestDTO.builder().customerId(merchantId).build();
        return apiGatewayService.getActiveInsuranceApplications(insuranceActiveApplicationRequest);
    }

    public void publishLoanInsuranceEvent(LendingApplication lendingApplication, LoanDashboardApiVersion loanDashboardApiVersion) {

        LendingConsent lendingConsent = lendingConsentDao.findLendingConsentByApplicationIdAndMerchantIdAndConsentType(
                lendingApplication.getId(),
                lendingApplication.getMerchantId(),
                INSURANCE_CONSENT_TYPE);

        LendingLoanInsurance lendingLoanInsurance = getInsuranceDetails(lendingApplication.getId(), lendingApplication.getLender(), SELECTED);

        if (!ObjectUtils.isEmpty(lendingConsent)) {
            if (!ObjectUtils.isEmpty(lendingLoanInsurance) && Lender.PAYU.name().equalsIgnoreCase(lendingApplication.getLender())) {
                // save insurance documents after disbursal
                String contentUrl = payUInsuranceService.invokeInsuranceDocument(lendingApplication);
                downloadInsuranceDocDetails(contentUrl, new Date(), new Date(), lendingApplication.getId(), lendingApplication.getLender(), lendingLoanInsurance);
            }
            FunnelEnums.StageEvent event = lendingConsent.getIsAccepted() && !ObjectUtils.isEmpty(lendingLoanInsurance) ?
                    FunnelEnums.StageEvent.ACCEPT : FunnelEnums.StageEvent.REJECT;
            log.info("Insurance is: {} for merchant: {}", event.name(), lendingApplication.getMerchantId());
            if (LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())) {
                funnelService.submitEventV3(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
                        FunnelEnums.StageId.INSURANCE, event, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
                return;
            }
            funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
                    FunnelEnums.StageId.INSURANCE, event, LocalDateTime.now().toString());
        }
    }

    public ResponseEntity<ApiResponse<?>> publishLoanInsuranceEventForPayU(Long applicationId) {
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);

        LendingLoanInsurance lendingLoanInsurance = getInsuranceDetails(lendingApplication.get().getId(), lendingApplication.get().getLender(), SELECTED);

        if (!ObjectUtils.isEmpty(lendingLoanInsurance) && Lender.PAYU.name().equalsIgnoreCase(lendingApplication.get().getLender())) {
            log.info("fetching insurance doc for applicationId: {}", applicationId);
            // save insurance documents after disbursal
            String contentUrl = payUInsuranceService.invokeInsuranceDocument(lendingApplication.get());
            downloadInsuranceDocDetails(contentUrl, new Date(), new Date(), lendingApplication.get().getId(), lendingApplication.get().getLender(), lendingLoanInsurance);
        }

        return ResponseEntity.ok(new ApiResponse<>(true, "Insurance document processed successfully")); // 200 OK
    }

    public void saveInsuranceDocDetails(String base64Image, Date commencementDate, Date maturityDate, Long applicationId, String lender) {
        try {
            LendingLoanInsurance loanInsurance = lendingLoanInsuranceDao.findByApplicationIdAndLenderAndStatus(applicationId, lender, SELECTED);
            if (ObjectUtils.isEmpty(loanInsurance)) {
                log.info("No insurance details found with lender {} for application Id: {}", lender, applicationId);
                return;
            }
            String fileName = INSURANCE_POLICY_DOC_PREFIX + applicationId;

            byte[] docBytes = Base64.getDecoder().decode(base64Image);
            InputStream inputStream = new ByteArrayInputStream(docBytes);

            s3BucketHandler.uploadToS3PdfBucket(inputStream, fileName, bucket);
            String docUrl = s3BucketHandler.getPreSignedPublicURL(fileName, bucket);
            String docShortUrl = apiGatewayService.getShortUrl(docUrl);

            loanInsurance.setPolicyDocFile(fileName);
            loanInsurance.setPolicyDocUrl(docShortUrl);
            loanInsurance.setCommencementDate(commencementDate);
            loanInsurance.setMaturityDate(maturityDate);
            lendingLoanInsuranceDao.save(loanInsurance);
        } catch (Exception e) {
            log.error("Exception in saving {} insurance doc details for applicationId {} {}", lender, applicationId, Arrays.asList(e.getStackTrace()));
        }
    }

    public void downloadInsuranceDocDetails(String imageUrl, Date commencementDate, Date maturityDate, Long applicationId, String lender, LendingLoanInsurance loanInsurance) {
        try {
            if (ObjectUtils.isEmpty(imageUrl)) {
                log.warn("No insurance document URL found from lender {} for application Id: {}", lender, applicationId);
                return;
            }

            if (ObjectUtils.isEmpty(loanInsurance)) {
                log.info("No insurance details found with lender {} for application Id: {}", lender, applicationId);
                return;
            }
            String fileName = INSURANCE_POLICY_DOC_PREFIX + applicationId;

            // Download the file from the provided URL
            InputStream inputStream = downloadFileFromUrl(imageUrl);

            // Upload the file to S3
            s3BucketHandler.uploadToS3PdfBucket(inputStream, fileName, bucket);
            String docUrl = s3BucketHandler.getPreSignedPublicURL(fileName, bucket);
            String docShortUrl = apiGatewayService.getShortUrl(docUrl);

            // Calculate maturityDate
            LocalDate commencementLocalDate = commencementDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate maturityLocalDate = commencementLocalDate.plusMonths(loanInsurance.getPolicyTermsInMonths());
            maturityDate = Date.from(maturityLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

            loanInsurance.setPolicyDocFile(fileName);
            loanInsurance.setPolicyDocUrl(docShortUrl);
            loanInsurance.setCommencementDate(commencementDate);
            loanInsurance.setMaturityDate(maturityDate);
            lendingLoanInsuranceDao.save(loanInsurance);
        } catch (Exception e) {
            log.error("Exception in saving {} insurance doc details for applicationId {} {}", lender, applicationId, Arrays.asList(e.getStackTrace()));
        }
    }

    private InputStream downloadFileFromUrl(String fileUrl) throws IOException {
        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(HttpMethod.GET.name());
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            log.info("Successfully downloaded file from URL: {}", fileUrl);
            return connection.getInputStream();
        } else {
            log.error("Failed to download file from URL: {} with response code: {}", fileUrl, connection.getResponseCode());
            throw new IOException("Failed to download file from URL: " + fileUrl + " with response code: " + connection.getResponseCode());
        }
    }

    public LendingLoanInsurance getInsuranceDetails(Long applicationId, String lender, String status) {
        LendingLoanInsurance lendingLoanInsurance = lendingLoanInsuranceDao.findByApplicationIdAndLenderAndStatus(applicationId, lender, status);
        return lendingLoanInsurance;
    }

    public InsuranceDetailsDTO fetchInsuranceDetailsForApplication(LendingApplicationSlave application) {
        try {
            LendingLoanInsurance lendingLoanInsurance = getInsuranceDetails(application.getId(), application.getLender(), SELECTED);
            if (!ObjectUtils.isEmpty(lendingLoanInsurance)) {
                String policyDocUrl = null;
                if (!ObjectUtils.isEmpty(lendingLoanInsurance.getPolicyDocFile())) {
                    policyDocUrl = s3BucketHandler.getPreSignedPublicURL(lendingLoanInsurance.getPolicyDocFile(), bucket);
                }
                return InsuranceDetailsDTO.builder()
                        .sumInsured(lendingLoanInsurance.getSumInsured())
                        .insurancePremiumAmount(lendingLoanInsurance.getInsurancePremium())
                        .insuranceProviderName(lendingLoanInsurance.getProvider())
                        .insuranceAvailedDate(lendingLoanInsurance.getCommencementDate())
                        .insuranceApplicable(true)
                        .insuranceDocument(policyDocUrl)
                        .benefitsOfTheInsurance(getInsuranceBenefits(application.getLender()))
                        .insurancePartnerContactDetails(getInsurancePartnerContactDetails(application.getLender()))
                        .build();
            }
        } catch (Exception e) {
            log.error("Exception in fetching insurance details for application {} {}", application.getId(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String getInsuranceBenefits(String lender) {
        switch (Lender.valueOf(lender.toUpperCase())) {
            case PIRAMAL:
                return insuranceConfig.piramalBenefits;
            case PAYU:
                return insuranceConfig.payUBenefits;
            case MUTHOOT:
                return insuranceConfig.muthootBenefits;
            // Add more cases as needed
            default:
                return null;
        }
    }

    private Map<String, String> getInsurancePartnerContactDetails(String lender) {
        switch (Lender.valueOf(lender.toUpperCase())) {
            case PIRAMAL:
                return insuranceConfig.piramalInsuranceContactDetails;
            case PAYU:
                return insuranceConfig.payUInsuranceContactDetails;
            case MUTHOOT:
                return insuranceConfig.muthootInsuranceContactDetails;
            // Add more cases as needed
            default:
                return null;
        }
    }


}
