package com.bharatpe.lending.loanV3.utils;

import com.bharatpe.lending.common.entity.LendingRiskVariables;
import org.apache.commons.collections.MapUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LrvUtility {

    @Value("${bl.eligible.merchant.ids:}")
    private List<Long> blEligibleList;

    @Value("${bl.non.eligible.merchant.ids:}")
    private List<Long> blNonEligibleList;

    public boolean getBlEligibility(LendingRiskVariables lendingRiskVariables) {
        if(lendingRiskVariables==null){
            return false;
        }
        if(blEligibleList.contains(lendingRiskVariables.getMerchantId())){
            return true;
        }
        if(blNonEligibleList.contains(lendingRiskVariables.getMerchantId())){
            return false;
        }
        Map<String, Object> metadata = lendingRiskVariables.getMetaData();
        if(MapUtils.isEmpty(metadata) || !metadata.containsKey("BUSINESSLOAN")){
            return false;
        }
        Map businessLoanData = MapUtils.getMap(metadata, "BUSINESSLOAN");
        String blEligible = MapUtils.getString(businessLoanData, "isBlEligible");
        return "Y".equalsIgnoreCase(blEligible);
    }
}
