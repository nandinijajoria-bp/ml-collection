package com.bharatpe.lending.dto.underwriting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchCriteriaDTO {
    private String field;
    private String operation;
    private Object value;
}