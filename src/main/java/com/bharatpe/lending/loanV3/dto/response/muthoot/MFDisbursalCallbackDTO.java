package com.bharatpe.lending.loanV3.dto.response.muthoot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
        private String referenceID;
        private String utrNumber;
        private Date disbursedAt;
        private double disbursedAmount;
        private String loanAccountNumber;
        private String status;
        private String message;
    }

}
