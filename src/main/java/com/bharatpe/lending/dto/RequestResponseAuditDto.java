package com.bharatpe.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestResponseAuditDto {
    String request;
    String response;
    String requestHeaders;
    String requestUri;
    String requestId;
    String requestParams;
}
