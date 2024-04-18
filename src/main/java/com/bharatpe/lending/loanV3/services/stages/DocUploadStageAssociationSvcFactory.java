package com.bharatpe.lending.loanV3.services.stages;

import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.factory.LenderAssociationServiceFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associations.AdditionalDocUploadService;
import com.bharatpe.lending.loanV3.services.associations.OldModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DocUploadStageAssociationSvcFactory extends LenderAssociationServiceFactory {
    @Autowired
    AdditionalDocUploadService additionalDocUploadService;

    @Autowired
    OldModelService oldModelService;

    @Override
    public ILenderAssociationService getLenderAssociationService(String lender) {
        switch (lender) {
            case "USFB":
            case "TRILLIONLOANS":
            case "MUTHOOT" :
            case "CAPRI":
                return additionalDocUploadService;
            default:
                return oldModelService;
        }
    }
}
