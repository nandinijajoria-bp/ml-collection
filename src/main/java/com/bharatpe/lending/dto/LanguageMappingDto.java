package com.bharatpe.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LanguageMappingDto {
    private String lender;
    private String languageLabel;
    private String languageValue;
    private String vernacCode;
    private String docType;
}
