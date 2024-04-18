package com.bharatpe.lending.loanV3.dto.response.capri;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CapriDocUploadResponseDTO {
    String status;
    String source;
    Long resourceId;
    String resourceIdentifier;
    Long imageId;
}
