package com.bharatpe.lending.loanV3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.Date;
import java.util.List;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class DocUploadApiRequestDto {
    String lender;
    Long applicationId;
    Boolean isTopup;
    Payload payload;
    String productName;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payload {
        String cccId;
        String fileUpload;
        String fileName;
        String accountId;
        String docType;
        String customerId;
        String requestId;
    }
}
