package com.bharatpe.lending.loanV3.dto.response.ugro;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UgroClosureBreakupResponse {
    private String status;
    private BreakupDetails breakup;

    @Data
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BreakupDetails {
        private Double la;
        private Double po;
        private Integer id;
        private Integer cd;
        private Double fc;
        private Double fcGst;
        private Double fcPctOnPo;
        private Double partialInterest;
        private Double waiverCharge;
        private Double waiverInterest;
        private Double perDayInterest;
        private Double lpp;
        private Double ea;
        private Double netCd;
        private Double netId;
        private Double total;
    }
}