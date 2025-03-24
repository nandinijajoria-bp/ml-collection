package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroGetLeadResponse;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

@Slf4j
@Service
public class UgroKycService {

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CommonService commonService;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

    @Autowired
    UgroConfig ugroConfig;

    public Boolean processKycCallback(NBFCResponseDTO nbfcResponseDTO) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponseDTO.getApplicationId())).orElse(null);
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("UGRO: No application found for applicationId : {}", nbfcResponseDTO.getApplicationId());
                return false;
            }

            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || !nbfcResponseDTO.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) {
                log.info("UGRO: No LendingApplicationLenderDetails found with lender {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }

            if (!LenderAssociationStages.KYC.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage()) || !LenderAssociationStatus.KYC_IN_PROGRESS.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycStatus())) {
                log.info("UGRO: Application not in correct state for status check for applicationId {}", lendingApplication.getId());
                return false;
            }

            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                    .applicationId(lendingApplication.getId()).lendingApplication(lendingApplication)
                    .lendingApplicationLenderDetails(lendingApplicationLenderDetails).manageState(true)
                    .modifyLender(enableLenderChange).build();

            if (!ObjectUtils.isEmpty(nbfcResponseDTO.getData()) && nbfcResponseDTO.getSuccess()) {
                UgroGetLeadResponse getLeadResponse = objectMapper.convertValue(nbfcResponseDTO.getData(), UgroGetLeadResponse.class);
                log.info("UGRO: Lead Status Response for {} {}", nbfcResponseDTO.getApplicationId(), getLeadResponse);
                if (!ObjectUtils.isEmpty(getLeadResponse) && nbfcResponseDTO.getSuccess() && getLeadResponse.getStatus().equalsIgnoreCase("APPLICATION_APPROVED")
                        && !ObjectUtils.isEmpty(getLeadResponse.getApprovedParameters().getInstallmentAmount()) && lendingApplication.getEdi().equals(getLeadResponse.getApprovedParameters().getInstallmentAmount())) {
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_COMPLETED.name());

                    // Here the application is approved and now we will check Udyam Status
                    if ((ugroConfig.getSuccessResponse().equalsIgnoreCase(getLeadResponse.getBusinessProofVerification())
                            || (!ObjectUtils.isEmpty(getLeadResponse.getKybRemarks())
                            && ugroConfig.getSuccessResponse().equalsIgnoreCase(getLeadResponse.getKybRemarks().getUdyamFormFilled())))) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setDataUploadStatus(LenderAssociationStatus.UDYAM_REGISTRATION_SUCCESS.name());
                    }else {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setDataUploadStatus(LenderAssociationStatus.UDYAM_REGISTRATION_PENDING.name());
                    }

                    commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                    return true;
                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.KYC_FAILED);
        } catch (Exception e) {
            log.error("UGRO: exception while processing status check for  {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }
}
