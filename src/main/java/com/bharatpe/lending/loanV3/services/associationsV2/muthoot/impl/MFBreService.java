package com.bharatpe.lending.loanV3.services.associationsV2.muthoot.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.muthoot.MFAcceptOfferRequestDTO;
import com.bharatpe.lending.loanV3.dto.request.muthoot.MFBreRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFAcceptOfferResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFBreCallbackResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFBreResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.*;


@Slf4j
@Service
public class MFBreService {

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

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

    @Transactional
    public boolean invokeBre(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
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
            NBFCRequestDTO breRequestPayload = getBreRequestPayload(lenderAssociationDetailsRequestDto);
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(breRequestPayload, LenderAssociationStages.BRE);
            log.info("BRE response of Muthoot from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequestDto.getApplicationId());

            if (Objects.nonNull(nbfcResponseDto.getData()) && nbfcResponseDto.getSuccess()) {
                MFBreResponseDTO breResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), MFBreResponseDTO.class);
                if ("GNO-S-000".equalsIgnoreCase(breResponseDTO.getStatusCode())) {
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                    return true;
                }
            }

        } catch (Exception e) {
            log.error("error while invoking BRE of Muthoot for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
        return false;
    }

    private NBFCRequestDTO getBreRequestPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
        try {
            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
            if (ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)) {
                throw new RuntimeException("Lending risk variable snapshot not found for application");
            }
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.MUTHOOT.name())
                    .payload(MFBreRequestDTO.builder()
                            .customerID(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())
                            .riskVariables(MFBreRequestDTO.RiskVariables.builder()
                                    .tpv(getValueOrDefault(lendingRiskVariablesSnapshot.getMonthlyTpv(), 0D))
                                    .loanAmount(lendingApplication.getLoanAmount())
                                    .tenure(lendingApplication.getTenureInMonths())
                                    .riskGroup(lendingRiskVariablesSnapshot.getRiskGroup())
                                    .riskSegment(lendingRiskVariablesSnapshot.getRiskSegment().name())
                                    .netFreeIncome(getValueOrDefault(lendingRiskVariablesSnapshot.getMonthlyNfi(), 0D))
                                    .pincodeColour(lendingRiskVariablesSnapshot.getPincodeColor().name())
                                    .build())
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("exception occurred while KYC request payload for createLead for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    @Async("lenderPoolTaskExecutor")
    public boolean invokeAcceptOffer(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto, MFBreCallbackResponseDTO callbackResponse) {
        try {
            if (Objects.isNull(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails())
                    || Objects.isNull(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadId())) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.ACCEPT_OFFER_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.ACCEPT_OFFER_FAILED);
                return false;
            }
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.ACCEPT_OFFER_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
            NBFCRequestDTO acceptOfferPayload = getAcceptOfferPayload(lenderAssociationDetailsRequestDto, callbackResponse);
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(acceptOfferPayload, LenderAssociationStages.ACCEPT_OFFER);
            log.info("Accept Offer response of Muthoot from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequestDto.getApplicationId());

            if (Objects.nonNull(nbfcResponseDto.getData()) && nbfcResponseDto.getSuccess()) {
                MFAcceptOfferResponseDTO acceptOfferResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), MFAcceptOfferResponseDTO.class);
                if ("AOP-S-000".equalsIgnoreCase(acceptOfferResponseDTO.getStatusCode()) && "ok".equalsIgnoreCase(acceptOfferResponseDTO.getData().getMessage())) {
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.ACCEPT_OFFER_SUCCESS.name());
                    commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequestDto);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("error while invoking Accept Offer of Muthoot for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.ACCEPT_OFFER_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.ACCEPT_OFFER_FAILED);
        return false;
    }

    private NBFCRequestDTO getAcceptOfferPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, MFBreCallbackResponseDTO breCallbackResponseDTO) {
        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
        try {
            MFAcceptOfferRequestDTO.RequestData requestData = MFAcceptOfferRequestDTO.RequestData.builder()
                    .offerID(breCallbackResponseDTO.getData().getOffers().get(0).getOfferID())
                    .amount(lendingApplication.getLoanAmount())
                    .tenure(lendingApplication.getTenureInMonths())
                    .tenureType("MONTH")
                    .processingFee(lendingApplication.getProcessingFee())
                    .interest(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getAnnualRoi())
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.MUTHOOT.name())
                    .payload(MFAcceptOfferRequestDTO.builder()
                            .customerID(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())
                            .offerDetails(requestData)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("exception occurred while KYC request payload for createLead for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    public Boolean processMFBreCallback(NBFCResponseDTO nbfcResponseDTO) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponseDTO.getApplicationId())).orElse(null);
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("No application found for applicationId : {}", nbfcResponseDTO.getApplicationId());
                return false;
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || !nbfcResponseDTO.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) {
                log.info("No LendingApplicationLenderDetails found with lender {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            if (!LenderAssociationStages.BRE.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())
                    || !LenderAssociationStatus.RISK_IN_PROGRESS.name().equalsIgnoreCase(lendingApplicationLenderDetails.getBreStatus())) {
                log.info("Application not in correct state for BRE callback for applicationId {}", lendingApplication.getId());
                return false;
            }
            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                    .applicationId(lendingApplication.getId())
                    .lendingApplication(lendingApplication)
                    .lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                    .modifyLender(enableLenderChange)
                    .manageState(true)
                    .build();
            if (Objects.nonNull(nbfcResponseDTO.getData()) && nbfcResponseDTO.getSuccess()) {
                MFBreCallbackResponseDTO breCallbackResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), MFBreCallbackResponseDTO.class);
                log.info("BRE callback Response of Muthoot for {} {}", nbfcResponseDTO.getApplicationId(), breCallbackResponseDTO);
                if (!ObjectUtils.isEmpty(breCallbackResponseDTO)) {
                    if ("COMPLETED".equalsIgnoreCase(breCallbackResponseDTO.getData().getStatus()) || "SUCCESS".equalsIgnoreCase(breCallbackResponseDTO.getData().getStatus())) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_COMPLETED.name());
                        commonService.manageApplicationState(lenderAssociationDetailsRequest);
                        invokeAcceptOffer(lenderAssociationDetailsRequest, breCallbackResponseDTO);
                        return true;
                    } else if ("REJECTED".equalsIgnoreCase(breCallbackResponseDTO.getData().getStatus()) || "FAILED".equalsIgnoreCase(breCallbackResponseDTO.getData().getStatus())) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
                        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.RISK_FAILED);
                        return false;
                    }
                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.RISK_FAILED);
        } catch (Exception e) {
            log.error("exception while processing BRE callback of Muthoot for  {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private <T> T getValueOrDefault(T value, T defaultValue) {
        if (!ObjectUtils.isEmpty(value)) {
            return value;
        }
        return defaultValue;
    }

}
