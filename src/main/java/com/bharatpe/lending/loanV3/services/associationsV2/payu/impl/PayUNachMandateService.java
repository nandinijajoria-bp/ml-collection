package com.bharatpe.lending.loanV3.services.associationsV2.payu.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.payu.PayUMandateRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.payu.PayUCommonResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.payu.PayUMandateResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.*;

@Slf4j
@Service
public class PayUNachMandateService {

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
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    private Integer nachPlusDays = 14600;

    @Transactional
    public Boolean invokeNachMandate(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        try {

            log.info("Payu inside nach {} {}", lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getDocUploadStatus(),lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLeadStatus());
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findByApplicationIdAndLender(lenderAssociationDetailsRequest.getLendingApplication().getId(), lenderAssociationDetailsRequest.getLendingApplication().getLender());

            if (!Arrays.asList(LenderAssociationStatus.BANK_UPDATION_SUCCESS.name(), LenderAssociationStatus.NACH_MANDATE_FAILED.name(), "NACH_MANDATE_HARD_FAILED").contains(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLeadStatus())) {
                log.info("Payu: Incorrect lead status for nachMandate");
                return Boolean.FALSE;
            }

            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.NACH_MANDATE.name());
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.NACH_MANDATE_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequest);

            NBFCRequestDTO nachManadateRequest = getPayload(lenderAssociationDetailsRequest);
            if (ObjectUtils.isEmpty(nachManadateRequest)) {
                log.info("error in nach mandate payload of PayU for applicationId: {}", lenderAssociationDetailsRequest.getApplicationId());
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.NACH_MANDATE_FAILED.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequest);
                return false;
            }
            NBFCResponseDTO nbfcResponseDTO = lenderAPIGateway.invokeStage(nachManadateRequest, LenderAssociationStages.NACH_MANDATE);
            log.info("nach mandate response of payU from nbfc: {} with applicationId: {}", nbfcResponseDTO, lenderAssociationDetailsRequest.getApplicationId());
            if (Objects.nonNull(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && Objects.nonNull(nbfcResponseDTO.getData())) {

                log.info("nach-mandate request of payU success for {}", lenderAssociationDetailsRequest.getApplicationId());

                PayUCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDTO.getData(), PayUCommonResponseDTO.class);

                PayUMandateResponseDTO nachMandateResponseDTO = objectMapper.convertValue(commonResponseDTO.getApiResponse(), PayUMandateResponseDTO.class);

                if ("SUCCESS".equalsIgnoreCase(commonResponseDTO.getApiStatus())) {
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadId(nachMandateResponseDTO.getApplicationId());
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.NACH_MANDATE_SUCCESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsRequest);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("error while pushing nach mandate request of PayU for  {} {} {}", lenderAssociationDetailsRequest.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.NACH_MANDATE_FAILED.name());
        commonService.manageApplicationState(lenderAssociationDetailsRequest);
        return Boolean.FALSE;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        try {
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
            PayUMandateRequestDTO mandateDetails = null;

            Long nachApplicationId = lendingApplicationDetails.getIsNachSkip() ? null : lendingApplication.getId();
                MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = enachHandler.findByMerchantIdAndApplicationIdAndLender(lendingApplication.getMerchantId(), nachApplicationId, lendingApplication.getLender());
                if (!ObjectUtils.isEmpty(merchantNachDetailsResponseDTO)) {
                    mandateDetails = PayUMandateRequestDTO.builder()
                            .applicationId(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLeadId())
                            .mandateState("SUCCESS")
                            .mandateId(merchantNachDetailsResponseDTO.getMandateId())
                            .umrn(merchantNachDetailsResponseDTO.getProviderUmrn())
                            .startDate(merchantNachDetailsResponseDTO.getStartDate())
                            .endDate(DateTimeUtil.addDays(merchantNachDetailsResponseDTO.getStartDate(), nachPlusDays))
                            .mandateAccountDetails(PayUMandateRequestDTO.MandateAccountDetails.builder().ifsc(merchantNachDetailsResponseDTO.getIfscCode()).maskedAccountNumber(merchantNachDetailsResponseDTO.getAccountNumber()).build())
                            .authMode("UPI")
                            .authAmount(merchantNachDetailsResponseDTO.getNachAmount())
                            .build();
                }

            if (ObjectUtils.isEmpty(mandateDetails)) {
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
                    .topup(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()))
                    .identifier(identifiers)
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating nach mandate payload of PayU for applicationId {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}
