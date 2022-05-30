package com.bharatpe.lending.loanV2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
public class LoanAndCreditCardDetailDTO {

    @JsonProperty("credit_card")
    private List<CreditCardDetail> creditCardDetail;

    @JsonProperty("loan_detail")
    private List<LoanDetail> loanDetail;

    @JsonProperty("experian_number")
    private String experianNumber;

    public List<LoanDetail> getLoanDetail() {
        return loanDetail;
    }

    @Data
    @Builder
    public static class LoanDetail {
        private String bankName;
        private boolean status;
        private String accountNumber;
        private String currentBalance;
        private String tenure;
        private String rateOfInterest;
        private Integer sanctionedAmount;
    }
    @Data
    @Builder
    public static class CreditCardDetail {
        private String bankName;
        private boolean status;
        private String creditCardNumber;
        private Integer cardLimit;
        private Integer balance;
    }
}
