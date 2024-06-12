package com.bharatpe.lending.collection.core.service;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.collection.core.dto.internal.LoanClosureDTO;
import com.bharatpe.lending.collection.core.dto.internal.LoanPaymentDetailDTO;
import com.bharatpe.lending.common.entity.LendingCollectionExcess;

import java.util.List;

public interface LoanPaymentService {

    LendingPaymentSchedule adjustMoney(LendingPaymentSchedule loan, LoanPaymentDetailDTO payment);
}
