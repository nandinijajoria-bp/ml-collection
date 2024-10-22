package com.bharatpe.lending.loanV3.services.associationsV2.creditsaison.impl;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.entity.LmsFieldValues;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.config.CreditSaisonConfig;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.creditsasion.CreditSasionBRERequestDTO;
import com.bharatpe.lending.loanV3.dto.response.creditsasion.CreditSaisonCallbackResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.creditsasion.CreditSasionCallbackResponseStatuses;
import com.bharatpe.lending.loanV3.dto.response.creditsasion.CreditSasionKYCResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.bharatpe.lending.constant.LendingConstants.BUSINESS_CATEGORY_LMS_FIELD_ID;
import static com.bharatpe.lending.constant.LendingConstants.BUSINESS_SUBCATEGORY_LMS_FIELD_ID;

@Slf4j
@Service
public class CreditSaisonBREService {

    @Autowired
    KycUtils kycUtils;

    @Lazy
    @Autowired
    NbfcUtils nbfcUtils;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    CreditSaisonConfig csConfig;
    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;
    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LmsFieldValuesDao lmsFieldValuesDao;

    @Autowired
    LendingRiskVariablesDao lendingRiskVariablesDao;

    @Transactional
    public boolean invokeBre(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("CS: Application Id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));

            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.BRE.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.BRE_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
            NBFCRequestDTO breRequestPayload = getBreRequestPayload(lenderAssociationDetailsDto);
            if (Objects.isNull(breRequestPayload)) {
                log.info("CS: error in BRE payload of CreditSaison for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.BRE_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.BRE_FAILED);
                return false;
            }
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(breRequestPayload, LenderAssociationStages.BRE);
            log.info("CS: BRE response of CreditSaison from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                log.info("CS: BRE request of CreditSaison success for {}", lenderAssociationDetailsDto.getApplicationId());
                CreditSasionKYCResponseDTO kycResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), CreditSasionKYCResponseDTO.class);
                if (csConfig.getSyncSuccessStatus().equalsIgnoreCase(kycResponseDTO.getStatus())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.BRE_IN_PROGRESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }

            }
        } catch (Exception e) {
            log.error("CS: exception occurred while BRE of CreditSaison for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.BRE_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.BRE_FAILED);
        return false;

    }

    private NBFCRequestDTO getBreRequestPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
        CKycResponseDto cKycResponseDto = lenderAssociationDetailsDto.getCKycResponseDto();
        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingApplication.getMerchantId());

        String meta_data = getDrog(lendingRiskVariables.getMetaData());

        String middleName = kycUtils.getMiddleName(cKycResponseDto);
        String lastName = kycUtils.getLastName(cKycResponseDto);
        String businessCategory = getBusinessCategory(lendingRiskVariablesSnapshot.getLoanSegment(), lendingApplication);

        LinkedHashMap<String, Object> identifiers = new LinkedHashMap<>();
        identifiers.put("partnerLoanId", lendingApplication.getExternalLoanId());
        try {
            Double sixtyDaysTpv = ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getSummaryTpv()) ? 0D : lendingRiskVariablesSnapshot.getSummaryTpv() * 60;

            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName(csConfig.getLendingProduct())
                    .lender(LendingEnum.LENDER.CREDITSAISON.name())
                    .identifier(identifiers)
                    .payload(CreditSasionBRERequestDTO.builder()
                            .partnerLoanId(lenderAssociationDetailsDto.getLendingApplication().getExternalLoanId())
                            .partnerId(csConfig.getPartnerId())
                            .customerConsents(
                                    getCustomerConsent(lendingApplication)
                            )
                            .loan(CreditSasionBRERequestDTO.Loan.builder()
                                    .amount(lendingApplication.getLoanAmount())
                                    .loanIntRate(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getAnnualRoi())
                                    .tenure(Math.toIntExact(lendingApplication.getPayableDays()))
                                    .monthlyRoi(lendingApplication.getInterestRate())
                                    .loanProduct(csConfig.getLoanProduct())
                                    .build())
                            .linkedIndividuals(
                                    Arrays.asList(
                                            CreditSasionBRERequestDTO.LinkedIndividual.builder()
                                                    .applicantType(csConfig.getApplicantType())
                                                    .contacts(
                                                            Arrays.asList(
                                                            CreditSasionBRERequestDTO.LinkedIndividual.Contact.builder()
                                                                    .countryCode(csConfig.getCountryCode())
                                                                    .notify(csConfig.getNotifyTrue())
                                                                    .value(kycUtils.getMobileFromKycData(cKycResponseDto))
                                                                    .type(csConfig.getContactTypePhone())
                                                                    .typeCode(csConfig.getContactTypeCodeMobile().toUpperCase())
                                                                    .priority(String.valueOf(csConfig.getPriority5()))
                                                                    .build())
                                                    )
                                                    .kyc(Arrays.asList(CreditSasionBRERequestDTO.LinkedIndividual.Kyc.builder()
                                                                    .issuedCountry(csConfig.getCountry())
                                                                    .kycType(csConfig.getContactKYCTypePan())
                                                                    .kycValue(cKycResponseDto.getPanNumber())
                                                            .build()))
                                                    .misc(CreditSasionBRERequestDTO.LinkedIndividual.Misc.builder()
                                                            .loanSegment(lendingRiskVariablesSnapshot.getLoanSegment())
                                                            .riskGroup(lendingRiskVariablesSnapshot.getRiskGroup())
                                                            .tpv60(sixtyDaysTpv)
                                                            .monthlyNfi(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getMonthlyNfi()) ? 0D : lendingRiskVariablesSnapshot.getMonthlyNfi())
                                                            .pincodeColour(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getPincodeColor()) ? "" : lendingRiskVariablesSnapshot.getPincodeColor().name())
                                                            .merchantVintageDays(Math.toIntExact(lendingRiskVariablesSnapshot.getVintage()))
                                                            .applicantProfile(csConfig.getBusinessApplicantProfile())
                                                            .applicationId(lenderAssociationDetailsDto.getLendingApplication().getExternalLoanId())
                                                            .businessType(csConfig.getBusinessType())
                                                            .businessAddressOwnership(csConfig.getBusinessAddressOwnerShip())
                                                            .businessCity(lenderAssociationDetailsDto.getLendingApplication().getCity())
                                                            .customerId(lendingRiskVariablesSnapshot.getAggregateId())
                                                            .businessAddressType(csConfig.getBusinessAddressType())
                                                            .businessAddressCountry(csConfig.getBusinessAddressCountry())
                                                            .businessAddressStreet1(lenderAssociationDetailsDto.getLendingApplication().getStreetAddress())
                                                            .businessAddressStreet2(lenderAssociationDetailsDto.getLendingApplication().getArea())
                                                            .businessAddressPostalCode(Math.toIntExact(lenderAssociationDetailsDto.getLendingApplication().getPincode()))
                                                            .businessAddressBuilding(lenderAssociationDetailsDto.getLendingApplication().getShopNumber())
                                                            .businessAddressState(lenderAssociationDetailsDto.getLendingApplication().getState())
                                                            .businessIndustry(businessCategory)
                                                            .businessMonthlyIncome(csConfig.getBusinessMonthlyIncome())
                                                            .businessName(lenderAssociationDetailsDto.getLendingApplication().getBusinessName())
                                                            .gst(Boolean.FALSE)
                                                            .derogCategory(meta_data)
                                                            .build())
                                                    .individual(CreditSasionBRERequestDTO.LinkedIndividual.Individual.builder()
                                                            .firstName(kycUtils.getFirstName(cKycResponseDto))
                                                            .middleName(!ObjectUtils.isEmpty(middleName) ? middleName : null)
                                                            .lastName(!ObjectUtils.isEmpty(lastName) ? lastName : null)
                                                            .dob(DateTimeUtil.formatDate(cKycResponseDto.getDob(), "dd/MM/yyyy",  "yyyy-MM-dd"))
                                                            .build())
                                                    .employmentStatus(csConfig.getEmployeeStatus()).build()
                                    )
                            ).build()
                    ).build();
        } catch (Exception e) {
            log.error("CS: exception occurred while BRE request payload for creditsaison for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    public List<CreditSasionBRERequestDTO.CustomerConsent> getCustomerConsent(LendingApplication lendingApplication) {
        String currDate = new SimpleDateFormat(csConfig.getLenderTimeFormat()).format(new Date());
        return Arrays.asList(
                CreditSasionBRERequestDTO.CustomerConsent.builder()
                        .consentFor(csConfig.getConsentForBureau())
                        .consentChannel(csConfig.getConsentChannel())
                        .consentTime(currDate)
                        .consentIdentifier(CreditSasionBRERequestDTO.CustomerConsent.ConsentIdentifier.builder()
                                .ip(lendingApplication.getIp())
                                .build())
                        .consentMode(csConfig.getConsentMode())
                        .build()
        );
    }

    public List<CreditSasionBRERequestDTO.LinkedIndividual.Address> getAddress(CKycResponseDto cKycResponseDto) {

        CreditSasionBRERequestDTO.LinkedIndividual.Address currentAddress = CreditSasionBRERequestDTO.LinkedIndividual.Address.builder()
                .type(csConfig.getCurrentAddressType())
                .city(cKycResponseDto.getCity())
                .state(csConfig.getState(cKycResponseDto.getState()))
                .pinCode(cKycResponseDto.getPincode())
                .build();

        return Arrays.asList(currentAddress);
    }

    public Boolean processCreditSasionBreCallback(NBFCResponseDTO nbfcResponseDTO) {
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
                    .manageState(true)
                    .modifyLender(true)
                    .lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                    .build();
            log.info("CS: processCreditSasionBreCallback getLendingApplicationLenderDetails{}", lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getId());
            log.info("CS: processCreditSasionBreCallback getLendingApplication {}", lenderAssociationDetailsRequest.getLendingApplication().getId());
            if (Objects.nonNull(nbfcResponseDTO.getData()) && nbfcResponseDTO.getSuccess()) {
                CreditSaisonCallbackResponseDTO breCallbackResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), CreditSaisonCallbackResponseDTO.class);
                log.info("CS: BRE callback Response of CreditSaison for {} {}", nbfcResponseDTO.getApplicationId(), breCallbackResponseDTO);
                if (!isApplicationStateValidForCallback(lendingApplicationLenderDetails)) {
                    log.info("CS: Application not in correct state for {} callback for applicationId {}", lendingApplicationLenderDetails.getLeadStatus(), lendingApplication.getId());
                    return false;
                }
                if (!ObjectUtils.isEmpty(breCallbackResponseDTO)) {
                    if (CreditSasionCallbackResponseStatuses.BRE_APPROVED.getStatusCode().equalsIgnoreCase(breCallbackResponseDTO.getStatus())) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setCccId(breCallbackResponseDTO.getAppFormId());
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.BRE_COMPLETED.name());

                        commonService.manageApplicationState(lenderAssociationDetailsRequest);
                        List<String> stagesToBeInvokedInOrder = getStageToBeInvokedInOrder();
                        Optional<String> failureStage = stagesToBeInvokedInOrder.stream().filter(stage -> !nbfcUtils.invokeSpecificStage(lenderAssociationDetailsRequest.getLendingApplication().getLender(), lenderAssociationDetailsRequest, stage)).findFirst();
                        if (failureStage.isPresent()) {
                            log.info("CS: lender association failed at {} stage for applicationId {}  with lender {}", failureStage.get(), lendingApplication.getId(), lenderAssociationDetailsRequest.getLendingApplication().getLender());
                            MDC.clear();
                            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setPennyDropStatus(LenderAssociationStatus.PENNY_DROP_FAILED.name());
                            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.PENNY_DROP_FAILED);
                            return false;
                        }
                        return true;
                    }
                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.BRE_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.BRE_FAILED);
        } catch (Exception e) {
            log.error("CS: exception while processing BRE callback of CreditSaison for  {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private boolean isApplicationStateValidForCallback(LendingApplicationLenderDetails lendingApplicationLenderDetails) {
            return (LenderAssociationStages.BRE.name().equalsIgnoreCase(lendingApplicationLenderDetails.getLeadStatus())
                    && LenderAssociationStatus.BRE_IN_PROGRESS.name().equalsIgnoreCase(lendingApplicationLenderDetails.getBreStatus()));
    }

    public List<String> getStageToBeInvokedInOrder() {
        return Arrays.asList(LenderAssociationStages.PENNY_DROP.name());
    }

    private String getBusinessCategory(String riskSegment, LendingApplication lendingApplication){

        if(RiskSegment.REPEAT.name().equalsIgnoreCase(riskSegment)){
            LendingApplication lastLmsDisbursedApplication = lendingApplicationDao.getLastLmsDisbursedLoan(lendingApplication.getMerchantId());
            if(ObjectUtils.isEmpty(lastLmsDisbursedApplication)){
                log.info("CS: last lms disbursed application not available for checks on app {}", lendingApplication.getId());
                return csConfig.getDefaultBusinessCategory();
            }
            List<Long> lmsFieldIds = new ArrayList<>();
            lmsFieldIds.add(BUSINESS_CATEGORY_LMS_FIELD_ID);
            lmsFieldIds.add(BUSINESS_SUBCATEGORY_LMS_FIELD_ID);
            List<LmsFieldValues> lmsFieldValuesList = lmsFieldValuesDao.findByLendingApplicationIdAndFieldIdIn(
                    lastLmsDisbursedApplication.getId(), lmsFieldIds
            );
            if(ObjectUtils.isEmpty(lmsFieldValuesList)){
                log.info("CS: business category not available from last disbursed app {}", lendingApplication.getId());
                return csConfig.getDefaultBusinessCategory();
            }

            for(LmsFieldValues lmsFieldValues : lmsFieldValuesList){
                if(lmsFieldValues.getFieldId() == BUSINESS_CATEGORY_LMS_FIELD_ID){
                    return lmsFieldValues.getFieldDropdownValue();
                }
            }
        }
        log.info("CS: business category not available for {}, {}", lendingApplication.getId(), lendingApplication.getLender());
        return csConfig.getDefaultBusinessCategory();
    }

    private String getDrog(Map<String, Object> derogFromDB) {
        if (derogFromDB != null && derogFromDB.containsKey("derog")) {
            Object drogValue = derogFromDB.get("derog");

            if (drogValue != null) {
                String derog = drogValue.toString();

                switch (derog) {
                    case "THIN":
                        return "LOW";
                    default:
                        return derog;
                }
            }
        }
        return "NO";
    }


}
