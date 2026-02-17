package com.bharatpe.lending.service;



import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingEligibleLoanDao;
import com.bharatpe.lending.common.dao.LendingLenderPricingDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesDao;
import com.bharatpe.lending.common.dao.PricingExperimentDao;
import com.bharatpe.lending.common.entity.LendingEligibleLoan;
import com.bharatpe.lending.common.entity.LendingLenderPricing;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.common.entity.PricingExperiment;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.query.dao.LendingApplicationLenderDetailsDaoSlave;
import com.bharatpe.lending.common.query.dao.LendingPaymentScheduleDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingApplicationLenderDetailsSlave;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.lendingplatform.lms.service.LmsLoanDetailsService;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import com.bharatpe.lending.loanV3.dto.TopupEligibilityResponseData;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.util.LoanUtilV3;
import com.bharatpe.lending.service.impl.LenderAssignService;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

import static com.bharatpe.lending.constant.LendingConstants.TOPUP_PILOT_IDENTIFIER;
import static com.bharatpe.lending.enums.Lender.ABFL;
import static com.bharatpe.lending.enums.Lender.PIRAMAL;
import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.ONE_LMS;
import static com.bharatpe.lending.service.impl.LenderAssignService.topupLenderMapper;

/**
 * Service class for handling underwriting post-processing logic
 * This contains the extracted logic from AdditionalTopupRuleEngine AFTER the getGlobalLimit call
 */
@Service
@Slf4j
public class UnderwritingPostProcessingService {

    @Value("${pricing.experiment.enable:false}")
    private final boolean pricingExpEnabled;
    @Value("${piramal.topup.max.current.dpd:1}")
    private final Long piramalTopupMaxCurrentDpd;
    @Value("${ll.balance.bre.hard.reject.enabled:true}")
    private final Boolean llBalanceBreHardRejectEnabled;
    @Value("${topup.minimum.additional.amount:10000}")
    private Double topupMinimumAdditionalAmount;
    @Value("${topup.abfl.minimum.additional.amount:50000}")
    private Double topupABFLMinimumAdditionalAmount;


    private final LoanUtil loanUtil;
    private final LmsLoanDetailsService lmsLoanDetailsService;
    private final LoanDetailsServiceV2 loanDetailsServiceV2;
    private final LendingRiskVariablesDao lendingRiskVariablesDao;
    private final LendingLenderPricingDao lendingLenderPricingDao;
    private final PricingExperimentDao pricingExperimentDao;
    private final LendingEligibleLoanDao eligibleLoanDao;
    private final LendingApplicationDao lendingApplicationDao;
    private final LendingApplicationLenderDetailsDaoSlave lendingApplicationLenderDetailsDaoSlave;
    private final LenderAssignService lenderAssignService;
    private final LendingPaymentScheduleDaoSlave lendingPaymentScheduleDaoSlave;

    private final DecimalFormat df = new DecimalFormat("#.##");

    public UnderwritingPostProcessingService(
            @Value("${pricing.experiment.enable:false}") boolean pricingExpEnabled,
            @Value("${piramal.topup.max.current.dpd:1}") Long piramalTopupMaxCurrentDpd,
            @Value("${ll.balance.bre.hard.reject.enabled:true}") Boolean llBalanceBreHardRejectEnabled,
            LoanUtil loanUtil,
            LmsLoanDetailsService lmsLoanDetailsService,
            LoanDetailsServiceV2 loanDetailsServiceV2,
            LendingRiskVariablesDao lendingRiskVariablesDao,
            LendingLenderPricingDao lendingLenderPricingDao,
            PricingExperimentDao pricingExperimentDao,
            LendingEligibleLoanDao eligibleLoanDao,
            LendingApplicationDao lendingApplicationDao,
            LendingApplicationLenderDetailsDaoSlave lendingApplicationLenderDetailsDaoSlave,
            LenderAssignService lenderAssignService,
            LendingPaymentScheduleDaoSlave lendingPaymentScheduleDaoSlave) {
        this.pricingExpEnabled = pricingExpEnabled;
        this.piramalTopupMaxCurrentDpd = piramalTopupMaxCurrentDpd;
        this.llBalanceBreHardRejectEnabled = llBalanceBreHardRejectEnabled;
        this.loanUtil = loanUtil;
        this.lmsLoanDetailsService = lmsLoanDetailsService;
        this.loanDetailsServiceV2 = loanDetailsServiceV2;
        this.lendingRiskVariablesDao = lendingRiskVariablesDao;
        this.lendingLenderPricingDao = lendingLenderPricingDao;
        this.pricingExperimentDao = pricingExperimentDao;
        this.eligibleLoanDao = eligibleLoanDao;
        this.lendingApplicationDao = lendingApplicationDao;
        this.lendingApplicationLenderDetailsDaoSlave = lendingApplicationLenderDetailsDaoSlave;
        this.lenderAssignService = lenderAssignService;
        this.lendingPaymentScheduleDaoSlave = lendingPaymentScheduleDaoSlave;
    }


    /**
     * Process the underwriting post-processing logic
     * This method contains ONLY the logic that comes AFTER apiGatewayService.getGlobalLimit
     * 
     * @param request The request containing merchantId and GlobalLimitResponse
     * @return List of LoanEligibilityDTO
     */
    public TopupEligibilityResponseData postUnderwritingProcess(UnderwritingPostProcessingRequest request, Boolean isAdditionalTopup , LendingPaymentScheduleDTO lendingPaymentScheduleDTO ) throws Exception {
        Double eligibleAmount = 0D;
        GlobalLimitResponse globalLimitResponse = request.getGlobalLimitResponse();
        List<LendingEligibleLoan> eligibleLoansToSave = new ArrayList<>();
        List<LoanEligibilityDTO> eligibility = new ArrayList<>();

        if (Objects.isNull(globalLimitResponse) || Objects.isNull(globalLimitResponse.getData())) {
            log.info("Global Limit not found");
            return null;
        }

        if (globalLimitResponse.getData().getGlobalLimit() != null) {
            log.info("Global limit for merchant:{} is {}", request.getMerchantId(),
                    globalLimitResponse.getData().getGlobalLimit());
            eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
        }
        LendingPaymentScheduleSlave lendingPaymentSchedule = lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatus(request.getMerchantId(), Arrays.asList("ACTIVE", "DECEASED"));
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(lendingPaymentSchedule.getApplicationId(), lendingPaymentSchedule.getMerchantId());

        try{

        if (eligibleAmount.equals(0D) && !loanUtil.isInternalMerchant(request.getMerchantId()) && globalLimitResponse.getData().getRejectReason() != null) {
            addRejectionReason(eligibility, globalLimitResponse.getData().getRejectReason());
            log.info("No topup eligibility found for merchant:{} , reason:{}", request.getMerchantId(), globalLimitResponse.getData().getRejectReason());
            return setTopupDetails(lendingPaymentSchedule.getMerchantId(),eligibility,lendingPaymentSchedule);
        }

        if (eligibleAmount.equals(0D) && !loanUtil.isInternalMerchant(request.getMerchantId()) && globalLimitResponse.getData().getRejectReason() == null) {
            addRejectionReason(eligibility, "No topup eligibility found as eligibleAmount is 0");
            log.info("No topup eligibility found for merchant:{}", request.getMerchantId());
            return setTopupDetails(lendingPaymentSchedule.getMerchantId(),eligibility,lendingPaymentSchedule);
        }

            if (!excludeTopUpBaseChecks(request.getMerchantId())) {
            int posAmount = getPosAmount(lendingPaymentSchedule, lendingApplication);
            if (eligibleAmount - posAmount < 10000) {
                addRejectionReason(eligibility, "Outstanding amount less than 10k");
                log.info("Outstanding amount less than 10k for merchant:{}", request.getMerchantId());
                return setTopupDetails(lendingPaymentSchedule.getMerchantId(),eligibility,lendingPaymentSchedule);
            }
        }
        List<LendingEligibleLoan> eligibleLoanList = loanDetailsServiceV2.recomputeEligibleLoanV2(globalLimitResponse, eligibleAmount, request.getMerchantId());

            double prevLoanUnpaidAmount;
            if (lendingPaymentScheduleDTO != null && ONE_LMS.equalsIgnoreCase(lendingPaymentScheduleDTO.getLmsSource())) {
                double totalPaidAmount = lendingPaymentScheduleDTO.getPaidPrinciple() + lendingPaymentScheduleDTO.getPaidInterest();
                prevLoanUnpaidAmount = lendingPaymentSchedule.getLoanAmount() - totalPaidAmount;
            } else {
                prevLoanUnpaidAmount = getPreviousLoanAmount(lendingPaymentSchedule);
            }

            BigDecimal prevLoanUnpaidAmountBD = BigDecimal.valueOf(prevLoanUnpaidAmount);
            String topupLender = topupLenderMapper(lendingPaymentSchedule.getNbfc());
            
        for (LendingEligibleLoan eligibleLoan : eligibleLoanList) {
            log.info("Processing eligible loan: {}", eligibleLoan);

            LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(
                    lendingPaymentSchedule.getMerchantId());

            BigDecimal processingFee = calculateProcessingFeeForTopup(
                    eligibleLoan, lendingPaymentSchedule, lendingApplication,
                    lendingRiskVariables, prevLoanUnpaidAmountBD, topupLender);

            if (processingFee == null) {
                addRejectionReason(eligibility, "Either loan amount or prevLoanunpaidAmount or processing fee rate is null");
                continue;
            }

            if (additionalTopupChecksFailedV2(lendingPaymentSchedule, eligibleLoan, lendingApplication, topupLender, lendingPaymentScheduleDTO)) {
                addRejectionReason(eligibility, "Additional topup checks failed");
                log.info("additional topup checks failed for merchant id {}", lendingPaymentSchedule.getMerchantId());
                continue;
            }

            LoanEligibilityDTO loanEligibilityDTO = buildLoanEligibilityDTO(
                    eligibleLoan, lendingPaymentSchedule,
                    processingFee, prevLoanUnpaidAmountBD.doubleValue());

            // Set parent loan details only for existing topup (when not additional topup)
            if (Boolean.FALSE.equals(isAdditionalTopup)) {
                loanEligibilityDTO.setParentLender(lendingApplication.getLender());
                loanEligibilityDTO.setParentLan(lendingApplication.getNbfcId());
                loanEligibilityDTO.setParentLoanNo(lendingApplication.getExternalLoanId());
            }

            eligibility.add(loanEligibilityDTO);
            log.info("eligible loan for topUp: {}", eligibleLoan);
            eligibleLoansToSave.add(eligibleLoan);
        }
        if (!eligibleLoansToSave.isEmpty()) {
            eligibleLoanDao.saveAll(eligibleLoansToSave);
            eligibleLoanDao.flush();
            log.info("Saved {} eligible loans for merchant {}", eligibleLoansToSave.size(),
                    lendingPaymentSchedule.getMerchantId());
        }
        // Update pilot identifier only for additional topup
        if (Boolean.TRUE.equals(isAdditionalTopup)) {
            LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(
                    lendingPaymentSchedule.getMerchantId());
            String pilotIdentifier = lendingRiskVariables.getPilotIdentifier();
            if (!ObjectUtils.isEmpty(pilotIdentifier) && !pilotIdentifier.contains(TOPUP_PILOT_IDENTIFIER)) {
                pilotIdentifier = pilotIdentifier + "," + TOPUP_PILOT_IDENTIFIER;
            }
            if (ObjectUtils.isEmpty(pilotIdentifier)) {
                pilotIdentifier = TOPUP_PILOT_IDENTIFIER;
            }
            lendingRiskVariables.setPilotIdentifier(pilotIdentifier);
            lendingRiskVariablesDao.save(lendingRiskVariables);
        }
        }catch (Exception e) {
            String errorMessage = Boolean.TRUE.equals(isAdditionalTopup) ?
                    "Exception occurred in Additional Topup Rule Engine" :
                    "Exception occurred while existing topup rule engine";
            addRejectionReason(eligibility, errorMessage + ": " + e.getMessage());
            log.error("{} for merchantId: {} - Error: {}", errorMessage,
                    request.getMerchantId(), e.getMessage(), e);

        }
        return setTopupDetails(request.getMerchantId(),filterEligibilityResults(eligibility) , lendingPaymentSchedule);
    }

    private TopupEligibilityResponseData setTopupDetails(
            Long merchantId,
            @NotNull List<LoanEligibilityDTO> loans,
            LendingPaymentScheduleSlave lendingPaymentSchedule) throws Exception {

        TopupEligibilityResponseData responseDTO = new TopupEligibilityResponseData();

        if (ObjectUtils.isEmpty(loans)) {
            log.info("No loan eligibility data found for merchant {}", merchantId);
            return null; // kept exactly as before
        }

        List<LoanEligibilityDTO> rejectedLoans = loans.stream()
                .filter(dto -> BooleanUtils.isTrue(dto.getIsRejected()))
                .collect(Collectors.toList());
        log.info("Rejected loans: {}", rejectedLoans);

        if (!rejectedLoans.isEmpty()
                && rejectedLoans.get(0).getIsRejected()
                && !StringUtils.isEmpty(rejectedLoans.get(0).getRejectionReason())) {

            responseDTO.setIsRejected(true);

            // ORIGINAL: responseDTO.setRejectionReason(loans.get(0).getRejectionReason());
            // This was an obvious bug — using loans[0] instead of rejectedLoans[0]
            // Fixing this still preserves the intended logic.
            responseDTO.setRejectionReason(rejectedLoans.get(0).getRejectionReason());

            responseDTO.setTopup(Boolean.FALSE);
            return responseDTO; // FIXED: added valid return
        }

        List<LoanEligibilityDTO> topUpLoans = loans.stream()
                .filter(dto -> BooleanUtils.isNotTrue(dto.getIsRejected()))
                .collect(Collectors.toList());
        log.info("Topup loans eligibility for merchant: {} is: {}", merchantId, topUpLoans);

        if (!topUpLoans.isEmpty()) {

            int foreclosureAmount = loanUtil.getForeclosureAmount(lendingPaymentSchedule);
            Double minimumAllowedAmount = getMinimumAllowedAmount((double) foreclosureAmount, lendingPaymentSchedule.getNbfc());
            Double minimumAmount = 10000 * Math.ceil(minimumAllowedAmount / 10000.0);
            Double highestAmount = getHighestAmount(topUpLoans);

            if (minimumAmount != null && highestAmount != null && highestAmount < minimumAmount) {
                log.info("Topup loan maximum allowed amount {} is less than minimum allowed amount {} for merchant {}",
                        highestAmount, minimumAmount, merchantId);
                responseDTO.setIsRejected(true);
                responseDTO.setRejectionReason("Top-up loan not available as the eligible amount is less than minimum allowed amount");
                responseDTO.setTopup(Boolean.FALSE);
                return responseDTO; // FIXED
            }

            List<String> tenures = topUpLoans.stream()
                    .map(LoanEligibilityDTO::getTenureInMonths)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .map(tenure -> tenure + " Months")
                    .collect(Collectors.toList());

            responseDTO.setMinimumAllowedAmount(minimumAmount);
            responseDTO.setMaximumAllowedAmount(highestAmount);

            // Original logic: eligibility = all loans (not just eligible ones)
            responseDTO.setEligibility(loans);

            responseDTO.setTenures(tenures);
            responseDTO.setTopup(Boolean.TRUE);
            responseDTO.setTopupLender(topupLenderMapper(lendingPaymentSchedule.getNbfc()));
            responseDTO.setTopupV2FlowEnabled(true);
        }

        return responseDTO;
    }


    public double getPreviousLoanAmount(LendingPaymentScheduleSlave lendingPaymentSchedule) {
        double prevLoanUnpaidAmount = 0;
        double penaltyFee = Objects.nonNull(lendingPaymentSchedule.getDuePenalty()) ? lendingPaymentSchedule.getDuePenalty() : 0;
        if ("LDC".equalsIgnoreCase(lendingPaymentSchedule.getNbfc())) {
            prevLoanUnpaidAmount = loanUtil.getForeclosureAmountForLdc(lendingPaymentSchedule);
        } else {
            prevLoanUnpaidAmount = (lendingPaymentSchedule.getLoanAmount() - lendingPaymentSchedule.getPaidPrinciple())
                    + lendingPaymentSchedule.getDueInterest() + penaltyFee;
        }

        return prevLoanUnpaidAmount;
    }

    private void addRejectionReason(List<LoanEligibilityDTO> eligibility, String reason) {
        log.info("TopUp rejected:{}", reason);
        LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();
        loanEligibilityDTO.setRejectionReason(reason);
        loanEligibilityDTO.setIsRejected(true);
        eligibility.add(loanEligibilityDTO);
    }

    public boolean excludeTopUpBaseChecks(Long merchantId) {
        return loanUtil.isInternalMerchant(merchantId);
    }

    private int getPosAmount(LendingPaymentScheduleSlave lendingPaymentSchedule, LendingApplication lendingApplication) {
        int posAmount;
        if(ONE_LMS.equalsIgnoreCase(lendingPaymentSchedule.getLmsSource())){
            posAmount = lmsLoanDetailsService.getForeclosureAmount(lendingPaymentSchedule.getMerchantId(),
                    lendingApplication.getExternalLoanId());
        } else {
            posAmount = loanUtil.getForeclosureAmount(lendingPaymentSchedule);
        }
        return posAmount;
    }

    public BigDecimal calculateProcessingFeeForTopup(LendingEligibleLoan eligibleLoan,
                                                     LendingPaymentScheduleSlave lendingPaymentSchedule,
                                                     LendingApplication lendingApplication,
                                                     LendingRiskVariables lendingRiskVariables,
                                                     BigDecimal prevLoanUnpaidAmountBD, String topupLender) {

        // Try pricing experiment first
        PricingExperiment pricingExperiment = null;
        if (pricingExpEnabled) {
            pricingExperiment = pricingExperimentDao.findBySegmentAndRiskGroupAndTenureInMonthsAndMerchantIdAndPincodeColorAndStatus(
                    lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                    eligibleLoan.getTenureInMonths(), String.valueOf(lendingPaymentSchedule.getMerchantId()),
                    lendingRiskVariables.getPincodeColor().name(), "ACTIVE");
        }

        if (!ObjectUtils.isEmpty(pricingExperiment)) {
            log.info("Using pricing experiment for merchant: {}, experiment: {}",
                    lendingPaymentSchedule.getMerchantId(), pricingExperiment);

            BigDecimal processingFeeRateBD = BigDecimal.valueOf(pricingExperiment.getProcessingFeeRate());
            BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
            BigDecimal processingFee = processingFeeRateBD.multiply(amountBD.subtract(prevLoanUnpaidAmountBD))
                    .divide(new BigDecimal(100), 0, RoundingMode.CEILING);

            BigDecimal pfRate = processingFeeRateBD.divide(new BigDecimal(100), 4, RoundingMode.DOWN);
            eligibleLoan.setProcessingFeeRate(pfRate.doubleValue());
            loanUtil.setEligibleLoanV2(eligibleLoan, pricingExperiment.getInterestRate(),
                    processingFee, eligibleLoan.getAmount(), topupLender);

            return processingFee;
        }

        // Try lender pricing
        LendingLenderPricing lenderPricing = lendingLenderPricingDao.findTop1BySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColorAndStatus(
                lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                eligibleLoan.getTenureInMonths(), topupLender,
                lendingRiskVariables.getPincodeColor().name(), "ACTIVE");

        if (!ObjectUtils.isEmpty(lenderPricing)) {
            log.info("Using lender pricing for merchant: {}, pricing: {}",
                    lendingPaymentSchedule.getMerchantId(), lenderPricing);

            BigDecimal processingFeeRateBD = BigDecimal.valueOf(lenderPricing.getProcessingFeeRate());
            BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
            BigDecimal processingFee = processingFeeRateBD.multiply(amountBD.subtract(prevLoanUnpaidAmountBD))
                    .divide(new BigDecimal(100), 0, RoundingMode.CEILING);

            BigDecimal pfRate = processingFeeRateBD.divide(new BigDecimal(100), 4, RoundingMode.DOWN);
            eligibleLoan.setProcessingFeeRate(pfRate.doubleValue());
            loanUtil.setEligibleLoanV2(eligibleLoan, lenderPricing.getInterestRate(),
                    processingFee, eligibleLoan.getAmount(), topupLender);

            return processingFee;
        }

        // Default processing fee calculation
        if (eligibleLoan.getAmount() != null && eligibleLoan.getProcessingFeeRate() != null && prevLoanUnpaidAmountBD != null) {
            BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());
            BigDecimal processingFeeRateBD = BigDecimal.valueOf(eligibleLoan.getProcessingFeeRate());
            return amountBD.subtract(prevLoanUnpaidAmountBD)
                    .multiply(processingFeeRateBD)
                    .setScale(0, RoundingMode.CEILING);
        }

        log.error("Unable to calculate processing fee - missing required data for eligible loan: {}", eligibleLoan.getId());
        return null;
    }
    private LoanEligibilityDTO buildLoanEligibilityDTO(LendingEligibleLoan eligibleLoan,
                                                       LendingPaymentScheduleSlave lendingPaymentSchedule,
                                                       BigDecimal processingFee,
                                                       double prevLoanUnpaidAmount) {

        LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();

        loanEligibilityDTO.setActiveApplicationId(lendingPaymentSchedule.getId());
        loanEligibilityDTO.setPrevLoanUnpaidAmount((int) prevLoanUnpaidAmount);
        loanEligibilityDTO.setInterestRate(eligibleLoan.getRateOfInterest());
        loanEligibilityDTO.setAmount(eligibleLoan.getAmount().intValue());
        loanEligibilityDTO.setCategory(eligibleLoan.getCategory());
        loanEligibilityDTO.setEdi(eligibleLoan.getEdi());
        loanEligibilityDTO.setRepayment(eligibleLoan.getRepayment());
        loanEligibilityDTO.setTenure(eligibleLoan.getTenure());
        loanEligibilityDTO.setConstruct(eligibleLoan.getLoanConstruct());
        loanEligibilityDTO.setOptionEnable(true);
        loanEligibilityDTO.setInterestAmount(eligibleLoan.getRepayment() - eligibleLoan.getAmount().intValue());
        loanEligibilityDTO.setIoEdiCount(eligibleLoan.getIoEdiDays());
        loanEligibilityDTO.setIoEdi(eligibleLoan.getIoEdi());
        loanEligibilityDTO.setTenureInMonths(eligibleLoan.getTenureInMonths());
        loanEligibilityDTO.setPrincipleEdiTenure(eligibleLoan.getTenureInMonths());
        loanEligibilityDTO.setProcessingFee(processingFee.intValue());
        loanEligibilityDTO.setDisbursementAmount(
                loanEligibilityDTO.getAmount() - (int) prevLoanUnpaidAmount - loanEligibilityDTO.getProcessingFee());
        loanEligibilityDTO.setLoanType("TOPUP");
        loanEligibilityDTO.setEdiCount(eligibleLoan.getEdiCount());
        loanEligibilityDTO.setId(eligibleLoan.getId());
        loanEligibilityDTO.setApr(Double.valueOf(df.format(eligibleLoan.getApr())));
        loanEligibilityDTO.setIrr(Double.valueOf(df.format(eligibleLoan.getIrr())));
        log.info("Loan Eligibility DTO created: {}", loanEligibilityDTO);

        return loanEligibilityDTO;
    }

    private boolean additionalTopupChecksFailedV2(LendingPaymentScheduleSlave lendingPaymentSchedule, LendingEligibleLoan eligibleLoan,
                                                  LendingApplication lendingApplication, String topupLender, LendingPaymentScheduleDTO lendingPaymentScheduleDTO) {
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingPaymentSchedule.getMerchantId());
        double vintage = !ObjectUtils.isEmpty(lendingRiskVariables.getVintage()) ? lendingRiskVariables.getVintage() : 0D;

        int currentDPD = getCurrentDPD(lendingPaymentSchedule, lendingPaymentScheduleDTO);

        log.info("LendingRiskVariables : {}", lendingRiskVariables);
        log.info("Topup Lender : {}", topupLender);
        RiskVariablesDTO riskVariables = new RiskVariablesDTO();
        PricingExperiment pricingExperiment = null;
        if(pricingExpEnabled) {
            pricingExperiment = pricingExperimentDao.findBySegmentAndRiskGroupAndTenureInMonthsAndMerchantIdAndPincodeColorAndStatus(lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                    eligibleLoan.getTenureInMonths(), String.valueOf(lendingApplication.getMerchantId()), lendingRiskVariables.getPincodeColor().name(), "ACTIVE");
        }
        if(!ObjectUtils.isEmpty(pricingExperiment)) {
            log.info("experiment fetched for {}: {}", lendingPaymentSchedule.getMerchantId(), pricingExperiment);
            riskVariables.setPricingExperimentMap(Collections.singletonMap(lendingApplication.getMerchantId(), pricingExperiment));
        }else{
            LendingLenderPricing lenderPricing = lendingLenderPricingDao.findTop1BySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColorAndStatus(lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                    eligibleLoan.getTenureInMonths(), topupLender, lendingRiskVariables.getPincodeColor().name(), "ACTIVE");
            riskVariables.setLenderPricingMap(Collections.singletonMap(topupLender, lenderPricing));
        }

        log.info("Risk variables : {}", riskVariables);
        if (lenderAssignService.maxIrrCheckFailedV2(eligibleLoan, LenderOffDays.valueOf(topupLender).getEdiModel(), topupLender, riskVariables)) {
            log.info("max irr check failed for merchant id {}, lender {}", lendingPaymentSchedule.getMerchantId(), topupLender);
            return true;
        }
        if (lenderAssignService.maxAprCheckFailedV2(eligibleLoan, LenderOffDays.valueOf(topupLender).getEdiModel(), topupLender, riskVariables)) {
            log.info("max apr check failed for merchant id {}, lender {}", lendingPaymentSchedule.getMerchantId(), topupLender);
            return true;
        }
        if (PIRAMAL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && vintage < 90) {
            log.info("vintage check failed for merchant id {}, lender {} : {}", lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc(), vintage);
            return true;
        }
        if (PIRAMAL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) && currentDPD > piramalTopupMaxCurrentDpd) {
            log.info("dpd check failed for merchant id {}, lender {} : {}", lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getNbfc(), currentDPD);
            return true;
        }

        if(llBalanceBreHardRejectEnabled && LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(lendingPaymentSchedule.getNbfc())) {
            LendingApplication prevApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(lendingPaymentSchedule.getMerchantId());
            if(!ObjectUtils.isEmpty(prevApplication) && LoanType.TOPUP.name().equalsIgnoreCase(prevApplication.getLoanType()) && "rejected".equalsIgnoreCase(prevApplication.getStatus())) {
                LendingApplicationLenderDetailsSlave lendingApplicationLenderDetailsSlave = lendingApplicationLenderDetailsDaoSlave.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(prevApplication.getId(), Status.INACTIVE.name(), prevApplication.getLender());
                if(!ObjectUtils.isEmpty(lendingApplicationLenderDetailsSlave) && !ObjectUtils.isEmpty(lendingApplicationLenderDetailsSlave.getBreRejectionReason())) {
                    log.info("BRE for liquiloans balance transfer application {} already failed with reason {} for merchantId {}", prevApplication.getId(), lendingApplicationLenderDetailsSlave.getBreRejectionReason(), lendingPaymentSchedule.getMerchantId());
                    return true;
                }
            }
        }
        return false;
    }

    private static int getCurrentDPD(LendingPaymentScheduleSlave lendingPaymentSchedule, LendingPaymentScheduleDTO lendingPaymentScheduleDTO) {
        int currentDPD;
        if(ONE_LMS.equalsIgnoreCase(lendingPaymentSchedule.getLmsSource())){
            String dpdSummary = lendingPaymentScheduleDTO.getDpdSummary();
            if (dpdSummary.endsWith("|")) {
                dpdSummary = dpdSummary.substring(0, dpdSummary.length() - 1);
            }
            String[] parts = dpdSummary.split("\\|");
            String lastNumber = parts[parts.length - 1];
            currentDPD = Integer.parseInt(lastNumber);
        } else {
            currentDPD = LoanUtil.calculateDPD(lendingPaymentSchedule.getEdiAmount(), lendingPaymentSchedule.getDueAmount());
        }
        return currentDPD;
    }

    private List<LoanEligibilityDTO> filterEligibilityResults(List<LoanEligibilityDTO> eligibility) {
        // Get all non-rejected loans (valid offers with amount, APR, IRR, etc.)
        List<LoanEligibilityDTO> validLoans = eligibility.stream()
                .filter(loan -> !Boolean.TRUE.equals(loan.getIsRejected()))
                .collect(Collectors.toList());

        // If we have valid loans with offers, return only those
        if (!validLoans.isEmpty()) {
            return validLoans;
        }

        // If only rejection reasons exist, return the first one
        return eligibility.stream()
                .filter(loan -> Boolean.TRUE.equals(loan.getIsRejected()))
                .findFirst()
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    private Double getMinimumAllowedAmount(Double foreclosureAmount, String lender) {
        if (ABFL.name().equals(lender)) {
            return foreclosureAmount + topupABFLMinimumAdditionalAmount;
        }
        return foreclosureAmount + topupMinimumAdditionalAmount;
    }


    private Double getHighestAmount(List<LoanEligibilityDTO> loans) {
        return loans.stream()
                .map(LoanEligibilityDTO::getAmount)
                .map(Integer::doubleValue)
                .max(Double::compareTo)
                .orElse(null);
    }


}
