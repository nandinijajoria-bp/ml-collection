package com.bharatpe.lending.loanV3.factory;

import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.services.INbfcLenderGateway;
import com.bharatpe.lending.loanV3.services.gateway.AbflApiGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LenderGatewayFactory {

    @Autowired
    AbflApiGateway abflApiGateway;

    public INbfcLenderGateway getLenderApiGateway(String lender) {
        if (Lender.ABFL.name().equalsIgnoreCase(lender)) {
            return abflApiGateway;
        }
        return null;
    }
}
