package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class AbflDigiSignResponseDTO {
    Long applicationId;
    String lender;
    Boolean success;
    String productName;
    RpsResponse data;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        String responseStatus;
        Error error;
        ResponseData data;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Error {
        private String code;
        private String description;
        private String errorType;
    }


    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseData{
        String accountId;
        SuccessMessage success_message;
        String pdf_request_type;
    }
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuccessMessage{
        String Message;
        String Status;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RpsResponse {
        String responseStatus;
        Error error;
        ResponseData data;
    }

}
