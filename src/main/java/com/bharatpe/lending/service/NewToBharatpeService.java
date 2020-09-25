package com.bharatpe.lending.service;

import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.PaymentTransactionNewDao;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.LendingCategories;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.PaymentTransactionNew;
import com.bharatpe.lending.common.dao.LendingBBSAuditDao;
import com.bharatpe.lending.common.dao.LendingBBSDao;
import com.bharatpe.lending.common.entity.LendingBBS;
import com.bharatpe.lending.common.entity.LendingBBSAudit;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dto.LoanEligibilityDTO;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class NewToBharatpeService {

    private final Logger logger = LoggerFactory.getLogger(NewToBharatpeService.class);

    @Autowired
    LendingBBSDao lendingBBSDao;

    @Autowired
	LendingBBSAuditDao lendingBBSAuditDao;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
	ExperianDao experianDao;

    @Autowired
	LendingCategoryDao lendingCategoryDao;

    @Autowired
	LoanEligibleService loanEligibleService;

    @Autowired
	EligibleLoanDao eligibleLoanDao;

    @Autowired
	PaymentTransactionNewDao paymentTransactionNewDao;

    SimpleDateFormat experianFormat = new SimpleDateFormat("yyyyMMdd");

    List<Integer> unsecuredLoan = Arrays.asList(0,5,6,8,9,10,11,12,14,16,18,19,20,31,35,36,37,38,39,43,51,52,53,54,55,56,57,58,61);

	List<Integer> activeStatusList=Arrays.asList(11, 21, 22, 23, 24, 25, 71, 78, 80, 82, 83, 84);

    private static final double LOAN_ENQUIRY_WEIGHT = 0.2;
    private static final double DELINQUENCY_WEIGHT = 0.2;
    private static final double LOAN_COUNT_WEIGHT = 0.2;
    private static final double LOAN_TYPE_WEIGHT = 0.2;
    private static final double UNSECURED_LOAN_WEIGHT = 0.1;
    private static final double CREDIT_HISTORY_WEIGHT = 0.1;
    private static final double BBS_MULTIPLIER = 300;

    public List<LoanEligibilityDTO> fetchBBSLoans(Merchant merchant, Experian experian, boolean yellowPincode) {
		logger.info("Fetching NTB loans for merchant:{}", experian.getMerchantId());
        if (experian.getResponse() == null) {
            logger.info("Merchant:{} not eligible for BBS", merchant.getId());
            return new ArrayList<>();
        }
        try {
        	LendingBBS lendingBBS = lendingBBSDao.findByMerchantId(merchant.getId());
        	if (lendingBBS == null || LoanUtil.getDateDiffInDays(lendingBBS.getCreatedAt(), new Date()) > 45) {
        		lendingBBS = calculateBBS(experian);
			}
			logger.info("BBS:{} for merchant:{}", lendingBBS.getBbs(), experian.getMerchantId());
        	if (!baseChecks(lendingBBS, merchant, experian, yellowPincode)) {
				logger.info("Base Checks Failed, so rejecting merchant: {}", merchant.getId());
				return new ArrayList<>();
			}
			return getBBSLoans(merchant, experian, lendingBBS, yellowPincode);
        } catch (Exception e) {
            logger.error("Exception in BBS---", e);
            return new ArrayList<>();
        }
    }

    private boolean baseChecks(LendingBBS lendingBBS, Merchant merchant, Experian experian, boolean yellowPincode) {
		if (lendingBBS.getBbs() < 500) {
			logger.info("BBS less than 500, rejecting merchant:{}", merchant.getId());
			experian.setCategory("1N");
			experian.setColor(ExperianConstants.COLOR.RED.name());
			experian.setReason(ExperianConstants.LOW_BBS);
			experianDao.save(experian);
			return false;
		}
		if (checkVintage(merchant) < 30 && ((yellowPincode && lendingBBS.getBbs() < 700) || (!yellowPincode && lendingBBS.getBbs() < 650))) {
			logger.info("Low BBS Vintage, rejecting merchant:{}", merchant.getId());
			experian.setCategory("1N");
			experian.setColor(ExperianConstants.COLOR.RED.name());
			experian.setReason(ExperianConstants.LOW_BBS_VINTAGE);
			experianDao.save(experian);
			return false;
		}
		if (checkVintage(merchant) < 60 && ((yellowPincode && lendingBBS.getBbs() < 650) || (!yellowPincode && lendingBBS.getBbs() < 600))) {
			logger.info("Low BBS Vintage, rejecting merchant:{}", merchant.getId());
			experian.setCategory("1N");
			experian.setColor(ExperianConstants.COLOR.RED.name());
			experian.setReason(ExperianConstants.LOW_BBS_VINTAGE);
			experianDao.save(experian);
			return false;
		}
		if (checkVintage(merchant) < 90 && ((yellowPincode && lendingBBS.getBbs() < 600) || (!yellowPincode && lendingBBS.getBbs() < 550))) {
			logger.info("Low BBS Vintage, rejecting merchant:{}", merchant.getId());
			experian.setCategory("1N");
			experian.setColor(ExperianConstants.COLOR.RED.name());
			experian.setReason(ExperianConstants.LOW_BBS_VINTAGE);
			experianDao.save(experian);
			return false;
		}
		if (checkVintage(merchant) >= 90 && ((yellowPincode && lendingBBS.getBbs() < 550) || (!yellowPincode && lendingBBS.getBbs() < 500))) {
			logger.info("Low BBS Vintage, rejecting merchant:{}", merchant.getId());
			experian.setCategory("1N");
			experian.setColor(ExperianConstants.COLOR.RED.name());
			experian.setReason(ExperianConstants.LOW_BBS_VINTAGE);
			experianDao.save(experian);
			return false;
		}
		return true;
	}

    private long checkVintage(Merchant merchant) {
		PaymentTransactionNew firstTransaction = paymentTransactionNewDao.getFirstTransaction(merchant.getId());
		if (firstTransaction != null) {
			return LoanUtil.getDateDiffInDays(firstTransaction.getCreatedAt(), new Date());
		} else {
			return LoanUtil.getDateDiffInDays(merchant.getCreatedAt(), new Date());
		}
	}

    private LendingBBS calculateBBS(Experian experian) throws IOException, ParseException {
		logger.info("Calculating BBS for merchant:{}", experian.getMerchantId());
        JsonNode experianResponse = objectMapper.readTree(experian.getResponse());
        Date reportDate = experianFormat.parse(experianResponse.get("INProfileResponse").get("CreditProfileHeader").get("ReportDate").asText());
        Set<Integer> loanTypes = new HashSet<>();
        int loanEnquires3mon = checkLoanEnquiriesInLast3Months(experianResponse);
        int delinquencyCount6mon = 0;
        int loanSanctioned3mon = 0;
        int unsecuredLoanCount6mon = 0;
        Date minOpenDate = reportDate;
        if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isObject()) {
            JsonNode caisAccountDetails = experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS");
            delinquencyCount6mon += checkDPDLastXmonths(caisAccountDetails, 6, reportDate);
            loanSanctioned3mon += loanSanctioned3mon(caisAccountDetails, reportDate);
            unsecuredLoanCount6mon += unsecuredLoan6mon(caisAccountDetails, reportDate);
            if (caisAccountDetails.get("Account_Type") != null) {
                loanTypes.add(caisAccountDetails.get("Account_Type").asInt());
            }
            if (caisAccountDetails.get("Open_Date") != null && !caisAccountDetails.get("Open_Date").asText().trim().equals("")) {
                Date openDate = experianFormat.parse(caisAccountDetails.get("Open_Date").asText());
                if (openDate.before(minOpenDate)) {
                    minOpenDate = openDate;
                }
            } else if (caisAccountDetails.get("DateOfAddition") != null && !caisAccountDetails.get("DateOfAddition").asText().trim().equals("")) {
                Date openDate = experianFormat.parse(caisAccountDetails.get("DateOfAddition").asText());
                if (openDate.before(minOpenDate)) {
                    minOpenDate = openDate;
                }
            }
        } else if (experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS") != null && experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS").isArray()) {
            for (JsonNode caisAccountDetails : experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS")) {
                delinquencyCount6mon += checkDPDLastXmonths(caisAccountDetails, 6, reportDate);
                loanSanctioned3mon += loanSanctioned3mon(caisAccountDetails, reportDate);
                unsecuredLoanCount6mon += unsecuredLoan6mon(caisAccountDetails, reportDate);
                if (caisAccountDetails.get("Account_Type") != null) {
                    loanTypes.add(caisAccountDetails.get("Account_Type").asInt());
                }
                if (caisAccountDetails.get("Open_Date") != null && !caisAccountDetails.get("Open_Date").asText().trim().equals("")) {
                    Date openDate = experianFormat.parse(caisAccountDetails.get("Open_Date").asText());
                    if (openDate.before(minOpenDate)) {
                        minOpenDate = openDate;
                    }
                } else if (caisAccountDetails.get("DateOfAddition") != null && !caisAccountDetails.get("DateOfAddition").asText().trim().equals("")) {
                    Date openDate = experianFormat.parse(caisAccountDetails.get("DateOfAddition").asText());
                    if (openDate.before(minOpenDate)) {
                        minOpenDate = openDate;
                    }
                }
            }
        }
        int typesOfLoan = loanTypes.size();
        double unsecuredLoanRatio6mon = unsecuredLoanCount6mon * 1.0 / 6;
        int creditHistory = (int) ChronoUnit.MONTHS.between(minOpenDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate(), reportDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        int loanEnquiries3monScore = loanEnquiresScore(loanEnquires3mon);
        int delinquencyCount6monScore = delinquencyScore(delinquencyCount6mon);
        int loanSanctioned3monScore = loanSanctionedScore(loanSanctioned3mon);
        int typesOfLoanScore = loanTypesScore(typesOfLoan);
        int unsecuredLoanRatio6monScore = unsecuredLoanScore(unsecuredLoanRatio6mon);
        int creditHistoryScore = historyScore(creditHistory);
        double bbs = ((LOAN_ENQUIRY_WEIGHT * loanEnquiries3monScore) + (DELINQUENCY_WEIGHT * delinquencyCount6monScore) + (LOAN_COUNT_WEIGHT * loanSanctioned3monScore)
                + (LOAN_TYPE_WEIGHT * typesOfLoanScore) + (UNSECURED_LOAN_WEIGHT * unsecuredLoanRatio6monScore) + (CREDIT_HISTORY_WEIGHT * creditHistoryScore)) * BBS_MULTIPLIER;
		logger.info("Calculating debt and income for merchant:{}", experian.getMerchantId());
        Map<String,Double> debtAndIncome = new HashMap<>();
		JsonNode loanDetails = experianResponse.get("INProfileResponse").get("CAIS_Account").get("CAIS_Account_DETAILS");
		if(loanDetails != null && loanDetails.isArray()) {
			debtAndIncome=getDebtAndIncome((ArrayNode)loanDetails);
		} else if (loanDetails != null) {
			debtAndIncome=getDebtAndIncome(loanDetails);
		}
		Double debt = debtAndIncome.getOrDefault("debt", 0D);
		Double income = debtAndIncome.getOrDefault("income", 0D);
		if(income < 10000) {
			income = debtAndIncome.getOrDefault("otherIncome", 0D);
		}
		double netFreeIncome = getNetFreeIncome(income, debt);
		double netFreeIncomePercent = income > 0 ? (netFreeIncome / income) * 100 : 0d;
		double extraPercent = netFreeIncomePercent - 10D;
		double amountToServe = income * (extraPercent/100);
        LendingBBS lendingBBS = new LendingBBS(experian.getMerchantId(), reportDate, loanEnquires3mon, loanEnquiries3monScore, delinquencyCount6mon, delinquencyCount6monScore, loanSanctioned3mon, loanSanctioned3monScore, typesOfLoan, typesOfLoanScore, unsecuredLoanRatio6mon, unsecuredLoanRatio6monScore, creditHistory, creditHistoryScore, bbs, debt, income, netFreeIncome, amountToServe);
		lendingBBSDao.deleteByMerchantId(experian.getMerchantId());
        lendingBBS = lendingBBSDao.save(lendingBBS);
        lendingBBSAuditDao.save(LendingBBSAudit.createObject(lendingBBS));
        return lendingBBS;
    }

	private List<LoanEligibilityDTO> getBBSLoans(Merchant merchant, Experian experian, LendingBBS lendingBBS, boolean yellowPincode){
		try {
			logger.info("Calculating ntb loan for merchant:{}", experian.getMerchantId());
			double netFreeIncomePercent = lendingBBS.getIncome() > 0 ? (lendingBBS.getNetFreeIncome() / lendingBBS.getIncome()) * 100 : 0d;
			if(netFreeIncomePercent < 10.0D) {
				logger.info("NFI less than 10%, rejecting merchant:{}", merchant.getId());
				experian.setCategory("1N");
				experian.setColor(ExperianConstants.COLOR.RED.name());
				experian.setReason(ExperianConstants.LOW_NFI);
				experianDao.save(experian);
				return new ArrayList<>();
			}
			double extraPercent = netFreeIncomePercent - 10D;
			double amountToServe = lendingBBS.getIncome() * (extraPercent/100);
			logger.info("amount to serve:{} for merchant:{}", amountToServe, merchant.getId());
			String category = getCategory(extraPercent, lendingBBS);
			logger.info("Category:{} found for merchant:{}", category, merchant.getId());
			if(category==null) {
				logger.error("No category found for merchant {}",merchant);
				return new ArrayList<>();
			}
			experian.setCategory(category);
			experian.setColor(ExperianConstants.COLOR_TO_CATEGORY.get(category));
			experianDao.save(experian);
			return getEligibleLoans(merchant, category, amountToServe, experian, yellowPincode);
		}
		catch(Exception e) {
			logger.error("Error occurred while fetching loan for BBS",e);
		}
		return new ArrayList<>();
	}

	private List<LoanEligibilityDTO> getEligibleLoans(Merchant merchant,String category, Double amountToServe,Experian experian, boolean yellowPincode){
		List<LendingCategories> lendingCategories=lendingCategoryDao.getByMasterCategoryForConstruct1(category);
		if(lendingCategories==null || lendingCategories.isEmpty()) {
			logger.error("No active lending category found for merchant: {}", merchant.getId());
			return new ArrayList<>();
		}
		List<LoanEligibilityDTO> loanEligibilityDTOList = new LinkedList<>();
		String loanType = "NTB";
		logger.info("Deleting eligible loans for merchant: {}", merchant.getId());
		eligibleLoanDao.deleteByMerchantId(merchant.getId());
		for (LendingCategories lendingCategory : lendingCategories) {
			LoanEligibilityDTO loanEligibilityDTO = loanEligibleService.calculateLoanBreakup(lendingCategory, 0D, null, experian.getMerchantId(), experian.getId(), (amountToServe * lendingCategory.getTenureMonths()), experian.getColor(), "2", loanType, false, yellowPincode);
			if (loanEligibilityDTO != null) {
				loanEligibilityDTOList.add(loanEligibilityDTO);
			} else {
				logger.info("loan offer is null for merchant: {}", merchant.getId());
			}
		}
		if (loanEligibilityDTOList.isEmpty()) {
			logger.info("No NTB loan for merchant:{}, fetching 10k loans", merchant.getId());
			for (LendingCategories lendingCategory : lendingCategories) {
				if (lendingCategory.getTenureMonths().equals(1F) || lendingCategory.getTenureMonths().equals(3F)) {
					LoanEligibilityDTO loanEligibilityDTO = loanEligibleService.calculateLoanBreakup(lendingCategory, 0D, null, experian.getMerchantId(), experian.getId(), 10000D, experian.getColor(), "2", loanType, false, yellowPincode);
					if (loanEligibilityDTO != null) {
						loanEligibilityDTOList.add(loanEligibilityDTO);
					} else {
						logger.info("loan offer is null for merchant: {}", merchant.getId());
					}
				}
			}
		}
		loanEligibilityDTOList.sort(Comparator.comparing(LoanEligibilityDTO::getAmount, Comparator.reverseOrder()).thenComparing(LoanEligibilityDTO::getEdi));
		if (!loanEligibilityDTOList.isEmpty()) {
			experianDao.updateEligibleAmount(experian.getId(), loanEligibilityDTOList.get(0).getAmount().doubleValue(), loanEligibilityDTOList.get(0).getPrincipleEdiTenure().toString(), "NTB");
		}
		return loanEligibilityDTOList;
	}

	private String getCategory(Double nfiToServeCurrentLoanPercent, LendingBBS lendingBBS) {
		if(lendingBBS!=null) {
			double bbs=lendingBBS.getBbs();
			if(nfiToServeCurrentLoanPercent<20D) {
				if(bbs>=500 && bbs<650) return "BBS11";
				else if(bbs>=650 && bbs<750) return "BBS12";
				else if(bbs>=750) return "BBS13";
			}
			else if(nfiToServeCurrentLoanPercent>=20D && nfiToServeCurrentLoanPercent<50D) {
				if(bbs>=500 && bbs<650) return "BBS21";
				else if(bbs>=650 && bbs<750) return "BBS22";
				else if(bbs>=750) return "BBS23";
			}
			else if(nfiToServeCurrentLoanPercent>=50D) {
				if(bbs>=500 && bbs<650) return "BBS31";
				else if(bbs>=650 && bbs<750) return "BBS32";
				else if(bbs>=750) return "BBS33";
			}
		}
		return null;
	}

	private Map<String,Double> getDebtAndIncome(ArrayNode loanDetails){
		Map<String,Double> debtAndIncome=new HashMap<>();
		double income=0D;
		double debt=0D;
		Double otherIncome=0D;
		for(JsonNode loan:loanDetails) {
			if (loan.get("Account_Type") == null) {
				continue;
			}
			int loanTypeNumber=loan.get("Account_Type").asInt();
			double loanAmount=getLoanAmount(loan);
			boolean isLoanClosed=isLoanClosed(loan);
			boolean isLoanClosedWithinAYear=isLoanClosedWithinOneYear(loan);
			String loanType=getLoanType(loanTypeNumber);
			if(loanAmount>10000 && (!isLoanClosed || isLoanClosedWithinAYear)) {
				logger.info("loanType:{}, accountType:{}, loanAmount:{}", loanType, loanTypeNumber, loanAmount);
				switch (loanType) {
					case "AL":{
						if(!isLoanClosedWithinAYear) {
							debt+=loanAmount*CreditConstants.EMI.get("AL");
						}
						income+=loanAmount*CreditConstants.EMI.get("AL")/CreditConstants.DBI.get("AL");
						otherIncome=CreditConstants.OTHER_INCOME.getOrDefault("AL", 0D)>otherIncome?CreditConstants.OTHER_INCOME.get("AL"):otherIncome;
					}
					break;
					case "PL":{
						if(!isLoanClosedWithinAYear) {
							debt+=loanAmount*CreditConstants.EMI.get("PL");
						}
						income+=loanAmount*CreditConstants.EMI.get("PL")/CreditConstants.DBI.get("PL");
						otherIncome=CreditConstants.OTHER_INCOME.getOrDefault("PL", 0D)>otherIncome?CreditConstants.OTHER_INCOME.get("PL"):otherIncome;
					}
					break;
					case "HL":{
						if(!isLoanClosedWithinAYear) {
							debt+=loanAmount*CreditConstants.EMI.get("HL");
						}
						income+=loanAmount*CreditConstants.EMI.get("HL")/CreditConstants.DBI.get("HL");
						otherIncome=CreditConstants.OTHER_INCOME.getOrDefault("HL", 0D)>otherIncome?CreditConstants.OTHER_INCOME.get("HL"):otherIncome;
					}
					break;
					case "BL":{
						if(!isLoanClosedWithinAYear) {
							debt+=loanAmount*CreditConstants.EMI.get("BL");
						}
						income+=loanAmount*CreditConstants.EMI.get("BL")/CreditConstants.DBI.get("BL");
						otherIncome=CreditConstants.OTHER_INCOME.getOrDefault("BL", 0D)>otherIncome?CreditConstants.OTHER_INCOME.get("BL"):otherIncome;
					}
					break;
					case "CC":{
						if(!isLoanClosedWithinAYear) {
							debt+=loanAmount*CreditConstants.EMI.get("CC");
						}
						income+=loanAmount*CreditConstants.EMI.get("CC")/CreditConstants.DBI.get("CC");
						otherIncome=CreditConstants.OTHER_INCOME.getOrDefault("CC", 0D)>otherIncome?CreditConstants.OTHER_INCOME.get("CC"):otherIncome;
					}
					break;
					case "TW":{
						if(!isLoanClosedWithinAYear) {
							debt+=loanAmount*CreditConstants.EMI.get("TW");
						}
						income+=loanAmount*CreditConstants.EMI.get("TW")/CreditConstants.DBI.get("TW");
						otherIncome=CreditConstants.OTHER_INCOME.getOrDefault("TW", 0D)>otherIncome?CreditConstants.OTHER_INCOME.get("TW"):otherIncome;
					}
					break;
					case "CD":{
						if(!isLoanClosedWithinAYear) {
							debt+=loanAmount*CreditConstants.EMI.get("CD");
						}
						income+=loanAmount*CreditConstants.EMI.get("CD")/CreditConstants.DBI.get("CD");
						otherIncome=CreditConstants.OTHER_INCOME.getOrDefault("CD", 0D)>otherIncome?CreditConstants.OTHER_INCOME.get("CD"):otherIncome;
					}
					break;
					case "GL":{
						if(!isLoanClosedWithinAYear) {
							debt+=loanAmount*CreditConstants.EMI.get("GL");
						}
						income+=loanAmount*CreditConstants.EMI.get("GL")/CreditConstants.DBI.get("GL");
						otherIncome=CreditConstants.OTHER_INCOME.getOrDefault("GL", 0D)>otherIncome?CreditConstants.OTHER_INCOME.get("GL"):otherIncome;
					}
					break;
					default:{
						if(!isLoanClosedWithinAYear) {
							debt+=loanAmount*CreditConstants.EMI.get("Other");
						}
						income+=loanAmount*CreditConstants.EMI.get("Other")/CreditConstants.DBI.get("Other");
						otherIncome=CreditConstants.OTHER_INCOME.getOrDefault("Other", 0D)>otherIncome?CreditConstants.OTHER_INCOME.get("Other"):otherIncome;
					}
					break;
				}
				logger.info("income:{}, debt:{}", income, debt);
			}
		}
		debtAndIncome.put("debt", debt);
		debtAndIncome.put("income", income);
		debtAndIncome.put("otherIncome", otherIncome);
		return debtAndIncome;
	}

	private Map<String,Double> getDebtAndIncome(JsonNode loan){
		Map<String, Double> debtAndIncome=new HashMap<>();
		double debt=0D;
		double income=0D;
		Double otherIncome=0D;
		if (loan.get("Account_Type") == null) {
			debtAndIncome.put("debt", debt);
			debtAndIncome.put("income", income);
			debtAndIncome.put("otherIncome", otherIncome);
			return debtAndIncome;
		}
		int loanTypeNumber=loan.get("Account_Type").asInt();
		double loanAmount=getLoanAmount(loan);
		boolean isLoanClosed=isLoanClosed(loan);
		boolean isLoanClosedWithinAYear=isLoanClosedWithinOneYear(loan);
		String loanType=getLoanType(loanTypeNumber);
		if(loanAmount>10000 && (!isLoanClosed || isLoanClosedWithinAYear)) {
			switch (loanType) {
				case "AL":{
					if(!isLoanClosedWithinAYear) {
						debt+=loanAmount*CreditConstants.EMI.get("AL");
					}
					income+=loanAmount*CreditConstants.EMI.get("AL")/CreditConstants.DBI.get("AL");
					otherIncome=CreditConstants.OTHER_INCOME.getOrDefault("AL", 0D)>otherIncome?CreditConstants.OTHER_INCOME.get("AL"):otherIncome;
				}
				break;
				case "PL":{
					if(!isLoanClosedWithinAYear) {
						debt+=loanAmount*CreditConstants.EMI.get("PL");
					}
					income+=loanAmount*CreditConstants.EMI.get("PL")/CreditConstants.DBI.get("PL");
					otherIncome=CreditConstants.OTHER_INCOME.getOrDefault("PL", 0D)>otherIncome?CreditConstants.OTHER_INCOME.get("PL"):otherIncome;
				}
				break;
				case "HL":{
					if(!isLoanClosedWithinAYear) {
						debt+=loanAmount*CreditConstants.EMI.get("HL");
					}
					income+=loanAmount*CreditConstants.EMI.get("HL")/CreditConstants.DBI.get("HL");
					otherIncome=CreditConstants.OTHER_INCOME.getOrDefault("HL", 0D)>otherIncome?CreditConstants.OTHER_INCOME.get("HL"):otherIncome;
				}
				break;
				case "BL":{
					if(!isLoanClosedWithinAYear) {
						debt+=loanAmount*CreditConstants.EMI.get("BL");
					}
					income+=loanAmount*CreditConstants.EMI.get("BL")/CreditConstants.DBI.get("BL");
					otherIncome=CreditConstants.OTHER_INCOME.getOrDefault("BL", 0D)>otherIncome?CreditConstants.OTHER_INCOME.get("BL"):otherIncome;
				}
				break;
				case "CC":{
					if(!isLoanClosedWithinAYear) {
						debt+=loanAmount*CreditConstants.EMI.get("CC");
					}
					income+=loanAmount*CreditConstants.EMI.get("CC")/CreditConstants.DBI.get("CC");
					otherIncome=CreditConstants.OTHER_INCOME.getOrDefault("CC", 0D)>otherIncome?CreditConstants.OTHER_INCOME.get("CC"):otherIncome;
				}
				break;
				case "TW":{
					if(!isLoanClosedWithinAYear) {
						debt+=loanAmount*CreditConstants.EMI.get("TW");
					}
					income+=loanAmount*CreditConstants.EMI.get("TW")/CreditConstants.DBI.get("TW");
					otherIncome=CreditConstants.OTHER_INCOME.getOrDefault("TW", 0D)>otherIncome?CreditConstants.OTHER_INCOME.get("TW"):otherIncome;
				}
				break;
				case "CD":{
					if(!isLoanClosedWithinAYear) {
						debt+=loanAmount*CreditConstants.EMI.get("CD");
					}
					income+=loanAmount*CreditConstants.EMI.get("CD")/CreditConstants.DBI.get("CD");
					otherIncome=CreditConstants.OTHER_INCOME.getOrDefault("CD", 0D)>otherIncome?CreditConstants.OTHER_INCOME.get("CD"):otherIncome;
				}
				break;
				case "GL":{
					if(!isLoanClosedWithinAYear) {
						debt+=loanAmount* CreditConstants.EMI.get("GL");
					}
					income+=loanAmount*CreditConstants.EMI.get("GL")/CreditConstants.DBI.get("GL");
					otherIncome=CreditConstants.OTHER_INCOME.getOrDefault("GL", 0D)>otherIncome?CreditConstants.OTHER_INCOME.get("GL"):otherIncome;
				}
				break;
				default:{
					if(!isLoanClosedWithinAYear) {
						debt+=loanAmount*CreditConstants.EMI.get("Other");
					}
					income+=loanAmount*CreditConstants.EMI.get("Other")/CreditConstants.DBI.get("Other");
					otherIncome=CreditConstants.OTHER_INCOME.getOrDefault("Other", 0D)>otherIncome?CreditConstants.OTHER_INCOME.get("Other"):otherIncome;
				}
				break;
			}
		}
		debtAndIncome.put("debt", debt);
		debtAndIncome.put("income", income);
		debtAndIncome.put("otherIncome", otherIncome);
		return debtAndIncome;
	}

	private Double getLoanAmount(JsonNode loan) {
		double amount1=loan.has("Highest_Credit_or_Original_Loan_Amount")?loan.get("Highest_Credit_or_Original_Loan_Amount").asDouble():0D;
		double amount2=loan.has("Credit_Limit_Amount")?loan.get("Credit_Limit_Amount").asDouble():0D;
		return amount1>amount2?amount1:amount2;
	}

	private boolean isLoanClosed(JsonNode loan) {
    	if (loan.get("Date_Closed") == null || loan.get("Date_Closed").toString().equals("\"\"")) {
    		return false;
		}
		Integer accountStatus=loan.has("Account_Status") && !loan.get("Account_Status").isNull()?loan.get("Account_Status").asInt():null;
		return !activeStatusList.contains(accountStatus);
	}

	private double getNetFreeIncome(double income,double debt) {
		double expenses=0.20*income;
		double totalExpenses=expenses+debt;
		return income-totalExpenses;
	}

	private boolean isLoanClosedWithinOneYear(JsonNode loan) {
		String date=(loan.has("Date_Closed") && !loan.get("Date_Closed").isNull() && ! loan.get("Date_Closed").asText().equalsIgnoreCase(""))?loan.get("Date_Closed").asText():null;
		if(date!=null) {
			SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
			try {
				Date closingDate= format.parse (date);
				Date today=new Date();
				if(((today.getTime()-closingDate.getTime())/ 1000)>31556952) {
					return false;
				}
				return true;
			} catch (Exception e) {
				logger.error("Error occured while checking for loan closing duration",e);
			}
		}
		return false;
	}

	private String getLoanType(Integer loanType) {
		if(loanType==1 || loanType==17 || loanType==32) {
			return "AL";
		}
		else if(loanType==4 || loanType==5 || loanType==9 || loanType==38 || loanType==39 || loanType==60) {
			return "PL";

		}
		else if(loanType==2 ||loanType==3){
			return "HL";
		}
		else if(loanType==51 ||loanType==52 || loanType==53 ||loanType==54 || loanType==59 ||loanType==61) {
			return "BL";
		}
		else if(loanType==10 ||loanType==35) {
			return "CC";
		}
		else if(loanType==13) {
			return "TW";
		}
		else if(loanType==6) {
			return "CD";
		}
		else if(loanType==7) {
			return "GL";
		}
		else {
			return "Other";
		}
	}

    private int historyScore(int creditHistory) {
        if (creditHistory < 6) {
            return 0;
        } else if (creditHistory < 12) {
            return 1;
        } else if (creditHistory < 24) {
            return 2;
        } else
            return 3;
    }

    private int unsecuredLoanScore(double unsecuredLoanRatio6mon) {
        if (unsecuredLoanRatio6mon < 0.17) {
            return 3;
        } else if (unsecuredLoanRatio6mon < 0.5) {
            return 2;
        } else if (unsecuredLoanRatio6mon < 0.67) {
            return 1;
        } else
            return 0;
    }

    private int loanTypesScore(int typesOfLoan) {
        if (typesOfLoan < 3) {
            return 0;
        } else if (typesOfLoan < 5) {
            return 1;
        } else if (typesOfLoan < 8) {
            return 2;
        } else
            return 3;
    }

    private int loanSanctionedScore(int loanSanctioned3mon) {
        if (loanSanctioned3mon < 2) {
            return 3;
        } else if (loanSanctioned3mon < 4) {
            return 2;
        } else if (loanSanctioned3mon < 6) {
            return 0;
        } else
            return -1;
    }

    private int delinquencyScore(int delinquencyCount6mon) {
        if (delinquencyCount6mon < 1) {
            return 3;
        } else if (delinquencyCount6mon < 3) {
            return -1;
        } else if (delinquencyCount6mon < 6) {
            return -2;
        } else
            return -5;
    }

    private int loanEnquiresScore(int loanEnquires3mon) {
        if (loanEnquires3mon < 4) {
            return 3;
        } else if (loanEnquires3mon < 7) {
            return 2;
        } else if (loanEnquires3mon < 10) {
            return 1;
        } else
            return 0;
    }

    private int checkLoanEnquiriesInLast3Months(JsonNode experianResponse) {
    	if (experianResponse.get("INProfileResponse").get("TotalCAPS_Summary") != null && experianResponse.get("INProfileResponse").get("TotalCAPS_Summary").get("TotalCAPSLast90Days") != null) {
    		return experianResponse.get("INProfileResponse").get("TotalCAPS_Summary").get("TotalCAPSLast90Days").asInt();
		}
        return 0;
    }

    private int checkDPDLastXmonths(JsonNode jsonNode, int months, Date reportDate){
		Date dateReported = null;
		try {
			if (jsonNode.get("Date_Reported") != null && !jsonNode.get("Date_Reported").asText().equalsIgnoreCase("")) {
				dateReported = new SimpleDateFormat("yyyyMMdd").parse(jsonNode.get("Date_Reported").asText());
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
            month = (c.get(Calendar.MONTH) + 1) < 10 ? "0" + (c.get(Calendar.MONTH) + 1) : (c.get(Calendar.MONTH) + 1) + "";
            monthYear.add(month + "$" + c.get(Calendar.YEAR));//01$2020
            c.add(Calendar.MONTH, -1);
        }
        if (jsonNode.get("CAIS_Account_History") != null && jsonNode.get("CAIS_Account_History").isArray()) {
            for (JsonNode cais_account_history : jsonNode.get("CAIS_Account_History")) {
                if (monthYear.contains(cais_account_history.get("Month").asText() + "$" + cais_account_history.get("Year").asText()) && !cais_account_history.get("Days_Past_Due").isNull() && !cais_account_history.get("Days_Past_Due").asText().equalsIgnoreCase("") && cais_account_history.get("Days_Past_Due").asInt() > 0) {
                    logger.info("dpd: {}: {}", cais_account_history.get("Month").asText() + "$" + cais_account_history.get("Year").asText(), cais_account_history.get("Days_Past_Due"));
                	dpd ++;
                }
            }
        } else if (jsonNode.get("CAIS_Account_History") != null && jsonNode.get("CAIS_Account_History").isObject()){
            JsonNode cais_account_history = jsonNode.get("CAIS_Account_History");
            if (monthYear.contains(cais_account_history.get("Month").asText() + "$" + cais_account_history.get("Year").asText()) && !cais_account_history.get("Days_Past_Due").isNull() && !cais_account_history.get("Days_Past_Due").asText().equalsIgnoreCase("") && cais_account_history.get("Days_Past_Due").asInt() > 0) {
				logger.info("dpd: {}: {}", cais_account_history.get("Month").asText() + "$" + cais_account_history.get("Year").asText(), cais_account_history.get("Days_Past_Due"));
            	dpd ++;
            }
        }
        return dpd;
    }

    private int loanSanctioned3mon(JsonNode jsonNode, Date reportDate) throws ParseException {
        if (jsonNode.get("Open_Date") != null && !jsonNode.get("Open_Date").asText().trim().equals("")) {
            Date openDate = experianFormat.parse(jsonNode.get("Open_Date").asText());
            return LoanUtil.getDateDiffInDays(openDate, reportDate) <= 90 ? 1 : 0;
        }
        return 0;
    }

    private int unsecuredLoan6mon(JsonNode jsonNode, Date reportDate) throws ParseException {
        if (jsonNode.get("Account_Type") != null && jsonNode.get("Open_Date") != null && !jsonNode.get("Open_Date").asText().trim().equals("")) {
            Date openDate = experianFormat.parse(jsonNode.get("Open_Date").asText());
            return LoanUtil.getDateDiffInDays(openDate, reportDate) <= 180 && unsecuredLoan.contains(jsonNode.get("Account_Type").asInt()) ? 1 : 0;
        } else if (jsonNode.get("Account_Type") != null && jsonNode.get("DateOfAddition") != null && !jsonNode.get("DateOfAddition").asText().trim().equals("")) {
            Date openDate = experianFormat.parse(jsonNode.get("DateOfAddition").asText());
            return LoanUtil.getDateDiffInDays(openDate, reportDate) <= 180 && unsecuredLoan.contains(jsonNode.get("Account_Type").asInt()) ? 1 : 0;
        }
        return 0;
    }
}
