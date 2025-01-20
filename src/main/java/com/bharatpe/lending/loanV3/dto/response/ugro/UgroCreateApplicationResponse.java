package com.bharatpe.lending.loanV3.dto.response.ugro;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UgroCreateApplicationResponse {
    private Boolean status;
    private String applicationId;
    private String userId;
    private String leadId;
}
