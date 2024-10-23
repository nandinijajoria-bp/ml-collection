package com.bharatpe.lending.loanV3.dto.response.smfg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;


@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmfgForeclosureDetailsResponse {

    private String status;
    private String message;
    private DataResponse data;
    private String dataResponse;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DataResponse {

        @JsonProperty("LAN")
        private String lan;

        @JsonProperty("CIF_NO")
        private String cifNo;

        @JsonProperty("BAL_PRIN")
        private String balPrin;

        @JsonProperty("FC_INT")
        private String fcInt;

        @JsonProperty("FC_CHRG")
        private String fcChrg;

        @JsonProperty("TOTAL_OUT")
        private String totalOut;

        @JsonProperty("PART_PREPAYMENT")
        private String partPrepayment;

        @JsonProperty("FORECLOSURE_AMT")
        private Double foreclosureAmt;

        @JsonProperty("BUSINESS_DATE")
        private String businessDate;

        @JsonProperty("RESPONSE_DATE")
        private String responseDate;

        @JsonProperty("NEW_FIELD_1")
        private String newField1;

        @JsonProperty("STATUS")
        private String status;

        @JsonProperty("MESSAGE")
        private String message;

        @JsonProperty("CODE")
        private String code;

        @JsonProperty("Due Details")
        private ArrayList<DueDetails> dueDetails;


    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DueDetails {

        @JsonProperty("CHARGE_ID")
        private int chargeId;

        @JsonProperty("CHARGENAME")
        private String chargeName;

        @JsonProperty("CHARGE_VALUE_DATE")
        private String chargeValueDate;

        @JsonProperty("CHARGE_BAL_AMOUNT")
        private int chargeBalAmount;

        @JsonProperty("CHARGE_TYPE")
        private String chargeType;
    }


}
