package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Date;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
@Data
@Getter
@Setter
public class ClubV2DTO {

    private boolean success;
    private String message;
    private ClubV2DTO.Data data;

    @lombok.Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @ToString
    @Builder
    @Getter
    @Setter
    public static class Data {

        Long application_id;
        String id;
        String club_name;
        Integer club_id;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        Date created_at;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        Date expired_at;
        Double annual_fee;
        List<Rewards> rewards;
        String payment_status;
        @JsonProperty
        boolean eligibile;
        String order_id;
        boolean card_eligibile;
        String status;
        String beneficiary_name;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        Date active_at;
        @JsonProperty
        boolean mdrBenefit;

    }

    @lombok.Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @ToString
    @Builder
    @Getter
    @Setter
    public static class Rewards {
        Long id;
        Double amount;
        String narration;
        String sub_narration;
        String source_module;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        Date created_at;
        Long unix_created_at;
        Integer club_id;
    }
}
