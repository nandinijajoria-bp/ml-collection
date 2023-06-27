package com.bharatpe.lending.dto;

import com.bharatpe.lending.constant.AutoPayStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MandateUPIStatusResponse {

    public Data data;

    public MandateUPIStatusResponse(Data data) {
        this.data = data;
    }


    @lombok.Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Data {

        private AutoPayStatusEnum status;
        private String orderId;
        private Long applicationId;

        public Data(String orderId, Long applicationId, AutoPayStatusEnum status) {
            this.orderId = orderId;
            this.applicationId = applicationId;
            this.status = status;
        }
    }

}
