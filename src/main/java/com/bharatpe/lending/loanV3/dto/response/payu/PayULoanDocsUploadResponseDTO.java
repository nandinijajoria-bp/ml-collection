package com.bharatpe.lending.loanV3.dto.response.payu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayULoanDocsUploadResponseDTO {

    @JsonProperty("document_list")
    private List<DocumentList> documentList;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocumentList {

        private String name;

        private String type;

        private String documentId;

        @JsonProperty("content_url")
        private String contentUrl;

        private Boolean isSigned;

        @JsonProperty("document_type")
        private String documentType;

    }

}
