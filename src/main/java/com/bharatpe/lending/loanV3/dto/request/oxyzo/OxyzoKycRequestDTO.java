package com.bharatpe.lending.loanV3.dto.request.oxyzo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OxyzoKycRequestDTO {
    private String organisationId;
    private String profilePicture;
    private String aadhaarXml;
}
