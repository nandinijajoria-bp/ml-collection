package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesSnapshotDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.common.entity.LendingRiskVariablesSnapshot;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.enums.TlBreExceptionEnum;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.MerchantAggregateDataDao;
import com.bharatpe.lending.entity.MerchantAggregateData;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLBreRequestDto;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.TLBreCallbackResponseDto;
import com.bharatpe.lending.loanV3.dto.response.trillionloans.TLBreResponseDto;
import com.bharatpe.lending.loanV3.revamp.util.LoanUtilV3;
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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.bharatpe.lending.common.enums.TlBreExceptionEnum.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    LendingRiskVariablesDao lendingRiskVariablesDao;

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

    @Value("${trillion.bre.retry.intervals:}")
    List<Integer> trillionBreRetryIntervals;

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static int attempt = 0;

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
            if (LoanType.TOPUP.name().equalsIgnoreCase(lenderAssociationDetailsRequestDto.getLendingApplication().getLoanType())) {
                LendingApplication parentApplication = loanUtil.fetchParentApplication(lenderAssociationDetailsRequestDto.getLendingApplication().getId());
                lenderAssociationDetailsRequestDto.setTopupParentLender(parentApplication.getLender());
            }
            if (executeBre(breRequest, lenderAssociationDetailsRequestDto)) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                log.info("Updated LendingApplicationLenderDetails: {}", lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails());
                return true;
            }
            if (!ObjectUtils.isEmpty(lenderAssociationDetailsRequestDto.getTopupParentLender()) && LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lenderAssociationDetailsRequestDto.getTopupParentLender())) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.BRE_RETRY.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                scheduleBreExecution(breRequest, lenderAssociationDetailsRequestDto);
                return false;
            }
        } catch (Exception e) {
            log.error("error while invoking Bre of TrillionLoans for  {} {} {}", lenderAssociationDetailsRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
        lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreRejectionReason(INTERNAL_ERROR.name());
        log.info("invoke bre failed for TrillionLoans for applicationId : {} due to : {}", lenderAssociationDetailsRequestDto.getApplicationId(), lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getBreRejectionReason());
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
            if (!LenderAssociationStages.BRE.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage()) || !Arrays.asList(LenderAssociationStatus.RISK_IN_PROGRESS.name(), LenderAssociationStatus.BRE_RETRY.name()).contains(lendingApplicationLenderDetails.getBreStatus())) {
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
            TLBreCallbackResponseDto breCallbackResponseDto = null;
            if (Objects.nonNull(nbfcResponseDTO.getData()) && nbfcResponseDTO.getSuccess()) {
                breCallbackResponseDto = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), TLBreCallbackResponseDto.class);
                log.info("BRE callback Response of TrillionLoans for {} {}", nbfcResponseDTO.getApplicationId(), breCallbackResponseDto);
                if (!ObjectUtils.isEmpty(breCallbackResponseDto) && breCallbackResponseDto.getSuccess() && breCallbackResponseDto.getAction().equalsIgnoreCase("Eligible")) {
                    if(!ObjectUtils.isEmpty(breCallbackResponseDto.getLimit()) && Double.parseDouble(breCallbackResponseDto.getLimit()) < lendingApplication.getLoanAmount()){
                        log.info("offer downgraded for application:{}", lendingApplication.getId());
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setNbfcApprovedLoanOfferAmt(Double.parseDouble(breCallbackResponseDto.getLimit()));
                        LendingApplication lendingApplication1 = commonService.createDuplicateApplication(lendingApplication, lenderAssociationDetailsRequest.getLendingApplicationLenderDetails());
                        boolean shouldLenderNotBeConsidered = commonService.additionalLenderDowngradeChecksFailed(lendingApplication1, "TRILLIONLOANS");
                        if(shouldLenderNotBeConsidered){
                            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
                            commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsRequest, LenderAssociationStatus.RISK_FAILED);
                            return false;
                        } else {
                            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setDrawDownStatus(LenderAssociationStatus.OFFER_MODIFICATION_PENDING.name());
                            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setStage(LenderAssociationStages.COMPLETED.name());
                            commonService.manageApplicationState(lenderAssociationDetailsRequest);
                            return true;
                        }
                    }
                    lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_PENDING.name());
                    commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                    log.info("LALD DAO - {}", lenderAssociationDetailsRequest.getLendingApplicationLenderDetails());
                    return true;
                }
            } else if (Objects.nonNull(nbfcResponseDTO.getData())) {
                breCallbackResponseDto = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), TLBreCallbackResponseDto.class);
                if (!ObjectUtils.isEmpty(breCallbackResponseDto) && !ObjectUtils.isEmpty(breCallbackResponseDto.getReasons())) {
                    String rejectionReason = Arrays.stream(breCallbackResponseDto.getReasons())
                            .map(Object::toString)
                            .collect(Collectors.joining(","));
                    Arrays.stream(TlBreExceptionEnum.values())
                            .filter(exceptionEnum -> exceptionEnum.getCompleteReason(exceptionEnum).equals(rejectionReason))
                            .findFirst()
                            .ifPresent(exceptionEnum -> lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreRejectionReason(exceptionEnum.name()));
                    if (ObjectUtils.isEmpty(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getBreRejectionReason())) {
                        if (!ObjectUtils.isEmpty(rejectionReason)) {
                            log.error("Unhandled rejection reason received for BRE for TrillionLoans for applicationId {} : {}", nbfcResponseDTO.getApplicationId(), rejectionReason);
                            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreRejectionReason(UNIDENTIFIED_REASON.name());
                        }
                    }
                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
            if (ObjectUtils.isEmpty(lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getBreRejectionReason())) {
                lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setBreRejectionReason(EMPTY_RESPONSE.name());
            }
            log.info("bre call back failed for TrillionLoans for applicationId : {} due to : {}", nbfcResponseDTO.getApplicationId(), lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().getBreRejectionReason());
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
            LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingApplication.getMerchantId());
            if (ObjectUtils.isEmpty(lendingRiskVariablesSnapshot) || ObjectUtils.isEmpty(lendingRiskVariables)) {
                throw new RuntimeException("Lending Risk variable and/or snapshot not found for application id: " + lendingApplication.getId() +", merchant id: " + lendingApplication.getMerchantId());
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
                            .values(getValues(lendingRiskVariables, lendingRiskVariablesSnapshot, lendingApplication, merchantAggregateData, cKycResponseDto))
                            .build())
                    .identifier(identifierMap)
                    .build();
        } catch (Exception e) {
            log.info("Exception in creating BRE payload of TrillionLoans for application {} {} {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private TLBreRequestDto.Values getValues(LendingRiskVariables lendingRiskVariables, LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot, LendingApplication lendingApplication, MerchantAggregateData merchantAggregateData, CKycResponseDto cKycResponseDto) throws IOException {


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
                        .eligCompDate(lendingRiskVariables.getUpdatedAt().toString())
                        .build())
                .build();
    }

    private boolean executeBre(NBFCRequestDTO breRequest, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        try {
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(breRequest, LenderAssociationStages.BRE);
            log.info("BRE response from NBFC: {} with applicationId: {}", nbfcResponseDto, lenderAssociationDetailsRequestDto.getApplicationId());
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                TLBreResponseDto breResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), TLBreResponseDto.class);
                if ("INITIATED".equalsIgnoreCase(breResponseDTO.getStatus())) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Exception executing BRE of {} for applicationId: {} {}", lenderAssociationDetailsRequestDto.getLendingApplication().getLender(), lenderAssociationDetailsRequestDto.getLendingApplication().getId(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private void scheduleBreExecution(NBFCRequestDTO breRequest, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto) {
        if (attempt >= trillionBreRetryIntervals.size()) {
            log.info("Max retries reached for BRE invocation. Stopping further attempts.");
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_FAILED.name());
            commonService.manageApplicationStateAndRejectApplication(lenderAssociationDetailsRequestDto);
            scheduler.shutdown();
            return;
        }
        int delay = trillionBreRetryIntervals.get(attempt);
        scheduler.schedule(() -> {
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findByApplicationIdAndLender(lenderAssociationDetailsRequestDto.getLendingApplication().getId(), lenderAssociationDetailsRequestDto.getLendingApplication().getLender());
            if(!ObjectUtils.isEmpty(lendingApplicationLenderDetails) && !LenderAssociationStatus.BRE_RETRY.name().equalsIgnoreCase(lendingApplicationLenderDetails.getBreStatus())) {
                log.info("BRE invocation of {} already completed for applicationId {}", lendingApplicationLenderDetails.getLender(), lendingApplicationLenderDetails.getApplicationId());
                scheduler.shutdown();
                return;
            }
            boolean success = executeBre(breRequest, lenderAssociationDetailsRequestDto);
            if (success) {
                lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.RISK_IN_PROGRESS.name());
                commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
                log.info("BRE initiated successfully. Stopping retries.");
                scheduler.shutdown();
                return;
            }

            attempt++;
            lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().setBreStatus(LenderAssociationStatus.BRE_RETRY.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequestDto);
            scheduleBreExecution(breRequest, lenderAssociationDetailsRequestDto);
        }, delay, TimeUnit.SECONDS);
    }
}
