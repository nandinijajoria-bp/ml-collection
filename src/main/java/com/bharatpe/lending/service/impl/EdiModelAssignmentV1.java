package com.bharatpe.lending.service.impl;

import com.bharatpe.lending.common.enums.EdiModel;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.loanV3.dto.ExtractedRulesAndLendersDTO;
import com.bharatpe.lending.service.AssignmentRuleUtils;
import com.bharatpe.lending.service.IEdiModelAssignment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@Slf4j
public class EdiModelAssignmentV1 implements IEdiModelAssignment {

    @Autowired
    AssignmentRuleUtils assignmentRuleUtils;

    @Override
    public EdiModel assignModel(Long merchantId) {
        ExtractedRulesAndLendersDTO extractedRulesAndLendersDTO = assignmentRuleUtils.extractRules(merchantId, null);
        assignmentRuleUtils.filterLenders(extractedRulesAndLendersDTO);
        Set<EdiModel> ediModels = assignmentRuleUtils.extractEdiModelFromFilteredLenders(extractedRulesAndLendersDTO);
        if (ediModels.isEmpty()) {
//            fallback lender here
            return LenderOffDays.valueOf(assignmentRuleUtils.fetchFallbackLender()).getEdiModel();
        }
        return ediModels.iterator().next();
    }
}
