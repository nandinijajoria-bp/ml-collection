package com.bharatpe.lending.lendingplatform.authentication.dto.request;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class TokenRequest {
    private String name;
    private String password;
}

