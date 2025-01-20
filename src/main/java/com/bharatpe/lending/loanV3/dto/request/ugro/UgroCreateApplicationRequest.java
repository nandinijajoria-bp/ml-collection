package com.bharatpe.lending.loanV3.dto.request.ugro;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UgroCreateApplicationRequest {
    private String leadId;
}

