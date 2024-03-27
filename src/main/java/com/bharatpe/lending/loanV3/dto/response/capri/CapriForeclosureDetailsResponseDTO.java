package com.bharatpe.lending.loanV3.dto.response.capri;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CapriForeclosureDetailsResponseDTO {
     Type type;
     List<Integer> date;
     List<Integer> valueDate;
     Currency currency;
     Integer amount;
     Double netForeclosureAmount;
     Integer principalPortion;
     Integer interestPortion;
     Integer feeChargesPortion;
     Integer penaltyChargesPortion;
     Integer outstandingLoanBalance;
     Boolean manuallyReversed;
     Boolean isRepaymentAtDisbursement;
     List<PaymentTypeOption> paymentTypeOptions;
     List<PaymentModeOption> paymentModeOptions;
     List<PreclosureReasonOption> preclosureReasonOptions;
     Boolean isGlimLoan;
     Integer excessAmountPaymentPortion;
     Boolean isAllowCompoundingOnEod;
     Object templateAdditionalDetails;
     Integer rebateAmount;
     List<Object> foreclosureChargesDetails;
     List<Object> penaltyChargesDetails;
     List<Object> feeChargesDetails;
     List<ChargeDiscountType> chargeDiscountTypes;
     Integer foreClosureChargesPortion;
     List<Object> loanChargeTaxDetails;
     LoanForeclosureAmountComponents loanForeclosureAmountComponents;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApplicableOn{
         Integer id;
         String code;
         String value;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChargeDiscountType{
         Integer id;
         String code;
         String value;
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Currency{
         String code;
         String name;
         Integer decimalPlaces;
         Integer inMultiplesOf;
         String displaySymbol;
         String nameCode;
         String displayLabel;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DefaultValueDateType{
         Integer id;
         String code;
         String value;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoanForeclosureAmountComponents{
         Double principal;
         Double principalOverdue;
         Double interestOverdue;
         Double brokenPeriodInterest;
         Integer interestDue;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PaymentMode{
         Integer id;
         String code;
         String value;
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PaymentModeOption{
         Integer id;
         String code;
         String value;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PaymentTypeOption{
         Integer id;
         String name;
         String description;
         Boolean isCashPayment;
         Integer position;
         String systemCode;
         PaymentMode paymentMode;
         ApplicableOn applicableOn;
         ServiceProvider serviceProvider;
         DefaultValueDateType defaultValueDateType;
         Integer externalServiceId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PreclosureReasonOption{
         Integer id;
         String name;
         Integer position;
         String description;
         Boolean isActive;
         Integer codeScore;
         Boolean mandatory;
         String systemIdentifier;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ServiceProvider{
         Integer id;
         Boolean isActive;
         Boolean mandatory;
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Type{
         Integer id;
         String code;
         String value;
         Boolean disbursement;
         Boolean repaymentAtDisbursement;
         Boolean repayment;
         Boolean contra;
         Boolean waiveInterest;
         Boolean waiveCharges;
         Boolean accrual;
         Boolean accrualReverse;
         Boolean writeOff;
         Boolean recoveryRepayment;
         Boolean initiateTransfer;
         Boolean approveTransfer;
         Boolean withdrawTransfer;
         Boolean rejectTransfer;
         Boolean chargePayment;
         Boolean refund;
         Boolean refundForActiveLoans;
         Boolean addSubsidy;
         Boolean revokeSubsidy;
         Boolean brokenPeriodInterestPosting;
         Boolean accrualSuspense;
         Boolean accrualWrittenOff;
         Boolean accrualSuspenseReverse;
         Boolean accrualIRDPosting;
         Boolean prudentialWriteoff;
         Boolean incomePosting;
         Boolean upfrontInterestCollection;
         Boolean isAdditionalInterestPosting;
         Boolean isLoanReversalAmount;
         Boolean cashbasedAccrualCharge;
         Boolean cashbasedAccrualRealization;
         Boolean cashbasedAccrualWriteoff;
         Boolean cashbasedAccrualRealizationReverse;
         Boolean cashbasedAccrualWriteoffReverse;
         Boolean predisbursementChargePayment;
         Boolean deductionFromDisbursement;
    }

}
