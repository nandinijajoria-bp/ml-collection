package com.bharatpe.lending.loanV3.dto.request.muthoot;

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
public class MFForeclosureDetailsRequestDTO {

    private String customerID;
    private String program;

}
