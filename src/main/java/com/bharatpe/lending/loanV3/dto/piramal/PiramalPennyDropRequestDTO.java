package com.bharatpe.lending.loanV3.dto.piramal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PiramalPennyDropRequestDTO {

    private String leadId;
    private String ifsc;
    private String accountNumber;
    private String bankAccountType;
    private String productId;
}
