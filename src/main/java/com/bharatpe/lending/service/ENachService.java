package com.bharatpe.lending.service;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.BharatPeEnachResponseDTO;
import com.bharatpe.lending.common.dto.LendingNachBankResponseDTO;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.constant.ErrorMessages;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.NachMandateRevokeRequestDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.entity.NachMandateRevokeRequest;
import com.bharatpe.lending.enums.*;
import com.bharatpe.lending.exception.CancelNachApiException;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.lendingplatform.lending.service.ENachRegister;
import com.bharatpe.lending.lendingplatform.lending.util.RolloutUtil;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactoryV2;
import com.bharatpe.lending.loanV3.revamp.enums.NachStatus;
import com.bharatpe.lending.loanV3.services.VKycService;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl.PiramalAdditionalDocUploadService;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.util.LoanUtil;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.validation.constraints.NotNull;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class ENachService {

    private final Logger logger = LoggerFactory.getLogger(ENachService.class);

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    VerifyOTPService verifyOTPService;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    EnachErrorHandingService enachErrorHandingService;

    @Autowired
    LendingPennydropDao lendingPennydropDao;

    @Autowired
    EnachHandler enachHandler;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    LendingBulkDisbursalDao lendingBulkDisbursalDao;

    @Autowired
    LendingBulkNachDao lendingBulkNachDao;

    @Autowired
    Environment env;

    @Autowired
    LendingCache lendingCache;

    @Autowired
    MerchantService merchantService;

    @Lazy
    @Autowired
    NbfcUtils nbfcUtils;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingAuditTrialDao lendingAuditTrialDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Lazy
    @Autowired
    PiramalAdditionalDocUploadService piramalAdditionalDocUploadService;

    ExecutorService executorService = Executors.newFixedThreadPool(50);
    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    private LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    private LoanDashboardService loanDashboardService;

    @Autowired
    LoanDetailsV3Service loanDetailsV3Service;

    @Autowired
    CleverTapEventService cleverTapEventService;

    @Value("${v3.easyloan.deeplink}")
    public String v3EasyloanDeeplink;

    @Value("${topup.nach.expiry.buffer:60}")
    private int topupNachExpiryBuffer;

    @Value("${skip.nach.disabled.lenders:ABFL}")
    private Set<String> skipNachDisabledLenders;

    @Autowired
    LenderAssociationStageFactoryV2 lenderAssociationStageFactoryV2;

    @Autowired
    FunnelService funnelService;

    @Autowired
    NachMandateRevokeRequestDao nachMandateRevokeRequestDao;

    @Autowired
    private RolloutUtil rolloutUtil;

    @Autowired
    private ENachRegister eNachRegister;

    @Autowired
    @Lazy
    VKycService vkycService;


    private final List<String> preFinalNachStatus = Arrays.asList(NachStatus.INPROCESS.name(), NachStatus.PENDING.name(),NachStatus.PENDING_VERIFICATION.name());
    private final List<String> nachCancellationInProgressStatus = Arrays.asList(NachStatus.CANCEL_INIT.name(), NachStatus.CANCEL_PENDING.name());
    private final List<String> nachCancellationSuccessStatus = Arrays.asList(NachStatus.CANCELLED.name(), NachStatus.REVOKED.name());
    private static final String NACH_APPROVED_STATUS = NachStatus.APPROVED.name();

    public ENachIntitiationResponseDTO eNachInitiate(BasicDetailsDto merchant, String token, String provider, String nachMode){
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
        if(lendingApplication == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Loan Application not found");
            logger.error("Unable to find loan application for Merchant - {}", merchant.getId());
            return responseDTO;
        }

        if(loanUtil.isEligibleForNachSkip(lendingApplication, lendingApplication.getLender())) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Nach can be skipped");
            logger.info("nach can be skipped for application:{}", lendingApplication.getId());
            return responseDTO;
        }

        if (provider != null && !"DIGIO".equalsIgnoreCase(provider)) {
            final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchant.getId());
            BankDetailsDto merchantBankDetail = null;
            if (bankDetailsDtoOptional.isPresent())
                merchantBankDetail = bankDetailsDtoOptional.get();
            if (fetchBankCode(merchantBankDetail.getIfsc().substring(0, 4), "BOTH") == null) {
                responseDTO.setResponse(false);
                responseDTO.setMessage("Bank not supported for enach");
                logger.error("Bank not supported for enach for Merchant - {}", merchant.getId());
                return responseDTO;
            }
        }

        funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
                FunnelEnums.StageId.NACH, FunnelEnums.StageEvent.INITIATED, nachMode);

        Double nachAmount = lendingApplication.getLoanAmount();
        if (EnachMode.UPI.name().equalsIgnoreCase(nachMode)) {
            nachAmount = lendingApplication.getLoanAmount() > 15000D ? 15000D : nachAmount;
        } else if (EnachMode.ADHAAR.name().equalsIgnoreCase(nachMode)) {
            nachAmount = lendingApplication.getLoanAmount() > 100000D ? 100000D : nachAmount;
        }

        String deep_link = apiGatewayService.getEnachProvider(token, lendingApplication.getLender(), merchant.getId());
        String providerName = deep_link.equals("bharatpe://enachdigio")?"DIGIO":"TECHPROCESS";
        return apiGatewayService.initiateEnach(new EnachInitiateRequestDTO(token, merchant.getId(), lendingApplication.getId(),
                String.valueOf(nachAmount), providerName, lendingApplication.getLender(), nachMode, lendingApplication.getTenureInMonths()),
                lendingApplication.getLoanType());
    }

    public ENachIntitiationResponseDTO submitEnach(BasicDetailsDto merchant, ENachSubmitRequestDTO requestDTO, String token)    {
        LendingApplication lendingApplication =
                lendingApplicationDao.findByIdAndMerchantId(requestDTO.getApplicationId(), merchant.getId());
        if(!ObjectUtils.isEmpty(lendingApplication) && "approved".equalsIgnoreCase(lendingApplication.getStatus())) {
            if (loanUtil.reNachEnabledMerchants().contains(merchant.getId())) {
                return submitEnachForRenachMerchants(merchant, requestDTO, token);
            }
        }

        String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + merchant.getId();
        logger.info("deleting cached key of loan details where nach is done for merchant: {}",merchant.getId());
        if(Objects.nonNull(lendingCache.get(loanDetailsCacheKey))) {
            lendingCache.delete(loanDetailsCacheKey);
        }
        LendingApplicationDetails lendingApplicationDetails = null;
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        responseDTO.setData(new ENachIntitiationResponseDTO.Data());
        responseDTO.getData().setDeep_link("bharatpe://dynamic?key=loan");
        BharatPeEnachResponseDTO bharatPeEnach = enachHandler.findByMerchantIdAndApplicationId(merchant.getId(), requestDTO.getApplicationId());
        if (bharatPeEnach == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Enach not initiated");
            return responseDTO;
        }
        LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchant.getId());

        requestDTO.setLender(lendingApplication.getLender());

        ENachIntitiationResponseDTO eNachIntitiationResponseDTO = apiGatewayService.submitEnach(requestDTO, token, merchant.getId(), bharatPeEnach.getEnachProvider(), "LENDING", lendingApplication.getLoanType());

        if (!ObjectUtils.isEmpty(eNachIntitiationResponseDTO) && !ObjectUtils.isEmpty(eNachIntitiationResponseDTO.getData()) && requestDTO.getStatus()) {
            logger.info("Enach success for merchant:{}", merchant.getId());
            if(Objects.nonNull(lendingApplication) && !StringUtils.isEmpty(lendingApplication.getCkycId())) {
                responseDTO.getData().setDeep_link("bharatpe://dynamic?key=easy-loans&wroute=enachSuccess");
                if("v2".equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
                    String deeplink = v3EasyloanDeeplink + "&applicationId=" + lendingApplication.getId();
                    responseDTO.getData().setDeep_link(deeplink);
                }
            } else {
                responseDTO.getData().setDeep_link("bharatpe://dynamic?key=loan&wroute=enachSuccess");
            }
            // Update Lending Application for ENACH
            if (lendingApplication == null) {
                responseDTO.setResponse(false);
                responseDTO.setMessage("Loan Application not found");
                return responseDTO;
            }
            lendingApplication.setNachType("ENACH");
//            lendingApplication.setNachLender("BHARATPE");
            if (EnachMode.ADHAAR.name().equalsIgnoreCase(bharatPeEnach.getMode())) {
                lendingApplication.setNachStatus("PENDING_VERIFICATION");
                funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
                        FunnelEnums.StageId.NACH, FunnelEnums.StageEvent.PENDING_APPLICATION, bharatPeEnach.getMode());
            } else {
                lendingApplication.setNachStatus("APPROVED");
                funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
                        FunnelEnums.StageId.NACH, FunnelEnums.StageEvent.SUCCESS, bharatPeEnach.getMode());
            }

            loanDashboardService.deleteLoanDashboardCache(merchant.getId());
            if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())){
                loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.getId(), LendingViewStates.AGREEMENT_PAGE);
            }
            else loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.getId(), vkycService.getLenderVkycPageOrDefault(LendingViewStates.APPLICATION_STATUS_PAGE, lendingApplication.getMerchantId(), lendingApplication.getLender(), LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())));
            logger.info("nach status for {}, {}, {}", lendingApplication.getId(), lendingApplication.getMerchantId(), lendingApplication.getNachStatus());
            lendingApplication.setNachReferenceNumber(bharatPeEnach.getProviderUmrn());
//            lendingApplicationDao.save(lendingApplication);

            lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
            if(!ObjectUtils.isEmpty(lendingApplicationDetails)){
                lendingApplicationDetails.setLeadAcceptanceTime(new Date());
                if("RESIGN_RENACH".equalsIgnoreCase(lendingApplication.getLmsStage())){
                    logger.info("Auditing lead acceptance timestamp update for :{}", lendingApplication.getId());
                    LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
                    lendingAuditTrial.setApplicationId(lendingApplicationDetails.getApplicationId());
                    lendingAuditTrial.setLoanId(lendingApplication.getExternalLoanId());
                    lendingAuditTrial.setMerchantId(lendingApplication.getMerchantId());
                    lendingAuditTrial.setType("UPDATE_LEAD_ACCEPTANCE_TIME");
                    logger.info("lendingAuditTrial -> {}", lendingAuditTrial);
                    lendingAuditTrialDao.save(lendingAuditTrial);
                }
            }

            if("RESIGN_RENACH".equalsIgnoreCase(lendingApplication.getLmsStage())){
                lendingApplication.setLmsStage("PENDING_DISBURSAL");
                lendingApplication.setStatus(ApplicationStatus.APPROVED.name().toLowerCase());

                LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
                lendingAuditTrial.setApplicationId(lendingApplication.getId());
                lendingAuditTrial.setMerchantId(lendingApplication.getMerchantId());
                lendingAuditTrial.setLoanId(lendingApplication.getExternalLoanId());
                lendingAuditTrial.setUserId(Long.parseLong("0"));
                lendingAuditTrial.setOldStatus(ApplicationStatus.PENDING_VERIFICATION.name().toLowerCase());
                lendingAuditTrial.setNewStatus(ApplicationStatus.APPROVED.name().toLowerCase());
                lendingAuditTrial.setType("APP_STATUS");
                LendingAuditTrial lendingAuditTrial1 = new LendingAuditTrial();
                lendingAuditTrial1.setApplicationId(lendingApplication.getId());
                lendingAuditTrial1.setMerchantId(lendingApplication.getMerchantId());
                lendingAuditTrial1.setLoanId(lendingApplication.getExternalLoanId());
                lendingAuditTrial1.setUserId(Long.parseLong("0"));
                lendingAuditTrial1.setOldStatus("RESIGN_RENACH");
                lendingAuditTrial1.setNewStatus("PENDING_DISBURSAL");
                lendingAuditTrial1.setType("LMS_STAGE");
                lendingAuditTrialDao.save(lendingAuditTrial);
                lendingAuditTrialDao.save(lendingAuditTrial1);
            }

            if("NTB".equalsIgnoreCase(lendingApplication.getLoanType()) || "NTB_SMS_1".equalsIgnoreCase(lendingApplication.getLoanType())){
                apiGatewayService.fosAttribution(merchant.getId(),"NTB_LOAN","CLOSED");
            }
            if (Arrays.asList(Lender.ABFL.name(), Lender.PIRAMAL.name(), Lender.USFB.name(), Lender.TRILLIONLOANS.name(), Lender.MUTHOOT.name(), Lender.CAPRI.name(), Lender.PAYU.name(), Lender.CREDITSAISON.name(), Lender.SMFG.name(), Lender.UGRO.name(), Lender.OXYZO.name()).contains(lendingApplication.getLender())) {
                    if (rolloutUtil.lendingPlatformNbfcFlowApplicable(lendingApplication.getMerchantId())) {
                    eNachRegister.pushDetailsToLender(lendingApplication);
                } else if (!"APPROVED".equalsIgnoreCase(lendingApplication.getNachStatus()) && Arrays.asList(Lender.TRILLIONLOANS.name(), Lender.MUTHOOT.name(), Lender.CAPRI.name(), Lender.PAYU.name(), Lender.CREDITSAISON.name(), Lender.SMFG.name(), Lender.UGRO.name(), Lender.OXYZO.name()).contains(lendingApplication.getLender())) {
                    logger.info("skipping invoke sanction workflow for application {} as nach status is {} ", lendingApplication.getId(), lendingApplication.getNachStatus());
                } else if(!LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())){
                    LendingApplicationLenderDetails lendingApplicationLenderDetails =
                            lendingApplicationLenderDetailsDao.
                                    findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc
                                            (lendingApplication.getId(), Status.ACTIVE.name());
                    if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails) &&
                            LenderAssociationStages.ASSC_COMPLETED.name()
                                    .equalsIgnoreCase(lendingApplicationLenderDetails.getStage())) {
                        Boolean autoInvokeNextStage;
                        if (Arrays.asList(Lender.ABFL.name(), Lender.PIRAMAL.name()).contains(lendingApplication.getLender())) {
                            autoInvokeNextStage = LenderAssociationStageFactory.autoInvokeNextStage(Lender.valueOf(lendingApplication.getLender()), LenderAssociationStages.ASSC_COMPLETED);
                        } else {
                            autoInvokeNextStage = LenderAssociationStageFactoryV2.autoInvokeNextStage(Lender.valueOf(lendingApplication.getLender()), LenderAssociationStages.ASSC_COMPLETED);
                        }
                        nbfcUtils.pushApplicationToNextStage
                                (lendingApplication.getId(), lendingApplication.getLender(),
                                        LenderAssociationStages.ASSC_COMPLETED.name(),
                                        autoInvokeNextStage
                                );
                        logger.info("invoked sanction workflow for application {}", lendingApplication.getId());
                    }
                }
            }


//            LendingPennydrop lendingPennydrop = lendingPennydropDao.isFailed(merchant.getId(), lendingApplication.getId());
//            if (lendingPennydrop == null) {
//                apiGatewayService.updateApplicationPriority(merchant.getId(), lendingApplication.getId());
//            }
            if (!LoanType.TOPUP.name().equals(lendingApplication.getLoanType())) {
                logger.info("pushing into post check kafka after nach success for applicationId: {}",lendingApplication.getId());
                verifyOTPService.sendDetailsForContactsVerification(merchant.getId(), lendingApplication.getId());
            }
        } else {
            logger.info("Enach failed for merchant:{}", merchant.getId());
            funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
                    FunnelEnums.StageId.NACH, FunnelEnums.StageEvent.FAILED, bharatPeEnach.getMode());
        }

        if(!ObjectUtils.isEmpty(eNachIntitiationResponseDTO) && !ObjectUtils.isEmpty(eNachIntitiationResponseDTO.getData())){
            if(!ObjectUtils.isEmpty(eNachIntitiationResponseDTO.getData().getLender())){
                lendingApplication.setNachLender(eNachIntitiationResponseDTO.getData().getLender());
            } else{
                lendingApplication.setNachLender("BHARATPE");
            }
        }
        responseDTO.setMessage(ObjectUtils.isEmpty(eNachIntitiationResponseDTO) ? null : eNachIntitiationResponseDTO.getMessage());

        if(!ObjectUtils.isEmpty(lendingApplicationDetails))lendingApplicationDetailsDao.save(lendingApplicationDetails);
        lendingApplicationDao.save(lendingApplication);

        HashMap<String, String> cleverTapEvtData = new HashMap<String, String>() {{
            put("loanAmount", lendingApplication.getLoanAmount().toString());
            put("beneficiaryName", lendingApplication.getMerchantName());
            put("businessName", lendingApplication.getBusinessName());
            put("loanType", lendingApplication.getLoanType());
        }};
        executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.LOAN_NACH_COMPLETED_BE.name(), cleverTapEvtData, merchant.getMid()));

        if(Objects.nonNull(requestDTO)){
            checkForApplicationRejection(merchant, requestDTO, lendingApplication);
        }
        if (!requestDTO.getStatus() && lendingApplication != null && !StringUtils.isEmpty(lendingApplication.getCkycId())) {
            if("v2".equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
                String deeplink = v3EasyloanDeeplink + "&applicationId=" + lendingApplication.getId();
                responseDTO.getData().setDeep_link(deeplink);
            }
            else responseDTO.getData().setDeep_link(env.getProperty("new.loan.deeplink"));
        }
        return responseDTO;
    }

    public ENachIntitiationResponseDTO submitEnachForRenachMerchants (BasicDetailsDto merchant, ENachSubmitRequestDTO requestDTO, String token) {

        logger.info("submitEnachForRenachMerchants for merchantId : {} ENachSubmitRequestDTO : {}", merchant.getId(), requestDTO);

        String loanDetailsCacheKey = "LENDING_LOAN_DETAILS_" + merchant.getId();
        logger.info("deleting cached key of loan details where nach is done for merchant: {}",merchant.getId());
        if(Objects.nonNull(lendingCache.get(loanDetailsCacheKey))) {
            lendingCache.delete(loanDetailsCacheKey);
        }
        LendingApplicationDetails lendingApplicationDetails = null;
        ENachIntitiationResponseDTO responseDTO = new ENachIntitiationResponseDTO();
        responseDTO.setData(new ENachIntitiationResponseDTO.Data());
        responseDTO.getData().setDeep_link("bharatpe://dynamic?key=loan");
        BharatPeEnachResponseDTO bharatPeEnach = enachHandler.findByMerchantIdAndApplicationId(merchant.getId(), requestDTO.getApplicationId());
        LendingApplication lendingApplication =
          lendingApplicationDao.findByIdAndMerchantId(requestDTO.getApplicationId(), merchant.getId());
        if (bharatPeEnach == null) {
            responseDTO.setResponse(false);
            responseDTO.setMessage("Enach not initiated");
            return responseDTO;
        }
        LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchant.getId());
        if (requestDTO.getStatus()) {
            logger.info("Enach success for merchant:{}", merchant.getId());
            if(Objects.nonNull(lendingApplication) && !StringUtils.isEmpty(lendingApplication.getCkycId())) {
                responseDTO.getData().setDeep_link("bharatpe://dynamic?key=easy-loans&wroute=enachSuccess");
                if("v2".equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
                    String deeplink = v3EasyloanDeeplink + "&applicationId=" + lendingApplication.getId();
                    responseDTO.getData().setDeep_link(deeplink);
                }
            } else {
                responseDTO.getData().setDeep_link("bharatpe://dynamic?key=loan&wroute=enachSuccess");
            }
            // Update Lending Application for ENACH
            if (lendingApplication == null) {
                responseDTO.setResponse(false);
                responseDTO.setMessage("Loan Application not found");
                return responseDTO;
            }
            lendingApplication.setNachType("ENACH");

            if (EnachMode.ADHAAR.name().equalsIgnoreCase(bharatPeEnach.getMode())) {
                lendingApplication.setNachStatus("PENDING_VERIFICATION");
                funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
                        FunnelEnums.StageId.NACH, FunnelEnums.StageEvent.PENDING_APPLICATION, bharatPeEnach.getMode());
            } else {
                lendingApplication.setNachStatus("APPROVED");
                funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
                        FunnelEnums.StageId.NACH, FunnelEnums.StageEvent.SUCCESS, bharatPeEnach.getMode());
            }

            loanDashboardService.deleteLoanDashboardCache(merchant.getId());

            logger.info("nach status for {}, {}, {}", lendingApplication.getId(), lendingApplication.getMerchantId(), lendingApplication.getNachStatus());

            lendingApplication.setNachReferenceNumber(bharatPeEnach.getProviderUmrn());
        } else {
            funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
                    FunnelEnums.StageId.NACH, FunnelEnums.StageEvent.FAILED, bharatPeEnach.getMode());
        }

        requestDTO.setLender(lendingApplication.getLender());
        ENachIntitiationResponseDTO eNachIntitiationResponseDTO = apiGatewayService.submitEnach(requestDTO, token, merchant.getId(), bharatPeEnach.getEnachProvider(), "LENDING", lendingApplication.getLoanType());

        if(!ObjectUtils.isEmpty(eNachIntitiationResponseDTO) && !ObjectUtils.isEmpty(eNachIntitiationResponseDTO.getData())){
            if(!ObjectUtils.isEmpty(eNachIntitiationResponseDTO.getData().getLender())){
                lendingApplication.setNachLender(eNachIntitiationResponseDTO.getData().getLender());
            } else{
                lendingApplication.setNachLender("BHARATPE");
            }
        }
        responseDTO.setMessage(ObjectUtils.isEmpty(eNachIntitiationResponseDTO) ? null : eNachIntitiationResponseDTO.getMessage());

        lendingApplicationDao.save(lendingApplication);

        HashMap<String, String> cleverTapEvtData = new HashMap<String, String>() {{
            put("loanAmount", lendingApplication.getLoanAmount().toString());
            put("beneficiaryName", lendingApplication.getMerchantName());
            put("businessName", lendingApplication.getBusinessName());
            put("loanType", lendingApplication.getLoanType());
        }};
        executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.LOAN_NACH_COMPLETED_BE.name(), cleverTapEvtData, merchant.getMid()));

//        if(Objects.nonNull(requestDTO)){
//            checkForApplicationRejection(merchant, requestDTO, lendingApplication);
//        }


        if (!requestDTO.getStatus() && lendingApplication != null && !StringUtils.isEmpty(lendingApplication.getCkycId())) {
            if("v2".equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
                String deeplink = v3EasyloanDeeplink + "&applicationId=" + lendingApplication.getId();
                responseDTO.getData().setDeep_link(deeplink);
            }
            else responseDTO.getData().setDeep_link(env.getProperty("new.loan.deeplink"));
        }
        return responseDTO;
    }

    public String checkForApplicationRejection(BasicDetailsDto merchant, ENachSubmitRequestDTO response, LendingApplication lendingApplication){
        logger.info("check for Application need to reject on Enach failure for Merchant - {}", merchant.getId());

        try{
            if(!response.getStatus()){
                switch (response.getStatusMessage()){
                    case ErrorMessages.MANDATE_REGISTRATION_FAILED:
                    case ErrorMessages.EMPTY_RESPONSE:
                    case ErrorMessages.AUTHENTICATION_FAILED:
                    case ErrorMessages.INVALID_CREDENTIAL:
                    case ErrorMessages.REJECT_CONFIRMATION:
                    case ErrorMessages.MERCHANT_SIGNATURE_VALIDATION_FAILED:
                    case ErrorMessages.MENDATE_VERIFICATION_FAILED:
                    case ErrorMessages.NO_RESPONSE_ON_MANDATE:
                    case ErrorMessages.MULTIPLE_ERROR:
                    case ErrorMessages.CHECKSUM_VALIDATION_FAILED:
                    case ErrorMessages.NO_RESPONSE_FROM_CUSTOMER:
                    case ErrorMessages.MENDATE_NOT_REGISTERED:
                    case ErrorMessages.DUPICATE_BANK_MANDATE_ID:
                    case ErrorMessages.CARD_VALIDATION_FAILED:
                    case ErrorMessages.DUPLICATE_BANK_MSGID:
                    case ErrorMessages.TECH_ERROR_OR_ISSUE_AT_BANK:
                    case ErrorMessages.REGS_FAILED:
                    case ErrorMessages.BANK_ERROR_XML:
                    case ErrorMessages.TXN_CNACALLED_AT_BANK:
                    case ErrorMessages.INVALIED_CREDENTIAL:
                        return enachErrorHandingService.retryPage(response, lendingApplication);
                    case ErrorMessages.MENDATE_NOT_REGISTERED_REQ_BALANC:
                    case ErrorMessages.BRANCH_KYC_NOT_COMPLETED:
                    case ErrorMessages.NO_ACCONT:
                    case ErrorMessages.INORRECT_MERCHANT_DEBITOR:
                    case ErrorMessages.MENDATE_DIFF_FROM_CBS:
                    case ErrorMessages.ACCOUNT_INOPERATIVE:
                    case ErrorMessages.MD_REGS_NOT_ALLOWED:
                        return enachErrorHandingService.sendForCpvOrReject(response, lendingApplication);
                    case ErrorMessages.AC_NOT_REGISTERED:
                    case ErrorMessages.AC_NUMBER_NOT_REGISTERED_WITH_NET_BANKING:
                    case ErrorMessages.VIEW_RIGHTS_ACCOUNT:
                        return enachErrorHandingService.sendForCpvOrRejectOrDebitScreenOnBankSupport(merchant, response, lendingApplication);
                    default:
                        return null;
                }
            }
        }catch(Exception ex){
            logger.error("Error Orrocured while Checking Application Rejection , Error - {}", ex);
            return null;
        }

        return "Success";
    }

    //changing skip status to true
    public ResponseDTO setEnachSkipStatus(BasicDetailsDto merchant){
        LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
        if (lendingApplication == null) {
            return new ResponseDTO(false, "Loan Application not found", null,null);
        }
        final boolean skipNach = enachHandler.skipNach(lendingApplication.getId(), merchant.getId());
//        LendingPennydrop lendingPennydrop = lendingPennydropDao.isFailed(merchant.getId(), lendingApplication.getId());
//        if (lendingPennydrop == null) {
//            apiGatewayService.updateApplicationPriority(merchant.getId(), lendingApplication.getId());
//        }
        return new ResponseDTO(skipNach, null, null, null);
    }

    // check if bank is supported or not
    public String fetchBankCode(String ifscCode, String mode){
        LendingNachBankResponseDTO lendingNachBank = enachHandler.findByIfsc(ifscCode);
        return lendingNachBank != null ? lendingNachBank.getBankCode() : null;
    }

    public CommonResponse cancelEnach(BasicDetailsDto merchant) {
        MerchantNachDetailsResponseDTO bpEnach = enachHandler.findSuccessEnach(merchant.getId());
        if (bpEnach == null) {
            logger.info("Enach not found for merchant:{}", merchant.getId());
        } else {
            apiGatewayService.cancelEnach(merchant.getId());
        }
        return new CommonResponse(true, "success");
    }

    public CommonResponse cancelEnach(Long merchantId, Long applicationId) {
        MerchantNachDetailsResponseDTO bpEnach = enachHandler.findSuccessEnach(merchantId, applicationId);
        if (bpEnach == null) {
            logger.info("Enach not found for merchant:{}", merchantId);
        } else {
            if(!apiGatewayService.cancelEnach(merchantId, applicationId)){
                return new CommonResponse(false, "Nach Cancellation Failed");
            }
        }
        return new CommonResponse(true, "success");
    }

    public ApiResponse<List<NachDetail>> getNachDetails(BasicDetailsDto merchantDetails){
        List<MerchantNachDetailsResponseDTO> merchantNachList = enachHandler.findByMerchantId(merchantDetails.getId());
        if(CollectionUtils.isEmpty(merchantNachList)){
            return null;
        }
        List<NachMandateRevokeRequest> mandateRevokeRequests = nachMandateRevokeRequestDao.findByMerchantId(merchantDetails.getId());
        Set<Long> mandateRevokeRaiseApplications = mandateRevokeRequests.stream()
                        .map(NachMandateRevokeRequest::getApplicationId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        merchantNachList = merchantNachList.stream()
                .filter(nach-> Objects.nonNull(nach.getStatus()))
                .map(nach-> {
                    nach.setStatus(nach.getStatus().toUpperCase());
                    return nach;
                })
                .collect(Collectors.toList());

        List<MerchantNachDetailsResponseDTO> nachDetailList = merchantNachList.stream()
                .filter(nach -> ! preFinalNachStatus.contains(nach.getStatus()))
                .filter(nach->
                        NACH_APPROVED_STATUS.equals(nach.getStatus())
                                || mandateRevokeRaiseApplications.contains(nach.getOwnerId()))
                .collect(Collectors.toList());

        List<Long> nonExpirableNachApplicationIds = getNonExpirableNachApplicationId(merchantDetails, merchantNachList);
        logger.info("non expirable nach application_ids are: {}", nonExpirableNachApplicationIds);
        if( ! CollectionUtils.isEmpty(nonExpirableNachApplicationIds)){
            nachDetailList = nachDetailList.stream()
                    .filter(nach-> ! nonExpirableNachApplicationIds.contains(nach.getOwnerId()))
                    .collect(Collectors.toList());
        }

        List<NachDetail> finalNachDetail = new ArrayList<>();
        for(MerchantNachDetailsResponseDTO nachDetailsResponseDTO: nachDetailList){
            NachDetail.NachDetailBuilder nachDetailBuilder = NachDetail.builder()
                    .id(nachDetailsResponseDTO.getId())
                    .applicationId(nachDetailsResponseDTO.getOwnerId())
                    .mandateId(nachDetailsResponseDTO.getMandateId())
                    .bankCode(nachDetailsResponseDTO.getBankCode())
                    .bankName(nachDetailsResponseDTO.getBankName())
                    .branchName(nachDetailsResponseDTO.getBranchName())
                    .accountNumber(nachDetailsResponseDTO.getAccountNumber())
                    .ifscCode(nachDetailsResponseDTO.getIfscCode())
                    .beneficiaryName(nachDetailsResponseDTO.getBeneficiaryName())
                    .nachStatus(nachDetailsResponseDTO.getNachStatus())
                    .status(nachDetailsResponseDTO.getStatus())
                    .startDate(nachDetailsResponseDTO.getStartDate())
                    .nachLender(nachDetailsResponseDTO.getNachLender());
            nachDetailBuilder.revokeStatus(getNachRevokeStatus(nachDetailsResponseDTO.getStatus()));
            finalNachDetail.add(nachDetailBuilder.build());
        }
        return new ApiResponse<>(finalNachDetail);
    }

    private NachRevokeStatus getNachRevokeStatus(@NotNull String status) {
        if(nachCancellationInProgressStatus.contains(status.toUpperCase())){
            return NachRevokeStatus.INIT;
        }
        if(nachCancellationSuccessStatus.contains(status.toUpperCase())){
            return NachRevokeStatus.SUCCESS;
        }
        return NachRevokeStatus.PENDING;
    }

    private List<Long> getNonExpirableNachApplicationId(BasicDetailsDto merchantDetails, List<MerchantNachDetailsResponseDTO> nachDetailList) {
        LendingPaymentSchedule activeLendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantDetails.getId(), Collections.singletonList(LoanStatus.ACTIVE.name()));
        List<MerchantNachDetailsResponseDTO> approvedNachList = nachDetailList.stream()
                .filter(nach-> NACH_APPROVED_STATUS.equals(nach.getStatus()))
                .collect(Collectors.toList());
        if(activeLendingPaymentSchedule!=null){
            logger.info("Found active application for merchant:{}", merchantDetails.getId());
            LendingApplication activeApplication = activeLendingPaymentSchedule.getLoanApplication();
            List<Long> usedNach = getUsedNachApplicationIds(merchantDetails, approvedNachList, activeApplication);
            LendingApplication latestApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchantDetails.getId());
            if(LoanType.TOPUP.name().equalsIgnoreCase(latestApplication.getLoanType())){
                if(latestApplication.getStatus()==null
                    || ApplicationStatus.DELETED.name().equalsIgnoreCase(latestApplication.getStatus())
                        || ApplicationStatus.REJECTED.name().equalsIgnoreCase(latestApplication.getStatus())){
                    return usedNach;
                }
                logger.info("latest application is of type topup with non expirable status");
                Long usedForTopup = getUsedNach(approvedNachList, latestApplication.getId(), latestApplication.getLender());
                if(usedForTopup!=null){
                    usedNach.add(usedForTopup);
                }
            }
            return usedNach;

        }else {
            LendingApplication latestApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchantDetails.getId());
            logger.info("latest application details for merchant: {} is: {}", merchantDetails.getId(), latestApplication);
            if(checkIfApplicationIsInMandateExpirableStatus(latestApplication)){
                return new ArrayList<>();
            }
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(latestApplication.getId());
            logger.info("latest application payment schedule details for merchant: {} is: {}",
                    merchantDetails.getId(), lendingPaymentSchedule==null ? null : lendingPaymentSchedule.toString());
            if(lendingPaymentSchedule !=null && "CLOSED".equalsIgnoreCase(lendingPaymentSchedule.getStatus())){
                return new ArrayList<>();
            }
            return getUsedNachApplicationIds(merchantDetails, approvedNachList, latestApplication);
        }
    }

    private List<Long> getUsedNachApplicationIds(
            BasicDetailsDto merchantDetails, List<MerchantNachDetailsResponseDTO> approvedNachList, LendingApplication loanApplication) {
        List<Long> finalApplicationIds = new ArrayList<>();
        finalApplicationIds.add(loanApplication.getId());
        String nachLender = loanUtil.enachServiceLenderMapper(loanApplication.getLender());
        Long usedNachApplicationId = getUsedNach(approvedNachList, loanApplication.getId(), nachLender);
        if(usedNachApplicationId != null){
            finalApplicationIds.add(usedNachApplicationId);
        }
        if(LoanType.TOPUP.name().equalsIgnoreCase(loanApplication.getLoanType())
                && ! isDisbursementTimeInCancellableState(loanApplication.getDisburseTimestamp())){
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findByApplicationId(loanApplication.getId());
            logger.info("lending application detail entry for latest application for merchant: {} is: {}",
                    merchantDetails.getId(), lendingApplicationDetails==null ? null : lendingApplicationDetails.toString());
            if(lendingApplicationDetails!=null && lendingApplicationDetails.getPrevAppId()!=null){
                // using same nachLender bec, topup is given on same lender only
                usedNachApplicationId = getUsedNach(approvedNachList, lendingApplicationDetails.getPrevAppId(), nachLender);
                if(usedNachApplicationId != null){
                    finalApplicationIds.add(usedNachApplicationId);
                }
            }
        }
        return finalApplicationIds;
    }
    public ApiResponse<?> cancelNach(BasicDetailsDto merchantDetails, Long applicationId) throws CancelNachApiException {
        Long merchantId = merchantDetails.getId();
        LendingApplication requestedApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchantId);
        if (requestedApplication == null) {
            logger.warn("Application not available for id: {}, and merchant_id: {}", applicationId, merchantId);
            throw new CancelNachApiException("Application not available");
        }
        logger.info("Application details: {}", requestedApplication);

        NachMandateRevokeRequest nachRevokeRequest = nachMandateRevokeRequestDao.findTop1ByMerchantIdAndApplicationIdOrderByIdDesc(merchantId, applicationId);
        logger.info("nach mandate revoke entry: {}", nachRevokeRequest);

        if (nachRevokeRequest != null) {
            throw new CancelNachApiException("Application NACH cancellation already raised");
        }

        MerchantNachDetailsResponseDTO nachDetails = enachHandler.findSuccessEnach(merchantId, applicationId);
        if (nachDetails == null) {
            logger.info("No approved nach exists for the given application id");
            throw new CancelNachApiException("No approved NACH exists for the given application");
        }
        logger.info("nach mandate revoke entry: {}", nachDetails);

        LendingPaymentSchedule activeSchedule = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId, Collections.singletonList("ACTIVE"));
        if (activeSchedule != null) {
            return handleActivePaymentSchedule(merchantDetails, merchantId, applicationId, requestedApplication, activeSchedule);
        }

        return handleNoActivePaymentSchedule(merchantDetails, merchantId, applicationId, requestedApplication);
    }

    private ApiResponse<?> handleActivePaymentSchedule(BasicDetailsDto merchantDetails, Long merchantId, Long applicationId,
                                                       LendingApplication requestedApplication, LendingPaymentSchedule activeSchedule)
            throws CancelNachApiException{
        LendingApplication activeApplication = activeSchedule.getLoanApplication();
        logger.info("Active application exists: {}", activeApplication);

        if (activeApplication.getId().equals(applicationId)) {
            throw new CancelNachApiException("Application is active");
        }

        if (!loanUtil.enachServiceLenderMapper(requestedApplication.getLender())
                .equalsIgnoreCase(loanUtil.enachServiceLenderMapper(activeApplication.getLender()))) {
            return startNachCancellation(merchantId, applicationId, merchantDetails);
        }

        MerchantNachDetailsResponseDTO usedNach = getPossibleUsedNach(merchantId, activeApplication.getId(), activeApplication.getLender());
        if(usedNach.getOwnerId().equals(applicationId)){
            throw new CancelNachApiException("NACH is used in active loan");
        }
        return handleNachCancellation(merchantDetails, merchantId, applicationId, activeApplication);
    }

    private ApiResponse<?> handleNoActivePaymentSchedule(BasicDetailsDto merchantDetails, Long merchantId, Long applicationId,
                                                         LendingApplication requestedApplication) throws CancelNachApiException{
        LendingApplication latestApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);

        if (!loanUtil.enachServiceLenderMapper(requestedApplication.getLender())
                .equals(loanUtil.enachServiceLenderMapper(latestApplication.getLender()))) {
            return startNachCancellation(merchantId, applicationId, merchantDetails);
        }

        if (checkIfApplicationIsInMandateExpirableStatus(latestApplication)) {
            return startNachCancellation(merchantId, applicationId, merchantDetails);
        }

        LendingPaymentSchedule latestAppSchedule = lendingPaymentScheduleDao.findByApplicationId(latestApplication.getId());
        if (latestAppSchedule != null && "CLOSED".equalsIgnoreCase(latestAppSchedule.getStatus())) {
            return startNachCancellation(merchantId, applicationId, merchantDetails);
        }

        MerchantNachDetailsResponseDTO usedNach = getPossibleUsedNach(merchantId, latestApplication.getId(), latestApplication.getLender());
        if(usedNach.getOwnerId().equals(applicationId)){
            throw new CancelNachApiException("NACH may be used in latest loan");
        }
        return (usedNach.getOwnerId().equals(applicationId))
                ? handleNachCancellation(merchantDetails, merchantId, applicationId, latestApplication)
                : new ApiResponse<>(false, "NACH may be used in latest loan");
    }

    private ApiResponse<?> handleNachCancellation(BasicDetailsDto merchantDetails, Long merchantId,
                                                  Long applicationId, LendingApplication lendingApplication) throws CancelNachApiException {
        if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())){
            return handleTopCaseForNachCancellation(merchantId, applicationId, lendingApplication, merchantDetails);
        }
        return startNachCancellation(merchantId, applicationId, merchantDetails);
    }

    private ApiResponse<?> startNachCancellation(Long merchantId, Long applicationId, BasicDetailsDto merchantDetails) throws CancelNachApiException{
        logger.info("starting nach cancellation for merchant_id: {}, and application_id: {}",merchantId, applicationId);
        boolean response = apiGatewayService.cancelEnach(merchantId, applicationId);
        if(response){
            NachMandateRevokeRequest nachMandateRevokeRequest = new NachMandateRevokeRequest(
                    merchantId, applicationId,  merchantDetails.getMobile(), merchantDetails.getBeneficiaryName(), "INIT");
            HashMap<String, Object> cleverTapEvtData = new HashMap<>();
            cleverTapEvtData.put("applicationId", applicationId);
            nachMandateRevokeRequestDao.save(nachMandateRevokeRequest);
            executorService.execute(() -> cleverTapEventService.sendClevertapEvent(
                    CleverTapEvents.NACH_CANCELLATION_INIT.name(), cleverTapEvtData, merchantId.toString()));
            return new ApiResponse<>(true, "cancel nach request submitted");
        }
        throw new CancelNachApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Got exception from nach");
    }


    private ApiResponse<?> handleTopCaseForNachCancellation(Long merchantId, Long applicationIdToCancel,
                                                            LendingApplication lendingApplication,
                                                            BasicDetailsDto merchantDetails) throws CancelNachApiException{
        logger.info("handling topup case for nach cancellation for merchant_id: {} and application_id: {}", merchantId, applicationIdToCancel);
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findByApplicationId(lendingApplication.getId());
        Long parentLoanApplicationId = lendingApplicationDetails.getPrevAppId();
        // not fetching lender info bec it should be same as the topup.
        MerchantNachDetailsResponseDTO parentLoanNachDetailsResponseDTO = getPossibleUsedNach(merchantId, parentLoanApplicationId, lendingApplication.getLender());
        if(parentLoanNachDetailsResponseDTO!= null
                && ! parentLoanNachDetailsResponseDTO.getOwnerId().equals(applicationIdToCancel)
                && isDisbursementTimeInCancellableState(lendingApplication.getDisburseTimestamp())){
            return startNachCancellation(merchantId, applicationIdToCancel, merchantDetails);
        }
        throw new CancelNachApiException("nach is used in topup loan of latest application");
    }

    private MerchantNachDetailsResponseDTO getPossibleUsedNach(Long merchantId, Long applicationId, String lender){
        MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = enachHandler.findSuccessEnach(merchantId, applicationId);
        if(merchantNachDetailsResponseDTO!=null){
            return merchantNachDetailsResponseDTO;
        }
        String nachLender = loanUtil.enachServiceLenderMapper(lender);
        return enachHandler.findByMerchantIdAndLender(merchantId, nachLender);
    }

    private boolean isDisbursementTimeInCancellableState(Date disbursalTimestamp) {
        if(disbursalTimestamp==null){
            return false;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -topupNachExpiryBuffer); // Get the date 60 days ago
        Date lastInEligibleDate = calendar.getTime();
        return disbursalTimestamp.before(lastInEligibleDate);
    }

    private Long getUsedNach(@NotNull List<MerchantNachDetailsResponseDTO> nachDetailList, Long id, String lender) {
         Optional<MerchantNachDetailsResponseDTO> nachByApplicationId = nachDetailList.stream()
                 .filter(nachDetail -> nachDetail.getOwnerId().equals(id))
                 .findFirst();
        if(nachByApplicationId.isPresent()){
            return nachByApplicationId.get().getOwnerId();
        }
        Optional<MerchantNachDetailsResponseDTO> nachByLender = nachDetailList.stream()
                .filter(nachDetail -> nachDetail.getNachLender().equals(lender))
                .findFirst();
        return nachByLender.map(MerchantNachDetailsResponseDTO::getOwnerId).orElse(null);
    }

    private boolean checkIfApplicationIsInMandateExpirableStatus(LendingApplication lendingApplication) {
        if(skipNachDisabledLenders.contains(lendingApplication.getLender())){
            return ApplicationStatus.DELETED.name().equalsIgnoreCase(lendingApplication.getStatus())
                    || ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getStatus())
                    || ApplicationStatus.DRAFT.name().equalsIgnoreCase(lendingApplication.getStatus());
        }
        return lendingApplication.getStatus()==null
                || ApplicationStatus.DELETED.name().equalsIgnoreCase(lendingApplication.getStatus())
                || ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getStatus());
    }

    public CommonResponse captureMandateRevokeRequest(BasicDetailsDto merchantDetails){
        logger.info("Mandate revoke request for merchant:{}", merchantDetails.getId());
        try{
            if (ObjectUtils.isEmpty(merchantDetails)){
                logger.info("merchant details not found");
                return new CommonResponse(false, "merchant not found");
            }

            NachMandateRevokeRequest nachMandateRevokeRequest = nachMandateRevokeRequestDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchantDetails.getId(), "PENDING");
            if (!ObjectUtils.isEmpty(nachMandateRevokeRequest)){
                logger.info("Request already exists in pending state for merchant:{}", merchantDetails.getId());
                return new CommonResponse(false, "Request already exists in pending state");
            }
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantDetails.getId(),  Collections.singletonList("ACTIVE"));
            if (Objects.nonNull(lendingPaymentSchedule)){
                logger.info("Active loan exists for merchant {} for application {}", merchantDetails.getId(), lendingPaymentSchedule.getApplicationId());
                return new CommonResponse(false, "active loan exists for merchant");
            }
            nachMandateRevokeRequest = new NachMandateRevokeRequest(merchantDetails.getId(), merchantDetails.getMobile(), merchantDetails.getBeneficiaryName(), "PENDING");
            if (!ObjectUtils.isEmpty(nachMandateRevokeRequestDao.save(nachMandateRevokeRequest))){
                return new CommonResponse(true, "data captured successfully");
            }
        }catch(Exception ex){
            logger.error("EXception occurred while capturing mandate revoke request for merchant:{} {}", merchantDetails.getId(), ex.getMessage());
        }
        return new CommonResponse(false, "Something went wrong");
    }

    public void uploadBulkEnach(EnachUploadRequestDTO enachUploadRequestDTO) {
        Long fileId = enachUploadRequestDTO.getFileId();
        Long userId = enachUploadRequestDTO.getUserId();
        LendingBulkDisbursal lendingBulkDisbursal = lendingBulkDisbursalDao.findByIdAndType(fileId,"BULK_NACH");
        if(lendingBulkDisbursal != null){
            try {
                String fileName = lendingBulkDisbursal.getFileName();
                logger.info("Getting file : {} from s3", fileName);
                InputStream lenderFile = s3BucketHandler.getObject(fileName, "loan-document");
                BufferedReader lenderFileReader = new BufferedReader(new InputStreamReader(lenderFile));
                String readLine = lenderFileReader.readLine();
                readLine = lenderFileReader.readLine();
                while (readLine != null) {
                    logger.info("readline: {}",readLine);
                    String[] arr = readLine.split(",");
                    Long merchantId = Long.valueOf(arr[1]);
                    Long applicationId = Long.valueOf(arr[2]);
                    String loanId = arr[3];
                    Double debitAmount = Double.valueOf(arr[4]);
                    String referenceNo = arr[5];
                    executorService.execute(() -> {
                        insertNachData(merchantId,applicationId,debitAmount,loanId,userId,referenceNo);
                    });
                    readLine = lenderFileReader.readLine();
                }
            }
            catch (Exception exception) {
                logger.error("Error occured while uploading nach file : {}",exception);
            }
        }
    }

    public void insertNachData(Long merchantId,Long applicationId,Double debitAmount,String loanId,Long userId,String referenceNo){
        logger.info("Creating bulk nach entry for merchantId: {},applicationId : {}",merchantId,applicationId);
        SimpleDateFormat formatter = new SimpleDateFormat("yy/MM/dd");
        BulkNach bulkNach = new BulkNach();
        bulkNach.setMerchantId(merchantId);
        bulkNach.setApplicationId(applicationId);
        bulkNach.setLoanId(loanId);
        bulkNach.setAmount(debitAmount);
        bulkNach.setRefNumber(referenceNo);
        bulkNach.setStatus("STARTED");
        bulkNach.setDebitDate(getCurrenntDate());
        bulkNach.setUserId(userId);
        lendingBulkNachDao.save(bulkNach);
    }

    private Date getCurrenntDate() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        return cal.getTime();
    }
}
