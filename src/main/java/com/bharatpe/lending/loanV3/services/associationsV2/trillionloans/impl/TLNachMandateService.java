package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLNachMandateRequestDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.validations.TLPayloadValidation;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class TLNachMandateService {
    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    TLPayloadValidation payloadValidation;

    @Autowired
    EnachHandler enachHandler;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    MerchantService merchantService;
    @Autowired
    LoanUtil loanUtil;

    @Autowired
    AutoPayUPIDao autoPayUPIDao;

    @Transactional
    public Boolean invokeNachMandate(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        try {
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.NACH_MANDATE_PENDING.name());
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.NACH_MANDATE.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequest);

            NBFCRequestDTO<?> nachManadateRequest = getPayload(lenderAssociationDetailsRequest);
            if (ObjectUtils.isEmpty(nachManadateRequest)) {
                log.info("error in nach mandate payload of TrillionLoans for applicationId: {}", lenderAssociationDetailsRequest.getApplicationId());
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.NACH_MANDATE_FAILED.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequest);
                return false;
            }
            NBFCResponseDTO<?> nbfcResponseDTO = lenderAPIGateway.invokeStage(nachManadateRequest, LenderAssociationStages.NACH_MANDATE);
            log.info("nach mandate response of TrillionLoans from nbfc: {} with applicationId: {}", nbfcResponseDTO, lenderAssociationDetailsRequest.getApplicationId());
            if (Objects.nonNull(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && Objects.nonNull(nbfcResponseDTO.getData())) {
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.NACH_MANDATE_SUCCESS.name());
                commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                return true;
            }
        } catch (Exception e) {
            log.error("error while pushing nach mandate request of TrillionLoans for  {} {} {}", lenderAssociationDetailsRequest.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.NACH_MANDATE_FAILED.name());
        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setSanctionStatus(LenderAssociationStages.NACH_MANDATE.name());
        commonService.manageApplicationState(lenderAssociationDetailsRequest);
        return Boolean.FALSE;
    }

    private NBFCRequestDTO<?> getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        Date expiryDate = Date.from(LocalDate.now().plusYears(2).atStartOfDay(ZoneId.systemDefault()).toInstant());
        try {
            LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
            TLNachMandateRequestDto mandateDetails = null;
            if(loanUtil.isMandateSwitchEnabled(lendingApplication) && lendingApplicationDetails.isAutoPayUpiEligible() && !lendingApplicationDetails.isNachEligible()){
                log.info("nach mandate is not eligible for TrillionLoans merchantId {} and application {}, fetching Upi Autopay Details", lendingApplication.getMerchantId(), lendingApplication.getId());
                mandateDetails = fetchUpiAutopayMandateDetails(lendingApplication, lenderAssociationDetailsRequest, expiryDate);
            }
            else {
                log.info("Fetching NACH mandate details for TrillionLoans merchantId {} and application {}", lendingApplication.getMerchantId(), lendingApplication.getId());
                Long nachApplicationId = lendingApplicationDetails.getIsNachSkip() ? null : lendingApplication.getId();
                MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = enachHandler.findByMerchantIdAndApplicationIdAndLender(lendingApplication.getMerchantId(), nachApplicationId, loanUtil.enachServiceLenderMapper(lendingApplication.getLender()));
                if (!ObjectUtils.isEmpty(merchantNachDetailsResponseDTO)) {
                    mandateDetails = TLNachMandateRequestDto.builder()
                            .leadId(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLeadId())
                            .status("DESTINATION_ACCEPTED")
                            .umrn(merchantNachDetailsResponseDTO.getProviderUmrn())
                            .bankAccountHolderName(merchantNachDetailsResponseDTO.getBeneficiaryName())
                            .bankName(merchantNachDetailsResponseDTO.getBankName())
                            .branchName(merchantNachDetailsResponseDTO.getBranchName())
                            .bankAccountNumber(merchantNachDetailsResponseDTO.getAccountNumber())
                            .micr(merchantNachDetailsResponseDTO.getMicrNumber())
                            .ifsc(merchantNachDetailsResponseDTO.getIfscCode())
                            .bankAccountType(getAccountType(merchantNachDetailsResponseDTO.getAccountType()))
                            .mandateRegistrationRequestedDate(DateTimeUtil.getDateInFormat(new Date(), "dd-MM-yyyy"))
                            .periodStartDate(DateTimeUtil.getDateInFormat(new Date(), "dd-MM-yyyy"))
                            .periodEndDate(DateTimeUtil.getDateInFormat(expiryDate, "dd-MM-yyyy"))
                            .periodUntilCancelled(Boolean.TRUE)
                            .debitTypeEnum("FIXED_AMOUNT")
                            .debitFrequencyEnum("DAILY")
                            .amount(merchantNachDetailsResponseDTO.getNachAmount())
                            .externalRefernceNumber(merchantNachDetailsResponseDTO.getMandateId())
                            .mode(merchantNachDetailsResponseDTO.getNachMode())
                            .build();
                }
            }
            log.info("Fetched mandate details for TrillionLoans merchantId {} and applicationId {}: {}", lendingApplication.getMerchantId(), lendingApplication.getId(), mandateDetails);
            if (payloadValidation.isInvalidNachMandatePayload(mandateDetails)) {
                log.info("error in getting mandate details payload for TrillionLoans merchantId {} and application {}", lendingApplication.getMerchantId(), lendingApplication.getId());
                return null;
            }
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(mandateDetails)
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating nach mandate payload of TrillionLoans for applicationId {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private TLNachMandateRequestDto fetchUpiAutopayMandateDetails(LendingApplication lendingApplication, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest, Date expiryDate) {
        log.info("Fetching UPI autopay mandate details for applicationId: {}", lendingApplication.getId());
        AutoPayUPI autoPayUPI = autoPayUPIDao.findByApplicationIdAndStatusOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender(), "ACTIVE");
        if(Objects.isNull(autoPayUPI)) {
            log.info("No active UPI autopay mandate found for applicationId: {}", lendingApplication.getId());
            return null;
        }
        log.info("Found UPI autopay mandate for applicationId: {} with mandateId: {}", lendingApplication.getId(), autoPayUPI.getMandateId());
        Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(lendingApplication.getMerchantId());
        BankDetailsDto merchantBankDetail = null;
        if (bankDetailsDtoOptional.isPresent())
            merchantBankDetail = bankDetailsDtoOptional.get();

        if (ObjectUtils.isEmpty(merchantBankDetail)) {
            return null;
        }
        log.info("Fetched merchant bank details for applicationId: {}", lendingApplication.getId());
        TLNachMandateRequestDto mandateDetails = TLNachMandateRequestDto.builder()
                .leadId(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLeadId())
                .status("DESTINATION_ACCEPTED")
                .umrn(autoPayUPI.getMandateId())
                .bankAccountHolderName(merchantBankDetail.getBeneficiaryName())
                .bankName(merchantBankDetail.getBankName())
                .bankAccountNumber(merchantBankDetail.getAccountNumber())
                .ifsc(merchantBankDetail.getIfsc())
                .bankAccountType(getAccountType(merchantBankDetail.getAccountType()))
                .mandateRegistrationRequestedDate(DateTimeUtil.getDateInFormat(new Date(), "dd-MM-yyyy"))
                .periodStartDate(DateTimeUtil.getDateInFormat(new Date(), "dd-MM-yyyy"))
                .periodEndDate(DateTimeUtil.getDateInFormat(expiryDate, "dd-MM-yyyy"))
                .periodUntilCancelled(Boolean.TRUE)
                .debitTypeEnum("FIXED_AMOUNT")
                .debitFrequencyEnum("DAILY")
                .amount(autoPayUPI.getAmount())
                .externalRefernceNumber(autoPayUPI.getMandateId())
                .mode("UPI")
                .build();
        log.info("Fetched UPI autopay mandate details for applicationId: {}", lendingApplication.getId());
        return mandateDetails;
    }

    private String getAccountType(String accountType) {
        if ("CURRENT".equalsIgnoreCase(accountType))
            return "CA";
        else if ("SAVINGS".equalsIgnoreCase(accountType))
            return "SB";
        else
            return "Other";
    }
}
