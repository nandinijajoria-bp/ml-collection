package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingPrebookTargetDao;
import com.bharatpe.lending.dto.LendingApplicationRequestDTO;
import com.bharatpe.lending.dto.LendingApplicationResponseDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.dto.TncDto;
import com.bharatpe.lending.entity.LendingPrebookTarget;
import com.bharatpe.lending.handlers.GupShupOTPHandler;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanCalculationUtil.LoanBreakupDetail;
import com.bharatpe.lending.util.LoanUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LendingApplicationService {
	private Logger logger = LoggerFactory.getLogger(LendingApplicationService.class);
	
	@Autowired
	LendingCitiesDao lendingCitiesDao;
	
	@Autowired
	LendingApplicationDao lendingApplicationDao;
	
	@Autowired
	AvailableLoanDao availableLoanDao;
	
	@Autowired
	LendingCategoryDao lendingCategoryDao;

	@Autowired
	LendingAuditTrialDao lendingAuditTrialDao;
	
	@Autowired
	MerchantSummarySnapshotDao merchantSummarySnapshotDao;
	
	@Autowired
	MerchantSummaryDao merchantSummaryDao;

	@Autowired
	EligibleLoanDao eligibleLoanDao;

	@Value("${experian.enable:true}")
	Boolean EXPERIAN_ENABLED;

	@Autowired
	GupShupOTPHandler gupShupOTPHandler;
	
	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;

	public LendingApplicationResponseDTO createApplication(Merchant merchant, RequestDTO<LendingApplicationRequestDTO> requestDTO) {
		LendingApplicationResponseDTO lendingApplicationResponse;
		LendingApplication lendingApplication;
		Long merchantId = merchant.getId();
		LendingApplicationRequestDTO lendingApplicationRequest = requestDTO.getPayload();

		if(lendingApplicationRequest.getApplicationId() != null && lendingApplicationRequest.getApplicationId() > 0) {
			lendingApplication = lendingApplicationDao.findByIdAndMerchantAndStatus(lendingApplicationRequest.getApplicationId(), merchant, "draft");
			if(lendingApplication == null) {
				logger.info("No application found in draft status for given application id {}", lendingApplicationRequest.getApplicationId());
				lendingApplicationResponse = new LendingApplicationResponseDTO();
				lendingApplicationResponse.setSuccess(false);
				return lendingApplicationResponse;
			}
			lendingApplication = updateApplication(lendingApplication, lendingApplicationRequest);
			lendingApplicationDao.save(lendingApplication);
		}else {
			List<EligibleLoan> eligibleLoans = new ArrayList<>();
			List<AvailableLoan> availableLoan = new ArrayList<>();
			if (EXPERIAN_ENABLED) {
				eligibleLoans = eligibleLoanDao.findByMerchantIdAndCategory(merchantId, lendingApplicationRequest.getCategory());
				if(eligibleLoans == null || eligibleLoans.isEmpty()) {
					logger.info("No loan available for Merchant {} and category {}", merchantId, lendingApplicationRequest.getCategory());
					lendingApplicationResponse = new LendingApplicationResponseDTO();
					lendingApplicationResponse.setSuccess(false);
					return lendingApplicationResponse;
				}
			} else {
				availableLoan = availableLoanDao.findByMerchantIdAndCategory(merchantId, lendingApplicationRequest.getCategory());
				if(availableLoan == null || availableLoan.isEmpty()) {
					logger.info("No loan available for Merchant {} and category {}", merchantId, lendingApplicationRequest.getCategory());
					lendingApplicationResponse = new LendingApplicationResponseDTO();
					lendingApplicationResponse.setSuccess(false);
					return lendingApplicationResponse;
				}
			}
			MerchantSummary summary =  merchantSummaryDao.getByMerchantId(merchant.getId());
			if (EXPERIAN_ENABLED) {
				lendingApplication = createApplication(merchant, eligibleLoans.get(0), lendingApplicationRequest);
			} else {
				lendingApplication = createApplication(merchant, availableLoan.get(0), lendingApplicationRequest);
			}
			lendingApplication.setLatitude(requestDTO.getMeta().getLatitude());
			lendingApplication.setLongitude(requestDTO.getMeta().getLongitude());
			lendingApplication.setIp(requestDTO.getMeta().getIp());
			lendingApplication.setTotalLoansCount(summary == null || summary.getTotalLoansCount() == null ? 0 : summary.getTotalLoansCount());
			lendingApplicationDao.save(lendingApplication);
			if (summary != null) {
				createMerchantSummarySnapshot(merchant, lendingApplication, summary);
			}
			createStatusAuditTrail(lendingApplication);
		}

		logger.info("Loan Application saved : {}",lendingApplication);
		return prepareAPIResponse(lendingApplication);
	}

	private LendingApplication updateApplication(LendingApplication lendingApplication, LendingApplicationRequestDTO lendingApplicationRequest) {
		lendingApplication.setBusinessName(lendingApplicationRequest.getBusinessName());
		lendingApplication.setShopNumber(lendingApplicationRequest.getShopNumber());
		lendingApplication.setStreetAddress(lendingApplicationRequest.getStreetAddress());
		lendingApplication.setArea(lendingApplicationRequest.getArea());
		lendingApplication.setLandmark(lendingApplicationRequest.getLandmark());
		lendingApplication.setPincode(lendingApplicationRequest.getPincode());
		lendingApplication.setCity(lendingApplicationRequest.getCity());
		lendingApplication.setState(lendingApplicationRequest.getState());
		if (lendingApplicationRequest.getAlternativeContact() != null) {
			lendingApplication.setAlternateMobile(lendingApplicationRequest.getAlternativeContact().getPhoneNumber());
			lendingApplication.setAlternateName(lendingApplicationRequest.getAlternativeContact().getName());
		}
		return lendingApplication;
	}
	
	private LendingApplication createApplication(Merchant merchant, EligibleLoan eligibleLoan, LendingApplicationRequestDTO lendingApplicationRequest) {
		LendingApplication lendingApplication = new LendingApplication();
		LendingCategories lendingCategory = lendingCategoryDao.findByCategory(eligibleLoan.getCategory()).get(0);
		
		//LoanBreakupDetail breakupDetail = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategory);
		int processingFee = (int) Math.ceil(eligibleLoan.getAmount() * Double.parseDouble(lendingCategory.getProcessingFee()));

		lendingApplication.setEdi(Double.valueOf(eligibleLoan.getEdi()));
		lendingApplication.setIoEdi(Double.valueOf(eligibleLoan.getIoEdi()));
		lendingApplication.setRepayment(Double.valueOf(eligibleLoan.getRepayment()));
		lendingApplication.setInterestRate(lendingCategory.getInterestRate());
		lendingApplication.setProcessingFee((double) processingFee);
		lendingApplication.setDisbursalAmount(eligibleLoan.getAmount() - processingFee);
		lendingApplication.setStatus("draft");
		lendingApplication.setMode("AUTO");
		lendingApplication.setMerchant(merchant);
		lendingApplication.setLoanAmount(eligibleLoan.getAmount());
		lendingApplication.setCategory(eligibleLoan.getCategory());
		lendingApplication.setTenure(lendingCategory.getPayableConverter());
		lendingApplication.setTenureInMonths(lendingCategory.getTenureMonths().intValue());
		lendingApplication.setPayableDays((long) lendingCategory.getPayableDays());
		lendingApplication.setEdiFreeDays(lendingCategory.getEdiFreeDays());
		lendingApplication.setIoPayableDays(lendingCategory.getIoPayableDays());
		lendingApplication.setLoanConstruct(eligibleLoan.getLoanConstruct());
		lendingApplication.setLoanType(eligibleLoan.getLoanType());
		lendingApplication = updateApplication(lendingApplication, lendingApplicationRequest);

		return lendingApplication;
	}

	private LendingApplication createApplication(Merchant merchant, AvailableLoan availableLoan, LendingApplicationRequestDTO lendingApplicationRequest) {
		LendingApplication lendingApplication = new LendingApplication();
		LendingCategories lendingCategory = lendingCategoryDao.findByCategory(availableLoan.getCategory()).get(0);

		LoanBreakupDetail breakupDetail = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategory, null);

		lendingApplication.setEdi(Double.valueOf(breakupDetail.getEdi()));
		lendingApplication.setIoEdi(Double.valueOf(breakupDetail.getIoEdi()));
		lendingApplication.setRepayment(Double.valueOf(breakupDetail.getRepayment()));
		lendingApplication.setInterestRate(breakupDetail.getEffectiveInterestRate());
		lendingApplication.setProcessingFee(Double.valueOf(breakupDetail.getProcessingFee()));
		lendingApplication.setDisbursalAmount(Double.valueOf(breakupDetail.getDisbursementAmount()));
		lendingApplication.setStatus("draft");
		lendingApplication.setMode("AUTO");
		lendingApplication.setMerchant(merchant);
		lendingApplication.setLoanAmount(availableLoan.getAmount());
		lendingApplication.setCategory(availableLoan.getCategory());
		lendingApplication.setTenure(lendingCategory.getPayableConverter());
		lendingApplication.setTenureInMonths(lendingCategory.getTenureMonths().intValue());
		lendingApplication.setPayableDays(Long.valueOf(lendingCategory.getPayableDays()));
		lendingApplication.setEdiFreeDays(lendingCategory.getEdiFreeDays());
		lendingApplication.setIoPayableDays(lendingCategory.getIoPayableDays());
		lendingApplication.setLoanConstruct(availableLoan.getLoanConstruct());

		lendingApplication = updateApplication(lendingApplication, lendingApplicationRequest);

		return lendingApplication;
	}

	private void createStatusAuditTrail(LendingApplication lendingApplication) {
		LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
		lendingAuditTrial.setMerchantId(lendingApplication.getMerchant().getId());
		lendingAuditTrial.setApplicationId(lendingApplication.getId());
		lendingAuditTrial.setLoanId("");
		lendingAuditTrial.setUserId(Long.parseLong("0"));
		lendingAuditTrial.setNewStatus("draft");
		lendingAuditTrial.setType("APP_STATUS");
		lendingAuditTrialDao.save(lendingAuditTrial);
	}
	
	private LendingApplicationResponseDTO prepareAPIResponse(LendingApplication lendingApplication) {
		LendingCategories lendingCategories = lendingCategoryDao.getByCategory(lendingApplication.getCategory());
		LendingApplicationResponseDTO lendingApplicationResponse = new LendingApplicationResponseDTO();
		LendingApplicationResponseDTO.LoanApplication loanApplication = lendingApplicationResponse.new LoanApplication();

		loanApplication.setApplicationId(lendingApplication.getId());
		loanApplication.setApplicationStatus(lendingApplication.getStatus());
		loanApplication.setSelectedLoan(LoanUtil.prepareSelectedLoanForClient(lendingApplication, lendingCategories));
		loanApplication.setShopDetails(LoanUtil.prepareShopDetailsForClient(lendingApplication));
		lendingApplicationResponse.setLoanApplication(loanApplication);
		lendingApplicationResponse.setSuccess(true);

		return lendingApplicationResponse;
	}
	
	public void createMerchantSummarySnapshot(Merchant merchant, LendingApplication application, MerchantSummary summary) {
		try {
			MerchantSummarySnapshot snapshot = new MerchantSummarySnapshot();
			List<Object[]> data = availableLoanDao.getMaxEligibilityDataForMerchant(merchant.getId());
			
			snapshot.setApplication(application);
			snapshot.setMerchant(merchant);
			snapshot.setLastTransactionDate(summary.getLastTransactionDate());
			snapshot.setTotalTxnCount(summary.getDailyTxnCount());
			snapshot.setTotalTxnAmount(summary.getDailyTxnAmount());
			snapshot.setCategory(summary.getCategory());
			snapshot.setAvgTpv(summary.getAvgTpv());
//			snapshot.setMaxEligibleLoanAmount(((BigDecimal) data.get(0)[0]).doubleValue());
//			snapshot.setEligibleLoanCategories((String) data.get(0)[1]);
			snapshot.setLoanType(summary.getLoanType());
			snapshot.setTpv1Mon(summary.getTpv1Mon());
			snapshot.setTpv2Mon(summary.getTpv2Mon());
			snapshot.setTpv3Mon(summary.getTpv3Mon());
			snapshot.setTxnDayCount1Mon(summary.getTxnDayCount1Mon());
			snapshot.setTxnDayCount2Mon(summary.getTxnDayCount2Mon());
			snapshot.setTxnDayCount3Mon(summary.getTxnDayCount3Mon());
			snapshot.setTotalTxns1Month(summary.getTotalTxns1Month());
			snapshot.setTotalTxns2Month(summary.getTotalTxns2Month());
			snapshot.setTotalTxns3Month(summary.getTotalTxns3Month());
			snapshot.setTotalLoansCount(summary.getTotalLoansCount());
			snapshot.setBpScore(summary.getBpScore());
			
			merchantSummarySnapshotDao.save(snapshot);
		} catch(Exception ex) {
			logger.error("Exception while creating merchant summary snapshot for merchant id {} and application id {}, Exception is {}", merchant.getId(), application.getId(), ex);
		}
	}
	
	public boolean checkLoanRequestPinCodeForLoanEligibilty(int pinCode) {
		try {
			LendingCities lendingCities=lendingCitiesDao.findActiveCityByPincode(pinCode);
			if(lendingCities==null) return false;
			return true;
		}
		catch(Exception e){
			logger.error("error occured while fetching the lending city details for pin code {}",pinCode);
		}
		return false;
	}

	public ResponseDTO sendOtp(Merchant merchant) {
		String message = "BharatPe: %code% is your OTP to register yourself on BharatPe Merchant App. BharatPe.com";
		Boolean otp = gupShupOTPHandler.sendOTP(merchant.getMobile(), message);
		if (otp) {
			logger.info("OTP sent on mobile: {} for merchant: {}", merchant.getMobile(), merchant.getId());
			ResponseDTO responseDTO = new ResponseDTO(true, null, null);
			responseDTO.setMobile(merchant.getMobile());
			return responseDTO;
		}
		return new ResponseDTO(false, "Unable to send otp", null);
	}
	
	public TncDto getTnc(Merchant merchant, long applicationId) {
		String html=populateHtml(merchant, applicationId);
		TncDto tnc=new TncDto();
		if(html==null) {
			tnc.setSuccess(false);
			tnc.setMessage("Error occured while fetching tnc");
			return tnc;
		}
		tnc.setSuccess(true);
		tnc.setHtmlString(html);
		return tnc;
	}
	
	public String populateHtml(Merchant merchant, long applicationId){
			Map<String, String> detail=getDetails(merchant, applicationId);
			if(detail==null) {
				return null;
			}
			String html =
					"<p><br /><br /><br /></p>\n" + 
					"<p><strong>Loan Sanction Letter</strong></p>\n" + 
					"<p>&nbsp;</p>\n" + 
					"<p><span style=\"font-weight: 400;\">This sanction letter includes the Most Important Terms and Conditions (MITC).</span></p>\n" + 
					"<p>&nbsp;</p>\n" + 
					"<table>\n" + 
					"<tbody>\n" + 
					"<tr>\n" + 
					"<td>\n" + 
					"<p><span style=\"font-weight: 400;\">Name of the Borrower</span></p>\n" + 
					"</td>\n" + 
					"<td>"+detail.getOrDefault("Name of the Borrower", "")+"</td>\n" + 
					"</tr>\n" + 
					"<tr>\n" + 
					"<td>\n" + 
					"<p><span style=\"font-weight: 400;\">Loan Amount (in INR)</span></p>\n" + 
					"</td>\n" + 
					"<td>"+detail.getOrDefault("Loan Amount", "")+"</td>\n" + 
					"</tr>\n" + 
					"<tr>\n" + 
					"<td>\n" + 
					"<p><span style=\"font-weight: 400;\">Tenure (in Months)</span></p>\n" + 
					"</td>\n" + 
					"<td>"+detail.getOrDefault("Tenure", "")+"</td>\n" + 
					"</tr>\n" + 
					"<tr>\n" + 
					"<td>\n" + 
					"<p><span style=\"font-weight: 400;\">Rate of Interest (per annum)</span></p>\n" + 
					"</td>\n" + 
					"<td>"+detail.getOrDefault("Rate of Interest", "")+"</td>\n" + 
					"</tr>\n" + 
					"<tr>\n" + 
					"<td>\n" + 
					"<p><span style=\"font-weight: 400;\">Penal Interest (per annum)</span></p>\n" + 
					"</td>\n" + 
					"<td>"+detail.getOrDefault("Penal Interest", "")+"</td>\n" + 
					"</tr>\n" + 
					"<tr>\n" + 
					"<td>\n" + 
					"<p><span style=\"font-weight: 400;\">Security</span></p>\n" + 
					"</td>\n" + 
					"<td>\n" + 
					"<p><span style=\"font-weight: 400;\">Unsecured</span></p>\n" + 
					"</td>\n" + 
					"</tr>\n" + 
					"<tr>\n" + 
					"<td>\n" + 
					"<p><span style=\"font-weight: 400;\">Co-Applicant Details</span></p>\n" + 
					"</td>\n" + 
					"<td>\n" + 
					"<p><span style=\"font-weight: 400;\">NA</span></p>\n" + 
					"</td>\n" + 
					"</tr>\n" + 
					"</tbody>\n" + 
					"</table>\n" + 
					"<p>&nbsp;</p>\n" + 
					"<p><span style=\"font-weight: 400;\">Please note that this is an indicative offer letter and should not be binding upon the lender or the borrower. The</span></p>\n" + 
					"<p><span style=\"font-weight: 400;\">loan is funded through NDX P2P Private Limited (&ldquo;LiquiLoans&rdquo;) by way of lenders on its platform and sourced</span></p>\n" + 
					"<p><span style=\"font-weight: 400;\">by BharatPe, which is sourcing partner of LiquiLoans.</span></p>\n" + 
					"<p><br /><br /><br /></p>\n" + 
					"<p><strong>LOAN AGREEMENT</strong></p>\n" + 
					"<p>&nbsp;</p>\n" + 
					"<p><strong>Loan Details&nbsp;</strong></p>\n" + 
					"<p><span style=\"font-weight: 400;\">Loan ID: &nbsp;&nbsp;"+detail.getOrDefault("Loan ID", "")+" &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span> <span style=\"font-weight: 400;\">Date: &nbsp;&nbsp;&nbsp;&nbsp; "+detail.getOrDefault("Date", "")+" </span> <span style=\"font-weight: 400;\">&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; Loan Amount (INR):&nbsp;&nbsp; "+detail.getOrDefault("Loan Amount", "")+"</span><span style=\"font-weight: 400;\"><br /></span><span style=\"font-weight: 400;\"><br /></span><span style=\"font-weight: 400;\"> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; </span></p>\n" + 
					"<p><span style=\"font-weight: 400;\">Tenure (Months): "+detail.getOrDefault("Tenure", "")+"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span></p>\n" +  
					"<p><span style=\"font-weight: 400;\">Amount of EDI: "+detail.getOrDefault("Amount of EDI", "")+"&nbsp;</span></p>\n" + 
					"<p>&nbsp;</p>\n" + 
					"<p><span style=\"font-weight: 400;\">Flat Rate of Interest (% per month):&nbsp;&nbsp; "+detail.getOrDefault("Interest", "")+" &nbsp;&nbsp;&nbsp;</span></p>\n" +
					"<p><span style=\"font-weight: 400;\">Flat Rate of Interest (% per annum):&nbsp;&nbsp; "+detail.getOrDefault("Rate of Interest", "")+"</span></p>\n" +
					"<p>&nbsp;</p>\n" + 
					"<p><span style=\"font-weight: 400;\">Registered Mobile Number:&nbsp;&nbsp; "+detail.getOrDefault("Registered Mobile Number", "")+" &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span></p>\n" + 
					"<p><span style=\"font-weight: 400;\">Location:&nbsp;&nbsp; "+detail.getOrDefault("Location", "")+"&nbsp;</span></p>\n" + 
					"<p>&nbsp;</p>\n" + 
					"<p><span style=\"font-weight: 400;\">EDI Due Date - Every day from Monday to Saturday from the successive day of disbursal</span></p>\n" + 
					"<p><span style=\"font-weight: 400;\"><br /></span><span style=\"font-weight: 400;\">Shop/Business Address:&nbsp;&nbsp;"+detail.getOrDefault("Shop/Business Address", "")+" &nbsp;&nbsp; </span><span style=\"font-weight: 400;\"><br /></span><span style=\"font-weight: 400;\">Landmark:&nbsp;&nbsp;"+detail.getOrDefault("Landmark", "")+" &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; </span><span style=\"font-weight: 400;\"><br /></span><span style=\"font-weight: 400;\">PIN:&nbsp;&nbsp; "+detail.getOrDefault("PIN", "")+" &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; City:&nbsp;&nbsp; "+detail.getOrDefault("City", "")+" &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;State:&nbsp;&nbsp; "+detail.getOrDefault("State", "")+"&nbsp; </span> <span style=\"font-weight: 400;\">Email:"+detail.getOrDefault("Email", "")+"</span></p>\n" + 
					"<p><span style=\"font-weight: 400;\"><br /><br /></span></p>\n" + 
					"<p><span style=\"font-weight: 400;\">Banking Details: The complete Loan Amount shall be credited to the &lsquo;Borrowers Authorised Bank Account&rsquo; as defined in the Agreement and as specified below&nbsp;</span></p>\n" + 
					"<p><span style=\"font-weight: 400;\"><br /></span><span style=\"font-weight: 400;\">Bank Name: "+detail.getOrDefault("Bank Name", "")+"&nbsp;</span></p>\n" + 
					"<p><span style=\"font-weight: 400;\">Account No. "+detail.getOrDefault("Account No", "")+"&nbsp;</span></p>\n" + 
					"<p><span style=\"font-weight: 400;\">Account Type: "+"SAVINGS"+"</span></p>\n" + 
					"<p><span style=\"font-weight: 400;\">IFSC Code: "+detail.getOrDefault("IFSC Code", "")+"&nbsp;</span></p>\n" +  
					"<p><br /><br /><br /><br /><br /><br /><br /><br /><br /><br /><br /><br /><br /><br /><br /><br /><br /></p>\n" + 
					"<p><span style=\"font-weight: 400;\">This Loan Agreement is made&nbsp;</span></p>\n" + 
					"<p><span style=\"font-weight: 400;\">BETWEEN lenders (as detailed in annexure) arranged by NDX P2P Private Limited (&ldquo;LiquiLoans&rdquo;), a P2P NBFC platform registered with RBI, hereinafter referred to as &ldquo;Lender&rdquo;</span></p>\n" + 
					"<p><span style=\"font-weight: 400;\">AND</span></p>\n" + 
					"<p><span style=\"font-weight: 400;\">The Borrower, material particulars whereof are described and set out above, of the OTHER PART. The Lender and Borrower are hereinafter collectively referred to as &lsquo;Parties&rsquo; and individually as &lsquo;Party&rsquo;.&nbsp;</span></p>\n" + 
					"<p><span style=\"font-weight: 400;\">WHEREAS:</span></p>\n" + 
					"<ol>\n" + 
					"<li><span style=\"font-weight: 400;\"> The Lender through LiquiLoans is desirous of providing loans/credit facilities to various customers.&nbsp;</span></li>\n" + 
					"<li><span style=\"font-weight: 400;\"> BharatPe is engaged in the business of, inter alia, providing aggregator services to Merchant(s)/ User(s) by offering a single unified QR code to the Merchant/User for accepting push payments through third party UPI apps / net-banking.</span></li>\n" + 
					"<li><span style=\"font-weight: 400;\"> The Borrower has requested the Lender to grant the Loan to the Borrower and the Lender, relying upon the representations made and information provided by the Borrower, has agreed to grant the Loan to the Borrower, on the terms and conditions mutually agreed and contained in this Agreement and in other Loan Documents, upto the maximum principal amount as mentioned above, in its sole and absolute discretion.</span></li>\n" + 
					"</ol>\n" + 
					"<p><strong>NOW, THEREFORE</strong><span style=\"font-weight: 400;\">, in consideration of the foregoing and other good and valid consideration, the receipt and adequacy of which is expressly acknowledged, the Parties hereby agree as follows: Declaration / Undertaking/Representation&nbsp;</span></p>\n" + 
					"<ol>\n" + 
					"<li><span style=\"font-weight: 400;\"> I/We hereby apply for a finance facility from lenders on LiquiLoans platform in partnership with Resilient Innovation Private Limited (&ldquo;BharatPe&rdquo;) as stated in this Application Form and declare that all the particulars, information and details provided in this Application Form and the documents submitted by me/us are true, correct, complete and up-to-date in all respects and that I/We have not withheld any material information.&nbsp;</span></li>\n" + 
					"<li><span style=\"font-weight: 400;\"> I/We have read and understood the fees and charges applicable to the finance facility (ies) that I/We may avail from time to time and confirm that no insolvency proceedings or suits for recovery of outstanding dues, monies or property (ies) and/or any criminal proceedings have been initiated and / or are pending against me / us.&nbsp;</span></li>\n" + 
					"<li><span style=\"font-weight: 400;\"> I/We declare that I/We have not received any request for or made any payment in cash, bearer&rsquo;s cheques or of any other kind in connection with this Application Form from/to any person.&nbsp;</span></li>\n" + 
					"<li><span style=\"font-weight: 400;\"> I/We hereby authorize LIQUILOANS/BharatPe to exchange or share information and details relating to my application to its group companies or any third party, as may be required or deemed fit, for the purpose of processing this loan application and/or related offerings or other products / services that I/We may apply for from time to time.&nbsp;</span></li>\n" + 
					"<li><span style=\"font-weight: 400;\"> By submitting this application, I/We hereby expressly authorize LIQUILOANS/BharatPe to send me communications regarding loans, insurance and other products from LIQUILOANS/BharatPe, its group companies and / or third parties through telephone calls / SMSs / emails / post etc. including but not limited to promotional communications. I/We confirm that I shall not challenge receipt of such communications by me as unsolicited communication, defined under&nbsp;</span></li>\n" + 
					"</ol>\n" + 
					"<p><br /><br /><br /></p>\n" + 
					"<p><span style=\"font-weight: 400;\">TRAI Regulations on Unsolicited Commercial Communications. I/We understand that I/ We can at any time opt not to receive any telecommunication by registering under the Do Not Call Registry.</span></p>\n" + 
					"<ol start=\"6\">\n" + 
					"<li><span style=\"font-weight: 400;\"> I/We understand and acknowledge that LIQUILOANS has the absolute discretion, without assigning any reasons to reject my application and that LIQUILOANS/BharatPe is not answerable / liable to me, in any manner whatsoever, for rejecting my application.&nbsp;&nbsp;</span></li>\n" + 
					"<li><span style=\"font-weight: 400;\"> That LIQUILOANS shall have the right to make disclosure of any information relating to me/us including personal information, details in relation to Loan, defaults, security, etc to the Credit Information Bureau of India (CIBIL) and/or any other governmental/regulatory/statutory or private agency / entity, credit bureau, RBI, the Bank's other branches/ subsidiaries / affiliates / rating agencies, service providers, other banks / financial institutions, any third parties, any assignes/potential assignees or transferees, who may need, process and publish the information in such manner and through such medium as it may be deemed necessary by the publisher/ Bank/ RBI, including publishing the name as part of willful defaulter's list from time to time, as also use for KYC information verification, credit risk analysis, or for other related purposes.</span></li>\n" + 
					"<li><span style=\"font-weight: 400;\"> I / We agrees and accept that LIQUILOANS/BharatPe may in its sole discretion, by its self or through authorised persons, advocate, agencies, bureau, etc. verify any information given, check credit references, employment details and obtain credit reports to determine creditworthiness from time to time.&nbsp;</span></li>\n" + 
					"<li><span style=\"font-weight: 400;\"> That the funds shall be used for the purpose for which loan has been applied and will not be used for speculative or antisocial purpose.&nbsp;</span></li>\n" + 
					"<li><span style=\"font-weight: 400;\"> I/We, hereby confirm that I contacted LIQUILOANS/BharatPe for my requirement of personal loan and no representative has emphasized me directly / indirectly to take the loan.&nbsp;</span></li>\n" + 
					"<li><span style=\"font-weight: 400;\"> I/We here by confirm having read and understood the Master Terms and Conditions applicable to Personal Loans</span></li>\n" + 
					"<li><span style=\"font-weight: 400;\"> I authorize BharatPe / LIQUILOANS to evaluate my transaction history on the BharatPe platform in order to check my eligibility for the loan.</span></li>\n" + 
					"<li><span style=\"font-weight: 400;\"> I hereby authorize BharatPe to Credit the Loan amount to the settlement &lsquo;Bank Account&rsquo; mentioned above and also deduct money from the settlement it is performing as part of BharatPe services and credit it to LIQUILOANS to facilitate the repayment of loan</span></li>\n" + 
					"<li><span style=\"font-weight: 400;\"> Lender on the platform understands that it will make upto 12% interest on the loan</span></li>\n" + 
					"</ol>\n" + 
					"<p>&nbsp;</p>\n" + 
					"<p><span style=\"font-weight: 400;\">Application Name:"+detail.getOrDefault("Name of the Borrower", "")+"</span></p>\n" + 
					"<p><span style=\"font-weight: 400;\">Platform:"+"Android"+"</span></p>\n" + 
					"<p><span style=\"font-weight: 400;\">IP Address:"+detail.getOrDefault("IP Address", "")+"</span></p>\n" + 
					"<p><span style=\"font-weight: 400;\">Mobile Number for eSign:"+detail.getOrDefault("Registered Mobile Number", "")+"</span></p>\n" + 
					"<p><span style=\"font-weight: 400;\">Timestamp:"+detail.getOrDefault("Timestamp", "")+"&nbsp;</span></p>\n";
			return html;
	}
	
	public Map<String,String> getDetails(Merchant merchant, long applicationId){
		Map<String,String> detail=new HashMap<>();
		try {
			LendingApplication lendingApplication=lendingApplicationDao.findByIdAndMerchant(applicationId, merchant);
			if(lendingApplication == null) {
				logger.error("Lending application not found for id {}",applicationId);
				return null;
			}
			detail.put("Name of the Borrower",merchant.getBeneficiaryName());
			detail.put("Loan Amount", lendingApplication.getLoanAmount().toString());
			detail.put("Tenure", lendingApplication.getTenureInMonths().toString());
			detail.put("Rate of Interest", Double.toString(lendingApplication.getInterestRate() * 12));
			detail.put("Interest", Double.toString(lendingApplication.getInterestRate()));
			detail.put("Penal Interest", "NA");
			detail.put("Loan ID", "Will be generated later");
			detail.put("Date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
			detail.put("Amount of EDI",lendingApplication.getEdi().toString());
			detail.put("Registered Mobile Number",merchant.getMobile());
			detail.put("Location",lendingApplication.getCity());
			detail.put("Shop/Business Address", lendingApplication.getShopNumber()+", "+lendingApplication.getStreetAddress()+", "+lendingApplication.getArea());
			detail.put("Landmark", lendingApplication.getLandmark());
			detail.put("PIN", lendingApplication.getPincode().toString());
			detail.put("City", lendingApplication.getCity());
			detail.put("State", lendingApplication.getState());
			detail.put("Email", lendingApplication.getEmail());
			MerchantBankDetail merchantBankDetail=merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
			if(merchantBankDetail!=null) {
				detail.put("Bank Name", merchantBankDetail.getBankName());
				detail.put("Account No", merchantBankDetail.getAccountNumber());
				detail.put("Account Type", merchantBankDetail.getAccType());
				detail.put("IFSC Code", merchantBankDetail.getIfscCode());
			}
			detail.put("IP Address", lendingApplication.getIp());
			detail.put("Timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
			return detail;
		}
		catch(Exception e) {
			logger.error("Error occured while fetching details",e);
			return null;
		}
	}
}
