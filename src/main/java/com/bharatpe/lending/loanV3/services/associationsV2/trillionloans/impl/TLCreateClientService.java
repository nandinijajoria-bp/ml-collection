package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
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
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLCreateClientRequestDto;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.TLCreateClientResponseDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.validations.TLPayloadValidation;
import com.bharatpe.lending.loanV3.services.associationsV2.wrapper.InvokeCreateLeadAndDocUploadWrapperService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.*;

@Slf4j
@Service
public class TLCreateClientService {

    @Autowired
    KycUtils kycUtils;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TLPayloadValidation payloadValidation;

    @Autowired
    ConverterUtils converterUtils;

    @Autowired
    MerchantService merchantService;

    @Transactional
    public boolean invokeCreateClient(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("Application Id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            if (InvokeCreateLeadAndDocUploadWrapperService.kycDataNeeded(LenderAssociationStages.CREATE_LEAD.name()) && ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));
            }
            if (payloadValidation.isInValidCreateClientPayload(lenderAssociationDetailsDto.getCKycResponseDto())) {
                log.info("invalid response from downstream api for createClient of TrillionLoans : {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.CREATE_CLIENT_FAILED);
                return false;
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.CREATE_CLIENT.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
            NBFCRequestDTO createLeadRequest = getPayload(lenderAssociationDetailsDto);
            if (Objects.isNull(createLeadRequest)) {
                log.info("error in create lead payload of TrillionLoans for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.CREATE_CLIENT_FAILED);
                return false;
            }
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(createLeadRequest, LenderAssociationStages.CREATE_CLIENT);
            log.info("create lead response of TrillionLoans from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                log.info("createLead request of TrillionLoans success for {}", lenderAssociationDetailsDto.getApplicationId());
                TLCreateClientResponseDto createClientResponse = objectMapper.readValue( objectMapper.writeValueAsString(nbfcResponseDto.getData()), TLCreateClientResponseDto.class);
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setCccId(createClientResponse.getClientId().toString());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_SUCCESS.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return true;
            }
        } catch (Exception e) {
            log.info("exception occurred while processing create client of TrillionLoans for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.CREATE_CLIENT_FAILED);
        return false;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        CKycResponseDto cKycResponseDto = lenderAssociationDetailsRequest.getCKycResponseDto();
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(TLCreateClientRequestDto.builder()
                            .clientDetails(getClientDetails(lendingApplication, cKycResponseDto))
                            .addressDetails(getAddressDetails(lendingApplication, cKycResponseDto))
                            .bankDetails(getBankDetails(lendingApplication, cKycResponseDto))
                            .clientIdentifierDetails(getClientIdentifier(cKycResponseDto))
                            .employmentDetails(getEmploymentDetails())
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating payload of Create Client of TrillionLoans for {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private TLCreateClientRequestDto.EmploymentDetails getEmploymentDetails() {
        return TLCreateClientRequestDto.EmploymentDetails.builder()
                .companyType("Partnership") // constant as per TL
                .employmentType("SELF") // constant as per TL
                .build();
    }

    private TLCreateClientRequestDto.ClientDetails getClientDetails(LendingApplication lendingApplication, CKycResponseDto cKycResponseDto) {
        String mobile = ObjectUtils.isEmpty(cKycResponseDto.getBureauMobile()) ? kycUtils.getMobileFromKycData(cKycResponseDto) : cKycResponseDto.getBureauMobile();
        return TLCreateClientRequestDto.ClientDetails.builder()
                .firstName(kycUtils.getFirstName(cKycResponseDto))
                .middleName(kycUtils.getMiddleName(cKycResponseDto))
                .lastName(kycUtils.getLastName(cKycResponseDto))
                .dateOfBirth(DateTimeUtil.formatDate(cKycResponseDto.getDob(), "dd/MM/yyyy",  "dd-MM-yyyy"))
                .gender(kycUtils.getGender(cKycResponseDto.getGender()))
                .mobileNo(mobile)
                .externalId(lendingApplication.getExternalLoanId())
                .build();
    }

    public List<TLCreateClientRequestDto.AddressDetails> getAddressDetails(LendingApplication lendingApplication, CKycResponseDto cKycResponseDto) {
        List<TLCreateClientRequestDto.AddressDetails> addressDataList = new ArrayList<>();
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
        TLCreateClientRequestDto.AddressDetails currentAddress = TLCreateClientRequestDto.AddressDetails.builder()
                .addressType(Collections.singletonList("PERMANENT")) //setting PERMANENT as default value
                .addressLineOne(address1)
                .addressLineTwo(address2)
                .postalCode(ObjectUtils.isEmpty(lendingApplication.getPincode()) ? cKycResponseDto.getPincode() : lendingApplication.getPincode().toString())
                .build();
        addressDataList.add(currentAddress);
        return addressDataList;
    }

    private List<TLCreateClientRequestDto.BankDetails> getBankDetails(LendingApplication lendingApplication, CKycResponseDto cKycResponseDto) {
        //TODO Need to verify with product, do we need to use nach bankDetails here or merchant bank details
        final MerchantDetailsDto merchantDetailsDto = merchantService.fetchMerchantDetails(lendingApplication.getMerchantId(), Arrays.asList(
                Constants.MerchantUtil.Scope.BANK_DETAIL,
                Constants.MerchantUtil.Scope.MERCHANT_USER
        ));
        if(ObjectUtils.isEmpty(merchantDetailsDto)) {
            log.info("merchant bank details not found for application {} with merchantId {}", lendingApplication.getId(), lendingApplication.getMerchantId());
            throw new RuntimeException("merchant bank details not found for application " + lendingApplication.getId());
        }
        List<TLCreateClientRequestDto.BankDetails> bankDetailsDataList = new ArrayList<>();
        TLCreateClientRequestDto.BankDetails bankDetails = TLCreateClientRequestDto.BankDetails.builder()
                .accountNumber(merchantDetailsDto.getBankDetail().getAccountNumber().trim())
                .accountType("current".equalsIgnoreCase(merchantDetailsDto.getBankDetail().getAccountType()) ? "CURRENTACCOUNT" : "SAVINGSACCOUNT") // for current : CURRENTACCOUNT
                .ifscCode(merchantDetailsDto.getBankDetail().getIfsc())
                .name(merchantDetailsDto.getBankDetail().getBeneficiaryName())
                .supportedForDisbursement(Boolean.TRUE)
                .supportedForRepayment(Boolean.TRUE)
                .build();
        bankDetailsDataList.add(bankDetails);
        return bankDetailsDataList;
    }

    private List<TLCreateClientRequestDto.ClientIdentifierDetails> getClientIdentifier(CKycResponseDto cKycResponseDto) {
        List<TLCreateClientRequestDto.ClientIdentifierDetails> clientIdentifierDetailsList = new ArrayList<>();
        TLCreateClientRequestDto.ClientIdentifierDetails clientIdentifierPan = TLCreateClientRequestDto.ClientIdentifierDetails.builder()
                .documentType("PAN")
                .documentKey(cKycResponseDto.getPanNumber())
                .build();
        clientIdentifierDetailsList.add(clientIdentifierPan);
        return clientIdentifierDetailsList;
    }
}
