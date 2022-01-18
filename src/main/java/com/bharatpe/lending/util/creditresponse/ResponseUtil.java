package com.bharatpe.lending.util.creditresponse;

import com.bharatpe.common.entities.*;
import com.bharatpe.lending.dto.CreditScoreReportDetailDTO;
import com.bharatpe.lending.dto.LoanAndCreditCardDetailDTO;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.text.ParseException;
import java.util.*;

public interface ResponseUtil {
    public int fetchBureauVintage();

    public String fetchAccountCategory();

    public Date getReportDate();

    public String getType();

    public boolean isValid(String panCard, String phoneNumber);

    public boolean isDerog(Merchant merchant, boolean isRepeatLoanNoDerog, Experian experian) throws ParseException;

    public String getEmail();

    public int countLoanEnquiriesInLast3Months() throws ParseException;

    public int countUnsecuredLoanEnquiriesInLast6Months() throws ParseException;

    public Double getBureauScore();

    public String getResponse();

    public Map<String, Object> getBBSCalculationDetails() throws IOException, ParseException;

    public CreditScoreReportDetailDTO getCreditDetailReport(JsonNode beruaeResponse);

    public LoanAndCreditCardDetailDTO getLoanAndCreditDetail(JsonNode beruaeResponse, Merchant merchant);

    public CreditScoreReportDetailDTO.CreditCardUtilization getCreditCardUtilization(JsonNode beruaeMap);

    public CreditScoreReportDetailDTO.PaymentHistory getPaymentHistory(JsonNode beruaeMap);

    public CreditScoreReportDetailDTO.AgeOfAccount getAgeOfAccount(JsonNode beruaeMap);

    public CreditScoreReportDetailDTO.TotalAccount getTotalAccount(JsonNode beruaeMap);

    public CreditScoreReportDetailDTO.CreditEnquries getCreditEnquiries(JsonNode beruaeMap);

    public String getExperianNumber(JsonNode beruaeMap);

//    public Object getLoanAddress(JsonNode beruaeMap);
}
