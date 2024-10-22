package com.bharatpe.lending.loanV3.dto.request.creditsasion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreditSasionDocumentUploadRequestDTO {

    private String partnerLoanId;
    private AppForm appForm;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AppForm {
        private List<Document> documents;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Document {
            private String fileName;
            private String docType;
            private List<String> docCategory;
            private String url;
            private MetaData metaData;

            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public static class MetaData {
                private String key;
                private String value;
            }
        }
    }
}
