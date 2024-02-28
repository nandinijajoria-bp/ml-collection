package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLConsentRequestDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
public class TLConsentPostingService {

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    public Boolean invokeConsentPosting(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        try {
            NBFCRequestDTO<?> consentPostingRequest = getPayload(lenderAssociationDetailsRequestDto);
            if (Objects.isNull(consentPostingRequest)) {
                log.info("error in consent posting payload of TrillionLoans for applicationId: {}", lenderAssociationDetailsRequestDto.getApplicationId());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(getLeadStatus(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails(), false));
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                return true;
            }
            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(consentPostingRequest, LenderAssociationStages.POST_CONSENT);
            log.info("Post Consent response of TrillionLoans from nbfc : {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequestDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess()) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(getLeadStatus(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails(), true));
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                return true;
            }
        } catch (Exception e) {
            log.error("exception occurred while invoking Post Consent of TrillionLoans for {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(getLeadStatus(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails(), false));
        commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
        return true;
    }

    private NBFCRequestDTO<?> getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails();

        if(ObjectUtils.isEmpty(lendingApplication) || ObjectUtils.isEmpty(lendingApplicationLenderDetails)){
            log.error("Lending Application / Lending Application Lender Details not found for application id : {}", lenderAssociationDetailsRequest.getApplicationId());
            return null;
        }

        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(TLConsentRequestDto.builder()
                            .clientId(Long.valueOf(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getCccId()))
                            .leadId(Long.valueOf(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLeadId()))
                            .consentKey("BRE".equalsIgnoreCase(lendingApplicationLenderDetails.getStage()) ? "TRILLION_BUREAU_CKYC_CONSENT" : "TRILLION_AGREEMENT_CONSENT")
                            .ipAddress(lendingApplication.getIp())
                            .isAccepted(Boolean.TRUE)
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating payload of Post Consent of TrillionLoans for {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String getLeadStatus(LendingApplicationLenderDetails lendingApplicationLenderDetails, Boolean success) {
        if (success && "BRE".equalsIgnoreCase(lendingApplicationLenderDetails.getStage()))
            return LenderAssociationStatus.BRE_CONSENT_SUCCESS.name();
        else if (!success && "BRE".equalsIgnoreCase(lendingApplicationLenderDetails.getStage()))
            return LenderAssociationStatus.BRE_CONSENT_FAILED.name();
        else if (success && "ASSC_COMPLETED".equalsIgnoreCase(lendingApplicationLenderDetails.getStage()))
            return LenderAssociationStatus.AGREEMENT_CONSENT_SUCCESS.name();
        else if (!success && "ASSC_COMPLETED".equalsIgnoreCase(lendingApplicationLenderDetails.getStage()))
            return LenderAssociationStatus.AGREEMENT_CONSENT_FAILED.name();
        else
            return "";
    }
}
