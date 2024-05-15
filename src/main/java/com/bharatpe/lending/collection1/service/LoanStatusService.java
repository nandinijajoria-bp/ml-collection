package com.bharatpe.lending.collection1.service;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection1.dto.LoanClosureDTO;
import com.bharatpe.lending.common.entity.LendingCollectionExcess;

import java.util.List;

public interface LoanStatusService {

    void processLoanClosure(LoanClosureDTO loanClosureDTO);

    void waiverSettleLoan(LendingPaymentSchedule activeLoan, Double amount, String bankRefNo, String source, String terminalOrderId, Double excessCollectionBalance, List<LendingCollectionExcess> lendingCollectionExcessList);
}
