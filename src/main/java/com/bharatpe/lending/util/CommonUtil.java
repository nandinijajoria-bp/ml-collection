package com.bharatpe.lending.util;

import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Objects;

@Slf4j
@Component
public class CommonUtil {

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    public String fetchLoanPurposeByApplicatioId(Long applicationId){
        if(Objects.isNull(applicationId)){
            log.info("invalid applicationId");
            return null;
        }
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(applicationId);
        if(!ObjectUtils.isEmpty(lendingApplicationDetails)){
            return lendingApplicationDetails.getLoanPurpose();
        }
        log.info("lendingApplicationDetails not found with applicationId : {}", applicationId);
        return null;
    }

    public String loanPurposeMapping(String loanPurpose) {
        if (loanPurpose == null) {
            return null;
        }

        switch (loanPurpose.toLowerCase()) {
            case "business_expansion":
                return "Business Expansion";
            case "working_capital_requirement":
                return "Working Capital Requirement";
            case "shop_renovation":
                return "Shop Renovation";
            case "purchase_of_equipment":
                return "Purchase of Equipment";
            case "others":
                return "Others";
            default:
                return null;
        }
    }

}
