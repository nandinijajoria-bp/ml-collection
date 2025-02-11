package com.bharatpe.lending.loanV3.dto.response.oxyzo;

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
public class OxyzoBreResponseDTO {
    private String organisationId;
    private String loanApplicationId;
    private String loanId;
    private Boolean limitAssigned;
    private String rejectionReason;
}
