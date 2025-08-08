package com.bharatpe.lending.lendingplatform.nbfc.dto.request;

import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VKYCRequest {
    @NotNull
    private ApplicationDetails applicationDetails;
    @NotNull
    private CustomerPersonalDetails customerPersonalDetails;
    @NotNull
    private CustomerAdditionalData customerAdditionalData;
}
