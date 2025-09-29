package com.bharatpe.lending.ai.services;

import com.bharatpe.lending.ai.dto.LoanDetailResponse;

public interface ILonaApplicationService {
    LoanDetailResponse getLoanApplicationDetails(Long merchantId);
}
