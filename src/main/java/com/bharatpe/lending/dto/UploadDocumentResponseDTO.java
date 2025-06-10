package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class UploadDocumentResponseDTO {

    private Boolean success;
    private List<Document> documents;
    private boolean invalidPhoto;
    private boolean sidGreaterThanRequired;

    @JsonProperty("is_sid_greater_than_required")
    public boolean isSidGreaterThanRequired() {
        return sidGreaterThanRequired;
    }

    public void setSidGreaterThanRequired(boolean sidGreaterThanRequired) {
        this.sidGreaterThanRequired = sidGreaterThanRequired;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public List<Document> getDocuments() {
        return documents;
    }

    public void setDocuments(List<Document> documents) {
        this.documents = documents;
    }

    public boolean isInvalidPhoto() {
        return invalidPhoto;
    }

    public void setInvalidPhoto(boolean invalidPhoto) {
        this.invalidPhoto = invalidPhoto;
    }

    @Override
    public String toString() {
        return "UploadDocumentResponseDTO{" +
                "success=" + success +
                ", documents=" + documents +
                ", invalidPhoto=" + invalidPhoto +
                ", sidGreaterThanRequired=" + sidGreaterThanRequired +
                '}';
    }

    public static class Document {
        @JsonProperty("proof_id")
        private Long proofId;

        @JsonProperty("proof_type")
        private String proofType;

        @JsonProperty("single_page_document")
        private Integer singlePageDocument;

        public Long getProofId() {
            return proofId;
        }

        public void setProofId(Long proofId) {
            this.proofId = proofId;
        }

        public String getProofType() {
            return proofType;
        }

        public void setProofType(String proofType) {
            this.proofType = proofType;
        }

        public Integer getSinglePageDocument() {
            return singlePageDocument;
        }

        public void setSinglePageDocument(Integer singlePageDocument) {
            this.singlePageDocument = singlePageDocument;
        }

        @Override
        public String toString() {
            return "Document{" +
                    "proofId=" + proofId +
                    ", proofType='" + proofType + '\'' +
                    ", singlePageDocument=" + singlePageDocument +
                    '}';
        }
    }
}