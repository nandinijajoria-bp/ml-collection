package com.bharatpe.lending.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.util.Date;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MerchantSmsAnalysis {

    private String mid;
    private String identifier;
    private Date createdAt = new Date();
    private Date updatedAt = new Date();

    public MerchantSmsAnalysis(String mid) {
        this.mid = mid;
        this.identifier = mid;
    }
}
