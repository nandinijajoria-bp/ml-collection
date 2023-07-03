package com.bharatpe.lending.loanV3.services.stages;

import com.bharatpe.lending.loanV3.factory.LenderAssociationServiceFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associations.OldModelService;
import com.bharatpe.lending.loanV3.services.associations.piramal.PushAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PushAuditSvcFactory extends LenderAssociationServiceFactory {

    @Autowired
    OldModelService oldModelService;

    @Autowired
    PushAuditService pushAuditService;


    @Override
    public ILenderAssociationService getLenderAssociationService(String lender) {
        switch (lender) {
            case "PIRAMAL":
                return pushAuditService;
            default:
                return oldModelService;
        }
    }
}
