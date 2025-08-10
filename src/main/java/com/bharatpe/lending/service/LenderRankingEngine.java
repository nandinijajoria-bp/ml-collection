package com.bharatpe.lending.service;

import com.bharatpe.lending.common.entity.OfferRankingConfig;
import com.bharatpe.lending.common.enums.RankingType;
import com.bharatpe.lending.common.enums.SortOrder;
import com.bharatpe.lending.entity.LenderMetricsHistory;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LenderRankingEngine {

    public List<String> rankLenders(List<LenderMetricsHistory> allLenders,
                                    List<OfferRankingConfig> rankingRules,
                                    RankingType rankingType,
                                    int limit) {
        log.info("lenders : {}, rankingRules : {}, rankingType : {}, limit : {}",
                allLenders, rankingRules, rankingType, limit);
        if (allLenders == null || rankingRules == null || rankingType == null || limit < 1) {
            throw new IllegalArgumentException("Invalid input parameters");
        }

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
}