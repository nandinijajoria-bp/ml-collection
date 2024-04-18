package com.bharatpe.lending.loanV3.services.associationsV2.capri.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingMerchantDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingMerchantDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
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
import com.bharatpe.lending.loanV3.dto.request.capri.CapriCreateClientRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.capri.CapriCreateClientResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.capri.validations.CapriPayloadValidation;
import com.bharatpe.lending.loanV3.services.associationsV2.wrapper.InvokeCreateLeadAndDocUploadWrapperService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
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
public class CapriCreateClientService {

    @Autowired
    KycUtils kycUtils;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CapriPayloadValidation capriPayloadValidation;

    @Autowired
    ConverterUtils converterUtils;

    @Autowired
    MerchantService merchantService;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Value("${capri.client.officeName:Head Office}")
    String capriClientOfficeName;

    @Transactional
    public boolean invokeCreateClient(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("Application Id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            if (InvokeCreateLeadAndDocUploadWrapperService.kycDataNeeded(LenderAssociationStages.CREATE_CLIENT.name()) && ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));
            }
            if (capriPayloadValidation.isInValidCreateClientPayload(lenderAssociationDetailsDto.getCKycResponseDto())) {
                log.info("invalid response from downstream api for createClient of Capri : {}", lenderAssociationDetailsDto.getApplicationId());
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
                log.info("error in create lead payload of Capri for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.CREATE_CLIENT_FAILED);
                return false;
            }
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(createLeadRequest, LenderAssociationStages.CREATE_CLIENT);
            log.info("create lead response of Capri from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                log.info("createLead request of Capri success for {}", lenderAssociationDetailsDto.getApplicationId());
                CapriCreateClientResponseDTO createClientResponse = objectMapper.readValue( objectMapper.writeValueAsString(nbfcResponseDto.getData()), CapriCreateClientResponseDTO.class);
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setCccId(createClientResponse.getClientId().toString());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_SUCCESS.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return true;
            }
        } catch (Exception e) {
            log.info("exception occurred while processing create client of Capri for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.CREATE_CLIENT_FAILED);
        return false;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        CKycResponseDto cKycResponseDto = lenderAssociationDetailsRequest.getCKycResponseDto();
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        try {
            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
            if(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)) {
                throw new RuntimeException("lending risk variable snapshot not found for application " + lendingApplication.getId());
            }
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(CapriCreateClientRequestDTO.builder()
                            .locale("en")
                            .dateFormat("dd-MM-yyyy")
                            .clientData(getClientData(cKycResponseDto, lendingApplication))
                            .addressData(getAddress(cKycResponseDto))
                            .bankDetailsData(getBankDetails(lendingApplication))
                            .clientIdentifierData(getClientIdentifier(cKycResponseDto))
                            .employmentDetailData(getEmploymentDetails(lendingRiskVariablesSnapshot, lendingApplication))
                            .additionalDetail(CapriCreateClientRequestDTO.AdditionalDetail.builder()
                                    .dataTableName("BPE")
                                    .appTable("m_client")
                                    .cibil(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getBureauScore()) ? "" : lendingRiskVariablesSnapshot.getBureauScore().toString())
                                    .build())
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating payload of Create Client of Capri for {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private CapriCreateClientRequestDTO.ClientData getClientData(CKycResponseDto cKycResponseDto, LendingApplication lendingApplication) {
         return CapriCreateClientRequestDTO.ClientData.builder()
                 .firstName(kycUtils.getFirstName(cKycResponseDto))
                 .middleName(kycUtils.getMiddleName(cKycResponseDto))
                 .lastName(kycUtils.getLastName(cKycResponseDto))
                 .gender(getGender(kycUtils.getGender(cKycResponseDto.getGender()))) // 23 : Male, 24 : Female
                 .mobileNo(kycUtils.getMobileFromKycData(cKycResponseDto))
                 .dateOfBirth(DateTimeUtil.formatDate(cKycResponseDto.getDob(), "dd/MM/yyyy",  "dd-MM-yyyy"))
                 .officeName(capriClientOfficeName)    // Fixed value from capri
                 .externalId(UUID.randomUUID().toString())
                 .build();
    }

    private String getGender(String gender) {
        if(!ObjectUtils.isEmpty(gender)) {
            if("male".equalsIgnoreCase(gender)) {
                return "Male";
            }
            return "Female";
        }
        return "Female";
    }

    public List<CapriCreateClientRequestDTO.AddressData> getAddress(CKycResponseDto cKycResponseDto) {
        List<CapriCreateClientRequestDTO.AddressData> addressDataList = new ArrayList<>();
        String address = converterUtils.parseData(cKycResponseDto.getAddress());
        int addressSize = address.length();
        String address1 = "", address2 = "";
        if (addressSize <= 40) {
            address1 = address;
        } else {
            address1 = address.substring(0, 40);
            address2 = address.substring(40, addressSize);
        }
        CapriCreateClientRequestDTO.AddressData currentAddress = CapriCreateClientRequestDTO.AddressData.builder()
                .addressType(Arrays.asList("Permanent Address"))  // 13 : PermanentAddress, 14 : ResidentialAddress
                .addressLineOne(address1)
                .addressLineTwo(address2)
                .postalCode(cKycResponseDto.getPincode())
                .ownershipType("own")
                .landmark(".")
                .build();
        addressDataList.add(currentAddress);
        return addressDataList;
    }

    private List<CapriCreateClientRequestDTO.BankDetailsData> getBankDetails(LendingApplication lendingApplication) {
        final MerchantDetailsDto merchantDetailsDto = merchantService.fetchMerchantDetails(lendingApplication.getMerchantId(), Arrays.asList(
                Constants.MerchantUtil.Scope.BANK_DETAIL,
                Constants.MerchantUtil.Scope.MERCHANT_USER
        ));
        if(ObjectUtils.isEmpty(merchantDetailsDto)) {
            log.info("merchant bank details not found for application {} with merchantId {}", lendingApplication.getId(), lendingApplication.getMerchantId());
            throw new RuntimeException("merchant bank details not found for application " + lendingApplication.getId());
        }
        List<CapriCreateClientRequestDTO.BankDetailsData> bankDetailsDataList = new ArrayList<>();
        CapriCreateClientRequestDTO.BankDetailsData bankDetails = CapriCreateClientRequestDTO.BankDetailsData.builder()
                .accountType("current".equalsIgnoreCase(merchantDetailsDto.getBankDetail().getAccountType()) ? "CURRENTACCOUNT" : "SAVINGSACCOUNT")
                .name(merchantDetailsDto.getBankDetail().getBeneficiaryName())
                .accountNumber(merchantDetailsDto.getBankDetail().getAccountNumber())
                .ifscCode(merchantDetailsDto.getBankDetail().getIfsc())
                .supportedForDisbursement(Boolean.TRUE)
                .supportedForRepayment(Boolean.TRUE)
                .build();
        bankDetailsDataList.add(bankDetails);
        return bankDetailsDataList;
    }

    private List<CapriCreateClientRequestDTO.ClientIdentifierData> getClientIdentifier(CKycResponseDto cKycResponseDto) {
        List<CapriCreateClientRequestDTO.ClientIdentifierData> clientIdentifierDataList = new ArrayList<>();
        CapriCreateClientRequestDTO.ClientIdentifierData clientIdentifierPan = CapriCreateClientRequestDTO.ClientIdentifierData.builder()
                .documentKey(cKycResponseDto.getPanNumber())
                .documentType("PAN")
                .build();
        clientIdentifierDataList.add(clientIdentifierPan);
        return clientIdentifierDataList;
    }

    private CapriCreateClientRequestDTO.EmploymentDetailData getEmploymentDetails(LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot, LendingApplication lendingApplication) {
        return CapriCreateClientRequestDTO.EmploymentDetailData.builder()
                .currentEmployerName(lendingApplication.getBusinessName())
                .monthlySalary(String.valueOf(lendingRiskVariablesSnapshot.getMonthlyIncome()))
                .occupationType("SENP")   // Need to verify with product
                .employmentType("SELF")       // Need to verify with product
                .build();
    }
}
