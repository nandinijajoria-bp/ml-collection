package com.bharatpe.lending.service;

import com.bharatpe.lending.common.dao.LendingLenderPricingDao;
import com.bharatpe.lending.common.dao.LendingRiskVariablesDao;
import com.bharatpe.lending.common.dao.PricingExperimentDao;
import com.bharatpe.lending.common.entity.LendingLenderPricing;
import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.common.entity.OfferRankingConfig;
import com.bharatpe.lending.common.entity.LenderMetricsHistory;
import com.bharatpe.lending.common.entity.PricingExperiment;
import com.bharatpe.lending.common.enums.RankingType;
import com.bharatpe.lending.common.enums.SortOrder;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LenderRankingEngine {

    @Autowired
    private LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    private PricingExperimentDao pricingExperimentDao;

    @Value("${pricing.experiment.enable:false}")
    boolean pricingExpEnabled;

    @Autowired
    private LendingLenderPricingDao lendingLenderPricingDao;

    public List<String> rankLenders(List<LenderMetricsHistory> allLenders,
                                    List<OfferRankingConfig> rankingRules,
                                    RankingType rankingType,
                                    int limit, Long merchantId, Integer tenureInMonths) {
        if (Objects.isNull(merchantId)) {
            log.info("merchantId not found");
            return null;
        }
        log.info("lenders : {}, rankingRules : {}, rankingType : {}, limit : {}",
                allLenders, rankingRules, rankingType, limit);
        if (allLenders == null || rankingRules == null || rankingType == null || limit < 1) {
            throw new IllegalArgumentException("Invalid input parameters");
        }

        populateInterestRates(allLenders,merchantId, tenureInMonths);
        try {
            List<OfferRankingConfig> sortedRules = getSortedRules(rankingRules, rankingType);
            log.info("sorted rules : {}", sortedRules);
            Comparator<LenderMetricsHistory> comparator = buildComparatorChain(sortedRules);
            List<String> lenders = allLenders.stream()
                    .filter(l -> sortedRules.stream()
                            .allMatch(rule -> l.getFieldValueAsDouble(rule.getFieldName()) != -1.0)) // remove invalid lenders
                    .sorted(comparator)
                    .limit(limit)
                    .map(LenderMetricsHistory::getLender)
                    .collect(Collectors.toList());

            log.info("approved lenders : {}", lenders);

            return lenders;

        } catch (Exception e) {
            log.error("Error ranking lenders: {}", e.getMessage());
            throw new RuntimeException("Failed to rank lenders", e);
        }
    }

    private List<OfferRankingConfig> getSortedRules(List<OfferRankingConfig> rankingRules, RankingType rankingType) {
        return rankingRules.stream()
                .filter(r -> r.getRankingType() == rankingType && Boolean.TRUE.equals(r.getEnabled()))
                .sorted(Comparator.comparingInt(OfferRankingConfig::getPriorityOrder))
                .collect(Collectors.toList());
    }

    private Comparator<LenderMetricsHistory> buildComparatorChain(List<OfferRankingConfig> rules) {
        Comparator<LenderMetricsHistory> comparator = null;

        for (OfferRankingConfig rule : rules) {
            Comparator<LenderMetricsHistory> ruleComparator = createRuleComparator(rule);
            comparator = comparator == null ? ruleComparator : comparator.thenComparing(ruleComparator);
        }

        // Add default tie-breaker - alphabetical order by lender name
        return comparator == null ?
                Comparator.comparing(l -> l.getLender().toUpperCase()) :
                comparator.thenComparing(l -> l.getLender().toUpperCase());
    }

    private Comparator<LenderMetricsHistory> createRuleComparator(OfferRankingConfig rule) {
        return Comparator.comparing(
                l -> l.getFieldValueAsDouble(rule.getFieldName()),
                getOrderComparator(rule.getSortOrder())
        );
    }

    private static Comparator<Double> getOrderComparator(SortOrder sortOrder) {
        return sortOrder == SortOrder.ASC ? Comparator.naturalOrder() : Comparator.reverseOrder();
    }

    public void populateInterestRates(
            List<LenderMetricsHistory> lenders,
            long merchantId, Integer tenureInMonths
    ) {
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao
                .findByMerchantId(merchantId);
        log.info("LendingRiskVariables for merchantId {}: {}", merchantId, lendingRiskVariables);

        PricingExperiment pricingExperiment = null;
        if (pricingExpEnabled) {
            pricingExperiment = pricingExperimentDao
                    .findBySegmentAndRiskGroupAndTenureInMonthsAndMerchantIdAndPincodeColorAndStatus(
                            lendingRiskVariables.getRiskSegment(),
                            lendingRiskVariables.getRiskGroup(),
                            tenureInMonths,
                            (int) (merchantId % 10),
                            lendingRiskVariables.getPincodeColor().name(),
                            "ACTIVE"
                    );
        }

        for (LenderMetricsHistory lender : lenders) {
            log.info("Processing lender: {}", lender.getLender());

            LendingLenderPricing lenderPricing = lendingLenderPricingDao
                    .findTop1BySegmentAndRiskGroupAndTenureInMonthsAndLenderAndPincodeColorAndStatus(
                            lendingRiskVariables.getRiskSegment(),
                            lendingRiskVariables.getRiskGroup(),
                            tenureInMonths,
                            lender.getLender(),
                            lendingRiskVariables.getPincodeColor().name(),
                            "ACTIVE"
                    );

            double rate = pricingExperiment != null
                    ? pricingExperiment.getInterestRate()
                    : lenderPricing != null ? lenderPricing.getInterestRate() : -1.0;
            log.info("Interest rate for lender {}: {}", lender.getLender(), rate);

            lender.setInterestRate(rate);
        }
    }

}