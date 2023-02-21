package com.bharatpe.lending.loanV3.services.stages;

import com.bharatpe.lending.loanV3.factory.LenderAssociationServiceFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associations.AbflSancWrapperService;
import com.bharatpe.lending.loanV3.services.associations.OldModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
public class SanctionWrapperStageAssociationSvcFactory  extends LenderAssociationServiceFactory {
    @Autowired
    AbflSancWrapperService abflSancWrapperService;

    @Autowired
    OldModelService oldModelService;

    @Override
    public ILenderAssociationService getLenderAssociationService(String lender) {
        switch (lender) {
            case "ABFL":
                return abflSancWrapperService;
            default:
                return oldModelService;
        }
    }
}
