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
import com.bharatpe.lending.loanV3.dto.request.usfb.UpdateLeadRequestDTO;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.usfb.validations.CreateLeadPayloadValidation;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.*;

@Slf4j
@Service
public class LeadUpdateService {
    @Autowired
    CommonService commonService;

    @Autowired
    KycUtils kycUtils;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    CreateLeadPayloadValidation createLeadPayloadValidation;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    LendingMerchantDetailsDao lendingMerchantDetailsDao;

    @Transactional
    public Boolean invokeUpdateLead(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        try {
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.UPDATE_LEAD.name());
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.UPDATE_LEAD_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);

            lenderAssociationDetailsRequestDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsRequestDto.getMerchantId()));
            NBFCRequestDTO updateLeadRequestDto = getPayload(lenderAssociationDetailsRequestDto);
            if (Objects.isNull(updateLeadRequestDto) || createLeadPayloadValidation.isInValidPayload(lenderAssociationDetailsRequestDto.getCKycResponseDto())) {
                log.info("error in update lead payload of USFB for applicationId: {}", lenderAssociationDetailsRequestDto.getApplicationId());
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.UPDATE_LEAD_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.UPDATE_LEAD_FAILED);
                return false;
            }
            NBFCResponseDTO updateLeadResponseDTO = lenderAPIGateway.invokeStage(updateLeadRequestDto, LenderAssociationStages.UPDATE_LEAD);
            log.info("update lead response of USFB from nbfc: {} with applicationId: {}", updateLeadResponseDTO, lenderAssociationDetailsRequestDto.getApplicationId());
            if (Objects.nonNull(updateLeadResponseDTO) && updateLeadResponseDTO.getSuccess() && Objects.nonNull(updateLeadResponseDTO.getData())) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.UPDATE_LEAD_COMPLETED.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                return true;
            }
        } catch (Exception e) {
            log.error("error while pushing update lead of USFB for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.UPDATE_LEAD_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.UPDATE_LEAD_FAILED);
        return Boolean.FALSE;
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
            UpdateLeadRequestDTO updateLeadRequest = UpdateLeadRequestDTO.builder()
                    .leadId(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId())
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
                    .disbursalDetail(Arrays.asList(UpdateLeadRequestDTO.DisbursalDetails.builder()
                            .amount(lendingApplication.getLoanAmount())
                            .processingFee(lendingApplication.getProcessingFee())
                            .disbursalType(0)  // 0 : Principal, 1 : withHold
                            .date(loanDate)
                            .build()))
                    .source("BharatPe")
                    .product("BharatPe")
                    .additionalVariables(UpdateLeadRequestDTO.AdditionalVariables.builder()
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
                    .payload(updateLeadRequest)
                    .build();
        } catch (Exception e) {
            log.info("exception occurred while creating request payload for createLead for applicationId: {}, {}", lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private List<UpdateLeadRequestDTO.Kyc> getKycDetails(CKycResponseDto cKycResponseDto) {
        List<UpdateLeadRequestDTO.Kyc> kycList = new ArrayList<>();
        kycList.add(UpdateLeadRequestDTO.Kyc.builder()
                .kycName(cKycResponseDto.getName())
                .identifier(cKycResponseDto.getPanNumber())
                .kycType(2)
                .isVerified(true)
                .verifiedUsing(4)
                .build()
        );
        kycList.add(UpdateLeadRequestDTO.Kyc.builder()
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
