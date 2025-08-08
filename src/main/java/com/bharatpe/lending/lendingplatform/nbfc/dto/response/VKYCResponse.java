package com.bharatpe.lending.lendingplatform.nbfc.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
public class VKYCResponse {
    private String statusCode;
    private String leadId;
    private String trackingId;
    private String vkycWaitPageUrl;
    private Date expiryTime;

}
