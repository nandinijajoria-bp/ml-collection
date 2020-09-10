package com.bharatpe.lending.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreditUploadDocumentRequestDTO {


    @JsonProperty("application_id")
    private Long applicationId;

    private List<Document> documents;

    public List<Document> getDocuments() {
        return documents;
    }

   
    
    public void setDocuments(List<Document> documents) {
        this.documents = documents;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }



	@Override
	public String toString() {
		return "CreditUploadDocumentRequestDTO [applicationId=" + applicationId + ", documents=" + documents + "]";
	}
    
 public static class Document {

        @JsonProperty("change_flag")
        private Boolean changeFlag;


        @JsonProperty("proof_type")
        private String proofType;


        @JsonProperty("single_page_document")
        private Boolean singlePageDocument;

        List<String> proof;

        public Boolean getChangeFlag() {
            return changeFlag;
        }

        public void setChangeFlag(Boolean changeFlag) {
            this.changeFlag = changeFlag;
        }

        public String getProofType() {
            return proofType;
        }

        public void setProofType(String proofType) {
            this.proofType = proofType;
        }

        public Boolean getSinglePageDocument() {
            return singlePageDocument;
        }

        public void setSinglePageDocument(Boolean singlePageDocument) {
            this.singlePageDocument = singlePageDocument;
        }

        public List<String> getProof() {
            return proof;
        }

        public void setProof(List<String> proof) {
            this.proof = proof;
        }

        @Override
        public String toString() {
            return "Documents{" +
                    "changeFlag=" + changeFlag +
                    ", proofType='" + proofType + '\'' +
                    ", singlePageDocument=" + singlePageDocument +
                    '}';
        }
    }
    
}
