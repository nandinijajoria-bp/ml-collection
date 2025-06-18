package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.ugro.UgroUdyamRegistrationRequest;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroUdyamRegistrationResponse;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;

@Slf4j
@Service
public class UgroDocumentGenerationService {

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UgroConfig ugroConfig;

    @Transactional
    public String getUdyamRegistrationResponse(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.error("UGRO: application id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return null;
            }

            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UDYAM_GENERATION_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);

            NBFCRequestDTO<?> udyamRegistrationPayload = getPayload(lenderAssociationDetailsDto);
            if (ObjectUtils.isEmpty(udyamRegistrationPayload)) {
                log.error("UGRO: error in udyam registration payload for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UDYAM_GENERATION_FAILED.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return null;
            }
            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(udyamRegistrationPayload, LenderAssociationStages.GENERATE_DOCUMENT);
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                UgroUdyamRegistrationResponse udyamRegistrationResponse = objectMapper.convertValue(nbfcResponseDto.getData(), UgroUdyamRegistrationResponse.class);
                if (!ObjectUtils.isEmpty(udyamRegistrationResponse) && !ObjectUtils.isEmpty(udyamRegistrationResponse.getLink())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UDYAM_GENERATION_SUCCESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return udyamRegistrationResponse.getLink();
                }
            }
        } catch (Exception e) {
            log.info("UGRO: exception occurred while processing udyam registration for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }

        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UDYAM_GENERATION_FAILED.name());
        commonService.manageApplicationState(lenderAssociationDetailsDto);
        return null;
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
                    .payload(UgroUdyamRegistrationRequest.builder()
                            .leadId(lendingApplicationLenderDetails.getLeadId())
                            .redirectURL(ugroConfig.getUdyamRedirectionUrl())
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("UGRO: Exception in creating payload of udyam registration lead for {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}
