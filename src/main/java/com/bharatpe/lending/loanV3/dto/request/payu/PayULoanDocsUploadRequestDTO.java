package com.bharatpe.lending.loanV3.dto.request.payu;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayULoanDocsUploadRequestDTO {

    @JsonProperty("application-id")
    private String applicationId;

    @JsonProperty("document_list")
    private List<DocumentList> documentList;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DocumentList {

        @JsonProperty("document_type")
        private String documentType;

        @JsonProperty("file_url")
        private String fileUrl;
    }

}
