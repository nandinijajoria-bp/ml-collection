package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class ImageProofResponseDto {

    private boolean success;
    private List<Proof> proofs;
    private boolean qrMandatory;

    @Data
    public static class Proof {
        @JsonProperty("proof_type")
        private String proofType;

        @JsonProperty("single_page_document")
        private boolean singlePageDocument;

        @JsonProperty("proof")
        private List<String> proofUrls;

        @JsonProperty("updated_at")
        private Date updatedAt;
    }
}