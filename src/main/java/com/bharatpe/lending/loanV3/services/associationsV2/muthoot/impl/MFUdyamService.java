package com.bharatpe.lending.loanV3.services.associationsV2.muthoot.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.UdyamStatusCheckResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.muthoot.MFUdyamRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFUdyamReponseDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFUdyamStatusCheckResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
public class MFUdyamService {
    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    KycUtils kycUtils;

    @Value("${muthoot.udyam.flow.retry.count:3}")
    Integer muthootUdyamFlowRetryCount;

    @Value("${eKyc.redirection.url:}")
    String udyamRedirectionUrl;

    private static final String MF_UDYAM_SUCCESS_STATUS = "form_submitted";
    private static final String MF_UDYAM_PENDING_STATUS = "form_submission_inprogress";

    @Transactional
    public String initiateUdyamFlow(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("Application Id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return null;
            }
            if(LenderAssociationStages.UDYAM.name().equalsIgnoreCase(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadStatus())
               && !ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getNbfcKycAsyncId())) {
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setDataUploadStatus(LenderAssociationStatus.UDYAM_REGISTRATION_INITIATED.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getNbfcKycAsyncId();
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.UDYAM.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setDataUploadStatus(LenderAssociationStatus.UDYAM_REGISTRATION_PENDING.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycRetryCount(0);
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            NBFCRequestDTO request = getPayload(lenderAssociationDetailsDto);
            if (Objects.isNull(request)) {
                log.info("error in udaym payload of muthoot for applicationId: {}", lenderAssociationDetailsDto.getLendingApplication().getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setDataUploadStatus(LenderAssociationStatus.UDYAM_REGISTRATION_FAILED.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return null;
            }
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(request, LenderAssociationStages.UDYAM);
            log.info("udyam response of muthoot from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                MFUdyamReponseDTO response = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), MFUdyamReponseDTO.class);
                if(!ObjectUtils.isEmpty(response) && !ObjectUtils.isEmpty(response.getData()) && !ObjectUtils.isEmpty(response.getData().getUrl())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setDataUploadStatus(LenderAssociationStatus.UDYAM_REGISTRATION_INITIATED.name());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setNbfcKycAsyncId(response.getData().getUrl() + "?partner=bharatPe&redirectUrl=" + udyamRedirectionUrl);
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getNbfcKycAsyncId();
                }
            }
        } catch (Exception e) {
            log.error("exception occurred while udyam of muthoot for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setDataUploadStatus(LenderAssociationStatus.UDYAM_REGISTRATION_FAILED.name());
        commonService.manageApplicationState(lenderAssociationDetailsDto);
        return null;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
        CKycResponseDto cKycResponseDto = kycUtils.getKycData(lendingApplication.getMerchantId());
        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.MUTHOOT.name())
                    .payload(MFUdyamRequestDTO.builder()
                            .custId(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())
                            .pan(cKycResponseDto.getPanNumber())
                            .partner("BharatPe")
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("exception occurred while udyam request payload of muthoot for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    public UdyamStatusCheckResponseDTO udyamStatusCheck(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        UdyamStatusCheckResponseDTO statusCheckResponse = new UdyamStatusCheckResponseDTO();
        statusCheckResponse.setIsUdyamRequired(Boolean.FALSE);
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("Application Id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return statusCheckResponse;
            }
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsDto.getLendingApplicationLenderDetails();
            if(LenderAssociationStatus.UDYAM_REGISTRATION_HARD_FAILED.name().equalsIgnoreCase(lendingApplicationLenderDetails.getDataUploadStatus())) {
                log.info("muthoot udyam status {}, returning udyam required false for applicationId {}", lendingApplicationLenderDetails.getDataUploadStatus(), lendingApplication.getId());
                statusCheckResponse.setUdyamStatus(lendingApplicationLenderDetails.getDataUploadStatus());
                return statusCheckResponse;
            }
            if(!LenderAssociationStatus.UDYAM_REGISTRATION_INITIATED.name().equalsIgnoreCase(lendingApplicationLenderDetails.getDataUploadStatus())) {
                log.info("invalid status {} for udyam status check for applicationId {}", lendingApplicationLenderDetails.getDataUploadStatus(), lendingApplication.getId());
                statusCheckResponse.setUdyamStatus(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getDataUploadStatus());
                statusCheckResponse.setIsUdyamRequired(!LenderAssociationStatus.UDYAM_GENERATION_SUCCESS.name().equalsIgnoreCase(lendingApplicationLenderDetails.getDataUploadStatus()));
                return statusCheckResponse;
            }
            NBFCRequestDTO request = NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .topup(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()))
                    .productName("LENDING")
                    .build();
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(request, LenderAssociationStages.UDYAM_STATUS_CHECK);
            log.info("udyam status check response of muthoot from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                MFUdyamStatusCheckResponseDTO response = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), MFUdyamStatusCheckResponseDTO.class);
                if(!ObjectUtils.isEmpty(response) && !ObjectUtils.isEmpty(response.getData())) {
                    if (MF_UDYAM_SUCCESS_STATUS.equalsIgnoreCase(response.getData().getCode())) {
                        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setDataUploadStatus(LenderAssociationStatus.UDYAM_REGISTRATION_SUCCESS.name());
                        commonService.manageApplicationState(lenderAssociationDetailsDto);
                        statusCheckResponse.setUdyamStatus(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getDataUploadStatus());
                        return statusCheckResponse;
                    }
                    if(MF_UDYAM_PENDING_STATUS.equalsIgnoreCase(response.getData().getCode())) {
                        int currentRetryCount = ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getKycRetryCount()) ? 0 : lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getKycRetryCount();
                        lendingApplicationLenderDetails.setKycRetryCount(currentRetryCount + 1);
                        lendingApplicationLenderDetails.setDataUploadStatus(LenderAssociationStatus.UDYAM_REGISTRATION_FAILED.name());
                        statusCheckResponse.setIsUdyamRequired(Boolean.TRUE);
                        if(lendingApplicationLenderDetails.getKycRetryCount() >= muthootUdyamFlowRetryCount) {
                            log.info("marking data upload status {} as udyam flow retry count exhausted for applicationId {}", LenderAssociationStatus.UDYAM_REGISTRATION_HARD_FAILED, lendingApplication.getId());
                            lendingApplicationLenderDetails.setDataUploadStatus(LenderAssociationStatus.UDYAM_REGISTRATION_HARD_FAILED.name());
                            statusCheckResponse.setIsUdyamRequired(Boolean.FALSE);
                        }
                        statusCheckResponse.setUdyamStatus(lendingApplicationLenderDetails.getDataUploadStatus());
                        lenderAssociationDetailsDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
                        commonService.manageApplicationState(lenderAssociationDetailsDto);
                        return statusCheckResponse;
                    }
                }
            }
            statusCheckResponse.setUdyamStatus(LenderAssociationStatus.UDYAM_STATUS_CHECK_FAILED.name());
            lenderAssociationDetailsDto.setLendingApplicationLenderDetails(lendingApplicationLenderDetails);
            commonService.manageApplicationState(lenderAssociationDetailsDto);
        } catch (Exception e) {
            log.error("exception occurred while udyam status of muthoot for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
            statusCheckResponse.setIsUdyamRequired(Boolean.TRUE);
        }
        return statusCheckResponse;
    }
}
