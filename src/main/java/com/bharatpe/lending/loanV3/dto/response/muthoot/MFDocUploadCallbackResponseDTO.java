package com.bharatpe.lending.loanV3.dto.response.muthoot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MFDocUploadCallbackResponseDTO {
    private ResponseData data;
    private String error;
    private String statusCode;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseData {
        private String status;
        private String agreementURL;
    }

}
