package com.bharatpe.lending.loanV3.factory;

import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;

public abstract class LenderAssociationServiceFactory {
    public abstract ILenderAssociationService getLenderAssociationService(String lender);
}
