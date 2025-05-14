package com.bharatpe.lending.lendingplatform.lms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class EmandateResponse {

    private String bpLoanId;

    private Date date;
}
