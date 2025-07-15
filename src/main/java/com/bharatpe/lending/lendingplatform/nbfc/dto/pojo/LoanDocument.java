package com.bharatpe.lending.lendingplatform.nbfc.dto.pojo;

import com.bharatpe.lending.lendingplatform.nbfc.enums.DocType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class LoanDocument {
    private DocType type;
    private String name;
    private String url;
    private String data;
    private String additionalData;

}
