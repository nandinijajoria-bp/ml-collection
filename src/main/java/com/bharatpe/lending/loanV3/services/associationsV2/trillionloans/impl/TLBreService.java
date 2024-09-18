package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.MerchantAggregateDataDao;
import com.bharatpe.lending.entity.MerchantAggregateData;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLBreRequestDto;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.TLBreCallbackResponseDto;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.TLBreResponseDto;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.io.IOException;
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
    MerchantAggregateDataDao merchantAggregateDataDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    KycUtils kycUtils;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Value("${lender.change.enabled:false}")
    Boolean enableLenderChange;

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

            if (nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                TLBreResponseDto breResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), TLBreResponseDto.class);
                if (breResponseDTO.getStatus().equalsIgnoreCase("INITIATED")) {
                    lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());
                    commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                    log.info("LALD DAO - {}", lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails());
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

    public Boolean processBreCallback(NBFCResponseDTO nbfcResponseDTO) {
        try {
            Thread.sleep(1000); // adding 1 second wait due to issue
            LendingApplication lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponseDTO.getApplicationId())).orElse(null);
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("No application found for applicationId : {}", nbfcResponseDTO.getApplicationId());
                return false;
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || !nbfcResponseDTO.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) {
                log.info("No LendingApplicationLenderDetails found with lender {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            if (!LenderAssociationStages.BRE.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage()) || !LenderAssociationStatus.RISK_IN_PROGRESS.name().equalsIgnoreCase(lendingApplicationLenderDetails.getBreStatus())) {
                log.info("Application not in correct state for BRE callback for applicationId {}", lendingApplication.getId());
                return false;
            }
            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                    .applicationId(lendingApplication.getId())
                    .lendingApplication(lendingApplication)
                    .lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                    .modifyLender(enableLenderChange)
                    .manageState(true)
                    .build();
            if (Objects.nonNull(nbfcResponseDTO.getData()) && nbfcResponseDTO.getSuccess()) {
                TLBreCallbackResponseDto breCallbackResponseDto = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), TLBreCallbackResponseDto.class);
                log.info("BRE callback Response of TrillionLoans for {} {}", nbfcResponseDTO.getApplicationId(), breCallbackResponseDto);
                if (!ObjectUtils.isEmpty(breCallbackResponseDto) && breCallbackResponseDto.getSuccess() && breCallbackResponseDto.getAction().equalsIgnoreCase("Eligible")) {
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_COMPLETED.name());
                    commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                    log.info("LALD DAO - {}", lenderAssociationDetailsRequest.getLendingApplicationLenderDetails());
                    return true;
                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.RISK_FAILED);
        } catch (Exception e) {
            log.error("exception while processing BRE callback of TrillionLoans for  {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lenderAssociationDetailsRequest.getLendingApplicationLenderDetails();
        CKycResponseDto cKycResponseDto = kycUtils.getKycData(lenderAssociationDetailsRequest.getMerchantId());

        try {
            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
            if (ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)) {
                throw new RuntimeException("Lending Risk variable snapshot not found for application id: " + lendingApplication.getId());
            }

            MerchantAggregateData merchantAggregateData = merchantAggregateDataDao.findByMerchantIdAndAggregateId(lendingApplication.getMerchantId(), lendingRiskVariablesSnapshot.getAggregateId());
            if (ObjectUtils.isEmpty(merchantAggregateData)) {
                throw new RuntimeException("Merchant Aggregate Data not found for merchant id: " + lendingApplication.getId() + ", aggregate id : " + lendingRiskVariablesSnapshot.getAggregateId());
            }


            LinkedHashMap<String, Object> identifierMap = new LinkedHashMap<>();
            identifierMap.put("leadId", lendingApplicationLenderDetails.getLeadId());

            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplication.getLender())
                    .productName("LENDING")
                    .payload(TLBreRequestDto.builder()
                            .values(getValues(lendingRiskVariablesSnapshot, lendingApplication, merchantAggregateData, cKycResponseDto))
                            .build())
                    .identifier(identifierMap)
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating BRE payload of TrillionLoans for application {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private TLBreRequestDto.Values getValues(LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot, LendingApplication lendingApplication, MerchantAggregateData merchantAggregateData, CKycResponseDto cKycResponseDto) throws IOException {


        LendingPaymentSchedule lastLoan = lendingPaymentScheduleDao.findTop1ByMerchantIdAndStatusAndCreditLoanOrderByIdDesc(lendingApplication.getMerchantId(), "CLOSED", false);
        LendingPaymentSchedule currentLoan = lendingPaymentScheduleDao.findTop1ByMerchantIdAndStatusAndCreditLoanOrderByIdDesc(lendingApplication.getMerchantId(), "ACTIVE", false);
        Integer maxDpdInLastLoan = loanUtil.getMaxDpdInLastLoan(lendingApplication.getMerchantId(), lastLoan);
        Integer maxDpdInCurrentLoan = loanUtil.getMaxDpdInLastLoan(lendingApplication.getMerchantId(), currentLoan);


        return TLBreRequestDto.Values.builder()
                .input(TLBreRequestDto.Values.Input.builder()
                        .applicationType(merchantAggregateData.getApplicationType())
                        .merchantId(lendingApplication.getMerchantId())
                        .pancard(cKycResponseDto.getPanNumber())
                        .loanSegment(lendingRiskVariablesSnapshot.getLoanSegment())
                        .riskSegment(lendingRiskVariablesSnapshot.getRiskSegment().name())
                        .riskGroup(lendingRiskVariablesSnapshot.getRiskGroup())
                        .businessCategory(lendingApplication.getCategory())
                        .shopStructure(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getShopStructure()) ? "" : lendingRiskVariablesSnapshot.getShopStructure().name())
                        .bureauScore(lendingRiskVariablesSnapshot.getBureauScore())
                        .drs(lendingRiskVariablesSnapshot.getDrsScore())
                        .bbs(lendingRiskVariablesSnapshot.getBbs())
                        .bbs2(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getBbs2()) ? 0 : lendingRiskVariablesSnapshot.getBbs2())
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
                        .pilots(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot.getPilotIdentifier()) ? "" : lendingRiskVariablesSnapshot.getPilotIdentifier())
                        .sources(objectMapper.readTree(merchantAggregateData.getSources()))
                        .scienapticProperties(objectMapper.readTree(merchantAggregateData.getScienapticProperties()))
                        .aggregateId(merchantAggregateData.getAggregateId())
                        .build())
                .build();
    }
}
