package com.bharatpe.lending.loanV3.dto.response.payu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayUDocumentDownloadResponseDTO {

    private String apiStatus;
    private Object errorCode;
    private int httpStatus;
    private ApiResponse apiResponse;
    private String message;
    private Object headers;
    private Object page;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiResponse{
        @JsonProperty("document_list")
        private ArrayList<DocumentList> documentList;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocumentList{
        private String name;
        private String type;
        private String documentId;
        private boolean isSigned;
        @JsonProperty("content_url")
        private String contentUrl;
        @JsonProperty("document_type")
        private String documentType;
    }
}
