package com.bharatpe.lending.loanV3.dto;


import com.bharatpe.lending.loanV3.dto.trillions.TrillionForeclosureRequestDto;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TrilionLoansForeclosureChargesRequestDto {
    private long applicationId;
    private String productName;
    private String lender;
    private Payload payload;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Payload {
        private String lan;
        private double amount;
        private String chargeId;
        @JsonFormat(shape=JsonFormat.Shape.STRING, pattern= "dd-MM-yyyy")
        private Date dueDate;
    }
}
