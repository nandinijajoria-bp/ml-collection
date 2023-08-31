package com.bharatpe.lending.loanV2.dto;

import com.bharatpe.lending.common.enums.BankStatementSessionStatus;
import com.bharatpe.lending.common.enums.Gst3bSessionStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;


import java.util.Date;

@Data
@NoArgsConstructor
@SuperBuilder
@ToString
public class UnderwritingDocEligibilityDTO {
    private Date activityStartTime;
    private String activityStatus;
    private String activityFailedError;
    private String activityType;
    private GST3b gst3b;
    private BankStatement bankStatement;

    @Data
    public static class GST3b {
        private Boolean active = Boolean.FALSE;
        private Gst3bSessionStatus status;
    }

    @Data
    public static class BankStatement {
        private Boolean active = Boolean.FALSE;
        private BankStatementSessionStatus status;
        private Boolean accountAggregatorActive = Boolean.FALSE;
        private Boolean uploadActive = Boolean.FALSE;
    }
}