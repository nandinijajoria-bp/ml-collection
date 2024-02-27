package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
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
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLUpdateLeadRequestDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.validations.TLPayloadValidation;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
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
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.UPDATE_LEAD.name());
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.UPDATE_LEAD_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);

            lenderAssociationDetailsRequestDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsRequestDto.getMerchantId()));
            NBFCRequestDTO updateLeadRequestDto = getPayload(lenderAssociationDetailsRequestDto);
            if (Objects.isNull(updateLeadRequestDto)) {
                log.info("error in update lead payload of TrillionLoans for applicationId: {}", lenderAssociationDetailsRequestDto.getApplicationId());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.UPDATE_LEAD_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.UPDATE_LEAD_FAILED);
                return false;
            }
            NBFCResponseDTO updateLeadResponseDTO = lenderAPIGateway.invokeStage(updateLeadRequestDto, LenderAssociationStages.UPDATE_LEAD);
            log.info("update lead response of TrillionLoans from nbfc: {} with applicationId: {}", updateLeadResponseDTO, lenderAssociationDetailsRequestDto.getApplicationId());
            if (Objects.nonNull(updateLeadResponseDTO) && updateLeadResponseDTO.getSuccess() && Objects.nonNull(updateLeadResponseDTO.getData())) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.UPDATE_LEAD_COMPLETED.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                return true;
            }
        } catch (Exception e) {
            log.error("error while pushing update lead of TrillionLoans for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.UPDATE_LEAD_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.UPDATE_LEAD_FAILED);
        return Boolean.FALSE;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        CKycResponseDto cKycResponseDto = lenderAssociationDetailsRequest.getCKycResponseDto();
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails();
        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(TLUpdateLeadRequestDto.builder()
                            .clientId(Long.valueOf(lendingApplicationLenderDetails.getCccId()))
                            .clientDetails(getClientDetails(cKycResponseDto))
                            .addressDetails(getAddressDetails(cKycResponseDto))
                            .bankDetails(getBankDetails(lendingApplication, cKycResponseDto))
                            .clientIdentifierDetails(getClientIdentifier(cKycResponseDto))
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating payload of Create Client of TrillionLoans for {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private TLUpdateLeadRequestDto.ClientDetails getClientDetails(CKycResponseDto cKycResponseDto) {
        return TLUpdateLeadRequestDto.ClientDetails.builder()
                .firstName(kycUtils.getFirstName(cKycResponseDto))
                .middleName(kycUtils.getMiddleName(cKycResponseDto))
                .lastName(kycUtils.getLastName(cKycResponseDto))
                .dateOfBirth(DateTimeUtil.formatDate(cKycResponseDto.getDob(), "dd/MM/yyyy",  "dd-MM-yyyy"))
                .gender(kycUtils.getGender(cKycResponseDto.getGender()))
                .mobileNo(ObjectUtils.isEmpty(cKycResponseDto.getMobile()) ? "" : cKycResponseDto.getMobile().substring(2))
                .build();
    }

    public List<TLUpdateLeadRequestDto.AddressDetails> getAddressDetails(CKycResponseDto cKycResponseDto) {
        List<TLUpdateLeadRequestDto.AddressDetails> addressDataList = new ArrayList<>();
        String address = converterUtils.parseData(cKycResponseDto.getAddress());
        int addressSize = address.length();
        String address1 = "", address2 = "";
        if (addressSize <= 150) {
            address1 = address;
        } else if (addressSize <= 300) {
            address1 = address.substring(0, 150);
            address2 = address.substring(150, addressSize);
        } else {
            address1 = address.substring(0, 150);
            address2 = address.substring(150, 300);
        }
        TLUpdateLeadRequestDto.AddressDetails currentAddress = TLUpdateLeadRequestDto.AddressDetails.builder()
                .addressType(Collections.singletonList("PERMANENT"))  // 13 : PermanentAddress, 14 : ResidentialAddress
                .addressLineOne(address1)
                .addressLineTwo(address2)
                .postalCode(cKycResponseDto.getPincode())
                .build();
        addressDataList.add(currentAddress);
        return addressDataList;
    }

    private List<TLUpdateLeadRequestDto.BankDetails> getBankDetails(LendingApplication lendingApplication, CKycResponseDto cKycResponseDto) {
        //TODO Need to verify with product, do we need to use nach bankDetails here or merchant bank details
        final MerchantDetailsDto merchantDetailsDto = merchantService.fetchMerchantDetails(lendingApplication.getMerchantId(), Arrays.asList(
                Constants.MerchantUtil.Scope.BANK_DETAIL,
                Constants.MerchantUtil.Scope.MERCHANT_USER
        ));
        if (ObjectUtils.isEmpty(merchantDetailsDto)) {
            log.info("merchant bank details not found for application {} with merchantId {}", lendingApplication.getId(), lendingApplication.getMerchantId());
            throw new RuntimeException("merchant bank details not found for application " + lendingApplication.getId());
        }
        List<TLUpdateLeadRequestDto.BankDetails> bankDetailsDataList = new ArrayList<>();
        TLUpdateLeadRequestDto.BankDetails bankDetails = TLUpdateLeadRequestDto.BankDetails.builder()
                .accountNumber(merchantDetailsDto.getBankDetail().getAccountNumber())
                .accountType("current".equalsIgnoreCase(merchantDetailsDto.getBankDetail().getAccountType()) ? "CURRENTACCOUNT" : "SAVINGSACCOUNT") // for current : CURRENTACCOUNT
                .ifscCode(merchantDetailsDto.getBankDetail().getIfsc())
                .name(merchantDetailsDto.getBankDetail().getBeneficiaryName())
                .supportedForDisbursement(Boolean.TRUE)
                .supportedForRepayment(Boolean.TRUE)
                .build();
        bankDetailsDataList.add(bankDetails);
        return bankDetailsDataList;
    }

    private List<TLUpdateLeadRequestDto.ClientIdentifierDetails> getClientIdentifier(CKycResponseDto cKycResponseDto) {
        List<TLUpdateLeadRequestDto.ClientIdentifierDetails> clientIdentifierDetailsList = new ArrayList<>();
        TLUpdateLeadRequestDto.ClientIdentifierDetails clientIdentifierPan = TLUpdateLeadRequestDto.ClientIdentifierDetails.builder()
                .documentType("PAN")
                .documentKey(cKycResponseDto.getPanNumber())
                .build();
        clientIdentifierDetailsList.add(clientIdentifierPan);
        return clientIdentifierDetailsList;
    }
}
