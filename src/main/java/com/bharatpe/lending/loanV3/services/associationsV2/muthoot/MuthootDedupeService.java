package com.bharatpe.lending.loanV3.services.associationsV2.muthoot;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.PricingExperimentDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.constant.CommonConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.muthoot.MuthootPreQualificationRequest;
import com.bharatpe.lending.loanV3.dto.response.muthoot.*;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
public class MuthootDedupeService {

    @Autowired
    private ILenderAPIGateway lenderAPIGateway;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CommonService commonService;
    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;
    @Autowired
    private LendingApplicationDao lendingApplicationDao;
    @Autowired
    private KycUtils kycUtils;
    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

    public Boolean invokeDedupeCheck(Long applicationId, LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        log.info("Muthoot dedupe check invoked for applicationId: {}", applicationId);
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.DEDUPE_CHECK.name());

        commonService.manageApplicationState(lenderAssociationDetailsDto);

        if (Objects.isNull(lenderAssociationDetailsDto.getLendingApplicationLenderDetails())
                || Objects.isNull(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())) {
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.DEDUPE_CHECK_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.DEDUPE_CHECK_FAILED);
            return false;
        }

        MuthootPreQualificationRequest muthootPreQualificationRequest = getPreQualificationPayload(lenderAssociationDetailsDto.getLendingApplicationLenderDetails());

        NBFCRequestDTO nbfcRequestDTO = NBFCRequestDTO.builder()
                .applicationId(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getApplicationId())
                .lender(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLender())
                .productName("LENDING")
                .payload(muthootPreQualificationRequest)
                .build();

        try {
            NBFCResponseDTO nbfcResponseDTO = lenderAPIGateway.invokeStage(nbfcRequestDTO, LenderAssociationStages.DEDUPE_CHECK);
            log.info("Muthoot pre-qualification response from nbfc: {} with applicationId: {}", nbfcResponseDTO, lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getApplicationId());
            if (Objects.nonNull(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && Objects.nonNull(nbfcResponseDTO.getData())) {
                MuthootPreQualificationResponse muthootPreQualificationResponse = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), MuthootPreQualificationResponse.class);
                if(Arrays.asList("TPQ-S-000","FBX-S-208").contains(muthootPreQualificationResponse.getStatusCode())) {
                    lenderAssociationDetailsDto.setLendingApplicationLenderDetails(setLeadStatus(lenderAssociationDetailsDto.getLendingApplicationLenderDetails(), LenderAssociationStatus.DEDUPE_CHECK_IN_PROGRESS.name()));
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }
            }
        }catch (Exception e){
            log.error("Exception while invoking Muthoot dedupe check for applicationId: {} , error: {}", applicationId, e.getMessage(), e);
        }

        lenderAssociationDetailsDto.setLendingApplicationLenderDetails(setLeadStatus(lenderAssociationDetailsDto.getLendingApplicationLenderDetails(), LenderAssociationStatus.DEDUPE_CHECK_FAILED.name()));
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.DEDUPE_CHECK_FAILED);
        return Boolean.FALSE;
    }

    private MuthootPreQualificationRequest getPreQualificationPayload(LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        return MuthootPreQualificationRequest.builder()
                .customerId(lendingApplicationLenderDetails.getLeadId())
                .program("EDI")
                .build();
    }

    private LendingApplicationLenderDetails setLeadStatus(LendingApplicationLenderDetails lendingApplicationLenderDetails, String status) {
        if ("LEAD_WRAPPER".equalsIgnoreCase(lendingApplicationLenderDetails.getStage())) {
            lendingApplicationLenderDetails.setKycStatus(status);
        } else {
            lendingApplicationLenderDetails.setSanctionStatus(status);
        }
        return lendingApplicationLenderDetails;
    }


    public Boolean processDedupeCallback(NBFCResponseDTO nbfcResponseDTO) {
        log.info("Muthoot dedupe callback processing for applicationId: {}", nbfcResponseDTO.getApplicationId());

        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findByApplicationIdAndLender(Long.valueOf(nbfcResponseDTO.getApplicationId()), nbfcResponseDTO.getLender());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || !nbfcResponseDTO.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) {
            log.info("No LendingApplicationLenderDetails found with lender {} for applicationId {}", nbfcResponseDTO.getLender(), nbfcResponseDTO.getApplicationId());
            return false;
        }

        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponseDTO.getApplicationId()));
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("No application found for applicationId : {}", nbfcResponseDTO.getApplicationId());
            return false;
        }

        LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = new LenderAssociationDetailsRequestDto();
        lenderAssociationDetailsRequest.setApplicationId(Long.valueOf(nbfcResponseDTO.getApplicationId()));
        lenderAssociationDetailsRequest.setLendingApplication(lendingApplication.get());
        lenderAssociationDetailsRequest.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
        lenderAssociationDetailsRequest.setModifyLender(enableLenderChange);
        lenderAssociationDetailsRequest.setManageState(Boolean.TRUE);


        try {
            log.info("Dedupe callback received {}", nbfcResponseDTO);
            if ( nbfcResponseDTO.getSuccess() && Objects.nonNull(nbfcResponseDTO.getData()) ) {

                MuthootPreQualificationStatusCheckResponse muthootPreQualificationStatusCheckResponse = objectMapper.readValue(
                        objectMapper.writeValueAsString(nbfcResponseDTO.getData()), MuthootPreQualificationStatusCheckResponse.class );

                MuthootPreQualificationStatusResponseData muthootPreQualificationStatusResponseData = muthootPreQualificationStatusCheckResponse.getData();

                if (!ObjectUtils.isEmpty(muthootPreQualificationStatusResponseData) && !CommonConstants.FAILED.equalsIgnoreCase(muthootPreQualificationStatusResponseData.getStatus())) {

                    List<MuthootPreQualificationStatusResponseResult> results = muthootPreQualificationStatusResponseData.getResults();
                    boolean dedupeStatus = true;

                    if (CollectionUtils.isEmpty(results)) {
                        log.info("Dedupe results empty for applicationId: {}", nbfcResponseDTO.getApplicationId());
                        dedupeStatus = false;
                    }
                    if( dedupeStatus ) {
                        for (MuthootPreQualificationStatusResponseResult result : results) {
                            log.info("Dedupe result for applicationId: {} is {}", nbfcResponseDTO.getApplicationId(), result);
                            MuthootPreQualificationStatusResponseRuleDecisions ruleDecisions = result.getRuleDecisions();

                            if (ObjectUtils.isEmpty(ruleDecisions)
                                    || ObjectUtils.isEmpty(ruleDecisions.getMnrlCheck())
                                    || !ruleDecisions.getMnrlCheck()
                            ) {
                                log.info("Dedupe check failed for applicationId: {} with ruleDecisions: {}", nbfcResponseDTO.getApplicationId(), ruleDecisions);
                                dedupeStatus = false;
                                break;
                            }

                        }
                    }

                    if (dedupeStatus) {
                        log.info("Dedupe check passed for applicationId: {}", nbfcResponseDTO.getApplicationId());
                        setLeadStatus(lendingApplicationLenderDetails, LenderAssociationStatus.DEDUPE_CHECK_COMPLETED.name());
                        lenderAssociationDetailsRequest.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
                        commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                        return true;
                    }
                }
            }
            log.info("Dedupe check failed for applicationId: {}", nbfcResponseDTO.getApplicationId());
            lenderAssociationDetailsRequest.setLendingApplicationLenderDetails(setLeadStatus(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails(), LenderAssociationStatus.DEDUPE_CHECK_FAILED.name()));
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.DEDUPE_CHECK_FAILED);
            return false;
        } catch (Exception e) {
            log.error("Exception in consuming Dedupe callback of {} for applicationId {}  {} {}", nbfcResponseDTO.getLender(), nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        log.info("Dedupe check failed for applicationId: {}", nbfcResponseDTO.getApplicationId());
        lenderAssociationDetailsRequest.setLendingApplicationLenderDetails(setLeadStatus(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails(), LenderAssociationStatus.DEDUPE_CHECK_FAILED.name()));
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.DEDUPE_CHECK_FAILED);
        return false;
    }
}
