package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MerchantConfidenceScoreDTO {
    @JsonProperty("status")
    String status;
    String message;
    Integer totalContacts;
    @JsonProperty("data")
    Data data;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("output")
        List<Contact> output;

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        @ToString
        @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Contact {
            @JsonProperty("phoneNumber")
            private String phoneNumber;
            private String name;
            private String inferredRelation;
            private String inferredRelationToken;
            private String priority;
            private Double fuzzyScore;
            private String inferredName;
            private Double inferredNameConfidence;
            private Integer numHits;
            private Boolean fraudFlag;
            private Boolean inBpUniverse;
            private Integer score;
            private ScoreComponent scoreComponents;

            @Getter
            @Setter
            @NoArgsConstructor
            @AllArgsConstructor
            @ToString
            @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class ScoreComponent {
                private Integer priorityScore;
                private Integer bpuScore;
                private Integer hitsScore;
            }
        }
    }
}
