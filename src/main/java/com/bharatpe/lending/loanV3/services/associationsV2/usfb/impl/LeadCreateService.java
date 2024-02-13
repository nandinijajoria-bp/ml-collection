package com.bharatpe.lending.loanV3.services.associationsV2.usfb.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingMerchantDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingMerchantDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.usfb.CreateLeadRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.usfb.CreateLeadResponseDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.usfb.validations.CreateLeadPayloadValidation;
import com.bharatpe.lending.loanV3.services.associationsV2.wrapper.InvokeCreateLeadAndDocUploadWrapperService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
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
public class LeadCreateService {
    @Autowired
    KycUtils kycUtils;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CreateLeadPayloadValidation createLeadPayloadValidation;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    LendingMerchantDetailsDao lendingMerchantDetailsDao;

    @Transactional
    public boolean invokeCreateLead(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId()) || ObjectUtils.isEmpty(lenderAssociationDetailsDto.getLendingApplication())) {
                log.info("Application Id not found for merchant: {}", lenderAssociationDetailsDto.getMerchantId());
                return false;
            }
            if (InvokeCreateLeadAndDocUploadWrapperService.kycDataNeeded(LenderAssociationStages.CREATE_LEAD.name()) && ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));
            }
            if (createLeadPayloadValidation.isInValidPayload(lenderAssociationDetailsDto.getCKycResponseDto())) {
                log.info("invalid response from downstream api for createLead of USFB : {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
                return false;
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.PiramalAssociationStages.LEAD_CREATION.name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);
            LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
            NBFCRequestDTO createLeadRequest = getPayload(lenderAssociationDetailsDto);
            if (Objects.isNull(createLeadRequest)) {
                log.info("error in create lead payload of USFB for applicationId: {}", lendingApplication.getId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
                return false;
            }
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(createLeadRequest, LenderAssociationStages.CREATE_LEAD);
            log.info("create lead response of USFB from nbfc {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                log.info("createLead request of USFB success for {}", lenderAssociationDetailsDto.getApplicationId());
                CreateLeadResponseDTO createLeadResponseDTO = objectMapper.readValue( objectMapper.writeValueAsString(nbfcResponseDto.getData()), CreateLeadResponseDTO.class);
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadId(createLeadResponseDTO.getData().getLeadId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_SUCCESS.name());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setNbfcId(createLeadResponseDTO.getData().getLoanAccountNumber());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return true;
            }
        } catch (Exception e) {
            log.info("exception occurred while processing create lead of USFB for {} {} {}", lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(LenderAssociationStatus.LEAD_CREATION_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, LenderAssociationStatus.LEAD_CREATION_FAILED);
        return false;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        CKycResponseDto cKycResponseDto = lenderAssociationDetailsDto.getCKycResponseDto();
        LendingApplication lendingApplication = lenderAssociationDetailsDto.getLendingApplication();
        try {
            LendingMerchantDetails lendingMerchantDetails = lendingMerchantDetailsDao.findTop1ByMerchantIdOrderByIdDesc(lendingApplication.getMerchantId());
            if(ObjectUtils.isEmpty(lendingMerchantDetails)) {
                throw new RuntimeException("lending merchant details not found for application");
            }
            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
            if(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)) {
                throw new RuntimeException("Lending risk variable snapshot not found for application");
            }
            String loanDate = DateTimeUtil.getDateInFormat(new Date(), "dd-MMM-yyyy");
            CreateLeadRequestDTO createLeadRequestDTO = CreateLeadRequestDTO.builder()
                    .mobileNumber(ObjectUtils.isEmpty(cKycResponseDto.getMobile()) ? "" : cKycResponseDto.getMobile().substring(2))
                    .fullName(cKycResponseDto.getName())
                    .email(cKycResponseDto.getEmail())
                    .gender("male".equalsIgnoreCase(kycUtils.getGender(cKycResponseDto.getGender())) ? 1 : 2) // 1: male 2 : female
                    .fathersName(getFathersName(cKycResponseDto.getAddress()))
                    .kyc(getKycDetails(cKycResponseDto))
                    .address(cKycResponseDto.getAddress())
                    .dateOfBirth(DateTimeUtil.formatDate(cKycResponseDto.getDob(), "dd/MM/yyyy","dd-MMM-yyyy"))
                    .agreementDate(loanDate)
                    .loanApplicationDate(loanDate)
                    .companyName(ObjectUtils.isEmpty(lendingMerchantDetails.getBusinessName()) ? "Company Name Not Present" : lendingMerchantDetails.getBusinessName())
                    .creditScore(lendingRiskVariablesSnapshot.getBureauScore().intValue())    // 300 : default value if not present
                    .employmentType(3)   // 3: Self-employed professional
                    .monthlyEmi(0)       // 0 : In case monthly emi is not there
                    .InterestRate(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getAnnualRoi())
                    .loanAmount(lendingApplication.getLoanAmount().intValue())
                    .monthlyIncome(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getMonthlyIncome()) ? 0 : lendingRiskVariablesSnapshot.getMonthlyIncome().intValue())    // 0 : In case don't have monthly income of merchant
                    .partnerLoanId(lendingApplication.getExternalLoanId())
                    .loanType(1)        // 1 : Personal loan
                    .tenure(lendingApplication.getTenureInMonths())
                    .paymentFrequency(8)  // 8: Daily
                    .agreementSignatureType(2)  // 2: Aadhaar e-signature
                    .repaymentCount(lendingApplication.getPayableDays())
                    .pincode(cKycResponseDto.getPincode())
                    .disbursalDetail(Arrays.asList(CreateLeadRequestDTO.DisbursalDetails.builder()
                            .amount(lendingApplication.getLoanAmount())
                            .processingFee(lendingApplication.getProcessingFee())
                            .disbursalType(0)  // 0 : Principal, 1 : withHold
                            .date(loanDate)
                            .build()))
                    .source("BharatPe")
                    .product("BharatPe")
                    .additionalVariables(CreateLeadRequestDTO.AdditionalVariables.builder()
                            .riskSegment(lendingRiskVariablesSnapshot.getRiskSegment().name())
                            .riskCategory(lendingRiskVariablesSnapshot.getRiskGroup())
                            .uploadDate(loanDate)
                            .cautionProfileCategory("")
                            .cautionProfileSubCategory("")
                            .latitude(lendingApplication.getLatitude())
                            .longitude(lendingApplication.getLongitude())
                            .pinCodeColour(lendingRiskVariablesSnapshot.getPincodeColor().name())
                            .build())
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.USFB.name())
                    .payload(createLeadRequestDTO)
                    .build();
        } catch (Exception e) {
            log.info("exception occurred while creating request payload for createLead for applicationId: {}, {}", lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private List<CreateLeadRequestDTO.Kyc> getKycDetails(CKycResponseDto cKycResponseDto) {
        List<CreateLeadRequestDTO.Kyc> kycList = new ArrayList<>();
        kycList.add(CreateLeadRequestDTO.Kyc.builder()
                .kycName(cKycResponseDto.getName())
                .identifier(cKycResponseDto.getPanNumber())
                .kycType(2)
                .isVerified(true)
                .verifiedUsing(4)
                .build()
        );
        kycList.add(CreateLeadRequestDTO.Kyc.builder()
                .kycName(cKycResponseDto.getName())
                .identifier(cKycResponseDto.getAadharNumber())
                .kycType(1)
                .isVerified(true)
                .verifiedUsing(4)
                .build()
        );
        return kycList;
    }

    private String getFathersName(String address) {
        String fathersName = "Father Name Not Present";  //default value in case fathersName not present
        address = address.toUpperCase();
        if(address.contains("S/O")) {
            address = address.replaceAll("S/O", "").replaceAll("\\.", "").replaceAll(":", "").replaceAll(" ", "");
            fathersName = address.substring(0, address.indexOf(","));
        }
        return fathersName;
    }
}
