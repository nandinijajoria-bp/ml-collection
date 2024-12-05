package com.bharatpe.lending.loanV3.services.associationsV2.smfg.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationKycDetailsDao;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationKycDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.loanV3.config.SmfgConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.smfg.SmfgAppPushRequest;
import com.bharatpe.lending.loanV3.dto.response.smfg.SmfgAppPushResponseDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Optional;

@Slf4j
@Service
public class SmfgNachMandateService {

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    EnachHandler enachHandler;

    @Autowired
    LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    SmfgConfig smfgConfig;

    @Transactional
    public Boolean invokeNachMandate(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        try {
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStatus.NACH_MANDATE_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequest);
            NBFCRequestDTO<SmfgAppPushRequest> nachMandateRequest = getPayload(lenderAssociationDetailsRequest);
            if (ObjectUtils.isEmpty(nachMandateRequest) || ObjectUtils.isEmpty(nachMandateRequest.getPayload()) || ObjectUtils.isEmpty(nachMandateRequest.getPayload().getRepaymentdisbbankdetails())) {
                log.info("error in nach mandate payload of SMFG for applicationId: {}, merchantId {}", lenderAssociationDetailsRequest.getApplicationId(), lenderAssociationDetailsRequest.getMerchantId());
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.PAYLOAD_ERROR.name());
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStatus.NACH_MANDATE_FAILED.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequest);
                return false;
            }
            String prevAccountNumber = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getPennyDropAccountNumber();
            if (!prevAccountNumber.equals(nachMandateRequest.getPayload().getRepaymentdisbbankdetails().getAccountno())) {
                log.info("bank account changed for application id : {}, prev accountNumber : {}, new accountNumber {}", lenderAssociationDetailsRequest.getApplicationId(), prevAccountNumber, nachMandateRequest.getPayload().getRepaymentdisbbankdetails().getAccountno());
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.NACH_ACCOUNT_CHANGE.name());
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStatus.NACH_MANDATE_FAILED.name());
                commonService.manageApplicationStateAndRejectApplication(lenderAssociationDetailsRequest);
                return false;
            }
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(nachMandateRequest, LenderAssociationStages.NACH_MANDATE);
            log.info("nach mandate response of SMFG from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequest.getApplicationId());
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                SmfgAppPushResponseDto responseDto = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), SmfgAppPushResponseDto.class);
                if (!ObjectUtils.isEmpty(responseDto) && "SUCCESS".equalsIgnoreCase(responseDto.getStatus())) {
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStatus.NACH_MANDATE_SUCCESS.name());
                    commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                    return true;
                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.API_RESPONSE_FAILED.name());
        } catch (Exception e) {
            log.error("error while pushing nach mandate request of SMFG for  {} {} {}", lenderAssociationDetailsRequest.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStatus.NACH_MANDATE_FAILED.name());
        commonService.manageApplicationState(lenderAssociationDetailsRequest);
        return Boolean.FALSE;
    }

    private NBFCRequestDTO<SmfgAppPushRequest> getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        LendingApplicationKycDetails lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
        if (ObjectUtils.isEmpty(lendingApplicationKycDetails) || ObjectUtils.isEmpty(lendingApplicationKycDetails.getEmail()) || ObjectUtils.isEmpty(lendingApplicationKycDetails.getFatherName())) {
            log.info("no father name / email found in kyc details for application id {}", lendingApplication.getId());
            return null;
        }
        try {
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
            Long ownerId = Boolean.TRUE.equals(lendingApplicationDetails.getIsNachSkip()) ? null : lendingApplication.getId();
            SmfgAppPushRequest requestDto = null;
            MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = enachHandler.findByMerchantIdAndApplicationIdAndLender(lendingApplication.getMerchantId(), ownerId, lendingApplication.getLender());
            if (!ObjectUtils.isEmpty(merchantNachDetailsResponseDTO)) {
                requestDto = SmfgAppPushRequest.builder()
                        .partnerid(smfgConfig.getPartnerId())
                        .partnerapplicationid(lendingApplication.getExternalLoanId())
                        .apiaction(smfgConfig.getDataPushApiAction())
                        .leaddetails(SmfgAppPushRequest.LeadDetails.builder()
                                .emailaddress(lendingApplicationKycDetails.getEmail()).build())
                        .additionaldetails(SmfgAppPushRequest.AdditionalDetails.builder()
                                .fathersName(lendingApplicationKycDetails.getFatherName()).build())
                        .repaymentdisbbankdetails(SmfgAppPushRequest.RepaymentDisbBankDetails.builder()
                                .accountholdername(merchantNachDetailsResponseDTO.getBeneficiaryName())
                                .bankname(merchantNachDetailsResponseDTO.getBankName())
                                .accountno(merchantNachDetailsResponseDTO.getAccountNumber())
                                .accounttype("CURRENT".equalsIgnoreCase(merchantNachDetailsResponseDTO.getAccountType()) ? smfgConfig.getCurrentAccountType() : smfgConfig.getSavingAccountType())
                                .ifsccode(merchantNachDetailsResponseDTO.getIfscCode()).build())
                        .mandatedetails(SmfgAppPushRequest.MandateDetails.builder()
                                .emistartdate(DateTimeUtil.getDateInFormat(merchantNachDetailsResponseDTO.getStartDate(), "dd-MM-yyyy"))
                                .emienddate(DateTimeUtil.getDateInFormat(DateTimeUtil.addDays(merchantNachDetailsResponseDTO.getStartDate(), smfgConfig.getNachPlusDays()), "dd-MM-yyyy"))
                                .mandateregflag(smfgConfig.getPositiveMandateFlag())
                                .emiamount(Optional.ofNullable(lendingApplication.getLoanAmount()).map(Double::intValue).orElse(null))
                                .emifrequency(smfgConfig.getDailyInstallmentFrequency())
                                .mandatereferenceno(merchantNachDetailsResponseDTO.getProviderUmrn()).build())
                        .build();
            }
            NBFCRequestDTO<SmfgAppPushRequest> requestDTO = new NBFCRequestDTO<>();
            requestDTO.setApplicationId(lendingApplication.getId());
            requestDTO.setLender(lendingApplication.getLender());
            requestDTO.setProductName("LENDING");
            requestDTO.setPayload(requestDto);
            return requestDTO;
        } catch (Exception e) {
            log.info("Exception in creating nach mandate payload of SMFG for applicationId {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

}
