package com.bharatpe.lending.lendingplatform.underwriting.dto.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiError {
    private String statusCode;
    private String message;
}
