 
  
package com.bharatpe.lending.service;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bharatpe.common.dao.AvailableLoanDao;
import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.LendingCitiesDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.dao.MerchantSummaryDao;
import com.bharatpe.common.dao.MerchantSummarySnapshotDao;
import com.bharatpe.common.entities.AvailableLoan;
import com.bharatpe.common.entities.EligibleLoan;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.LendingCities;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.common.entities.MerchantSummary;
import com.bharatpe.common.entities.MerchantSummarySnapshot;
import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.service.WhatsappNotificationService;
import com.bharatpe.lending.dto.CreditApplicationRequestDTO;
import com.bharatpe.lending.dto.CreditApplicationResponseDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.handlers.GupShupOTPHandler;
import com.bharatpe.lending.util.CreditUtil;
import com.bharatpe.lending.util.LoanUtil;
 


@Service
public class CreditApplicationService {

	private Logger logger = LoggerFactory.getLogger(CreditApplicationService.class);

	@Autowired
	LendingCitiesDao lendingCitiesDao;
	
	@Autowired
	CreditUtil creditUtil;

	@Autowired
	CreditApplicationDao creditApplicationDao;
	
	@Autowired
	CreditApplicationAddressDao creditApplicationAddressDao;

	@Autowired
	AvailableLoanDao availableLoanDao;
	
	@Autowired
	ExperianDao experianDao;
  
	@Autowired
	CreditApplicationTransitionDao creditApplicationTransitiondao;
	
	@Autowired
	MerchantSummarySnapshotDao merchantSummarySnapshotDao;

	@Autowired
	MerchantSummaryDao merchantSummaryDao;

	@Autowired
	EligibleLoanDao eligibleLoanDao;
	
	@Autowired
	GupShupOTPHandler gupShupOTPHandler;

	@Value("${experian.enable:true}")
	Boolean EXPERIAN_ENABLED;

	@Autowired
	CreditLineMerchantDao creditLineMerchantDao;
	
	@Autowired
	SmsServiceHandler smsServiceHandler;
  
	@Autowired
	WhatsappNotificationService whatsappNotificationService;
	
	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;
	
	@Autowired
	RedisNotificationService redisNotificationService;

	@Autowired
	CreditApplicationReasonDao creditApplicationReasonDao;

	public CreditApplicationResponseDTO createApplication(Merchant merchant, RequestDTO<CreditApplicationRequestDTO> requestDTO) {
		CreditApplicationResponseDTO creditApplicationResponse;
		CreditApplication creditApplication;
		CreditLineMerchant creditLineMerchant = creditLineMerchantDao.findByMerchantId(merchant.getId());
		if (creditLineMerchant == null) {
			logger.error("Merchant:{} not applicable for credit line", merchant.getId());
			creditApplicationResponse = new CreditApplicationResponseDTO();
			creditApplicationResponse.setSuccess(false);
			return creditApplicationResponse;
		}
			
		
		Long merchantId = merchant.getId();
		CreditApplicationRequestDTO creditApplicationRequest = requestDTO.getPayload();

		if(creditApplicationRequest.getApplicationId() != null && creditApplicationRequest.getApplicationId() > 0) {
			creditApplication = creditApplicationDao.findByIdAndMerchantIdAndStatus(creditApplicationRequest.getApplicationId(), merchant.getId(), "draft");
			if(creditApplication == null) {
				logger.info("No application found in draft status for given application id {}", creditApplicationRequest.getApplicationId());
				creditApplicationResponse = new CreditApplicationResponseDTO();
				creditApplicationResponse.setSuccess(false);
				return creditApplicationResponse;
			}
			creditApplication=updateApplication(creditApplication, creditApplicationRequest);
			creditApplicationDao.save(creditApplication);
			
		
		}
		else {
			List<EligibleLoan> eligibleLoans = new ArrayList<>();
			List<AvailableLoan> availableLoan = new ArrayList<>();
			if (EXPERIAN_ENABLED) {
				eligibleLoans = eligibleLoanDao.findByMerchantIdAndCategory(merchantId, creditApplicationRequest.getCategory());
				if(eligibleLoans == null || eligibleLoans.isEmpty()) {
					logger.info("No loan available for Merchant {} and category {}", merchantId, creditApplicationRequest.getCategory());
					creditApplicationResponse = new CreditApplicationResponseDTO();
					creditApplicationResponse.setSuccess(false);
					return creditApplicationResponse;
				}
			} else {
				availableLoan = availableLoanDao.findByMerchantIdAndCategory(merchantId, creditApplicationRequest.getCategory());
				if(availableLoan == null || availableLoan.isEmpty()) {
					logger.info("No loan available for Merchant {} and category {}", merchantId, creditApplicationRequest.getCategory());
					creditApplicationResponse = new CreditApplicationResponseDTO();
					creditApplicationResponse.setSuccess(false);
					return creditApplicationResponse;
				}
			}
	 	 MerchantSummary summary =  merchantSummaryDao.getByMerchantId(merchant.getId());
			if (EXPERIAN_ENABLED) {
				creditApplication = createApplication(merchant, eligibleLoans.get(0), creditApplicationRequest);
			} else {
				creditApplication = createApplication(merchant, availableLoan.get(0), creditApplicationRequest);
			}
			creditApplication.setLatitude(Double.valueOf(requestDTO.getMeta().getLatitude()));
			creditApplication.setLongitude(Double.valueOf(requestDTO.getMeta().getLongitude()));
			creditApplication.setIp(requestDTO.getMeta().getIp());
			  //creditApplication.setTotalLoansCount(summary.getTotalLoansCount() == null ? 0 : summary.getTotalLoansCount());
			creditApplicationDao.save(creditApplication);
		 //createMerchantSummarySnapshot(merchant, creditApplication, summary);
			creditApplication.setExternalLoanId(getExternalLoanId(creditApplication));
			creditApplication.setLender("LIQUILOANS");
			creditApplication = creditApplicationDao.save(creditApplication);
			creditLineMerchant.setCreditApplicationId(creditApplication.getId());
			creditLineMerchantDao.save(creditLineMerchant); 
			createStatusAuditTrail(creditApplication);
			CreditApplicationReason creditApplicationReason = new CreditApplicationReason();
			creditApplicationReason.setMerchantId(merchantId);
			creditApplicationReason.setApplicationId(creditApplication.getId());
			creditApplicationReasonDao.save(creditApplicationReason);
		}

		logger.info("Loan Application saved : {}",creditApplication);
		redisNotificationService.sendDraftNotificationForCreditLine(merchant, creditApplication);
		//sendNotification(merchant,creditApplication);
		return prepareAPIResponse(creditApplication);
		
	}

	private CreditApplication updateApplication(CreditApplication creditApplication, CreditApplicationRequestDTO creditApplicationRequest) {
		CreditApplicationAddress creditApplicationAddress=creditApplicationAddressDao.findByMerchantIdAndApplicationId(creditApplication.getMerchantId(),creditApplication.getId());

		creditApplication.setPincode(String.valueOf(creditApplicationRequest.getPincode()));
		creditApplication.setBusinessName(creditApplicationRequest.getBusinessName());
		creditApplication .setAlternateMobile(creditApplicationRequest.getAlternativeContact().getPhoneNumber());
		creditApplicationAddress.setShopNumber(creditApplicationRequest.getShopNumber());
		creditApplicationAddress.setStreetAddress(creditApplicationRequest.getStreetAddress());
		creditApplicationAddress.setArea(creditApplicationRequest.getArea());
		creditApplicationAddress.setLandmark(creditApplicationRequest.getLandmark());
		creditApplicationAddress.setPincode(creditApplicationRequest.getPincode());
		creditApplicationAddress.setCity(creditApplicationRequest.getCity());
		creditApplicationAddress.setState(creditApplicationRequest.getState());
		creditApplicationAddressDao.save(creditApplicationAddress);
		return creditApplication;
	}

	private CreditApplication createApplication(Merchant merchant, EligibleLoan eligibleLoan, CreditApplicationRequestDTO creditApplicationRequest) {
		CreditApplication creditApplication = new CreditApplication();
		 //LendingCategories lendingCategory = lendingCategoryDao.findByCategory(eligibleLoan.getCategory()).get(0);

		 //LoanBreakupDetail breakupDetail = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategory);
  
		Experian experian = experianDao.getByMerchantId(merchant.getId());
		creditApplication.setPancardNumber(experian.getPancardNumber());
		creditApplication.setDisbursalAmount(eligibleLoan.getAmount());
		creditApplication.setStatus("draft");
		 
		creditApplication.setMerchantId(merchant.getId());
		//creditApplication.setMerchantStoreId(merchant.getStoreId());
		creditApplication.setAmount(eligibleLoan.getAmount());
		creditApplication.setCategory(eligibleLoan.getCategory());
		 creditApplication.setLoanType(eligibleLoan.getLoanType());
		 creditApplication.setMobile(merchant.getMobile());
		 creditApplication.setAgreement(false);
		  
		  creditApplication =creditApplicationDao.save(creditApplication);
			CreditApplicationAddress creditApplicationAddress=new 	CreditApplicationAddress();
				creditApplicationAddress.setApplicationId(creditApplication.getId());
				creditApplicationAddress.setMerchantId(creditApplication.getMerchantId());
				creditApplicationAddressDao.save(creditApplicationAddress);
		creditApplication = updateApplication(creditApplication, creditApplicationRequest);
		
		return creditApplication;
	}
	
	private String getExternalLoanId(CreditApplication creditApplication) {
		DateFormat df = new SimpleDateFormat("ddMMyy");
		Date dateobj = new Date();
		String loanId = "CL" + df.format(dateobj) + creditApplication.getId();
		return loanId;
	}
	
	private CreditApplication createApplication(Merchant merchant, AvailableLoan availableLoan, CreditApplicationRequestDTO creditApplicationRequest) {
		CreditApplication creditApplication = new CreditApplication();
		//LendingCategories lendingCategory = lendingCategoryDao.findByCategory(availableLoan.getCategory()).get(0);
	   
	 

		Experian experian = experianDao.getByMerchantId(merchant.getId());
		creditApplication.setPancardNumber(experian.getPancardNumber());
		creditApplication.setStatus("draft");
		 
		creditApplication.setMerchantId(merchant.getId());
		 
		creditApplication.setCategory(availableLoan.getCategory());
		
		 creditApplication =creditApplicationDao.save(creditApplication);
		 
			CreditApplicationAddress creditApplicationAddress=new 	CreditApplicationAddress();
			 creditApplicationAddress.setApplicationId(creditApplication.getId());
				creditApplicationAddress.setMerchantId(creditApplication.getMerchantId());
				creditApplicationAddressDao.save(creditApplicationAddress);
		 

		creditApplication = updateApplication(creditApplication, creditApplicationRequest);
 
		return creditApplication;
	}

	private void createStatusAuditTrail(CreditApplication creditApplication) {
		CreditApplicationTransition creditApplicationTransition = new CreditApplicationTransition();
 
		creditApplicationTransition.setApplicationId(creditApplication.getId());
		creditApplicationTransition.setFromStatus("");
		 
		creditApplicationTransition.setToStatus("draft");
		creditApplicationTransition.setComment("APP_STATUS");
		creditApplicationTransitiondao.save(creditApplicationTransition);
	}
 
	private CreditApplicationResponseDTO prepareAPIResponse(CreditApplication creditApplication) {
		CreditApplicationResponseDTO creditApplicationResponse = new CreditApplicationResponseDTO();
		CreditApplicationResponseDTO.LoanApplication loanApplication = creditApplicationResponse.new LoanApplication();

		loanApplication.setApplicationId(creditApplication.getId());
		loanApplication.setApplicationStatus(creditApplication.getStatus());
		loanApplication.setSelectedLoan(creditUtil.prepareSelectedLoanForClient(creditApplication));
		loanApplication.setShopDetails(creditUtil.prepareShopDetailsForClient(creditApplication));
		 
		creditApplicationResponse.setSuccess(true);
		creditApplicationResponse.setLoanApplication(loanApplication);
		return creditApplicationResponse;
	}
	
 	public void createMerchantSummarySnapshot(Merchant merchant, CreditApplication application, MerchantSummary summary) {
		try {
			MerchantSummarySnapshot snapshot = new MerchantSummarySnapshot();
			List<Object[]> data = availableLoanDao.getMaxEligibilityDataForMerchant(merchant.getId());

			// snapshot.setApplication(application);
			snapshot.setMerchant(merchant);
			snapshot.setLastTransactionDate(summary.getLastTransactionDate());
			snapshot.setTotalTxnCount(summary.getDailyTxnCount());
			snapshot.setTotalTxnAmount(summary.getDailyTxnAmount());
			snapshot.setCategory(summary.getCategory());
			snapshot.setAvgTpv(summary.getAvgTpv());
		 //	snapshot.setMaxEligibleLoanAmount(((BigDecimal) data.get(0)[0]).doubleValue());
			snapshot.setEligibleLoanCategories((String) data.get(0)[1]);
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

	public void sendNotification(Merchant merchant, CreditApplication creditApplication) {
		List<String> mobiles = new ArrayList<> ();
		mobiles.add(merchant.getMobile());
		String message=getNotificationContent(merchant, creditApplication);
		if(message!=null) {
			smsServiceHandler.sendSMS(mobiles, message, NotificationProvider.SMS.GUPSHUP);
			whatsappNotificationService.send(merchant, null, message, mobiles, null);
		}
	}
	
	public String getNotificationContent(Merchant merchant,CreditApplication creditApplication) {
		MerchantBankDetail merchantBankDetail=merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
		if(merchantBankDetail==null) {
			logger.error("Bank detail not found for merchant {}",merchant.getId());
			return null;
		}
		
		String message = "Hi  " + merchantBankDetail.getBeneficiaryName() + ",\n\n" +
				"Your Application (ID - "+creditApplication.getExternalLoanId()+") for Rs. " + creditApplication.getAmount().intValue() + "BharatPe Loan Balance has been registered successfully. Application is under review and limit will be activated within 48 hours";
		
		return message;
	}
}
