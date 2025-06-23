package com.bharatpe.lending.dto.vkyc.request;

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
public class VKycInitiateRequestDto {
    private String leadId;
    private String redirectUrl;
    private Boolean isRetry;
}
