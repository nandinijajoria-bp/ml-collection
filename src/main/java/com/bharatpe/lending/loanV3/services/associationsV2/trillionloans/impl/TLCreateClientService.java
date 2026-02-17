package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingMerchantDetailsDao;
import com.bharatpe.lending.common.entity.LendingMerchantDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.service.merchant.constants.Constants;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.config.TrillionLoansConfig;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.NameAndDobDetailsDto;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLCreateClientRequestDto;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.TLCreateClientResponseDto;
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

    @Autowired
    TrillionLoansConfig trillionLoansConfig;

    @Autowired
    LendingMerchantDetailsDao lendingMerchantDetailsDao;

    private static final Map<String, String> allowedRegexMap;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("BANK_BENE_NAME",
                "^[a-zA-Z0-9.!@#$%&*()_\\-+\\[\\],<>/\\\\{}?:;\"^' ]{1,}$"
        );
        map.put("ADDRESS_LINE",
                "^[a-zA-Z0-9:,@+ _\\t\\r\\n\"()\\.\\-{}\\[\\]/\\\\]+$"
        );
        allowedRegexMap = Collections.unmodifiableMap(map);
    }

    @Transactional
    public boolean invokeCreateClient(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        boolean updateClientException = false;
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("Application Id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            boolean isTopup =  LoanType.TOPUP.name().equalsIgnoreCase(lenderAssociationDetailsDto.getLendingApplication().getLoanType());
            boolean isEligibleForLenderKycOrSkipKyc = kycUtils.isEligibleForLenderKyc(Lender.TRILLIONLOANS.name(), lenderAssociationDetailsDto.getLendingApplication().getMerchantId(),isTopup)
                                                      || kycUtils.isEligibleForSkipKyc(lenderAssociationDetailsDto.getLendingApplication().getId(), Lender.TRILLIONLOANS, lenderAssociationDetailsDto.getLendingApplication().getMerchantId(), isTopup);
            lenderAssociationDetailsDto.setCKycResponseDto(isEligibleForLenderKycOrSkipKyc ? kycUtils.getPanData(lenderAssociationDetailsDto.getMerchantId()) : kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));

            if (payloadValidation.isInValidCreateClientPayload(lenderAssociationDetailsDto.getCKycResponseDto(), isEligibleForLenderKycOrSkipKyc)) {
                log.info("invalid response from downstream api for createClient of TrillionLoans : {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.CREATE_CLIENT_FAILED);
                return false;
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.CREATE_CLIENT.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
            NBFCRequestDTO<?> createClientRequest = getPayload(lenderAssociationDetailsDto, isEligibleForLenderKycOrSkipKyc);
            if (Objects.isNull(createClientRequest)) {
                log.info("error in create client payload of TrillionLoans for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.CREATE_CLIENT_FAILED);
                return false;
            }
            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(createClientRequest, LenderAssociationStages.CREATE_CLIENT, trillionLoansConfig.getCreateClientTimeoutThreshold());
            log.info("create client response of TrillionLoans from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto)) {
                if(nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                    log.info("create client request of TrillionLoans success for {}", lenderAssociationDetailsDto.getApplicationId());
                    TLCreateClientResponseDto createClientResponse = objectMapper.convertValue(nbfcResponseDto.getData(), TLCreateClientResponseDto.class);
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setCccId(createClientResponse.getClientId().toString());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_SUCCESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }
                if(nbfcResponseDto.getRetry()) {
                    log.info("createClient request of trillionLoans pushed to retry for {}", lenderAssociationDetailsDto.getApplicationId());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.CREATE_CLIENT_RETRY.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("exception occurred while processing create client of TrillionLoans for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(updateClientException ? LenderAssociationStatus.UPDATE_CLIENT_FAILED.name() : LenderAssociationStatus.CREATE_CLIENT_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, updateClientException ? LenderAssociationStatus.UPDATE_CLIENT_FAILED : LenderAssociationStatus.CREATE_CLIENT_FAILED);
        return false;
    }

    private NBFCRequestDTO<?> getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest, boolean isEligibleForLenderKyc) {
        CKycResponseDto cKycResponseDto = lenderAssociationDetailsRequest.getCKycResponseDto();
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(TLCreateClientRequestDto.builder()
                            .clientDetails(getClientDetails(lendingApplication, cKycResponseDto, isEligibleForLenderKyc))
                            .addressDetails(!isEligibleForLenderKyc ? getAddressDetails(lendingApplication, cKycResponseDto) : null)
                            .bankDetails(getBankDetails(lendingApplication, cKycResponseDto))
                            .clientIdentifierDetails(getClientIdentifier(cKycResponseDto))
                            .employmentDetails(getEmploymentDetails())
                            .additionalDetails(getAdditionalDetails(lendingApplication, cKycResponseDto))
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Exception in creating payload of Create Client of TrillionLoans for {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private List<TLCreateClientRequestDto.AdditionalDetails> getAdditionalDetails(LendingApplication lendingApplication, CKycResponseDto cKycResponseDto) {
        log.info("fetching additional details for create client of TrillionLoans for applicationId {}", lendingApplication.getId());
        List<TLCreateClientRequestDto.AdditionalDetails> additionalDetailList = new ArrayList<>();
        MerchantDetailsDto merchantDetailsDto = merchantService.fetchMerchantDetails(lendingApplication.getMerchantId());
        CKycResponseDto gstResponse = kycUtils.getGstData(lendingApplication.getMerchantId());
        log.info("gst details fetched from kyc service for create client of TrillionLoans for applicationId {} is {}", lendingApplication.getId(), gstResponse);
        LendingMerchantDetails merchantDetails = lendingMerchantDetailsDao.findTop1ByMerchantIdOrderByIdDesc(lendingApplication.getMerchantId());

        TLCreateClientRequestDto.AdditionalDetails additionalDetail = TLCreateClientRequestDto.AdditionalDetails.builder()
                .dataTableName("merchant_details")
                .appTable("m_client")
                .subIndustry(ObjectUtils.isEmpty(merchantDetails.getBusinessSubCategory()) ? null : merchantDetails.getBusinessSubCategory())
                .industry(ObjectUtils.isEmpty(merchantDetails.getBusinessCategory()) ? null : merchantDetails.getBusinessCategory())
                .businessAddress(ObjectUtils.isEmpty(cKycResponseDto.getAddress()) ? null : cKycResponseDto.getAddress())
                .state(ObjectUtils.isEmpty(cKycResponseDto.getState()) ? null : cKycResponseDto.getState())
                .city(ObjectUtils.isEmpty(cKycResponseDto.getCity()) ? null : cKycResponseDto.getCity())
                .country("India")
                .postalCode(ObjectUtils.isEmpty(lendingApplication.getPincode()) ? null : lendingApplication.getPincode())
                .gstNumber(ObjectUtils.isEmpty(gstResponse.getGstNumber()) ? null : gstResponse.getGstNumber())
                .businessDocument(ObjectUtils.isEmpty(gstResponse.getName()) ? null : gstResponse.getName())
                .legalName(ObjectUtils.isEmpty(merchantDetails.getBusinessName()) ? null : merchantDetails.getBusinessName())
                .tradeName(ObjectUtils.isEmpty(gstResponse.getTradeName()) ? null : gstResponse.getTradeName())
                .bankBeneName(ObjectUtils.isEmpty(merchantDetailsDto) || ObjectUtils.isEmpty(merchantDetailsDto.getBankDetail()) || ObjectUtils.isEmpty(merchantDetailsDto.getBankDetail().getBeneficiaryName()) ? null : converterUtils.sanitizeByRegex(merchantDetailsDto.getBankDetail().getBeneficiaryName(), allowedRegexMap.getOrDefault("BANK_BENE_NAME", null)) )
                .build();
        additionalDetailList.add(additionalDetail);
        return additionalDetailList;
    }

    private TLCreateClientRequestDto.EmploymentDetails getEmploymentDetails() {
        return TLCreateClientRequestDto.EmploymentDetails.builder()
                .companyType("Partnership") // constant as per TL
                .employmentType("SELF") // constant as per TL
                .build();
    }

    private TLCreateClientRequestDto.ClientDetails getClientDetails(LendingApplication lendingApplication, CKycResponseDto cKycResponseDto, boolean isEligibleForLenderKyc) {
        String mobile = ObjectUtils.isEmpty(cKycResponseDto.getBureauMobile()) ? kycUtils.getMobileFromKycData(cKycResponseDto) : cKycResponseDto.getBureauMobile();
        NameAndDobDetailsDto nameAndDobDetailsDto = kycUtils.getNameAndDobValues(cKycResponseDto, lendingApplication.getMerchantId());
        updateLastNameAndMiddleName(nameAndDobDetailsDto);
        String gender = kycUtils.getGender(cKycResponseDto.getGender());
        return  isEligibleForLenderKyc ? TLCreateClientRequestDto.ClientDetails.builder()
                .firstName(
                        !ObjectUtils.isEmpty(nameAndDobDetailsDto.getFirstName()) ? nameAndDobDetailsDto.getFirstName() :
                                !ObjectUtils.isEmpty(nameAndDobDetailsDto.getMiddleName()) ? nameAndDobDetailsDto.getMiddleName() :
                                        nameAndDobDetailsDto.getLastName()
                )
                .middleName(nameAndDobDetailsDto.getMiddleName())
                .lastName(ObjectUtils.isEmpty(nameAndDobDetailsDto.getLastName()) ? nameAndDobDetailsDto.getFirstName() : nameAndDobDetailsDto.getLastName())
                .dateOfBirth(DateTimeUtil.formatDate(nameAndDobDetailsDto.getDob(), "dd/MM/yyyy",  "dd-MM-yyyy"))
                .gender("OTHERS".equalsIgnoreCase(gender) ? "OTHER" : gender)
                .mobileNo(mobile)
                .externalId(lendingApplication.getExternalLoanId())
                .build() :
                TLCreateClientRequestDto.ClientDetails.builder()
                        .firstName(
                                !ObjectUtils.isEmpty(kycUtils.getFirstName(cKycResponseDto)) ? kycUtils.getFirstName(cKycResponseDto) :
                                        !ObjectUtils.isEmpty(kycUtils.getMiddleName(cKycResponseDto)) ? kycUtils.getMiddleName(cKycResponseDto) :
                                                kycUtils.getLastName(cKycResponseDto)
                        )
                        .middleName(kycUtils.getMiddleName(cKycResponseDto))
                        .lastName(ObjectUtils.isEmpty(kycUtils.getLastName(cKycResponseDto)) ? kycUtils.getFirstName(cKycResponseDto) : kycUtils.getLastName(cKycResponseDto))
                        .dateOfBirth(DateTimeUtil.formatDate(nameAndDobDetailsDto.getDob(), "dd/MM/yyyy",  "dd-MM-yyyy"))
                        .gender("OTHERS".equalsIgnoreCase(gender) ? "OTHER" : gender)
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
                .addressLineOne(converterUtils.sanitizeByRegex(address1, allowedRegexMap.getOrDefault("ADDRESS_LINE", null)))
                .addressLineTwo(converterUtils.sanitizeByRegex(address2, allowedRegexMap.getOrDefault("ADDRESS_LINE", null)))
                .postalCode(ObjectUtils.isEmpty(cKycResponseDto.getPincode()) ? lendingApplication.getPincode().toString() : cKycResponseDto.getPincode())
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

    public Boolean updateClient(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UPDATE_CLIENT_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));
            }
            NBFCRequestDTO<?> updateClientRequest = getUpdateClientPayload(lenderAssociationDetailsDto);
            if (Objects.isNull(updateClientRequest)) {
                log.info("error in update client payload of TrillionLoans for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UPDATE_CLIENT_FAILED.name());
                return true;
            }

            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(updateClientRequest, LenderAssociationStages.UPDATE_CLIENT, trillionLoansConfig.getCreateClientTimeoutThreshold());

            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UPDATE_CLIENT_SUCCESS.name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return true;
            }
        } catch (Exception e) {
            log.error("Exception occurred while processing update client of TrillionLoans for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.UPDATE_CLIENT_FAILED.name());
        commonService.manageApplicationState(lenderAssociationDetailsDto);
        return true;
    }

    public NBFCRequestDTO<?> getUpdateClientPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication application = lenderAssociationDetailsRequest.getLendingApplication();
        CKycResponseDto cKycResponseDto = lenderAssociationDetailsRequest.getCKycResponseDto();
        String clientId = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getCccId();
        try {
            NBFCRequestDTO<?> updateClientRequest = NBFCRequestDTO.builder()
                    .applicationId(application.getId())
                    .payload(TLCreateClientRequestDto.builder()
                            .addressDetails(getAddressDetails(application, cKycResponseDto))
                            .clientId(clientId)
                            .build())
                    .lender(Lender.TRILLIONLOANS.name())
                    .productName("LENDING")
                    .topup(LoanType.TOPUP.name().equalsIgnoreCase(application.getLoanType()))
                    .build();
            return updateClientRequest;
        } catch (Exception e) {
            log.error("Exception in creating updateClient payload of trillionLoans for applicationId {} {}", application.getId(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private void updateLastNameAndMiddleName(NameAndDobDetailsDto nameAndDobDetailsDto) {
        String lastName = nameAndDobDetailsDto.getLastName();
        String middleName = nameAndDobDetailsDto.getMiddleName();
        int lastSpace = lastName.trim().lastIndexOf(" ");
        if (lastSpace == -1) {
            return;
        }
        nameAndDobDetailsDto.setLastName(lastName.substring(lastSpace + 1));
        nameAndDobDetailsDto.setMiddleName(middleName + " " + lastName.substring(0, lastSpace));
    }

}