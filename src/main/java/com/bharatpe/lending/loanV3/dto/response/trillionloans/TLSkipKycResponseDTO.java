package com.bharatpe.lending.loanV3.dto.response.trillionloans;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLSkipKycResponseDTO {
    private String status;
    private String message;
    private Object traceId;
    private Object data;
}
