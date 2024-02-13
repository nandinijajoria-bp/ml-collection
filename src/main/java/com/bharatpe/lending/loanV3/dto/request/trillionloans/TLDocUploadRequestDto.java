package com.bharatpe.lending.loanV3.dto.request.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLDocUploadRequestDto {
    private String clientId;
    private String leadId;
    private String name;
    private MetaData metaData;
    private List<MetaData> metaDataList;
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MetaData {
        private String docType;
        private String docName;
        private String data;
        private String fileName;
        private String url;
    }
}
