package com.bharatpe.lending.loanV3.dto.response.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLCreateLeadResponseDto {
    private Long clientId;
    private Long resourceId;
    private Boolean rollbackTransaction;
    private AdditionalResponseData additionalResponseData;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AdditionalResponseData {
        private String loanApplicationReferenceNo;
    }
}
