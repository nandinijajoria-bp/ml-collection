package com.bharatpe.lending.loanV3.dto.request.ugro;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UgroDocumentUploadRequest {

    private String leadId;
    private String documentType;
    private MetaData metaData;
    private File file;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MetaData {
        private String docType;
        private String data;
        private String fileName;
        private String url;
    }

}
