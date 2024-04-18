package com.bharatpe.lending.loanV3.services.associationsV2.capri.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.capri.CapriCreateLeadRequestDTO;
import com.bharatpe.lending.loanV3.dto.request.capri.CapriUpdateLeadRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.capri.CapriCreateLeadResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.capri.validations.CapriPayloadValidation;
import com.bharatpe.lending.loanV3.services.associationsV2.wrapper.InvokeCreateLeadAndDocUploadWrapperService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;

import java.util.*;

@Slf4j
@Service
public class CapriLeadService {
    @Autowired
    KycUtils kycUtils;

    @Autowired
    CapriPayloadValidation capriPayloadValidation;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Value("${capri.lead.sourcing.channel.id:554}")
    Long capriLeadSourcingChannelId;

    @Value("${capri.lead.los.product.key:BEL-}")
    String capriLeadLosProductKey;

    @Value("${capri.lead.chargeId:16}")
    Long capriLeadChargeId;

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
            if (capriPayloadValidation.isInvalidCreateLeadPayload()) {
                log.info("invalid response from downstream api for createLead of Capri : {}", lenderAssociationDetailsDto.getApplicationId());
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
                log.info("error in create lead payload of Capri for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
                return false;
            }
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(createLeadRequest, LenderAssociationStages.CREATE_LEAD);
            log.info("create lead response of Capri from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                log.info("createLead request of Capri success for {}", lenderAssociationDetailsDto.getApplicationId());
                CapriCreateLeadResponseDTO createLeadResponseDTO = objectMapper.readValue( objectMapper.writeValueAsString(nbfcResponseDto.getData()), CapriCreateLeadResponseDTO.class);
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadId(createLeadResponseDTO.getResourceId().toString());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_SUCCESS.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return true;
            }
        } catch (Exception e) {
            log.info("exception occurred while processing create lead of Capri for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
        return false;
    }

    private NBFCRequestDTO getCreateLeadPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails();
        try {
            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
            if(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)) {
                throw new RuntimeException("Lending risk variable snapshot not found for application " + lendingApplication.getId());
            }
            CapriCreateLeadRequestDTO createLeadRequest = CapriCreateLeadRequestDTO.builder()
                    .loanOfficerId(1L)
                    .losProductKey(capriLeadLosProductKey)    // need to update if required
                    .amount(lendingApplication.getLoanAmount())
                    .loanPurposeId(1L)         // need to update if required
                    .sourcingChannelId(capriLeadSourcingChannelId)   // need to update if required
                    .associations(CapriCreateLeadRequestDTO.Associations.builder()
                            .anchor("Anchor")
                            .merchant("Merchant")
                            .thirdparty("Third party")
                            .self("Self")
                            .build())
                    .leadApplicationTerms(CapriCreateLeadRequestDTO.LeadApplicationTerms.builder()
                            .maxEligibleAmount(lendingApplication.getLoanAmount())
                            .numberOfRepayments(lendingApplication.getPayableDays())
                            .interestRatePerPeriod(lendingApplicationLenderDetails.getAnnualRoi())
                            .dateFormat("dd-MM-yyyy")
                            .graceOnPrincipalPayment(1L)
                            .graceOnInterestCharged(1L)
                            .amountForUpfrontCollection(100L)
                            .build())
                    .charges(getCharges(lendingApplication))
                    .build();
            LinkedHashMap<String, Object> identifiers = new LinkedHashMap<>();
            identifiers.put("clientId", lendingApplicationLenderDetails.getCccId());
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(createLeadRequest)
                    .identifier(identifiers)
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating payload of create lead of Capri for {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private List<CapriCreateLeadRequestDTO.Charge> getCharges(LendingApplication lendingApplication) {
        List<CapriCreateLeadRequestDTO.Charge> charges = new ArrayList<>();
        CapriCreateLeadRequestDTO.Charge processingFee = CapriCreateLeadRequestDTO.Charge.builder()
                .chargeId(capriLeadChargeId)
                .amount(lendingApplication.getProcessingFee())
                .isMandatory(Boolean.TRUE)
                .isAmountNonEditable(Boolean.TRUE)
                .build();
        charges.add(processingFee);
        return charges;
    }

    @Transactional
    public Boolean invokeUpdateLead(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        try {
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.UPDATE_LEAD.name());
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.UPDATE_LEAD_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);

            lenderAssociationDetailsRequestDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsRequestDto.getMerchantId()));
            NBFCRequestDTO updateLeadRequestDto = getUpdateLeadPayload(lenderAssociationDetailsRequestDto.getLendingApplication(), lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails());
            if (Objects.isNull(updateLeadRequestDto)) {
                log.info("error in update lead payload of Capri for applicationId: {}", lenderAssociationDetailsRequestDto.getApplicationId());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.UPDATE_LEAD_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.UPDATE_LEAD_FAILED);
                return false;
            }
            NBFCResponseDTO updateLeadResponseDTO = lenderAPIGateway.invokeStage(updateLeadRequestDto, LenderAssociationStages.UPDATE_LEAD);
            log.info("update lead response of Capri from nbfc: {} with applicationId: {}", updateLeadResponseDTO, lenderAssociationDetailsRequestDto.getApplicationId());
            if (Objects.nonNull(updateLeadResponseDTO) && updateLeadResponseDTO.getSuccess() && Objects.nonNull(updateLeadResponseDTO.getData())) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.UPDATE_LEAD_COMPLETED.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                return true;
            }
        } catch (Exception e) {
            log.error("error while pushing update lead of Capri for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.UPDATE_LEAD_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.UPDATE_LEAD_FAILED);
        return Boolean.FALSE;
    }

    private NBFCRequestDTO getUpdateLeadPayload(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        try {
            LinkedHashMap<String, Object> identifiers = new LinkedHashMap<>();
            identifiers.put("leadId", lendingApplicationLenderDetails.getLeadId());
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(lendingApplication.getLender())
                    .payload(CapriUpdateLeadRequestDTO.builder()
                            .loanAmountRequested(lendingApplication.getLoanAmount())
                            .rateOfInterest(lendingApplicationLenderDetails.getAnnualRoi())
                            .tenure(lendingApplication.getTenureInMonths())
                            .build())
                    .identifier(identifiers)
                    .build();
        } catch (Exception e) {
            log.info("exception occurred while creating request payload for updateLead for applicationId: {}, {}", lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}
