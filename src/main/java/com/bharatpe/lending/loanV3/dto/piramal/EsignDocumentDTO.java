package com.bharatpe.lending.loanV3.dto.piramal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EsignDocumentDTO {
    private String leadId;
    private String fileBlob;
    private String lanNo;
    private String fileFormat;
    private String documentType;
}
