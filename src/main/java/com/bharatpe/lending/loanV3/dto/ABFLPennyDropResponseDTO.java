package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class ABFLPennyDropResponseDTO {
    Long applicationId;
    String lender;
    String productName;
    Boolean success;
    ABFLPennyDropResponseDTO.Data data;

    @lombok.Data
    @ToString
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Builder
    public static class Data {
        public ABFLPennyDropResponseDTO.Data.ResponseData data;
        public Integer status;
        public String message;


        @lombok.Data
        @ToString
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Builder
        public class ResponseData{
            @JsonProperty("IBLRefNo")
            public String iBLRefNo;
            @JsonProperty("CustomerRefNo")
            public String customerRefNo;
            @JsonProperty("StatusCode")
            public String statusCode;
            @JsonProperty("StatusDesc")
            public String statusDesc;
            @JsonProperty("Amount")
            public String amount;
            @JsonProperty("TranType")
            public String tranType;
            @JsonProperty("PaymentDate")
            public String paymentDate;
            @JsonProperty("ImpsBeneName")
            public String impsBeneName;
        }
    }
}
