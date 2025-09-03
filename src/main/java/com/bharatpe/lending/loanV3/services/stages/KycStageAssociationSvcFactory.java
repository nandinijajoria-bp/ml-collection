package com.bharatpe.lending.loanV3.services.stages;

import com.bharatpe.lending.loanV3.factory.LenderAssociationServiceFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associations.AbflKycService;
import com.bharatpe.lending.loanV3.services.associations.OldModelService;
import com.bharatpe.lending.loanV3.services.associations.piramal.CreateLeadAndDocUploadInitService;
import com.bharatpe.lending.loanV3.services.associations.KycService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KycStageAssociationSvcFactory extends LenderAssociationServiceFactory {

    @Autowired
    OldModelService oldModelService;

    @Autowired
    AbflKycService abflKycService;

    @Autowired
    CreateLeadAndDocUploadInitService createLeadAndDocUploadInitService;

    @Autowired
    KycService kycService;

    @Override
    public ILenderAssociationService getLenderAssociationService(String lender) {
        switch (lender) {
            case "ABFL":
                return abflKycService;
            case "PIRAMAL":
                return createLeadAndDocUploadInitService;
            case "USFB":
            case "TRILLIONLOANS":
            case "MUTHOOT":
            case "CAPRI":
            case "PAYU":
            case "CREDITSAISON":
            case "UGRO":
            case "OXYZO":
                return kycService;
            default:
                return oldModelService;
        }
    }
}
