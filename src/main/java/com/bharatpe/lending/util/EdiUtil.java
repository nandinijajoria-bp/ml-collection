package com.bharatpe.lending.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.util.List;

@Slf4j
@Service
public class EdiUtil {

    @Value("${round.down.eligible.lenders:TRILLIONLOANS}")
    private List<String> roundDownEligibleLenders;

    public double getEdiAfterRoundingLogic(Long applicationId, @NotNull Double edi,String lender) {
        if(roundDownEligibleLenders.contains(lender)){
            log.info("rounding-down the edi amount for application: {}, and lender: {}",applicationId , lender);
            return Math.floor(edi);
        } else {
            log.info("rounding-up the edi amount for application: {}, and lender: {}", applicationId, lender);
            return Math.ceil(edi);
        }
    }

    public double getEdiAfterRoundingOfferLogic(Long merchantId, @NotNull Double edi,String lender) {
        if(roundDownEligibleLenders.contains(lender)){
            log.info("rounding-down the edi amount for merchantId: {}, and lender: {}",merchantId , lender);
            return Math.floor(edi);
        } else {
            log.info("rounding-up the edi amount for merchantId: {}, and lender: {}", merchantId, lender);
            return Math.ceil(edi);
        }
    }

    public boolean isRoundDownEligibleLender(String lender) {
        return roundDownEligibleLenders.contains(lender);
    }
}
