package com.bharatpe.lending.loanV3.dto.request.smfg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmfgCallbackRequest {

    private String status;
    private Data data;
    private String partnerapplicationid;
    private String status_code;
    private String error;

    @lombok.Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Data {

        private String applicationid;
        @JsonProperty("status_code")
        private Integer statusCode;
        private String status;
        @JsonProperty("callback_stage")
        private String callbackStage;
        private Output output;
    }

    @lombok.Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Output {
        private String result;
        private String finalEligibility;
        private String emiamount;
        private String roi;
        private String tenure;

        //Disbursal
        private String status;
        private String message;
        @JsonProperty("PROSPECTID")
        private String prospectId;
        @JsonProperty("SANCTIONREFERENCENUMBER")
        private String sanctionreferencenumber;
        @JsonProperty("UCICNUMBER")
        private String ucicnumber;
        @JsonProperty("LANNo")
        private String lanno;
        @JsonProperty("UTRNo")
        private String utrno;
        @JsonProperty("NEW_FIELD1")
        private String newfield1;
        @JsonProperty("LEADID")
        private String leadid;
        @JsonProperty("Drawdown_Amount")
        private Double drawdownamount;
        @JsonProperty("Date_Disbursal")
        private Date datedisbursal;
        @JsonProperty("Bank_Disbursal_Time")
        private String bankdisbursaltime;
        @JsonProperty("reject_message")
        private String rejectMessage;
        @JsonProperty("Rejected_Reason")
        private String rejectedreason;
        @JsonProperty("Disbursal_Status")
        private String disbursalstatus;
        @JsonProperty("Transfer_Type")
        private String transfertype;
        @JsonProperty("l_Disb_IFSC")
        private String ldisbifsc;
        @JsonProperty("l_DISB_Account_No")
        private String ldisbaccountno;
    }

}
