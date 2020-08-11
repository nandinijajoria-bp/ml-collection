package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.util.DateTimeUtil;
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

	@Autowired
	LendingRedCitiesDao lendingRedCitiesDao;
	
	@Autowired
	RedisNotificationService redisNotificationService;

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
			if(isLdc()){
				lendingApplication.setLender("LDC");
			}
			else {
				lendingApplication.setLender("HINDON");
			}		
			lendingApplicationDao.save(lendingApplication);
			if (summary != null) {
				createMerchantSummarySnapshot(merchant, lendingApplication, summary);
			}
			createStatusAuditTrail(lendingApplication);
		}
		redisNotificationService.sendNotificationForAppliedApplication(merchantId, lendingApplication);
		logger.info("Loan Application saved : {}",lendingApplication);
		return prepareAPIResponse(lendingApplication);
	}
	
	private boolean isLdc() {
		Long todayApplicationCount=lendingApplicationDao.getLendingApplicationCountBetweenDate(DateTimeUtil.getStartTimeFromDateTime(new Date()), DateTimeUtil.getEndTimeFromDateTime(new Date()));
		return todayApplicationCount>25?false:true;
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
			LendingRedCities redCity = lendingRedCitiesDao.findByPincode(pinCode);
			if(lendingCities==null && redCity != null) return false;
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
		String html;
		String lender=detail.get("Lender");
		if(lender.equalsIgnoreCase("LDC")) {
			html=getLdcTnc(detail);
		}
		else {
			html ="<p><br /><br /><br /></p>\n" +
						"<p><strong>Loan Proposal Letter</strong></p>\n" +
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
						"<p><span style=\"font-weight: 400;\">This sanction letter is subject to terms & conditions.</span></p>\n" +
						"<p><span style=\"font-weight: 400;\">The said proposed loan amount will be financed by Hindon Merchantile Limited (“Hindon”) as Lender to the Borrower, as provided by its sourcing partner, Resilient Innovations Private Limited (BharatPe).</span></p>\n" +
						"<p><span style=\"font-weight: 400;\">Please note that this is an indicative in nature and should not be binding upon either party, Lender or Borrower. </span></p>\n" +
						"   <p class=\"p4\">&nbsp;</p>\n" +
						"   <p class=\"p4\">&nbsp;</p>\n" +
						"    <p class=\"p5\"><strong>Declaration / Undertaking/Representation by Borrower</strong></p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li6\">I/We hereby apply for a finance facility as proposition made by <strong>Resilient Innovation Private Limited (&ldquo;BharatPe&rdquo;)</strong> as in terms of Loan Agreement as below and declare that all the particulars, information and details provided and other documents submitted by me/us are true, correct, complete and up-to-date in all respects and that I/We have not withheld any material information.</li>\n" +
						"    <li class=\"li6\">I/We hereby authorize <span class=\"s2\">Lender</span>/BharatPe to exchange or share information and details relating to my application to its group companies or any third party, as may be required or deemed fit, for the purpose of processing this loan application and/or related offerings or other products / services that I/We may apply for from time to time.</li>\n" +
						"    <li class=\"li6\">By submitting this application, I/We hereby expressly authorize <span class=\"s2\">Lender</span>/BharatPe to send me communications regarding loans, insurance and other products from <span class=\"s2\">Lender</span>/BharatPe, its group<span class=\"Apple-converted-space\">&nbsp; </span>companies and / or third parties through telephone calls / SMSs / emails / post etc. including but not limited to promotional, transactional communications. I/We confirm that I shall not challenge receipt of such communications by me as unsolicited communication, defined under TRAI Regulations on Unsolicited Commercial Communications.</li>\n" +
						"    <li class=\"li6\">I authorize BharatPe / <span class=\"s2\">Lender</span> to evaluate my transaction history on the BharatPe platform in order to check my eligibility for the loan and understand and acknowledge that <span class=\"s2\">Lender</span>/BharatPe has the absolute discretion, without assigning any reasons to reject my application and that <span class=\"s2\">Lender</span>/BharatPe is not answerable / liable to me, in any manner whatsoever,<span class=\"Apple-converted-space\">&nbsp; </span>for<span class=\"Apple-converted-space\">&nbsp; </span>rejecting<span class=\"Apple-converted-space\">&nbsp; </span>my application.</li>\n" +
						"    <li class=\"li6\">I / We agrees and accept that <span class=\"s2\">Lender</span>/BharatPe may in its sole discretion, by its self or through authorised persons, advocate, agencies, bureau, etc. verify any information given, check credit references, employment details and obtain credit reports to determine creditworthiness from time to time.</li>\n" +
						"    </ul>\n" +
						"    <p class=\"p7\">&nbsp;</p>\n" +
						"    <p class=\"p8\" style=\"text-align: center;\"><strong>LOAN AGREEMENT</strong></p>\n" +
						"    <p class=\"p9\">&nbsp;</p>\n" +
						"    <p class=\"p10\">&nbsp;</p>\n" +
						"    <p class=\"p11\">This <strong>Loan Agreement</strong> (&ldquo;<strong>Agreement</strong>&rdquo;) is made and executed at the place mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) and on the date mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) by and between:</p>\n" +
						"    <p class=\"p11\">HINDON MERCANTILE LIMITED, a non-banking finance company, having its registered office at Unit No 307, Third Floor Plot\n" +
						"No. H-1 Garg Tower, NSP, Pitampura Delhi (hereinafter referred to as the &ldquo;<strong>Lender</strong>&rdquo; which expression shall, unless repugnant to the context or meaning thereof, be deemed to mean and include its successor(s) and permitted assign(s)) of the One Part;</p>\n" +
						"    <p class=\"p11\"><strong>AND</strong></p>\n" +
						"    <p class=\"p11\"><strong><em>[Details from the Schedule I]</em></strong>, hereto as the borrower and co-borrower (if any) (wherever the context so requires) (hereinafter referred to as the &ldquo;<strong>Borrower</strong>&rdquo; which expression shall, unless repugnant to the context or meaning thereof, be deemed to mean and include his/her/their heir(s), successor(s), legal representative(s), executor(s), administrator(s) and permitted assign(s)) of the Other Part.</p>\n" +
						"    <p class=\"p11\">The Lender and the Borrower are hereinafter collectively referred to as the &ldquo;<strong>Parties</strong>&rdquo; and each individually as the &ldquo;<strong>Party</strong>&rdquo;.</p>\n" +
						"    <p class=\"p11\"><strong>WHEREAS</strong>:</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">The Lender is a non-banking financing company, registered with the Reserve Bank of India, having registration no. B-14-00518],\n" +
						"and is <em>inter alia</em> engaged in the business of advancing loans and other financial facilities.</li>\n" +
						"    <li class=\"li11\">The Borrower has approached the Lender and has requested for grant of loan facility for the purpose of <strong><em>as mentioned in Schedule I </em></strong>and in reliance on the acceptance of the terms, conditions, assurances, representations and warranties of the Borrower, the Lender has agreed to grant loan facility to the Borrower, subject to the terms and conditions contained in this Agreement.</li>\n" +
						"    <li class=\"li11\">The Parties hereto are now desirous of <em>inter alia</em> entering into this Agreement to set out the terms and conditions in relation to the Facility.</li>\n" +
						"    </ul>\n" +
						"    <p class=\"p11\"><strong>Now, therefore, in view of the foregoing and in consideration of the mutual covenants and agreements herein set forth, the parties hereby agree as follows:</strong></p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\">DEFINITIONS AND INTERPRETATION</li>\n" +
						"    </ul>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li13\"><strong>Definitions</strong></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p14\">&ldquo;<strong>Borrower Account</strong>&rdquo; means the following bank account of the Borrower <strong><em>as mentioned in Schedule I</em></strong>, unless otherwise notified by the Borrower in writing.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Due Date</strong>&rdquo; means the date(s) on which any amounts from the Borrower to the Lender including the principal amounts of the Facility, interest and/or any other Outstanding Amounts, fall due as per <strong>Schedule I</strong> (<em>Terms of the Facility</em>) of this Agreement or any other Facility Document, or as demanded by the Lender in accordance with a Facility Document.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Events of Default</strong>&rdquo; shall have the meaning ascribed to it under the terms herein.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Facility</strong>&rdquo; means the facility amount mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>).</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Final Settlement Date</strong>&rdquo; means the date on which all the Outstanding Amounts have been fully paid and the Facility has been irrevocably discharged to the satisfaction of the Lender.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Financing Documents</strong>&rdquo; means this Agreement and such other documents as may be executed or required to be executed between the Lender and/or the Borrower in order to perfect or validate this Agreement.</p>\n" +
						"    <p class=\"p14\">&ldquo;<strong>Government Authority</strong>&rdquo; means any governmental department, commission, board, bureau, agency, regulatory authority, instrumentality, court or other judicial, quasi-judicial or administrative body, whether central, state, provincial or local, having jurisdiction over the subject matter or matters in question. For avoidance of doubt, it is hereby clarified that the term &ldquo;Government Authority&rdquo; does not include any bank/financial institution acting solely in its capacity as a lender to the Borrower.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Interest Rate</strong>&rdquo; means the rate of interest mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>).</p>\n" +
						"    <p class=\"p14\">&ldquo;<strong>Laws</strong>&rdquo; means any statute, law, regulation, ordinance, rule, judgment, order, decree, bye-laws, rule of law, directives, guidelines policy, requirement, or any governmental restriction or any similar form of decision of, or determination by, or any interpretation or administration having the force of law of any of the foregoing, by any Government Authority having jurisdiction over the matter in subject, whether in effect as of the date of this Agreement or hereafter.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Loan Application</strong>&rdquo; means the application made by the Borrower in the form specified by the Lender for availing the Facility and where the context so requires, all other information, particulars submitted by the Borrower to the Lender with a view to avail the Facility.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Material Adverse Effect</strong>&rdquo; means adverse effect on: (a) the ability of the Borrower to observe and perform in a timely manner their respective obligations under any of the Financing Documents to which it is or would be a party or; (b) the legality, validity, binding nature or enforceability of any of the Financing Documents; or (d) the Business or financial condition of the Borrower which is reasonably likely to impair its ability to service the Facility as and when becoming due; or (e) the rights and remedies of the Lender under the Financing Documents.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Outstanding Amounts</strong>&rdquo; mean principal amount of the Facility outstanding from time to time, and all interests, Penal Interest, prepayment charges, costs, commissions, fees &amp; charges, expenses and other amounts due under or in respect of this Agreement.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Payment Mechanism</strong>&rdquo; means ECS, ACH, NEFT, RTGS or payment by way of cheque, as the case may be.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Person</strong>&rdquo; shall, unless specifically provided otherwise, mean any individual, corporation, partnership, association of persons, company, joint stock company, trust or Government Authority, as the context may admit.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Prepayment</strong>&rdquo; means the premature repayment of the Facility as per the terms and conditions approved by the Lender in this regard and prevailing at the time of such premature repayment by the Borrower.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Purpose</strong>&rdquo; means the purpose for which the Facility has been agreed to be utilised by the Borrower, as mentioned in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) to this Agreement.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>RBI</strong>&rdquo; means the Reserve Bank of India.</p>\n" +
						"    <p class=\"p15\">&ldquo;<strong>Tax</strong>&rdquo; means any tax, levy, impost, duty or other charge or withholding of a similar nature (including any penalty or interest payable in connection with the failure to pay or delay in paying any of the same).</p>\n" +
						"    <p class=\"p16\">&ldquo;<strong>Term</strong>&rdquo; or &ldquo;<strong>Tenure</strong>&rdquo; means the period as specified in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) of this Agreement, within which the Facility has to be repaid by the Borrower to the Lender along with interest, cost, expenses, fees &amp; charges and other amount as specified in this Agreement.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li5\"><strong>Principles of Interpretation</strong>: In this Agreement, unless the context otherwise requires:</li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p14\"><strong>T</strong>he headings are for convenience or reference only and shall not be used in and shall not affect the construction or interpretation of this Agreement.</p>\n" +
						"    <p class=\"p14\"><strong>T</strong>he words &ldquo;include&rdquo; and &ldquo;including&rdquo; are to be construed without limitation.</p>\n" +
						"    <p class=\"p14\"><strong>W</strong>ords importing a particular gender shall include all genders.</p>\n" +
						"    <p class=\"p14\"><strong>R</strong>eferences to any law shall include references to such law as it may, after the date of this Agreement, from time to time be amended, supplemented or re-enacted.</p>\n" +
						"    <p class=\"p14\"><strong>T</strong>he Schedule(s) annexed to this Agreement form an integral part of this Agreement and will be of full force and effect as though they were expressly set out in the body of the Agreement;</p>\n" +
						"    <p class=\"p14\"><strong>R</strong>eference to any agreement, including this Agreement, deed, document, instrument, rule, regulation, notification, statute or the like shall mean a reference to the same as may have been duly amended, modified or replaced. For the avoidance of doubt, a document shall be construed as amended, modified or replaced only if such amendment, modification or replacement is executed in compliance with the provisions of such document(s);</p>\n" +
						"    <p class=\"p17\"><strong>I</strong>n the event of any disagreement or dispute between the Lender and the Borrower regarding the materiality or reasonableness of any matter, the opinion of Lender as to the materiality shall be final and binding on the Borrower.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\">FACILITY</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\"><strong>The Lender at the request of the Borrower agrees to grant to the Borrower and the Borrower agrees to borrow from the Lender, the Facility, on the basis and subject to the covenants and terms and conditions set forth herein. </strong></li>\n" +
						"    <li class=\"li12\"><strong>If in future, the Borrower approaches the Lender for grant of an additional facility or increase in the amount of Facility, the Lender shall have the sole discretion for granting the same and the Lender can either proceed with<span class=\"Apple-converted-space\">&nbsp; </span>the execution of fresh loan agreement with the Borrower or execute a supplemental loan agreement.</strong></li>\n" +
						"    <li class=\"li12\"><strong>Disbursement shall be made directly and only to Borrower. </strong></li>\n" +
						"    <li class=\"li12\"><strong>The Lender shall have the right to adjust and/or set off any Outstanding Amounts or other dues against any subsequent amount of the Facility due to be disbursed by the Lender to the Borrower.</strong></li>\n" +
						"    <li class=\"li12\"><strong>Notwithstanding anything stated herein, the continuation of the Facility shall be at sole and absolute discretion of the Lender and the Lender may at any time in its sole discretion and without assigning any reason call upon the Borrower to pay the Outstanding Balance and upon such demand by the Lender, the Borrower shall, within 48 hours of being so called upon, pay the whole of the Outstanding Balance to the Lender without any delay or demur. </strong></li>\n" +
						"    <li class=\"li12\"><strong>The Lender may, at its discretion, maintain appropriate entries in its books of accounts in relation to the Facility and such entries shall be final and binding upon the Borrower.</strong></li>\n" +
						"    </ul>\n" +
						"    <li class=\"li12\">MODE OF DISBURSAL</li>\n" +
						"    </ul>\n" +
						"    <p class=\"p18\"><strong>The Facility shall be made by the Lender by RTGS/NEFT to the Borrower Account and charges for the same, if any, shall be borne by the Borrower. Such charges shall be deemed to form part of the Outstanding Amounts.</strong></p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\">INTEREST</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\"><strong>The Borrower shall pay interest on the principal amount of the Facility outstanding from time to time at the Interest Rate mentioned in </strong>Schedule I<strong> (<em>Terms of the Facility</em>) to this Agreement. </strong></li>\n" +
						"    <li class=\"li12\"><strong>Interest on the Facility will begin to accrue in favour of the Lender as and from the date of disbursal of amount of Facility. Interest shall accrue from day to day and shall be computed on the basis of 365 days a year (irrespective of leap year) and the actual number of days elapsed. However, in the event of the Borrower intends to Prepay the Facility, Interest would be calculated up to the date of actual prepayment, subject to payment of Prepayment charges as applicable.</strong></li>\n" +
						"    <li class=\"li12\"><strong>Without prejudice to the Lender's rights, Interest and any other Outstanding Amounts shall be charged/debited to the Borrower Account.</strong></li>\n" +
						"    <li class=\"li12\"><strong>Lender at its sole discretion, may change in the prevailing rate of interest on the Facility, either due to change in its policies, or issuance of RBI guidelines and notifications with respect to the same or for any other reason whatsoever and in such an event the term 'Interest Rate' shall for all purposes mean the revised interest rate, which shall always be construed as agreed to be paid by the Borrower and hereby secured.</strong></li>\n" +
						"    </ul>\n" +
						"    <li class=\"li12\">FEES &amp; REPAYMENT</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\"><strong>The Borrower shall, on or before or after the disbursement of the Facility, bear, pay and reimburse to the Lender all cost, fee, charges, including stamp duty charges, applicable on the Financing Documents and any increased costs expenses incurred and/or to be incurred by the Lender, on a full indemnity basis, in connection with the Facility.</strong></li>\n" +
						"    <li class=\"li12\"><strong>The Borrower shall, on or before the disbursement of the Facility, pay to the Lender processing/service fee calculated at the rate provided in </strong>Schedule I<strong> (<em>Terms of the Facility</em>) to this Agreement, on the amount of the Facility sanctioned by the Lender along-with applicable GST. The processing/service fee shall be non-refundable. The Lender shall be entitled to recover the non-refundable processing fees and GST by way of deduction from Drawdown(s). </strong></li>\n" +
						"    <li class=\"li13\">All fees and charges payable by the Borrower to the Lender under this Clause shall be reimbursed by the Borrower to the Lender within 7 (seven) days from the date of notice of demand from the Lender and shall be debited to the Borrower Account.</li>\n" +
						"    <li class=\"li13\">The Lender have appointed Resilient Innovations Private Limited (BharatPe) having registered office at 90/20, Malviya Nagar, New Delhi 110017 as its collection agent and for such other services as agreed between the Lender and BharatPe, from time to time. All Outstanding Balance shall be payable/paid as may be directed &amp; advised by Lender/BharatPe.</li>\n" +
						"    <li class=\"li13\">The Borrower shall repay the Facility, if not demanded earlier by Lender pursuant to a Financing Document, as stipulated in and in accordance with and subject to the terms and conditions of the Repayment Schedule set out in <strong>Schedule II </strong>(<em>Repayment Schedule</em>).</li>\n" +
						"    <li class=\"li13\">No notice, reminder or intimation in any manner shall be given by the Lender to the Borrower regarding its obligation and responsibility to ensure prompt and regular payment of the Outstanding Amounts to the Lender on Due Dates. It shall be entirely the Borrower's responsibility to ensure prompt and regular payment of the Outstanding Amount.</li>\n" +
						"    <li class=\"li13\">The Borrower agrees that the repayment of the amount of Facility together with interest, Penal Interest, if any, and all such other sums due and payable by the Borrower to the Lender shall be payable to the Lender Account by way of a Payment Mechanism approved by the Lender, provided that the Lender may, at its sole discretion, require the Borrower to adopt or switch to any alternative mode of payment and the Borrower shall comply with such request, without demur or delay. The Borrower undertakes to remit all Outstanding Amounts to the Lender on the respective Due Date.</li>\n" +
						"    <li class=\"li13\">Any instruction under the Payment Mechanism which is revoked/ dishonoured shall make the Borrower liable for payment of charges as per the prevailing rules of the Lender in force from time to time, in addition to any Penal Interest that may be levied by the Lender and without prejudice to the Lender's right to take appropriate legal action against the Borrower for such revocation / dishonour.</li>\n" +
						"    <li class=\"li13\">The Lender expressly reserves its right to call upon the Borrower to pay the whole or part of the Outstanding Amounts at any time after the date of first Drawdown in the event of a default by the Borrower under any Financing Document.</li>\n" +
						"    <li class=\"li13\">In the event of any change in Repayment Schedule (at the request of the Borrower or due to an Event of Default), the Borrower shall be liable to pay rescheduling charges at the rate specified in <strong>Schedule I</strong> (<em>Terms of the Facility</em>) to this Agreement. Such payment of rescheduling charges shall be in addition to any other rights and remedies available with the Lender in the Event of Default or otherwise.</li>\n" +
						"    </ul>\n" +
						"    <li class=\"li13\">SECURITY</li>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">The Borrower hereby agrees, undertakes and confirms that it shall deliver to the Lender such security, if applicable, as may be required pursuant to <strong>Schedule I </strong>(<em>Terms of the Facility</em>) to this Agreement, as security towards the payment of the Outstanding Amounts with the Lender named as the payee therein.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\">PENAL INTEREST</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\"><strong>Upon occurrence of any of the events mentioned in Article 13 below, the Borrower shall be liable to pay Penal Interest which shall be in addition to the Interest payable by the Borrower under Article 5.1.</strong></li>\n" +
						"    <li class=\"li12\"><strong>The Borrower expressly agrees that the rate of Penal Interest is a fair estimate of the loss likely to be suffered by the Lender by reason of such delay/default on the part of the Borrower.</strong></li>\n" +
						"    <li class=\"li12\"><strong>Penal Interest shall accrue from day to day and shall be computed on the basis of 365 (three hundred and sixty) days a year (irrespective of leap year).</strong></li>\n" +
						"    <li class=\"li12\"><strong>Penal Interest shall be computed for (i) in case the Penal Interest is payable due to default/delay in any payment, then the period commencing from the Due Date of payment of the amount in default/delay up to the payment of amount in default/delay along-with Penal Interest and (ii) in case of occurrence of any other Event of Default, for the period during which the Event of Default or breach, as the case may be, persists.</strong></li>\n" +
						"    </ul>\n" +
						"    <li class=\"li12\">PREPAYMENT / FORECLOSURE</li>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">The Borrower shall be entitled to prepay/ foreclose the Outstanding Amounts, subject to payment of prepayment charges as set out in <strong>Schedule I</strong> (<em>Terms of the Facility</em>).</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\">TAXES</li>\n" +
						"    </ul>\n" +
						"    <p class=\"p16\">The Borrower shall make all payments to be made by it hereunder without and free from any Tax deduction and/or other deduction and/or withholding and/or statutory levies/duties/charges (&ldquo;<strong>Withholding</strong>&rdquo;), unless a Withholding is required by Law.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\">PURPOSE OF THE FACILITY</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li13\">The Borrower undertakes and confirms that the entire Facility amount shall be utilized/ deployed only for the Purpose and for no other purpose that shall include without limitation to invest in share market, real estate or in any subsidiary/ associates of the Borrower.</li>\n" +
						"    <li class=\"li13\">Any default, fraud, legal incompetence during the currency of the limits, non-compliance of agreed terms and conditions, non-submission of required papers, any other irregularities by the Borrower will enable the Lender to recall the Facility.</li>\n" +
						"    <li class=\"li13\">The Borrower further confirms and/or undertakes that the Facility shall not be utilized for the following:</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">Subscription to or purchase of shares/debentures;</li>\n" +
						"    <li class=\"li11\">Extending unsecured loans to subsidiary company/ associates or for making inter corporate deposits;</li>\n" +
						"    <li class=\"li11\">Any speculative purposes or any anti-social purpose or any unlawful purpose.</li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <li class=\"li12\">COVENANTS</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li13\">The Borrower agrees to promptly notify, in writing, the Lender about any litigation, arbitration, investigative, regulatory or administrative proceeding/action having a Material Adverse Effect.</li>\n" +
						"    <li class=\"li13\">All terms and conditions of this Agreement including the Repayment Schedule in relation to the Facility shall remain same even if any amount under the Facility is being taken over by/assigned to any new lender.</li>\n" +
						"    <li class=\"li13\">The Borrower declares that all the amounts including the amount of own contribution paid/ payable in connection with the Facility, is/ shall be through legitimate source and does not/ shall not constitute an offence of money laundering under the Prevention of Money Laundering Act, 2002.</li>\n" +
						"    <li class=\"li13\">The Borrower shall perform, on request of the Lender, such acts as may be necessary to carry out the intent of the Financing Documents.</li>\n" +
						"    <li class=\"li13\">The Borrower shall deliver to the Lender in form and detail, such details, information, documents etc to the satisfaction of the Lender, as may reasonably be required, within such period as required by the Lender from time to time.</li>\n" +
						"    <li class=\"li13\">In case the Borrower is a body corporate, it shall not induct any person on the board of directors or as partners who have been identified as a wilful defaulter by the RBI. The Borrower confirms that neither it nor any member of its organisation has been declared as wilful defaulter.</li>\n" +
						"    <li class=\"li12\"><strong>The Borrower hereby agrees, undertakes and covenants that unless the Lender otherwise agrees in writing, so long as the Facility or any part thereof is outstanding and an Event of Default has occurred and continuing, until full and final payment of all money owing hereunder, the Borrower </strong>SHALL NOT<strong>:</strong></li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">Grant any loans; grant any credit (except in the ordinary course of business) to or for the benefit of any Person other than itself.</li>\n" +
						"    <li class=\"li11\">Allow its principal shareholders/ directors/ promoters/ partners to withdraw monies brought in by them or withdraw the profits earned in the business/capital invested in the business.</li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <li class=\"li12\">REPRESENTATIONS AND WARRANTIES</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">The Borrower hereby represents and warrants to the Lender on a continuing basis that:</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li13\"><span class=\"s1\"><strong>Confirmation of Loan Application</strong></span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">The Borrower acknowledges and confirms that all the factual information provided by the Borrower to the Lender in the Loan Application or otherwise in order to avail the Facility and any prior or subsequent information or explanation given to the Lender in this regard is true and accurate in all material respects as at the date it was provided and does not omit to state a material fact necessary in order to make the statements contained therein misleading in the light of the circumstances under which such statements were or are made.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li13\"><span class=\"s1\"><strong>Compliance with Laws</strong></span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">The Borrower has complied with all the applicable Laws and is not a party to any litigation, arbitration or administrative or regulatory proceedings or investigations of a material character and that the Borrower is not aware, to the best of its knowledge and belief, of any facts likely to give rise to such litigation, arbitration or administrative or regulatory proceedings or investigations or to material claims against the Borrower.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li13\"><span class=\"s1\"><strong>Litigation</strong></span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">Where applicable, the Borrower shall supply to the Lender, promptly upon becoming aware of them, details of any filing by any creditor (financial creditor or operational creditor) which are made or threatened against them, in accordance with the provisions of the Insolvency and Bankruptcy Code, 2016 or any analogous laws.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li13\"><span class=\"s1\"><strong>Compliance of Know Your Customer (KYC) Policy:</strong></span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">The Borrower is fully aware of the KYC Policy of the Lender and RBI and confirms that the information/clarification/documents/signage provided by it on its identity, address, authorised signatory, board resolution, PAN and all other material facts are true and correct and the transaction, etc. are <em>bonafide </em>and as per Law. The Borrower further confirms that it has disclosed all facts/information as are required to be disclosed for the adherence and compliance of the provisions related to the KYC Policy. The Lender reserve the right to recall the Facility or close the account in case the required documents are not provided by the Borrower to the Lender.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li13\"><strong>The Lender/BharatPe shall, without notice to or without any consent of the Borrower, be absolutely entitled and have full right, power and authority to make disclosure of any information relating to Borrower including personal information, details in relation to documents, Loan, defaults, security, obligations of Borrower, to the Credit Information Bureau of India (CIBIL) and/or any other governmental/regulatory/statutory or private agency/entity, credit bureau, RBI, the Lender&rsquo;s other branches/ subsidiaries / affiliates / rating agencies, service providers, other Lenders / financial institutions, any third parties, any assignees/potential assignees or transferees, who may need the information and may process the information, publish in such manner and through such medium as may be deemed necessary by the publisher/ Lender/ RBI, including publishing the name as part of willful defaulter&rsquo;s list from time to time, as also use for KYC information verification, credit risk analysis, or for other related purposes. The Borrower waives the privilege of privacy and privity of contract. </strong></li>\n" +
						"    <li class=\"li13\"><strong>The execution and delivery of this Agreement and documents to be executed in pursuance hereof, and the performance of the Borrower's obligations hereunder and thereunder does not and will not (i) contravene any applicable Law, statute or regulation or any judgment or decree to which any of the Borrowers and/or their Assets and/or business and/or their undertaking is subject, or (ii) conflict with or result in any breach of, any of the terms of or constitute default of any covenants, conditions and stipulations under any existing agreement or contract or binding to which any of the Borrowers are a party or subject or (iii) conflict or contravene any provision of the memorandum and the articles of association and/or any constituting/governing documents of Borrowers. </strong></li>\n" +
						"    <li class=\"li13\"><strong>The Borrower has informed the Lender about all loans/finances/advances availed by the Borrower from other banks/financial institutions/third parties up to the date of this Agreement to the Lender.</strong></li>\n" +
						"    <li class=\"li13\"><span class=\"s1\"><strong>No</strong> <strong>default</strong></span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p16\">The Borrower and/or its group companies, affiliates have no over dues/not defaulted in repayment of any amount due and payable to any other bank/financial institutions.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li13\"><span class=\"s1\"><strong>Material Adverse Effect</strong></span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">There are no facts or circumstances, conditions or occurrences, which could collectively or otherwise be expected to result in the Borrower being unable to perform their respective obligations under the Financing Documents to which they are expressed to be a party, or which could affect the legality, validity, binding nature or enforceability of this Agreement or other Financing Documents or is otherwise expected to have an Material Adverse Effect.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\">EVENT OF DEFAULT AND CONSEQUENCES</li>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">The Borrower expressly and irrevocably hereby agrees and declares that each of the following events or events similar thereto shall constitute an &ldquo;<strong>Events of Default</strong>&rdquo;: The following events shall constitute events of default (each an &ldquo;Event of Default&rdquo;), and upon the occurrence of any of them the entire Outstanding Balance shall become immediately due and payable by the Borrower and further enable the Lender inter alia to recall the entire Outstanding Balance and/or enforce any security and transfer/sell the same and/or take, initiate and pursue any actions/proceedings as deemed necessary by the Lender for recovery of the dues, or such other action as the Lender may deem fit: (a) Failure on Borrower&rsquo;s part to perform any of the obligations or terms or conditions or covenants applicable in relation to the Loan including under this document/other documents including non-payment in full of any part of the Outstanding Balance when due or when demanded by Lender/BharatPe; (b) any misrepresentations or misstatement by the Borrower; or (c) occurrence of any circumstance or event which adversely affects Borrower&rsquo;s ability/capacity to pay/repay the Outstanding Balance or any part thereof or perform any of the obligations; (d) the event of death, insolvency, cessation, failure in business of the Borrower, or change or termination of employment/profession/business for any reason whatsoever<span class=\"s3\">. </span></p>\n" +
						"    <p class=\"p17\">On and any time after the occurrence of Event of Default, the Lender may, without prejudice to any other rights that it may have under this Agreement or applicable Law (including right to accelerate payment obligations of the Borrower under the Financing Documents) take one or more of the following actions: (a) recall or declare the Outstanding Amounts to be forthwith due and payable, whereupon such amounts shall become forthwith due and payable without presentment, demand, protest or any other notice of any kind, all of which are hereby expressly waived, anything contained herein to the contrary notwithstanding;<strong> (b) </strong>exercise any and all rights specified in the Financing Documents including, without limitation, to enforce any security created/provided;<strong> (c) </strong>to initiate, appropriate proceedings for recovery of its dues by invoking the jurisdiction of appropriate court at its sole discretion, in addition to taking further action or actions under any other statute in force; and/or (d) exercise such other remedies as permitted or available under applicable law in the sole discretion of the Lender; and/or<strong> (e) </strong>disclose the name of the Borrower, and its promoters/directors/partners to RBI, TransUnion CIBIL and/or any other authorised agency.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li12\">SUCCESSION</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">In case of the death of the Borrower, where the Borrower is an individual and the Lender agrees to continue extending the Facility, the legal representative of the Borrower, with such other requirements as the Lender may deem fit.</li>\n" +
						"    </ul>\n" +
						"    <li class=\"li12\">MISCELLANEOUS</li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\"><span class=\"s1\">Governing Law and Jurisdiction</span></li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">This Agreement and the rights and obligations of the Parties hereunder shall be construed in accordance with and be governed by the laws of India.</li>\n" +
						"    <li class=\"li11\">The Parties agree that the courts in New Delhi shall have exclusive jurisdiction to settle any disputes which may arise out of or in connection with the Financing Documents.</li>\n" +
						"    <li class=\"li11\">The Borrower irrevocably waive any objection, now or in future, to the venue of any Proceedings being the courts at New Delhi or any claim that any such Proceedings have been brought in an inconvenient forum.</li>\n" +
						"    <li class=\"li11\">Nothing contained herein shall limit any right of the Lender to take Proceedings in any other court of competent jurisdiction, nor shall the taking of proceedings in one or more jurisdictions preclude the taking of proceedings in any other jurisdiction whether concurrently or not and the Borrower irrevocably waive any objection it may have now or in the future to the laying of the venue of any Proceedings on the grounds that such Proceedings have been brought in an inconvenient forum.</li>\n" +
						"    </ul>\n" +
						"    <li class=\"li11\"><span class=\"s1\">Arbitration</span></li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">Without prejudice to the other legal remedies available to the Lender under applicable law (including under the SARFAESI Act, 2002 and Insolvency and Bankruptcy Code, 2016), any dispute arising out of or in connection with the Financing Documents shall be referred to and finally resolved by arbitration under the Arbitration and Conciliation Act, 1996 (as amended from time to time).</li>\n" +
						"    <li class=\"li11\">The arbitration shall be referred to a sole arbitrator appointed by the Lender. The seat and venue of the arbitration shall be New Delhi. The language of the arbitration and the award of the arbitrator shall be in the English language. The award of the arbitrator shall be final and binding on the Parties and the expenses of the arbitration shall be borne in such manner as the arbitrator may determine.</li>\n" +
						"    </ul>\n" +
						"    <li class=\"li11\"><span class=\"s1\">Indemnity</span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p16\">Without prejudice to and in addition to other provisions contained in the Financing Documents, the Borrower hereby agrees to indemnify the Lender/BharatPe and its directors, officers, representatives and agents against any losses, liabilities, claims, damages or the like (including, without limitation, reasonable attorneys' fees and expenses) which may be sustained or incurred by any of them as a result of, or in connection with, or arising out of:</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">the Borrower failing to comply with the provisions of any Financing Documents and applicable Laws; and / or</li>\n" +
						"    <li class=\"li11\">the occurrence of any Event of Default; and / or</li>\n" +
						"    <li class=\"li11\">levy by any Government Authority of any charge, Taxes or penalty in connection with regularising or perfecting any of the Financing Documents as may be required under applicable Law at any time during the currency of the Facility, or getting any of the Financing Documents admitted into evidence, or relying on any Financing Documents for proving any claim; and/or</li>\n" +
						"    <li class=\"li11\">the exercise of any of the rights by the Lender under this Agreement and any of the Financing Documents; and/or</li>\n" +
						"    <li class=\"li11\">any of the representations and warranties of the Borrower under the Financing Documents are found to be false or untrue or incorrect on a future date.</li>\n" +
						"    </ul>\n" +
						"    <li class=\"li11\"><span class=\"s1\">Confidentiality</span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p16\">Any information supplied by a Party to another Party pursuant hereto which is by its nature can reasonably be construed to be proprietary or confidential or is marked &ldquo;confidential&rdquo; (&ldquo;<strong>Confidential Information</strong>&rdquo;) shall be kept confidential by the recipient unless or until compelled to disclose the same (i) by judicial or administrative process, or (ii) by law, or unless the same (iii) is in or is a part of public domain, or (iv) is required to be furnished to the bankers or investors or potential investors in the either Party, or (v) is required to be furnished to any Government Authority having jurisdiction over the recipient, or (vi) can be shown by the receiving Party to the reasonable satisfaction of the disclosing Party to have been known to the receiving Party prior to it being disclosed by the disclosing Party to the receiving Party, or (vii) subsequently comes lawfully into the possession of the receiving Party from a third party, and in such cases the confidentiality obligations shall cease to the extent required under the foregoing circumstances.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\"><span class=\"s1\">Amendments and Waivers</span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p16\">This Agreement (including the schedules, annexure and appendices hereto) may not be amended, supplemented or modified and no other Financing Document may be amended, supplemented or modified and no term or condition thereof may be waived without the written consent of the Parties to such Financing Document.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\"><span class=\"s1\">Severability</span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p16\">Any provision of this Agreement or any other Financing Document which is prohibited or unenforceable shall be ineffective to the extent of prohibition or unenforceability but shall not invalidate the remaining provisions of this Agreement or any Financing Document.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\"><span class=\"s1\">Survival</span></li>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">This Agreement shall be in force until all the Outstanding Amounts under this Agreement have been fully and irrevocably paid in accordance with the terms and provisions hereof.</li>\n" +
						"    <li class=\"li11\">The obligations of the Borrower under the Financing Documents will not be affected by:</li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\">any unenforceability, illegality or invalidity of any obligation of any Person under a Financing Document; or</li>\n" +
						"    <li class=\"li11\">the breach, frustration or non-fulfilment of any provisions of, or claim arising out of or in connection with a Financing Document.</li>\n" +
						"    </ul>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\"><span class=\"s1\">Right of Set-off</span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p16\">In addition to any rights now or hereafter granted under Applicable Law or otherwise, and not by way of limitation of any such rights, upon the occurrence and continuation of an Event of Default, the Lender is hereby authorised by the Borrower to, from time to time, without presentment, demand, protest or other notice of any kind to the Borrower, or to any other Person, set off and/or appropriate and/or apply any and all deposits (general or special) at any time held or owing by the Lender (including, without limitation, by any branches and agencies other than the lending office of Lender) to or for the credit or the account of the Borrower against and on account of the obligations and liabilities of the Borrower to the Lender under this Agreement or under any of the other Financing Documents.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\"><span class=\"s1\">Notices</span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p16\">All notices and other communications provided at various places in this Agreement shall be in writing and (a) sent by hand delivery, or (b) prepaid registered post with acknowledgment due, or (c) by e-mail followed by prepaid registered post with acknowledgment due, at the address and/or email first above written. All such notices and communications shall be deemed to have been delivered effective: (i) if sent by email, when sent (provided the email enters the sent folder of the sender), (ii) if sent by prepaid registered post, 3 (three) Business Days after its dispatch.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\"><span class=\"s1\">Effectiveness</span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">This Agreement shall become binding on the Parties hereto on and from the date hereof and shall be in force and effect till the Final Settlement Date.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\"><span class=\"s1\">Entire Agreement</span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">This Agreement and other Financing Documents shall represent the entire understanding of the Parties on the subject matter hereof and shall override all the previous understanding and agreement between the Parties hereto.</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li11\"><span class=\"s1\">No Discrimination</span></li>\n" +
						"    </ul>\n" +
						"    </ul>\n" +
						"    <p class=\"p17\">The Borrower shall, at all times during the term of this Agreement, ensure that no fraudulent preference is given to other lender of the Borrower, both present and future, so as to defeat Lender&rsquo;s rights, either present and future under this Agreement or to fraudulently service the dues owed to other lenders in preference to the dues owed to the Lender or to wilfully act in or consent to any third party acting in a manner as would cause a Material Adverse Effect.</p>\n" +
						"    <p class=\"p19\">&nbsp;</p>\n" +
						"    <p class=\"p19\">&nbsp;</p>\n" +
						"    <p class=\"p20\">&nbsp;</p>\n" +
						"    <p class=\"p20\">&nbsp;</p>\n" +
						"    <p class=\"p8\" style=\"text-align: center;\"><strong>SCHEDULE I</strong></p>\n" +
						"    <p class=\"p21\">&nbsp;</p>\n" +
						"    <p class=\"p8\" style=\"text-align: center;\"><strong>TERMS OF THE FACILITY</strong></p>\n" +
						"    <p class=\"p9\">&nbsp;</p>\n" +
						"    <table class=\"t1 new-table1\" cellspacing=\"0\" cellpadding=\"0\">\n" +
						"    <tbody>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p22\"><strong>S. NO.</strong></p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p23\"><strong>PARTICULARS</strong></p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p24\"><strong>DETAILS</strong></p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">1&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Date of Agreement</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p20\">"+detail.getOrDefault("Date", "")+"&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">2&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Place of Agreement</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p20\">"+detail.getOrDefault("City", "")+"&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">3&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Loan Agreement No.</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p20\">&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">4&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Name of Borrower</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p20\">"+detail.getOrDefault("Name of the Borrower", "")+"&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">5&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Address of Borrower</p>\n" +
						"    <p class=\"p26\">&nbsp;</p>\n" +
						"    <p class=\"p25\">Email Address of Borrower</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p20\">"+detail.getOrDefault("Shop/Business Address", "")+"&nbsp;</p>\n" +
						"    <p class=\"p20\">"+detail.getOrDefault("Email", "")+"&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">6&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Borrower's constitution</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p27\">&nbsp;</p>\n" +
						"    <p class=\"p27\">&nbsp;</p>\n" +
						"    <p class=\"p28\">"+"Individual"+"</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">7&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Purpose of the Facility/ Proposed utilization of the Facility</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p20\">"+"For General"+"&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">8&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Amount of Loan</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p29\">"+detail.getOrDefault("Loan Amount", "")+"&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">9&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Availability Period</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p6\">The period of days/months commencing from the date of execution of this Agreement or by such extended time as may be allowed by the Lender, available for draw down by the Borrower.</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">10&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Business of the Borrower</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p20\">"+(merchant.getBusinessCategory()==null?"":merchant.getBusinessCategory())+"&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">11&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Penal Interest Rate</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p20\">NIL&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">12&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Interest Rate</p>\n" +
						"    <ul class=\"ul1\">\n" +
						"    <li class=\"li6\">Interest chargeable (In case of Fixed/Monthly Loans)</li>\n"+
						"    </ul>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">2% per month&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">13&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p25\">Non-refundable Processing Fees /</p>\n" +
						"    <p class=\"p25\">service charge</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" colspan=\"5\" valign=\"middle\">\n" +
						"    <p class=\"p20\">Nil&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    </tbody>\n" +
						"    </table>\n" +
						"    <p class=\"p32\">&nbsp;</p>\n" +
						"    <p class=\"p35\" style=\"text-align: center;\"><strong>TABLE OF CHARGES</strong></p>\n" +
						"    <p class=\"p21\">&nbsp;</p>\n" +
						"    <table class=\"t1 new-table2\" cellspacing=\"0\" cellpadding=\"0\">\n" +
						"    <tbody>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p36\"><strong>Type of Charges</strong></p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p37\">&nbsp;</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p38\"><strong>Type of Charges</strong></p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p37\">&nbsp;</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p39\"><strong>Type of Charges</strong></p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p37\">&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p40\">Late payment Charges</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">NIL&nbsp;</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p41\">Part Prepayment Charges</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">NIL&nbsp;</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p42\">Title Search Report Charges (Legal Charges)</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">NIL&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p40\">Stamping Charges</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">NIL&nbsp;</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p41\">Processing Fee</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">NIL&nbsp;</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p43\">Other Charges</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">NIL&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    </tbody>\n" +
						"    </table>\n" +
						"    <p class=\"p32\">&nbsp;</p>\n" +
						"    <p class=\"p32\">&nbsp;</p>\n" +
						"    <p class=\"p8\" style=\"text-align: center;\"><strong>SCHEDULE II</strong></p>\n" +
						"    <p class=\"p21\">&nbsp;</p>\n" +
						"    <p class=\"p8\" style=\"text-align: center;\"><strong>REPAYMENT SCHEDULE</strong></p>\n" +
						"    <p class=\"p9\">&nbsp;</p>\n" +
						"    <table class=\"t1 new-table1\" cellspacing=\"0\" cellpadding=\"0\">\n" +
						"    <tbody>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p44\"><strong>S. No</strong></p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p45\"><strong>Particulars</strong></p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p46\"><strong>Details</strong></p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">1&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p47\">Number of EDI</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">"+detail.getOrDefault("EDI Count", "")+"&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">2&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p47\">Date of Commencement of EDI</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">"+((new Date().getDay()!=6?new Date(): DateTimeUtil.addDays(new Date(), 2)))+"&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    <tr>\n" +
						"    <td class=\"td1\" valign=\"middle\">3&nbsp;</td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p47\">Mode of repayment</p>\n" +
						"    </td>\n" +
						"    <td class=\"td1\" valign=\"middle\">\n" +
						"    <p class=\"p20\">QR Settlement&nbsp;</p>\n" +
						"    </td>\n" +
						"    </tr>\n" +
						"    </tbody>\n" +
						"    </table>\n" +
						"    <p class=\"p32\">&nbsp;</p>\n" +
						"    <p class=\"p32\">&nbsp;</p>\n" +
						"    <p class=\"p20\">&nbsp;</p>";
		}
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
			detail.put("EDI Start Date", new Date().getDay()!=6?new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()): new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(DateTimeUtil.addDays(new Date(), 2)));
			detail.put("Amount of EDI",lendingApplication.getEdi().toString());
			detail.put("Registered Mobile Number",merchant.getMobile());
			detail.put("Location",lendingApplication.getCity());
			detail.put("Shop/Business Address", lendingApplication.getShopNumber()+", "+lendingApplication.getStreetAddress()+", "+lendingApplication.getArea());
			detail.put("Landmark", lendingApplication.getLandmark());
			detail.put("PIN", lendingApplication.getPincode().toString());
			detail.put("City", lendingApplication.getCity());
			detail.put("State", lendingApplication.getState());
			detail.put("Email", lendingApplication.getEmail() != null ? lendingApplication.getEmail() : "");
			detail.put("EDI Count", lendingApplication.getPayableDays().toString());
			detail.put("Lender",lendingApplication.getLender());
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
	
	private String getLdcTnc(Map<String,String> detail) {
		String html="<html>\n" + 
				"<body>\n" + 
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
				"<td>"+detail.getOrDefault("Name of the Borrower", "")+"&nbsp;</td>\n" + 
				"</tr>\n" + 
				"<tr>\n" + 
				"<td>\n" + 
				"<p><span style=\"font-weight: 400;\">Loan Amount (in INR)</span></p>\n" + 
				"</td>\n" + 
				"<td>"+detail.getOrDefault("Loan Amount", "")+"&nbsp;</td>\n" + 
				"</tr>\n" + 
				"<tr>\n" + 
				"<td>\n" + 
				"<p><span style=\"font-weight: 400;\">Tenure (in Months)</span></p>\n" + 
				"</td>\n" + 
				"<td>"+detail.getOrDefault("Tenure", "")+"&nbsp;</td>\n" + 
				"</tr>\n" + 
				"<tr>\n" + 
				"<td>\n" + 
				"<p><span style=\"font-weight: 400;\">Rate of Interest (per annum)</span></p>\n" + 
				"</td>\n" +
				"<td>"+(detail.get("Interest")!=null?Double.valueOf(detail.get("Interest"))*12:"")+"&nbsp;</td>\n" + 
				"</tr>\n" + 
				"<tr>\n" + 
				"<td>\n" + 
				"<p><span style=\"font-weight: 400;\">Penal Interest (per annum)</span></p>\n" + 
				"</td>\n" + 
				"<td>"+detail.getOrDefault("Penal Interest", "")+"&nbsp;</td>\n" + 
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
				"<p><span style=\"font-weight: 400;\">Loan ID: &nbsp;&nbsp;"+detail.getOrDefault("Loan ID", "")+" &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span> <span style=\"font-weight: 400;\">Date: &nbsp;&nbsp;&nbsp;&nbsp; 28-Jun-2020 </span> <span style=\"font-weight: 400;\">&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; Loan Amount (INR):&nbsp;&nbsp; 75,000</span><span style=\"font-weight: 400;\"><br /></span><span style=\"font-weight: 400;\"><br /></span><span style=\"font-weight: 400;\"> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; </span></p>\n" + 
				"<p><span style=\"font-weight: 400;\">Tenure (Months): "+detail.getOrDefault("Tenure", "")+" Months&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span></p>\n" + 
				"<p><span style=\"font-weight: 400;\">Amount of EDI: "+detail.getOrDefault("Amount of EDI", "")+"&nbsp;</span></p>\n" + 
				"<p>&nbsp;</p>\n" + 
				"<p><span style=\"font-weight: 400;\">Flat Rate of Interest (% per month):&nbsp;&nbsp; 2.00 &nbsp;&nbsp;&nbsp;</span></p>\n" + 
				"<p><span style=\"font-weight: 400;\">Flat Rate of Interest (% per annum):&nbsp;&nbsp; 24</span></p>\n" + 
				"<p>&nbsp;</p>\n" + 
				"<p><span style=\"font-weight: 400;\">Registered Mobile Number:&nbsp;&nbsp; "+detail.getOrDefault("Registered Mobile Number", "")+" &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span></p>\n" + 
				"<p><span style=\"font-weight: 400;\">Location:&nbsp;&nbsp; "+detail.getOrDefault("Location", "")+"&nbsp;</span></p>\n" + 
				"<p>&nbsp;</p>\n" + 
				"<p><span style=\"font-weight: 400;\">EDI Due Date - Every day from Monday to Saturday from the successive day of disbursal</span></p>\n" + 
				"<p><span style=\"font-weight: 400;\"><br /></span><span style=\"font-weight: 400;\">Shop/Business Address:&nbsp;&nbsp;"+detail.getOrDefault("Shop/Business Address", "")+" &nbsp;&nbsp; </span><span style=\"font-weight: 400;\"><br /></span><span style=\"font-weight: 400;\">Landmark:&nbsp;&nbsp;"+detail.getOrDefault("Name of the Borrower", "")+" &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; </span><span style=\"font-weight: 400;\"><br /></span><span style=\"font-weight: 400;\">PIN:&nbsp;&nbsp; "+detail.getOrDefault("Name of the Borrower", "")+" &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; City:&nbsp;&nbsp; "+detail.getOrDefault("Name of the Borrower", "")+" &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;State:&nbsp;&nbsp; "+detail.getOrDefault("Name of the Borrower", "")+"&nbsp; </span> <span style=\"font-weight: 400;\">Email:"+detail.getOrDefault("Name of the Borrower", "")+"</span></p>\n" + 
				"<p><span style=\"font-weight: 400;\"><br /><br /></span></p>\n" + 
				"<p><span style=\"font-weight: 400;\">Banking Details: The complete Loan Amount shall be credited to the &lsquo;Borrowers Authorised Bank Account&rsquo; as defined in the Agreement and as specified below&nbsp;</span></p>\n" + 
				"<p><span style=\"font-weight: 400;\"><br /></span><span style=\"font-weight: 400;\">Bank Name:"+detail.getOrDefault("Bank Name", "")+"&nbsp;</span></p>\n" + 
				"<p><span style=\"font-weight: 400;\">Account No. "+detail.getOrDefault("Account No", "")+"&nbsp;</span></p>\n" + 
				"<p><span style=\"font-weight: 400;\">Account Type: "+detail.getOrDefault("Account Type", "")+"</span></p>\n" + 
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
				"<p><span style=\"font-weight: 400;\">Application Name:</span></p>\n" + 
				"<p><span style=\"font-weight: 400;\">Platform:Android</span></p>\n" + 
				"<p><span style=\"font-weight: 400;\">IP Address:"+detail.getOrDefault("IP Address", "")+"</span></p>\n" + 
				"<p><span style=\"font-weight: 400;\">Mobile Number for eSign:</span></p>\n" + 
				"<p><span style=\"font-weight: 400;\">Timestamp:"+new Date()+"&nbsp;</span></p>\n" + 
				"</body>\n" + 
				"</html>";
		
		return html;
	}
}