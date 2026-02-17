package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.enums.RejectionStage;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.ugro.UgroConsentRequest;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroConsentResponse;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.LinkedHashMap;

import static com.bharatpe.lending.constant.RejectionReasons.*;

@Slf4j
@Service
public class UgroBreService {
    @Autowired
    CommonService commonService;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    UgroConfig ugroConfig;

    @Transactional
    public Boolean invokeBre(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails()) || ObjectUtils.isEmpty(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadId())) {
                log.error("UGRO: LALD/LeadId is not present for applicationId: {}", lenderAssociationDetailsRequestDto.getApplicationId());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
                return false;
            }

            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.RISK_DECISION.name());

            NBFCRequestDTO<?> breRequest = getPayload(lenderAssociationDetailsRequestDto);
            if (ObjectUtils.isEmpty(breRequest)) {
                log.info("UGRO: bre payload is empty for applicationId: {}", lenderAssociationDetailsRequestDto.getApplicationId());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
                return false;
            }

            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(breRequest, LenderAssociationStages.POST_CONSENT);
            if (nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                UgroConsentResponse breResponseDTO = objectMapper.convertValue(nbfcResponseDto.getData(), UgroConsentResponse.class);
                if (ugroConfig.getSuccessResponse().equalsIgnoreCase(breResponseDTO.getStatus())) {
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_COMPLETED.name());
                    commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequestDto);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("UGRO: error while invoking BRE for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
        return false;
    }


    @Transactional
    public Boolean invokeCounterOffer(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        try {
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.POST_CONSENT_DOWNGRADE.name());
            if (ObjectUtils.isEmpty(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails()) || ObjectUtils.isEmpty(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadId())) {
                log.error("UGRO: LALD/LeadId is not present for applicationId: {}", lenderAssociationDetailsRequestDto.getApplicationId());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.POST_CONSENT_DOWNGRADE_FAILED.name());
                lenderAssociationDetailsRequestDto.getLendingApplication().setRejectionReason(LEAD_NOT_PRESENT);
                lenderAssociationDetailsRequestDto.getLendingApplication().setRejectionStage(RejectionStage.UPDATE_LEAD);
                commonService.manageApplicationStateAndRejectApplication(lenderAssociationDetailsRequestDto);
                return false;
            }

            NBFCRequestDTO<?> counterOfferNbfcRequest = getCounterOfferPayload(lenderAssociationDetailsRequestDto);
            if (ObjectUtils.isEmpty(counterOfferNbfcRequest)) {
                log.info("UGRO: counterOffer payload is empty for applicationId: {}", lenderAssociationDetailsRequestDto.getApplicationId());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.POST_CONSENT_DOWNGRADE_FAILED.name());
                lenderAssociationDetailsRequestDto.getLendingApplication().setRejectionReason(EMPTY_COUNTER_OFFER);
                lenderAssociationDetailsRequestDto.getLendingApplication().setRejectionStage(RejectionStage.UPDATE_LEAD);
                commonService.manageApplicationStateAndRejectApplication(lenderAssociationDetailsRequestDto);
                return false;
            }

            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(counterOfferNbfcRequest, LenderAssociationStages.POST_CONSENT);
            if (nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                UgroConsentResponse counterOfferResponse = objectMapper.convertValue(nbfcResponseDto.getData(), UgroConsentResponse.class);
                if (counterOfferResponse.getStatus().equalsIgnoreCase("success")) {
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setStage(LenderAssociationStages.KYC.name());
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_IN_PROGRESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("UGRO: error while invoking counterOffer for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.POST_CONSENT_DOWNGRADE_FAILED.name());
        lenderAssociationDetailsRequestDto.getLendingApplication().setRejectionReason(UPDATE_LEAD_FAILED);
        lenderAssociationDetailsRequestDto.getLendingApplication().setRejectionStage(RejectionStage.UPDATE_LEAD);
        commonService.manageApplicationStateAndRejectApplication(lenderAssociationDetailsRequestDto);
        return false;
    }

    private NBFCRequestDTO<?> getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails();
        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(UgroConsentRequest.builder()
                            .leadId(lendingApplicationLenderDetails.getLeadId())
                            .consent(Boolean.TRUE)
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("UGRO: Exception in creating BRE payload for application {} {} {}", lenderAssociationDetailsRequest.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private NBFCRequestDTO<?> getCounterOfferPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequestDto.getLendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails();
        try {

            LinkedHashMap<String, Object> identifierMap = new LinkedHashMap<>();
            identifierMap.put("isDowngrade", "true");

            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(UgroConsentRequest.builder()
                            .leadId(lendingApplicationLenderDetails.getLeadId())
                            .consent(Boolean.FALSE)
                            .counterOffer(UgroConsentRequest.CounterOfferDetails.builder()
                                    .amount(lendingApplication.getLoanAmount())
                                    .tenure(lendingApplication.getTenureInMonths())
                                    .interestRate(lendingApplicationLenderDetails.getAnnualRoi())
                                    .processingFeePct(Double.valueOf(String.format("%.4f", (lendingApplication.getProcessingFee() / lendingApplication.getLoanAmount()) * 100)))
                                    .build())
                            .build())
                    .identifier(identifierMap)
                    .build();
        } catch (Exception e) {
            log.info("UGRO: Exception in creating counterOffer payload for application {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    @Transactional
    public Boolean invokeConsentWithFalse(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto){
        try {
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus("DEDUPE_" + LenderAssociationStages.POST_CONSENT.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.DEDUPE_CONSENT_PENDING.name());

            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplicationLenderDetails()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())) {
                log.error("UGRO: LALD/LeadId is not present for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.DEDUPE_CONSENT_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.DEDUPE_CONSENT_FAILED);
                return false;
            }

            NBFCRequestDTO<?> dedupeConsentRequest = getConsentFalsePayload(lenderAssociationDetailsDto);
            if (ObjectUtils.isEmpty(dedupeConsentRequest)) {
                log.info("UGRO: dedupe consent false payload is empty for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.DEDUPE_CONSENT_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.DEDUPE_CONSENT_FAILED);
                return false;
            }

            NBFCResponseDTO<?> consentResponseDto = lenderAPIGateway.invokeStage(dedupeConsentRequest, LenderAssociationStages.POST_CONSENT);
            if (consentResponseDto.getSuccess() && !ObjectUtils.isEmpty(consentResponseDto.getData())) {
                UgroConsentResponse consentResponseDTO = objectMapper.convertValue(consentResponseDto.getData(), UgroConsentResponse.class);
                if (ugroConfig.getSuccessResponse().equalsIgnoreCase(consentResponseDTO.getStatus())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.DEDUPE_CONSENT_SUCCESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("UGRO: error while invoking dedupe consent false for  {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.DEDUPE_CONSENT_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.DEDUPE_CONSENT_FAILED);
        return false;
    }

    private NBFCRequestDTO<?> getConsentFalsePayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails();
        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(UgroConsentRequest.builder()
                            .leadId(lendingApplicationLenderDetails.getLeadId())
                            .consent(Boolean.FALSE)
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("UGRO: Exception in creating BRE payload for application {} {} {}", lenderAssociationDetailsRequest.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}
