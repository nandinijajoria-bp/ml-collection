package com.bharatpe.lending.loanV3.dto.response.oxyzo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OxyzoDisbursalCallbackResponseDTO {

    private String partnerId;
    private String organisationId;
    private String loanId;
    private String disbursalId;
    private String disbursalStatus;
    private String bankAccountNumber;
    private String disbursalLetterLink;
    private BigDecimal preEmiAmount;
    private BigDecimal processingFee;
    private BigDecimal disbursalAmount;
    private BigDecimal paymentAmount;
    private String utr;
    private Long paymentDate;
    private Long disbursementDate;
    private Long disbursalDate;
    private List<ScheduleList> schedule;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ScheduleList {

        private Number emiDate;
        private BigDecimal interest;
        private BigDecimal principal;

    }

}
