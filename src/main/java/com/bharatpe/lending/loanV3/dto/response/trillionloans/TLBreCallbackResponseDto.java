package com.bharatpe.lending.loanV3.dto.response.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLBreCallbackResponseDto {

    public Object[] reasons;
    public Boolean success;
    public String limit;
    public String action;
    public String roi;
    public String loanId;
    public String tenure;
}
