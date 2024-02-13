package com.bharatpe.lending.loanV3.services.associationsV2.usfb.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.*;
import com.bharatpe.lending.loanV3.dto.request.usfb.LoanOnboardingRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.usfb.LoanOnboardingCallbackResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.usfb.LoanOnboardingResponseDTO;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactoryV2;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
public class LoanOnboardingService {

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    public Boolean invokeLoanOnboardingFLow(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        try {
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.RISK_DECISION.name());
            if (Objects.isNull(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails())
                    || Objects.isNull(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadId())) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
                return false;
            }
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
            NBFCRequestDTO loanOnboardingRequest = getPayload(lenderAssociationDetailsRequestDto);
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(loanOnboardingRequest, LenderAssociationStages.BRE);
            log.info("loan onboarding response of USFB from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequestDto.getApplicationId());
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());

            if (nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                LoanOnboardingResponseDTO loanOnboardingResponse = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), LoanOnboardingResponseDTO.class);
                if(!ObjectUtils.isEmpty(loanOnboardingResponse)) {
                    if ("Approved".equalsIgnoreCase(loanOnboardingResponse.getData().getStatus())) {
                        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_COMPLETED.name());
                        commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                        return true;
                    }
                    if ("Rejected".equalsIgnoreCase(loanOnboardingResponse.getData().getStatus())) {
                        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
                        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
                        return false;
                    }
                    if("Completely Submitted".equalsIgnoreCase(loanOnboardingResponse.getData().getStatus())) {
                        commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                        return false;
                    }
                }
            }
        } catch (Exception e) {
            log.error("error while invoking loan onboarding of USFB for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
        return false;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        return NBFCRequestDTO.builder()
                .lender(LendingEnum.LENDER.USFB.name())
                .applicationId(lenderAssociationDetailsRequestDto.getApplicationId())
                .productName("LENDING")
                .payload(LoanOnboardingRequestDTO.builder().leadId(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadId()).build())
                .build();
    }

    public Boolean processLoanOnboardingCallback(NBFCResponseDTO nbfcResponseDTO) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponseDTO.getApplicationId())).orElse(null);
            if(ObjectUtils.isEmpty(lendingApplication)) {
                log.info("No application found for applicationId : {}", nbfcResponseDTO.getApplicationId());
                return false;
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
            if(ObjectUtils.isEmpty(lendingApplicationLenderDetails) || !nbfcResponseDTO.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) {
                log.info("No LendingApplicationLenderDetails found with lender {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            if(!LenderAssociationStatus.RISK_IN_PROGRESS.name().equalsIgnoreCase(lendingApplicationLenderDetails.getBreStatus())) {
                log.info("Bre status of {} application is not in progress for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                    .applicationId(lendingApplication.getId())
                    .lendingApplication(lendingApplication)
                    .lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                    .manageState(true)
                    .build();
            if (nbfcResponseDTO.getSuccess() && Objects.nonNull(nbfcResponseDTO.getData())) {
                LoanOnboardingCallbackResponseDTO loanOnboardingCallbackResponse = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), LoanOnboardingCallbackResponseDTO.class);
                log.info("LoanOnboarding callback Response of USFB for {} {}", nbfcResponseDTO.getApplicationId(), loanOnboardingCallbackResponse);
                if(!ObjectUtils.isEmpty(loanOnboardingCallbackResponse)) {
                   if("LOAN_APPROVED".equalsIgnoreCase(loanOnboardingCallbackResponse.getEventName()) && "approved".equalsIgnoreCase(loanOnboardingCallbackResponse.getPayload().getDecisionStatus())) {
                       lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_COMPLETED.name());
                       commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                       return true;
                   }
                   if("LOAN_REJECTED".equalsIgnoreCase(loanOnboardingCallbackResponse.getEventName()) && "Rejected".equalsIgnoreCase(loanOnboardingCallbackResponse.getPayload().getDecisionStatus())) {
                       lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
                       commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.RISK_FAILED);
                       return false;
                   }
                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.RISK_FAILED);
        } catch (Exception e) {
            log.error("exception while processing loan onboarding callback of USFB for  {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }
}
