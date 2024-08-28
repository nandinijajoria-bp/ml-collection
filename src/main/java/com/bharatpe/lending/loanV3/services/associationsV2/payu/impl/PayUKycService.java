package com.bharatpe.lending.loanV3.services.associationsV2.payu.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.payu.PayUKycRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.payu.PayUCommonResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.payu.PayUKycResponseDTO;
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
public class PayUKycService {

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Transactional
    public boolean invokeKyc(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("Application Id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }

            commonService.manageApplicationState(lenderAssociationDetailsDto);
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
            NBFCRequestDTO kycRequestPayload = getKycRequestPayload(lenderAssociationDetailsDto);
            if (Objects.isNull(kycRequestPayload)) {
                log.info("error in KYC payload of payU for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.KYC_FAILED);
                return false;
            }
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(kycRequestPayload, LenderAssociationStages.KYC);
            log.info("KYC response of PayU from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                log.info("KYC request of PayU success for {}", lenderAssociationDetailsDto.getApplicationId());
                PayUCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDto.getData(), PayUCommonResponseDTO.class);
                PayUKycResponseDTO kycResponseDTO = objectMapper.convertValue(commonResponseDTO.getApiResponse(), PayUKycResponseDTO.class);
                if (!"SUCCESS".equalsIgnoreCase(commonResponseDTO.getApiStatus()) || kycResponseDTO.getData().getKycIdentity().equalsIgnoreCase("FAILED")) {

                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
                    commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.KYC_FAILED);
                    return false;
                }
                else{
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_COMPLETED.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }

            }
        } catch (Exception e) {
            log.error("exception occurred while KYC of PAYU for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.KYC_FAILED);
        return false;

    }

    private NBFCRequestDTO getKycRequestPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.PAYU.name())
                    .payload(PayUKycRequestDTO.builder()
                            .applicationId(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())
                            .kycType("DIGILOCKER_OKYC")
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("exception occurred while KYC request payload for PayU for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

}
