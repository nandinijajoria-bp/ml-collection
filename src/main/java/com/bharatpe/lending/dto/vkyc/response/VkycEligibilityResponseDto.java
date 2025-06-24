package com.bharatpe.lending.dto.vkyc.response;

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
public class VkycEligibilityResponseDto {
    private String leadId;
    private boolean vkycEligible;
    private boolean dkycEligible;
}
