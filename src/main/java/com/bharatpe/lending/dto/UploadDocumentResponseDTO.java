package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class UploadDocumentResponseDTO {

    private Boolean success;
    private List<Document> documents;
    private boolean invalidPhoto;
    private boolean sidGreaterThanRequired;

    @JsonProperty("is_sid_greater_than_required")
    private boolean isSidGreaterThanRequired;

    @Data
    public static class Document {
        @JsonProperty("proof_id")
        private Long proofId;

        @JsonProperty("proof_type")
        private String proofType;

        @JsonProperty("single_page_document")
        private Integer singlePageDocument;
    }
}