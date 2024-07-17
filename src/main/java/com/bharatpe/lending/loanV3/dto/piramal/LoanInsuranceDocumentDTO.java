package com.bharatpe.lending.loanV3.dto.piramal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoanInsuranceDocumentDTO {

    private String leadId;
    private String fileBlob;
    private String lanNo;
    private String fileFormat;
    private String documentType;
    private String policyNumber;
    private String policyStatus;
    private Long commencementDate;
    private Long maturityDate;

}
