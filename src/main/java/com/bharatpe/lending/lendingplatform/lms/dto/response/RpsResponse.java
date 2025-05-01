package com.bharatpe.lending.lendingplatform.lms.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RpsResponse {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date date;

    private List<RepaymentSchedule> repaymentSchedule;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RepaymentSchedule {
        private int totalInstallment;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private Date installmentDate;

        private int installmentNumber;
        private BigDecimal openingBalance;
        private BigDecimal edi;
        private BigDecimal principal;
        private BigDecimal interest;
    }
}
