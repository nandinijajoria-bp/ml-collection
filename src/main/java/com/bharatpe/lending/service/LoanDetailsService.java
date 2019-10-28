package com.bharatpe.lending.service;

import java.util.ArrayList;
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
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.dao.MerchantDao;
import com.bharatpe.common.dao.MerchantSummaryDao;
import com.bharatpe.common.entities.AvailableLoan;
import com.bharatpe.common.entities.BankList;
import com.bharatpe.common.entities.DocumentsIdProof;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingCategories;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.entities.LoanDetails;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.common.entities.MerchantSummary;
import com.bharatpe.common.objects.CommonAPIRequest;
import com.bharatpe.lending.dao.BankListDao;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;

@Service
public class LoanDetailsService {
	private Logger logger = LoggerFactory.getLogger(LoanDetailsService.class);
	
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

	public Map<String, Object> fetchLoanDetails(Merchant merchant, CommonAPIRequest commonAPIRequest) {
		Map<String, Object> resp;
		List<Map<String, Object>> eligibility = new ArrayList<>();
		Map<String, Object> details = new LinkedHashMap<>();
		Boolean eligibleFlag = false;
		Boolean validBankFlag = true;
		
		Long merchantId = merchant.getId();
		
		Merchant merchantAgentCheck = merchantDao.findValidMerchant(merchantId);
		Merchant merchantDIY = merchantDao.findValidMerchantForDIY(merchantId);
		if(merchantAgentCheck != null || merchantDIY != null) {
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchantId,"ACTIVE");
			
			if(isValidBank(merchantBankDetail, merchantId)) {
				List<MerchantSummary> merchantSummaryList = merchantSummaryDao.fetchActiveMerchantLoan(merchantId);
				
				if(merchantSummaryList.size() == 1) {
					
					//checkLastPaymentSchedule(merchantId);
					eligibility = fetchEligibleLoans(merchantSummaryList.get(0).getLoanType(), merchantId, eligibility);
					
					String toBeEligibleLoanType = "FIRST_TO_BE_ELIGIBLE";
					if(merchantSummaryList.get(0).getLoanType().equals("SUBSEQUENT")) {
						toBeEligibleLoanType = "SUBSEQUENT_TO_BE_ELIGIBLE";
					}
					eligibility = fetchEligibleLoans(toBeEligibleLoanType, merchantId, eligibility);
					
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
	
	private Boolean isValidBank(MerchantBankDetail merchantBankDetail, Long merchantId) {
		Boolean flag = true;
		
		if(merchantBankDetail != null) {
			String ifsc = merchantBankDetail.getIfscCode();
			if(ifsc != null || ifsc != "") {
				ifsc = ifsc.substring(0,4);
				
				List<BankList> nonPaymentBankList = bankListDao.fetchNonPaymentBankList(ifsc);
				
				if(nonPaymentBankList == null || nonPaymentBankList.size() == 0) {
					logger.info("LoanDetails bankList not found for merchant {} and ifsc : {}",merchantId, ifsc);
					flag = false;
				}
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
	
	private List<Map<String, Object>> fetchEligibleLoans(String loanType, Long merchantId, List<Map<String, Object>> eligibility) {
		List<AvailableLoan> availableLoanList = availableLoanDao.findByMerchantIdAndTypeOrderByAmountDesc(merchantId, loanType);
		for(AvailableLoan availableLoan : availableLoanList) {
			List<LendingCategories> lendingCategoriesList = lendingCategoryDao.findByCategory(availableLoan.getCategory());
			if(lendingCategoriesList.size() == 1) {
				Map<String, Object> elegibleLoan = new LinkedHashMap<>();
				
				if(availableLoan.getAmount() == 5000 && lendingCategoriesList.get(0).getTenureMonths() == 1.00 ) {
//					lendingCategoriesList.get(0).setProcessingFee("0");
					elegibleLoan.put("processing_fee", lendingCategoriesList.get(0).getProcessingFee());
				}else {
//					lendingCategoriesList.get(0).setInterestRate(Double.valueOf(0));
					elegibleLoan.put("interest_rate", lendingCategoriesList.get(0).getInterestRate());
				}
				elegibleLoan.put("loan_amount", availableLoan.getAmount());
				elegibleLoan.put("category", lendingCategoriesList.get(0).getCategory());

				elegibleLoan.put("duration", lendingCategoriesList.get(0).getPayableDays());
				elegibleLoan.put("tenure", lendingCategoriesList.get(0).getPayableConverter());
				
				int edi = (int) Math.ceil((availableLoan.getAmount() + (availableLoan.getAmount() * (lendingCategoriesList.get(0).getInterestRate() / 100) * lendingCategoriesList.get(0).getTenureMonths()) + Integer.parseInt(lendingCategoriesList.get(0).getProcessingFee())) / lendingCategoriesList.get(0).getPayableDays());
				
				elegibleLoan.put("edi", edi);
				
				if(lendingCategoriesList.get(0).getInterestRate() != 0) {
					Double interestRate = (((edi * lendingCategoriesList.get(0).getPayableDays() - availableLoan.getAmount()) / availableLoan.getAmount()) / lendingCategoriesList.get(0).getTenureMonths()) * 100;
					lendingCategoriesList.get(0).setInterestRate(interestRate);
				}

				elegibleLoan.put("repayment", Math.round(lendingCategoriesList.get(0).getPayableDays() * edi));
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
				
				List<LoanDetails> loanDetailsList = loanDetailsDao.findByMerchantId(merchantId);
				for(LoanDetails loanDetails : loanDetailsList) {
					String title = "";
					String message = "";
					if(loanDetails.getStatus().equals("INTRANSFER")) {
		                title = "Loan Approved";
						message = "The amount will reflect in your  bank account within 48 hours.";
					}
					
					LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId, "ACTIVE");
					if(lendingPaymentSchedule != null) {
						loanHistory.put("repaid", lendingPaymentSchedule.getPaidAmount());
					}else {
						loanHistory.put("repaid",0);
					}
					loanHistory.put("id",loanDetails.getId());
					loanHistory.put("amount",loanDetails.getLoanAmount());
					loanHistory.put("start_date",loanDetails.getStartDate());
					loanHistory.put("end_date",loanDetails.getEndDate());
					loanHistory.put("status",loanDetails.getStatus());
					loanHistory.put("loan_status_title",title);
					loanHistory.put("loan_status_message",message);
					loanHistory.put("due",lendingPaymentSchedule.getTotalPayableAmount());
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
		Map<String, Object> shopDetails = new LinkedHashMap<>();
		Map<String, Object> selectedLoan = new LinkedHashMap<>();
		
		shopDetails.put("business_name", lendingApplication.getBusinessName());
		shopDetails.put("shop_number", lendingApplication.getShopNumber());
		shopDetails.put("street_address", lendingApplication.getStreetAddress());
		shopDetails.put("area", lendingApplication.getArea());
		shopDetails.put("landmark", lendingApplication.getLandmark());
		shopDetails.put("pincode", lendingApplication.getPincode());
		shopDetails.put("city", lendingApplication.getCity());
		shopDetails.put("state", lendingApplication.getState());
		shopAndSelectedLoanDetails.put("shopDetails", shopDetails);
		
		selectedLoan.put("category",lendingApplication.getCategory());
		selectedLoan.put("processing_fee",lendingApplication.getProcessingFee());
		selectedLoan.put("duration",lendingApplication.getPayableDays());
		selectedLoan.put("loan_amount",lendingApplication.getLoanAmount());
		selectedLoan.put("option_enable",1);
		selectedLoan.put("edi",lendingApplication.getEdi());
		selectedLoan.put("interest_rate",lendingApplication.getInterestRate());
		selectedLoan.put("repayment",lendingApplication.getRepayment());
		selectedLoan.put("tenure",lendingApplication.getTenure());
		shopAndSelectedLoanDetails.put("selectedLoan", selectedLoan);
		
		return shopAndSelectedLoanDetails;
	}
	
	private List<Map<String, Object>> fetchAndSetDocumentsDetail(Long applicationId, Long merchantId) {
		List<Map<String, Object>> documents = new ArrayList<>();
		List<DocumentsIdProof> documentsIdProofList = documentsIdProofDao.findByMerchantIdAndApplicationId(merchantId, applicationId);
		for(DocumentsIdProof documentsIdProof : documentsIdProofList) {
			Map<String, Object> document = new LinkedHashMap<>();
			document.put("proof_type", documentsIdProof.getProofType());
			document.put("id", documentsIdProof.getId());
			document.put("single_page_document", documentsIdProof.getSinglePage());
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
}
