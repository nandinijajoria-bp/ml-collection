package com.bharatpe.lending.loanV3.services.stages;

import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.factory.LenderAssociationServiceFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associations.AbflBreService;
import com.bharatpe.lending.loanV3.services.associations.OldModelService;
import com.bharatpe.lending.loanV3.services.associations.UpdateLeadAndBREWorkflowService;
import com.bharatpe.lending.loanV3.services.associations.piramal.UpdateLeadAndRunBREInitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BreStageAssociationSvcFactory extends LenderAssociationServiceFactory {

    @Autowired
    OldModelService oldModelService;

    @Autowired
    AbflBreService abflBreService;

    @Autowired
    UpdateLeadAndRunBREInitService updateLeadAndRunBREInitService;

    @Autowired
    UpdateLeadAndBREWorkflowService updateLeadAndBREWorkflowService;

    @Override
    public ILenderAssociationService getLenderAssociationService(String lender) {
        switch (lender) {
            case "ABFL":
                return abflBreService;
            case "PIRAMAL":
                return updateLeadAndRunBREInitService;
            case "USFB":
            case "TRILLIONLOANS":
            case "MUTHOOT" :
            case "CAPRI":
            case "PAYU":
            case "CREDITSAISON":
            case "SMFG":
                return updateLeadAndBREWorkflowService;
            default:
                return oldModelService;
        }
    }
}
