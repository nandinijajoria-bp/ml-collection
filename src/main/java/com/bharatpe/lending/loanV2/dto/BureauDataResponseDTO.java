package com.bharatpe.lending.loanV2.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BureauDataResponseDTO {
     String pancard;
     String mobile;
     String firstName;
     String lastName;
     @JsonProperty("bureau_type")
     String bureauType;
     @JsonProperty("bureau_response")
     JsonNode bureauResponse;
     @JsonProperty("created_at")
     String createdAt;
     @JsonProperty("report_date")
     String reportDate;
     @JsonProperty("updated_at")
     String updatedAt;
     @JsonProperty("hit_id_created_at")
     String hitIdCreatedAt;
     String identifier;
     @JsonProperty("hit_id")
     String hitId;
     @JsonProperty("mobile_bureau")
     String mobileBureau;
     String status;
     Integer statusCode;
     String source;
     String bureauMobile;
     ConsentDetails consentDetails;
     BureauResponseDTO.BureauVariables bureauVariables;

     @Data
     @Builder
     @NoArgsConstructor
     @AllArgsConstructor
     @JsonIgnoreProperties(ignoreUnknown = true)
     @JsonInclude(JsonInclude.Include.NON_NULL)
     public static class ConsentDetails {
          String mobileId;
          String latitude;
          String longitude;
          String ip;
          String consentDate;
          Boolean consent;
          Long consentDateEpoch;
     }
}