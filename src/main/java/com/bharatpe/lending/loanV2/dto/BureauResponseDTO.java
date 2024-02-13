package com.bharatpe.lending.loanV2.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BureauResponseDTO {
    private String mobile;
    private String pancard;
    private JsonNode bureauData;
    private JsonNode bureauTradeLineData;
    private Boolean isNTC;
    private BureauVariables variables;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BureauVariables {
        private Double bureauScore;
        private String email;
        private List<String> address;
        private String fatherName;
        private Double bbs;
        private Double nfi;
        private Integer bureauVintage;
        private Long reportDate;
        private Integer creditCardCount;
        private Integer ccVintage;
        private Integer twlCount;
        private Integer glCount;
        private Integer cdlCount;
        private Integer plCount;
        private Integer hlCount;
        private Integer alCount;
        private Integer blCount;
        private Integer olCount;
        private Integer excludedLoanCount;
        private Integer totalLoanCount;
        private Boolean thinLine;
        private Integer age;
        private String dob;
        private Integer maxDpd3Months;
        private Integer maxDpd6Months;
        private Integer maxDpd12Months;
        private Integer maxDpd18Months;
        private Integer maxDpd24Months;
        private Boolean writtenOffLast12Months;
        private Integer maxOverdueAmount6Months;
        private Integer unsecuredEnquiries3Months;
        private Integer securedEnquiries3Months;
        private Integer unsecuredEnquiries6Months;
        private Double maxHLAmount;
        private Double maxALAmount;
        private Double maxPLAmount;
        private Double maxBLAmount;
        private Double maxOLAmount;
        private Double maxCCLimit;
        private Double maxCCBalance;
        private Double minCCLimit;
        private Double totalHLAmount;
        private Double totalALAmount;
        private Double totalPLAmount;
        private Double totalBLAmount;
        private Double totalOLAmount;
        private Double totalCCLimit;
        private Double unsecuredLoanUtilization;
        private Integer totalEnquiryLast12Month;
        private Double nonCreditCardOverduePast3Month;
        private Double nonCreditCardOverduePast6Month;
        private Double nonCreditCardOverduePast9Month;
        private Double nonCreditCardOverduePast12Month;
        private Double creditCardOverDuePast3Month;
        private Double creditCardOverDuePast6Month;
        private Double creditCardOverDuePast9Month;
        private Double creditCardOverDuePast12Month;
        private Double settleLoanPast12Month;
        private Double settleLoanPast24Month;
        private Integer maxCreditCardTradeMoreThan60;
        private Integer maxNonCreditCardTradeMoreThan60;
        private Double closedLoanWithOverDueLast12Month;
        private Integer activeGoldLoanGreaterThan20K;
        private Integer activePersonalLoanGreaterThan10k;
        private Integer tradeDpd15to29Last3month;
        private Integer tradeDpd30to59Last6month;
        private Integer tradeDpd60to89Last12month;
        private Integer tradeDpd30to59MoreThan6Month;
        private Integer tradeDpd60to89MoreThan12Month;
        private Integer subDpdGreaterThan90Last18Month;
        private Integer subDpdGreaterThan90MoreThan18Month;
        private Integer loanEnquires3mon;
        private Integer loanEnquires3monScore;
        private Integer delinquencyCount6mon;
        private Integer delinquencyCount6monScore;
        private Integer loanSanctioned3mon;
        private Integer loanSanctioned3monScore;
        private Integer loanTypeSize;
        private Integer loanTypeSizeScore;
        private Double unsecuredLoanRatio6mon;
        private Integer unsecuredLoanRatio6monScore;
        private Integer creditHistory;
        private Integer creditHistoryScore;
        private Double debt;
        private Double income;
        private Integer tradeDpdGreaterThan90Last24Month;
        private Boolean loanSettlement;
        private Double debtActiveLoan;
        private CreditScoreReportDetailDTO creditScoreReportDetailDTO;
        private LoanAndCreditCardDetailDTO loanAndCreditCardDetailDTO;
    }
}
