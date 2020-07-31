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
			lendingApplication.setLender("HINDON");
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
			String html =
					"<p><br /><br /><br /></p>\n" + 
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
					"<p><br /><br /><br /></p>\n";
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