package com.bharatpe.lending.loanV3.dto.response.muthoot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MuthootPreQualificationStatusResponseData {
    private String customerID;
    private String referenceID;
    private List<MuthootPreQualificationStatusResponseResult> results;
    private String status;
}
