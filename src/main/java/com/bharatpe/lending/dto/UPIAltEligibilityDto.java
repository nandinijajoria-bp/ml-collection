package com.bharatpe.lending.dto;

import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UPIAltEligibilityDto {

    private Data data;

    @NoArgsConstructor
    @AllArgsConstructor
    @lombok.Data
    public static class Data {
        private boolean isEligible = false;
        private UpiMandateDetails upiMandateDetails;
        private UpiMandateDetails prevUpiMandateDetails;
    }

    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UpiMandateDetails {
        private String status;
        private String bankName;
        private String accountNumber;
        private String accountName;
        private String ifsc;
    }

}
