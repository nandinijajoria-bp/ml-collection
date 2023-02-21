package com.bharatpe.lending.loanV3.services.stages;

import com.bharatpe.lending.loanV3.factory.LenderAssociationServiceFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associations.AbflReceiptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ReceiptStageSvcFactory extends LenderAssociationServiceFactory {

    @Autowired
    AbflReceiptService abflReceiptService;

    @Override
    public ILenderAssociationService getLenderAssociationService(String lender) {
        switch (lender) {
            case "ABFL":
                return abflReceiptService;
            default:
                return null;
        }
    }
}