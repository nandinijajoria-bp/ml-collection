package com.bharatpe.lending.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bharatpe.common.dao.AvailableLoanDao;
import com.bharatpe.common.dao.DocumentsIdProofDao;
import com.bharatpe.common.dao.LoanDetailsDao;
import com.bharatpe.common.dao.MerchantAddressDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.dao.MerchantSummaryDao;
import com.bharatpe.common.entities.Agent;
import com.bharatpe.common.entities.AvailableLoan;
import com.bharatpe.common.entities.BankList;
import com.bharatpe.common.entities.DocumentsIdProof;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingCategories;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantAddress;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.common.entities.MerchantSummary;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.bean.Label;
import com.bharatpe.lending.dao.AgentDao;
import com.bharatpe.lending.dao.BankListDao;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.util.LoanUtil;
import com.bharatpe.common.enums.MerchantCategory;
import com.bharatpe.common.enums.Status.GeneralStatus;

@Service
public class LoanDetailsService {
	private Logger logger = LoggerFactory.getLogger(LoanDetailsService.class);
	List<String> validAgentCities = Arrays.asList("Bangalore","Hyderabad","Pune","Delhi","Noida","Gurgaon","Mumbai");
	List<String> validDIYCities = Arrays.asList("Bengaluru","Pune","Delhi","Noida","Gurgaon","Faridabad","Ghaziabad","Thane","Mumbai","Hyderabad");
	
	@Autowired
	MerchantDao merchantDao;
	
	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;
	
	@Autowired
	BankListDao bankListDao;
	
	@Autowired
	MerchantSummaryDao merchantSummaryDao;
	
	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	AvailableLoanDao availableLoanDao;
	
	@Autowired
	LendingCategoryDao lendingCategoryDao;
	
	@Autowired
	LoanDetailsDao loanDetailsDao;
	
	@Autowired
	DocumentsIdProofDao documentsIdProofDao;
	
	@Autowired
	AgentDao agentDao;
	
	@Autowired
	MerchantAddressDao merchantAddressDao;

	public Map<String, Object> fetchLoanDetails(Merchant merchant, CommonAPIRequest commonAPIRequest) {
		Map<String, Object> resp;
		List<Map<String, Object>> eligibility = new ArrayList<>();
		Map<String, Object> details = new LinkedHashMap<>();
		Boolean eligibleFlag = false;
		
		Long merchantId = merchant.getId();
		
		if(isValidFOSMerchant(merchant.getReferalCode()) || isValidDIYMerchant(merchant)) {
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchantId,"ACTIVE");
			
			if(merchantBankDetail != null) {
				Boolean isPaymentBankFlag = isPaymentBank(merchantBankDetail);
				MerchantSummary merchantSummary = merchantSummaryDao.fetchActiveMerchantLoan(merchantId);
				
				if(merchantSummary != null) {
					
					//checkLastPaymentSchedule(merchantId);
					eligibility = fetchEligibleLoans(merchantSummary.getLoanType(), merchantId, eligibility, isPaymentBankFlag);
					
					String toBeEligibleLoanType = "FIRST_TO_BE_ELIGIBLE";
					if(merchantSummary.getLoanType().equals("SUBSEQUENT")) {
						toBeEligibleLoanType = "SUBSEQUENT_TO_BE_ELIGIBLE";
					}
					eligibility = fetchEligibleLoans(toBeEligibleLoanType, merchantId, eligibility, isPaymentBankFlag);
					
					if(eligibility.size() > 0) {
						eligibleFlag = true;
					}
					
					details = fetchLoanHistory(merchantId);
				}
			}
		}else {
			logger.info("LoanDetails No valid merchant for merchantId : {}", merchantId);
		}	
		resp = prepareResponse(eligibility, details, eligibleFlag);
		
		return resp;
	}
	
	private Boolean isValidFOSMerchant(String referalCode) {
		Boolean responseFlag = false;
		
		if(!referalCode.isEmpty()) {
			Agent agent = agentDao.fetchByReferalCode(referalCode);
			if(agent != null && validAgentCities.contains(agent.getCity())) {
				responseFlag = true;
			}
		}
		
		return responseFlag;
	}
	
	private Boolean isValidDIYMerchant(Merchant merchant) {
		Boolean responseFlag = false;
		
		MerchantAddress merchantAddress = merchantAddressDao.findBymerchantIdAndType(merchant.getId(), "SELF");
		if(merchantAddress != null && validDIYCities.contains(merchantAddress.getCity()) && isInvalidAgentReferalCode(merchant.getReferalCode())) {
			responseFlag = true;
		}
		
		return responseFlag;
	}
	
	private Boolean isInvalidAgentReferalCode(String agentReferalCode) {
		Boolean flag = false;
		
		Agent agent = agentDao.fetchByReferalCode(agentReferalCode);
		if(agent != null) {
			flag = true;
		}
		
		return flag;
	}
	
	private Boolean isPaymentBank(MerchantBankDetail merchantBankDetail) {
		Boolean flag = true;
		
		String ifsc = merchantBankDetail.getIfscCode();
		if(ifsc != null || ifsc != "") {
			ifsc = ifsc.substring(0,4);
			
			List<BankList> nonPaymentBankList = bankListDao.fetchNonPaymentBankList(ifsc);
			
			if(nonPaymentBankList == null || nonPaymentBankList.size() == 0) {
				flag = false;
			}
		}
		
		return flag;
	}
	
	
//	private Map<String, Object> checkLastPaymentSchedule(Long merchantId) {
//		Map<String, Object> previousLoanDetails = new LinkedHashMap<>();
//		previousLoanDetails.put("isSubsequentLoan", false);
//		previousLoanDetails.put("prevTenure", "");
//		
//		LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findLatestLendingPaymentScheduleByMerchantId(merchantId);
//		if(lendingPaymentSchedule != null) {
//			String status = lendingPaymentSchedule.getStatus();
//			if(status.equals("CLOSED")) {
//				previousLoanDetails.put("isSubsequentLoan", true);
//				LendingApplication lendingApplication = lendingApplicationDao.findByApplicationId(lendingPaymentSchedule.getApplicationId());
//				if(lendingApplication != null) {
//					previousLoanDetails.put("prevTenure", lendingApplication.getTenure());
//				}
//			}
//		}
//		return previousLoanDetails;
//	}
	
	private List<Map<String, Object>> fetchEligibleLoans(String loanType, Long merchantId, List<Map<String, Object>> eligibility, Boolean isPaymentBank) {
		
		List<AvailableLoan> availableLoanList = null;
		
		if(merchantId % 2 == 0) {
			availableLoanList = availableLoanDao.findByMerchantIdAndTypeAndLoanConstructOrderByAmountDesc(merchantId, loanType, "CONSTRUCT_2");
		} else {
			availableLoanList = availableLoanDao.findByMerchantIdAndTypeAndLoanConstructOrderByAmountDesc(merchantId, loanType, "CONSTRUCT_3");
		}
		
		if(availableLoanList == null || availableLoanList.isEmpty()) {
			availableLoanList = availableLoanDao.findByMerchantIdAndTypeAndLoanConstructOrderByAmountDesc(merchantId, loanType, "CONSTRUCT_1");
			
			if(availableLoanList != null && !availableLoanList.isEmpty()) {
				availableLoanList = sort(availableLoanList);
			}
		}
		
		List<LendingCategories> lendingCategoriesList = (List<LendingCategories>) lendingCategoryDao.findByStatus(GeneralStatus.ACTIVE.toString());
		for(AvailableLoan availableLoan : availableLoanList) {
			if(isPaymentBank && availableLoan.getAmount() != 5000) {
				continue;
			}
			LendingCategories lendingCategoryDetail = fetchCategoryDetails(lendingCategoriesList, availableLoan.getCategory());
			if(lendingCategoryDetail != null) {
				Map<String, Object> elegibleLoan = new LinkedHashMap<>();
				
				if(availableLoan.getAmount() == 5000 && lendingCategoryDetail.getTenureMonths() == 1.00 ) {
//					lendingCategoriesList.get(0).setProcessingFee("0");
					elegibleLoan.put("processing_fee", lendingCategoryDetail.getProcessingFee());
				} else {
//					lendingCategoriesList.get(0).setInterestRate(Double.valueOf(0));
					elegibleLoan.put("interest_rate", lendingCategoryDetail.getInterestRate());
				}
				elegibleLoan.put("amount", String.valueOf(availableLoan.getAmount().intValue()));
				elegibleLoan.put("category", lendingCategoryDetail.getCategory());
				elegibleLoan.put("tenure", lendingCategoryDetail.getPayableConverter().replace("Months", "").replace("Month", "").trim());
				elegibleLoan.put("construct", availableLoan.getLoanConstruct());
				elegibleLoan.put("list", prepareLabels(availableLoan, lendingCategoryDetail));
				elegibleLoan.put("type", getType(availableLoan));
				if(loanType.equals("FIRST_TO_BE_ELIGIBLE") || loanType.equals("SUBSEQUENT_TO_BE_ELIGIBLE")) {
					elegibleLoan.put("option_enable", false);
				} else {
					elegibleLoan.put("option_enable", true);
				}
				eligibility.add(elegibleLoan);
			}
		}
		return eligibility;
	}
	
	private String getType(AvailableLoan availableLoan) {
		if("CONSTRUCT_1".equals(availableLoan.getLoanConstruct())) {
			return null;
		} else if("CONSTRUCT_2".equals(availableLoan.getLoanConstruct())) {
			return "1st Month Free";
		} else if("CONSTRUCT_3".equals(availableLoan.getLoanConstruct())) {
			return "Only Interest";
		}
		return null;
	}

	private List<Label> prepareLabels(AvailableLoan availableLoan, LendingCategories lendingCategoryDetail) {
		List<Label> list = new ArrayList<>();
		
		if("CONSTRUCT_1".equals(availableLoan.getLoanConstruct())) {
			int edi = (int) Math.ceil((availableLoan.getAmount() + (availableLoan.getAmount() * (lendingCategoryDetail.getInterestRate() / 100) * lendingCategoryDetail.getTenureMonths()) + Integer.parseInt(lendingCategoryDetail.getProcessingFee())) / lendingCategoryDetail.getPayableDays());
			list.add(new Label("Daily Installment", "₹" + edi + "/day"));
			list.add(new Label("No Installment on", "Sundays"));
			list.add(new Label("Repayment Amount", String.valueOf(Math.round(lendingCategoryDetail.getPayableDays() * edi))));
		} else if("CONSTRUCT_2".equals(availableLoan.getLoanConstruct())) {
			Double processingFeeMultiplier = Double.valueOf(lendingCategoryDetail.getProcessingFee());
			Double processingFee = availableLoan.getAmount() * processingFeeMultiplier;
			
			int edi = (int) Math.ceil((availableLoan.getAmount() + (availableLoan.getAmount() * (lendingCategoryDetail.getInterestRate() / 100) * lendingCategoryDetail.getTenureMonths()) + processingFee) / lendingCategoryDetail.getPayableDays());
			list.add(new Label("EDI for 1st Month", "ZERO"));
			list.add(new Label("EDI for Next " + (lendingCategoryDetail.getTenureMonths().intValue() - 1) + " Month", "₹" + edi + "/day"));
			list.add(new Label("No EDI on", "Sundays"));
			list.add(new Label("Repayment Amount", String.valueOf(Math.round(lendingCategoryDetail.getPayableDays() * edi))));
		} else if("CONSTRUCT_3".equals(availableLoan.getLoanConstruct())) {
			Double processingFeeMultiplier = Double.valueOf(lendingCategoryDetail.getProcessingFee());
			Double processingFee = availableLoan.getAmount() * processingFeeMultiplier;
			
			int ioEdi = (int) Math.ceil((availableLoan.getAmount() * (lendingCategoryDetail.getInterestRate() / 100) * lendingCategoryDetail.getIoTenureMonths()) / lendingCategoryDetail.getIoPayableDays());
			int edi = (int) Math.ceil((availableLoan.getAmount() + (availableLoan.getAmount() * (lendingCategoryDetail.getInterestRate() / 100) * (lendingCategoryDetail.getTenureMonths() - lendingCategoryDetail.getIoTenureMonths())) + processingFee) / lendingCategoryDetail.getPayableDays());
			list.add(new Label("EDI for 1st Month", "₹" + ioEdi + "/day"));
			list.add(new Label("EDI for Next " + (lendingCategoryDetail.getTenureMonths().intValue() - lendingCategoryDetail.getIoTenureMonths().intValue()) + " Month", "₹" + edi + "/day"));
			list.add(new Label("No EDI on", "Sundays"));
			list.add(new Label("Repayment Amount", String.valueOf(Math.round(lendingCategoryDetail.getPayableDays() * edi) + Math.round(lendingCategoryDetail.getIoPayableDays() * ioEdi))));
		} else {
			logger.error("Construct {} not defined, throwing Exception", availableLoan.getLoanConstruct());
			throw new RuntimeException("Construct not defined.");
		}
		
		return list;
	}

	private List<AvailableLoan> sort(List<AvailableLoan> availableLoanList) {
		List<AvailableLoan> sortedAvailableLoans = new ArrayList<>();
		try {
			String maxCategory = null;
			
//			1st loan in the list is with the maximum amount
			for(AvailableLoan current : sortedAvailableLoans) {
				if(maxCategory == null) {
					if(MerchantCategory.AA3.toString().equals(current.getCategory()) || MerchantCategory.AA1.toString().equals(current.getCategory())) {
						maxCategory = MerchantCategory.AA.toString();
					} else if (MerchantCategory.BB6.toString().equals(current.getCategory()) || MerchantCategory.BB3.toString().equals(current.getCategory())) {
						maxCategory = MerchantCategory.BB.toString();
					} else if (MerchantCategory.CC12.toString().equals(current.getCategory()) || MerchantCategory.CC6.toString().equals(current.getCategory()) || MerchantCategory.CC3.toString().equals(current.getCategory())) {
						maxCategory = MerchantCategory.CC.toString();
					} else {
						logger.debug("Loan category not as expected, returning all available loans");
						return availableLoanList;
					}
				}
				
				if(current.getCategory().startsWith(maxCategory)) {
					sortedAvailableLoans.add(current);
				}
			}
			return sortedAvailableLoans;
		} catch(Exception ex) {
			logger.error("Exception while soring available loans returning all, Exception is {}", ex);
			return availableLoanList;
		}
		
	}

	private LendingCategories fetchCategoryDetails(List<LendingCategories> lendingCategoriesList, String loanCategory) {
		LendingCategories lendingCategoryDetails = null;
		
		if(lendingCategoriesList.size() > 0) {
			for(LendingCategories categoryDetails : lendingCategoriesList) {
				if(categoryDetails.getCategory().equalsIgnoreCase(loanCategory)) {
					lendingCategoryDetails = categoryDetails;
					break;
				}
			}
		}
		
		return lendingCategoryDetails;
	}
	
	private Map<String, Object> fetchLoanHistory(Long merchantId) {
		Map<String, Object> loanHistoryDetails = new LinkedHashMap<>();
		Map<String, Object> loanHistory = new LinkedHashMap<>();
		List<Map<String, Object>> loanHistoryList = new ArrayList<>();
		LendingApplication lendingApplication = lendingApplicationDao.fetchLatestOpenApplication(merchantId);
		if(lendingApplication != null) {
			Map<String, Object> shopAndSelectedLoanDetails = fetchShopAndSelectedLoanDetails(lendingApplication);
			loanHistoryDetails.put("shopDetails", shopAndSelectedLoanDetails.get("shopDetails"));
			loanHistoryDetails.put("selectedLoan", shopAndSelectedLoanDetails.get("selectedLoan"));
			
			String lendingApplicationStatus = lendingApplication.getStatus();
			loanHistoryDetails.put("applicationStatus", lendingApplicationStatus);
			if(lendingApplicationStatus.equals("pending_verification") || lendingApplicationStatus.equals("approved")) {
				loanHistoryDetails.put("statusTitle", "Application submitted successfully!");
				loanHistoryDetails.put("statusMessage", "Your Application ID is " + lendingApplication.getExternalLoanId() + ". Our executive will visit you for verification. Please keep a cheque of total loan amount & a proof of ownership ready. Your loan will be disbursed within 24 hours after verification.");
			}else if(lendingApplicationStatus.equals("rejected")) {
				loanHistoryDetails.put("showReapply", true);
				loanHistoryDetails.put("statusTitle", "Verification Failed!");
				loanHistoryDetails.put("statusMessage", "We regret to inform you that we are unable to process your application as it does not meet the guidelines for document assessment.");
			}
			
			if(!lendingApplicationStatus.equals("pending_verification")) {
				if(lendingApplicationStatus.equals("approved")) {
					loanHistory.put("id",lendingApplication.getApplicationId());
					loanHistory.put("amount",lendingApplication.getLoanAmount());
					loanHistory.put("start_date","");
					loanHistory.put("end_date","");
					loanHistory.put("status","INTRANSFER");
					loanHistory.put("loan_status_title","Loan Approved");
					loanHistory.put("loan_status_message","The amount will reflect in your bank account within 48 hours.");
					loanHistory.put("repaid",0);
					loanHistory.put("due",lendingApplication.getRepayment());
					
					loanHistoryList.add(loanHistory);
				}
				
				//List<LoanDetails> loanDetailsList = loanDetailsDao.findByMerchantId(merchantId);
				List<LendingPaymentSchedule> loanDetailsList = lendingPaymentScheduleDao.findByMerchantIdOrderByIdDesc(merchantId);
				LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId, "ACTIVE");
				
				for(LendingPaymentSchedule loanDetails : loanDetailsList) {
					String title = "";
					String message = "";
					if(loanDetails.getStatus().equals("INTRANSFER")) {
		                title = "Loan Approved";
						message = "The amount will reflect in your  bank account within 48 hours.";
					}
					
					if(activeLoan != null) {
						loanHistory.put("repaid", activeLoan.getPaidAmount());
						loanHistory.put("due",activeLoan.getTotalPayableAmount());
					} else {
						loanHistory.put("repaid",0);
						loanHistory.put("due", loanDetails.getTotalPayableAmount());
					}
					loanHistory.put("id",loanDetails.getId());
					loanHistory.put("amount",loanDetails.getLoanAmount());
					loanHistory.put("start_date",loanDetails.getStartDate());
					if(loanDetails.getStatus().equals("closed")) {
						loanHistory.put("end_date",loanDetails.getUpdatedAt());
					}else {
						loanHistory.put("end_date",null);
					}
					loanHistory.put("status",loanDetails.getStatus());
					loanHistory.put("loan_status_title",title);
					loanHistory.put("loan_status_message",message);
					loanHistoryList.add(loanHistory);
				}
			}
			List<Map<String, Object>> documents = fetchAndSetDocumentsDetail(lendingApplication.getApplicationId(), merchantId);
			loanHistoryDetails.put("applicationId", lendingApplication.getApplicationId());
			loanHistoryDetails.put("loanHistory", loanHistoryList);
			loanHistoryDetails.put("documents", documents);
		}
		return loanHistoryDetails;
	}
	
	private Map<String, Object> fetchShopAndSelectedLoanDetails(LendingApplication lendingApplication) {
		Map<String, Object> shopAndSelectedLoanDetails = new LinkedHashMap<>();
		shopAndSelectedLoanDetails.put("shopDetails", LoanUtil.prepareShopDetailsForClient(lendingApplication));
		shopAndSelectedLoanDetails.put("selectedLoan", LoanUtil.prepareSelectedLoanForClient(lendingApplication));
		return shopAndSelectedLoanDetails;
	}
	
	private List<Map<String, Object>> fetchAndSetDocumentsDetail(Long applicationId, Long merchantId) {
		List<Map<String, Object>> documents = new ArrayList<>();
		List<DocumentsIdProof> documentsIdProofList = documentsIdProofDao.findByMerchantIdAndApplicationId(merchantId, applicationId);
		for(DocumentsIdProof documentsIdProof : documentsIdProofList) {
			Map<String, Object> document = new LinkedHashMap<>();
			document.put("proof_type", documentsIdProof.getProofType());
			document.put("id", documentsIdProof.getId());
			document.put("single_page_document", documentsIdProof.getSinglePage() == 0 ? false : true );
			documents.add(document);
		}
		return documents;
	}
	
	private Map<String, Object> prepareResponse(List<Map<String, Object>> eligibility, Map<String, Object> loanDetails, Boolean eligibleFlag) {
		Map<String, Object> response = new LinkedHashMap<> ();
		Map<String, Object> details = new LinkedHashMap<> ();
		
		response.put("success", true);
		
		details.put("eligible", eligibleFlag);
		if(loanDetails.get("loanHistory") != null) {
			details.put("loan_history", loanDetails.get("loanHistory"));
		}else {
			details.put("loan_history", new ArrayList());
		}
		details.put("eligibility", eligibility);
		
		Map<String, Object> loanApplication = prepareLoanApplication(loanDetails);
		details.put("loan_application", loanApplication);
		
		response.put("details", details);
		return response;
	}
	
	private Map<String, Object> prepareLoanApplication(Map<String, Object> loanDetails) {
		Map<String, Object> loanApplication = new LinkedHashMap<> ();
		if(loanDetails.get("shopDetails") != null) {
			loanApplication.put("shop_details",loanDetails.get("shopDetails"));
		}else {
			loanApplication.put("shop_details",new LinkedHashMap());
		}
		if(loanDetails.get("selectedLoan") != null) {
			loanApplication.put("selected_loan",loanDetails.get("selectedLoan"));
		}else {
			loanApplication.put("selected_loan",new LinkedHashMap());
		}
		if(loanDetails.get("documents") != null) {
			loanApplication.put("documents",loanDetails.get("documents"));
		}else {
			loanApplication.put("documents",new ArrayList());
		}
		if(loanDetails.get("applicationStatus") != null) {
			loanApplication.put("application_status",loanDetails.get("applicationStatus"));
		}else {
			loanApplication.put("application_status","");
		}
		if(loanDetails.get("applicationId") != null) {
			loanApplication.put("application_id",loanDetails.get("applicationId"));
		}else {
			loanApplication.put("application_id","");
		}
		if(loanDetails.get("statusTitle") != null) {
			loanApplication.put("status_title",loanDetails.get("statusTitle"));
		}else {
			loanApplication.put("status_title","");
		}
		if(loanDetails.get("showReapply") != null && (Boolean)loanDetails.get("showReapply") == true) {
			loanApplication.put("reapply", false);
		}
		if(loanDetails.get("statusMessage") != null) {
			loanApplication.put("status_message",loanDetails.get("statusMessage"));
		}else {
			loanApplication.put("status_message","");
		}
		
		loanApplication.put("agreement","");
		return loanApplication;
	}
	
	public static void main(String[] args) {
		
	}
}
