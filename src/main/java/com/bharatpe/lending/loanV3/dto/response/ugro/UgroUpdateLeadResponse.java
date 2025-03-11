package com.bharatpe.lending.loanV3.dto.response.ugro;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UgroUpdateLeadResponse {
    private Boolean status;
    private String id;
    private String leadId;
}
