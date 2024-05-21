package com.bharatpe.lending.loanV3.services.associationsV2.muthoot.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.dto.BharatPeEnachResponseDTO;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.dto.NachStatusResponseDTO;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.service.merchant.constants.Constants;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.muthoot.MFCreateLeadRequestDTO;
import com.bharatpe.lending.loanV3.dto.request.muthoot.MFUpdateLeadRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFCreateLeadResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFUpdateLeadResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.muthoot.validations.LeadPayloadValidation;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.*;

@Slf4j
@Service
public class MFLeadService {
    @Autowired
    KycUtils kycUtils;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LeadPayloadValidation leadPayloadValidation;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    EnachHandler enachHandler;

    @Autowired
    MerchantService merchantService;

    @Autowired
    LendingShopDocumentsDao lendingShopDocumentsDao;

    @Autowired
    DsHandler dsHandler;

    @Autowired
    private Validator validator;

    @Lazy
    @Autowired
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Transactional
    public boolean invokeCreateLead(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("Application Id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.PiramalAssociationStages.LEAD_CREATION.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
            String customerID = UUID.randomUUID().toString();
            NBFCRequestDTO createLeadRequest = getCreateLeadPayload(lenderAssociationDetailsDto, customerID);
            if (Objects.isNull(createLeadRequest)) {
                log.info("error in create lead payload of Muthoot for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
                return false;
            }
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(createLeadRequest, LenderAssociationStages.CREATE_LEAD);
            log.info("create lead response of Muthoot from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                log.info("createLead request of Muthoot success for {}", lenderAssociationDetailsDto.getApplicationId());
                MFCreateLeadResponseDTO createLeadResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), MFCreateLeadResponseDTO.class);
                if ("CUR-S-000".equalsIgnoreCase(createLeadResponseDTO.getStatusCode())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadId(customerID);
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_SUCCESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("exception occurred while processing create lead of Muthoot for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
        return false;
    }

    private NBFCRequestDTO getCreateLeadPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, String customerID) {
        CKycResponseDto cKycResponseDto = lenderAssociationDetailsDto.getCKycResponseDto();
        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
        try {
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.MUTHOOT.name())
                    .payload(MFCreateLeadRequestDTO.builder()
                            .customerID(customerID)
                            .mobile(kycUtils.getMobileFromKycData(cKycResponseDto))
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("exception occurred while creating request payload for createLead for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    @Transactional
    public Boolean invokeUpdateLead(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        try {
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.UPDATE_LEAD.name());
            lenderAssociationDetailsRequest.setLendingApplicationLenderDetails(setLeadStatus(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails(), LenderAssociationStatus.UPDATE_LEAD_PENDING.name()));
            commonService.manageApplicationState(lenderAssociationDetailsRequest);

            if (ObjectUtils.isEmpty(lenderAssociationDetailsRequest.getCKycResponseDto())) {
                lenderAssociationDetailsRequest.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsRequest.getMerchantId()));
            }
            NBFCRequestDTO updateLeadRequestDto = getPayload(lenderAssociationDetailsRequest);
            if (Objects.isNull(updateLeadRequestDto) || leadPayloadValidation.isInValidPayload(lenderAssociationDetailsRequest.getCKycResponseDto())) {
                log.info("error in update lead payload of Muthoot for applicationId: {}", lenderAssociationDetailsRequest.getApplicationId());
                lenderAssociationDetailsRequest.setLendingApplicationLenderDetails(setLeadStatus(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails(), LenderAssociationStatus.UPDATE_LEAD_FAILED.name()));
                if ("KYC".equalsIgnoreCase(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getStage())) {
                    commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.UPDATE_LEAD_FAILED);
                } else {
                    commonService.manageApplicationState(lenderAssociationDetailsRequest);
                }
                return false;
            }
            NBFCResponseDTO nbfcResponseDTO = lenderAPIGateway.invokeStage(updateLeadRequestDto, LenderAssociationStages.UPDATE_LEAD);
            log.info("update lead response of Muthoot from nbfc: {} with applicationId: {}", nbfcResponseDTO, lenderAssociationDetailsRequest.getApplicationId());
            if (Objects.nonNull(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && Objects.nonNull(nbfcResponseDTO.getData())) {
                MFUpdateLeadResponseDTO updateLeadResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), MFUpdateLeadResponseDTO.class);
                if ("UUD-S-000".equalsIgnoreCase(updateLeadResponseDTO.getStatusCode()) || "FBX-S-208".equalsIgnoreCase(updateLeadResponseDTO.getStatusCode())) {
                    lenderAssociationDetailsRequest.setLendingApplicationLenderDetails(setLeadStatus(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails(), LenderAssociationStatus.UPDATE_LEAD_COMPLETED.name()));
                    commonService.manageApplicationState(lenderAssociationDetailsRequest);
                    return true;
                }
                log.info("different status code received: {} from muthoot in update lead for application id : {}", updateLeadResponseDTO.getStatusCode(), lenderAssociationDetailsRequest.getApplicationId());
            }
        } catch (Exception e) {
            log.error("error while pushing update lead of Muthoot for  {} {} {}", lenderAssociationDetailsRequest.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequest.setLendingApplicationLenderDetails(setLeadStatus(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails(), LenderAssociationStatus.UPDATE_LEAD_FAILED.name()));
        if ("KYC".equalsIgnoreCase(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getStage())) {
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.UPDATE_LEAD_FAILED);
        } else {
            commonService.manageApplicationState(lenderAssociationDetailsRequest);
        }
        return Boolean.FALSE;
    }

    private LendingApplicationLenderDetails setLeadStatus(LendingApplicationLenderDetails lendingApplicationLenderDetails, String status) {
        if ("KYC".equalsIgnoreCase(lendingApplicationLenderDetails.getStage())) {
            lendingApplicationLenderDetails.setKycStatus(status);
        } else {
            lendingApplicationLenderDetails.setSanctionStatus(status);
        }
        return lendingApplicationLenderDetails;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        CKycResponseDto cKycResponseDto = lenderAssociationDetailsRequest.getCKycResponseDto();
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        try {
            MFUpdateLeadRequestDTO payload = null;
            if ("KYC".equalsIgnoreCase(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getStage())) {
                payload = MFUpdateLeadRequestDTO.builder()
                        .customerID(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLeadId())
                        .program("EDI")
                        .basicDetails(MFUpdateLeadRequestDTO.BasicDetail.builder()
                                .personalDetails(getPersonalDetails(cKycResponseDto))
                                .build())
                        .consents(getConsents(lendingApplication))
                        .build();
            } else {
                payload = MFUpdateLeadRequestDTO.builder()
                        .customerID(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getLeadId())
                        .program("EDI")
                        .basicDetails(MFUpdateLeadRequestDTO.BasicDetail.builder()
                                .businessDetails(getBusinessAddress(lendingApplication))
                                .build())
                        .mandateDetails(getMandateDetails(lendingApplication))
                        .build();
            }

            Set<ConstraintViolation<MFUpdateLeadRequestDTO>> violations = validator.validate(payload);
            if (!violations.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (ConstraintViolation<MFUpdateLeadRequestDTO> violation : violations) {
                    sb.append(violation.getMessage()).append("\n");
                }
                throw new IllegalArgumentException("update lead validation failed: " + sb.toString());
            }
            return NBFCRequestDTO.builder().applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(payload)
                    .build();
        } catch (Exception e) {
            log.error("exception occurred while creating request payload for createLead for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private MFUpdateLeadRequestDTO.PersonalDetail getPersonalDetails(CKycResponseDto cKycResponseDto) {
        return MFUpdateLeadRequestDTO.PersonalDetail.builder()
                .pan(cKycResponseDto.getPanNumber())
                .name(cKycResponseDto.getName()).build();
    }


    private MFUpdateLeadRequestDTO.BusinessDetail getBusinessAddress(LendingApplication lendingApplication) {
        String address = lendingApplicationServiceV2.constructShopAddress(lendingApplication);
        int addressSize = address.length();
        String address1 = "", address2 = "";
        if (addressSize <= 40) {
            address1 = address;
        } else {
            address1 = address.substring(0, 40);
            address2 = address.substring(40, addressSize);
        }
        MFUpdateLeadRequestDTO.BusinessDetail businessAddress = MFUpdateLeadRequestDTO.BusinessDetail.builder()
                .address(MFUpdateLeadRequestDTO.Address.builder()
                        .businessAddressType("OWNED")
                        .line1(address1)
                        .line2(address2)
                        .city(lendingApplication.getCity())
                        .state(lendingApplication.getState())
                        .landmark(Optional.ofNullable(lendingApplication.getLandmark()).orElse("NONE"))
                        .pincode(lendingApplication.getPincode().toString())
                        .location(getAddressLocation(lendingApplication.getMerchantId(), lendingApplication.getId()))
                        .build())
                .build();
        return businessAddress;
    }

    private List<MFUpdateLeadRequestDTO.Consent> getConsents(LendingApplication lendingApplication) {
        List<MFUpdateLeadRequestDTO.Consent> consents = new ArrayList<>();
        consents.add(MFUpdateLeadRequestDTO.Consent.builder()
                .type("CKYC")
                .timestamp(String.valueOf(lendingApplication.getCreatedAt().getTime()))
                .ipAddress(lendingApplication.getIp())
                .url("")
                .body("I agree to sharing my KYC information with Bharatpe’s lending and other financial services partners")
                .build());
        consents.add(MFUpdateLeadRequestDTO.Consent.builder()
                .type("TNC")
                .ipAddress(lendingApplication.getIp())
                .timestamp(String.valueOf(lendingApplication.getCreatedAt().getTime()))
                .url("")
                .body("By clicking on I Agree, I accept the Key Facts Statement, Sanction and loan agreement, Privacy Policy and Terms & Conditions of LSP")
                .build());
        consents.add(MFUpdateLeadRequestDTO.Consent.builder()
                .type("BUREAU")
                .ipAddress(lendingApplication.getIp())
                .timestamp(String.valueOf(lendingApplication.getCreatedAt().getTime()))
                .url("")
                .body("I agree to avail the loan facilitation services offered by Resilient Digi Services Private Limited (RDSPL) through RBI registered NBFC/Banks and further authorize and appoint RDSPL as my authorized agent to receive my credit information from credit information companies such as Experian, CRIF High Mark etc. subject to the terms and conditions. I hereby also agree to the lending partner of RDSPL to obtain my credit information from the credit bureaus")
                .build());
        consents.add(MFUpdateLeadRequestDTO.Consent.builder()
                .type("PRIVACY_POLICY")
                .ipAddress(lendingApplication.getIp())
                .timestamp(String.valueOf(lendingApplication.getCreatedAt().getTime()))
                .url("")
                .body("Please read our Privacy policy and Terms & Conditions")
                .build());
        return consents;
    }

    private MFUpdateLeadRequestDTO.MandateDetails getMandateDetails(LendingApplication lendingApplication) {
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
        MFUpdateLeadRequestDTO.MandateDetails mandateDetails = null;
        if (lendingApplicationDetails.getIsNachSkip()) {
            MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = enachHandler.findByMerchantIdAndLender(lendingApplication.getMerchantId(), LendingEnum.LENDER.MUTHOOT.name());
            if (!ObjectUtils.isEmpty(merchantNachDetailsResponseDTO)) {
                mandateDetails = MFUpdateLeadRequestDTO.MandateDetails.builder()
                        .accountNumber(merchantNachDetailsResponseDTO.getAccountNumber())
                        .accountHolderName(merchantNachDetailsResponseDTO.getBeneficiaryName())
                        .accountType(ObjectUtils.isEmpty(merchantNachDetailsResponseDTO.getAccountType()) ? "savings" : merchantNachDetailsResponseDTO.getAccountType().toLowerCase())
                        .ifsc(merchantNachDetailsResponseDTO.getIfscCode())
                        .bankName(merchantNachDetailsResponseDTO.getBankName())
                        .mandateAmount(merchantNachDetailsResponseDTO.getNachAmount())
                        .mandateType("E_MANDATE")
                        .vendor(merchantNachDetailsResponseDTO.getProvider())
                        .vendorDocID(merchantNachDetailsResponseDTO.getProviderUmrn())
                        .npciTxnID(merchantNachDetailsResponseDTO.getNpciTxnId())
                        .build();
            }
        } else {
            BharatPeEnachResponseDTO bharatPeEnachResponseDTO = enachHandler.findByMerchantIdAndApplicationId(lendingApplication.getMerchantId(), lendingApplication.getId());
            final MerchantDetailsDto merchantDetailsDto = merchantService.fetchMerchantDetails(lendingApplication.getMerchantId(), Arrays.asList(
                    Constants.MerchantUtil.Scope.BANK_DETAIL,
                    Constants.MerchantUtil.Scope.MERCHANT_USER
            ));
            if (!ObjectUtils.isEmpty(bharatPeEnachResponseDTO) && !ObjectUtils.isEmpty(merchantDetailsDto)) {
                mandateDetails = MFUpdateLeadRequestDTO.MandateDetails.builder()
                        .accountNumber(merchantDetailsDto.getBankDetail().getAccountNumber())
                        .accountHolderName(merchantDetailsDto.getBankDetail().getBeneficiaryName())
                        .accountType(ObjectUtils.isEmpty(merchantDetailsDto.getBankDetail().getAccountType()) ? "savings" : merchantDetailsDto.getBankDetail().getAccountType().toLowerCase())
                        .ifsc(merchantDetailsDto.getBankDetail().getIfsc())
                        .bankName(merchantDetailsDto.getBankDetail().getBankName())
                        .mandateAmount(bharatPeEnachResponseDTO.getAmount())
                        .mandateType("E_MANDATE")
                        .vendor(bharatPeEnachResponseDTO.getEnachProvider())
                        .vendorDocID(bharatPeEnachResponseDTO.getProviderUmrn())
                        .npciTxnID(bharatPeEnachResponseDTO.getNpciTxnId())
                        .build();
            }
        }
        if (ObjectUtils.isEmpty(mandateDetails)) {
            throw new RuntimeException("mandateDetails not found for merchantId or applicationId");
        }
        return mandateDetails;
    }

    private MFUpdateLeadRequestDTO.Location getAddressLocation(Long merchantId, Long applicationId) {
        try {
            List<LendingShopDocuments> lendingShopDocumentsList = lendingShopDocumentsDao.findByMerchantIdAndLendingApplicationId(merchantId, applicationId);
            for (LendingShopDocuments lendingShopDocuments : lendingShopDocumentsList) {
                if (!ObjectUtils.isEmpty(lendingShopDocuments) && !ObjectUtils.isEmpty(lendingShopDocuments.getLatitude()) && !ObjectUtils.isEmpty(lendingShopDocuments.getLongitude())) {
                    return MFUpdateLeadRequestDTO.Location.builder().latitude(lendingShopDocuments.getLatitude()).longitude(lendingShopDocuments.getLongitude()).build();
                }
            }
            Map<String, Double> dsResponse = dsHandler.fetchDsLocation(merchantId);
            if (!ObjectUtils.isEmpty(dsResponse) || dsResponse.containsKey("latitude") || !ObjectUtils.isEmpty(dsResponse.get("latitude")) || dsResponse.containsKey("longitude") || !ObjectUtils.isEmpty(dsResponse.get("longitude"))) {
                return MFUpdateLeadRequestDTO.Location.builder().latitude(String.valueOf(dsResponse.get("latitude"))).longitude(String.valueOf(dsResponse.get("longitude"))).build();
            }

        } catch (Exception e) {
            log.error("error while getting latitude and longitude for application Id {} and merchantId {}, {}, {}", applicationId, merchantId, e, Arrays.asList(e.getStackTrace()));
        }

        log.info("couldn't get lat long for applicationId {} and merchantId {} from shop picture and fetch DS location", applicationId, merchantId);
        return MFUpdateLeadRequestDTO.Location.builder().latitude("0.0").longitude("0.0").build();
    }

}