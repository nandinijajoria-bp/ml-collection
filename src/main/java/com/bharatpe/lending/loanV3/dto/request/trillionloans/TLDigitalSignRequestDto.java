package com.bharatpe.lending.loanV3.dto.request.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.io.File;
import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class TLDigitalSignRequestDto {
    private String fileUrl;
    private String fileName;
    private List<Signer> signers;
    private Integer expireInDays;
    private String displayOnPage;
    private Boolean sendSignLink;
    private Boolean notifySigners;
    private Boolean isEstampRequired;


    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class Signer {
        private String identifier;
        private String name;
        private String signType;
        private String reason;
    }
}
