package com.bharatpe.lending.loanV3.dto.response.muthoot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MFDisbursalCallbackDTO {

    private String statusCode;
    private CallbackDTO data;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CallbackDTO {
        public String customerID;
        private String referenceID;
        private String utrNumber;
        private Date disbursedAt;
        public Insurance insurance;
        private double disbursedAmount;
        private String loanAccountNumber;
        private String status;
        private String message;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Insurance{
        public String policyID;
        public String policyNumber;
        public String certificatePDF;
        public String certificateURL;
        public ArrayList<Document> documents;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Document{
        public String docName;
        public String url;
    }
}
