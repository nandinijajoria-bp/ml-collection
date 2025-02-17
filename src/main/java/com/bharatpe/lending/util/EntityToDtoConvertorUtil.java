package com.bharatpe.lending.util;

import com.bharatpe.lending.common.entity.LendingRiskVariables;
import com.bharatpe.lending.dto.RiskVariablesDTO;
import com.bharatpe.lending.service.AssignmentRuleUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class EntityToDtoConvertorUtil {

    public static RiskVariablesDTO convertToRiskVariablesDTO(LendingRiskVariables lendingRiskVariables) {
        RiskVariablesDTO riskVariablesDTO = new RiskVariablesDTO();
        if (!ObjectUtils.isEmpty(lendingRiskVariables)) {

            riskVariablesDTO.setBureauScore(Objects.nonNull(lendingRiskVariables.getBureauScore()) ? lendingRiskVariables.getBureauScore() : 0D);
            riskVariablesDTO.setRiskSegment(Objects.nonNull(lendingRiskVariables.getRiskSegment()) ? "%" + lendingRiskVariables.getRiskSegment() + "%" : "");
            riskVariablesDTO.setRiskGroup(lendingRiskVariables.getRiskGroup());
            riskVariablesDTO.setRiskGroupLike(Objects.nonNull(lendingRiskVariables.getRiskGroup()) ? "%" + lendingRiskVariables.getRiskGroup() + "%" : "");
            riskVariablesDTO.setPincodeColor(lendingRiskVariables.getPincodeColor());
            riskVariablesDTO.setIsGstOffer(Objects.nonNull(lendingRiskVariables.getGstAffectedOffer()) ? lendingRiskVariables.getGstAffectedOffer() : Boolean.FALSE);
            riskVariablesDTO.setVintage(Objects.nonNull(lendingRiskVariables.getVintage()) ? lendingRiskVariables.getVintage() : 0L);
            riskVariablesDTO.setSummaryTpv(Objects.nonNull(lendingRiskVariables.getSummaryTpv()) ? lendingRiskVariables.getSummaryTpv() : 0D);
            riskVariablesDTO.setTpvOffer(Objects.nonNull(lendingRiskVariables.getTpvOffer()) ? lendingRiskVariables.getTpvOffer() : 0D);
            riskVariablesDTO.setMonthlyTpv(Objects.nonNull(lendingRiskVariables.getMonthlyTpv()) ? lendingRiskVariables.getMonthlyTpv() : 0D);

            AssignmentRuleUtils assignmentRuleUtils = new AssignmentRuleUtils();
            riskVariablesDTO.setUnsecuredPos(assignmentRuleUtils.getUnsecuredPos(lendingRiskVariables.getMetaData()));

            Set<String> rejectedLenders = new HashSet<>();
            if (!StringUtils.isEmpty(lendingRiskVariables.getRejectedLenders())) {
                List<String> rejectedLendersArray = Arrays.asList(lendingRiskVariables.getRejectedLenders().split(","));
                if (!CollectionUtils.isEmpty(rejectedLendersArray)) {
                    rejectedLendersArray.forEach(l -> rejectedLenders.add(l.trim()));
                }
            }
            riskVariablesDTO.setRejectedLenders(rejectedLenders);

            riskVariablesDTO.setMaxTenure(Objects.nonNull(lendingRiskVariables.getTenure()) ? lendingRiskVariables.getTenure() : 0);
        }
        return riskVariablesDTO;
    }
}