package com.bharatpe.lending.loanV2.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import org.springframework.web.bind.annotation.RequestParam;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BankStatementUploadRequestDto {
    String fileName;
    String password;
    String base64;
    String bankName;
    Long merchantId;
}
