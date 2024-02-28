package com.bharatpe.lending.loanV3.dto.response.trillionloans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TLDocUploadResponseDto {
    private Long resourceId;
    private String resourceIdentifier;
    private Long imageId;
}
