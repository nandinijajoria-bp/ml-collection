package com.bharatpe.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ComparisonResult<T> {
    private boolean failed;
    private T actual;
    private T expected;
}

