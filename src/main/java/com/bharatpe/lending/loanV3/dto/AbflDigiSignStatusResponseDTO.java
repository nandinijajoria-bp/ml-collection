package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class AbflDigiSignStatusResponseDTO {
    private String responseStatus;
    private ResponseData data;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseData {
        private String accountId;
        private String status;
        private String shortUrl;

    }

}
