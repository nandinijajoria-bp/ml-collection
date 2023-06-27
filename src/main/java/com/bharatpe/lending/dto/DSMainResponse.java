package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class DSMainResponse {
    private Profile profile;
    private Location location;
    private MerchantTag merchantTag;

    private VisionResponse visionResponse;

    @Data
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class Profile {
        private int lendingNameFlag;
    }
    @Data
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class MerchantTag {
        private String merchantVsNon;
    }

    @Data
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class Location {
        private int lendingAddressFlag;
        private float imageInferredDistance;
        private float imagePincodeDistance;
        private String inferredLat;
        private String inferredLon;
    }

    @Data
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    public static class VisionResponse {
        private Boolean retailSignal;
        private String imageBusinessName;
        private String businessNameMatch;
        private Double businessNameMatchConf;
        private MetaData meta;

        @Data
        @ToString
        @AllArgsConstructor
        @NoArgsConstructor
        @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
        public static class VisionData {
            @JsonProperty("class")
            private String classifier;
            private Double conf;
            private String modelUsed;
        }

        @Data
        @ToString
        @AllArgsConstructor
        @NoArgsConstructor
        @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
        public static class MetaData {
            private String imageBusinessNameTrans;
            private VisionData businessNameRelevance;
            private VisionData businessNameCategory;
            private VisionData imageTextCategory;
            private VisionData shopFrontExistence;
            private VisionData shopFrontStructure;
            private VisionData shopStockCategory;
            private Double businessNameMatch;
            private Double imageBusinessNameMatch;
        }

    }
}