package com.bharatpe.lending.loanV3.revamp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class EnachStatusUpdateRequestDto {
    private Long applicationId;
    private String mandateId;
    private String status;
    private String rejectReason;
    private String rejectCode;
    private String customerVpa;
    private String umrn;

    public boolean isValid() {
        return applicationId != null && mandateId != null && status != null;
    }
}
