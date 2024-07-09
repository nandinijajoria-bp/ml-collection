package com.bharatpe.lending.loanV3.services.stages;

import com.bharatpe.lending.loanV3.factory.LenderAssociationServiceFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associations.AbflBreService;
import com.bharatpe.lending.loanV3.services.associations.AbflRepaymentScheduleService;
import com.bharatpe.lending.loanV3.services.associations.OldModelService;
import com.bharatpe.lending.loanV3.services.associations.UpdateLeadAndBREWorkflowService;
import com.bharatpe.lending.loanV3.services.associations.piramal.UpdateLeadAndRunBREInitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RpsStageAssociationSvcFactory extends LenderAssociationServiceFactory{

    @Autowired
    AbflRepaymentScheduleService abflRepaymentScheduleService;

    @Override
    public ILenderAssociationService getLenderAssociationService(String lender) {
        switch (lender) {
            case "ABFL":
                return abflRepaymentScheduleService;
            default:
                return null;
        }
    }
}

