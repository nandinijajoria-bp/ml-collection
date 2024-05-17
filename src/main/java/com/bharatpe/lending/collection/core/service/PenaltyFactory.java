package com.bharatpe.lending.collection.core.service;

import com.bharatpe.lending.collection.core.service.impl.LiquiLoansPenaltyService;
import com.bharatpe.lending.collection.core.service.impl.TrillionLoansPenaltyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PenaltyFactory {

    @Autowired
    TrillionLoansPenaltyService trillionLoansPenaltyService;

    @Autowired
    LiquiLoansPenaltyService liquiLoansPenaltyService;


    public IPenaltyService getPenaltyService(String lender) {
        switch (lender) {

            case "LIQUILOANS_P2P":
            case "LIQUILOANS_P2P_OF":
                return liquiLoansPenaltyService;

            case "TRILLIONLOANS":
                return trillionLoansPenaltyService;

            default:
                return null;
        }
    }


}
