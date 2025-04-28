package com.bharatpe.lending.lendingplatform.nbfc.dto.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SelfieDetails {
    private String imageBase64;
}
