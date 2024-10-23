package com.bharatpe.lending.loanV3.dto.request.smfg;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmfgDocumentUploadRequest {

    private String partnerapplicationid;
    private String partnerid;
    private Vkycinfo vkycinfo;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Vkycinfo {
        private DocumentInfo documentInfo;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DocumentInfo {
        private String documentData;
        private String documentType;
        private String documentName;
        private String longitude;
        private String latitude;
        private String ipaddress;
    }
}
