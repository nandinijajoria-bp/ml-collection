package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.ugro.UgroCreateApplicationRequest;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroCreateApplicationResponse;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;

@Slf4j
@Service
public class UgroCreateClientService {

    @Autowired
    KycUtils kycUtils;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Transactional
    public boolean invokeCreateClient(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("UGRO: application id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }

            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.CREATE_CLIENT.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);

            NBFCRequestDTO<?> createLeadRequest = getPayload(lenderAssociationDetailsDto);
            if (ObjectUtils.isEmpty(createLeadRequest)) {
                log.info("UGRO: error in create lead payload for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.CREATE_CLIENT_FAILED);
                return false;
            }
            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(createLeadRequest, LenderAssociationStages.CREATE_CLIENT);
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                log.info("UGRO: createLead request success for {}", lenderAssociationDetailsDto.getApplicationId());
                UgroCreateApplicationResponse createClientResponse = objectMapper.convertValue(nbfcResponseDto.getData(), UgroCreateApplicationResponse.class);
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setCccId(createClientResponse.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_IN_PROGRESS.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return true;
            }
        } catch (Exception e) {
            log.info("UGRO: exception occurred while processing create client for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.CREATE_CLIENT_FAILED);
        return false;
    }

    private NBFCRequestDTO<?> getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(UgroCreateApplicationRequest.builder()
                            .leadId(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLeadId())
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("UGRO: Exception in creating payload for {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}
