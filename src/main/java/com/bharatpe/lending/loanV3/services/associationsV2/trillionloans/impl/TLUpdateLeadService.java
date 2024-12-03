package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.service.merchant.constants.Constants;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLNachMandateRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLUpdateLeadRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLUpdateLeadRequestV2Dto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.validations.TLPayloadValidation;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.*;

@Slf4j
@Service
public class TLUpdateLeadService {
    @Autowired
    KycUtils kycUtils;

    @Autowired
    TLPayloadValidation payloadValidation;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    EnachHandler enachHandler;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;


    @Autowired
    ConverterUtils converterUtils;

    @Autowired
    MerchantService merchantService;

    @Transactional
    public boolean invokeUpdateLead(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        try {
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UPDATE_LEAD_PENDING.name());
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.UPDATE_LEAD.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);

            lenderAssociationDetailsRequestDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsRequestDto.getMerchantId()));
            NBFCRequestDTO updateLeadRequestDto = getPayload(lenderAssociationDetailsRequestDto);
            if (Objects.isNull(updateLeadRequestDto)) {
                log.info("error in update lead payload of TrillionLoans for applicationId: {}", lenderAssociationDetailsRequestDto.getApplicationId());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UPDATE_LEAD_FAILED.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                return false;
            }
            NBFCResponseDTO updateLeadResponseDTO = lenderAPIGateway.invokeStage(updateLeadRequestDto, LenderAssociationStages.UPDATE_LEAD);
            log.info("update lead response of TrillionLoans from nbfc: {} with applicationId: {}", updateLeadResponseDTO, lenderAssociationDetailsRequestDto.getApplicationId());
            if (Objects.nonNull(updateLeadResponseDTO) && updateLeadResponseDTO.getSuccess() && Objects.nonNull(updateLeadResponseDTO.getData())) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UPDATE_LEAD_COMPLETED.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                return true;
            }
        } catch (Exception e) {
            log.error("error while pushing update lead of TrillionLoans for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UPDATE_LEAD_FAILED.name());
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.UPDATE_LEAD.name());
        commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
        return Boolean.FALSE;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        try {
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
            Long nachApplicationId = lendingApplicationDetails.getIsNachSkip() ? null : lendingApplication.getId();
            TLUpdateLeadRequestV2Dto updateLeadDetails = null;
            MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = enachHandler.findByMerchantIdAndApplicationIdAndLender(lendingApplication.getMerchantId(), nachApplicationId, loanUtil.enachServiceLenderMapper(lendingApplication.getLender()));
            if (!ObjectUtils.isEmpty(merchantNachDetailsResponseDTO)) {
                updateLeadDetails = TLUpdateLeadRequestV2Dto.builder()
                        .clientId(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getCccId())
                        .leadId(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLeadId())
                        .accountNumber(merchantNachDetailsResponseDTO.getAccountNumber())
                        .ifscCode(merchantNachDetailsResponseDTO.getIfscCode())
                        .accountHolderName(merchantNachDetailsResponseDTO.getBeneficiaryName())
                        .bankName(merchantNachDetailsResponseDTO.getBankName())
                        .bankAccountType(getAccountType(merchantNachDetailsResponseDTO.getAccountType()))
                        .beneficiaryType("SELF")
                        .build();
            }
            if (payloadValidation.isInvalidUpdateLeadPayload(updateLeadDetails)) {
                log.info("Error in getting update lead details payload for TrillionLoans merchantId {} and application {}", lendingApplication.getMerchantId(), lendingApplication.getId());
                return null;
            }
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(updateLeadDetails)
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating payload of Update Lead of TrillionLoans for {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String getAccountType(String accountType) {
        if (Arrays.asList("CURRENT", "SAVINGS").contains(accountType))
            return accountType;
        else
            return "OTHER";
    }
}
