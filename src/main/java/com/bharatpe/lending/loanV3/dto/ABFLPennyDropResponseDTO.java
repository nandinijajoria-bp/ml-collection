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
    Data data;

    @lombok.Data
    @ToString
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Builder
    public static class Data {
        public String responseStatus;
        public ResponseData data;
        public Error error;
        public Integer status;
        public String message;

        @lombok.Data
        @ToString
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Builder
        public static class Error{
            public String code;
            public String description;
            public String errorType;
        }

        @lombok.Data
        @ToString
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Builder
        public static class ResponseData{
            @JsonProperty("IBLRefNo")
            public String iBLRefNo;
            @JsonProperty("CustomerRefNo")
            public String customerRefNo;
            @JsonProperty("StatusDesc")
            public String statusDesc;
            @JsonProperty("Amount")
            public String amount;
            @JsonProperty("TranType")
            public String tranType;
            @JsonProperty("StatusCode")
            public String statusCode;
            @JsonProperty("RRNRefNo")
            public String rRNRefNo;
        }
    }
}
