 
  
package com.bharatpe.lending.service;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.handlers.BharatPeOtpHandler;
import com.bharatpe.lending.handlers.MerchantSummaryExceptionHandler;
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
import com.bharatpe.common.dao.MerchantSummarySnapshotDao;
import com.bharatpe.common.entities.AvailableLoan;
import com.bharatpe.common.entities.EligibleLoan;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.LendingCities;
import com.bharatpe.common.entities.MerchantSummarySnapshot;
import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.service.WhatsappNotificationService;
import com.bharatpe.lending.dto.CreditApplicationRequestDTO;
import com.bharatpe.lending.dto.CreditApplicationResponseDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.util.CreditUtil;
import org.springframework.util.ObjectUtils;


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

//	@Autowired
//	MerchantSummaryDao merchantSummaryDao;

//	@Autowired
//	MerchantDao merchantDao;

	@Autowired
	EligibleLoanDao eligibleLoanDao;
	
	@Autowired
	BharatPeOtpHandler bharatPeOtpHandler;

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
	
	@Autowired
	ExperianSnapshotDao experianSnapshotDao;

	@Autowired
	MerchantSummaryHandler merchantSummaryHandler;
	public CreditApplicationResponseDTO createApplication(BasicDetailsDto merchant, RequestDTO<CreditApplicationRequestDTO> requestDTO) {

		CreditApplicationResponseDTO creditApplicationResponse;
		CreditApplication creditApplication;
		CreditLineMerchant creditLineMerchant = creditLineMerchantDao.findByMerchantId(merchant.getId());
		if (creditLineMerchant == null) {
			logger.info("Merchant:{} not applicable for credit line", merchant.getId());
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
//	 	 MerchantSummary summary =  merchantSummaryDao.getByMerchantId(merchant.getId());
			MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(merchant.getId());
			if (ObjectUtils.isEmpty(merchantResponseDTO)) {
				throw new MerchantSummaryExceptionHandler(merchant.getId().toString());
			}
			if (EXPERIAN_ENABLED) {
				creditApplication = createApplication(merchant, eligibleLoans.get(0), creditApplicationRequest);
			} else {
				creditApplication = createApplication(merchant, availableLoan.get(0), creditApplicationRequest);
			}
			creditApplication.setExternalLoanId(getExternalLoanId(creditApplication));
			creditApplicationDao.save(creditApplication);
			createMerchantSummarySnapshot(merchant, creditApplication, merchantResponseDTO);
			createExperianSnapshot(merchant, creditApplication);
			creditLineMerchant.setCreditApplicationId(creditApplication.getId());
			creditLineMerchantDao.save(creditLineMerchant); 
			createStatusAuditTrail(creditApplication);
			CreditApplicationReason creditApplicationReason = new CreditApplicationReason();
			creditApplicationReason.setMerchantId(merchantId);
			creditApplicationReason.setApplicationId(creditApplication.getId());
			creditApplicationReasonDao.save(creditApplicationReason);
		}

		logger.info("Loan Application saved : {}",creditApplication);
	//	redisNotificationService.sendDraftNotificationForCreditLine(merchant, creditApplication);
		//sendNotification(merchant,creditApplication);
		return prepareAPIResponse(creditApplication);
		
	}
	
	private void createExperianSnapshot(BasicDetailsDto merchant,CreditApplication creditApplication) {
		Experian experian = experianDao.getByMerchantId(merchant.getId());
		if(experian!=null) {
			ExperianSnapshot experianSnapshot=new ExperianSnapshot();
			experianSnapshot.setMerchantId(experian.getMerchantId());
			experianSnapshot.setIp(experian.getIp());
			experianSnapshot.setLatitude(experian.getLatitude());
			experianSnapshot.setLongitude(experian.getLongitude());
			experianSnapshot.setResponse(experian.getResponse());
			experianSnapshot.setMerchantName(experian.getMerchantName());
			experianSnapshot.setEmail(experian.getEmail());
			experianSnapshot.setRejected(experian.getRejected());
			experianSnapshot.setReason(experian.getReason());
			experianSnapshot.setRequestedLoanAmount(experian.getRequestedLoanAmount());
			experianSnapshot.setPancardNumber(experian.getPancardNumber());
			experianSnapshot.setTnc(experian.getTnc());
			experianSnapshot.setBpScore(experian.getBpScore());
			experianSnapshot.setExperianScore(experian.getExperianScore());
			experianSnapshot.setCategory(experian.getCategory());
			experianSnapshot.setColor(experian.getColor());
			experianSnapshot.setRetryCount(experian.getRetryCount());
			experianSnapshot.setSkip(experian.isSkip());
			experianSnapshot.setPincode(experian.getPincode());
			experianSnapshot.setApplicationId(creditApplication.getId());
			experianSnapshot.setBureau(experian.getBureau());
			experianSnapshotDao.save(experianSnapshot);

		}
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

	private CreditApplication createApplication(BasicDetailsDto merchant, EligibleLoan eligibleLoan, CreditApplicationRequestDTO creditApplicationRequest) {
		CreditApplication creditApplication = new CreditApplication();
		 //LendingCategories lendingCategory = lendingCategoryDao.findByCategory(eligibleLoan.getCategory()).get(0);

		 //LoanBreakupDetail breakupDetail = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategory);
  
		Experian experian = experianDao.getByMerchantId(merchant.getId());
		creditApplication.setPancardNumber(experian.getPancardNumber());
		creditApplication.setDisbursalAmount(eligibleLoan.getAmount());
		creditApplication.setStatus("draft");
		creditApplication.setLender("LIQUILOANS");
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
	
	private CreditApplication createApplication(BasicDetailsDto merchant, AvailableLoan availableLoan, CreditApplicationRequestDTO creditApplicationRequest) {
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

 	public void createMerchantSummarySnapshot(BasicDetailsDto merchantBasicDetails, CreditApplication application, MerchantResponseDTO merchantResponseDTO) {
		try {
			MerchantSummarySnapshot snapshot = new MerchantSummarySnapshot();
			List<Object[]> data = availableLoanDao.getMaxEligibilityDataForMerchant(merchantBasicDetails.getId());

			// TODO : remove this and use api
//			Merchant merchant = merchantDao.getById(merchantBasicDetails.getId());

			snapshot.setApplication(application.getId());
			snapshot.setMerchantId(merchantBasicDetails.getId());
			snapshot.setLastTransactionDate(merchantResponseDTO.getLastTransactionDate());
			snapshot.setTotalTxnCount(merchantResponseDTO.getDailyTxnCount());
			snapshot.setTotalTxnAmount(merchantResponseDTO.getDailyTxnAmount());
			snapshot.setCategory(merchantResponseDTO.getCategory());
			snapshot.setAvgTpv(merchantResponseDTO.getAvgTpv());
		 //	snapshot.setMaxEligibleLoanAmount(((BigDecimal) data.get(0)[0]).doubleValue());
			snapshot.setEligibleLoanCategories((String) data.get(0)[1]);
			snapshot.setLoanType(merchantResponseDTO.getLoanType());
			snapshot.setTpv1Mon(merchantResponseDTO.getTpv1Mon());
			snapshot.setTpv2Mon(merchantResponseDTO.getTpv2Mon());
			snapshot.setTpv3Mon(merchantResponseDTO.getTpv3Mon());
			snapshot.setTxnDayCount1Mon(merchantResponseDTO.getTxnDayCount1Mon());
			snapshot.setTxnDayCount2Mon(merchantResponseDTO.getTxnDayCount2Mon());
			snapshot.setTxnDayCount3Mon(merchantResponseDTO.getTxnDayCount3Mon());
			snapshot.setTotalTxns1Month(merchantResponseDTO.getTotalTxns1Month());
			snapshot.setTotalTxns2Month(merchantResponseDTO.getTotalTxns2Month());
			snapshot.setTotalTxns3Month(merchantResponseDTO.getTotalTxns3Month());
			snapshot.setTotalLoansCount(merchantResponseDTO.getTotalLoansCount());
			snapshot.setBpScore(merchantResponseDTO.getBpScore());
			snapshot.setUniqueCustomer1mon(merchantResponseDTO.getUniqueCustomer1mon());
			snapshot.setFraudCustomer(merchantResponseDTO.getFraudCustomer());

			merchantSummarySnapshotDao.save(snapshot);
		} catch(Exception ex) {
			logger.error("Exception while creating merchant summary snapshot for merchant id {} and application id {}, Exception is {}", merchantBasicDetails.getId(), application.getId(), ex);
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
	
	public ResponseDTO sendOtp(BasicDetailsDto merchant) {
		String message = "BharatPe: {otp} is your OTP to register yourself on BharatPe Merchant App. BharatPe.com";
		Map<String, Object> response = new HashMap<String, Object>();
		response= bharatPeOtpHandler.sendOtp(merchant.getMobile(), message);
		Boolean otp = (Boolean) response.get("success");
		String uuid = (String) response.get("uuid");
		logger.info("OTP sent on mobile: {} ", uuid);
		if (otp) {
			logger.info("OTP sent on mobile: {} for merchant: {}", merchant.getMobile(), merchant.getId());
			ResponseDTO responseDTO = new ResponseDTO(true, null, null,uuid);
			responseDTO.setMobile(merchant.getMobile());
			return responseDTO;
		}
		return new ResponseDTO(false, "Unable to send otp", null,uuid);
	}

//	public void sendNotification(Merchant merchant, CreditApplication creditApplication) {
//		List<String> mobiles = new ArrayList<> ();
//		mobiles.add(merchant.getMobile());
//		String message=getNotificationContent(merchant, creditApplication);
//		if(message!=null) {
//			smsServiceHandler.sendSMS(mobiles, message, NotificationProvider.SMS.GUPSHUP);
//			whatsappNotificationService.send(merchant, null, message, mobiles, null);
//		}
//	}
	
//	public String getNotificationContent(Merchant merchant,CreditApplication creditApplication) {
//		MerchantBankDetail merchantBankDetail=merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
//		if(merchantBankDetail==null) {
//			logger.error("Bank detail not found for merchant {}",merchant.getId());
//			return null;
//		}
//
//		String message = "Hi  " + merchantBankDetail.getBeneficiaryName() + ",\n\n" +
//				"Your Application (ID - "+creditApplication.getExternalLoanId()+") for Rs. " + creditApplication.getAmount().intValue() + "BharatPe Loan Balance has been registered successfully. Application is under review and limit will be activated within 48 hours";
//
//		return message;
//	}
}
