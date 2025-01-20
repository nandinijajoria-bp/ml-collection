package com.bharatpe.lending.loanV3.dto.response.ugro;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UgroCreateLeadResponse {
    private Boolean status;
    private String id;
    private String err;
    private String leadId;
}
