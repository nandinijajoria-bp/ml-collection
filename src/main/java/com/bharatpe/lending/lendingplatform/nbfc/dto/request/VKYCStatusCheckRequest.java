package com.bharatpe.lending.lendingplatform.nbfc.dto.request;

import com.bharatpe.lending.lendingplatform.nbfc.dto.pojo.ApplicationDetails;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@Builder
public class VKYCStatusCheckRequest {
    @NotNull
    ApplicationDetails applicationDetails;
}
