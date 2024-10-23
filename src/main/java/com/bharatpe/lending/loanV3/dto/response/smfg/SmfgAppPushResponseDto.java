package com.bharatpe.lending.loanV3.dto.response.smfg;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmfgAppPushResponseDto {
    private String status;
    private Data data;
    private String partnerapplicationid;
    private String status_code;

    @lombok.Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Data {
        private String applicationid;
    }
}
