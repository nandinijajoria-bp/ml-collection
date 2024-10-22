package com.bharatpe.lending.loanV3.services.associationsV2.creditsaison.impl;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.config.CreditSaisonConfig;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.creditsasion.CreditSasionKYCRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.creditsasion.CreditSaisonCallbackResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.creditsasion.CreditSasionCallbackResponseStatuses;
import com.bharatpe.lending.loanV3.dto.response.creditsasion.CreditSasionKYCResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Service
public class CreditSaisonKYCService {

    @Autowired
    KycUtils kycUtils;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ConverterUtils converterUtils;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Lazy
    @Autowired
    CreditSaisonConfig csConfig;

    @Transactional
    public boolean invokeKyc(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("CS: Application Id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.KYC.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();

            lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));

            NBFCRequestDTO kycRequestPayload = getKycRequestPayload(lenderAssociationDetailsDto);

            if (Objects.isNull(kycRequestPayload)) {
                log.info("CS: error in KYC payload of CreditSaison for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.KYC_FAILED);
                return false;
            }
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(kycRequestPayload, LenderAssociationStages.KYC);

            log.info("CS: KYC response of CreditSaison from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                log.info("CS: KYC request of CreditSaison success for {}", lenderAssociationDetailsDto.getApplicationId());
                CreditSasionKYCResponseDTO kycResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), CreditSasionKYCResponseDTO.class);
                if (csConfig.getSyncSuccessStatus().equalsIgnoreCase(kycResponseDTO.getStatus())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_IN_PROGRESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }

            }
        } catch (Exception e) {
            log.error("CS: exception occurred while KYC of CreditSaison for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.KYC_FAILED);
        return false;

    }

    private NBFCRequestDTO getKycRequestPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
        CKycResponseDto cKycResponseDto = lenderAssociationDetailsDto.getCKycResponseDto();
        try {
            String middleName = kycUtils.getMiddleName(cKycResponseDto);
            String lastName = kycUtils.getLastName(cKycResponseDto);

            LinkedHashMap<String, Object> identifiers = new LinkedHashMap<>();
            identifiers.put("adharXML", cKycResponseDto.getPoaString());
            identifiers.put("selfie", cKycResponseDto.getSelfieString());
            identifiers.put("partnerLoanId", lendingApplication.getExternalLoanId());
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName(csConfig.getLendingProduct())
                    .lender(LendingEnum.LENDER.CREDITSAISON.name())
                    .identifier(identifiers)
                    .payload(CreditSasionKYCRequestDTO.builder()
                            .partnerLoanId(lenderAssociationDetailsDto.getLendingApplication().getExternalLoanId())
                            .kycType(csConfig.getKycType())
                            .partnerAppId(lenderAssociationDetailsDto.getLendingApplication().getExternalLoanId())
                            .partnerId(csConfig.getPartnerId())
                            .partnerAppId(lenderAssociationDetailsDto.getLendingApplication().getExternalLoanId())
                            .partnerSanctionTime(new SimpleDateFormat(csConfig.getLenderTimeFormat()).format(new Date()))
                            .source(csConfig.getSource())
                            .loanType(csConfig.getLoanType())
                            .loan(CreditSasionKYCRequestDTO.Loan.builder()
                                    .loanProduct(csConfig.getLoanProduct())
                                    .build())
                            .linkedIndividuals(Arrays.asList(CreditSasionKYCRequestDTO.LinkedIndividual.builder()
                                        .aadhaarXmlType(csConfig.getAdharXMLType())
                                        .applicantType(csConfig.getApplicantType())
                                        .individual(CreditSasionKYCRequestDTO.LinkedIndividual.Individual.builder()
                                                .firstName(kycUtils.getFirstName(cKycResponseDto))
                                                .middleName(!ObjectUtils.isEmpty(middleName) ? middleName : null)
                                                .lastName(!ObjectUtils.isEmpty(lastName) ? lastName : null)
                                                .salutation(csConfig.getSalutation())
                                                .gender(csConfig.getGender(kycUtils.getGender(cKycResponseDto.getGender()))) // 23 : Male, 24 : Female)
                                                .fullName(cKycResponseDto.getName())
                                                .dob(DateTimeUtil.formatDate(cKycResponseDto.getDob(), "dd/MM/yyyy",  "yyyy-MM-dd"))
                                                .birthCountry(csConfig.getCountry())
                                                .fatherName(csConfig.getFathersName())
                                                .build())
                                            .addresses(getAddress(cKycResponseDto))
                                            .contacts(Arrays.asList(
                                                    CreditSasionKYCRequestDTO.LinkedIndividual.Contact.builder()
                                                            .type(csConfig.getContactTypePhone())
                                                            .value(kycUtils.getMobileFromKycData(cKycResponseDto))
                                                            .typeCode(csConfig.getContactTypeCodeMobile())
                                                            .priority(csConfig.getPriority5())
                                                            .build()
                                            ))
                                            .kyc(Arrays.asList(
                                                    CreditSasionKYCRequestDTO.LinkedIndividual.Kyc.builder()
                                                            .issuedCountry(csConfig.getCountry())
                                                            .kycType(csConfig.getContactKYCTypePan())
                                                            .kycValue(cKycResponseDto.getPanNumber())
                                                            .build()
                                            ))
                                            .docsList(Arrays.asList(
                                                    CreditSasionKYCRequestDTO.LinkedIndividual.Doc.builder()
                                                            .type(csConfig.getContactPhotoType())
                                                            .build(),
                                                    CreditSasionKYCRequestDTO.LinkedIndividual.Doc.builder()
                                                            .type(csConfig.getContactAdharType())
                                                            .build()
                                            ))
                                        .build()))
                            .customerConsents(getCustomerConsent(lendingApplication, kycUtils.getMobileFromKycData(cKycResponseDto)))
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("CS: exception occurred while KYC request payload for createLead for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    public List<CreditSasionKYCRequestDTO.CustomerConsent> getCustomerConsent(LendingApplication lendingApplication, String mobile) { //TODO once check can we share all consents including whatsapp
        String currDate = new SimpleDateFormat(csConfig.getLenderTimeFormat()).format(new Date());
        return Arrays.asList(
                CreditSasionKYCRequestDTO.CustomerConsent.builder()
                        .consentFor(csConfig.getConsentForNSDL())
                        .consentChannel(csConfig.getConsentChannel())
                        .consentTime(currDate)
                        .consentIdentifier(CreditSasionKYCRequestDTO.CustomerConsent.ConsentIdentifier.builder()
                                .ip(lendingApplication.getIp())
                                .rmn(mobile)
                                .build())
                        .consentMode(csConfig.getConsentMode())
                        .build(),
                CreditSasionKYCRequestDTO.CustomerConsent.builder()
                        .consentFor(csConfig.getConsentForHHI())
                        .consentChannel(csConfig.getConsentChannel())
                        .consentTime(currDate)
                        .consentIdentifier(CreditSasionKYCRequestDTO.CustomerConsent.ConsentIdentifier.builder()
                                .ip(lendingApplication.getIp())
                                .rmn(mobile)
                                .build())
                        .consentMode(csConfig.getConsentMode())
                        .build(),
                CreditSasionKYCRequestDTO.CustomerConsent.builder()
                        .consentFor(csConfig.getConsentForSelfie())
                        .consentChannel(csConfig.getConsentChannel())
                        .consentTime(currDate)
                        .consentIdentifier(CreditSasionKYCRequestDTO.CustomerConsent.ConsentIdentifier.builder()
                                .ip(lendingApplication.getIp())
                                .rmn(mobile)
                                .build())
                        .consentMode(csConfig.getConsentMode())
                        .build(),
                CreditSasionKYCRequestDTO.CustomerConsent.builder()
                        .consentFor(csConfig.getConsentForAdhar())
                        .consentChannel(csConfig.getConsentChannel())
                        .consentTime(currDate)
                        .consentIdentifier(CreditSasionKYCRequestDTO.CustomerConsent.ConsentIdentifier.builder()
                                .ip(lendingApplication.getIp())
                                .rmn(mobile)
                                .build())
                        .consentMode(csConfig.getConsentMode())
                        .build(),
                CreditSasionKYCRequestDTO.CustomerConsent.builder()
                        .consentFor(csConfig.getConsentForDataSharing())
                        .consentChannel(csConfig.getConsentChannel())
                        .consentTime(currDate)
                        .consentIdentifier(CreditSasionKYCRequestDTO.CustomerConsent.ConsentIdentifier.builder()
                                .ip(lendingApplication.getIp())
                                .rmn(mobile)
                                .build())
                        .consentMode(csConfig.getConsentMode())
                        .build(),
                CreditSasionKYCRequestDTO.CustomerConsent.builder()
                        .consentFor(csConfig.getConsentForWhatsapp())
                        .consentChannel(csConfig.getConsentChannel())
                        .consentTime(currDate)
                        .consentIdentifier(CreditSasionKYCRequestDTO.CustomerConsent.ConsentIdentifier.builder()
                                .ip(lendingApplication.getIp())
                                .rmn(mobile)
                                .build())
                        .consentMode(csConfig.getConsentMode())
                        .build(),
                CreditSasionKYCRequestDTO.CustomerConsent.builder()
                        .consentFor(csConfig.getConsentForKFS())
                        .consentChannel(csConfig.getConsentChannel())
                        .consentTime(currDate)
                        .consentIdentifier(CreditSasionKYCRequestDTO.CustomerConsent.ConsentIdentifier.builder()
                                .ip(lendingApplication.getIp())
                                .rmn(mobile)
                                .build())
                        .consentMode(csConfig.getConsentMode())
                        .build()
        );
    }

    public List<CreditSasionKYCRequestDTO.LinkedIndividual.Address> getAddress(CKycResponseDto cKycResponseDto) {
        String address = converterUtils.parseData(cKycResponseDto.getAddress());
        int addressSize = address.length();
        String address1 = "", address2 = null;
        if (addressSize <= 255) {
            address1 = address;
        } else {
            address1 = address.substring(0, 255);
            address2 = address.substring(255, 509);
        }
        CreditSasionKYCRequestDTO.LinkedIndividual.Address currentAddress = CreditSasionKYCRequestDTO.LinkedIndividual.Address.builder()
                .type(csConfig.getCurrentAddressType())
                .line1(address1)
                .line2(address2)
                .city(cKycResponseDto.getCity())
                .state(csConfig.getState(cKycResponseDto.getState()))
                .country(csConfig.getCountry())
                .pinCode(cKycResponseDto.getPincode())
                .priority(csConfig.getPriority5())
                .build();

        CreditSasionKYCRequestDTO.LinkedIndividual.Address permAddress = CreditSasionKYCRequestDTO.LinkedIndividual.Address.builder()
                .type(csConfig.getPermanentAddressType())
                .line1(address1)
                .line2(address2)
                .city(cKycResponseDto.getCity())
                .state(csConfig.getState(cKycResponseDto.getState()))
                .country(csConfig.getCountry())
                .pinCode(cKycResponseDto.getPincode())
                .priority(csConfig.getPriority4())
                .build();

        return Arrays.asList(currentAddress,permAddress);
    }

    public Boolean processCreditSasionKycCallback(NBFCResponseDTO nbfcResponseDTO) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponseDTO.getApplicationId())).orElse(null);
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("CS: No application found for applicationId : {}", nbfcResponseDTO.getApplicationId());
                return false;
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || !nbfcResponseDTO.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) {
                log.info("CS: No LendingApplicationLenderDetails found with lender {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                    .applicationId(lendingApplication.getId())
                    .lendingApplication(lendingApplication)
                    .lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                    .manageState(true)
                    .modifyLender(true)
                    .build();
            if (Objects.nonNull(nbfcResponseDTO.getData()) && nbfcResponseDTO.getSuccess()) {
                CreditSaisonCallbackResponseDTO kycCallbackResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), CreditSaisonCallbackResponseDTO.class);
                log.info("CS: KYC callback Response of CreditSaison for {} {}", nbfcResponseDTO.getApplicationId(), kycCallbackResponseDTO);
                if (!isApplicationStateValidForCallback(lendingApplicationLenderDetails)) {
                    log.info("CS: Application not in correct state for {} callback for applicationId {}", lendingApplicationLenderDetails.getLeadStatus(), lendingApplication.getId());
                    return false;
                }
                if (!ObjectUtils.isEmpty(kycCallbackResponseDTO)) {
                    if (CreditSasionCallbackResponseStatuses.KYC.getStatusCode().equalsIgnoreCase(kycCallbackResponseDTO.getStatus())) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_COMPLETED.name());
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setLeadId(lendingApplication.getExternalLoanId());
                        commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                        return true;
                    }
                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.KYC_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.KYC_FAILED);
        } catch (Exception e) {
            log.error("CS: exception while processing KYC callback of creditsaison for  {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private boolean isApplicationStateValidForCallback(LendingApplicationLenderDetails lendingApplicationLenderDetails) {
            return (LenderAssociationStages.KYC.name().equalsIgnoreCase(lendingApplicationLenderDetails.getLeadStatus())
                    && LenderAssociationStatus.KYC_IN_PROGRESS.name().equalsIgnoreCase(lendingApplicationLenderDetails.getKycStatus()));
    }
}
