package com.bharatpe.lending.util.creditresponse;

import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.LendingMerchantDropoffDao;
import com.bharatpe.lending.common.entity.LendingMerchantDropoff;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.constant.CrifConstants;
import com.bharatpe.lending.dto.CreditScoreReportDetailDTO;
import com.bharatpe.lending.dto.LoanAndCreditCardDetailDTO;
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

public class CrifResponseUtil extends ResponseUtilBase implements ResponseUtil {

    List<String> delinquentDPDStatus = Arrays.asList("SUB", "DBT", "SMA", "LOS");
    List<String> unsecuredProducts = CrifConstants.UNSECURED_PRODUCTS;

    List<String> loanTypeAL = Arrays.asList("auto loan (personal)", "commercial vehicle loan", "used car loan");
    List<String> loanTypeBL = Arrays.asList("business loan - secured", "business loan against bank deposits",
            "business loan general", "business loan priority sector agriculture",
            "business loan priority sector others", "business loan priority sector small business",
            "business loan unsecured", "microfinance business loan", "od on savings account");
    List<String> loanTypeCC = Arrays.asList("corporate credit card", "credit card", "secured credit card");
    List<String> loanTypeCD = Arrays.asList("consumer loan");
    List<String> loanTypeGL = Arrays.asList("gold loan");
    List<String> loanTypeHL = Arrays.asList("housing loan", "microfinance housing loan",
            "pradhan mantri awas yojana - clss", "property loan");
    List<String> loanTypePL = Arrays.asList("loan against shares / securities", "loan to professional",
            "microfinance personal loan", "mudra loans – shishu / kishor / tarun", "personal loan",
            "prime minister jaan dhan yojana - overdraft", "staff loan");
    List<String> loanTypeTW = Arrays.asList("two-wheeler loan");
    List<String> loanTypeOther = Arrays.asList("auto overdraft", "business non-funded credit facility general",
            "business non-funded credit facility-priority sector- small business",
            "business non-funded credit facility-priority sector-agriculture",
            "business non-funded credit facility-priority sector-others", "charge card", "commercial equipment loan",
            "education loan", "fleet card", "individual", "jlg group", "jlg individual", "kisan credit card", "leasing",
            "loan against bank deposits", "loan against card", "loan on credit card", "microfinance others",
            "non-funded credit facility", "other", "overdraft", "shd intra - group", "shg group", "shg group – govt",
            "shg individual", "telco broadband", "telco landline", "telco wireless", "used tractor loan");
    List<String> unsecuredLoanTypes = CrifConstants.UNSECURED_ACCT_TYPES;

    List<String> categoryA = Arrays.asList("consumer loan", "gold loan", "two-wheeler loan",
            "prime minister jaan dhan yojana - overdraft", "mudra loans – shishu / kishor / tarun",
            "microfinance others");
    List<String> categoryB = Arrays.asList("auto loan (personal)", "personal loan", "education loan",
            "loan to professional", "credit card", "leasing", "overdraft", "commercial vehicle loan", "used car loan",
            "construction equipment loan", "tractor loan", "kisan credit card", "loan on credit card",
            "business loan general", "business loan priority sector small business",
            "business loan priority sector agriculture", "business loan priority sector others",
            "business non-funded credit facility general",
            "business non-funded credit facility-priority sector- small business",
            "business non-funded credit facility-priority sector-agriculture",
            "business non-funded credit facility-priority sector-others", "business loan against bank deposits",
            "staff loan", "business loan unsecured");
    List<String> categoryC = Arrays.asList("housing loan", "property loan");
    ExperianDao experianDao;
    EasyLoanUtil easyLoanUtil;
    LendingMerchantDropoffDao lendingMerchantDropoffDao;

    SimpleDateFormat dateFormat = new SimpleDateFormat(CrifConstants.DATE_FORMAT);

    public CrifResponseUtil(ExperianDao experianDao, EasyLoanUtil easyLoanUtil) {
        this.type = "CRIF";
        this.experianDao = experianDao;
        this.easyLoanUtil = easyLoanUtil;
    }

    public CrifResponseUtil(ExperianDao experianDao) {
        this.type = "CRIF";
        this.experianDao = experianDao;
    }

    public CrifResponseUtil(JsonNode response, ExperianDao experianDao, LendingMerchantDropoffDao lendingMerchantDropoffDao) {
        this.type = "CRIF";
        this.response = response;
        this.experianDao = experianDao;
        this.lendingMerchantDropoffDao = lendingMerchantDropoffDao;
    }

    @Override
    public String getEmail() {
        JsonNode email = response.get(CrifConstants.REPORT_HEADER).get(CrifConstants.REQUEST);
        email = email != null ? email.get("EMAIL-1") : null;
        return email != null ? easyLoanUtil.getNodeValueAsString(email) : null;
    }

    @Override
    public String getType() {
        return this.type;
    }

    @Override
    public Double getBureauScore() {
        JsonNode bureauScore = response.get(CrifConstants.REPORT_HEADER).get(CrifConstants.SCORES);
        bureauScore = bureauScore != null ? bureauScore.get(CrifConstants.SCORE) : null;
        return bureauScore != null && bureauScore.get("SCORE-VALUE") != null ? bureauScore.get("SCORE-VALUE").asDouble()
                : null;
    }

    @Override
    public Date getReportDate() {
        try {
            JsonNode dateOfIssue = response.get(CrifConstants.REPORT_HEADER).get("HEADER");
            dateOfIssue = dateOfIssue != null ? dateOfIssue.get("DATE-OF-ISSUE") : null;
            return dateFormat.parse(easyLoanUtil.getNodeValueAsString(dateOfIssue));
        } catch (Exception e) {
            logger.info("Exception in parsing reportDate in CrifResponseUtil", e);
            return null;
        }
    }

    @Override
    public String getResponse() {
        return this.response.toString();
    }

    @Override
    public int fetchBureauVintage() {
        DateTimeFormatter formatter = DateTimeFormat.forPattern(CrifConstants.DATE_FORMAT);
        DateTime min = new DateTime();
        JsonNode loanDetails = response.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES);
        loanDetails = loanDetails != null ? loanDetails.get(CrifConstants.RESPONSE) : null;
        if (loanDetails != null && loanDetails.isArray()) {
            for (JsonNode jsonNode : loanDetails) {
                try {
                    String openDate = jsonNode.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.DISBURSED_DT).asText();
                    min = formatter.parseDateTime(openDate).isBefore(min) ? formatter.parseDateTime(openDate) : min;
                } catch (Exception e) {
                    logger.info("Invalid Open_Date");
                }
            }
            return Months.monthsBetween(min, DateTime.now()).getMonths();
        } else if (loanDetails != null && loanDetails.isObject()) {
            try {
                String openDate = loanDetails.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.DISBURSED_DT).asText();
                min = formatter.parseDateTime(openDate).isBefore(min) ? formatter.parseDateTime(openDate) : min;
            } catch (Exception e) {
                logger.info("Invalid Open_Date");
            }
            return Months.monthsBetween(min, DateTime.now()).getMonths();
        }
        return 0;
    }

    @Override
    public String fetchAccountCategory() {
        boolean a = false, b = false, c = false;
        JsonNode accountDetails = response.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES);
        accountDetails = accountDetails != null ? accountDetails.get(CrifConstants.RESPONSE) : null;
        if (accountDetails != null && accountDetails.isArray()) {
            for (JsonNode jsonNode : accountDetails) {
                jsonNode = jsonNode.get(CrifConstants.LOAN_DETAILS);
                if (jsonNode != null) {
                    String acctType = easyLoanUtil.getNodeValueAsString(jsonNode.get(CrifConstants.ACCT_TYPE));
                    if (categoryA.contains(acctType.toLowerCase())) {
                        a = true;
                    }
                    if (categoryB.contains(acctType.toLowerCase())) {
                        b = true;
                    }
                    if (categoryC.contains(acctType.toLowerCase())) {
                        c = true;
                    }
                }
            }
        } else if (accountDetails != null && accountDetails.isObject()) {
            JsonNode jsonNode = accountDetails.get(CrifConstants.LOAN_DETAILS);
            if (jsonNode != null) {
                String acctType = easyLoanUtil.getNodeValueAsString(jsonNode.get(CrifConstants.ACCT_TYPE));
                if (categoryA.contains(acctType.toLowerCase())) {
                    a = true;
                }
                if (categoryB.contains(acctType.toLowerCase())) {
                    b = true;
                }
                if (categoryC.contains(acctType.toLowerCase())) {
                    c = true;
                }
            }
        }
        return c ? "C" : b ? "B" : a ? "A" : "NTC";
    }

    @Override
    public boolean isValid(String panCard, String phoneNumber) {
        boolean checkPan = false;
        boolean checkPhone = false;
        if (this.response != null) {
            JsonNode personalData = this.response.get(CrifConstants.REPORT_HEADER)
                    .get(CrifConstants.PERSONAL_VARIATIONS);
            if (personalData == null || personalData.toString().equalsIgnoreCase("\"\"")) {
                return false;
            }
            if (personalData.get(CrifConstants.PAN_VARIATIONS) == null
                    || personalData.get(CrifConstants.PAN_VARIATIONS).toString().equalsIgnoreCase("\"\"")) {
                return false;
            }
            if (personalData.get(CrifConstants.PHONE_VARIATIONS) == null
                    || personalData.get(CrifConstants.PHONE_VARIATIONS).toString().equalsIgnoreCase("\"\"")) {
                return false;
            }
            List<JsonNode> panVariations = LoanUtil
                    .jsonNodeArrayUtil(personalData.get(CrifConstants.PAN_VARIATIONS).get(CrifConstants.VARIATION));
            List<JsonNode> phoneVariations = LoanUtil
                    .jsonNodeArrayUtil(personalData.get(CrifConstants.PHONE_VARIATIONS).get(CrifConstants.VARIATION));
            if (phoneNumber.length() > 10) {
                phoneNumber = phoneNumber.substring(2);// remove 91
            }
            for (JsonNode pan : panVariations) {
                checkPan = easyLoanUtil.getNodeValueAsString(pan.get("VALUE")).equalsIgnoreCase(panCard);
                if(checkPan) break;
            }
            for (JsonNode phone : phoneVariations) {
                checkPhone = easyLoanUtil.getNodeValueAsString(phone.get("VALUE")).equalsIgnoreCase(phoneNumber);
                if(checkPhone) break;
            }
        }
        return checkPan && checkPhone;
    }

    @Override
    public boolean isDerog(BasicDetailsDto merchant, boolean isRepeatLoanNoDerog, Experian experian) throws ParseException {
        Date reportDate = getReportDate();
        JsonNode responses = response.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES);
        responses = responses != null ? responses.get(CrifConstants.RESPONSE) : null;
        if (responses != null && responses.isObject() && responses.get(CrifConstants.LOAN_DETAILS) != null
                && derogChecks(responses.get(CrifConstants.LOAN_DETAILS), merchant.getId(), isRepeatLoanNoDerog,
                reportDate, experian)) {
            logger.info("Derog check failed, rejecting merchant: {}", merchant.getId());
            return true;
        } else if (responses != null) {
            int unsecuredLoanCount = 0;
            for (JsonNode resp : responses) {
                JsonNode loanDetail = resp.get(CrifConstants.LOAN_DETAILS);
                if (loanDetail != null
                        && derogChecks(loanDetail, merchant.getId(), isRepeatLoanNoDerog, reportDate, experian)) {
                    logger.info("Derog check failed, rejecting merchant: {}", merchant.getId());
                    return true;
                }
                if (loanDetail != null && checkUnsecuredLiveLoans(loanDetail)) {
                    unsecuredLoanCount++;
                }
            }
            // Not more than 3 live unsecured loans running
            if (!isRepeatLoanNoDerog && unsecuredLoanCount > 3) {
                logger.info("Derog more than 3 live unsecured loans running, rejecting merchant: {}", merchant.getId());
                experian.setRejected(true);
                experian.setRejectedDate(new Date());
                experian.setReason(CrifConstants.DEROG_UNSECURED_LOANS);
                experianDao.save(experian);
                return true;
            }
        }
        // Not more than 8 unsecured loan enquiries in the last 6 months --- Derog check
        if (!isRepeatLoanNoDerog) {
            int unsecuredEnquiries = countUnsecuredLoanEnquiriesInLast6Months();
            if (unsecuredEnquiries > 8) {
                logger.info("Derog more than 8 unsecured loan enquiries in the last 6 months, rejecting merchant: {}",
                        merchant.getId());
                experian.setRejected(true);
                experian.setRejectedDate(new Date());
                experian.setReason(CrifConstants.DEROG_UNSECURED_LOAN_ENQUIRY);
                experianDao.save(experian);
                return true;
            }
            if (unsecuredEnquiries > 4) {
                lendingMerchantDropoffDao.save(new LendingMerchantDropoff(merchant.getId(), "DEROG", CrifConstants.DEROG_UNSECURED_LOAN_ENQUIRY, String.valueOf(unsecuredEnquiries)));
            }
        }
        // Not more than 6 enquiries in the last 3 months ( across all product types)
        // --- Derog check
        if (!isRepeatLoanNoDerog && countLoanEnquiriesInLast3Months() > 6) {
            logger.info("Derog more than 6 enquiries in the last 3 months, rejecting merchant: {}", merchant.getId());
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(CrifConstants.DEROG_MORE_THAN_6_LOAN_ENQUIRY);
            experianDao.save(experian);
            return true;
        }
        return false;
    }

    public boolean derogChecks(JsonNode jsonNode, Long merchantId, boolean isRepeatLoanNoDerog, Date reportDate,
                               Experian experian) {
        // Check for Derog Account Status
        if (jsonNode.get(CrifConstants.ACCT_STATUS) != null
                && easyLoanUtil.getNodeValueAsString(jsonNode.get(CrifConstants.ACCT_STATUS)).equalsIgnoreCase("\"WRITTEN-OFF\"")) {
            logger.info("Derog Account Status check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(CrifConstants.DEROG_ACCOUNT_STATUS);
            experianDao.save(experian);
            return true;
        }
        // Check for Derog DPD Last 3 months
        if (!isRepeatLoanNoDerog && checkDPDLastXmonths(jsonNode, 3, reportDate)) {
            lendingMerchantDropoffDao.save(new LendingMerchantDropoff(merchantId, "DEROG", CrifConstants.DEROG_DPD_LAST_3_MONTHS, String.valueOf(countDPDLastXmonths(jsonNode, 3, reportDate))));
        }
        // Check for Derog DPD Last 6 months
        if (!isRepeatLoanNoDerog && checkDPDLastXmonths(jsonNode, 6, reportDate)) {
            logger.info("Derog DPD Last 6 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(CrifConstants.DEROG_DPD_LAST_6_MONTHS);
            experianDao.save(experian);
            return true;
        }
        // Check for Derog DPD Last 12 months
        if (!isRepeatLoanNoDerog && checkDPDLastXmonths(jsonNode, 12, reportDate)) {
            logger.info("Derog DPD Last 12 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(CrifConstants.DEROG_DPD_LAST_12_MONTHS);
            experianDao.save(experian);
            return true;
        }
        // Check for Derog DPD Last 24 months
        if (!isRepeatLoanNoDerog && checkDPDLastXmonths(jsonNode, 24, reportDate)) {
            logger.info("Derog DPD Last 24 months check failed, rejecting merchant: {}", merchantId);
            experian.setRejected(true);
            experian.setRejectedDate(new Date());
            experian.setReason(CrifConstants.DEROG_DPD_LAST_24_MONTHS);
            experianDao.save(experian);
            return true;
        }
        return false;
    }

    private boolean checkDPDLastXmonths(JsonNode jsonNode, int months, Date reportDate) {
        Date dateReported = null;
        try {
            JsonNode dateRep = jsonNode.get(CrifConstants.DATE_REPORTED);
            if (dateRep != null && !dateRep.toString().equalsIgnoreCase("\"\"")) {
                dateReported = dateFormat.parse(easyLoanUtil.getNodeValueAsString(dateRep));
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
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMM");
        for (int i = 0; i < months; i++) {
            month = monthFormat.format(c.getTime());
            monthYear.add(month + ":" + c.get(Calendar.YEAR));// Jan:2020
            c.add(Calendar.MONTH, -1);
        }
        int dpdToCheck = 5;// 3 months
        switch (months) {
            case 6:
                dpdToCheck = 30;
                break;
            case 12:
                dpdToCheck = 60;
                break;
            case 24:
                dpdToCheck = 90;
                break;
            default:
                break;
        }
        JsonNode paymentHistory = jsonNode.get("COMBINED-PAYMENT-HISTORY");
        int dpdCount = 0;
        if (paymentHistory != null && !paymentHistory.toString().equalsIgnoreCase("\"\"")) {
            List<String> loanHistory = Arrays.asList(easyLoanUtil.getNodeValueAsString(paymentHistory).split("\\|"));
            for (String monthNode : loanHistory) {
                String date = monthNode.split(",")[0];
                String code1 = monthNode.split(",")[1].split("/")[0];
                String code2 = monthNode.split(",")[1].split("/")[1];
                if (monthYear.contains(date)) {
                    dpdCount = (delinquentDPDStatus.contains(code2)) ? 90 : codeToCount(code1);
                    if (dpdCount >= dpdToCheck)
                        return true;
                }
            }
        }
        return false;
    }

    private boolean checkUnsecuredLiveLoans(JsonNode jsonNode) {
        return (easyLoanUtil.getNodeValueAsString(jsonNode.get(CrifConstants.ACCT_STATUS)).equalsIgnoreCase("\"ACTIVE\"")
                || easyLoanUtil.getNodeValueAsString(jsonNode.get(CrifConstants.ACCT_STATUS)).equalsIgnoreCase("\"CURRENT\""))
                && unsecuredProducts.contains(easyLoanUtil.getNodeValueAsString(jsonNode.get(CrifConstants.ACCT_TYPE)).toLowerCase());
    }

    @Override
    public int countLoanEnquiriesInLast3Months() throws ParseException {
        Date reportDate = getReportDate();
        int inquiryCount = 0;
        JsonNode inquiryDate;
        JsonNode inquiryHistory = response.get(CrifConstants.REPORT_HEADER).get(CrifConstants.INQUIRY_HISTORY);
        if (inquiryHistory == null || inquiryHistory.toString().equalsIgnoreCase("\"\""))
            return 0;
        inquiryHistory = inquiryHistory.get(CrifConstants.HISTORY);
        if (inquiryHistory == null || inquiryHistory.toString().equalsIgnoreCase("\"\""))
            return 0;
        if (inquiryHistory.isArray()) {
            for (JsonNode history : inquiryHistory) {
                inquiryDate = history.get(CrifConstants.INQUIRY_DATE);
                if (inquiryDate == null)
                    continue;
                Date inqDate = dateFormat.parse(easyLoanUtil.getNodeValueAsString(inquiryDate));
                if (LoanUtil.getDateDiffInDays(inqDate, reportDate) <= 90)
                    inquiryCount += 1;
            }
        } else if (inquiryHistory.isObject()) {
            inquiryDate = inquiryHistory.get(CrifConstants.INQUIRY_DATE);
            if (inquiryDate == null)
                return inquiryCount;
            Date inqDate = dateFormat.parse(easyLoanUtil.getNodeValueAsString(inquiryDate));
            if (LoanUtil.getDateDiffInDays(inqDate, reportDate) <= 90)
                inquiryCount += 1;
        }
        return inquiryCount;
    }

    @Override
    public int countUnsecuredLoanEnquiriesInLast6Months() throws ParseException {
        Date reportDate = getReportDate();
        int inquiryCount = 0;
        JsonNode inquiryDate;
        JsonNode purpose;
        JsonNode inquiryHistory = response.get(CrifConstants.REPORT_HEADER).get(CrifConstants.INQUIRY_HISTORY);
        if (inquiryHistory == null || inquiryHistory.toString().equalsIgnoreCase("\"\""))
            return 0;
        inquiryHistory = inquiryHistory.get(CrifConstants.HISTORY);
        if (inquiryHistory == null || inquiryHistory.toString().equalsIgnoreCase("\"\""))
            return 0;
        if (inquiryHistory.isArray()) {
            for (JsonNode history : inquiryHistory) {
                inquiryDate = history.get(CrifConstants.INQUIRY_DATE);
                purpose = history.get("PURPOSE");
                if (inquiryDate == null || purpose == null)
                    continue;
                Date inqDate = dateFormat.parse(easyLoanUtil.getNodeValueAsString(inquiryDate));
                if (LoanUtil.getDateDiffInDays(inqDate, reportDate) <= 180
                        && unsecuredProducts.contains(easyLoanUtil.getNodeValueAsString(purpose).toLowerCase()))
                    inquiryCount += 1;
            }
        } else if (inquiryHistory.isObject()) {
            inquiryDate = inquiryHistory.get(CrifConstants.INQUIRY_DATE);
            purpose = inquiryHistory.get("PURPOSE");
            if (inquiryDate == null || purpose == null)
                return inquiryCount;
            Date inqDate = dateFormat.parse(easyLoanUtil.getNodeValueAsString(inquiryDate));
            if (LoanUtil.getDateDiffInDays(inqDate, reportDate) <= 180
                    && unsecuredProducts.contains(easyLoanUtil.getNodeValueAsString(purpose).toLowerCase()))
                inquiryCount += 1;
        }
        return inquiryCount;
    }

    private boolean isLoanClosed(JsonNode loan) {
        JsonNode closedDate = loan.get(CrifConstants.CLOSED_DATE);
        Date clsDate = null;
        try {
            clsDate = dateFormat.parse(easyLoanUtil.getNodeValueAsString(closedDate));
        } catch (Exception e) {
            clsDate = null;
        }
        if (clsDate != null && !clsDate.after(new Date())) {
            return true;
        }
        return loan.get(CrifConstants.ACCT_STATUS) != null
                && (loan.get(CrifConstants.ACCT_STATUS).toString().equalsIgnoreCase("\"Closed\""));
    }

    private Double getLoanAmount(JsonNode loan) {
        Double amount = 0D;
        if (loan.get(CrifConstants.DISBURSED_AMT) != null) {
            amount = Double.valueOf(easyLoanUtil.getNodeValueAsString(loan.get(CrifConstants.DISBURSED_AMT)).replace(",", ""));
        }
        if (loan.get(CrifConstants.CREDIT_LIMIT) != null) {
            amount = Math.max(Double.valueOf(easyLoanUtil.getNodeValueAsString(loan.get(CrifConstants.CREDIT_LIMIT)).replace(",", "")), amount);
        }
        return amount;
    }

    private boolean isLoanClosedWithinOneYear(JsonNode loan) {
        String date = (loan.has(CrifConstants.CLOSED_DATE) && !loan.get(CrifConstants.CLOSED_DATE).isNull()
                && !loan.get(CrifConstants.CLOSED_DATE).asText().equalsIgnoreCase(""))
                ? loan.get(CrifConstants.CLOSED_DATE).asText()
                : null;
        if (date != null) {
            try {
                Date closingDate = dateFormat.parse(date);
                Date today = new Date();
                return ((today.getTime() - closingDate.getTime()) / 1000) <= 31556952;
            } catch (Exception e) {
                logger.error("Error occured while checking for loan closing duration", e);
            }
        }
        return false;
    }

    private String getLoanType(String loanType) {
        if (loanType == null || loanType.isEmpty()) {
            return "Other";
        }
        loanType = loanType.toLowerCase();
        if (loanTypeAL.contains(loanType)) {
            return "AL";
        } else if (loanTypePL.contains(loanType)) {
            return "PL";
        } else if (loanTypeHL.contains(loanType)) {
            return "HL";
        } else if (loanTypeBL.contains(loanType)) {
            return "BL";
        } else if (loanTypeCC.contains(loanType)) {
            return "CC";
        } else if (loanTypeTW.contains(loanType)) {
            return "TW";
        } else if (loanTypeCD.contains(loanType)) {
            return "CD";
        } else if (loanTypeGL.contains(loanType)) {
            return "GL";
        } else {
            return "Other";
        }
    }

    private Map<String, Double> getDebtAndIncome(JsonNode loan) {
        Map<String, Double> debtAndIncome = new HashMap<>();
        double debt = 0D;
        double income = 0D;
        if (loan == null || loan.get(CrifConstants.ACCT_TYPE) == null
                || loan.get(CrifConstants.ACCT_TYPE).toString().equalsIgnoreCase("\"\"")) {
            debtAndIncome.put("debt", debt);
            debtAndIncome.put("income", income);
            return debtAndIncome;
        }
        double loanAmount = getLoanAmount(loan);
        boolean isLoanClosed = isLoanClosed(loan);
        boolean isLoanClosedWithinAYear = isLoanClosedWithinOneYear(loan);
        String loanType = getLoanType(easyLoanUtil.getNodeValueAsString(loan.get(CrifConstants.ACCT_TYPE)));
        if (loanAmount >= 5000 && (!isLoanClosed || isLoanClosedWithinAYear)) {
            if (!isLoanClosedWithinAYear) {
                debt += loanAmount * CreditConstants.EMI.get(loanType);
            }
            income += loanAmount * CreditConstants.EMI.get(loanType) / CreditConstants.DBI.get(loanType);
            if (income < CreditConstants.OTHER_INCOME.getOrDefault(loanType, 0D)) {
                income = CreditConstants.OTHER_INCOME.getOrDefault(loanType, 0D);
            }
            logger.info(
                    "loanStatus: {}, loanAmount:{}, isLoanClosed: {}, isLoanClosedWithinAYear: {}, loanType: {}, income:{}, debt:{}",
                    loan.get(CrifConstants.ACCT_STATUS), loanAmount, isLoanClosed, isLoanClosedWithinAYear, loanType,
                    income, debt);
        }
        debtAndIncome.put("debt", debt);
        debtAndIncome.put("income", income);
        return debtAndIncome;
    }

    private Map<String, Double> getDebtAndIncome(ArrayNode loanDetails) {
        Map<String, Double> debtAndIncome = new HashMap<>();
        Map<String, Double> incomeMap = new HashMap<>();
        double debt = 0D;
        for (JsonNode loan : loanDetails) {
            loan = loan.get(CrifConstants.LOAN_DETAILS);
            if (loan == null || loan.get(CrifConstants.ACCT_TYPE) == null
                    || loan.get(CrifConstants.ACCT_TYPE).toString().equalsIgnoreCase("\"\"")) {
                continue;
            }
            double loanAmount = getLoanAmount(loan);
            boolean isLoanClosed = isLoanClosed(loan);
            boolean isLoanClosedWithinAYear = isLoanClosedWithinOneYear(loan);
            String loanType = getLoanType(easyLoanUtil.getNodeValueAsString(loan.get(CrifConstants.ACCT_TYPE)));
            if (loanAmount >= 5000 && (!isLoanClosed || isLoanClosedWithinAYear)) {
                double income = loanAmount * CreditConstants.EMI.get(loanType) / CreditConstants.DBI.get(loanType);
                incomeMap.put(loanType, incomeMap.getOrDefault(loanType, 0D) + income);
                if (!isLoanClosedWithinAYear) {
                    debt += loanAmount * CreditConstants.EMI.get(loanType);
                }
                logger.info(
                        "loanStatus: {}, loanAmount:{}, isLoanClosed: {}, isLoanClosedWithinAYear: {}, loanType: {}, income:{}, debt:{}",
                        loan.get(CrifConstants.ACCT_STATUS), loanAmount, isLoanClosed, isLoanClosedWithinAYear, loanType,
                        income, debt);
            }
        }
        double totalIncome = 0d;
        for (String loanType : incomeMap.keySet()) {
            totalIncome += Math.max(incomeMap.get(loanType), CreditConstants.OTHER_INCOME.getOrDefault(loanType, 0D));
        }
        debtAndIncome.put("debt", debt);
        debtAndIncome.put("income", totalIncome);
        return debtAndIncome;
    }

    private int countDPDLastXmonths(JsonNode jsonNode, int months, Date reportDate) {
        Date dateReported = null;
        try {
            JsonNode dateRep = jsonNode.get(CrifConstants.DATE_REPORTED);
            if (dateRep != null && !dateRep.toString().equalsIgnoreCase("\"\"")) {
                dateReported = dateFormat.parse(dateRep.asText());
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
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMM");
        for (int i = 0; i < months; i++) {
            month = monthFormat.format(c.getTime());
            monthYear.add(month + ":" + c.get(Calendar.YEAR));// Jan:2020
            c.add(Calendar.MONTH, -1);
        }
        JsonNode paymentHistory = jsonNode.get("COMBINED-PAYMENT-HISTORY");
        int dpdCount = 0;
        if (paymentHistory != null && !paymentHistory.toString().equalsIgnoreCase("\"\"")) {
            List<String> loanHistory = Arrays.asList(paymentHistory.asText().split("\\|"));
            for (String monthNode : loanHistory) {
                String date = monthNode.split(",")[0];
                String code1 = monthNode.split(",")[1].split("/")[0];
                String code2 = monthNode.split(",")[1].split("/")[1];
                if (monthYear.contains(date)) {
                    int loanDpd = (delinquentDPDStatus.contains(code2)) ? 90 : codeToCount(code1);
                    if (loanDpd > 5) {
                        dpdCount++;
                    }
                }
            }
        }
        return dpdCount;
    }

    private int codeToCount(String code) {
        try {
            return Integer.parseInt(code);
        } catch (Exception e) {
            logger.info("Bad DPD Count String Exception");
            return 0;
        }
    }

    private int loanSanctioned3mon(JsonNode jsonNode, Date reportDate) throws ParseException {
        if (jsonNode.get(CrifConstants.DISBURSED_DT) != null
                && !jsonNode.get(CrifConstants.DISBURSED_DT).toString().equalsIgnoreCase("\"\"")) {
            Date openDate = dateFormat.parse(jsonNode.get(CrifConstants.DISBURSED_DT).asText());
            return LoanUtil.getDateDiffInDays(openDate, reportDate) <= 90 ? 1 : 0;
        }
        return 0;
    }

    private int unsecuredLoan6mon(JsonNode jsonNode, Date reportDate) throws ParseException {
        JsonNode acctType = jsonNode.get(CrifConstants.ACCT_TYPE);
        JsonNode openDt = jsonNode.get(CrifConstants.DISBURSED_DT);
        if (acctType != null && openDt != null && !openDt.toString().equalsIgnoreCase("\"\"")) {
            Date openDate = dateFormat.parse(openDt.asText());
            return LoanUtil.getDateDiffInDays(openDate, reportDate) <= 180
                    && unsecuredLoanTypes.contains(acctType.asText().toLowerCase()) ? 1 : 0;
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
        Set<String> loanTypes = new HashSet<>();
        JsonNode loanDetails = response.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES)
                .get(CrifConstants.RESPONSE);
        if (loanDetails != null && loanDetails.isObject()) {
            loanDetails = loanDetails.get(CrifConstants.LOAN_DETAILS);
            JsonNode acctType = loanDetails.get(CrifConstants.ACCT_TYPE);
            JsonNode openDt = loanDetails.get(CrifConstants.DISBURSED_DT);
            debtAndIncome = getDebtAndIncome(loanDetails);
            delinquencyCount6mon += countDPDLastXmonths(loanDetails, 6, reportDate);
            loanSanctioned3mon += loanSanctioned3mon(loanDetails, reportDate);
            unsecuredLoanCount6mon += unsecuredLoan6mon(loanDetails, reportDate);
            loanTypes.add(getLoanType(easyLoanUtil.getNodeValueAsString(acctType)));
            if (openDt != null && !openDt.toString().equalsIgnoreCase("\"\"")) {
                Date openDate = dateFormat.parse(openDt.asText());
                if (openDate.before(minOpenDate)) {
                    minOpenDate = openDate;
                }
            }
        } else if (loanDetails != null && loanDetails.isArray()) {
            debtAndIncome = getDebtAndIncome((ArrayNode) loanDetails);
            for (JsonNode detail : loanDetails) {
                detail = detail.get(CrifConstants.LOAN_DETAILS);
                JsonNode acctType = detail.get(CrifConstants.ACCT_TYPE);
                JsonNode openDt = detail.get(CrifConstants.DISBURSED_DT);
                delinquencyCount6mon += countDPDLastXmonths(detail, 6, reportDate);
                loanSanctioned3mon += loanSanctioned3mon(detail, reportDate);
                unsecuredLoanCount6mon += unsecuredLoan6mon(detail, reportDate);
                loanTypes.add(getLoanType(easyLoanUtil.getNodeValueAsString(acctType)));
                if (openDt != null && !openDt.toString().equalsIgnoreCase("\"\"")) {
                    Date openDate = dateFormat.parse(openDt.asText());
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


    public CreditScoreReportDetailDTO.CreditCardUtilization getCreditCardUtilization(JsonNode beruaeResponse){

        try{
            boolean responseCheck = Objects.nonNull(beruaeResponse.get(CrifConstants.REPORT_HEADER)) && Objects.nonNull(beruaeResponse.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES)) && Objects.nonNull(beruaeResponse.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES).get(CrifConstants.RESPONSE));
            int cardLimit = 0;
            int currentBalance = 0;
            int totalUtilization = 0;
            String impact = null;

            if(responseCheck){
                JsonNode responses = beruaeResponse.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES).get(CrifConstants.RESPONSE);

                if(responses.isObject() && (easyLoanUtil.getNodeValueAsString(responses.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.ACCT_TYPE)).equals("Credit Card") || easyLoanUtil.getNodeValueAsString(responses.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.ACCT_TYPE)).equals("Corporate Credit Card")) && !isLoanClosed(responses.get(CrifConstants.LOAN_DETAILS))){
                    if(Objects.nonNull(responses.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.CREDIT_LIMIT))){
                        cardLimit += Math.max(Integer.parseInt(responses.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.CREDIT_LIMIT).asText().replace(",", "")), 0);
                    }else if(Objects.nonNull(responses.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.DISBURSED_AMT))){
                        cardLimit += Math.max(Integer.parseInt(responses.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.DISBURSED_AMT).asText().replace(",", "")), 0);
                    }
                    if(Objects.nonNull(responses.get(CrifConstants.LOAN_DETAILS).get("CURRENT-BAL"))){
                        currentBalance += Math.max(Integer.parseInt(responses.get(CrifConstants.LOAN_DETAILS).get("CURRENT-BAL").asText().replace(",", "")), 0);
                    }
                }else if(responses.isArray()){
                    for(JsonNode response: responses){
                        if(Objects.nonNull(response.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.ACCT_TYPE)) && (easyLoanUtil.getNodeValueAsString(response.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.ACCT_TYPE)).equals("Credit Card") || easyLoanUtil.getNodeValueAsString(response.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.ACCT_TYPE)).equals("Corporate Credit Card") && !isLoanClosed(response.get(CrifConstants.LOAN_DETAILS)))){
                            if(Objects.nonNull(response.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.CREDIT_LIMIT))){
                                cardLimit += Math.max(Integer.parseInt(response.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.CREDIT_LIMIT).asText().replace(",", "")), 0);
                            }else if(Objects.nonNull(response.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.DISBURSED_AMT))){
                                cardLimit += Math.max(Integer.parseInt(response.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.DISBURSED_AMT).asText().replace(",", "")), 0);
                            }
                            if(Objects.nonNull(response.get(CrifConstants.LOAN_DETAILS).get("CURRENT-BAL"))){
                                currentBalance += Math.max(Integer.parseInt(response.get(CrifConstants.LOAN_DETAILS).get("CURRENT-BAL").asText().replace(",", "")), 0);
                            }
                        }
                    }
                }
            }

            if(cardLimit !=0){
                totalUtilization = (currentBalance * 100)/cardLimit;
            }

            if(totalUtilization < 25){
                impact = "excellent";
            }else if(totalUtilization < 75){
                impact = "average";
            }else {
                impact = "bad";
            }
            CreditScoreReportDetailDTO creditScoreReportDetailDTO = new CreditScoreReportDetailDTO();
            CreditScoreReportDetailDTO.CreditCardUtilization creditCardUtilization = creditScoreReportDetailDTO.new CreditCardUtilization();

            if(totalUtilization == 0 && currentBalance == 0 && cardLimit == 0){
                return null;
            }
            creditCardUtilization.setTotalUtilization(totalUtilization);
            creditCardUtilization.setCardUtilization(currentBalance);
            creditCardUtilization.setCardLimit(cardLimit);
            creditCardUtilization.setImpact(impact);

            return creditCardUtilization;
        }catch(Exception ex){
            logger.error("Error Occurred while calculating card utilization Error :{0}", ex);
        }
        return null;
    }

    public CreditScoreReportDetailDTO.PaymentHistory getPaymentHistory(JsonNode beruaeResponse){

        try{
            boolean responseCheck = Objects.nonNull(beruaeResponse.get(CrifConstants.REPORT_HEADER)) && Objects.nonNull(beruaeResponse.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES)) && Objects.nonNull(beruaeResponse.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES).get(CrifConstants.RESPONSE));
            int totalPayment = 0;
            int onTimePayment = 0;
            int deqPayment = 0;
            int timelyPayment = 0;
            String impact = null;

            if(responseCheck) {
                JsonNode responses = beruaeResponse.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES).get(CrifConstants.RESPONSE);

                if (responses.isObject() && Objects.nonNull(responses.get(CrifConstants.LOAN_DETAILS))) {
                    JsonNode paymentHistory = responses.get(CrifConstants.LOAN_DETAILS).get("COMBINED-PAYMENT-HISTORY");

                    if (paymentHistory != null && !paymentHistory.toString().equalsIgnoreCase("\"\"")) {
                        List<String> loanHistory = Arrays.asList(paymentHistory.asText().split("\\|"));
                        totalPayment = loanHistory.size();
                        for (String monthNode : loanHistory) {
                            String date = monthNode.split(",")[0];
                            String code1 = monthNode.split(",")[1].split("/")[0];
                            String code2 = monthNode.split(",")[1].split("/")[1];
                            if(delinquentDPDStatus.contains(code2)){
                                deqPayment+=1;
                            }
                        }
                        onTimePayment = totalPayment - deqPayment;
                    }
                }else if(responses.isArray()){
                    for(JsonNode response: responses){
                        JsonNode paymentHistory = response.get(CrifConstants.LOAN_DETAILS).get("COMBINED-PAYMENT-HISTORY");

                        if (paymentHistory != null && !paymentHistory.toString().equalsIgnoreCase("\"\"")) {
                            List<String> loanHistory = Arrays.asList(paymentHistory.asText().split("\\|"));
                            totalPayment += loanHistory.size();
                            for (String monthNode : loanHistory) {
                                String date = monthNode.split(",")[0];
                                String code1 = monthNode.split(",")[1].split("/")[0];
                                String code2 = monthNode.split(",")[1].split("/")[1];
                                if(delinquentDPDStatus.contains(code2)){
                                    deqPayment+=1;
                                }
                            }
                            onTimePayment += loanHistory.size() - deqPayment;
                        }
                    }
                }
            }

            if(totalPayment!=0){
                timelyPayment = (onTimePayment*100)/totalPayment;
            }

            if(timelyPayment > 90){
                impact = "excellent";
            }else if(timelyPayment > 50 && timelyPayment <= 90){
                impact = "average";
            }else if(timelyPayment <= 50){
                impact = "bad";
            }

            CreditScoreReportDetailDTO creditScoreReportDetailDTO = new CreditScoreReportDetailDTO();
            CreditScoreReportDetailDTO.PaymentHistory paymentHistory = creditScoreReportDetailDTO.new PaymentHistory();

            if(totalPayment == 0){
                return null;
            }

            paymentHistory.setTotalPayment(totalPayment);
            paymentHistory.setOntimePayment(onTimePayment);
            // NEED TO CHECK WHEN NO PAYMENT HISTORY
            paymentHistory.setTimelyPayment(timelyPayment);
            paymentHistory.setImpact(impact);

            return paymentHistory;
        }catch (Exception ex){
            logger.error("Error Occurred while checking payment history Error :{0}", ex);
        }
        return null;
    }

    public CreditScoreReportDetailDTO.AgeOfAccount getAgeOfAccount(JsonNode beruaeResponse){

        try{
            boolean responseCheck = Objects.nonNull(beruaeResponse.get(CrifConstants.REPORT_HEADER)) && Objects.nonNull(beruaeResponse.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES)) && Objects.nonNull(beruaeResponse.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES).get(CrifConstants.RESPONSE));
            int averageAge = 0;
            int newestAccount = 0;
            int oldestAccount = 0;
            int currentDiff = 0;
            int totalAge = 0;
            String impact = null;

            if(responseCheck) {
                JsonNode responses = beruaeResponse.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES).get(CrifConstants.RESPONSE);

                if (responses.isObject() && Objects.nonNull(responses.get(CrifConstants.LOAN_DETAILS))) {
                    JsonNode loanDetail = responses.get(CrifConstants.LOAN_DETAILS);
                    JsonNode openDate = responses.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.DISBURSED_DT);
                    Date dateReported = null;
                    Date OpenDateType = null;
                    try {
                        if (loanDetail.get(CrifConstants.DATE_REPORTED) != null
                                && !loanDetail.get(CrifConstants.DATE_REPORTED).asText().equalsIgnoreCase("") && Objects.nonNull(openDate) && !openDate.asText().equalsIgnoreCase("")) {
                            dateReported = dateFormat.parse(loanDetail.get(CrifConstants.DATE_REPORTED).asText());
                            OpenDateType = dateFormat.parse(openDate.asText());

                            averageAge = (int)LoanUtil.getDateDiffInDays(OpenDateType, dateReported) / 365 ;
                            newestAccount = averageAge;
                            oldestAccount = averageAge;
                        }
                    } catch (Exception e) {
                        logger.error("Exception:", e);
                    }
                }else if(responses.isArray()){
                    int total = responses.size();
                    for(JsonNode response : responses){
                        JsonNode loanDetail = response.get(CrifConstants.LOAN_DETAILS);
                        JsonNode openDate = response.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.DISBURSED_DT);
                        Date dateReported = null;
                        Date OpenDateType = null;
                        try {
                            if (loanDetail.get(CrifConstants.DATE_REPORTED) != null
                                    && !loanDetail.get(CrifConstants.DATE_REPORTED).asText().equalsIgnoreCase("") && Objects.nonNull(openDate) && !openDate.asText().equalsIgnoreCase("")) {
                                dateReported = dateFormat.parse(loanDetail.get(CrifConstants.DATE_REPORTED).asText());
                                OpenDateType = dateFormat.parse(openDate.asText());

                                currentDiff = (int)LoanUtil.getDateDiffInDays(OpenDateType, dateReported) / 365 ;
                                newestAccount = Math.min(newestAccount == 0 ? Integer.MAX_VALUE : newestAccount , currentDiff);
                                oldestAccount = Math.max( oldestAccount, currentDiff);
                                totalAge += currentDiff;
                            }
                        } catch (Exception e) {
                            logger.error("Exception:", e);
                        }
                    }
                    if(total!= 0){
                        averageAge = totalAge/total;
                    }
                }
            }

            if(averageAge < 1){
                impact = "average";
            }else {
                impact = "excellent";
            }
            CreditScoreReportDetailDTO creditScoreReportDetailDTO = new CreditScoreReportDetailDTO();
            CreditScoreReportDetailDTO.AgeOfAccount ageOfAccount = creditScoreReportDetailDTO.new AgeOfAccount();

            ageOfAccount.setNewestAccount(newestAccount);
            ageOfAccount.setOldestAccount(oldestAccount);
            ageOfAccount.setAverageAge(averageAge);
            ageOfAccount.setImpact(impact);
            return ageOfAccount;
        }catch (Exception ex){
            logger.error("Error Occurred while checking age of account Error :{0}", ex);
        }
        return null;
    }

    public CreditScoreReportDetailDTO.TotalAccount getTotalAccount(JsonNode beruaeResponse){

        try{
            boolean responseCheck = Objects.nonNull(beruaeResponse.get(CrifConstants.REPORT_HEADER)) && Objects.nonNull(beruaeResponse.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES)) && Objects.nonNull(beruaeResponse.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES).get(CrifConstants.RESPONSE));

            int totalNumAccount = 0;
            int activeAccount = 0;
            int closedAccount = 0;
            String impact = null;

            if(responseCheck){
                JsonNode responses = beruaeResponse.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES).get(CrifConstants.RESPONSE);
                if (responses.isObject() && Objects.nonNull(responses.get(CrifConstants.LOAN_DETAILS))) {
                    JsonNode loanDetail = responses.get(CrifConstants.LOAN_DETAILS);
                    if(isLoanClosed(loanDetail)){
                        closedAccount +=1;
                    }
                    totalNumAccount = closedAccount;
                }else if(responses.isArray()){
                    for(JsonNode response : responses){
                        JsonNode loanDetail = response.get(CrifConstants.LOAN_DETAILS);
                        if(isLoanClosed(loanDetail)){
                            closedAccount +=1;
                        }else{
                            activeAccount+=1;
                        }
                    }
                    totalNumAccount = closedAccount + activeAccount;
                }
            }

            if(activeAccount <= 10){
                impact = "excellent";
            }else if(activeAccount <= 20){
                impact = "average";
            }else {
                impact = "bad";
            }

            CreditScoreReportDetailDTO creditScoreReportDetailDTO = new CreditScoreReportDetailDTO();
            CreditScoreReportDetailDTO.TotalAccount totalAccount = creditScoreReportDetailDTO.new TotalAccount();

            if(totalNumAccount==0 && activeAccount==0 && closedAccount==0){
                return null;
            }

            totalAccount.setTotalAccount(totalNumAccount);
            totalAccount.setActiveAccount(activeAccount);
            totalAccount.setClosedAccount(closedAccount);
            totalAccount.setImpact(impact);
            return totalAccount;
        }catch(Exception ex){
            logger.error("Error Occurred while checking total account, Error :{0}", ex);

        }
        return null;
    }

    public CreditScoreReportDetailDTO.CreditEnquries getCreditEnquiries(JsonNode beruaeResponse){

        try{
            boolean creditEnquriesCheck = Objects.nonNull(beruaeResponse.get(CrifConstants.REPORT_HEADER)) && Objects.nonNull(beruaeResponse.get(CrifConstants.REPORT_HEADER).get("ACCOUNTS-SUMMARY"));

            int totalEnquries = 0;
            int creditCardEnquries= 0;
            int loanEnqueries = 0;
            String impact = null;

            if(creditEnquriesCheck) {
                JsonNode accountSummaries = beruaeResponse.get(CrifConstants.REPORT_HEADER).get("ACCOUNTS-SUMMARY");
                if (accountSummaries.isObject()) {
                    JsonNode enquiryReason = accountSummaries.get("DERIVED-ATTRIBUTES");
                    if (Objects.nonNull(enquiryReason)) {
                        creditCardEnquries += enquiryReason.get("INQURIES-IN-LAST-SIX-MONTHS").asLong();
                    }
                } else if (accountSummaries.isArray()) {
                    for (JsonNode accountSummary : accountSummaries) {
                        JsonNode enquiryReason = accountSummaries.get("DERIVED-ATTRIBUTES");
                        if (Objects.nonNull(enquiryReason)) {
                            creditCardEnquries += enquiryReason.get("INQURIES-IN-LAST-SIX-MONTHS").asLong();
                        }
                    }
                }
                totalEnquries = creditCardEnquries + loanEnqueries;
            }

            if(totalEnquries <= 2){
                impact = "excellent";
            }else if(totalEnquries > 3 && totalEnquries <= 6){
                impact = "average";
            }else if(totalEnquries >= 7){
                impact = "bad";
            }

            CreditScoreReportDetailDTO creditScoreReportDetailDTO = new CreditScoreReportDetailDTO();
            CreditScoreReportDetailDTO.CreditEnquries creditEnquries = creditScoreReportDetailDTO.new CreditEnquries();

            creditEnquries.setTotalEnquiries(totalEnquries);
            creditEnquries.setLoanEnquiries(loanEnqueries);
            creditEnquries.setCreditCardEnquiries(creditCardEnquries);
            creditEnquries.setImpact(impact);
            return creditEnquries;
        }catch(Exception ex){
            logger.error("Error Occurred while checking total enquries, Error :{0}", ex);

        }
        return null;

    }

    public String getExperianNumber(JsonNode beruaeResponse){
        String experianNumber = null;
        boolean experianHeaderDetails = Objects.nonNull(beruaeResponse.get("B2C-REPORT")) && Objects.nonNull(beruaeResponse.get("B2C-REPORT").get("HEADER"));
        if(experianHeaderDetails){
            JsonNode headerDetails = beruaeResponse.get("B2C-REPORT").get("HEADER");
            if(Objects.nonNull(headerDetails.get("REPORT-ID"))){
                experianNumber = headerDetails.get("REPORT-ID").asText();
            }
        }

        return experianNumber;
    }


    public LoanAndCreditCardDetailDTO getLoanAndCreditDetail(JsonNode beruaeResponse, BasicDetailsDto merchant){
        try{
            boolean responseCheck = Objects.nonNull(beruaeResponse.get(CrifConstants.REPORT_HEADER)) && Objects.nonNull(beruaeResponse.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES)) && Objects.nonNull(beruaeResponse.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES).get(CrifConstants.RESPONSE));


            List<LoanAndCreditCardDetailDTO.CreditCardDetail> creditCardDetails = new ArrayList<>();
            List<LoanAndCreditCardDetailDTO.LoanDetail> loanDetails = new ArrayList<>();
            LoanAndCreditCardDetailDTO loanAndCreditCardDetailDTO = new LoanAndCreditCardDetailDTO();

            if(responseCheck) {
                JsonNode responses = beruaeResponse.get(CrifConstants.REPORT_HEADER).get(CrifConstants.RESPONSES).get(CrifConstants.RESPONSE);
                if(responses.isObject()){
                    JsonNode loan = responses.get(CrifConstants.LOAN_DETAILS);
                    LoanAndCreditCardDetailDTO.CreditCardDetail creditCardDetail = loanAndCreditCardDetailDTO.new CreditCardDetail();
                    LoanAndCreditCardDetailDTO.LoanDetail loanDetail = loanAndCreditCardDetailDTO.new LoanDetail();
                    if(Objects.nonNull(loan) && (easyLoanUtil.getNodeValueAsString(loan.get(CrifConstants.ACCT_TYPE)).equals("Credit Card") || easyLoanUtil.getNodeValueAsString(loan.get(CrifConstants.ACCT_TYPE)).equals("Corporate Credit Card"))){
                        creditCardDetail.setBankName(easyLoanUtil.getNodeValueAsString(loan.get("CREDIT-GUARANTOR")));
                        creditCardDetail.setStatus(!isLoanClosed(loan));
                        creditCardDetail.setCreditCardNumber(easyLoanUtil.getNodeValueAsString(loan.get("ACCT-NUMBER")));
                        if(Objects.nonNull(loan.get("CURRENT-BAL"))) {
                            creditCardDetail.setBalance(Math.max(Integer.parseInt(easyLoanUtil.getNodeValueAsString(loan.get("CURRENT-BAL")).replace(",", "")), 0));
                        }
                        if(Objects.nonNull(responses.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.CREDIT_LIMIT))){
                            creditCardDetail.setCardLimit(Math.max(Integer.parseInt(responses.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.CREDIT_LIMIT).asText().replace(",", "")), 0));
                        }else if(Objects.nonNull(responses.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.DISBURSED_AMT))){
                            creditCardDetail.setCardLimit(Math.max(Integer.parseInt(responses.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.DISBURSED_AMT).asText().replace(",", "")), 0));
                        }
                        creditCardDetails.add(creditCardDetail);
                    }else{
                        loanDetail.setAccountNumber(easyLoanUtil.getNodeValueAsString(loan.get("ACCT-NUMBER")));
                        loanDetail.setBankName(easyLoanUtil.getNodeValueAsString(loan.get("CREDIT-GUARANTOR")));
                        loanDetail.setStatus(!isLoanClosed(loan));
                        if(Objects.nonNull(responses.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.DISBURSED_AMT))){
                            loanDetail.setSanctionedAmount(Integer.parseInt(responses.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.DISBURSED_AMT).asText().replace(",", "")));
                        }
                        if(Objects.nonNull(loan.get("ORIGINAL-TERM"))){
                            loanDetail.setTenure(loan.get("ORIGINAL-TERM").asText());
                        }
                        loanDetail.setCurrentBalance(easyLoanUtil.getNodeValueAsString(loan.get("CURRENT-BAL")));
                        if(Objects.nonNull(loan.get("INTEREST-RATE"))) {
                            loanDetail.setRateOfInterest(loan.get("INTEREST-RATE").asText());
                        }
                        loanDetails.add(loanDetail);
                    }
                }else if(responses.isArray()){
                    for (JsonNode response: responses) {
                        JsonNode loan = response.get(CrifConstants.LOAN_DETAILS);
                        LoanAndCreditCardDetailDTO.CreditCardDetail creditCardDetail = loanAndCreditCardDetailDTO.new CreditCardDetail();
                        LoanAndCreditCardDetailDTO.LoanDetail loanDetail = loanAndCreditCardDetailDTO.new LoanDetail();
                        if(Objects.nonNull(loan) && (easyLoanUtil.getNodeValueAsString(loan.get(CrifConstants.ACCT_TYPE)).equals("Credit Card") || easyLoanUtil.getNodeValueAsString(loan.get(CrifConstants.ACCT_TYPE)).equals("Corporate Credit Card"))){
                            creditCardDetail.setBankName(easyLoanUtil.getNodeValueAsString(loan.get("CREDIT-GUARANTOR")));
                            creditCardDetail.setStatus(!isLoanClosed(loan));
                            creditCardDetail.setCreditCardNumber(easyLoanUtil.getNodeValueAsString(loan.get("ACCT-NUMBER")));
                            if(Objects.nonNull(loan.get("CURRENT-BAL"))) {
                                creditCardDetail.setBalance(Math.max(Integer.parseInt(loan.get("CURRENT-BAL").asText().replace(",", "")), 0));
                            }
                            if(Objects.nonNull(response.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.CREDIT_LIMIT))){
                                creditCardDetail.setCardLimit(Math.max(Integer.parseInt(response.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.CREDIT_LIMIT).asText().replace(",", "")), 0));
                            }else if(Objects.nonNull(response.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.DISBURSED_AMT))){
                                creditCardDetail.setCardLimit(Math.max(Integer.parseInt(response.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.DISBURSED_AMT).asText().replace(",", "")), 0));
                            }

                            creditCardDetails.add(creditCardDetail);
                        }else{
                            loanDetail.setAccountNumber(easyLoanUtil.getNodeValueAsString(loan.get("ACCT-NUMBER")));
                            loanDetail.setBankName(easyLoanUtil.getNodeValueAsString(loan.get("CREDIT-GUARANTOR")));
                            if(Objects.nonNull(response.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.DISBURSED_AMT))){
                                loanDetail.setSanctionedAmount(Integer.parseInt(response.get(CrifConstants.LOAN_DETAILS).get(CrifConstants.DISBURSED_AMT).asText().replace(",", "")));
                            }
                            if(Objects.nonNull(loan.get("ORIGINAL-TERM"))){
                                loanDetail.setTenure(loan.get("ORIGINAL-TERM").asText());
                            }
                            loanDetail.setStatus(!isLoanClosed(loan));
                            loanDetail.setCurrentBalance(easyLoanUtil.getNodeValueAsString(loan.get("CURRENT-BAL")));
                            if(Objects.nonNull(loan.get("INTEREST-RATE"))){
                                loanDetail.setRateOfInterest(loan.get("INTEREST-RATE").asText());
                            }
                            loanDetails.add(loanDetail);
                        }

                    }
                }
            }

            loanAndCreditCardDetailDTO.setLoanDetail(loanDetails);
            loanAndCreditCardDetailDTO.setCreditCardDetail(creditCardDetails);
            loanAndCreditCardDetailDTO.setExperianNumber(getExperianNumber(beruaeResponse));
            return loanAndCreditCardDetailDTO;
        }catch(Exception ex){
            logger.error("Error Occurred while checking loan and credit card details for merchantId : {}, Error : {}", merchant.getId(), ex.getMessage());
        }
        return null;

    }

    public CreditScoreReportDetailDTO getCreditDetailReport(JsonNode beruaeResponse){

        CreditScoreReportDetailDTO creditScoreReportDetailDTO = new CreditScoreReportDetailDTO();
        try{
            CreditScoreReportDetailDTO.CreditCardUtilization creditCardUtilization = getCreditCardUtilization(beruaeResponse);
            CreditScoreReportDetailDTO.PaymentHistory paymentHistory = getPaymentHistory(beruaeResponse);
            CreditScoreReportDetailDTO.AgeOfAccount ageOfAccount = getAgeOfAccount(beruaeResponse);
            CreditScoreReportDetailDTO.TotalAccount totalAccount = getTotalAccount(beruaeResponse);
            CreditScoreReportDetailDTO.CreditEnquries creditEnquries= getCreditEnquiries(beruaeResponse);
            creditScoreReportDetailDTO.setCreditEnquries(creditEnquries);
            creditScoreReportDetailDTO.setCreditCardUtilization(creditCardUtilization);
            creditScoreReportDetailDTO.setAgeOfAccount(ageOfAccount);
            creditScoreReportDetailDTO.setTotalAccount(totalAccount);
            creditScoreReportDetailDTO.setPaymentHistory(paymentHistory);
            creditScoreReportDetailDTO.setExperianNumber(getExperianNumber(beruaeResponse));
        }catch(Exception ex){
            logger.error("Error Occurred , Error :{0}", ex);

        }

        return creditScoreReportDetailDTO;
    }

//    public Object getLoanAddress(JsonNode beruaeMap){
//
//        try{
//
//        }catch(Exception ex){
//            logger.error("Error Occurred while checking loan address from experian, Error :{0}", ex);
//        }
//
//        return null;
//    }
}