package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class UploadDocumentResponseDTO {
    private Boolean success;

    private List<Document> document;

    private boolean isInValidPhoto;

    @JsonProperty("selected_loan")
    Map<String, Object> selectedLoan;

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public List<Document> getDocument() {
        return document;
    }

    public void setDocument(List<Document> document) {
        this.document = document;
    }

    public Map<String, Object> getSelectedLoan() {
        return selectedLoan;
    }

    public void setSelectedLoan(Map<String, Object> selectedLoan) {
        this.selectedLoan = selectedLoan;
    }

    public boolean isInValidPhoto() {
        return isInValidPhoto;
    }

    public void setInValidPhoto(boolean inValidPhoto) {
        isInValidPhoto = inValidPhoto;
    }

    @Override
    public String toString() {
        return "UploadDocumentResponse{" +
                "success=" + success +
                ", document=" + document +
                ", selectedLoan=" + selectedLoan +
                '}';
    }

    public class Document {
        @JsonProperty("proof_id")
        Long proofId;

        @JsonProperty("proof_type")
        String proofType;

        @JsonProperty("single_page_document")
        Integer singlePageDocument;

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
