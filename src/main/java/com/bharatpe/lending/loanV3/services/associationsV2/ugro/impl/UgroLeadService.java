package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.ugro.UgroCreateLeadRequest;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroCreateLeadResponse;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.ugro.validations.UgroPayloadValidation;
import com.bharatpe.lending.loanV3.services.associationsV2.wrapper.InvokeCreateLeadAndDocUploadWrapperService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class UgroLeadService {
    @Autowired
    KycUtils kycUtils;

    @Autowired
    UgroPayloadValidation payloadValidation;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UgroConfig ugroConfig;

    @Autowired
    ConverterUtils converterUtils;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;


    @Transactional
    public boolean invokeCreateLead(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("UGRO: application id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            if (InvokeCreateLeadAndDocUploadWrapperService.kycDataNeeded(LenderAssociationStages.CREATE_LEAD.name()) && ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));
            }
            if (payloadValidation.isInvalidCreateLeadCKycData(lenderAssociationDetailsDto.getCKycResponseDto())) {
                log.error("UGRO: invalid response from downstream api for createLead : {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
                return false;
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.PiramalAssociationStages.LEAD_CREATION.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            NBFCRequestDTO<?> createLeadRequest = getCreateLeadPayload(lenderAssociationDetailsDto);
            if (ObjectUtils.isEmpty(createLeadRequest)) {
                log.info("UGRO: error in create lead payload for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
                return false;
            }
            NBFCResponseDTO<?> initialResponse = lenderAPIGateway.invokeStage(createLeadRequest, LenderAssociationStages.CREATE_LEAD);
            if (!ObjectUtils.isEmpty(initialResponse) && initialResponse.getSuccess() && !ObjectUtils.isEmpty(initialResponse.getData())) {
                log.info("UGRO: createLead request success for {}", lenderAssociationDetailsDto.getApplicationId());
                UgroCreateLeadResponse createLeadResponse = objectMapper.convertValue(initialResponse.getData(), UgroCreateLeadResponse.class);

                if (!ObjectUtils.isEmpty(createLeadResponse) && !ObjectUtils.isEmpty(createLeadResponse.getLeadId())) {
                    // in case of dedupe response, return false
                    if (!ObjectUtils.isEmpty(createLeadResponse.getErr()) && HttpStatus.BAD_REQUEST.toString().equalsIgnoreCase(initialResponse.getError())) {
                        return false;
                    }

                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadId(createLeadResponse.getLeadId());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_SUCCESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }
            }
        } catch (Exception e) {
            log.info("UGRO: exception occurred while processing create lead for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
        return false;
    }

    private NBFCRequestDTO<?> getCreateLeadPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        try {
            CKycResponseDto cKycResponseDto = lenderAssociationDetailsRequest.getCKycResponseDto();
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails();
            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || ObjectUtils.isEmpty(lendingApplication) || ObjectUtils.isEmpty(cKycResponseDto) || ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)) {
                throw new RuntimeException("UGRO: LA/LALD/CKyc/LRVS not found for application " + lendingApplication.getId());
            }

            CKycResponseDto gstResponseDto = kycUtils.getGstData(lenderAssociationDetailsRequest.getMerchantId());
            Map<String, String> businessCategoryAndSubCategoryMap = kycUtils.getBusinessCategoryAndSubCategory(lendingApplication.getMerchantId());

            String businessAddress = Optional.ofNullable(lendingApplication.getArea()).orElse("") + " " + Optional.ofNullable(lendingApplication.getLandmark()).orElse("");

            UgroCreateLeadRequest createLeadRequest = UgroCreateLeadRequest.builder()
                    .product(ugroConfig.getProduct())
                    .profileData(UgroCreateLeadRequest.ProfileData.builder()
                            .name(cKycResponseDto.getName())
                            .panNumber(cKycResponseDto.getPanNumber())
                            .dob(DateTimeUtil.getDateInMillis(cKycResponseDto.getDob(), "dd/MM/yyyy"))
                            .gender(ObjectUtils.isEmpty(cKycResponseDto.getGender()) ? "transgender" : kycUtils.getGender(cKycResponseDto.getGender()).toLowerCase())
                            .mobile(ObjectUtils.isEmpty(cKycResponseDto.getMobile()) ? "" : cKycResponseDto.getMobile().substring(2))
                            .currentEmployer(ObjectUtils.isEmpty(gstResponseDto) || ObjectUtils.isEmpty(gstResponseDto.getTradeName()) ? lendingApplication.getBusinessName() : gstResponseDto.getTradeName())
                            .employeeId(lendingApplication.getExternalLoanId())
                            .referenceId(lendingApplication.getExternalLoanId())
                            .address(getAddress(lendingApplication, cKycResponseDto))
                            .workAddress(getWorkAddress(lendingApplication))
                            .financeBasicInfo(UgroCreateLeadRequest.ProfileData.FinanceBasicInfo.builder()
                                    .netMonthlyIncome(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getMonthlyTpv()) ? null : lendingRiskVariablesSnapshot.getMonthlyTpv())
                                    .employmentType(ugroConfig.getEmploymentType()).build())
                            .loanRequest(UgroCreateLeadRequest.ProfileData.LoanRequest.builder()
                                    .amount(lendingApplication.getLoanAmount())
                                    .tenure(lendingApplication.getTenureInMonths())
                                    .interestRate(lendingApplicationLenderDetails.getAnnualRoi())
                                    .processingFeePct(Double.valueOf(String.format("%.4f", (lendingApplication.getProcessingFee() / lendingApplication.getLoanAmount()) * 100)))
                                    .purpose(ugroConfig.getLoanRequestPurpose()).build())
                            .build())
                    .acquisitionPlatformData(UgroCreateLeadRequest.AcquisitionPlatformData.builder()
                            .loanSegment(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getRiskSegment()) ? null : lendingRiskVariablesSnapshot.getRiskSegment().name())
                            .segmentBand(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getRiskGroup()) ? null : lendingRiskVariablesSnapshot.getRiskGroup())
                            .pincodeSegment(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getPincodeColor()) ? null : lendingRiskVariablesSnapshot.getPincodeColor().name())
                            .tpv(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getMonthlyTpv()) ? null : lendingRiskVariablesSnapshot.getMonthlyTpv())
                            .netFreeIncome(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getMonthlyNfi()) ? null : lendingRiskVariablesSnapshot.getMonthlyNfi())
                            .vintageMonths(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getVintage()) ? null : lendingRiskVariablesSnapshot.getVintage().intValue() / 30)
                            .isActiveTransactor(Boolean.TRUE)
                            .build())
                    .udyamRegistrationFields(UgroCreateLeadRequest.UdyamRegistrationFields.builder()
                            .enterpriseName(ObjectUtils.isEmpty(gstResponseDto) || ObjectUtils.isEmpty(gstResponseDto.getTradeName()) ? lendingApplication.getBusinessName() : gstResponseDto.getTradeName())
                            .residentialAddressLine(converterUtils.parseData(cKycResponseDto.getAddress()))
                            .residentialCity(Optional.ofNullable(converterUtils.parseData(cKycResponseDto.getCity())).orElse(converterUtils.parseData(cKycResponseDto.getAddress())))
                            .residentialState(Optional.ofNullable(converterUtils.parseData(cKycResponseDto.getState())).orElse(converterUtils.parseData(cKycResponseDto.getAddress())))
                            .residentialDistrict(Optional.ofNullable(converterUtils.parseData(cKycResponseDto.getCity())).orElse(converterUtils.parseData(cKycResponseDto.getAddress())))
                            .residentialPincode(ObjectUtils.isEmpty(cKycResponseDto.getPincode()) ? lendingApplication.getPincode().toString() : cKycResponseDto.getPincode())
                            .typeOfOrganization(ugroConfig.getTypeOfOrganization())
                            .businessAddressLine(businessAddress)
                            .businessCity(Optional.ofNullable(lendingApplication.getCity()).orElse(businessAddress))
                            .businessState(Optional.ofNullable(lendingApplication.getState()).orElse(businessAddress))
                            .businessDistrict(Optional.ofNullable(lendingApplication.getCity()).orElse(businessAddress))
                            .businessPincode(lendingApplication.getPincode().toString())
                            .businessSector(ugroConfig.getBusinessSector())
                            .bsrCode(ugroConfig.getBsrCode(businessCategoryAndSubCategoryMap.getOrDefault("businessCategory", ugroConfig.getDefaultBsrCode())))
                            .build())
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(createLeadRequest).build();
        } catch (Exception e) {
            log.info("UGRO: Exception in creating payload of create lead for {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    public UgroCreateLeadRequest.ProfileData.Address getAddress(LendingApplication lendingApplication, CKycResponseDto cKycResponseDto) {
        String address = converterUtils.parseData(cKycResponseDto.getAddress()).trim();
        int addressSize = address.length();
        String address1 = "", address2 = "", address3 = "", address4 = "";
        if (addressSize <= 40) {
            address1 = address;
            address2 = address;
            address3 = address;
            address4 = address;
        } else if (addressSize <= 80) {
            address1 = address.substring(0, 40);
            address2 = address.substring(40, addressSize);
            address3 = address1;
            address4 = address1;
        } else if (addressSize <= 120) {
            address1 = address.substring(0, 40);
            address2 = address.substring(40, 80);
            address3 = address.substring(80, addressSize);
            address4 = address1;
        } else {
            address1 = address.substring(0, 40);
            address2 = address.substring(40, 80);
            address3 = address.substring(80, 120);
            address4 = address.substring(120, addressSize);
        }
        return UgroCreateLeadRequest.ProfileData.Address.builder()
                .house(address1).locality(address2)
                .landmark(address3).street(address4)
                .pincode(ObjectUtils.isEmpty(cKycResponseDto.getPincode()) ? lendingApplication.getPincode().toString() : cKycResponseDto.getPincode())
                .build();
    }

    public UgroCreateLeadRequest.ProfileData.Address getWorkAddress(LendingApplication lendingApplication) {
        String address = Optional.ofNullable(lendingApplication.getArea()).orElse("") + " " + Optional.ofNullable(lendingApplication.getLandmark()).orElse("");
        address = address.trim();
        int addressSize = address.length();
        String address1 = "", address2 = "", address3 = "";
        if (addressSize <= 40) {
            address1 = address;
            address2 = address;
            address3 = address;
        } else if (addressSize <= 80) {
            address1 = address.substring(0, 40);
            address2 = address.substring(40, addressSize);
            address3 = address1;
        } else {
            address1 = address.substring(0, 40);
            address2 = address.substring(40, 80);
            address3 = address.substring(80, addressSize);
        }
        return UgroCreateLeadRequest.ProfileData.Address.builder()
                .house(lendingApplication.getShopNumber() + address1).locality(address2)
                .landmark(address3).street(lendingApplication.getStreetAddress())
                .pincode(String.valueOf(lendingApplication.getPincode())).build();
    }
}
