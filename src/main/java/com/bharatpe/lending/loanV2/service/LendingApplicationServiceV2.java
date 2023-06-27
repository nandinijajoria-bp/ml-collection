package com.bharatpe.lending.loanV2.service;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.constants.Constants;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.entity.LendingApplicationKycDetails;
import com.bharatpe.lending.common.enums.EdiModel;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.common.service.ILenderAssignService;
import com.bharatpe.lending.common.service.LenderAssignmentRandom;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.common.Constants.BusinessCategories;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.AddLeadRequestNimbusDto;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.service.CallingLeadNimbusService;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.KfsConstants;
import com.bharatpe.lending.constant.OfferDowngradeApplication;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.entity.LmsStageHistory;
import com.bharatpe.lending.entity.LoanDowngradeConfigEntity;
import com.bharatpe.lending.enums.*;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.handlers.MerchantSummaryExceptionHandler;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV2.dto.*;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.LenderMappingService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.service.LendingEdiScheduleService;
import com.bharatpe.lending.service.impl.LenderAssignService;
import com.bharatpe.lending.service.CleverTapEventService;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.bharatpe.lending.constant.KfsConstants.*;

@Service
@Slf4j
public class LendingApplicationServiceV2 {
    @Autowired
    private LmsStageHistoryDao lmsStageHistoryDao;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingResubmitTaskDao lendingResubmitTaskDao;

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

//    @Autowired
//    OrderStickerDaoSlave orderStickerDaoSlave;

    @Autowired
    LendingDisbursalStageDao lendingDisbursalStageDao;

    @Autowired
    Environment env;

    @Autowired
    LendingShopDocumentsDao lendingShopDocumentsDao;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    LendingApplicationPriorityDao lendingApplicationPriorityDao;

    @Autowired
    LendingMerchantDetailsDao lendingMerchantDetailsDao;

    @Autowired
    DateTimeUtil dateTimeUtil;

    @Autowired
    CallingLeadNimbusService callingLeadNimbusService;

    @Autowired
    CallingLeadResponseNimbusDao callingLeadResponseNimbusDao;

//    @Autowired
//    MerchantDao merchantDao;

    @Autowired
    LendingCache lendingCache;

    @Autowired
    MerchantSummaryHandler merchantSummaryHandler;

    ExecutorService executorService = Executors.newFixedThreadPool(10);
    @Autowired
    MerchantService merchantService;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    LendingEdiScheduleService lendingEdiScheduleService;

    @Autowired
    LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;

    @Value("${kyc.revalidation.deeplink}")
    String kycRevalidationDeeplink;

    @Value("${kyc.deeplink}")
    String kycDeepLink;

    @Value("${loan.details.refresh.window:15}")
    int loanDetailsRefreshWindow;

    @Autowired
    LenderAssignService lenderAssignService;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    CleverTapEventService cleverTapEventService;

    @Autowired
    FunnelService funnelService;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    LoanDowngradeConfigDao loanDowngradeConfigDao;

    @Autowired
    LendingResubmitReasonCountDao lendingResubmitReasonCountDao;

    @Value("${downgrade.config.version:1.1}")
    double downgradeConfigVersion;


    public ApiResponse<?> initiateKyc(BasicDetailsDto merchant, InitiateKycRequest initiateKycRequest) {
        try {
            if (Objects.isNull(merchant.getId())) {
                log.info("merchantId not found");
                return new ApiResponse<>(false, "MerchantID not found");
            }
            executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.LOAN_KYC_INITIATED_BE.name(), null, merchant.getMid()));
            funnelService.submitEvent(merchant.getId(), null, initiateKycRequest.getApplicationId(),
                    FunnelEnums.StageId.KYC, FunnelEnums.StageEvent.INITIATED, LocalDateTime.now().toString());
            cacheInitiateKycCall(merchant.getId(), loanDetailsRefreshWindow);
            String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + merchant.getId();
            log.info("deleting cached key of loan details in create application for merchant: {}", merchant.getId());
            lendingCache.delete(loanDetailsCacheKey);

            Experian experian = experianDao.getByMerchantId(merchant.getId());
            if (experian == null || experian.getPancardNumber() == null) {
                return new ApiResponse<>(false, "Pancard does not exist");
            }
            boolean newMerchantFirstCall = false;
            boolean newLoanFirstCall = false;
            Date validAfterDate = null;
            LendingApplicationKycDetails lendingApplicationKycDetails = null;
            if(Objects.nonNull(initiateKycRequest.getApplicationId())){
                log.info("Table entry fetched from applicationId for : {}", merchant.getId());
                lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(initiateKycRequest.getApplicationId());
            }
            if(ObjectUtils.isEmpty(lendingApplicationKycDetails)){
                log.info("Table entry fetched from merchantId for : {}", merchant.getId());
                lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
            }
            if (ObjectUtils.isEmpty(lendingApplicationKycDetails)) {
                //New merchant applying for loan
                log.info("New merchant applying for loan with id : {}", merchant.getId());
                newMerchantFirstCall = true;
                LendingApplicationKycDetails lendingApplicationKycDetails1 = new LendingApplicationKycDetails();
                lendingApplicationKycDetails1.setMerchantId(merchant.getId());
                if(Objects.nonNull(initiateKycRequest.getApplicationId()))lendingApplicationKycDetails1.setApplicationId(initiateKycRequest.getApplicationId());
                lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails1);
                lendingApplicationKycDetails = lendingApplicationKycDetails1;
                validAfterDate = lendingApplicationKycDetails1.getCreatedAt();
            } else {
                if (lendingApplicationKycDetails.getApplicationId() == 0) {
                    log.info("application id unavailable in kyc table for merchant : {}", merchant.getId());
                    validAfterDate = lendingApplicationKycDetails.getCreatedAt();
                    log.info("setting validAfter date : {} for merchant : {}", validAfterDate.toString(), merchant.getId());
                    if (initiateKycRequest.getApplicationId() != null) {
                        try {
                            log.info("saving application details in kyc table for merchant : {}", merchant.getId());
                            saveKycDetails(initiateKycRequest.getApplicationId(), lendingApplicationKycDetails);
                        } catch (Exception e) {
                            log.error("Unable to save application details to Kyc table for : {}, {}, {}", initiateKycRequest.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
                            new ApiResponse<>(false, "Unable to save application details to Kyc table");
                        }
                    }
                } else {
                    if ((initiateKycRequest.getApplicationId() == null) || (lendingApplicationKycDetails.getApplicationId() != initiateKycRequest.getApplicationId())) {
                        //merchant has applied previously before
                        log.info("making a new entry in table for : {}", merchant.getId());
                        newLoanFirstCall = true;
                        LendingApplicationKycDetails lendingApplicationKycDetails1 = new LendingApplicationKycDetails();
                        lendingApplicationKycDetails1.setMerchantId(merchant.getId());
                        lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails1);
                        lendingApplicationKycDetails = lendingApplicationKycDetails1;
                        validAfterDate = lendingApplicationKycDetails1.getCreatedAt();
                    } else {
                        validAfterDate = lendingApplicationKycDetails.getCreatedAt();
                    }
                }
            }
            if(Objects.isNull(lendingApplicationKycDetails.getKycInitiatedAt())){
                lendingApplicationKycDetails.setKycInitiatedAt(new Date());
                lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails);
            }
            boolean selfieValid = false;
            boolean aadharValid = false;
            boolean aadharDigilocker = false;
            boolean panCardApproved = false;
            boolean panNoApproved = false;
            List<KycDoc> kycDocs = kycHandler.getKycDoc(merchant.getId(), validAfterDate, LendingConstants.POA_PROVIDER);

//            if (ObjectUtils.isEmpty(kycDocs)) {
//                log.info("Unable to fetch KYC Docs for id : {}, merchantId : {}", initiateKycRequest.getApplicationId(), merchant.getId());
//                return new ApiResponse<>(false, "Unable to fetch KYC Docs");
//            }

            log.info("KYC doc fetched for : {}", merchant.getId());
            for (KycDoc kycDoc : kycDocs) {
                if (kycDoc.getDocType() != null && KycDocType.SELFIE.equals(kycDoc.getDocType()) && KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
                    lendingApplicationKycDetails.setSelfieUrl(kycDoc.getDocFrontImageUrl());
                    if(Objects.isNull(lendingApplicationKycDetails.getSelfieApprovedAt()))lendingApplicationKycDetails.setSelfieApprovedAt(new Date());
                    selfieValid = true;
                    log.info("Selfie is valid for : {}", merchant.getId());
                } else if (kycDoc.getDocType() != null && KycDocType.POA.equals(kycDoc.getDocType()) && KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
                    aadharValid = true;
                    log.info("Aadhar is valid for : {}", merchant.getId());
                    if (kycDoc.getSubDocType() != null && KycDocType.EKYC.equals(kycDoc.getSubDocType())) {
                        lendingApplicationKycDetails.setAadharIdentifier(kycDoc.getDocIdentifier());
                        lendingApplicationKycDetails.setAadharAddress(kycDoc.getAddress());
                        if(Objects.isNull(lendingApplicationKycDetails.getAadharApprovedAt()))lendingApplicationKycDetails.setAadharApprovedAt(new Date());
                        if (!ObjectUtils.isEmpty(kycDoc.getDigioXml())) {
                            lendingApplicationKycDetails.setAadharXml(kycDoc.getDigioXml());
                        }
                        aadharDigilocker = true;
                        log.info("Aadhar is digilocker approved for : {}", merchant.getId());
                    }
                } else if (kycDoc.getDocType() != null && KycDocType.PAN_CARD.equals(kycDoc.getDocType()) && KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
                    lendingApplicationKycDetails.setPanUrl(kycDoc.getDocFrontImageUrl());
                    if(Objects.isNull(lendingApplicationKycDetails.getPanApprovedAt()))lendingApplicationKycDetails.setPanApprovedAt(new Date());
                    panCardApproved = true;
                    log.info("Pan Card is valid for : {}", merchant.getId());
                } else if (kycDoc.getDocType() != null && KycDocType.PAN_NO.equals(kycDoc.getDocType()) && KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
                    lendingApplicationKycDetails.setPan(kycDoc.getDocIdentifier());
                    panNoApproved = true;
                    log.info("Pan No is valid for : {}", merchant.getId());
                }
            }
            lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails);
            if (selfieValid && aadharValid && aadharDigilocker && panCardApproved && panNoApproved) {
                lendingApplicationKycDetails.setConsentDate(new Date());
                lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails);
                log.info("Kyc details verified for merchant : {}", merchant.getId());
                executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.LOAN_KYC_VERIFIED_BE.name(), null, merchant.getMid()));
                funnelService.submitEvent(merchant.getId(), null, initiateKycRequest.getApplicationId(),
                        FunnelEnums.StageId.KYC, FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString());
                return new ApiResponse<>(kycDeepLink);
            }
            List<KycDocType> docTypes = new ArrayList<>();
            if (!panCardApproved) {
                docTypes.add(KycDocType.PAN_CARD);
                docTypes.add(KycDocType.PAN_NO);
            }
            docTypes.add(KycDocType.SELFIE);
            docTypes.add(KycDocType.EKYC);
            String callBackURL = env.getProperty("kyc.loan.deeplink");
            if (!StringUtils.isEmpty(initiateKycRequest.getWroute())) {
                callBackURL += "&wroute=" + initiateKycRequest.getWroute();
            }
            InitiateKycDTO initiateKycDTO = InitiateKycDTO.builder()
                    .referenceId(initiateKycRequest.getApplicationId() != null ? String.valueOf(initiateKycRequest.getApplicationId()) : String.valueOf(merchant.getId()))
                    .panNumber(experian.getPancardNumber())
                    .callBackUrl(callBackURL)
                    .merchantId(String.valueOf(merchant.getId())).build();
            Map<String, String> ckycResponseObj = kycHandler.initiateKyc(merchant.getId(), initiateKycDTO, docTypes, validAfterDate);
            if (ckycResponseObj.containsKey("ckycId")) {
                if (initiateKycRequest.getApplicationId() != null) {
                    lendingApplicationDao.updateKycId(initiateKycRequest.getApplicationId(), ckycResponseObj.get("ckycId"), merchant.getId());
                }
                if (newMerchantFirstCall || newLoanFirstCall) return new ApiResponse<>(kycDeepLink);
                return new ApiResponse<>(kycRevalidationDeeplink);
            }
            log.error("Uanble to initiate kyc for merchant : {}", merchant.getId());
            return new ApiResponse<>(false, ckycResponseObj.get("message"));
        }
        catch(Exception ex){
            log.error("Exception while initiating kyc for merchant:{} {} {}", merchant.getId(), ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            return new ApiResponse<>(false, "Something went wrong");
        }
    }

    private void cacheInitiateKycCall(Long merchantId, int ttl){
        AddCacheDto addCacheDto = new AddCacheDto();
        String key = LendingConstants.INITIATE_KYC_CACHE_KEYWORD + merchantId;
        Boolean initiateKycCalled = true;
        addCacheDto.setKey(key);
        addCacheDto.setValue(initiateKycCalled);
        addCacheDto.setTtl(ttl);
        lendingCache.add(addCacheDto, TimeUnit.MINUTES);
        log.info("Initiate KYC call cached with Key : {}", key);
    }


//    public void saveKycDetails(LendingApplicationKycDetails lendingApplicationKycDetails, List<KycDoc> kycDocs) throws Exception {
//        Date currDate = dateTimeUtil.getCurrentDate();
//        if(ObjectUtils.isEmpty(lendingApplicationKycDetails))throw new Exception("Unable to fetch lending application kyc details");
//        lendingApplicationKycDetails.setConsentDate(currDate);
//        for(KycDoc kycDoc : kycDocs) {
//            if (KycDocType.POA.equals(kycDoc.getDocType())) {
//                lendingApplicationKycDetails.setAadharIdentifier(kycDoc.getDocIdentifier());
//                lendingApplicationKycDetails.setAadharAddress(kycDoc.getAddress());
//                if (!ObjectUtils.isEmpty(kycDoc.getXml())) {
//                    lendingApplicationKycDetails.setAadharXml(kycDoc.getXml());
//                } else if (!ObjectUtils.isEmpty(kycDoc.getDigioXml())) {
//                    lendingApplicationKycDetails.setAadharXml(kycDoc.getDigioXml());
//                } else lendingApplicationKycDetails.setAadharXml(kycDoc.getDocFrontImageUrl());
//                lendingApplicationKycDetails.setAadharApprovedAt(currDate);
//            } else if (KycDocType.SELFIE.equals(kycDoc.getDocType())) {
//                lendingApplicationKycDetails.setSelfieUrl(kycDoc.getDocFrontImageUrl());
//                lendingApplicationKycDetails.setSelfieApprovedAt(currDate);
//            } else if (KycDocType.PAN_CARD.equals(kycDoc.getDocType())) {
//                lendingApplicationKycDetails.setPanUrl(kycDoc.getDocFrontImageUrl());
//                lendingApplicationKycDetails.setPanApprovedAt(currDate);
//            } else if (KycDocType.PAN_NO.equals(kycDoc.getDocType())) {
//                lendingApplicationKycDetails.setPan(kycDoc.getDocIdentifier());
//            }
//        }
//        lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails);
//
//    }

    public void saveKycDetails(Long applicationId, LendingApplicationKycDetails lendingApplicationKycDetails) throws Exception {
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, lendingApplicationKycDetails.getMerchantId());
        if(ObjectUtils.isEmpty(lendingApplication))throw new Exception("Unable to fetch application details for {}" + applicationId);
        lendingApplicationKycDetails.setApplicationId(lendingApplication.getId());
        lendingApplicationKycDetails.setLender(lendingApplication.getLender());
        lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails);
    }

    public ApiResponse<?> createApplication(BasicDetailsDto merchant, CreateApplicationRequest applicationRequest) {
        if(Objects.nonNull(merchant.getId())) {
            String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + merchant.getId();
            log.info("deleting cached key of loan details in create application for merchant: {}",merchant.getId());
            lendingCache.delete(loanDetailsCacheKey);
        } else {
            log.info("merchant id not found in create application");
        }
        if (applicationRequest.getApplicationId() == null) {
            return createNewApplication(merchant, applicationRequest);
        } else {
            return updateApplication(merchant, applicationRequest);
        }
    }

    private ApiResponse<?> updateApplication(BasicDetailsDto merchant, CreateApplicationRequest applicationRequest) {
        log.info("updating existing application:{} for merchant:{}", applicationRequest.getApplicationId(), merchant.getId());
        try {
            LendingApplication lendingApplication =
              lendingApplicationDao.findByIdAndMerchantIdAndStatus(applicationRequest.getApplicationId(), merchant.getId(),
                "draft");
            if (lendingApplication == null) {
                LendingResubmitTask lendingResubmitTask = lendingResubmitTaskDao.findTopByApplicationId(applicationRequest.getApplicationId());
                if(lendingResubmitTask != null && lendingResubmitTask.getResubmit() && !lendingResubmitTask.getResubmitDone()){
                    lendingApplication = lendingApplicationDao.findById(applicationRequest.getApplicationId()).get();
                    if(lendingApplication==null){
                        log.info("Application not found for id:{}", applicationRequest.getApplicationId());
                    }
                    lendingApplication.setBusinessName(applicationRequest.getBusinessName());
                    if (applicationRequest.getAddressDetails() != null) {
                        AddressDetails addressDetails = applicationRequest.getAddressDetails();
                        lendingApplication.setPincode(!StringUtils.isEmpty(addressDetails.getPincode()) ? Long.valueOf(addressDetails.getPincode()) : lendingApplication.getPincode());
                        lendingApplication.setArea(!StringUtils.isEmpty(addressDetails.getArea()) ? addressDetails.getArea() : lendingApplication.getArea());
                        lendingApplication.setCity(!StringUtils.isEmpty(addressDetails.getCity()) ? addressDetails.getCity() : lendingApplication.getCity());
                        lendingApplication.setState(!StringUtils.isEmpty(addressDetails.getState()) ? addressDetails.getState() : lendingApplication.getState());
                        lendingApplication.setShopNumber(!StringUtils.isEmpty(addressDetails.getAddress1()) ?
                                addressDetails.getAddress1().substring(0, Math.min(addressDetails.getAddress1().length(), 98)) : lendingApplication.getShopNumber());
                        lendingApplication.setStreetAddress(!StringUtils.isEmpty(addressDetails.getAddress2()) ? addressDetails.getAddress2() : lendingApplication.getStreetAddress());
                        lendingApplication.setLandmark(!StringUtils.isEmpty(addressDetails.getLandmark()) ? addressDetails.getLandmark() : lendingApplication.getLandmark());
                        log.info("shop address updated in lending_application: {}", applicationRequest.getApplicationId());
                    }

                    lendingApplicationDao.save(lendingApplication);
                    log.info("Application Resubmit With Business Name, Shop Address for application id:{}", applicationRequest.getApplicationId());
                    return new ApiResponse<>(CreateApplicationResponse.builder().applicationId(lendingApplication.getId()).build());
                }
                log.info("Draft application not found for id:{}", applicationRequest.getApplicationId());
                return new ApiResponse<>(false, "Draft application not found");
            }
            AddressValidationDto addressValidationDto = null;
            if (isAddressUpdated(lendingApplication,applicationRequest)) {
                addressValidationDto = getAddressValidationScore(applicationRequest);
                if (addressQltyScoreLessThanThreshold(addressValidationDto)) {
                    log.info("address quality score less than 20");
                    return new ApiResponse<>(ApplicationAddressValidation.builder().hasAValidAddress(false).build());
                }
            }
            updateApplicationData(lendingApplication, applicationRequest, addressValidationDto);
            return new ApiResponse<>(CreateApplicationResponse.builder().applicationId(lendingApplication.getId()).build());
        } catch (Exception e) {
            log.error("Exception in updateApplication for merchant:{} {}", merchant.getId(), e.getMessage());
            return new ApiResponse<>(false, "Something went wrong");
        }
    }

    private ApiResponse<?> createNewApplication(BasicDetailsDto merchant, CreateApplicationRequest applicationRequest) {
        log.info("creating new application for merchant:{}", merchant.getId());
        try {
            AddressValidationDto addressValidationDto = getAddressValidationScore(applicationRequest);
            String error = baseChecks(merchant, applicationRequest);
            if (error != null) return new ApiResponse<>(false, error);
            if (addressQltyScoreLessThanThreshold(addressValidationDto)) {
                log.info("address quality score less than 20");
                return new ApiResponse<>(ApplicationAddressValidation.builder().hasAValidAddress(false).build());
            }
            EligibleLoan eligibleLoan = eligibleLoanDao.findTopByMerchantIdAndOfferTypeOrderByIdDesc(merchant.getId(), "CUSTOM");
//            LendingCategories lendingCategory = lendingCategoryDao.getByCategory(applicationRequest.getCategory());
            if (Objects.isNull(eligibleLoan)) {
                log.info("eligible loan not available for merchant:{} and category:{}", merchant.getId(), applicationRequest.getCategory());
                return new ApiResponse<>(false, "eligible loan not found");
            }
            LendingApplication lendingApplication = saveLendingApplication(merchant, eligibleLoan, applicationRequest, null, addressValidationDto);
            loanUtil.createApplicationSnapshot(lendingApplication, merchant);
            createStatusAuditTrail(lendingApplication);
            executorService.submit(() -> {
                loanUtil.callingDeForReferences(merchant.getId(),lendingApplication);
            });
            loanUtil.publishApplicationEvent(lendingApplication);
            funnelService.submitEvent(merchant.getId(), null, lendingApplication.getId(),
                    FunnelEnums.StageId.APPLICATION, FunnelEnums.StageEvent.INITIATED, LocalDateTime.now().toString());
            return new ApiResponse<>(CreateApplicationResponse.builder().applicationId(lendingApplication.getId()).build());
        } catch (Exception e) {
            log.error("Exception in createNewApplication for merchant:{} {} {}", merchant.getId(), e.getMessage(), e);
            return new ApiResponse<>(false, "Something went wrong");
        }
    }

    private AddressValidationDto getAddressValidationScore(CreateApplicationRequest createApplicationRequest) {
        AddressValidationDto addressValidationDto = null;
        try {
            if (!ObjectUtils.isEmpty(createApplicationRequest.getAddressDetails())) {
                addressValidationDto = apiGatewayService.validateAddress(createApplicationRequest.getAddressDetails());
            }
        } catch (Exception e) {
            log.error("error occured while validating address: {}", e);
        }
        return addressValidationDto;
    }

    private void createStatusAuditTrail(LendingApplication lendingApplication) {
        LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
        lendingAuditTrial.setMerchantId(lendingApplication.getMerchantId());
        lendingAuditTrial.setApplicationId(lendingApplication.getId());
        lendingAuditTrial.setLoanId("");
        lendingAuditTrial.setUserId(Long.parseLong("0"));
        lendingAuditTrial.setNewStatus("draft");
        lendingAuditTrial.setType("APP_STATUS");
        lendingAuditTrialDao.save(lendingAuditTrial);
    }

    private LendingApplication saveLendingApplication(BasicDetailsDto merchantBasicDetails, EligibleLoan eligibleLoan, CreateApplicationRequest lendingApplicationRequest, LendingCategories lendingCategory, AddressValidationDto addressValidationDto) {
        LendingApplication lendingApplication = new LendingApplication();
        int processingFee;
        if (apiGatewayService.eligibleForProcessingFee(merchantBasicDetails.getId())) {
            processingFee = 0;
        } else {
            processingFee = eligibleLoan.getProcessingFee();
        }

//        Merchant merchant = merchantDao.getById(merchantBasicDetails.getId());

        lendingApplication.setMerchantName(merchantBasicDetails.getBeneficiaryName());
        lendingApplication.setEdi(Double.valueOf(eligibleLoan.getEdi()));
        lendingApplication.setIoEdi(eligibleLoan.getIoEdi() != null ? Double.valueOf(eligibleLoan.getIoEdi()) : 0D);
        lendingApplication.setRepayment(Double.valueOf(eligibleLoan.getRepayment()));
        lendingApplication.setInterestRate(eligibleLoan.getRateOfInterest());
        lendingApplication.setProcessingFee(Double.valueOf(processingFee));
        lendingApplication.setDisbursalAmount(eligibleLoan.getAmount() - processingFee);
        lendingApplication.setStatus("draft");
        lendingApplication.setMode("AUTO");
        lendingApplication.setMerchantId(merchantBasicDetails.getId());
        lendingApplication.setLoanAmount(eligibleLoan.getAmount());
        lendingApplication.setCategory(eligibleLoan.getCategory());
        lendingApplication.setTenure(eligibleLoan.getTenure());
        lendingApplication.setTenureInMonths(eligibleLoan.getTenureInMonths());
        lendingApplication.setPayableDays(Long.valueOf(eligibleLoan.getEdiCount()));
        lendingApplication.setEdiFreeDays(0);
        lendingApplication.setIoPayableDays(eligibleLoan.getIoEdiDays());
        lendingApplication.setLoanConstruct(eligibleLoan.getLoanConstruct());
        lendingApplication.setLoanType(eligibleLoan.getLoanType());
        lendingApplication.setTotalLoansCount(loanUtil.getPreviousLoans(merchantBasicDetails.getId()).size());
        lendingApplication.setCkycId(String.valueOf(merchantBasicDetails.getId()));
        lendingApplication.setLatitude(!StringUtils.isEmpty(lendingApplicationRequest.getLatitude()) ? lendingApplicationRequest.getLatitude() : null);
        lendingApplication.setLongitude(!StringUtils.isEmpty(lendingApplicationRequest.getLongitude()) ? lendingApplicationRequest.getLongitude() : null);
        lendingApplication.setBusinessName(lendingApplicationRequest.getBusinessName());
        lendingApplication.setEdiFreeDays(eligibleLoan.getEdiCount() % 30 == 0 ? 0 : 1);
        lendingApplication = lendingApplicationDao.save(lendingApplication);

        if (loanUtil.isInternalMerchant(merchantBasicDetails.getId()) || (eligibleLoan.getEdiCount() % 30 == 0)) {
            DateFormat df = new SimpleDateFormat("ddMMyy");
            Date dateobj = new Date();
            String loanId = "BPL" + df.format(dateobj) + lendingApplication.getId();
            lendingApplication.setExternalLoanId(loanId);
            lendingApplication = lendingApplicationDao.save(lendingApplication);
        }

        LendingApplicationDetails lendingApplicationDetails = new LendingApplicationDetails();
        lendingApplicationDetails.setApplicationId(lendingApplication.getId());
        lendingApplicationDetails.setStage(LenderAssociationStages.INIT.name());
        lendingApplicationDetails.setEdiModelModified(false);
        lendingApplicationDetails.setLenderAssc(false);
        lendingApplicationDetails.setEdiModel(eligibleLoan.getEdiCount() % 30 == 0 ? EdiModel.SEVEN_DAY_MODEL.name() : EdiModel.SIX_DAY_MODEL.name());
        lendingApplicationDetails.setIsNachSkip(loanUtil.isEligibleForNachSkip(lendingApplication, lendingApplication.getLender()));
        lendingApplicationDetailsDao.save(lendingApplicationDetails);
        lenderAssignService.assignLender(lendingApplication, eligibleLoan.getEdiCount() % 30 == 0 ?
                EdiModel.SEVEN_DAY_MODEL : EdiModel.SIX_DAY_MODEL, merchantBasicDetails);

//        log.info("existing lender {} now changed to ABFL for {}", lendingApplication.getLender(), lendingApplication.getId());
//        lendingApplication.setLender("ABFL");
//        lendingApplication = lendingApplicationDao.save(lendingApplication);
        updateApplicationData(lendingApplication, lendingApplicationRequest, addressValidationDto);
        replicateApplicationData(lendingApplication);
        log.info("saved lending application details for  {}", lendingApplicationDetails);
        executorService.execute(() -> apiGatewayService.globalLimitTxn(merchantBasicDetails.getId(), "DEBIT", eligibleLoan.getAmount()));
        executorService.execute(() -> {
            JsonNode smsAnalysisData = apiGatewayService.getMerchantSmsAnalysisData(merchantBasicDetails);
            if (smsAnalysisData == null) {
                loanUtil.publishSmsAnalysisData(merchantBasicDetails);
            }
        });
        return lendingApplication;
    }

    private void replicateApplicationData(LendingApplication lendingApplication) {
        try {
            LendingApplication prevApplication = lendingApplicationDao.getLastDisbursedLoan(lendingApplication.getMerchantId());
            if (prevApplication != null) {
                log.info("Replicating application for merchant:{} and previous application:{}", lendingApplication.getMerchantId(), prevApplication.getId());
                LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(prevApplication.getId());
                if (lendingGstDetail != null) {
                    LendingGstDetail replicateGst = lendingGstDao.findByApplicationId(lendingApplication.getId());
                    if (replicateGst == null) {
                        replicateGst = new LendingGstDetail();
                        replicateGst.setApplicationId(lendingApplication.getId());
                        replicateGst.setMerchantId(lendingApplication.getMerchantId());
                        replicateGst.setAddressQlty(lendingGstDetail.getAddressQlty());
                        replicateGst.setAddressQltyScore(lendingGstDetail.getAddressQltyScore());
                    }
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
                List<LendingShopDocuments> lendingShopDocuments = lendingShopDocumentsDao.findByMerchantIdAndLendingApplicationId(prevApplication.getMerchantId(), prevApplication.getId());
                if (!lendingShopDocuments.isEmpty()) {
                    for (LendingShopDocuments shopDocuments : lendingShopDocuments) {
                        LendingShopDocuments replicateShopDocument = new LendingShopDocuments();
                        replicateShopDocument.setApplicationId(lendingApplication.getId());
                        replicateShopDocument.setMerchantId(lendingApplication.getMerchantId());
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

    private void updateApplicationData(LendingApplication lendingApplication, CreateApplicationRequest applicationRequest, AddressValidationDto addressValidationDto) {
        try {
            if (applicationRequest.getAddressDetails() != null) {
                AddressDetails addressDetails = applicationRequest.getAddressDetails();
                lendingApplication.setPincode(!StringUtils.isEmpty(addressDetails.getPincode()) ? Long.valueOf(addressDetails.getPincode()) : lendingApplication.getPincode());
                lendingApplication.setArea(!StringUtils.isEmpty(addressDetails.getArea()) ? addressDetails.getArea() : lendingApplication.getArea());
                lendingApplication.setCity(!StringUtils.isEmpty(addressDetails.getCity()) ? addressDetails.getCity() : lendingApplication.getCity());
                lendingApplication.setState(!StringUtils.isEmpty(addressDetails.getState()) ? addressDetails.getState() : lendingApplication.getState());
                lendingApplication.setShopNumber(!StringUtils.isEmpty(addressDetails.getAddress1()) ?
                        addressDetails.getAddress1().substring(0, Math.min(addressDetails.getAddress1().length(), 98)) : lendingApplication.getShopNumber());
                lendingApplication.setStreetAddress(!StringUtils.isEmpty(addressDetails.getAddress2()) ? addressDetails.getAddress2() : lendingApplication.getStreetAddress());
                lendingApplication.setLandmark(!StringUtils.isEmpty(addressDetails.getLandmark()) ? addressDetails.getLandmark() : lendingApplication.getLandmark());
                log.info("shop number getting saved in lending_application: {}", !StringUtils.isEmpty(addressDetails.getAddress1()) ? addressDetails.getAddress1() : lendingApplication.getShopNumber());
            }
            if (applicationRequest.getAdditionalDetails() != null) {
                AdditionalDetails additionalDetails = applicationRequest.getAdditionalDetails();
                lendingApplication.setEmail(!StringUtils.isEmpty(additionalDetails.getEmail()) ? additionalDetails.getEmail() : lendingApplication.getEmail());
                lendingApplication.setAlternateMobile(!StringUtils.isEmpty(additionalDetails.getAlternateContact()) ? additionalDetails.getAlternateContact() : lendingApplication.getAlternateMobile());
            }
            if (applicationRequest.getProfessionalDetails() != null) {
                saveGstDetails(lendingApplication, applicationRequest.getProfessionalDetails());
            }
            saveAddressQltyDetails(lendingApplication,addressValidationDto);
            lendingApplication.setBusinessName(!StringUtils.isEmpty(applicationRequest.getBusinessName()) ? applicationRequest.getBusinessName() : lendingApplication.getBusinessName());
            lendingApplicationDao.save(lendingApplication);
        } catch (Exception e) {
            log.error("Exception in updateApplicationData for application:{} , {} {} {}", lendingApplication.getId(), applicationRequest, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }
    public boolean isAddressUpdated(LendingApplication lendingApplication, CreateApplicationRequest applicationRequest) {
        try {
            return !(!ObjectUtils.isEmpty(applicationRequest.getAddressDetails()) &&
                    (!ObjectUtils.isEmpty(lendingApplication.getShopNumber()) && lendingApplication.getShopNumber().equalsIgnoreCase(applicationRequest.getAddressDetails().getAddress1())) &&
                    (!ObjectUtils.isEmpty(lendingApplication.getStreetAddress()) && lendingApplication.getStreetAddress().equalsIgnoreCase(applicationRequest.getAddressDetails().getAddress2())) &&
                    (!ObjectUtils.isEmpty(lendingApplication.getLandmark()) && lendingApplication.getLandmark().equalsIgnoreCase(applicationRequest.getAddressDetails().getLandmark())) &&
                    (!ObjectUtils.isEmpty(lendingApplication.getPincode()) && lendingApplication.getPincode().toString().equalsIgnoreCase(applicationRequest.getAddressDetails().getPincode())) &&
                    (!ObjectUtils.isEmpty(lendingApplication.getCity()) && lendingApplication.getCity().equalsIgnoreCase(applicationRequest.getAddressDetails().getCity())) &&
                    (!ObjectUtils.isEmpty(lendingApplication.getState()) && lendingApplication.getState().equalsIgnoreCase(applicationRequest.getAddressDetails().getState())));
        } catch (Exception e) {
            log.error("exception occurred while comparing address for application : {}", applicationRequest.getApplicationId());
        }
        return true;
    }

    private void saveAddressQltyDetails(LendingApplication lendingApplication, AddressValidationDto addressValidationDto) {
        try {
            if (!ObjectUtils.isEmpty(addressValidationDto) && !ObjectUtils.isEmpty(addressValidationDto.getResult())) {
                LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(lendingApplication.getId());
                if (lendingGstDetail == null) {
                    lendingGstDetail = new LendingGstDetail();
                    lendingGstDetail.setMerchantId(lendingApplication.getMerchantId());
                    lendingGstDetail.setApplicationId(lendingApplication.getId());
                    lendingGstDetail.setGst(false);
                }
                lendingGstDetail.setAddressQlty(addressValidationDto.getResult().getAddressValidity());
                lendingGstDetail.setAddressQltyScore(addressValidationDto.getResult().getAddressQualityScore());
                lendingGstDao.save(lendingGstDetail);
            }
        } catch (Exception e) {
            log.error("exception occurred while saving application address quality: {}", e);
        }
    }

    private void saveGstDetails(LendingApplication lendingApplication, ProfessionalDetails professionalDetails) {
        try {
            LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(lendingApplication.getId());
            if (lendingGstDetail == null) {
                lendingGstDetail = new LendingGstDetail();
                lendingGstDetail.setMerchantId(lendingApplication.getMerchantId());
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

    private String baseChecks(BasicDetailsDto merchant, CreateApplicationRequest applicationRequest) {

        if(easyLoanUtil.isDummyMerchant(merchant.getId())) {
            return null;
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

        if (loanUtil.hasActiveLoan(merchant)){
            log.info("Already an ongoing loan exists for the merchant : {}", merchant.getId());
            return "Already an ongoing loan exists";
        }
        return null;
    }

    public ApiResponse<?> getAgreement(Long applicationId, BasicDetailsDto merchant) {
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantIdAndStatus(applicationId,
          merchant.getId(), "draft");
        LendingResubmitTask lendingResubmitTask =lendingResubmitTaskDao.findTopByApplicationId(applicationId);
        if(lendingApplication == null  && (Objects.isNull(lendingResubmitTask) || lendingResubmitTask.getDowngradeDone())) {
            log.info("Application not found for Id: {} for merchant : {}", applicationId, merchant.getId());
            return new ApiResponse<>(false, "Draft application not found");
        }
        if(lendingApplication == null && Objects.nonNull(lendingResubmitTask) && lendingResubmitTask.getDowngrade() != null && lendingResubmitTask.getDowngrade() && (lendingResubmitTask.getDowngradeDone() == null || !lendingResubmitTask.getDowngradeDone())){
            lendingApplication =lendingApplicationDao.findById(applicationId).get();
        }
        if (lendingApplication == null) {
            log.info("Draft application not found for id:{}", applicationId);
        }
        LendingCategories lendingCategories = lendingCategoryDao.getByCategory(lendingApplication.getCategory());
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
        AgreementResponse agreementResponse = AgreementResponse.builder()
                .applicationId(lendingApplication.getId())
                .lender(lendingApplication.getLender())
                .loanAmount(lendingApplication.getLoanAmount())
                .interestRate(lendingApplication.getInterestRate())
                .arrangerFee(lendingApplication.getProcessingFee().intValue())
                .disbursalAmount(lendingApplication.getDisbursalAmount())
                .tenure(lendingApplication.getTenure())
                .ediAmount(lendingApplication.getEdi().intValue())
                .ediCount(lendingApplication.getPayableDays().intValue())
                .bpClubMember(apiGatewayService.eligibleForProcessingFee(merchant.getId()))
                .clubV2(apiGatewayService.checkClubV2(merchant.getId()))
                .ediModelModified(!ObjectUtils.isEmpty(lendingApplicationDetails) && Optional.ofNullable(lendingApplicationDetails.getEdiModelModified()).orElse(false))
                .repayment(AgreementResponse.Repayment.builder()
                        .principal(lendingApplication.getLoanAmount())
                        .interest(lendingApplication.getRepayment() - lendingApplication.getLoanAmount())
                        .total(lendingApplication.getRepayment())
                        .build())
                .accountDetails(loanUtil.getAccountDetails(lendingApplication.getMerchantId())).build();
        return new ApiResponse<>(agreementResponse);
    }

    private List<EligibleLoan> fetchEligibleLoansForCreateApplication(Long merchantId, String category, String offerType) {
        if ("CUSTOM".equalsIgnoreCase(offerType) && !"SMALL_TICKET2".equalsIgnoreCase(category)) {
            return eligibleLoanDao.findByMerchantIdAndCategoryAndOfferType(merchantId, category, offerType);
        }
        return eligibleLoanDao.findByMerchantIdAndCategory(merchantId, category);
    }

    public ApiResponse<ApplicationStatusResponseDTO> getApplicationStatus(Long applicationId, BasicDetailsDto merchantBasicDetailsDto, Boolean isIOS, String token) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId,
              merchantBasicDetailsDto.getId());
            if (lendingApplication == null) {
                return new ApiResponse<>(false, "application not found");
            }
            boolean isSmallTicketLoan = LoanType.SMALL_TICKET.name().equalsIgnoreCase(lendingApplication.getLoanType());
            if (ApplicationStatus.DRAFT.name().equalsIgnoreCase(lendingApplication.getStatus()) || ApplicationStatus.DELETED.name().equalsIgnoreCase(lendingApplication.getStatus())) {
                return new ApiResponse<>(false, "Application not in pending state");
            }
            ApplicationStatusResponseDTO applicationStatusResponseDTO = new ApplicationStatusResponseDTO();
            applicationStatusResponseDTO.setBpClubMember(apiGatewayService.eligibleForProcessingFee(merchantBasicDetailsDto.getId()));
            LendingCategories lendingCategories = lendingCategoryDao.getByCategory(lendingApplication.getCategory());
            MerchantNachDetailsResponseDTO successEnach = loanUtil.getSuccessNach(merchantBasicDetailsDto.getId(), lendingApplication.getId());
            if(ObjectUtils.isEmpty(successEnach) && loanUtil.isEligibleForNachSkip(lendingApplication, lendingApplication.getLender())){
                successEnach = loanUtil.getSuccessNach(lendingApplication.getMerchantId(), lendingApplication.getLender());
            }
//            OrderStickerSlave orderSticker = orderStickerDaoSlave.findByMerchantId(merchantBasicDetailsDto.getId());
            MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(merchantBasicDetailsDto.getId());
            if (ObjectUtils.isEmpty(merchantResponseDTO)) {
                throw new MerchantSummaryExceptionHandler(merchantBasicDetailsDto.getId().toString());
            }
//            MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchantBasicDetailsDto.getId());

            // TODO : remove this and use api
//            Merchant merchant = merchantDao.getById(merchantBasicDetailsDto.getId());

            boolean diy = loanUtil.isDIY(merchantBasicDetailsDto);
            boolean showOrderQr = false;
            boolean isLowPriority = loanUtil.isLowPriority(lendingApplication.getId());
            int tat = easyLoanUtil.isDummyMerchant(merchantBasicDetailsDto.getId()) ? 4 : loanUtil.getApplicationTAT(lendingApplication.getId());
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
            applicationLoanDetailsDTO.setArrangerFee(lendingApplication.getProcessingFee().intValue());
            String modalType = null;
            if (isLowPriority && showOrderQr && ("NTB".equals(lendingApplication.getLoanType()) || "NTB_SMS_1".equals(lendingApplication.getLoanType()))) {
                modalType = "QR";
            } else if (isLowPriority && merchantResponseDTO != null && (merchantResponseDTO.getTxnDayCount1Mon() == null || merchantResponseDTO.getTxnDayCount1Mon() < 5)) {
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
            if (easyLoanUtil.isDummyMerchant(merchantBasicDetailsDto.getId())) {
                applicationDTO2.setStatus("APPROVED");
                applicationDTO2.setText("e-NACH Done");
                applicationDTO2.setButtonContextDTO(null);
                applicationDTO2.setDisabled(("rejected".equalsIgnoreCase(lendingApplication.getStatus())));
                ApplicationDTO.DateDTO dateDTO = new ApplicationDTO.DateDTO();
                dateDTO.setDay(lendingApplication.getAgreementAt().toString());
                dateDTO.setTime(lendingApplication.getAgreementAt().toString());
                applicationDTO2.setDateDTO(dateDTO);
                applicationDTO.add(applicationDTO2);
            } else if (successEnach != null || "APPROVED".equals(lendingApplication.getNachStatus())) {
                applicationDTO2.setStatus(!ObjectUtils.isEmpty(successEnach) ? successEnach.getStatus() : lendingApplication.getNachStatus());
                applicationDTO2.setText("e-NACH Done");
                applicationDTO2.setButtonContextDTO(null);
                applicationDTO2.setDisabled(("rejected".equalsIgnoreCase(lendingApplication.getStatus())));
                ApplicationDTO.DateDTO dateDTO = new ApplicationDTO.DateDTO();
                dateDTO.setDay(getDateInFormat(ObjectUtils.isEmpty(successEnach)?lendingApplication.getCreatedAt():successEnach.getCreatedAt()));
                dateDTO.setTime(getDateInFormat(ObjectUtils.isEmpty(successEnach)?lendingApplication.getCreatedAt():successEnach.getCreatedAt()));
                applicationDTO2.setDateDTO(dateDTO);
                applicationDTO.add(applicationDTO2);
            } else if ("pending_verification".equalsIgnoreCase(lendingApplication.getStatus()) && loanUtil.isEnachBank(merchantBasicDetailsDto.getId())) {
                if("PENDING_VERIFICATION".equalsIgnoreCase(lendingApplication.getNachStatus())){
                    applicationDTO2.setStatus("PENDING_VERIFICATION");
                    applicationDTO2.setText("e-NACH Verification Pending");
                    applicationDTO2.setButtonContextDTO(null);
                    applicationDTO2.setDisabled(("rejected".equalsIgnoreCase(lendingApplication.getStatus())));
                    applicationDTO.add(applicationDTO2);
                }
                else{
                    applicationDTO2.setStatus("PENDING");
                    applicationDTO2.setText("e-NACH Pending");
                    applicationDTO2.setComment("Register eNACH for Instant Loan Approval. Get Rs100 cashback");
                    ApplicationDTO.ButtonContextDTO buttonContextDTO = new ApplicationDTO.ButtonContextDTO();
                    buttonContextDTO.setAction("Enach");
                    buttonContextDTO.setText("Do eNACH");
                    if (BooleanUtils.isTrue(isIOS)) {
                        buttonContextDTO.setDeeplink("bharatpe://enachtp");
                    } else {
                        buttonContextDTO.setDeeplink(apiGatewayService.getEnachProvider(token, lendingApplication.getLender(), merchantBasicDetailsDto.getId()));
                    }
                    applicationDTO2.setButtonContextDTO(buttonContextDTO);
                    applicationDTO.add(applicationDTO2);
                }
            }
            boolean enachMandatory = true; //TODO when enach skip is true then uncomment below code
            boolean enachSkipped = loanUtil.isNachSkipped(merchantBasicDetailsDto.getId(), lendingApplication.getId());
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
            kycDTO.setDisabled("rejected".equalsIgnoreCase(lendingApplication.getStatus()));
            kycDTO.setStatus(lendingApplication.getCkycStatus());
            kycDTO.setComment(lendingApplication.getCkycRejectionReason());
            if (lendingApplication.getCkycDate() != null) {
                kycDTO.setDateDTO(new ApplicationDTO.DateDTO(lendingApplication.getCkycDate()));
            }
            applicationDTO.add(kycDTO);

            ApplicationDTO applicationDTO3 = new ApplicationDTO();
            applicationDTO3.setText("Document Verification");
            applicationDTO3.setDisabled(enachMandatory);
            applicationDTO3.setDisabled("rejected".equalsIgnoreCase(lendingApplication.getStatus()));
            if (kycStatus.equalsIgnoreCase("APPROVED") || kycStatus.equalsIgnoreCase("REJECTED")) {
                applicationDTO3.setDisabled(false);
            }
            applicationDTO3.setDisabled("rejected".equalsIgnoreCase(lendingApplication.getStatus()));
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
            if (!isSmallTicketLoan && (cpvRequired && !"REJECTED".equalsIgnoreCase(kycStatus)) || "REJECTED".equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus())) {
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
            if ("rejected".equalsIgnoreCase(lendingApplication.getStatus())) {
                ApplicationDTO rejectionDTO = new ApplicationDTO();
                rejectionDTO.setText("Application Rejected");
                rejectionDTO.setDisabled(!"rejected".equalsIgnoreCase(lendingApplication.getStatus()));
                rejectionDTO.setStatus("REJECTED");
                rejectionDTO.setComment(lendingApplication.getCkycRejectionReason());
                if (lendingApplication.getCkycDate() != null) {
                    rejectionDTO.setDateDTO(new ApplicationDTO.DateDTO(lendingApplication.getUpdatedAt()));
                }
                applicationDTO.add(rejectionDTO);
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
                if(!"APPROVED".equals(lendingApplication.getNachStatus())) {
                    if("PENDING_VERIFICATION".equalsIgnoreCase(lendingApplication.getNachStatus())){
                        headerDTO.setTitle("eNach Verification Pending");
                        headerDTO.setComment("We are verifying your eNach application");
                    }
                    else{
                        headerDTO.setTitle("Bank A/c Linking Pending");
                        headerDTO.setComment("Complete eNACH to process you loan");
                    }
                }
            } else if (lendingApplication.getCkycStatus() != null && lendingApplication.getCkycStatus().equalsIgnoreCase(KycStatus.PENDING.name())) {
                headerDTO.setTitle("KYC Verification Pending");
                headerDTO.setComment("We are verifying your kyc documents");
            } else if (lendingApplication.getCkycStatus() != null && lendingApplication.getCkycStatus().equalsIgnoreCase(KycStatus.REJECTED.name())) {
                headerDTO.setTitle("KYC Verification Failed");
                headerDTO.setComment(lendingApplication.getCkycRejectionReason());
            } else if (KycStatus.REJECTED.name().equalsIgnoreCase(kycStatus)) {
                headerDTO.setTitle("Document Verification Failed");
                String rejectionMessage;
                String rejectionReason;
                if (KycStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getManualCibil())) {
                    rejectionMessage = easyLoanUtil.getRejectionMessage(lendingApplication.getManualCibilReason(), RejectionStage.CIBIL);
                    rejectionReason = Objects.nonNull(lendingApplication.getManualCibilReason()) ? lendingApplication.getManualCibilReason() : null;
                } else {
                    rejectionMessage = easyLoanUtil.getRejectionMessage(lendingApplication.getManualKycReason(), RejectionStage.KYC);
                    rejectionReason = Objects.nonNull(lendingApplication.getManualKycReason()) ? lendingApplication.getManualKycReason() : null;
                }
                rejectionMessage = Objects.nonNull(rejectionMessage) ? rejectionMessage : "Please re-apply with correct shop details";
                headerDTO.setComment(rejectionMessage);
                applicationStatusResponseDTO.setRejectionReason(rejectionReason);
            }  else if (KycStatus.REJECTED.name().equalsIgnoreCase(cpvStatus)) {
                String rejectionMessage = easyLoanUtil.getRejectionMessage(lendingApplication.getPhysicalReason(), RejectionStage.QC);
                rejectionMessage = Objects.nonNull(rejectionMessage) ? rejectionMessage : "Please re-apply with correct shop details";
                String rejectionReason = Objects.nonNull(lendingApplication.getPhysicalReason()) ? lendingApplication.getPhysicalReason() : null;
                headerDTO.setTitle("Document Verification Failed");
                headerDTO.setComment(rejectionMessage);
                applicationStatusResponseDTO.setRejectionReason(rejectionReason);
            } else if (KycStatus.REJECTED.name().equalsIgnoreCase(callingStatus)) {
                headerDTO.setTitle("Verification Call Failed");
                headerDTO.setComment("You were unreachable on " + merchantBasicDetailsDto.getMobile());
            } else if (KycStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getStatus())) {
                String rejectionMessage = easyLoanUtil.getRejectionMessage(lendingApplication.getPhysicalReason(), RejectionStage.QC);
                rejectionMessage = Objects.nonNull(rejectionMessage) ? rejectionMessage : "Please re-apply with correct shop details";
                String rejectionReason = Objects.nonNull(lendingApplication.getPhysicalReason()) ? lendingApplication.getPhysicalReason() : null;
                headerDTO.setTitle("Document Verification Failed");
                headerDTO.setComment(rejectionMessage);
                applicationStatusResponseDTO.setRejectionReason(rejectionReason);
            } else if (KycStatus.PENDING.name().equalsIgnoreCase(kycStatus)) {
                headerDTO.setTitle("Document Verification Pending");
                headerDTO.setComment("We are reviewing your shop documents");
            } else if (KycStatus.PENDING.name().equalsIgnoreCase(cpvStatus)) {
                headerDTO.setTitle("Document Verification Pending");
                headerDTO.setComment("Our agents will visit your shop to collect business documents");
            } else if (KycStatus.PENDING.name().equalsIgnoreCase(callingStatus)) {
                headerDTO.setTitle("Verification Call Pending");
                headerDTO.setComment("Our agents will call you on " + merchantBasicDetailsDto.getMobile() + " in 1-2 days for verification");
            }  else {
                headerDTO = null;
            }
            applicationStatusResponseDTO.setApplicationLoanDetailsDTO(applicationLoanDetailsDTO);
            applicationStatusResponseDTO.setHeader(isSmallTicketLoan ? null : headerDTO);
            applicationStatusResponseDTO.setApplicationDTOList(applicationDTO);
            return new ApiResponse<>(applicationStatusResponseDTO);
        } catch (Exception e) {
            log.error("Exception in applicationStatus v2 for application:{}", applicationId, e);
        }
        return new ApiResponse<>(false, "Something went wrong");
    }

    public ApiResponse<ApplicationStatusResponseDTO> getApplicationStatus(Long applicationId, Boolean isIOS,
                                                                          String token) {
        try {
            Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);
            if (lendingApplicationOptional == null) {
                return new ApiResponse<>(false, "application not found");
            }
            LendingApplication lendingApplication = lendingApplicationOptional.get();
            Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(applicationId);
            if (ObjectUtils.isEmpty(basicDetailsDto)) {
                return new ApiResponse<>(false, "merchant not found");
            }

            boolean isSmallTicketLoan = LoanType.SMALL_TICKET.name().equalsIgnoreCase(lendingApplication.getLoanType());
            if (ApplicationStatus.DRAFT.name().equalsIgnoreCase(lendingApplication.getStatus()) || ApplicationStatus.DELETED.name().equalsIgnoreCase(lendingApplication.getStatus())) {
                return new ApiResponse<>(false, "Application not in pending state");
            }
            ApplicationStatusResponseDTO applicationStatusResponseDTO = new ApplicationStatusResponseDTO();
            applicationStatusResponseDTO.setBpClubMember(apiGatewayService.eligibleForProcessingFee(basicDetailsDto.get().getId()));
            LendingCategories lendingCategories = lendingCategoryDao.getByCategory(lendingApplication.getCategory());
            MerchantNachDetailsResponseDTO successEnach = loanUtil.getSuccessNach(basicDetailsDto.get().getId(), lendingApplication.getId());
//            OrderStickerSlave orderSticker = orderStickerDaoSlave.findByMerchantId(basicDetailsDto.get().getId());
            MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(basicDetailsDto.get().getId());
            if (ObjectUtils.isEmpty(merchantResponseDTO)) {
                throw new MerchantSummaryExceptionHandler(basicDetailsDto.get().getId().toString());
            }
//            MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchant.getId());
            boolean diy = loanUtil.isDIY(basicDetailsDto.get());
            boolean showOrderQr = false;
            boolean isLowPriority = loanUtil.isLowPriority(lendingApplication.getId());
            int tat = easyLoanUtil.isDummyMerchant(basicDetailsDto.get().getId()) ? 4 : loanUtil.getApplicationTAT(lendingApplication.getId());
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
            } else if (isLowPriority && merchantResponseDTO != null && (merchantResponseDTO.getTxnDayCount1Mon() == null || merchantResponseDTO.getTxnDayCount1Mon() < 5)) {
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
            if (easyLoanUtil.isDummyMerchant(basicDetailsDto.get().getId())) {
                applicationDTO2.setStatus("APPROVED");
                applicationDTO2.setText("e-NACH Done");
                applicationDTO2.setButtonContextDTO(null);
                ApplicationDTO.DateDTO dateDTO = new ApplicationDTO.DateDTO();
                dateDTO.setDay(lendingApplication.getAgreementAt().toString());
                dateDTO.setTime(lendingApplication.getAgreementAt().toString());
                applicationDTO2.setDateDTO(dateDTO);
                applicationDTO.add(applicationDTO2);
            } else if (successEnach != null || "APPROVED".equals(lendingApplication.getNachStatus())) {
                applicationDTO2.setStatus(ObjectUtils.isEmpty(successEnach) ? lendingApplication.getNachStatus() : successEnach.getStatus());
                applicationDTO2.setText("e-NACH Done");
                applicationDTO2.setButtonContextDTO(null);
                applicationDTO2.setDisabled(("rejected".equalsIgnoreCase(lendingApplication.getStatus())));
                ApplicationDTO.DateDTO dateDTO = new ApplicationDTO.DateDTO();
                dateDTO.setDay(ObjectUtils.isEmpty(successEnach) ? getDateInFormat(lendingApplication.getCreatedAt()) : getDateInFormat(successEnach.getCreatedAt()));
                dateDTO.setTime(ObjectUtils.isEmpty(successEnach) ? getDateInFormat(lendingApplication.getCreatedAt()) : getDateInFormat(successEnach.getCreatedAt()));
                applicationDTO2.setDateDTO(dateDTO);
                applicationDTO.add(applicationDTO2);
            } else if ("pending_verification".equalsIgnoreCase(lendingApplication.getStatus()) && loanUtil.isEnachBank(basicDetailsDto.get().getId())) {
                applicationDTO2.setStatus("PENDING");
                applicationDTO2.setText("e-NACH Pending");
                applicationDTO2.setComment("Register eNACH for Instant Loan Approval. Get Rs100 cashback");
                ApplicationDTO.ButtonContextDTO buttonContextDTO = new ApplicationDTO.ButtonContextDTO();
                buttonContextDTO.setAction("Enach");
                buttonContextDTO.setText("Do eNACH");
                if (BooleanUtils.isTrue(isIOS)) {
                    buttonContextDTO.setDeeplink("bharatpe://enachtp");
                } else {
                    buttonContextDTO.setDeeplink(apiGatewayService.getEnachProvider(token, lendingApplication.getLender(),basicDetailsDto.get().getId()));
                }
                applicationDTO2.setButtonContextDTO(buttonContextDTO);
                applicationDTO.add(applicationDTO2);
            }
            boolean enachMandatory = true; //TODO when enach skip is true then uncomment below code
            boolean enachSkipped = loanUtil.isNachSkipped(basicDetailsDto.get().getId(), lendingApplication.getId());
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
            if (!isSmallTicketLoan && (cpvRequired && !"REJECTED".equalsIgnoreCase(kycStatus)) || "REJECTED".equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus())) {
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
            } else if (KycStatus.REJECTED.name().equalsIgnoreCase(kycStatus)) {
                headerDTO.setTitle("Document Verification Failed");
                String rejectionMessage;
                if (KycStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getManualCibil())) {
                    rejectionMessage = easyLoanUtil.getRejectionMessage(lendingApplication.getManualCibilReason(), RejectionStage.CIBIL);
                } else {
                    rejectionMessage = easyLoanUtil.getRejectionMessage(lendingApplication.getManualKycReason(), RejectionStage.KYC);
                }
                rejectionMessage = Objects.nonNull(rejectionMessage) ? rejectionMessage : "Please re-apply with correct shop details";
                headerDTO.setComment(rejectionMessage);
            }  else if (KycStatus.REJECTED.name().equalsIgnoreCase(cpvStatus)) {
                String rejectionMessage = easyLoanUtil.getRejectionMessage(lendingApplication.getPhysicalReason(), RejectionStage.QC);
                rejectionMessage = Objects.nonNull(rejectionMessage) ? rejectionMessage : "Please re-apply with correct shop details";
                headerDTO.setTitle("Document Verification Failed");
                headerDTO.setComment(rejectionMessage);
            } else if (KycStatus.REJECTED.name().equalsIgnoreCase(callingStatus)) {
                headerDTO.setTitle("Verification Call Failed");
                headerDTO.setComment("You were unreachable on " + basicDetailsDto.get().getMobile());
            } else if (KycStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getStatus())) {
                String rejectionMessage = easyLoanUtil.getRejectionMessage(lendingApplication.getPhysicalReason(), RejectionStage.QC);
                rejectionMessage = Objects.nonNull(rejectionMessage) ? rejectionMessage : "Please re-apply with correct shop details";
                headerDTO.setTitle("Document Verification Failed");
                headerDTO.setComment(rejectionMessage);
            } else if (KycStatus.PENDING.name().equalsIgnoreCase(kycStatus)) {
                headerDTO.setTitle("Document Verification Pending");
                headerDTO.setComment("We are reviewing your shop documents");
            } else if (KycStatus.PENDING.name().equalsIgnoreCase(cpvStatus)) {
                headerDTO.setTitle("Document Verification Pending");
                headerDTO.setComment("Our agents will visit your shop to collect business documents");
            } else if (KycStatus.PENDING.name().equalsIgnoreCase(callingStatus)) {
                headerDTO.setTitle("Verification Call Pending");
                headerDTO.setComment("Our agents will call you on " + basicDetailsDto.get().getMobile() + " in 1-2 days for verification");
            }  else {
                headerDTO = null;
            }
            applicationStatusResponseDTO.setApplicationLoanDetailsDTO(applicationLoanDetailsDTO);
            applicationStatusResponseDTO.setHeader(isSmallTicketLoan ? null : headerDTO);
            applicationStatusResponseDTO.setApplicationDTOList(applicationDTO);
            return new ApiResponse<>(applicationStatusResponseDTO);
        } catch (Exception e) {
            log.error("Exception in applicationStatus v2 for application:{}", applicationId, e);
        }
        return new ApiResponse<>(false, "Something went wrong");
    }


    public ApiResponse<?> resubmitApplication(ResubmitApplicationDTO resubmitApplicationDTO){
        try{
            if(Objects.isNull(resubmitApplicationDTO.getApplicationId()) || Objects.isNull(resubmitApplicationDTO.getMerchantId()) || Objects.isNull(resubmitApplicationDTO.getType())){
                return new ApiResponse<>(false,"Request is Invalid.");
            }
            LendingApplication lendingApplication = lendingApplicationDao.findById(resubmitApplicationDTO.getApplicationId()).get();
            if(resubmitApplicationDTO.getType().equals(LendingResubmitEnum.RESUBMIT)){
                MerchantDetailsDto merchantDetailsDTO =  merchantService.fetchMerchantDetails(resubmitApplicationDTO.getMerchantId(), Collections.singletonList(Constants.MerchantUtil.Scope.MERCHANT_USER));
                BasicDetailsDto basicDetailsDto = merchantDetailsDTO.getMerchantDetail();
                if (!ObjectUtils.isEmpty(basicDetailsDto) && !ObjectUtils.isEmpty(basicDetailsDto.getMid())) {
                    HashMap<String, String> cleverTapEvtData = new HashMap<String, String>() {{
                        put("resubmitReason", resubmitApplicationDTO.getResubmitReason());
                    }};
                    executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.LOAN_RESUBMIT_INITIATED.name(), cleverTapEvtData, basicDetailsDto.getMid()));
                }
            }
            if(resubmitApplicationDTO.getType().equals(LendingResubmitEnum.RESUBMIT) && !"pending_verification".equalsIgnoreCase(lendingApplication.getStatus())){
                return new ApiResponse<>(false,"application Not Eligible for resubmited");
            }

            if(Objects.isNull(resubmitApplicationDTO.getCustomAmount()) && (resubmitApplicationDTO.getType().equals(LendingResubmitEnum.DOWNGRADE) && !"approved".equalsIgnoreCase(lendingApplication.getStatus()))){
                return new ApiResponse<>(false,"application Not Eligible for downgrade");
            }

            LendingResubmitTask lendingResubmitTask = lendingResubmitTaskDao.findTopByApplicationIdAndMerchantId(resubmitApplicationDTO.getApplicationId(),resubmitApplicationDTO.getMerchantId());
            if(Objects.nonNull(lendingResubmitTask) && (resubmitApplicationDTO.getType().equals(LendingResubmitEnum.RESUBMIT) &&(lendingResubmitTask.getResubmit() || lendingResubmitTask.getResubmitDone())) && resubmitApplicationDTO.getType().equals(LendingResubmitEnum.DOWNGRADE) &&(lendingResubmitTask.getDowngrade() || lendingResubmitTask.getDowngradeDone())){
                return new ApiResponse<>(false,"application already resubmited");
            }

            if(Objects.isNull(lendingResubmitTask)){
                lendingResubmitTask = new LendingResubmitTask();
                lendingResubmitTask.setMerchantId(resubmitApplicationDTO.getMerchantId());
                lendingResubmitTask.setApplicationId(resubmitApplicationDTO.getApplicationId());
            }
            if(resubmitApplicationDTO.getType().name().equalsIgnoreCase(LendingResubmitEnum.RESUBMIT.name())){
                lendingResubmitTask.setResubmit(Boolean.TRUE);
                lendingResubmitTask.setResubmitDone(Boolean.FALSE);
                lendingResubmitTask.setResubmitReason(resubmitApplicationDTO.getResubmitReason());
                lendingResubmitTask.setResubmitTimestamp(new Date());

                LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
                lendingAuditTrial.setMerchantId(lendingApplication.getMerchantId());
                lendingAuditTrial.setApplicationId(lendingApplication.getId());
                lendingAuditTrial.setLoanId(lendingApplication.getExternalLoanId());
                lendingAuditTrial.setType("APP_STATUS");
                lendingAuditTrial.setNewStatus(resubmitApplicationDTO.getType().toString());
                lendingAuditTrial.setOldStatus(lendingApplication.getStatus());
                lendingAuditTrial.setUserId(0L);
                lendingAuditTrialDao.save(lendingAuditTrial);

            }else if(resubmitApplicationDTO.getType().name().equalsIgnoreCase(LendingResubmitEnum.DOWNGRADE.name())){
                Double previousOferAmount = lendingApplication.getLoanAmount();
                Boolean downGradeStatus= downgradeApplication(lendingApplication, resubmitApplicationDTO);
                double loanAmountDifference = previousOferAmount - lendingApplication.getLoanAmount();
                if(downGradeStatus && loanAmountDifference > 0){
                    lendingResubmitTask.setPreviousOfferAmount(previousOferAmount);
                    lendingResubmitTask.setNewOfferAmount(lendingApplication.getLoanAmount());
                    lendingResubmitTask.setDowngrade(Boolean.TRUE);
                    lendingResubmitTask.setDowngradeDone(Boolean.FALSE);
                    lendingResubmitTask.setDowngradeTimestamp(new Date());
                    lendingResubmitTask.setLmsLastStage(lendingApplication.getLmsStage());
                    if (Objects.nonNull(resubmitApplicationDTO.getCustomAmount())) {
                        lendingApplication.setLmsStage(LendingConstants.CUSTOM_OFFER_DOWNGRADE);
                    }
                    lendingApplicationDao.save(lendingApplication);
                }else if(!downGradeStatus) {
                    lendingApplication.setManualKyc("REJECTED");
                    lendingApplication.setManualKycReason("DOWNGRADE_REJECT");
                    lendingApplication.setLmsStage("QC_REJECTED");
                    lendingApplication.setStatus("rejected");
                    lendingApplicationDao.save(lendingApplication);
                } else if (loanAmountDifference == 0) {
                    lendingApplication.setLmsStage(LendingConstants.PENDING_DISBURSAL);
                    lendingApplicationDao.save(lendingApplication);
                    return new ApiResponse<>(true,"Application Submitted Successfully");
                }
            }
            lendingResubmitTaskDao.save(lendingResubmitTask);

            return new ApiResponse<>(true,"Application Submitted Successfully");
        }catch (Exception e){
            log.error("Exception in resubmit application for application:{}", resubmitApplicationDTO.getApplicationId(), e);
        }
        return new ApiResponse<>(false,"Something went wrong");
    }

    public Boolean downgradeApplication(LendingApplication lendingApplication, ResubmitApplicationDTO resubmitApplicationDTO){
        try{
            Double loanAmount;
            if (OfferDowngradeApplication.eligibleForDowngrade(lendingApplication)) {
                loanAmount = OfferDowngradeApplication.getOfferRevisedAmount(lendingApplication);
                if (Objects.isNull(loanAmount)) {
                    loanAmount = 0d;
                }
            } else if (Objects.nonNull(resubmitApplicationDTO.getCustomAmount())){
                loanAmount = resubmitApplicationDTO.getCustomAmount();
            } else if (Objects.nonNull(resubmitApplicationDTO.getShopStructure()) &&
                    (resubmitApplicationDTO.getShopStructure().equalsIgnoreCase("movable") || resubmitApplicationDTO.getShopStructure().equalsIgnoreCase("temporary"))) {
                loanAmount = getLoanAmount(lendingApplication, resubmitApplicationDTO.getShopStructure());
            }else {
                loanAmount = roundDown(lendingApplication.getLoanAmount() * 0.5);
                loanAmount = Math.min(loanAmount, 100000d);
            }
            if(loanAmount > lendingApplication.getLoanAmount()) {
                return false;
            }
            if(loanAmount < 10000d){
                return false;
            }
            Double amountDiffrence = lendingApplication.getLoanAmount() - loanAmount;
            int processingFee = 0;
            if (lendingApplication.getProcessingFee() > 0) {
                processingFee = (int) Math.ceil((loanAmount * lendingApplication.getProcessingFee())/lendingApplication.getLoanAmount());
            }
            Integer edi,repayment;
            edi = (int) Math.ceil(((loanAmount + (loanAmount * (lendingApplication.getInterestRate() / 100) * lendingApplication.getTenureInMonths()))) / lendingApplication.getPayableDays());
            repayment = (int) Math.round(lendingApplication.getPayableDays() * edi);

            lendingApplication.setEdi(Double.valueOf(edi));
            lendingApplication.setRepayment(Double.valueOf(repayment));
            lendingApplication.setProcessingFee((double) processingFee);
            lendingApplication.setDisbursalAmount(loanAmount - processingFee);
            lendingApplication.setLoanAmount(loanAmount);
            lendingApplicationDao.save(lendingApplication);

            LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
            lendingAuditTrial.setMerchantId(lendingApplication.getMerchantId());
            lendingAuditTrial.setApplicationId(lendingApplication.getId());
            lendingAuditTrial.setLoanId(lendingApplication.getExternalLoanId());
            lendingAuditTrial.setType("APP_STATUS");
            lendingAuditTrial.setNewStatus(Objects.isNull(resubmitApplicationDTO.getCustomAmount()) ? resubmitApplicationDTO.getType().toString() : LendingConstants.CUSTOM_OFFER_DOWNGRADE);
            lendingAuditTrial.setOldStatus(lendingApplication.getStatus());
            lendingAuditTrial.setUserId(0L);
            lendingAuditTrialDao.save(lendingAuditTrial);

            executorService.execute(() -> apiGatewayService.globalLimitTxn(lendingApplication.getMerchantId(), "CREDIT", amountDiffrence));

            return true;
        }catch (Exception e){
            log.error("Exception while downgrading application for applicationId:{}",lendingApplication.getId(),e);
        }
        return false;
    }

    private Double getLoanAmount(LendingApplication lendingApplication, String shopType) {
        log.info("calculating downgraded loan amount for application: {}, with shop type: {}", lendingApplication.getId(), shopType);
        Double loanAmount = lendingApplication.getLoanAmount();
        try {
            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
            List<LoanDowngradeConfigEntity> loanDowngradeConfigEntities = loanDowngradeConfigDao.findByRiskSegmentAndRiskGroupAndColorAndVersion(lendingRiskVariablesSnapshot.getRiskSegment().name(),
                    lendingRiskVariablesSnapshot.getRiskGroup(), lendingRiskVariablesSnapshot.getPincodeColor(),
                    Sort.by(Sort.Direction.DESC, "tenure"), downgradeConfigVersion);
            if (ObjectUtils.isEmpty(loanDowngradeConfigEntities)) {
                log.info("no config found with risk segment: {}, riskGroup: {}, color: {}, tenure: {}",lendingRiskVariablesSnapshot.getRiskSegment().name(),
                        lendingRiskVariablesSnapshot.getRiskGroup(), lendingRiskVariablesSnapshot.getPincodeColor(), lendingApplication.getTenureInMonths());
                return loanAmount;
            }
            LoanDowngradeConfigEntity loanDowngradeConfigEntity = loanDowngradeConfigEntities.get(0);
            for (LoanDowngradeConfigEntity loanDowngradeConfig: loanDowngradeConfigEntities) {
                if (loanDowngradeConfig.getTenure() <= lendingApplication.getTenureInMonths()) {
                    loanDowngradeConfigEntity = loanDowngradeConfig;
                    break;
                }
            }
            log.info("downgrade config entity used for downgrade: {} for application: {}", loanDowngradeConfigEntity, lendingApplication.getId());
            Double maxLimit = shopType.equalsIgnoreCase("movable") ? loanDowngradeConfigEntity.getMaxLimitMov() : loanDowngradeConfigEntity.getMaxLimitTemp();
            double amount;
            double nfiLimit = LoanUtil.roundUp(lendingRiskVariablesSnapshot.getMonthlyNfi() * loanDowngradeConfigEntity.getNfiMultiplier() * lendingRiskVariablesSnapshot.getTenure());
            double tpvLimit = LoanUtil.roundUp(lendingRiskVariablesSnapshot.getMonthlyTpv() * loanDowngradeConfigEntity.getTpvMultiplier() * lendingRiskVariablesSnapshot.getTenure());
            amount = Math.max(nfiLimit, tpvLimit);
            amount = Math.min(amount, maxLimit);
            amount = Math.min(amount, loanAmount);
            log.info("final amount: {} from downgrade config for application: {}",amount, lendingApplication.getId());
            if (loanDowngradeConfigEntity.getTenure() > 0) {
                lendingApplication.setTenure(loanDowngradeConfigEntity.getTenure().toString() + " months");
                lendingApplication.setTenureInMonths(loanDowngradeConfigEntity.getTenure());
                lendingApplication.setPayableDays(loanDowngradeConfigEntity.getTenure() == 0 ? 0
                        : (long) easyLoanUtil.getEdiDays(LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel(), loanDowngradeConfigEntity.getTenure()));
                lendingApplicationDao.save(lendingApplication);
            }

            return amount;
        } catch (Exception e) {
            log.error("exception while downgrade loan amount according to config for applicationId: {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return loanAmount;
    }

    private double roundDown(double limit) {//round down to nearest 1000
        return (int)(limit/1000) * 1000;
    }

    public ApiResponse<?> resubmitDone(Long merchantId,Long applicationId, String resubmitReasons, String mid){
        try{
            if(Objects.isNull(merchantId) || Objects.isNull(applicationId)){
                return new ApiResponse<>(false,"Request is Invalid.");
            }

            LendingApplication lendingApplication = lendingApplicationDao.findByMerchantIdAndApplicationIdAndStatus(merchantId,applicationId,"pending_verification");
            if(Objects.isNull(lendingApplication)){
                return new ApiResponse<>(false,"application not eligible for resubmit");
            }

            LendingResubmitTask lendingResubmitTask = lendingResubmitTaskDao.findTopByApplicationIdAndMerchantId(applicationId,merchantId);
            if(Objects.isNull(lendingResubmitTask) || lendingResubmitTask.getResubmitDone()){
                return new ApiResponse<>(false,"Already Resubmit Done For ApplicationId");
            }

            List<LendingResubmitReasonCount> lendingResubmitReasonCountList = lendingResubmitReasonCountDao.findByApplicationIdAndMerchantId(applicationId, merchantId);
            if(ObjectUtils.isEmpty(lendingResubmitReasonCountList)){
                return new ApiResponse<>(false,"Unable to fetch resubmit reason entry.");
            }
            Boolean resubmitCompleted = true;
            List<String> resubmitReasonList = Arrays.asList(resubmitReasons.split("\\s*,\\s*"));
            Integer maxCount = -1;
            for(LendingResubmitReasonCount lendingResubmitReasonCount : lendingResubmitReasonCountList){
                if(lendingResubmitReasonCount.getResubmitCount() > maxCount)maxCount = lendingResubmitReasonCount.getResubmitCount();
            }
            for(LendingResubmitReasonCount lendingResubmitReasonCount : lendingResubmitReasonCountList){
                if(lendingResubmitReasonCount.getResubmitCount() != maxCount)continue;
                for(String resubmitReason : resubmitReasonList){
                    if(resubmitReason.equalsIgnoreCase(lendingResubmitReasonCount.getResubmitReason())){
                        lendingResubmitReasonCount.setResubmitDone(Boolean.TRUE);
                        lendingResubmitReasonCount.setResubmittedAt(new Date());
                        lendingResubmitReasonCountDao.save(lendingResubmitReasonCount);
                        funnelService.submitEvent(merchantId, null, applicationId, FunnelEnums.StageId.RESUBMIT,
                                FunnelEnums.StageEvent.COMPLETED, resubmitReason);
                    }
                }
                resubmitCompleted = resubmitCompleted && lendingResubmitReasonCount.getResubmitDone();
            }

            if(resubmitCompleted){
                lendingResubmitTask.setResubmitDone(Boolean.TRUE);
                lendingResubmitTask.setResubmittedAt(new Date());
                lendingResubmitTaskDao.save(lendingResubmitTask);

                lendingApplication.setLmsStage("PENDING_KYC_ASSIGNMENT");
                lendingApplicationDao.save(lendingApplication);

                // update tat start time on resubmit
                LendingApplicationPriority lendingApplicationPriority = lendingApplicationPriorityDao.findByApplicationId(lendingApplication.getId());
                if (!ObjectUtils.isEmpty(lendingApplicationPriority)) {
                    lendingApplicationPriority.setTatStartTime(new Date());
                    lendingApplicationPriorityDao.save(lendingApplicationPriority);
                }

                funnelService.submitEvent(merchantId, null, applicationId, FunnelEnums.StageId.RESUBMIT,
                        FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString());
                Integer finalMaxCount = maxCount;
                HashMap<String, String> cleverTapEvtData = new HashMap<String, String>() {{
                    put("resubmitReason", lendingResubmitTask.getResubmitReason());
                    put("resubmitCount", finalMaxCount.toString());
                }};
                executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.LOAN_RESUBMIT_COMPLETED.name(), cleverTapEvtData, mid));
                LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
                lendingAuditTrial.setMerchantId(lendingApplication.getMerchantId());
                lendingAuditTrial.setApplicationId(lendingApplication.getId());
                lendingAuditTrial.setLoanId(lendingApplication.getExternalLoanId());
                lendingAuditTrial.setType("APP_STATUS");
                lendingAuditTrial.setNewStatus("RESUBMIT_DONE");
                lendingAuditTrial.setOldStatus(lendingApplication.getStatus());
                lendingAuditTrial.setUserId(0L);
                lendingAuditTrialDao.save(lendingAuditTrial);
                loanUtil.publishDSData(lendingApplication);
            }
            evictCache(merchantId);
            return new ApiResponse<>(true,"Resubmit Done Succesfully.");
        }catch (Exception e){
            log.error("Exception in resubmit Done for application:{}", applicationId, e);
        }
        return new ApiResponse<>(false,"Something Went Wrong.");
    }

    public ApiResponse<?> getBusinessCategory(){
        BusinessCategoryResponseDTO businessCategoryResponseDTO = new BusinessCategoryResponseDTO();
        businessCategoryResponseDTO.setBusinessCategory(BusinessCategories.getBusinessCategories);
        businessCategoryResponseDTO.setBusinessSubCategory(BusinessCategories.getBusinessSubCategories);
        return new ApiResponse<>(businessCategoryResponseDTO);
    }

    public ApiResponse<?> addBusinessDetails(BusinessDetailsDTO businessDetailsDTO, BasicDetailsDto merchant) {
        try {
            if(Objects.nonNull(merchant.getId())) {
                String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + merchant.getId();
                log.info("deleting cached key of loan details in business details for merchant: {}",merchant.getId());
                lendingCache.delete(loanDetailsCacheKey);
            } else {
                log.info("merchant id not found in add business details");
            }
            LendingMerchantDetails lendingMerchantDetails = new LendingMerchantDetails();
            lendingMerchantDetails.setMerchantId(merchant.getId());
            lendingMerchantDetails.setBusinessName(businessDetailsDTO.getBusinessName());
            lendingMerchantDetails.setBusinessSubCategory(businessDetailsDTO.getBusinessSubCategory());
            lendingMerchantDetails.setBusinessCategory(businessDetailsDTO.getBusinessCategory());
            lendingMerchantDetailsDao.save(lendingMerchantDetails);
            return new ApiResponse<>(true, "Business Details Added Successfully");
        } catch (Exception ex) {
            log.error("Exception Occured while adding business details for merchantId: {} {}", merchant.getId(), ex.getMessage());
        }
        return new ApiResponse<>(false, "Something Went Wrong.");
    }

    public boolean addressQltyScoreLessThanThreshold(AddressValidationDto addressValidationDto) {
        return (!ObjectUtils.isEmpty(addressValidationDto) && !ObjectUtils.isEmpty(addressValidationDto.getResult()) &&
                !ObjectUtils.isEmpty(addressValidationDto.getResult().getAddressQualityScore()) &&
                addressValidationDto.getResult().getAddressQualityScore() < 20);
    }

    public ApiResponse<?> addCallbackRequest(RequestCallbackDto requestCallbackDto, BasicDetailsDto merchant) {
        try {
            if (ObjectUtils.isEmpty(requestCallbackDto) || ObjectUtils.isEmpty(requestCallbackDto.getApplicationStage())) {
                return new ApiResponse<>(false, "something went wrong !");
            }
            Date dateWindow = dateTimeUtil.getDatePlusDays(dateTimeUtil.getCurrentDate(),-24);
            Optional<CallingLeadResponseNimbus> callingLeadResponseNimbus =
                    callingLeadResponseNimbusDao.findTopByMerchantIdAndSourceOrderByCreatedAtDesc(merchant.getId(),"RC");
            log.info("fetching latest existing callback requests for merchant {}",merchant);
            if (ObjectUtils.isEmpty(callingLeadResponseNimbus) || (callingLeadResponseNimbus.get().getCreatedAt().before(dateWindow) &&
                    !ObjectUtils.isEmpty(callingLeadResponseNimbus.get().getDisposition()))) {
                AddLeadRequestNimbusDto addLeadRequestNimbusDto = new AddLeadRequestNimbusDto();
                addLeadRequestNimbusDto.setMerchantId(merchant.getId());
                if (!ObjectUtils.isEmpty(requestCallbackDto.getApplicationId())) {
                    addLeadRequestNimbusDto.setApplicationId(requestCallbackDto.getApplicationId());
                    Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(requestCallbackDto.getApplicationId());
                    addLeadRequestNimbusDto.setProcessedAt(!ObjectUtils.isEmpty(lendingApplication.get()) ? lendingApplication.get().getUpdatedAt() : null);
                }
                addLeadRequestNimbusDto.setPhoneNo(merchant.getMobile());
                addLeadRequestNimbusDto.setListId(new Long(66666));
                addLeadRequestNimbusDto.setPhoneCode(new Integer(1));
                addLeadRequestNimbusDto.setSource("RC");
                addLeadRequestNimbusDto.setComments("RC:"+ requestCallbackDto.getApplicationStage());
                callingLeadNimbusService.addLeadToNimbusWithoutException(addLeadRequestNimbusDto);
                return new ApiResponse<>(CallBackRequestResponseDto.builder().callbackStatus("Thank you! We will reach out to you soon").build());
            } else {
                return new ApiResponse<>(CallBackRequestResponseDto.builder().callbackStatus("Your request is already in queue !").build());
            }
        } catch (Exception e) {
            log.error("Exception occurred while adding callback request for merchantId: {} {}", requestCallbackDto, Arrays.toString(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "Something Went Wrong !");
    }

    @Async
    public void evictCache( Long merchantId) {
        if(Objects.nonNull(merchantId)) {
            String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + merchantId;
            log.info("deleting cached key of loan details in create application for merchant: {}",merchantId);
            lendingCache.delete(loanDetailsCacheKey);
        } else {
            log.info("no key exists!");
        }
    }

    public static String getDateInFormat(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S"); // Set your date format
        String currentData = sdf.format(date);
        return currentData;
    }

    public ApiResponse<?> getApplicationDoc(Long applicationId, BasicDetailsDto merchant, String docType){
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId,
          merchant.getId());
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("Application not found while fetching KFS details for Id: {} for merchant : {}", applicationId, merchant.getId());
            return new ApiResponse<>(false, "Unable to fetch application details");
        }

        try{
            if(docType.equalsIgnoreCase(ApplicationDocType.KEY_FACTS_STATEMENT_DETAILS.toString())){
                return getKfsDetails(applicationId,lendingApplication, merchant);
            }
            else if(docType.equalsIgnoreCase(ApplicationDocType.KEY_FACTS_STATEMENT_DOC.toString())){
                return generateKfs(applicationId, lendingApplication, merchant, false, null);
            }
            else if(docType.equalsIgnoreCase(ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC.toString())){
                return generateSanctionCumLoanAgreement(applicationId, lendingApplication, merchant, false, null);
            }
            return new ApiResponse<>(false, "Unhandled DocType");
        }
        catch(Exception e){
            log.error("Exception for applicationId : {}, merchant : {}{}{}",applicationId, merchant.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Something went wrong");
        }
    }

    public ApiResponse<?> getKfsDetails(Long applicationId, LendingApplication lendingApplication1, BasicDetailsDto merchant){
        try{
            LendingApplication lendingApplication;
            if(ObjectUtils.isEmpty(lendingApplication1)){
                lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId,
                        merchant.getId());
                if(ObjectUtils.isEmpty(lendingApplication)) {
                    log.info("Application not found while fetching KFS details for Id: {} for merchant : {}", applicationId, merchant.getId());
                    return new ApiResponse<>(false, "Unable to fetch application details");
                }
            }
            else lendingApplication = lendingApplication1;
            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
            if(ObjectUtils.isEmpty(lendingKfs)){
                log.info("KFS details not present for Id: {} for merchant : {}", applicationId, merchant.getId());
                lendingKfs = saveKfsDetails(merchant, lendingApplication);
            }
            if(ObjectUtils.isEmpty(lendingKfs)){
                return new ApiResponse<>(false, "Unable to create KFS details");
            }
            Double apr = null;
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao
                    .findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name());
            if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails) && !ObjectUtils.isEmpty(lendingApplicationLenderDetails.getAnnualRoi())) {
                apr = lendingApplicationLenderDetails.getAnnualRoi();
            }

            String lenderCorporateName = "";
            String lenderBusinessAddress = "";
            String lenderContactName = "";
            String lenderContactEmail = "";
            String lenderContactNumber = "";
            String colenderCorporateName = "";
            String colenderBusinessAddress = "";

            if(lendingApplication.getLender().equalsIgnoreCase(Lender.LIQUILOANS_P2P.toString()) || lendingApplication.getLender().equalsIgnoreCase(Lender.LIQUILOANS_P2P_OF.toString())){
                lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_LIQUILOANS;
                lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_LIQUILOANS;
                lenderContactName = KfsConstants.LENDER_CONTACT_NAME_LIQUILOANS;
                lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_LIQUILOANS;
                lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_LIQUILOANS;
            }
            else if(lendingApplication.getLender().equalsIgnoreCase(Lender.LIQUILOANS_NBFC.toString())){
                lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_LL_NBFC;
                lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_LL_NBFC;
                lenderContactName = KfsConstants.LENDER_CONTACT_NAME_LL_NBFC;
                lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_LL_NBFC;
                lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_LL_NBFC;
            }
            else if(lendingApplication.getLender().equalsIgnoreCase(Lender.LDC.toString())){
                lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_LDC;
                lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_LDC;
                lenderContactName = KfsConstants.LENDER_CONTACT_NAME_LDC;
                lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_LDC;
                lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_LDC;
            }
            else if(lendingApplication.getLender().equalsIgnoreCase(Lender.ABFL.toString())){
                lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_ABFL;
                lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_ABFL;
                lenderContactName = KfsConstants.LENDER_CONTACT_NAME_ABFL;
                lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_ABFL;
                lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_ABFL;
            }
            else if(lendingApplication.getLender().equalsIgnoreCase(Lender.MAMTA.toString())
              || lendingApplication.getLender().equalsIgnoreCase(Lender.MAMTA0.toString())
              || lendingApplication.getLender().equalsIgnoreCase(Lender.MAMTA1.toString())
              || lendingApplication.getLender().equalsIgnoreCase(Lender.MAMTA2.toString()) ){
                lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_MAMTA;
                lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_MAMTA;
                lenderContactName = KfsConstants.LENDER_CONTACT_NAME_MAMTA;
                lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_MAMTA;
                lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_MAMTA;
            }
            if(lendingApplication.getLender().equalsIgnoreCase(Lender.MAMTA1.toString())){
                colenderCorporateName = KfsConstants.COLENDER_CORPORATE_NAME_MAMTA1;
                colenderBusinessAddress = KfsConstants.COLENDER_BUSINESS_ADDRESS_MAMTA1;
            } else if(lendingApplication.getLender().equalsIgnoreCase(Lender.MAMTA2.toString())){
                colenderCorporateName = KfsConstants.COLENDER_CORPORATE_NAME_MAMTA2;
                colenderBusinessAddress = KfsConstants.COLENDER_BUSINESS_ADDRESS_MAMTA2;
            }

            KfsDto kfsDto = KfsDto.builder()
                    .merchantId(lendingKfs.getMerchantId())
                    .applicationId(lendingKfs.getApplicationId())
                    .externalLoanId(lendingApplication.getExternalLoanId())
                    .lender(lendingApplication.getLender())
                    .lenderCorporateName(lenderCorporateName)
                    .lenderBusinessAddress(lenderBusinessAddress)
                    .colenderCorporateName(colenderCorporateName)
                    .colenderBusinessAddress(colenderBusinessAddress)
                    .lenderContactName(lenderContactName)
                    .lenderContactEmail(lenderContactEmail)
                    .lenderContactNumber(lenderContactNumber)
                    .loanAmount(lendingApplication.getLoanAmount())
                    .processingFee(lendingApplication.getProcessingFee())
                    .processingFeePercentage(Double.valueOf(String.format("%.2f", (lendingApplication.getProcessingFee()/(lendingApplication.getDisbursalAmount() + lendingApplication.getProcessingFee()) * 100))))
                    .processingFeePercentageWithoutGst(Double.valueOf(String.format("%.4f", (lendingApplication.getProcessingFee()*100D/(100D + GST_PERCENTAGE))/(lendingApplication.getLoanAmount()) * 100)))
                    .tenureInMonths(lendingApplication.getTenureInMonths())
                    .disbursalAmount(lendingApplication.getDisbursalAmount())
                    .repaymentAmount(lendingApplication.getRepayment())
                    .interestRate(lendingApplication.getInterestRate())
                    .apr(Optional.ofNullable(apr).orElse(lendingKfs.getApr()))
                    .isTopUpLoan("TOPUP".equals(lendingApplication.getLoanType()))
                    .locationLatLong(Objects.toString(lendingApplication.getLatitude(),"") + ", " + Objects.toString(lendingApplication.getLongitude(),""))
                    .coolingOffDays(KfsConstants.COOLING_OFF_DAYS)
                    .ediCount(lendingApplication.getPayableDays())
                    .ediOffData(lendingApplication.getPayableDays() % 30 ==0 ? "" : " (except Sunday)")
                    .ediAmount(lendingApplication.getEdi())
                    .lspContactName(KfsConstants.LSP_CONTACT_NAME)
                    .lspContactEmail(KfsConstants.LSP_CONTACT_EMAIL)
                    .lspContactNumber(KfsConstants.LSP_CONTACT_NUMBER)
                    .nbfcId(lendingApplication.getNbfcId())
                    .build();
            return new ApiResponse<>(kfsDto);
        }
        catch(Exception e){
            log.error("Unable to fetch KFS details for applicationId : {}, Exception : {}, stacktrace : {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Something went wrong");
        }
    }

    public LendingKfs saveKfsDetails(BasicDetailsDto merchant, LendingApplication lendingApplication){
        LendingKfs lendingKfs = new LendingKfs();
        lendingKfs.setApplicationId(lendingApplication.getId());
        lendingKfs.setMerchantId(merchant.getId());
        lendingKfs.setLender(lendingApplication.getLender());
        Double apr = getApr(merchant.getId(), lendingApplication.getId(), lendingApplication.getLoanAmount() - lendingApplication.getProcessingFee(), LenderOffDays.valueOf(lendingApplication.getLender()).getEdiModel().getNoOfEdiDaysInAWeek());
        if(ObjectUtils.isEmpty(apr)) return null;
        lendingKfs.setApr(Double.valueOf(String.format("%.2f", apr)));
        lendingKfsDao.save(lendingKfs);
        return lendingKfs;
    }

    public void storeApplicationDocs(Long applicationId, LendingApplication lendingApplication, BasicDetailsDto merchant) throws Exception {
        LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
        if(ObjectUtils.isEmpty(lendingKfs)){
            log.error("Unable to retrieve KFS details from db for applicationId : {}", applicationId);
            throw new Exception("Unable to retrieve KFS details from db for applicationId : " + applicationId);
        }
        //KFS
        generateKfsDocument(lendingApplication, merchant, lendingKfs, null);
        lendingKfs.setKfsSignedAt(dateTimeUtil.getCurrentDate());

        //Loan Agreement
        generateSanctionCumLoanAgreementDoc(lendingApplication, merchant, lendingKfs, null);
        lendingKfs.setSanctionLoanAgreementSignedAt(dateTimeUtil.getCurrentDate());
        lendingKfsDao.save(lendingKfs);
        funnelService.submitEvent(merchant.getId(), null, applicationId,
                FunnelEnums.StageId.AGREEMENT, FunnelEnums.StageEvent.SUBMITTED, LocalDateTime.now().toString());
    }

    public void generateSanctionCumLoanAgreementDoc(LendingApplication lendingApplication, BasicDetailsDto merchant, LendingKfs lendingKfs, Date dateTime) throws Exception{
        String fileName = "";
        ApiResponse<?> apiResponse = generateSanctionCumLoanAgreement(lendingApplication.getId(), lendingApplication, merchant, true, dateTime);
        if(apiResponse.success){
            String sanctionCumLoanAgreementHtml = (String)apiResponse.data;
            fileName = SANCTION_LOAN_AGREEMENT_S3_KEY_PREFIX + lendingApplication.getId() + ".pdf";
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(outStream);
            PdfDocument pdfDocument = new PdfDocument(writer);
            if(!getLenderLogo(lendingApplication.getLender(), ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC).isEmpty()){
                if (Lender.ABFL.name().equalsIgnoreCase(lendingKfs.getLender())) {
                    ImageData headerImageData = ImageDataFactory.create(getLenderLogo(lendingApplication.getLender(), ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC));
                    ImageData footerImageData = ImageDataFactory.create(getLenderLogo(lendingApplication.getLender(), ApplicationDocType.ABFL_LETTERHEAD_FOOTER));
                    Header headerHandler = new Header(headerImageData);
                    pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, headerHandler);
                    Footer footerHandler = new Footer(footerImageData);
//                    HeaderFooter headerFooterHandler = new HeaderFooter(headerImageData,footerImageData);
                    pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, footerHandler);
                } else {
                    ImageData logoImageData = ImageDataFactory.create(getLenderLogo(lendingApplication.getLender(), ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC));
                    Header headerHandler = new Header(logoImageData);
                    pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, headerHandler);
                }
            }
            InputStream htmlStringInputStream = new ByteArrayInputStream(sanctionCumLoanAgreementHtml.getBytes(StandardCharsets.UTF_8));
            HtmlConverter.convertToPdf(htmlStringInputStream, pdfDocument);
            ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
            s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, "loan-document");
            String sanctionCumLoanAgreementUrl = s3BucketHandler.getPreSignedPublicURL(fileName, "loan-document");
            String shortUrl = apiGatewayService.getShortUrl(sanctionCumLoanAgreementUrl);
            if(shortUrl == null || shortUrl.isEmpty() || shortUrl.trim().isEmpty())throw new Exception("Unable to create short URL for Sanction Loan Agreement doc link for : " + lendingApplication.getId());
            else {
                lendingKfs.setSanctionLoanAgreementDocFile(fileName);
                lendingKfs.setSanctionLoanAgreementDocUrl(shortUrl);
            }
        }
        else{
            log.error("Unable to store Sanction Cum Loan Agreement pdf doc for applicationId : {}", lendingApplication.getId());
            throw new Exception("Unable to generate Sanction Cum Loan Agreement pdf doc for applicationID" + lendingApplication.getId());
        }
    }

    public Double getApr(Long merchantId, Long applicationId, Double amountToCalculateAprOn, Integer ediModel){
        try{
            log.info("calculating APR for applicationId : {}", applicationId);
            Double guess = 0.01;
            ArrayList<Double> values = new ArrayList<>();
            CommonResponse response = lendingEdiScheduleService.getEdiScheduleV2(merchantId, applicationId);
            if(!response.isSuccess()){
                log.info(response.getMessage());
                log.info("Unable to fetch edi schedule for APR calculation for applicationId : {}", applicationId);
                return null;
            }
            List<EdiScheduleV2DTO> ediSchedule = (List<EdiScheduleV2DTO>)response.getData();
            if(ObjectUtils.isEmpty(ediSchedule)){
                log.info("Unable to fetch edi schedule for APR calculation for applicationid : {}", applicationId);
                return null;
            }
            values.add(0-amountToCalculateAprOn);
            for(int i = 0; i < ediSchedule.size(); i++){
                if(ediSchedule.get(i).getSerialNumber() == 0)continue;
                values.add(new Double(ediSchedule.get(i).getEdiAmount()));
                if((i+1) < ediSchedule.size()){
                    long diff = Math.abs(dateTimeUtil.getDateDiffInDays(ediSchedule.get(i).getDate(), ediSchedule.get(i+1).getDate()));
                    if(diff == 2){
                        values.add(0.0);
                    }
                }
            }
            int tenureInDays = values.size() - 1;
            Double apr = 0.0;
            double[] valuesDouble = new double[values.size()];
            for(int i = 0;i < values.size();i++)valuesDouble[i] = values.get(i);
            log.info("valuesDouble Size : {}", valuesDouble.length);
            int daysInYear = (ediModel == 7) ? 360 : 365;
            apr = LoanCalculationUtil.irr(valuesDouble, guess) * daysInYear;
            if(apr.isNaN()){
                log.info("APR : {}", apr);
                return null;
            }
            log.info("APR : {}", apr);
            return apr * 100;
        }
        catch(Exception e){
            log.error("Unable to calculate APR for applicationId : {} Exception : {}, stacktrace : {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    public void generateKfsDocument(LendingApplication lendingApplication, BasicDetailsDto merchant, LendingKfs lendingKfs, Date dateTime) throws Exception {
        String fileName = "";
        ApiResponse<?> apiResponse;
        apiResponse = generateKfs(lendingApplication.getId(), lendingApplication, merchant, true, dateTime);
        if (apiResponse.success) {
            String kfsHtml = (String) apiResponse.data;
            fileName = KFS_S3_KEY_PREFIX + lendingApplication.getId() + ".pdf";
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(outStream);
            PdfDocument pdfDocument = new PdfDocument(writer);
            if (!getLenderLogo(lendingApplication.getLender(), ApplicationDocType.KEY_FACTS_STATEMENT_DOC).isEmpty()) {
                if (Lender.ABFL.name().equalsIgnoreCase(lendingKfs.getLender())) {
                    ImageData headerImageData = ImageDataFactory.create(getLenderLogo(lendingApplication.getLender(), ApplicationDocType.KEY_FACTS_STATEMENT_DOC));
                    ImageData footerImageData = ImageDataFactory.create(getLenderLogo(lendingApplication.getLender(), ApplicationDocType.ABFL_LETTERHEAD_FOOTER));
                    Header headerHandler = new Header(headerImageData);
                    pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, headerHandler);
                    Footer footerHandler = new Footer(footerImageData);
//                    HeaderFooter headerFooterHandler = new HeaderFooter(headerImageData,footerImageData);
                    pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, footerHandler);
                } else {
                    ImageData logoImageData = ImageDataFactory.create(getLenderLogo(lendingApplication.getLender(), ApplicationDocType.KEY_FACTS_STATEMENT_DOC));
                    Header headerHandler = new Header(logoImageData);
                    pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, headerHandler);
                }
            }
            InputStream htmlStringInputStream = new ByteArrayInputStream(kfsHtml.getBytes(StandardCharsets.UTF_8));
            HtmlConverter.convertToPdf(htmlStringInputStream, pdfDocument);
            ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
            s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, "loan-document");
            String kfsUrl = s3BucketHandler.getPreSignedPublicURL(fileName, "loan-document");
            String kfsShortUrl = apiGatewayService.getShortUrl(kfsUrl);
            if (kfsShortUrl == null || kfsShortUrl.isEmpty() || kfsShortUrl.trim().isEmpty())
                throw new Exception("Unable to create short URL for KFS doc link for : " + lendingApplication.getId());
            else {
                lendingKfs.setKfsDocFile(fileName);
                lendingKfs.setKfsDocUrl(kfsShortUrl);
            }
        }
        else{
            log.error("Unable to store KFS pdf doc for applicationId : {}", lendingApplication.getId());
            throw new Exception("Unable to generate KFS for applicationID" + lendingApplication.getId());
        }
    }

    public ApiResponse<?> generateKfs(Long applicationId, LendingApplication lendingApplication, BasicDetailsDto merchant, boolean timeStamp, Date dateTime){
        ApiResponse apiResponse = getKfsDetails(applicationId, lendingApplication, merchant);
        if(!apiResponse.success){
            log.info("Unable to get KFS details while creating KFS doc for applicationId: {}", applicationId);
            return new ApiResponse<>(false,"Unable to retrieve KFS Details");
        }
        KfsDto kfsDto = (KfsDto)apiResponse.data;
        if(kfsDto.getLender() == null){
            log.info("Unable to get lender details while creating KFS doc for applicationId: {}", applicationId);
            return new ApiResponse<>(false,"Unable to get lender while generating KFS");
        }
        try {
            Map<String, Object> data = new HashMap<>();
            data = getApplicationDocData(applicationId, kfsDto, merchant, timeStamp, ApplicationDocType.KEY_FACTS_STATEMENT_DOC, dateTime, lendingApplication.getIp());
            String lender = kfsDto.getLender();
            String html = "";
            String filePath = "";
            if(lender.equalsIgnoreCase(Lender.LDC.toString()) || lender.equalsIgnoreCase(Lender.LIQUILOANS_P2P.toString()) || lender.equalsIgnoreCase(Lender.LIQUILOANS_P2P_OF.toString())){
                filePath = "/templates/" + "KFS_P2P" + ".html";
            }
            else filePath = "/templates/" + "KFS_NONP2P" + ".html";
            InputStream inputStream = this.getClass().getResourceAsStream(filePath);
            Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
            html = scanner.hasNext() ? scanner.next() : "";;
            for(Map.Entry<String,Object> entry : data.entrySet()) {
                String key = "{{" + entry.getKey() + "}}";
                String val = Objects.nonNull(entry.getValue()) ? entry.getValue().toString() : "";
                log.info(key + " " + val);
                html = html.replace(key, val);
            }
            return new ApiResponse<>(html);
        } catch (Exception e) {
            log.error("Exception while generating KFS html for applicationId : {}, {}, {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Unable to generate KFS");
        }
    }

    public ApiResponse<?> generateSanctionCumLoanAgreement(Long applicationId, LendingApplication lendingApplication, BasicDetailsDto merchant, boolean timeStamp, Date dateTime){
        ApiResponse apiResponse = getKfsDetails(applicationId, lendingApplication, merchant);
        if(!apiResponse.success){
            log.info("Unable to get KFS details while creating Sanction Cum Loan Agreement doc for applicationId: {}", applicationId);
            return new ApiResponse<>(false,"Unable to retrieve KFS Details");
        }
        KfsDto kfsDto = (KfsDto)apiResponse.data;
        if(kfsDto.getLender() == null){
            return new ApiResponse<>(false,"Unable to get lender while generating Sanction Cum Loan Agreement");
        }
        try {
            Map<String, Object> data = new HashMap<>();
            data = getApplicationDocData(applicationId, kfsDto, merchant, timeStamp, ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC, dateTime, lendingApplication.getIp());
            String lender = kfsDto.getLender();
            String html = "";
            String filePath = "";
            if(lender.equalsIgnoreCase(Lender.LDC.toString()) || lender.equalsIgnoreCase(Lender.LIQUILOANS_P2P.toString()) || lender.equalsIgnoreCase(Lender.LIQUILOANS_P2P_OF.toString())){
                filePath = "/templates/" + "SANCTION_LOAN_AGREEMENT_P2P" + ".html";
            } else if (lender.equalsIgnoreCase(Lender.MAMTA1.toString())) {
                filePath = "/templates/SANCTION_LOAN_AGREEMENT_MAMTA1.html";
            }
            else filePath = "/templates/" + "SANCTION_LOAN_AGREEMENT_NONP2P" + ".html";
            InputStream inputStream = this.getClass().getResourceAsStream(filePath);
            Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
            html = scanner.hasNext() ? scanner.next() : "";;
            for(Map.Entry<String,Object> entry : data.entrySet()) {
                String key = "{{" + entry.getKey() + "}}";
                String val = Objects.nonNull(entry.getValue()) ? entry.getValue().toString() : "";
                log.info(key + " " + val);
                html = html.replace(key, val);
            }
            return new ApiResponse<>(html);
        } catch (Exception e) {
            log.error("Exception while generating Sanction Cum Loan Agreement html for applicationId : {}, {}, {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Unable to generate Sanction Cum Loan Agreement");
        }
    }

    public String fetchKfsFromS3andGenerateShortUrl(LendingApplication lendingApplication) throws Exception {
            String fileName = KFS_S3_KEY_PREFIX + lendingApplication.getId();
            String kfsUrl = s3BucketHandler.getPreSignedPublicURL(fileName, "loan-document");
            String kfsShortUrl = apiGatewayService.getShortUrl(kfsUrl);
            if (kfsShortUrl == null || kfsShortUrl.isEmpty() || kfsShortUrl.trim().isEmpty())
                throw new Exception("Unable to create short URL for KFS doc link for : " + lendingApplication.getId());

            return kfsShortUrl;
    }

    public String fetchSanctionAndLoanAgreementFromS3andGenerateShortUrl(LendingApplication lendingApplication) throws Exception {
        String fileName = SANCTION_LOAN_AGREEMENT_S3_KEY_PREFIX + lendingApplication.getId();
        String sanctionCumLoanAgreementUrl = s3BucketHandler.getPreSignedPublicURL(fileName, "loan-document");
        String shortUrl = apiGatewayService.getShortUrl(sanctionCumLoanAgreementUrl);
        if(shortUrl == null || shortUrl.isEmpty() || shortUrl.trim().isEmpty())
            throw new Exception("Unable to create short URL for Sanction Loan Agreement doc link for : " + lendingApplication.getId());

        return shortUrl;
    }

    public Map<String, Object> getApplicationDocData(Long applicationId, KfsDto kfsDto, BasicDetailsDto merchant, boolean timeStamp, ApplicationDocType applicationDocType, Date dateTime, String ip) throws Exception {
        if(ObjectUtils.isEmpty(dateTime)){
            dateTime  = dateTimeUtil.getCurrentDate();
        }
        Map<String, Object> data = new HashMap<>();
        data.put("name_of_lender_nbfc", kfsDto.getLenderCorporateName());
        data.put("register_address_of_nbfc", kfsDto.getLenderBusinessAddress());
        data.put("loan_amount_in_figure", kfsDto.getLoanAmount());
        data.put("loan_amount_in_words", getAmountInWords(kfsDto.getLoanAmount().toString()));
        data.put("processing_percentage", kfsDto.getProcessingFeePercentage());
        data.put("processing_percentage_without_gst", kfsDto.getProcessingFeePercentageWithoutGst());
        data.put("processing_fee_includes_tax", kfsDto.getProcessingFee());
        data.put("processing_fee_in_words_includes_tax", getAmountInWords(kfsDto.getProcessingFee().toString()));
        data.put("rate_of_interest", kfsDto.getInterestRate());
        data.put("apr", kfsDto.getApr());
        data.put("interest_charged_to_borrower", kfsDto.getRepaymentAmount() - kfsDto.getLoanAmount());
        data.put("interest_charged_to_borrower_in_words", getAmountInWords(data.get("interest_charged_to_borrower").toString()));
        data.put("total_amount_including_interest_paid_by_borrower_entire_tenure", kfsDto.getRepaymentAmount());
        data.put("total_amount_including_interest_paid_by_borrower_entire_tenure_in_words", getAmountInWords(kfsDto.getRepaymentAmount().toString()));
        data.put("loan_amount_disbursed", kfsDto.getDisbursalAmount());
        data.put("loan_amount_disbursed_in_words", getAmountInWords(kfsDto.getDisbursalAmount().toString()));
        data.put("interest_equal_daily", kfsDto.getEdiAmount());
        data.put("interest_equal_daily_in_words", getAmountInWords(kfsDto.getEdiAmount().toString()));
        data.put("no_of_edis", kfsDto.getEdiCount());
        data.put("edi_off_day",kfsDto.getEdiOffData());
        data.put("default_rate_of_interest", "N/A");
        data.put("default_rate_of_interest_on_monthly_or_daily_basis", "N/A");
        data.put("forclosure_amount_rate_of_rate_of_interest", "Foreclosure is allowed with no charges");
        data.put("cooling_off_days", kfsDto.getCoolingOffDays());
        data.put("tenure_of_loan_in_months", kfsDto.getTenureInMonths());
        data.put("name_of_nodal_officer", kfsDto.getLenderContactName());
        data.put("email_id", kfsDto.getLenderContactEmail());
        data.put("conatct_no", kfsDto.getLenderContactNumber());
        data.put("timing_for_contact", "");
        data.put("name_of_nodal_officer_lsp", kfsDto.getLspContactName());
        data.put("email_id_lsp", kfsDto.getLspContactEmail());
        data.put("conatct_no_lsp", kfsDto.getLspContactNumber());
        data.put("timing_for_contact_lsp", "");
        data.put("facilitation_fee_in_figure", "0.00");
        log.info("lender {} {}", kfsDto.getLender(), applicationDocType);
        data.put("processing_fee_statement", kfsDto.isTopUpLoan()?"":kfsDto.getProcessingFeePercentageWithoutGst()+"% of the loan Amount " + (kfsDto.getProcessingFee()==0?"":("+ " + KfsConstants.GST_PERCENTAGE + "% GST on processing fees ")) + "i.e. ");
        String repaymentSchedule = getRepaymentSchedule(applicationId, merchant);
        if(ObjectUtils.isEmpty(repaymentSchedule))throw new Exception("Unable to create repayment schedule for" + applicationId);
        data.put("repayment_schedule", repaymentSchedule);
        if(timeStamp)data.put("date", new SimpleDateFormat("dd-MM-yyyy").format(dateTime));
        else data.put("date", "");
        data.put("disbursal_date", new SimpleDateFormat("dd-MMM-yyyy").format(dateTime));
        data.put("mobile_number_for_otp", merchant.getMobile());
        data.put("platform", "BHARATPE");
        data.put("ip_address", ip);
        data.put("location", kfsDto.getLocationLatLong());
        if(timeStamp)data.put("time_stamp", dateTime);
        else data.put("time_stamp", "");
        data.put("loan_id", kfsDto.getExternalLoanId());
        data.put("bharatpe_id", kfsDto.getExternalLoanId());
        data.put("borrower_name", merchant.getBeneficiaryName());
        data.put("email_of_borrower", merchant.getEmail());
        data.put("phone_number_of_borrower", merchant.getMobile());
        data.put("loan_account_number", Optional.ofNullable(kfsDto.getNbfcId()).orElse(""));
        data.put("product_name", Lender.ABFL.name().equalsIgnoreCase(kfsDto.getLender()) ? "ESB MCA PERSONAL LOAN" : "");
        data.put("repayment_mode", Lender.ABFL.name().equalsIgnoreCase(kfsDto.getLender()) ? "ACH" : "");
        data.put("pan_of_borrower", kycHandler.getPanNumber(merchant.getId()));
        ApiResponse aadharAddressResponse = getAadhaarAddress(merchant, applicationId);
        if(aadharAddressResponse.isSuccess()){
            AadhaarAddressResponseDTO aadhaarAddressResponseDTO = (AadhaarAddressResponseDTO)aadharAddressResponse.getData();
            data.put("address_of_borrower", aadhaarAddressResponseDTO.getAddress());
        }
        else throw new Exception("Unable to get aadhar address for : " + applicationId);
        data.put("device_id", "");
        if(!timeStamp){
            String lenderLogoHtml = "<p class=\"text-center\">\n" +
                    "<img class=\"width-66 mb20\" src=\"" + getLenderLogo(kfsDto.getLender(), applicationDocType) + "\" alt=\"lender\" />\n" +
                    "</p>";
            data.put("lender_logo", lenderLogoHtml);
        }
        else data.put("lender_logo", "");

        data.put("lender_tag", "");
        data.put("name_of_colender", "");
        data.put("register_address_of_colender", "");
        data.put("colender_text", "");

        if(kfsDto.getLender().equalsIgnoreCase(Lender.MAMTA1.toString()) || kfsDto.getLender().equalsIgnoreCase(Lender.MAMTA2.toString())){
            data.put("lender_tag", "(Lender)");
            data.put("name_of_colender", kfsDto.getColenderCorporateName());
            data.put("register_address_of_colender", kfsDto.getColenderBusinessAddress() + " (Co-Lender)");
            data.put("colender_text", "The loan is given under the Co-Lending model by the Lender & Co-Lender in the ratio of 20:80 respectively.");
        }
        log.info("data ****** {}", new ObjectMapper().writeValueAsString(data));
        return data;
    }

    public String getLenderLogo(String lender, ApplicationDocType applicationDocType){
        String logoUrl = "";
        if(lender.equalsIgnoreCase(Lender.LIQUILOANS_P2P.toString()) || lender.equalsIgnoreCase(Lender.LIQUILOANS_P2P_OF.toString())){
            logoUrl = "https://d36q81tf33qni.cloudfront.net/LiquiLoans.png";
        }
        else if(lender.equalsIgnoreCase(Lender.LIQUILOANS_NBFC.toString())){
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/TrillionLogoKFS.png";
        }
        else if(lender.equalsIgnoreCase(Lender.ABFL.toString()) && applicationDocType.equals(ApplicationDocType.ABFL_LETTERHEAD_FOOTER)){
//            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/abfl-footer.png";
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/abfl-letterhead-with-padding_1.png";
        }
        else if(lender.equalsIgnoreCase(Lender.ABFL.toString()) && applicationDocType.equals(ApplicationDocType.WELCOME_LETTER_DOC)){
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/abfl-welcome.png";
        }
        else if(lender.equalsIgnoreCase(Lender.ABFL.toString())){
//            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/abfl-letterhead.png";
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/abfl-letterhead-compressed_1.png";
        }
        else if(lender.equalsIgnoreCase(Lender.LDC.toString()) && applicationDocType.equals(ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC)){
            logoUrl = "https://bharatpe-cdn.s3.ap-south-1.amazonaws.com/LendenAddress.png";
        }
        else if(lender.equalsIgnoreCase(Lender.LDC.toString()) && applicationDocType.equals(ApplicationDocType.KEY_FACTS_STATEMENT_DOC)){
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/Lenden.png";
        } else if (lender.equalsIgnoreCase(Lender.HINDON.name()) && applicationDocType.equals(ApplicationDocType.HINDON_LETTERHEAD_HEADER)) {
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/hindon_letterhead-1681130033877.png";
        } else if (lender.equalsIgnoreCase(Lender.HINDON.name()) && applicationDocType.equals(ApplicationDocType.HINDON_LETTERHEAD_FOOTER)) {
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/hindon_footer-1681129971473.png";
        }
        else if(lender.equalsIgnoreCase(Lender.MAMTA.toString())
          || lender.equalsIgnoreCase(Lender.MAMTA0.toString())
          || lender.equalsIgnoreCase(Lender.MAMTA1.toString())
          || lender.equalsIgnoreCase(Lender.MAMTA2.toString())){
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/MamtaLogoKFS.png";
        }
        return logoUrl;
    }

    public String getRepaymentSchedule(Long applicationId, BasicDetailsDto merchant) {
        CommonResponse response = lendingEdiScheduleService.getEdiScheduleV2(merchant.getId(), applicationId);
        if(!response.isSuccess()){
            log.info(response.getMessage());
            log.info("Unable to fetch edi schedule for applicationId : {}", applicationId);
            return null;
        }
        List<EdiScheduleV2DTO> ediSchedule = (List<EdiScheduleV2DTO>)response.getData();
        if(ObjectUtils.isEmpty(ediSchedule)){
            log.info("Unable to fetch edi schedule for applicationId : {}", applicationId);
            return null;
        }

        String html = "";
        for(int i = 0; i < ediSchedule.size(); i++){
            if(ediSchedule.get(i).getSerialNumber() == 0)continue;
            html += "    <tr class=\"width-100\">\n" +
                    "      <td class=\"width-auto\">\n" +
                    "        <p style=\"margin: 0px; font-size: 10px;\" class=\"fw400 text-center\">" + ediSchedule.get(i).getSerialNumber() + "</p>\n" +
                    "      </td>\n" +
                    "      <td class=\"width-auto\">\n" +
                    "        <p style=\"margin: 0px; font-size: 10px;\" class=\"fw400 text-center\">" + ediSchedule.get(i).getBalance() + "</p>\n" +
                    "      </td>\n" +
                    "      <td class=\"width-auto\">\n" +
                    "        <p style=\"margin: 0px; font-size: 10px;\" class=\"fw400 text-center\">" + ediSchedule.get(i).getPrincipal() + "</p>\n" +
                    "      </td>\n" +
                    "      <td class=\"width-auto\">\n" +
                    "        <p style=\"margin: 0px; font-size: 10px;\" class=\"fw400 text-center\">" + ediSchedule.get(i).getInterest() + "</p>\n" +
                    "      </td>\n" +
                    "      <td class=\"width-auto\">\n" +
                    "        <p style=\"margin: 0px; font-size: 10px;\" class=\"fw400 text-center\">" + ediSchedule.get(i).getEdiAmount() + "</p>\n" +
                    "      </td>\n" +
                    "    </tr>\n";
        }
        return html;
    }

    public String getAmountInWords(String amount){
        BigDecimal bd = new BigDecimal(amount);
        long number = bd.longValue();
        long no = bd.longValue();
        int decimal = (int) (bd.remainder(BigDecimal.ONE).doubleValue() * 100);
        int digits_length = String.valueOf(no).length();
        int i = 0;
        ArrayList<String> str = new ArrayList<>();
        HashMap<Integer, String> words = new HashMap<>();
        words.put(0, "");
        words.put(1, "One");
        words.put(2, "Two");
        words.put(3, "Three");
        words.put(4, "Four");
        words.put(5, "Five");
        words.put(6, "Six");
        words.put(7, "Seven");
        words.put(8, "Eight");
        words.put(9, "Nine");
        words.put(10, "Ten");
        words.put(11, "Eleven");
        words.put(12, "Twelve");
        words.put(13, "Thirteen");
        words.put(14, "Fourteen");
        words.put(15, "Fifteen");
        words.put(16, "Sixteen");
        words.put(17, "Seventeen");
        words.put(18, "Eighteen");
        words.put(19, "Nineteen");
        words.put(20, "Twenty");
        words.put(30, "Thirty");
        words.put(40, "Forty");
        words.put(50, "Fifty");
        words.put(60, "Sixty");
        words.put(70, "Seventy");
        words.put(80, "Eighty");
        words.put(90, "Ninety");
        String digits[] = {"", "Hundred", "Thousand", "Lakh", "Crore"};
        while (i < digits_length) {
            int divider = (i == 2) ? 10 : 100;
            number = no % divider;
            no = no / divider;
            i += divider == 10 ? 1 : 2;
            if (number > 0) {
                int counter = str.size();
                String plural = (counter > 0 && number > 9) ? "s" : "";
                String tmp = (number < 21) ? words.get(Integer.valueOf((int) number)) + " " + digits[counter] + plural : words.get(Integer.valueOf((int) Math.floor(number / 10) * 10)) + " " + words.get(Integer.valueOf((int) (number % 10))) + " " + digits[counter] + plural;
                str.add(tmp);
            } else {
                str.add("");
            }
        }

        Collections.reverse(str);
        String Rupees = String.join(" ", str).trim();

        //String paise = (decimal) > 0 ? " And Paise " + words.get(Integer.valueOf((int) (decimal - decimal % 10))) + " " + words.get(Integer.valueOf((int) (decimal % 10))) : "";
        return Rupees;
    }

    protected static class Header implements IEventHandler {
        private ImageData headerImage;

        public Header(ImageData header) {
            this.headerImage = header;
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdf = docEvent.getDocument();
            PdfPage page = docEvent.getPage();
            Rectangle pageSize = page.getPageSize();
            Rectangle rectangle = new Rectangle(0, pageSize.getHeight() - 75, pageSize.getWidth(), 75);
            PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdf);
            pdfCanvas.addImageFittedIntoRectangle(headerImage, rectangle, false);
            pdfCanvas.release();
        }
    }
    protected static class HeaderFooter implements IEventHandler {
        private ImageData footerImage;
        private ImageData headerImage;

        public HeaderFooter(ImageData header,ImageData footer) {
            this.footerImage = footer;
            this.headerImage = header;
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdf = docEvent.getDocument();
            PdfPage page = docEvent.getPage();
            Rectangle pageSize = page.getPageSize();
            Rectangle headerRectangle = new Rectangle(0, pageSize.getHeight() - 75, pageSize.getWidth(), 75);
            Rectangle footerRectangle = new Rectangle(0, 20 , pageSize.getWidth(), 80);
            PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdf);
            pdfCanvas.addImageFittedIntoRectangle(headerImage, headerRectangle, false);
            pdfCanvas.addImageFittedIntoRectangle(footerImage, footerRectangle, false);
            pdfCanvas.release();
        }
    }

    protected static class Footer implements IEventHandler {
        private ImageData footerImage;

        public Footer(ImageData footer) {
            this.footerImage = footer;
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdf = docEvent.getDocument();
            PdfPage page = docEvent.getPage();
            Rectangle pageSize = page.getPageSize();
            Rectangle footerRectangle =  new Rectangle(0, 0 , pageSize.getWidth(), 90);
            PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdf);
            pdfCanvas.addImageFittedIntoRectangle(footerImage, footerRectangle, false);
            pdfCanvas.release();
        }
    }

    public ApiResponse<?> updateCurrentAddress(BasicDetailsDto merchant, Long applicationId, AddressDetails addressDetails, Boolean sameAsAdhaar) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchant.getId());
            if (Objects.isNull(lendingApplication)) {
                return new ApiResponse<>(false, "There is no such applicationId for given merchantId");
            }
            LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(applicationId);
            if (Objects.isNull(lendingGstDetail)) {
                lendingGstDetail = new LendingGstDetail();
                lendingGstDetail.setMerchantId(merchant.getId());
                lendingGstDetail.setApplicationId(applicationId);
            }
            if (sameAsAdhaar == true) {
                List<KycDoc> kycDocs = kycHandler.getKycDoc(lendingApplication.getMerchantId());
                for (KycDoc kycDoc : kycDocs) {
                    if (KycDocType.POA.equals(kycDoc.getDocType())) {
                        lendingGstDetail.setAddress1(kycDoc.getAddress());
                        lendingGstDetail.setCity(kycDoc.getCity());
                        lendingApplication.setPincode(Long.valueOf(kycDoc.getPincode()));
                        lendingGstDetail.setState(kycDoc.getState());
                        lendingGstDetail.setAddress2(null);
                        lendingGstDetail.setLandmark(null);
                    }
                }
                log.info("Updating current address details as aadhaar address of applicationId {} and merchantId {}", applicationId, merchant.getId());
            } else {

                lendingGstDetail.setCity(addressDetails.getCity());
                lendingGstDetail.setAddress1(addressDetails.getAddress1());
                lendingGstDetail.setAddress2(addressDetails.getAddress2());
                lendingGstDetail.setPincode(addressDetails.getPincode());
                lendingGstDetail.setLandmark(addressDetails.getLandmark());
                lendingGstDetail.setState(addressDetails.getState());
                log.info("Updating current address details as address provided by merchant of applicationId {} and merchantId {}", applicationId, merchant.getId());
            }
            lendingGstDao.save(lendingGstDetail);
            funnelService.submitEvent(merchant.getId(), null, applicationId,
                    FunnelEnums.StageId.ADDITIONAL_DETAILS, FunnelEnums.StageEvent.SUBMITTED, LocalDateTime.now().toString());
            return new ApiResponse<>(true, "Current Address updated successfully!");
        } catch (Exception e) {
            log.error("Exception occurred while updating current address for applicationId: {}, {}", applicationId, Arrays.toString(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "Something Went Wrong !");
    }

    public ApiResponse<?> getAadhaarAddress(BasicDetailsDto merchant, Long applicationId) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchant.getId());
            if (Objects.isNull(lendingApplication)) {
                return new ApiResponse<>(false, "There is no such applicationId for given merchantId");
            }
            List<KycDoc> kycDocs = kycHandler.getKycDoc(lendingApplication.getMerchantId());
            for (KycDoc kycDoc : kycDocs) {
                if (KycDocType.POA.equals(kycDoc.getDocType())) {
                    AadhaarAddressResponseDTO dto = new AadhaarAddressResponseDTO();
                    dto.setAddress(kycDoc.getAddress());
                    dto.setCity(kycDoc.getCity());
                    dto.setPincode(kycDoc.getPincode());
                    dto.setState(kycDoc.getState());
                    return new ApiResponse<>(dto);
                }
            }
        } catch (Exception e) {
            log.error("Exception occurred while fetching aadhaar address for applicationId: {}, {}", applicationId, Arrays.toString(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "Something Went Wrong !");
    }

    public Boolean generateWelcomeDocument(LendingApplication lendingApplication, LendingKfs lendingKfs, BasicDetailsDto merchant, Date dateTime) throws Exception {
        if (!Lender.ABFL.name().equalsIgnoreCase(lendingApplication.getLender())) {
            log.info("no welcome format exists for the lender {} for app {}", lendingApplication.getLender(), lendingApplication.getId());
            return Boolean.FALSE;
        }
        ApiResponse apiResponse = getKfsDetails(lendingApplication.getId(), lendingApplication, merchant);
        if(!apiResponse.success){
            log.info("Unable to get KFS details while creating Sanction Cum Loan Agreement doc for applicationId: {}", lendingApplication.getId());
            return Boolean.FALSE;
        }
        KfsDto kfsDto = (KfsDto)apiResponse.data;
        if(kfsDto.getLender() == null){
            log.info("lender not found for {}", lendingApplication.getId());
            return Boolean.FALSE;
        }
        Map<String,Object> data = getApplicationDocData(lendingApplication.getId(), kfsDto, merchant, true, ApplicationDocType.WELCOME_LETTER_DOC, dateTime, lendingApplication.getIp());

        String lender = lendingApplication.getLender();
        String welcomeHtml = "";
        String filePath = "";
        if(lender.equalsIgnoreCase(Lender.ABFL.toString())){
            filePath = "/templates/" + "WELCOME_LETTER_NONP2P_ABFL" + ".html";
        } else {
            return Boolean.FALSE;
        }
        InputStream inputStream = this.getClass().getResourceAsStream(filePath);
        Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
        welcomeHtml = scanner.hasNext() ? scanner.next() : "";;
        for(Map.Entry<String,Object> entry : data.entrySet()) {
            String key = "{{" + entry.getKey() + "}}";
            String val = Objects.nonNull(entry.getValue()) ? entry.getValue().toString() : "";
            log.info(key + " " + val);
            welcomeHtml = welcomeHtml.replace(key, val);
        }
        String fileName = "";
        fileName = WELCOME_S3_KEY_PREFIX + lendingApplication.getId() + ".pdf";
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(outStream);
        PdfDocument pdfDocument = new PdfDocument(writer);
        if (!getLenderLogo(lendingApplication.getLender(), ApplicationDocType.WELCOME_LETTER_DOC).isEmpty()) {
            if (Lender.ABFL.name().equalsIgnoreCase(lendingKfs.getLender())) {
                ImageData headerImageData = ImageDataFactory.create(getLenderLogo(lendingApplication.getLender(), ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC));
                ImageData footerImageData = ImageDataFactory.create(getLenderLogo(lendingApplication.getLender(), ApplicationDocType.ABFL_LETTERHEAD_FOOTER));
                Header headerHandler = new Header(headerImageData);
                pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, headerHandler);
                Footer footerHandler = new Footer(footerImageData);
//                    HeaderFooter headerFooterHandler = new HeaderFooter(headerImageData,footerImageData);
                pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, footerHandler);
            } else {
                ImageData logoImageData = ImageDataFactory.create(getLenderLogo(lendingApplication.getLender(), ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC));
                Header headerHandler = new Header(logoImageData);
                pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, headerHandler);
            }
        }
        InputStream htmlStringInputStream = new ByteArrayInputStream(welcomeHtml.getBytes(StandardCharsets.UTF_8));
        HtmlConverter.convertToPdf(htmlStringInputStream, pdfDocument);
        ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
        s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, "loan-document");
        String welcomeUrl = s3BucketHandler.getPreSignedPublicURL(fileName, "loan-document");
        String welcomeShortUrl = apiGatewayService.getShortUrl(welcomeUrl);
        if (welcomeShortUrl == null || welcomeShortUrl.isEmpty() || welcomeShortUrl.trim().isEmpty())
            throw new Exception("Unable to create short URL for KFS doc link for : " + lendingApplication.getId());
        else {
            lendingKfs.setWelcomeDocFile(fileName);
            lendingKfs.setWelcomeDocUrl(welcomeShortUrl);
            lendingKfsDao.save(lendingKfs);
        }
        return Boolean.TRUE;
    }
}
