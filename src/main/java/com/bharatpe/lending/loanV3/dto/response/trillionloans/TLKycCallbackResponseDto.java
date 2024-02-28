package com.bharatpe.lending.loanV3.dto.response.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLKycCallbackResponseDto {
        private String aadhaarxmlfacematch;
        private String aadhaarxmlnamematch;
        private String loanApplicationId;
        private String aadhaarxmlnamematchStatus;
        private Integer taskConfigId;
        private String aadhaarxmlvaliditycheck;
        private String aadhaarxmlfacematchStatus;
        private String aadhaarxmlvaliditystatus;

}
