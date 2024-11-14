package com.bharatpe.lending.constant;

import com.bharatpe.lending.loanV2.dto.HomepageCardsDetailsDTO;

public class HomepageCardsConstants {
    public static final String DEEPLINK = "{{deeplink}}";

    public static class CARD_LOAN_ELIGIBLE {
        public static final String CARDTYPE = "EXCLUSIVE FOR YOU";
        public static final String STATUSTEXT = "Loan offer for you";
        public static final String ACTIONTEXT = "Eligible to apply for a loan";
        public static final String CARDSTATE = "IN_PROGRESS";
        public static final String JOURNEYTEXT = "Apply for loan";
        public static final String LOANAMOUNT = "Upto ₹{{loanAmount}}";
    }

    public static class CARD_RTE_ELIGIBLE {
        public static final String CARDTYPE = "JOIN ELIGIBILITY PROGRAM";
        public static final String STATUSTEXT = "Get loan in {{targetDurationDays}} days";
        public static final String ACTIONTEXT = "Achieve targets to get loan";
        public static final String CARDSTATE = "IN_PROGRESS";
        public static final String JOURNEYTEXT = "ENROLL NOW";
    }

    public static class NO_BANNER {
        public static final String CARDTYPE = "";
        public static final String STATUSTEXT = "";
        public static final String ACTIONTEXT = "";
        public static final String CARDSTATE = "";
        public static final String JOURNEYTEXT = "";
    }

    public static HomepageCardsDetailsDTO noBanner(HomepageCardsDetailsDTO data){
        data.setCardType("");
        data.setStatusText("");
        data.setActionText("");
        data.setCardState("");
        data.setJourneyText("");
        return data;
    }
    //case:1
    public static HomepageCardsDetailsDTO cardLoanEligible(HomepageCardsDetailsDTO data, Double loanAmount){
        data.setCardType("EXCLUSIVE FOR YOU");
        data.setStatusText("Upto ₹"+ String.format("%.0f", loanAmount));
        data.setActionText("Loan offer for you");
        data.setCardState("TO_BE_STARTED");
        data.setJourneyText("Apply for loan");
        data.setLoanAmount("");
        return data;
    }
    //case:2
    public static HomepageCardsDetailsDTO cardRTEEligible(HomepageCardsDetailsDTO data, String targetDurationDays){
        data.setCardType("JOIN PROGRAM");
        data.setStatusText("Get loan in "+targetDurationDays+" days");
        data.setActionText("Achieve targets to get loan");
        data.setCardState("TO_BE_STARTED");
        data.setJourneyText("ENROLL NOW");
        return data;
    }

    //case:3
    public static HomepageCardsDetailsDTO cardLoanEligibilityNotChecked(HomepageCardsDetailsDTO data){
        data.setCardType("EXCLUSIVE FOR YOU");
        data.setStatusText("Upto ₹10,00,000");
        data.setActionText("Enjoy easy access to loans");
        data.setCardState("TO_BE_STARTED");
        data.setJourneyText("Get loan");
        data.setLoanAmount("");
        return data;
    }

    //case:6
    public static HomepageCardsDetailsDTO cardRTEEnrolled(HomepageCardsDetailsDTO data, double mileStoneCompletePercent){
        data.setCardType("PROGRAM ENROLLED");
        data.setStatusText("Achieve targets to avail loan");
        data.setActionText("Just a few steps to get loan!");
        data.setCardState("TO_BE_STARTED");
        data.setJourneyText("Track your progress");
        data.setLoanAmount(String.format("%.0f%%", Math.ceil(mileStoneCompletePercent)));
        return data;
    }

    //case:7
    public static HomepageCardsDetailsDTO cardApplicationCreatedButNotCompleted(HomepageCardsDetailsDTO data, Double loanAmount){
        data.setCardType("Status");
        data.setStatusText("Resume application");
        data.setActionText("Few steps to get loan!");
        data.setCardState("IN_PROGRESS");
        data.setJourneyText("Get loan");
        data.setLoanAmount("₹"+ String.format("%.0f", loanAmount));
        return data;
    }

    //case:8
    public static HomepageCardsDetailsDTO cardAgreementNotSigned(HomepageCardsDetailsDTO data, Double loanAmount){
        data.setCardType("STATUS");
        data.setStatusText("Sign loan agreement");
        data.setActionText("Few steps to get loan!");
        data.setCardState("IN_PROGRESS");
        data.setJourneyText("Loan applied for");
        data.setLoanAmount("₹"+ String.format("%.0f", loanAmount));
        return data;
    }

    //case:9
    public static HomepageCardsDetailsDTO cardAgreementSignedButNachNotSet(HomepageCardsDetailsDTO data, Double loanAmount){
        data.setCardType("STATUS");
        data.setStatusText("Complete loan application");
        data.setActionText("One step away");
        data.setCardState("IN_PROGRESS");
        data.setJourneyText("Loan applied for");
        data.setLoanAmount("₹"+ String.format("%.0f", loanAmount));
        return data;
    }

    //case:10
    public static HomepageCardsDetailsDTO cardVerificationInProgress(HomepageCardsDetailsDTO data, Double loanAmount){
        data.setCardType("STATUS");
        data.setStatusText("In Review");
        data.setActionText("Application review in progress");
        data.setCardState("IN_PROGRESS");
        data.setJourneyText("Loan applied for");
        data.setLoanAmount("₹"+ String.format("%.0f", loanAmount));
        return data;
    }

    //case:11
    public static HomepageCardsDetailsDTO cardReuploadDocuments(HomepageCardsDetailsDTO data, Double loanAmount){
        data.setCardType("STATUS");
        data.setStatusText("Re-submit");
        data.setActionText("Please resubmit documents");
        data.setCardState("ACTION_REQUIRED");
        data.setJourneyText("Loan applied for");
        data.setLoanAmount("₹"+ String.format("%.0f", loanAmount));
        return data;
    }

    //case:12
    public static HomepageCardsDetailsDTO cardDisbursalPending(HomepageCardsDetailsDTO data, Double loanAmount){
        data.setCardType("STATUS");
        data.setStatusText("Disbursal under process");
        data.setActionText("Would take 3-5 days");
        data.setCardState("COMPLETED");
        data.setJourneyText("Loan approved");
        data.setLoanAmount("₹"+ String.format("%.0f", loanAmount));
        return data;
    }

    //case:13
    public static HomepageCardsDetailsDTO cardAmountOverdueDPD(HomepageCardsDetailsDTO data, Double loanAmount, Double ediAmount){
        data.setCardType("EDI");
        data.setStatusText("Due Amount");
        data.setEdiAmount(String.format("%.0f", ediAmount));
        data.setActionText("EDI is overdue, pay now");
        data.setCardState("ACTION_REQUIRED");
        data.setJourneyText("Loan amount");
        data.setLoanAmount("₹"+ String.format("%.0f", loanAmount));
        return data;
    }

    //case:15
    public static HomepageCardsDetailsDTO cardEDIAutoDebited(HomepageCardsDetailsDTO data, Double loanAmount, Double ediAmount){
        data.setCardType("EDI");
        data.setStatusText("Due Amount");
        data.setEdiAmount(String.format("%.0f", ediAmount)); //set edi amount
        data.setActionText("EDI will be auto debited");
        data.setCardState("IN_PROGRESS");
        data.setJourneyText("Loan amount");
        data.setLoanAmount("₹"+ String.format("%.0f", loanAmount));
        return data;
    }

    //case:16
    public static HomepageCardsDetailsDTO cardTopUpLoanOfferAmount(HomepageCardsDetailsDTO data, Double loanAmount){
        data.setCardType("OFFER FOR YOU");
        data.setStatusText("Top up loan");
        data.setActionText("You are eligible for top up");
        data.setCardState("To be started");
        data.setJourneyText("Apply for top up loan");
        data.setLoanAmount("Upto ₹"+ String.format("%.0f", loanAmount));
        return data;
    }

}