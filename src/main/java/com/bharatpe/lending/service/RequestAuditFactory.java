package com.bharatpe.lending.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RequestAuditFactory {

    @Autowired
    private LoanDetailsRequestAuditService loanDetailsRequestAuditService;

    public IRequestAudit getRequestAuditService(String uri) {
        if (uri.equalsIgnoreCase("/lending/loanDetails/v2")) {
            return loanDetailsRequestAuditService;
        }
        return null;
    }
}
