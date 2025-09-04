package com.bharatpe.lending.loanV3.services.associationsV2.oxyzo.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.config.OxyzoConfig;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.NameAndDobDetailsDto;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.oxyzo.OxyzoCreateLeadRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.oxyzo.OxyzoCommonResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.oxyzo.OxyzoCreateLeadResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.service.AssignmentRuleUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
public class OxyzoLeadService {
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
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    OxyzoConfig oxyzoConfig;

    @Autowired
    AssignmentRuleUtils assignmentRuleUtils;

    @Autowired
    @Lazy
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    LendingShopDocumentsDao lendingShopDocumentsDao;

    @Autowired
    DsHandler dsHandler;

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

            NBFCRequestDTO createLeadRequest = getCreateLeadPayload(lenderAssociationDetailsDto);

            log.info("oxyzo: create lead nbfc request dto :{} for applicationId:{}",createLeadRequest, lendingApplication.getId());

            if (Objects.isNull(createLeadRequest)) {
                log.info("error in create lead payload of oxyzo for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
                return false;
            }

            OxyzoCreateLeadRequestDTO payload = (OxyzoCreateLeadRequestDTO) createLeadRequest.getPayload();

            if(ObjectUtils.isEmpty(payload.getSonOfDaughterOf()) && ObjectUtils.isEmpty(payload.getWifeOf()) && ObjectUtils.isEmpty(payload.getCareOf())){
                log.info("oxyzo: careOf check in aadhar failed");
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
                return false;
            }

            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(createLeadRequest, LenderAssociationStages.CREATE_LEAD);
            log.info("create lead response of oxyzo from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                log.info("createLead request of oxyzo success for {}", lenderAssociationDetailsDto.getApplicationId());

                OxyzoCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDto.getData(), OxyzoCommonResponseDTO.class);

                OxyzoCreateLeadResponseDTO createLeadResponseDTO =  objectMapper.convertValue(commonResponseDTO.getData(), OxyzoCreateLeadResponseDTO.class);


                if (commonResponseDTO.getSuccess() && oxyzoConfig.getSuccessErrorCode().equalsIgnoreCase(commonResponseDTO.getErrorCode()) && StringUtils.isNotBlank(createLeadResponseDTO.getOrganisationId())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setCccId(createLeadResponseDTO.getOrganisationId());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadId(createLeadResponseDTO.getOrganisationId());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_SUCCESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("exception occurred while processing create lead of oxyzo for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
        return false;
    }

    private NBFCRequestDTO getCreateLeadPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        CKycResponseDto cKycResponseDto = lenderAssociationDetailsDto.getCKycResponseDto();
        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();

        LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
        if (ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)) {
            throw new RuntimeException("Lending risk variable snapshot not found for application");
        }

        Map<String, String> businessCategoryAndSubCategoryMap = kycUtils.getBusinessCategoryAndSubCategory(lendingApplication.getMerchantId());
        String merchantCategory = ObjectUtils.isEmpty(businessCategoryAndSubCategoryMap.get("businessCategory"))?"DEFAULT BUSINESS CATEGORY":businessCategoryAndSubCategoryMap.get("businessCategory");

        try {

            Double unsecuredPos = assignmentRuleUtils.getUnsecuredPos(lendingRiskVariablesSnapshot.getMetaData());
            BigDecimal ediTpvRatio = BigDecimal.valueOf(lendingApplication.getEdi()/(lendingRiskVariablesSnapshot.getMonthlyTpv()/30));
            BigDecimal tpv = BigDecimal.valueOf(lendingRiskVariablesSnapshot.getMonthlyTpv());
            log.info("oxyzo: ediTpvRatio: {}, tpv:{} for applicationId:{}",ediTpvRatio,tpv,lendingApplication.getId());

            Map<String, String> location =  getAddresDetails(lenderAssociationDetailsDto);

            NameAndDobDetailsDto nameAndDobDetailsDto = kycUtils.getNameAndDobValues(cKycResponseDto, lendingApplication.getMerchantId());

            CKycResponseDto poa = kycUtils.parsePoaXML(cKycResponseDto.getPoaString(), lendingApplication.getMerchantId(), cKycResponseDto);

            String careOf = poa.getCareOf();
            careOf += ",";

            String sonOfDaughterOf = "";
            String wifeOf = "";
            String careOfGuardian = "";


            if (careOf.contains("S/O") || careOf.contains("D/O")) {
                String relation = careOf.contains("S/O") ? "S/O" : "D/O";
                sonOfDaughterOf = kycUtils.getCareOfName(careOf, relation);
            } else if (careOf.contains("W/O")) {
                wifeOf = kycUtils.getCareOfName(careOf, "W/O");
            } else if (careOf.contains("C/O")) {
                careOfGuardian = kycUtils.getCareOfName(careOf, "C/O");
            }

            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(lendingApplication.getLender())
                    .payload(OxyzoCreateLeadRequestDTO.builder()
                            .mobileNum(kycUtils.getMobileFromKycData(cKycResponseDto))
                            .customerId(lendingApplication.getExternalLoanId())
                            .fullName(nameAndDobDetailsDto.getFullName())
                            .gender(getGender(kycUtils.getGender(cKycResponseDto.getGender())))
                            .dob(DateTimeUtil.parseDate(nameAndDobDetailsDto.getDob(), "dd/MM/yyyy").getTime())
                            .pan(cKycResponseDto.getPanNumber())
                            .aadhaarNo(cKycResponseDto.getAadharNumber())
                            .residentBifurcation(oxyzoConfig.getResidentBifurcation())
                            .currentAddress(null)
                            .currentPincode(null)
                            .permanentAddressSameAsCurrentAddress(true)
                            .permanentAddress(converterUtils.parseData(cKycResponseDto.getAddress()))
                            .permanentPincode(cKycResponseDto.getPincode())
                            .shopName(lendingApplication.getBusinessName())
                            .shopAddress(lendingApplicationServiceV2.constructShopAddress(lendingApplication))
                            .shopPincode(lendingApplication.getPincode().toString())
                            .riskGroup(lendingRiskVariablesSnapshot.getRiskGroup())
                            .pincodeCategory(lendingRiskVariablesSnapshot.getPincodeColor().toString())
                            .merchantCategory(merchantCategory)
                            .vintageOnPlatform(lendingRiskVariablesSnapshot.getVintage().intValue())
                            .tpvMultiplier(BigDecimal.valueOf((lendingApplication.getLoanAmount() + unsecuredPos)/lendingRiskVariablesSnapshot.getMonthlyTpv()))
                            .tpv(tpv)
                            .dailyTpv(BigDecimal.valueOf(lendingRiskVariablesSnapshot.getMonthlyTpv()/30))
                            .ediDailyTpvRatio(ediTpvRatio)
                            .customerType(oxyzoConfig.getCustomerType())
                            .paymentFrequency(oxyzoConfig.getPaymentFrequency())
                            .loanType(oxyzoConfig.getLoanType())
                            .loanSegment(getLoanSegmentMapping(lendingRiskVariablesSnapshot.getLoanSegment()))
                            .interestRate(BigDecimal.valueOf(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getAnnualRoi()))
                            .kycDetails(OxyzoCreateLeadRequestDTO.OxyzoKycDetailsRequestDto.builder().type("AADHAAR").isVerified(true).build())
                            .shopGeoTagLatitude(location.get("latitude"))
                            .shopGeoTagLongitude(location.get("longitude"))
                            .geoTagLatitude(location.get("latitude"))
                            .geoTagLongitude(location.get("longitude"))
                            .sonOfDaughterOf(!ObjectUtils.isEmpty(sonOfDaughterOf) ? sonOfDaughterOf : null)
                            .wifeOf(!ObjectUtils.isEmpty(wifeOf) ? wifeOf : null)
                            .careOf(!ObjectUtils.isEmpty(careOfGuardian) ? careOfGuardian : null)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("exception occurred while creating request payload for createLead of oxyzo for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String getGender(String gender) {
        if(!ObjectUtils.isEmpty(gender)) {
            if("female".equalsIgnoreCase(gender)) {
                return "FEMALE";
            }
            else if("male".equalsIgnoreCase(gender)) {
                return "MALE";
            }
            return "OTHER";
        }
        return "MALE";
    }

    private Map<String, String> getAddresDetails(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {

        Map<String, String> location = new HashMap<>();

        try{

            List<LendingShopDocuments> lendingShopDocumentsList = lendingShopDocumentsDao.findByMerchantIdAndLendingApplicationId(lenderAssociationDetailsRequestDto.getMerchantId(), lenderAssociationDetailsRequestDto.getApplicationId());
            for (LendingShopDocuments lendingShopDocuments : lendingShopDocumentsList) {
                if (!ObjectUtils.isEmpty(lendingShopDocuments) && !ObjectUtils.isEmpty(lendingShopDocuments.getLatitude()) && !ObjectUtils.isEmpty(lendingShopDocuments.getLongitude())) {
                    location.put("latitude", lendingShopDocuments.getLatitude());
                    location.put("longitude", lendingShopDocuments.getLongitude());
                    return location;
                }
            }
            Map<String, Double> dsResponse = dsHandler.fetchDsLocation(lenderAssociationDetailsRequestDto.getMerchantId());

            if (!CollectionUtils.isEmpty(dsResponse)
                    && dsResponse.containsKey("latitude") && dsResponse.containsKey("longitude")
                    && dsResponse.get("latitude") != null && dsResponse.get("longitude") != null) {
                location.put("latitude", String.valueOf(dsResponse.get("latitude")));
                location.put("longitude", String.valueOf(dsResponse.get("longitude")));
                return location;
            }
        } catch (Exception e) {
            log.error("error while getting latitude and longitude for application Id {} and merchantId {}, {}, {}", lenderAssociationDetailsRequestDto.getApplicationId(), lenderAssociationDetailsRequestDto.getMerchantId(), e, Arrays.asList(e.getStackTrace()));
        }

        log.info("couldn't get lat long for applicationId {} and merchantId {} from shop picture and fetch DS location", lenderAssociationDetailsRequestDto.getApplicationId(), lenderAssociationDetailsRequestDto.getMerchantId());

        location.put("latitude", "0.0");
        location.put("longitude", "0.0");

        return location;
    }

    private String getLoanSegmentMapping(String loanSegment){

        switch(loanSegment){
            case "FRESH":
            case "REGULAR_ETC":
                return "REGULAR_ETC";
            case "REPEAT":
                return "REPEAT";
            case "TOPUP":
                return "TOP_UP";
            default:
                return loanSegment;
        }

    }
}

