package com.bharatpe.lending.loanV3.dto.piramal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PiramalPennyDropResponseDTO {

    private String leadId;
    private String message;
    private String status;
    private String errorCode;
    private String errorDescription;
    private String httpStatus;
}
