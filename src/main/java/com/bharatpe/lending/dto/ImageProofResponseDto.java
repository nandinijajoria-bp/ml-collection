package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;
import java.util.List;

public class ImageProofResponseDto {

    private boolean success;
    private List<Proof> proofs;
    private boolean qrMandatory;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public List<Proof> getProofs() { return proofs; }
    public void setProofs(List<Proof> proofs) { this.proofs = proofs; }

    public boolean isQrMandatory() { return qrMandatory; }
    public void setQrMandatory(boolean qrMandatory) { this.qrMandatory = qrMandatory; }

    @Override
    public String toString() {
        return "ImageProofResponseDto{" +
                "success=" + success +
                ", proofs=" + proofs +
                ", qrMandatory=" + qrMandatory +
                '}';
    }

    public static class Proof {
        @JsonProperty("proof_type")
        private String proofType;

        @JsonProperty("single_page_document")
        private boolean singlePageDocument;

        @JsonProperty("proof")
        private List<String> proofUrls;

        @JsonProperty("updated_at")
        private Date updatedAt;

        public String getProofType() { return proofType; }
        public void setProofType(String proofType) { this.proofType = proofType; }

        public boolean isSinglePageDocument() { return singlePageDocument; }
        public void setSinglePageDocument(boolean singlePageDocument) { this.singlePageDocument = singlePageDocument; }

        public List<String> getProofUrls() { return proofUrls; }
        public void setProofUrls(List<String> proofUrls) { this.proofUrls = proofUrls; }

        public Date getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

        @Override
        public String toString() {
            return "Proof{" +
                    "proofType='" + proofType + '\'' +
                    ", singlePageDocument=" + singlePageDocument +
                    ", proofUrls=" + proofUrls +
                    ", updatedAt=" + updatedAt +
                    '}';
        }
    }
}
