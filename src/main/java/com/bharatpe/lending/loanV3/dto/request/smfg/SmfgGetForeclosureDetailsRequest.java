package com.bharatpe.lending.loanV3.dto.request.smfg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmfgGetForeclosureDetailsRequest {

    @JsonProperty("AUTHENTICATION")
    private Authentication authentication;

    @JsonProperty("BASICINFO")
    private BasicInfo basicInfo;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Authentication {

        @JsonProperty("APP_NAME")
        private String appName;

        @JsonProperty("APP_PASS")
        private String appPass;

        @JsonProperty("IPADDRESS")
        private String ipAddress;

        @JsonProperty("DEVICE_ID")
        private String deviceId;

        @JsonProperty("LONGITUDE")
        private String longitude;

        @JsonProperty("LATITUDE")
        private String latitude;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BasicInfo {

        @JsonProperty("LANID")
        private String landId;

    }
}
