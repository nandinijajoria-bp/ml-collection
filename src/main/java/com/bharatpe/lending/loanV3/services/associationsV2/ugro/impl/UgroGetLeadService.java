package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.UdyamStatusCheckResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.ugro.UgroGetLeadRequest;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroGetLeadResponse;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
public class UgroGetLeadService {

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UgroConfig ugroConfig;

    @Transactional
    public boolean invokeGetLead(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("UGRO: application id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }

            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.GET_LEAD_PENDING.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.GET_LEAD.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);

            NBFCRequestDTO<?> getLeadRequest = getPayload(lenderAssociationDetailsDto);
            if (ObjectUtils.isEmpty(getLeadRequest)) {
                log.info("UGRO: error in create lead payload for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.GET_LEAD_FAILED.name());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.GET_LEAD.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return false;
            }
            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(getLeadRequest, LenderAssociationStages.GET_LEAD);
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                UgroGetLeadResponse getLeadResponse = objectMapper.convertValue(nbfcResponseDto.getData(), UgroGetLeadResponse.class);
                if (!ObjectUtils.isEmpty(getLeadResponse) && Arrays.asList(ugroConfig.getClosedResponse(), ugroConfig.getRejectedResponse()).contains(getLeadResponse.getStatus())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.GET_LEAD_FAILED.name());
                    commonService.manageApplicationStateAndRejectApplication(lenderAssociationDetailsDto);
                    return false;
                }

                //TODO: Need to change the conditions only for penny drop now as udyam is moved to early stage (This will be done after 15 days)
                // But for old application we need the check of udyam also
                if (!ObjectUtils.isEmpty(getLeadResponse)
                        && ugroConfig.getSuccessResponse().equalsIgnoreCase(getLeadResponse.getBankAccountVerification())
                        && (ugroConfig.getSuccessResponse().equalsIgnoreCase(getLeadResponse.getBusinessProofVerification())
                        || (!ObjectUtils.isEmpty(getLeadResponse.getKybRemarks())
                        && ugroConfig.getSuccessResponse().equalsIgnoreCase(getLeadResponse.getKybRemarks().getUdyamFormFilled())))) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_SUCCESS.name());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setDataUploadStatus(LenderAssociationStatus.UDYAM_REGISTRATION_SUCCESS.name());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.GET_LEAD_SUCCESS.name());
                    commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsDto);
                    return true;
                } else if (!ObjectUtils.isEmpty(getLeadResponse)
                        && ugroConfig.getSuccessResponse().equalsIgnoreCase(getLeadResponse.getBankAccountVerification())) {
                    // This block of code is only for old applications because new applications will only be able to reach here after completing udyam
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_SUCCESS.name());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setDataUploadStatus(LenderAssociationStatus.UDYAM_REGISTRATION_PENDING.name());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.GET_LEAD_SUCCESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return false;
                }

                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.GET_LEAD_SUCCESS.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
            }
        } catch (Exception e) {
            log.info("UGRO: exception occurred while processing get lead for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.GET_LEAD_FAILED.name());
        commonService.manageApplicationState(lenderAssociationDetailsDto);
        return false;
    }

    @Transactional
    public UdyamStatusCheckResponseDTO invokeUdyamStatusCheck(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        UdyamStatusCheckResponseDTO statusCheckResponse = new UdyamStatusCheckResponseDTO();
        statusCheckResponse.setIsUdyamRequired(Boolean.TRUE);
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.error("UGRO: application id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return statusCheckResponse;
            }
            log.info("UGRO: starting GetLead: UdyamStatusCheck for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
            NBFCRequestDTO<?> getLeadRequest = getPayload(lenderAssociationDetailsDto);
            if (ObjectUtils.isEmpty(getLeadRequest)) {
                log.error("UGRO: error in getLead: udyam statusCheck payload for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                return statusCheckResponse;
            }

            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(getLeadRequest, LenderAssociationStages.GET_LEAD);
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                UgroGetLeadResponse getLeadResponse = objectMapper.convertValue(nbfcResponseDto.getData(), UgroGetLeadResponse.class);
                if (!ObjectUtils.isEmpty(getLeadResponse) && Arrays.asList(ugroConfig.getClosedResponse(), ugroConfig.getRejectedResponse()).contains(getLeadResponse.getStatus())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus("UDYAM_" + LenderAssociationStatus.GET_LEAD_FAILED.name());
                    commonService.manageApplicationStateAndRejectApplication(lenderAssociationDetailsDto);
                    statusCheckResponse.setUdyamStatus(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadStatus());
                    return statusCheckResponse;
                }


                if (!lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getStage().equalsIgnoreCase(LenderAssociationStages.ASSC_COMPLETED.name())
                        && !ObjectUtils.isEmpty(getLeadResponse)
                        && ugroConfig.getSuccessResponse().equalsIgnoreCase(getLeadResponse.getBankAccountVerification())
                        && (ugroConfig.getSuccessResponse().equalsIgnoreCase(getLeadResponse.getBusinessProofVerification())
                        || (!ObjectUtils.isEmpty(getLeadResponse.getKybRemarks())
                        && ugroConfig.getSuccessResponse().equalsIgnoreCase(getLeadResponse.getKybRemarks().getUdyamFormFilled())))) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_SUCCESS.name());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setDataUploadStatus(LenderAssociationStatus.UDYAM_REGISTRATION_SUCCESS.name());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus("UDYAM_" + LenderAssociationStatus.GET_LEAD_SUCCESS.name());

                    // Here for old applications it was required to push to next stage but in new flow it's not required
                    commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsDto);
                    statusCheckResponse.setIsUdyamRequired(Boolean.FALSE);
                    statusCheckResponse.setUdyamStatus(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getDataUploadStatus());
                    return statusCheckResponse;
                }

                // This condition will only be executed for new cases as penny drop will be PENDING for new cases
                if (!ObjectUtils.isEmpty(getLeadResponse)
                        && (ugroConfig.getSuccessResponse().equalsIgnoreCase(getLeadResponse.getBusinessProofVerification())
                        || (!ObjectUtils.isEmpty(getLeadResponse.getKybRemarks())
                        && ugroConfig.getSuccessResponse().equalsIgnoreCase(getLeadResponse.getKybRemarks().getUdyamFormFilled())))) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setDataUploadStatus(LenderAssociationStatus.UDYAM_REGISTRATION_SUCCESS.name());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus("UDYAM_" + LenderAssociationStatus.GET_LEAD_SUCCESS.name());
                    log.info("UGRO: udyam data upload status success {}", lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getDataUploadStatus());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    statusCheckResponse.setIsUdyamRequired(Boolean.FALSE);
                    statusCheckResponse.setUdyamStatus(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getDataUploadStatus());
                    return statusCheckResponse;
                }

                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus("UDYAM_" + LenderAssociationStatus.GET_LEAD_SUCCESS.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                statusCheckResponse.setUdyamStatus(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadStatus());
                return statusCheckResponse;
            }
        } catch (Exception e) {
            log.error("UGRO: exception occurred while processing udyam status check for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus("UDYAM_" +LenderAssociationStatus.GET_LEAD_FAILED.name());
        commonService.manageApplicationState(lenderAssociationDetailsDto);
        statusCheckResponse.setUdyamStatus(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadStatus());
        return statusCheckResponse;
    }

    private NBFCRequestDTO<?> getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        try {
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails();
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                throw new RuntimeException("UGRO: LALD not found for application " + lendingApplication.getId());
            }

            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(UgroGetLeadRequest.builder()
                            .leadId(lendingApplicationLenderDetails.getLeadId())
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("UGRO: Exception in creating payload of create lead for {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    @Transactional
    public NBFCResponseDTO<?>  getDedupeGetLeadResponse(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto){
        try {
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus("DEDUPE_" + LenderAssociationStages.GET_LEAD.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus("DEDUPE_" + LenderAssociationStatus.GET_LEAD_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);

            NBFCRequestDTO<?> getLeadRequest = getPayload(lenderAssociationDetailsDto);
            if (ObjectUtils.isEmpty(getLeadRequest)) {
                log.info("UGRO: error in get lead status payload for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus("DEDUPE_" + LenderAssociationStatus.GET_LEAD_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.GET_LEAD_FAILED);
                return null;
            }

            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(getLeadRequest, LenderAssociationStages.GET_LEAD);
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus("DEDUPE_" + LenderAssociationStatus.GET_LEAD_SUCCESS.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return nbfcResponseDto;
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus("DEDUPE_" + LenderAssociationStatus.GET_LEAD_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.GET_LEAD_FAILED);
        } catch (Exception e) {
            log.info("UGRO: exception occurred while processing get lead for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}