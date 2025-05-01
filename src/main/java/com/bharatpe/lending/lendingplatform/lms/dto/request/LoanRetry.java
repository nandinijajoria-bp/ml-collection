package com.bharatpe.lending.lendingplatform.lms.dto.request;

import java.time.LocalDateTime;

public class LoanRetry {
    private String bpLoanId;
    private String status;
    private int count;
    private String remarks;
    private CreateLoanRequest request;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
