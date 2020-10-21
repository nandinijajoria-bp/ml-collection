package com.bharatpe.lending.util.creditresponse;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import org.joda.time.DateTime;
import org.joda.time.Months;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ExperianResponseUtil extends ResponseUtilBase implements ResponseUtil {

    List<Integer> derogUnsecuredProducts = Arrays.asList(5, 10, 36, 37, 38, 39, 43, 51, 52, 53, 54, 55, 56, 57, 58, 60,
            61);
    List<Integer> derogAccountStatus = Arrays.asList(93, 89, 93, 97, 97, 97, 97, 30, 31, 32, 33, 35, 37, 38, 39, 41, 42,
            43, 44, 45, 47, 49, 50, 51, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 72, 73,
            74, 75, 76, 77, 79, 81, 85, 86, 87, 88, 94, 90, 91);

    List<Integer> activeStatusList = Arrays.asList(11, 21, 22, 23, 24, 25, 71, 78, 80, 82, 83, 84);

    List<Integer> unsecuredLoan = Arrays.asList(0, 5, 6, 8, 9, 10, 11, 12, 14, 16, 18, 19, 20, 31, 35, 36, 37, 38, 39,
            43, 51, 52, 53, 54, 55, 56, 57, 58, 61);
    ExperianDao experianDao;

    SimpleDateFormat dateFormat = new SimpleDateFormat(ExperianConstants.DATE_FORMAT);

    public ExperianResponseUtil(ExperianDao experianDao) {
        this.type = "EXPERIAN";
        this.experianDao = experianDao;
    }

    public ExperianResponseUtil(JsonNode response, ExperianDao experianDao) {
        this.type = "EXPERIAN";
        this.response = response;
        this.experianDao = experianDao;
    }

    @Override
    public String getType() {
        return this.type;
    }

    @Override
    public String getResponse() {
        return this.response.toString();
    }

    @Override
    public boolean isValid(String panCard, String phoneNumber) {
        return this.response != null;
    }

    @Override
    public Date getReportDate() {
        try {
            return dateFormat
                    .parse(response.get("INProfileResponse").get("CreditProfileHeader").get("ReportDate").asText());
        } catch (ParseException e) {
            logger.info("Exception in parsing report date", e);
            return null;
        }
    }

    @Override
    public String getEmail() {
        String email = null;
        JsonNode currentApplicationDetails = response.get(ExperianConstants.PROFILE_RESPONSE).get("Current_Application")
                .get("Current_Application_Details");
        if (currentApplicationDetails != null && currentApplicationDetails.get("Current_Applicant_Details") != null) {
            email = currentApplicationDetails.get("Current_Applicant_Details").get("EMailId").asText();
        }
        return email;
    }

    @Override
    public Double getBureauScore() {
        JsonNode bureauScore = response.get(ExperianConstants.PROFILE_RESPONSE).get("SCORE").get("BureauScore");
        return bureauScore != null ? bureauScore.doubleValue() : null;
    }

    @Override
    public int fetchBureauVintage() {
        DateTimeFormatter formatter = DateTimeFormat.forPattern(ExperianConstants.DATE_FORMAT);
        DateTime min = new DateTime();
        JsonNode accountDetails = response.get(ExperianConstants.PROFILE_RESPONSE).get(ExperianConstants.ACCT)
                .get(ExperianConstants.ACCT_DETAILS);
        if (accountDetails != null && accountDetails.isArray()) {
            for (JsonNode jsonNode : accountDetails) {
                try {
                    min = formatter.parseDateTime(jsonNode.get(ExperianConstants.OPEN_DATE).toString()).isBefore(min)
                            ? formatter.parseDateTime(jsonNode.get(ExperianConstants.OPEN_DATE).toString())
                            : min;
                } catch (Exception e) {
                    logger.info("Invalid Open_Date");
                }
            }
            return Months.monthsBetween(min, DateTime.now()).getMonths();
        } else if (accountDetails != null && accountDetails.isObject()) {
            JsonNode jsonNode = accountDetails;
            try {
                min = formatter.parseDateTime(jsonNode.get(ExperianConstants.OPEN_DATE).toString()).isBefore(min)
                        ? formatter.parseDateTime(jsonNode.get(ExperianConstants.OPEN_DATE).toString())
                        : min;
            } catch (Exception e) {
                logger.info("Invalid Open_Date");
            }
            return Months.monthsBetween(min, DateTime.now()).getMonths();
        }
        return 0;
    }

    public String fetchAccountCategory() {
        List<Integer> categoryA = Arrays.asList(6, 7, 13, 38, 39, 43);
        List<Integer> categoryB = Arrays.asList(1, 5, 8, 9, 10, 11, 12, 17, 32, 33, 34, 36, 37, 51, 52, 53, 54, 55, 56,
                57, 58, 59, 60, 61);
        List<Integer> categoryC = Arrays.asList(2, 3);
        boolean a = false, b = false, c = false;
        JsonNode accountDetails = response.get(ExperianConstants.PROFILE_RESPONSE).get(ExperianConstants.ACCT)
                .get(ExperianConstants.ACCT_DETAILS);
        if (accountDetails != null && accountDetails.isArray()) {
            for (JsonNode jsonNode : accountDetails) {
                if (categoryA.contains(jsonNode.get(ExperianConstants.ACCT_TYPE).asInt())) {
                    a = true;
                }
                if (categoryB.contains(jsonNode.get(ExperianConstants.ACCT_TYPE).asInt())) {
                    b = true;
                }
                if (categoryC.contains(jsonNode.get(ExperianConstants.ACCT_TYPE).asInt())) {
                    c = true;
                }
            }
        } else if (accountDetails != null && accountDetails.isObject()) {
            JsonNode jsonNode = accountDetails;
            if (categoryA.contains(jsonNode.get(ExperianConstants.ACCT_TYPE).asInt())) {
                a = true;
            }
            if (categoryB.contains(jsonNode.get(ExperianConstants.ACCT_TYPE).asInt())) {
                b = true;
            }
            if (categoryC.contains(jsonNode.get(ExperianConstants.ACCT_TYPE).asInt())) {
                c = true;
            }
        }
        return c ? "C" : b ? "B" : a ? "A" : "NTC";
    }

    @Override
    public boolean isDerog(Merchant merchant, boolean isRepeatLoanNoDerog, Experian experian) throws ParseException {
        Date reportDate = dateFormat.parse(
                response.get(ExperianConstants.PROFILE_RESPONSE).get("CreditProfileHeader").get("ReportDate").asText());
        if (response.get(ExperianConstants.PROFILE_RESPONSE).get(ExperianConstants.ACCT)
                .get(ExperianConstants.ACCT_DETAILS) != null
                && response.get(ExperianConstants.PROFILE_RESPONSE).get(ExperianConstants.ACCT)
                        .get(ExperianConstants.ACCT_DETAILS).isObject()) {
            JsonNode caisAccountDetails = response.get(ExperianConstants.PROFILE_RESPONSE).get(ExperianConstants.ACCT)
                    .get(ExperianConstants.ACCT_DETAILS);
            if (derogChecks(caisAccountDetails, merchant.getId(), isRepeatLoanNoDerog, reportDate, experian)) {
                logger.info("Derog check failed, rejecting merchant: {}", merchant.getId());
                return true;
            }
        } else if (response.get(ExperianConstants.PROFILE_RESPONSE).get(ExperianConstants.ACCT)
                .get(ExperianConstants.ACCT_DETAILS) != null
                && response.get(ExperianConstants.PROFILE_RESPONSE).get(ExperianConstants.ACCT)
                        .get(ExperianConstants.ACCT_DETAILS).isArray()) {
            int unsecuredLoanCount = 0;
            for (JsonNode caisAccountDetails : response.get(ExperianConstants.PROFILE_RESPONSE)
                    .get(ExperianConstants.ACCT).get(ExperianConstants.ACCT_DETAILS)) {
                if (derogChecks(caisAccountDetails, merchant.getId(), isRepeatLoanNoDerog, reportDate, experian)) {
                    logger.info("Derog check failed, rejecting merchant: {}", merchant.getId());
                    return true;
                }
                if (checkUnsecuredLiveLoans(caisAccountDetails)) {
                    unsecuredLoanCount++;
                }
            }
            // Not more than 3 live unsecured loans running
            if (!isRepeatLoanNoDerog && unsecuredLoanCount > 3) {
                logger.info("Derog more than 3 live unsecured loans running, rejecting merchant: {}", merchant.getId());
                experian.setRejected(true);
                experian.setRejectedDate(new Date());
                experian.setReason(ExperianConstants.DEROG_UNSECURED_LOANS);
                experianDao.save(experian);
                return true;
            }
        }
        // Not more than 4 unsecured loan enquiries in the last 6 months --- Derog check
        if (!isRepeatLoanNoDerog && countUnsecuredLoanEnquiriesInLast6Months() == 1) {
            logger.info("Derog more than 4 unsecured loan enquiries in the last 6 months, rejecting merchant: {}",
                    merchant.getId());
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(ExperianConstants.DEROG_UNSECURED_LOAN_ENQUIRY);
            experianDao.save(experian);
            return true;
        }
        // Not more than 6 enquiries in the last 3 months ( across all product types)
        // --- Derog check
        if (!isRepeatLoanNoDerog && countLoanEnquiriesInLast3Months() > 6) {
            logger.info("Derog more than 6 enquiries in the last 3 months, rejecting merchant: {}", merchant.getId());
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(ExperianConstants.DEROG_MORE_THAN_6_LOAN_ENQUIRY);
            experianDao.save(experian);
            return true;
        }
        return false;
    }

    private boolean derogChecks(JsonNode jsonNode, Long merchantId, boolean isRepeatLoanNoDerog, Date reportDate,
            Experian experian) {
        // Check for Derog Account Status
        if (jsonNode.get(ExperianConstants.ACCT_STATUS) != null
                && derogAccountStatus.contains(jsonNode.get(ExperianConstants.ACCT_STATUS).asInt())) {
            logger.info("Derog Account Status check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(ExperianConstants.DEROG_ACCOUNT_STATUS);
            experianDao.save(experian);
            return true;
        }
        // Check for Derog DPD Last 3 months
        if (!isRepeatLoanNoDerog && jsonNode.get(ExperianConstants.ACCT_HOLDER_TYPE_CODE).asInt() != 7
                && checkDPDLastXmonths(jsonNode, 3, reportDate)) {
            logger.info("Derog DPD Last 3 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(ExperianConstants.DEROG_DPD_LAST_3_MONTHS);
            experianDao.save(experian);
            return true;
        }
        // Check for Derog DPD Last 6 months
        if (jsonNode.get(ExperianConstants.ACCT_HOLDER_TYPE_CODE).asInt() != 7
                && checkDPDLastXmonths(jsonNode, 6, reportDate)) {
            logger.info("Derog DPD Last 6 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(ExperianConstants.DEROG_DPD_LAST_6_MONTHS);
            experianDao.save(experian);
            return true;
        }
        // Check for Derog DPD Last 12 months
        if (!isRepeatLoanNoDerog && jsonNode.get(ExperianConstants.ACCT_HOLDER_TYPE_CODE).asInt() != 7
                && checkDPDLastXmonths(jsonNode, 12, reportDate)) {
            logger.info("Derog DPD Last 12 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(ExperianConstants.DEROG_DPD_LAST_12_MONTHS);
            experianDao.save(experian);
            return true;
        }
        // Check for Derog DPD Last 24 months
        if (!isRepeatLoanNoDerog && jsonNode.get(ExperianConstants.ACCT_HOLDER_TYPE_CODE).asInt() != 7
                && checkDPDLastXmonths(jsonNode, 24, reportDate)) {
            logger.info("Derog DPD Last 24 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(ExperianConstants.DEROG_DPD_LAST_24_MONTHS);
            experianDao.save(experian);
            return true;
        }
        return false;
    }

    private boolean checkDPDLastXmonths(JsonNode jsonNode, int months, Date reportDate) {
        Date dateReported = null;
        try {
            if (jsonNode.get(ExperianConstants.DATE_REPORTED) != null
                    && !jsonNode.get(ExperianConstants.DATE_REPORTED).asText().equalsIgnoreCase("")) {
                dateReported = dateFormat.parse(jsonNode.get(ExperianConstants.DATE_REPORTED).asText());
            }
        } catch (Exception e) {
            logger.error("Exception:", e);
        }
        List<String> monthYear = new ArrayList<>();
        Calendar c = Calendar.getInstance();
        if (dateReported != null && LoanUtil.getDateDiffInDays(dateReported, reportDate) > months * 30) {
            return false;
        }
        if (dateReported != null && LoanUtil.getDateDiffInDays(dateReported, reportDate) <= months * 30) {
            c.setTime(dateReported);
        } else {
            c.setTime(reportDate);
        }
        String month;
        int dpd = 5;// 3 months
        switch (months) {
            case 6:
                dpd = 30;
                break;
            case 12:
                dpd = 60;
                break;
            case 24:
                dpd = 90;
                break;
            default:
                break;
        }
        for (int i = 0; i < months; i++) {
            month = (c.get(Calendar.MONTH) + 1) < 10 ? "0" + (c.get(Calendar.MONTH) + 1)
                    : (c.get(Calendar.MONTH) + 1) + "";
            monthYear.add(month + "$" + c.get(Calendar.YEAR));// 01$2020
            c.add(Calendar.MONTH, -1);
        }
        if (jsonNode.get(ExperianConstants.ACCT_HISTORY) != null
                && jsonNode.get(ExperianConstants.ACCT_HISTORY).isArray()) {
            for (JsonNode history : jsonNode.get(ExperianConstants.ACCT_HISTORY)) {
                if (monthYear.contains(history.get("Month").asText() + "$" + history.get("Year").asText())
                        && !history.get(ExperianConstants.DPD).isNull()
                        && !history.get(ExperianConstants.DPD).asText().equalsIgnoreCase("")
                        && history.get(ExperianConstants.DPD).asInt() >= dpd) {
                    return true;
                }
            }
        } else if (jsonNode.get(ExperianConstants.ACCT_HISTORY) != null
                && jsonNode.get(ExperianConstants.ACCT_HISTORY).isObject()) {
            JsonNode history = jsonNode.get(ExperianConstants.ACCT_HISTORY);
            return monthYear.contains(history.get("Month").asText() + "$" + history.get("Year").asText())
                    && !history.get(ExperianConstants.DPD).isNull()
                    && !history.get(ExperianConstants.DPD).asText().equalsIgnoreCase("")
                    && history.get(ExperianConstants.DPD).asInt() >= dpd;
        }
        return false;
    }

    private int countDPDLastXmonths(JsonNode jsonNode, int months, Date reportDate) {
        Date dateReported = null;
        try {
            if (jsonNode.get(ExperianConstants.DATE_REPORTED) != null
                    && !jsonNode.get(ExperianConstants.DATE_REPORTED).asText().equalsIgnoreCase("")) {
                dateReported = dateFormat.parse(jsonNode.get(ExperianConstants.DATE_REPORTED).asText());
            }
        } catch (Exception e) {
            logger.error("Exception:", e);
        }
        List<String> monthYear = new ArrayList<>();
        Calendar c = Calendar.getInstance();
        if (dateReported != null && LoanUtil.getDateDiffInDays(dateReported, reportDate) > months * 30) {
            return 0;
        }
        if (dateReported != null && LoanUtil.getDateDiffInDays(dateReported, reportDate) <= months * 30) {
            c.setTime(dateReported);
        } else {
            c.setTime(reportDate);
        }
        String month;
        int dpd = 0;
        for (int i = 0; i < months; i++) {
            month = (c.get(Calendar.MONTH) + 1) < 10 ? "0" + (c.get(Calendar.MONTH) + 1)
                    : (c.get(Calendar.MONTH) + 1) + "";
            monthYear.add(month + "$" + c.get(Calendar.YEAR));// 01$2020
            c.add(Calendar.MONTH, -1);
        }
        if (jsonNode.get(ExperianConstants.ACCT_HISTORY) != null
                && jsonNode.get(ExperianConstants.ACCT_HISTORY).isArray()) {
            for (JsonNode history : jsonNode.get(ExperianConstants.ACCT_HISTORY)) {
                if (monthYear.contains(history.get("Month").asText() + "$" + history.get("Year").asText())
                        && !history.get(ExperianConstants.DPD).isNull()
                        && !history.get(ExperianConstants.DPD).asText().equalsIgnoreCase("")
                        && history.get(ExperianConstants.DPD).asInt() > 0) {
                    dpd++;
                }
            }
        } else if (jsonNode.get(ExperianConstants.ACCT_HISTORY) != null
                && jsonNode.get(ExperianConstants.ACCT_HISTORY).isObject()) {
            JsonNode history = jsonNode.get(ExperianConstants.ACCT_HISTORY);
            if (monthYear.contains(history.get("Month").asText() + "$" + history.get("Year").asText())
                    && !history.get(ExperianConstants.DPD).isNull()
                    && !history.get(ExperianConstants.DPD).asText().equalsIgnoreCase("")
                    && history.get(ExperianConstants.DPD).asInt() > 0) {
                dpd++;
            }
        }
        return dpd;
    }

    private int loanSanctioned3mon(JsonNode jsonNode, Date reportDate) throws ParseException {
        if (jsonNode.get(ExperianConstants.OPEN_DATE) != null
                && !jsonNode.get(ExperianConstants.OPEN_DATE).toString().equalsIgnoreCase("\"\"")) {
            Date openDate = dateFormat.parse(jsonNode.get(ExperianConstants.OPEN_DATE).asText());
            return LoanUtil.getDateDiffInDays(openDate, reportDate) <= 90 ? 1 : 0;
        }
        return 0;
    }

    private int unsecuredLoan6mon(JsonNode jsonNode, Date reportDate) throws ParseException {
        if (jsonNode.get(ExperianConstants.ACCT_TYPE) != null && jsonNode.get(ExperianConstants.OPEN_DATE) != null
                && !jsonNode.get(ExperianConstants.OPEN_DATE).toString().equalsIgnoreCase("\"\"")) {
            Date openDate = dateFormat.parse(jsonNode.get(ExperianConstants.OPEN_DATE).asText());
            return LoanUtil.getDateDiffInDays(openDate, reportDate) <= 180
                    && unsecuredLoan.contains(jsonNode.get(ExperianConstants.ACCT_TYPE).asInt()) ? 1 : 0;
        } else if (jsonNode.get(ExperianConstants.ACCT_TYPE) != null
                && jsonNode.get(ExperianConstants.DATE_ADDITION) != null
                && !jsonNode.get(ExperianConstants.DATE_ADDITION).toString().equalsIgnoreCase("\"\"")) {
            Date openDate = dateFormat.parse(jsonNode.get(ExperianConstants.DATE_ADDITION).asText());
            return LoanUtil.getDateDiffInDays(openDate, reportDate) <= 180
                    && unsecuredLoan.contains(jsonNode.get(ExperianConstants.ACCT_TYPE).asInt()) ? 1 : 0;
        }
        return 0;
    }

    private boolean isLoanClosed(JsonNode loan) {
        JsonNode closedDate = loan.get(ExperianConstants.DATE_CLOSED);
        Date clsDate = null;
        try {
            clsDate = dateFormat.parse(closedDate.asText());
        } catch (Exception e) {
            clsDate = null;
        }
        boolean isClosed = false;
        if (clsDate != null) {
            isClosed = !clsDate.after(new Date());
        }
        Integer accountStatus = loan.has(ExperianConstants.ACCT_STATUS)
                && !loan.get(ExperianConstants.ACCT_STATUS).isNull() ? loan.get(ExperianConstants.ACCT_STATUS).asInt()
                        : null;
        return isClosed || !activeStatusList.contains(accountStatus);
    }

    private Double getLoanAmount(JsonNode loan) {
        double amount1 = loan.has("Highest_Credit_or_Original_Loan_Amount")
                ? loan.get("Highest_Credit_or_Original_Loan_Amount").asDouble()
                : 0D;
        double amount2 = loan.has("Credit_Limit_Amount") ? loan.get("Credit_Limit_Amount").asDouble() : 0D;
        return amount1 > amount2 ? amount1 : amount2;
    }

    private boolean isLoanClosedWithinOneYear(JsonNode loan) {
        String date = (loan.has(ExperianConstants.DATE_CLOSED) && !loan.get(ExperianConstants.DATE_CLOSED).isNull()
                && !loan.get(ExperianConstants.DATE_CLOSED).asText().equalsIgnoreCase(""))
                        ? loan.get(ExperianConstants.DATE_CLOSED).asText()
                        : null;
        if (date != null) {
            try {
                Date closingDate = dateFormat.parse(date);
                Date today = new Date();
                if (((today.getTime() - closingDate.getTime()) / 1000) > 31556952) {
                    return false;
                }
                return true;
            } catch (Exception e) {
                logger.error("Error occured while checking for loan closing duration", e);
            }
        }
        return false;
    }

    private String getLoanType(Integer loanType) {
        if (loanType == 1 || loanType == 17 || loanType == 32) {
            return "AL";
        } else if (loanType == 4 || loanType == 5 || loanType == 9 || loanType == 38 || loanType == 39
                || loanType == 60) {
            return "PL";

        } else if (loanType == 2 || loanType == 3) {
            return "HL";
        } else if (loanType == 51 || loanType == 52 || loanType == 53 || loanType == 54 || loanType == 59
                || loanType == 61) {
            return "BL";
        } else if (loanType == 10 || loanType == 35) {
            return "CC";
        } else if (loanType == 13) {
            return "TW";
        } else if (loanType == 6) {
            return "CD";
        } else if (loanType == 7) {
            return "GL";
        } else {
            return "Other";
        }
    }

    private Map<String, Double> getDebtAndIncome(JsonNode loan) {
        Map<String, Double> debtAndIncome = new HashMap<>();
        double debt = 0D;
        double income = 0D;
        Double otherIncome = 0D;
        if (loan.get(ExperianConstants.ACCT_TYPE) == null) {
            debtAndIncome.put("debt", debt);
            debtAndIncome.put("income", income);
            debtAndIncome.put("otherIncome", otherIncome);
            return debtAndIncome;
        }
        int loanTypeNumber = loan.get(ExperianConstants.ACCT_TYPE).asInt();
        double loanAmount = getLoanAmount(loan);
        boolean isLoanClosed = isLoanClosed(loan);
        boolean isLoanClosedWithinAYear = isLoanClosedWithinOneYear(loan);
        String loanType = getLoanType(loanTypeNumber);
        if (loanAmount > 10000 && (!isLoanClosed || isLoanClosedWithinAYear)) {
            switch (loanType) {
                case "AL": {
                    if (!isLoanClosedWithinAYear) {
                        debt += loanAmount * CreditConstants.EMI.get("AL");
                    }
                    income += loanAmount * CreditConstants.EMI.get("AL") / CreditConstants.DBI.get("AL");
                    otherIncome = CreditConstants.OTHER_INCOME.getOrDefault("AL", 0D) > otherIncome
                            ? CreditConstants.OTHER_INCOME.get("AL")
                            : otherIncome;
                }
                    break;
                case "PL": {
                    if (!isLoanClosedWithinAYear) {
                        debt += loanAmount * CreditConstants.EMI.get("PL");
                    }
                    income += loanAmount * CreditConstants.EMI.get("PL") / CreditConstants.DBI.get("PL");
                    otherIncome = CreditConstants.OTHER_INCOME.getOrDefault("PL", 0D) > otherIncome
                            ? CreditConstants.OTHER_INCOME.get("PL")
                            : otherIncome;
                }
                    break;
                case "HL": {
                    if (!isLoanClosedWithinAYear) {
                        debt += loanAmount * CreditConstants.EMI.get("HL");
                    }
                    income += loanAmount * CreditConstants.EMI.get("HL") / CreditConstants.DBI.get("HL");
                    otherIncome = CreditConstants.OTHER_INCOME.getOrDefault("HL", 0D) > otherIncome
                            ? CreditConstants.OTHER_INCOME.get("HL")
                            : otherIncome;
                }
                    break;
                case "BL": {
                    if (!isLoanClosedWithinAYear) {
                        debt += loanAmount * CreditConstants.EMI.get("BL");
                    }
                    income += loanAmount * CreditConstants.EMI.get("BL") / CreditConstants.DBI.get("BL");
                    otherIncome = CreditConstants.OTHER_INCOME.getOrDefault("BL", 0D) > otherIncome
                            ? CreditConstants.OTHER_INCOME.get("BL")
                            : otherIncome;
                }
                    break;
                case "CC": {
                    if (!isLoanClosedWithinAYear) {
                        debt += loanAmount * CreditConstants.EMI.get("CC");
                    }
                    income += loanAmount * CreditConstants.EMI.get("CC") / CreditConstants.DBI.get("CC");
                    otherIncome = CreditConstants.OTHER_INCOME.getOrDefault("CC", 0D) > otherIncome
                            ? CreditConstants.OTHER_INCOME.get("CC")
                            : otherIncome;
                }
                    break;
                case "TW": {
                    if (!isLoanClosedWithinAYear) {
                        debt += loanAmount * CreditConstants.EMI.get("TW");
                    }
                    income += loanAmount * CreditConstants.EMI.get("TW") / CreditConstants.DBI.get("TW");
                    otherIncome = CreditConstants.OTHER_INCOME.getOrDefault("TW", 0D) > otherIncome
                            ? CreditConstants.OTHER_INCOME.get("TW")
                            : otherIncome;
                }
                    break;
                case "CD": {
                    if (!isLoanClosedWithinAYear) {
                        debt += loanAmount * CreditConstants.EMI.get("CD");
                    }
                    income += loanAmount * CreditConstants.EMI.get("CD") / CreditConstants.DBI.get("CD");
                    otherIncome = CreditConstants.OTHER_INCOME.getOrDefault("CD", 0D) > otherIncome
                            ? CreditConstants.OTHER_INCOME.get("CD")
                            : otherIncome;
                }
                    break;
                case "GL": {
                    if (!isLoanClosedWithinAYear) {
                        debt += loanAmount * CreditConstants.EMI.get("GL");
                    }
                    income += loanAmount * CreditConstants.EMI.get("GL") / CreditConstants.DBI.get("GL");
                    otherIncome = CreditConstants.OTHER_INCOME.getOrDefault("GL", 0D) > otherIncome
                            ? CreditConstants.OTHER_INCOME.get("GL")
                            : otherIncome;
                }
                    break;
                default: {
                    if (!isLoanClosedWithinAYear) {
                        debt += loanAmount * CreditConstants.EMI.get("Other");
                    }
                    income += loanAmount * CreditConstants.EMI.get("Other") / CreditConstants.DBI.get("Other");
                    otherIncome = CreditConstants.OTHER_INCOME.getOrDefault("Other", 0D) > otherIncome
                            ? CreditConstants.OTHER_INCOME.get("Other")
                            : otherIncome;
                }
                    break;
            }
            logger.info(
                    "loanStatus: {}, loanAmount:{}, isLoanClosed: {}, isLoanClosedWithinAYear: {}, loanType: {}, income:{}, debt:{}",
                    loan.get(ExperianConstants.ACCT_STATUS), loanAmount, isLoanClosed, isLoanClosedWithinAYear,
                    loanType, income, debt);
        }
        debtAndIncome.put("debt", debt);
        debtAndIncome.put("income", income);
        debtAndIncome.put("otherIncome", otherIncome);
        return debtAndIncome;
    }

    private Map<String, Double> getDebtAndIncome(ArrayNode loanDetails) {
        Map<String, Double> debtAndIncome = new HashMap<>();
        double income = 0D;
        double debt = 0D;
        Double otherIncome = 0D;
        for (JsonNode loan : loanDetails) {
            if (loan.get(ExperianConstants.ACCT_TYPE) == null) {
                continue;
            }
            int loanTypeNumber = loan.get(ExperianConstants.ACCT_TYPE).asInt();
            double loanAmount = getLoanAmount(loan);
            boolean isLoanClosed = isLoanClosed(loan);
            boolean isLoanClosedWithinAYear = isLoanClosedWithinOneYear(loan);
            String loanType = getLoanType(loanTypeNumber);
            if (loanAmount > 10000 && (!isLoanClosed || isLoanClosedWithinAYear)) {
                switch (loanType) {
                    case "AL": {
                        if (!isLoanClosedWithinAYear) {
                            debt += loanAmount * CreditConstants.EMI.get("AL");
                        }
                        income += loanAmount * CreditConstants.EMI.get("AL") / CreditConstants.DBI.get("AL");
                        otherIncome = CreditConstants.OTHER_INCOME.getOrDefault("AL", 0D) > otherIncome
                                ? CreditConstants.OTHER_INCOME.get("AL")
                                : otherIncome;
                    }
                        break;
                    case "PL": {
                        if (!isLoanClosedWithinAYear) {
                            debt += loanAmount * CreditConstants.EMI.get("PL");
                        }
                        income += loanAmount * CreditConstants.EMI.get("PL") / CreditConstants.DBI.get("PL");
                        otherIncome = CreditConstants.OTHER_INCOME.getOrDefault("PL", 0D) > otherIncome
                                ? CreditConstants.OTHER_INCOME.get("PL")
                                : otherIncome;
                    }
                        break;
                    case "HL": {
                        if (!isLoanClosedWithinAYear) {
                            debt += loanAmount * CreditConstants.EMI.get("HL");
                        }
                        income += loanAmount * CreditConstants.EMI.get("HL") / CreditConstants.DBI.get("HL");
                        otherIncome = CreditConstants.OTHER_INCOME.getOrDefault("HL", 0D) > otherIncome
                                ? CreditConstants.OTHER_INCOME.get("HL")
                                : otherIncome;
                    }
                        break;
                    case "BL": {
                        if (!isLoanClosedWithinAYear) {
                            debt += loanAmount * CreditConstants.EMI.get("BL");
                        }
                        income += loanAmount * CreditConstants.EMI.get("BL") / CreditConstants.DBI.get("BL");
                        otherIncome = CreditConstants.OTHER_INCOME.getOrDefault("BL", 0D) > otherIncome
                                ? CreditConstants.OTHER_INCOME.get("BL")
                                : otherIncome;
                    }
                        break;
                    case "CC": {
                        if (!isLoanClosedWithinAYear) {
                            debt += loanAmount * CreditConstants.EMI.get("CC");
                        }
                        income += loanAmount * CreditConstants.EMI.get("CC") / CreditConstants.DBI.get("CC");
                        otherIncome = CreditConstants.OTHER_INCOME.getOrDefault("CC", 0D) > otherIncome
                                ? CreditConstants.OTHER_INCOME.get("CC")
                                : otherIncome;
                    }
                        break;
                    case "TW": {
                        if (!isLoanClosedWithinAYear) {
                            debt += loanAmount * CreditConstants.EMI.get("TW");
                        }
                        income += loanAmount * CreditConstants.EMI.get("TW") / CreditConstants.DBI.get("TW");
                        otherIncome = CreditConstants.OTHER_INCOME.getOrDefault("TW", 0D) > otherIncome
                                ? CreditConstants.OTHER_INCOME.get("TW")
                                : otherIncome;
                    }
                        break;
                    case "CD": {
                        if (!isLoanClosedWithinAYear) {
                            debt += loanAmount * CreditConstants.EMI.get("CD");
                        }
                        income += loanAmount * CreditConstants.EMI.get("CD") / CreditConstants.DBI.get("CD");
                        otherIncome = CreditConstants.OTHER_INCOME.getOrDefault("CD", 0D) > otherIncome
                                ? CreditConstants.OTHER_INCOME.get("CD")
                                : otherIncome;
                    }
                        break;
                    case "GL": {
                        if (!isLoanClosedWithinAYear) {
                            debt += loanAmount * CreditConstants.EMI.get("GL");
                        }
                        income += loanAmount * CreditConstants.EMI.get("GL") / CreditConstants.DBI.get("GL");
                        otherIncome = CreditConstants.OTHER_INCOME.getOrDefault("GL", 0D) > otherIncome
                                ? CreditConstants.OTHER_INCOME.get("GL")
                                : otherIncome;
                    }
                        break;
                    default: {
                        if (!isLoanClosedWithinAYear) {
                            debt += loanAmount * CreditConstants.EMI.get("Other");
                        }
                        income += loanAmount * CreditConstants.EMI.get("Other") / CreditConstants.DBI.get("Other");
                        otherIncome = CreditConstants.OTHER_INCOME.getOrDefault("Other", 0D) > otherIncome
                                ? CreditConstants.OTHER_INCOME.get("Other")
                                : otherIncome;
                    }
                        break;
                }
            }
            logger.info(
                    "loanStatus: {}, loanAmount:{}, isLoanClosed: {}, isLoanClosedWithinAYear: {}, loanType: {}, income:{}, debt:{}",
                    loan.get(ExperianConstants.ACCT_STATUS), loanAmount, isLoanClosed, isLoanClosedWithinAYear,
                    loanType, income, debt);
        }
        debtAndIncome.put("debt", debt);
        debtAndIncome.put("income", income);
        debtAndIncome.put("otherIncome", otherIncome);
        return debtAndIncome;
    }

    private boolean checkUnsecuredLiveLoans(JsonNode jsonNode) {
        return jsonNode.get(ExperianConstants.DATE_CLOSED).toString().equals("\"\"")
                && jsonNode.get(ExperianConstants.ACCT_TYPE).asInt() != 10
                && derogUnsecuredProducts.contains(jsonNode.get(ExperianConstants.ACCT_TYPE).asInt());
    }

    @Override
    public int countLoanEnquiriesInLast3Months() throws ParseException {
        if (response.get(ExperianConstants.PROFILE_RESPONSE).get(ExperianConstants.CAPS_SUMMARY) != null
                && response.get(ExperianConstants.PROFILE_RESPONSE).get(ExperianConstants.CAPS_SUMMARY)
                        .get("TotalCAPSLast90Days") != null)
            return response.get(ExperianConstants.PROFILE_RESPONSE).get(ExperianConstants.CAPS_SUMMARY)
                    .get("TotalCAPSLast90Days").asInt();
        return 0;
    }

    @Override
    public int countUnsecuredLoanEnquiriesInLast6Months() throws ParseException {
        Date reportDate = getReportDate();
        if (response.get(ExperianConstants.PROFILE_RESPONSE).get(ExperianConstants.CAPS_SUMMARY) != null
                && response.get(ExperianConstants.PROFILE_RESPONSE).get(ExperianConstants.CAPS_SUMMARY)
                        .get("TotalCAPSLast180Days") != null
                && response.get(ExperianConstants.PROFILE_RESPONSE).get(ExperianConstants.CAPS_SUMMARY)
                        .get("TotalCAPSLast180Days").asInt() <= 4) {
            return 0;
        }
        Calendar c = Calendar.getInstance();
        c.setTime(reportDate);
        c.add(Calendar.MONTH, -6);
        String month = (c.get(Calendar.MONTH) + 1) < 10 ? "0" + (c.get(Calendar.MONTH) + 1)
                : (c.get(Calendar.MONTH) + 1) + "";
        String day = (c.get(Calendar.DAY_OF_MONTH) + 1) < 10 ? "0" + (c.get(Calendar.DAY_OF_MONTH) + 1)
                : (c.get(Calendar.DAY_OF_MONTH) + 1) + "";
        long previous6MonthDate = Long.parseLong(c.get(Calendar.YEAR) + month + day);
        if (response.get(ExperianConstants.PROFILE_RESPONSE).get("CAPS").get(ExperianConstants.CAPS_DETAILS) != null
                && response.get(ExperianConstants.PROFILE_RESPONSE).get("CAPS").get(ExperianConstants.CAPS_DETAILS)
                        .isObject()) {
            JsonNode jsonNode = response.get(ExperianConstants.PROFILE_RESPONSE).get("CAPS")
                    .get(ExperianConstants.CAPS_DETAILS);
            return jsonNode.get(ExperianConstants.PRODUCT) != null && jsonNode.get(ExperianConstants.DOR) != null
                    && derogUnsecuredProducts.contains(jsonNode.get(ExperianConstants.PRODUCT).asInt())
                    && jsonNode.get(ExperianConstants.DOR).longValue() >= previous6MonthDate ? 1 : 0;
        } else if (response.get(ExperianConstants.PROFILE_RESPONSE).get("CAPS")
                .get(ExperianConstants.CAPS_DETAILS) != null
                && response.get(ExperianConstants.PROFILE_RESPONSE).get("CAPS").get(ExperianConstants.CAPS_DETAILS)
                        .isArray()) {
            for (JsonNode jsonNode : response.get(ExperianConstants.PROFILE_RESPONSE).get("CAPS")
                    .get(ExperianConstants.CAPS_DETAILS)) {
                if (jsonNode.get(ExperianConstants.PRODUCT) != null
                        && derogUnsecuredProducts.contains(jsonNode.get(ExperianConstants.PRODUCT).asInt())
                        && jsonNode.get(ExperianConstants.DOR) != null
                        && jsonNode.get(ExperianConstants.DOR).longValue() >= previous6MonthDate) {
                    return 1;
                }
            }
        }
        return 0;
    }

    @Override
    public Map<String, Object> getBBSCalculationDetails() throws IOException, ParseException {
        Map<String, Object> res = new HashMap<>();
        Map<String, Double> debtAndIncome = new HashMap<>();
        Date reportDate = getReportDate();
        int delinquencyCount6mon = 0;
        int loanSanctioned3mon = 0;
        int unsecuredLoanCount6mon = 0;
        Date minOpenDate = reportDate;
        Set<Integer> loanTypes = new HashSet<>();
        JsonNode accountDetails = response.get(ExperianConstants.PROFILE_RESPONSE).get(ExperianConstants.ACCT)
                .get(ExperianConstants.ACCT_DETAILS);
        if (accountDetails != null && accountDetails.isObject()) {
            debtAndIncome = getDebtAndIncome(accountDetails);
            delinquencyCount6mon += countDPDLastXmonths(accountDetails, 6, reportDate);
            loanSanctioned3mon += loanSanctioned3mon(accountDetails, reportDate);
            unsecuredLoanCount6mon += unsecuredLoan6mon(accountDetails, reportDate);
            if (accountDetails.get(ExperianConstants.ACCT_TYPE) != null) {
                loanTypes.add(accountDetails.get(ExperianConstants.ACCT_TYPE).asInt());
            }
            if (accountDetails.get(ExperianConstants.OPEN_DATE) != null
                    && !accountDetails.get(ExperianConstants.OPEN_DATE).toString().equalsIgnoreCase("\"\"")) {
                Date openDate = dateFormat.parse(accountDetails.get(ExperianConstants.OPEN_DATE).asText());
                if (openDate.before(minOpenDate)) {
                    minOpenDate = openDate;
                }
            } else if (accountDetails.get(ExperianConstants.DATE_ADDITION) != null
                    && !accountDetails.get(ExperianConstants.DATE_ADDITION).toString().equalsIgnoreCase("\"\"")) {
                Date openDate = dateFormat.parse(accountDetails.get(ExperianConstants.DATE_ADDITION).asText());
                if (openDate.before(minOpenDate)) {
                    minOpenDate = openDate;
                }
            }
        } else if (accountDetails != null && accountDetails.isArray()) {
            debtAndIncome = getDebtAndIncome((ArrayNode) accountDetails);
            for (JsonNode caisAccountDetails : accountDetails) {
                delinquencyCount6mon += countDPDLastXmonths(caisAccountDetails, 6, reportDate);
                loanSanctioned3mon += loanSanctioned3mon(caisAccountDetails, reportDate);
                unsecuredLoanCount6mon += unsecuredLoan6mon(caisAccountDetails, reportDate);
                if (caisAccountDetails.get(ExperianConstants.ACCT_TYPE) != null) {
                    loanTypes.add(caisAccountDetails.get(ExperianConstants.ACCT_TYPE).asInt());
                }
                if (caisAccountDetails.get(ExperianConstants.OPEN_DATE) != null
                        && !caisAccountDetails.get(ExperianConstants.OPEN_DATE).toString().equalsIgnoreCase("\"\"")) {
                    Date openDate = dateFormat.parse(caisAccountDetails.get(ExperianConstants.OPEN_DATE).asText());
                    if (openDate.before(minOpenDate)) {
                        minOpenDate = openDate;
                    }
                } else if (caisAccountDetails.get(ExperianConstants.DATE_ADDITION) != null
                        && !caisAccountDetails.get(ExperianConstants.DATE_ADDITION).toString().equalsIgnoreCase("\"\"")) {
                    Date openDate = dateFormat.parse(caisAccountDetails.get(ExperianConstants.DATE_ADDITION).asText());
                    if (openDate.before(minOpenDate)) {
                        minOpenDate = openDate;
                    }
                }
            }
        }
        res.put("debtAndIncome", debtAndIncome);
        res.put("delinquencyCount6mon", delinquencyCount6mon);
        res.put("loanSanctioned3mon", loanSanctioned3mon);
        res.put("unsecuredLoanCount6mon", unsecuredLoanCount6mon);
        res.put("minOpenDate", minOpenDate);
        res.put("loanTypes", loanTypes);
        return res;
    }

}
