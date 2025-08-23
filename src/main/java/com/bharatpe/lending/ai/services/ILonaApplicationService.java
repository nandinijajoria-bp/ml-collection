package com.bharatpe.lending.ai.services;

import com.bharatpe.lending.ai.dto.LoanApplicationDetail;
import com.bharatpe.lending.ai.dto.LoanDetailResponse;

import java.util.List;

public interface ILonaApplicationService {
    public LoanDetailResponse getLoanApplicationDetails(Long merchantId);
}
