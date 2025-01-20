package com.bharatpe.lending.loanV3.dto.response.ugro;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UgroConsentResponse {
    private String status;
    private String message;
}

