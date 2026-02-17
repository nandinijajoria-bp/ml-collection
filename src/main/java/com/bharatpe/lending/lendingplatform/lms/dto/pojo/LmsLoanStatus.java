package com.bharatpe.lending.lendingplatform.lms.dto.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LmsLoanStatus {
    private String bpLoanId;
    private String status;
}
