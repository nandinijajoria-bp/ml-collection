package com.bharatpe.lending.lendingplatform.underwriting.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnderwritingBaseRequest<T> {
    private String customerMobile;
    private String customerId;
    private String clientIdentifier;
    private T data;
}
