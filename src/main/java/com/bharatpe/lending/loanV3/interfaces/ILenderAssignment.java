package com.bharatpe.lending.loanV3.interfaces;

import com.bharatpe.lending.enums.Lender;
import org.springframework.stereotype.Service;

public interface ILenderAssignment {
    Lender assignLender(Integer ediModel);
    Lender changeLender(Long applicationId);
}
