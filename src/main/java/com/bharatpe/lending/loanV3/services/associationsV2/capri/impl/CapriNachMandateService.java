package com.bharatpe.lending.loanV3.services.associationsV2.capri.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dto.BharatPeEnachResponseDTO;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.service.merchant.constants.Constants;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.capri.CapriMandateRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.capri.CapriMandateResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Objects;

@Slf4j
@Service
public class CapriNachMandateService {

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    EnachHandler enachHandler;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    MerchantService merchantService;

    @Transactional
    public Boolean invokeNachMandate(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        try {
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.NACH_MANDATE_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequest);

            NBFCRequestDTO nachManadateRequest = getPayload(lenderAssociationDetailsRequest);
            if (ObjectUtils.isEmpty(nachManadateRequest)) {
                log.info("error in nach mandate payload of Capri for applicationId: {}", lenderAssociationDetailsRequest.getApplicationId());
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.NACH_MANDATE_FAILED.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequest);
                return false;
            }
            NBFCResponseDTO nbfcResponseDTO = lenderAPIGateway.invokeStage(nachManadateRequest, LenderAssociationStages.NACH_MANDATE);
            log.info("nach mandate response of capri from nbfc: {} with applicationId: {}", nbfcResponseDTO, lenderAssociationDetailsRequest.getApplicationId());
            if (Objects.nonNull(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && Objects.nonNull(nbfcResponseDTO.getData())) {
                CapriMandateResponseDTO capriMandateResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), CapriMandateResponseDTO.class);
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.NACH_MANDATE_SUCCESS.name());
                commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                return true;
            }
        } catch (Exception e) {
            log.error("error while pushing nach mandate request of Capri for  {} {} {}", lenderAssociationDetailsRequest.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.NACH_MANDATE_FAILED.name());
        commonService.manageApplicationState(lenderAssociationDetailsRequest);
        return Boolean.FALSE;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        try {
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
            CapriMandateRequestDTO mandateDetails = null;
            if (lendingApplicationDetails.getIsNachSkip()) {
                MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = enachHandler.findByMerchantIdAndLender(lendingApplication.getMerchantId(), lendingApplication.getLender());
                if(!ObjectUtils.isEmpty(merchantNachDetailsResponseDTO)) {
                    mandateDetails = CapriMandateRequestDTO.builder()
                            .status("DESTINATION_ACCEPTED") // To be update if required
                            .umrn(merchantNachDetailsResponseDTO.getProviderUmrn())
                            .bankAccountId(1)    // 1 : Individual
                            .bankAccountHolderName(merchantNachDetailsResponseDTO.getBeneficiaryName())
                            .bankName(merchantNachDetailsResponseDTO.getBankName())
                            .bankAccountNumber(merchantNachDetailsResponseDTO.getAccountNumber())
                            .micr(merchantNachDetailsResponseDTO.getMicrNumber())
                            .ifsc(merchantNachDetailsResponseDTO.getIfscCode())
                            .bankAccountType(merchantNachDetailsResponseDTO.getAccountType())
                            .mandateRegistrationRequestedDate(DateTimeUtil.getDateInFormat(new Date(), "dd-MM-yyyy"))
                            .dateFormat("dd-MM-yyyy")
                            .periodStartDate(DateTimeUtil.getDateInFormat(new Date(), "dd-MM-yyyy"))
                            .mode(merchantNachDetailsResponseDTO.getNachMode())
                            .periodUntilCancelled(Boolean.TRUE)
                            .debitTypeEnum("FIXED_AMOUNT")     // To be confirm with product
                            .debitFrequencyEnum("DAILY")       // To be confirm with product
                            .amount(merchantNachDetailsResponseDTO.getNachAmount())
                            .build();
                }
            } else {
                BharatPeEnachResponseDTO bharatPeEnachResponseDTO = enachHandler.findByMerchantIdAndApplicationId(lendingApplication.getMerchantId(), lendingApplication.getId());
                final MerchantDetailsDto merchantDetailsDto = merchantService.fetchMerchantDetails(lendingApplication.getMerchantId(), Arrays.asList(
                        Constants.MerchantUtil.Scope.BANK_DETAIL,
                        Constants.MerchantUtil.Scope.MERCHANT_USER
                ));
                if (!ObjectUtils.isEmpty(bharatPeEnachResponseDTO) && !ObjectUtils.isEmpty(merchantDetailsDto)) {
                    mandateDetails = CapriMandateRequestDTO.builder()
                            .status("DESTINATION_ACCEPTED")
                            .umrn(bharatPeEnachResponseDTO.getProviderUmrn())
                            .bankAccountId(1)    // 1 : Individual
                            .bankAccountHolderName(merchantDetailsDto.getBankDetail().getBeneficiaryName())
                            .bankName(merchantDetailsDto.getBankDetail().getBankName())
                            .bankAccountNumber(merchantDetailsDto.getBankDetail().getAccountNumber())
                            .ifsc(merchantDetailsDto.getBankDetail().getIfsc())
                            .bankAccountType(merchantDetailsDto.getBankDetail().getAccountType())
                            .mandateRegistrationRequestedDate(DateTimeUtil.getDateInFormat(new Date(), "dd-MM-yyyy"))
                            .dateFormat("dd-MM-yyyy")
                            .periodStartDate(DateTimeUtil.getDateInFormat(new Date(), "dd-MM-yyyy"))
                            .mode(bharatPeEnachResponseDTO.getMode())
                            .periodUntilCancelled(Boolean.TRUE)
                            .debitTypeEnum("FIXED_AMOUNT")      // To be confirm with product
                            .debitFrequencyEnum("DAILY")        // To be confirm with product
                            .amount(bharatPeEnachResponseDTO.getAmount())
                            .build();
                }
            }
            if(ObjectUtils.isEmpty(mandateDetails)) {
                log.info("error in getting mandate Details for merchantId {} and application {}", lendingApplication.getMerchantId(), lendingApplication.getId());
                return null;
            }
            LinkedHashMap<String, Object> identifiers = new LinkedHashMap<>();
            identifiers.put("leadId", lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLeadId());
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(mandateDetails)
                    .identifier(identifiers)
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating nach mandate payload of capri for applicationId {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}
