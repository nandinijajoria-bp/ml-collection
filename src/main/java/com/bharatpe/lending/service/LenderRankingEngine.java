package com.bharatpe.lending.service;

import com.bharatpe.lending.entity.LenderMetricsHistory;
import com.bharatpe.lending.entity.OfferRankingConfig;
import com.bharatpe.lending.enums.RankingType;
import com.bharatpe.lending.enums.SortOrder;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LenderRankingEngine {

    public List<String> rankLenders(List<LenderMetricsHistory> allLenders,
                                    List<OfferRankingConfig> rankingRules,
                                    RankingType rankingType,
                                    int limit) {
        if (allLenders == null || rankingRules == null || rankingType == null || limit < 1) {
            throw new IllegalArgumentException("Invalid input parameters");
        }

        try {
            List<LenderMetricsHistory> activeLenders = filterActiveLenders(allLenders);
            List<OfferRankingConfig> sortedRules = getSortedRules(rankingRules, rankingType);
            Comparator<LenderMetricsHistory> comparator = buildComparatorChain(sortedRules);

            return activeLenders.stream()
                    .sorted(comparator)
                    .limit(limit)
                    .map(LenderMetricsHistory::getLender)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error ranking lenders: {}", e.getMessage());
            throw new RuntimeException("Failed to rank lenders", e);
        }
    }

    //todo : remove this method, we'll have a proper database query for active lenders
    private List<LenderMetricsHistory> filterActiveLenders(List<LenderMetricsHistory> allLenders) {
        return allLenders.stream()
                .filter(l -> Boolean.FALSE.equals(l.getIsLenderSwitchedOff()))
                .collect(Collectors.toList());
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