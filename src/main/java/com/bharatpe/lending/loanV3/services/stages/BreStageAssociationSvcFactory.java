package com.bharatpe.lending.loanV3.services.stages;

import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.factory.LenderAssociationServiceFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associations.AbflBreService;
import com.bharatpe.lending.loanV3.services.associations.OldModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BreStageAssociationSvcFactory extends LenderAssociationServiceFactory {

    @Autowired
    OldModelService oldModelService;

    @Autowired
    AbflBreService abflBreService;

    @Override
    public ILenderAssociationService getLenderAssociationService(String lender) {
        switch (lender) {
            case "ABFL":
                return abflBreService;
            default:
                return oldModelService;
        }
    }
}
