package com.bharatpe.lending.loanV3.services.associationsV2.oxyzo.impl;

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
import com.bharatpe.lending.loanV3.config.OxyzoConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.oxyzo.OxyzoBreRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.oxyzo.OxyzoBreResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.oxyzo.OxyzoCommonResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.service.AssignmentRuleUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Objects;

import static com.bharatpe.lending.constant.KfsConstants.GST_PERCENTAGE;

@Slf4j
@Service
public class OxyzoBreService {

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    OxyzoConfig oxyzoConfig;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

    @Autowired
    AssignmentRuleUtils assignmentRuleUtils;

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
            log.info("BRE response of Oxyzo from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequestDto.getApplicationId());

            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                log.info("bre request of oxyzo success for {}", lenderAssociationDetailsRequestDto.getApplicationId());

                OxyzoCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDto.getData(), OxyzoCommonResponseDTO.class);

                if(oxyzoConfig.getSuccessErrorCode().equalsIgnoreCase(commonResponseDTO.getErrorCode())) {
                    log.info("Bre response of oxyzo from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequestDto.getApplicationId());
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                    return true;
                }

            }
            else if(Objects.nonNull(nbfcResponseDto)){

                OxyzoCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDto.getData(), OxyzoCommonResponseDTO.class);

                    if(!oxyzoConfig.getSuccessErrorCode().equalsIgnoreCase(commonResponseDTO.getErrorCode())){
                        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadId(null);
                        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
                        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
                        return false;
                    }
            }

        } catch (Exception e) {
            log.error("error while invoking BRE of Oxyzo for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));

        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadId(null);
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

            Double processingFeePercentageWithoutGst = Double.valueOf(String.format("%.4f", (lendingApplication.getProcessingFee() * 100D / (100D + GST_PERCENTAGE)) / (lendingApplication.getLoanAmount()) * 100));
            Double unsecuredPos = assignmentRuleUtils.getUnsecuredPos(lendingRiskVariablesSnapshot.getMetaData());

            Double processingFeeWithoutGst = Double.valueOf(String.format("%.2f", (lendingApplication.getLoanAmount() * processingFeePercentageWithoutGst) / 100D ));

            BigDecimal ediTpvRatio = BigDecimal.valueOf(lendingApplication.getEdi()/(lendingRiskVariablesSnapshot.getMonthlyTpv()/30));
            BigDecimal tpv = BigDecimal.valueOf(lendingRiskVariablesSnapshot.getMonthlyTpv());
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.OXYZO.name())
                    .payload(OxyzoBreRequestDTO.builder()
                            .organisationId(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getCccId())
                            .loanAmount(BigDecimal.valueOf(lendingApplication.getLoanAmount()))
                            .interestRate(BigDecimal.valueOf(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getAnnualRoi()))
                            .referenceLoanId(lendingApplication.getExternalLoanId())
                            .numEdis(lendingApplication.getPayableDays().intValue())
                            .processingAmount(BigDecimal.valueOf(processingFeeWithoutGst))
                            .tpvMultiplier(BigDecimal.valueOf((lendingApplication.getLoanAmount() + unsecuredPos)/lendingRiskVariablesSnapshot.getMonthlyTpv()))
                            .tpv(tpv)
                            .dailyTpv(BigDecimal.valueOf(lendingRiskVariablesSnapshot.getMonthlyTpv()/30))
                            .ediDailyTpvRatio(ediTpvRatio)
                            .productType(oxyzoConfig.getProductType())
                            .callbackUrl(oxyzoConfig.getCallbackUrl())
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("exception occurred while BRE request payload for oxyzo for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
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

                OxyzoCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDTO.getData(), OxyzoCommonResponseDTO.class);
                OxyzoBreResponseDTO breCallbackResponseDTO = objectMapper.convertValue(commonResponseDTO.getData(), OxyzoBreResponseDTO.class);
                log.info("Bre callback Response of Oxyzo for {} {}", nbfcResponseDTO.getApplicationId(), breCallbackResponseDTO);
                if(!ObjectUtils.isEmpty(breCallbackResponseDTO) && oxyzoConfig.getSuccessErrorCode().equalsIgnoreCase(commonResponseDTO.getErrorCode()) && Objects.isNull(breCallbackResponseDTO.getLimitAssigned()) && !ObjectUtils.isEmpty(breCallbackResponseDTO.getLoanId())) {
                    lendingApplicationLenderDetails.setLeadId(breCallbackResponseDTO.getLoanId());
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_COMPLETED.name());
                    commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                    return true;
                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadId(null);
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.RISK_FAILED);
        } catch (Exception e) {
            log.error("exception while processing bre callback of Oxyzo for {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }
}
