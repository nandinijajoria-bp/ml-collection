package com.bharatpe.lending.dto;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@Builder
public class InitiateKycBLDocUploadDTO {
    private String referenceId;
    private String merchantId;
    private Integer docUploadCountRequiredByProduct;
    private String callBackUrl;
}
