package com.bharatpe.lending.loanV2.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KfsDetailsDto {

    private String lenderCorporateName;
    private String lenderBusinessAddress;
    private  String lenderContactName;
    private String lenderContactEmail;
    private String lenderContactNumber;
    private String colenderCorporateName;
    private String colenderBusinessAddress;
    private String lenderGrievanceTime;
    private Integer coolingOffDays;
    private Double processingFeePercentage;
    private Double processingFeePercentageWithoutGst;
    private Double processingFeeWithoutGst;
    private Double annualTurnover;
    private Double disbursalAmount;

}
