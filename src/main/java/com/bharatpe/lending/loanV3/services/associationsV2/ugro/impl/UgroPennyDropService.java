package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.ugro.UgroCreateLeadRequest;
import com.bharatpe.lending.loanV3.dto.request.ugro.UgroPennyDropRequest;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroGetLeadResponse;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroPennyDropResponse;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.ugro.validations.UgroPayloadValidation;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Collections;

@Slf4j
@Service
public class UgroPennyDropService {

    @Autowired
    UgroConfig ugroConfig;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CommonService commonService;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    EnachHandler enachHandler;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    UgroPayloadValidation payloadValidation;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;


    @Transactional
    public Boolean invokePennyDrop(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.PENNY_DROP_PENDING.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_PENDING.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.PENNY_DROP.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);

            NBFCRequestDTO<?> createLeadRequest = getPayload(lenderAssociationDetailsDto);
            if (ObjectUtils.isEmpty(createLeadRequest)) {
                log.info("UGRO: error in pennyDrop payload for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.PENNY_DROP_FAILED.name());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_FAILED.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return false;
            }
            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(createLeadRequest, LenderAssociationStages.PENNY_DROP);
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                UgroPennyDropResponse pennyDropResponse = objectMapper.convertValue(nbfcResponseDto.getData(), UgroPennyDropResponse.class);
                if (!ObjectUtils.isEmpty(pennyDropResponse) && !ObjectUtils.isEmpty(pennyDropResponse.getLeadId()) && lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId().equalsIgnoreCase(pennyDropResponse.getLeadId())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.PENNY_DROP_IN_PROGRESS.name());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_IN_PROGRESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    // Returning false to prevent invoking the next stage, as next stage is only for verifying the penny drop status in case the penny drop status is not updated in the callback
                    return false;
                }
            }
        } catch (Exception e) {
            log.info("UGRO: exception occurred while processing pennyDrop for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.PENNY_DROP_FAILED.name());
        commonService.manageApplicationState(lenderAssociationDetailsDto);
        return false;
    }

    private NBFCRequestDTO<?> getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        try {
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
            Long nachApplicationId = lendingApplicationDetails.getIsNachSkip() ? null : lendingApplication.getId();
            MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = enachHandler.findByMerchantIdAndApplicationIdAndLender(lendingApplication.getMerchantId(), nachApplicationId, loanUtil.enachServiceLenderMapper(lendingApplication.getLender()));

            UgroPennyDropRequest updateLead = null;

            if (!ObjectUtils.isEmpty(merchantNachDetailsResponseDTO)) {
                updateLead = UgroPennyDropRequest.builder()
                        .id(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLeadId())
                        .profileData(UgroCreateLeadRequest.ProfileData.builder()
                                .bankAccounts(Collections.singletonList(UgroCreateLeadRequest.ProfileData.BankAccount.builder()
                                        .beneficiaryName(merchantNachDetailsResponseDTO.getBeneficiaryName())
                                        .ifsc(merchantNachDetailsResponseDTO.getIfscCode())
                                        .accountNumber(merchantNachDetailsResponseDTO.getAccountNumber())
                                        .purposeType(ugroConfig.getPurposeType())
                                        .build()))
                                .build())
                        .build();
            }
            if (payloadValidation.isInvalidUpdateLeadPayload(updateLead)) {
                log.info("UGRO: Error in getting pennyDrop payload for merchantId {} and application {}", lendingApplication.getMerchantId(), lendingApplication.getId());
                return null;
            }
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(updateLead)
                    .build();
        } catch (Exception e) {
            log.info("UGRO: Exception in creating payload of pennyDrop for {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }


    public Boolean processCallback(NBFCResponseDTO<?> nbfcResponseDTO) {
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

            if (!LenderAssociationStatus.PENNY_DROP_IN_PROGRESS.name().equalsIgnoreCase(lendingApplicationLenderDetails.getPennyDropStatus())) {
                log.info("UGRO: pennydrop status is not in progress for applicationId {}", lendingApplication.getId());
                return false;
            }

            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                    .applicationId(lendingApplication.getId()).lendingApplication(lendingApplication)
                    .lendingApplicationLenderDetails(lendingApplicationLenderDetails).manageState(true)
                    .modifyLender(enableLenderChange).build();

            if (!ObjectUtils.isEmpty(nbfcResponseDTO.getData()) && nbfcResponseDTO.getSuccess()) {
                UgroGetLeadResponse getLeadResponse = objectMapper.convertValue(nbfcResponseDTO.getData(), UgroGetLeadResponse.class);

                if (!ObjectUtils.isEmpty(getLeadResponse) && Arrays.asList(ugroConfig.getClosedResponse(), ugroConfig.getRejectedResponse()).contains(getLeadResponse.getStatus())) {
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_FAILED.name());
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.PENNY_DROP_FAILED.name());
                    commonService.manageApplicationStateAndRejectApplication(lenderAssociationDetailsRequest);
                    return false;
                }

                if (!ObjectUtils.isEmpty(getLeadResponse)
                        && ugroConfig.getSuccessResponse().equalsIgnoreCase(getLeadResponse.getBankAccountVerification())
                        && (ugroConfig.getSuccessResponse().equalsIgnoreCase(getLeadResponse.getBusinessProofVerification())
                        || (!ObjectUtils.isEmpty(getLeadResponse.getKybRemarks())
                        && ugroConfig.getSuccessResponse().equalsIgnoreCase(getLeadResponse.getKybRemarks().getUdyamFormFilled())))) {
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setDataUploadStatus(LenderAssociationStatus.UDYAM_SUCCESS.name());
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_SUCCESS.name());
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.PENNY_DROP_SUCCESS.name());
                    commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                    return true;
                } else if (!ObjectUtils.isEmpty(getLeadResponse)
                        && ugroConfig.getSuccessResponse().equalsIgnoreCase(getLeadResponse.getBankAccountVerification())) {
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setDataUploadStatus(LenderAssociationStatus.UDYAM_PENDING.name());
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_SUCCESS.name());
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.PENNY_DROP_SUCCESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsRequest);
                    return true;
                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.GET_LEAD_FAILED.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequest);
        } catch (Exception e) {
            log.error("UGRO: exception while processing pennyDrop status check for  {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

}