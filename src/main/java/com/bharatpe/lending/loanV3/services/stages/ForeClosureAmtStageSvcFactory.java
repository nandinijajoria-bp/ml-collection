package com.bharatpe.lending.loanV3.services.stages;

import com.bharatpe.lending.loanV3.factory.LenderAssociationServiceFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associations.AbflForeclosureFetchService;
import com.bharatpe.lending.loanV3.services.associations.piramal.PiramalForeclosureFetchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ForeClosureAmtStageSvcFactory extends LenderAssociationServiceFactory {
    @Autowired
    AbflForeclosureFetchService abflForeclosureFetchService;

    @Autowired
    PiramalForeclosureFetchService piramalForeclosureFetchService;
    @Override
    public ILenderAssociationService getLenderAssociationService(String lender) {
        switch (lender) {
            case "ABFL":
                return abflForeclosureFetchService;
            case "PIRAMAL":
                return piramalForeclosureFetchService;
            default:
                return null;
        }
    }
}
