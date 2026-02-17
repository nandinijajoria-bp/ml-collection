package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.loanV3.config.TrillionLoansConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLConsentRequestDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
public class TLConsentPostingService {

    private static final String CONSENT_POST_SUCCESS = "CONSENT_POST_SUCCESS";
    private static final String CONSENT_POST_FAILED = "CONSENT_POST_FAILED";

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    TrillionLoansConfig trillionLoansConfig;

    public Boolean invokeConsentPosting(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        try {
            NBFCRequestDTO<?> consentPostingRequest = getPayload(lenderAssociationDetailsRequestDto);
            if (Objects.isNull(consentPostingRequest)) {
                log.info("error in consent posting payload of TrillionLoans for applicationId: {}", lenderAssociationDetailsRequestDto.getApplicationId());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(CONSENT_POST_FAILED);
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                return true;
            }
            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(consentPostingRequest, LenderAssociationStages.POST_CONSENT, trillionLoansConfig.getPostConsentTimeoutThreshold());
            log.info("Post Consent response of TrillionLoans from nbfc : {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequestDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess()) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(CONSENT_POST_SUCCESS);
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
            }
            return true;
        } catch (Exception e) {
            log.error("exception occurred while invoking Post Consent of TrillionLoans for {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(CONSENT_POST_FAILED);
        commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
        return true;
    }

    private NBFCRequestDTO<?> getPayload(LenderAssociationDetailsRequestDto request) {
        LendingApplication app = request.getLendingApplication();
        LendingApplicationLenderDetails details = request.getLendingApplicationLenderDetails();

        try {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

            String createdAtJson = "{\"timestamp\":\"" + formatter.format(app.getCreatedAt()) + "\"}";
            String agreementAtJson = "{\"timestamp\":\"" + formatter.format(app.getAgreementAt()) + "\"}";

            return NBFCRequestDTO.builder()
                    .applicationId(app.getId())
                    .lender(app.getLender())
                    .productName("LENDING")
                    .payload(TLConsentRequestDto.builder()
                            .clientId(Long.valueOf(details.getCccId()))
                            .leadId(Long.valueOf(details.getLeadId()))
                            .consents(Arrays.asList(
                                new TLConsentRequestDto.Consent("PAN_PIN_PAGE_KYC_BPML", app.getIp(), Boolean.TRUE, createdAtJson),
                                new TLConsentRequestDto.Consent("PAN_PIN_PAGE_CREDIT_INFO_BPML", app.getIp(), Boolean.TRUE, createdAtJson),
                                new TLConsentRequestDto.Consent("PAN_PIN_PAGE_PEP_BPML", app.getIp(), Boolean.TRUE, createdAtJson),
                                new TLConsentRequestDto.Consent("PAN_PIN_PAGE_T&C_BPML", app.getIp(), Boolean.TRUE, createdAtJson),
                                new TLConsentRequestDto.Consent("PAN_PIN_PAGE_MFI_BPML", app.getIp(), Boolean.TRUE, createdAtJson),
                                new TLConsentRequestDto.Consent("KEY_FACTOR_STATEMENT_PAGE_BPML", app.getIp(), Boolean.TRUE, agreementAtJson),
                                new TLConsentRequestDto.Consent("REFERENCE_PAGE_BPML", app.getIp(), Boolean.TRUE, agreementAtJson),
                                new TLConsentRequestDto.Consent("ENACH_PAGE_BPML", app.getIp(), Boolean.TRUE, agreementAtJson),
                                new TLConsentRequestDto.Consent("Data sharing consent_BPML", app.getIp(), Boolean.TRUE, createdAtJson),
                                new TLConsentRequestDto.Consent("Current address_PA_BPML", app.getIp(), Boolean.TRUE, agreementAtJson)
                            ))
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating payload of Post Consent of TrillionLoans for {} {} {}", app.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}
