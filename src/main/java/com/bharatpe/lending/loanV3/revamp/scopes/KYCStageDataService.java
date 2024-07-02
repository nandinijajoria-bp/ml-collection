package com.bharatpe.lending.loanV3.revamp.scopes;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingResubmitReasonCountDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingResubmitReasonCount;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.KycConstants;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.common.dao.LendingApplicationKycDetailsDao;
import com.bharatpe.lending.dto.KycDoc;
import com.bharatpe.lending.common.entity.LendingApplicationKycDetails;
import com.bharatpe.lending.dto.KycDocResponseDTO;
import com.bharatpe.lending.enums.*;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.loanV2.dto.InitiateKycDTO;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    EasyLoanUtil easyLoanUtil;


    @Override
    public LendingStateDTO<KYCStateDTO> processCurrentStage(ScopeDataArgs scopeDataArgs) {
        return fetchScopedData(scopeDataArgs);
    }

    @Override
    @Transactional
    public LendingStateDTO<KYCStateDTO> fetchScopedData(ScopeDataArgs scopeDataArgs) {
        KYCStateDTO initiateKycResponse = new KYCStateDTO();
        LendingApplication lendingApplication = lendingApplicationServiceV3.getLendingApplication(scopeDataArgs.getApplicationId(),scopeDataArgs.getMerchant().getId());
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("Application not found for {}", scopeDataArgs.getMerchant().getId());
            throw new LoanDetailsException(LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorCode(),LoanDetailExceptionEnum.APPLICATION_NOT_FOUND.getErrorMessage());
        }
        initiateKycResponse.setLender(lendingApplication.getLender());
        boolean isResubmittedApplication = false;
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
            if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType())){
                initiateKycResponse.setTopup(true);
                log.info("setting topup {} for {} {}", initiateKycResponse.isTopup(), scopeDataArgs.getMerchant().getId(), lendingApplication.getId());
                if(!Lender.LIQUILOANS_NBFC.name().equalsIgnoreCase(lendingApplication.getLender())){
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
            }

//            String wroute = getWroute(nextState,lendingApplication.getId());
            LendingApplicationKycDetails lendingApplicationKycDetails = null;

            // START remove skipping kyc for merchant if already done on that lender

//            lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findSuccessKycDetails(lendingApplication.getMerchantId(), lendingApplication.getLender());
//           /*
//           check if kyc is already completed for a lender
//            */
//            if (Objects.nonNull(lendingApplicationKycDetails)) {
//                log.info("Kyc already done for merchant Id:{} for lender:{}", scopeDataArgs.getMerchant().getId(), lendingApplication.getLender());
//                if (!KycStatus.APPROVED.name().equalsIgnoreCase(lendingApplication.getCkycStatus())) {
//                    log.info("Updating ckyc status for merchant Id:{} and lender:{}", scopeDataArgs.getMerchant().getId(), lendingApplication.getLender());
//                    lendingApplication.setCkycStatus(KycStatus.APPROVED.name());
//                    lendingApplication.setCkycDate(new Date());
//                    lendingApplicationDao.save(lendingApplication);
//                }
//                initiateKycResponse.setKycStatus(KycStatus.APPROVED);
//                initiateKycResponse.setShowKycPage(false);
//                log.info("Returning from kyc stage for merchant Id:{} for lender:{}, kyc already done", scopeDataArgs.getMerchant().getId(), lendingApplication.getLender());
//                if(initiateKycResponse.isTopup()){
//                    loanDetailsV3Service.saveApplicationViewState(lendingApplicationDetails, lendingApplication.getId(), LendingViewStates.ENACH_PAGE);
//                    return new LendingStateDTO<>(initiateKycResponse, LendingViewStates.ENACH_PAGE, LendingViewStates.KYC_PAGE);
//                }
//                loanDetailsV3Service.saveApplicationViewState(lendingApplicationDetails, lendingApplication.getId(), LendingViewStates.LENDER_EVALUATION_PAGE);
//                return new LendingStateDTO<>(initiateKycResponse, LendingViewStates.LENDER_EVALUATION_PAGE, LendingViewStates.KYC_PAGE);
//            }

            // END remove skipping kyc for merchant if already done on that lender

            lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());

            if (!ObjectUtils.isEmpty(lendingApplicationKycDetails)) {
                // treating consent_date as kys success, because it is set only after aadhaar,selfie,and pan gets verified.
                if (Objects.nonNull(lendingApplicationKycDetails.getConsentDate())) {
                    initiateKycResponse.setKycStatus(KycStatus.APPROVED);
                    initiateKycResponse.setShowKycPage(false);
                    if(initiateKycResponse.isTopup()){
                        loanDetailsV3Service.saveApplicationViewState(lendingApplicationDetails, lendingApplication.getId(), LendingViewStates.ENACH_PAGE);
                        return new LendingStateDTO<>(initiateKycResponse, LendingViewStates.ENACH_PAGE, LendingViewStates.KYC_PAGE);
                    }
                    loanDetailsV3Service.saveApplicationViewState(lendingApplicationDetails, lendingApplication.getId(), LendingViewStates.LENDER_EVALUATION_PAGE);
                    return new LendingStateDTO<>(initiateKycResponse, LendingViewStates.LENDER_EVALUATION_PAGE, LendingViewStates.KYC_PAGE);
                }
                // check the status for already created entry in table
               Date validAfter = isResubmittedApplication ? lendingResubmitReasonCount.getResubmitTimestamp() : lendingApplication.getCreatedAt();
               boolean kycVerified=updateApplicationKycDetails(
                       lendingApplicationKycDetails, lendingApplication.getId(), scopeDataArgs.getMerchant().getId(),scopeDataArgs.getMerchant().getMid(),
                       validAfter, initiateKycResponse, isResubmittedApplication, lendingApplication.getLoanType()
               );
                if(kycVerified){
                    initiateKycResponse.setKycStatus(KycStatus.APPROVED);
                    initiateKycResponse.setDeeplink(kycDeepLink);
                    initiateKycResponse.setShowKycPage(true);
                    loanDetailsV3Service.saveApplicationViewState(lendingApplicationDetails, lendingApplication.getId(), LendingViewStates.KYC_PAGE);
                    if(initiateKycResponse.isTopup()){
                        return new LendingStateDTO<>(initiateKycResponse, LendingViewStates.ENACH_PAGE, LendingViewStates.KYC_PAGE);
                    }
                    return new LendingStateDTO<>(initiateKycResponse , LendingViewStates.LENDER_EVALUATION_PAGE, LendingViewStates.KYC_PAGE);
                }
                initiateKycResponse=initiateKyc(lendingApplication,scopeDataArgs.getMerchant().getId(), initiateKycResponse.isTopup(), initiateKycResponse.isFreshKyc(), isResubmittedApplication, validAfter);
                loanDetailsV3Service.saveApplicationViewState(lendingApplicationDetails, lendingApplication.getId(), LendingViewStates.KYC_PAGE);
                if(initiateKycResponse.isTopup()){
                    return new LendingStateDTO<>(initiateKycResponse, LendingViewStates.ENACH_PAGE, LendingViewStates.KYC_PAGE);
                }
                return new LendingStateDTO<>(initiateKycResponse , LendingViewStates.LENDER_EVALUATION_PAGE, LendingViewStates.KYC_PAGE);
            } else {
                log.info("No kyc entry for merchantId:{} and applicationId:{},Creating entry in KYC table", scopeDataArgs.getMerchant().getId(), lendingApplication.getId());
                lendingApplicationKycDetails = new LendingApplicationKycDetails();
                lendingApplicationKycDetails.setMerchantId(scopeDataArgs.getMerchant().getId());
                lendingApplicationKycDetails.setApplicationId(lendingApplication.getId());
                lendingApplicationKycDetails.setLender(lendingApplication.getLender());
                lendingApplicationKycDetails.setKycInitiatedAt(new Date());
                lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails);


                boolean kycVerified=updateApplicationKycDetails(
                        lendingApplicationKycDetails, lendingApplication.getId(), scopeDataArgs.getMerchant().getId(),scopeDataArgs.getMerchant().getMid(),
                        lendingApplication.getCreatedAt(), initiateKycResponse, isResubmittedApplication,lendingApplication.getLoanType()
                );

                initiateKycResponse=initiateKyc(lendingApplication,scopeDataArgs.getMerchant().getId(), initiateKycResponse.isTopup(), initiateKycResponse.isFreshKyc(), isResubmittedApplication, lendingApplication.getCreatedAt());
                executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.LOAN_KYC_INITIATED_BE.name(), null, scopeDataArgs.getMerchant().getMid()));
                funnelService.submitEventV3(scopeDataArgs.getMerchant().getId(), null, lendingApplication.getId(),lendingApplication.getLoanType(),
                        FunnelEnums.StageId.KYC, FunnelEnums.StageEvent.INITIATED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
                loanDetailsV3Service.saveApplicationViewState(lendingApplicationDetails, lendingApplication.getId(), LendingViewStates.KYC_PAGE);
                if(initiateKycResponse.isTopup()){
                    return new LendingStateDTO<>(initiateKycResponse, LendingViewStates.ENACH_PAGE, LendingViewStates.KYC_PAGE);
                }
                return new LendingStateDTO<>(initiateKycResponse , LendingViewStates.LENDER_EVALUATION_PAGE, LendingViewStates.KYC_PAGE);
            }
        } catch (Exception ex) {
            log.error("Exception while initiating kyc for merchant:{} {} {}", scopeDataArgs.getMerchant().getId(), ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            throw new LoanDetailsException(LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorCode(),LoanDetailExceptionEnum.SOMETHING_WENT_WRONG.getErrorMessage());
        }
    }

    private KYCStateDTO initiateKyc(LendingApplication lendingApplication,Long merchantId, Boolean isTopup, boolean isFreshKyc, boolean isResubmittedApplication, Date validAfter){
        KYCStateDTO initiateKycResponse = new KYCStateDTO();
        initiateKycResponse.setLender(lendingApplication.getLender());
        initiateKycResponse.setTopup(isTopup);
        List<KycDocType> docTypes = new ArrayList<>();
//        docTypes.add(KycDocType.PAN_CARD);
        if(isResubmittedApplication) {
           docTypes.add(KycDocType.SELFIE);
           initiateKycResponse.setSelfieResumit(true);
        } else  {
            docTypes.add(KycDocType.PAN_NO);
            docTypes.add(KycDocType.SELFIE);
            docTypes.add(KycDocType.EKYC);
        }
        String callBackURL = env.getProperty("kyc.loan.deeplink.v3");
        Experian experian = experianDao.getByMerchantId(merchantId);
        if (experian == null || experian.getPancardNumber() == null) {
            throw new LoanDetailsException(LoanDetailExceptionEnum.PANCARD_DOES_NOT_EXIST.getErrorCode(),LoanDetailExceptionEnum.PANCARD_DOES_NOT_EXIST.getErrorMessage());
        }
        String wroute = "&applicationId="+lendingApplication.getId();
        callBackURL += wroute;
        String kycInitReferenceId = lendingApplication.getId().toString();
        InitiateKycDTO initiateKycDTO = InitiateKycDTO.builder()
                .referenceId(kycInitReferenceId)
                .panNumber(experian.getPancardNumber())
                .callBackUrl(callBackURL)
                .merchantId(merchantId.toString()).build();
        Map<String, String> ckycResponseObj = kycHandler.initiateKyc(merchantId, initiateKycDTO, docTypes, validAfter);
        if (ckycResponseObj.containsKey("ckycId")) {
            lendingApplicationDao.updateKycId(lendingApplication.getId(), ckycResponseObj.get("ckycId"), merchantId);
            if(isFreshKyc){
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

    private boolean updateApplicationKycDetails(LendingApplicationKycDetails lendingApplicationKycDetails, Long applicationId, Long merchantId,String mid, Date vaildAfterDate, KYCStateDTO kycStateDTO, Boolean isResubmittedApplication, String loanType) {
        boolean kycVerified=false;
        log.info("Updating kyc details for merchant:{}", merchantId);
        boolean selfieValid = false;
        boolean aadharValid = false;
        boolean aadharDigilocker = false;
//        boolean panCardApproved = false;
        boolean panNoApproved = false;
        String docs = "PAN_NO,SELFIE,POA";
        if (isResubmittedApplication) {
            docs = "SELFIE";
        }
        KycDocResponseDTO kycDocResponseDTO = kycHandler.getKycDocs(merchantId, vaildAfterDate, LendingConstants.POA_PROVIDER, docs);
        log.info("KYC docs fetched for merchantId : {}", merchantId);
        if(!KycConstants.KYC_ENTITY_ACTIVATED.equalsIgnoreCase(kycDocResponseDTO.getEntityStatus())){
            kycStateDTO.setFreshKyc(true);
        }
        for (KycDoc kycDoc : kycDocResponseDTO.getKycDocs()) {
            switch (kycDoc.getDocType()) {
                case SELFIE:
                    if (KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
                        log.info("Selfie doc is valid for merchantId:{}", merchantId);
                        lendingApplicationKycDetails.setSelfieUrl(kycDoc.getDocFrontImageUrl());
                        if (Objects.isNull(lendingApplicationKycDetails.getSelfieApprovedAt()))
                            lendingApplicationKycDetails.setSelfieApprovedAt(new Date());
                        selfieValid = true;
                    }
                case POA:
                    if (KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
                        aadharValid = true;
                        log.info("Aadhaar doc is valid for merchantId:{}", merchantId);
                        if (kycDoc.getSubDocType() != null && KycDocType.EKYC.equals(kycDoc.getSubDocType())) {
                            lendingApplicationKycDetails.setAadharIdentifier(kycDoc.getDocIdentifier());
                            lendingApplicationKycDetails.setAadharAddress(kycDoc.getAddress());
                            if (Objects.isNull(lendingApplicationKycDetails.getAadharApprovedAt()))
                                lendingApplicationKycDetails.setAadharApprovedAt(new Date());
                            if (!ObjectUtils.isEmpty(kycDoc.getDigioXml())) {
                                lendingApplicationKycDetails.setAadharXml(kycDoc.getDigioXml());
                            }
                            String dob = KycUtils.getDOB(kycDoc);
                            log.info("dob from POA kyc doc for merchant: {}, {}",dob,merchantId);
                            lendingApplicationKycDetails.setDob(dob);
                            aadharDigilocker = true;
                            log.info("Aadhaar digilocker doc valid for merchantId:{}", merchantId);
                        }
                    }
//                case PAN_CARD:
//                    if (KycDocStatus.APPROVED.equals(kycDoc.getStatus())) {
//                        lendingApplicationKycDetails.setPanUrl(kycDoc.getDocFrontImageUrl());
//                        if (Objects.isNull(lendingApplicationKycDetails.getPanApprovedAt()))
//                            lendingApplicationKycDetails.setPanApprovedAt(new Date());
//                        panCardApproved = true;
//                        log.info("Pan Card is valid for merchantId:{}", lendingApplicationKycDetails.getMerchantId());
//                    }
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
        if (selfieValid && aadharValid && aadharDigilocker && panNoApproved) {
            log.info("All the required kyc documents are valid for merchantId:{}, setting consent date as kyc success", merchantId);
            lendingApplicationKycDetails.setConsentDate(new Date());
            kycVerified=true;
            log.info("Kyc details verified for merchant : {}", merchantId);
            executorService.execute(() -> cleverTapEventService.sendClevertapEvent(CleverTapEvents.LOAN_KYC_VERIFIED_BE.name(), null, mid));
            funnelService.submitEventV3(merchantId, null, applicationId,loanType,
                    FunnelEnums.StageId.KYC, FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
        }
        lendingApplicationKycDetailsDao.save(lendingApplicationKycDetails);
        return kycVerified;
    }
}

