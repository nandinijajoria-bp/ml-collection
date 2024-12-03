package com.bharatpe.lending.loanV3.dto.request.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLUpdateLeadRequestV2Dto {
    private String clientId;

    private String leadId;

    private String accountNumber;

    private String ifscCode;

    private String accountHolderName;

    private String bankName;

    private String bankAccountType;

    private String beneficiaryType;

}
