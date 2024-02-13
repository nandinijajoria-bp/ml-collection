package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingGstDetail;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.BankStatementSessionDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.loanV2.handlers.BureauHandler;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLBreRequestDto;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.TLBreResponseDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Objects;

@Slf4j
@Service
public class TLBreService {
    @Autowired
    CommonService commonService;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    ConverterUtils converterUtils;

    @Autowired
    KycUtils kycUtils;

    @Autowired
    LendingGstDao lendingGstDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Transactional
    public Boolean invokeBre(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        try {
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStages.RISK_DECISION.name());
            if (Objects.isNull(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails())
                    || Objects.isNull(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadId())) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
                return false;
            }
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_PENDING.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
            NBFCRequestDTO breRequest = getPayload(lenderAssociationDetailsRequestDto);
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(breRequest, LenderAssociationStages.BRE);
            log.info("Bre response of TrillionLoans from nbfc: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequestDto.getApplicationId());
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());

            if (nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                TLBreResponseDto breResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), TLBreResponseDto.class);
                if (breResponseDTO.getSuccess()) {
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_COMPLETED.name());
                    commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequestDto);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("error while invoking Bre of TrillionLoans for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequestDto, LenderAssociationStatus.RISK_FAILED);
        return false;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails();
        CKycResponseDto cKycResponseDto = kycUtils.getKycData(lenderAssociationDetailsRequest.getMerchantId());

        try {
            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
            LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(lendingApplication.getId());

            if (ObjectUtils.isEmpty(lendingRiskVariablesSnapshot) && ObjectUtils.isEmpty(lendingGstDetail)) {
                throw new RuntimeException("Lending Risk variable snapshot/Lending GST details not found for application id: " + lendingApplication.getId());
            }

            LinkedHashMap<String, Object> identifierMap = new LinkedHashMap<>();
            identifierMap.put("leadId", lendingApplicationLenderDetails.getLeadId());

            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(TLBreRequestDto.builder()
                            .accountId(lendingApplication.getExternalLoanId())
                            .productCode("BharatPe")
                            .source("BharatPe")
                            .customerReport(getCustomerReport(cKycResponseDto))
                            .loanApplicationRequest(getLoanApplicationRequest(lendingApplication, lendingApplicationLenderDetails))
                            .values(getValues(lendingRiskVariablesSnapshot, lendingApplication, lendingGstDetail, cKycResponseDto))
                            .build())
                    .identifier(identifierMap)
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating BRE payload of TrillionLoans for application {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private TLBreRequestDto.Values getValues(LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot, LendingApplication lendingApplication, LendingGstDetail lendingGstDetail, CKycResponseDto cKycResponseDto) {


        LendingPaymentSchedule lastLoan = lendingPaymentScheduleDao.findTop1ByMerchantIdAndStatusAndCreditLoanOrderByIdDesc(lendingApplication.getMerchantId(), "CLOSED", false);
        LendingPaymentSchedule currentLoan = lendingPaymentScheduleDao.findTop1ByMerchantIdAndStatusAndCreditLoanOrderByIdDesc(lendingApplication.getMerchantId(), "ACTIVE", false);
        Integer maxDpdInLastLoan = loanUtil.getMaxDpdInLastLoan(lendingApplication.getMerchantId(), lastLoan);
        Integer maxDpdInCurrentLoan = loanUtil.getMaxDpdInLastLoan(lendingApplication.getMerchantId(), currentLoan);


        return TLBreRequestDto.Values.builder()
                .input(TLBreRequestDto.Values.Input.builder()
                        .loanSegment(lendingRiskVariablesSnapshot.getLoanSegment())
                        .riskSegment(lendingRiskVariablesSnapshot.getRiskSegment().name())
                        .riskGroup(lendingRiskVariablesSnapshot.getRiskGroup())
                        .businessCategory(lendingApplication.getCategory())
                        .shopStructure(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getShopStructure()) ? "" : lendingRiskVariablesSnapshot.getShopStructure().name())
                        .bureauScore(lendingRiskVariablesSnapshot.getBureauScore())
                        .drs(lendingRiskVariablesSnapshot.getDrsScore())
                        .bbs(lendingRiskVariablesSnapshot.getBbs())
                        .bpScore(lendingRiskVariablesSnapshot.getBpScore())
                        .vintage(lendingRiskVariablesSnapshot.getVintage())
                        .uniqueCustomerCount(lendingRiskVariablesSnapshot.getUniqueCustomer1mon())
                        .maxDPDlastLoan(maxDpdInLastLoan)
                        .maxDPDcurrentLoan(maxDpdInCurrentLoan)
                        .pincodeColor(lendingRiskVariablesSnapshot.getPincodeColor().name())
                        .pincode(cKycResponseDto.getPincode())
                        .merchantStatus("ACTIVE")
                        .adjMontlyNFI(lendingRiskVariablesSnapshot.getMonthlyNfi())
                        .adjMontlyTPV(lendingRiskVariablesSnapshot.getMonthlyTpv())
                        .bankEnancedOffer(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getBankBasedOffer()) ? 0 : lendingRiskVariablesSnapshot.getBankBasedOffer())
                        .bankEnancedOfferEligibility(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getBankBasedAffectedOffer()) ? Boolean.FALSE : lendingRiskVariablesSnapshot.getBankBasedAffectedOffer())
                        .aaEnancedOffer(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getAaBasedOffer()) ? 0 : lendingRiskVariablesSnapshot.getAaBasedOffer())
                        .aaEnancedOfferEligibility(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getAaBasedAffectedOffer()) ? Boolean.FALSE : lendingRiskVariablesSnapshot.getAaBasedAffectedOffer())
                        .gstEnancedOffer(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getGstOffer()) ? 0 : lendingRiskVariablesSnapshot.getGstOffer())
                        .gstEnancedOfferEligibility(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getGstAffectedOffer()) ? Boolean.FALSE : lendingRiskVariablesSnapshot.getGstAffectedOffer())
                        .gst3bEnancedOffer(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getGst3bBasedOffer()) ? 0 : lendingRiskVariablesSnapshot.getGst3bBasedOffer())
                        .gst3bEnancedOfferEligibility(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getGst3bBasedAffectedOffer()) ? Boolean.FALSE : lendingRiskVariablesSnapshot.getGst3bBasedAffectedOffer())
                        .maxTenure(lendingApplication.getTenureInMonths())
                        .loanCapping(lendingApplication.getLoanAmount())
                        .age(kycUtils.getAgeFromDob(cKycResponseDto.getDob()))
                        .build())
                .build();
    }

    private TLBreRequestDto.LoanApplicationRequest getLoanApplicationRequest(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        return TLBreRequestDto.LoanApplicationRequest.builder()
                .requestedLoanAmount(lendingApplication.getLoanAmount())
                .roi(lendingApplicationLenderDetails.getAnnualRoi().toString())
                .tenure(lendingApplication.getTenureInMonths().toString())
                .build();
    }

    private TLBreRequestDto.CustomerReport getCustomerReport(CKycResponseDto cKycResponseDto) {
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

        return TLBreRequestDto.CustomerReport.builder()
                .kycInfo(TLBreRequestDto.CustomerReport.KycInfo.builder()
                        .city(cKycResponseDto.getCity())
                        .gender(kycUtils.getGender(cKycResponseDto.getGender()))
                        .firstName(kycUtils.getFirstName(cKycResponseDto))
                        .middleName(kycUtils.getMiddleName(cKycResponseDto))
                        .lastName(kycUtils.getLastName(cKycResponseDto))
                        .panNumber(cKycResponseDto.getPanNumber())
                        .pincode(cKycResponseDto.getPincode())
                        .mobile(ObjectUtils.isEmpty(cKycResponseDto.getMobile()) ? "" : cKycResponseDto.getMobile().substring(2))
                        .state(cKycResponseDto.getState())
                        .addressLine1(address1)
                        .addressLine2(address2)
                        .addressLine3(address3)
                        .dob(DateTimeUtil.formatDate(cKycResponseDto.getDob(), "dd/MM/yyyy", "yyyy-MM-dd"))
                        .build()).build();
    }
}
