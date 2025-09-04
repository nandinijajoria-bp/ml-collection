package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.TLCreateClientResponseDto;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.TLCreateLeadResponseDto;
import com.bharatpe.lending.loanV3.enums.KycMode;
import com.bharatpe.lending.loanV3.revamp.util.LoanUtilV3;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Slf4j
@Service
public class TLRetryService {
    @Autowired
    CommonService commonService;

    @Autowired
    KycUtils kycUtils;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LoanUtil loanUtil;

    public Boolean processCallback(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest, NBFCResponseDTO<?> response) {
        try {
            Boolean isTopup = LoanType.TOPUP.name().equalsIgnoreCase(lenderAssociationDetailsRequest.getLendingApplication().getLoanType());
            boolean isEligibleForLenderKyc = kycUtils.isEligibleForLenderKyc(Lender.TRILLIONLOANS.name(), lenderAssociationDetailsRequest.getLendingApplication().getMerchantId(), isTopup);
            boolean inRolloutForSkipKyc = kycUtils.isEligibleForSkipKyc(lenderAssociationDetailsRequest.getLendingApplication().getId(), Lender.TRILLIONLOANS, lenderAssociationDetailsRequest.getLendingApplication().getMerchantId(), isTopup);
            boolean isEligibleForSkipKyc = Arrays.asList(LenderAssociationStages.CREATE_LEAD.name(), LenderAssociationStages.CREATE_CLIENT.name()).contains(response.getType())
                            ? inRolloutForSkipKyc : ObjectUtils.isEmpty(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getKycMode())
                            ? Optional.ofNullable(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getMetaData()).map(id-> id.get("eligibleForSkipKyc")).filter(Boolean.class::isInstance).map(Boolean.class::cast).orElse(false)
                            : LenderAssociationStages.SKIP_KYC.name().equalsIgnoreCase(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getKycMode());
            if (LoanType.TOPUP.name().equalsIgnoreCase(lenderAssociationDetailsRequest.getLendingApplication().getLoanType())) {
                LendingApplication parentApplication = loanUtil.fetchParentApplication(lenderAssociationDetailsRequest.getLendingApplication().getId());
                lenderAssociationDetailsRequest.setTopupParentLender(parentApplication.getLender());
            }
            List<LenderAssociationStages> stageToBeInvokedInOrder = new ArrayList<>();
            if (response.getSuccess() && !ObjectUtils.isEmpty(response.getData())) {
                switch (response.getType()) {
                    case "CREATE_CLIENT":
                        log.info("Create client request of TrillionLoans success for {}", lenderAssociationDetailsRequest.getApplicationId());
                        TLCreateClientResponseDto createClientResponse = objectMapper.convertValue(response.getData(), TLCreateClientResponseDto.class);
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setCccId(createClientResponse.getClientId().toString());
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_SUCCESS.name());
                        commonService.manageApplicationState(lenderAssociationDetailsRequest);
                        addStagesIfEligible(stageToBeInvokedInOrder, isEligibleForLenderKyc, isTopup, LenderAssociationStages.CREATE_CLIENT, isEligibleForSkipKyc);
                        break;
                    case "CREATE_LEAD":
                        log.info("Create lead request of TrillionLoans success for {}", lenderAssociationDetailsRequest.getApplicationId());
                        TLCreateLeadResponseDto createLeadResponse = objectMapper.convertValue(response.getData(), TLCreateLeadResponseDto.class);
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadId(createLeadResponse.getResourceId().toString());
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(isEligibleForLenderKyc ? LenderAssociationStatus.SELFIE_PENDING_FOR_LENDER_KYC.name() : LenderAssociationStatus.LEAD_CREATION_SUCCESS.name());
                        addStagesIfEligible(stageToBeInvokedInOrder, isEligibleForLenderKyc, isTopup, LenderAssociationStages.CREATE_LEAD, isEligibleForSkipKyc);
                        break;
                    case "DOCUMENT_UPLOAD":
                        log.info("Doc upload request of TrillionLoans success for {}", lenderAssociationDetailsRequest.getApplicationId());
                        LenderAssociationStages docStage = LenderAssociationStatus.SELFIE_UPLOAD_RETRY.name().equalsIgnoreCase(
                                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getKycStatus())
                                ? LenderAssociationStages.SELFIE_UPLOAD
                                : LenderAssociationStages.AADHAR_UPLOAD;
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(getDocUploadStatus(isTopup, docStage));
                        addStagesIfEligible(stageToBeInvokedInOrder, isEligibleForLenderKyc, isTopup, docStage, isEligibleForSkipKyc);
                        break;
                    case "KYC" :
                        log.info("Kyc request of TrillionLoans success for {}", lenderAssociationDetailsRequest.getApplicationId());
                        LenderAssociationStatus kycStatus = isEligibleForSkipKyc ? (isEligibleForLenderKyc ? LenderAssociationStatus.SKIP_KYC_CONSENT_PENDING : LenderAssociationStatus.SKIP_KYC_PENDING) : (isEligibleForLenderKyc ? LenderAssociationStatus.EKYC_PENDING : LenderAssociationStatus.KYC_IN_PROGRESS);
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(kycStatus.name());
                        addStagesIfEligible(stageToBeInvokedInOrder, isEligibleForLenderKyc, isTopup, LenderAssociationStages.KYC, isEligibleForSkipKyc);
                        break;
                    case "BRE":
                        log.info("Bre request of TrillionLoans success for {}", lenderAssociationDetailsRequest.getApplicationId());
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());
                        if(!LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lenderAssociationDetailsRequest.getTopupParentLender())) stageToBeInvokedInOrder.add(LenderAssociationStages.POST_CONSENT);
                        break;
                    default:
                        log.info("invalid response type for trillion retry callback {}", response);
                }
                commonService.manageApplicationState(lenderAssociationDetailsRequest);
                Optional<LenderAssociationStages> failureStage = stageToBeInvokedInOrder.stream().filter(stage -> !commonService.invokeStage(lenderAssociationDetailsRequest, stage.name())).findFirst();
                if (failureStage.isPresent()) {
                    log.info("lender association failed at {} stage for applicationId {}  with lender {}", failureStage.get(), lenderAssociationDetailsRequest.getApplicationId(), lenderAssociationDetailsRequest.getLendingApplication().getLender());
                    MDC.clear();
                    return false;
                }
                if(LenderAssociationStages.LEAD_WRAPPER.name().equalsIgnoreCase(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getStage())
                   && Boolean.FALSE.equals(isEligibleForLenderKyc) && Boolean.FALSE.equals(isEligibleForSkipKyc)) {
                    commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                }
                return true;
            }
            LendingApplicationLenderDetails lenderDetails = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails();
            LenderAssociationStatus currentStatus = LenderAssociationStages.BRE.name().equalsIgnoreCase(lenderDetails.getStage()) ?
                    LenderAssociationStatus.valueOf(lenderDetails.getBreStatus()) : LenderAssociationStatus.valueOf(lenderDetails.getKycStatus());
            LenderAssociationStatus newStatus = getFailureStatus(currentStatus, lenderDetails);
            lenderAssociationDetailsRequest.setLendingApplicationLenderDetails(lenderDetails);
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, newStatus);
        } catch (Exception e) {
            log.error("Exception in processing trillion retry callback {} for applicationId {} {}", response, lenderAssociationDetailsRequest.getApplicationId(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private String getDocUploadStatus(Boolean isTopup, LenderAssociationStages docStage) {
        String kycStatus = LenderAssociationStages.SELFIE_UPLOAD.equals(docStage)
                ? LenderAssociationStatus.SELFIE_UPLOAD_SUCCESS.name()
                : isTopup
                ? LenderAssociationStatus.AADHAR_UPLOAD_IN_PROGRESS.name()
                : LenderAssociationStatus.AADHAR_UPLOAD_SUCCESS.name();
        return kycStatus;
    }

    private void addStagesIfEligible(List<LenderAssociationStages> stagesToInvoked, boolean isEligibleForLenderKyc, boolean isTopup, LenderAssociationStages currStage, boolean isEligibleForSkipKyc) {
        switch (currStage) {
            case CREATE_CLIENT:
                stagesToInvoked.add(LenderAssociationStages.CREATE_LEAD);
            case CREATE_LEAD:
                if (isEligibleForSkipKyc) stagesToInvoked.add(LenderAssociationStages.KYC_VALIDITY);
                break;
            case SELFIE_UPLOAD:
                if (Boolean.FALSE.equals(isEligibleForLenderKyc) && Boolean.FALSE.equals(isEligibleForSkipKyc)) stagesToInvoked.add(LenderAssociationStages.AADHAR_UPLOAD);
                if (Boolean.FALSE.equals(isEligibleForSkipKyc) || Boolean.FALSE.equals(isTopup)) stagesToInvoked.add(LenderAssociationStages.KYC);
                if (isEligibleForSkipKyc) stagesToInvoked.add(LenderAssociationStages.SKIP_KYC);
                break;
            case AADHAR_UPLOAD:
                stagesToInvoked.add(LenderAssociationStages.KYC);
                break;
            case KYC:
                if(isEligibleForSkipKyc && Boolean.FALSE.equals(isEligibleForLenderKyc)) stagesToInvoked.add(LenderAssociationStages.SKIP_KYC);
            default:
        }
    }

    private LenderAssociationStatus getFailureStatus(LenderAssociationStatus currentStatus, LendingApplicationLenderDetails lenderDetails) {
        switch (currentStatus) {
            case CREATE_CLIENT_RETRY:
                lenderDetails.setKycStatus(LenderAssociationStatus.CREATE_CLIENT_FAILED.name());
                return LenderAssociationStatus.CREATE_CLIENT_FAILED;
            case CREATE_LEAD_RETRY:
                lenderDetails.setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
                return LenderAssociationStatus.LEAD_CREATION_FAILED;
            case SELFIE_UPLOAD_RETRY:
                lenderDetails.setKycStatus(LenderAssociationStatus.SELFIE_UPLOAD_FAILED.name());
                return LenderAssociationStatus.SELFIE_UPLOAD_FAILED;
            case AADHAR_UPLOAD_RETRY:
                lenderDetails.setKycStatus(LenderAssociationStatus.AADHAR_UPLOAD_RETRY.name());
                return LenderAssociationStatus.AADHAR_UPLOAD_FAILED;
            case KYC_RETRY:
                lenderDetails.setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
                return LenderAssociationStatus.KYC_FAILED;
            case BRE_RETRY:
                lenderDetails.setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
                return LenderAssociationStatus.RISK_FAILED;
            default:
                throw new RuntimeException("Invalid status for retry callback");
        }
    }
}
