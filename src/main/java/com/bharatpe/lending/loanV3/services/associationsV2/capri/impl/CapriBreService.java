package com.bharatpe.lending.loanV3.services.associationsV2.capri.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.capri.CapriBreDataTableRequestDTO;
import com.bharatpe.lending.loanV3.dto.request.capri.CapriBreRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.capri.CapriBreCallbackResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.capri.CapriBreResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class CapriBreService {

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

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    MerchantService merchantService;

    @Transactional
    public Boolean invokeBre(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
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
            NBFCRequestDTO breRequest = getPayload(lenderAssociationDetailsRequestDto);
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(breRequest, LenderAssociationStages.BRE);
            log.info("Bre response of Capri from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequestDto.getApplicationId());
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());

            if (nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                CapriBreResponseDTO breResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), CapriBreResponseDTO.class);
                if("SUCCESS".equalsIgnoreCase(breResponseDTO.getStatus())) {
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("error while invoking Bre of Capri for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
        return false;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails();
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
        if(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)) {
            log.info("lending risk variable snapshot not found for applicationId : {}", lendingApplication.getId());
            throw new RuntimeException("lending risk variable snapshot not found for capri application " + lendingApplication.getId());
        }
        try {
            Double sixtyDaysTpv = ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getSummaryTpv()) ? 0D : lendingRiskVariablesSnapshot.getSummaryTpv() * 60;
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(CapriBreRequestDTO.builder()
                            .ctaKey("BPECTA")
                            .leadId(lendingApplicationLenderDetails.getLeadId())
                            .clientId(lendingApplicationLenderDetails.getCccId())
                            .breDataTableRequest(CapriBreDataTableRequestDTO.builder()
                                    .loanSegment(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getRiskSegment()) ? "" : lendingRiskVariablesSnapshot.getRiskSegment().name())
                                    .tpv(String.valueOf(sixtyDaysTpv.intValue()))
                                    .nfi(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getMonthlyNfi()) ? "" : String.valueOf(lendingRiskVariablesSnapshot.getMonthlyNfi().intValue()))
                                    .tenure(String.valueOf(lendingApplication.getTenureInMonths()))
                                    .pincodeColor(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getPincodeColor()) ? "" : lendingRiskVariablesSnapshot.getPincodeColor().name())
                                    .riskGroup(lendingRiskVariablesSnapshot.getRiskGroup())
                                    .locale("en")
                                    .edi(ObjectUtils.isEmpty(lendingApplication.getEdi()) ? "0" : String.valueOf(lendingApplication.getEdi().intValue()))
                                    .pincode(String.valueOf(lendingApplication.getPincode()))// shop pincode
                                    .apr(lendingApplicationLenderDetails.getAnnualRoi())
                                    .build())
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating BRE payload of Capri for application {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    public Boolean processBreCallback(NBFCResponseDTO nbfcResponseDTO) {
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
                    .modifyLender(enableLenderChange)
                    .manageState(true)
                    .build();
            if (nbfcResponseDTO.getSuccess() && Objects.nonNull(nbfcResponseDTO.getData())) {
                CapriBreCallbackResponseDTO breCallbackResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), CapriBreCallbackResponseDTO.class);
                log.info("Bre callback Response of Capri for {} {}", nbfcResponseDTO.getApplicationId(), breCallbackResponseDTO);
                if(!ObjectUtils.isEmpty(breCallbackResponseDTO)) {
                    if("Approved".equalsIgnoreCase(breCallbackResponseDTO.getStatus())) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_COMPLETED.name());
                        commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                        return true;
                    }
                    if("Rejected".equalsIgnoreCase(breCallbackResponseDTO.getStatus())) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
                        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.RISK_FAILED);
                        return false;
                    }
                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.RISK_FAILED);
        } catch (Exception e) {
            log.error("exception while processing bre callback of Capri for {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }
}
