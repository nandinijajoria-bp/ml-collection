package com.bharatpe.lending.service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.constant.ExperianConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bharatpe.common.dao.AvailableLoanDao;
import com.bharatpe.common.dao.ExperianAuditTrailDao;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.LendingCitiesDao;
import com.bharatpe.common.dao.LendingClosedAuditDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.dao.MerchantStoreDao;
import com.bharatpe.common.dao.MerchantSummaryDao;
import com.bharatpe.common.dao.PaymentTransactionNewDao;
import com.bharatpe.common.dao.PincodeCityStateMappingDao;
import com.bharatpe.common.entities.AvailableLoan;
import com.bharatpe.common.entities.Experian;
import com.bharatpe.common.entities.ExperianAuditTrail;
import com.bharatpe.common.entities.LendingCategories;
import com.bharatpe.common.entities.LendingCities;
import com.bharatpe.common.entities.LendingClosedAudit;
import com.bharatpe.common.entities.Merchant;
import com.bharatpe.common.entities.MerchantBankDetail;
import com.bharatpe.common.entities.MerchantStore;
import com.bharatpe.common.entities.MerchantSummary;
import com.bharatpe.common.entities.PincodeCityStateMapping;
import com.bharatpe.common.enums.Status.GeneralStatus;
import com.bharatpe.common.handlers.EmailHandler;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dto.CreditLoanDetailsResponseDto;
import com.bharatpe.lending.dto.CreditLoanDetailsResponseDto.LoanDetailsDTO;
import com.bharatpe.lending.dto.DocumentDTO;
import com.bharatpe.lending.dto.IneligibleRequestDTO;
import com.bharatpe.lending.dto.LoanApplicationDTO;
import com.bharatpe.lending.dto.LoanEligibilityDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.dto.SelectedLoanDTO;
import com.bharatpe.lending.dto.ShopDetailsDTO;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanUtil;
import com.bharatpe.lending.util.LoanCalculationUtil.LoanBreakupDetail;

@Service
public class CreditLineLoanDetailsService {
	
	@Autowired
	ExperianDao experianDao;
	
	@Autowired
	MerchantSummaryDao merchantSummeryDao;
	
	@Autowired
	MerchantStoreDao merchantStoreDao;
	
	@Autowired
	LendingCitiesDao lendingCitiesDao;
	
	@Autowired
	PincodeCityStateMappingDao pincodeCityStateMappingDao;
	
	@Autowired
	LendingClosedAuditDao lendingClosedAuditDao;
	
	@Autowired
	CreditApplicationDao creditApplicationDao;
	
	@Autowired
	LendingClEnachDao lendingClEnachDao;
	
	@Autowired
	ENachService eNachService;
	
	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;
	
	@Value("${enach.provider}")
	private String enachServiceToUse;
	
	@Autowired
	LoanEligibleService loanEligibleService;
	
	@Autowired
	LoanUtil loanUtil;
	
	@Autowired
	EmailHandler emailHandler;
	
	Logger logger=LoggerFactory.getLogger(CreditLineLoanDetailsService.class);
	
	@Autowired
	PaymentTransactionNewDao paymentTransactionNewDao;
	
	@Autowired
	AvailableLoanDao availableLoanDao;
	
	@Autowired
	CreditApplicationTransitionDao creditApplicationTransitionDao;
	
	@Autowired
	CreditApplicationAddressDao creditApplicationAddressDao;
	
	@Autowired
	MerchantDocumentProofDao merchantDocumentProofDao;
	
	@Autowired
	CreditLineCategoriesDao creditLineCategoriesDao;
	
	@Autowired
	LendingCategoryDao lendingCategoryDao;
	
	@Autowired
	LendingEkycDao lendingEkycDao;
	
	@Autowired
	CreditAccountDao creditAccountDao;

	@Autowired
	LendingCaBalanceDetailDao lendingCaBalanceDetailDao;

	@Autowired
	CreditLineMerchantDao creditLineMerchantDao;
	
	@Autowired
	RedisNotificationService redisNotificationService;
	
	public CreditLoanDetailsResponseDto getLoanDetails(Merchant merchant, RequestDTO<IneligibleRequestDTO> requestDTO, String clientIp){
		
		try {
			
			Experian experian=null;
			
			CreditLoanDetailsResponseDto response=new CreditLoanDetailsResponseDto();
			if(!isMerchantFromCreditLine(merchant)) {
				response.setDeeplink("bharatpe://dynamic?key=loan");
				response.setSuccess(true);
				response.setMessage("Loan Merchant");
				response.setDetails(null);
				return response;
			}
			MerchantSummary merchantSummary=null;
			
			CreditApplication creditApplication=null;
			LoanDetailsDTO loanDetailsDto=new LoanDetailsDTO();
			
			
			response.setDetails(loanDetailsDto);
			
					
			Integer pincode=requestDTO.getPayload().getPincode();
			String panCard=requestDTO.getPayload().getPanCard();
			
//			if(checkForOrganizedMerchant(merchant,response)){
//				//response was changed inside above called function
//				return response;
//
//			}
			CreditLineMerchant creditLineMerchant = creditLineMerchantDao.findByMerchantId(merchant.getId());
			if (creditLineMerchant == null) {
				logger.error("Merchant:{} not applicable for credit line", merchant.getId());
				return response;
			}
			
			merchantSummary=merchantSummeryDao.getByMerchantId(merchant.getId());
			experian=populateExperianDetailsInExperianTable(merchant, requestDTO, clientIp, merchantSummary,experian);
			
			if(experian==null){
				
				response.getDetails().setPanCard(null);
				response.getDetails().setPincode(null);
				response.getDetails().setEligible(false);
				response.getDetails().setRejected(false);
				
				return response;
				
			}
			else if (experian != null && experian.getPancardNumber() != null) {
				
				panCard = experian.getPancardNumber();
				if (merchantSummary != null && merchantSummary.getBpScore() != null) {
					experian.setBpScore(merchantSummary.getBpScore());
				}	
			}
			
//			if (experian != null && experian.getRejected() && experian.getRejectedDate() != null && LoanUtil.getDateDiffInDays(experian.getRejectedDate(), new Date()) < 30) {
//
//				response.getDetails().setRejected(true);
//				response.getDetails().setRejectReason(experian.getReason());
//				return response;
//
//			}
			if (experian != null && experian.getRejected() && experian.getRejectedDate() != null && LoanUtil.getDateDiffInDays(experian.getRejectedDate(), new Date()) >= 30) {
				experian.setRejected(false);
				experian.setReason(null);
				experian.setCreatedAt(new Date());
				experianDao.save(experian);
			}
			//setting up pincode and pancard data in case data is null in request but present in experian table
			
			pincode=pincode!=null?pincode:experian.getPincode();
			
			response.getDetails().setPanCard(panCard);
			response.getDetails().setPincode(pincode);
			
			if(pincode!=null){
				
				logger.info("checking for OGL");
				
				Boolean isOGL=checkForOgl(merchant, response, pincode, panCard);
				if(isOGL==null){
					return getErrorMessage("Error occured while checking for OGL");
				}
				else if(isOGL==true){
					//response body modified inside checkForOgl function
					return response;
				}
			}
			else{
				
				return getErrorMessage("Pincode does not exists");
			}
		
			creditApplication=creditApplicationDao.findOpenApplication(merchant.getId());
			
			if(creditApplication==null){
				return getResponseForNewLoanApplication(merchant, requestDTO, merchantSummary,experian,response,panCard,pincode);
			}
			else {
				return getResponseForExistingCreditApplication(merchant,creditApplication,requestDTO,response,panCard,pincode);
			}
			
		}
		catch(Exception e) {
			logger.error("Error occured while getting eligible loan",e);
			return getErrorMessage("Error occured while getting eligible loan");
		}
		
	}
	
	private CreditLoanDetailsResponseDto getResponseForNewLoanApplication(Merchant merchant, RequestDTO<IneligibleRequestDTO> requestDTO, MerchantSummary merchantSummary,Experian experian, CreditLoanDetailsResponseDto response,String panCard, Integer pincode) {
		
		List<LoanEligibilityDTO> loanEligibilityDTOs = new ArrayList<>();
		
		try {
			
			logger.info("Fetching merchant bank details for merchant {}",merchant.getId());
		    MerchantBankDetail merchantBankDetail=merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
		    
		    
		if (experian != null) {
			
			try {
				
				logger.info("Checking cibil score through experian for getting loan eligibility");	
				//merchantSummery field was populated in populateExperianDetailsInExperianTable function which is called before this function
				
				loanEligibilityDTOs.addAll(loanEligibleService.getNewLoanDetails(merchant, experian, merchantSummary, merchantBankDetail, requestDTO.getPayload().isSkip(), requestDTO.getPayload().getPanCard(), false,"CREDITLINE", false, null));
				//send notification
				//redisNotificationService.sendEligibleNotificationForCreditLine(merchant, loanEligibilityDTOs);
				loanUtil.auditExperian(experian);
			} catch (Exception e) {
				logger.error("Exception fetching eligible loan for merchant: {}", merchant.getId());
				logger.error("Exception---", e);
				emailHandler.sendEmail(new ArrayList<String>(){{add("khushal.virmani@bharatpe.com");}}, "Eligible Loan Exception", "");
			}
			if (experian.getRejected()) {
				logger.info("Loan application rejected becuase of {}",experian.getReason());
				response.getDetails().setNoExperian(false);
				response.getDetails().setRejected(true);
				response.getDetails().setRejectReason(experian.getReason());
				
				if(experian.getReason()!=null && experian.getReason().equalsIgnoreCase("fraud")) {
					lendingClosedAuditDao.save(new LendingClosedAudit(merchant.getId(), panCard, pincode, "FRAUD"));
				}
				
				return response;
			}
			if (experian.getRetryCount() == 1) {//experian timeout
				
				logger.info("Error occured due to experian timeout");
				
				return getErrorMessage("Experian timeout");
				
			}
			if (experian.isNoExperian()) {
				
				response.getDetails().setNoExperian(true);
				if (experian.getMaskedMobiles() != null && !experian.getMaskedMobiles().isEmpty()) {
					
					response.getDetails().setMaskedMobiles(experian.getMaskedMobiles());
					
				}
			}
			if(!checkEligibility(experian)){
				response.getDetails().setEligible(false);
				response.getDetails().setTempClosed("INELIGIBLE");
				experian.setReason(ExperianConstants.CREDIT_LINE_CATEGORY);
				experianDao.save(experian);
				lendingClosedAuditDao.save(new LendingClosedAudit(merchant.getId(), panCard, pincode, "CREDIT_LINE_CATEGORY"));
				return response;
			}
			
		} 
		else if (merchantSummary != null){
			loanEligibilityDTOs.addAll(fetchEligibleLoans(merchantSummary.getLoanType(), merchant));
		}
			
		if(loanEligibilityDTOs==null || loanEligibilityDTOs.isEmpty()) {
	
			logger.warn("No eligible loan found");
			response.getDetails().setEligible(false);
			response.getDetails().setTempClosed("INELIGIBLE");
			lendingClosedAuditDao.save(new LendingClosedAudit(merchant.getId(), panCard, pincode, "INELIGIBLE"));
	
		}
		else {
			
				LoanEligibilityDTO maxLoan=getMaxLoanAmount(loanEligibilityDTOs);		
				response.getDetails().setEligible(true);
				response.getDetails().setCreditAmount((float)(int)maxLoan.getAmount());
				response.getDetails().setCategory(maxLoan.getCategory());
			}
		
		}
		catch(Exception e) {
			
			logger.error("Error occured while calculating loan amount",e);
			
			return getErrorMessage("Error occured while fetching eligible loan");
			
		}
	
		return response;
		
	}
	
	private Boolean checkEligibility(Experian experian){
		List<String> ntcSegments=Arrays.asList("3N","4N");
		List<String> etcSegments=Arrays.asList("3", "4", "15", "16", "27", "28", "39", "40","7", "8", "11", "19", "20", "31", "33", "34", "42", "45","12", "23", "24", "32", "35", "36", "43", "44", "46", "47", "48");
		if(experian!=null && experian.getCategory()!=null) {
			if(ntcSegments.contains(experian.getCategory())) {
				return true;
			}
			else if(etcSegments.contains(experian.getCategory())){
				return true;
			}
			return false;
		}
		return false;
	}
	
	private LoanEligibilityDTO getMaxLoanAmount(List<LoanEligibilityDTO> loanEligibilityDTOs) {
		loanEligibilityDTOs.sort(Comparator.comparing(LoanEligibilityDTO::getPrincipleEdiTenure));
		List<Integer> eligibleTenure = Arrays.asList(1,3,6);
		LoanEligibilityDTO maxLoan=loanEligibilityDTOs.get(0);
		for(LoanEligibilityDTO loanEligibilityDto : loanEligibilityDTOs) {
			if (eligibleTenure.contains(loanEligibilityDto.getPrincipleEdiTenure())) {
				maxLoan = loanEligibilityDto.getAmount() > maxLoan.getAmount() ? loanEligibilityDto : maxLoan;
			}
		}
		
		return maxLoan;
	}
	
	private CreditLoanDetailsResponseDto getResponseForExistingCreditApplication(Merchant merchant, CreditApplication creditApplication,RequestDTO<IneligibleRequestDTO> requestDTO, CreditLoanDetailsResponseDto response,String panCard, Integer pincode){
		try {

			logger.info("Fetching appliaction detail for application id {}",creditApplication.getId());
			
			LoanApplicationDTO loanApplicationDto = fetchLoanApplication(merchant, creditApplication);
			
			if(loanApplicationDto==null) {
				
				logger.error("Not able to fetch loan application data for existng credit application");
				return getErrorMessage("Not able to fetch application details");
			}
			
			//populating application details in response body
			loanApplicationDto.setStatusHeader("Details Submitted");
			response.getDetails().setLoanApplication(loanApplicationDto);
			
			if(creditApplication.getStatus().equalsIgnoreCase("approved")) {
				response.getDetails().getLoanApplication().setStatusHeader("Final Step Remaining");
				CreditAccount creditAccount=creditAccountDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
				if(creditAccount==null) {
					//return getErrorMessage("Credit account not found for approved loan");
					response.getDetails().setAmount(500F);
					response.getDetails().setAccountPresent(false);
				}
//				else if(creditAccount.getStatus().equalsIgnoreCase("PAYMENT_PENDING")){
//					response.getDetails().setAmount(500F);
//					response.getDetails().setAccountPresent(true);
//				}
				else {
					response.getDetails().setAccountPresent(true);
				}
				
				response.getDetails().setEligible(true);
				response.getDetails().setRejected(false);
				response.getDetails().setLoanPage("Deep link for credit loan"); //deep link to credit loan
				return response;
			}
			
			else if(creditApplication.getStatus().equalsIgnoreCase("rejected")) {				
				
				List<CreditApplicationTransition> applicationTransitionList = creditApplicationTransitionDao.findByApplicationIdAndToStatus(creditApplication.getId(), "rejected");
				CreditApplicationTransition applicationTransition = null;
				if(applicationTransitionList != null && !applicationTransitionList.isEmpty()){
					applicationTransition = applicationTransitionList.get(0);
				}
				response.getDetails().setEligible(false);
				response.getDetails().getLoanApplication().setStatusMessage("We regret to inform you that we are unable to process your application as it does not meet the guidelines for document assessment. Please write to us at support@bharatpe.com for any query.");
				
				if("cibil".equalsIgnoreCase(creditApplication.getRejectionReason()) || rejectedInLastNDays(applicationTransition, 7)) {
					response.getDetails().getLoanApplication().setShowReapply(true);
					return responseForLoansRejectedInPast(applicationTransition, creditApplication,response);
				}
				//for the case of kyc and cpv
				response.getDetails().getLoanApplication().setStatusTitle("Verification Failed!");
				
				return response;
			}
			else if(creditApplication.getStatus().equalsIgnoreCase("draft")){
			
					return responseForDraftLoans(response,panCard,pincode); 
			} else{
				return responseForLoanPendingForVerification(creditApplication, requestDTO, merchant,response);
			}
		}
		catch(Exception e){
			logger.error("Error occured processing existing credit application details",e);
		}
		response.setSuccess(false);
		response.setMessage("Error occured while fetching loan details");
		response.setDetails(null);
		
		return response;
	}
	
	public boolean createCreditAccount(CreditApplication creditApplication,Merchant merchant){
		try {
			logger.info("Fetching segment detail from experian table");
			
			Experian experian= experianDao.getByMerchantId(merchant.getId());
			
			if(experian!=null && experian.getColor()!=null) {
				
				logger.info("Inserting new entry in credit_account table");
				
				CreditAccount creditAccount=new CreditAccount();
				
				creditAccount.setMerchantId(creditApplication.getMerchantId());
				creditAccount.setMerchantStoreId(creditApplication.getMerchantStoreId());
				creditAccount.setStatus("ACTIVE");
				creditAccount.setSegment(experian.getColor());
				creditAccount.setLimit(creditApplication.getAmount());
				creditAccount.setAvailableBalance(creditApplication.getAmount());
				creditAccount.setUsedBalance(0D);
				creditAccount.setPayableAmount(0D);
				creditAccount.setInterestDue(0D);
				creditAccount.setMinimumAmountDue(0D);
				creditAccount.setActivationDate(new Date());
				creditAccount.setCreatedAt(new Date());
				creditAccount.setUpdatedAt(new Date());
				creditAccount.setNextBillDate(DateTimeUtil.addDays(new Date(), 20));
				creditAccount.setDueDate(DateTimeUtil.addDays(new Date(), 30));

				creditAccount = creditAccountDao.save(creditAccount);

				LendingCaBalanceDetail lendingCaBalanceDetail = new LendingCaBalanceDetail();
				lendingCaBalanceDetail.setMerchantId(creditApplication.getMerchantId());
				lendingCaBalanceDetail.setMerchantStoreId(creditApplication.getMerchantStoreId());
				lendingCaBalanceDetail.setCreditAccountId(creditAccount.getId());
				lendingCaBalanceDetail.setAccountLimit(creditApplication.getAmount());
				lendingCaBalanceDetail.setAvailableBalance(creditApplication.getAmount());
				lendingCaBalanceDetail.setUsedBalance(0D);
				lendingCaBalanceDetail.setUsedBalanceCl(0D);
				lendingCaBalanceDetail.setUsedBalanceG1(0D);
				lendingCaBalanceDetail.setUsedBalanceG2(0D);
				lendingCaBalanceDetail.setUsedBalanceG3(0D);
				lendingCaBalanceDetail.setInterestDue(0D);
				lendingCaBalanceDetail.setCreatedAt(new Date());
				lendingCaBalanceDetail.setUpdatedAt(new Date());
				lendingCaBalanceDetailDao.save(lendingCaBalanceDetail);

				return true;
		}
		else {
				logger.warn("Experian detail not found");
				return false;
			}
		}
		catch(Exception e) {
			logger.error("Error occured while creating credit account",e);
			return false;
		}
	}
	
//	public Boolean isMinTransactionDone(CreditApplication creditApplication,Merchant merchant){
//		try {
//			logger.info("Fetching transaction amount for merchant {}",merchant.getId());
//			List<CreditApplicationTransition> creditApplicationTransitionList=creditApplicationTransitionDao.findByApplicationIdAndToStatus(creditApplication.getId(),"approved");
//			if(creditApplicationTransitionList==null || creditApplicationTransitionList.isEmpty()){
//				return null;
//			}
//			CreditApplicationTransition creditApplicationTransition=creditApplicationTransitionList.get(0);
//			Date startDate=creditApplicationTransition.getCreatedAt();
//			BigDecimal transactionAmount=(BigDecimal)paymentTransactionNewDao.getAmount(startDate, new Date(), merchant.getId());
//			return transactionAmount.compareTo(new BigDecimal("500"))>=0?true:false;
//		}
//		catch(Exception e) {
//			logger.error("Error occured while fetching transaction amount",e);
//			return null;
//		}
//	}

	
	private CreditLoanDetailsResponseDto responseForLoanPendingForVerification(CreditApplication creditApplication,RequestDTO<IneligibleRequestDTO> requestDTO,Merchant merchant, CreditLoanDetailsResponseDto response) {
		try {
			response.getDetails().setEligible(false);
			
			logger.info("Fetching enach data for application with application id {}",creditApplication.getId());
			LendingClEnach lendingEnach = lendingClEnachDao.findByMerchantIdAndApplicationId(merchant.getId(), creditApplication.getId());
			
			
//			if (lendingEnach!=null && lendingEnach.getStatus() != null && lendingEnach.getStatus()) {
//
//				response.getDetails().setAccountDetails(true);
//				Boolean isEKycDone=isEkycDone(merchant);
//				if(isEKycDone==null) {
//					return getErrorMessage("Error occured while checking for ekyc status");
//				}
//				//enach is successful but ekyc not done
//				//response.getDetails().getLoanApplication().setStatusHeader("Net Banking Linked Successfully");
//				if(!isEKycDone){
//					response.getDetails().getLoanApplication().setStatusTitle("Verification in Progress");
//					response.getDetails().getLoanApplication().setStatusMessage("Loan limit will be approved once your documents are verified");
//				}
//				//enach and kyc both are done
//				else {
//					response.getDetails().getLoanApplication().setStatusTitle("Physical Verification Still Needed");
//					response.getDetails().getLoanApplication().setStatusMessage("Since your loan amount is high, we need to perform physical verification. Our agent will visite you in next 72 hours");
//				}
//
//				return response;
//			}
			
			logger.info("Fetching bank details for merchant {}",merchant.getId());
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
			
			if(merchantBankDetail==null) {
				
				logger.error("Bank details not found for merchant {}",merchant.getId());
				
				return getErrorMessage("Error occured while fethcing bank details");
				
			}
			
			String bankCode;
			
			if (requestDTO.getMeta().getAppVersion() != null && !requestDTO.getMeta().getAppVersion().equalsIgnoreCase("undefined") && Integer.parseInt(requestDTO.getMeta().getAppVersion()) >= 238) {
				bankCode = eNachService.fetchBankCode(merchantBankDetail.getIfscCode().substring(0,4), "BOTH");
			} else {
				bankCode = eNachService.fetchBankCode(merchantBankDetail.getIfscCode().substring(0,4), "NET");
			}
			
			LendingClEnach enachSuccess = lendingClEnachDao.findSuccessEnach(merchant.getId());
			
			try {
				//enach not success and not skipped and bankcode enachable and app version >= 237
				if ((enachSuccess == null || (enachSuccess.getIdentifier() != null && "LIQUILOANS".equalsIgnoreCase(enachSuccess.getIdentifier()))) && (lendingEnach == null || !lendingEnach.getSkip()) && bankCode != null && requestDTO.getMeta().getAppVersion() != null && !requestDTO.getMeta().getAppVersion().equalsIgnoreCase("undefined") && Integer.parseInt(requestDTO.getMeta().getAppVersion()) >= 237) {
					if (enachServiceToUse != null && enachServiceToUse.equalsIgnoreCase("digio")) {
						response.getDetails().setEnach("bharatpe://enachdigio?platform=cl&resultCode=true");//set deep link for enach digio
					} else {
						response.getDetails().setEnach("bharatpe://enachtp?platform=cl&resultCode=true");//set deep link for enach techprocess
					}
				}
			} catch (Exception e) {// exception due to undefined app version
				logger.error("Exception while checking enach bank", e);
				if ((enachSuccess == null || (enachSuccess.getIdentifier() != null && "LIQUILOANS".equalsIgnoreCase(enachSuccess.getIdentifier()))) && (lendingEnach == null || !lendingEnach.getSkip()) && bankCode != null) {
					if (enachServiceToUse != null && enachServiceToUse.equalsIgnoreCase("digio")) {
						response.getDetails().setEnach("bharatpe://enachdigio?platform=cl&resultCode=true");//set deep link for enach digio
					} else {
						response.getDetails().setEnach("bharatpe://enachtp?platform=cl&resultCode=true");//set deep link for enach techprocess
					}
				}
			}
			response.getDetails().getLoanApplication().setStatusHeader("Loan Applied Successfully");
			response.getDetails().getLoanApplication().setSelfVerification(false);
			if (response.getDetails().getEnach() == null && enachSuccess != null) {
				response.getDetails().setEnach("success");
			}
			response.getDetails().getLoanApplication().setStatusTitle("Verification In Progress");
			if(creditApplication.getStatus().equalsIgnoreCase("kyc")) {
				if (enachSuccess != null) {
					response.getDetails().getLoanApplication().setStatusHeader("eNACH Registered Successfully");
					response.getDetails().getLoanApplication().setStatusMessage("Net Banking/ Debit Card Linked successfully. Your Application ID is <b>"+creditApplication.getExternalLoanId()+"</b>. Your loan will be approved in the next 24 hours after document verification.");
				} else {
					response.getDetails().getLoanApplication().setStatusMessage("Your Application ID is <b>" + creditApplication.getExternalLoanId() + "</b>. Your loan will be approved after document verification in the next 48-72 hours.");
				}
			}
			else {
				String account = "xx" + merchantBankDetail.getAccountNumber().substring(merchantBankDetail.getAccountNumber().length()-4);
				response.getDetails().getLoanApplication().setStatusTitle("Physical Verification Pending");
				response.getDetails().getLoanApplication().setStatusMessage("Your Application ID is <b>"+creditApplication.getExternalLoanId()+"</b>. Our agent will visit you within 3 days for document verification. Keep a copy of your PAN Card, Proof of Address <b>Aadhaar Card</b>, Cheque of Bank A/c <b>"+account+"</b> ready for physical verification.");
			}
			
			
			return response;	
			
		}
		catch(Exception e){
			
			logger.error("Error occured while creating response for loan application pending for verification",e);
			return getErrorMessage("Error occured while fetching application details");
		}
		
	}
	
	private LoanApplicationDTO fetchLoanApplication(Merchant merchant, CreditApplication application) {
		
		LoanApplicationDTO loanApplicationDTO = new LoanApplicationDTO();
		
		try {
			
			logger.info("Fetching credit application address detail for applicationId {}",application.getId());
			
			CreditApplicationAddress creditApplicationAddress=creditApplicationAddressDao.findTop1ByMerchantIdAndApplicationIdOrderByIdDesc(merchant.getId(), application.getId());
			
		        ShopDetailsDTO shopDetails = LoanUtil.prepareShopDetailsDTO(application,creditApplicationAddress);
		        SelectedLoanDTO selectedLoan = LoanUtil.prepareSelectedLoanDTO(application);
		        List<DocumentDTO> documents = fetchDocuments(merchant, application);
		        
		        loanApplicationDTO.setApplicationId(String.valueOf(application.getId()));
		        loanApplicationDTO.setShopDetails(shopDetails);
		        loanApplicationDTO.setSelectedLoan(selectedLoan);
		        loanApplicationDTO.setDocuments(documents);
		        loanApplicationDTO.setApplicationStatus(application.getStatus());
		
		}
		catch(Exception e){
			logger.error("Error occured while fetching loan detail ",e);
			return null;
		}
	    return loanApplicationDTO;
	}

	public CreditLoanDetailsResponseDto responseForDraftLoans(CreditLoanDetailsResponseDto response, String panCard, Integer pincode){
		
		response.getDetails().setEligible(true);
		response.getDetails().setRejected(false);
		response.getDetails().setPincode(pincode);
		response.getDetails().setPanCard(panCard);
		response.getDetails().setEnach(null);
		response.getDetails().setAccountDetails(false);
		response.getDetails().setSkipEnatch(true);
		response.setSuccess(true);
		return response;
		
	}
	
	private CreditLoanDetailsResponseDto responseForLoansRejectedInPast(CreditApplicationTransition applicationTransition,CreditApplication creditApplication, CreditLoanDetailsResponseDto response){
		try {
			
		if("cibil".equalsIgnoreCase(creditApplication.getRejectionReason())) {
			logger.info("Rejected because of cibil");
			response.getDetails().getLoanApplication().setStatusMessage("We regret to inform you that we are unable to process your application as it does not meet the guidelines for document assessment. Please write to us on  support@bharatpe.com to apply again.");
			
		} else {
			Date rejecetdAt = applicationTransition.getCreatedAt();
			Calendar calender = Calendar.getInstance();
			calender.setTime(rejecetdAt);
			calender.add(Calendar.DATE, 7);
			response.getDetails().getLoanApplication().setStatusMessage("Please revisit the page after " + new SimpleDateFormat("dd-MM-yyyy").format(calender.getTime()) + " to check your eligibility and apply again.");
			
		}
		}
		catch(Exception e){
			logger.error("Error occured while creating response body for rejected loan");
			return null;
		}

		return response;
	}
	
	private Experian populateExperianDetailsInExperianTable(Merchant merchant,RequestDTO<IneligibleRequestDTO> requestDTO, String clientIp, MerchantSummary merchantSummary,Experian experian){
		try {
			logger.info("Fetching experian details for merchant {}",merchant.getId());
			 experian=experianDao.getByMerchantId(merchant.getId());
			
			if(requestDTO.getPayload().getPincode()!=null){
				
				logger.info("Deleting experian detail from experian table for the merchant {}",merchant.getId());
				experianDao.deleteByMerchantId(merchant.getId());
				
				logger.info("Entering new experian details");
				experian = experianDao.save(new Experian(merchant.getId(), clientIp, merchant.getLatitude(), merchant.getLongitude(), 0, requestDTO.getPayload().getPanCard(), (merchantSummary != null && merchantSummary.getBpScore() != null) ? merchantSummary.getBpScore() : 0D, experian != null ? experian.getRetryCount() : 0, requestDTO.getPayload().getPincode()));
				
			}
		}
		catch(Exception e) {
			
			logger.error("Error occured while fetching experian for merchant ",e);
			return null;
		}
		
		return experian;
	}
		
	private Boolean checkForOgl(Merchant merchant, CreditLoanDetailsResponseDto response,Integer pincode,String panCard){
		try {
			
			logger.info("Fetching lending city for pincode {}",pincode);
			LendingCities lendingCity=lendingCitiesDao.findActiveCityByPincode(pincode);
			
			if (pincode != null && lendingCity == null) {
				
				PincodeCityStateMapping pincodeCityStateMapping = pincodeCityStateMappingDao.findByPincode(pincode);
				lendingClosedAuditDao.save(new LendingClosedAudit(merchant.getId(), panCard, pincode, "OGL"));
				
				response.getDetails().setEligible(false);
				response.getDetails().setRejected(false);
				response.getDetails().setRejectReason(null);
				response.getDetails().setPanCard(panCard);
				response.getDetails().setOgl(true);
				response.getDetails().setPincode(pincode);
				if (pincodeCityStateMapping != null && !StringUtils.isEmpty(pincodeCityStateMapping.getCity())) {
					response.getDetails().setCity(pincodeCityStateMapping.getCity());
				} else {
					response.getDetails().setCity(" ");
				}
				
				response.setSuccess(true);
				return true;
		}
		
		}
		catch(Exception e) {
			
			logger.error("Error occured while checking for OGL",e);
			return null;
		}
		return false;
	}
	
	private boolean rejectedInLastNDays(CreditApplicationTransition applicationTransition, int nDays) {
		try {
			if(applicationTransition == null) {
				return false;
			}
			
			Date rejectedTimestamp = applicationTransition.getCreatedAt();
			Date nDaysBeforeTimestamp = new Date(System.currentTimeMillis() - (long) nDays * 24 * 3600 * 1000);
			
			if(rejectedTimestamp.compareTo(nDaysBeforeTimestamp) > 0) {
				logger.info("Application with id {} has been rejected in last {} days", applicationTransition.getApplicationId(), nDays);
				return true;
			}
			
		} catch(Exception ex) {
			logger.error("Exception while checking if rejected in n days for application id {}, Exception is {}", applicationTransition.getApplicationId(), ex);
		}
		
		return false;
	}
	
	private boolean checkForOrganizedMerchant(Merchant merchant, CreditLoanDetailsResponseDto response){
		try {
			
			logger.info("Check for organized merchant");
			logger.info("fetching store related details for the merchant {}",merchant.getId());
			List<MerchantStore> stores=merchantStoreDao.findByMerchant(merchant);
			if(stores!=null && !stores.isEmpty()) {
				
				logger.warn("Merchant is organized");
				
				response.getDetails().setEligible(false);
				response.getDetails().setRejected(false);
				response.getDetails().setRejectReason(null);
				response.getDetails().setPanCard(null);
				response.setSuccess(true);
				response.getDetails().setOrganised(true);
				
				return true;
			}
		}
		catch(Exception e){
			logger.error("Error occured while checking for merchant type i.e., organized/unorganised ",e);
			
			response.setSuccess(false);
			response.setMessage("Error occured while checking for organised merchant");
			response.setDetails(null);
			
			return true;
			}
		return false;
		}
	
	private List<DocumentDTO> fetchDocuments(Merchant merchant, CreditApplication creditApplication) {
		
		try {
			
			logger.info("Fetching documents for merchant {}",merchant.getId());
			List<DocumentDTO> documents = new ArrayList<>();
			
			List<MerchantDocumentProof> documentsIdProofList = merchantDocumentProofDao.findByMerchantIdAndOwnerIdAndOwnerType(merchant.getId(), creditApplication.getId(), "LENDING");
			for(MerchantDocumentProof merchantIdProof : documentsIdProofList) {
				DocumentDTO document = new DocumentDTO();
				document.setId(merchantIdProof.getId());
				document.setProofType(merchantIdProof.getProofType());
				//document.setSinglePageDocument(documentsIdProof.() != null && documentsIdProof.getSinglePage() == 0 ? false : true);
			}
			return documents;
		}
		catch(Exception e) {
			logger.error("Error occured while fetching merchanr document proof for merchant {}",merchant.getId());
		}
		return null;
	}
	
private List<LoanEligibilityDTO> fetchEligibleLoans(String loanType, Merchant merchant) {
		
		List<LoanEligibilityDTO> availableLoanDTOList = new ArrayList<>();

		List<AvailableLoan> availableLoanList = availableLoanDao.findByMerchantIdAndTypeAndLoanConstructOrderByAmountDesc(merchant.getId(), loanType, "CONSTRUCT_3");;

		if(availableLoanList == null || availableLoanList.isEmpty()) {
			
			availableLoanList = availableLoanDao.findByMerchantIdAndTypeAndLoanConstructOrderByAmountDesc(merchant.getId(), loanType, "CONSTRUCT_1");
		}
		
		if(availableLoanList == null || availableLoanList.isEmpty()) {
			logger.error("No available loan found for merchant id {}", merchant.getId());
			return availableLoanDTOList;
		}
		
		List<LendingCategories> lendingCategoriesList = lendingCategoryDao.findByStatus(GeneralStatus.ACTIVE.toString());
		for(AvailableLoan availableLoan : availableLoanList) {
			LendingCategories lendingCategoryDetail = fetchCategoryDetails(lendingCategoriesList, availableLoan.getCategory());
			if(lendingCategoryDetail != null) {
				LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();
				LoanBreakupDetail breakup = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategoryDetail, null);
				
				loanEligibilityDTO.setProcessingFee(breakup.getProcessingFee());
				loanEligibilityDTO.setInterestRate(breakup.getEffectiveInterestRate());
				loanEligibilityDTO.setAmount(availableLoan.getAmount().intValue());
				loanEligibilityDTO.setCategory(lendingCategoryDetail.getCategory());
				loanEligibilityDTO.setInterestAmount(breakup.getTotalInterestAmount());
				loanEligibilityDTO.setEdi(breakup.getEdi());
				loanEligibilityDTO.setRepayment(breakup.getRepayment());
				loanEligibilityDTO.setDisbursementAmount(breakup.getDisbursementAmount());
				loanEligibilityDTO.setTenure(lendingCategoryDetail.getPayableConverter());
				loanEligibilityDTO.setConstruct(availableLoan.getLoanConstruct());
				//loanEligibilityDTO.setList(LoanCalculationUtil.prepareLabels(breakup, breakup.getIoOrFreeEdiTenure()));
				loanEligibilityDTO.setType(breakup.getType());
				loanEligibilityDTO.setOptionEnable(true);

				availableLoanDTOList.add(loanEligibilityDTO);
			} else {
				logger.error("No lending category found for merchant {} and category {}", merchant.getId(), availableLoan.getCategory());
			}
		}
		return availableLoanDTOList;
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
	
	public CreditLoanDetailsResponseDto getErrorMessage(String message) {
		
		CreditLoanDetailsResponseDto errorResponse=new CreditLoanDetailsResponseDto();
		
		errorResponse.setSuccess(false);
		errorResponse.setMessage(message);
		errorResponse.setDetails(null);
		
		return errorResponse;
		
	}
	
	public boolean isMerchantFromCreditLine(Merchant merchant) {
		CreditLineMerchant creditLineMerchant=creditLineMerchantDao.findByMerchantId(merchant.getId());
		return creditLineMerchant != null;
	}
	
	}