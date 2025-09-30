package com.bharatpe.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AutoPayRequiredDto {

    private String message;
    private boolean upiAutoPayRequired;

}
