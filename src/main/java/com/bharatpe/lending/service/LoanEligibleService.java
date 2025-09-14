package com.bharatpe.lending.service;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.LendingPancardDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.Loan;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.MaxPricingValuesDTO;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.entity.EligibleLoanAudit;
import com.bharatpe.lending.common.enums.EdiModel;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.common.entity.LenderMetricsHistory;
import com.bharatpe.lending.common.enums.*;
import org.springframework.http.ResponseEntity;
import com.bharatpe.lending.common.query.dao.ForeClosureConfigDao;
import com.bharatpe.lending.common.query.dao.LendingLedgerSlaveDao;
import com.bharatpe.lending.common.query.dao.LendingPaymentScheduleDaoSlave;
import com.bharatpe.lending.common.query.dao.PenaltyFeeConfigDaoSlave;
import com.bharatpe.lending.common.query.entity.ForeClosureConfig;
import com.bharatpe.lending.common.query.entity.LendingLedgerSlave;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.query.entity.PenaltyFeeConfigSlave;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.PincodeCityStateMappingDTO;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.query.dao.ExternalGatewayDaoSlave;
import com.bharatpe.lending.common.query.entity.ExternalGatewaySlave;
import com.bharatpe.lending.common.util.AesEncryptionUtil;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.entity.*;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.enums.EligibilityRequestSource;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.exception.BureauCallMaskedApiException;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.handlers.MerchantSummaryExceptionHandler;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.dto.BureauResponseDTO;
import com.bharatpe.lending.loanV2.handlers.BureauHandler;
import com.bharatpe.lending.loanV2.dto.LoanDetailsResponse;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import com.bharatpe.lending.service.impl.LenderAssignService;
import com.bharatpe.lending.loanV3.config.*;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.utils.OfferUtils;
import com.bharatpe.lending.util.*;
import com.bharatpe.lending.util.creditresponse.CrifResponseUtil;
import com.bharatpe.lending.util.creditresponse.ExperianResponseUtil;
import com.bharatpe.lending.util.creditresponse.ResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.bharatpe.lending.common.enums.RiskSegment.REGULAR_ETC;
import static com.bharatpe.lending.constant.LendingConstants.*;
import static com.bharatpe.lending.constant.LendingConstants.NEGATIVE_BUSINESS_CATEGORY_REJECTION;
import static com.bharatpe.lending.enums.Lender.*;

@Service
public class LoanEligibleService {

    List<Long> exemptMerchant = Arrays.asList(2411647L, 1210933L, 4340760L, 2097359L, 7090157L, 6518986L, 1141505L, 3L, 3543643L, 9319451L, 8891247L, 2078363L);

    private final Logger logger = LoggerFactory.getLogger(LoanEligibleService.class);

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LmsFieldValuesDao lmsFieldValuesDao;

    @Autowired
    LenderBusinessCategoryDao lenderBusinessCategoryDao;

    @Autowired
    FunnelService funnelService;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    LendingLedgerSlaveDao lendingLedgerSlaveDao;

    @Autowired
    LendingCache lendingCache;

    @Autowired
    LendingEligibleLoanDao eligibleLoanDao;

    @Autowired
    LendingPancardDao lendingPancardDao;

    @Autowired
    ExternalGatewayDaoSlave externalGatewayDaoSlave;

    @Autowired
    AesEncryptionUtil aesEncryptionUtil;

    @Autowired
    private PayUConfig payUConfig;

    @Autowired
    LendingHmacCalculator lendingHmacCalculator;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LenderAssignmentRulesDao lenderAssignmentRulesDao;

    @Autowired
    LendingEligibleLoanAuditDao eligibleLoanAuditDao;

    @Autowired
    ExperianService experianService;

    @Autowired
    SmfgConfig smfgConfig;

    @Autowired
    LenderRankingEngine lenderRankingEngine;

    @Autowired
    PricingExperimentDao pricingExperimentDao;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LendingAuditTrialDao lendingAuditTrialDao;

    @Autowired
    LendingMerchantDropoffDao lendingMerchantDropoffDao;

    @Autowired
    LoanDetailsServiceV2 loanDetailsServiceV2;

    @Autowired
    LendingPaymentScheduleDaoSlave lendingPaymentScheduleDaoSlave;

    @Value("${pricing.experiment.enable:false}")
    boolean pricingExpEnabled;

    @Autowired
    DateTimeUtil dateTimeUtil;

    @Autowired
    MerchantSummaryHandler merchantSummaryHandler;

    @Autowired
    BureauHandler bureauHandler;

    @Autowired
    LendingMetricHistoryCustomDao lenderMetricsHistoryDao;

    @Autowired
    OfferRankingConfigDao offerRankingConfigDao;

    @Value("${eligibility.refresh.window:1}")
    int eligibilityRefreshWindow;

    @Value("${lender.eligible.pincode.check:PIRAMAL}")
    List<String> lenderEligiblePincodeCheckList;

    @Value("${new.eligibility.refresh.window:1}")
    int newEligibilityRefreshWindow;

    @Value("${inital.lenders.limit:3}")
    int initalLendersLimit;

    @Value("${fallback.lenders.limit:6}")
    int fallbackLendersLimit;

    @Value("${new.eligibility.refresh.window.rollout.percent:0}")
    Integer newEligibilityRefreshWindowRolloutPercent;

    @Value("${piramal.rollout.percent:1}")
    Integer piramalRolloutPercentage;

    @Value("${aadhaar.seeding.status.check.lenders:}")
    String aadhaarSeedingStatusCheckLenders;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Autowired
    MerchantService merchantService;

    @Autowired
    KycHandler kycHandler;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    MerchantLoansService merchantLoansService;

    @Autowired
    @Lazy
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    LendingPancardDetailsDao lendingPancardDetailsDao;

    @Autowired
    LendingLenderPricingDao lendingLenderPricingDao;

    @Autowired
    LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    LenderEligiblePincodesDao lenderEligiblePincodesDao;

    @Autowired
    EdiUtil ediUtil;

    @Value("${max.pf.eligible.lenders:}")
    String maxPfEligibleLender;

    @Autowired
    LenderDisbursalLimitsDao lenderDisbursalLimitsDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    PenaltyFeeConfigDaoSlave penaltyFeeConfigDaoSlave;

    @Autowired
    ForeClosureConfigDao foreClosureDao;

    @Value("${usfb.rollout.percent:1}")
    Integer usfbRolloutPercentage;

    @Value("${trillionLoans.rollout.percent:1}")
    Integer trillionLoansRolloutPercentage;

    @Value("${muthoot.rollout.percent:1}")
    Integer muthootRolloutPercentage;

    @Value("${capri.rollout.percent:1}")
    Integer capriRolloutPercent;

    @Value("${payu.rollout.percent:1}")
    Integer payuRolloutPercent;

    @Autowired
    UgroConfig ugroConfig;

    @Autowired
    OxyzoConfig oxyzoConfig;

    @Autowired
    CreditSaisonConfig creditSaisonConfig;

    @Value("#{'ABFL,PIRAMAL,TRILLIONLOANS,MUTHOOT,PAYU,CREDITSAISON,SMFG,UGRO,OXYZO'.split(',')}")
    private List<String> activeLenders;

    @Autowired
    LenderAssignService lenderAssignService;

    static List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(),
            LoanType.IO_TOPUP.name());

    public TopUpOfferResponseDto getTopupEligibilityDetails(Long merchantId, TopUpOfferRequestDto topUpOfferRequestDto) throws BureauCallMaskedApiException {

        if (merchantId == null || topUpOfferRequestDto == null ||
                topUpOfferRequestDto.getAmount() == null ||
                topUpOfferRequestDto.getApplicationId() == null) {
            logger.error("Invalid input parameters for topup eligibility check");
            throw new IllegalArgumentException("Required parameters are missing");
        }

        logger.info("Processing topup eligibility for merchantId: {}, selectedAmount: {}, applicationId: {}",
                merchantId, topUpOfferRequestDto.getAmount(), topUpOfferRequestDto.getApplicationId());

        TopUpOfferResponseDto responseDto = new TopUpOfferResponseDto();
        List<TopUpOfferResponseDto.Offer> offers = new ArrayList<>();
        List<String> availableTenures = new ArrayList<>();
        List<LendingEligibleLoan> validEligibleLoans = new ArrayList<>();

        try {
            List<LendingEligibleLoan> eligibleLoans = eligibleLoanDao.findRecentByMerchantIdAndLoanTypeAndAmountGreaterThanEqualOrderByAmountDesc(
                    merchantId,
                    LoanType.TOPUP.name(),
                    topUpOfferRequestDto.getAmount()
            );

            if (eligibleLoans == null || eligibleLoans.isEmpty()) {
                logger.info("No eligible loans found for merchantId: {}", merchantId);
                return createEmptyResponse("No eligible topup offers available", topUpOfferRequestDto.getApplicationId());
            }

            LendingPaymentScheduleSlave lendingPaymentSchedule = lendingPaymentScheduleDaoSlave
                    .findByMerchantIdAndStatus(merchantId, Arrays.asList("ACTIVE", "DECEASED"));

            if (lendingPaymentSchedule == null) {
                logger.error("No active payment schedule found for merchantId: {}", merchantId);
                return createEmptyResponse("No active loan found for topup", topUpOfferRequestDto.getApplicationId());
            }

            LendingApplication lendingApplication = lendingApplicationDao
                    .findByIdAndMerchantId(topUpOfferRequestDto.getApplicationId(), merchantId);

            if (lendingApplication == null || lendingApplication.getLender() == null) {
                logger.error("No lending application found for applicationId: {}, merchantId: {}",
                        topUpOfferRequestDto.getApplicationId(), merchantId);
                return createEmptyResponse("Invalid application details", topUpOfferRequestDto.getApplicationId());
            }

            LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchantId);

            if (lendingRiskVariables == null || lendingRiskVariables.getRiskSegment() == null ||
                    lendingRiskVariables.getRiskGroup() == null || lendingRiskVariables.getPincodeColor() == null) {
                logger.error("No risk variables found for merchantId: {}", merchantId);
                return createEmptyResponse("Risk assessment data not available", topUpOfferRequestDto.getApplicationId());
            }

            BigDecimal prevLoanUnpaidAmountBD;
            try {
                Double prevAmount = merchantLoansService.getPreviousLoanAmount(lendingPaymentSchedule);
                prevLoanUnpaidAmountBD = BigDecimal.valueOf(prevAmount != null ? prevAmount : 0.0);
            } catch (Exception e) {
                logger.error("Error calculating previous loan amount for merchantId: {}", merchantId, e);
                prevLoanUnpaidAmountBD = BigDecimal.ZERO;
            }

            RiskVariablesDTO riskVariables = new RiskVariablesDTO();
            Set<Integer> processedTenures = new HashSet<>();
            for (LendingEligibleLoan eligibleLoan : eligibleLoans) {
                if (eligibleLoan == null || eligibleLoan.getAmount() == null ||
                        eligibleLoan.getTenureInMonths() == null) {
                    logger.warn("Skipping invalid eligible loan entry for merchantId: {}", merchantId);
                    continue;
                }

                Integer tenure = eligibleLoan.getTenureInMonths();
                if (processedTenures.contains(tenure)) {
                    logger.info("Skipping duplicate tenure {} for merchantId: {}", tenure, merchantId);
                    continue;  // Skip if tenure already processed
                }
                processedTenures.add(tenure);

                try {
                    PricingExperiment pricingExperiment = null;
                    LendingLenderPricing lenderPricing = null;
                    if(pricingExpEnabled) {
                        pricingExperiment = pricingExperimentDao.findBySegmentAndRiskGroupAndTenureInMonthsAndMerchantIdAndPincodeColorAndStatus(lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                                eligibleLoan.getTenureInMonths(), (int) (lendingApplication.getMerchantId()%10), lendingRiskVariables.getPincodeColor().name(), "ACTIVE");
                    }
                    if(!ObjectUtils.isEmpty(pricingExperiment)) {
                        logger.info("experiment fetched for {}: {}", lendingPaymentSchedule.getMerchantId(), pricingExperiment);
                        riskVariables.setPricingExperimentMap(Collections.singletonMap(lendingApplication.getMerchantId(), pricingExperiment));
                    }else{
                        lenderPricing = lendingLenderPricingDao.
                                findTop1BySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColorAndStatus
                                        (lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                                                eligibleLoan.getTenureInMonths(), topUpOfferRequestDto.getTopupLender(), lendingRiskVariables.getPincodeColor().name(), "ACTIVE");
                        logger.info("LendingLenderPricing found: {}", lenderPricing);
                        riskVariables.setLenderPricingMap(Collections.singletonMap(topUpOfferRequestDto.getTopupLender(), lenderPricing));
                    }

                    if (lenderPricing == null && pricingExperiment == null) {
                        logger.info("No lender pricing found for merchantId: {}, loan amount: {}, tenureInMonths: {}, lender: {}",
                                merchantId, eligibleLoan.getAmount(), eligibleLoan.getTenureInMonths(), lendingApplication.getLender());
                        continue;
                    }

                    //This is to create eligible loan for checking apr/irr. Values will be overridden with correct values in calculateProcessingFeeForTopup method
                    GlobalLimitResponse.OfferDetail offerDetail = new GlobalLimitResponse.OfferDetail();
                    offerDetail.setInterestRate(eligibleLoan.getRateOfInterest());
                    offerDetail.setTenure(eligibleLoan.getTenureInMonths());
                    offerDetail.setProcessingFee(eligibleLoan.getProcessingFee().doubleValue());
                    offerDetail.setInitialRoi(eligibleLoan.getInitialRoi());
                    offerDetail.setClubV2Amount(eligibleLoan.getClubV2Amount());

                    LendingEligibleLoan newEligibleLoan = loanUtil.calculateLoanBreakupV3(offerDetail, merchantId, eligibleLoan.getLoanType(), topUpOfferRequestDto.getAmount(), null, eligibleLoan.getVersion());

                    merchantLoansService.calculateProcessingFeeForTopup(
                            newEligibleLoan, lendingPaymentSchedule, lendingApplication,
                            lendingRiskVariables, prevLoanUnpaidAmountBD, topUpOfferRequestDto.getTopupLender());

                    if (performRiskChecks(newEligibleLoan, topUpOfferRequestDto.getTopupLender(), riskVariables, merchantId)) {
                        eligibleLoanDao.deleteById(newEligibleLoan.getId());
                        continue; // Skip this loan if risk checks fail
                    }

                    validEligibleLoans.add(newEligibleLoan);
                } catch (Exception e) {
                    logger.error("Error processing eligible loan for merchantId: {}, amount: {}",
                            merchantId, eligibleLoan.getAmount(), e);
                }
            }

            if (validEligibleLoans.isEmpty()) {
                logger.info("No valid eligible loans after processing for merchantId: {}", merchantId);
                return createEmptyResponse("No valid topup offers available after risk assessment", topUpOfferRequestDto.getApplicationId());
            }

            for (LendingEligibleLoan eligibleLoan : validEligibleLoans) {
                try {
                    TopUpOfferResponseDto.Offer offer = mapEligibleLoanToOffer(eligibleLoan);
                    offers.add(offer);
                    availableTenures.add(eligibleLoan.getTenure());

                } catch (Exception e) {
                    logger.error("Error mapping eligible loan to offer for loanId: {}", eligibleLoan.getId(), e);
                }
            }

            Collections.sort(availableTenures);

            responseDto.setOffers(offers);
            responseDto.setTenure(availableTenures);
            responseDto.setExistingApplicationId(topUpOfferRequestDto.getApplicationId());
            responseDto.setSuccess(!offers.isEmpty());
            responseDto.setMessage(offers.isEmpty() ? "No valid topup offers available" : "Success");

            logger.info("Topup offers processed successfully for merchantId: {}, offers count: {}", merchantId, offers.size());

        } catch (Exception e) {
            logger.error("Error processing topup eligibility for merchantId: {}", merchantId, e);
            throw new BureauCallMaskedApiException("Failed to process topup eligibility: " + e.getMessage(), null);

        }

        return responseDto;
    }

    private TopUpOfferResponseDto createEmptyResponse(String message, Long applicationId) {
        TopUpOfferResponseDto responseDto = new TopUpOfferResponseDto();
        responseDto.setOffers(new ArrayList<>());
        responseDto.setTenure(new ArrayList<>());
        responseDto.setExistingApplicationId(applicationId);
        responseDto.setSuccess(false);
        responseDto.setMessage(message);
        return responseDto;
    }

    private BigDecimal calculateProcessingFee(LendingLenderPricing lenderPricing, LendingEligibleLoan eligibleLoan, BigDecimal prevLoanUnpaidAmountBD) {
        if (lenderPricing.getProcessingFeeRate() == null || eligibleLoan.getAmount() == null) {
            return BigDecimal.ZERO;
        }

        try {
            BigDecimal processingFeeRateBD = BigDecimal.valueOf(lenderPricing.getProcessingFeeRate());
            BigDecimal amountBD = BigDecimal.valueOf(eligibleLoan.getAmount());

            return processingFeeRateBD.multiply(amountBD.subtract(prevLoanUnpaidAmountBD))
                    .divide(new BigDecimal(100), 0, RoundingMode.CEILING);
        } catch (Exception e) {
            logger.error("Error calculating processing fee for eligible loan: {}", eligibleLoan.getId(), e);
            return BigDecimal.ZERO;
        }
    }

    private boolean performRiskChecks(LendingEligibleLoan eligibleLoan, String topupLender,
                                      RiskVariablesDTO riskVariables, Long merchantId) {
        try {
            EdiModel ediModel = LenderOffDays.valueOf(topupLender).getEdiModel();

            boolean irrCheckFailed = lenderAssignService.maxIrrCheckFailedV2(eligibleLoan, ediModel,
                    topupLender, riskVariables);
            boolean aprCheckFailed = lenderAssignService.maxAprCheckFailedV2(eligibleLoan, ediModel,
                    topupLender, riskVariables);

            if (irrCheckFailed || aprCheckFailed) {
                logger.info("Risk check failed for merchantId: {}, amount: {}, ediCount: {}, lender: {}, IRR: {}, APR: {}",
                        merchantId, eligibleLoan.getAmount(), eligibleLoan.getEdiCount(),
                        topupLender, irrCheckFailed, aprCheckFailed);
                return true;
            }

            return false;
        } catch (Exception e) {
            logger.error("Error performing risk checks for merchantId: {}, amount: {}",
                    merchantId, eligibleLoan.getAmount(), e);
            return true;
        }
    }

    private TopUpOfferResponseDto.Offer mapEligibleLoanToOffer(LendingEligibleLoan eligibleLoan) {
        TopUpOfferResponseDto.Offer offer = new TopUpOfferResponseDto.Offer();

        offer.setEligibleLoanId(eligibleLoan.getId());
        offer.setAmount(eligibleLoan.getAmount());
        offer.setMaxAmount(eligibleLoan.getAmount()); // Same as amount for individual offers
        offer.setTenureInMonths(eligibleLoan.getTenureInMonths());
        offer.setEdi(eligibleLoan.getEdi() != null ? eligibleLoan.getEdi().doubleValue() : null);
        offer.setEdiCount(eligibleLoan.getEdiCount());
        offer.setCategory(eligibleLoan.getCategory());
        offer.setProcessingFee(eligibleLoan.getProcessingFee() != null ? eligibleLoan.getProcessingFee().doubleValue() : null);
        offer.setRateOfInterest(eligibleLoan.getRateOfInterest());
        offer.setRepaymentAmount(eligibleLoan.getRepayment() != null ? eligibleLoan.getRepayment().doubleValue() : null);
        offer.setApr(eligibleLoan.getApr());
        offer.setIrr(eligibleLoan.getIrr());
        offer.setTenure(eligibleLoan.getTenure());

        return offer;
    }

    /*public Mono<EligibleLendingOffersResponseDTO> getEligibilityDetailsReactive(Long merchantId, Double queryAmount, Integer ediModel) {
        return Mono.fromCallable(() -> {
            try {
                return getEligibilityDetails(merchantId, queryAmount, ediModel);
            } catch (BureauCallMaskedApiException e) {
                throw Exceptions.propagate(e);
            }
        }).subscribeOn(Schedulers.elastic());
    }*/

    public EligibleLendingOffersResponseDTO getEligibilityDetails(Long merchantId, Double queryAmount, Integer ediModel) throws BureauCallMaskedApiException {

        EligibleLendingOffersResponseDTO responseDTO = new EligibleLendingOffersResponseDTO();

        int updatedEligibilityRefreshWindow = easyLoanUtil.percentScaleUp(merchantId, newEligibilityRefreshWindowRolloutPercent) ? newEligibilityRefreshWindow : eligibilityRefreshWindow;
        Date dateWindow = dateTimeUtil.getDatePlusMinutes(dateTimeUtil.getCurrentDate(), -1);

//        List<LendingEligibleLoan> eligibleLoans = eligibleLoanDao.findByMerchantIdAndAmountAndCreatedAtIsGreaterThanEqualAndLoanTypeNotIn(merchantId, queryAmount, dateWindow, topupLoans,
//          Sort.by(Sort.Direction.DESC, "id"));

        List<LendingEligibleLoan> eligibleLoans = new ArrayList<>();

        if (ObjectUtils.isEmpty(eligibleLoans)) {
            GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(merchantId, EligibilityRequestSource.EASY_LOANS);
            //Todo if global response is null: return
            if ((queryAmount < 10000 && Objects.nonNull(globalLimitResponse) && Objects.nonNull(globalLimitResponse.getData()) &&
                    !globalLimitResponse.getData().getLoanType().equalsIgnoreCase(LoanType.SMALL_TICKET.name())) || (Objects.nonNull(globalLimitResponse)
              && Objects.nonNull(globalLimitResponse.getData()) && ObjectUtils.isEmpty(globalLimitResponse.getData().getOfferDetails()))) {
                responseDTO.setSuccess(false);
                responseDTO.setMessage("Invalid Loan Amount");
                return responseDTO;
            }
            //        eligibleLoanDao.deleteCustomOffers(merchantId);
            if (Objects.nonNull(globalLimitResponse) && Objects.nonNull(globalLimitResponse.getData()) && Objects.nonNull(globalLimitResponse.getData().getGlobalLimit())) {
                logger.info("query amount: {}, global calculated offer: {}", queryAmount, globalLimitResponse.getData().getGlobalLimit());
                queryAmount = queryAmount > globalLimitResponse.getData().getGlobalLimit() ? globalLimitResponse.getData().getGlobalLimit() : queryAmount;
            }
            loanDetailsServiceV2.recomputeEligibleLoan(globalLimitResponse, queryAmount, merchantId, false);
            eligibleLoans = eligibleLoanDao.findByMerchantIdAndAmountAndCreatedAtIsGreaterThanEqualAndLoanTypeNotIn(merchantId, queryAmount, dateWindow, topupLoans,
                    Sort.by(Sort.Direction.DESC, "id"));
            logger.info("Eligible loan offers : {} , {}", merchantId, eligibleLoans);
        }

        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchantId);
        List<EligibleLendingOffersResponseDTO.TenureDetails> tenures = new ArrayList<>();
        Double interestRate = null;
        for (LendingEligibleLoan el : eligibleLoans) {
            MaxPricingValuesDTO maxPricingValuesDTO = null;
            if (Boolean.TRUE.equals(loanUtil.isLenderPricingApplicableMerchant(merchantId))) {
                maxPricingValuesDTO = loanUtil.getMaxPricingValues(lendingRiskVariables, el.getTenureInMonths());
            }
            if (ediModel == 6 && el.getEdiCount() % 30 !=0){
                tenures.add(convertLoanToTenureDetails(el, responseDTO, maxPricingValuesDTO));
            } else if (ediModel == 7 && el.getEdiCount() % 30 ==0) {
                tenures.add(convertLoanToTenureDetails(el, responseDTO,maxPricingValuesDTO));
            }
        }
        responseDTO.setEligibleOfferDetails(responseDTO.new EligibleOfferDetails(queryAmount, tenures));
        responseDTO.setMessage("Available tenures for given amount");
        responseDTO.setSuccess(true);
        logger.info("Eligibility Details response for merchant: {} is: {}", merchantId, responseDTO);
        return responseDTO;
    }

    public ResponseEntity<ApiResponseDTOV2<EligibleOffersResponseDTO>> getEligibilityDetailsV2(Long merchantId, Double queryAmount, Integer ediModel, BasicDetailsDto merchantDetails) {
        final String METHOD = "getEligibilityDetailsV2";
        AsyncLoggerUtil.logInfo(logger, "ENTRY {} - merchantId: {}, amount: {}, ediModel: {}", METHOD, merchantId, queryAmount, ediModel);

        try {
            // Validate input parameters
            if (merchantId == null || queryAmount == null) {
                AsyncLoggerUtil.logError(logger, "EXIT {} - Invalid request parameters", METHOD);
                return ApiResponseUtil.badRequest("Invalid request parameters", "INVALID_PARAMS");
            }

            // Generate cache key and check cache
            String cacheKey = generateEligibilityCacheKey(merchantId, queryAmount, ediModel);
            EligibleOffersResponseDTO cachedResponse = null;

            try {
                cachedResponse = (EligibleOffersResponseDTO) lendingCache.get(cacheKey);
                if (cachedResponse != null) {
                    AsyncLoggerUtil.logInfo(logger, "EXIT {} - Cache hit for merchantId: {}", METHOD, merchantId);
                    return ApiResponseUtil.ok(cachedResponse, "Eligibility details fetched successfully from cache");
                }
            } catch (Exception e) {
                AsyncLoggerUtil.logError(logger, "Cache retrieval failed for key: {} - {}", cacheKey, e.getMessage());
                // Continue execution despite cache error
            }

            AsyncLoggerUtil.logInfo(logger, "Cache miss for merchantId: {}, processing eligibility", merchantId);
            EligibleOffersResponseDTO responseDTO = new EligibleOffersResponseDTO();

            // Get global limit
            GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(merchantId, EligibilityRequestSource.EASY_LOANS);
            if (globalLimitResponse == null || globalLimitResponse.getData() == null) {
                AsyncLoggerUtil.logError(logger, "EXIT {} - Failed to retrieve global limit for merchantId: {}", METHOD, merchantId);
                return ApiResponseUtil.notFound("Global limit not available", "GLOBAL_LIMIT_NOT_FOUND");
            }

            // Validate loan amount and offer details
            GlobalLimitResponse.Data data = globalLimitResponse.getData();
            boolean isSmallTicket = data.getLoanType() != null &&
                    data.getLoanType().equalsIgnoreCase(LoanType.SMALL_TICKET.name());
            boolean hasOfferDetails = !ObjectUtils.isEmpty(data.getOfferDetails());

            if ((queryAmount < 10000 && !isSmallTicket) || !hasOfferDetails) {
                AsyncLoggerUtil.logError(logger, "EXIT {} - Invalid loan amount or no offer details for merchantId: {}", METHOD, merchantId);
                return ApiResponseUtil.badRequest("Invalid loan amount or no offers available", "INVALID_AMOUNT_OR_NO_OFFERS");
            }

            // Adjust amount based on global limit
            Double effectiveQueryAmount = queryAmount;
            if (data.getGlobalLimit() != null && queryAmount > data.getGlobalLimit()) {
                AsyncLoggerUtil.logInfo(logger, "Adjusting query amount from {} to global limit {} for merchantId: {}",
                        queryAmount, data.getGlobalLimit(), merchantId);
                effectiveQueryAmount = data.getGlobalLimit();
            }

            // Get eligible loans
            List<EligibleLoanDTO> eligibleLoans;
            try {
                eligibleLoans = loanDetailsServiceV2.recomputeEligibleOfferLoan(
                        globalLimitResponse, effectiveQueryAmount, merchantId);

                if (eligibleLoans.isEmpty()) {
                    AsyncLoggerUtil.logError(logger, "EXIT {} - No eligible loans found for merchantId: {}", METHOD, merchantId);
                    return ApiResponseUtil.notFound("No eligible offers available", "NO_ELIGIBLE_OFFERS");
                }
            } catch (Exception e) {
                AsyncLoggerUtil.logError(logger, "EXIT {} - Failed to compute eligible loans for merchantId: {}: {}",
                        METHOD, merchantId, e.getMessage(), e);
                return ApiResponseUtil.internalError("Failed to compute eligible offers", e.getMessage());
            }

            // Get risk variables
            LendingRiskVariables lendingRiskVariables;
            try {
                lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchantId);
                if (lendingRiskVariables == null) {
                    AsyncLoggerUtil.logError(logger, "EXIT {} - Risk variables not found for merchantId: {}", METHOD, merchantId);
                    return ApiResponseUtil.notFound("Risk assessment data not available", "RISK_DATA_NOT_FOUND");
                }
            } catch (Exception e) {
                AsyncLoggerUtil.logError(logger, "EXIT {} - Failed to retrieve risk variables for merchantId: {}: {}",
                        METHOD, merchantId, e.getMessage(), e);
                return ApiResponseUtil.internalError("Failed to retrieve risk data", e.getMessage());
            }

            String evaluationId = merchantId+ "_" + effectiveQueryAmount.intValue();

            // Get eligible lender list
            List<EligibleOffersResponseDTO.TenureWithLender> tenureWithLenders;
            try {
                tenureWithLenders = getEligibleLenderList(merchantId, eligibleLoans, merchantDetails, lendingRiskVariables, evaluationId);
                if (tenureWithLenders == null || tenureWithLenders.isEmpty()) {
                    AsyncLoggerUtil.logInfo(logger, "EXIT {} - No eligible lenders found for merchantId: {}", METHOD, merchantId);
                    return ApiResponseUtil.notFound("No lenders available for the requested amount", "NO_ELIGIBLE_LENDERS");
                }
            } catch (Exception e) {
                AsyncLoggerUtil.logError(logger, "EXIT {} - Failed to get eligible lenders for merchantId: {}: {}",
                        METHOD, merchantId, e.getMessage(), e);
                return ApiResponseUtil.internalError("Failed to process lender eligibility", e.getMessage());
            }

            // Create offer with tenures
            responseDTO.setEligibleOffers(tenureWithLenders);
            responseDTO.setLoanAmount(effectiveQueryAmount);

            // Cache successful response
            try {
                cacheEligibilityResponse(cacheKey, responseDTO, merchantId);
            } catch (Exception e) {
                AsyncLoggerUtil.logError(logger, "Failed to cache response for key: {} - {}", cacheKey, e.getMessage());
                // Continue despite cache error
            }

            AsyncLoggerUtil.logInfo(logger, "EXIT {} - Successfully processed eligibility for merchantId: {}, found {} tenures",
                    METHOD, merchantId, tenureWithLenders.size());
            return ApiResponseUtil.ok(responseDTO, "Eligibility details fetched successfully");

        } catch (Exception e) {
            AsyncLoggerUtil.logError(logger, "EXIT {} - Unexpected error for merchantId: {}: {}",
                    METHOD, merchantId, e.getMessage(), e);
            return ApiResponseUtil.internalError("An unexpected error occurred", e.getMessage());
        }
    }

    public List<EligibleOffersResponseDTO.TenureWithLender> getEligibleLenderList(Long merchantId, List<EligibleLoanDTO> eligibleLoans, BasicDetailsDto merchantDetails, LendingRiskVariables lendingRiskVariables, String evaluationId) {
        final String METHOD = "getEligibleLenderList";
        AsyncLoggerUtil.logInfo(logger, "ENTRY {} - Processing {} eligible loans for merchantId: {}", METHOD, eligibleLoans.size(), merchantId);

        try {
            // Call lender assignment handler to get eligible loans with assigned lenders
            //handle switchoff lenders here
            List<EligibleLoanDTO> eligibleOffersWithLenders = lenderAssignmentHandlerV1(merchantId, eligibleLoans, merchantDetails, evaluationId);

            if (CollectionUtils.isEmpty(eligibleOffersWithLenders)) {
                AsyncLoggerUtil.logInfo(logger, "EXIT {} - No eligible offers with lenders found for merchantId: {}", METHOD, merchantId);
                return null;
            }

            List<EligibleOffersResponseDTO.TenureWithLender> tenureWithLenders = new ArrayList<>();

            LendingApplication openApplication = lendingApplicationDao.findByMerchantIdAndStatus(merchantId, ApplicationStatus.DRAFT.name());
            if(openApplication != null)
            {
               LendingAuditTrial lendingAuditTrialFallback = lendingAuditTrialDao.findTopByEvaluationIdAndType(evaluationId, "INITIAL_LENDERS");
               lendingAuditTrialFallback.setApplicationId(openApplication.getId());
               lendingAuditTrialDao.save(lendingAuditTrialFallback);

                LendingAuditTrial lendingAuditTrialInitial = lendingAuditTrialDao.findTopByEvaluationIdAndType(evaluationId, "FALLBACK_LENDERS");
                lendingAuditTrialInitial.setApplicationId(openApplication.getId());
                lendingAuditTrialDao.save(lendingAuditTrialInitial);
            }

            List<LenderMetricsHistory> switchedOffLenders = lenderMetricsHistoryDao.findByIsLenderSwitchedOff(Boolean.TRUE);

            List<String> switchedOffLenderNames = switchedOffLenders.stream()
                    .map(LenderMetricsHistory::getLender)
                    .collect(Collectors.toList());

            AsyncLoggerUtil.logInfo(logger, "Lenders switched off in the system: {} for merchantId: {}", switchedOffLenderNames, merchantId);

            if (!switchedOffLenderNames.isEmpty()) {
                for (EligibleLoanDTO loan : eligibleOffersWithLenders) {
                    if (loan.getEligibleLenders() != null) {
                        loan.getEligibleLenders().removeAll(switchedOffLenderNames);
                        AsyncLoggerUtil.logInfo(logger, "Filtered out switched off lenders for tenure {} months, remaining lenders: {}",
                                loan.getTenureInMonths(), loan.getEligibleLenders());
                    }
                }
            } else {
                AsyncLoggerUtil.logInfo(logger, "No lenders are switched off, skipping filtering step");
            }

            List<String> rejectedLenders = new ArrayList<>();
            if (openApplication != null) {
                AsyncLoggerUtil.logInfo(logger, "Found open application with ID: {}", openApplication.getId());
                List<String> alreadyAssignedLender = lendingApplicationLenderDetailsDao.findLendersByApplicationId(openApplication.getId());
                AsyncLoggerUtil.logInfo(logger, "Already assigned lenders for applicationId : {} {}", openApplication.getId(), alreadyAssignedLender);

                rejectedLenders = alreadyAssignedLender;

                for (EligibleLoanDTO loan : eligibleOffersWithLenders) {
                    if (loan.getEligibleLenders() != null) {
                        loan.getEligibleLenders().removeAll(alreadyAssignedLender);
                    }

                }
            }

            AsyncLoggerUtil.logInfo(logger, "Lenders after removing rejected lenders due to open application: {} for merchantId: {}", eligibleOffersWithLenders , merchantId);

            List<String> ineligibleLenders = new ArrayList<>();

            // Process each eligible loan
            for (EligibleLoanDTO loan : eligibleOffersWithLenders) {
                if (!CollectionUtils.isEmpty(loan.getEligibleLenders())) {
                    try {
                        // Get detailed lender data for this loan
                        List<EligibleOffersResponseDTO.LenderData> lenderDataForLoan = getLenderData(
                                loan.getEligibleLenders(), loan, lendingRiskVariables, merchantId);

                        AsyncLoggerUtil.logInfo(logger, "Lender data: {},fetched for merchantId: {}", lenderDataForLoan, merchantId);

                        List<String> lenderNames = lenderDataForLoan.stream()
                                .map(EligibleOffersResponseDTO.LenderData::getLenderName)
                                .collect(Collectors.toList());

                        AsyncLoggerUtil.logInfo(logger, "eligible lenders for tenure {} months: {}",
                                loan.getTenureInMonths(), lenderNames);


                        if (!StringUtils.isEmpty(lendingRiskVariables.getRejectedLenders())) {
                            List<String> ineligibleLendersArray = Arrays.asList(lendingRiskVariables.getRejectedLenders().split(","))
                                    .stream()
                                    .map(String::trim)
                                    .collect(Collectors.toList());

                            // Process each lender to see if it's rejected
                            List<String> lendersToRemove = new ArrayList<>();
                            for (String lender : lenderNames) {
                                if (ineligibleLendersArray.contains(loanUtil.getLenderRejectedMapping(lender.toUpperCase()))) {
                                    AsyncLoggerUtil.logInfo(logger, "Skipping {} due to lender in rejected lender list in lending risk variables for merchant: {}",
                                            lender, merchantId);
                                    String remarks = "Skipping " + lender + " due to lender in rejected lender list in lending risk variables";
                                    createAndSaveLendingAuditTrial(merchantId, lender, "LENDER_REMOVED", remarks, evaluationId);
                                    lendersToRemove.add(lender);
                                    ineligibleLenders.add(lender);
                                }
                            }

                            // Remove rejected lenders from the eligible list
                            lenderNames.removeAll(lendersToRemove);
                        }

                        //fetch from lender removed from lending audit trial
                        Set<String> ineligibleLendersSet = new HashSet<>(ineligibleLenders);

                        for (String activeLender : activeLenders) {
                            if (!lenderNames.contains(activeLender)) {
                                ineligibleLendersSet.add(activeLender);
                            }
                        }

                        ineligibleLenders = new ArrayList<>(ineligibleLendersSet);

                        AsyncLoggerUtil.logInfo(logger, "Complete ineligible lenders list: {} for merchantId: {}",
                                ineligibleLenders, merchantId);

                        List<LenderMetricsHistory> lenderMetricsHistoryList = lenderMetricsHistoryDao.findByLenderInAndIsLenderSwitchedOffFalse(lenderNames);

                        int initialLendersCount = 0;
                        int initialMatchingLendersCount = 0;
                        int fallbackMatchingLendersCount = 0;
                        List<String> initialLendersAssigned = new ArrayList<>();
                        List<String> fallbackLendersAssigned = new ArrayList<>();
                        LendingAuditTrial lendingAuditTrialInitial = null;
                        if(openApplication != null) {
                            AsyncLoggerUtil.logInfo(logger, "Fetching lending audit trial for open applicationId: {} and merchantId: {}",
                                    openApplication.getId(), merchantId);
                            lendingAuditTrialInitial = lendingAuditTrialDao.findTopByApplicationIdAndType(openApplication.getId(), "INITIAL_LENDERS");
                        }
                        else {
                            AsyncLoggerUtil.logInfo(logger, "Fetching lending audit trial for evaluationId merchantId: {} and merchantId: {}",
                                    merchantId, merchantId);
                            lendingAuditTrialInitial = lendingAuditTrialDao.findTopByEvaluationIdAndType(evaluationId, "INITIAL_LENDERS");
                        }
                        AsyncLoggerUtil.logInfo(logger, "Lending audit trial fetched for INITIAL_LENDERS: {} for merchantId: {}",
                                lendingAuditTrialInitial, lendingAuditTrialInitial.getId());

                        if (lendingAuditTrialInitial != null && !StringUtils.isEmpty(lendingAuditTrialInitial.getRemarks())) {
                            String remarks = lendingAuditTrialInitial.getRemarks();

                            if (remarks.startsWith("Initial lenders:")) {
                                remarks = remarks.substring("Initial lenders:".length()).trim();
                            }

                            initialLendersAssigned = Arrays.asList(remarks.split(","));
                            AsyncLoggerUtil.logInfo(logger, "Initial lenders after parsing: {}, count: {}",
                                    initialLendersAssigned, initialLendersAssigned.size());
                             initialLendersCount = initialLendersAssigned.size();
                            AsyncLoggerUtil.logInfo(logger, "Initial lenders from audit trail: {}, count: {}", initialLendersAssigned, initialLendersCount);

                            // Check for matches between alreadyAssignedLender (rejectedLenders) and initialLendersAssigned
                            List<String> matchingLenders = new ArrayList<>();
                            if (rejectedLenders != null && !rejectedLenders.isEmpty()) {
                                for (String lender : rejectedLenders) {
                                    if (initialLendersAssigned.contains(lender)) {
                                        matchingLenders.add(lender);
                                    }
                                }
                                initialMatchingLendersCount = matchingLenders.size();
                                AsyncLoggerUtil.logInfo(logger, "Matching lenders found in both rejected and initial lists: {}, matching count: {}",
                                        matchingLenders, initialMatchingLendersCount);
                            }
                        }

                        LendingAuditTrial lendingAuditTrialFallback = null;

                        if(openApplication != null) {
                            AsyncLoggerUtil.logInfo(logger, "Fetching lending audit trial for open applicationId: {} and merchantId: {}",
                                    openApplication.getId(), merchantId);
                            lendingAuditTrialFallback = lendingAuditTrialDao.findTopByApplicationIdAndType(openApplication.getId(), "FALLBACK_LENDERS");
                        }
                        else {
                            AsyncLoggerUtil.logInfo(logger, "Fetching lending audit trial for evaluationId merchantId: {} and merchantId: {}",
                                    merchantId, merchantId);
                            lendingAuditTrialFallback = lendingAuditTrialDao.findTopByEvaluationIdAndType(evaluationId, "FALLBACK_LENDERS");
                        }
                         AsyncLoggerUtil.logInfo(logger, "Lending audit trial fetched for FALLBACK_LENDERS: {} for merchantId: {}",
                                 lendingAuditTrialFallback, lendingAuditTrialFallback.getId());

                        if (lendingAuditTrialFallback != null && !StringUtils.isEmpty(lendingAuditTrialFallback.getRemarks())) {
                            AsyncLoggerUtil.logInfo(logger, "Initial lenders remarks from audit trial: {} for merchantId: {}",
                                    lendingAuditTrialFallback.getRemarks(), merchantId);
                            fallbackLendersAssigned = Arrays.asList(lendingAuditTrialFallback.getRemarks().split(","));
                            fallbackMatchingLendersCount = fallbackLendersAssigned.size();
                            AsyncLoggerUtil.logInfo(logger, "Fallback lenders from audit trail: {}, count: {}", fallbackLendersAssigned, fallbackMatchingLendersCount);

                            // Check for matches between alreadyAssignedLender (rejectedLenders) and initialLendersAssigned
                            List<String> matchingLenders = new ArrayList<>();
                            if (rejectedLenders != null && !rejectedLenders.isEmpty()) {
                                for (String lender : rejectedLenders) {
                                    if (fallbackLendersAssigned.contains(lender)) {
                                        matchingLenders.add(lender);
                                    }
                                }
                                fallbackMatchingLendersCount = matchingLenders.size();
                                AsyncLoggerUtil.logInfo(logger, "Matching lenders found in both rejected and initial lists: {}, matching count: {}",
                                        matchingLenders, fallbackMatchingLendersCount);
                            }
                        }

                        List<OfferRankingConfig> initialOfferRankingConfigs = offerRankingConfigDao.findByEnabledAndRankingType(true, RankingType.INITIAL);
                        List<String> initialLendersList = lenderRankingEngine.rankLenders(
                                lenderMetricsHistoryList,
                                initialOfferRankingConfigs,
                                RankingType.INITIAL,
                                initalLendersLimit - initialMatchingLendersCount,
                                merchantId,
                                loan.getTenureInMonths());

                        if(openApplication == null) {
                            createAndSaveLendingAuditTrial(
                                    merchantId,
                                    null,
                                    "INITIAL_LENDERS",
                                    String.join(",", initialLendersList),
                                    evaluationId
                            );
                        }

                        AsyncLoggerUtil.logInfo(logger, "Initial lenders for loan with tenure {} months: {} for merchantId: {}",
                                loan.getTenureInMonths(), initialLendersList, merchantId);

                        List<LenderMetricsHistory> fallbackCandidates = lenderMetricsHistoryList.stream()
                                .filter(lender -> !initialLendersList.contains(lender.getLender()))
                                .collect(Collectors.toList());

                        List<OfferRankingConfig> fallbackOfferRankingConfigs = offerRankingConfigDao.findByEnabledAndRankingType(true, RankingType.FALLBACK);

                        List<String> fallbackLendersList = fallbackCandidates.isEmpty() ?
                                Collections.emptyList() :
                                lenderRankingEngine.rankLenders(
                                        fallbackCandidates,
                                        fallbackOfferRankingConfigs,
                                        RankingType.FALLBACK,
                                        fallbackLendersLimit - fallbackMatchingLendersCount,
                                        merchantId,
                                        loan.getTenureInMonths());

                        if(openApplication == null) {
                            createAndSaveLendingAuditTrial(
                                    merchantId,
                                    null,
                                    "FALLBACK_LENDERS",
                                    String.join(",", fallbackLendersList),
                                    evaluationId
                            );
                        }

                        AsyncLoggerUtil.logInfo(logger, "Fallback lenders for loan with tenure {} months: {} for merchantId: {}",
                                loan.getTenureInMonths(), fallbackLendersList, merchantId);

                        Map<String, EligibleOffersResponseDTO.LenderData> lenderDataMap = lenderDataForLoan.stream()
                                .collect(Collectors.toMap(EligibleOffersResponseDTO.LenderData::getLenderName, Function.identity()));

                        List<EligibleOffersResponseDTO.LenderData> initialLenders = initialLendersList.stream()
                                .map(lenderDataMap::get)
                                .filter(Objects::nonNull)
                                .peek(ld -> ld.setRankingType(RankingType.valueOf(RankingType.INITIAL.name())))
                                .collect(Collectors.toList());

                        List<EligibleOffersResponseDTO.LenderData> fallbackLenders = fallbackLendersList.stream()
                                .map(lenderDataMap::get)
                                .filter(Objects::nonNull)
                                .peek(ld -> ld.setRankingType(RankingType.valueOf(RankingType.FALLBACK.name())))
                                .collect(Collectors.toList());
                        AsyncLoggerUtil.logInfo(logger,"initial lenders: {}, fallback lenders: {} for merchantId: {}", initialLenders, fallbackLenders, merchantId);


                        if (!CollectionUtils.isEmpty(lenderDataForLoan)) {
                            // Create TenureWithLender object with all required data
                            EligibleOffersResponseDTO.TenureWithLender tenureWithLender = new EligibleOffersResponseDTO.TenureWithLender(
                                    loan.getCategory(),
                                    loan.getTenure(),
                                    loan.getTenureInMonths(),
                                    loan.getEdiCount(),
                                    initialLenders,
                                    fallbackLenders,
                                    rejectedLenders,
                                    ineligibleLenders
                            );
                            tenureWithLenders.add(tenureWithLender);
                            AsyncLoggerUtil.logInfo(logger, "Added tenure option: {} months with {} lenders for merchantId: {}",
                                    loan.getTenureInMonths(), lenderDataForLoan.size(), merchantId);
                        }
                    } catch (Exception e) {
                        AsyncLoggerUtil.logError(logger, "Error processing lender data for loan with tenure {} months: {}",
                                loan.getTenureInMonths(), e.getMessage());
                        // Continue processing other loans despite this error
                    }
                }
            }

            if (tenureWithLenders.isEmpty()) {
                AsyncLoggerUtil.logInfo(logger, "{} - No valid tenure options found, but returning ineligible lenders for merchantId: {}", METHOD, merchantId);

                // Create a default TenureWithLender with empty eligible lenders but with ineligible lenders
                EligibleOffersResponseDTO.TenureWithLender defaultTenure = new EligibleOffersResponseDTO.TenureWithLender(
                        "NO_ELIGIBLE_OFFERS",  // category
                        "0 Months",            // tenure
                        0,                     // tenureInMonths
                        0,                     // ediCount
                        new ArrayList<>(),     // initialLenders (empty)
                        new ArrayList<>(),     // fallbackLenders (empty)
                        rejectedLenders,
                        ineligibleLenders
                );

                tenureWithLenders.add(defaultTenure);
            }

            AsyncLoggerUtil.logInfo(logger, "EXIT {} - Found {} tenure options for merchantId: {}",
                    METHOD, tenureWithLenders.size(), merchantId);
            return tenureWithLenders;
        } catch (Exception e) {
            AsyncLoggerUtil.logError(logger, "Unexpected error in {}: {}", METHOD, e.getMessage(), e);
            return null;
        }
    }


    public List<EligibleLoanDTO> lenderAssignmentHandlerV1(Long merchantId, List<EligibleLoanDTO> eligibleLoans, BasicDetailsDto merchantDetails, String evaluationId) {
        final String METHOD = "lenderAssignmentHandlerV1";
        AsyncLoggerUtil.logInfo(logger, "ENTRY {} - Processing {} eligible loans for merchantId: {}", METHOD, eligibleLoans.size(), merchantId);

        try {
            // Fetch risk variables for merchant
            LendingRiskVariables lendingRiskVariables;
            try {
                lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchantId);
                if (lendingRiskVariables == null) {
                    AsyncLoggerUtil.logError(logger, "EXIT {} - No risk variables found for merchantId: {}", METHOD, merchantId);
                    return Collections.emptyList();
                }
            } catch (Exception e) {
                AsyncLoggerUtil.logError(logger, "Error fetching risk variables for merchantId {}: {}", merchantId, e.getMessage(), e);
                return Collections.emptyList();
            }

            // Convert to DTO for processing
            RiskVariablesDTO baseRiskVariables = EntityToDtoConvertorUtil.convertToRiskVariablesDTO(lendingRiskVariables);
            AsyncLoggerUtil.logInfo(logger, "{} - Base risk variables obtained for merchantId {}", METHOD, merchantId);

            // Process each loan to assign eligible lenders
            for (EligibleLoanDTO loan : eligibleLoans) {
                try {
                    AsyncLoggerUtil.logDebug(logger, "{} - Processing loan with tenure {} months for merchantId: {}",
                            METHOD, loan.getTenureInMonths(), merchantId);

                    // Prepare risk variables for this specific loan
                    RiskVariablesDTO loanRiskVariables = prepareLoanRiskVariables(merchantId, loan, lendingRiskVariables, baseRiskVariables);

                    AsyncLoggerUtil.logInfo(logger, "{} - Prepared risk variables for loan with tenure {} months for merchantId: {}",
                            METHOD, loan.getTenureInMonths(), merchantId);

                    // Fetch applicable lender assignment rules
                    List<LenderAssignmentRules> ruleList = fetchApplicableRules(merchantId, loan, lendingRiskVariables, loanRiskVariables);

                    if (CollectionUtils.isEmpty(ruleList)) {
                        AsyncLoggerUtil.logInfo(logger, "{} - No applicable rules found for merchantId: {}, tenure: {}",
                                METHOD, merchantId, loan.getTenureInMonths());
                        loan.setEligibleLenders(Collections.emptyList());
                        continue;
                    }

                    // Get initial list of eligible lenders based on rules
                    List<String> eligibleLenders = getLenderList(
                            ruleList,
                            EdiModel.SEVEN_DAY_MODEL,
                            null,
                            merchantId,
                            evaluationId);

                    if (CollectionUtils.isEmpty(eligibleLenders)) {
                        AsyncLoggerUtil.logInfo(logger, "{} - No eligible lenders from rules for merchantId: {}", METHOD, merchantId);
                        loan.setEligibleLenders(Collections.emptyList());
                        continue;
                    }

                    // Apply additional filters to eligible lenders
                    eligibleLenders = filterEligibleLenders(
                            merchantId,
                            loan,
                            lendingRiskVariables,
                            loanRiskVariables,
                            eligibleLenders,
                            merchantDetails,
                            evaluationId);

                    AsyncLoggerUtil.logInfo(logger, "{} - Final eligible lenders for merchantId {}, tenure {}: {}",
                            METHOD, merchantId, loan.getTenureInMonths(), eligibleLenders);

                    // Set the eligible lenders for this loan
                    loan.setEligibleLenders(eligibleLenders);

                } catch (Exception ex) {
                    AsyncLoggerUtil.logError(logger, "Error in {} processing loan with tenure {} months for merchantId {}: {}",
                            METHOD, loan.getTenureInMonths(), merchantId, ex.getMessage(), ex);
                    loan.setEligibleLenders(Collections.emptyList());
                }
            }

            // Count loans with valid lenders
            int loansWithLenders = (int) eligibleLoans.stream()
                    .filter(loan -> !CollectionUtils.isEmpty(loan.getEligibleLenders()))
                    .count();

            AsyncLoggerUtil.logInfo(logger, "EXIT {} - Completed processing for merchantId: {}, found lenders for {}/{} loans",
                    METHOD, merchantId, loansWithLenders, eligibleLoans.size());

            return eligibleLoans;
        } catch (Exception e) {
            AsyncLoggerUtil.logError(logger, "Unexpected error in {} for merchantId {}: {}", METHOD, merchantId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private RiskVariablesDTO prepareLoanRiskVariables(Long merchantId, EligibleLoanDTO loan,
                                                      LendingRiskVariables lendingRiskVariables, RiskVariablesDTO baseRiskVariables) {

        RiskVariablesDTO loanRiskVariables = new RiskVariablesDTO();
        BeanUtils.copyProperties(baseRiskVariables, loanRiskVariables);

        if (pricingExpEnabled) {
            PricingExperiment experiment = pricingExperimentDao.findBySegmentAndRiskGroupAndTenureInMonthsAndMidEndsWithAndPincodeColor(
                    lendingRiskVariables.getRiskSegment(),
                    lendingRiskVariables.getRiskGroup(),
                    loan.getTenureInMonths(),
                    (int) (merchantId % 10),
                    lendingRiskVariables.getPincodeColor().name(),
                    DateTime.now().toDate()
            );

            if (experiment != null) {
                loanRiskVariables.setPricingExperimentMap(Collections.singletonMap(merchantId, experiment));
                AsyncLoggerUtil.logInfo(logger,"Applied pricing experiment for merchantId {}", merchantId);
                return loanRiskVariables;
            }
        }

        List<LendingLenderPricing> lenderPricingList = lendingLenderPricingDao.findBySegmentAndRiskGroupAndTenureInMonthsAndPincodeColor(
                lendingRiskVariables.getRiskSegment(),
                lendingRiskVariables.getRiskGroup(),
                loan.getTenureInMonths(),
                lendingRiskVariables.getPincodeColor().name(),
                DateTime.now().toDate()
        );

        if (!CollectionUtils.isEmpty(lenderPricingList)) {
            loanRiskVariables.setLenderPricingMap(lenderPricingList.stream()
                    .collect(Collectors.toMap(LendingLenderPricing::getLender, Function.identity(), (a, b) -> a)));
        }

        return loanRiskVariables;
    }

    private List<LenderAssignmentRules> fetchApplicableRules(Long merchantId, EligibleLoanDTO loan,
                                                             LendingRiskVariables lendingRiskVariables, RiskVariablesDTO loanRiskVariables) {

        // Instead of logging the entire object:
        AsyncLoggerUtil.logInfo(logger, "Fetching applicable rules for merchantId: {}, key risk variables: bureau={}, segment={}, riskGroup={}, pincodeColor={}",
                merchantId,
                lendingRiskVariables.getBureauScore(),
                lendingRiskVariables.getRiskSegment(),
                lendingRiskVariables.getRiskGroup(),
                lendingRiskVariables.getPincodeColor());
        double bureauScore = lendingRiskVariables.getBureauScore();
        String riskSegment = "%" + lendingRiskVariables.getRiskSegment() + "%";
        String riskGroupLike =  "%" + lendingRiskVariables.getRiskGroup() +  "%";
        String pincodeColor = ObjectUtils.isEmpty(lendingRiskVariables.getPincodeColor()) ? "" : "%" + lendingRiskVariables.getPincodeColor().name() + "%";
        String tenure = "%" + loan.getTenureInMonths() + "%";

        AsyncLoggerUtil.logInfo(logger,"Fetching rules: bureau={}, segment={}, tenure={}, amount={}, riskGroup={}, pincodeColor={}",
                bureauScore, riskSegment, tenure, loan.getAmount(), riskGroupLike, pincodeColor);

        if (loanUtil.isInternalMerchant(merchantId)) {
            return lenderAssignmentRulesDao.fetchEligibleRulesForInternal(loan.getAmount(), bureauScore, riskSegment, tenure, riskGroupLike, pincodeColor);
        }
        return lenderAssignmentRulesDao.fetchEligibleRules(loan.getAmount(), bureauScore, riskSegment, tenure, riskGroupLike, pincodeColor);
    }

    private List<String> filterEligibleLenders(Long merchantId, EligibleLoanDTO loan,
                                               LendingRiskVariables lendingRiskVariables,
                                               RiskVariablesDTO riskVariables,
                                               List<String> lenders,
                                               BasicDetailsDto merchantDetails,
                                               String evaluationId) {

        final String METHOD = "filterEligibleLenders";
        AsyncLoggerUtil.logInfo(logger, "ENTRY {} - Starting lender filtering for merchantId: {}, loan amount: {}, tenure: {}",
                METHOD, merchantId, loan.getAmount(), loan.getTenureInMonths());
        AsyncLoggerUtil.logInfo(logger, "Initial lenders to check: {} for merchantId: {}", lenders, merchantId);

        if (CollectionUtils.isEmpty(lenders)) {
            AsyncLoggerUtil.logInfo(logger, "EXIT {} - No lenders to filter for merchantId: {}", METHOD, merchantId);
            return lenders;
        }

        int initialLenderCount = lenders.size();
        boolean isPanAadhaarLinkedChecked = false;
        boolean isPanAadhaarLinked = false;

        Iterator<String> iterator = lenders.iterator();
        while (iterator.hasNext()) {
            String lender = iterator.next().toUpperCase();
            AsyncLoggerUtil.logDebug(logger, "Evaluating lender: {} for merchantId: {}", lender, merchantId);

            if (isRejectedLender(riskVariables, lender, merchantId, evaluationId)) {
                AsyncLoggerUtil.logInfo(logger, "Removing lender: {} - Listed as rejected lender for merchantId: {}",
                        lender, merchantId);
                iterator.remove();
                continue;
            }

            if (!isPincodeEligible(lender, loan, lendingRiskVariables)) {
                String rejectReason = "Pincode not eligible for merchantId";
                AsyncLoggerUtil.logInfo(logger, "Removing lender: {} - {} {}", lender, rejectReason, merchantId);
                createAndSaveLendingAuditTrial(merchantId, lender, "LENDER_REMOVED", rejectReason, evaluationId);
                iterator.remove();
                continue;
            }

            if (!lenderBaseChecksCleared(loan, lender, EdiModel.SEVEN_DAY_MODEL, riskVariables, merchantId, evaluationId)) {
                AsyncLoggerUtil.logInfo(logger, "Removing lender: {} - Base checks failed for merchantId: {}",
                        lender, merchantId);
                iterator.remove();
                continue;
            }

            if (aadhaarSeedingStatusCheckLenders.contains(lender)) {
                if (!isPanAadhaarLinkedChecked) {
                    isPanAadhaarLinked = isPanAndAadhaarLinked(merchantId);
                    isPanAadhaarLinkedChecked = true;
                    AsyncLoggerUtil.logInfo(logger, "PAN-Aadhaar link status checked: {} for merchantId: {}",
                            isPanAadhaarLinked, merchantId);
                }

                if (!isPanAadhaarLinked) {
                    String rejectReason = "PAN-Aadhaar not linked for merchantId";
                    AsyncLoggerUtil.logInfo(logger, "Removing lender: {} - {} {}", lender, rejectReason, merchantId);
                    createAndSaveLendingAuditTrial(merchantId, lender, "LENDER_REMOVED", rejectReason, evaluationId);
                    iterator.remove();
                    continue;
                }
            }

            Pair<Boolean, String> checkResponse = runLenderChecksForApplication(loan, lender, riskVariables, merchantId);
            if (!checkResponse.getKey()) {
                String rejectReason = "Failed lender-specific checks: " + checkResponse.getValue();
                AsyncLoggerUtil.logInfo(logger, "Removing lender: {} - {} for merchantId: {}", lender, rejectReason, merchantId);
                createAndSaveLendingAuditTrial(merchantId, lender, "LENDER_REMOVED", rejectReason, evaluationId);
                iterator.remove();
                continue;
            }

            if (additionalChecksFailed(merchantId, lender, merchantDetails, evaluationId)) {
                String rejectReason = "Failed additional merchant checks for merchantId";
                AsyncLoggerUtil.logInfo(logger, "Removing lender: {} - {} {}", lender, rejectReason, merchantId);
                createAndSaveLendingAuditTrial(merchantId, lender, "LENDER_REMOVED", rejectReason, evaluationId);
                iterator.remove();
                continue;
            }

           /* if (lenderRolloutFailedCheck(lender, merchantId, evaluationId)) {
                String rejectReason = "Failed rollout percentage check for merchantId";
                AsyncLoggerUtil.logInfo(logger, "Removing lender: {} - {} {}", lender, rejectReason, merchantId);
                createAndSaveLendingAuditTrial(merchantId, lender, "LENDER_REMOVED", rejectReason, evaluationId);
                iterator.remove();
                continue;
            }*/

            if (!negativeCategoryAndLoanAmountCheckPassed(loan, lendingRiskVariables.getRiskSegment(), lender,merchantId, evaluationId)) {
                AsyncLoggerUtil.logInfo(logger, "skipping {} due to business category check failure for {}", lender, merchantId);
                iterator.remove();
            }

            AsyncLoggerUtil.logDebug(logger, "Lender: {} passed all eligibility checks for merchantId: {}",
                    lender, merchantId);
        }

        int filteredCount = initialLenderCount - lenders.size();
        AsyncLoggerUtil.logInfo(logger, "EXIT {} - Completed filtering for merchantId: {}, filtered out {}/{} lenders",
                METHOD, merchantId, filteredCount, initialLenderCount);
        AsyncLoggerUtil.logInfo(logger, "Final eligible lenders: {} for merchantId: {}", lenders, merchantId);

        return lenders;
    }

    private boolean isRejectedLender(RiskVariablesDTO riskVariables, String lender, Long merchantId, String evaluationId) {
        if (riskVariables.getRejectedLenders().contains(loanUtil.getLenderRejectedMapping(lender))) {
            createAndSaveLendingAuditTrial(merchantId, lender, "LENDER_REMOVED", "Rejected lender", evaluationId);
            return true;
        }
        return false;
    }

    private boolean negativeCategoryAndLoanAmountCheckPassed(EligibleLoanDTO eligibleLoanDTO, String riskSegment, String lender, Long merchantId, String evaluationId) {
        if(RiskSegment.REPEAT.name().equalsIgnoreCase(riskSegment)){
            LendingApplication lastLmsDisbursedApplication = lendingApplicationDao.getLastLmsDisbursedLoan(merchantId);
            if(ObjectUtils.isEmpty(lastLmsDisbursedApplication)){
                AsyncLoggerUtil.logInfo(logger,"last lms disbursed application not available for checks for merchantId{}", merchantId);
                return true;
            }
            List<Long> lmsFieldIds = new ArrayList<>();
            lmsFieldIds.add(BUSINESS_CATEGORY_LMS_FIELD_ID);
            lmsFieldIds.add(BUSINESS_SUBCATEGORY_LMS_FIELD_ID);
            List<LmsFieldValues> lmsFieldValuesList = lmsFieldValuesDao.findByLendingApplicationIdAndFieldIdIn(
                    lastLmsDisbursedApplication.getId(), lmsFieldIds
            );
            if(ObjectUtils.isEmpty(lmsFieldValuesList)){
                AsyncLoggerUtil.logInfo(logger,"business category not available from last disbursed app for merchantId{}", merchantId);
                return true;
            }
            String businessCategory = null;
            String businessSubcategory = null;
            for(LmsFieldValues lmsFieldValues : lmsFieldValuesList){
                if(lmsFieldValues.getFieldId() == BUSINESS_CATEGORY_LMS_FIELD_ID){
                    businessCategory = lmsFieldValues.getFieldDropdownValue();
                }
                else if (lmsFieldValues.getFieldId() == BUSINESS_SUBCATEGORY_LMS_FIELD_ID){
                    businessSubcategory = lmsFieldValues.getFieldDropdownValue();
                }
            }

            LenderBusinessCategory lendingLenderBusinessCategory = lenderBusinessCategoryDao.findBusinessCategoryChecks(
                    lender, businessCategory, businessSubcategory
            );
            if(ObjectUtils.isEmpty(lendingLenderBusinessCategory)){
                AsyncLoggerUtil.logInfo(logger,"business category not available for merchantId {}, {}", merchantId, lender);
                return true;
            }
            if("INACTIVE".equalsIgnoreCase(lendingLenderBusinessCategory.getStatus())){
                AsyncLoggerUtil.logInfo(logger,"skipping lender {} due to negative category for merchantId {}", lender, merchantId);
                funnelService.submitEventV3(merchantId, null, null,
                        FunnelEnums.StageId.LENDER_ASSIGNMENT, FunnelEnums.StageEvent.LENDER_SKIPPED_NEGATIVE_CATEGORY, lender, LoanDetailsConstant.FUNNEL_VERSION_TAG);
                String remarks = "skipping lender " + lender + " due to lending business category status: " + lendingLenderBusinessCategory.getStatus() + " is inactive for " + merchantId;
                createAndSaveLendingAuditTrial(merchantId, lender, "LENDER_REMOVED", remarks, evaluationId);
                return false;
            }
            else if ("ACTIVE".equalsIgnoreCase(lendingLenderBusinessCategory.getStatus())){
                if(Objects.nonNull(lendingLenderBusinessCategory.getMaxAmount()) &&
                        (eligibleLoanDTO.getAmount() > lendingLenderBusinessCategory.getMaxAmount())
                ){
                    funnelService.submitEventV3(merchantId, null, null,
                            FunnelEnums.StageId.LENDER_ASSIGNMENT, FunnelEnums.StageEvent.LENDER_SKIPPED_CATEGORY_AMOUNT_LIMIT, lender, LoanDetailsConstant.FUNNEL_VERSION_TAG);
                    AsyncLoggerUtil.logInfo(logger,"skipping {} due to breach of business category amount limit fo merchantId {}", lender, merchantId);
                    String remarks = "skipping " + lender + " due to breach of business category amount limit: " + lendingLenderBusinessCategory.getMaxAmount() + "is less than lending application amount: " + eligibleLoanDTO.getAmount() + " for merchantId " + merchantId;
                    createAndSaveLendingAuditTrial(merchantId, lender, "LENDER_REMOVED", remarks, evaluationId);
                    return false;
                }
            }
        }
        else{
            List<LendingApplication> rejectedApplicationList = lendingApplicationDao.getLastThreeRejectedApplications(merchantId);
            for(LendingApplication rejectedApplication : rejectedApplicationList){
                if(rejectedApplication.getLender().equalsIgnoreCase(lender)){
                    if(NEGATIVE_BUSINESS_CATEGORY_REJECTION.equalsIgnoreCase(rejectedApplication.getManualKycReason()) ||
                            NEGATIVE_BUSINESS_CATEGORY_REJECTION.equalsIgnoreCase(rejectedApplication.getPhysicalReason())
                    ){
                        AsyncLoggerUtil.logInfo(logger,"skipping lender {} due to last rejected application on negative category for merchantId {}", lender, merchantId);
                        return false;
                    }
                }
            }
        }
        return true;
    }


    private boolean isPincodeEligible(String lender, EligibleLoanDTO loan, LendingRiskVariables lendingRiskVariables) {
        if (lenderEligiblePincodeCheckList.contains(lender)) {
            boolean shouldCheckPincode = !PAYU.name().equalsIgnoreCase(lender) || loan.getAmount() > 500000;
            if (shouldCheckPincode) {
                return lenderEligiblePincodesDao.findByLenderAndPincodeAndStatus(
                        lender, lendingRiskVariables.getPincode(), LenderEligiblePincodes.LenderEligiblePincodesStatus.ACTIVE
                ) != null;
            }
        }
        return true;
    }


    public boolean lenderBaseChecksCleared(EligibleLoanDTO eligibleLoan, String lender, EdiModel ediModel, RiskVariablesDTO riskVariables, Long merchantId, String evaluationId) {
        if(maxIrrCheckFailedV2(eligibleLoan,ediModel, lender, riskVariables, merchantId)) {
            AsyncLoggerUtil.logInfo(logger,"skipping {} due to lender pricing based maxIrr checks failing for {}", lender, merchantId);
            String remarks = "skipping " + lender + " due to maxIrr checks failing for " + merchantId;
            createAndSaveLendingAuditTrial(merchantId, lender, "LENDER_REMOVED", remarks, evaluationId);
            return false;
        }
        if(maxAprCheckFailedV2(eligibleLoan, ediModel, lender, riskVariables, merchantId)){
            AsyncLoggerUtil.logInfo(logger,"skipping {} due to lender pricing based maxApr checks failing for {}", lender, merchantId);
            String remarks = "skipping " + lender + " due to maxApr checks failing for " + merchantId;
            createAndSaveLendingAuditTrial(merchantId, lender, "LENDER_REMOVED", remarks, evaluationId);
            return false;
        }

        if(maxPfEligibleLender.contains(lender) && maxPfCheckFailedV2(eligibleLoan, merchantId,lender, riskVariables)){
            AsyncLoggerUtil.logInfo(logger,"skipping {} due to maxPf checks failing for {}", lender, merchantId);
            String remarks = "skipping " + lender + " due to maxPf checks failing for " + merchantId;
            createAndSaveLendingAuditTrial(merchantId, lender, "LENDER_REMOVED", remarks, evaluationId);
            return false;
        }
        return true;
    }


    private Pair<Boolean, String> runLenderChecksForApplication(EligibleLoanDTO eligibleLoanDTO, String lender, RiskVariablesDTO riskVariables, Long merchantId) {
        boolean success = true;
        String response = null;
        Lender lenderEnum = valueOf(lender);

        double loanAmount = eligibleLoanDTO.getAmount();
        Integer tenureInMonths = eligibleLoanDTO.getTenureInMonths();
        Double summaryTpv = riskVariables.getSummaryTpv();
        String riskSegment = riskVariables.getRiskSegment();
        if(!ObjectUtils.isEmpty(riskSegment)) {
            riskSegment = riskSegment.replace("%", "");
        }
        int maxTenure = riskVariables.getMaxTenure();
        double tpvOffer = riskVariables.getTpvOffer();
        if(maxTenure != 0 && tpvOffer != 0D && !ObjectUtils.isEmpty(tenureInMonths) && tenureInMonths != 0) {
            tpvOffer = (tpvOffer / maxTenure) * tenureInMonths;
        }

        double edi = eligibleLoanDTO.getEdi();
        if(!CollectionUtils.isEmpty(riskVariables.getPricingExperimentMap())) {
            PricingExperiment pricingExperiment = riskVariables.getPricingExperimentMap().get(merchantId);
            AsyncLoggerUtil.logInfo(logger,"experiment available for {}: {}",merchantId , pricingExperiment);
            Long payableDays = (long) OfferUtils.getEdiDays(tenureInMonths, LenderOffDays.valueOf(lender).getEdiModel());
            Double interestAmt = (loanAmount * (pricingExperiment.getInterestRate() * tenureInMonths) / 100) ;
            edi = ((loanAmount + interestAmt) / payableDays);
            edi = ediUtil.getEdiAfterRoundingOfferLogic(merchantId, edi, lender);
        }
        else {
            LendingLenderPricing lenderPricing = riskVariables.getLenderPricingMap().get(lender);
            if (!ObjectUtils.isEmpty(lenderPricing)) {
                Long payableDays = (long) OfferUtils.getEdiDays(tenureInMonths, LenderOffDays.valueOf(lender).getEdiModel());
                Double interestAmt = (loanAmount * (lenderPricing.getInterestRate() * tenureInMonths) / 100) ;
                edi = ((loanAmount + interestAmt) / payableDays);
                edi = ediUtil.getEdiAfterRoundingOfferLogic(merchantId, edi, lender);
            }
        }

        /*switch (lenderEnum) {
            case CAPRI:
                if (edi > summaryTpv) {
                    response = "skipping capri for merchantId : " + merchantId + " due to merchant edi: " + edi + " is greater than summaryTpv: " + summaryTpv;
                    success = false;
                    break;
                }
                break;
            case MUTHOOT:
                if (loanAmount > (Math.round(tpvOffer / 1000) * 1000)) {
                    response = "skipping muthoot for merchantId : " + merchantId + " due to merchant loan amount: " + loanAmount + " is greater than tpvOffer: " + (Math.round(tpvOffer / 1000) * 1000);
                    success = false;
                    break;
                }
                if (edi > 0.9 * riskVariables.getSummaryTpv()) {
                    response = "skipping muthoot for merchantId : " + merchantId + " due to merchant loan edi amount: " + edi + " is greater than 0.9 * summary_tpv " + 0.9 * summaryTpv;
                    success = false;
                    break;
                }
                break;
            case PAYU :
                if (loanAmount > (Math.ceil(tpvOffer / 10000) * 10000)) {
                    response = "skipping payU for merchantId : " + merchantId + " due to merchant loan amount: " + loanAmount + " is greater than tpvOffer: " + (Math.ceil(tpvOffer / 10000) * 10000);
                    success = false;
                    break;
                }
                if (edi > riskVariables.getSummaryTpv()) {
                    response = "skipping payu for merchantId: " + merchantId + " due to merchant loan edi amount: " + edi + " is greater than summary_tpv " + summaryTpv;
                    success = false;
                    break;
                }
                if(tenureInMonths >= 15 && riskVariables.getVintage() < payUConfig.getMinVintageForMoreThan15MonthsLoans()) {
                    response = "skipping " + lender + " due to vintage " + riskVariables.getVintage() + " less than " + payUConfig.getMinVintageForMoreThan15MonthsLoans() + " for " + tenureInMonths + " months loan tenure for merchantId "  + merchantId;
                    success = false;
                    break;
                }
                if(riskVariables.getVintage() <= 180 && tenureInMonths > payUConfig.getMaxLoanTenureFor180DaysVintage()) {
                    response = "skipping " + lender + " due to tenure " + tenureInMonths + " greater than " + payUConfig.getMaxLoanTenureFor180DaysVintage() + " for vintage less than equal to 180 for merchantId "  + merchantId;
                    success = false;
                    break;
                }
                break;
            case SMFG:
                if (edi > 0.7 * summaryTpv) {
                    response = "skipping smfg for merchantId : " + merchantId + " due to edi amount: " + edi + " is greater than 0.7 * summary_tpv " + 0.7 * summaryTpv;
                    success = false;
                    break;
                }
                if (REGULAR_ETC.name().equalsIgnoreCase(riskSegment)) {
                    String category = null, subcategory = null;
                    Optional<BasicDetailsDto> merchantDetailsOptional = merchantService.fetchMerchantBasicDetails(merchantId);
                    if (merchantDetailsOptional.isPresent()) {
                        category = merchantDetailsOptional.get().getBussinessCategory();
                        subcategory = merchantDetailsOptional.get().getSubCategory();
                    }
                    if (riskVariables.isSTPFlag() && loanAmount <= 200000 &&
                            (ObjectUtils.isEmpty(category) || ObjectUtils.isEmpty(subcategory))) {
                        response = "skipping smfg for merchantId: " + merchantId + " due to risk segment: " + riskVariables.getRiskSegment() + ", loan amount: " + loanAmount + " is smaller than 200000 and category " + category + " or subcategory " + subcategory + " is empty";
                        success = false;
                        break;
                    }
                }
                break;
            case OXYZO:

                AsyncLoggerUtil.logInfo(logger,"runLenderChecksForApplication for Oxyzo {} {} {}", riskVariables.getUnsecuredPos(), riskVariables.getMonthlyTpv(), loanAmount);

                if(edi > 0.7 * (riskVariables.getMonthlyTpv()/30)){
                    response = "skipping oxyzo for merchantId : " + merchantId + " due to edi amount: " + edi + " is greater than 0.7 * monthly_tpv " + 0.7 * (riskVariables.getMonthlyTpv()/30);
                    success = false;
                    break;
                }
                if(loanAmount > Math.ceil(tpvOffer)){
                    response = "skipping oxyzo for merchantId : " + merchantId + " due to merchant loan amount: " + loanAmount + " is greater than tpvOffer: " + (Math.ceil(tpvOffer));
                    success = false;
                    break;
                }
                if("R1".equalsIgnoreCase(riskVariables.getRiskGroup()) && ((loanAmount + riskVariables.getUnsecuredPos()) > (6 * riskVariables.getMonthlyTpv()))){
                    response = "skipping oxyzo for merchantId : " + merchantId + " due to merchant loan amount: " + loanAmount + "+ unsecuredPos : " + riskVariables.getUnsecuredPos() + " is greater than 6 * Monthly Adj TPV: " + 6 * riskVariables.getMonthlyTpv();
                    success = false;
                    AsyncLoggerUtil.logInfo(logger,"inside unsecuredPOs check for R1");
                    break;
                }
                if("R2".equalsIgnoreCase(riskVariables.getRiskGroup()) && ((loanAmount + riskVariables.getUnsecuredPos()) > (4.5 * riskVariables.getMonthlyTpv()))){
                    response = "skipping oxyzo for merchantId : " + merchantId + " due to merchant loan amount: " + loanAmount + "+ unsecuredPos : " + riskVariables.getUnsecuredPos() + " is greater than 4.5 * Monthly Adj TPV: " + 4.5 * riskVariables.getMonthlyTpv();
                    success = false;
                    AsyncLoggerUtil.logInfo(logger,"inside unsecuredPOs check for R2");
                    break;
                }
                break;
            default:
                AsyncLoggerUtil.logDebug(logger,"No specific checks found for lender : {} for merchantId : {}", lender, merchantId);
        }*/

        return new ImmutablePair<>(success, response);
    }

    public boolean maxIrrCheckFailedV2(EligibleLoanDTO eligibleLoan, EdiModel ediModel, String lender, RiskVariablesDTO riskVariables, Long merchantId) {
        BigDecimal maxIrr = BigDecimal.ZERO;
        double interestRate = eligibleLoan.getRateOfInterest();

        PricingExperiment pricingExperiment = null;
        if(pricingExpEnabled) {
            pricingExperiment = !CollectionUtils.isEmpty(riskVariables.getPricingExperimentMap()) ? riskVariables.getPricingExperimentMap().get(merchantId) : null;
        }
        if(!ObjectUtils.isEmpty(pricingExperiment)) {
            logger.info("Experiment fetched for {}: {}", merchantId, pricingExperiment);
            maxIrr = BigDecimal.valueOf(pricingExperiment.getIrr());
            interestRate = pricingExperiment.getInterestRate();
        }else {
            LendingLenderPricing lendingLenderPricing = !CollectionUtils.isEmpty(riskVariables.getLenderPricingMap()) ? riskVariables.getLenderPricingMap().get(lender) : null;
            logger.info("Lending Lender pricing fetched : {}", lendingLenderPricing);
            if (!ObjectUtils.isEmpty(lendingLenderPricing)) {
                maxIrr = BigDecimal.valueOf(lendingLenderPricing.getIrr());
                interestRate = lendingLenderPricing.getInterestRate();
            }
        }

        AsyncLoggerUtil.logInfo(logger,"loan amount : {}, edi model : {} for merchantId : {}", eligibleLoan.getAmount(), ediModel.getNoOfEdiDaysInAWeek(), merchantId);
        Double apr = getAprForBaseChecks(eligibleLoan, eligibleLoan.getAmount(), ediModel.getNoOfEdiDaysInAWeek(), lender, interestRate, merchantId);

        AsyncLoggerUtil.logInfo(logger,"Calculated IRR : {}, IRR in DB : {}, merchantId : {}", apr, maxIrr, merchantId);
        return BigDecimal.valueOf(apr).setScale(2, RoundingMode.DOWN).compareTo(maxIrr.setScale(2, RoundingMode.DOWN)) > 0;
    }

    public boolean isPanAndAadhaarLinked(Long merchantId) {
        LendingPancardDetails lendingPancardDetails = lendingPancardDetailsDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
        if (!ObjectUtils.isEmpty(lendingPancardDetails) && !ObjectUtils.isEmpty(lendingPancardDetails.getName()) && !ObjectUtils.isEmpty(lendingPancardDetails.getPancardNumber()) && !ObjectUtils.isEmpty(lendingPancardDetails.getDob())) {

            PanVerifyKYCResponseDto responseDto = kycHandler.verifyPanDetailsInternal(lendingPancardDetails.getPancardNumber(), lendingPancardDetails.getName(), lendingPancardDetails.getDob(), merchantId);

            if (!ObjectUtils.isEmpty(responseDto)) {

                String aadhaarSeedingStatus = !ObjectUtils.isEmpty(responseDto.getData())
                        && !ObjectUtils.isEmpty(responseDto.getData().getAadhaarSeedingStatus()) ? responseDto.getData().getAadhaarSeedingStatus() : null;

                if ("Y".equalsIgnoreCase(aadhaarSeedingStatus)) {
                    return true;
                }

            }

        }
        return false;
    }


    public Double getAprForBaseChecks(EligibleLoanDTO eligibleLoan, Double amountToCalculateAprOn, Integer ediModel, String lender, double interestRate, Long merchantId) {
        try{
            AsyncLoggerUtil.logInfo(logger,"calculating APR using Lender Pricing for merchantId : {}", merchantId);
            Double guess = 0.01;
            ArrayList<Double> values = new ArrayList<>();
            AsyncLoggerUtil.logInfo(logger,"amountToCalculateAprOn: {}", amountToCalculateAprOn);

            //Get Lender pricing config for APR calculation
            Double edi = Double.valueOf(eligibleLoan.getEdi());
            AsyncLoggerUtil.logInfo(logger,"Edi of merchantId : {} and lender ;{}  is {}", merchantId, lender, edi);
            Long payableDays = (long) OfferUtils.getEdiDays(eligibleLoan.getTenureInMonths(), LenderOffDays.valueOf(lender).getEdiModel());
            Double interestAmt = (eligibleLoan.getAmount() * (interestRate * eligibleLoan.getTenureInMonths()) / 100) ;
            double ediAmount = ((eligibleLoan.getAmount() + interestAmt) / payableDays);
            edi = ediUtil.getEdiAfterRoundingLogic(null, ediAmount, lender);
            AsyncLoggerUtil.logInfo(logger,"payable days : {}, loan amt : {}, interest rate : {}, edi : {}, interest amt : {}", payableDays, eligibleLoan.getAmount(), interestRate, edi, interestAmt);


            CommonResponse response = getEdiScheduleForEdi(eligibleLoan,edi, merchantId, lender);
            if(!response.isSuccess()){
                AsyncLoggerUtil.logInfo(logger,response.getMessage());
                AsyncLoggerUtil.logInfo(logger,"Unable to fetch edi schedule for APR calculation for merchantId : {}", merchantId);
                return null;
            }
            List<EdiScheduleV2DTO> ediSchedule = (List<EdiScheduleV2DTO>)response.getData();
            if(ObjectUtils.isEmpty(ediSchedule)){
                AsyncLoggerUtil.logInfo(logger,"Unable to fetch edi schedule for APR calculation for merchantId : {}", merchantId);
                return null;
            }
            values.add(0-amountToCalculateAprOn);
            for(int i = 0; i < ediSchedule.size(); i++){
                if(ediSchedule.get(i).getSerialNumber() == 0)continue;
                values.add(new Double(ediSchedule.get(i).getEdiAmount()));
                if((i+1) < ediSchedule.size()){
                    long diff = Math.abs(dateTimeUtil.getDateDiffInDays(ediSchedule.get(i).getDate(), ediSchedule.get(i+1).getDate()));
                    if(diff == 2){
                        values.add(0.0);
                    }
                }
            }
            int tenureInDays = values.size() - 1;
            Double apr = 0.0;
            double[] valuesDouble = new double[values.size()];
            for(int i = 0;i < values.size();i++)valuesDouble[i] = values.get(i);
            AsyncLoggerUtil.logInfo(logger,"valuesDouble Size : {}", valuesDouble.length);
            int daysInYear = (ediModel == 7 && Arrays.asList(Lender.ABFL.name(), Lender.TRILLIONLOANS.name(), Lender.CAPRI.name(), Lender.PAYU.name(),Lender.CREDITSAISON.name(), Lender.UGRO.name()).contains(lender)) ? 360 : 365;
            AsyncLoggerUtil.logInfo(logger,"days in year : {} for application id : {}", daysInYear, merchantId);
            apr = LoanCalculationUtil.irr(valuesDouble, guess) * daysInYear;
            if(apr.isNaN()){
                AsyncLoggerUtil.logInfo(logger,"APR : {}", apr);
                return null;
            }
            AsyncLoggerUtil.logInfo(logger,"APR : {}", apr);
            return apr * 100;
        }
        catch(Exception e){
            logger.error("Unable to calculate APR for applicationId : {} Exception : {}, stacktrace : {}",merchantId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }


    public boolean maxAprCheckFailedV2(EligibleLoanDTO eligibleLoan, EdiModel ediModel, String lender, RiskVariablesDTO riskVariables, Long merchantId) {
        BigDecimal maxApr = BigDecimal.ZERO;
        double interestRate = eligibleLoan.getRateOfInterest();
        double processingFee = eligibleLoan.getProcessingFee();

        PricingExperiment pricingExperiment = null;
        if(pricingExpEnabled) {
            pricingExperiment = !CollectionUtils.isEmpty(riskVariables.getPricingExperimentMap()) ? riskVariables.getPricingExperimentMap().get(eligibleLoan.getMerchantId()) : null;
        }
        if(!ObjectUtils.isEmpty(pricingExperiment)) {
           AsyncLoggerUtil.logInfo(logger,"Experiment fetched for {}: {}", eligibleLoan.getMerchantId(), pricingExperiment);
            maxApr = BigDecimal.valueOf(pricingExperiment.getApr());
            processingFee = eligibleLoan.getAmount() * (pricingExperiment.getProcessingFeeRate() / 100);
            interestRate = pricingExperiment.getInterestRate();
        }else {
            LendingLenderPricing lendingLenderPricing = !CollectionUtils.isEmpty(riskVariables.getLenderPricingMap()) ? riskVariables.getLenderPricingMap().get(lender) : null;
            AsyncLoggerUtil.logInfo(logger,"Lending Lender pricing fetched : {}", lendingLenderPricing);

            if (!ObjectUtils.isEmpty(lendingLenderPricing)) {
                maxApr = BigDecimal.valueOf(lendingLenderPricing.getApr());
                processingFee = eligibleLoan.getAmount() * (lendingLenderPricing.getProcessingFeeRate() / 100);
                interestRate = lendingLenderPricing.getInterestRate();
            }
        }

        AsyncLoggerUtil.logInfo(logger,"Processing fee {}, loan amount : {}, edi model : {} for merchantId: {}", processingFee, eligibleLoan.getAmount(), ediModel.getNoOfEdiDaysInAWeek(), merchantId);
        Double apr = getAprForBaseChecks(eligibleLoan, eligibleLoan.getAmount() - processingFee, ediModel.getNoOfEdiDaysInAWeek(), lender, interestRate,  merchantId);
        AsyncLoggerUtil.logInfo(logger,"Calculated APR : {}, APR in DB : {}, merchantId : {}", apr, maxApr, merchantId);
        return BigDecimal.valueOf(apr).setScale(2, RoundingMode.DOWN).compareTo(maxApr.setScale(2, RoundingMode.DOWN)) > 0;
    }

    private boolean maxPfCheckFailedV2(EligibleLoanDTO eligibleLoan, Long merchantId, String lender, RiskVariablesDTO riskVariables) {
        Double processingFee = Double.valueOf(eligibleLoan.getProcessingFee());
        Double loanAmount= eligibleLoan.getAmount();
        AsyncLoggerUtil.logInfo(logger,"PF generated for application_id:{} PF:{} and lender:{}", merchantId, processingFee, lender);
        Double pfPercentage = (processingFee/loanAmount)*100D;

        PricingExperiment pricingExperiment = null;
        if(pricingExpEnabled) {
            pricingExperiment = !CollectionUtils.isEmpty(riskVariables.getPricingExperimentMap()) ? riskVariables.getPricingExperimentMap().get(merchantId) : null;
        }
        if(!ObjectUtils.isEmpty(pricingExperiment)) {
            AsyncLoggerUtil.logInfo(logger,"Experiment fetched for {}: {}", merchantId, pricingExperiment);
            pfPercentage = pricingExperiment.getProcessingFeeRate();
        }else {
            LendingLenderPricing lendingLenderPricing = !CollectionUtils.isEmpty(riskVariables.getLenderPricingMap()) ? riskVariables.getLenderPricingMap().get(lender) : null;
            AsyncLoggerUtil.logInfo(logger,"Lending Lender pricing fetched : {}", lendingLenderPricing);
            if (!ObjectUtils.isEmpty(lendingLenderPricing)) {
                pfPercentage = lendingLenderPricing.getProcessingFeeRate();
            }
        }

        Double maxPf = 0D;
        switch (lender) {
            case "SMFG":
                maxPf = smfgConfig.getMaxProcessingFee();
                break;
            default:
                maxPf = 0D;
        }
        return pfPercentage > maxPf;
    }


    public boolean additionalChecksFailed(Long  merchantId, String lender, BasicDetailsDto merchantDetails, String evaluationId){
        AsyncLoggerUtil.logInfo(logger,"Running additional checks for lender:{}", lender);
        boolean flag = false;
        if(ABFL.equals(lender)){
            if(ObjectUtils.isEmpty(merchantDetails)){
                merchantDetails=merchantService.fetchMerchantDetails(merchantId).getMerchantDetail();
            }
            BureauResponseDTO responseDTO = null;
            if(!ObjectUtils.isEmpty(merchantDetails)){
                responseDTO = bureauHandler.getBureauData(merchantDetails.getPanNumber(), merchantDetails.getId(), merchantDetails.getMobile(), 30L);
            }
            if(ObjectUtils.isEmpty(responseDTO) || ObjectUtils.isEmpty(responseDTO.getVariables()) || ObjectUtils.isEmpty(responseDTO.getVariables().getMaxDpd6Months())){
                flag = false;
            } else{
                flag =  responseDTO.getVariables().getMaxDpd6Months()>=30;
                if (flag) {
                    String remarks = "skipping " + lender + " due to max Dpd 6 months: " + responseDTO.getVariables().getMaxDpd6Months() + " is greater than 30 for " + merchantId;
                    createAndSaveLendingAuditTrial(merchantId, lender, "LENDER_REMOVED", remarks, evaluationId);
                }
            }
        }
        return flag;
    }


    public List<EligibleOffersResponseDTO.LenderData> getLenderData(List<String> eligibleLenders, EligibleLoanDTO eligibleLoanDTO, LendingRiskVariables lendingRiskVariables, Long merchantId) {
        try {
            Double amount = eligibleLoanDTO.getAmount();
            Integer tenureInMonths = eligibleLoanDTO.getTenureInMonths();
            if(!ObjectUtils.isEmpty(eligibleLenders)) {
                List<LendingLenderQuota> lenderLimits;
                lenderLimits = lenderDisbursalLimitsDao.fetchEligibleLenderLimits(eligibleLenders, eligibleLoanDTO.getAmount());
                eligibleLenders.clear();
                AsyncLoggerUtil.logInfo(logger,"lender limits : {} for merchantId: {}", lenderLimits, merchantId);
                if (Objects.nonNull(lenderLimits)) {
                    for (LendingLenderQuota lendingLenderQuota : lenderLimits) {
                        eligibleLenders.add(lendingLenderQuota.getLender());
                    }
                    AsyncLoggerUtil.logInfo(logger,"eligible lenders: {} for merchantId: {}", eligibleLenders, merchantId);
                }
            }
            List<EligibleOffersResponseDTO.LenderData> eligibleLenderList = new ArrayList<>();

            for (String lender : eligibleLenders) {
                LendingLenderPricing lendingLenderPricing = null;
                PricingExperiment pricingExperiment = null;
                if(loanUtil.isLenderPricingApplicableMerchant(merchantId)) {
                    if(pricingExpEnabled) {
                        pricingExperiment = pricingExperimentDao.findBySegmentAndRiskGroupAndTenureInMonthsAndMidEndsWithAndPincodeColor(lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(),
                                eligibleLoanDTO.getTenureInMonths(), (int) (merchantId % 10), lendingRiskVariables.getPincodeColor().name(), Date.from(Instant.now()));
                    }
                    lendingLenderPricing = lendingLenderPricingDao.findBySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColor(lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getRiskGroup(), eligibleLoanDTO.getTenureInMonths(), lender,
                            lendingRiskVariables.getPincodeColor().name(), Date.from(Instant.now()));
                }
                logger.info("adding lender {} to list", lender);
                Double apr;
                Double irr = null;
                Double interestRate = null;
                DecimalFormat df = new DecimalFormat("0.00");
                Double edi;
                Double processingFee;
                if(!ObjectUtils.isEmpty(pricingExperiment)) {
                    String formattedRate = df.format(pricingExperiment.getInterestRate());
                    interestRate = Double.parseDouble(formattedRate);
                    processingFee = Math.ceil(amount * (pricingExperiment.getProcessingFeeRate() / 100));
                    Long payableDays = (long) OfferUtils.getEdiDays(tenureInMonths, LenderOffDays.valueOf(lender).getEdiModel());
                    Double interestAmt = (amount * (pricingExperiment.getInterestRate() * tenureInMonths) / 100) ;
                    Double ediAmount = ((amount + interestAmt) / payableDays);
                    edi = ediUtil.getEdiAfterRoundingOfferLogic(merchantId, ediAmount, lender);
                    apr = lendingApplicationServiceV2.getApr(Math.toIntExact(eligibleLoanDTO.getEdiCount()),edi,amount - processingFee, merchantId, lender);
                    irr = lendingApplicationServiceV2.getApr(eligibleLoanDTO.getEdiCount().intValue(), edi,amount, merchantId, lender);
                }
                else if(!ObjectUtils.isEmpty(lendingLenderPricing) && loanUtil.isLenderPricingApplicableMerchant(merchantId)){
                    processingFee =Math.ceil(amount * (lendingLenderPricing.getProcessingFeeRate() / 100));
                    String formattedRate = df.format(lendingLenderPricing.getInterestRate());
                    interestRate = Double.parseDouble(formattedRate);
                    Long payableDays = (long) OfferUtils.getEdiDays(tenureInMonths, LenderOffDays.valueOf(lender).getEdiModel());
                    Double interestAmt = (amount * (lendingLenderPricing.getInterestRate() * tenureInMonths) / 100) ;
                    Double ediAmount = ((amount + interestAmt) / payableDays);
                    edi = ediUtil.getEdiAfterRoundingOfferLogic(merchantId, ediAmount, lender);
                    apr = lendingApplicationServiceV2.getApr(Math.toIntExact(eligibleLoanDTO.getEdiCount()),edi,amount - processingFee, merchantId, lender);
                    irr = lendingApplicationServiceV2.getApr(eligibleLoanDTO.getEdiCount().intValue(), edi,amount, merchantId, lender);
                }
                else{
                    processingFee =Math.ceil(amount * (lendingLenderPricing.getProcessingFeeRate() / 100));
                    String formattedRate = df.format(lendingLenderPricing.getInterestRate());
                    interestRate = Double.parseDouble(formattedRate);
                    Long payableDays = (long) OfferUtils.getEdiDays(tenureInMonths, LenderOffDays.valueOf(lender).getEdiModel());
                    Double interestAmt = (amount * (lendingLenderPricing.getInterestRate() * tenureInMonths) / 100) ;
                    Double ediAmount = ((amount + interestAmt) / payableDays);
                    edi = ediUtil.getEdiAfterRoundingOfferLogic(merchantId, ediAmount, lender);
                    apr = lendingApplicationServiceV2.getApr(Math.toIntExact(eligibleLoanDTO.getEdiCount()),edi,amount - processingFee, merchantId, lender);
                    irr = lendingApplicationServiceV2.getApr(eligibleLoanDTO.getEdiCount().intValue(), edi,amount, merchantId, lender);
                }
                EligibleOffersResponseDTO.LenderData lenderData = new EligibleOffersResponseDTO.LenderData();
                lenderData.setPenaltyConfigs(getPenaltyConfig(lender));
                lenderData.setLenderName(lender);
                lenderData.setApr(apr);
                lenderData.setIrr(irr);
                lenderData.setEdi(edi);
                lenderData.setProcessingFee(processingFee);
                lenderData.setRepaymentAmount((int) (edi * eligibleLoanDTO.getEdiCount()));
                //lenderData.setRejected(Objects.nonNull(prevAssignedLenders) && prevAssignedLenders.contains(lender));
                //lenderData.setApprovalRate(getPropensityMatrix(valueOf(lender)));
                lenderData.setForeClosureDetails(getForeclosureAmount(valueOf(lender)));
                lenderData.setNachBounceAmount(getNachBounceAmount(valueOf(lender)));
                lenderData.setInterestRate(interestRate);
                eligibleLenderList.add(lenderData);
            }
            AsyncLoggerUtil.logInfo(logger,"eligible lenders after sorting:{}", eligibleLenderList);

            return eligibleLenderList;
        } catch (Exception ex) {
            AsyncLoggerUtil.logInfo(logger,"exception occurred:{},{}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            return null;
        }
    }

    private static final Map<Lender, Integer> NACH_BOUNCE_AMOUNT_MAP;

    static {
        NACH_BOUNCE_AMOUNT_MAP = new HashMap<>();
        NACH_BOUNCE_AMOUNT_MAP.put(TRILLIONLOANS, 500);
        NACH_BOUNCE_AMOUNT_MAP.put(PAYU, 500);
        NACH_BOUNCE_AMOUNT_MAP.put(PIRAMAL, 650);
        NACH_BOUNCE_AMOUNT_MAP.put(LIQUILOANS, 650);
        NACH_BOUNCE_AMOUNT_MAP.put(CREDITSAISON, 650);
        NACH_BOUNCE_AMOUNT_MAP.put(LIQUILOANS_P2P, 650);
        NACH_BOUNCE_AMOUNT_MAP.put(LIQUILOANS_P2P_OF, 650);
    }

    public Integer getNachBounceAmount(Lender lender) {
        return NACH_BOUNCE_AMOUNT_MAP.getOrDefault(lender, null);
    }

    List<EligibleOffersResponseDTO.ForeClosureEntityDTO> getForeclosureAmount(Lender lender){

        List<ForeClosureConfig> foreClosureConfigs = foreClosureDao.findByLender(lender.name());
        return convertToForeClosureEntityDto(foreClosureConfigs);

    }

    private List<EligibleOffersResponseDTO.ForeClosureEntityDTO> convertToForeClosureEntityDto(List<ForeClosureConfig> foreClosureConfigs) {
        List<EligibleOffersResponseDTO.ForeClosureEntityDTO> foreClosureEntityDTOList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(foreClosureConfigs)) {
            for (ForeClosureConfig foreClosureConfig: foreClosureConfigs) {
                EligibleOffersResponseDTO.ForeClosureEntityDTO foreClosureEntityDTO = new EligibleOffersResponseDTO.ForeClosureEntityDTO();
                foreClosureEntityDTO.setRate(foreClosureConfig.getRate());
                foreClosureEntityDTO.setDurationFrom(foreClosureConfig.getDurationFrom());
                foreClosureEntityDTO.setDurationTo(foreClosureConfig.getDurationTo());
                foreClosureEntityDTO.setMinAmount(foreClosureConfig.getMinAmount());
                foreClosureEntityDTO.setTenure(foreClosureConfig.getTenure());
                foreClosureEntityDTOList.add(foreClosureEntityDTO);
            }
        }
        return foreClosureEntityDTOList;
    }

    public List<EligibleOffersResponseDTO.PenaltyConfig> getPenaltyConfig(String lender) {
        List<PenaltyFeeConfigSlave> penaltyFeeConfigSlaves = penaltyFeeConfigDaoSlave.findByVersionAndStatusAndLenderOrderByMinAmountAsc(2D, true, lender);
        AsyncLoggerUtil.logInfo(logger,"penal charges for lender:{}:{}", lender, penaltyFeeConfigSlaves);
        List<EligibleOffersResponseDTO.PenaltyConfig> penaltyConfigs = new ArrayList<>();
        if (!ObjectUtils.isEmpty(penaltyFeeConfigSlaves)) {
            for (PenaltyFeeConfigSlave penaltyFeeConfigSlave : penaltyFeeConfigSlaves) {
                EligibleOffersResponseDTO.PenaltyConfig penaltyConfig = new EligibleOffersResponseDTO.PenaltyConfig();
                penaltyConfig.setMinAmount(penaltyFeeConfigSlave.getMinAmount());
                penaltyConfig.setMaxAmount(penaltyFeeConfigSlave.getMaxAmount());
                penaltyConfig.setPenalty(penaltyFeeConfigSlave.getPenalty());
                penaltyConfigs.add(penaltyConfig);
            }
        }
        return penaltyConfigs;
    }

    List<String> getLenderList(List<LenderAssignmentRules> lenderAssignmentRules, EdiModel ediModel, String assignedLender, Long merchantId, String evaluationId) {
        AsyncLoggerUtil.logInfo(logger,"Assigned Lender: {}  EdiModel: {}", assignedLender, ediModel );
        Set<String> eligibleLendersSet = new LinkedHashSet<>();
        AsyncLoggerUtil.logInfo(logger,"lender assignment rules: {}", lenderAssignmentRules);
        AsyncLoggerUtil.logInfo(logger,"is internal merchant {}", loanUtil.isInternalMerchant(merchantId));
        for(LenderAssignmentRules rule:lenderAssignmentRules){
            String lender = rule.getLender();
            AsyncLoggerUtil.logInfo(logger,"running skip check for lender {} for  {}", lender, merchantId);
            if(ObjectUtils.isEmpty(ediModel) || ediModel.name().equals(LenderOffDays.valueOf(lender).getEdiModel().name())){
                if(!ObjectUtils.isEmpty(assignedLender) && rule.getLender().equals(assignedLender)){
                    AsyncLoggerUtil.logInfo(logger,"lender change workflow, skip {} for {}", lender, merchantId);
                    continue;
                }
                AsyncLoggerUtil.logInfo(logger,"adding {} to the eligible list for merchantId: {}", lender, merchantId);
                if(PIRAMAL.name().equalsIgnoreCase(lender) && !loanUtil.isInternalMerchant(merchantId) && !easyLoanUtil.percentScaleUp(merchantId, piramalRolloutPercentage)) {
                    AsyncLoggerUtil.logInfo(logger,"removing {} from eligible list for merchantId : {} due to not in rollout percentage {}", lender, merchantId, piramalRolloutPercentage);
                    String remarks = "removing " + lender + " from eligible list for merchantId : " + merchantId + " due to not in rollout percentage " + piramalRolloutPercentage;
                    createAndSaveLendingAuditTrial(merchantId, lender, "LENDER_REMOVED", remarks, evaluationId);
                    continue;
                }/*
                if(lenderRolloutFailedCheck(lender, merchantId, evaluationId)) {
                    continue;
                }*/
                eligibleLendersSet.add(lender);
            }
        }
        List<String> eligibleLenders = new ArrayList<>(eligibleLendersSet);
        AsyncLoggerUtil.logInfo(logger,"Eligible Lenders: {}", eligibleLenders);
        return eligibleLenders;
    }

    private void createAndSaveLendingAuditTrial(Long merchantId,  String oldStatus, String type, String remarks, String evaluationId) {
        try {
            logger.info("Auditing lender remove log for merchantId {}", merchantId);
            LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
            lendingAuditTrial.setMerchantId(merchantId);
            lendingAuditTrial.setOldStatus(oldStatus);
            lendingAuditTrial.setType(type);
            lendingAuditTrial.setEvaluationId(evaluationId);
            lendingAuditTrial.setRemarks(remarks);
            lendingAuditTrialDao.save(lendingAuditTrial);
            logger.info("Details getting saved in Lending audit Trial");
        } catch (Exception e) {
            logger.info("Exception in saving lender remove log for merchantId {} {}", merchantId, Arrays.asList(e.getStackTrace()));
        }
    }


    private boolean lenderRolloutFailedCheck(String lender, Long merchantId, String evaluationId) {
        List<Lender> skipRolloutCheckForLenders = Arrays.asList(LDC, MAMTA, HINDON, LIQUILOANS, LIQUILOANS_NBFC, LIQUILOANS_P2P, LIQUILOANS_P2P_OF, MAMTA0, MAMTA1, MAMTA2, ABFL,PIRAMAL);
        if(skipRolloutCheckForLenders.contains(valueOf(lender))) {
            return false;
        }
        Integer rolloutPercent = 0;
        switch (lender) {
            case "USFB":
                rolloutPercent = usfbRolloutPercentage;
                break;
            case "TRILLIONLOANS":
                rolloutPercent = trillionLoansRolloutPercentage;
                break;
            case "MUTHOOT":
                rolloutPercent = muthootRolloutPercentage;
                break;
            case "CAPRI":
                rolloutPercent = capriRolloutPercent;
                break;
            case "PAYU":
                rolloutPercent = payuRolloutPercent;
                break;
            case "CREDITSAISON":
                rolloutPercent = creditSaisonConfig.getRolloutPercent();
                break;
            case "SMFG":
                rolloutPercent = smfgConfig.getRolloutPercentage();
                break;
            case "UGRO":
                rolloutPercent = ugroConfig.getRolloutPercentage();
                break;
            case "OXYZO":
                rolloutPercent = oxyzoConfig.getRolloutPercentage();
                break;
            default:
                rolloutPercent = 0;
        }
        if(!loanUtil.isInternalMerchant(merchantId) && !easyLoanUtil.percentScaleUp(merchantId, rolloutPercent)) {
            AsyncLoggerUtil.logInfo(logger,"removing {} from eligible lender list for merchantId : {} due to not in rollout percentage {}", lender, merchantId, rolloutPercent);
            String remarks = "removing " + lender + " from eligible list for merchantId : " + merchantId + " due to not in rollout percentage " + rolloutPercent;
            createAndSaveLendingAuditTrial(merchantId, lender, "LENDER_REMOVED", remarks, evaluationId);
            return true;
        }
        return false;
    }

    public CommonResponse getEdiScheduleForEdi(EligibleLoanDTO eligibleLoan, Double edi, Long merchantId, String lender ) {
        AsyncLoggerUtil.logInfo(logger,"Creating EDI Schedule V2 for merchantId:{}", merchantId);
        try {
            if (eligibleLoan == null) {
                return new CommonResponse(false, "eligible loan not found");
            }

            int installmentNo = 1;
            int ediCount = eligibleLoan.getEdiCount();
            Double openingBalance = eligibleLoan.getAmount();
            double totalInterest = 0D;
            Double totalPrincipal = 0D;
            List<EdiScheduleV2DTO> ediSchedules = new ArrayList<>();
            Calendar cal = Calendar.getInstance();

            double reducingInterestRateDaily =
                    Finance.rate(ediCount, edi.intValue(), eligibleLoan.getAmount());
            int normalEdIinstallmentNo = 1;
            while (normalEdIinstallmentNo <= ediCount) {
                Double principal = round(Finance.ppmt(reducingInterestRateDaily, normalEdIinstallmentNo, ediCount, -1 * eligibleLoan.getAmount()));
                double interest = round(edi.intValue() - principal);

                if (Lender.PIRAMAL.name().equalsIgnoreCase(lender)) {
                    interest = roundToWhole(interest);
                    principal = edi.intValue() - interest;
                }

                if(normalEdIinstallmentNo == ediCount && !eligibleLoan.getAmount().equals(totalPrincipal + principal)) {
                    double diff = eligibleLoan.getAmount() - (totalPrincipal + principal);
                    principal = round(eligibleLoan.getAmount() - totalPrincipal);
                    interest = round(interest - diff < 0 ? 0 : interest - diff);
                }
                totalPrincipal = totalPrincipal + principal;
                totalInterest = totalInterest + interest;
                EdiScheduleV2DTO currentSchedule = new EdiScheduleV2DTO();
                currentSchedule.setSerialNumber(installmentNo);
                currentSchedule.setInterest(interest);
                currentSchedule.setPrincipal(principal);
                currentSchedule.setEdiAmount((int) (principal + interest));
                currentSchedule.setDate(cal.getTime());
                currentSchedule.setBalance(round(openingBalance));
                ediSchedules.add(currentSchedule);
                openingBalance = openingBalance - principal;
                installmentNo++;
                normalEdIinstallmentNo++;
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            return new CommonResponse(true, "success", ediSchedules);
        } catch(Exception ex) {
            logger.error("Exception while creating schedule V2 for merchantId {}, Exception is {}, Stacktrace : {}", merchantId, ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        return new CommonResponse(false, "Something went wrong");
    }

    private static double round(double value) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private static double roundToWhole(double value) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(0, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private String generateEligibilityCacheKey(Long merchantId, Double amount, Integer ediModel) {
        return String.format("eligibility:v2:%d:%.2f:%d",
                merchantId,
                amount,
                ediModel != null ? ediModel : 0);
    }

    private void cacheEligibilityResponse(String cacheKey, EligibleOffersResponseDTO response, Long merchantId) {
        try {
            AddCacheDto cacheDto = new AddCacheDto();
            cacheDto.setKey(cacheKey);
            cacheDto.setValue(response);
            cacheDto.setTtl(600); // 10 minutes TTL
            cacheDto.setVersion(String.valueOf(System.currentTimeMillis())); // Version tracking for invalidation
            lendingCache.add(cacheDto);
            logger.debug("Cached eligibility response with key: {}", cacheKey);
        } catch (Exception e) {
            logger.warn("Failed to cache eligibility response: {}", e.getMessage());
        }
    }

    private EligibleLendingOffersResponseDTO.TenureDetails convertLoanToTenureDetails(
            LendingEligibleLoan eligibleLoan, EligibleLendingOffersResponseDTO responseDTO, MaxPricingValuesDTO maxPricingValuesDTO) {
        Double irr;
        Double apr;
        Double processingFee;
        Double interestRate;
        int edi;
        int repayment;
        int ediCount = eligibleLoan.getEdiCount();

        if (ObjectUtils.isEmpty(maxPricingValuesDTO)){
            irr = lendingApplicationServiceV2.getApr(eligibleLoan.getEdiCount(), Double.valueOf(eligibleLoan.getEdi()), eligibleLoan.getAmount(), eligibleLoan.getMerchantId(), null);
            apr = lendingApplicationServiceV2.getApr(eligibleLoan.getEdiCount(), Double.valueOf(eligibleLoan.getEdi()), eligibleLoan.getAmount()-eligibleLoan.getProcessingFee(), eligibleLoan.getMerchantId(), null);
            processingFee = Double.valueOf(eligibleLoan.getProcessingFee());
            interestRate = eligibleLoan.getRateOfInterest();
            edi = eligibleLoan.getEdi();
            repayment = eligibleLoan.getRepayment();
        }
        else {
            processingFee = Math.ceil(maxPricingValuesDTO.getMaxProcessingFeeRate() * eligibleLoan.getAmount()/100);
            interestRate = maxPricingValuesDTO.getMaxInterestRate();
            edi = (int) Math.ceil((eligibleLoan.getAmount() + (interestRate * eligibleLoan.getAmount() * eligibleLoan.getTenureInMonths())/100) / ediCount);
            repayment = edi * ediCount;
            irr = lendingApplicationServiceV2.getApr(ediCount, Double.valueOf(edi), eligibleLoan.getAmount(), eligibleLoan.getMerchantId(), null);
            apr = lendingApplicationServiceV2.getApr(ediCount, Double.valueOf(edi), eligibleLoan.getAmount()-processingFee, eligibleLoan.getMerchantId(), null);
        }
        EligibleLendingOffersResponseDTO.TenureDetails tenureDetails = responseDTO.new TenureDetails();
        tenureDetails.setTenure(eligibleLoan.getTenure());
        tenureDetails.setCategory(eligibleLoan.getCategory());
        tenureDetails.setEdi(edi);
        tenureDetails.setIoEdi(eligibleLoan.getIoEdi());
        tenureDetails.setFinanceCharge(processingFee.intValue());
        tenureDetails.setRateOfInterest(interestRate);
        tenureDetails.setRepaymentAmount(repayment);
        tenureDetails.setEdiCount(ediCount);
        tenureDetails.setTenureInMonths(eligibleLoan.getTenureInMonths());
        tenureDetails.setIrr(irr);
        tenureDetails.setApr(apr);
        return tenureDetails;
    }

    public ResponseDTO updateEligibleLoan(Long merchantId, EligibleLoanUpdateRequestDTO body) {
        ResponseDTO responseDTO = new ResponseDTO();
        // todo final fix this later
        Date dateWindow = dateTimeUtil.getDatePlusDays(dateTimeUtil.getCurrentDate(), -24 );
        if (loanUtil.isInternalMerchant(merchantId)) {
            body.setEdiDays( body.getEdiDays() == null ? body.getTenure() * 30 : body.getEdiDays());
//            dateWindow = dateTimeUtil.getDatePlusMinutes(dateTimeUtil.getCurrentDate(), -5);
        }
        try {
        logger.info("LendingEligibleLoan Query values merchantId : {}, amount : {}, tenure : {}, dateWindow : {}", merchantId, body.getAmount(), body.getTenure(), dateWindow);
        LendingEligibleLoan eligibleLoan = null;
        if (body.getEdiDays() == null) {
            eligibleLoan = eligibleLoanDao.findTopByMerchantIdAndAmountAndTenureInMonthsAndCreatedAtIsGreaterThanEqualAndLoanTypeNotInOrderByIdDesc(merchantId, body.getAmount(), body.getTenure(), dateWindow, topupLoans);
        } else {
            eligibleLoan = eligibleLoanDao.findTopByMerchantIdAndAmountAndEdiCountAndCreatedAtIsGreaterThanEqualAndLoanTypeNotInOrderByIdDesc(merchantId, body.getAmount(), body.getEdiDays(),dateWindow, topupLoans);
        }
            logger.info("eligibleLoan merchant_id : {}", eligibleLoan);
//        LendingEligibleLoan eligibleLoan = eligibleLoanDao.findTopByMerchantIdAndAmountAndTenureInMonthsOrderByIdDesc(merchantId, body.getAmount(), body.getTenure());
        if (Objects.nonNull(eligibleLoan)) {
            LendingEligibleLoan customLoan = new LendingEligibleLoan(eligibleLoan);
            customLoan.setOfferType("CUSTOM");
            eligibleLoanDao.save(customLoan);
            logger.info("Eligible loan custom offer for merchantId : {} {}", merchantId, customLoan);
            eligibleLoanAuditDao.save(EligibleLoanAudit.createObject(customLoan));
            responseDTO.setMessage("Created eligible loan entry successfully");
            responseDTO.setSuccess(true);
            Map<String,Object> map = new HashMap<>();
            map.put("isAggregationFlowApplicable", loanUtil.isApplicableForAggregationFlow(merchantId, null));
            responseDTO.setData(map);
            return responseDTO;
        }
        } catch (Exception e) {
            logger.error("Error occurred while creating custom offer for merchantId : {} {}", merchantId, Arrays.asList(e.getStackTrace()));
        }

        logger.info("Could not create custom offer for the merchant : {}", merchantId);
        responseDTO.setMessage("No eligible loan entry found");
        responseDTO.setSuccess(false);
        return responseDTO;
    }

    public ApplicationDerogResponseDTO processDerogSince(Long merchantId, Long applicationId, int daysDiffToCheck) {
        ApplicationDerogResponseDTO responseDTO = new ApplicationDerogResponseDTO();
        Date reportDate = null;
//        Optional<Merchant> merchantOptional = merchantDao.findById(merchantId);

        Optional<BasicDetailsDto> merchantOptional = merchantService.fetchMerchantBasicDetails(merchantId);
        if (!merchantOptional.isPresent()) {
            logger.info("Merchant not found for merchantId: {}", merchantId);
            responseDTO.setMessage("Merchant not found");
            responseDTO.setIsRejected(false);
            responseDTO.setSuccess(false);
            return responseDTO;
        }
        BasicDetailsDto merchant = merchantOptional.get();
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchant.getId());
        if (lendingApplication == null) {
            logger.info("Application not found for applicationId: {}", applicationId);
            responseDTO.setMessage("Application not found");
            responseDTO.setIsRejected(false);
            responseDTO.setSuccess(false);
            return responseDTO;
        }
        Experian experian = experianDao.getByMerchantId(merchantId);
        if (experian == null) {
            logger.info("Experian not found for merchantId: {}", merchantId);
            responseDTO.setMessage("Experian not found");
            responseDTO.setIsRejected(false);
            responseDTO.setSuccess(false);
            return responseDTO;
        }
        JsonNode bureauResponse = null;
        ResponseUtil creditBureauResponseUtil = getCreditBureauResponse(experian);
        if (creditBureauResponseUtil.isValid(experian.getPancardNumber(), merchant.getMobile())) {
            reportDate = creditBureauResponseUtil.getReportDate();
        }
        if (reportDate == null || LoanUtil.getDateDiffInDays(reportDate, new Date()) >= daysDiffToCheck) {
            final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchant.getId());
            BankDetailsDto merchantBankDetail = null;
            if (bankDetailsDtoOptional.isPresent())
                merchantBankDetail = bankDetailsDtoOptional.get();
            if (merchantBankDetail == null) {
                logger.info("MerchantBankDetail not found for merchantId: {}", merchantId);
                responseDTO.setMessage("Experian not found");
                responseDTO.setIsRejected(false);
                responseDTO.setSuccess(false);
                return responseDTO;
            }
//            MerchantSummary merchantSummary = merchantSummaryDao.findByMerchantId(merchantId);
            MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(merchantId);
            if (ObjectUtils.isEmpty(merchantResponseDTO)) {
                throw new MerchantSummaryExceptionHandler(merchantId.toString());
            }
            Double bpScore = (merchantResponseDTO != null && merchantResponseDTO.getBpScore() != null) ? merchantResponseDTO.getBpScore() : 0D;
            bureauResponse = getLatestExperianDetails(merchant.getMobile(), experian, merchant.getId(), bpScore, merchantBankDetail, 3);
            if (bureauResponse != null) {
                experian.setResponse(bureauResponse.toString());
                experian.setBureau(LendingConstants.BUREAU_TYPES.EXPERIAN.name());
            }
            List<LendingPaymentSchedule> prevLoans = lendingPaymentScheduleDao.findPreviousLoansByMerchantAndCreditLoan(merchantId, false);
            boolean isRepeatLoanNoDerog = isRepeatLoanNoDerog(prevLoans,merchantId);
            creditBureauResponseUtil = getCreditBureauResponse(experian);
            if (!creditBureauResponseUtil.isValid(experian.getPancardNumber(), merchant.getMobile())) {
                responseDTO.setMessage("Unable to fetch experian data, please retry!");
                responseDTO.setIsRejected(false);
                responseDTO.setSuccess(true);
                return responseDTO;
            }
            experianDao.save(experian);
            if (isDerogApplication(creditBureauResponseUtil, merchant, experian, isRepeatLoanNoDerog)) {
                loanUtil.auditExperian(experian);
                lendingApplication.setStatus("rejected");
                lendingApplication.setManualCibil("REJECTED");
                lendingApplication.setManualCibilReason("EXPERIAN DEROG FAILED");
                lendingApplication.setCibilApprovedDate(new Date());
                lendingApplicationDao.save(lendingApplication);
                executorService.execute(() -> apiGatewayService.globalLimitTxn(merchantId, "CREDIT", lendingApplication.getLoanAmount()));
                responseDTO.setManualCibil("REJECTED");
                responseDTO.setManualCibilReason("EXPERIAN DEROG FAILED");
                responseDTO.setMessage("Application Derof Failed");
                responseDTO.setIsRejected(true);
                responseDTO.setSuccess(true);
                return responseDTO;
            }
            loanUtil.auditExperian(experian);
        }
        responseDTO.setMessage("Application Derog Passed");
        responseDTO.setIsRejected(false);
        responseDTO.setSuccess(true);
        return responseDTO;
    }

    private JsonNode getLatestExperianDetails(String contact, Experian experian, Long merchantId, Double bpScore, BankDetailsDto merchantBankDetail, int maxExperianRetryCount) {
        int retryCount = 0;
        JsonNode experianResponse = null;
        while (retryCount < maxExperianRetryCount) {
            try {
                experianResponse = fetchExperianDetails(contact, experian, merchantId, bpScore, merchantBankDetail, true);
                break;
            } catch (Exception e) {
                logger.info("Exception occured, sending for retry --- ", e);
                retryCount += 1;
            }
        }
        return experianResponse;
    }

    private boolean isDerogApplication(ResponseUtil responseUtil, BasicDetailsDto merchant, Experian experian, boolean isRepeatLoanNoDerog) {
        try {
            if (!exemptMerchant.contains(merchant.getId()) && responseUtil.isDerog(merchant, isRepeatLoanNoDerog, experian)) {
                return true;
            }
        } catch (Exception e) {
            logger.info("Exception while checking derog for merchant: {}", merchant.getId());
            logger.error("Exception---", e);
        }
        return false;
    }

    private JsonNode parseStringResponse(String response) {
        if (response == null || response.isEmpty()) return null;
        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            logger.info("Exception while parsing string response ", e);
            return null;
        }
    }

    private boolean checkOverdue(List<LendingPaymentSchedule> prevLoans, Long merchantId) {
        try {
            if (prevLoans == null || prevLoans.isEmpty()) {
                return false;
            }
            if (prevLoans.get(0) != null && prevLoans.get(0).getLoanAmount() <= 5000D) {
                return false;
            }
            return getOvershootPeriod(prevLoans.get(0)) > 15;
        } catch (Exception ex) {
            logger.error("Error while fetching eligibility for repeat loan no derog", ex);
            return false;
        }
    }

    public boolean checkFraud(MerchantResponseDTO merchantResponseDTO) {
        int selfTxnCount = (merchantResponseDTO != null && merchantResponseDTO.getSelfTxnCount1Mon() != null) ? merchantResponseDTO.getSelfTxnCount1Mon() : 0;
        return (merchantResponseDTO != null && merchantResponseDTO.getUniqueCustomer1mon() != null && (merchantResponseDTO.getUniqueCustomer1mon() - selfTxnCount) < 15)
                || (merchantResponseDTO != null && merchantResponseDTO.getFraudCustomer() != null);
    }

    private LendingPancard fetchNameFromLiquiloans(String pancardNumber, Long merchantId) {
        logger.info("Calling Liquiloan Name Fetch Api for merchant:{}", merchantId);
        String name = null;
        String apiResponse = null;
        try {
            ExternalGatewaySlave externalGateway = externalGatewayDaoSlave.findByGatewayNameAndTypeAndStatus("LIQUILOANS", null, "ACTIVE");
            if (externalGateway != null) {
                Map<String, String> requestParams = new HashMap<>();
                Date currentTime = new Date();
                String payload = pancardNumber + "||" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(currentTime);
                String checksum = lendingHmacCalculator.calculateHMACHexEncoded(payload, aesEncryptionUtil.decrypt(externalGateway.getSecret()));
                logger.info("Liquiloans Checksum:{} for payload: {} for merchant:{}, PAN: {}", checksum, payload, merchantId, pancardNumber);
                requestParams.put("MID", externalGateway.getMbid());
                requestParams.put("Pan", pancardNumber);
                requestParams.put("Timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(currentTime));
                requestParams.put("Checksum", checksum);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setCacheControl(CacheControl.noCache());
                HttpEntity<Map<String, String>> request = new HttpEntity<>(requestParams, headers);
                try {
                    long startTime = System.currentTimeMillis();
                    int retry = 0;
                    Map response = null;
                    while (retry < 3) {
                        try {
                            response = restTemplate.postForObject("https://api.liquiloans.com/api/apiintegration/v3/VerifyPanNumber", request, Map.class);
                            if (response != null) {
                                break;
                            }
                        } catch (Exception e) {
                            logger.info("Exception in liquiloans pancard api---", e);
                        }
                        retry++;
                    }
                    logger.info("Liquloans PAN validation API response: {}, response time: {}ms", response, (System.currentTimeMillis() - startTime));
                    if (response != null && response.containsKey("status")) {
                        apiResponse = response.toString();
                        boolean status = (boolean) response.get("status");
                        Map responseDataMap = (Map) response.get("data");
                        String statusCode = (String) responseDataMap.get("status-code");
                        if (status && statusCode.equals("101")) {
                            Map responseResultMap = (Map) responseDataMap.get("result");
                            name = (String) responseResultMap.get("name");
                            logger.info("Liquiloans Set status success for merchant: {}", merchantId);
                        } else {
                            logger.info("Liquiloans Set status failed Response params status : {}, status code: {} for merchant: {}", status, statusCode.equals("101"), merchantId);
                        }
                    } else {
                        logger.info("Liquiloans Set status failed response not contain status for merchant: {}", merchantId);
                    }
                } catch (RestClientException e) {
                    logger.error("RestClient Exception accrue in Liquiloans API calling", e);
                }
            }
        } catch (Exception e) {
            logger.error("Exception while fetching name from liquiloans for merchant: {}", merchantId);
            logger.error("Exception---", e);
        }
        LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchantId);
        if (lendingPancard != null) {
            return lendingPancard;
        }
        if (name == null) {
            return null;
        }
        lendingPancardDao.deleteByMerchantId(merchantId);
        return lendingPancardDao.save(new LendingPancard(merchantId, pancardNumber, name, apiResponse));
    }

    public LendingPancard fetchPanName(String pancardNumber, Long merchantId) {
        logger.info("Calling Pan Fetch Api for merchant:{}", merchantId);
        try {
            String name = kycHandler.getPanName(pancardNumber, merchantId);
            if (!ObjectUtils.isEmpty(name)) {
                logger.info("Name:{} found in pancard:{} for merchant:{}", name, pancardNumber, merchantId);
                LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchantId);
                if (lendingPancard != null) {
                    lendingPancard.setName(name);
                    lendingPancard.setPancardNumber(pancardNumber);
                    return lendingPancardDao.save(lendingPancard);
                }
                return lendingPancardDao.save(new LendingPancard(merchantId, pancardNumber, name));
            }
        } catch (Exception e) {
            logger.error("Exception in fetchNameFromSignzy for merchant:{}", merchantId, e);
        }
        return null;
    }


    public VerifyPanCardResponseDto verifyPanDetails(VerifyPanCardRequestDto verifyPanCardRequestDto, String token, Long merchantId, VerifyPanCardResponseDto verifyPanCardResponseDto) {
        logger.info("Calling Pan Verify Api for merchant:{}", merchantId);
        try {
            LendingPancardDetails lendingPancard = lendingPancardDetailsDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);

            if (!ObjectUtils.isEmpty(lendingPancard) && LendingConstants.PAN_VERIFICATION_VERSION.equals(lendingPancard.getVersion())) {
                logger.info("PAN previously verified for merchant:{}", merchantId);
                verifyPanCardResponseDto.setIsPanVerified(true);
                verifyPanCardResponseDto.setIsDobVerified(true);
                verifyPanCardResponseDto.setIsPanVerified(true);
                return verifyPanCardResponseDto;

            }
            PanVerifyKYCResponseDto responseDto = kycHandler.verifyPanDetails(token, verifyPanCardRequestDto.getPanNumber(), verifyPanCardRequestDto.getFullName(), verifyPanCardRequestDto.getDob(), merchantId);
            if (!ObjectUtils.isEmpty(responseDto)) {
                if (responseDto.getStatus()) {
                    verifyPanCardResponseDto.setIsPanVerified(!ObjectUtils.isEmpty(responseDto.getData().getPanValid()) ? responseDto.getData().getPanValid() : false);
                    verifyPanCardResponseDto.setIsDobVerified(!ObjectUtils.isEmpty(responseDto.getData().getDobMatch()) ? responseDto.getData().getDobMatch() : false);
                    verifyPanCardResponseDto.setIsNameVerified(!ObjectUtils.isEmpty(responseDto.getData().getNameMatch()) ? responseDto.getData().getNameMatch() : false);
                } else {
                    verifyPanCardResponseDto.setMessage(responseDto.getData().getMessage());
                }

                if (verifyPanCardResponseDto.getIsPanVerified() && verifyPanCardResponseDto.getIsDobVerified() && verifyPanCardResponseDto.getIsNameVerified()) {
                    String aadhaarSeedingStatus = !ObjectUtils.isEmpty(responseDto.getData()) && !ObjectUtils.isEmpty(responseDto.getData().getAadhaarSeedingStatus()) ? responseDto.getData().getAadhaarSeedingStatus() : null;
                    if (!ObjectUtils.isEmpty(lendingPancard)) {
                        lendingPancard.setName(responseDto.getData().getPanHolderName());
                        lendingPancard.setPancardNumber(verifyPanCardRequestDto.getPanNumber());
                        lendingPancard.setVersion(LendingConstants.PAN_VERIFICATION_VERSION);
                        lendingPancard.setAadhaarSeedingStatus(aadhaarSeedingStatus);
                        lendingPancard.setDob(verifyPanCardResponseDto.getIsDobVerified()?verifyPanCardRequestDto.getDob():null);
                        lendingPancard.setResponse(responseDto.toString());
                        lendingPancardDetailsDao.save(lendingPancard);
                    } else {
                        lendingPancardDetailsDao.save(new LendingPancardDetails(merchantId, verifyPanCardRequestDto.getPanNumber(), verifyPanCardRequestDto.getFullName(), responseDto.toString(), LendingConstants.PAN_VERIFICATION_VERSION, aadhaarSeedingStatus, verifyPanCardRequestDto.getDob()));
                    }
                }
                return verifyPanCardResponseDto;
            }
        }catch (HttpClientErrorException.TooManyRequests e) {
            throw e;
        }catch (Exception e) {
            logger.error("Exception in verifyPanDetails for merchantId: {}, {}", merchantId, Arrays.asList(e.getStackTrace()));
        }
        return null;
    }


    public LoanEligibilityDTO calculateLoanBreakup(LendingCategories lendingCategories, double avgTpv, String type, Long merchantId, Long experianId, double prevLoanAmount, String color, String set, String loanType, boolean isZomato, boolean yellowPincode) {
        Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(merchantId);
        if (ObjectUtils.isEmpty(basicDetailsDto)) {
            return null;
        }

        LendingPaymentSchedule previousLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId, Arrays.asList("CLOSED"));
        Experian experian = experianDao.getByMerchantId(merchantId);
        boolean cpvCity = false;
        if (experian != null && experian.getPincode() != null) {
            PincodeCityStateMappingDTO pincodeCityStateMapping = merchantService.findByPincode(experian.getPincode());
            cpvCity = (pincodeCityStateMapping != null && LendingConstants.CPV_CITIES.contains(pincodeCityStateMapping.getCity()));
        }
        boolean isNTC = isNTC(experian);
//        Merchant merchant = merchantDao.findById(merchantId).get();

        double bureauScore = experian != null && experian.getExperianScore() != null ? experian.getExperianScore() : 0;
        Double percentage = lendingCategories.getMultiplier();
        double interest = "TOPUP".equalsIgnoreCase(loanType) ? 1.75 : lendingCategories.getInterestRate();
//        if ("S4A".equalsIgnoreCase(lendingCategories.getMasterCategory()) || "S4LG".equalsIgnoreCase(lendingCategories.getMasterCategory())) {
//            long dpd = getDPDInLastLoan(merchantId);
//            if (dpd > 5) {
//                interest = 2.75d;
//            }
//        } else if ("S4DG".equalsIgnoreCase(lendingCategories.getMasterCategory())) {
//            long dpd = getDPDInLastLoan(merchantId);
//            if (dpd > 10) {
//                interest = 2.25d;
//            }
//        }
        int tenure = Math.round(lendingCategories.getTenureMonths());
        int ioTenure = Math.round(lendingCategories.getIoTenureMonths());
        logger.info("score:{} for merchant:{}", bureauScore, merchantId);
        Integer maxAmount = bureauScore > 0 && bureauScore < 700 && !yellowPincode ? new Integer(300000) : lendingCategories.getMaxTpvAmount();
        int ioPayableDays = lendingCategories.getIoPayableDays();
        String construct = lendingCategories.getLoanConstruct();
        String category = lendingCategories.getCategory();
        String payableConverter = lendingCategories.getPayableConverter();
        int ioEdiDays = lendingCategories.getIoEdiDays();
        LoanCalculationUtil.LoanBreakupDetail breakup;
        // Capping first loan and Non CPV city
        if (previousLoan == null && !cpvCity) {
            maxAmount = 100000;
        }
        if (isNTC && !cpvCity) {
            maxAmount = 50000;
        }
        if (avgTpv == 0 && prevLoanAmount > 0) {
            if ("NTB".equalsIgnoreCase(loanType)) {
                maxAmount = 100000;
            } else if (!(previousLoan == null && !cpvCity)) {

                maxAmount = bureauScore > 0 && bureauScore < 700 && !yellowPincode ? 300000 : LendingConstants.MAX_LOAN_AMOUNT_INTEGER;
            }
            if (previousLoan != null && prevLoanAmount > previousLoan.getLoanAmount() && prevLoanAmount > 2.5 * previousLoan.getLoanAmount() && !yellowPincode && !"NTB".equalsIgnoreCase(loanType)) {
                maxAmount = Double.valueOf(2.5 * previousLoan.getLoanAmount()).intValue();
            }
            if (isNTC && !cpvCity) {
                maxAmount = 50000;
            }
            prevLoanAmount = Math.min(roundUp(prevLoanAmount), maxAmount);
            if (prevLoanAmount > 35000 && isNTC && basicDetailsDto.get().getBussinessCategory() != null && LendingConstants.FOOD_BEVERAGES.contains(basicDetailsDto.get().getBussinessCategory())) {
                prevLoanAmount = 35000 + ((prevLoanAmount - 35000) / 2);
            }
            AvailableLoan availableLoan = new AvailableLoan();
            availableLoan.setAmount(prevLoanAmount);
            breakup = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategories, loanType);
        } else {
            breakup = getBreakup(tenure, construct, type, avgTpv, percentage, interest, maxAmount, ioTenure, ioPayableDays, lendingCategories, isNTC, basicDetailsDto.get(), previousLoan);
        }
        if (!isZomato) {
            if (color != null && color.equalsIgnoreCase("AMBER") && breakup.getLoanAmount() < 20000 && !"NTB".equalsIgnoreCase(loanType) && !"OGL".equalsIgnoreCase(loanType) && !"BHARAT_SWIPE".equalsIgnoreCase(loanType)) {
                logger.info("loan amount is less than 20000 for merchant: {}", merchantId);
                return null;
            } else if (breakup.getLoanAmount() < 10000) {
                logger.info("loan amount is less than 10000 for merchant: {}", merchantId);
                return null;
            }
        }
        logger.info("saving eligible loan for merchant: {}", merchantId);
        LendingEligibleLoan eligibleLoan = eligibleLoanDao.save(new LendingEligibleLoan(merchantId, experianId, (double) breakup.getLoanAmount(), payableConverter, "ACTIVE", category, ioEdiDays, 0, avgTpv, breakup.getEdi(), breakup.getIoEdi(), breakup.getRepayment(), construct, loanType, null));
        logger.info("eligible loan for merchant: {} is-- {}", merchantId, eligibleLoan.toString());
        eligibleLoanAuditDao.save(EligibleLoanAudit.createObject(eligibleLoan));
        return createLoanEligibilityDTO(breakup, payableConverter, lendingCategories, loanType);
    }

    private LoanCalculationUtil.LoanBreakupDetail getBreakup(int tenureMonth, String construct, String type, double avgTpv, double percentage, double interest, int maxAmount, int ioTenure, int ioPayableDays, LendingCategories categories, boolean isNTC, BasicDetailsDto merchant, LendingPaymentSchedule previousLoan) {
        int tenure = tenureMonth - ioTenure;
        int ediDays, disbursementAmount, ioInterestAmount, principleEdiTenure, repayment;
        double loanAmount, edi, totalInterestAmount, ioEdi;
        ediDays = getEdiDays(tenure);
        edi = (avgTpv * percentage);
        repayment = (int) Math.round(ediDays * edi);
        loanAmount = Math.min(roundUp(repayment / (1 + (interest / 100) * tenure)), maxAmount);// round down
        if (loanAmount > 35000 && isNTC && merchant.getBussinessCategory() != null && LendingConstants.FOOD_BEVERAGES.contains(merchant.getBussinessCategory())) {
            loanAmount = 35000 + ((loanAmount - 35000) / 2);
        }
        if (previousLoan != null && loanAmount > previousLoan.getLoanAmount() && loanAmount > 2.5 * previousLoan.getLoanAmount()) {
            loanAmount = 2.5 * previousLoan.getLoanAmount();
        }
        int processingFee = LoanCalculationUtil.getProcessingFee(loanAmount, categories);
        edi = Math.ceil((loanAmount * (1 + (interest / 100) * tenure)) / ediDays);
        disbursementAmount = (int) loanAmount - processingFee;
        ioEdi = ioPayableDays > 0 ? Math.ceil((loanAmount * (interest / 100)) / ioPayableDays) : 0;
        ioInterestAmount = (int) (ioEdi * ioPayableDays);
        repayment = (int) Math.round((edi * ediDays) + ioInterestAmount);
        totalInterestAmount = repayment - loanAmount;
        principleEdiTenure = tenure;

        return new LoanCalculationUtil.LoanBreakupDetail(construct, (int) edi, (int) ioEdi, processingFee, ioInterestAmount, (int) totalInterestAmount, (int) totalInterestAmount,
                ioTenure, principleEdiTenure, repayment, disbursementAmount, type, (int) loanAmount, interest);
    }

    private double roundUp(double loanAmount) {
        if (loanAmount < 20000) {
            return (Math.ceil(loanAmount / 1000.0) * 1000);
        } else if (loanAmount < 100000) {
            return (Math.ceil(loanAmount / 5000.0) * 5000);
        } else {
            return (Math.ceil(loanAmount / 10000.0) * 10000);
        }
    }

    private LoanEligibilityDTO createLoanEligibilityDTO(LoanCalculationUtil.LoanBreakupDetail breakup, String tenure, LendingCategories category, String loanType) {
        LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();
        loanEligibilityDTO.setProcessingFee(breakup.getProcessingFee());
        loanEligibilityDTO.setInterestRate(breakup.getEffectiveInterestRate());
        loanEligibilityDTO.setAmount(breakup.getLoanAmount());
        loanEligibilityDTO.setCategory(category.getCategory());
        loanEligibilityDTO.setInterestAmount(breakup.getTotalInterestAmount());
        loanEligibilityDTO.setEdi(breakup.getEdi());
        loanEligibilityDTO.setRepayment(breakup.getRepayment());
        loanEligibilityDTO.setDisbursementAmount(breakup.getDisbursementAmount());
        loanEligibilityDTO.setEdiCount(category.getPayableDays());
        loanEligibilityDTO.setConstruct(category.getLoanConstruct());
        loanEligibilityDTO.setTenure(tenure);
        loanEligibilityDTO.setConstruct(breakup.getConstruct());
        loanEligibilityDTO.setList(LoanCalculationUtil.prepareLabels(breakup, breakup.getIoOrFreeEdiTenure()));
        loanEligibilityDTO.setType(breakup.getType());
        loanEligibilityDTO.setOptionEnable(true);
        loanEligibilityDTO.setPrincipleEdiTenure(breakup.getPrincipleEdiTenure());
        loanEligibilityDTO.setLoanType(loanType);
        return loanEligibilityDTO;
    }

    private int getEdiDays(int tenure) {
        switch (tenure) {
            case 1:
                return 26;
            case 3:
                return 77;
            case 6:
                return 155;
            case 9:
                return 234;
            case 12:
                return 311;
            default:
                return 388;//15 months
        }
    }


    public boolean isEligibleForConstruct2And3(MerchantSummary summary, List<LendingPaymentSchedule> prevLoans) {
        try {
            if (prevLoans == null || prevLoans.isEmpty()) {
                return false;
            }
            if (isAnyHighTPVLoan(prevLoans)) {
                return false;
            }
            if (summary == null || summary.getTxnDayCount1Mon() == null || summary.getTxnDayCount1Mon() < 15) {
                return false;
            }
            return getOvershootPeriod(prevLoans.get(0)) <= 5;
        } catch (Exception ex) {
            logger.error("Error while fetching eligiblity for construct 2 and 3", ex);
            return false;
        }
    }

    public boolean isRepeatLoanNoDerog(List<LendingPaymentSchedule> prevLoans, Long merchantId) {
        try {
            if (prevLoans == null || prevLoans.isEmpty()) {
                return false;
            }
            if (prevLoans.get(0) != null && prevLoans.get(0).getLoanAmount() < 5000D) {
                return false;
            }
            return getOvershootPeriod(prevLoans.get(0)) <= 15;
        } catch (Exception ex) {
            logger.error("Error while fetching eligibility for repeat loan no derog", ex);
            return false;
        }
    }

    private boolean isAnyHighTPVLoan(List<LendingPaymentSchedule> lendingPaymentScheduleList) {
        if (lendingPaymentScheduleList == null || lendingPaymentScheduleList.isEmpty()) {
            return false;
        }
        for (LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentScheduleList) {
            if (Loan.Category.HIGHTPV.toString().equals(lendingPaymentSchedule.getLoanType())) {
                return true;
            }
        }
        return false;
    }

    private long getOvershootPeriod(LendingPaymentSchedule lendingPaymentSchedule) {
        if (lendingPaymentSchedule == null) {
            return 0;
        }
        LendingLedger lastEDI = lendingLedgerDao.findLastEDIDueEntryByMerchantAndLoan(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId());
        LendingLedgerSlave lastPayment = lendingLedgerSlaveDao.findLastPaymentEntryByMerchantAndLoan(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId());
        if (lastEDI == null || lastPayment == null) {
            return 0;
        }
        return LoanUtil.getDateDiffInDays(lastEDI.getDate(), lastPayment.getDate());
    }

    private long getDPDInLastLoan(Long merchantId) {
        LendingPaymentSchedule lastLoan = lendingPaymentScheduleDao.findTop1ByMerchantIdAndStatusAndCreditLoanOrderByIdDesc(merchantId, "CLOSED", false);
        return getOvershootPeriod(lastLoan);
    }

    public String calculateSegment(int bureauVintage, String accountCategory, Double bpScore) {
        int col;
        int row;
        String[][] m1 = {{"1", "5", "9"}, {"2", "6", "10"}, {"3", "7", "11"}, {"4", "8", "12"}};
        String[][] m2 = {{"13", "17", "21"}, {"14", "18", "22"}, {"15", "19", "23"}, {"16", "20", "24"}};
        String[][] m3 = {{"25", "29", "33"}, {"26", "30", "34"}, {"27", "31", "35"}, {"28", "32", "36"}};
        String[][] m4 = {{"37", "41", "45"}, {"38", "42", "46"}, {"39", "43", "47"}, {"40", "44", "48"}};
        switch (accountCategory) {
            case "B":
                col = 1;
                break;
            case "C":
                col = 2;
                break;
            default:
                col = 0;// "A"
        }
        if (bpScore <= 15) {
            row = 0;
        } else if (bpScore < 20) {
            row = 1;
        } else if (bpScore <= 25) {
            row = 2;
        } else {
            row = 3;
        }
        if (bureauVintage < 6) {
            return m1[row][col];
        } else if (bureauVintage <= 12) {
            return m2[row][col];
        } else if (bureauVintage <= 24) {
            return m3[row][col];
        } else {
            return m4[row][col];
        }
    }

    private boolean validatePancard(JsonNode experianResponse, String panCard, Long merchantId, Experian experian) {
        if (experianResponse.get("INProfileResponse").get("Current_Application").get("Current_Application_Details") != null && experianResponse.get("INProfileResponse").get("Current_Application").get("Current_Application_Details").get("Current_Applicant_Details") != null) {
            String email = experianResponse.get("INProfileResponse").get("Current_Application").get("Current_Application_Details").get("Current_Applicant_Details").get("EMailId").asText();
            experian.setEmail(email);
        }
        if (experianResponse.get("INProfileResponse").get("SCORE").get("BureauScore") != null) {
            experian.setExperianScore(experianResponse.get("INProfileResponse").get("SCORE").get("BureauScore").doubleValue());
        }
        if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isArray()) {
            for (JsonNode jsonNode : experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS")) {
                if (jsonNode.get("CAIS_Holder_Details") != null && jsonNode.get("CAIS_Holder_Details").isObject()) {
                    if (jsonNode.get("CAIS_Holder_Details").get("Income_TAX_PAN") != null && jsonNode.get("CAIS_Holder_Details").get("Income_TAX_PAN").asText().equalsIgnoreCase(panCard)) {
                        String merchantName = jsonNode.get("CAIS_Holder_Details").get("First_Name_Non_Normalized").asText() + " " + jsonNode.get("CAIS_Holder_Details").get("Middle_Name_1_Non_Normalized").asText() + " " + jsonNode.get("CAIS_Holder_Details").get("Surname_Non_Normalized").asText();
                        experian.setMerchantName(merchantName);
                        experianDao.save(experian);
                        return true;
                    }
                } else if (jsonNode.get("CAIS_Holder_Details") != null && jsonNode.get("CAIS_Holder_Details").isArray()) {
                    for (JsonNode node : jsonNode.get("CAIS_Holder_Details")) {
                        if (node.get("Income_TAX_PAN") != null && node.get("Income_TAX_PAN").asText().equalsIgnoreCase(panCard)) {
                            String merchantName = node.get("First_Name_Non_Normalized").asText() + " " + node.get("Middle_Name_1_Non_Normalized").asText() + " " + node.get("Surname_Non_Normalized").asText();
                            experian.setMerchantName(merchantName);
                            experianDao.save(experian);
                            return true;
                        }
                    }
                }
            }
        } else if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isObject()) {
            JsonNode jsonNode = experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS");
            if (jsonNode.get("CAIS_Holder_Details") != null && jsonNode.get("CAIS_Holder_Details").isObject()) {
                if (jsonNode.get("CAIS_Holder_Details").get("Income_TAX_PAN") != null && jsonNode.get("CAIS_Holder_Details").get("Income_TAX_PAN").asText().equalsIgnoreCase(panCard)) {
                    String merchantName = jsonNode.get("CAIS_Holder_Details").get("First_Name_Non_Normalized").asText() + " " + jsonNode.get("CAIS_Holder_Details").get("Middle_Name_1_Non_Normalized").asText() + " " + jsonNode.get("CAIS_Holder_Details").get("Surname_Non_Normalized").asText();
                    experian.setMerchantName(merchantName);
                    experianDao.save(experian);
                    return true;
                }
            } else if (jsonNode.get("CAIS_Holder_Details") != null && jsonNode.get("CAIS_Holder_Details").isArray()) {
                for (JsonNode node : jsonNode.get("CAIS_Holder_Details")) {
                    if (node.get("Income_TAX_PAN") != null && node.get("Income_TAX_PAN").asText().equalsIgnoreCase(panCard)) {
                        String merchantName = node.get("First_Name_Non_Normalized").asText() + " " + node.get("Middle_Name_1_Non_Normalized").asText() + " " + node.get("Surname_Non_Normalized").asText();
                        experian.setMerchantName(merchantName);
                        experianDao.save(experian);
                        return true;
                    }
                }
            }
        }
        logger.info("Pancard not matched with experian for merchant: {}", merchantId);
        experian.setReason(ExperianConstants.INVALID_PANCARD);
        experianDao.save(experian);
        return false;
    }


    public JsonNode fetchExperianDetails(String contact, Experian experian, Long merchantId, Double bpScore, BankDetailsDto merchantBankDetail, boolean callRefreshApi) {
        JsonNode refreshResponse = null;
        if (experian.getHitId() != null && callRefreshApi) {
            refreshResponse = apiGatewayService.experianRefreshApi(merchantId, experian.getHitId());
        }
        if (refreshResponse != null) {
            return refreshResponse;
        }
        LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchantId);
        if (lendingPancard == null || lendingPancard.getName() == null || !lendingPancard.getPancardNumber().equalsIgnoreCase(experian.getPancardNumber())) {// get data from signzy
            try {
                lendingPancard = fetchPanName(experian.getPancardNumber(), merchantId);
            } catch (Exception e) {
                logger.error("Exception in Signzy pan fetch API---", e);
            }
        }
        String firstName;
        String lastName;
        if (lendingPancard != null && lendingPancard.getName() != null && !lendingPancard.getName().trim().equalsIgnoreCase("")) {
            firstName = getFirstName(lendingPancard.getName());
            lastName = getLastName(lendingPancard.getName());
        } else {
            firstName = getFirstName(merchantBankDetail.getBeneficiaryName());
            lastName = getLastName(merchantBankDetail.getBeneficiaryName());
        }
        if (contact.length() > 10) {
            contact = contact.substring(2);//remove 91
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        setExperianApiParams(body, firstName, lastName, contact, experian.getPancardNumber());
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        Long a = DateTime.now().getMillis();
        logger.info("Experian request for merchant: {} is {}", merchantId, body);
        String response = restTemplate.postForObject(ExperianConstants.SHORT_API_URL, request, String.class);
        Long b = DateTime.now().getMillis();
        logger.info("Experian Short API response time---{}ms", (b - a));
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            if (jsonNode == null || jsonNode.get("showHtmlReportForCreditReport").isNull()) {
                experianService.insertExperianCallRecord(null, "SHORT_API_URL", objectMapper.writeValueAsString(request), merchantId, bpScore, experian.getPancardNumber(), contact);
                return null;
            }
            String xmlResponse = jsonNode.get("showHtmlReportForCreditReport").asText().replaceAll("&amp;", "&").replaceAll("&gt;", ">").replaceAll("&lt;", "<").replaceAll("&quot;", "\"");
            JSONObject jsonObject = XML.toJSONObject(xmlResponse);
            experian.setHitId(jsonNode.get("stageOneId_").asText());
            experianService.insertExperianCallRecord(objectMapper.readTree(jsonObject.toString()).toString(), "SHORT_API_URL", objectMapper.writeValueAsString(request), merchantId, bpScore, experian.getPancardNumber(), contact);
            return objectMapper.readTree(jsonObject.toString());
        } catch (Exception e) {
            logger.info("Exception while parsing experian response", e);
            return null;
        }
    }

    private void setExperianApiParams(MultiValueMap<String, Object> body, String firstName, String lastName, String contact, String panCard) {
        body.add("clientName", ExperianConstants.CLIENT_NAME);
        body.add("allowInput", "1");
        body.add("allowEdit", "1");
        body.add("allowCaptcha", "1");
        body.add("allowConsent", "1");
        body.add("allowEmailVerify", "1");
        body.add("allowVoucher", "1");
        body.add("voucherCode", ExperianConstants.VOUCHER_CODE);
        body.add("firstName", firstName);
        body.add("surName", lastName);
        body.add("mobileNo", contact);
        body.add("noValidationByPass", "0");
        body.add("emailConditionalByPass", "1");
        body.add("pan", panCard);
    }

    public String getFirstName(String name) {
        if (name == null) {
            return "";
        }
        int lastIndexOfSpace = name.lastIndexOf(" ");
        if (lastIndexOfSpace != -1) {
            return name.substring(0, lastIndexOfSpace);
        } else {
            lastIndexOfSpace = name.lastIndexOf(".");
            if (lastIndexOfSpace != -1) {
                return name.substring(0, lastIndexOfSpace);
            } else {
                return name;
            }
        }
    }

    public String getLastName(String name) {
        if (name == null) {
            return "";
        }
        int lastIndexOfSpace = name.lastIndexOf(" ");
        if (lastIndexOfSpace != -1) {
            return name.substring(lastIndexOfSpace + 1);
        } else {
            lastIndexOfSpace = name.lastIndexOf(".");
            if (lastIndexOfSpace != -1) {
                return name.substring(lastIndexOfSpace + 1);
            } else {
                return name;
            }
        }
    }

    public boolean isNTC(Experian experian) {
        if (experian == null || experian.getCategory() == null) {
            return true;
        }
        if (experian.getReason() != null && experian.getReason().equalsIgnoreCase("ZOMATO_ETC")) {
            return false;
        }
        List<String> ntcCategories = Arrays.asList("1N", "2N", "3N", "4N");
        return ntcCategories.contains(experian.getCategory());
    }


    public ResponseUtil getCreditBureauResponse(Experian experian) {
        JsonNode bureauResponse = null;
        if (experian != null) {
            bureauResponse = parseStringResponse(experian.getResponse());
            if (experian.getBureau() != null && experian.getBureau().equalsIgnoreCase("crif")) {
                return new CrifResponseUtil(bureauResponse, experianDao, lendingMerchantDropoffDao);
            } else {
                return new ExperianResponseUtil(bureauResponse, experianDao, lendingMerchantDropoffDao);
            }
        }
        return new ExperianResponseUtil(bureauResponse, experianDao, lendingMerchantDropoffDao);
    }
}
