package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class UploadDocumentRequestDTO {

    @JsonProperty("application_id")
    private Long applicationId;

    private List<Document> documents;

    @Data
    public static class Document {

        @JsonProperty("change_flag")
        private Boolean changeFlag;

        @JsonProperty("proof_type")
        private String proofType;

        @JsonProperty("single_page_document")
        private Boolean singlePageDocument;

        private List<String> proof;
    }
}