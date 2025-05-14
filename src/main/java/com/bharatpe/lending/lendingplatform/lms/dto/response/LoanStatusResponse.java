package com.bharatpe.lending.lendingplatform.lms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoanStatusResponse {

    private String externalLmsId;

    private Date date;
}
