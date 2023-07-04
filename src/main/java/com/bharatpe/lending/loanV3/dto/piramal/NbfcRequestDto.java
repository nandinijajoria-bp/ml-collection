package com.bharatpe.lending.loanV3.dto.piramal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NbfcRequestDto<T> {

        String lender;
        String productName;
        Long applicationId;
        T payload;
}
