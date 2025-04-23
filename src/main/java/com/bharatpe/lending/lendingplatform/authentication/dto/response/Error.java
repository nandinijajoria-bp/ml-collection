package com.bharatpe.lending.lendingplatform.authentication.dto.response;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Error {
    private String statusCode;
    private String message;
}
