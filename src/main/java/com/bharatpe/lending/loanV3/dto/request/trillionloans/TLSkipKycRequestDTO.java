package com.bharatpe.lending.loanV3.dto.request.trillionloans;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLSkipKycRequestDTO {
    private String consentKey;
    private String ipAddress;
    private Boolean isAccepted;
    private String dateTime;
    private String reuseLoanId;
    private AdditionalDetails additionalDetails;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AdditionalDetails {
        private String source;
        private String version;
        private String timestamp;
    }
}
