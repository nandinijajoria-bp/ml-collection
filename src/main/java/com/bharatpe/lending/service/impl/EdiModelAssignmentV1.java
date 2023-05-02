package com.bharatpe.lending.service.impl;

import com.bharatpe.lending.common.enums.EdiModel;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.loanV3.dto.ExtractedRulesAndLendersDTO;
import com.bharatpe.lending.service.AssignmentRuleUtils;
import com.bharatpe.lending.service.IEdiModelAssignment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;

@Service
@Slf4j
public class EdiModelAssignmentV1 implements IEdiModelAssignment {

    @Autowired
    AssignmentRuleUtils assignmentRuleUtils;

    @Override
    public EdiModel assignModel(Long merchantId) {
        try {
            ExtractedRulesAndLendersDTO extractedRulesAndLendersDTO = assignmentRuleUtils.extractRules(merchantId, null);
            log.info("extracted response {}", extractedRulesAndLendersDTO);
            log.info("extracted lrvs response {}", extractedRulesAndLendersDTO.getRiskParamsDTO());
            log.info("extracted assignment rules response {}", extractedRulesAndLendersDTO.getLenderAssignmentRules());
            assignmentRuleUtils.filterLenders(extractedRulesAndLendersDTO);
            log.info("extracted filtered  lenders response {}", extractedRulesAndLendersDTO.getFilteredLenders());
            Set<EdiModel> ediModels = assignmentRuleUtils.extractEdiModelFromFilteredLenders(extractedRulesAndLendersDTO);
            log.info("ediModels derived {}", ediModels);
            EdiModel assignedModel = null;
            if (ediModels.isEmpty()) {
    //            fallback lender here
                String fallbackLender = assignmentRuleUtils.fetchFallbackLender();
                assignedModel = LenderOffDays.valueOf(fallbackLender).getEdiModel();
                log.info("assigned lender {} model {} for merchant {}", fallbackLender, assignedModel, merchantId);
                return LenderOffDays.valueOf(fallbackLender).getEdiModel();
            }
            assignedModel = ediModels.iterator().next();
            log.info("assigned model {} for merchant {}", assignedModel, merchantId);
            return assignedModel;
        } catch (Exception e) {
            log.error("error occurred while computing edi Model for merchant {} {} {}", merchantId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return EdiModel.SEVEN_DAY_MODEL;
    }
}
