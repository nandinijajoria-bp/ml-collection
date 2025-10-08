package com.bharatpe.lending.loanV2.service;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.LendingDisbursalStageDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.common.Constants.BusinessCategories;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.AddLeadRequestNimbusDto;
import com.bharatpe.lending.common.dto.MaxPricingValuesDTO;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.entity.EligibleLoanAudit;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.query.dao.ForeClosureConfigDao;
import com.bharatpe.lending.common.query.dao.LendingApplicationLenderDetailsDaoSlave;
import com.bharatpe.lending.common.query.dao.PenaltyFeeConfigDaoSlave;
import com.bharatpe.lending.common.query.entity.ForeClosureConfig;
import com.bharatpe.lending.common.query.entity.LendingApplicationLenderDetailsSlave;
import com.bharatpe.lending.common.query.entity.PenaltyFeeConfigSlave;
import com.bharatpe.lending.common.service.CallingLeadNimbusService;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.constants.Constants;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.ReqAddAddress;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.KfsConstants;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.constant.OfferDowngradeApplication;
import com.bharatpe.lending.constant.PaymentConstants;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.entity.LendingLenderQuota;
import com.bharatpe.lending.entity.LoanDowngradeConfigEntity;
import com.bharatpe.lending.enums.*;
import com.bharatpe.lending.exceptions.DowngradeConfigNotFoundException;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.handlers.MerchantSummaryExceptionHandler;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV2.dto.*;
import com.bharatpe.lending.loanV2.handlers.BureauHandler;
import com.bharatpe.lending.loanV3.config.OxyzoConfig;
import com.bharatpe.lending.loanV3.config.SmfgConfig;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.dto.EmiDashboardResponse;
import com.bharatpe.lending.loanV3.revamp.dto.LoanDetailsV3Response;
import com.bharatpe.lending.loanV3.revamp.dto.ShopPicturesStateDTO;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.loanV3.revamp.util.LoanUtilV3;
import com.bharatpe.lending.loanV3.revamp.services.businessLoan.EmiDashboardService;
import com.bharatpe.lending.loanV3.services.LendingApplicationServiceV3Base;
import com.bharatpe.lending.loanV3.services.VKycService;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.AssociationServiceUtil;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.wrapper.InvokeCreateLeadAndDocUploadWraperService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.DocUploadUtils;
import com.bharatpe.lending.loanV3.utils.EmiUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.bharatpe.lending.loanV3.utils.OfferUtils;
import com.bharatpe.lending.service.*;
import com.bharatpe.lending.service.impl.LenderAssignService;
import com.bharatpe.lending.util.*;
import com.bharatpe.util.pdf.HTMLEdittor.HTMLEditor;
import com.bharatpe.util.pdf.PdfCompressorUtil;
import com.bharatpe.util.pdf.dto.PdfGenerationRequest;
import com.bharatpe.util.pdf.dto.PdfGenerationResponse;
import com.bharatpe.util.pdf.PdfGeneratorUtilV2;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.html2pdf.ConverterProperties;
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
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.IBlockElement;
import com.itextpdf.layout.element.IElement;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.bharatpe.lending.common.enums.RiskSegment.REPEAT;
import static com.bharatpe.lending.common.enums.VkycStatus.VKYC_SKIPPED;
import static com.bharatpe.lending.constant.InsuranceConstant.SELECTED;
import static com.bharatpe.lending.constant.KfsConstants.*;
import static com.bharatpe.lending.constant.LendingConstants.MERCHANT_CATEGORY;
import static com.bharatpe.lending.constant.LendingConstants.MERCHANT_SUB_CATEGORY;
import static com.bharatpe.lending.lendingplatform.nbfc.enums.Lender.CREDITSAISON;
import static com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant.DUMMY_MERCHANT_TRANSFER_DAYS_TEXT;
import static com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant.F_TPV_PILOT_IDENTIFIER;

@Service
@Slf4j
public class LendingApplicationServiceV2 {

    @Autowired
    private LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    private LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    AutoPayUPIDao autoPayUPIDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingResubmitTaskDao lendingResubmitTaskDao;

    @Autowired
    @Lazy
    LoanUtil loanUtil;

    @Autowired
    LendingEligibleLoanDao eligibleLoanDao;

    @Autowired
    LendingCategoryDao lendingCategoryDao;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LendingGstDao lendingGstDao;

    @Autowired
    LendingAuditTrialDao lendingAuditTrialDao;

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

    @Autowired
    private LoanDashboardService loanDashboardService;

    @Value("${downgrade.config.version:1.1}")
    double downgradeConfigVersion;

    @Value("${force.set.piramal:false}")
    private Boolean forceSetPiramal;

    @Autowired
    PenaltyFeeConfigDaoSlave penaltyFeeConfigDaoSlave;

    @Autowired
    LoanDetailsV3Service loanDetailsV3Service;

    @Value("${penalty.rollout.date:}")
    String penalDate;

    @Value("${penalty.rollout.date.liquiloans:}")
    String penalDateLiquiloans;

    @Value("${penalty.rollout.date.trillion:}")
    String penalDateTrillion;
    @Value("${penalty.rollout.date.trillionloans:}")
    String penalDateTrillionloans;

    @Value("${shop.photo.sync.rollout:0}")
    private Integer shopPhotoSyncRollout;

    @Value("${kfs.compression.level:2}")
    int kfsCompressionLevel;

    @Value("${sanction.aggrement.level:5}")
    int sanctionCompressionLevel;

    @Value("${offer.downgrade.disabled.lenders:TRILLIONLOANS}")
    String offerDowngradeDisabledLenders;

    @Autowired
    KycUtils kycUtils;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    CommonService commonService;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ForeClosureConfigDao foreClosureConfigDao;

    @Autowired
    CommonUtil commonUtil;

    @Autowired
    AssociationServiceUtil associationServiceUtil;

    @Autowired
    LendingApplicationLenderDetailsDaoSlave lendingApplicationLenderDetailsDaoSlave;

    @Value("${lender.doc.generate.enabled.lenders:}")
    String lenderDocGenerateEnabledLenders;

    @Value("${lender.doc.generate.topup.enabled.lenders:}")
    String lenderDocGenerateTopUpEnabledLenders;

    @Value("${aws.s3.bucket:loan-document}")
    private String s3Bucket;

    @Lazy
    @Autowired
    SmfgConfig smfgConfig;

    @Lazy
    @Autowired
    DocUploadUtils docUploadUtils;

    @Autowired
    LendingLenderPricingDao lendingLenderPricingDao;
    @Value("${enable.bl.tagging:true}")
    Boolean blTaggingEnabled;

    @Value("${bl.eligible.lenders:IIFL}")
    String blEligibleLendersList;

    @Value("${udyam.registration.required.lenders:}")
    String udyamRegistrationRequiredLenders;

    @Autowired
    @Lazy
    UgroConfig ugroConfig;

    @Autowired
    @Lazy
    OxyzoConfig oxyzoConfig;

    @Autowired
    private EmiUtils emiUtils;
    @Autowired
    private EmiDashboardService emiDashboardService;

    @Autowired
    LanguageService languageService;

    @Value("${lender.vernac.lang.rollout.percent:1}")
    Integer lenderVernacLangRolloutPercent;

    @Autowired
    VKycService vkycService;

    @Autowired
    LendingApplicationVkycDetailsDao lendingApplicationVkycDetailsDao;

    @Autowired
    private EdiUtil ediUtil;

    @Autowired
    PdfGeneratorUtilV2 pdfGeneratorUtil;

    @Autowired
    HTMLEditor htmlEditor;

    @Autowired
    LenderDisbursalLimitsDao lenderDisbursalLimitsDao;

    @Autowired
    LendingApplicationServiceV3Base lendingApplicationServiceV3Base;

    @Autowired
    LendingEligibleLoanAuditDao eligibleLoanAuditDao;

    @Autowired
    InsuranceService insuranceService;

    @Value("${new.pdf.generation.method.lenders:-}")
    String newPdfGenerationMethodLenders;

    private final List<String> udyamSuccessStatus = Arrays.asList(LenderAssociationStatus.UDYAM_REGISTRATION_SUCCESS.name());

    public ApiResponse<?> initiateKyc(BasicDetailsDto merchant, InitiateKycRequest initiateKycRequest) {
        try {
            if (Objects.isNull(merchant.getId())) {
                log.info("merchantId not found");
                return new ApiResponse<>(false, "MerchantID not found");
            }
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(initiateKycRequest.getApplicationId(), merchant.getId());
            executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.LOAN_KYC_INITIATED_BE.name(), null, merchant.getMid()));
            funnelService.submitEvent(merchant.getId(), null, initiateKycRequest.getApplicationId(),ObjectUtils.isEmpty(lendingApplication)?null:lendingApplication.getLoanType(),
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
//                        if (!ObjectUtils.isEmpty(kycDoc.getDigioXml())) {
//                            lendingApplicationKycDetails.setAadharXml(kycDoc.getDigioXml());
//                        }
                        String dob = KycUtils.getDOB(kycDoc);
                        log.info("dob from POA kyc doc for merchant: {}, {}",dob,merchant.getId());
                        lendingApplicationKycDetails.setDob(dob);
                        aadharDigilocker = true;
                        log.info("Aadhar is digilocker approved for : {}", merchant.getId());
                    }
                }
//                else if (kycDoc.getDocType() != null && KycDocType.PAN_CARD.equals(kycDoc.getDocType()) && KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
//                    lendingApplicationKycDetails.setPanUrl(kycDoc.getDocFrontImageUrl());
//                    if(Objects.isNull(lendingApplicationKycDetails.getPanApprovedAt()))lendingApplicationKycDetails.setPanApprovedAt(new Date());
//                    panCardApproved = true;
//                    log.info("Pan Card is valid for : {}", merchant.getId());
//                }
                else if (kycDoc.getDocType() != null && KycDocType.PAN_NO.equals(kycDoc.getDocType()) && KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
                    lendingApplicationKycDetails.setPan(kycDoc.getDocIdentifier());
                    panNoApproved = true;
                    log.info("Pan No is valid for : {}", merchant.getId());
                }
            }
            lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails);
            if (selfieValid && aadharValid && aadharDigilocker && panNoApproved) {
                lendingApplicationKycDetails.setConsentDate(new Date());
                lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails);
                log.info("Kyc details verified for merchant : {}", merchant.getId());
                executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.LOAN_KYC_VERIFIED_BE.name(), null, merchant.getMid()));
                funnelService.submitEvent(merchant.getId(), null, initiateKycRequest.getApplicationId(),
                        FunnelEnums.StageId.KYC, FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString());
                return new ApiResponse<>(kycDeepLink);
            }
            List<KycDocType> docTypes = new ArrayList<>();
            if (!panNoApproved) {
//                docTypes.add(KycDocType.PAN_CARD);
                docTypes.add(KycDocType.PAN_NO);
            }
            docTypes.add(KycDocType.SELFIE);
            docTypes.add(KycDocType.EKYC);
            String callBackURL = env.getProperty("kyc.loan.deeplink");
            if (!StringUtils.isEmpty(initiateKycRequest.getWroute())) {
                callBackURL += "&wroute=" + initiateKycRequest.getWroute();
            }
            String kycInitReferenceId = initiateKycRequest.getApplicationId() != null ? String.valueOf(initiateKycRequest.getApplicationId()) : String.valueOf(merchant.getId());
            InitiateKycDTO initiateKycDTO = InitiateKycDTO.builder()
                    .referenceId(kycInitReferenceId)
                    .panNumber(experian.getPancardNumber())
                    .callBackUrl(callBackURL)
                    .merchantId(String.valueOf(merchant.getId())).build();
            Map<String, String> ckycResponseObj = kycHandler.initiateKyc(merchant.getId(), initiateKycDTO, docTypes, validAfterDate, false, null, null);
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

    private void saveAuditTrail(LendingApplication lendingApplication, String type, String oldStatus, String newStatus) {
        LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
        lendingAuditTrial.setApplicationId(lendingApplication.getId());
        lendingAuditTrial.setLoanId(ObjectUtils.isEmpty(lendingApplication.getExternalLoanId()) ? "" : lendingApplication.getExternalLoanId());
        lendingAuditTrial.setMerchantId(lendingApplication.getMerchantId());
        lendingAuditTrial.setType(type);
        lendingAuditTrial.setOldStatus(oldStatus);
        lendingAuditTrial.setNewStatus(newStatus);
        lendingAuditTrialDao.save(lendingAuditTrial);
    }

    public void updateEligibleLoan(Long merchantId, EligibleLoanDTO eligibleLoanDTO) {
        log.info("Updating eligible loan for merchantId: {}", merchantId);

        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchantId);

        try {
            if (Objects.isNull(merchantId) || Objects.isNull(eligibleLoanDTO)) {
                AsyncLoggerUtil.logError(log, "MerchantId or EligibleLoanDTO is null for merchantId: {}", merchantId);
                return;
            }

            LendingEligibleLoan eligibleLoan =  new LendingEligibleLoan();
            eligibleLoan.setMerchantId(merchantId);
            eligibleLoan.setCreatedAt(new Date());
            eligibleLoan.setCategory(eligibleLoanDTO.getCategory());
            eligibleLoan.setAmount(eligibleLoanDTO.getAmount());
            eligibleLoan.setTenure(eligibleLoanDTO.getTenure() != null ? String.valueOf(eligibleLoanDTO.getTenure()) : null);
            eligibleLoan.setEdi(eligibleLoanDTO.getEdi());
            eligibleLoan.setIoEdi(eligibleLoanDTO.getIoEdi());
            eligibleLoan.setIoEdiDays(eligibleLoanDTO.getIoEdiDays());
            eligibleLoan.setEdiFreeDays(eligibleLoanDTO.getEdiFreeDays());
            eligibleLoan.setRepayment(eligibleLoanDTO.getRepaymentAmount());
            eligibleLoan.setEdiCount(eligibleLoanDTO.getEdiCount());
            eligibleLoan.setOfferType(eligibleLoanDTO.getOfferType());
            eligibleLoan.setRateOfInterest(eligibleLoanDTO.getRateOfInterest());
            eligibleLoan.setProcessingFee(eligibleLoanDTO.getProcessingFee());
            eligibleLoan.setApr(eligibleLoanDTO.getApr());
            eligibleLoan.setIrr(eligibleLoanDTO.getIrr());
            eligibleLoan.setLoanType(lendingRiskVariables.getLoanType()!=null ?
                    lendingRiskVariables.getLoanType() :
                    eligibleLoanDTO.getLoanType());
            eligibleLoan.setTenureInMonths(eligibleLoanDTO.getTenureInMonths());
            eligibleLoan.setUpdatedAt(new Date());
            eligibleLoan.setOfferType("CUSTOM");

            LendingEligibleLoan savedLoan = eligibleLoanDao.save(eligibleLoan);
            AsyncLoggerUtil.logInfo(log, "Eligible loan updated for merchantId: {}, data: {}", merchantId, savedLoan);

            eligibleLoanAuditDao.save(EligibleLoanAudit.createObject(savedLoan));

        } catch (Exception e) {
            AsyncLoggerUtil.logError(log, "Exception while updating eligible loan for merchantId: {}, exception: {}",
                    merchantId, e.getMessage());
        }
    }

    public void createEligibleLoan(Long merchantId, EligibleLoanDTO eligibleLoanDTO) {
        log.info("Updating eligible loan for merchantId: {}", merchantId);

        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchantId);

        try {
            if (Objects.isNull(merchantId) || Objects.isNull(eligibleLoanDTO)) {
                AsyncLoggerUtil.logError(log, "MerchantId or EligibleLoanDTO is null for merchantId: {}", merchantId);
                return;
            }

            LendingEligibleLoan  eligibleLoan = new LendingEligibleLoan();
            eligibleLoan.setMerchantId(merchantId);
            eligibleLoan.setCreatedAt(new Date());

            eligibleLoan.setCategory(eligibleLoanDTO.getCategory());
            eligibleLoan.setAmount(eligibleLoanDTO.getAmount());
            eligibleLoan.setTenure(eligibleLoanDTO.getTenure() != null ? String.valueOf(eligibleLoanDTO.getTenure()) : null);
            eligibleLoan.setEdi(eligibleLoanDTO.getEdi());
            eligibleLoan.setIoEdi(eligibleLoanDTO.getIoEdi());
            eligibleLoan.setIoEdiDays(eligibleLoanDTO.getIoEdiDays());
            eligibleLoan.setEdiFreeDays(eligibleLoanDTO.getEdiFreeDays());
            eligibleLoan.setRepayment(eligibleLoanDTO.getRepaymentAmount());
            eligibleLoan.setEdiCount(eligibleLoanDTO.getEdiCount());
            eligibleLoan.setOfferType(eligibleLoanDTO.getOfferType());
            eligibleLoan.setLoanType(lendingRiskVariables.getLoanType()!=null ?
                    lendingRiskVariables.getLoanType() :
                    eligibleLoanDTO.getLoanType());
            eligibleLoan.setRateOfInterest(eligibleLoanDTO.getRateOfInterest());
            eligibleLoan.setProcessingFee(eligibleLoanDTO.getProcessingFee());
            eligibleLoan.setTenureInMonths(eligibleLoanDTO.getTenureInMonths());
            eligibleLoan.setApr(eligibleLoanDTO.getApr());
            eligibleLoan.setIrr(eligibleLoanDTO.getIrr());
            eligibleLoan.setUpdatedAt(new Date());
            eligibleLoan.setOfferType("CUSTOM");

            LendingEligibleLoan savedLoan = eligibleLoanDao.save(eligibleLoan);
            AsyncLoggerUtil.logInfo(log, "Eligible loan updated for merchantId: {}, data: {}", merchantId, savedLoan);

            eligibleLoanAuditDao.save(EligibleLoanAudit.createObject(savedLoan));

        } catch (Exception e) {
            AsyncLoggerUtil.logError(log, "Exception while updating eligible loan for merchantId: {}, exception: {}",
                    merchantId, e.getMessage());
        }
    }

    public ApiResponse<?> createApplication(BasicDetailsDto merchant, CreateApplicationRequest applicationRequest, String token) {
        if(Objects.nonNull(merchant.getId())) {
            String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + merchant.getId();
            log.info("deleting cached key of loan details in create application for merchant: {}",merchant.getId());
            lendingCache.delete(loanDetailsCacheKey);
        } else {
            log.info("merchant id not found in create application");
        }
        loanDashboardService.deleteLoanDashboardCache(merchant.getId());

        if(emiUtils.isEmiFlowEnabled()){
            EmiDashboardResponse emiDashboardResponse = emiDashboardService.getDashboardResponse(merchant.getId(), token);
            if(!emiUtils.isEligibleForEdiCreateApplication(emiDashboardResponse)){
                log.warn("application already exist at business loan for merchant: {}", merchant.getId());
                return new ApiResponse<>(true, "application already exist at bl");
            }
        }

        boolean isApplicableForAggregationFlow = loanUtil.isApplicableForAggregationFlowV2(merchant.getId(), null);
        LendingApplication inProgressLoanApplication = lendingApplicationDao.getLatestPendingApplication(merchant.getId());
        if (inProgressLoanApplication == null) {
            // No in-progress application exists - create new application
            if (isApplicableForAggregationFlow) {
                updateEligibleLoan(merchant.getId(), applicationRequest.getEligibleLoanDTO());
                return createNewApplicationV2(merchant, applicationRequest);
            } else {
                return createNewApplication(merchant, applicationRequest);
            }
        } else {
            // We have an in-progress application - update existing application
            if (isApplicableForAggregationFlow) {
                return updateApplicationV2(merchant, applicationRequest);
            } else {
                return updateApplication(merchant, applicationRequest);
            }
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
                    BusinessDetailsDTO businessDetailsDTO = BusinessDetailsDTO.builder().businessCategory(applicationRequest.getCategory()).
                            businessName(applicationRequest.getBusinessName()).build();
                    addBusinessDetails(businessDetailsDTO,merchant);
                    merchantService.updateMerchantBusinessName(lendingApplication.getMerchantId(), applicationRequest.getBusinessName());
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
            if (applicationRequest != null && applicationRequest.getAddressDetails() != null && isAddressUpdated(lendingApplication, applicationRequest)) {
                addressValidationDto = getAddressValidationScore(applicationRequest.getAddressDetails());
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
            LendingApplication inProgressLoanApplication = lendingApplicationDao.getLatestPendingApplication(merchant.getId());
            if (!ObjectUtils.isEmpty(inProgressLoanApplication)) {
                log.error("application already exist for this merchant id {}",inProgressLoanApplication);
                return new ApiResponse<>(true,"Application already exist for this merchant id");
            }
            Boolean isPreApproved = checkForPreapprovedRepeatLoan(merchant.getId(), applicationRequest);
            AddressValidationDto  addressValidationDto = null;
            Boolean isApplicableForAggregationFlow = loanUtil.isApplicableForAggregationFlow(merchant.getId(), null);
            if ((!isApplicableForAggregationFlow || isPreApproved) && applicationRequest != null && applicationRequest.getAddressDetails() != null){
               addressValidationDto = getAddressValidationScore(applicationRequest.getAddressDetails());
                String error = baseChecks(merchant, applicationRequest.getAddressDetails());
                if (error != null) return new ApiResponse<>(false, error);
                   if (addressQltyScoreLessThanThreshold(addressValidationDto)) {
                    log.info("address quality score less than 20");
                   return new ApiResponse<>(ApplicationAddressValidation.builder().hasAValidAddress(false).build());
                }
            }
            LendingEligibleLoan eligibleLoan = eligibleLoanDao.findTopByMerchantIdAndOfferTypeOrderByIdDesc(merchant.getId(), "CUSTOM");
//            LendingCategories lendingCategory = lendingCategoryDao.getByCategory(applicationRequest.getCategory());
            if (Objects.isNull(eligibleLoan)) {
                log.info("eligible loan not available for merchant:{} and category:{}", merchant.getId(), applicationRequest.getCategory());
                return new ApiResponse<>(false, "eligible loan not found");
            }
            LendingApplication lendingApplication = saveLendingApplication(merchant,isPreApproved, eligibleLoan, applicationRequest, null, addressValidationDto, isApplicableForAggregationFlow);
            loanUtil.createApplicationSnapshot(lendingApplication, merchant);

            final boolean rejected = checkAndRejectPilotIdentifierApplication(lendingApplication);

            if (rejected) {
                return new ApiResponse<>(false, "Ineligible ! Please try again in sometime");
            }

            if("rejected".equalsIgnoreCase(lendingApplication.getStatus()) && LendingConstants.NONE_LENDER.equalsIgnoreCase(lendingApplication.getLender())){
                return new ApiResponse<>(true, "No lender assigned, application rejected");
            }
            loanUtil.isApplicableForAggregationFlow(lendingApplication.getMerchantId(), lendingApplication.getId()); // For saving screen type if lender aggregation is applicable.

            createStatusAuditTrail(lendingApplication);
            executorService.submit(() -> {
                loanUtil.callingDeForReferences(merchant.getId(),lendingApplication);
            });
            loanUtil.publishApplicationEvent(lendingApplication);
            LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchant.getId(), lendingApplication);
            if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
                funnelService.submitEventV3(merchant.getId(), null, lendingApplication.getId(),lendingApplication.getLoanType(),
                        FunnelEnums.StageId.APPLICATION, FunnelEnums.StageEvent.INITIATED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
                HashMap<String, String> cleverTapEvtData = new HashMap<String, String>() {{
                    put("loanAmount", lendingApplication.getLoanAmount().toString());
                    put("beneficiaryName", lendingApplication.getMerchantName());
                    put("businessName", lendingApplication.getBusinessName());
                    put("loanType", lendingApplication.getLoanType());
                }};
                executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.LOAN_APPLICATION_INITIATED_BE.name(), cleverTapEvtData, merchant.getMid()));
            }
            else{
                funnelService.submitEvent(merchant.getId(), null, lendingApplication.getId(),lendingApplication.getLoanType(),
                        FunnelEnums.StageId.APPLICATION, FunnelEnums.StageEvent.INITIATED, LocalDateTime.now().toString());
            }
            return new ApiResponse<>(CreateApplicationResponse.builder().applicationId(lendingApplication.getId()).build());
        } catch (Exception e) {
            log.error("Exception in createNewApplication for merchant:{} {} {}", merchant.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Something went wrong");
        }
    }

    private ApiResponse<?> createNewApplicationV2(BasicDetailsDto merchant, CreateApplicationRequest applicationRequest) {
        log.info("creating new application v2 for merchant:{}", merchant.getId());
        try {
            LendingApplication inProgressLoanApplication = lendingApplicationDao.getLatestPendingApplication(merchant.getId());
            if (!ObjectUtils.isEmpty(inProgressLoanApplication)) {
                log.error("application already exist for this merchant id {}", inProgressLoanApplication);
                return new ApiResponse<>(true, "Application already exist for this merchant id");
            }
            AddressValidationDto  addressValidationDto = null;
            if (applicationRequest != null && applicationRequest.getAddressDetails() != null){
                addressValidationDto = getAddressValidationScore(applicationRequest.getAddressDetails());
                String error = baseChecks(merchant, applicationRequest.getAddressDetails());
                if (error != null) return new ApiResponse<>(false, error);
                if (addressQltyScoreLessThanThreshold(addressValidationDto)) {
                    log.info("address quality score less than 20");
                    return new ApiResponse<>(ApplicationAddressValidation.builder().hasAValidAddress(false).build());
                }
            }

            LendingEligibleLoan eligibleLoan = eligibleLoanDao.findTopByMerchantIdAndOfferTypeAndAmountAndTenureInMonthsOrderByIdDesc(merchant.getId(), "CUSTOM", applicationRequest.getEligibleLoanDTO().getAmount(), applicationRequest.getEligibleLoanDTO().getTenureInMonths());
            if (Objects.isNull(eligibleLoan)) {
                log.error("eligible loan not available for merchant:{} and category:{}", merchant.getId(), applicationRequest.getCategory());
                return new ApiResponse<>(false, "eligible loan not found");
            }
            log.info("Eligible loan found for merchant: {} , {}",
                    merchant.getId(), eligibleLoan);
            LendingApplication lendingApplication = saveLendingApplicationV2(merchant, eligibleLoan, applicationRequest, addressValidationDto);
            String evaluationId = merchant.getId() +"_" + lendingApplication.getLoanAmount().intValue();
            log.info("Evaluation id to fetch initial and fallback lenders : {}", evaluationId);

            LendingAuditTrial lendingAuditTrialInitial =
                    lendingAuditTrialDao.findTopByMerchantIdAndLoanAmountAndTypeAndTenureOrderByIdDesc(merchant.getId(),lendingApplication.getLoanAmount(), "INITIAL_LENDERS",lendingApplication.getTenureInMonths());
            if (lendingAuditTrialInitial != null) {
                log.info("Initial lenders audit trail found for evaluationId: {} : {}", lendingAuditTrialInitial, lendingAuditTrialInitial.getId());
                lendingAuditTrialInitial.setApplicationId(lendingApplication.getId());
                lendingAuditTrialDao.save(lendingAuditTrialInitial);
                log.info("Updated initial lenders audit trail with applicationId: {}", lendingApplication.getId());
            } else {
                log.error("No initial lenders audit trail found for evaluationId: {}", evaluationId);
            }


            LendingAuditTrial lendingAuditTrialFallback =
                    lendingAuditTrialDao.findTopByMerchantIdAndLoanAmountAndTypeAndTenureOrderByIdDesc(merchant.getId(),lendingApplication.getLoanAmount(), "FALLBACK_LENDERS", lendingApplication.getTenureInMonths());
            if (lendingAuditTrialFallback != null) {
                log.info("Fallback lenders audit trail found for evaluationId: {} : {}", lendingAuditTrialFallback, lendingAuditTrialFallback.getId());
                lendingAuditTrialFallback.setApplicationId(lendingApplication.getId());
                lendingAuditTrialDao.save(lendingAuditTrialFallback);
                log.info("Updated fallback lenders audit trail with applicationId: {}", lendingApplication.getId());
            } else {
                log.error("No fallback lenders audit trail found for evaluationId: {}", evaluationId);
            }

            LendingAuditTrial lendingEligibleLenders =
                    lendingAuditTrialDao.findTopByMerchantIdAndLoanAmountAndTypeAndTenureOrderByIdDesc(merchant.getId(),lendingApplication.getLoanAmount(), "ELIGIBLE_LENDERS", lendingApplication.getTenureInMonths());
            if (lendingEligibleLenders != null) {
                log.info("Eligible lenders audit trail found for evaluationId: {} : {}", lendingEligibleLenders, lendingEligibleLenders.getId());
                lendingEligibleLenders.setApplicationId(lendingApplication.getId());
                lendingAuditTrialDao.save(lendingEligibleLenders);
                log.info("Updated fallback lenders audit trail with applicationId: {}", lendingApplication.getId());
            } else {
                log.error("No fallback lenders audit trail found for evaluationId: {}", evaluationId);
            }

            loanUtil.createApplicationSnapshot(lendingApplication, merchant);

            final boolean rejected = checkAndRejectPilotIdentifierApplication(lendingApplication);

            if (rejected) {
                return new ApiResponse<>(false, "Ineligible ! Please try again in sometime");
            }

            if("rejected".equalsIgnoreCase(lendingApplication.getStatus()) && LendingConstants.NONE_LENDER.equalsIgnoreCase(lendingApplication.getLender())){
                return new ApiResponse<>(true, "No lender assigned, application rejected");
            }

            createStatusAuditTrail(lendingApplication);
            executorService.submit(() -> {
                loanUtil.callingDeForReferences(merchant.getId(),lendingApplication);
            });
            loanUtil.publishApplicationEvent(lendingApplication);
            LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchant.getId(), lendingApplication);
            if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
                funnelService.submitEventV3(merchant.getId(), null, lendingApplication.getId(),lendingApplication.getLoanType(),
                        FunnelEnums.StageId.APPLICATION, FunnelEnums.StageEvent.INITIATED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
                HashMap<String, String> cleverTapEvtData = new HashMap<String, String>() {{
                    put("loanAmount", lendingApplication.getLoanAmount().toString());
                    put("beneficiaryName", lendingApplication.getMerchantName());
                    put("businessName", lendingApplication.getBusinessName());
                    put("loanType", lendingApplication.getLoanType());
                }};
                executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.LOAN_APPLICATION_INITIATED_BE.name(), cleverTapEvtData, merchant.getMid()));
            }
            else{
                funnelService.submitEvent(merchant.getId(), null, lendingApplication.getId(),lendingApplication.getLoanType(),
                        FunnelEnums.StageId.APPLICATION, FunnelEnums.StageEvent.INITIATED, LocalDateTime.now().toString());
            }
            return new ApiResponse<>(CreateApplicationResponse.builder().applicationId(lendingApplication.getId()).build());
        } catch (Exception e) {
            log.error("Exception in createNewApplication for merchant:{} {} {}", merchant.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Something went wrong");
        }
    }

    private ApiResponse<?> updateApplicationV2(BasicDetailsDto merchant, CreateApplicationRequest applicationRequest) {
        log.info("updating existing application:{} for merchant:{}", applicationRequest.getApplicationId(), merchant.getId());
        try {
            LendingApplication lendingApplication =
                    lendingApplicationDao.findByIdAndMerchantIdAndStatus(applicationRequest.getApplicationId(), merchant.getId(),
                            ApplicationStatus.DRAFT.name());
            if (lendingApplication == null) {
                LendingResubmitTask lendingResubmitTask = lendingResubmitTaskDao.findTopByApplicationId(applicationRequest.getApplicationId());
                if(lendingResubmitTask != null && lendingResubmitTask.getResubmit() && !lendingResubmitTask.getResubmitDone()){
                    lendingApplication = lendingApplicationDao.findById(applicationRequest.getApplicationId()).get();
                    if(lendingApplication==null){
                        log.info("Application not found for id:{}", applicationRequest.getApplicationId());
                    }
                    lendingApplication.setBusinessName(applicationRequest != null ? applicationRequest.getBusinessName() : null);
                    BusinessDetailsDTO businessDetailsDTO = BusinessDetailsDTO.builder().businessCategory(applicationRequest.getCategory()).
                            businessName(applicationRequest.getBusinessName()).build();
                    addBusinessDetails(businessDetailsDTO,merchant);
                    merchantService.updateMerchantBusinessName(lendingApplication.getMerchantId(), applicationRequest.getBusinessName());
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
            else {
                log.info("Draft application found for id:{}", applicationRequest.getApplicationId());
                if(applicationRequest.getEligibleLoanDTO() != null) {
                    saveAuditTrail(lendingApplication,
                            "OFFER_MODIFIED_LENDER_CHANGE",
                            "OLD_LENDER_" + lendingApplication.getLender(),
                            "NEW_LENDER_" + applicationRequest.getEligibleLoanDTO().getLender());
                    updateLendingApplicationV2(lendingApplication, merchant, applicationRequest.getEligibleLoanDTO(), applicationRequest);
                    createEligibleLoan(merchant.getId(), applicationRequest.getEligibleLoanDTO());
                }
                AddressValidationDto addressValidationDto = null;
                if (applicationRequest != null && applicationRequest.getAddressDetails() != null && isAddressUpdated(lendingApplication, applicationRequest)) {
                    addressValidationDto = getAddressValidationScore(applicationRequest.getAddressDetails());
                    if (addressQltyScoreLessThanThreshold(addressValidationDto)) {
                        log.info("address quality score less than 20");
                        return new ApiResponse<>(ApplicationAddressValidation.builder().hasAValidAddress(false).build());
                    }
                }
                updateApplicationData(lendingApplication, applicationRequest, addressValidationDto);
                return new ApiResponse<>(CreateApplicationResponse.builder().applicationId(lendingApplication.getId()).build());
            }
        } catch (Exception e) {
            log.error("Exception in updateApplication for merchant:{} {}", merchant.getId(), e.getMessage());
            return new ApiResponse<>(false, "Something went wrong");
        }
    }

    private LendingApplication saveLendingApplicationV2(BasicDetailsDto merchantBasicDetails, LendingEligibleLoan eligibleLoan, CreateApplicationRequest lendingApplicationRequest, AddressValidationDto addressValidationDto) {
        LendingApplication lendingApplication = new LendingApplication();

        lendingApplication.setMerchantName(merchantBasicDetails.getBeneficiaryName());
        lendingApplication.setLender(lendingApplicationRequest.getEligibleLoanDTO().getLender());
        lendingApplication.setEdi(Double.valueOf(lendingApplicationRequest.getEligibleLoanDTO().getEdi()));
        lendingApplication.setIoEdi(eligibleLoan.getIoEdi() != null ? Double.valueOf(eligibleLoan.getIoEdi()) : 0D);
        lendingApplication.setRepayment(Double.valueOf(lendingApplicationRequest.getEligibleLoanDTO().getRepaymentAmount()));
        lendingApplication.setInterestRate(lendingApplicationRequest.getEligibleLoanDTO().getRateOfInterest());
        lendingApplication.setProcessingFee(Double.valueOf(eligibleLoan.getProcessingFee()));
        lendingApplication.setDisbursalAmount(lendingApplicationRequest.getEligibleLoanDTO().getAmount() - eligibleLoan.getProcessingFee());
        lendingApplication.setStatus("draft");
        lendingApplication.setMode("AUTO");
        lendingApplication.setMerchantId(merchantBasicDetails.getId());
        lendingApplication.setLoanAmount(lendingApplicationRequest.getEligibleLoanDTO().getAmount());
        lendingApplication.setCategory(eligibleLoan.getCategory());
        lendingApplication.setTenure(lendingApplicationRequest.getEligibleLoanDTO().getTenure());
        lendingApplication.setTenureInMonths(lendingApplicationRequest.getEligibleLoanDTO().getTenureInMonths());
        lendingApplication.setPayableDays(Long.valueOf(eligibleLoan.getEdiCount()));
        lendingApplication.setEdiFreeDays(0);
        lendingApplication.setIoPayableDays(eligibleLoan.getIoEdiDays());
        lendingApplication.setLoanConstruct(eligibleLoan.getLoanConstruct());
        lendingApplication.setLoanType(eligibleLoan.getLoanType());
        lendingApplication.setTotalLoansCount(loanUtil.getPreviousLoans(merchantBasicDetails.getId()).size());
        lendingApplication.setCkycId(String.valueOf(merchantBasicDetails.getId()));
        lendingApplication.setLatitude(!StringUtils.isEmpty(lendingApplicationRequest.getLatitude()) ? lendingApplicationRequest.getLatitude() : null);
        lendingApplication.setLongitude(!StringUtils.isEmpty(lendingApplicationRequest.getLongitude()) ? lendingApplicationRequest.getLongitude() : null);
        lendingApplication.setBusinessName(!StringUtils.isEmpty(lendingApplicationRequest.getBusinessName()) ? lendingApplicationRequest.getBusinessName() : null);
        lendingApplication.setEdiFreeDays(lendingApplicationRequest.getEligibleLoanDTO().getEdiCount() % 30 == 0 ? 0 : 1);
        lendingApplication.setIp(Optional.ofNullable(lendingApplication.getIp()).orElse(lendingApplicationRequest.getIp()));
        lendingApplication = lendingApplicationDao.save(lendingApplication);

        if (lendingApplicationRequest != null) {
            BusinessDetailsDTO businessDetailsDTO = BusinessDetailsDTO.builder()
                    .businessCategory(lendingApplicationRequest.getCategory())
                    .businessName(lendingApplicationRequest.getBusinessName())
                    .build();

            if (businessDetailsDTO != null) {
                addBusinessDetails(businessDetailsDTO, merchantBasicDetails);
            }

            if (lendingApplication != null && lendingApplication.getMerchantId() != null && lendingApplication.getBusinessName() != null) {
                merchantService.updateMerchantBusinessName(lendingApplication.getMerchantId(), lendingApplication.getBusinessName());
            }
        }

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
        lendingApplicationDetails.setIsNachSkip(loanUtil.isEligibleForNachSkip(lendingApplication, lendingApplication.getLender(), true));
        if (loanUtil.isLenderPricingApplicableMerchant(merchantBasicDetails.getId())){
            lendingApplicationDetails.setOfferId(eligibleLoan.getId());
        }
        lendingApplicationDetailsDao.save(lendingApplicationDetails);

        loanDetailsV3Service.saveApplicationViewState(null,lendingApplication.getId(), LendingViewStates.SHOP_DETAILS_PAGE);

        if(LendingConstants.NONE_LENDER.equalsIgnoreCase(lendingApplication.getLender())){
            rejectApplicationForIncorrectLender(lendingApplication);
            return lendingApplication;
        }

        updateApplicationDataV2(lendingApplication, lendingApplicationRequest, addressValidationDto);
        replicateApplicationData(merchantBasicDetails,lendingApplication);
        saveGstDetailsV3(merchantBasicDetails, lendingApplication);
        lenderAssignService.saveLenderChangeAudit(lendingApplication, lendingApplication.getLender(), null);
        //updateLendingAuditTrial(merchantBasicDetails.getId(), lendingApplication);
        log.info("saved lending application details for  {}", lendingApplicationDetails);
        executorService.execute(() -> apiGatewayService.globalLimitTxn(merchantBasicDetails.getId(), "DEBIT", eligibleLoan.getAmount()));
        executorService.execute(() -> {
            JsonNode smsAnalysisData = apiGatewayService.getMerchantSmsAnalysisData(merchantBasicDetails);
            if (smsAnalysisData == null) {
                loanUtil.publishSmsAnalysisData(merchantBasicDetails);
            }
        });
        loanUtil.createLendingAuditTrailDTO(lendingApplication);
        return lendingApplication;
    }

    private LendingApplication  updateLendingApplicationV2(LendingApplication lendingApplication, BasicDetailsDto merchantBasicDetails,  EligibleLoanDTO eligibleLoan, CreateApplicationRequest lendingApplicationRequest) {

        lendingApplication.setLender(eligibleLoan.getLender());
        lendingApplication.setEdi(Double.valueOf(eligibleLoan.getEdi()));
        lendingApplication.setIoEdi(eligibleLoan.getIoEdi() != null ? Double.valueOf(eligibleLoan.getIoEdi()) : 0D);
        lendingApplication.setRepayment(Double.valueOf(eligibleLoan.getRepaymentAmount()));
        lendingApplication.setInterestRate(eligibleLoan.getRateOfInterest());
        lendingApplication.setProcessingFee(eligibleLoan.getProcessingFee().doubleValue());
        lendingApplication.setDisbursalAmount(eligibleLoan.getAmount() - eligibleLoan.getProcessingFee());
        lendingApplication.setMode("AUTO");
        lendingApplication.setLoanAmount(eligibleLoan.getAmount());
        lendingApplication.setCategory(eligibleLoan.getCategory() != null ? eligibleLoan.getCategory() : lendingApplication.getCategory());
        lendingApplication.setTenure(eligibleLoan.getTenure());
        lendingApplication.setTenureInMonths(eligibleLoan.getTenureInMonths());
        lendingApplication.setPayableDays(Long.valueOf(eligibleLoan.getEdiCount()));
        lendingApplication.setEdiFreeDays(0);
        lendingApplication.setIoPayableDays(eligibleLoan.getIoEdiDays() != null ? eligibleLoan.getIoEdiDays() : lendingApplication.getIoPayableDays());
        lendingApplication.setLoanConstruct(eligibleLoan.getLoanConstruct() != null ? eligibleLoan.getLoanConstruct() : lendingApplication.getLoanConstruct());
        lendingApplication.setLoanType(eligibleLoan.getLoanType() != null ? eligibleLoan.getLoanType() : lendingApplication.getLoanType());
        lendingApplication.setTotalLoansCount(loanUtil.getPreviousLoans(merchantBasicDetails.getId()).size());
        lendingApplication.setCkycId(String.valueOf(merchantBasicDetails.getId()));
        lendingApplication.setEdiFreeDays(eligibleLoan.getEdiCount() % 30 == 0 ? 0 : 1);
        lendingApplication.setIp(Optional.ofNullable(lendingApplication.getIp()).orElse(lendingApplicationRequest.getIp()));
        lendingApplication = lendingApplicationDao.save(lendingApplication);

        if (lendingApplicationRequest != null) {
            BusinessDetailsDTO businessDetailsDTO = BusinessDetailsDTO.builder()
                    .businessCategory(lendingApplicationRequest.getCategory())
                    .businessName(lendingApplicationRequest.getBusinessName())
                    .build();

            if (businessDetailsDTO != null) {
                addBusinessDetails(businessDetailsDTO, merchantBasicDetails);
            }

            if (lendingApplication != null && lendingApplication.getMerchantId() != null && lendingApplication.getBusinessName() != null) {
                merchantService.updateMerchantBusinessName(lendingApplication.getMerchantId(), lendingApplication.getBusinessName());
            }
        }

        if (loanUtil.isInternalMerchant(merchantBasicDetails.getId()) || (eligibleLoan.getEdiCount() % 30 == 0)) {
            DateFormat df = new SimpleDateFormat("ddMMyy");
            Date dateobj = new Date();
            String loanId = "BPL" + df.format(dateobj) + lendingApplication.getId();
            lendingApplication.setExternalLoanId(loanId);
            lendingApplication = lendingApplicationDao.save(lendingApplication);
        }

        log.info("assigning lender:{} for application:{}", eligibleLoan.getLender(), lendingApplication.getId());
        LendingLenderQuota lendingLenderQuota = lenderDisbursalLimitsDao.findByLender(eligibleLoan.getLender());
        if(!ObjectUtils.isEmpty(lendingLenderQuota)) {
            lenderAssignService.updateLenderLimits(lendingLenderQuota, lendingApplication);
        }
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
        if(ObjectUtils.isEmpty(lendingApplicationDetails)){
            lendingApplicationDetails = new LendingApplicationDetails();
            lendingApplicationDetails.setApplicationId(lendingApplication.getId());
        }
        lendingApplicationDetails.setLenderAssc(Boolean.FALSE);
        lendingApplicationDetailsDao.save(lendingApplicationDetails);

        loanDetailsV3Service.saveApplicationViewState(null,lendingApplication.getId(), LendingViewStates.OFFER_EVALUATION_PAGE);

        if(LendingConstants.NONE_LENDER.equalsIgnoreCase(lendingApplication.getLender())){
            rejectApplicationForIncorrectLender(lendingApplication);
            return lendingApplication;
        }
        loanUtil.createLendingAuditTrailDTO(lendingApplication);
        return lendingApplication;
    }


    private void updateLendingAuditTrial(Long merchantId, LendingApplication lendingApplication) {
        try {
            String evaluationId = merchantId + "_" + lendingApplication.getLoanAmount().intValue();
            List<LendingAuditTrial> lendingAuditTrials =
                    lendingAuditTrialDao.findByMerchantIdAndLoanAmountAndTenureOrderByIdDesc(merchantId, lendingApplication.getLoanAmount(), lendingApplication.getTenureInMonths());
            if (lendingAuditTrials != null && !lendingAuditTrials.isEmpty()) {
                for (LendingAuditTrial lendingAuditTrial : lendingAuditTrials) {
                    lendingAuditTrial.setApplicationId(lendingApplication.getId());
                    lendingAuditTrialDao.save(lendingAuditTrial);
                }
            }
        } catch (Exception e) {
            log.error("Exception in updateLendingAuditTrial for application:{} , {} {} {}", lendingApplication.getId(), merchantId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    private void updateApplicationDataV2(LendingApplication lendingApplication, CreateApplicationRequest applicationRequest, AddressValidationDto addressValidationDto) {
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
            lendingApplication.setBusinessName(
                    !StringUtils.isEmpty(applicationRequest.getBusinessName()) ?
                            applicationRequest.getBusinessName() :
                            (StringUtils.isEmpty(lendingApplication.getBusinessName()) ? null : lendingApplication.getBusinessName())
            );
            lendingApplicationDao.save(lendingApplication);
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
            lendingApplicationDetails.setCurrentAddressSameAsPermanentAddress(applicationRequest.getCurrentAddressSameAsPermanentAddress());
            lendingApplicationDetailsDao.save(lendingApplicationDetails);

        } catch (Exception e) {
            log.error("Exception in updateApplicationData for application:{} , {} {} {}", lendingApplication.getId(), applicationRequest, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    private boolean checkAndRejectPilotIdentifierApplication(LendingApplication lendingApplication) {

        boolean rejected = false;

        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
        if(Objects.nonNull(lendingRiskVariablesSnapshot) && !ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getPilotIdentifier())
            && lendingRiskVariablesSnapshot.getPilotIdentifier().contains(F_TPV_PILOT_IDENTIFIER)) {
            log.info("rejecting applications with pilot indetifier F_TPV");
            lendingApplication.setStatus("rejected");
            lendingApplication.setManualCibil("REJECTED");
            lendingApplication.setManualCibilReason("credit assesment failed : F_TPV");
            lendingApplication.setCibilApprovedDate(new Date());
            rejected = true;
        }
        lendingApplicationDao.save(lendingApplication);
        return rejected;
    }

    private AddressValidationDto getAddressValidationScore(AddressDetails addressDetails) {
        AddressValidationDto addressValidationDto = null;
        try {
            if (!ObjectUtils.isEmpty(addressDetails)) {
                addressValidationDto = apiGatewayService.validateAddress(addressDetails);
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

    private LendingApplication saveLendingApplication(BasicDetailsDto merchantBasicDetails, Boolean isPreApproved, LendingEligibleLoan eligibleLoan, CreateApplicationRequest lendingApplicationRequest, LendingCategories lendingCategory, AddressValidationDto addressValidationDto, Boolean isApplicableForAggregationFlow) {
        LendingApplication lendingApplication = new LendingApplication();
        BigDecimal processingFee;
        BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
        MaxPricingValuesDTO maxPricingValuesDTO = null;
        if (loanUtil.isLenderPricingApplicableMerchant(merchantBasicDetails.getId())){
            LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchantBasicDetails.getId());
            maxPricingValuesDTO = loanUtil.getMaxPricingValues(lendingRiskVariables, eligibleLoan.getTenureInMonths());
        }
        if (apiGatewayService.eligibleForProcessingFee(merchantBasicDetails.getId())) {
            processingFee = BigDecimal.ZERO;
        } else if (!ObjectUtils.isEmpty(maxPricingValuesDTO)){
            BigDecimal maxProcessingFeeRateBD = BigDecimal.valueOf(maxPricingValuesDTO.getMaxProcessingFeeRate());
           // processingFee = (int) Math.ceil(maxPricingValuesDTO.getMaxProcessingFeeRate() * eligibleLoan.getAmount()/100);
            processingFee = maxProcessingFeeRateBD.multiply(amountBD)
                    .divide(new BigDecimal(100), 0, RoundingMode.CEILING);
        }
        else {
            if(eligibleLoan.getProcessingFee() != null) {
                processingFee = BigDecimal.valueOf(eligibleLoan.getProcessingFee());

            }else{
                throw new NullPointerException("processing fee cannot be null for eligible loan");
            }

        }
        if (!ObjectUtils.isEmpty(maxPricingValuesDTO)){
            loanUtil.setEligibleLoan(eligibleLoan, maxPricingValuesDTO.getMaxInterestRate(), processingFee, eligibleLoan.getAmount(), null);
        }

        lendingApplication.setMerchantName(merchantBasicDetails.getBeneficiaryName());
        lendingApplication.setEdi(Double.valueOf(eligibleLoan.getEdi()));
        lendingApplication.setIoEdi(eligibleLoan.getIoEdi() != null ? Double.valueOf(eligibleLoan.getIoEdi()) : 0D);
        lendingApplication.setRepayment(Double.valueOf(eligibleLoan.getRepayment()));
        lendingApplication.setInterestRate(eligibleLoan.getRateOfInterest());
        lendingApplication.setProcessingFee(processingFee.doubleValue());
        lendingApplication.setDisbursalAmount(eligibleLoan.getAmount() - processingFee.intValue());
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
        lendingApplication.setBusinessName(!StringUtils.isEmpty(lendingApplicationRequest.getBusinessName()) ? lendingApplicationRequest.getBusinessName() : null);
        lendingApplication.setEdiFreeDays(eligibleLoan.getEdiCount() % 30 == 0 ? 0 : 1);
        lendingApplication.setIp(Optional.ofNullable(lendingApplication.getIp()).orElse(lendingApplicationRequest.getIp()));
        lendingApplication = lendingApplicationDao.save(lendingApplication);

        if (lendingApplicationRequest != null) {
            BusinessDetailsDTO businessDetailsDTO = BusinessDetailsDTO.builder()
                    .businessCategory(lendingApplicationRequest.getCategory())
                    .businessName(lendingApplicationRequest.getBusinessName())
                    .build();

            if (businessDetailsDTO != null) {
                addBusinessDetails(businessDetailsDTO, merchantBasicDetails);
            }

            if (lendingApplication != null && lendingApplication.getMerchantId() != null && lendingApplication.getBusinessName() != null) {
                merchantService.updateMerchantBusinessName(lendingApplication.getMerchantId(), lendingApplication.getBusinessName());
            }
        }

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
        lendingApplicationDetails.setIsNachSkip(loanUtil.isEligibleForNachSkip(lendingApplication, lendingApplication.getLender(), true));
        if (loanUtil.isLenderPricingApplicableMerchant(merchantBasicDetails.getId())){
            lendingApplicationDetails.setOfferId(eligibleLoan.getId());
        }
        lendingApplicationDetailsDao.save(lendingApplicationDetails);
        if(isApplicableForAggregationFlow) {
            loanDetailsV3Service.saveApplicationViewState(null,lendingApplication.getId(), LendingViewStates.LENDER_AGGREGATION);
        }
        if (forceSetPiramal && lendingApplication.getMerchantId() == 20000962) { //TODO For Testing
            lendingApplication.setLender("PIRAMAL"); //TODO For Testing
            lendingApplication = lendingApplicationDao.save(lendingApplication);
        } else {
            lenderAssignService.assignLender(lendingApplication, eligibleLoan.getEdiCount() % 30 == 0 ?
                    EdiModel.SEVEN_DAY_MODEL : EdiModel.SIX_DAY_MODEL, merchantBasicDetails, isApplicableForAggregationFlow);
        }

        if(LendingConstants.NONE_LENDER.equalsIgnoreCase(lendingApplication.getLender())){
            rejectApplicationForIncorrectLender(lendingApplication);
            return lendingApplication;
        }

        updateApplicationData(lendingApplication, lendingApplicationRequest, addressValidationDto);
        replicateApplicationData(merchantBasicDetails,lendingApplication);
        saveGstDetailsV3(merchantBasicDetails, lendingApplication);
        log.info("saved lending application details for  {}", lendingApplicationDetails);
        executorService.execute(() -> apiGatewayService.globalLimitTxn(merchantBasicDetails.getId(), "DEBIT", eligibleLoan.getAmount()));
        executorService.execute(() -> {
            JsonNode smsAnalysisData = apiGatewayService.getMerchantSmsAnalysisData(merchantBasicDetails);
            if (smsAnalysisData == null) {
                loanUtil.publishSmsAnalysisData(merchantBasicDetails);
            }
        });
        loanUtil.createLendingAuditTrailDTO(lendingApplication);
        return lendingApplication;
    }

    private void replicateApplicationData(BasicDetailsDto merchant, LendingApplication lendingApplication) {
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

                loanUtil.createRiskVariablesSnapshot(lendingApplication);

                ShopPicturesStateDTO shopPicturesStateDTO = new ShopPicturesStateDTO();
                shopPicturesStateDTO.setMerchantId(lendingApplication.getMerchantId());
                shopPicturesStateDTO.setApplicationId(lendingApplication.getId());
                LoanDetailsV3Response loanDetailsV3Response = new LoanDetailsV3Response();
                if (Boolean.TRUE.equals(loanDetailsV3Service.processLenderSpecificShopPictureRules(merchant, shopPicturesStateDTO, loanDetailsV3Response, lendingApplication))) {
                    log.info("Shop picture skipped for lender: {} and merchant: {}",
                            lendingApplication.getLender(), lendingApplication.getMerchantId());
                    loanDetailsV3Response.setSkipShopPicture(true);
                    loanDetailsV3Response.setImageExist(false);
                    loanDetailsV3Service.updateLendingShopDocumentsIsSkipped(lendingApplication.getMerchantId(), lendingApplication.getId(), loanDetailsV3Response);
                } else {
                    log.info("skipping shop picture validation failed for lender: {} and merchant: {}",
                            lendingApplication.getLender(), lendingApplication.getMerchantId());
                    List<LendingShopDocuments> lendingShopDocuments = lendingShopDocumentsDao.findByMerchantIdAndLendingApplicationId(prevApplication.getMerchantId(), prevApplication.getId());
                    List<LendingShopDocuments> filteredDocuments = lendingShopDocuments.stream()
                            .filter(doc -> doc.getLatitude() != null && doc.getLongitude() != null)
                            .collect(Collectors.groupingBy(LendingShopDocuments::getProofType))
                            .values().stream()
                            .flatMap(docs -> docs.stream().limit(1))
                            .collect(Collectors.toList());
                    if (!filteredDocuments.isEmpty() && filteredDocuments.size() >= 2) {
                        List<LendingShopDocuments> replicatedDocuments = new ArrayList<>();
                        for (LendingShopDocuments shopDocuments : filteredDocuments) {
                            LendingShopDocuments replicateShopDocument = getReplicateShopDocument(lendingApplication, shopDocuments);
                            replicatedDocuments.add(replicateShopDocument);
                        }
                        lendingShopDocumentsDao.saveAll(replicatedDocuments);
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

    private static LendingShopDocuments getReplicateShopDocument(LendingApplication lendingApplication, LendingShopDocuments shopDocuments) {
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
        return replicateShopDocument;
    }

    private void saveGstDetailsV3(BasicDetailsDto merchant, LendingApplication lendingApplication){
        LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchant.getId());
        if("v2".equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
            LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(lendingApplication.getId());
            if(ObjectUtils.isEmpty(lendingGstDetail)){
                LendingGstDetail lendingGstDetail1 = new LendingGstDetail();
                lendingGstDetail1.setApplicationId(lendingApplication.getId());
                lendingGstDetail1.setMerchantId(lendingApplication.getMerchantId());
                lendingGstDetail1.setEntityType("Business");
                lendingGstDao.save(lendingGstDetail1);
            }
        }
    }

    private void updateApplicationData(LendingApplication lendingApplication, CreateApplicationRequest applicationRequest, AddressValidationDto addressValidationDto) {
        try {
            if (applicationRequest.getAddressDetails() != null) {
                AddressDetails addressDetails = applicationRequest.getAddressDetails();
                //ReqAddAddress reqAddAddress = new ReqAddAddress();
                //boolean isMismatch = checkAndUpdateAddressMismatch(lendingApplication, addressDetails, reqAddAddress);
                lendingApplication.setPincode(!StringUtils.isEmpty(addressDetails.getPincode()) ? Long.valueOf(addressDetails.getPincode()) : lendingApplication.getPincode());
                lendingApplication.setArea(!StringUtils.isEmpty(addressDetails.getArea()) ? addressDetails.getArea() : lendingApplication.getArea());
                lendingApplication.setCity(!StringUtils.isEmpty(addressDetails.getCity()) ? addressDetails.getCity() : lendingApplication.getCity());
                lendingApplication.setState(!StringUtils.isEmpty(addressDetails.getState()) ? addressDetails.getState() : lendingApplication.getState());
                lendingApplication.setShopNumber(!StringUtils.isEmpty(addressDetails.getAddress1()) ?
                        addressDetails.getAddress1().substring(0, Math.min(addressDetails.getAddress1().length(), 98)) : lendingApplication.getShopNumber());
                lendingApplication.setStreetAddress(!StringUtils.isEmpty(addressDetails.getAddress2()) ? addressDetails.getAddress2() : lendingApplication.getStreetAddress());
                lendingApplication.setLandmark(!StringUtils.isEmpty(addressDetails.getLandmark()) ? addressDetails.getLandmark() : lendingApplication.getLandmark());
                log.info("shop number getting saved in lending_application: {}", !StringUtils.isEmpty(addressDetails.getAddress1()) ? addressDetails.getAddress1() : lendingApplication.getShopNumber());
//                if (isMismatch) {
//                    log.info("Address mismatch found. Saving updated address details.");
//                    merchantService.addAddress(lendingApplication.getMerchantId(),reqAddAddress);
//                }
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
            lendingApplication.setBusinessName(
                    !StringUtils.isEmpty(applicationRequest.getBusinessName()) ?
                            applicationRequest.getBusinessName() :
                            (StringUtils.isEmpty(lendingApplication.getBusinessName()) ? null : lendingApplication.getBusinessName())
            );
            lendingApplicationDao.save(lendingApplication);
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
            lendingApplicationDetails.setCurrentAddressSameAsPermanentAddress(applicationRequest.getCurrentAddressSameAsPermanentAddress());
            lendingApplicationDetails.setStage(LenderAssociationStages.INIT.name());
            lendingApplicationDetails.setApplicationViewState(getNextLendingViewState(lendingApplication).name());
            lendingApplicationDetailsDao.save(lendingApplicationDetails);

        } catch (Exception e) {
            log.error("Exception in updateApplicationData for application:{} , {} {} {}", lendingApplication.getId(), applicationRequest, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    private LendingViewStates getNextLendingViewState(LendingApplication lendingApplication) {
        if (lendingApplication == null) {
            return LendingViewStates.OFFER_EVALUATION_PAGE;
        }
        boolean isAddressPresent = commonUtil.doesApplicationHaveCompleteAddress(lendingApplication);
        if (!isAddressPresent) {
            return LendingViewStates.SHOP_DETAILS_PAGE;
        }
        boolean hasValidShopPhotos = lendingShopDocumentsDao.hasValidProofTypes(
                lendingApplication.getMerchantId(),
                lendingApplication.getId()
        ) > 0;
        if(!hasValidShopPhotos)
        {
            return LendingViewStates.SHOP_PICTURES_PAGE;
        }

        Boolean bpKycRequired = lendingApplicationServiceV3Base.checkForBPKycRequired(lendingApplication, LenderAssociationStages.INIT);

        return bpKycRequired ? LendingViewStates.KYC_PAGE : LendingViewStates.LENDER_EVALUATION_PAGE;
    }

    private boolean checkAndUpdateAddressMismatch(LendingApplication lendingApplication, AddressDetails addressDetails, ReqAddAddress reqAddAddress) {
        boolean isMismatch = false;
        log.info("Checking and updating address mismatch for application: {}", lendingApplication.getId());

        reqAddAddress.setPincode(String.valueOf(addressDetails.getPincode()));
        reqAddAddress.setArea(addressDetails.getArea());
        reqAddAddress.setCity(addressDetails.getCity());
        reqAddAddress.setState(addressDetails.getState());
        reqAddAddress.setAddress1(addressDetails.getAddress1());
        reqAddAddress.setAddress2(addressDetails.getAddress2());
        reqAddAddress.setLandmark(addressDetails.getLandmark());
        reqAddAddress.setType("Shop/Office");

        if (!StringUtils.equals(String.valueOf(lendingApplication.getPincode()), addressDetails.getPincode())) {
            isMismatch = true;
        }
        if (!StringUtils.equals(lendingApplication.getArea(), addressDetails.getArea())) {
            isMismatch = true;
        }
        if (!StringUtils.equals(lendingApplication.getCity(), addressDetails.getCity())) {
            isMismatch = true;
        }
        if (!StringUtils.equals(lendingApplication.getState(), addressDetails.getState())) {
            isMismatch = true;
        }
        if (!StringUtils.equals(lendingApplication.getShopNumber(), addressDetails.getAddress1())) {
            isMismatch = true;
        }
        if (!StringUtils.equals(lendingApplication.getStreetAddress(), addressDetails.getAddress2())) {
            isMismatch = true;
        }
        if (!StringUtils.equals(lendingApplication.getLandmark(), addressDetails.getLandmark())) {
            isMismatch = true;
        }

        log.info("Address details set in reqAddAddress: {}", reqAddAddress);
        return isMismatch;
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

    private LendingGstDetail saveGstDetails(LendingApplication lendingApplication, ProfessionalDetails professionalDetails) {
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
            return lendingGstDetail;
        } catch (Exception e) {
            log.error("Exception in saveGstDetails for application:{}", lendingApplication.getId(), e);
        }
        return null;
    }

    private String baseChecks(BasicDetailsDto merchant, AddressDetails addressDetails) {

        if (easyLoanUtil.isDummyMerchant(merchant.getId())) {
            return null;
        }

        log.info("result:{}, {}", Objects.isNull(addressDetails),  Objects.isNull(addressDetails.getPincode()));
        log.info("pincode:{} for merchant:{}",addressDetails.getPincode(), merchant.getId());
        log.info("address details:{} for merchant:{}", addressDetails, merchant.getId());
        if (Objects.isNull(addressDetails) || Objects.isNull(addressDetails.getPincode())) {
            log.info("pincode not found in address details ;{} for merchant:{}", merchant.getId(), addressDetails);
            return "pincode not found";
        }
       /* LendingApplication openApplication = lendingApplicationDao.getLatestPendingApplication(merchant.getId());
        if (openApplication != null) {
            log.info("Already open application found for merchant:{}", merchant.getId());
            return "Application already exist for this merchant id";
        }*/
        Integer pincode = Integer.valueOf(addressDetails.getPincode());
        if (loanUtil.isOGL(pincode)) {
            log.info("OGL pincode found for merchant:{}", merchant.getId());
            return "OGL pincode";
        }
        Experian experian = experianDao.getByMerchantId(merchant.getId());
        if (experian != null && experian.getPincode() != null && !pincode.equals(experian.getPincode())) {
            log.info("pincode mismatch for merchant:{}", merchant.getId());
            return "pincode mismatch";
        }

        if (loanUtil.hasActiveLoan(merchant)) {
            log.info("Already an ongoing loan exists for the merchant : {}", merchant.getId());
            return "Already an ongoing loan exists";
        }

        return null;
    }

    public Boolean checkForPreapprovedRepeatLoan(Long merchantId, CreateApplicationRequest applicationRequest) {
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchantId);
        if (lendingRiskVariables != null) {
            String pilotIdentifier = lendingRiskVariables.getPilotIdentifier();
            if (!ObjectUtils.isEmpty(pilotIdentifier) && pilotIdentifier.contains(LoanDetailsConstant.PREAPPROVED_REPEAT_LOAN_IDENTIFIER)) {
                log.info("loan request is pre-approved repeat for {}", merchantId);
                fetchPreviousApplicationData(merchantId, applicationRequest);
                return true;
            }
        }
        return false;
    }

    public void fetchPreviousApplicationData(Long merchantId, CreateApplicationRequest applicationRequest){
        LendingApplication prevApplication =
                lendingApplicationDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchantId, "APPROVED");
        log.info("pervious application for merchant {} : {}", merchantId, prevApplication);
        if(!ObjectUtils.isEmpty(prevApplication)){
            AddressDetails addressDetails = new AddressDetails();
            addressDetails.setPincode(prevApplication.getPincode().toString());
            addressDetails.setArea(prevApplication.getArea());
            addressDetails.setCity(prevApplication.getCity());
            addressDetails.setState(prevApplication.getState());
            addressDetails.setAddress1(prevApplication.getShopNumber());
            addressDetails.setAddress2(prevApplication.getStreetAddress());
            addressDetails.setLandmark(prevApplication.getLandmark());

            applicationRequest.setAddressDetails(addressDetails);
            applicationRequest.setBusinessName(prevApplication.getBusinessName());
        }
        log.info("CreateApplicationRequest for  {} : {}", merchantId, applicationRequest);
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

    private List<LendingEligibleLoan> fetchEligibleLoansForCreateApplication(Long merchantId, String category, String offerType) {
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

            log.info("Checking if Nach is ineligible for application: {}", lendingApplication.getId());
            boolean isNachIneligible = loanUtil.isMandateSwitchEnabled(lendingApplication) ? loanUtil.isLendingApplicationIneligibleForNach(lendingApplication) : false;
            log.info("Nach ineligibility status for application {}: {}", lendingApplication.getId(), isNachIneligible);

            boolean isSmallTicketLoan = LoanType.SMALL_TICKET.name().equalsIgnoreCase(lendingApplication.getLoanType());
            if (ApplicationStatus.DRAFT.name().equalsIgnoreCase(lendingApplication.getStatus()) || ApplicationStatus.DELETED.name().equalsIgnoreCase(lendingApplication.getStatus())) {
                return new ApiResponse<>(false, "Application not in pending state");
            }
            LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchantBasicDetailsDto.getId());

            ApplicationStatusResponseDTO applicationStatusResponseDTO = new ApplicationStatusResponseDTO();
            applicationStatusResponseDTO.setBpClubMember(apiGatewayService.eligibleForProcessingFee(merchantBasicDetailsDto.getId()));
            LendingCategories lendingCategories = lendingCategoryDao.getByCategory(lendingApplication.getCategory());
            MerchantNachDetailsResponseDTO successEnach = loanUtil.getSuccessNach(merchantBasicDetailsDto.getId(), lendingApplication.getId());
            if(ObjectUtils.isEmpty(successEnach) && loanUtil.isEligibleForNachSkip(lendingApplication, lendingApplication.getLender(), true)){
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
            String transferDays = easyLoanUtil.isDummyMerchant(merchantBasicDetailsDto.getId()) ? DUMMY_MERCHANT_TRANSFER_DAYS_TEXT : loanUtil.getApplicationTatMessage(lendingApplication);
            List<ApplicationDTO> applicationDTO = new ArrayList<>();
            ApplicationStatusResponseDTO.ApplicationLoanDetailsDTO applicationLoanDetailsDTO = new ApplicationStatusResponseDTO.ApplicationLoanDetailsDTO();
            applicationLoanDetailsDTO.setAmount(lendingApplication.getLoanAmount());
            applicationLoanDetailsDTO.setFailedMsg("");
            applicationLoanDetailsDTO.setOrderID(lendingApplication.getExternalLoanId());
            applicationLoanDetailsDTO.setTransferDays(transferDays);
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
                if("v2".equalsIgnoreCase(loanDashboardApiVersion.getApiVersion()))applicationDTO1.setText("App Submitted");
                else applicationDTO1.setText("Application Submitted");
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
//                applicationDTO.add(applicationDTO2);
            } else if (successEnach != null || "APPROVED".equals(lendingApplication.getNachStatus())) {
                applicationDTO2.setStatus(!ObjectUtils.isEmpty(successEnach) ? successEnach.getStatus() : lendingApplication.getNachStatus());
                applicationDTO2.setText("e-NACH Done");
                applicationDTO2.setButtonContextDTO(null);
                applicationDTO2.setDisabled(("rejected".equalsIgnoreCase(lendingApplication.getStatus())));
                ApplicationDTO.DateDTO dateDTO = new ApplicationDTO.DateDTO();
                dateDTO.setDay(getDateInFormat(ObjectUtils.isEmpty(successEnach)?lendingApplication.getCreatedAt():successEnach.getCreatedAt()));
                dateDTO.setTime(getDateInFormat(ObjectUtils.isEmpty(successEnach)?lendingApplication.getCreatedAt():successEnach.getCreatedAt()));
                applicationDTO2.setDateDTO(dateDTO);
//                applicationDTO.add(applicationDTO2);
            } else if ("pending_verification".equalsIgnoreCase(lendingApplication.getStatus()) && loanUtil.isEnachBank(merchantBasicDetailsDto.getId())) {
                if("PENDING_VERIFICATION".equalsIgnoreCase(lendingApplication.getNachStatus())){
                    applicationDTO2.setStatus("PENDING_VERIFICATION");
                    applicationDTO2.setText("e-NACH Verification Pending");
                    applicationDTO2.setButtonContextDTO(null);
                    applicationDTO2.setDisabled(("rejected".equalsIgnoreCase(lendingApplication.getStatus())));
//                    applicationDTO.add(applicationDTO2);
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
//                    applicationDTO.add(applicationDTO2);
                }
            }
            boolean enachMandatory = true; //TODO when enach skip is true then uncomment below code
            boolean enachSkipped = loanUtil.isNachSkipped(merchantBasicDetailsDto.getId(), lendingApplication.getId());
            if (successEnach != null) {
                enachMandatory = false;
            }
            else if(isNachIneligible){
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

            // UPI-Autopay Status
            if(isEligibleToShowUpiAutopayStatus(lendingApplication)){
                log.info("Fetching UPI Autopay Details for application: {}", lendingApplication.getId());
                ApplicationDTO upiAutopayDTO = fetchUpiAutopayDetails(lendingApplication);
                applicationDTO.add(upiAutopayDTO);
            }

            // E-Nach Status
            if(!isNachIneligible){
                applicationDTO.add(applicationDTO2);
            }

            if (vkycService.isVkycEnabled(lendingApplication.getMerchantId(), lendingApplication.getLender(), LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()))) {
                LendingApplicationVkycDetails vkycDetails = lendingApplicationVkycDetailsDao.findByApplicationIdAndLender(lendingApplication.getId(), lendingApplication.getLender()).orElse(null);
                String status = "PENDING";
                if (!ObjectUtils.isEmpty(vkycDetails) && VkycStatus.getTerminatedVkycStatusList().contains(vkycDetails.getStatus())) {
                    status = VkycStatus.getSuccessVkycStatusList().contains(vkycDetails.getStatus()) ? "APPROVED" : "REJECTED";
                }

                if (shouldSkipVkycVerificationForCS(lendingApplication, vkycDetails)) {
                    log.info("VKYC skipped for application: will not show the VKYC Verification {} | vkycDetails: {} | lender: {}", lendingApplication.getId(), vkycDetails, lendingApplication.getLender());
                } else if (!ObjectUtils.isEmpty(vkycDetails)) {
                    ApplicationDTO vKycDTO = new ApplicationDTO();
                    vKycDTO.setText("VKYC Verification");
                    vKycDTO.setDisabled(enachMandatory);
                    vKycDTO.setDisabled("rejected".equalsIgnoreCase(lendingApplication.getStatus()));
                    vKycDTO.setStatus(status);
                    applicationDTO.add(vKycDTO);
                }
            }

            if(udyamRegistrationRequiredLenders.contains(lendingApplication.getLender())) {
                LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
                if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails) && !ObjectUtils.isEmpty(lendingApplicationLenderDetails.getDataUploadStatus())) {
                    ApplicationDTO udyamRegistrationDto = new ApplicationDTO();
                    udyamRegistrationDto.setText("Udyam Registration");
                    udyamRegistrationDto.setDisabled(enachMandatory);
                    udyamRegistrationDto.setDisabled("rejected".equalsIgnoreCase(lendingApplication.getStatus()));
                    udyamRegistrationDto.setStatus(udyamSuccessStatus.contains(lendingApplicationLenderDetails.getDataUploadStatus()) ? "APPROVED" : "PENDING" );
                    applicationDTO.add(udyamRegistrationDto);
                }
            }

            ApplicationDTO applicationDTO3 = new ApplicationDTO();
            if("v2".equalsIgnoreCase(loanDashboardApiVersion.getApiVersion()))applicationDTO3.setText("Doc Verification");
            else applicationDTO3.setText("Document Verification");
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
            String cpvStatus = lendingApplication.getPhysicalVerificationStatus() != null &&
                    (lendingApplication.getPhysicalVerificationStatus().equalsIgnoreCase("APPROVED") ||
                            lendingApplication.getPhysicalVerificationStatus().equalsIgnoreCase("REJECTED")) ?
                    lendingApplication.getPhysicalVerificationStatus() : "PENDING";
            String pncRejectionReason = lendingApplication.getRejectionStage() != null
                    && "PNC".equalsIgnoreCase(lendingApplication.getRejectionStage().name()) &&  lendingApplication.getRejectionReason()!=null ? lendingApplication.getRejectionReason() : null;
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
                if("v2".equalsIgnoreCase(loanDashboardApiVersion.getApiVersion()))applicationDTO5.setText("Verification Call");
                else applicationDTO5.setText("Disbursal Review & Calling");
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
                if("v2".equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())) applicationDTO6.setText("Decision Pending");
                else applicationDTO6.setText("Disbursal!");
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
            } else if ("Rejected_At_PNC_Hard_Failure".equalsIgnoreCase(pncRejectionReason) || "Rejected_At_PNC_Pending_Verification".equalsIgnoreCase(pncRejectionReason)) {
                String rejectionMessage = easyLoanUtil.getRejectionMessage(lendingApplication.getRejectionReason(), RejectionStage.PNC);
                rejectionMessage = Objects.nonNull(rejectionMessage) ? rejectionMessage : "Please re-apply";
                String rejectionReason = Objects.nonNull(lendingApplication.getRejectionReason()) ? lendingApplication.getRejectionReason() : null;
                headerDTO.setTitle("Application Rejected");
                headerDTO.setComment(rejectionMessage);
                applicationStatusResponseDTO.setRejectionReason(rejectionReason);
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

            LendingApplicationLenderDetailsSlave lendingApplicationLenderDetailsSlave = lendingApplicationLenderDetailsDaoSlave.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), "ACTIVE", lendingApplication.getLender());
            if(!ObjectUtils.isEmpty(lendingApplicationLenderDetailsSlave)){
                Double annualROI = lendingApplicationLenderDetailsSlave.getAnnualRoi();
                applicationLoanDetailsDTO.setAnnualRoi(annualROI);
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

    private boolean isEligibleToShowUpiAutopayStatus(LendingApplication lendingApplication) {
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
        if(ObjectUtils.isEmpty(lendingApplicationDetails)){
            log.error("LendingApplicationDetails not found for application: {}", lendingApplication.getId());
            return false;
        }
        log.info("Checking if UPI Autopay status should be shown for application: {} and lending application details: {}", lendingApplication.getId(), lendingApplicationDetails);
        if(loanUtil.isMandateSwitchEnabled(lendingApplication) && lendingApplicationDetails.isAutoPayUpiEligible() && loanUtil.isEligibleForUpiAutopayDedicatedScreen(lendingApplication)){
            return true;
        }
        else if(!loanUtil.isMandateSwitchEnabled(lendingApplication) && loanUtil.isEligibleForUpiAutopayDedicatedScreen(lendingApplication)){
            return true;
        }
        return false;
    }

    private ApplicationDTO fetchUpiAutopayDetails(LendingApplication lendingApplication) {
        log.info("Fetching UPI Autopay details for Application Id: {}", lendingApplication.getId());
        ApplicationDTO upiAutopayDetails = new ApplicationDTO();
        upiAutopayDetails.setText("UPI Autopay Done");
        upiAutopayDetails.setDisabled("rejected".equalsIgnoreCase(lendingApplication.getStatus()));

        AutoPayUPI autoPayUPI = autoPayUPIDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
        if(ObjectUtils.isEmpty(autoPayUPI)){
            upiAutopayDetails.setStatus("PENDING");
            return upiAutopayDetails;
        }

        log.info("Autopay Upi Mandate found for Application Id: {} : {}", lendingApplication.getId(), autoPayUPI);


        upiAutopayDetails.setStatus(AutoPayStatusEnum.ACTIVE.equals(autoPayUPI.getStatus()) ? "APPROVED" : "PENDING");

        upiAutopayDetails.setComment(PaymentConstants.UPI_AUTOPAY_ERROR_CODE_TO_DISPLAY_MESSAGE_MAP.getOrDefault(autoPayUPI.getErrorCode(), "AutoPay not completed"));
        ApplicationDTO.DateDTO dateDTO = new ApplicationDTO.DateDTO();
        dateDTO.setDay(ObjectUtils.isEmpty(autoPayUPI) ? getDateInFormat(lendingApplication.getCreatedAt()) : getDateInFormat(autoPayUPI.getCreatedAt()));
        dateDTO.setTime(ObjectUtils.isEmpty(autoPayUPI) ? getDateInFormat(lendingApplication.getCreatedAt()) : getDateInFormat(autoPayUPI.getCreatedAt()));

        upiAutopayDetails.setDateDTO(dateDTO);
        return upiAutopayDetails;
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
            String transferDays = easyLoanUtil.isDummyMerchant(basicDetailsDto.get().getId()) ? DUMMY_MERCHANT_TRANSFER_DAYS_TEXT : loanUtil.getApplicationTatMessage(lendingApplication);
            List<ApplicationDTO> applicationDTO = new ArrayList<>();
            ApplicationStatusResponseDTO.ApplicationLoanDetailsDTO applicationLoanDetailsDTO = new ApplicationStatusResponseDTO.ApplicationLoanDetailsDTO();
            applicationLoanDetailsDTO.setAmount(lendingApplication.getLoanAmount());
            applicationLoanDetailsDTO.setFailedMsg("");
            applicationLoanDetailsDTO.setOrderID(lendingApplication.getExternalLoanId());
            applicationLoanDetailsDTO.setTransferDays(transferDays);
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
                        put("loanAmount", lendingApplication.getLoanAmount().toString());
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
            if(Objects.nonNull(lendingResubmitTask) && (LendingResubmitEnum.RESUBMIT.equals(resubmitApplicationDTO.getType()))){
                if(Objects.nonNull(lendingResubmitTask.getResubmit()) && lendingResubmitTask.getResubmit() &&
                        Objects.nonNull(lendingResubmitTask.getResubmitDone()) && !lendingResubmitTask.getResubmitDone()
                ) {
                    return new ApiResponse<>(false,"application already resubmited");
                }
            }
            if(Objects.nonNull(lendingResubmitTask) && (LendingResubmitEnum.DOWNGRADE.equals(resubmitApplicationDTO.getType()))){
                if(Objects.nonNull(lendingResubmitTask.getDowngrade()) && lendingResubmitTask.getDowngrade() &&
                        Objects.nonNull(lendingResubmitTask.getDowngradeDone()) && !lendingResubmitTask.getDowngradeDone()
                ) {
                    return new ApiResponse<>(false,"application already resubmited");
                }
            }

            if (LendingResubmitEnum.DOWNGRADE.equals(resubmitApplicationDTO.getType()) && offerDowngradeDisabledLenders.contains(lendingApplication.getLender())) {
                return new ApiResponse<>(false,"offer downgrade disabled for lender " + lendingApplication.getLender());
            }

            LendingApplicationPriority lendingApplicationPriority = lendingApplicationPriorityDao.findByApplicationId(lendingApplication.getId());
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

                createLendingResubmitReasonCountRecord(lendingApplication, resubmitApplicationDTO.getResubmitReason(), resubmitApplicationDTO.getResubmitCount());
                if(!ObjectUtils.isEmpty(lendingApplicationPriority)){
                    lendingApplicationPriority.setTatStartTime(new Date());
                }

                LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
                lendingAuditTrial.setMerchantId(lendingApplication.getMerchantId());
                lendingAuditTrial.setApplicationId(lendingApplication.getId());
                lendingAuditTrial.setLoanId(lendingApplication.getExternalLoanId());
                lendingAuditTrial.setType("APP_STATUS");
                lendingAuditTrial.setNewStatus(resubmitApplicationDTO.getType().toString());
                lendingAuditTrial.setOldStatus(lendingApplication.getStatus());
                lendingAuditTrial.setUserId(0L);
                lendingAuditTrialDao.save(lendingAuditTrial);

                if(resubmitApplicationDTO.getResubmitReason().contains("INCORRECT_SELFIE")) {
                    LendingApplicationKycDetails lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
                    if(!ObjectUtils.isEmpty(lendingApplicationKycDetails)) {
                        log.info("Updating lending application kyc details for selfie resubmit : {}", lendingApplication.getId());
                        lendingApplicationKycDetails.setConsentDate(null);
                        lendingApplicationKycDetails.setSelfieApprovedAt(null);
                        lendingApplicationKycDetails.setSelfieUrl(null);
                        lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails);
                    }
                }

            } else if(resubmitApplicationDTO.getType().name().equalsIgnoreCase(LendingResubmitEnum.DOWNGRADE.name())){
                Double previousOferAmount = lendingApplication.getLoanAmount();
                Integer previousTenureInMonths = lendingApplication.getTenureInMonths();
                Boolean downGradeStatus= downgradeApplication(lendingApplication, resubmitApplicationDTO);
                double loanAmountDifference = previousOferAmount - lendingApplication.getLoanAmount();
                if(downGradeStatus && (loanAmountDifference > 0 || !Objects.equals(previousTenureInMonths, lendingApplication.getTenureInMonths()))){
                    if(lendingApplication.getLender().equalsIgnoreCase(Lender.TRILLIONLOANS.toString())) {
                        if(!invokeUpdateLeadApi(lendingApplication, true)) {
                            return new ApiResponse<>(false, "Downgrade initiation failed for lender "+lendingApplication.getLender());
                        }
                    }
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
                    if(!ObjectUtils.isEmpty(lendingApplicationPriority)){
                        lendingApplicationPriority.setTatStartTime(new Date());
                    }
                }else if(!downGradeStatus) {
                    lendingApplication.setManualKyc("REJECTED");
                    lendingApplication.setManualKycReason("DOWNGRADE_REJECT");
                    lendingApplication.setLmsStage("QC_REJECTED");
                    lendingApplication.setStatus("rejected");
                    lendingApplicationDao.save(lendingApplication);
                } else if (loanAmountDifference == 0) {
                    lendingApplication.setLmsStage(LendingConstants.PENDING_DISBURSAL);
                    lendingApplicationDao.save(lendingApplication);
                    loanDashboardService.deleteLoanDashboardCache(resubmitApplicationDTO.getMerchantId());

                    loanUtil.checkForPendingDisbursalStageSkip(lendingApplication, MDC.get("requestId"));

                    return new ApiResponse<>(true,"Application Submitted Successfully");
                }
            }
            lendingResubmitTaskDao.save(lendingResubmitTask);
            lendingApplicationPriorityDao.save(lendingApplicationPriority);

            loanDashboardService.deleteLoanDashboardCache(resubmitApplicationDTO.getMerchantId());
            return new ApiResponse<>(true,"Application Submitted Successfully");
        }catch (DowngradeConfigNotFoundException e) {
            log.info("Exception while downgrading application for applicationId:{} {}", resubmitApplicationDTO.getApplicationId(), e.getMessage());
            return new ApiResponse<>(false, "Downgrade config not found");
        }catch (Exception e){
            log.error("Exception in resubmit application for application:{}, {}, {}", resubmitApplicationDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        loanDashboardService.deleteLoanDashboardCache(resubmitApplicationDTO.getMerchantId());
        return new ApiResponse<>(false,"Something went wrong");
    }

    public boolean invokeUpdateLeadApi(LendingApplication lendingApplication, boolean isDowngradeInitiateFlow) {
        log.info("Calling update lead api for applicationId: {} & merchantId: {}", lendingApplication.getId(), lendingApplication.getMerchantId());
        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto = new LenderAssociationDetailsRequestDto();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
        if(ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.error("Lending application lender details not found for applicationId: {} & lender: {}", lendingApplication.getId(), lendingApplication.getLender());
            return false;
        }
        try {
            if(lendingApplicationLenderDetails.getLeadStatus().equals(LenderAssociationStatus.UPDATE_LEAD_DOWNGRADE_COMPLETED.name())) {
                return true;
            }

            lenderAssociationDetailsRequestDto.setApplicationId(lendingApplication.getId());
            lenderAssociationDetailsRequestDto.setManageState(true);
            lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UPDATE_LEAD_DOWNGRADE_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);

            NBFCRequestDTO updateLeadRequestDto = getPayload(lendingApplicationLenderDetails, lendingApplication);
            if (Objects.isNull(updateLeadRequestDto)) {
                log.info("error in creating payload while invoking update-lead api of applicationId: {}", lendingApplication.getId());
                return false;
            }

            int retry = 0;
            while (retry < 3) {
                NBFCResponseDTO nbfcResponseDTO = lenderAPIGateway.invokeStage(updateLeadRequestDto, LenderAssociationStages.UPDATE_LEAD);
                log.info("update lead response from nbfc: {} with applicationId: {}", nbfcResponseDTO, lendingApplication.getId());
                if (Objects.nonNull(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && Objects.nonNull(nbfcResponseDTO.getData())) {
                    TLUpdateLeadDowngradeResponseDto tlUpdateLeadDowngradeResponseDto = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), TLUpdateLeadDowngradeResponseDto.class);
                    lendingApplicationLenderDetails.setLeadId(String.valueOf(tlUpdateLeadDowngradeResponseDto.getResourceId()));
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UPDATE_LEAD_DOWNGRADE_COMPLETED.name());
                    commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                    return true;
                }
                retry++;
            }
        }catch (Exception e) {
            log.error("Exception while calling updateLead api for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UPDATE_LEAD_DOWNGRADE_FAILED.name());
        lenderAssociationDetailsRequestDto.setManageState(true);
        commonService.manageApplicationState(lenderAssociationDetailsRequestDto);

        if(isDowngradeInitiateFlow){
            log.info("Error response from update-lead api for applicationId: {}", lendingApplication.getId());
            lendingApplication.setManualKyc("REJECTED");
            lendingApplication.setManualKycReason("DOWNGRADE_REJECT");
            lendingApplication.setLmsStage("QC_REJECTED");
            lendingApplication.setStatus("rejected");
            lendingApplicationDao.save(lendingApplication);
        }
        return false;
    }

    public NBFCRequestDTO getPayload(LendingApplicationLenderDetails lendingApplicationLenderDetails, LendingApplication lendingApplication) {
        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(TLUpdateLeadDowngradeRequestDto.builder()
                            .leadId(lendingApplicationLenderDetails.getLeadId())
                            .loanAmountRequested(String.valueOf(lendingApplication.getLoanAmount()))
                            .tenure(lendingApplication.getPayableDays())
                            .rateOfInterest(String.valueOf(lendingApplicationLenderDetails.getAnnualRoi()))
                            .build())
                    .build();
        }catch (Exception e) {
            log.info("Exception in getPayload for applicationId: {} {}", lendingApplication.getId(), e);
        }
        return null;
    }

    private void createLendingResubmitReasonCountRecord(LendingApplication lendingApplication, String resubmitReasons, Integer resubmitCount) {
        log.info("Creating entry in lending resubmit reason count for {}", lendingApplication.getId());
        List<String> resubmitReasonList = Arrays.asList(resubmitReasons.split("\\s*,\\s*"));

        for(String resubmitReason : resubmitReasonList){
            LendingResubmitReasonCount lendingResubmitReasonCount = new LendingResubmitReasonCount();
            lendingResubmitReasonCount.setApplicationId(lendingApplication.getId());
            lendingResubmitReasonCount.setResubmitCount(Objects.isNull(resubmitCount) ? 1 : resubmitCount);
            lendingResubmitReasonCount.setMerchantId(lendingApplication.getMerchantId());
            lendingResubmitReasonCount.setResubmitDone(false);
            lendingResubmitReasonCount.setResubmit(true);
            lendingResubmitReasonCount.setResubmitReason(resubmitReason);
            lendingResubmitReasonCount.setResubmitTimestamp(new Date());
            lendingResubmitReasonCountDao.save(lendingResubmitReasonCount);
            log.info("LendingResubmitReasonCount : {}", lendingResubmitReasonCount);
        }
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
            //ProcessingFee change to BigDecimal for calculation and then store the double value in lending_application table
            BigDecimal processingFee = BigDecimal.ZERO;
            if (lendingApplication.getProcessingFee() > 0 && lendingApplication.getProcessingFee() != null) {
                BigDecimal loanAmountBD = new BigDecimal(loanAmount);
                BigDecimal processingFeeRate = BigDecimal.valueOf(lendingApplication.getProcessingFee());
                BigDecimal loanAmountInApp = BigDecimal.valueOf(lendingApplication.getLoanAmount());

                processingFee = loanAmountBD.multiply(processingFeeRate)
                        .divide(loanAmountInApp, 0, RoundingMode.CEILING);
            }
            else{
                throw new NullPointerException("processing Fee can not be null for lending application");
            }
            double interestAmount = (loanAmount * lendingApplication.getInterestRate() * lendingApplication.getTenureInMonths() / 100);
            double ediAmount = ((loanAmount + interestAmount) / lendingApplication.getPayableDays());
            ediAmount = ediUtil.getEdiAfterRoundingLogic(lendingApplication.getId(), ediAmount, lendingApplication.getLender());
            Integer repayment = Math.round(lendingApplication.getPayableDays() * (int)ediAmount);
            lendingApplication.setEdi(ediAmount);
            lendingApplication.setRepayment(Double.valueOf(repayment));
            lendingApplication.setProcessingFee(processingFee.doubleValue());
            lendingApplication.setDisbursalAmount(loanAmount - processingFee.intValue());
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
        }catch (DowngradeConfigNotFoundException e) {
            throw e;
        }catch(Exception e){
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
            LoanDowngradeConfigEntity loanDowngradeConfigEntity = null;
            for (LoanDowngradeConfigEntity loanDowngradeConfig: loanDowngradeConfigEntities) {
                if (loanDowngradeConfig.getTenure() <= lendingApplication.getTenureInMonths()) {
                    loanDowngradeConfigEntity = loanDowngradeConfig;
                    break;
                }
            }
            if(ObjectUtils.isEmpty(loanDowngradeConfigEntity)) {
                throw new DowngradeConfigNotFoundException("Downgrade config not found");
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
                        : (long) easyLoanUtil.getEdiDays(LoanUtil.getEdiModal(lendingApplication), loanDowngradeConfigEntity.getTenure()));
                lendingApplicationDao.save(lendingApplication);
            }

            return amount;
        } catch (DowngradeConfigNotFoundException e){
            throw e;
        }catch (Exception e) {
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
            List<String> updatedResubmitReasonsList = fetchUpdatedResubmitReasonsList(resubmitReasonList);
            LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchantId, lendingApplication);
            Integer maxCount = -1;
            for(LendingResubmitReasonCount lendingResubmitReasonCount : lendingResubmitReasonCountList){
                if(lendingResubmitReasonCount.getResubmitCount() > maxCount)maxCount = lendingResubmitReasonCount.getResubmitCount();
            }
            boolean syncedShopPhoto = false;
            for(LendingResubmitReasonCount lendingResubmitReasonCount : lendingResubmitReasonCountList){
                if(lendingResubmitReasonCount.getResubmitCount() != maxCount)continue;
                for(String resubmitReason : updatedResubmitReasonsList){
                    if(resubmitReason.equalsIgnoreCase(lendingResubmitReasonCount.getResubmitReason())){
                        if(easyLoanUtil.percentScaleUp(merchantId, shopPhotoSyncRollout))
                            if(!syncedShopPhoto && "SHOP_PHOTO".equalsIgnoreCase(resubmitReason)){
                                kycHandler.syncShopPhoto(merchantId, applicationId);
                                syncedShopPhoto=true;
                            }
                        lendingResubmitReasonCount.setResubmitDone(Boolean.TRUE);
                        lendingResubmitReasonCount.setResubmittedAt(new Date());
                        lendingResubmitReasonCountDao.save(lendingResubmitReasonCount);
                        if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
                            funnelService.submitEventV3(merchantId, null, applicationId, FunnelEnums.StageId.RESUBMIT,
                                    FunnelEnums.StageEvent.COMPLETED, resubmitReason, LoanDetailsConstant.FUNNEL_VERSION_TAG);
                        }
                        else{
                            funnelService.submitEvent(merchantId, null, applicationId, FunnelEnums.StageId.RESUBMIT,
                                    FunnelEnums.StageEvent.COMPLETED, resubmitReason);
                        }
                    }
                }
                resubmitCompleted = resubmitCompleted && lendingResubmitReasonCount.getResubmitDone();
            }

            if(resubmitCompleted){
                lendingResubmitTask.setResubmitDone(Boolean.TRUE);
                lendingResubmitTask.setResubmittedAt(new Date());
                lendingResubmitTaskDao.save(lendingResubmitTask);

                lendingApplication.setLmsStage("PENDING_QC_ASSIGNMENT");
                lendingApplicationDao.save(lendingApplication);

                loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.getId(), vkycService.getLenderVkycPageOrDefault(LendingViewStates.APPLICATION_STATUS_PAGE, lendingApplication.getMerchantId(), lendingApplication.getLender(), LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())));

                // update tat start time on resubmit
                LendingApplicationPriority lendingApplicationPriority = lendingApplicationPriorityDao.findByApplicationId(lendingApplication.getId());
                if (!ObjectUtils.isEmpty(lendingApplicationPriority)) {
                    lendingApplicationPriority.setTatStartTime(new Date());
                    lendingApplicationPriorityDao.save(lendingApplicationPriority);
                }

                if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
                    funnelService.submitEventV3(merchantId, null, applicationId, FunnelEnums.StageId.RESUBMIT,
                            FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
                }
                else{
                    funnelService.submitEvent(merchantId, null, applicationId, FunnelEnums.StageId.RESUBMIT,
                            FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString());
                }
                Integer finalMaxCount = maxCount;
                HashMap<String, String> cleverTapEvtData = new HashMap<String, String>() {{
                    put("resubmitReason", lendingResubmitTask.getResubmitReason());
                    put("resubmitCount", finalMaxCount.toString());
                    put("loanAmount", lendingApplication.getLoanAmount().toString());
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

    private List<String> fetchUpdatedResubmitReasonsList(List<String> resubmitReasonList) {
        List<String> updatedResubmitReasonsList = new ArrayList<>();

        for (String resubmitReason : resubmitReasonList){
            if(!updatedResubmitReasonsList.contains(resubmitReason)){
                updatedResubmitReasonsList.addAll(LendingConstants.ResubmitReasonMap.get(resubmitReason));
            }
        }
        return updatedResubmitReasonsList;
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
            LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchant.getId());

            LendingMerchantDetails lendingMerchantDetails = new LendingMerchantDetails();
            lendingMerchantDetails.setMerchantId(merchant.getId());
            lendingMerchantDetails.setBusinessName(businessDetailsDTO.getBusinessName());
            if("v1".equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
                lendingMerchantDetails.setBusinessSubCategory(businessDetailsDTO.getBusinessSubCategory());
                lendingMerchantDetails.setBusinessCategory(businessDetailsDTO.getBusinessCategory());
            }
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
        if(Objects.isNull(merchantId)){
            log.info("merchant id empty");
        }
        try{
            String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + merchantId;
            log.info("deleting cached key of loan details in create application for merchant: {}",merchantId);
            lendingCache.delete(loanDetailsCacheKey);
        }
        catch(Exception e){
            log.info("unable to evict loan details cache for : {}", merchantId);
        }
        try{
            String loanDetailsCacheKey = LoanDetailsConstant.LENDING_DASHBOARD_DETAILS_V3_KEY_PREFIX + merchantId;
            log.info("deleting cached key of loan dashboard api for merchant: {}",merchantId);
            lendingCache.delete(loanDetailsCacheKey);
        }
        catch(Exception e){
            log.info("unable to evict dashboard api cache for : {}", merchantId);
        }
    }

    public static String getDateInFormat(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S"); // Set your date format
        String currentData = sdf.format(date);
        return currentData;
    }

    public ApiResponse<?> getApplicationDoc(Long applicationId, BasicDetailsDto merchant, String docType,String clientIp, String deviceId, String platform, String lang){
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId,
          merchant.getId());
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("Application not found while fetching KFS details for Id: {} for merchant : {}", applicationId, merchant.getId());
            return new ApiResponse<>(false, "Unable to fetch application details");
        }

        try{
            Date date = new Date();

            if (docType.equalsIgnoreCase(ApplicationDocType.KEY_FACTS_STATEMENT_DETAILS.toString())) {
                return getKfsDetails(applicationId, lendingApplication, merchant, null, ApplicationDocType.KEY_FACTS_STATEMENT_DETAILS);
            } else if (docType.equalsIgnoreCase(ApplicationDocType.KEY_FACTS_STATEMENT_DOC.toString())) {
                return generateKfs(applicationId, lendingApplication, merchant, false, null, lang);
            } else if (docType.equalsIgnoreCase(ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC.toString())) {
                return generateSanctionCumLoanAgreement(applicationId, lendingApplication, merchant, false, null, lang);
            } else if (docType.equalsIgnoreCase(ApplicationDocType.DISBURSMENT_REQUEST_LETTER_DOC.toString())) {
                return generateDisbursementRequestLetter(applicationId, lendingApplication, merchant, clientIp, deviceId, platform);
            } else if (docType.equalsIgnoreCase(ApplicationDocType.AUTHORIZATION_LETTER_DOC.toString())) {
                return generateAuthorizationLetter(applicationId, lendingApplication, merchant, false, null, lang);
            } else if (docType.equalsIgnoreCase(ApplicationDocType.PAYU_MITC_DOC.toString())) {
                return generateMITC(applicationId, lendingApplication, merchant, false, date);
            } else if (docType.equalsIgnoreCase(ApplicationDocType.PAYU_GTC_DOC.toString())) {
                return generateGTC(applicationId, lendingApplication, merchant, false, date);
            } else if (docType.equalsIgnoreCase(ApplicationDocType.LOA_DOC.toString())) {
                return generateLOA(applicationId, lendingApplication, merchant, false, date);
            } else if (docType.equalsIgnoreCase(ApplicationDocType.APPLICATION_FORM_DOC.toString())) {
                return generateApplicationForm(applicationId, lendingApplication, merchant, false, date);
            }

            return new ApiResponse<>(false, "Unhandled DocType");
        }
        catch(Exception e){
            log.error("Exception for applicationId : {}, merchant : {}{}{}",applicationId, merchant.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Something went wrong");
        }
    }

    public ApiResponse<?> getKfsDetails(Long applicationId, LendingApplication lendingApplication1, BasicDetailsDto merchant, String lang, ApplicationDocType docType){
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

            String language = "";
            Boolean enableKFSVernacLang = easyLoanUtil.percentScaleUp(lendingApplication.getMerchantId(), lenderVernacLangRolloutPercent);

            if(ObjectUtils.isEmpty(lendingKfs)){
                log.info("KFS details not present for Id: {} for merchant : {}", applicationId, merchant.getId());
                lendingKfs = saveKfsDetails(merchant.getId(), lendingApplication);
            }
            if(enableKFSVernacLang){
                if(ObjectUtils.isEmpty(lang)){
                    //this case will only get called when details api is first called
                    language = languageService.getDocLanguage(lendingApplication.getMerchantId(), lendingApplication.getLender(), false);
                } else {
                    language =  languageService.getOrSetLanguageMappingByLenderAndLang(lendingApplication.getLender(), lendingApplication.getId(), lang);
                }
                String docLang = lendingKfs.getDocLanguage();
                boolean isDocTypeSupported = Arrays.asList("KEY_FACTS_STATEMENT_DOC", "SANCTION_CUM_LOAN_AGREEMENT_DOC")
                        .contains(docType.toString());
                if (!ObjectUtils.isEmpty(language) && (ObjectUtils.isEmpty(docLang) || (isDocTypeSupported && !language.equalsIgnoreCase(docLang)))) {
                    lendingKfs.setDocLanguage(language);
                    lendingKfsDao.save(lendingKfs);
                }
            }
            if(ObjectUtils.isEmpty(lendingKfs)){
                return new ApiResponse<>(false, "Unable to create KFS details");
            }
            String lenderKfsUrl = null;
            boolean generateLenderDoc = "TOPUP".equalsIgnoreCase(lendingApplication.getLoanType()) ?
                    lenderDocGenerateTopUpEnabledLenders.contains(lendingApplication.getLender()) : lenderDocGenerateEnabledLenders.contains(lendingApplication.getLender());
            if (generateLenderDoc) {
                ApiResponse lenderDocResponse = generateLenderKfs(lendingApplication, true);
                if (!lenderDocResponse.isSuccess()) {
                    return new ApiResponse<>(false, "Unable to create KFS details");
                }
                lenderKfsUrl = (String) lenderDocResponse.getData();
            }
            Double apr = null;
            Double annualRoi = null;
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao
                    .findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name());
            if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails) && !ObjectUtils.isEmpty(lendingApplicationLenderDetails.getAnnualRoi())) {
                annualRoi = lendingApplicationLenderDetails.getAnnualRoi();
            }
            LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingKfs.getMerchantId());
            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());

            String lenderCorporateName = "";
            String lenderBusinessAddress = "";
            String lenderContactName = "";
            String lenderContactEmail = "";
            String lenderContactNumber = "";
            String colenderCorporateName = "";
            String colenderBusinessAddress = "";
            String lenderGrievanceTime = "";
            String parentLenderCorporateName = "NA";

            if(lendingApplication.getLender().equalsIgnoreCase(Lender.LIQUILOANS_P2P.toString()) || lendingApplication.getLender().equalsIgnoreCase(Lender.LIQUILOANS_P2P_OF.toString())){
                lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_LIQUILOANS;
                lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_LIQUILOANS;
                lenderContactName = KfsConstants.LENDER_CONTACT_NAME_LIQUILOANS;
                lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_LIQUILOANS;
                lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_LIQUILOANS;
                lenderGrievanceTime = LENDER_GRIEVANCE_TIME_LIQUILOANS;
            }
            else if(lendingApplication.getLender().equalsIgnoreCase(Lender.LIQUILOANS_NBFC.toString()) ||
                    lendingApplication.getLender().equalsIgnoreCase(Lender.TRILLIONLOANS.toString())){
                lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_LL_NBFC;
                lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_LL_NBFC;
                lenderContactName = KfsConstants.LENDER_CONTACT_NAME_LL_NBFC;
                lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_LL_NBFC;
                lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_LL_NBFC;
                lenderGrievanceTime = LENDER_GRIEVANCE_TIME_LL_NBFC;
            }
            else if(lendingApplication.getLender().equalsIgnoreCase(Lender.LDC.toString())){
                lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_LDC;
                lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_LDC;
                lenderContactName = KfsConstants.LENDER_CONTACT_NAME_LDC;
                lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_LDC;
                lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_LDC;
                lenderGrievanceTime = LENDER_GRIEVANCE_TIME_LDC;
            }
            else if(lendingApplication.getLender().equalsIgnoreCase(Lender.ABFL.toString())){
                lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_ABFL;
                lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_ABFL;
                lenderContactName = KfsConstants.LENDER_CONTACT_NAME_ABFL;
                lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_ABFL;
                lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_ABFL;
                lenderGrievanceTime = KfsConstants.LENDER_GRIEVANCE_TIME_ABFL;
            }
            else if(lendingApplication.getLender().equalsIgnoreCase(Lender.PIRAMAL.toString())){
                lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_PIRAMAL;
                lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_PIRAMAL;
                lenderContactName = KfsConstants.LENDER_CONTACT_NAME_PIRAMAL;
                lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_PIRAMAL;
                lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_PIRAMAL;
            } else if(lendingApplication.getLender().equalsIgnoreCase(Lender.CAPRI.name())) {
                lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_CAPRI;
                lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_CAPRI;
                lenderContactName = KfsConstants.LENDER_CONTACT_NAME_CAPRI;
                lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_CAPRI;
                lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_CAPRI;
            }
            else if(lendingApplication.getLender().equalsIgnoreCase(Lender.MUTHOOT.toString())){
                lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_MUTHOOT;
                lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_MUTHOOT;
                lenderContactName = KfsConstants.LENDER_CONTACT_NAME_MUTHOOT;
                lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_MUTHOOT;
                lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_MUTHOOT;
            }
            else if(lendingApplication.getLender().equalsIgnoreCase(Lender.PAYU.toString())){
                lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_PAYU;
                lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_PAYU;
                lenderContactName = KfsConstants.LENDER_CONTACT_NAME_PAYU;
                lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_PAYU;
                lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_PAYU;
            }
            else if(lendingApplication.getLender().equalsIgnoreCase(Lender.CREDITSAISON.toString())){
                lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_CREDITSAISON;
                lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_CREDITSAISON;
                lenderContactName = KfsConstants.LENDER_CONTACT_NAME_CREDITSAISON;
                lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_CREDITSAISON;
                lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_CREDITSAISON;
            }
            else if (lendingApplication.getLender().equalsIgnoreCase(Lender.SMFG.toString())) {
                lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_SMFG;
                lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_SMFG;
                lenderContactName = KfsConstants.LENDER_CONTACT_NAME_SMFG;
                lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_SMFG;
                lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_SMFG;
            } else if (lendingApplication.getLender().equalsIgnoreCase(Lender.UGRO.toString())) {
                lenderCorporateName = ugroConfig.getCorporateName();
                lenderBusinessAddress = ugroConfig.getBusinessAddress();
                lenderContactName = ugroConfig.getContactName();
                lenderContactEmail = ugroConfig.getContactEmail();
                lenderContactNumber = ugroConfig.getContactNumber();
                lenderGrievanceTime = ugroConfig.getGrievanceTIme();
            } else if (lendingApplication.getLender().equalsIgnoreCase(Lender.OXYZO.toString())) {
                lenderCorporateName = oxyzoConfig.getCorporateName();
                lenderBusinessAddress = oxyzoConfig.getBusinessAddress();
                lenderContactName = oxyzoConfig.getContactName();
                lenderContactEmail = oxyzoConfig.getContactEmail();
                lenderContactNumber = oxyzoConfig.getContactNumber();
                lenderGrievanceTime = oxyzoConfig.getGrievanceTIme();
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

            String shopAddress = constructShopAddress(lendingApplication);
            Double insurancePremium = getInsurancePremium(lendingApplication);
//            if (insurancePremium.equals(0D)) {
//                insurancePremium = null;
//            }

            Double processingFeePercentageWithoutGst = Double.valueOf(String.format("%.4f", (lendingApplication.getProcessingFee() * 100D / (100D + GST_PERCENTAGE)) / (lendingApplication.getLoanAmount()) * 100));

            Double processingFeeWithoutGst = Double.valueOf(String.format("%.2f", (lendingApplication.getLoanAmount() * processingFeePercentageWithoutGst) / 100D ));

            Double disbursalAmount = lendingApplication.getDisbursalAmount();
            Double repaymentAmount = lendingApplication.getRepayment();
            if(Lender.PAYU.name().equalsIgnoreCase(lendingApplication.getLender())){
                processingFeePercentageWithoutGst = Double.valueOf(String.format("%.2f", processingFeePercentageWithoutGst));
                RepaymentScheduleResponseDTO repaymentScheduleResponseDTO = getLenderRepaymentSchedule(applicationId, lendingApplication.getLender(), false);
                if (!LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())) {
                    disbursalAmount = Double.valueOf(String.format("%.2f", repaymentScheduleResponseDTO.getNetDisbursalAmount()));
                }
                repaymentAmount = Double.valueOf(String.format("%.2f",repaymentScheduleResponseDTO.getTotalRepaymentExpected()));
            }
            Double monthlyIncome = lendingRiskVariables.getMonthlyIncome();
            if(Lender.UGRO.name().equalsIgnoreCase(lendingApplication.getLender())) {
                monthlyIncome = lendingRiskVariablesSnapshot.getMonthlyTpv();
            }
            Double processingFeePercentage;
            if(lendingApplication.getLender().equalsIgnoreCase(Lender.PIRAMAL.toString())){
                processingFeePercentage =  (Double.valueOf(String.format("%.2f", (lendingApplication.getProcessingFee()/(lendingApplication.getDisbursalAmount() + lendingApplication.getProcessingFee() + insurancePremium) * 100))));
            }else{
                processingFeePercentage =  (Double.valueOf(String.format("%.2f", (lendingApplication.getProcessingFee()/(lendingApplication.getDisbursalAmount() + lendingApplication.getProcessingFee()) * 100))));

            }

            Date lendingApplicationCreatedAt = lendingApplication.getCreatedAt();

            if(Lender.ABFL.name().equals(lendingApplication.getLender())){
                lendingApplicationCreatedAt = lendingApplication.getAgreementAt();
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
                    .lenderGrievanceTime(lenderGrievanceTime)
                    .loanAmount(lendingApplication.getLoanAmount())
                    .processingFee(lendingApplication.getProcessingFee())
                    .processingFeePercentage(processingFeePercentage)
                    .processingFeeWithoutGst(processingFeeWithoutGst)
                    .processingFeePercentageWithoutGst(processingFeePercentageWithoutGst)
                    .tenureInMonths(lendingApplication.getTenureInMonths())
                    .disbursalAmount(disbursalAmount)
                    .repaymentAmount(repaymentAmount)
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
                    .ediDays((lendingApplication.getPayableDays() % 30) == 0 ? 7 : 6)
                    .agreementAt(lendingApplication.getAgreementAt())
                    .shopAddress(shopAddress)
                    .shopName(lendingApplication.getBusinessName())
                    .shopState(lendingApplication.getState())
                    .shopPincode(String.valueOf(lendingApplication.getPincode()))
                    .shopCity(lendingApplication.getCity())
                    .annualTurnover(Optional.ofNullable(lendingRiskVariablesSnapshot.getSummaryTpv()).map(tpv -> tpv * 360).orElse(null))
                    .monthlyIncome(monthlyIncome)
                    .annualRoi(annualRoi)
                    .foreclosureChargesRequired(loanUtil.checkIfForeClosureChargesApplicableKfs(lendingApplicationCreatedAt , lendingApplication.getLender()))
                    .loanPurpose(commonUtil.fetchLoanPurposeByApplicatioId(applicationId))
                    .insurancePremium(insurancePremium)
                    .lenderKfsUrl(lenderKfsUrl)
                    .smbId(lendingApplicationLenderDetails.getSmbId())
                    .offerId(lendingApplicationLenderDetails.getOfferId())
                    .leadId(lendingApplicationLenderDetails.getLeadId())
                    .languageData(enableKFSVernacLang ? languageService.getOrSetLanguageMapping(lendingApplication.getLender(), lendingApplication.getId()) : null)
                    .showVernacKFSLanguage(enableKFSVernacLang)
                    .selectedLanguage(language)
                    .build();

            if(Arrays.asList(Lender.ABFL.name(), Lender.TRILLIONLOANS.name(), Lender.PIRAMAL.name(), Lender.PAYU.name()).contains(lendingApplication.getLender()) && LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())){
                LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingApplication.getMerchantId(), "ACTIVE");
                if(ObjectUtils.isEmpty(lendingPaymentSchedule)){
                    log.error("Unable to fetch parent loan details for merchant: {}", lendingApplication.getMerchantId());
                    throw new Exception("Unable to fetch parent loan details");
                }
                Optional<LendingApplication> parentLendingApplicationOptional = lendingApplicationDao.findById(lendingPaymentSchedule.getApplicationId());
                if(!parentLendingApplicationOptional.isPresent()){
                    log.error("Unable to fetch parent application for application: {}", lendingPaymentSchedule.getApplicationId());
                    throw new Exception("Unable to fetch parent application");
                }
                kfsDto.setLenderForeclosureAmount(fetchLenderForeclosureAmount(lendingPaymentSchedule));
                kfsDto.setParentLoanBplId(parentLendingApplicationOptional.get().getExternalLoanId());
                kfsDto.setParentLender(parentLendingApplicationOptional.get().getLender());
                kfsDto.setParentLoanAmount(parentLendingApplicationOptional.get().getLoanAmount());
                if(LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(parentLendingApplicationOptional.get().getLender())) {
                    parentLenderCorporateName = LENDER_CORPORATE_NAME_LIQUILOANS;
                }
                kfsDto.setParentLenderCorporateName(parentLenderCorporateName);
            }
            return new ApiResponse<>(kfsDto);
        }
        catch(Exception e){
            log.error("Unable to fetch KFS details for applicationId : {}, Exception : {}, stacktrace : {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Something went wrong");
        }
    }

    public LendingKfs saveKfsDetails(Long merchantId, LendingApplication lendingApplication){
        LendingKfs lendingKfs = new LendingKfs();
        lendingKfs.setApplicationId(lendingApplication.getId());
        lendingKfs.setMerchantId(merchantId);
        lendingKfs.setLender(lendingApplication.getLender());
        Double insurancePremium = getInsurancePremium(lendingApplication);
        Double processingFee = lendingApplication.getProcessingFee();

        Double amountToCalculateAprOn = lendingApplication.getLoanAmount() - processingFee - insurancePremium;
        Double apr = getApr(merchantId, lendingApplication.getId(), amountToCalculateAprOn, LoanUtil.getEdiModal(lendingApplication).getNoOfEdiDaysInAWeek(), lendingApplication.getLender());
        if(ObjectUtils.isEmpty(apr)) return null;
        lendingKfs.setApr(Double.valueOf(String.format("%.2f", apr)));
        lendingKfsDao.save(lendingKfs);
        return lendingKfs;
    }

    public Double getInsurancePremium(LendingApplication lendingApplication) {
        LendingLoanInsurance lendingLoanInsurance = insuranceService.getInsuranceDetails(lendingApplication.getId(), lendingApplication.getLender(), SELECTED);
        return Objects.nonNull(lendingLoanInsurance) ? lendingLoanInsurance.getInsurancePremium() : 0D;
    }

    public void storeApplicationDocs(Long applicationId, LendingApplication lendingApplication, BasicDetailsDto merchant) throws Exception {
        LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
        if(ObjectUtils.isEmpty(lendingKfs)){
            log.error("Unable to retrieve KFS details from db for applicationId : {}", applicationId);
            throw new Exception("Unable to retrieve KFS details from db for applicationId : " + applicationId);
        }
        //KFS
        generateKfsDocument(lendingApplication, merchant, lendingKfs, null);
        //Loan Agreement
        generateSanctionCumLoanAgreementDoc(lendingApplication, merchant, lendingKfs, null);

        //For Appending SignedDetails in lender generated agreementDocs
        if(Collections.singletonList(Lender.ABFL.name()).contains(lendingApplication.getLender())) {
            generateAndAppendSignedDetails(lendingApplication, lendingKfs, merchant);
        }

        lendingKfs.setSanctionLoanAgreementSignedAt(dateTimeUtil.getCurrentDate());
        lendingKfs.setKfsSignedAt(dateTimeUtil.getCurrentDate());


        //Authorization Letter
        if (Arrays.asList(Lender.ABFL.name(),Lender.TRILLIONLOANS.name(),Lender.LIQUILOANS_NBFC.name(),Lender.LIQUILOANS_P2P.name(),Lender.LIQUILOANS_P2P_OF.name(),Lender.SMFG.name(), Lender.UGRO.name(), Lender.OXYZO.name(),Lender.PIRAMAL.name(), Lender.CREDITSAISON.name(), Lender.MUTHOOT.name(), Lender.CAPRI.name()).contains(lendingApplication.getLender())){
            generateAuthorizationLetterDoc(lendingApplication, merchant, lendingKfs, null);
            lendingKfs.setAuthorizationLetterSignedAt(dateTimeUtil.getCurrentDate());
        }

        Date date = new Date();
        //AUTHORIZATION LETTER PAYU
        // AUDIT TRAIL LETTER SMFG
        if (Arrays.asList(Lender.PAYU.name(), Lender.SMFG.name(), Lender.UGRO.name()).contains(lendingApplication.getLender())) {
            generateLOADoc(lendingApplication, merchant, lendingKfs, date);
        }

        //PAYU LOAN DOCS
        if (Lender.PAYU.name().equalsIgnoreCase(lendingApplication.getLender())){
            generateMITCDoc(lendingApplication, merchant, lendingKfs, date);
            generateGTCDoc(lendingApplication, merchant, lendingKfs, date);
        }

        // Application form doc
        if(Arrays.asList(Lender.PAYU.name(), Lender.UGRO.name()).contains(lendingApplication.getLender())) {
            generateApplicationFormDoc(lendingApplication, merchant, lendingKfs, date);
        }

        lendingKfsDao.save(lendingKfs);
        LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchant.getId(), lendingApplication);
        if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
            funnelService.submitEventV3(merchant.getId(), null, applicationId,lendingApplication.getLoanType(),
                    FunnelEnums.StageId.AGREEMENT, FunnelEnums.StageEvent.SUBMITTED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
        }
        else{
            funnelService.submitEvent(merchant.getId(), null, applicationId, lendingApplication.getLoanType(),
                    FunnelEnums.StageId.AGREEMENT, FunnelEnums.StageEvent.SUBMITTED, LocalDateTime.now().toString());
        }
    }

    public void generateSanctionCumLoanAgreementDoc(LendingApplication lendingApplication, BasicDetailsDto merchant,
                                                    LendingKfs lendingKfs, Date dateTime) throws Exception {
        boolean generateLenderDoc = "TOPUP".equalsIgnoreCase(lendingApplication.getLoanType()) ?
                lenderDocGenerateTopUpEnabledLenders.contains(lendingApplication.getLender()) : lenderDocGenerateEnabledLenders.contains(lendingApplication.getLender());
        if (generateLenderDoc) {
            generateLenderSanctionCumLoanAgreement(lendingApplication, true);
            return;
        }
        String fileName = "";
        ApiResponse<?> apiResponse = generateSanctionCumLoanAgreement(lendingApplication.getId(), lendingApplication, merchant, true, dateTime, lendingKfs.getDocLanguage());
        if (apiResponse.success) {
            String sanctionCumLoanAgreementHtml = (String) apiResponse.data;

            // Call the helper method
            fileName = (String) generateAndUploadSanctionLoanAgreementPdf(lendingApplication, sanctionCumLoanAgreementHtml, lendingKfs.getLender(), false);

            // Get the URL for the file and generate a short link
            String sanctionCumLoanAgreementUrl = s3BucketHandler.getPreSignedPublicURL(fileName, s3Bucket);
            String shortUrl = apiGatewayService.getShortUrl(sanctionCumLoanAgreementUrl);
            if (shortUrl == null || shortUrl.isEmpty() || shortUrl.trim().isEmpty()) {
                throw new Exception("Unable to create short URL for Sanction Loan Agreement doc link for : " + lendingApplication.getId());
            } else {
                lendingKfs.setSanctionLoanAgreementDocFile(fileName);
                lendingKfs.setSanctionLoanAgreementDocUrl(shortUrl);
            }
        } else {
            log.error("Unable to store Sanction Cum Loan Agreement pdf doc for applicationId : {}", lendingApplication.getId());
            throw new Exception("Unable to generate Sanction Cum Loan Agreement pdf doc for applicationID" + lendingApplication.getId());
        }
    }

    public Object generateAndUploadSanctionLoanAgreementPdf(LendingApplication lendingApplication,
                                                                 String sanctionCumLoanAgreementHtml, String lender, boolean isForPdf) throws Exception {
        String fileName = "";

        /**
         * New Library is being used to generate PDF for only english lenders
         * identified only english lenders are
         * CREDITSAISON
         * OXYZO
         * PAYU  (not switched for now)
         * SMFG
         * UGRO
         */
        if (newPdfGenerationMethodLenders.contains(lendingApplication.getLender())) {
            fileName = SANCTION_LOAN_AGREEMENT_S3_KEY_PREFIX + lendingApplication.getId() + ".pdf";
            String headerImageUrl = null;
            String footerImageUrl = null;
            boolean footerOnAllPages = false;

            if (!getLenderLogo(lendingApplication.getLender(), ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC).isEmpty()) {
                headerImageUrl = getLenderLogo(lendingApplication.getLender(), ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC);
                if (Arrays.asList(Lender.ABFL.name(), Lender.PIRAMAL.name(), Lender.MUTHOOT.name(), Lender.CAPRI.name()).contains(lender)) {
                    log.info("Add header and footer in sanction letter doc for applicationId:" + lendingApplication.getId());
                    footerImageUrl = getLenderLogo(lendingApplication.getLender(),
                            ApplicationDocType.getFooterMapping(Lender.valueOf(lendingApplication.getLender())));
                    footerOnAllPages = true;
                } else {
                    log.info("Add header in sanction letter doc for applicationId:" + lendingApplication.getId());
                }
            }

            log.info("Starting PDF generation for Sanction Loan Agreement using PdfGeneratorUtilV2 - applicationId: {}, lender: {}, headerImageUrl: {}, footerImageUrl: {}", 
                    lendingApplication.getId(), lendingApplication.getLender(), 
                    headerImageUrl != null ? "present" : "null", footerImageUrl != null ? "present" : "null");

            PdfGenerationRequest.PdfGenerationRequestBuilder requestBuilder = PdfGenerationRequest.builder()
                    .html(sanctionCumLoanAgreementHtml);

            if (headerImageUrl != null) {
                requestBuilder.headerImageUrl(headerImageUrl);
                log.debug("Added header image to PDF generation request - applicationId: {}", lendingApplication.getId());
            }
            if (footerImageUrl != null) {
                requestBuilder.footerImageUrl(footerImageUrl);
                requestBuilder.footerOnAllPages(footerOnAllPages);
                log.debug("Added footer image to PDF generation request - applicationId: {}, footerOnAllPages: {}", 
                        lendingApplication.getId(), footerOnAllPages);
            }

            PdfGenerationRequest request = requestBuilder.build();
            log.info("Calling PdfGeneratorUtilV2.generatePdf for Sanction Loan Agreement - applicationId: {}", lendingApplication.getId());
            PdfGenerationResponse response = pdfGeneratorUtil.generatePdf(request);

            if (response.getSuccess()) {
                byte[] pdfByteArray = response.getPdfAsBytes();
                log.info("PDF generation successful for Sanction Loan Agreement - applicationId: {}, pdfSize: {} bytes", 
                        lendingApplication.getId(), pdfByteArray.length);
                ByteArrayInputStream inStream = new ByteArrayInputStream(pdfByteArray);
                s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, s3Bucket);
                log.info("Successfully uploaded Sanction Loan Agreement to S3 - applicationId: {}, fileName: {}", 
                        lendingApplication.getId(), fileName);
            } else {
                log.error("PDF generation failed for Sanction Loan Agreement - applicationId: {}, response: {}", 
                        lendingApplication.getId(), response);
                throw new Exception("Unable to generate Sanction Cum Loan Agreement pdf doc for applicationID" + lendingApplication.getId());
            }
        }
        /** new Library code ends here **/

        else if (Lender.PAYU.name().equalsIgnoreCase(lendingApplication.getLender())) {
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
            fileName = lendingApplicationLenderDetails.getLeadId() + '_' + SANCTION_LETTER_S3_KEY_PREFIX + new SimpleDateFormat("dd-MM-yyyy").format(dateTimeUtil.getCurrentDate()) + ".pdf";
            ByteArrayInputStream inStream = getLoanDocPdf(sanctionCumLoanAgreementHtml, ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC, lendingApplication, sanctionCompressionLevel);
            if (ObjectUtils.isEmpty(inStream)) {
                throw new Exception("Unable to generate Sanction Cum Loan Agreement for applicationID" + lendingApplication.getId());
            }
            s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, s3Bucket);
        }else {
            fileName = SANCTION_LOAN_AGREEMENT_S3_KEY_PREFIX + lendingApplication.getId() + ".pdf";
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(outStream, new WriterProperties().setCompressionLevel(sanctionCompressionLevel));
            PdfDocument pdfDocument = new PdfDocument(writer);
            if (!getLenderLogo(lendingApplication.getLender(), ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC).isEmpty()) {
                if (Arrays.asList(Lender.ABFL.name(), Lender.PIRAMAL.name(), Lender.MUTHOOT.name(), Lender.CAPRI.name()).contains(lender)) {
                    ImageData headerImageData = ImageDataFactory.create(getLenderLogo(lendingApplication.getLender(), ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC));
                    ImageData footerImageData = ImageDataFactory.create(getLenderLogo(lendingApplication.getLender(),
                            ApplicationDocType.getFooterMapping(Lender.valueOf(lendingApplication.getLender()))));
                    Header headerHandler = new Header(headerImageData);
                    pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, headerHandler);

                    Footer footerHandler = new Footer(footerImageData);
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
            s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, s3Bucket);
        }
        if (isForPdf) {
            return s3BucketHandler.getObject(fileName, s3Bucket);
        } else {
            return fileName;
        }
    }

    private LendingKfs getLendingKfs(LendingApplication lendingApplication, Boolean preSigned) {
        LendingKfs lendingKfs = null;
        try {
            lendingKfs = lendingKfsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
            if (ObjectUtils.isEmpty(lendingKfs) || ObjectUtils.isEmpty(lendingKfs.getSanctionLoanAgreementDocFile())) {
                DocType docType = DocType.LOAN_AGREEMENT;
                Boolean success = associationServiceUtil.invokeDocsGenerateService(lendingApplication.getLender(), lendingApplication, docType, preSigned);
                if (success) {
                    lendingKfs = lendingKfsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
                }
            }
        } catch (Exception e) {
            log.info("Exception in generating lender SanctionCumLoanAgreement document of {} for applicationId {} {}", lendingApplication.getLender(), lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
        }
        return lendingKfs;
    }

    public String generateLenderSanctionCumLoanAgreementWithFileName(LendingApplication lendingApplication, Boolean preSigned) {
        try {
            LendingKfs lendingKfs = getLendingKfs(lendingApplication, preSigned);
            if (!ObjectUtils.isEmpty(lendingKfs)) {
                String fileName = preSigned ? lendingKfs.getSanctionLoanAgreementDocFile() : lendingKfs.getSignedSanctionDocFile();
                if (!ObjectUtils.isEmpty(fileName)) {
                    String lenderSanctionUrl = s3BucketHandler.getPreSignedPublicURL(fileName, s3Bucket);
                    return fileName;
                }
            }
            log.info("Unable to generate lender SanctionCumLoanAgreement document of {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
        } catch (Exception e) {
            log.info("Exception in generating lender SanctionCumLoanAgreement document of {} for applicationId {} {}", lendingApplication.getLender(), lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }


    public InputStream generateSanctionCumLoanAgreementDocAsPdf(LendingApplication lendingApplication, BasicDetailsDto merchant,
                                                                String lender, Date dateTime) throws Exception {
        boolean generateLenderDoc = "TOPUP".equalsIgnoreCase(lendingApplication.getLoanType()) ?
                lenderDocGenerateTopUpEnabledLenders.contains(lendingApplication.getLender()) : lenderDocGenerateEnabledLenders.contains(lendingApplication.getLender());

        if (generateLenderDoc) {
            String fileName = generateLenderSanctionCumLoanAgreementWithFileName(lendingApplication, true);
            InputStream inputStream = s3BucketHandler.getObject(fileName, s3Bucket);
            return inputStream;
        }
        ApiResponse<?> apiResponse = generateSanctionCumLoanAgreement(lendingApplication.getId(), lendingApplication, merchant, true, dateTime, null);
        if (apiResponse.success) {
            String sanctionCumLoanAgreementHtml = (String) apiResponse.data;
            return (InputStream) generateAndUploadSanctionLoanAgreementPdf(lendingApplication, sanctionCumLoanAgreementHtml, lender, true);
        } else {
            log.error("Unable to store Sanction Cum Loan Agreement pdf doc for applicationId : {}", lendingApplication.getId());
            throw new Exception("Unable to generate Sanction Cum Loan Agreement pdf doc for applicationID" + lendingApplication.getId());
        }
    }

    public Double getApr(Long merchantId, Long applicationId, Double amountToCalculateAprOn, Integer ediModel, String lender){
        try{
            log.info("calculating APR for applicationId : {}", applicationId);
            Double guess = 0.01;
            ArrayList<Double> values = new ArrayList<>();
            CommonResponse response = lendingEdiScheduleService.getEdiScheduleV2(merchantId, applicationId, null);
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
            int daysInYear = (ediModel == 7 && Arrays.asList(Lender.ABFL.name(), Lender.TRILLIONLOANS.name(), Lender.CAPRI.name(), Lender.PAYU.name(),Lender.CREDITSAISON.name(), Lender.UGRO.name(), Lender.OXYZO.name(), Lender.PIRAMAL.name()).contains(lender)) ? 360 : 365;
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

    /*
        * This method is to be used specifically for calculating APR for base checks only.
        * This caters the case of using lender pricing config for APR calculation
     */
    public Double getAprForBaseChecks(LoanApplicationDetailsDto loanApplicationDetailsDto, Double amountToCalculateAprOn, Integer ediModel, String lender, double interestRate){
        try{
            Long applicationId = loanApplicationDetailsDto.getId();

            log.info("calculating APR using Lender Pricing for applicationId : {}", applicationId);
            Double guess = 0.01;
            ArrayList<Double> values = new ArrayList<>();
            log.info("amountToCalculateAprOn: {}", amountToCalculateAprOn);

            //Get Lender pricing config for APR calculation
            Double edi = loanApplicationDetailsDto.getEdi();
            log.info("Edi of loan id : {} is {}", loanApplicationDetailsDto.getId(), edi);
            Long payableDays = (long) OfferUtils.getEdiDays(loanApplicationDetailsDto.getTenureInMonths(), LenderOffDays.valueOf(lender).getEdiModel());
            Double interestAmt = (loanApplicationDetailsDto.getLoanAmount() * (interestRate * loanApplicationDetailsDto.getTenureInMonths()) / 100) ;
            double ediAmount = ((loanApplicationDetailsDto.getLoanAmount() + interestAmt) / payableDays);
            edi = ediUtil.getEdiAfterRoundingLogic(loanApplicationDetailsDto.getId(), ediAmount, lender);
            log.info("payable days : {}, loan amt : {}, interest rate : {}, edi : {}, interest amt : {}", payableDays, loanApplicationDetailsDto.getLoanAmount(), interestRate, edi, interestAmt);


            CommonResponse response = lendingEdiScheduleService.getEdiScheduleForEdi(applicationId, edi, loanApplicationDetailsDto);
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
            int daysInYear = (ediModel == 7 && Arrays.asList(Lender.ABFL.name(), Lender.TRILLIONLOANS.name(), Lender.CAPRI.name(), Lender.PAYU.name(),Lender.CREDITSAISON.name(), Lender.UGRO.name(), Lender.PIRAMAL.name()).contains(lender)) ? 360 : 365;
            log.info("days in year : {} for application id : {}", daysInYear, loanApplicationDetailsDto.getId());
            apr = LoanCalculationUtil.irr(valuesDouble, guess) * daysInYear;
            if(apr.isNaN()){
                log.info("APR : {}", apr);
                return null;
            }
            log.info("APR : {}", apr);
            return apr * 100;
        }
        catch(Exception e){
            log.error("Unable to calculate APR for applicationId : {} Exception : {}, stacktrace : {}", loanApplicationDetailsDto.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    public Double getApr(Integer ediCount, Double edi, Double amountToCalculateAprOn, Long merchantId, String lender) {
        try {
            Double guess = 0.01;
            ArrayList<Double> values = new ArrayList<>();
            values.add(0 - amountToCalculateAprOn);
            for (int i = 0; i < ediCount; i++) {
                values.add(new Double(edi));
            }
            Double apr = 0.0;
            double[] valuesDouble = new double[values.size()];
            for (int i = 0; i < values.size(); i++) valuesDouble[i] = values.get(i);
            log.info("valuesDouble Size : {}", valuesDouble.length);
            int daysInYear = 360;
            apr = LoanCalculationUtil.irr(valuesDouble, guess) * daysInYear;
            if (apr.isNaN()) {
                log.info("APR : {}", apr);
                return null;
            }
            log.info("APR : {}", apr);
            return apr * 100;
        } catch (Exception e) {
            log.error("Unable to calculate APR for merchant:{}", merchantId);
        }
        return null;
    }

//    Double getApr(String segment, String riskGroup, Integer tenureInMonths, String lender){
//        LendingLenderPricing lendingLenderPricing = lendingLenderPricingDao.findBySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColor(segment, riskGroup, tenureInMonths, lender);
//        return ObjectUtils.isEmpty(lendingLenderPricing)?null:lendingLenderPricing.getApr();
//    }
//
//    Double getIrr(String segment, String riskGroup, Integer tenureInMonths, String lender){
//        LendingLenderPricing lendingLenderPricing = lendingLenderPricingDao.findBySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColor(segment, riskGroup, tenureInMonths, lender);
//        return ObjectUtils.isEmpty(lendingLenderPricing)?null:lendingLenderPricing.getIrr();
//    }

    public void generateKfsDocument(LendingApplication lendingApplication, BasicDetailsDto merchant, LendingKfs lendingKfs, Date dateTime) throws Exception {
        boolean generateLenderDoc = "TOPUP".equalsIgnoreCase(lendingApplication.getLoanType()) ?
                lenderDocGenerateTopUpEnabledLenders.contains(lendingApplication.getLender()) : lenderDocGenerateEnabledLenders.contains(lendingApplication.getLender());
        if (generateLenderDoc) {
            generateLenderKfs(lendingApplication, true);
            return;
        }
        String fileName = "";
        ApiResponse<?> apiResponse;
        apiResponse = generateKfs(lendingApplication.getId(), lendingApplication, merchant, true, dateTime, lendingKfs.getDocLanguage());
        if (apiResponse.success) {
            String kfsHtml = (String) apiResponse.data;

            /**
             * New Library is being used to generate PDF for only english lenders
             * identified only english lenders are
             * CREDITSAISON
             * OXYZO
             * PAYU  (not switched for now)
             * SMFG
             * UGRO
             */
            if (newPdfGenerationMethodLenders.contains(lendingKfs.getLender())) {

                fileName = KFS_S3_KEY_PREFIX + lendingApplication.getId() + ".pdf";
                String headerImageUrl = null;
                String footerImageUrl = null;
                boolean footerOnAllPages = false;

                if (!getLenderLogo(lendingApplication.getLender(), ApplicationDocType.KEY_FACTS_STATEMENT_DOC).isEmpty()) {
                    headerImageUrl = getLenderLogo(lendingApplication.getLender(), ApplicationDocType.KEY_FACTS_STATEMENT_DOC);
                    if (Arrays.asList(Lender.ABFL.name(), Lender.PIRAMAL.name(), Lender.MUTHOOT.name(), Lender.CAPRI.name()).contains(lendingKfs.getLender())) {
                        log.info("Add header and footer in kfs doc for applicationId:" + lendingApplication.getId());
                        footerImageUrl = getLenderLogo(lendingApplication.getLender(),
                                ApplicationDocType.getFooterMapping(Lender.valueOf(lendingApplication.getLender())));
                        footerOnAllPages = true;
                    } else {
                        log.info("Add header in kfs doc with applicationId:" + lendingApplication.getId());
                    }
                }

                PdfGenerationRequest.PdfGenerationRequestBuilder requestBuilder = PdfGenerationRequest.builder()
                        .html(kfsHtml);

                if (headerImageUrl != null) {
                    requestBuilder.headerImageUrl(headerImageUrl);
                }
                if (footerImageUrl != null) {
                    requestBuilder.footerImageUrl(footerImageUrl);
                    requestBuilder.footerOnAllPages(footerOnAllPages);
                }

                PdfGenerationRequest request = requestBuilder.build();
                PdfGenerationResponse response = pdfGeneratorUtil.generatePdf(request);

                if (response.getSuccess()) {
                    byte[] pdfByteArray = response.getPdfAsBytes();
                    try{
                      pdfByteArray = PdfCompressorUtil.compressPdf(pdfByteArray,0.7f);
                    }catch (Exception e){
                      log.error("Error while compressing KFS pdf for applicationId: {}, proceeding with uncompressed pdf. Exception: {}", lendingApplication.getId(), e.getMessage());
                    }
                    ByteArrayInputStream inStream = new ByteArrayInputStream(pdfByteArray);
                    s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, s3Bucket);
                } else {
                    log.error("Failed to generate KFS PDF for applicationId: {}", lendingApplication.getId());
                    throw new Exception("Unable to generate KFS pdf doc for applicationID" + lendingApplication.getId());
                }
            }
            /** new Library code ends here **/

            else if (Lender.PAYU.name().equalsIgnoreCase(lendingApplication.getLender())) {
                LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
                fileName = lendingApplicationLenderDetails.getLeadId() + '_' + KFS_LETTER_S3_KEY_PREFIX + new SimpleDateFormat("dd-MM-yyyy").format(dateTimeUtil.getCurrentDate()) + ".pdf";
                ByteArrayInputStream inStream = getLoanDocPdf(kfsHtml, ApplicationDocType.KEY_FACTS_STATEMENT_DOC, lendingApplication, kfsCompressionLevel);
                if (ObjectUtils.isEmpty(inStream)) {
                    throw new Exception("Unable to generate KFS for applicationID" + lendingApplication.getId());
                }
                s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, s3Bucket);
            }else {
                fileName = KFS_S3_KEY_PREFIX + lendingApplication.getId() + ".pdf";
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                PdfWriter writer = new PdfWriter(outStream, new WriterProperties().setCompressionLevel(kfsCompressionLevel));
                PdfDocument pdfDocument = new PdfDocument(writer);
                if (!getLenderLogo(lendingApplication.getLender(), ApplicationDocType.KEY_FACTS_STATEMENT_DOC).isEmpty()) {
                    if (Arrays.asList(Lender.ABFL.name(), Lender.PIRAMAL.name(), Lender.MUTHOOT.name()
                            , Lender.CAPRI.name()).contains(lendingKfs.getLender())) {
                        ImageData headerImageData = ImageDataFactory.create(getLenderLogo(lendingApplication.getLender(), ApplicationDocType.KEY_FACTS_STATEMENT_DOC));
                        ImageData footerImageData = ImageDataFactory.create(getLenderLogo(lendingApplication.getLender(),
                                ApplicationDocType.getFooterMapping(Lender.valueOf(lendingApplication.getLender()))));
                        Header headerHandler = new Header(headerImageData);
                        pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, headerHandler);
                        Footer footerHandler = new Footer(footerImageData);
//                        HeaderFooter headerFooterHandler = new HeaderFooter(headerImageData,footerImageData);
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
                s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, s3Bucket);
            }

            String kfsUrl = s3BucketHandler.getPreSignedPublicURL(fileName, s3Bucket);
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

    private ByteArrayInputStream getLoanDocPdf(String docHtml, ApplicationDocType applicationDocType, LendingApplication lendingApplication, int compressionLevel) {

        ByteArrayInputStream inStream = null;

        try{
            /**
             * New Library is being used to generate PDF for selected lenders
             * if reverting back to iText, set the condition to false
             */
            if (true) {
                log.info("Using new PDF generation method for applicationId: {} and lender: {}", lendingApplication.getId(), lendingApplication.getLender());
                
                String headerImageUrl = null;
                String footerImageUrl = null;
                boolean footerOnAllPages = false;

              if (!getLenderLogo(lendingApplication.getLender(), applicationDocType).isEmpty()) {
                headerImageUrl = getLenderLogo(lendingApplication.getLender(), applicationDocType);
                log.info("Add header and footer in loan doc for applicationId:" + lendingApplication.getId());
                footerImageUrl = getLenderLogo(lendingApplication.getLender(),ApplicationDocType.getFooterMapping(Lender.valueOf(lendingApplication.getLender())));
                footerOnAllPages = true;
              }

                log.info("Starting PDF generation for KFS document using PdfGeneratorUtilV2 - applicationId: {}, lender: {}, headerImageUrl: {}, footerImageUrl: {}", 
                        lendingApplication.getId(), lendingApplication.getLender(), 
                        headerImageUrl != null ? "present" : "null", footerImageUrl != null ? "present" : "null");

                PdfGenerationRequest.PdfGenerationRequestBuilder requestBuilder = PdfGenerationRequest.builder()
                        .html(docHtml);

                if (headerImageUrl != null) {
                    requestBuilder.headerImageUrl(headerImageUrl);
                    log.debug("Added header image to KFS PDF generation request - applicationId: {}", lendingApplication.getId());
                }
                if (footerImageUrl != null) {
                    requestBuilder.footerImageUrl(footerImageUrl);
                    requestBuilder.footerOnAllPages(footerOnAllPages);
                    log.debug("Added footer image to KFS PDF generation request - applicationId: {}, footerOnAllPages: {}", 
                            lendingApplication.getId(), footerOnAllPages);
                }

                PdfGenerationRequest request = requestBuilder.build();
                log.info("Calling PdfGeneratorUtilV2.generatePdf for KFS document - applicationId: {}", lendingApplication.getId());
                PdfGenerationResponse response = pdfGeneratorUtil.generatePdf(request);

                if (response.getSuccess()) {
                    byte[] pdfByteArray = response.getPdfAsBytes();
                    log.info("PDF generation successful for KFS document - applicationId: {}, pdfSize: {} bytes", 
                            lendingApplication.getId(), pdfByteArray.length);
                    inStream = new ByteArrayInputStream(pdfByteArray);
                } else {
                    log.error("PDF generation failed for KFS document - applicationId: {}, response: {}", 
                            lendingApplication.getId(), response);
                    throw new Exception("Unable to generate PDF doc for applicationID" + lendingApplication.getId());
                }
            } else {
                // Fallback to existing PDF generation method
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                ConverterProperties properties = new ConverterProperties();
                properties.setBaseUri(new File(docHtml).getParent());

                // Convert the HTML content to PDF elements
                List<IElement> elements = HtmlConverter.convertToElements(docHtml, properties);
                PdfWriter writer = new PdfWriter(outStream, new WriterProperties().setCompressionLevel(compressionLevel));
                PdfDocument pdfDocument = new PdfDocument(writer);
                if (!getLenderLogo(lendingApplication.getLender(), applicationDocType).isEmpty()) {

                    ImageData headerImageData = ImageDataFactory.create(getLenderLogo(lendingApplication.getLender(), applicationDocType));
                    ImageData footerImageData = ImageDataFactory.create(getLenderLogo(lendingApplication.getLender(),
                            ApplicationDocType.getFooterMapping(Lender.valueOf(lendingApplication.getLender()))));
                    Header headerHandler = new Header(headerImageData);
                    pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, headerHandler);

                    Footer footerHandler = new Footer(footerImageData);
                    pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, footerHandler);


                }
                // Create a Document object to manage content layout
                Document document = new Document(pdfDocument);
                document.setMargins(20, 50, 90, 50);

                // Add the converted HTML elements to the document
                for (IElement element : elements) {
                    document.add((IBlockElement) element);
                }

                // Close the document to finalize the PDF creation
                document.close();

                inStream = new ByteArrayInputStream(outStream.toByteArray());
            }

        } catch(Exception e){
            log.error("Error in creating loan doc {} for application id {} with exception {}", applicationDocType, lendingApplication.getId(), e.getMessage());
        }

        return inStream;
    }

    public ApiResponse<?> generateKfs(Long applicationId, LendingApplication lendingApplication, BasicDetailsDto merchant, boolean timeStamp, Date dateTime, String lang){
        boolean generateLenderDoc = "TOPUP".equalsIgnoreCase(lendingApplication.getLoanType()) ?
                lenderDocGenerateTopUpEnabledLenders.contains(lendingApplication.getLender()) : lenderDocGenerateEnabledLenders.contains(lendingApplication.getLender());
        if (generateLenderDoc) {
            return generateLenderKfs(lendingApplication, true);
        }
        ApiResponse apiResponse = getKfsDetails(applicationId, lendingApplication, merchant, lang, ApplicationDocType.KEY_FACTS_STATEMENT_DOC);
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
            Date penaltyDate = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss").parse(penalDate);
            Date penaltyDateLiquiloans = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss").parse(penalDateLiquiloans);
            Date penaltyDateTrillion = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss").parse(penalDateTrillion);
            Date penaltyDateTrillionLoans = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss").parse(penalDateTrillionloans);
            String html = "";
            String filePath = "";
            String language = "";


            if(easyLoanUtil.percentScaleUp(lendingApplication.getMerchantId(), lenderVernacLangRolloutPercent)){
                language = ObjectUtils.isEmpty(kfsDto.getSelectedLanguage()) ? languageService.getOrSetLanguageMappingByLenderAndLang(lender, lendingApplication.getId(), lang) : "_" + kfsDto.getSelectedLanguage();
                log.info("language of selection: {}", language);
            } else {
                language = languageService.getVernacLanguage(lendingApplication.getLender(), lendingApplication.getLoanType(), lendingApplication.getMerchantId());
            }

            if (ObjectUtils.isEmpty(language) || language.toUpperCase().contains("ENGLISH")) {
                language = "";
            }



            if(lender.equalsIgnoreCase(Lender.LDC.toString())){
                filePath = "/templates/" + "KFS_P2P" + ".html";
            } else if (lender.equalsIgnoreCase(Lender.LIQUILOANS_P2P.toString()) || lender.equalsIgnoreCase(Lender.LIQUILOANS_P2P_OF.toString())) {
                filePath = getFilePathLiquiloans(lendingApplication, penaltyDate, penaltyDateLiquiloans, ApplicationDocType.KEY_FACTS_STATEMENT_DOC, kfsDto.isForeclosureChargesRequired());
            } else if (lender.equalsIgnoreCase(Lender.PIRAMAL.name())) {
                filePath = (Objects.nonNull(lendingApplication.getCreatedAt()) && kfsDto.isForeclosureChargesRequired())? "/templates/PIRAMAL/" + "KFS_NONP2P_PIRAMAL" + language + ".html" : "/templates/" + "KFS_NONP2P_PIRAMAL" + language + ".html";
            } else if (lender.equalsIgnoreCase(Lender.ABFL.toString())) {
                filePath = "/templates/KFS_NONP2P_ABFL" + language +".html";
            } else if(lender.equalsIgnoreCase(Lender.USFB.name())) {
                filePath = "/templates/" + "KFS_NONP2P_USFB" + ".html";
            } else if(Lender.CAPRI.name().equalsIgnoreCase(lender)) {
                filePath = "/templates/KFS_NONP2P_CAPRI.html";
            } else if (Objects.nonNull(lendingApplication.getAgreementAt()) && lendingApplication.getAgreementAt().before(penaltyDateTrillion) && lender.equalsIgnoreCase(Lender.LIQUILOANS_NBFC.name())) {
                filePath = "/templates/" + "KFS_NONP2P" + ".html";
            } else if (lender.equalsIgnoreCase(Lender.LIQUILOANS_NBFC.name()) || lender.equalsIgnoreCase(Lender.TRILLIONLOANS.name())) {
                filePath = getFilePathTrillionLoans(lendingApplication, penaltyDate, penaltyDateTrillionLoans, ApplicationDocType.KEY_FACTS_STATEMENT_DOC, kfsDto.isForeclosureChargesRequired(), language);
            } else if(lender.equalsIgnoreCase(Lender.MUTHOOT.name())) {
                filePath = "/templates/" + "KFS_NONP2P_MUTHOOT" + language + ".html";
            } else if(lender.equalsIgnoreCase(Lender.PAYU.name())) {
                filePath = "/templates/" + "KFS_NONP2P_PAYU" + ".html";
            }else if(Lender.CREDITSAISON.name().equalsIgnoreCase(lender)) {
                filePath = "/templates/CREDITSAISON/" + "KFS_CREDIT_SAISON" + ".html";
            } else if(lender.equalsIgnoreCase(Lender.SMFG.name())) {
                filePath = "/templates/" + "KFS_NONP2P_SMFG" + ".html";
            } else if(lender.equalsIgnoreCase(Lender.UGRO.name())) {
                filePath = "/templates/" + "UGRO/" + "KFS.html";
            } else if(lender.equalsIgnoreCase(Lender.OXYZO.name())) {
                filePath = "/templates/OXYZO/" + "KFS_OXYZO" + ".html";
            } else {
                filePath = "/templates/" + "KFS_NONP2P" + ".html";
            }
            log.info("file path for kfs: {}", filePath);
            InputStream inputStream = this.getClass().getResourceAsStream(filePath);
            Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
            html = scanner.hasNext() ? scanner.next() : "";;
            for(Map.Entry<String,Object> entry : data.entrySet()) {
                String key = "{{" + entry.getKey() + "}}";
                String val = Objects.nonNull(entry.getValue()) ? entry.getValue().toString() : "";
                //log.info(key + " " + val);
                html = html.replace(key, val);
            }
            return new ApiResponse<>(html);
        } catch (Exception e) {
            log.error("Exception while generating KFS html for applicationId : {}, {}, {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Unable to generate KFS");
        }
    }

    private String getFilePathTrillionLoans(LendingApplication lendingApplication, Date penaltyDate, Date penaltyDateTrillionLoans, ApplicationDocType type, boolean foreclousureChargeApplicable, String language) {
       log.info("Generating KFS/Sanction Letter for Trillionloans for application: {}, language: {}", lendingApplication, language);
        if (ApplicationDocType.KEY_FACTS_STATEMENT_DOC.equals(type)) {
            log.info("Generating KFS for Trillionloans for application: {}", lendingApplication);
            log.info("Trillionloans Penalty Release Date: {} and Lending Application Created At: {}", penaltyDateTrillionLoans, lendingApplication.getCreatedAt());
            if (ObjectUtils.isEmpty(language)) {
                language = "";
            }
            return "/templates/TRILLIONLOANS_NEW/KFS_TRILLION_PC_v3" + language + ".html";
        } else if (ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC.equals(type)) {
            if (ObjectUtils.isEmpty(language)) {
                language = "";
            }
            return "/templates/TRILLIONLOANS_NEW/SANCTION_LOAN_AGREEMENT_TRILLION_PC_v3" + language + ".html";
        }
        return null;
    }

    private String getFilePathLiquiloans(LendingApplication lendingApplication, Date penaltyDate, Date penaltyDateLiquiloans, ApplicationDocType type, boolean foreclousureChargeApplicable) {

        if (ApplicationDocType.KEY_FACTS_STATEMENT_DOC.equals(type)) {
            if (Objects.nonNull(lendingApplication.getAgreementAt()) && lendingApplication.getAgreementAt().before(penaltyDate)) {
                return "/templates/" + "KFS_P2P" + ".html";
            } else if (Objects.nonNull(lendingApplication.getAgreementAt()) && lendingApplication.getAgreementAt().before(penaltyDateLiquiloans)) {
                return (foreclousureChargeApplicable) ? "/templates/LL_P2P/KFS_P2P_PC_FC.html" : "/templates/KFS_P2P_PC.html";
            }
            return (foreclousureChargeApplicable) ? "/templates/LL_P2P/KFS_P2P_Penalty_FC.html" : "/templates/KFS_P2P_Penalty.html";

        } else if (ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC.equals(type)) {
            if (Objects.nonNull(lendingApplication.getAgreementAt()) && lendingApplication.getAgreementAt().before(penaltyDate)) {
                return "/templates/" + "SANCTION_LOAN_AGREEMENT_P2P" + ".html";
            } else if (Objects.nonNull(lendingApplication.getAgreementAt()) && lendingApplication.getAgreementAt().before(penaltyDateLiquiloans)) {
                return (foreclousureChargeApplicable) ? "/templates/LL_P2P/SANCTION_LOAN_AGREEMENT_P2P_PC_FC.html" : "/templates/SANCTION_LOAN_AGREEMENT_P2P_PC.html";
            }
            return (foreclousureChargeApplicable) ? "/templates/LL_P2P/SANCTION_LOAN_AGREEMENT_P2P_Penalty_FC.html" : "/templates/SANCTION_LOAN_AGREEMENT_P2P_Penalty.html";

        }
        return null;
    }

    public ApiResponse<?> generateSanctionCumLoanAgreement(Long applicationId, LendingApplication lendingApplication, BasicDetailsDto merchant, boolean timeStamp, Date dateTime, String lang){
        boolean generateLenderDoc = "TOPUP".equalsIgnoreCase(lendingApplication.getLoanType()) ?
                lenderDocGenerateTopUpEnabledLenders.contains(lendingApplication.getLender()) : lenderDocGenerateEnabledLenders.contains(lendingApplication.getLender());
        if (generateLenderDoc) {
            return generateLenderSanctionCumLoanAgreement(lendingApplication, true);
        }
        ApiResponse apiResponse = getKfsDetails(applicationId, lendingApplication, merchant, lang, ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC);
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
            Date penaltyDate = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss").parse(penalDate);
            Date penaltyDateLiquiloans = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss").parse(penalDateLiquiloans);
            Date penaltyDateTrillion = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss").parse(penalDateTrillion);
            Date penaltyDateTrillionLoans = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss").parse(penalDateTrillionloans);
            data = getApplicationDocData(applicationId, kfsDto, merchant, timeStamp, ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC, dateTime, lendingApplication.getIp());
            String lender = kfsDto.getLender();
            String html = "";
            String filePath = "";
            String language = "";


            if(easyLoanUtil.percentScaleUp(lendingApplication.getMerchantId(), lenderVernacLangRolloutPercent)){
                language = ObjectUtils.isEmpty(kfsDto.getSelectedLanguage()) ? languageService.getOrSetLanguageMappingByLenderAndLang(lender, lendingApplication.getId(), lang) : "_" + kfsDto.getSelectedLanguage();

                log.info("language of selection: {}", language);

            } else {
                language = languageService.getVernacLanguage(lendingApplication.getLender(), lendingApplication.getLoanType(), lendingApplication.getMerchantId());
            }

            if (ObjectUtils.isEmpty(language) || language.toUpperCase().contains("ENGLISH")) {
                language = "";
            }

            if(lender.equalsIgnoreCase(Lender.LDC.toString())){
                filePath = "/templates/" + "SANCTION_LOAN_AGREEMENT_P2P" + ".html";
            } else if (lender.equalsIgnoreCase(Lender.LIQUILOANS_P2P.toString()) || lender.equalsIgnoreCase(Lender.LIQUILOANS_P2P_OF.toString())) {
                filePath = getFilePathLiquiloans(lendingApplication, penaltyDate, penaltyDateLiquiloans, ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC, kfsDto.isForeclosureChargesRequired());
            } else if (lender.equalsIgnoreCase(Lender.MAMTA1.toString())) {
                filePath = "/templates/SANCTION_LOAN_AGREEMENT_MAMTA1.html";
            } else if (lender.equalsIgnoreCase(Lender.PIRAMAL.toString())) {
                filePath = (Objects.nonNull(lendingApplication.getCreatedAt()) && kfsDto.isForeclosureChargesRequired())? "/templates/PIRAMAL/SANCTION_LOAN_AGREEMENT_PIRAMAL" + language + ".html" : "/templates/SANCTION_LOAN_AGREEMENT_PIRAMAL" + language + ".html";
            } else if (lender.equalsIgnoreCase(Lender.ABFL.toString())) {
                filePath = "/templates/SANCTION_LOAN_AGREEMENT_NONP2P_ABFL" + language + ".html";
            } else if (lender.equalsIgnoreCase(Lender.USFB.name())) {
                filePath = "/templates/SANCTION_LOAN_AGREEMENT_USFB.html";
            } else if (Lender.CAPRI.name().equalsIgnoreCase(lender)) {
                filePath = "/templates/SANCTION_LOAN_AGREEMENT_CAPRI.html";
            } else if (Objects.nonNull(lendingApplication.getAgreementAt()) && lendingApplication.getAgreementAt().before(penaltyDateTrillion) && lender.equalsIgnoreCase(Lender.LIQUILOANS_NBFC.name())) {
                filePath = "/templates/" + "SANCTION_LOAN_AGREEMENT_NONP2P" + ".html";
            } else if (lender.equalsIgnoreCase(Lender.LIQUILOANS_NBFC.name()) || lender.equalsIgnoreCase(Lender.TRILLIONLOANS.name())) {
                filePath = getFilePathTrillionLoans(lendingApplication, penaltyDate, penaltyDateTrillionLoans, ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC, kfsDto.isForeclosureChargesRequired(), language);
            } else if (lender.equalsIgnoreCase(Lender.MUTHOOT.name())) {
                filePath = "/templates/SANCTION_LOAN_AGREEMENT_MUTHOOT" + language + ".html";
            } else if (lender.equalsIgnoreCase(Lender.PAYU.name())) {
                filePath = "/templates/SANCTION_LOAN_AGREEMENT_PAYU.html";
            } else if (lender.equalsIgnoreCase(Lender.CREDITSAISON.name())) {
                filePath = "/templates/CREDITSAISON/" + "SANCTION_LOAN_AGREEMENT_CREDIT_SAISON" + ".html";
            } else if (lender.equalsIgnoreCase(Lender.SMFG.name())) {
                filePath = "/templates/SANCTION_LOAN_AGREEMENT_SMFG.html";
            } else if(lender.equalsIgnoreCase(Lender.UGRO.name())) {
                filePath = "/templates/" + "UGRO/" + "SANCTION_LETTER.html";
            } else if (lender.equalsIgnoreCase(Lender.OXYZO.name())) {
                filePath = "/templates/OXYZO/" + "SANCTION_LOAN_AGREEMENT_OXYZO.html";
            } else {
                filePath = "/templates/" + "SANCTION_LOAN_AGREEMENT_NONP2P" + ".html";
            }

            InputStream inputStream = this.getClass().getResourceAsStream(filePath);
            Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
            html = scanner.hasNext() ? scanner.next() : "";;
            for(Map.Entry<String,Object> entry : data.entrySet()) {
                String key = "{{" + entry.getKey() + "}}";
                String val = Objects.nonNull(entry.getValue()) ? entry.getValue().toString() : "";
                //log.info(key + " " + val);
                html = html.replace(key, val);
            }
            return new ApiResponse<>(html);
        } catch (Exception e) {
            log.error("Exception while generating Sanction Cum Loan Agreement html for applicationId : {}, {}, {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Unable to generate Sanction Cum Loan Agreement");
        }
    }
//
//    public String getDisbursementRequestLetter(Long applicationId, LendingApplication lendingApplication, BasicDetailsDto merchant) {
//
//        String disbursementRequestLetterFileName =  DISBURSEMENT_REQUEST_LETTER_S3_KEY_PREFIX + applicationId + ".pdf";
//        String bucket = "loan-document";
//
//        String sanctionAndLoanAgreementShorturl = "";
//
//        if (s3BucketHandler.doesS3ObjectExist(disbursementRequestLetterFileName, bucket)) {
//            sanctionAndLoanAgreementShorturl = fetchDisbursementRequestLetterFromS3andGenerateShortUrl(applicationId, disbursementRequestLetterFileName);
//        } else {
//            sanctionAndLoanAgreementShorturl = createAndPutDisbursementRequestLetterInS3(lendingApplication.getId(), lendingApplication, merchant);
//        }
//
//        return sanctionAndLoanAgreementShorturl;
//    }

    public String createAndPutDisbursementRequestLetterInS3(Long applicationId, String html) {
        String fileName =  DISBURSEMENT_REQUEST_LETTER_S3_KEY_PREFIX + applicationId + ".pdf";;
        String bucket = s3Bucket;
        if (s3BucketHandler.doesS3ObjectExist(fileName, bucket)) {
            return fetchDisbursementRequestLetterFromS3andGenerateShortUrl(applicationId, fileName);
        }
        String shortUrl = "";
        try {
            // Get the lending application to check if it uses new PDF generation method
            LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
            
            /**
             * New Library is being used to generate PDF for selected lenders
             */
            if (lendingApplication != null && newPdfGenerationMethodLenders.contains(lendingApplication.getLender())) {
                log.info("Using new PDF generation method for disbursement request letter for applicationId: {} and lender: {}", applicationId, lendingApplication.getLender());
                
                PdfGenerationRequest.PdfGenerationRequestBuilder requestBuilder = PdfGenerationRequest.builder()
                        .html(html);

                PdfGenerationRequest request = requestBuilder.build();
                PdfGenerationResponse response = pdfGeneratorUtil.generatePdf(request);

                if (response.getSuccess()) {
                    byte[] pdfByteArray = response.getPdfAsBytes();
                    ByteArrayInputStream inStream = new ByteArrayInputStream(pdfByteArray);
                    s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, s3Bucket);
                    String disbursementRequestLetterUrl = s3BucketHandler.getPreSignedPublicURL(fileName, s3Bucket);
                    shortUrl = apiGatewayService.getShortUrl(disbursementRequestLetterUrl);
                } else {
                    log.error("Failed to generate PDF using new method for disbursement request letter for applicationId: {}", applicationId);
                    throw new Exception("Unable to generate disbursement request letter PDF for applicationID" + applicationId);
                }
            } else {
                // Fallback to existing PDF generation method
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                PdfWriter writer = new PdfWriter(outStream);
                PdfDocument pdfDocument = new PdfDocument(writer);
                InputStream htmlStringInputStream = new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));

                HtmlConverter.convertToPdf(htmlStringInputStream, pdfDocument);
                ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
                s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, s3Bucket);
                String disbursementRequestLetterUrl = s3BucketHandler.getPreSignedPublicURL(fileName, s3Bucket);
                shortUrl = apiGatewayService.getShortUrl(disbursementRequestLetterUrl);
            }
        } catch (Exception e) {
            log.error("Error while creating DisbursementRequestLetter for applicationiId : {} {} {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }

        return shortUrl;
    }



    public ApiResponse<?> generateDisbursementRequestLetter(Long applicationId, LendingApplication lendingApplication, BasicDetailsDto merchant, String clientIp, String deviceId, String platform){
        try {
            if (!"TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())) {
                return new ApiResponse<>(false, "loantype for application is not topup");
            }

            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());

            if (ObjectUtils.isEmpty(lendingApplicationDetails)) {
                return new ApiResponse<>(false, "lending application details not found");
            }

            log.info("pervious application id : {} for applicationId : {}", lendingApplicationDetails.getPrevAppId(), applicationId);

            // fetch previous lending application on which topup is created
            Optional<LendingApplication> previousLendingApplicationOptional = lendingApplicationDao.findById(lendingApplicationDetails.getPrevAppId());


            if (!previousLendingApplicationOptional.isPresent()) {
                return new ApiResponse<>(false, "previous lending application not found");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("loan_id", previousLendingApplicationOptional.get().getNbfcId());

            SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy");

            String dateString = format.format(new Date());

            data.put("date", dateString);
            data.put("borrower_name", lendingApplication.getMerchantName());

            LendingApplicationKycDetails lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());

            data.put("pan_of_borrower", lendingApplicationKycDetails.getPan());
            data.put("mobile_number", merchant.getMobile());
            data.put("device_id", Objects.toString(deviceId, ""));
            data.put("ip_address", Objects.toString(clientIp, ""));
            data.put("platform", Objects.toString(PLATFORM_PREFIX + platform, PLATFORM_PREFIX));
            data.put("time_stamp", new Date());


            String html = "";
            String filePath = "/templates/topup_disbursement_letter_liquiloans.html";
            InputStream inputStream = this.getClass().getResourceAsStream(filePath);
            Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
            html = scanner.hasNext() ? scanner.next() : "";;
            for(Map.Entry<String,Object> entry : data.entrySet()) {
                String key = "{{" + entry.getKey() + "}}";
                String val = Objects.nonNull(entry.getValue()) ? entry.getValue().toString() : "";
                log.info(key + " " + val);
                html = html.replace(key, val);
            }
            createAndPutDisbursementRequestLetterInS3(applicationId, html);
            return new ApiResponse<>(html);
        } catch (Exception e) {
            log.error("Exception while generating Disbursement Request Letter html for applicationId : {}, {}, {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Unable to generate Disbursement Request Letter");
        }
    }

    public String fetchKfsFromS3andGenerateShortUrl(Long applicationId, String fileName) throws Exception {
            String kfsUrl = s3BucketHandler.getPreSignedPublicURL(fileName, s3Bucket);
            String kfsShortUrl = apiGatewayService.getShortUrl(kfsUrl);
            if (kfsShortUrl == null || kfsShortUrl.isEmpty() || kfsShortUrl.trim().isEmpty())
                throw new Exception("Unable to create short URL for KFS doc link for : " + applicationId);

            return kfsShortUrl;
    }

    public String fetchSanctionAndLoanAgreementFromS3andGenerateShortUrl(Long applicationId, String fileName) throws Exception {
        String sanctionCumLoanAgreementUrl = s3BucketHandler.getPreSignedPublicURL(fileName, s3Bucket);
        String shortUrl = apiGatewayService.getShortUrl(sanctionCumLoanAgreementUrl);
        if(shortUrl == null || shortUrl.isEmpty() || shortUrl.trim().isEmpty())
            throw new Exception("Unable to create short URL for Sanction Loan Agreement doc link for : " + applicationId);

        return shortUrl;
    }

    public String fetchDisbursementRequestLetterFromS3andGenerateShortUrl(Long applicationId, String fileName) {
        String disbursementRequestLetterUrl = null;
        try {
            disbursementRequestLetterUrl = s3BucketHandler.getPreSignedPublicURL(fileName, s3Bucket);
        } catch (FileNotFoundException e) {
            log.error("Unable to find file  disbursementRequestLetter for applicationId :  {} {}", applicationId, e.getMessage());
            return null;
        }
        String shortUrl = apiGatewayService.getShortUrl(disbursementRequestLetterUrl);
        if(shortUrl == null || shortUrl.isEmpty() || shortUrl.trim().isEmpty()){
            log.error("Unable to create disbursementRequestLetterUrl for applicationId :  {}", applicationId);
        }

        return shortUrl;
    }

    public String fetchLoanInsuranceDoc(Long applicationId, String fileName) {
        String insuranceDocUrl = null;
        try {
            insuranceDocUrl = s3BucketHandler.getPreSignedPublicURL(fileName, s3Bucket);
        } catch (FileNotFoundException e) {
            log.error("Unable to find file  insuranceDoc for applicationId :  {} {}", applicationId, e.getMessage());
            return null;
        }
        String shortUrl = apiGatewayService.getShortUrl(insuranceDocUrl);
        if(shortUrl == null || shortUrl.isEmpty() || shortUrl.trim().isEmpty()){
            log.error("Unable to create insuranceDocUrl for applicationId :  {}", applicationId);
        }
        return shortUrl;
    }

    public String fetchAuthorizationLetterFromS3andGenerateShortUrl(Long applicationId, String fileName) throws Exception {
        String authorizationLetterUrl = s3BucketHandler.getPreSignedPublicURL(fileName, s3Bucket);
        String authorizationLetterShortUrl = apiGatewayService.getShortUrl(authorizationLetterUrl);
        if (ObjectUtils.isEmpty(authorizationLetterShortUrl)|| authorizationLetterShortUrl.trim().isEmpty())
            throw new Exception("Unable to create short URL for Authorization doc link for : " + applicationId);

        return authorizationLetterShortUrl;
    }

    public Map<String, Object> getApplicationDocData(Long applicationId, KfsDto kfsDto, BasicDetailsDto merchant, boolean timeStamp, ApplicationDocType applicationDocType, Date dateTime, String ip) throws Exception {
        if(ObjectUtils.isEmpty(dateTime)){
            dateTime  = dateTimeUtil.getCurrentDate();
        }
        double version = 2;

        if(Lender.TRILLIONLOANS.toString().equals(kfsDto.getLender())){
            version = 1;
        }
        Double aprWithoutGst = null;
        if (Lender.SMFG.name().equalsIgnoreCase(kfsDto.getLender()) && Objects.nonNull(kfsDto.getLoanAmount()) && Objects.nonNull(kfsDto.getProcessingFeeWithoutGst()) && kfsDto.getLoanAmount() != 0) {

            Double amountToCalculateAprOn = kfsDto.getLoanAmount() - kfsDto.getProcessingFeeWithoutGst();;

            aprWithoutGst = getApr(kfsDto.getMerchantId(), applicationId, amountToCalculateAprOn,
                    EdiModel.SEVEN_DAY_MODEL.getNoOfEdiDaysInAWeek(), kfsDto.getLender());
            aprWithoutGst = Double.valueOf(String.format("%.2f", aprWithoutGst));
        }

        List<PenaltyFeeConfigSlave> penaltyFeeConfigSlaveList = penaltyFeeConfigDaoSlave.findByVersionAndStatusAndLenderOrderByMinAmountAsc(version, true, kfsDto.getLender());
        final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchant.getId());
        BankDetailsDto merchantBankDetail = bankDetailsDtoOptional.orElse(null);
        Map<String, Object> data = new HashMap<>();
        data.put("lsp_contact_name", KfsConstants.LSP_CONTACT_NAME);
        data.put("lsp_contact_number", KfsConstants.LSP_CONTACT_NUMBER_FORMATTED);
        data.put("nodal_officer_name", KfsConstants.NODAL_OFFICER_NAME);
        data.put("nodal_officer_contact_number", KfsConstants.NODAL_OFFICER_CONTACT_NUMBER);
        data.put("gro_officer_name", KfsConstants.GRO_OFFICER_NAME);
        data.put("gro_officer_contact_number", KfsConstants.GRO_OFFICER_CONTACT_NUMBER);
        data.put("name_of_lender_nbfc", kfsDto.getLenderCorporateName());
        data.put("register_address_of_nbfc", kfsDto.getLenderBusinessAddress());
        data.put("loan_amount_in_figure", kfsDto.getLoanAmount());
        data.put("loan_amount_in_words", getAmountInWords(kfsDto.getLoanAmount().toString()));
        data.put("processing_percentage", kfsDto.getProcessingFeePercentage());
        data.put("processing_percentage_without_gst", kfsDto.getProcessingFeePercentageWithoutGst());
        data.put("processing_fee_without_gst", kfsDto.getProcessingFeeWithoutGst());
        data.put("processing_fee_includes_tax", kfsDto.getProcessingFee());
        data.put("processing_fee_in_words_includes_tax", getAmountInWords(kfsDto.getProcessingFee().toString()));
        data.put("rate_of_interest", kfsDto.getInterestRate());
        data.put("rate_of_interest_in_words",getAmountInWords(kfsDto.getInterestRate().toString()));
        data.put("apr", kfsDto.getApr());
        data.put("interest_charged_to_borrower", kfsDto.getRepaymentAmount() - kfsDto.getLoanAmount());
        data.put("interest_charged_to_borrower_in_words", getAmountInWords(data.get("interest_charged_to_borrower").toString()));
        data.put("total_amount_including_interest_paid_by_borrower_entire_tenure", kfsDto.getRepaymentAmount());
        data.put("total_amount_including_interest_paid_by_borrower_entire_tenure_in_words", getAmountInWords(kfsDto.getRepaymentAmount().toString()));
        data.put("loan_amount_disbursed", kfsDto.getDisbursalAmount());
        data.put("loan_amount_disbursed_in_words", getAmountInWords(kfsDto.getDisbursalAmount().toString()));
        data.put("interest_equal_daily", kfsDto.getEdiAmount());
        data.put("edi_amount", kfsDto.getEdiAmount());
        data.put("edi_amount_in_words", getAmountInWords(kfsDto.getEdiAmount().toString()));
        data.put("interest_equal_daily_in_words", getAmountInWords(kfsDto.getEdiAmount().toString()));
        data.put("no_of_edis", kfsDto.getEdiCount());
        data.put("repayment_frequency", Arrays.asList(Lender.PIRAMAL.name()).contains(kfsDto.getLender()) ? "Daily" : "");
        data.put("edi_off_day",kfsDto.getEdiOffData());
        data.put("default_rate_of_interest", "N/A");
        data.put("default_rate_of_interest_on_monthly_or_daily_basis", "N/A");
        data.put("forclosure_amount_rate_of_rate_of_interest", "Foreclosure is allowed with no charges");
        data.put("cooling_off_days", kfsDto.getCoolingOffDays());
        data.put("tenure_of_loan_in_months", kfsDto.getTenureInMonths());
        data.put("name_of_nodal_officer", kfsDto.getLenderContactName());
        data.put("email_id", kfsDto.getLenderContactEmail());
        data.put("conatct_no", kfsDto.getLenderContactNumber());
        data.put("grievance_time", kfsDto.getLenderGrievanceTime());
        data.put("timing_for_contact", "");
        data.put("name_of_nodal_officer_lsp", kfsDto.getLspContactName());
        data.put("email_id_lsp", kfsDto.getLspContactEmail());
        data.put("conatct_no_lsp", kfsDto.getLspContactNumber());
        data.put("timing_for_contact_lsp", "");
        data.put("facilitation_fee_in_figure", "0.00");
        data.put("monthlyIncome", kfsDto.getMonthlyIncome());
        log.info("lender {} {}", kfsDto.getLender(), applicationDocType);
        data.put("processing_fee_statement", kfsDto.isTopUpLoan() && !Lender.TRILLIONLOANS.name().equalsIgnoreCase(kfsDto.getLender()) ? "" : kfsDto.getProcessingFeePercentageWithoutGst()+"% of the loan Amount " + (kfsDto.getProcessingFee()==0?"":("+ " + KfsConstants.GST_PERCENTAGE + "% GST on processing fees ")) + "i.e. ");
        String repaymentSchedule = "";
        if(Arrays.asList(Lender.PAYU.name()).contains(kfsDto.getLender())) {
            RepaymentScheduleResponseDTO repaymentScheduleResponseDTO = getLenderRepaymentSchedule(applicationId, kfsDto.getLender(), true);
            repaymentSchedule = repaymentScheduleResponseDTO.getRepaymentSchedule();
            if (ObjectUtils.isEmpty(repaymentScheduleResponseDTO.getTotalInterestPayable()) ||
                    ObjectUtils.isEmpty(repaymentScheduleResponseDTO.getTotalRepaymentExpected()) ||
                    ObjectUtils.isEmpty(repaymentScheduleResponseDTO.getNetDisbursalAmount())
            ) throw new Exception("Unable to create repayment schedule for PayU" + applicationId);
            data.put("interest_charged_to_borrower", repaymentScheduleResponseDTO.getTotalInterestPayable());
            data.put("repayment_amount", repaymentScheduleResponseDTO.getTotalRepaymentExpected());
            if (!kfsDto.isTopUpLoan()) {
                data.put("loan_amount_disbursed", repaymentScheduleResponseDTO.getNetDisbursalAmount());
            }
        } else {
            repaymentSchedule = getRepaymentSchedule(applicationId, merchant, kfsDto.getLender());
        }
        if (ObjectUtils.isEmpty(repaymentSchedule))throw new Exception("Unable to create repayment schedule for" + applicationId);
        data.put("repayment_schedule", repaymentSchedule);
        if(timeStamp)data.put("date", new SimpleDateFormat("dd-MM-yyyy").format(dateTime));
        else data.put("date", "");
        data.put("disbursal_date", new SimpleDateFormat("dd-MMM-yyyy").format(dateTime));
        data.put("mobile_number_for_otp", merchant.getMobile());
        data.put("platform", "BharatPe for Business");
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
        data.put("upfront_charges", "NA");
        data.put("loan_purpose",(blTaggingEnabled && blEligibleLendersList.contains(kfsDto.getLender()) ? "Business loan" : kfsDto.getLoanPurpose()));
        data.put("smb_user_id", kfsDto.getSmbId());
        data.put("lead_id", kfsDto.getLeadId());
        data.put("offer_id",kfsDto.getOfferId());
        data.put("repayment_amount",kfsDto.getRepaymentAmount());
        ApiResponse aadharAddressResponse = getAadhaarAddress(merchant, applicationId);
        if(aadharAddressResponse.isSuccess()){
            AadhaarAddressResponseDTO aadhaarAddressResponseDTO = (AadhaarAddressResponseDTO)aadharAddressResponse.getData();
            data.put("address_of_borrower", aadhaarAddressResponseDTO.getAddress());
            if (!ObjectUtils.isEmpty(aadhaarAddressResponseDTO.getName())) {
                data.put("borrower_name", aadhaarAddressResponseDTO.getName());
                data.put("borrower_city",aadhaarAddressResponseDTO.getCity());
                data.put("borrower_state",aadhaarAddressResponseDTO.getState());
                data.put("borrower_pincode",aadhaarAddressResponseDTO.getPincode());
                data.put("gender",aadhaarAddressResponseDTO.getGender());
                data.put("dob",aadhaarAddressResponseDTO.getDob());
                data.put("age_of_applicant", kycUtils.getAgeFromDob(aadhaarAddressResponseDTO.getDob()));
                data.put("aadhar_Number",aadhaarAddressResponseDTO.getAadharNumber());
                log.info("borrower name getting populated in agreement for application: {} {}", aadhaarAddressResponseDTO.getName(), applicationId);
            }

        }
        else throw new Exception("Unable to get aadhar address for : " + applicationId);
        data.put("device_id", "");
        if(!ObjectUtils.isEmpty(merchantBankDetail)) {
            data.put("accountNumber", LendingEnum.LENDER.SMFG.name().equalsIgnoreCase(kfsDto.getLender()) ? merchantBankDetail.getAccountNumber() :  "XXXXXXXX" + merchantBankDetail.getAccountNumber().substring(merchantBankDetail.getAccountNumber().length() - 4));
            data.put("accountType", merchantBankDetail.getAccountType());
            data.put("ifsc", merchantBankDetail.getIfsc());
            data.put("bankName", merchantBankDetail.getBankName());
            log.info("borrower bank details getting populated for application ; {}",applicationId);
        }
        LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(applicationId);

        if (Lender.PAYU.name().equalsIgnoreCase(kfsDto.getLender()) && !ObjectUtils.isEmpty(lendingGstDetail)) {
            data.put("business_city",lendingGstDetail.getCity());
            data.put("business_state",lendingGstDetail.getState());
            data.put("business_pincode",lendingGstDetail.getPincode());
        }

        if(Lender.PAYU.name().equalsIgnoreCase(kfsDto.getLender())){
            String formattedMobileNumber = merchant.getMobile();
            formattedMobileNumber = formattedMobileNumber.substring(0,2) + " " + formattedMobileNumber.substring(2);
            data.put("phone_number_of_borrower", formattedMobileNumber);
            data.put("mobile_number_for_otp", formattedMobileNumber);
            data.put("processing_fee_includes_tax", String.format("%.2f", kfsDto.getProcessingFee()));
            data.put("processing_percentage_without_gst", String.format("%.2f",kfsDto.getProcessingFeePercentageWithoutGst()));
            data.put("gst_amount_of_processing_fee", String.format("%.2f",(kfsDto.getLoanAmount() * (kfsDto.getProcessingFeePercentageWithoutGst()/100D) * (KfsConstants.GST_PERCENTAGE)/100D)));
        }

        if(Lender.CREDITSAISON.name().equalsIgnoreCase(kfsDto.getLender())){
            data.put("processing_fee_excludes_tax", String.format("%.2f", kfsDto.getProcessingFee() / 1.18));
            data.put("annual_rate_of_interest", String.format("%.2f", kfsDto.getAnnualRoi()));
        }

        String ediStartDate = lendingEdiScheduleService.getEdiStartDate(merchant.getId(),applicationId);
        if(!ObjectUtils.isEmpty(ediStartDate))
        {
            data.put("ediStartDate",ediStartDate);
        }
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
        if (!penaltyFeeConfigSlaveList.isEmpty()) {
            data.put("outstanding_amount_1", penaltyFeeConfigSlaveList.get(0).getMaxAmount());
            data.put("penal_charges_1", penaltyFeeConfigSlaveList.get(0).getPenalty());
            for (int i = 1; i < penaltyFeeConfigSlaveList.size(); i++) {
                String value = String.valueOf((i + 1));
                data.put("outstanding_amount_min_" + value, penaltyFeeConfigSlaveList.get(i).getMinAmount());
                data.put("outstanding_amount_max_" + value, penaltyFeeConfigSlaveList.get(i).getMaxAmount());
                data.put("penal_charges_" + value, penaltyFeeConfigSlaveList.get(i).getPenalty());
            }
        }
        data.put("edi_days", kfsDto.getEdiDays());
        data.put("date_of_execution",Optional.ofNullable(kfsDto.getAgreementAt()).map(String::valueOf).orElse(""));
        data.put("shopAddress",kfsDto.getShopAddress());
        data.put("shop_name", kfsDto.getShopName());
        data.put("shop_state", kfsDto.getShopState());
        data.put("shop_city", kfsDto.getShopCity());
        data.put("shop_pincode", kfsDto.getShopPincode());
        data.put("annual_turnover",kfsDto.getAnnualTurnover()); // summaryTPV * 360
        data.put("monthlyIncome",Optional.ofNullable(kfsDto.getMonthlyIncome()).map(String::valueOf).orElse(""));
        data.put("annual_roi", kfsDto.getAnnualRoi());
        data.put("parent_lender_corporate_name", kfsDto.getParentLenderCorporateName());

        if(kfsDto.isForeclosureChargesRequired()) {
            List<ForeClosureConfig> foreClosureConfigList = foreClosureConfigDao.findByLender(kfsDto.getLender());
            if (!CollectionUtils.isEmpty(foreClosureConfigList)) {
                data.put("min_charge", foreClosureConfigList.get(0).getMinAmount());
                data.put("gst_rate", foreClosureConfigList.get(0).getGst());
                for (int i = 0; i < foreClosureConfigList.size(); i++) {
                    String value = String.valueOf(i + 1);
                    data.put("foreclosure_tenure_" + value, foreClosureConfigList.get(i).getTenure());
                    data.put("closure_max_" + value, foreClosureConfigList.get(i).getDurationTo());
                    data.put("rate_of_principle_" + value, foreClosureConfigList.get(i).getRate());
                    data.put("closure_min_" + value, foreClosureConfigList.get(i).getDurationFrom());
                }
            }
        }

        if(Lender.ABFL.name().equalsIgnoreCase(kfsDto.getLender())){
            if(kfsDto.isTopUpLoan()){
                data.put("foreclosure_amount_display_prop", "table-row");
                data.put("foreclosure_amount", kfsDto.getLenderForeclosureAmount());
                data.put("foreclosure_amount_in_words", getAmountInWords(kfsDto.getLenderForeclosureAmount().toString()));
                data.put("topup_loan_clause_display_prop", "block");
                data.put("parent_loan_bpl_id", kfsDto.getParentLoanBplId());
            } else{
                data.put("foreclosure_amount_display_prop", "none");
                data.put("topup_loan_clause_display_prop", "none");
            }
        }

        if (Arrays.asList(Lender.PIRAMAL.name(), Lender.PAYU.name()).contains(kfsDto.getLender())) {
            LendingApplication parentLendingApplication = lendingApplicationDao.findByExternalLoanId(kfsDto.getParentLoanBplId());
            if (kfsDto.isTopUpLoan()) {
                data.put("parent_loan_amount", parentLendingApplication.getLoanAmount());
                data.put("parent_lan_no", parentLendingApplication.getNbfcId());
                data.put("foreclosure_amount", kfsDto.getLenderForeclosureAmount());
                data.put("foreclosure_amount_in_words", getAmountInWords(kfsDto.getLenderForeclosureAmount().toString()));
                data.put("topup_loan_clause_display_prop", "block");
                data.put("topup_loan_na_clause_display_prop", "none");
            } else {
                data.put("parent_loan_amount", "NA");
                data.put("parent_lan_no", "NA");
                data.put("foreclosure_amount", "NA");
                data.put("topup_loan_clause_display_prop", "none");
                data.put("topup_loan_na_clause_display_prop", "block");
            }
        }


        if (Lender.TRILLIONLOANS.name().equalsIgnoreCase(kfsDto.getLender())) {
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(kfsDto.getApplicationId());
            if (!lendingApplication.isPresent()) {
                log.error("LendingApplication is not present for applicationId : {}", kfsDto.getApplicationId());
                throw new RuntimeException();
            }

            Date penaltyDateTrillionLoans = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss").parse(penalDateTrillionloans);
            if (lendingApplication.get().getCreatedAt().before(penaltyDateTrillionLoans)) {
                data.put("penalty_charges_clause_display_prop", "none");
                data.put("penalty_charges_na_clause_display_prop", "block");
            } else {
                data.put("penalty_charges_clause_display_prop", "block");
                data.put("penalty_charges_na_clause_display_prop", "none");
            }

            if (kfsDto.isForeclosureChargesRequired()) {
                data.put("foreclosure_charges_clause_display_prop", "block");
                data.put("foreclosure_charges_na_clause_display_prop", "none");
            } else {
                data.put("foreclosure_charges_clause_display_prop", "none");
                data.put("foreclosure_charges_na_clause_display_prop", "block");
            }

            if (kfsDto.isTopUpLoan()) {
                data.put("loan_foreclosure_amount", kfsDto.getLenderForeclosureAmount());
                data.put("parent_loan_bpl_id", kfsDto.getParentLoanBplId());
                data.put("topup_loan_clause_display_prop", "block");
                data.put("topup_loan_na_clause_display_prop", "none");
            } else {
                data.put("topup_loan_clause_display_prop", "none");
                data.put("topup_loan_na_clause_display_prop", "block");
            }
        }

        data.put("personal_loan_amount", kfsDto.getDisbursalAmount() + kfsDto.getProcessingFee());
        data.put("personal_loan_amount_in_words", getAmountInWords(String.valueOf(kfsDto.getDisbursalAmount() + kfsDto.getProcessingFee())));
        LendingLoanInsurance lendingLoanInsurance = insuranceService.getInsuranceDetails(applicationId, kfsDto.getLender(), SELECTED);
        if(ObjectUtils.isEmpty(lendingLoanInsurance)) {
            data.put("insurance_na_display", "block");
            data.put("insurance_display", "none");
            data.put("insurance_premium", "NA");
            data.put("insurance_premium_in_words", "NA");

        } else {
            data.put("insurance_na_display", "none");
            data.put("insurance_display", "block");
            data.put("insurance_premium", lendingLoanInsurance.getInsurancePremium());
            data.put("insurance_premium_in_words", getAmountInWords(lendingLoanInsurance.getInsurancePremium().toString()));
        }

        LendingApplicationKycDetails lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(kfsDto.getApplicationId(), kfsDto.getLender());
        if (!ObjectUtils.isEmpty(lendingApplicationKycDetails)) {
            data.put("father_name", lendingApplicationKycDetails.getFatherName());
            data.put("email_address", lendingApplicationKycDetails.getEmail());
        }
        data.put("total_amount_including_processing_fees", kfsDto.getRepaymentAmount() + kfsDto.getProcessingFee());
        if (Lender.UGRO.name().equalsIgnoreCase(kfsDto.getLender())) {
            data.put("gst_number", !ObjectUtils.isEmpty(lendingGstDetail) && !ObjectUtils.isEmpty(lendingGstDetail.getGstNumber()) ? lendingGstDetail.getGstNumber() : "NOT AVAILABLE");
            data.put("father_name", !ObjectUtils.isEmpty(lendingApplicationKycDetails) && !ObjectUtils.isEmpty(lendingApplicationKycDetails.getFatherName()) ? lendingApplicationKycDetails.getFatherName() : "NOT AVAILABLE");
            data.put("annual_roi", String.format("%.2f", kfsDto.getAnnualRoi()));
        }
        CKycResponseDto cKycResponseDto = kycUtils.getKycData(kfsDto.getMerchantId());
        data.put("borrower_selfie", cKycResponseDto.getSelfieString());
        if (Lender.SMFG.name().equalsIgnoreCase(kfsDto.getLender())) {
            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(applicationId);
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(kfsDto.getApplicationId(), kfsDto.getLender());
            String businessCategory = "Others";
            String businessSubCategory = "Others";
            if (REPEAT.equals(lendingRiskVariablesSnapshot.getRiskSegment())) {
                Map<String, Object> metaData = lendingApplicationLenderDetails.getMetaData();
                if (Objects.nonNull(metaData) && metaData.containsKey(MERCHANT_CATEGORY)) {
                    businessCategory = String.valueOf(metaData.getOrDefault(MERCHANT_CATEGORY, ""));
                    businessSubCategory = String.valueOf(metaData.getOrDefault(MERCHANT_SUB_CATEGORY, ""));
                }
                if(ObjectUtils.isEmpty(businessCategory)){
                    throw new Exception("SMFG : Business Category is empty for applicationId : " + applicationId);
                }
            }
            data.put("business_category", businessCategory);
            data.put("business_sub_category", businessSubCategory);
            data.put("udyam_number", null);
            data.put("apr_without_gst", aprWithoutGst);
            Double gstAmountOfProcessingFee = kfsDto.getProcessingFee() - kfsDto.getProcessingFeeWithoutGst();
            data.put("gst_amount_of_processing_fee", String.format("%.2f",gstAmountOfProcessingFee));
            data.put("tenure_of_loan_in_days", kfsDto.getEdiCount());
            if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails) && !ObjectUtils.isEmpty(lendingApplicationLenderDetails.getDataUploadStatus()) && lendingApplicationLenderDetails.getDataUploadStatus().equalsIgnoreCase(smfgConfig.getPslFlagTrue())) {
                PriorityQueue<BusinessDocsDTO> businessDocs = kycUtils.getBusinessDocData(kfsDto.getMerchantId(), "SMFG", KycDocType.UDYAM_CERTIFICATE.name());
                data.put("udyam_number", businessDocs.peek() != null ? businessDocs.peek().getDocIdentifier() : null);
            }
        }
        data.put("parent_lender", ObjectUtils.isEmpty(kfsDto.getParentLender()) ? "NA" : kfsDto.getParentLender());
        //log.info("data ****** {}", new ObjectMapper().writeValueAsString(data));
        return data;
    }

    public String getLenderLogo(String lender, ApplicationDocType applicationDocType){
        String logoUrl = "";
        if(lender.equalsIgnoreCase(Lender.LIQUILOANS_P2P.toString()) || lender.equalsIgnoreCase(Lender.LIQUILOANS_P2P_OF.toString())){
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/easy_loans/liquiloans_header-1709183554567.png";
        } else if((lender.equalsIgnoreCase(Lender.LIQUILOANS_NBFC.name()) || Lender.TRILLIONLOANS.toString().equalsIgnoreCase(lender))
                && applicationDocType.equals(ApplicationDocType.LIQUILOANS_NBFC_FOOTER)) {
//            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/easy_loans/easy_loans/Trliions_Footer-1705915638774.png";
            logoUrl = "";
        } else if (lender.equalsIgnoreCase(Lender.LIQUILOANS_NBFC.toString()) || Lender.TRILLIONLOANS.toString().equalsIgnoreCase(lender)) {
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/easy_loans/3x-1744701539763.png";
        } else if (lender.equalsIgnoreCase(Lender.ABFL.toString()) && applicationDocType.equals(ApplicationDocType.ABFL_LETTERHEAD_FOOTER)) {
//            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/abfl-footer.png";
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/abfl-letterhead-with-padding_1.png";
        }
        else if(lender.equalsIgnoreCase(Lender.PIRAMAL.toString()) && applicationDocType.equals(ApplicationDocType.PIRAMAL_LETTERHEAD_FOOTER)){
//            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/pirmal/piramal_letter_footer.jpg";
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/easy_loans/pirmal-foot-1747895919163.png";
        }
        else if(lender.equalsIgnoreCase(Lender.ABFL.toString()) && applicationDocType.equals(ApplicationDocType.WELCOME_LETTER_DOC)){
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/abfl-welcome.png";
        }
        else if(lender.equalsIgnoreCase(Lender.ABFL.toString())){
//            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/abfl-letterhead.png";
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/abfl-letterhead-compressed_1.png";
        }
        else if(lender.equalsIgnoreCase(Lender.PIRAMAL.name())) {
//            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/piramal-logo.png";
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/pirmal/piramal_logo_header.png";
        }
        else if(lender.equalsIgnoreCase(Lender.MUTHOOT.name()) && applicationDocType.equals(ApplicationDocType.MUTHOOT_LETTERHEAD_FOOTER)){
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/easy_loans/mfl-footer-1708687663592.png";
        }
        else if(lender.equalsIgnoreCase(Lender.MUTHOOT.name())){
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/easy_loans/mfl-header-1708687626411.png";
        }
        else if(lender.equalsIgnoreCase(Lender.PAYU.name()) && applicationDocType.equals(ApplicationDocType.PAYU_LETTERHEAD_FOOTER)){
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/easy_loans/payu-footer-bp-compressed-1729234893465.png";
        }
        else if(lender.equalsIgnoreCase(Lender.PAYU.name())){
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/easy_loans/Payu-1743068063665.png";
        }
        else if(lender.equalsIgnoreCase(Lender.LDC.toString()) && applicationDocType.equals(ApplicationDocType.SANCTION_CUM_LOAN_AGREEMENT_DOC)){
            logoUrl = "https://bharatpe-cdn.s3.ap-south-1.amazonaws.com/LendenAddress.png";
        } else if(Lender.CAPRI.name().equalsIgnoreCase(lender) && ApplicationDocType.CAPRI_LETTERHEAD_FOOTER.equals(applicationDocType)) {
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/easy_loans/capri_footer-1709098048918.png";
        } else if(Lender.CAPRI.name().equalsIgnoreCase(lender)) {
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/easy_loans/capri_header-1709098083927.png";
        } else if(lender.equalsIgnoreCase(Lender.LDC.toString()) && applicationDocType.equals(ApplicationDocType.KEY_FACTS_STATEMENT_DOC)){
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/Lenden.png";
        } else if (lender.equalsIgnoreCase(Lender.HINDON.name()) && applicationDocType.equals(ApplicationDocType.HINDON_LETTERHEAD_HEADER)) {
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/hindon_letterhead-1681130033877.png";
        } else if (lender.equalsIgnoreCase(Lender.HINDON.name()) && applicationDocType.equals(ApplicationDocType.HINDON_LETTERHEAD_FOOTER)) {
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/hindon_footer-1681129971473.png";
        } else if (lender.equalsIgnoreCase(Lender.CREDITSAISON.name())) {
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/easy_loans/CREDIT_SASION_INDIA_LOGOMARK_new_font-1732282601256.png";
        } else if (lender.equalsIgnoreCase(Lender.UGRO.name())) {
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/easy_loans/UGRO_CAPITAL-1736507987426.png";
        } else if(lender.equalsIgnoreCase(Lender.MAMTA.toString())
          || lender.equalsIgnoreCase(Lender.MAMTA0.toString())
          || lender.equalsIgnoreCase(Lender.MAMTA1.toString())
          || lender.equalsIgnoreCase(Lender.MAMTA2.toString())){
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/MamtaLogoKFS.png";
        } else if (lender.equalsIgnoreCase(Lender.SMFG.name())) {
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/easy_loans/SMICC-Logo-1724934813170_1-1728285292259.png";
        } else if(lender.equalsIgnoreCase(Lender.OXYZO.name()) && applicationDocType.equals(ApplicationDocType.OXYZO_LETTERHEAD_FOOTER)){
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/easy_loans/Oxyzo_footer-1737110322362.png";
        }
        else if(lender.equalsIgnoreCase(Lender.OXYZO.name())){
            logoUrl = "https://d30gqtvesfc1d5.cloudfront.net/hubble/easy_loans/Oxyzo_header-1737110280049.png";
        }
        return logoUrl;
    }

    public String getRepaymentSchedule(Long applicationId, BasicDetailsDto merchant, String lender) {
        CommonResponse response = Lender.SMFG.name().equalsIgnoreCase(lender) ? lendingEdiScheduleService.getEdiScheduleV3(merchant.getId(), applicationId)
        : lendingEdiScheduleService.getEdiScheduleV2(merchant.getId(), applicationId,null);
        if (!response.isSuccess()) {
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

    public RepaymentScheduleResponseDTO getLenderRepaymentSchedule(Long applicationId, String lender, Boolean isRpsRequired) {
        LenderEdIScheduleResponseDTO lenderEdIScheduleResponse= associationServiceUtil.invokeRepaymentScheduleService(lender, applicationId, Boolean.TRUE);
        if(ObjectUtils.isEmpty(lenderEdIScheduleResponse)){
            log.info("Unable to fetch lender edi schedule for applicationId : {}", applicationId);
            return null;
        }

        String html = "";
        if(isRpsRequired) {
            LenderEdIScheduleResponseDTO.RepaymentSchedule edi = null;
            log.info("lenderEdIScheduleResponse response received from loan preview is : {}", lenderEdIScheduleResponse);
            for (int i = 0; i < lenderEdIScheduleResponse.getRepaymentSchedule().size(); i++) {
                edi = lenderEdIScheduleResponse.getRepaymentSchedule().get(i);
                int serialNumber = i + 1;
                html += "    <tr class=\"width-100\">\n" +
                        "      <td class=\"width-auto\">\n" +
                        "        <p style=\"margin: 0px; font-size: 10px;\" class=\"fw400 text-center\">" + serialNumber + "</p>\n" +
                        "      </td>\n" +
                        "      <td class=\"width-auto\">\n" +
                        "        <p style=\"margin: 0px; font-size: 10px;\" class=\"fw400 text-center\">" + edi.getOpeningBalance() + "</p>\n" +
                        "      </td>\n" +
                        "      <td class=\"width-auto\">\n" +
                        "        <p style=\"margin: 0px; font-size: 10px;\" class=\"fw400 text-center\">" + edi.getPrincipal() + "</p>\n" +
                        "      </td>\n" +
                        "      <td class=\"width-auto\">\n" +
                        "        <p style=\"margin: 0px; font-size: 10px;\" class=\"fw400 text-center\">" + edi.getInterest() + "</p>\n" +
                        "      </td>\n" +
                        "      <td class=\"width-auto\">\n" +
                        "        <p style=\"margin: 0px; font-size: 10px;\" class=\"fw400 text-center\">" + edi.getTotalEdi() + "</p>\n" +
                        "      </td>\n" +
                        "    </tr>\n";
            }
        }
        RepaymentScheduleResponseDTO repaymentScheduleResponseDTO = RepaymentScheduleResponseDTO.builder()
                .repaymentSchedule(html)
                .totalInterestPayable(lenderEdIScheduleResponse.getTotalInterestPayable())
                .totalRepaymentExpected(lenderEdIScheduleResponse.getTotalRepaymentExpected())
                .netDisbursalAmount(lenderEdIScheduleResponse.getNetDisbursalAmount())
                .build();
        return repaymentScheduleResponseDTO;
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
                        lendingGstDetail.setPincode(kycDoc.getPincode());
//                        lendingApplication.setPincode(Long.valueOf(kycDoc.getPincode())); commenting due to EL-2030
                        lendingGstDetail.setState(kycDoc.getState());
                        lendingGstDetail.setAddress2(null);
                        lendingGstDetail.setLandmark(null);
                        lendingGstDetail.setAddressType("Same");
                    }
                }
                if(kycUtils.isEligibleForSkipKycOrLenderKyc(lendingApplication)) {
                    LendingApplicationKycDetails lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, lendingApplication.getLender());
                    if (!ObjectUtils.isEmpty(lendingApplicationKycDetails)) {
                        lendingGstDetail.setAddress1(lendingApplicationKycDetails.getAadharAddress());
                        lendingGstDetail.setCity(lendingApplicationKycDetails.getAadharCity());
                        lendingGstDetail.setPincode(lendingApplicationKycDetails.getAadharPinCode());
                        lendingGstDetail.setState(lendingApplicationKycDetails.getAadharState());
                        lendingGstDetail.setAddress2(null);
                        lendingGstDetail.setLandmark(null);
                        lendingGstDetail.setAddressType("Same");
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
                lendingGstDetail.setAddressType("Different");
                log.info("Updating current address details as address provided by merchant of applicationId {} and merchantId {}", applicationId, merchant.getId());
            }
            lendingGstDao.save(lendingGstDetail);
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
            lendingApplicationDetails.setCurrentAddressSameAsPermanentAddress(sameAsAdhaar);
            lendingApplicationDetailsDao.save(lendingApplicationDetails);
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
            if(kycUtils.isEligibleForSkipKycOrLenderKyc(lendingApplication)) {
                LendingApplicationKycDetails lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, lendingApplication.getLender());
                if (!ObjectUtils.isEmpty(lendingApplicationKycDetails)) {
                    AadhaarAddressResponseDTO dto = new AadhaarAddressResponseDTO();
                    dto.setAddress(lendingApplicationKycDetails.getAadharAddress());
                    dto.setGender(lendingApplicationKycDetails.getGender());
                    dto.setName(lendingApplicationKycDetails.getAadharName());
                    dto.setDob(lendingApplicationKycDetails.getDob());
                    dto.setAadharNumber(lendingApplicationKycDetails.getAadharIdentifier());
                    dto.setState(lendingApplicationKycDetails.getAadharState());
                    dto.setCity(lendingApplicationKycDetails.getAadharCity());
                    dto.setPincode(lendingApplicationKycDetails.getAadharPinCode());
                    return new ApiResponse<>(dto);
                }
            }
            List<KycDoc> kycDocs = kycHandler.getKycDoc(lendingApplication.getMerchantId());
            for (KycDoc kycDoc : kycDocs) {
                if (KycDocType.POA.equals(kycDoc.getDocType())) {
                    AadhaarAddressResponseDTO dto = new AadhaarAddressResponseDTO();
                    dto.setAddress(kycDoc.getAddress());
                    dto.setCity(kycDoc.getCity());
                    dto.setPincode(kycDoc.getPincode());
                    dto.setState(kycDoc.getState());
                    dto.setName(kycDoc.getName());
                    dto.setDob(kycDoc.getDob());
                    dto.setGender(kycDoc.getGender());
                    dto.setAadharNumber(kycDoc.getDocIdentifier());
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
        ApiResponse apiResponse = getKfsDetails(lendingApplication.getId(), lendingApplication, merchant, null, ApplicationDocType.WELCOME_LETTER_DOC);
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
        s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, s3Bucket);
        String welcomeUrl = s3BucketHandler.getPreSignedPublicURL(fileName, s3Bucket);
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


    public ApiResponse<?> generateAuthorizationLetter(Long applicationId, LendingApplication lendingApplication, BasicDetailsDto merchant, boolean timeStamp, Date dateTime, String lang){
        ApiResponse apiResponse = getKfsDetails(applicationId, lendingApplication, merchant, lang, ApplicationDocType.AUTHORIZATION_LETTER_DOC);
        if(!apiResponse.success){
            log.info("Unable to get KFS details while creating Authorization doc for applicationId: {}", applicationId);
            return new ApiResponse<>(false,"Unable to retrieve KFS Details");
        }
        KfsDto kfsDto = (KfsDto)apiResponse.data;
        if(ObjectUtils.isEmpty(kfsDto.getLender())){
            log.info("Unable to get lender details while creating Authorization doc for applicationId: {}", applicationId);
            return new ApiResponse<>(false,"Unable to get lender while generating Authorization Letter");
        }
        try {
            Map<String, Object> data = new HashMap<>();
            data = getApplicationDocData(applicationId, kfsDto, merchant, timeStamp, ApplicationDocType.AUTHORIZATION_LETTER_DOC, dateTime, lendingApplication.getIp());
            String lender = kfsDto.getLender();
            Date penaltyDate = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss").parse(penalDate);
            Date penaltyDateTrillion = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss").parse(penalDateTrillion);
            String html = "";
            String filePath = "";

            String language = "";


            language = languageService.getVernacLanguage(lendingApplication.getLender(), lendingApplication.getLoanType(), lendingApplication.getMerchantId());

            if (ObjectUtils.isEmpty(language) || language.toUpperCase().contains("ENGLISH")) {
                language = "";
            }

            if (Objects.nonNull(lendingApplication.getAgreementAt()) && lendingApplication.getAgreementAt().before(penaltyDate)
                    && (lender.equalsIgnoreCase(Lender.LIQUILOANS_P2P.toString()) || lender.equalsIgnoreCase(Lender.LIQUILOANS_P2P_OF.toString()))) {
                filePath = "/templates/" + "AUTHORIZATION_LETTER_P2P_PC" + ".html";
            } else if (lender.equalsIgnoreCase(Lender.LIQUILOANS_P2P.toString()) || lender.equalsIgnoreCase(Lender.LIQUILOANS_P2P_OF.toString())) {
                filePath = "/templates/" + "AUTHORIZATION_LETTER_P2P_PC" + ".html";
            }  else if (lender.equalsIgnoreCase(Lender.ABFL.toString())) {
                filePath = "/templates/AUTHORIZATION_LETTER_NONP2P_ABFL"+ language +".html";
            } else if (Objects.nonNull(lendingApplication.getAgreementAt()) && lendingApplication.getAgreementAt().before(penaltyDateTrillion) && lender.equalsIgnoreCase(Lender.LIQUILOANS_NBFC.name())) {
                filePath = "/templates/" + "AUTHORIZATION_LETTER_TRILLIONS_PC" + ".html";
            } else if (lender.equalsIgnoreCase(Lender.LIQUILOANS_NBFC.name()) || lender.equalsIgnoreCase(Lender.TRILLIONLOANS.name())) {
                filePath = "/templates/AUTHORIZATION_LETTER_TRILLIONS_PC"+ language +".html";
            } else if (lender.equalsIgnoreCase(Lender.SMFG.name())) {
               filePath = "/templates/AUTHORIZATION_LETTER_SMFG" +".html";
            } else if (lender.equalsIgnoreCase(Lender.UGRO.name())) {
               filePath = "/templates/" + "UGRO/" + "AUTHORIZATION_LETTER.html";
           } else {
                filePath = "/templates/" + "AUTHORIZATION_LETTER_P2P_PC" + ".html";
            }
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
            log.error("Exception while generating Authorization html for applicationId : {}, {}, {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Unable to generate Authorization Letter");
        }
    }

    public void generateAuthorizationLetterDoc(LendingApplication lendingApplication, BasicDetailsDto merchant, LendingKfs lendingKfs, Date dateTime) throws Exception {
        String fileName = "";
        ApiResponse<?> apiResponse;
        apiResponse = generateAuthorizationLetter(lendingApplication.getId(), lendingApplication, merchant, true, dateTime, null);
        if (apiResponse.success) {
            String authorizationHtml = (String) apiResponse.data;
            fileName = AUTHORIZATION_S3_KEY_PREFIX + lendingApplication.getId() + ".pdf";
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(outStream,new WriterProperties().setCompressionLevel(kfsCompressionLevel));
            PdfDocument pdfDocument = new PdfDocument(writer);
            InputStream htmlStringInputStream = new ByteArrayInputStream(authorizationHtml.getBytes(StandardCharsets.UTF_8));
            HtmlConverter.convertToPdf(htmlStringInputStream, pdfDocument);
            ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
            s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, s3Bucket);
            String authorizationLetterUrl = s3BucketHandler.getPreSignedPublicURL(fileName, s3Bucket);
            String authorizationLetterShortUrl = apiGatewayService.getShortUrl(authorizationLetterUrl);
            if (ObjectUtils.isEmpty(authorizationLetterShortUrl) || authorizationLetterShortUrl.trim().isEmpty())
                throw new Exception("Unable to create short URL for Authorization doc link for : " + lendingApplication.getId());
            else {
                lendingKfs.setAuthorizationLetterDocFile(fileName);
                lendingKfs.setAuthorizationLetterDocUrl(authorizationLetterShortUrl);
            }
        }
        else{
            log.error("Unable to store Authorization Letter pdf doc for applicationId : {}", lendingApplication.getId());
            throw new Exception("Unable to generate Authorization Letter doc for applicationID" + lendingApplication.getId());
        }
    }
    public String constructShopAddress(LendingApplication lendingApplication) {
        return (ObjectUtils.isEmpty(lendingApplication.getShopNumber()) ? "" : lendingApplication.getShopNumber()) + "," +
                (ObjectUtils.isEmpty(lendingApplication.getStreetAddress()) ? "" : lendingApplication.getStreetAddress()) + "," +
                (ObjectUtils.isEmpty(lendingApplication.getLandmark()) ? "" : lendingApplication.getLandmark()) + "," +
                (ObjectUtils.isEmpty(lendingApplication.getCity()) ? "" : lendingApplication.getCity()) + "," +
                (ObjectUtils.isEmpty(lendingApplication.getState()) ? "" : lendingApplication.getState()) + "," +
                (ObjectUtils.isEmpty(lendingApplication.getPincode()) ? "" : lendingApplication.getPincode());

    }

    public ApiResponse<?> loanPurpose(Long applicationId, String loanPurpose) {
        try {
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(applicationId);
            if (Objects.isNull(lendingApplicationDetails)) {
                return new ApiResponse<>(false, "There is no application details for given  applicationId");
            }

            loanPurpose = commonUtil.loanPurposeMapping(loanPurpose);
            if (StringUtils.isBlank(loanPurpose)) {
                return new ApiResponse<>(false, "loan purpose is not a valid string");
            }
            lendingApplicationDetails.setLoanPurpose(loanPurpose);
            lendingApplicationDetailsDao.save(lendingApplicationDetails);
            return new ApiResponse<>(true, "loan purpose updated successfully!");
        } catch (Exception e) {
            log.error("Exception occurred while populating loan purpose for applicationId: {}, {}", applicationId, Arrays.toString(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "Something Went Wrong!");
    }

    private Double fetchLenderForeclosureAmount(LendingPaymentSchedule lendingPaymentSchedule) throws Exception {
        Double foreClosureAmount = 0D;
        foreClosureAmount = loanUtil.getForeClosureAmountForLender(lendingPaymentSchedule);
        if (foreClosureAmount <= 0) {
            log.error("previousAmount <= 0 for merchantId {}, loan : {}", lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId());
            throw new Exception("Unable to fetch foreclosure amount for parent loan");
        }
        return foreClosureAmount;
    }

    public ApiResponse<?> generateLenderKfs(LendingApplication lendingApplication, Boolean preSigned) {
        try {
            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
            if(ObjectUtils.isEmpty(lendingKfs) || ObjectUtils.isEmpty(lendingKfs.getKfsDocFile())) {
                DocType docType = DocType.KEY_FACT_STATEMENT;
                Boolean success = associationServiceUtil.invokeDocsGenerateService(lendingApplication.getLender(), lendingApplication, docType, preSigned);
                if(success) {
                    lendingKfs = lendingKfsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
                }
            }
            if (!ObjectUtils.isEmpty(lendingKfs)) {
                String fileName = preSigned ? lendingKfs.getKfsDocFile() : lendingKfs.getSignedKfsDocFile();
                if (!ObjectUtils.isEmpty(fileName)) {
                    String lenderKfsUrl = s3BucketHandler.getPreSignedPublicURL(fileName, s3Bucket);
                    return new ApiResponse<>(lenderKfsUrl);
                }
            }
            log.info("Unable to generate lender kfs document of {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
        } catch (Exception e) {
            log.info("Exception in generating lender kfs document of {} for applicationId {} {}", lendingApplication.getLender(), lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "Unable to generate KFS");
    }

    public ApiResponse<?> generateLenderSanctionCumLoanAgreement(LendingApplication lendingApplication, Boolean preSigned) {
        try {
            LendingKfs lendingKfs = getLendingKfs(lendingApplication, preSigned);
            if (!ObjectUtils.isEmpty(lendingKfs)) {
                String fileName = preSigned ? lendingKfs.getSanctionLoanAgreementDocFile() : lendingKfs.getSignedSanctionDocFile();
                if (!ObjectUtils.isEmpty(fileName)) {
                    String lenderSanctionUrl = s3BucketHandler.getPreSignedPublicURL(fileName, s3Bucket);
                    return new ApiResponse<>(lenderSanctionUrl);
                }
            }
            log.info("Unable to generate lender SanctionCumLoanAgreement document of {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
        } catch (Exception e) {
            log.info("Exception in generating lender SanctionCumLoanAgreement document of {} for applicationId {} {}", lendingApplication.getLender(), lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
        }
        return new ApiResponse<>(false, "Unable to generate lender Sanction Cum Loan Agreement");
    }
    public void generateMITCDoc(LendingApplication lendingApplication, BasicDetailsDto merchant, LendingKfs lendingKfs, Date dateTime) throws Exception {

        String fileName = "";
        ApiResponse<?> apiResponse;
        apiResponse = generateMITC(lendingApplication.getId(), lendingApplication, merchant, true, dateTime);
        if (apiResponse.success) {
            String mitcHtml = (String) apiResponse.data;

            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());

            fileName = lendingApplicationLenderDetails.getLeadId() + '_' + MITC_S3_KEY_PREFIX + new SimpleDateFormat("dd-MM-yyyy").format(dateTimeUtil.getCurrentDate()) + ".pdf";

            ByteArrayInputStream inStream = getLoanDocPdf(mitcHtml, ApplicationDocType.PAYU_MITC_DOC, lendingApplication, sanctionCompressionLevel);

            if (ObjectUtils.isEmpty(inStream)) {
                throw new Exception("Unable to generate MITC for applicationID" + lendingApplication.getId());
            }

            s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, s3Bucket);

            String mitcUrl = s3BucketHandler.getPreSignedPublicURL(fileName, s3Bucket);
            String mitcShortUrl = apiGatewayService.getShortUrl(mitcUrl);
            if (mitcShortUrl == null || mitcShortUrl.isEmpty() || mitcShortUrl.trim().isEmpty())
                throw new Exception("Unable to create short URL for MITC doc link for : " + lendingApplication.getId());
            else {
                lendingKfs.setMitcDocFile(fileName);
                lendingKfs.setMitcDocUrl(mitcShortUrl);
            }
        }
        else{
            log.error("Unable to store MITC pdf doc for applicationId : {}", lendingApplication.getId());
            throw new Exception("Unable to generate MITC for applicationID" + lendingApplication.getId());
        }
    }


    public ApiResponse<?> generateMITC(Long applicationId, LendingApplication lendingApplication, BasicDetailsDto merchant, boolean timeStamp, Date dateTime){
        ApiResponse apiResponse = getKfsDetails(applicationId, lendingApplication, merchant, null, ApplicationDocType.PAYU_MITC_DOC);
        if(!apiResponse.success){
            log.info("Unable to get MITC details while creating MITC doc for applicationId: {}", applicationId);
            return new ApiResponse<>(false,"Unable to retrieve MITC Details");
        }
        KfsDto kfsDto = (KfsDto)apiResponse.data;
        if(kfsDto.getLender() == null){
            log.info("Unable to get lender details while creating MITC doc for applicationId: {}", applicationId);
            return new ApiResponse<>(false,"Unable to get lender while generating MITC");
        }
        try {
            Map<String, Object> data = new HashMap<>();
            data = getApplicationDocData(applicationId, kfsDto, merchant, timeStamp, ApplicationDocType.PAYU_MITC_DOC, dateTime, lendingApplication.getIp());
            String lender = kfsDto.getLender();
            String html = "";
            String filePath = "";


            if(lender.equalsIgnoreCase(Lender.PAYU.name())) {
                filePath = "/templates/" + "MITC_PAYU" + ".html";
            }

            log.info("file path for MITC: {}", filePath);
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
            log.error("Exception while generating MITC html for applicationId : {}, {}, {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Unable to generate MITC");
        }
    }

    public void generateGTCDoc(LendingApplication lendingApplication, BasicDetailsDto merchant, LendingKfs lendingKfs, Date dateTime) throws Exception {

        String fileName = "";
        ApiResponse<?> apiResponse;
        apiResponse = generateGTC(lendingApplication.getId(), lendingApplication, merchant, true, dateTime);
        if (apiResponse.success) {
            String gtcHtml = (String) apiResponse.data;

            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());

            fileName = lendingApplicationLenderDetails.getLeadId() + '_' + GTC_S3_KEY_PREFIX + new SimpleDateFormat("dd-MM-yyyy").format(dateTimeUtil.getCurrentDate()) + ".pdf";

            ByteArrayInputStream inStream = getLoanDocPdf(gtcHtml, ApplicationDocType.PAYU_GTC_DOC, lendingApplication, sanctionCompressionLevel);

            if (ObjectUtils.isEmpty(inStream)) {
                throw new Exception("Unable to generate GTC for applicationID" + lendingApplication.getId());
            }

            s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, s3Bucket);
            String gtcUrl = s3BucketHandler.getPreSignedPublicURL(fileName, s3Bucket);
            String gtcShortUrl = apiGatewayService.getShortUrl(gtcUrl);
            if (gtcShortUrl == null || gtcShortUrl.isEmpty() || gtcShortUrl.trim().isEmpty())
                throw new Exception("Unable to create short URL for GTC doc link for : " + lendingApplication.getId());
            else {
                lendingKfs.setGtcDocFile(fileName);
                lendingKfs.setGtcDocUrl(gtcShortUrl);
            }
        }
        else{
            log.error("Unable to store GTC pdf doc for applicationId : {}", lendingApplication.getId());
            throw new Exception("Unable to generate GTC for applicationID" + lendingApplication.getId());
        }
    }

    public ApiResponse<?> generateGTC(Long applicationId, LendingApplication lendingApplication, BasicDetailsDto merchant, boolean timeStamp, Date dateTime){

        ApiResponse apiResponse = getKfsDetails(applicationId, lendingApplication, merchant, null, ApplicationDocType.PAYU_GTC_DOC);
        if(!apiResponse.success){
            log.info("Unable to get gtc details while creating GTC doc for applicationId: {}", applicationId);
            return new ApiResponse<>(false,"Unable to retrieve GTC Details");
        }
        KfsDto kfsDto = (KfsDto)apiResponse.data;
        if(kfsDto.getLender() == null){
            log.info("Unable to get lender details while creating GTC doc for applicationId: {}", applicationId);
            return new ApiResponse<>(false,"Unable to get lender while generating GTC");
        }
        try {
            Map<String, Object> data = new HashMap<>();
            data = getApplicationDocData(applicationId, kfsDto, merchant, timeStamp, ApplicationDocType.PAYU_GTC_DOC, dateTime, lendingApplication.getIp());
            String lender = kfsDto.getLender();
            String html = "";
            String filePath = "";


            if(lender.equalsIgnoreCase(Lender.PAYU.name())) {
                filePath = "/templates/" + "GTC_PAYU" + ".html";
            }

            log.info("file path for GTC: {}", filePath);
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
            log.error("Exception while generating GTC html for applicationId : {}, {}, {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Unable to generate GTC");
        }
    }

    public void generateLOADoc(LendingApplication lendingApplication, BasicDetailsDto merchant, LendingKfs lendingKfs, Date dateTime) throws Exception {

        String fileName = "";
        ApiResponse<?> apiResponse;
        apiResponse = generateLOA(lendingApplication.getId(), lendingApplication, merchant, true, dateTime);
        if (apiResponse.success) {
            String loaHtml = (String) apiResponse.data;
            if(Lender.PAYU.name().equalsIgnoreCase(lendingApplication.getLender())) {
                LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
                fileName = lendingApplicationLenderDetails.getLeadId() + '_' + LOA_S3_KEY_PREFIX + new SimpleDateFormat("dd-MM-yyyy").format(dateTimeUtil.getCurrentDate()) + ".pdf";
                ByteArrayInputStream inStream = getLoanDocPdf(loaHtml, ApplicationDocType.LOA_DOC, lendingApplication, sanctionCompressionLevel);
                if (ObjectUtils.isEmpty(inStream)) {
                    throw new Exception("Unable to generate LOA for applicationID" + lendingApplication.getId());
                }
                s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, s3Bucket);
            } else {
                fileName = LENDER_ADDITIONAL_S3_KEY_PREFIX + lendingApplication.getId() + ".pdf";
                
                /**
                 * New Library is being used to generate PDF for selected lenders
                 */
                if (newPdfGenerationMethodLenders.contains(lendingApplication.getLender())) {
                    log.info("Using new PDF generation method for LOA for applicationId: {} and lender: {}", lendingApplication.getId(), lendingApplication.getLender());
                    
                    String headerImageUrl = null;
                    String footerImageUrl = null;
                    boolean footerOnAllPages = false;

                    if (!getLenderLogo(lendingApplication.getLender(), ApplicationDocType.LOA_DOC).isEmpty()) {
                        headerImageUrl = getLenderLogo(lendingApplication.getLender(), ApplicationDocType.LOA_DOC);
                        if (Arrays.asList(Lender.ABFL.name(), Lender.PIRAMAL.name(), Lender.MUTHOOT.name(), Lender.CAPRI.name()).contains(lendingApplication.getLender())) {
                            log.info("Add header and footer in LOA doc for applicationId:" + lendingApplication.getId());
                            footerImageUrl = getLenderLogo(lendingApplication.getLender(),
                                    ApplicationDocType.getFooterMapping(Lender.valueOf(lendingApplication.getLender())));
                            footerOnAllPages = true;
                        } else {
                            log.info("Add header in LOA doc for applicationId:" + lendingApplication.getId());
                        }
                    }

                    PdfGenerationRequest.PdfGenerationRequestBuilder requestBuilder = PdfGenerationRequest.builder()
                            .html(loaHtml);

                    if (headerImageUrl != null) {
                        requestBuilder.headerImageUrl(headerImageUrl);
                    }
                    if (footerImageUrl != null) {
                        requestBuilder.footerImageUrl(footerImageUrl);
                        requestBuilder.footerOnAllPages(footerOnAllPages);
                    }

                    PdfGenerationRequest request = requestBuilder.build();
                    PdfGenerationResponse response = pdfGeneratorUtil.generatePdf(request);

                    if (response.getSuccess()) {
                        byte[] pdfByteArray = response.getPdfAsBytes();
                        ByteArrayInputStream inStream = new ByteArrayInputStream(pdfByteArray);
                        s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, s3Bucket);
                    } else {
                        log.error("Failed to generate PDF using new method for LOA for applicationId: {}", lendingApplication.getId());
                        throw new Exception("Unable to generate LOA PDF for applicationID" + lendingApplication.getId());
                    }
                } else {
                    // Fallback to existing PDF generation method
                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                    PdfWriter writer = new PdfWriter(outStream, new WriterProperties().setCompressionLevel(sanctionCompressionLevel));
                    PdfDocument pdfDocument = new PdfDocument(writer);

                    if (Collections.singletonList(Lender.UGRO.name()).contains(lendingKfs.getLender()) && !getLenderLogo(lendingApplication.getLender(), ApplicationDocType.LOA_DOC).isEmpty()) {
                        ImageData logoImageData = ImageDataFactory.create(getLenderLogo(lendingApplication.getLender(), ApplicationDocType.LOA_DOC));
                        Header headerHandler = new Header(logoImageData);
                        pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, headerHandler);
                    }
                    InputStream htmlStringInputStream = new ByteArrayInputStream(loaHtml.getBytes(StandardCharsets.UTF_8));
                    HtmlConverter.convertToPdf(htmlStringInputStream, pdfDocument);
                    ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
                    s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, s3Bucket);
                }
            }
            String loaUrl = s3BucketHandler.getPreSignedPublicURL(fileName, s3Bucket);
            String loaShortUrl = apiGatewayService.getShortUrl(loaUrl);
            if (loaShortUrl == null || loaShortUrl.isEmpty() || loaShortUrl.trim().isEmpty())
                throw new Exception("Unable to create short URL for loaHtml doc link for : " + lendingApplication.getId());
            else {
                lendingKfs.setLoaDocFile(fileName);
                lendingKfs.setLoaDocUrl(loaShortUrl);
            }
        }
        else{
            log.error("Unable to store LOA / Audit Trail pdf doc for applicationId : {}", lendingApplication.getId());
            throw new Exception("Unable to generate LOA for applicationID" + lendingApplication.getId());
        }
    }

    public ApiResponse<?> generateLOA(Long applicationId, LendingApplication lendingApplication, BasicDetailsDto merchant, boolean timeStamp, Date dateTime){
        if (Lender.SMFG.name().equalsIgnoreCase(lendingApplication.getLender())) {
            return generateAuditTrail(lendingApplication);
        }

        ApiResponse apiResponse = getKfsDetails(applicationId, lendingApplication, merchant, null, ApplicationDocType.LOA_DOC);
        if(!apiResponse.success){
            log.info("Unable to get LOA details while creating LOA doc for applicationId: {}", applicationId);
            return new ApiResponse<>(false,"Unable to retrieve LOA Details");
        }
        KfsDto kfsDto = (KfsDto)apiResponse.data;
        if(kfsDto.getLender() == null){
            log.info("Unable to get lender details while creating LOA doc for applicationId: {}", applicationId);
            return new ApiResponse<>(false,"Unable to get lender while generating LOA");
        }
        try {
            Map<String, Object> data = new HashMap<>();
            data = getApplicationDocData(applicationId, kfsDto, merchant, timeStamp, ApplicationDocType.LOA_DOC, dateTime, lendingApplication.getIp());

            data.put("merchant_id", lendingApplication.getMerchantId());

            String lender = kfsDto.getLender();
            String html = "";
            String filePath = "";


            if (lender.equalsIgnoreCase(Lender.PAYU.name())) {
                filePath = "/templates/" + "AUTHORIZATION_LETTER_PAYU" + ".html";
            } else if(lender.equalsIgnoreCase(Lender.UGRO.name())) {
                filePath = "/templates/" + "UGRO/" + "FACILITY_AGREEMENT.html";
            }

            log.info("file path for LOA: {}", filePath);
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
            log.error("Exception while generating LOA html for applicationId : {}, {}, {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Unable to generate LOA");
        }
    }

    public void generateApplicationFormDoc(LendingApplication lendingApplication, BasicDetailsDto merchant, LendingKfs lendingKfs, Date dateTime) throws Exception {

        String fileName = "";
        ApiResponse<?> apiResponse;
        apiResponse = generateApplicationForm(lendingApplication.getId(), lendingApplication, merchant, true, dateTime);
        if (apiResponse.success) {
            String applicationFormHtml = (String) apiResponse.data;

            if(Lender.PAYU.name().equalsIgnoreCase(lendingApplication.getLender())) {
                LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
                fileName = lendingApplicationLenderDetails.getLeadId() + '_' + AF_S3_KEY_PREFIX + new SimpleDateFormat("dd-MM-yyyy").format(dateTimeUtil.getCurrentDate()) + ".pdf";
                ByteArrayInputStream inStream = getLoanDocPdf(applicationFormHtml, ApplicationDocType.APPLICATION_FORM_DOC, lendingApplication, sanctionCompressionLevel);
                if (ObjectUtils.isEmpty(inStream)) {
                    throw new Exception("Unable to generate Application Form for applicationID" + lendingApplication.getId());
                }
                s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, s3Bucket);
            } else {
                fileName = AF_S3_KEY_PREFIX + lendingApplication.getId() + ".pdf";
                
                /**
                 * New Library is being used to generate PDF for selected lenders
                 */
                if (newPdfGenerationMethodLenders.contains(lendingApplication.getLender())) {
                    log.info("Using new PDF generation method for Application Form for applicationId: {} and lender: {}", lendingApplication.getId(), lendingApplication.getLender());
                    
                    String headerImageUrl = null;
                    String footerImageUrl = null;
                    boolean footerOnAllPages = false;

                    if (!getLenderLogo(lendingApplication.getLender(), ApplicationDocType.APPLICATION_FORM_DOC).isEmpty()) {
                        headerImageUrl = getLenderLogo(lendingApplication.getLender(), ApplicationDocType.APPLICATION_FORM_DOC);
                        if (Arrays.asList(Lender.ABFL.name(), Lender.PIRAMAL.name(), Lender.MUTHOOT.name(), Lender.CAPRI.name()).contains(lendingApplication.getLender())) {
                            log.info("Add header and footer in Application Form doc for applicationId:" + lendingApplication.getId());
                            footerImageUrl = getLenderLogo(lendingApplication.getLender(),
                                    ApplicationDocType.getFooterMapping(Lender.valueOf(lendingApplication.getLender())));
                            footerOnAllPages = true;
                        } else {
                            log.info("Add header in Application Form doc for applicationId:" + lendingApplication.getId());
                        }
                    }

                    PdfGenerationRequest.PdfGenerationRequestBuilder requestBuilder = PdfGenerationRequest.builder()
                            .html(applicationFormHtml);

                    if (headerImageUrl != null) {
                        requestBuilder.headerImageUrl(headerImageUrl);
                    }
                    if (footerImageUrl != null) {
                        requestBuilder.footerImageUrl(footerImageUrl);
                        requestBuilder.footerOnAllPages(footerOnAllPages);
                    }

                    PdfGenerationRequest request = requestBuilder.build();
                    PdfGenerationResponse response = pdfGeneratorUtil.generatePdf(request);

                    if (response.getSuccess()) {
                        byte[] pdfByteArray = response.getPdfAsBytes();
                        ByteArrayInputStream inStream = new ByteArrayInputStream(pdfByteArray);
                        s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, s3Bucket);
                    } else {
                        log.error("Failed to generate PDF using new method for Application Form for applicationId: {}", lendingApplication.getId());
                        throw new Exception("Unable to generate Application Form PDF for applicationID" + lendingApplication.getId());
                    }
                } else {
                    // Fallback to existing PDF generation method
                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                    PdfWriter writer = new PdfWriter(outStream, new WriterProperties().setCompressionLevel(sanctionCompressionLevel));
                    PdfDocument pdfDocument = new PdfDocument(writer);
                    if (Collections.singletonList(Lender.UGRO.name()).contains(lendingKfs.getLender()) && !getLenderLogo(lendingApplication.getLender(), ApplicationDocType.APPLICATION_FORM_DOC).isEmpty()) {
                        ImageData logoImageData = ImageDataFactory.create(getLenderLogo(lendingApplication.getLender(), ApplicationDocType.APPLICATION_FORM_DOC));
                        Header headerHandler = new Header(logoImageData);
                        pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE, headerHandler);
                    }
                    InputStream htmlStringInputStream = new ByteArrayInputStream(applicationFormHtml.getBytes(StandardCharsets.UTF_8));
                    HtmlConverter.convertToPdf(htmlStringInputStream, pdfDocument);
                    ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
                    s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, s3Bucket);
                }
            }

            String applicationFormUrl = s3BucketHandler.getPreSignedPublicURL(fileName, s3Bucket);
            String applicationFormShortUrl = apiGatewayService.getShortUrl(applicationFormUrl);
            if (applicationFormShortUrl == null || applicationFormShortUrl.isEmpty() || applicationFormShortUrl.trim().isEmpty())
                throw new Exception("Unable to create short URL for applicationFormHtml doc link for : " + lendingApplication.getId());
            else {
                lendingKfs.setApplicationFormDocFile(fileName);
                lendingKfs.setApplicationFormDocUrl(applicationFormShortUrl);
            }
        }
        else{
            log.error("Unable to store application form pdf doc for applicationId : {}", lendingApplication.getId());
            throw new Exception("Unable to generate Application form for applicationID" + lendingApplication.getId());
        }
    }

    public ApiResponse<?> generateApplicationForm(Long applicationId, LendingApplication lendingApplication, BasicDetailsDto merchant, boolean timeStamp, Date dateTime){

        ApiResponse apiResponse = getKfsDetails(applicationId, lendingApplication, merchant, null, ApplicationDocType.APPLICATION_FORM_DOC);
        if(!apiResponse.success){
            log.info("Unable to get application form details while creating application form doc for applicationId: {}", applicationId);
            return new ApiResponse<>(false,"Unable to retrieve application form Details");
        }
        KfsDto kfsDto = (KfsDto)apiResponse.data;
        if(kfsDto.getLender() == null){
            log.info("Unable to get lender details while creating application form doc for applicationId: {}", applicationId);
            return new ApiResponse<>(false,"Unable to get lender while generating application form");
        }
        try {
            Map<String, Object> data = new HashMap<>();
            data = getApplicationDocData(applicationId, kfsDto, merchant, timeStamp, ApplicationDocType.APPLICATION_FORM_DOC, dateTime, lendingApplication.getIp());
            String lender = kfsDto.getLender();
            String html = "";
            String filePath = "";

            CKycResponseDto cKycResponseDto = kycUtils.getKycData(lendingApplication.getMerchantId());
            data.put("borrower_selfie", cKycResponseDto.getSelfieString());
            data.put("business_city",lendingApplication.getCity());
            data.put("business_state",lendingApplication.getState());
            data.put("business_pincode",lendingApplication.getPincode());

            String shopAddress = (ObjectUtils.isEmpty(lendingApplication.getShopNumber()) ? "" : lendingApplication.getShopNumber()) + "," +
                    (ObjectUtils.isEmpty(lendingApplication.getStreetAddress()) ? "" : lendingApplication.getStreetAddress()) + "," +
                    (ObjectUtils.isEmpty(lendingApplication.getLandmark()) ? "" : lendingApplication.getLandmark());

            data.put("business_address", shopAddress);

            if (lender.equalsIgnoreCase(Lender.PAYU.name())) {
                filePath = "/templates/" + "APPLICATION_FORM_PAYU" + ".html";
            } else if (lender.equalsIgnoreCase(Lender.UGRO.name())) {
                filePath = "/templates/" + "UGRO/" + "APPLICATION_FORM.html";
            }

            log.info("file path for application form: {}", filePath);
            InputStream inputStream = this.getClass().getResourceAsStream(filePath);
            Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
            html = scanner.hasNext() ? scanner.next() : "";;
            for(Map.Entry<String,Object> entry : data.entrySet()) {
                String key = "{{" + entry.getKey() + "}}";
                String val = Objects.nonNull(entry.getValue()) ? entry.getValue().toString() : "";
                log.debug(key + " " + val);
                html = html.replace(key, val);
            }
            return new ApiResponse<>(html);
        } catch (Exception e) {
            log.error("Exception while generating application form html for applicationId : {}, {}, {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Unable to generate application form");
        }
    }

    public ApiResponse<?> assignLender(BasicDetailsDto merchatDetails, AssignLenderRequestDto requestDto) {
        if (ObjectUtils.isEmpty(requestDto) || ObjectUtils.isEmpty(requestDto.getApplicationId()) || ObjectUtils.isEmpty(requestDto.getLender())) {
            log.info("Request params are missing:{}", requestDto.toString());
            return new ApiResponse<>(false, "Request params are missing");
        }
        Map<String, Object> response = lenderAssignService.assignLender(requestDto.getApplicationId(), merchatDetails, LendingEnum.LENDER.valueOf(requestDto.getLender()));
        if (Objects.nonNull(response) && response.containsKey("success") && response.get("success").equals(true)){
            return new ApiResponse<>(true, response, "lender assigned successfully");
        }
        return new ApiResponse<>(false, "Something went wrong");
    }

    public ApiResponse<?> saveAddressDetails(BasicDetailsDto merchantDetails, SaveMerchantDetailsDto merchantDetailsDto){
        Long applicationId = null;
        try{
            log.info("save address request:{} for merchant:{}", merchantDetailsDto, merchantDetails.getId());
            AddressValidationDto addressValidationDto = getAddressValidationScore(merchantDetailsDto.getAddressDetails());
            String error = baseChecks(merchantDetails, merchantDetailsDto.getAddressDetails());
            if (error != null) return new ApiResponse<>(false, error);
            if (addressQltyScoreLessThanThreshold(addressValidationDto)) {
                log.info("address quality score less than 20");
                return new ApiResponse<>(ApplicationAddressValidation.builder().hasAValidAddress(false).build());
            }
            if (Objects.nonNull(merchantDetailsDto.getAddressDetails())) {
                LendingApplication lendingApplication = lendingApplicationDao.findByMerchantIdAndStatus(merchantDetails.getId(), "draft");
                if(ObjectUtils.isEmpty(lendingApplication)){
                    return new ApiResponse<>(false, "open application not found.");
                }
                applicationId = lendingApplication.getId();
                lendingApplication.setPincode(!StringUtils.isEmpty(merchantDetailsDto.getAddressDetails().getPincode())?Long.valueOf(merchantDetailsDto.getAddressDetails().getPincode()):lendingApplication.getPincode());
                lendingApplication.setArea(!StringUtils.isEmpty(merchantDetailsDto.getAddressDetails().getArea()) ? merchantDetailsDto.getAddressDetails().getArea() : lendingApplication.getArea());
                lendingApplication.setCity(!StringUtils.isEmpty(merchantDetailsDto.getAddressDetails().getCity()) ? merchantDetailsDto.getAddressDetails().getCity() : lendingApplication.getCity());
                lendingApplication.setState(!StringUtils.isEmpty(merchantDetailsDto.getAddressDetails().getState()) ? merchantDetailsDto.getAddressDetails().getState() : lendingApplication.getState());
                lendingApplication.setShopNumber(!StringUtils.isEmpty(merchantDetailsDto.getAddressDetails().getAddress1()) ?
                        merchantDetailsDto.getAddressDetails().getAddress1().substring(0, Math.min(merchantDetailsDto.getAddressDetails().getAddress1().length(), 98)) : lendingApplication.getShopNumber());
                lendingApplication.setStreetAddress(!StringUtils.isEmpty(merchantDetailsDto.getAddressDetails().getAddress2()) ? merchantDetailsDto.getAddressDetails().getAddress2() : lendingApplication.getStreetAddress());
                lendingApplication.setLandmark(!StringUtils.isEmpty(merchantDetailsDto.getAddressDetails().getLandmark()) ? merchantDetailsDto.getAddressDetails().getLandmark() : lendingApplication.getLandmark());
                lendingApplication.setBusinessName(merchantDetailsDto.getBusinessName());
                if(!ObjectUtils.isEmpty(merchantDetailsDto.getAdditionalDetails())){
                    lendingApplication.setEmail(merchantDetailsDto.getAdditionalDetails().getEmail());
                    lendingApplication.setAlternateMobile(merchantDetailsDto.getAdditionalDetails().getAlternateContact());
                }
                if(!ObjectUtils.isEmpty(merchantDetailsDto.getProfessionalDetails())){
                    saveGstDetails(lendingApplication,merchantDetailsDto.getProfessionalDetails());
                }
                saveAddressQltyDetails(lendingApplication,addressValidationDto);
                lendingApplicationDao.save(lendingApplication);
                LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
                lendingApplicationDetails.setCurrentAddressSameAsPermanentAddress(merchantDetailsDto.getCurrentAddressSameAsPermanentAddress());
                lendingApplicationDetailsDao.save(lendingApplicationDetails);
            }
        } catch (Exception ex) {

            log.error("Exception while saving address details for merchant:{}, {}, {}", merchantDetails.getId(), ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            return new ApiResponse<>(false, "Something went wrong");

        }
        return new ApiResponse<>(ApplicationAddressValidation.builder().hasAValidAddress(true).applicationId(applicationId).build());
    }


    private void rejectApplicationForIncorrectLender(LendingApplication lendingApplication) {
        log.info("Default lender is none for the applicationId: {}", lendingApplication.getId());
        lenderAssignService.saveEligibleLenderAudit(lendingApplication, "rejected",
                !ObjectUtils.isEmpty(lendingApplication.getStatus()) ? lendingApplication.getStatus() : "",
                "APP_STATUS");
        lendingApplication.setStatus("rejected");
        lendingApplicationDao.save(lendingApplication);
        evictCache(lendingApplication.getMerchantId());
    }


    public ApiResponse<?> generateAuditTrail(LendingApplication lendingApplication) {
        try {
            Map<String, Object> data = new HashMap<>();
            String html = "";
            String filePath = "";

            CKycResponseDto cKycResponseDto = kycUtils.getKycData(lendingApplication.getMerchantId());
            NameAndDobDetailsDto nameAndDobDetailsDto = kycUtils.getNameAndDobValues(cKycResponseDto, lendingApplication.getMerchantId());
            data.put("otp_date_with_timestamp", !ObjectUtils.isEmpty(lendingApplication.getAgreementAt()) ? lendingApplication.getAgreementAt().toString() : null);
            data.put("dob", nameAndDobDetailsDto.getDob());
            data.put("pan_dob_match_kyc", "YES");
            data.put("aadhar_linked_status_with_pan", "YES");
            data.put("kyc_name_match_score_pennydrop", "YES");
            data.put("selfie_match_score", cKycResponseDto.getSelfieAadhaarFaceMatchPer());
            data.put("liveliness_score", cKycResponseDto.getSelfieLivelinessScore());
            data.put("liveliness_check", "YES");

            if (Lender.SMFG.name().equalsIgnoreCase(lendingApplication.getLender())) {
                filePath = "/templates/" + "AUDIT_TRAIL_DOC_SMFG" + ".html";
            }

            log.info("file path for audit trail doc: {}", filePath);
            InputStream inputStream = this.getClass().getResourceAsStream(filePath);
            Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
            html = scanner.hasNext() ? scanner.next() : "";
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String key = "{{" + entry.getKey() + "}}";
                String val = Objects.nonNull(entry.getValue()) ? entry.getValue().toString() : "";
                log.info(key + " " + val);
                html = html.replace(key, val);
            }
            return new ApiResponse<>(html);
        } catch (Exception e) {
            log.error("Exception while generating audit trail document html for applicationId : {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            return new ApiResponse<>(false, "Unable to generate AuditTrailDoc form");
        }
    }

    private void generateAndAppendSignedDetails(LendingApplication lendingApplication, LendingKfs lendingKfs, BasicDetailsDto merchant) {
        try {
            Map<String, Object> data = new HashMap<>();
            String html = "";
            String filePath = "";
            Date dateTime = dateTimeUtil.getCurrentDate();

            data.put("borrower_name", merchant.getBeneficiaryName());
            data.put("mobile_number_for_otp", merchant.getMobile());
            data.put("platform", "BharatPe for Business");
            data.put("ip_address", lendingApplication.getIp());
            data.put("time_stamp", dateTime);
            data.put("loan_id", lendingApplication.getExternalLoanId());
            data.put("date", new SimpleDateFormat("dd-MM-yyyy").format(dateTime));
            ApiResponse aadharAddressResponse = getAadhaarAddress(merchant, lendingApplication.getId());
            if (aadharAddressResponse.isSuccess()) {
                AadhaarAddressResponseDTO aadhaarAddressResponseDTO = (AadhaarAddressResponseDTO) aadharAddressResponse.getData();
                if (!ObjectUtils.isEmpty(aadhaarAddressResponseDTO.getName())) {
                    data.put("borrower_name", aadhaarAddressResponseDTO.getName());
                }
            }
            filePath = "/templates/" + "SIGNED_DETAILS" + ".html";
            log.info("file path for signed details doc: {}", filePath);
            InputStream inputStream = this.getClass().getResourceAsStream(filePath);
            Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
            html = scanner.hasNext() ? scanner.next() : "";
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String key = "{{" + entry.getKey() + "}}";
                String val = Objects.nonNull(entry.getValue()) ? entry.getValue().toString() : "";
                html = html.replace(key, val);
            }

            ByteArrayInputStream inStream = null;
            
            /**
             * New Library is being used to generate PDF for selected lenders
             */
            if (newPdfGenerationMethodLenders.contains(lendingApplication.getLender())) {
                log.info("Starting PDF generation for Signed Details using PdfGeneratorUtilV2 - applicationId: {}, lender: {}", 
                        lendingApplication.getId(), lendingApplication.getLender());
                
                PdfGenerationRequest.PdfGenerationRequestBuilder requestBuilder = PdfGenerationRequest.builder()
                        .html(html);

                PdfGenerationRequest request = requestBuilder.build();
                log.info("Calling PdfGeneratorUtilV2.generatePdf for Signed Details - applicationId: {}", lendingApplication.getId());
                PdfGenerationResponse response = pdfGeneratorUtil.generatePdf(request);

                if (response.getSuccess()) {
                    byte[] pdfByteArray = response.getPdfAsBytes();
                    log.info("PDF generation successful for Signed Details - applicationId: {}, pdfSize: {} bytes", 
                            lendingApplication.getId(), pdfByteArray.length);
                    inStream = new ByteArrayInputStream(pdfByteArray);
                } else {
                    log.error("PDF generation failed for Signed Details - applicationId: {}, response: {}", 
                            lendingApplication.getId(), response);
                    throw new Exception("Unable to generate Signed Details PDF for applicationID" + lendingApplication.getId());
                }
            } else {
                // Fallback to existing PDF generation method
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                PdfWriter writer = new PdfWriter(outStream, new WriterProperties().setCompressionLevel(kfsCompressionLevel));
                PdfDocument pdfDocument = new PdfDocument(writer);
                InputStream htmlStringInputStream = new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
                HtmlConverter.convertToPdf(htmlStringInputStream, pdfDocument);
                inStream = new ByteArrayInputStream(outStream.toByteArray());
            }

            //Appending signed details in kfs doc
            URL url1 = new URL(docUploadUtils.getS3PresignedUrlFromKey(lendingKfs.getKfsDocFile()));
            URLConnection connection1 = url1.openConnection();
            InputStream kfsStream = connection1.getInputStream();
            String kfsUrl = docUploadUtils.mergeDocs(lendingApplication.getId(), kfsStream, inStream, lendingKfs.getKfsDocFile());
            String kfsShortUrl = apiGatewayService.getShortUrl(kfsUrl);
            if (kfsShortUrl == null || kfsShortUrl.isEmpty() || kfsShortUrl.trim().isEmpty())
                throw new Exception("Unable to create short URL for KFS doc link for : " + lendingApplication.getId());

            //Appending signed details in sanction doc
            // Create a new stream for the second merge operation
            ByteArrayInputStream inStream2 = null;
            if (newPdfGenerationMethodLenders.contains(lendingApplication.getLender())) {
                // For new PDF generation method, we need to regenerate the PDF
                PdfGenerationRequest.PdfGenerationRequestBuilder requestBuilder2 = PdfGenerationRequest.builder()
                        .html(html);
                PdfGenerationRequest request2 = requestBuilder2.build();
                PdfGenerationResponse response2 = pdfGeneratorUtil.generatePdf(request2);
                if (response2.getSuccess()) {
                    byte[] pdfByteArray2 = response2.getPdfAsBytes();
                    inStream2 = new ByteArrayInputStream(pdfByteArray2);
                } else {
                    throw new Exception("Unable to regenerate Signed Details PDF for applicationID" + lendingApplication.getId());
                }
            } else {
                // For fallback method, create new stream from the same data
                ByteArrayOutputStream outStream2 = new ByteArrayOutputStream();
                PdfWriter writer2 = new PdfWriter(outStream2, new WriterProperties().setCompressionLevel(kfsCompressionLevel));
                PdfDocument pdfDocument2 = new PdfDocument(writer2);
                InputStream htmlStringInputStream2 = new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
                HtmlConverter.convertToPdf(htmlStringInputStream2, pdfDocument2);
                inStream2 = new ByteArrayInputStream(outStream2.toByteArray());
            }
            URL url2 = new URL(docUploadUtils.getS3PresignedUrlFromKey(lendingKfs.getSanctionLoanAgreementDocFile()));
            URLConnection connection2 = url2.openConnection();
            InputStream sanctionStream = connection2.getInputStream();
            String sanctionUrl = docUploadUtils.mergeDocs(lendingApplication.getId(), sanctionStream, inStream2, lendingKfs.getSanctionLoanAgreementDocFile());
            String sanctionShortUrl = apiGatewayService.getShortUrl(sanctionUrl);
            if (sanctionShortUrl == null || sanctionShortUrl.isEmpty() || sanctionShortUrl.trim().isEmpty())
                throw new Exception("Unable to create short URL for sanction doc link for : " + lendingApplication.getId());
            lendingKfs.setSanctionLoanAgreementDocUrl(sanctionShortUrl);
            lendingKfs.setKfsDocUrl(kfsShortUrl);
        } catch (Exception e) {
            log.error("Exception while generating and appending details in agreementDocs for applicationId : {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }


    @Transactional
    public ResponseEntity<ApiResponse<?>> saveAddressAndBusinessName(BasicDetailsDto merchant, SaveMerchantDetailsDto saveMerchantDetailsDto) {
        log.info("Capture address & business name for merchant:{}", merchant.getId());
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByMerchantIdAndStatus(merchant.getId(), "draft");
            if (ObjectUtils.isEmpty(lendingApplication)) {
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new ApiResponse<>(false, "Open application not found."));
            }

            AddressValidationDto addressValidationDto = getAddressValidationScore(saveMerchantDetailsDto.getAddressDetails());
            if (addressQltyScoreLessThanThreshold(addressValidationDto)) {
                log.info("address quality score less than 20");
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new ApiResponse<>(false, "Address quality score less than 20"));
            }

            if (!ObjectUtils.isEmpty(saveMerchantDetailsDto.getBusinessName())) {
                boolean isUpdated = merchantService.updateMerchantBusinessName(merchant.getId(), saveMerchantDetailsDto.getBusinessName());
                if (isUpdated) {
                    LendingMerchantDetails lendingMerchantDetails = lendingMerchantDetailsDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
                    lendingMerchantDetails.setBusinessName(saveMerchantDetailsDto.getBusinessName());
                    lendingMerchantDetailsDao.save(lendingMerchantDetails);
                    log.info("Business name updated for application: {}", lendingApplication.getId());
                    lendingApplication.setBusinessName(saveMerchantDetailsDto.getBusinessName());
                }
            }

            if (Objects.nonNull(saveMerchantDetailsDto.getAddressDetails())) {
                ReqAddAddress reqAddAddress = createMerchantAddAddressRequest(merchant.getId(), saveMerchantDetailsDto.getAddressDetails());
                try {
                    merchantService.addAddress(merchant.getId(), reqAddAddress);
                }catch (Exception e) {
                    log.error("Error while saving address while calling addAddress for merchant: {}", merchant.getId());
                }

                AddressDetails addressDetails = saveMerchantDetailsDto.getAddressDetails();
                lendingApplication.setPincode(!StringUtils.isEmpty(addressDetails.getPincode()) ? Long.valueOf(addressDetails.getPincode()) : lendingApplication.getPincode());
                lendingApplication.setArea(!StringUtils.isEmpty(addressDetails.getArea()) ? addressDetails.getArea() : lendingApplication.getArea());
                lendingApplication.setCity(!StringUtils.isEmpty(addressDetails.getCity()) ? addressDetails.getCity() : lendingApplication.getCity());
                lendingApplication.setState(!StringUtils.isEmpty(addressDetails.getState()) ? addressDetails.getState() : lendingApplication.getState());
                lendingApplication.setShopNumber(!StringUtils.isEmpty(addressDetails.getAddress1()) ?
                        addressDetails.getAddress1().substring(0, Math.min(addressDetails.getAddress1().length(), 98)) : lendingApplication.getShopNumber());
                lendingApplication.setStreetAddress(!StringUtils.isEmpty(addressDetails.getAddress2()) ? addressDetails.getAddress2() : lendingApplication.getStreetAddress());
                lendingApplication.setLandmark(!StringUtils.isEmpty(addressDetails.getLandmark()) ? addressDetails.getLandmark() : lendingApplication.getLandmark());
            }

            lendingApplicationDao.save(lendingApplication);
            log.info("Shop address & business name updated for application: {}", lendingApplication.getId());
            return ResponseEntity.ok(new ApiResponse<>(true, "Address & Business name updated successfully")); // 200 OK
        } catch (Exception e) {
            log.error("Exception while capturing address & business name for merchant : {}, {}, {}", merchant.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Internal server error"));
        }
    }

    private ReqAddAddress createMerchantAddAddressRequest(Long merchantId, AddressDetails addressDetails) {
        log.info("Create add address request for merchant: {}", merchantId);
        ReqAddAddress reqAddAddress = new ReqAddAddress();
        reqAddAddress.setPincode(addressDetails.getPincode());
        reqAddAddress.setArea(addressDetails.getArea());
        reqAddAddress.setCity(addressDetails.getCity());
        reqAddAddress.setState(addressDetails.getState());
        reqAddAddress.setAddress1(addressDetails.getAddress1());
        reqAddAddress.setAddress2(addressDetails.getAddress2());
        reqAddAddress.setLandmark(addressDetails.getLandmark());
        reqAddAddress.setType("Shop/Office");
        log.info("Add address request for merchant:{} {}", merchantId, reqAddAddress);
        return reqAddAddress;
    }

    private boolean shouldSkipVkycVerificationForCS(LendingApplication lendingApplication, LendingApplicationVkycDetails vkycDetails) {
        if (ObjectUtils.isEmpty(lendingApplication) || ObjectUtils.isEmpty(vkycDetails)) {
            log.info("Lending application or vkyc details are empty, skipping vkyc verification for CS");
            return false;
        }

        return CREDITSAISON.name().equalsIgnoreCase(lendingApplication.getLender())
                && VKYC_SKIPPED.equals(vkycDetails.getStatus());
    }

    public ApiResponse<?> getKfsDetailsOnOfferPage(OfferPageKfsDetailsRequest request, BasicDetailsDto merchant) {

        Double loanAmount = request.getLoanAmount();
        Double processingFee =  request.getProcessingFee();

        Double disbursalAmount = loanAmount - processingFee;
        Double processingFeePercentage = processingFee * 100D / loanAmount;

        Double processingFeePercentageWithoutGst = Double.valueOf(String.format("%.4f", (processingFee * 100D / (100D + GST_PERCENTAGE)) / (loanAmount) * 100));

        Double processingFeeWithoutGst = Double.valueOf(String.format("%.2f", (loanAmount * processingFeePercentageWithoutGst) / 100D ));

        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchant.getId());

        String lenderCorporateName = "";
        String lenderBusinessAddress = "";
        String lenderContactName = "";
        String lenderContactEmail = "";
        String lenderContactNumber = "";
        String colenderCorporateName = "";
        String colenderBusinessAddress = "";
        String lenderGrievanceTime = "";

        if(request.getLender().equalsIgnoreCase(Lender.LIQUILOANS_P2P.toString()) || request.getLender().equalsIgnoreCase(Lender.LIQUILOANS_P2P_OF.toString())){
            lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_LIQUILOANS;
            lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_LIQUILOANS;
            lenderContactName = KfsConstants.LENDER_CONTACT_NAME_LIQUILOANS;
            lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_LIQUILOANS;
            lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_LIQUILOANS;
            lenderGrievanceTime = LENDER_GRIEVANCE_TIME_LIQUILOANS;
        }
        else if(request.getLender().equalsIgnoreCase(Lender.LIQUILOANS_NBFC.toString()) ||
                request.getLender().equalsIgnoreCase(Lender.TRILLIONLOANS.toString())){
            lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_LL_NBFC;
            lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_LL_NBFC;
            lenderContactName = KfsConstants.LENDER_CONTACT_NAME_LL_NBFC;
            lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_LL_NBFC;
            lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_LL_NBFC;
            lenderGrievanceTime = LENDER_GRIEVANCE_TIME_LL_NBFC;
        }
        else if(request.getLender().equalsIgnoreCase(Lender.LDC.toString())){
            lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_LDC;
            lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_LDC;
            lenderContactName = KfsConstants.LENDER_CONTACT_NAME_LDC;
            lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_LDC;
            lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_LDC;
            lenderGrievanceTime = LENDER_GRIEVANCE_TIME_LDC;
        }
        else if(request.getLender().equalsIgnoreCase(Lender.ABFL.toString())){
            lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_ABFL;
            lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_ABFL;
            lenderContactName = KfsConstants.LENDER_CONTACT_NAME_ABFL;
            lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_ABFL;
            lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_ABFL;
            lenderGrievanceTime = KfsConstants.LENDER_GRIEVANCE_TIME_ABFL;
        }
        else if(request.getLender().equalsIgnoreCase(Lender.PIRAMAL.toString())){
            lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_PIRAMAL;
            lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_PIRAMAL;
            lenderContactName = KfsConstants.LENDER_CONTACT_NAME_PIRAMAL;
            lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_PIRAMAL;
            lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_PIRAMAL;
        } else if(request.getLender().equalsIgnoreCase(Lender.CAPRI.name())) {
            lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_CAPRI;
            lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_CAPRI;
            lenderContactName = KfsConstants.LENDER_CONTACT_NAME_CAPRI;
            lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_CAPRI;
            lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_CAPRI;
        }
        else if(request.getLender().equalsIgnoreCase(Lender.MUTHOOT.toString())){
            lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_MUTHOOT;
            lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_MUTHOOT;
            lenderContactName = KfsConstants.LENDER_CONTACT_NAME_MUTHOOT;
            lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_MUTHOOT;
            lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_MUTHOOT;
        }
        else if(request.getLender().equalsIgnoreCase(Lender.PAYU.toString())){
            lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_PAYU;
            lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_PAYU;
            lenderContactName = KfsConstants.LENDER_CONTACT_NAME_PAYU;
            lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_PAYU;
            lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_PAYU;
        }
        else if(request.getLender().equalsIgnoreCase(Lender.CREDITSAISON.toString())){
            lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_CREDITSAISON;
            lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_CREDITSAISON;
            lenderContactName = KfsConstants.LENDER_CONTACT_NAME_CREDITSAISON;
            lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_CREDITSAISON;
            lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_CREDITSAISON;
        }
        else if (request.getLender().equalsIgnoreCase(Lender.SMFG.toString())) {
            lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_SMFG;
            lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_SMFG;
            lenderContactName = KfsConstants.LENDER_CONTACT_NAME_SMFG;
            lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_SMFG;
            lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_SMFG;
        } else if (request.getLender().equalsIgnoreCase(Lender.UGRO.toString())) {
            lenderCorporateName = ugroConfig.getCorporateName();
            lenderBusinessAddress = ugroConfig.getBusinessAddress();
            lenderContactName = ugroConfig.getContactName();
            lenderContactEmail = ugroConfig.getContactEmail();
            lenderContactNumber = ugroConfig.getContactNumber();
            lenderGrievanceTime = ugroConfig.getGrievanceTIme();
        } else if (request.getLender().equalsIgnoreCase(Lender.OXYZO.toString())) {
            lenderCorporateName = oxyzoConfig.getCorporateName();
            lenderBusinessAddress = oxyzoConfig.getBusinessAddress();
            lenderContactName = oxyzoConfig.getContactName();
            lenderContactEmail = oxyzoConfig.getContactEmail();
            lenderContactNumber = oxyzoConfig.getContactNumber();
            lenderGrievanceTime = oxyzoConfig.getGrievanceTIme();
        }
        else if(request.getLender().equalsIgnoreCase(Lender.MAMTA.toString())
                || request.getLender().equalsIgnoreCase(Lender.MAMTA0.toString())
                || request.getLender().equalsIgnoreCase(Lender.MAMTA1.toString())
                || request.getLender().equalsIgnoreCase(Lender.MAMTA2.toString()) ){
            lenderCorporateName = KfsConstants.LENDER_CORPORATE_NAME_MAMTA;
            lenderBusinessAddress = KfsConstants.LENDER_BUSINESS_ADDRESS_MAMTA;
            lenderContactName = KfsConstants.LENDER_CONTACT_NAME_MAMTA;
            lenderContactEmail = KfsConstants.LENDER_CONTACT_EMAIL_MAMTA;
            lenderContactNumber = KfsConstants.LENDER_CONTACT_NUMBER_MAMTA;
        }
        if(request.getLender().equalsIgnoreCase(Lender.MAMTA1.toString())){
            colenderCorporateName = KfsConstants.COLENDER_CORPORATE_NAME_MAMTA1;
            colenderBusinessAddress = KfsConstants.COLENDER_BUSINESS_ADDRESS_MAMTA1;
        } else if(request.getLender().equalsIgnoreCase(Lender.MAMTA2.toString())){
            colenderCorporateName = KfsConstants.COLENDER_CORPORATE_NAME_MAMTA2;
            colenderBusinessAddress = KfsConstants.COLENDER_BUSINESS_ADDRESS_MAMTA2;
        }

        KfsDetailsDto kfsDetailsDto = KfsDetailsDto.builder()
                .lenderCorporateName(lenderCorporateName)
                .lenderBusinessAddress(lenderBusinessAddress)
                .lenderContactName(lenderContactName)
                .lenderContactEmail(lenderContactEmail)
                .lenderContactNumber(lenderContactNumber)
                .colenderCorporateName(colenderCorporateName)
                .colenderBusinessAddress(colenderBusinessAddress)
                .lenderGrievanceTime(lenderGrievanceTime)
                .processingFeePercentage(processingFeePercentage)
                .processingFeePercentageWithoutGst(processingFeePercentageWithoutGst)
                .processingFeeWithoutGst(processingFeeWithoutGst)
                .coolingOffDays(COOLING_OFF_DAYS)
                .disbursalAmount(disbursalAmount)
                .annualTurnover(Optional.ofNullable(lendingRiskVariables.getSummaryTpv()).map(tpv -> tpv * 360).orElse(null))
                .build();

        return new ApiResponse<>(kfsDetailsDto);

    }


}
