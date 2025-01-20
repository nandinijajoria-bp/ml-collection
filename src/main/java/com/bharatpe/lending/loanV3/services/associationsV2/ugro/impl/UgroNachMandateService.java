package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.enums.EnachMode;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.ugro.UgroNachMandateRequest;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.ugro.validations.UgroPayloadValidation;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
public class UgroNachMandateService {
    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    UgroPayloadValidation payloadValidation;

    @Autowired
    EnachHandler enachHandler;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    UgroConfig ugroConfig;

    @Transactional
    public Boolean invokeNachMandate(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        try {
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.NACH_MANDATE_PENDING.name());
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.NACH_MANDATE.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequest);

            NBFCRequestDTO<?> nachMandateRequest = getPayload(lenderAssociationDetailsRequest);
            if (ObjectUtils.isEmpty(nachMandateRequest)) {
                log.info("UGRO: error in nach mandate payload for applicationId: {}", lenderAssociationDetailsRequest.getApplicationId());
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.NACH_MANDATE_FAILED.name());
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.NACH_MANDATE.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequest);
                return false;
            }
            NBFCResponseDTO<?> nbfcResponseDTO = lenderAPIGateway.invokeStage(nachMandateRequest, LenderAssociationStages.NACH_MANDATE);
            if (Objects.nonNull(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && Objects.nonNull(nbfcResponseDTO.getData())) {
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.NACH_MANDATE_SUCCESS.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequest);
                return true;
            }
        } catch (Exception e) {
            log.error("UGRO: error while pushing nach mandate request for  {} {} {}", lenderAssociationDetailsRequest.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.NACH_MANDATE_FAILED.name());
        commonService.manageApplicationState(lenderAssociationDetailsRequest);
        return Boolean.FALSE;
    }

    private NBFCRequestDTO<?> getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        try {
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
            UgroNachMandateRequest mandateDetails = null;
            Long nachApplicationId = lendingApplicationDetails.getIsNachSkip() ? null : lendingApplication.getId();
            MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = enachHandler.findByMerchantIdAndApplicationIdAndLender(lendingApplication.getMerchantId(), nachApplicationId, loanUtil.enachServiceLenderMapper(lendingApplication.getLender()));
            if (!ObjectUtils.isEmpty(merchantNachDetailsResponseDTO)) {
                mandateDetails = UgroNachMandateRequest.builder()
                        .leadId(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLeadId())
                        .accountNumber(merchantNachDetailsResponseDTO.getAccountNumber())
                        .ifsc(merchantNachDetailsResponseDTO.getIfscCode())
                        .name(merchantNachDetailsResponseDTO.getApplicantName())
                        .mandateCreationDate(String.valueOf(merchantNachDetailsResponseDTO.getStartDate().getTime()))
                        .startDate(String.valueOf(merchantNachDetailsResponseDTO.getStartDate().getTime()))
                        .endDate(String.valueOf(DateTimeUtil.addDays(merchantNachDetailsResponseDTO.getStartDate(), ugroConfig.getNachPlusDays()).getTime()))
                        .maxAmount(merchantNachDetailsResponseDTO.getNachAmount())
                        .authorisationMode(getAuthorisationMode(merchantNachDetailsResponseDTO.getMode()))
                        .UMRNNumber(merchantNachDetailsResponseDTO.getProviderUmrn())
                        .nachMode("ENACH")
                        .nachVendor(merchantNachDetailsResponseDTO.getProvider())
                        .vendorRequestId(merchantNachDetailsResponseDTO.getMandateId())
                        .status(ugroConfig.getNachStatus())
                        .build();
            }
            if (payloadValidation.isInvalidNachMandatePayload(mandateDetails)) {
                log.info("UGRO: error in getting mandate details payload for merchantId {} and application {}", lendingApplication.getMerchantId(), lendingApplication.getId());
                return null;
            }
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(mandateDetails)
                    .build();
        } catch (Exception e) {
            log.info("UGRO: Exception in creating nach mandate payload for applicationId {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String getAuthorisationMode(String mode) {
        if(EnachMode.ADHAAR.name().equalsIgnoreCase(mode)) {
            return ugroConfig.getAadhaarEnachMode();
        } else {
            return EnachMode.NB_DC.name();
        }
    }
}
