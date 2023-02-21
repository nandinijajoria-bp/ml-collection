package com.bharatpe.lending.loanV3.services.stages;

import com.bharatpe.lending.loanV3.factory.LenderAssociationServiceFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associations.AbflDataUploadService;
import com.bharatpe.lending.loanV3.services.associations.OldModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DataUploadStageAssociationSvcFactory extends LenderAssociationServiceFactory {
    @Autowired
    AbflDataUploadService abflDataUploadService;

    @Autowired
    OldModelService oldModelService;

    @Override
    public ILenderAssociationService getLenderAssociationService(String lender) {
        switch (lender) {
            case "ABFL":
                return abflDataUploadService;
            default:
                return oldModelService;
        }
    }
}
