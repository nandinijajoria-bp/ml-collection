package com.bharatpe.lending.util.creditresponse;

import com.bharatpe.common.entities.*;

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

}
