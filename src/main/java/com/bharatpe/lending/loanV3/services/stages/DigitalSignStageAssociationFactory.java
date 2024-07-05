package com.bharatpe.lending.loanV3.services.stages;

import com.bharatpe.lending.loanV3.factory.LenderAssociationServiceFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associations.DigiSignService;
import com.bharatpe.lending.loanV3.services.associations.ABFLDigiSignService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DigitalSignStageAssociationFactory extends LenderAssociationServiceFactory {

    @Autowired
    DigiSignService digiSignService;

    @Autowired
    ABFLDigiSignService abflDigiSignService;

    @Override
    public ILenderAssociationService getLenderAssociationService(String lender) {
        switch (lender) {
            case "TRILLIONLOANS":
                return digiSignService;
            case "ABFL":
                return abflDigiSignService;
            default:
                return null;
        }
    }
}
