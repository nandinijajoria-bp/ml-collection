package com.bharatpe.lending.service;

import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.PaymentTransactionNewDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.LendingBBSAuditDao;
import com.bharatpe.lending.common.dao.LendingBBSDao;
import com.bharatpe.lending.common.entity.LendingBBS;
import com.bharatpe.lending.common.entity.LendingBBSAudit;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dto.LoanEligibilityDTO;
import com.bharatpe.lending.util.LoanUtil;
import com.bharatpe.lending.util.creditresponse.ResponseUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired
	LendingApplicationDao lendingApplicationDao;

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

    public List<LoanEligibilityDTO> fetchBBSLoans(Merchant merchant, Experian experian, boolean yellowPincode, boolean hasRegularLoan) {
		logger.info("Fetching NTB loans for merchant:{}", experian.getMerchantId());
//        if (experian.getResponse() == null) {
//            logger.info("Merchant:{} not eligible for BBS", merchant.getId());
//            return new ArrayList<>();
//        }
        try {
        	LendingBBS lendingBBS = lendingBBSDao.findByMerchantId(merchant.getId());
        	if (lendingBBS == null || LoanUtil.getDateDiffInDays(lendingBBS.getCreatedAt(), new Date()) > 45) {
        		lendingBBS = calculateBBS(experian, merchant);
			}
			if(lendingBBS == null){
				logger.info("BBS Calculation failed, invalid report, so rejecting merchant: {}", merchant.getId());
				return new ArrayList<>();
			}
			logger.info("BBS:{} for merchant:{}", lendingBBS.getBbs(), experian.getMerchantId());
        	if (!baseChecks(lendingBBS, merchant, experian, yellowPincode, hasRegularLoan)) {
				logger.info("Base Checks Failed, so rejecting merchant: {}", merchant.getId());
				return new ArrayList<>();
			}
			return getBBSLoans(merchant, experian, lendingBBS, yellowPincode, hasRegularLoan);
        } catch (Exception e) {
            logger.error("Exception in BBS---", e);
            return new ArrayList<>();
        }
    }

    private boolean baseChecks(LendingBBS lendingBBS, Merchant merchant, Experian experian, boolean yellowPincode, boolean hasRegularLoan) {
		if (lendingBBS.getBbs() < 500) {
			logger.info("BBS less than 500, rejecting merchant:{}", merchant.getId());
			if (!hasRegularLoan) {
				experian.setCategory("1N");
				experian.setColor(ExperianConstants.COLOR.RED.name());
				experian.setReason(ExperianConstants.LOW_BBS);
				experianDao.save(experian);
			}
			return false;
		}
		if (checkVintage(merchant) < 30 && ((yellowPincode && lendingBBS.getBbs() < 700) || (!yellowPincode && lendingBBS.getBbs() < 650))) {
			logger.info("Low BBS Vintage, rejecting merchant:{}", merchant.getId());
			if (!hasRegularLoan) {
				experian.setCategory("1N");
				experian.setColor(ExperianConstants.COLOR.RED.name());
				experian.setReason(ExperianConstants.LOW_BBS_VINTAGE);
				experianDao.save(experian);
			}
			return false;
		}
		if (checkVintage(merchant) < 60 && ((yellowPincode && lendingBBS.getBbs() < 650) || (!yellowPincode && lendingBBS.getBbs() < 600))) {
			logger.info("Low BBS Vintage, rejecting merchant:{}", merchant.getId());
			if (!hasRegularLoan) {
				experian.setCategory("1N");
				experian.setColor(ExperianConstants.COLOR.RED.name());
				experian.setReason(ExperianConstants.LOW_BBS_VINTAGE);
				experianDao.save(experian);
			}
			return false;
		}
		if (checkVintage(merchant) < 90 && ((yellowPincode && lendingBBS.getBbs() < 600) || (!yellowPincode && lendingBBS.getBbs() < 550))) {
			logger.info("Low BBS Vintage, rejecting merchant:{}", merchant.getId());
			if (!hasRegularLoan) {
				experian.setCategory("1N");
				experian.setColor(ExperianConstants.COLOR.RED.name());
				experian.setReason(ExperianConstants.LOW_BBS_VINTAGE);
				experianDao.save(experian);
			}
			return false;
		}
		if (checkVintage(merchant) >= 90 && ((yellowPincode && lendingBBS.getBbs() < 550) || (!yellowPincode && lendingBBS.getBbs() < 500))) {
			logger.info("Low BBS Vintage, rejecting merchant:{}", merchant.getId());
			if (!hasRegularLoan) {
				experian.setCategory("1N");
				experian.setColor(ExperianConstants.COLOR.RED.name());
				experian.setReason(ExperianConstants.LOW_BBS_VINTAGE);
				experianDao.save(experian);
			}
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

    public LendingBBS calculateBBS(Experian experian, Merchant merchant) throws IOException, ParseException {
		logger.info("Calculating BBS for merchant:{}", experian.getMerchantId());
		ResponseUtil creditBureauResponseUtil = loanEligibleService.getCreditBureauResponse(experian);
		if(!creditBureauResponseUtil.isValid(experian.getPancardNumber(), merchant.getMobile())) return null;
		Date reportDate = creditBureauResponseUtil.getReportDate();
		Map<String, Object> data = creditBureauResponseUtil.getBBSCalculationDetails();
        Set<Integer> loanTypes = (Set<Integer>)data.get("loanTypes");
        int loanEnquires3mon = creditBureauResponseUtil.countLoanEnquiriesInLast3Months();
        int delinquencyCount6mon = (Integer)data.get("delinquencyCount6mon");
        int loanSanctioned3mon = (Integer)data.get("loanSanctioned3mon");
        int unsecuredLoanCount6mon = (Integer)data.get("unsecuredLoanCount6mon");
		Date minOpenDate = (Date)data.get("minOpenDate");
		Map<String,Double> debtAndIncome = (Map<String, Double>)data.get("debtAndIncome");
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

	private List<LoanEligibilityDTO> getBBSLoans(Merchant merchant, Experian experian, LendingBBS lendingBBS, boolean yellowPincode, boolean hasRegularLoan){
		try {
			logger.info("Calculating ntb loan for merchant:{}", experian.getMerchantId());
			double netFreeIncomePercent = lendingBBS.getIncome() > 0 ? (lendingBBS.getNetFreeIncome() / lendingBBS.getIncome()) * 100 : 0d;
			if(netFreeIncomePercent < 10.0D) {
				logger.info("NFI less than 10%, rejecting merchant:{}", merchant.getId());
				if (!hasRegularLoan) {
					experian.setCategory("1N");
					experian.setColor(ExperianConstants.COLOR.RED.name());
					experian.setReason(ExperianConstants.LOW_NFI);
					experianDao.save(experian);
				}
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
			return getEligibleLoans(merchant, category, amountToServe, experian, yellowPincode, lendingBBS.getBbs(), hasRegularLoan);
		}
		catch(Exception e) {
			logger.error("Error occurred while fetching loan for BBS",e);
		}
		return new ArrayList<>();
	}

	private List<LoanEligibilityDTO> getEligibleLoans(Merchant merchant,String category, Double amountToServe,Experian experian, boolean yellowPincode, double bbs, boolean hasRegularLoan){
		List<LendingCategories> lendingCategories=lendingCategoryDao.getByMasterCategoryForConstruct1(category);
		if(lendingCategories==null || lendingCategories.isEmpty()) {
			logger.error("No active lending category found for merchant: {}", merchant.getId());
			return new ArrayList<>();
		}
		List<LoanEligibilityDTO> loanEligibilityDTOList = new LinkedList<>();
		String loanType = "NTB";
		logger.info("Deleting NTB eligible loans for merchant: {}", merchant.getId());
		eligibleLoanDao.deleteByMerchantIdAndLoanType(merchant.getId(), "NTB");
		for (LendingCategories lendingCategory : lendingCategories) {
			double loanAmount = (amountToServe * lendingCategory.getTenureMonths());
			if (bbs >= 500 && bbs <= 600) {
				if (loanAmount > 35000 && loanAmount <= 50000) {
					loanAmount = 35000;
				} else if (loanAmount > 50000 && loanAmount <= 80000) {
					loanAmount = 45000;
				}
			}
			if (bbs <= 600 && loanAmount < 25000) {
				logger.info("NTB loan amount is less than 25000 for merchant: {}", experian.getMerchantId());
				continue;
			}
			LoanEligibilityDTO loanEligibilityDTO = loanEligibleService.calculateLoanBreakup(lendingCategory, 0D, null, experian.getMerchantId(), experian.getId(), loanAmount, experian.getColor(), "2", loanType, false, yellowPincode);
			if (loanEligibilityDTO != null) {
				loanEligibilityDTOList.add(loanEligibilityDTO);
			} else {
				logger.info("loan offer is null for merchant: {}", merchant.getId());
			}
		}
		if (loanEligibilityDTOList.isEmpty() && bbs > 600) {
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
		try {
			LendingApplication ntbLoan = lendingApplicationDao.getPreviousNTBLoan(merchant.getId());
			if (!loanEligibilityDTOList.isEmpty() && ntbLoan != null && ntbLoan.getLoanAmount() * 1.25 > loanEligibilityDTOList.get(0).getAmount()) {
				logger.info("Calculating ntb loan using previous NTB loan amount for merchant:{}", merchant.getId());
				LendingCategories categories = lendingCategoryDao.getByCategory(ntbLoan.getCategory());
				LoanEligibilityDTO loanEligibilityDTO = loanEligibleService.calculateLoanBreakup(categories, 0D, null, experian.getMerchantId(), experian.getId(), ntbLoan.getLoanAmount() * 1.25, experian.getColor(), "2", loanType, false, yellowPincode);
				if (loanEligibilityDTO != null) {
					logger.info("loan offer calculated using previous ntb loan for merchant: {}", merchant.getId());
					loanEligibilityDTOList.add(loanEligibilityDTO);
					loanEligibilityDTOList.sort(Comparator.comparing(LoanEligibilityDTO::getAmount, Comparator.reverseOrder()).thenComparing(LoanEligibilityDTO::getEdi));
				} else {
					logger.info("loan offer is null for merchant: {}", merchant.getId());
				}
			}
		} catch (Exception e) {
			logger.error("Exception in ntb loan", e);
		}
		if (!loanEligibilityDTOList.isEmpty()) {
			experianDao.updateEligibleAmount(experian.getId(), loanEligibilityDTOList.get(0).getAmount().doubleValue(), loanEligibilityDTOList.get(0).getPrincipleEdiTenure().toString(), "NTB");
		}
		if (loanEligibilityDTOList.isEmpty() && !hasRegularLoan) {
			logger.info("Low ATS, so rejecting ntb loan for merchant: {}", experian.getMerchantId());
			experian.setCategory("1N");
			experian.setColor(ExperianConstants.COLOR.RED.name());
			experian.setReason(ExperianConstants.LOW_ATS);
			experianDao.save(experian);
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

	private double getNetFreeIncome(double income,double debt) {
		double expenses=0.20*income;
		double totalExpenses=expenses+debt;
		return income-totalExpenses;
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
}
