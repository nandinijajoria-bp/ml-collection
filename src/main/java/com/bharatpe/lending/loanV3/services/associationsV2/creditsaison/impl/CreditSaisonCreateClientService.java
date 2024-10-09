package com.bharatpe.lending.loanV3.services.associationsV2.creditsaison.impl;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.loanV3.config.CreditSaisonConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.creditsasion.CreditSasionDedupeRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.creditsasion.CreditSasionDedupeResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.creditsaison.validations.CreditSaisonPayloadValidation;
import com.bharatpe.lending.loanV3.services.associationsV2.wrapper.InvokeCreateLeadAndDocUploadWrapperService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
public class CreditSaisonCreateClientService {

    @Autowired
    KycUtils kycUtils;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CreditSaisonPayloadValidation creditSaisonPayloadValidation;

    @Lazy
    @Autowired
    CreditSaisonConfig csConfig;


    @Transactional
    public Boolean invokeCreateClient(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto){
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("CS: Application Id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            if (InvokeCreateLeadAndDocUploadWrapperService.kycDataNeeded(LenderAssociationStages.CREATE_CLIENT.name()) && ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));
            }
            if (creditSaisonPayloadValidation.isInValidCreateClientPayload(lenderAssociationDetailsDto.getCKycResponseDto())) {
                log.info("CS: invalid response from downstream api for createClient of Credit Sasion : {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.CREATE_CLIENT_FAILED);
                return false;
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.CREATE_CLIENT.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();

            //GET PAYLOAD FOR DEDUPE/CREATE CLIENT
            NBFCRequestDTO createClientRequest = getPayload(lenderAssociationDetailsDto);

            if (Objects.isNull(createClientRequest)) {
                log.info("CS: error in create lead payload of Credit Sasion for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.CREATE_CLIENT_FAILED);
                return false;
            }

            //INVOKE CREATE CLIENT STAGE // meaning call to nbfc
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(createClientRequest, LenderAssociationStages.CREATE_CLIENT);


            log.info("CS: create lead response of CreditSasion from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                log.info("CS: createLead request of CreditSasion success for {}", lenderAssociationDetailsDto.getApplicationId());
                CreditSasionDedupeResponseDTO createClientResponse = objectMapper.readValue( objectMapper.writeValueAsString(nbfcResponseDto.getData()), CreditSasionDedupeResponseDTO.class);

                boolean isNoExposureAndNoMatch = ObjectUtils.isEmpty(createClientResponse.getAllowableExposure()) && "No Match".equalsIgnoreCase(createClientResponse.getDedupeStatus());
                boolean isPositiveExposureAndEntityExists = !ObjectUtils.isEmpty(createClientResponse.getAllowableExposure()) && createClientResponse.getAllowableExposure() > 0.0 && "Entity Exists".equalsIgnoreCase(createClientResponse.getDedupeStatus());

                if (isNoExposureAndNoMatch || isPositiveExposureAndEntityExists) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_SUCCESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }
            }
        } catch (Exception e) {
            log.info("CS: exception occurred while processing create client of Credit Sasion for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.CREATE_CLIENT_FAILED);
        return false;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        String panNumber = lenderAssociationDetailsRequest.getCKycResponseDto().getPanNumber();
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName(csConfig.getLendingProduct())
                    .payload(CreditSasionDedupeRequestDTO.builder()
                            .loanProduct(csConfig.getLoanProduct())
                            .kycs(
                                    Arrays.asList(
                                            new CreditSasionDedupeRequestDTO.KYC(
                                                    "panCard", panNumber)
                                    )
                            )
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("CS: Exception in creating payload of Create Client of Credit Saison for {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}
