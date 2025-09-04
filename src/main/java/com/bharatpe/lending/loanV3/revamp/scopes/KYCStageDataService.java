package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationKycDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingResubmitReasonCountDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationKycDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingResubmitReasonCount;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.KycConstants;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.KycDoc;
import com.bharatpe.lending.dto.KycDocResponseDTO;
import com.bharatpe.lending.enums.*;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.InitiateKycDTO;
import com.bharatpe.lending.loanV3.enums.KycMode;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.dto.KYCStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LendingStateDTO;
import com.bharatpe.lending.loanV3.revamp.dto.ScopeDataArgs;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.enums.LoanDetailExceptionEnum;
import com.bharatpe.lending.loanV3.revamp.exception.LoanDetailsException;
import com.bharatpe.lending.loanV3.revamp.services.LendingApplicationServiceV3;
import com.bharatpe.lending.loanV3.revamp.services.LoanDetailsV3Service;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.service.CleverTapEventService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.bharatpe.lending.enums.KycRanking.P2MS;
import static com.bharatpe.lending.enums.KycRanking.P2PM;

@Component
@Slf4j
public class KYCStageDataService implements IStageDataService<KYCStateDTO> {

    @Autowired
    CleverTapEventService cleverTapEventService;

    @Autowired
    FunnelService funnelService;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Autowired
    LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;

    @Autowired
    Environment env;

    @Value("${kyc.revalidation.deeplink}")
    String kycRevalidationDeeplink;

    @Value("${kyc.deeplink}")
    String kycDeepLink;

    @Autowired
    private LendingApplicationServiceV3 lendingApplicationServiceV3;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    private LoanDetailsV3Service loanDetailsV3Service;

    @Autowired
    LendingResubmitReasonCountDao lendingResubmitReasonCountDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Lazy
    @Autowired
    KycUtils kycUtils;

    @Autowired
    LoanUtil loanUtil;

    @Value("${enable.p2pm.flag:false}")
    boolean p2pmEnabled;

    @Override
    public LendingStateDTO<KYCStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        return fetchScopedData(scopeDataArgs);
    }

    @Override
    @Transactional
    public LendingStateDTO<KYCStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        KYCStateDTO initiateKycResponse = new KYCStateDTO();
        LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(),scopeDataArgs.getMerchant().getId());
        initiateKycResponse.setMerchantId(scopeDataArgs.getMerchant().getId());
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
            throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
        }
        initiateKycResponse.setLender(lendingApplication.getLender());
        boolean isResubmittedApplication = false;
        initiateKycResponse.setLenderKycPipe(kycUtils.isEligibleForLenderKyc(lendingApplication.getLender(), lendingApplication.getMerchantId(), LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())));
        LendingResubmitReasonCount lendingResubmitReasonCount = null;
        if(ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(lendingApplication.getStatus())){
            lendingResubmitReasonCount = lendingResubmitReasonCountDao.findTopByApplicationIdAndResubmitReasonAndResubmitDoneOrderByIdDesc(lendingApplication.getId(), "INCORRECT_SELFIE", Boolean.FALSE);
            if(!ObjectUtils.isEmpty(lendingResubmitReasonCount)) {
                isResubmittedApplication = true;
                initiateKycResponse.setSelfieResumit(true);
            }
        }
        if (!ApplicationStatus.DRAFT.name().equalsIgnoreCase(lendingApplication.getStatus()) && !isResubmittedApplication) {
            log.info("draft application not found for {}", scopeDataArgs.getMerchant().getId());
            throw new LoanDetailsException(LoanDetailExceptionEnum.DRAFT_APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.DRAFT_APPLICATION_NOT_FOUND.getErrorMessage());
        }
        if(easyLoanUtil.isDummyMerchant(lendingApplication.getMerchantId())) {
            initiateKycResponse.setDummyMerchant(true);
            initiateKycResponse.setKycStatus(KycStatus.APPROVED);
            initiateKycResponse.setShowKycPage(false);
            log.info("Returning from kyc stage for merchant Id:{}, kyc skipped for dummy merchant", scopeDataArgs.getMerchant().getId());
            return new LendingStateDTO<>(initiateKycResponse, LendingViewStates.ENACH_PAGE, LendingViewStates.KYC_PAGE);
        }
        try {
            if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())) {
                initiateKycResponse.setTopup(true);
                log.info("setting topup {} for {} {}", initiateKycResponse.isTopup(), scopeDataArgs.getMerchant().getId(), lendingApplication.getId());
                if (!Arrays.asList(Lender.LIQUILOANS_NBFC.name(), Lender.ABFL.name(), Lender.TRILLIONLOANS.name(), Lender.PIRAMAL.name()).contains(lendingApplication.getLender())) {
                    log.info("Sending kyc approved for merchant Id:{} for lender:{}", scopeDataArgs.getMerchant().getId(), lendingApplication.getLender());
                    if (!KycStatus.APPROVED.name().equalsIgnoreCase(lendingApplication.getCkycStatus())) {
                        log.info("Updating ckyc status for merchant Id:{} and lender:{}", scopeDataArgs.getMerchant().getId(), lendingApplication.getLender());
                        lendingApplication.setCkycStatus(KycStatus.APPROVED.name());
                        lendingApplication.setCkycDate(new Date());
                        lendingApplicationDao.save(lendingApplication);
                    }
                    initiateKycResponse.setKycStatus(KycStatus.APPROVED);
                    initiateKycResponse.setShowKycPage(false);
                    loanDetailsV3Service.saveApplicationViewState(null, lendingApplication.getId(), LendingViewStates.ENACH_PAGE);
                    log.info("Returning from kyc stage for merchant Id:{} for lender:{}, kyc skipped", scopeDataArgs.getMerchant().getId(), lendingApplication.getLender());
                    return new LendingStateDTO<>(initiateKycResponse, LendingViewStates.ENACH_PAGE, LendingViewStates.KYC_PAGE);
                }
            }

            //checking lender association
            LendingApplicationDetails lendingApplicationDetails =
                    lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
            if (!ObjectUtils.isEmpty(lendingApplicationDetails)) {
                log.info("lender assc for {} {}", lendingApplicationDetails.getLenderAssc(), lendingApplicationDetails.getApplicationId());
                initiateKycResponse.setLenderAssc(Optional.ofNullable(lendingApplicationDetails.getLenderAssc()).orElse(false));
                if(LenderAssociationStages.LENDER_CHANGE.name().equals(lendingApplicationDetails.getStage()) && !ObjectUtils.isEmpty(loanUtil.getLenderAggregationScreenV2(lendingApplication.getId(), scopeDataArgs.getMerchant().getId()))){
                    if (loanUtil.isApplicableForAggregationFlowV2(scopeDataArgs.getMerchant().getId(), lendingApplication.getId())) {
                        return new LendingStateDTO<>(initiateKycResponse, LendingViewStates.OFFER_EVALUATION_PAGE, LendingViewStates.KYC_PAGE);
                    }
                }
                else
                {
                    if(LenderAssociationStages.LENDER_CHANGE.name().equals(lendingApplicationDetails.getStage()) && !ObjectUtils.isEmpty(loanUtil.getLenderAggregationScreen(lendingApplication.getId(), scopeDataArgs.getMerchant().getId()))){
                        if (loanUtil.isApplicableForAggregationFlow(scopeDataArgs.getMerchant().getId(), lendingApplication.getId())) {
                            return new LendingStateDTO<>(initiateKycResponse, LendingViewStates.OFFER_EVALUATION_PAGE, LendingViewStates.KYC_PAGE);
                        }
                    }

                }
            }

            boolean reKyc = false;
            LendingApplicationLenderDetails lenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
            if(!ObjectUtils.isEmpty(lenderDetails)) {
                reKyc = LenderAssociationStatus.RE_KYC.name().equalsIgnoreCase(lenderDetails.getKycStatus());
                boolean isSkipKycCase = (ObjectUtils.isEmpty(lenderDetails.getKycMode())) ?
                        Optional.ofNullable(lenderDetails.getMetaData()).map(id -> id.get("eligibleForSkipKyc")).filter(Boolean.class::isInstance).map(Boolean.class::cast).orElse(false)
                        : LenderAssociationStages.SKIP_KYC.name().equalsIgnoreCase(lenderDetails.getKycMode());
                initiateKycResponse.setSkipKycEligible(isSkipKycCase);
            }
            String kycMode = initiateKycResponse.isSkipKycEligible() ? KycMode.SKIP_KYC.name() : initiateKycResponse.isLenderKycPipe() ? KycMode.LENDER_KYC.name() : KycMode.BP_KYC.name();
            LendingApplicationKycDetails lendingApplicationKycDetails  = isResubmittedApplication ?
                    lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId()) :
                    lendingApplicationKycDetailsDao.findTop1ByApplicationIdAndLenderAndKycModeOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender(), kycMode);

            if (!ObjectUtils.isEmpty(lendingApplicationKycDetails)) {
                // treating consent_date as kys success, because it is set only after aadhaar,selfie,and pan gets verified.
                if (Objects.nonNull(lendingApplicationKycDetails.getConsentDate()) && !reKyc) {
                    initiateKycResponse.setKycStatus(KycStatus.APPROVED);
                    initiateKycResponse.setShowKycPage(false);
                    if(initiateKycResponse.isTopup() && !Arrays.asList(Lender.ABFL.name(), Lender.TRILLIONLOANS.name(), Lender.PIRAMAL.name()).contains(lendingApplication.getLender())){  // Add lender in list for redirecting on lender evaluation page after kyc scope in each case of topup
                        loanDetailsV3Service.saveApplicationViewState(lendingApplicationDetails, lendingApplication.getId(), LendingViewStates.ENACH_PAGE);
                        return new LendingStateDTO<>(initiateKycResponse, LendingViewStates.ENACH_PAGE, LendingViewStates.KYC_PAGE);
                    }
                    loanDetailsV3Service.saveApplicationViewState(lendingApplicationDetails, lendingApplication.getId(), LendingViewStates.LENDER_EVALUATION_PAGE);
                    return new LendingStateDTO<>(initiateKycResponse, LendingViewStates.LENDER_EVALUATION_PAGE, LendingViewStates.KYC_PAGE);
                }
                // check the status for already created entry in table
               Date validAfter = isResubmittedApplication ? lendingResubmitReasonCount.getResubmitTimestamp() : reKyc ? lenderDetails.getUpdatedAt() : lendingApplicationKycDetails.getKycInitiatedAt();
               boolean kycVerified=updateApplicationKycDetails(
                       lendingApplicationKycDetails, lendingApplication, lendingApplicationDetails, scopeDataArgs.getMerchant().getId(),scopeDataArgs.getMerchant().getMid(),
                       validAfter, initiateKycResponse, isResubmittedApplication);
                if(kycVerified){
                    initiateKycResponse.setKycStatus(KycStatus.APPROVED);
                    initiateKycResponse.setDeeplink(kycDeepLink);
                    initiateKycResponse.setShowKycPage(true);
                    if(initiateKycResponse.isTopup() && reKyc) {
                        if(!ObjectUtils.isEmpty(lenderDetails)) {
                            lenderDetails.setKycStatus(LenderAssociationStatus.EKYC_PENDING.name());
                            lendingApplicationLenderDetailsDao.save(lenderDetails);
                        }
                        initiateKycResponse.setShowKycPage(false);
                        loanDetailsV3Service.saveApplicationViewState(lendingApplicationDetails, lendingApplication.getId(), LendingViewStates.LENDER_EVALUATION_PAGE);
                        return new LendingStateDTO<>(initiateKycResponse , LendingViewStates.LENDER_EVALUATION_PAGE, LendingViewStates.KYC_PAGE);
                    }
                    loanDetailsV3Service.saveApplicationViewState(lendingApplicationDetails, lendingApplication.getId(), LendingViewStates.KYC_PAGE);
                    if(initiateKycResponse.isTopup() && !Arrays.asList(Lender.ABFL.name(), Lender.TRILLIONLOANS.name(), Lender.PIRAMAL.name()).contains(lendingApplication.getLender())){   // Add lender in list for redirecting on lender evaluation page after kyc scope in each case of topup
                        return new LendingStateDTO<>(initiateKycResponse, LendingViewStates.ENACH_PAGE, LendingViewStates.KYC_PAGE);
                    }
                    return new LendingStateDTO<>(initiateKycResponse , LendingViewStates.LENDER_EVALUATION_PAGE, LendingViewStates.KYC_PAGE);
                }
                initiateKycResponse=initiateKyc(lendingApplication,scopeDataArgs.getMerchant().getId(), initiateKycResponse.isFreshKyc(), isResubmittedApplication, validAfter, initiateKycResponse.getConvertedKycRanking(), initiateKycResponse);
            } else {
                log.info("No kyc entry for merchantId:{} and applicationId:{},Creating entry in KYC table", scopeDataArgs.getMerchant().getId(), lendingApplication.getId());
                lendingApplicationKycDetails = new LendingApplicationKycDetails();
                lendingApplicationKycDetails.setMerchantId(scopeDataArgs.getMerchant().getId());
                lendingApplicationKycDetails.setApplicationId(lendingApplication.getId());
                lendingApplicationKycDetails.setLender(lendingApplication.getLender());
                lendingApplicationKycDetails.setKycInitiatedAt(new Date());
                lendingApplicationKycDetails.setKycMode(kycMode);
                lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails);
                initiateKycResponse=initiateKyc(lendingApplication,scopeDataArgs.getMerchant().getId(), initiateKycResponse.isFreshKyc(), isResubmittedApplication, lendingApplicationKycDetails.getKycInitiatedAt(), initiateKycResponse.getConvertedKycRanking(), initiateKycResponse);
                executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.LOAN_KYC_INITIATED_BE.name(), null, scopeDataArgs.getMerchant().getMid()));
                funnelService.submitEventV3(scopeDataArgs.getMerchant().getId(), null, lendingApplication.getId(),lendingApplication.getLoanType(),
                        FunnelEnums.StageId.KYC, FunnelEnums.StageEvent.INITIATED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
            }
            loanDetailsV3Service.saveApplicationViewState(lendingApplicationDetails, lendingApplication.getId(), LendingViewStates.KYC_PAGE);
            if(initiateKycResponse.isTopup() && !reKyc && !Arrays.asList(Lender.ABFL.name(), Lender.TRILLIONLOANS.name(), Lender.PIRAMAL.name()).contains(lendingApplication.getLender())){
                return new LendingStateDTO<>(initiateKycResponse, LendingViewStates.ENACH_PAGE, LendingViewStates.KYC_PAGE);
            }
            return new LendingStateDTO<>(initiateKycResponse , LendingViewStates.LENDER_EVALUATION_PAGE, LendingViewStates.KYC_PAGE);
        } catch (Exception ex) {
            log.error("Exception while initiating kyc for merchant:{} {} {}", scopeDataArgs.getMerchant().getId(), ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            throw new LoanDetailsException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(),LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
        }
    }

    private KYCStateDTO initiateKyc(LendingApplication lendingApplication, Long merchantId, boolean isFreshKyc, boolean isResubmittedApplication, Date validAfter, String convertedKycRanking, KYCStateDTO initiateKycResponse){
        initiateKycResponse.setConvertedKycRanking(convertedKycRanking);
        List<KycDocType> docTypes = new ArrayList<>(getKycTypesByLender(Lender.valueOf(lendingApplication.getLender()), initiateKycResponse));
//        docTypes.add(KycDocType.PAN_CARD);
        if(isResubmittedApplication) {
            docTypes.clear();
           docTypes.add(KycDocType.SELFIE);
           initiateKycResponse.setSelfieResumit(true);
        }
        String callBackURL = env.getProperty("kyc.loan.deeplink.v3");
        Experian experian = experianDao.getByMerchantId(merchantId);
        if (experian == null || experian.getPancardNumber() == null) {
            throw new LoanDetailsException(LoanDetailExceptionEnum.PANCARD_DOES_NOT_EXIST.getErrorCode(),LoanDetailExceptionEnum.PANCARD_DOES_NOT_EXIST.getErrorMessage());
        }
        String wroute = "&applicationId="+lendingApplication.getId();
        callBackURL += wroute;
        String kycInitReferenceId = lendingApplication.getId().toString() + "_" + lendingApplication.getLender() + "_" + validAfter;
        InitiateKycDTO initiateKycDTO = InitiateKycDTO.builder()
                .referenceId(kycInitReferenceId)
                .panNumber(experian.getPancardNumber())
                .callBackUrl(callBackURL)
                .merchantId(merchantId.toString()).build();
        boolean onlySelfieLivelinessRequired = (initiateKycResponse.isLenderKycPipe() || initiateKycResponse.isSkipKycEligible());
        Map<String, String> ckycResponseObj = kycHandler.initiateKyc(merchantId, initiateKycDTO, docTypes, validAfter, onlySelfieLivelinessRequired, convertedKycRanking, initiateKycResponse);
        if (ckycResponseObj.containsKey("ckycId")) {
            lendingApplicationDao.updateKycId(lendingApplication.getId(), ckycResponseObj.get("ckycId"), merchantId);
            if(isFreshKyc || !ObjectUtils.isEmpty(convertedKycRanking)){
                initiateKycResponse.setDeeplink(ckycResponseObj.containsKey("callBackUrl") ? ckycResponseObj.get("callBackUrl") : kycDeepLink);
            }
            else {
                initiateKycResponse.setDeeplink(kycRevalidationDeeplink);
            }
            initiateKycResponse.setShowKycPage(true);
            initiateKycResponse.setKycStatus(KycStatus.DRAFT);
            return initiateKycResponse;
        }
        log.error("Unable to initiate kyc for merchant :{} with error message:{}",merchantId,ckycResponseObj.get("message"));
        throw new LoanDetailsException(LoanDetailExceptionEnum.INITIATE_KYC_FAILED.getErrorCode(),LoanDetailExceptionEnum.INITIATE_KYC_FAILED.getErrorMessage());
    }

    private boolean updateApplicationKycDetails(LendingApplicationKycDetails lendingApplicationKycDetails, LendingApplication lendingApplication, LendingApplicationDetails lendingApplicationDetails, Long merchantId,String mid, Date vaildAfterDate, KYCStateDTO kycStateDTO, Boolean isResubmittedApplication) {
        boolean kycVerified=false;
        log.info("Updating kyc details for merchant:{}", merchantId);
        boolean selfieValid = false;
        boolean aadharValid = false;
        boolean aadharDigilocker = false;
//        boolean panCardApproved = false;
        boolean panNoApproved = false;
        String docs = getKycDocsForVerificationByLender(Lender.valueOf(lendingApplicationKycDetails.getLender()), kycStateDTO);
        if (isResubmittedApplication) {
            docs = "SELFIE";
        }
        Boolean lenderKycOrSkipKyc = kycStateDTO.isLenderKycPipe() || kycStateDTO.isSkipKycEligible();
        // Fetch kycRanking from metadata or initialize it
        Map<String, Object> metaData = lendingApplicationDetails.getMetaData();
        String kycRankingConverted = MapUtils.getString(metaData, "kycRankingConverted");
        kycStateDTO.setConvertedKycRanking(kycRankingConverted);
        KycDocResponseDTO kycDocResponseDTO = kycHandler.getKycDocs(merchantId, vaildAfterDate, LendingConstants.POA_PROVIDER, docs, false, lenderKycOrSkipKyc, kycRankingConverted);
        log.info("KYC docs fetched for merchantId : {}", merchantId);

        if(kycDocResponseDTO != null) {
            kycStateDTO.setKycRanking(kycDocResponseDTO.getKycRanking());
            kycStateDTO.setKycRankingStatus(kycDocResponseDTO.getEntityStatus());
            log.info("kycRanking: {} and status: {} for merchantId : {}", kycDocResponseDTO.getKycRanking(), kycDocResponseDTO.getEntityStatus(), merchantId);
        }
        if(p2pmChecks(kycDocResponseDTO.getKycRanking()) && ObjectUtils.isEmpty(kycRankingConverted)) {
            if(ObjectUtils.isEmpty(metaData)) {
                metaData = new HashMap<>();
            }
            kycRankingConverted = convertKycRanking(kycDocResponseDTO.getKycRanking());
            metaData.put("kycRankingReceived", kycDocResponseDTO.getKycRanking());
            if(!ObjectUtils.isEmpty(kycRankingConverted)) {
                metaData.put("kycRankingConverted", kycRankingConverted);
                kycStateDTO.setConvertedKycRanking(kycRankingConverted);
            }
            lendingApplicationDetails.setMetaData(metaData);
            lendingApplicationDetailsDao.save(lendingApplicationDetails);
            log.info("KYC ranking saved for merchantId: {} (Received: {}, Converted: {})",
                    merchantId, kycDocResponseDTO.getKycRanking(), kycRankingConverted);
        }

        if(!KycConstants.KYC_ENTITY_ACTIVATED.equalsIgnoreCase(kycDocResponseDTO.getEntityStatus())){
            kycStateDTO.setFreshKyc(true);
        }
        for (KycDoc kycDoc : kycDocResponseDTO.getKycDocs()) {
            switch (kycDoc.getDocType()) {
                case SELFIE:
                    if (KycDocStatus.APPROVED.equals(kycDoc.getStatus()) || (lenderKycOrSkipKyc && KycDocStatus.DRAFT.equals(kycDoc.getStatus()))) {
                        log.info("Selfie doc is valid for merchantId:{}", merchantId);
                        lendingApplicationKycDetails.setSelfieUrl(kycDoc.getDocFrontImageUrl());
                        if (Objects.isNull(lendingApplicationKycDetails.getSelfieApprovedAt()))
                            lendingApplicationKycDetails.setSelfieApprovedAt(new Date());
                        selfieValid = true;
                    }
                    break;
                case POA:
                    if (KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
                        aadharValid = true;
                        log.info("Aadhaar doc is valid for merchantId:{}", merchantId);
                        if (kycDoc.getSubDocType() != null && KycDocType.EKYC.equals(kycDoc.getSubDocType())) {
                            lendingApplicationKycDetails.setAadharIdentifier(kycDoc.getDocIdentifier());
                            lendingApplicationKycDetails.setAadharAddress(kycDoc.getAddress());
                            if (Objects.isNull(lendingApplicationKycDetails.getAadharApprovedAt()))
                                lendingApplicationKycDetails.setAadharApprovedAt(new Date());
                            String dob = KycUtils.getDOB(kycDoc);
                            log.info("dob from POA kyc doc for merchant: {}, {}", dob, merchantId);
                            lendingApplicationKycDetails.setDob(dob);
                            lendingApplicationKycDetails.setFatherName(KycUtils.getFatherName(kycDoc.getAddress()));
                            aadharDigilocker = true;
                            log.info("Aadhaar digilocker doc valid for merchantId:{}", merchantId);
                        }
                    }
                    break;
                case PAN_NO:
                    if (KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
                        lendingApplicationKycDetails.setPan(kycDoc.getDocIdentifier());
                        panNoApproved = true;
                        log.info("Pan No is valid for merchantId:{}", lendingApplicationKycDetails.getMerchantId());
                    }
            }
        }
        if(isResubmittedApplication) {
            aadharValid = true;
            aadharDigilocker = true;
            panNoApproved = true;
        }
        if(lenderKycOrSkipKyc) {
            aadharValid = true;
            aadharDigilocker = true;
        }

        boolean p2pmCheckPassed = true;
        if (kycDocResponseDTO.isActivatedViaNewObV3() && p2pmChecks(kycDocResponseDTO.getKycRanking())) {
            String requestedkycRankingStatus = kycDocResponseDTO.getStatusOfRequestedKycRanking();
            p2pmCheckPassed = (!ObjectUtils.isEmpty(requestedkycRankingStatus)
                    && ("APPROVED".equalsIgnoreCase(requestedkycRankingStatus) || "PENDING".equalsIgnoreCase(requestedkycRankingStatus)));
            log.info("kycRanking check passed for requestedkycRankingStatus {}", requestedkycRankingStatus);
        }

        if (selfieValid && aadharValid && aadharDigilocker && panNoApproved && p2pmCheckPassed) {
            log.info("All the required kyc documents are valid for merchantId:{}, setting consent date as kyc success", merchantId);
            lendingApplicationKycDetails.setConsentDate(new Date());
            kycVerified=true;
            log.info("Kyc details verified for merchant : {}", merchantId);
            executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.LOAN_KYC_VERIFIED_BE.name(), null, mid));
            funnelService.submitEventV3(merchantId, null, lendingApplication.getId(),lendingApplication.getLoanType(),
                    FunnelEnums.StageId.KYC, FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
        }
        lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails);
        return kycVerified;
    }

    private boolean p2pmChecks(String kycRanking) {
        return p2pmEnabled &&
                isKycRankingApplicable(kycRanking);
    }

    private boolean isKycRankingApplicable(String kycRanking) {
        return ObjectUtils.isEmpty(kycRanking) ||
                P2PM.name().equalsIgnoreCase(kycRanking) ||
                P2MS.name().equalsIgnoreCase(kycRanking);
    }


    private List<KycDocType> getKycTypesByLender(Lender lender, KYCStateDTO kycState) {
        if(kycState.isLenderKycPipe() || kycState.isSkipKycEligible()) {
            switch (lender) {
                case ABFL:
                case PIRAMAL:
                case TRILLIONLOANS:
                    return Arrays.asList(KycDocType.PAN_NO, KycDocType.SELFIE);
                default:
                    return Arrays.asList(KycDocType.PAN_NO, KycDocType.SELFIE, KycDocType.EKYC);
            }
        }
        return Arrays.asList(KycDocType.PAN_NO, KycDocType.SELFIE, KycDocType.EKYC);
    }

    private String convertKycRanking(String kycRanking) {
        if(isKycRankingApplicable(kycRanking)) {
            return "P2MS";
        }
        return null;
    }


    private String getKycDocsForVerificationByLender(Lender lender, KYCStateDTO kycState) {
        if(kycState.isLenderKycPipe() || kycState.isSkipKycEligible()) {
            switch (lender) {
                case ABFL:
                case PIRAMAL:
                case TRILLIONLOANS:
                    return "PAN_NO,SELFIE";
                default:
                    return "PAN_NO,SELFIE,POA";
            }
        }
        return "PAN_NO,SELFIE,POA";
    }
}

