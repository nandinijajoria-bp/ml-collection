package com.bharatpe.lending.dto;

import com.bharatpe.lending.constant.AutoPayStatusEnum;
import lombok.Data;

@Data
public class MandateUPIStatusResponse {

    private AutoPayStatusEnum status;
    private String orderId;
    private Long applicationId;

    public MandateUPIStatusResponse(String orderId, Long applicationId, AutoPayStatusEnum status) {
        this.orderId = orderId;
        this.applicationId = applicationId;
        this.status = status;
    }
}
