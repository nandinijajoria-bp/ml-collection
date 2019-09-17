package com.bharatpe.lending.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

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
	
	private Boolean successFlag = true;
	private Boolean eligibleFlag = false;
	private List<Map<String, Object>> loanHistory = new ArrayList<>();
	private List<Map<String, Object>> eligibility = new ArrayList<>();
	private Map<String, Object> shopDetails = new LinkedHashMap<>();
	private Map<String, Object> selectedLoan = new LinkedHashMap<>();
	private List<Map<String, Object>> documents = new ArrayList<>();
	private String applicationStatus = "";
	private Long applicationId;
	private Long merchantId;
	private String statusTitle = "";
	private Boolean reapply = false;
	private Boolean showReapply = false;
	private String statusMessage = "";
	private String agreement = "";
	private String prevTenure;
	private Boolean isSubsequentLoan = false;
	

	public Map<String, Object> runService(HttpServletRequest request, HttpServletResponse response, @RequestBody CommonAPIRequest commonAPIRequest) {
		Map<String, Object> resp;
		
		this.merchantId = Long.parseLong(request.getAttribute("merchantId").toString());
		
		initialize();
		
		Merchant merchant = merchantDao.findValidMerchant(this.merchantId);
		Merchant merchantDIY = merchantDao.findValidMerchantForDIY(this.merchantId);
		if(merchant != null || merchantDIY != null) {
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(this.merchantId,"ACTIVE");
			
			if(merchantBankDetail != null) {
				String ifsc = merchantBankDetail.getIfscCode();
				if(ifsc != null || ifsc != "") {
					ifsc = ifsc.substring(0,4);
					
					List<BankList> bankList = bankListDao.fetchByIfsc(ifsc);
					
					if(bankList == null || bankList.isEmpty() ) {
						logger.info("LoanDetails bankList not found for merchant {} and ifsc : {}",this.merchantId, ifsc);
						resp = prepareResponse();
						return resp;
					}
				}
			}
			
			List<MerchantSummary> merchantSummaryList = merchantSummaryDao.fetchActiveMerchantLoan(this.merchantId);
			
			if(merchantSummaryList.size() == 1) {
				
				checkLastPaymentSchedule();
				fetchEligibleLoans(merchantSummaryList.get(0).getLoanType());
				
				String toBeEligibleLoanType = "FIRST_TO_BE_ELIGIBLE";
				if(merchantSummaryList.get(0).getLoanType().equals("SUBSEQUENT")) {
					toBeEligibleLoanType = "SUBSEQUENT_TO_BE_ELIGIBLE";
				}
				fetchEligibleLoans(toBeEligibleLoanType);
				
				fetchLoanHistory();
			}
		}else {
			logger.info("LoanDetails No valid merchant for merchantId : {}", this.merchantId);
		}	
		resp = prepareResponse();
		
		return resp;
	}
	
	
	private void checkLastPaymentSchedule() {
		LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findLatestLendingPaymentScheduleByMerchantId(this.merchantId);
		if(lendingPaymentSchedule != null) {
			String status = lendingPaymentSchedule.getStatus();
			if(status.equals("CLOSED")) {
				this.isSubsequentLoan = true;
				LendingApplication lendingApplication = lendingApplicationDao.findByApplicationId(lendingPaymentSchedule.getApplicationId());
				if(lendingApplication != null) {
					this.prevTenure = lendingApplication.getTenure();
				}
			}
		}
	}
	
	private void fetchEligibleLoans(String loanType) {
		List<AvailableLoan> availableLoanList = availableLoanDao.findByMerchantIdAndTypeOrderByAmountDesc(this.merchantId, loanType);
		for(AvailableLoan availableLoan : availableLoanList) {
			List<LendingCategories> lendingCategoriesList = lendingCategoryDao.findByCategory(availableLoan.getCategory());
			if(lendingCategoriesList.size() == 1) {
				Map<String, Object> elegibleLoan = new LinkedHashMap<>();
				
				if(availableLoan.getAmount() != 5000 || lendingCategoriesList.get(0).getTenureMonths() == 1.00 ) {
					lendingCategoriesList.get(0).setProcessingFee("0");
				}else {
					lendingCategoriesList.get(0).setInterestRate(Double.valueOf(0));
				}
				elegibleLoan.put("loan_amount", availableLoan.getAmount());
				elegibleLoan.put("category", lendingCategoriesList.get(0).getCategory());
				elegibleLoan.put("processing_fee", lendingCategoriesList.get(0).getProcessingFee());
				elegibleLoan.put("duration", lendingCategoriesList.get(0).getPayableDays());
				elegibleLoan.put("tenure", lendingCategoriesList.get(0).getPayableConverter());
				
				int edi = (int) Math.ceil((availableLoan.getAmount() + (availableLoan.getAmount() * (lendingCategoriesList.get(0).getInterestRate() / 100) * lendingCategoriesList.get(0).getTenureMonths()) + Integer.parseInt(lendingCategoriesList.get(0).getProcessingFee())) / lendingCategoriesList.get(0).getPayableDays());
				
				elegibleLoan.put("edi", edi);
				
				if(lendingCategoriesList.get(0).getInterestRate() != 0) {
					Double interestRate = (((edi * lendingCategoriesList.get(0).getPayableDays() - availableLoan.getAmount()) / availableLoan.getAmount()) / lendingCategoriesList.get(0).getTenureMonths()) * 100;
					lendingCategoriesList.get(0).setInterestRate(interestRate);
				}
				elegibleLoan.put("interest_rate", lendingCategoriesList.get(0).getInterestRate());
				elegibleLoan.put("repayment", Math.round(lendingCategoriesList.get(0).getPayableDays() * edi));
				if(loanType.equals("FIRST_TO_BE_ELIGIBLE") || loanType.equals("SUBSEQUENT_TO_BE_ELIGIBLE")) {
					elegibleLoan.put("option_enable", false);
					this.eligibility.add(elegibleLoan);
				}else {
					elegibleLoan.put("option_enable", true);
//					if(this.isSubsequentLoan == true) {
//						if((this.prevTenure.equals("2 Weeks") || this.prevTenure.equals("1 Months")) && lendingCategoriesList.get(0).getPayableConverter().equals("3 Months")) {
//							this.eligibility.add(elegibleLoan);
//						}else if(this.prevTenure.equals("3 Months") && lendingCategoriesList.get(0).getPayableConverter().equals("6 Months")) {
//							this.eligibility.add(elegibleLoan);
//						}else if(this.prevTenure.equals("6 Months") && lendingCategoriesList.get(0).getPayableConverter().equals("12 Months")) {
//							this.eligibility.add(elegibleLoan);
//						}
//					}else {
						this.eligibility.add(elegibleLoan);
//					}
				}
			}
		}
	}
	
	private void fetchLoanHistory() {
		Map<String, Object> loanHistory = new LinkedHashMap<>();
		LendingApplication lendingApplication = lendingApplicationDao.fetchLatestOpenApplication(this.merchantId);
		if(lendingApplication != null) {
			setShopAndSelectedLoanDetails(lendingApplication);
			
			String lendingApplicationStatus = lendingApplication.getStatus();
			this.applicationStatus = lendingApplicationStatus;
			if(lendingApplicationStatus.equals("pending_verification") || lendingApplicationStatus.equals("approved")) {
				this.statusTitle = "Application submitted successfully!";
	            this.statusMessage = "Your Application ID is " + lendingApplication.getExternalLoanId() + ". Our executive will visit you for verification. Please keep a cheque of total loan amount & a proof of ownership ready. Your loan will be disbursed within 24 hours after verification.";
				//this.statusMessage = "Your loan application will be processed within 24 hours after document verification.Your Application ID is " + lendingApplication.getExternalLoanId() + ".";
			}else if(lendingApplicationStatus.equals("rejected")) {
				this.showReapply = true;
				this.statusTitle = "Verification Failed!";
	            this.statusMessage = "We regret to inform you that we are unable to process your application as it does not meet the guidelines for document assessment.";
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
					
					this.loanHistory.add(loanHistory);
				}
				
				List<LoanDetails> loanDetailsList = loanDetailsDao.findByMerchantId(this.merchantId);
				for(LoanDetails loanDetails : loanDetailsList) {
					String title = "";
					String message = "";
					if(loanDetails.getStatus().equals("INTRANSFER")) {
		                title = "Loan Approved";
						message = "The amount will reflect in your  bank account within 48 hours.";
					}
					
					LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndStatus(this.merchantId, "ACTIVE");
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
					this.loanHistory.add(loanHistory);
				}
			}
			fetchAndSetDocumentsDetail(lendingApplication.getApplicationId());
		}
	}
	
	private void setShopAndSelectedLoanDetails(LendingApplication lendingApplication) {
		this.shopDetails.put("business_name", lendingApplication.getBusinessName());
		this.shopDetails.put("shop_number", lendingApplication.getShopNumber());
		this.shopDetails.put("street_address", lendingApplication.getStreetAddress());
		this.shopDetails.put("area", lendingApplication.getArea());
		this.shopDetails.put("landmark", lendingApplication.getLandmark());
		this.shopDetails.put("pincode", lendingApplication.getPincode());
		this.shopDetails.put("city", lendingApplication.getCity());
		this.shopDetails.put("state", lendingApplication.getState());
		
		this.selectedLoan.put("category",lendingApplication.getCategory());
		this.selectedLoan.put("processing_fee",lendingApplication.getProcessingFee());
		this.selectedLoan.put("duration",lendingApplication.getPayableDays());
		this.selectedLoan.put("loan_amount",lendingApplication.getLoanAmount());
		this.selectedLoan.put("option_enable",1);
		this.selectedLoan.put("edi",lendingApplication.getEdi());
		this.selectedLoan.put("interest_rate",lendingApplication.getInterestRate());
		this.selectedLoan.put("repayment",lendingApplication.getRepayment());
		this.selectedLoan.put("tenure",lendingApplication.getTenure());
	}
	
	private void fetchAndSetDocumentsDetail(Long applicationId) {
		List<DocumentsIdProof> documentsIdProofList = documentsIdProofDao.findByMerchantIdAndApplicationId(this.merchantId, applicationId);
		for(DocumentsIdProof documentsIdProof : documentsIdProofList) {
			Map<String, Object> documents = new LinkedHashMap<>();
			documents.put("proof_type", documentsIdProof.getProofType());
			documents.put("id", documentsIdProof.getId());
			documents.put("single_page_document", documentsIdProof.getSinglePage());
			this.documents.add(documents);
		}
	}
	
	private Map<String, Object> prepareResponse() {
		Map<String, Object> response = new LinkedHashMap<> ();
		Map<String, Object> details = new LinkedHashMap<> ();
		
		response.put("success", this.successFlag);
		
		details.put("eligible", this.eligibleFlag);
		details.put("loan_history", this.loanHistory);
		details.put("eligibility", this.eligibility);
		
		Map<String, Object> loanApplication = prepareLoanApplication();
		details.put("loan_application", loanApplication);
		
		response.put("details", details);
		return response;
	}
	
	private Map<String, Object> prepareLoanApplication() {
		Map<String, Object> loanApplication = new LinkedHashMap<> ();
		loanApplication.put("shop_details",this.shopDetails);
		loanApplication.put("selected_loan",this.selectedLoan);
		loanApplication.put("documents",this.documents);
		loanApplication.put("application_status",this.applicationStatus);

		if(this.applicationId != null) {
			loanApplication.put("application_id",this.applicationId);
		}else {
			loanApplication.put("application_id","");
		}
		
		loanApplication.put("status_title",this.statusTitle);
		
		if(showReapply == true) {
			loanApplication.put("reapply",this.reapply);
		}
		
		loanApplication.put("status_message",this.statusMessage);
		
		loanApplication.put("agreement",this.agreement);
		return loanApplication;
	}
	
	private void initialize() {
		this.successFlag = true;
		this.eligibleFlag = false;
		this.loanHistory = new ArrayList<>();
		this.eligibility = new ArrayList<>();
		this.shopDetails = new LinkedHashMap<>();
		this.selectedLoan = new LinkedHashMap<>();
		this.documents = new ArrayList<>();
		this.applicationStatus = "";
		this.statusTitle = "";
		this.reapply = false;
		this.showReapply = false;
		this.statusMessage = "";
		this.agreement = "";
		this.prevTenure = "";
		this.isSubsequentLoan = false;
	}
}
