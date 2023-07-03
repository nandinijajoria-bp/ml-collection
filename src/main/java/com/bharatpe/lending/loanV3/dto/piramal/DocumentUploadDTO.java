package com.bharatpe.lending.loanV3.dto.piramal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class DocumentUploadDTO {
    String leadId;
    Integer version;
    String uploadDate;
    List<DocumentList> documentList;
    GeoLocation geoLocation;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Builder
    public static class DocumentList {
        String type;
        String documentCategory;
        String documentSubCategory;
        String fileName;
        String uploadDate;
        MetaData metadata;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Builder
    public static class MetaData {
        String type;
        String url;
        String data;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Builder
    public static class GeoLocation {
        String latitude;
        String longitude;
    }
}
