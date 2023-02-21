package com.bharatpe.lending.loanV3.services;

import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssignment;
import org.springframework.stereotype.Service;

@Service
public class LenderMappingServiceV3Impl implements ILenderAssignment {
    @Override
    public Lender assignLender(Integer ediModel) {
        return Lender.ABFL;
    }

    @Override
    public Lender changeLender(Long applicationId) {
        // change lender in application
        return null;
    }
}
