package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLCreateLeadRequestDto;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.TLCreateLeadResponseDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.validations.TLPayloadValidation;
import com.bharatpe.lending.loanV3.services.associationsV2.wrapper.InvokeCreateLeadAndDocUploadWrapperService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.tools.javac.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
public class TLCreateLeadService {
    @Autowired
    KycUtils kycUtils;

    @Autowired
    TLPayloadValidation payloadValidation;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Transactional
    public boolean invokeCreateLead(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("Application Id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            if (InvokeCreateLeadAndDocUploadWrapperService.kycDataNeeded(LenderAssociationStages.CREATE_LEAD.name()) && ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));
            }
            if (payloadValidation.isInvalidCreateLeadPayload()) {
                log.info("invalid response from downstream api for createLead of TrillionLoans : {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
                return false;
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.PiramalAssociationStages.LEAD_CREATION.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
            NBFCRequestDTO createLeadRequest = getCreateLeadPayload(lenderAssociationDetailsDto);
            if (Objects.isNull(createLeadRequest)) {
                log.info("error in create lead payload of TrillionLoans for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
                return false;
            }
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(createLeadRequest, LenderAssociationStages.CREATE_LEAD);
            log.info("create lead response of TrillionLoans from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                log.info("createLead request of TrillionLoans success for {}", lenderAssociationDetailsDto.getApplicationId());
                TLCreateLeadResponseDto createLeadResponseDTO = objectMapper.readValue( objectMapper.writeValueAsString(nbfcResponseDto.getData()), TLCreateLeadResponseDto.class);
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadId(createLeadResponseDTO.getResourceId().toString());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_SUCCESS.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return true;
            }
        } catch (Exception e) {
            log.info("exception occurred while processing create lead of TrillionLoans for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
        return false;
    }

    private NBFCRequestDTO getCreateLeadPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        try {
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails();
            if(ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                throw new RuntimeException("Lending application details not found for application " + lendingApplication.getId());
            }
            TLCreateLeadRequestDto createLeadRequest = TLCreateLeadRequestDto.builder()
                    .clientId(Long.valueOf(lendingApplicationLenderDetails.getCccId()))
                    .loanOfficerId(1L)
                    .amount(lendingApplication.getLoanAmount())
                    .losProductKey("ELO")    // need to update it later
                    .leadApplicationTerms(TLCreateLeadRequestDto.LeadApplicationTerms.builder()
                            .maxEligibleAmount(lendingApplication.getLoanAmount())
                            .numberOfRepayments(lendingApplication.getPayableDays())
                            .repayEvery(1)                      // fixed
                            .repaymentPeriodFrequencyEnum(0)    // 0 : Days
                            .termPeriodFrequencyEnum(0)         // 0 : Days
                            .termFrequency(1)                   // fixed
                            .interestRatePerPeriod(lendingApplicationLenderDetails.getAnnualRoi())
                            .graceOnPrincipalPayment(0.00)
                            .graceOnInterestCharged(0.00)
                            .amountForUpfrontCollection(0.00)
                            .build())
                    .charges(List.of(TLCreateLeadRequestDto.Charge.builder()
                                    .chargeId(2L)
                                    .amount(String.format("%.2f",(lendingApplication.getProcessingFee() / lendingApplication.getLoanAmount()) * 100))
                            .build()))
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(createLeadRequest)
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating payload of create lead of TrillionLoans for {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}
