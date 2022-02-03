package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class DsImageValidationRequestDto {
    @JsonProperty(value = "s3_url")
    String s3Url;
    File file;
    Boolean verifyGoogle;
    Boolean extractCategory;
    Boolean extractStructure;
    Boolean extractExistence;

    public DsImageValidationRequestDto(String s3Url, Boolean verifyGoogle, Boolean extractCategory, Boolean extractStructure, Boolean extractExistence) {
        this.s3Url = s3Url;
        this.verifyGoogle = verifyGoogle;
        this.extractCategory = extractCategory;
        this.extractStructure = extractStructure;
        this.extractExistence = extractExistence;
    }
}
