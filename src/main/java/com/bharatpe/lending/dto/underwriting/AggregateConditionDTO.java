package com.bharatpe.lending.dto.underwriting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AggregateConditionDTO {
    private String field;
    private String operation;
    private Object value;
    private String alias;
}
