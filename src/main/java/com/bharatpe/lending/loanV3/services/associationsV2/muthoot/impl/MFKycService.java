package com.bharatpe.lending.loanV3.services.associationsV2.muthoot.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.muthoot.MFKycRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFKycCallbackResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFKycResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.AssociationServiceUtil;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
public class MFKycService {

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
    AssociationServiceUtil associationServiceUtil;

    @Transactional
    public boolean invokeKyc(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("Application Id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.OKYC.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.OKYC_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
            NBFCRequestDTO kycRequestPayload = getKycRequestPayload(lenderAssociationDetailsDto);
            if (Objects.isNull(kycRequestPayload)) {
                log.info("error in KYC payload of Muthoot for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.OKYC_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.OKYC_FAILED);
                return false;
            }
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(kycRequestPayload, LenderAssociationStages.KYC);
            log.info("KYC response of Muthoot from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                log.info("KYC request of Muthoot success for {}", lenderAssociationDetailsDto.getApplicationId());
                MFKycResponseDTO kycResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), MFKycResponseDTO.class);
                if ("TOK-S-000".equalsIgnoreCase(kycResponseDTO.getStatusCode())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.OKYC_IN_PROGRESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }

            }
        } catch (Exception e) {
            log.error("exception occurred while KYC of Muthoot for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.OKYC_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.OKYC_FAILED);
        return false;

    }

    private NBFCRequestDTO getKycRequestPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
        CKycResponseDto cKycResponseDto = lenderAssociationDetailsDto.getCKycResponseDto();
        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.MUTHOOT.name())
                    .payload(MFKycRequestDTO.builder()
                            .customerID(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())
                            .program("EDI")
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("exception occurred while KYC request payload for createLead for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    public Boolean processMFKycCallback(NBFCResponseDTO nbfcResponseDTO) {
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
            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                    .applicationId(lendingApplication.getId())
                    .lendingApplication(lendingApplication)
                    .lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                    .manageState(true)
                    .modifyLender(true)
                    .build();
            if (Objects.nonNull(nbfcResponseDTO.getData()) && nbfcResponseDTO.getSuccess()) {
                MFKycCallbackResponseDTO kycCallbackResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), MFKycCallbackResponseDTO.class);
                log.info("KYC callback Response of Muthoot for {} {}", nbfcResponseDTO.getApplicationId(), kycCallbackResponseDTO);
                if (!isApplicationStateValidForCallback(lendingApplicationLenderDetails, kycCallbackResponseDTO)) {
                    log.info("Application not in correct state for {} callback for applicationId {}", lendingApplicationLenderDetails.getLeadStatus(), lendingApplication.getId());
                    return false;
                }
                if (!ObjectUtils.isEmpty(kycCallbackResponseDTO)) {
                    if ("APPROVED".equalsIgnoreCase(kycCallbackResponseDTO.getData().getStatus()) || "SUCCESS".equalsIgnoreCase(kycCallbackResponseDTO.getData().getStatus())) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(getStatus(kycCallbackResponseDTO).name());
                        commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                        return true;
                    }
                    if ("PROCESSING".equalsIgnoreCase(kycCallbackResponseDTO.getData().getStatus()) && ("TRIGGER_IDENTITY_CHECKS".equalsIgnoreCase(kycCallbackResponseDTO.getData().getAction()) || "TRIGGER_IDENTITY_CHECK".equalsIgnoreCase(kycCallbackResponseDTO.getData().getAction()))) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(getStatus(kycCallbackResponseDTO).name());
                        commonService.manageApplicationState(lenderAssociationDetailsRequest);
                        invokeNextStage(lenderAssociationDetailsRequest, kycCallbackResponseDTO);
                        return true;
                    }
                    if ("REJECTED".equalsIgnoreCase(kycCallbackResponseDTO.getData().getStatus()) || "FAILED".equalsIgnoreCase(kycCallbackResponseDTO.getData().getStatus())) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(getStatus(kycCallbackResponseDTO).name());
                        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, getStatus(kycCallbackResponseDTO));
                        return false;
                    }
                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.KYC_FAILED);
        } catch (Exception e) {
            log.error("exception while processing KYC callback of Muthoot for  {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    @Async("lenderPoolTaskExecutor")
    private Boolean invokeNextStage(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest, MFKycCallbackResponseDTO kycCallbackResponseDTO) {
        if("TRIGGER_IDENTITY_CHECKS".equalsIgnoreCase(kycCallbackResponseDTO.getData().getAction()) || "TRIGGER_IDENTITY_CHECK".equalsIgnoreCase(kycCallbackResponseDTO.getData().getAction())){
            return associationServiceUtil.invokeDocUploadService(lenderAssociationDetailsRequest.getLendingApplication().getLender(), lenderAssociationDetailsRequest, "SELFIE_UPLOAD");
        }
        return false;
    }

    private LenderAssociationStatus getStatus(MFKycCallbackResponseDTO kycCallbackResponseDTO) {
        if("TRIGGER_IDENTITY_CHECKS".equalsIgnoreCase(kycCallbackResponseDTO.getData().getAction())) {
            switch (kycCallbackResponseDTO.getData().getStatus()) {
                case "PROCESSING":
                case "COMPLETED":
                case "SUCCESS":
                    return LenderAssociationStatus.OKYC_COMPLETED;
                case "REJECTED":
                case "FAILED":
                    return LenderAssociationStatus.OKYC_FAILED;
                default:
                    return LenderAssociationStatus.OKYC_FAILED;
            }
        } else {
            switch (kycCallbackResponseDTO.getData().getStatus()) {
                case "APPROVED":
                case "COMPLETED":
                case "SUCCESS":
                    return LenderAssociationStatus.SELFIE_UPLOAD_SUCCESS;
                case "REJECTED":
                case "FAILED":
                    return LenderAssociationStatus.SELFIE_UPLOAD_FAILED;
                default:
                    return LenderAssociationStatus.SELFIE_UPLOAD_FAILED;
            }
        }
    }

    private boolean isApplicationStateValidForCallback(LendingApplicationLenderDetails lendingApplicationLenderDetails, MFKycCallbackResponseDTO kycCallbackResponseDTO) {
        if ("TRIGGER_IDENTITY_CHECKS".equalsIgnoreCase(kycCallbackResponseDTO.getData().getAction())) {
            return (LenderAssociationStages.OKYC.name().equalsIgnoreCase(lendingApplicationLenderDetails.getLeadStatus())
                    && LenderAssociationStatus.OKYC_IN_PROGRESS.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycStatus()));
        }
        return ("SELFIE_UPLOAD".equalsIgnoreCase(lendingApplicationLenderDetails.getLeadStatus())
                && LenderAssociationStatus.SELFIE_UPLOAD_IN_PROGRESS.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycStatus()));
    }

}
