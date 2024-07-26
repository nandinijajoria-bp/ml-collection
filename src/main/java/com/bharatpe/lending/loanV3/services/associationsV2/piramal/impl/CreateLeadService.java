package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.NameAndDobDetailsDto;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.piramal.*;
import com.bharatpe.lending.loanV3.enums.StateMapping;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.validations.CreateLeadPayloadValidationLayer;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.wrapper.InvokeCreateLeadAndDocUploadWraperService;
import com.bharatpe.lending.loanV3.services.gateway.piramal.ILenderGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Objects;
import java.util.*;

@Service
@Slf4j
public class CreateLeadService {

    @Autowired
    KycUtils kycUtils;

    @Autowired
    ConverterUtils converterUtils;

    @Autowired
    ILenderGateway iLenderGateway;

    @Autowired
    CommonService commonService;


    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    CreateLeadPayloadValidationLayer createLeadPayloadValidationLayer;

    @Transactional
    public boolean invokeCreateLead(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("Application Id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            if (InvokeCreateLeadAndDocUploadWraperService.kycDataNeeded(LenderAssociationStages.PiramalAssociationStages.LEAD_CREATION.name()) && ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));
            }
            if (createLeadPayloadValidationLayer.isInValidPayload(lenderAssociationDetailsDto.getCKycResponseDto(), lenderAssociationDetailsDto.getMerchantId())) {
                log.info("invalid response from downstream api : {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
                return false;
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.PiramalAssociationStages.LEAD_CREATION.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
            NbfcRequestDto createLeadRequestDTO = getPayload(lenderAssociationDetailsDto);
            if (Objects.isNull(createLeadRequestDTO)) {
                log.info("error in create lead payload for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
                return false;
            }
            NbfcResponseDto nbfcResponseDto = iLenderGateway.invokeStage(createLeadRequestDTO, LenderAssociationStages.PiramalAssociationStages.LEAD_CREATION);
            log.info("create lead response from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                CreateLeadResponseDTO createLeadResponseDTO = objectMapper.readValue( objectMapper.writeValueAsString(nbfcResponseDto.getData()), CreateLeadResponseDTO.class);
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadId(createLeadResponseDTO.getLeadId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_SUCCESS.name());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setCccId(createLeadResponseDTO.getApplicantDetail().get(0).getCustomerId());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return true;
            }
        } catch (Exception e) {
            log.info("exception occurred while processing create lead {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
        return false;
    }


    private NbfcRequestDto getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        // call validation Layer
        CKycResponseDto cKycResponseDto = lenderAssociationDetailsDto.getCKycResponseDto();
        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
        try {
            NameAndDobDetailsDto nameAndDobDetailsDto = kycUtils.getNameAndDobValues(cKycResponseDto, lenderAssociationDetailsDto.getMerchantId());
            String firstName = nameAndDobDetailsDto.getFirstName();
            String middleName = nameAndDobDetailsDto.getMiddleName();
            String lastName = nameAndDobDetailsDto.getLastName();
            List<CreateLeadRequestDTO.ApplicantsDetail> applicant = new ArrayList<>();
            applicant.add(getApplicantDetails(cKycResponseDto, nameAndDobDetailsDto));
            CreateLeadRequestDTO createLeadRequestDTO = CreateLeadRequestDTO.builder()
                            .partnerApplicationId(lenderAssociationDetailsDto.getLendingApplication().getExternalLoanId())
                            .entityType("INDIVIDUAL")
                            .primaryApplicantDetail(
                                    CreateLeadRequestDTO.PrimaryApplicantDetail.builder()
                                            .firstName(firstName)
                                            .middleName(middleName)
                                            .lastName(lastName)
                                            .mobileNo(ObjectUtils.isEmpty(cKycResponseDto.getMobile()) ? "" : cKycResponseDto.getMobile().substring(2))
                                            .build()
                            )
                            .applicantsDetail(applicant)
                            .loanInformation(CreateLeadRequestDTO.LoanInformation.builder()
                                    .offeredAmount(lendingApplication.getLoanAmount())
                                    .requestedAmount(lendingApplication.getLoanAmount())
                                    .requestedTenure(lendingApplication.getTenureInMonths() * 30)
                                    .offeredTenure(lendingApplication.getTenureInMonths() * 30)
                                    .offeredInterestRate(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getAnnualRoi())
                                    .requestedRateOfInterest(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getAnnualRoi())
                                    .loanPurpose("PERSONAL")
                                    .loanProductType("PERSONAL_LOAN")
                                    .build())
                            .build();
            log.info("create lead request dto: {} for applicationId: {}", createLeadRequestDTO, lendingApplication.getId());
            return NbfcRequestDto.builder().applicationId(lenderAssociationDetailsDto.getApplicationId()).payload(createLeadRequestDTO).lender(Lender.PIRAMAL.name()).productName("LENDING").build();
        } catch (Exception e) {
            log.info("exception occurred while create lead request dto formation for applicationId: {} {} {}", e.getMessage(), lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private CreateLeadRequestDTO.ApplicantsDetail getApplicantDetails(CKycResponseDto cKycResponseDto, NameAndDobDetailsDto nameAndDobDetailsDto) {
        CreateLeadRequestDTO.ApplicantsDetail applicantsDetail = CreateLeadRequestDTO.ApplicantsDetail.builder()
                .applicant(CreateLeadRequestDTO.ApplicantsDetail.Applicant.builder()
                        .firstName(nameAndDobDetailsDto.getFirstName())
                        .applicantType("PRIMARY")
                        .middleName(nameAndDobDetailsDto.getMiddleName())
                        .lastName(nameAndDobDetailsDto.getLastName())
                        .countryOfBirth("INDIA")
                        .residentialStatus("RESIDENTIAL_INDIAN")
                        .maritalStatus("OTHER")
                        .occupationType("SENP")
                        .gender(ObjectUtils.isEmpty(cKycResponseDto.getGender()) ? "OTHERS" : getGender(cKycResponseDto.getGender()))
                        .dateOfBirth(DateTimeUtil.formatDate(nameAndDobDetailsDto.getDob(), "dd/MM/yyyy", "yyyy-MM-dd'T'HH:mm:ss.000'Z'"))
                        .salutation(cKycResponseDto.getGender().equalsIgnoreCase("F") ? "MRS" : "MR")
                        .mobileNo(ObjectUtils.isEmpty(cKycResponseDto.getMobile()) ? "" : cKycResponseDto.getMobile().substring(2))
                        .panCardDetail(CreateLeadRequestDTO.ApplicantsDetail.Applicant.PanCardDetail.builder()
                                .name(nameAndDobDetailsDto.getFirstName())
                                .panCardNo(cKycResponseDto.getPanNumber())
                                .dateOfBirth(DateTimeUtil.formatDate(nameAndDobDetailsDto.getDob(), "dd/MM/yyyy", "yyyy-MM-dd'T'HH:mm:ss.000'Z'"))
                                .build())
                        .currentAddress(getAddress(cKycResponseDto, "CURRENT"))
                        .permanentAddress(getAddress(cKycResponseDto, "PERMANENT"))
                        .kycAddress(getAddress(cKycResponseDto, "KYC"))
                        .build())
                .kycDetail(CreateLeadRequestDTO.ApplicantsDetail.KycDetail.builder()
//                        .ckycID(cKycResponseDto.getAadharNumber())
                        .kycType("OKYC")
                        .build())
                .build();
        return applicantsDetail;
    }

    private String getGender(String gender) {
        if (Objects.nonNull(gender) && ("M".equalsIgnoreCase(gender) || "MALE".equalsIgnoreCase(gender))) {
            return "MALE";
        }
        if (Objects.nonNull(gender) && ("F".equalsIgnoreCase(gender) || "FEMALE".equalsIgnoreCase(gender))) {
            return "FEMALE";
        }
        return "OTHERS";
    }

    public CreateLeadRequestDTO.ApplicantsDetail.Applicant.Address getAddress(CKycResponseDto cKycResponseDto, String addressType) {
        String address = converterUtils.parseData(cKycResponseDto.getAddress());
        int addressSize = address.length();
        String address1 = "", address2 = "", address3 = "";
        if (addressSize <= 40) {
            address1 = address;
        } else if (addressSize <= 80) {
            address1 = address.substring(0, 40);
            address2 = address.substring(40, addressSize);
        } else {
            address1 = address.substring(0, 40);
            address2 = address.substring(40, 80);
            address3 = address.substring(80, addressSize);
        }
        CreateLeadRequestDTO.ApplicantsDetail.Applicant.Address currentAddress = CreateLeadRequestDTO.ApplicantsDetail.Applicant.Address
                .builder()
                .addressType(addressType)
                .addressLine1(address1)
                .addressLine2(address2)
                .addressLine3(address3)
                .city(cKycResponseDto.getCity())
                .stateCode(cKycResponseDto.getState())
                .street(".")
                .buildingNumber(".")
                .stateCode(Objects.nonNull(StateMapping.getStateEnum(cKycResponseDto.getState())) ? StateMapping.getStateEnum(cKycResponseDto.getState()).name() : null)
                .country("INDIA")
                .postalCode(cKycResponseDto.getPincode())
                .build();
        return currentAddress;
    }
}
