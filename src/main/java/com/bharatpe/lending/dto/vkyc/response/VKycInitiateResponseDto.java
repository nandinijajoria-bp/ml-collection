package com.bharatpe.lending.dto.vkyc.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VKycInitiateResponseDto {
    private String leadId;
    private String sessionId;
    private String sessionUrl;
    private Date expiryTime;
}
