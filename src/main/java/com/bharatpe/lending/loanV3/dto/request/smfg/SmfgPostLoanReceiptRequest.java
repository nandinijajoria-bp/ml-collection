package com.bharatpe.lending.loanV3.dto.request.smfg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmfgPostLoanReceiptRequest {

    @JsonProperty("AUTHENTICATION")
    private Authentication authentication;

    @JsonProperty("BASICINFO")
    private BasicInfo basicinfo;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Authentication {

        @JsonProperty("APP_NAME")
        private String appName;

        @JsonProperty("APP_PASS")
        private String appPass;

        @JsonProperty("IPADDRESS")
        private String ipAddress;

        @JsonProperty("DEVICE_ID")
        private String deviceId;

    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BasicInfo {
        @JsonProperty("PROSPECT_ID")
        private String prospectId;

        @JsonProperty("INSTR_NO")
        private String instrNo;

        @JsonProperty("INSTR_TYPE")
        private Long instrType;

        @JsonProperty("TRANSACTION_DATE")
        private String transactionDate;

        @JsonProperty("VALUE_DATE")
        private String valueDate;

        @JsonProperty("DEPOSIT_AMT")
        private Double depositAmt;

        @JsonProperty("TOWARDS")
        private String towards;

        @JsonProperty("NEW_FIELD1")
        private String newfield1;
    }


}
