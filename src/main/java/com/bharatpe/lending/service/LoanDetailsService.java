package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.NotificationProvider;
import com.bharatpe.common.enums.Status.LendingStatus;
import com.bharatpe.common.handlers.EmailHandler;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.MerchantDocumentProof;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.dto.LoanDetailsResponseDTO.LoanDetailsDTO;
import com.bharatpe.lending.entity.LendingBlockedPancard;
import com.bharatpe.lending.entity.LendingPrebookTarget;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class LoanDetailsService {

	private final Logger logger = LoggerFactory.getLogger(LoanDetailsService.class);

	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;

	@Autowired
	MerchantSummaryDao merchantSummaryDao;

	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;

	@Autowired
	LendingApplicationDao lendingApplicationDao;

	@Autowired
	LendingGstDao lendingGstDao;

	@Autowired
	SmsServiceHandler smsServiceHandler;

	@Autowired
	LendingCategoryDao lendingCategoryDao;

	@Autowired
	DocumentsIdProofDao documentsIdProofDao;

	@Autowired
	LendingPancardDao lendingPancardDao;

	@Autowired
	LendingEDIScheduleDao lendingEDIScheduleDao;

	@Autowired
	MerchantStoreDao merchantStoreDao;

	@Autowired
	ExperianDao experianDao;

	@Autowired
	LoanEligibleService loanEligibleService;

	@Autowired
	LoanUtil loanUtil;

	@Value("${experian.enable:true}")
	Boolean EXPERIAN_ENABLED;

	@Autowired
	EmailHandler emailHandler;

	@Autowired
	LendingCitiesDao lendingCitiesDao;

	@Autowired
	ENachService eNachService;

	@Autowired
	TopupLoanEligibleService topupLoanEligibleService;

	@Autowired
	PincodeCityStateMappingDao pincodeCityStateMappingDao;

	@Autowired
	LendingClosedAuditDao lendingClosedAuditDao;

	@Autowired
	LendingPrebookTargetDao lendingPrebookTargetDao;

	@Autowired
	PaymentTransactionNewDao paymentTransactionNewDao;

	@Autowired
	LendingPartnerOffersDao lendingPartnerOffersDao;

	@Autowired
	EligibleLoanDao eligibleLoanDao;

	@Autowired
	LendingLedgerDao lendingLedgerDao;

	@Autowired
	LendingRedCitiesDao lendingRedCitiesDao;

	@Autowired
	RedisNotificationService redisNotificationService;

	@Autowired
	CreditLineMerchantDao creditLineMerchantDao;

	@Autowired
	NewToBharatpeService newToBharatpeService;

	@Autowired
	LendingBlockedPancardDao lendingBlockedPancardDao;

	@Autowired
	LendingBharatswipeOffersDao lendingBharatswipeOffersDao;

	@Autowired
	BankListDao bankListDao;

	@Autowired
	MerchantDocumentProofDao merchantDocumentProofDao;

	@Autowired
	APIGatewayService apiGatewayService;

	@Autowired
	BPEnachDao bpEnachDao;

	@Autowired
	PhonebookDao phonebookDao;

	@Autowired
	LendingMerchantDropoffDao lendingMerchantDropoffDao;

	@Autowired
	BharatPeEnachDao bharatPeEnachDao;

	@Autowired
	EnachErrorHandingService enachErrorHandingService;

	List<Long> exemptMerchant = Arrays.asList(2411647L, 1210933L, 4340760L, 2097359L, 7090157L, 6518986L, 1141505L, 3L, 3543643L, 9319451L, 8891247L, 2078363L);

//	@Transactional

	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	public LoanDetailsResponseDTO fetchLoanDetails(Merchant merchant, RequestDTO<IneligibleRequestDTO> requestDTO, String clientIp, String token) {
		LoanDetailsResponseDTO response = new LoanDetailsResponseDTO();
		try {
			if(isMerchantFromCreditLine(merchant)) {
				response.setDeeplink("bharatpe://dynamic?key=credit-line");
				response.setSuccess(true);
				response.setMessage("Credit line merchant");
				return response;
			}
			MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchant.getId());
			List<LendingPartnerOffers> lendingPartnerOffers = lendingPartnerOffersDao.findByMerchantIdAndPartnerAndMobile(merchant.getId(), "ZOMATO", merchant.getMobile());
			LendingRedCities redCity = null;
			boolean isZomato = false;
			if (lendingPartnerOffers != null && !lendingPartnerOffers.isEmpty()) {
				isZomato = true;
			}
			boolean eligibleFlag = true;
			boolean rejected = false;
			boolean noExperian = false;
			boolean accountDetails = false;
			boolean skipEnatch = false;
			String enach = null;
			List<String> maskedMobiles = null;
			String rejectReason = null;
			String panCard = null;
			String tempClosed = null;
			BpEnach enachSuccess = bpEnachDao.findSuccessEnach(merchant.getId());
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
			if (enachSuccess != null && enachSuccess.getAccountNumber() != null && !enachSuccess.getAccountNumber().equals(merchantBankDetail.getAccountNumber())) {
				enachSuccess = null;
			}
			Experian experian = experianDao.getByMerchantId(merchant.getId());
			List<MerchantStore> stores = merchantStoreDao.findByMerchant(merchant);
			Integer pincode = null;
			LendingCities lendingCity = null;
			if (requestDTO.getPayload().getPanCard() != null) {
				if (requestDTO.getPayload().getPincode() == null) {
					logger.info("pincode bug for merchant:{}", merchant.getId());
					emailHandler.sendEmail(new ArrayList<String>(){{add("khushal.virmani@bharatpe.com");
						add("mihit@bharatpe.com");add("anubhav.mathur@bharatpe.com");}}, "Pincode Bug", "merchant id: " + merchant.getId() + ", mid:" + merchant.getMid());
					LoanDetailsResponseDTO response1 = new LoanDetailsResponseDTO();
					response1.setSuccess(false);
					response1.setMessage("Pincode not found");
					return response1;
				}
				panCard = requestDTO.getPayload().getPanCard();
				if (experian != null) {
					experian.setPancardNumber(requestDTO.getPayload().getPanCard());
					experian.setBpScore((merchantSummary != null && merchantSummary.getBpScore() != null) ? merchantSummary.getBpScore() : 0D);
					experian.setPincode(requestDTO.getPayload().getPincode());
					experian.setResponse(null);
					experian.setBureau(null);
					experianDao.save(experian);
				} else {
					experian = experianDao.save(new Experian(merchant.getId(), clientIp, merchant.getLatitude() != null && merchant.getLatitude() <= 90 ? merchant.getLatitude() : null, merchant.getLongitude() != null && merchant.getLongitude() <= 90 ? merchant.getLongitude() : null, 0, requestDTO.getPayload().getPanCard(), (merchantSummary != null && merchantSummary.getBpScore() != null) ? merchantSummary.getBpScore() : 0D, experian != null ? experian.getRetryCount() : 0, requestDTO.getPayload().getPincode()));
				}
			}
			if (experian != null && experian.getPancardNumber() != null) {
				panCard = experian.getPancardNumber();
				if (merchantSummary != null && merchantSummary.getBpScore() != null) {
					experian.setBpScore(merchantSummary.getBpScore());
				}
			}
			if (requestDTO.getPayload().getPincode() != null) {
				pincode = requestDTO.getPayload().getPincode();
			} else if (experian != null && experian.getPincode() != null) {
				pincode = experian.getPincode();
			}

			if (pincode != null) {
				lendingCity = lendingCitiesDao.findActiveCityByPincode(pincode);
				redCity = lendingRedCitiesDao.findByPincode(pincode);
			}

			if(stores != null && !stores.isEmpty()) {
				LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
				loanDetailsDTO.setEligibility(new ArrayList<>());
				loanDetailsDTO.setHistory(new ArrayList<>());
				loanDetailsDTO.setEligible(false);
				loanDetailsDTO.setRejected(false);
				loanDetailsDTO.setRejectReason(null);
				loanDetailsDTO.setPanCard(null);
				loanDetailsDTO.setZomato(isZomato);
				loanDetailsDTO.setBharatPeClubMember(apiGatewayService.eligibleForProcessingFee(merchant.getId()));
				response.setDetails(loanDetailsDTO);
				response.setSuccess(true);
				if (experian != null) {
					loanUtil.auditExperian(experian);
				}
				return response;
			}
			BankList bankList = bankListDao.findByBankCode(merchantBankDetail.getBankCode());
			//check for payments bank
			boolean paymentsBank = bankList != null && bankList.getIsPaymentBank();
			if (EXPERIAN_ENABLED) {
				if (experian != null && experian.getRejected() && experian.getRejectedDate() != null && LoanUtil.getDateDiffInDays(experian.getRejectedDate(), new Date()) < 30) {

					if(Objects.nonNull(experian.getReason()) && (experian.getReason().equalsIgnoreCase(ExperianConstants.FOS_APP) || experian.getReason().equalsIgnoreCase(ExperianConstants.MULTIPLE_PSP_APPS))) {
						rejected = false;
					}else{
						rejected = true;
						rejectReason = experian.getReason();
					}
				} else if (experian != null && experian.getRejected() && experian.getRejectedDate() != null && LoanUtil.getDateDiffInDays(experian.getRejectedDate(), new Date()) >= 30) {
					experian.setRejected(false);
					experian.setReason(null);
					experian.setRejectedDate(null);
					experianDao.save(experian);
				}
			} else {
				panCard = requestDTO.getPayload().getPanCard();
			}
			List<LendingPaymentSchedule> lendingPaymentScheduleList = lendingPaymentScheduleDao.findByMerchantIdAndCreditLoanOrderByIdDesc(merchant.getId(),false);
			boolean repeatLoan = lendingPaymentScheduleList != null && lendingPaymentScheduleList.size() > 0;

			LendingPaymentSchedule activeLoan = getActiveLoan(lendingPaymentScheduleList);
			boolean isActiveLoan = activeLoan != null;

			List<LendingApplication> lendingApplicationList = lendingApplicationDao.fetchLatestOpenApplication(merchant);

			LendingApplication lendingApplication = null;
			if(lendingApplicationList != null && !lendingApplicationList.isEmpty()) {
				lendingApplication = lendingApplicationList.get(0);
			}
			boolean showTarget = false;
			double targetTpv = 0d;
			try {
				// 4 may to 13may target and 24 april to 3 may tpv (lockdown end date - 3may)
				if (lendingApplication != null && lendingApplication.getLoanType() != null && lendingApplication.getLoanType().equalsIgnoreCase("PREBOOK") && "approved".equals(lendingApplication.getStatus()) && !"disbursed".equalsIgnoreCase(lendingApplication.getLoanDisbursalStatus())) {
					LendingPrebookTarget lendingPrebookTarget = lendingPrebookTargetDao.findByMerchantIdAndApplicationId(lendingApplication.getMerchant().getId(), lendingApplication.getId());
					if (lendingPrebookTarget != null && !lendingPrebookTarget.getTargetAchieved()) {
						// check last 10 days transaction tpv from lockdown end date/approved date
						DateTime lockdownEndDate = new DateTime(lendingPrebookTarget.getLockdownEndDate()).plusDays(1);
						Calendar c = Calendar.getInstance();
						c.setTime(lendingPrebookTarget.getLockdownEndDate());
						c.add(Calendar.DAY_OF_MONTH, -9);
						Date startDate = c.getTime();
						double tpv = ((BigDecimal) paymentTransactionNewDao.getAmount(startDate, lockdownEndDate.toDate(), lendingApplication.getMerchant().getId())).doubleValue();
						if (tpv < lendingPrebookTarget.getTarget()) {
							// check last 10 days transaction tpv from today
							c.setTime(new Date());
							c.add(Calendar.DAY_OF_MONTH, -9);
							double currentTpv = ((BigDecimal) paymentTransactionNewDao.getAmount(c.getTime(), new Date(), lendingApplication.getMerchant().getId())).doubleValue();
							if (currentTpv < lendingPrebookTarget.getTarget()) {
								showTarget = true;
								targetTpv = lendingPrebookTarget.getTarget() - currentTpv;
							}
						}
						if (!showTarget) {
							lendingPrebookTargetDao.updateTargetAchieved(lendingPrebookTarget.getMerchantId(), new Date());
						}
					}
				}
			} catch (Exception e) {
				logger.error("Exception while calculating prebook target for merchant: {}", merchant.getId());
				logger.error("Exception---", e);
			}

			List<LoanHistoryDTO> orignalHistoryDTOs = fetchLoanHistory(lendingApplication, lendingPaymentScheduleList, activeLoan, repeatLoan, enachSuccess, showTarget, targetTpv);
			List<LoanHistoryDTO> loanHistoryDTOs = orignalHistoryDTOs;
			LoanApplicationDTO loanApplicationDTO = fetchLoanApplication(merchant, lendingApplication);
			List<LoanEligibilityDTO> loanEligibilityDTOs = new ArrayList<>();
			String bankCode = eNachService.fetchBankCode(merchantBankDetail.getIfscCode().substring(0, 4), "BOTH");
			if(lendingApplication != null) {
				BharatPeEnach enachSkipped = bharatPeEnachDao.isSkipped(merchant.getId(), lendingApplication.getId());
				if ((enachSuccess != null && repeatLoan) || (enachSuccess != null && lendingApplication.getLoanAmount() < 100000) || (lendingApplication.getPhysicalVerificationStatus() != null && !lendingApplication.getPhysicalVerificationStatus().equalsIgnoreCase("null"))) {
					loanApplicationDTO.setSelfVerification(false);
				}
				if("rejected".equals(lendingApplication.getStatus())) {
					if("REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil()) || rejectedInLastNDays(lendingApplication, 7)) {
						eligibleFlag = false;
						loanHistoryDTOs = null;
						loanApplicationDTO.setStatusHeader("Loan Application Submitted");
						loanApplicationDTO.setStatusTitle("Verification Failed!");
						if("REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil())) {
							loanApplicationDTO.setStatusMessage("We regret to inform you that we are unable to process your application as it does not meet the guidelines for document assessment. Please write to us on  support@bharatpe.com to apply again.");
						} else {
							Date rejecetdAt = lendingApplication.getUpdatedAt();
							Calendar calender = Calendar.getInstance();
							calender.setTime(rejecetdAt);
							calender.add(Calendar.DATE, 7);
							loanApplicationDTO.setStatusMessage("Please revisit the page after " + new SimpleDateFormat("dd-MM-yyyy").format(calender.getTime()) + " to check your eligibility and apply again.");
						}
					}
					if ("PREBOOK".equalsIgnoreCase(lendingApplication.getLoanType())) {
						loanApplicationDTO.setStatusTitle("Loan Offer Expired!");
						loanApplicationDTO.setStatusMessage("We regret to inform you that we are unable to process your loan since you did not meet your QR Transaction target.");
					}
				} else if ("approved".equals(lendingApplication.getStatus()) && !"disbursed".equalsIgnoreCase(lendingApplication.getLoanDisbursalStatus())) {
					eligibleFlag = false;
					accountDetails = true;
					if (enachSuccess == null && enachSkipped == null && bankCode != null) {
						enach = apiGatewayService.getEnachProvider(token, merchant.getId());
						skipEnatch = true;
					}
					if ("PREBOOK".equalsIgnoreCase(lendingApplication.getLoanType())) {
						loanApplicationDTO.setStatusHeader("Loan Approved");
						loanApplicationDTO.setStatusTitle("Loan Transfer Post Lockdown");
						loanApplicationDTO.setStatusMessage("Your Application ID is " + lendingApplication.getExternalLoanId() + ". The amount will reflect in your bank account within <b>10 days</b> after Lockdown ends.");
					} else {
						loanApplicationDTO.setStatusHeader("Loan Approved");
						loanApplicationDTO.setStatusTitle("Loan Approved");
						if (enachSuccess != null && repeatLoan) {
							loanApplicationDTO.setStatusMessage("Net Banking / Debit Card Linked Successfully!\nAmount will reflect in your A/c in 24 hours.");
						} else {
							loanApplicationDTO.setStatusMessage("The amount will reflect in your Bank A/c in the next 7-10 days. Keep transacting on BharatPe QR to get money in your Account faster.");
						}
					}
				} else if ("pending_verification".equals(lendingApplication.getStatus())) {

					BharatPeEnach bharatPeEnach = bharatPeEnachDao.findByMerchantIdAndApplicationId(merchant.getId(), lendingApplication.getId());

					if(Objects.nonNull(bharatPeEnach) && !bharatPeEnach.getSuccess()){

						EnachErrorMessageDTO enachMessage = enachErrorHandingService.enachErrorResponse(bharatPeEnach, merchant, lendingApplication, experian);
						loanApplicationDTO.setEnachErrorResponse(enachMessage);
						skipEnatch = enachMessage.getSkipEnach();
					}

					//enach not success and not skipped and bankcode enachable
					if (enachSuccess == null && enachSkipped == null && bankCode != null) {
						enach = apiGatewayService.getEnachProvider(token, merchant.getId());
					}
					if(enachSuccess != null && "OGL".equalsIgnoreCase(lendingApplication.getLoanType())) {
						enach = null;
						skipEnatch = true;
					}
					eligibleFlag = false;
					loanHistoryDTOs = null;
					if (enach != null) {
						loanApplicationDTO.setStatusHeader("Details Submitted");//enach screen
					} else {
						loanApplicationDTO.setStatusHeader("Loan Applied Successfully");
					}
					if ("PREBOOK".equalsIgnoreCase(lendingApplication.getLoanType())) {
						loanApplicationDTO.setStatusTitle("Verification Pending");
						loanApplicationDTO.setStatusMessage("Your Application ID is " + lendingApplication.getExternalLoanId() + ". Our executive will visit you for verification. Please keep a cheque of your bank A/c & a proof of ownership ready. Your loan will be disbursed within 72 hours after verification.");
					} else {
						loanApplicationDTO.setStatusHeader("Loan Applied Successfully");
						if (enachSuccess != null) {
							accountDetails = true;
							loanApplicationDTO.setStatusTitle("Net Banking / Debit Card Linked Successfully!");
							loanApplicationDTO.setStatusMessage("Your Application ID is " + lendingApplication.getExternalLoanId() + ". Loan will be transferred in 24-48 hours after document verification.");
						} else if (enachSkipped == null) {
							loanApplicationDTO.setStatusTitle("Net Banking / Debit Card could not be Linked!");
							loanApplicationDTO.setStatusMessage("Your Application ID is " + lendingApplication.getExternalLoanId() + ". Our executive will visit you for verification in the next 3 days. Please keep a cheque of your bank A/c & a proof of ownership ready. Your loan will be disbursed within 24 hours after verification.");
						} else {
							loanApplicationDTO.setStatusTitle("Application submitted successfully!");
							loanApplicationDTO.setStatusMessage("Your Application ID is " + lendingApplication.getExternalLoanId() + ". Our executive will visit you for verification in the next 3 days. Please keep a cheque of your bank A/c & a proof of ownership ready. Your loan will be disbursed within 24 hours after verification.");
						}
					}
				} else if("draft".equals(lendingApplication.getStatus())) {
					eligibleFlag = false;
					loanHistoryDTOs = null;
				}
			}
//			if (lendingApplication != null && lendingApplication.getAgreementAt() != null && "REGULAR".equals(lendingApplication.getLoanType()) && lendingApplication.getLoanAmount() > 50000 && LoanUtil.getDateDiffInDays(lendingApplication.getAgreementAt(), new Date()) > 3) {
//				skipEnatch = true;
//			}
//			if (lendingApplication != null && "BHARAT_SWIPE".equals(lendingApplication.getLoanType())) {
//				skipEnatch = true;
//			}

			if(activeLoan != null) {
				logger.info("Active loan found for merchant with ID {}", merchant.getId());
				boolean syncContacts = false;
				Optional<Phonebook> phonebook = phonebookDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
				if (!phonebook.isPresent() || phonebook.get().getContactsCount() == null) {
					logger.info("Contacts not synced for merchant:{}", merchant.getId());
					syncContacts = true;
				}
				LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
				loanDetailsDTO.setSyncContacts(syncContacts);
				loanDetailsDTO.setEligibility(loanEligibilityDTOs);
				loanDetailsDTO.setHistory(orignalHistoryDTOs);
				loanDetailsDTO.setEligible(true);
				loanDetailsDTO.setRejected(rejected);
				loanDetailsDTO.setRejectReason(rejectReason);
				loanDetailsDTO.setPanCard(panCard);
				loanDetailsDTO.setAccountDetails(accountDetails);
				loanDetailsDTO.setZomato(isZomato);
				loanDetailsDTO.setActiveLoan(isActiveLoan);
				loanDetailsDTO.setHasExperian(experian != null);
				if(!(pincode != null && lendingCity == null) && !isZomato) {
					List<LoanEligibilityDTO> topupLoans = topupLoanEligibleService.fetchTopupLoans(merchant, experian, merchantSummary, merchantBankDetail, lendingPaymentScheduleList, bankCode);
					loanDetailsDTO.setTopupLoan(topupLoans == null || topupLoans.isEmpty() ? null : topupLoans);
					if(!topupLoans.isEmpty() && lendingApplication != null && !StringUtils.isEmpty(loanApplicationDTO.getApplicationStatus()) && ("pending_verification".equalsIgnoreCase(loanApplicationDTO.getApplicationStatus()) || "approved".equalsIgnoreCase(loanApplicationDTO.getApplicationStatus()) || "rejected".equalsIgnoreCase(loanApplicationDTO.getApplicationStatus())) && "TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())) {
						loanDetailsDTO.setLoanApplication(loanApplicationDTO);
						double prevLoanUnpaidAmount = (activeLoan.getLoanAmount() - activeLoan.getPaidPrinciple()) + activeLoan.getDueInterest();
						Integer disbursementAmount = loanDetailsDTO.getLoanApplication().getSelectedLoan().getDisbursementAmount() - (int) prevLoanUnpaidAmount;
						loanDetailsDTO.getLoanApplication().getSelectedLoan().setDisbursementAmount(disbursementAmount);
						BharatPeEnach enachSkipped = bharatPeEnachDao.isSkipped(merchant.getId(), lendingApplication.getId());
						if (enachSuccess == null && enachSkipped == null && bankCode != null) {
							enach = apiGatewayService.getEnachProvider(token, merchant.getId());
							loanDetailsDTO.getTopupLoan().get(0).setEnach(enach);
							experian = experianDao.getByMerchantId(merchant.getId());
//							if ("approved".equalsIgnoreCase(loanApplicationDTO.getApplicationStatus()) && (experian == null || (experian.getColor() != null && !"AMBER".equalsIgnoreCase(experian.getColor())))) {
//								loanDetailsDTO.getTopupLoan().get(0).setSkipEnatch(true);
//							} else {
//								loanDetailsDTO.getTopupLoan().get(0).setSkipEnatch(false);
//							}
						}
					} else if (lendingApplication != null && loanApplicationDTO != null && "TOPUP".equalsIgnoreCase(lendingApplication.getLoanType()) && !StringUtils.isEmpty(loanApplicationDTO.getApplicationStatus()) && "draft".equalsIgnoreCase(loanApplicationDTO.getApplicationStatus())) {
						loanDetailsDTO.setLoanApplication(loanApplicationDTO);
					} else {
						loanDetailsDTO.setLoanApplication(null);
					}
				} else {
					loanDetailsDTO.setTopupLoan(null);
				}
				loanDetailsDTO.setSkipEnatch(skipEnatch);
				loanDetailsDTO.setEnach(enach);
				loanDetailsDTO.setBharatPeClubMember(apiGatewayService.eligibleForProcessingFee(merchant.getId()));
				loanDetailsDTO.setBureauScore(experian != null ? experian.getExperianScore() : null);
				response.setDetails(loanDetailsDTO);
				response.setSuccess(true);
				return response;
			}
			if (requestDTO.getPayload().isIOS() != null && requestDTO.getPayload().isIOS()) {
				logger.info("request from IOS for merchant:{}", merchant.getId());
				enach = "bharatpe://enachtp";
			}

			if(lendingApplication != null && !eligibleFlag) {
				boolean syncContacts = false;
				Optional<Phonebook> phonebook = phonebookDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
				if (!phonebook.isPresent() || phonebook.get().getContactsCount() == null) {
					logger.info("Contacts not synced for merchant:{}", merchant.getId());
					syncContacts = true;
				}
				LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
				loanDetailsDTO.setSyncContacts(syncContacts);
				loanDetailsDTO.setEligibility(loanEligibilityDTOs);
				loanDetailsDTO.setHistory(loanHistoryDTOs);
				loanDetailsDTO.setLoanApplication(loanApplicationDTO);
				loanDetailsDTO.setEligible(true);
				loanDetailsDTO.setRejected(rejected);
				loanDetailsDTO.setRejectReason(rejectReason);
				loanDetailsDTO.setPanCard(panCard);
				loanDetailsDTO.setPincode(pincode);
				loanDetailsDTO.setEnach(enach);
				loanDetailsDTO.setAccountDetails(accountDetails);
				loanDetailsDTO.setSkipEnatch(skipEnatch);
				loanDetailsDTO.setZomato(isZomato);
				loanDetailsDTO.setHasExperian(experian != null);
				loanDetailsDTO.setBharatPeClubMember(apiGatewayService.eligibleForProcessingFee(merchant.getId()));
				loanDetailsDTO.setBureauScore(experian != null ? experian.getExperianScore() : null);
				response.setDetails(loanDetailsDTO);
				response.setSuccess(true);
				return response;
			}
			PincodeCityStateMapping pincodeCityStateMapping = null;
			if (!exemptMerchant.contains(merchant.getId()) && ((pincode != null && lendingCity == null && redCity != null) || (lendingCity != null && lendingCity.getCategoriesAllowed() != null && !lendingCity.getCategoriesAllowed().contains(merchant.getBusinessCategory())))) {
				pincodeCityStateMapping = pincodeCityStateMappingDao.findByPincode(pincode);
				lendingClosedAuditDao.save(new LendingClosedAudit(merchant.getId(), panCard, pincode, "OGL"));
				if (experian != null) {
					experian.setEligibleAmount(null);
					experian.setEligibleTenure(null);
					experian.setLoanType(null);
					experian.setReason(ExperianConstants.OGL);
					experianDao.save(experian);
					loanUtil.auditExperian(experian);
				}
				LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
				loanDetailsDTO.setEligibility(new ArrayList<>());
				loanDetailsDTO.setHistory(new ArrayList<>());
				loanDetailsDTO.setEligible(false);
				loanDetailsDTO.setRejected(false);
				loanDetailsDTO.setRejectReason(null);
				loanDetailsDTO.setPanCard(panCard);
				loanDetailsDTO.setOgl(true);
				loanDetailsDTO.setPincode(pincode);
				loanDetailsDTO.setHasExperian(experian != null);
				loanDetailsDTO.setZomato(isZomato);
				loanDetailsDTO.setSkipEnatch(skipEnatch);
				loanDetailsDTO.setBharatPeClubMember(apiGatewayService.eligibleForProcessingFee(merchant.getId()));
				loanDetailsDTO.setBureauScore(experian != null ? experian.getExperianScore() : null);
				if (pincodeCityStateMapping != null && !StringUtils.isEmpty(pincodeCityStateMapping.getCity())) {
					loanDetailsDTO.setCity(pincodeCityStateMapping.getCity());
				} else {
					loanDetailsDTO.setCity(" ");
				}
				response.setDetails(loanDetailsDTO);
				response.setSuccess(true);
				return response;
			}
			boolean yellowPincode = false;
			if (!isZomato && lendingCity == null && redCity == null) {
				yellowPincode = true;
			}

			if (EXPERIAN_ENABLED && experian != null && !rejected) {

				try {
					loanEligibilityDTOs.addAll(loanEligibleService.getNewLoanDetails(merchant, experian, merchantSummary, merchantBankDetail, requestDTO.getPayload().isSkip(), requestDTO.getPayload().getPanCard(), isZomato,"NORMAL", yellowPincode, bankCode));
				} catch (Exception e) {
					logger.error("Exception fetching eligible loan for merchant: {}", merchant.getId());
					logger.error("Exception---", e);
					emailHandler.sendEmail(new ArrayList<String>(){{add("khushal.virmani@bharatpe.com");}}, "Eligible Loan Exception", "");
				}
				if (!experian.getRejected() && experian.getReason() != null) {
					lendingMerchantDropoffDao.save(new LendingMerchantDropoff(experian.getMerchantId(), "REGULAR", experian.getReason(), null));
				}
				if (experian.getRejected()) {
					if(Objects.nonNull(experian.getReason()) && (experian.getReason().equalsIgnoreCase(ExperianConstants.FOS_APP) || experian.getReason().equalsIgnoreCase(ExperianConstants.MULTIPLE_PSP_APPS))) {
						rejected = false;
					}else{
						rejected = true;
						rejectReason = experian.getReason();
					}
				}
				if (experian.getRetryCount() == 1) {//experian timeout
					return null;
				}
				if (experian.isNoExperian()) {
					noExperian = true;
					if (experian.getMaskedMobiles() != null && !experian.getMaskedMobiles().isEmpty()) {
						maskedMobiles = experian.getMaskedMobiles();
					}
				}
				//fetching Zomato loans
				if (isZomato && !rejected) {
					loanEligibilityDTOs.clear();
					loanEligibilityDTOs.addAll(fetchZomatoOffers(experian, lendingPartnerOffers));
				}
				//fetching NTB loans
				if (!rejected && !isZomato && !exemptMerchant.contains(merchant.getId())) {
					experian.setReason(null);
					experianDao.save(experian);
					if (bankCode == null && loanEligibilityDTOs.isEmpty()) {
						logger.info("Non enachable bank code, so rejecting ntb loan for merchant: {}", experian.getMerchantId());
						experian.setCategory("1N");
						experian.setColor(ExperianConstants.COLOR.RED.name());
						experian.setReason(ExperianConstants.ENACH);
						experianDao.save(experian);
					} else if (experian.getResponse() == null && loanEligibilityDTOs.isEmpty()) {
						logger.info("NTC merchant, so rejecting ntb loan for merchant: {}", experian.getMerchantId());
						experian.setCategory("1N");
						experian.setColor(ExperianConstants.COLOR.RED.name());
						experian.setReason(ExperianConstants.NTC);
						experianDao.save(experian);
					} else if (yellowPincode && loanEligibilityDTOs.isEmpty()) {
						logger.info("Yellow pincode, so rejecting ntb loan for merchant: {}", experian.getMerchantId());
						experian.setCategory("1N");
						experian.setColor(ExperianConstants.COLOR.RED.name());
						experian.setReason(ExperianConstants.YELLOW);
						experianDao.save(experian);
					} else if (bankCode != null && experian.getResponse() != null && !yellowPincode) {
						List<LoanEligibilityDTO> ntbLoans = newToBharatpeService.fetchBBSLoans(merchant, experian, yellowPincode, !loanEligibilityDTOs.isEmpty());
						if (!ntbLoans.isEmpty()) {
							if (loanEligibilityDTOs.isEmpty()) {
								eligibleLoanDao.deleteNonNTB(merchant.getId());
								loanEligibilityDTOs.addAll(ntbLoans);
							} else if (loanEligibilityDTOs.get(0).getAmount() < ntbLoans.get(0).getAmount()) {
								logger.info("Deleting Non NTB eligible loans for merchant: {}", merchant.getId());
								eligibleLoanDao.deleteNonNTB(merchant.getId());
								lendingMerchantDropoffDao.save(new LendingMerchantDropoff(experian.getMerchantId(), "REGULAR", "High Loan Amount For NTB", String.valueOf(loanEligibilityDTOs.get(0).getAmount())));
								loanEligibilityDTOs.clear();
								loanEligibilityDTOs.addAll(ntbLoans);
							} else {
								logger.info("Deleting NTB eligible loans for merchant: {}", merchant.getId());
								eligibleLoanDao.deleteByMerchantIdAndLoanType(merchant.getId(), "NTB");
							}
						}
					}
				}




				if(!loanEligibilityDTOs.isEmpty() && repeatLoanGlobalCheck(merchant)){
					try {
						logger.info("repeatLoanGlobalCheck success for merchant:{}", merchant.getId());
						LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findLatestLendingPaymentScheduleByMerchantId(merchant.getId());

						if (lendingPaymentSchedule.getLoanAmount() >= 10000) {

							List<LoanEligibilityDTO> removeList = new ArrayList<>();
							for (LoanEligibilityDTO loanEligibilityDTO : loanEligibilityDTOs) {
								if (loanEligibilityDTO.getAmount().doubleValue() > lendingPaymentSchedule.getLoanAmount()) {
									removeList.add(loanEligibilityDTO);
								}
							}
							if (!removeList.isEmpty()) {
								loanEligibilityDTOs.removeAll(removeList);
								LendingCategories lendingCategories = lendingCategoryDao.getByCategory(removeList.get(0).getCategory());
								loanEligibilityDTOs.add(loanEligibleService.calculateLoanBreakup(lendingCategories, 0, removeList.get(0).getType(), merchant.getId(), experian.getId(), lendingPaymentSchedule.getLoanAmount(), experian.getColor(), null, removeList.get(0).getLoanType(), false, yellowPincode));
							}
						}else {
							loanEligibilityDTOs.clear();
						}
						}catch(Exception ex){
							logger.error("Error on repeatLoanGlobalCheck merchant_id: {}  Er:{}", merchant.getId(), ex);
						}
				}
				loanEligibilityDTOs.sort(Comparator.comparing(LoanEligibilityDTO::getAmount, Comparator.reverseOrder()).thenComparing(LoanEligibilityDTO::getEdi));

				LendingBlockedPancard lendingBlockedPancard = lendingBlockedPancardDao.getByPancardOrMerchanIdOrMobileNumber(experian.getPancardNumber(), merchant.getId(), merchant.getMobile());
				Map<String, Object> pspCheck = apiGatewayService.riskByPspApp(merchant);
				if (!exemptMerchant.contains(merchant.getId()) && lendingBlockedPancard != null) {
					logger.info("Blocked pancard:{}", experian.getPancardNumber());
					loanEligibilityDTOs.clear();
					experian.setReason(ExperianConstants.BLOCKED_PANCARD);
					experian.setCategory("1N");
					experian.setColor(ExperianConstants.COLOR.RED.name());
					experianDao.save(experian);
				} else if (!exemptMerchant.contains(merchant.getId()) && (paymentsBank || (merchantBankDetail.getBankCode() != null && merchantBankDetail.getBankCode().equalsIgnoreCase("LAVB38")))) {
					logger.info("Payments bank pancard:{}", experian.getPancardNumber());
					loanEligibilityDTOs.clear();
					experian.setReason(ExperianConstants.ENACH);
					experian.setCategory("1N");
					experian.setColor(ExperianConstants.COLOR.RED.name());
					experianDao.save(experian);
				}else if(!exemptMerchant.contains(merchant.getId()) && (!loanEligibilityDTOs.isEmpty() && isRegularLoanInEligible(experian, loanEligibilityDTOs.get(0).getAmount().doubleValue()) && Objects.isNull(bankCode))) {
					logger.info("isRegularLoanInEligible experianId: {} and amount: {}", experian.getId(), loanEligibilityDTOs.get(0).getAmount().doubleValue());
					if(loanEligibilityDTOs.get(0).getAmount().doubleValue() < 50000){
						experian.setReason(ExperianConstants.ENACH);
					}else{
						experian.setReason(ExperianConstants.NON_CPV_CITY);
					}
					experian.setCategory("1N");
					experian.setColor(ExperianConstants.COLOR.RED.name());
					experianDao.save(experian);
					loanEligibilityDTOs.clear();
				}else if(!merchant.getId().equals(6603108L) && !exemptMerchant.contains(merchant.getId()) && (Boolean)pspCheck.get("status")){
					logger.info("multiple psp app in merchant phone:{}", experian.getPancardNumber());
					loanEligibilityDTOs.clear();
					experian.setRejected(true);
					experian.setRejectedDate(new Date());
					experian.setReason(pspCheck.get("reason").toString());
					experian.setCategory("1N");
					experian.setColor(ExperianConstants.COLOR.RED.name());
					experianDao.save(experian);
				}

				if (!loanEligibilityDTOs.isEmpty()) {
					experian.setEligibleAmount(loanEligibilityDTOs.get(0).getAmount().doubleValue());
					experian.setEligibleTenure(loanEligibilityDTOs.get(0).getPrincipleEdiTenure().toString());
					experian.setLoanType(loanEligibilityDTOs.get(0).getLoanType());
					experianDao.save(experian);
				}
				if (experian.getEligibleAmount() != null && loanEligibilityDTOs.isEmpty()) {
					experian.setEligibleAmount(null);
					experian.setEligibleTenure(null);
					experian.setLoanType(null);
					experianDao.save(experian);
				}
				experian = experianDao.getByMerchantId(merchant.getId());// refreshing object after update
				loanUtil.auditExperian(experian);
				new Thread(() -> apiGatewayService.updateGlobalLimit(merchant.getId())).start();
			}
			boolean ogl = false;
			if(lendingApplication != null
					&& (("rejected".equalsIgnoreCase(lendingApplication.getStatus()) && !"REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil()))
					|| "approved".equalsIgnoreCase(lendingApplication.getStatus()))) {
				loanApplicationDTO.setShowReapply(true);
				loanApplicationDTO.setApplicationId(null);
			}
			if(loanHistoryDTOs.isEmpty() && loanEligibilityDTOs.isEmpty()) {
				eligibleFlag = false;
			}
			if (experian != null && experian.getReason() != null && experian.getReason().equalsIgnoreCase(ExperianConstants.FRAUD)  && !isZomato) {
				tempClosed = "FRAUD";
				eligibleFlag = true;
				lendingClosedAuditDao.save(new LendingClosedAudit(merchant.getId(), panCard, pincode, "FRAUD"));
			} else if (eligibleFlag && lendingCity != null && !lendingCity.getNtcAllowed() && loanEligibleService.isNTC(experian)) {
				lendingClosedAuditDao.save(new LendingClosedAudit(merchant.getId(), panCard, pincode, "OGL"));
				eligibleFlag = false;
				ogl = true;

			} else if (!eligibleFlag && !rejected) {
				tempClosed = "INELIGIBLE";
				lendingClosedAuditDao.save(new LendingClosedAudit(merchant.getId(), panCard, pincode, "INELIGIBLE"));
			}
			boolean hasExperian;
			if (panCard == null && pincode == null) {
				hasExperian = false;
				MerchantDocumentProof merchantDocumentProof = merchantDocumentProofDao.findVerifiedProofType(merchant.getId(), "pancard");
				if (merchantDocumentProof != null && merchantDocumentProof.getProofNumber() != null) {
					panCard = merchantDocumentProof.getProofNumber();
				}
				pincode = fetchPincode(merchant.getId());
			} else {
				hasExperian = true;
			}

			LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
			loanDetailsDTO.setEligibility(loanEligibilityDTOs);
			loanDetailsDTO.setHistory(loanHistoryDTOs);
			loanDetailsDTO.setLoanApplication(loanApplicationDTO);
			loanDetailsDTO.setEligible(eligibleFlag);
			loanDetailsDTO.setRejected(rejected);
			loanDetailsDTO.setRejectReason(rejectReason);
			loanDetailsDTO.setPanCard(panCard);
			loanDetailsDTO.setNoExperian(noExperian);
			loanDetailsDTO.setMaskedMobiles(maskedMobiles);
			loanDetailsDTO.setTempClosed(hasExperian ? tempClosed : null);
			loanDetailsDTO.setAccountDetails(accountDetails);
			loanDetailsDTO.setSkipEnatch(skipEnatch);
			loanDetailsDTO.setZomato(isZomato);
			loanDetailsDTO.setOgl(ogl);
			loanDetailsDTO.setPincode(pincode);
			loanDetailsDTO.setZomato(isZomato);
			loanDetailsDTO.setSkipEnatch(skipEnatch);
			loanDetailsDTO.setHasExperian(hasExperian);
			loanDetailsDTO.setBharatPeClubMember(apiGatewayService.eligibleForProcessingFee(merchant.getId()));
			loanDetailsDTO.setBureauScore(experian != null ? experian.getExperianScore() : null);
			loanDetailsDTO.setMinAmount(Objects.isNull(bankCode) && !loanEligibilityDTOs.isEmpty() ? 50000D : null);
			if (pincodeCityStateMapping != null && !StringUtils.isEmpty(pincodeCityStateMapping.getCity())) {
				loanDetailsDTO.setCity(pincodeCityStateMapping.getCity());
			} else {
				loanDetailsDTO.setCity(" ");
			}
			response.setDetails(loanDetailsDTO);
			response.setSuccess(true);

		} catch(Exception ex) {
			logger.error("Exception while checking loan details for merchant id {}", merchant.getId(), ex);
			return createFailureResponse();
		}
		return response;
	}

	private Integer fetchPincode(Long merchantId) {
		logger.info("Fetching pincode for merchant:{}", merchantId);
		try {
			MerchantInfoDTO merchantInfoDTO = apiGatewayService.getMerchantAddress(merchantId);
			if (merchantInfoDTO != null && merchantInfoDTO.getData() != null && merchantInfoDTO.getData().get(0).getAddressDetail() != null) {
				for (MerchantInfoDTO.AddressDetail addressDetail : merchantInfoDTO.getData().get(0).getAddressDetail()) {
					if (addressDetail.getPinCode() != null && !addressDetail.getPinCode().trim().equalsIgnoreCase("")) {
						return Integer.parseInt(addressDetail.getPinCode());
					}
				}
			}
		} catch (Exception e) {
			logger.info("Exception while fetching pincode for merchant:{}", merchantId, e);
		}
		return null;
	}


	private LendingBharatswipeOffers getSwipeLoanOffer(Merchant merchant) {
		LendingBharatswipeOffers lendingBharatswipeOffers=lendingBharatswipeOffersDao.findByMerchantId(merchant.getId());
		if(lendingBharatswipeOffers!=null && !isOfferExpired(lendingBharatswipeOffers) && lendingBharatswipeOffers.getTpv()!=null && lendingBharatswipeOffers.getTpv()>0) {
			return lendingBharatswipeOffers;
		}
		return null;
	}

	private List<LoanEligibilityDTO> fetchSwipeOffer(Merchant merchant,Experian experian,LendingBharatswipeOffers lendingBharatswipeOffers) {
		logger.info("Fetching loan details for bharat swipe for merchant {}",merchant);
		List<LendingCategories> lendingCategoriesList=lendingCategoryDao.findByBureau("BHARAT_SWIPE");
		if(!lendingCategoriesList.isEmpty()) {
			if(lendingBharatswipeOffers!=null) {
				List<LoanEligibilityDTO> eligibilityDTOs = new ArrayList<>();
				eligibleLoanDao.deleteByMerchantId(experian.getMerchantId());
				for (LendingCategories lendingCategories : lendingCategoriesList) {
					eligibilityDTOs.add(loanEligibleService.calculateLoanBreakup(lendingCategories, 0, null, experian.getMerchantId(), experian.getId(), lendingBharatswipeOffers.getLoanAmount(), experian.getColor(), "2", "BHARAT_SWIPE", false, false));
				}
				if (!eligibilityDTOs.isEmpty()) {
					eligibilityDTOs.sort(Comparator.comparing(LoanEligibilityDTO::getAmount, Comparator.reverseOrder()).thenComparing(LoanEligibilityDTO::getEdi));
					experianDao.updateEligibleAmount(experian.getId(), eligibilityDTOs.get(0).getAmount().doubleValue(), eligibilityDTOs.get(0).getPrincipleEdiTenure().toString(), "BHARAT_SWIPE");
				}
				return eligibilityDTOs;
			}
		}
		return new ArrayList<>();
	}

	public boolean isRegularLoanInEligible(Experian experian, Double amount){
		if (experian != null && experian.getPincode() != null && Objects.nonNull(amount) ) {
			PincodeCityStateMapping pincodeCityStateMapping = pincodeCityStateMappingDao.findByPincode(experian.getPincode());
			Boolean cpvCity = (pincodeCityStateMapping != null && LendingConstants.CPV_CITIES.contains(pincodeCityStateMapping.getCity()));

			return !cpvCity || amount < 50000;
		}

		return false;
	}


	public boolean repeatLoanGlobalCheck(Merchant merchant){

		try {
			logger.info("Checking repeat loan closure less than 65% for merchant:{}", merchant.getId());
			LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findLatestLendingPaymentScheduleByMerchantId(merchant.getId());
			if(Objects.nonNull(lendingPaymentSchedule) && "CLOSED".equalsIgnoreCase(lendingPaymentSchedule.getStatus())){
				Integer ledger = lendingLedgerDao.findLedgerCountOnAmountGreaterThanEdiAmount(lendingPaymentSchedule.getId(), lendingPaymentSchedule.getEdiAmount());
				if (Objects.nonNull(ledger)) {

					int totalEdis = lendingPaymentSchedule.getEdiCount();
					int onTimePaymentPercentage = 0;
					if (totalEdis != 0) {
						onTimePaymentPercentage = (ledger * 100) / totalEdis;

						return onTimePaymentPercentage <= 65;
					}
				}
			}
		}catch (Exception ex){
			logger.error("Error Occurred while checking repeatLoanGlobalCheck for merchantId: {}, Er :{}", merchant.getId(), ex);
		}
		return false;
	}

	private Boolean isOfferExpired(LendingBharatswipeOffers offer) {
		if(offer!=null && offer.getExpiryDate()!=null) {
			return offer.getExpiryDate().compareTo(new Date())<=0;
		}
		return true;
	}

	private List<LoanEligibilityDTO> fetchZomatoOffers(Experian experian, List<LendingPartnerOffers> lendingPartnerOffers) {
		if (lendingPartnerOffers.isEmpty()) {
			return new ArrayList<>();
		}
		boolean ntc = loanEligibleService.isNTC(experian);
		List<LoanEligibilityDTO> eligibilityDTOS = new ArrayList<>();
		List<String> categorySeen = new ArrayList<>();
		eligibleLoanDao.deleteByMerchantId(experian.getMerchantId());
		for (LendingPartnerOffers lendingPartnerOffer : lendingPartnerOffers) {
			if (categorySeen.contains(lendingPartnerOffer.getCategory())) {
				continue;
			}
			LendingCategories lendingCategories = lendingCategoryDao.getByCategory(lendingPartnerOffer.getCategory());
			if (lendingCategories == null || !lendingCategories.getLoanConstruct().equalsIgnoreCase("CONSTRUCT_1")) {
				logger.error("Invalid Zomato category:{} for merchant:{}", lendingPartnerOffer.getCategory(), experian.getMerchantId());
				continue;
			}
			if (ntc && !lendingCategories.getCategory().contains("ZNTC")) {
				logger.error("Invalid Zomato NTC category:{} for merchant:{}", lendingPartnerOffer.getCategory(), experian.getMerchantId());
				continue;
			}
			eligibilityDTOS.add(loanEligibleService.calculateLoanBreakup(lendingCategories, 0, null, experian.getMerchantId(), experian.getId(), lendingPartnerOffer.getLoanAmount(), experian.getColor(), "2", "ZOMATO", true, false));
			categorySeen.add(lendingPartnerOffer.getCategory());
		}
		if (!eligibilityDTOS.isEmpty()) {
			eligibilityDTOS.sort(Comparator.comparing(LoanEligibilityDTO::getAmount).thenComparing(LoanEligibilityDTO::getPrincipleEdiTenure).reversed());
			experianDao.updateEligibleAmount(experian.getId(), eligibilityDTOS.get(0).getAmount().doubleValue(), eligibilityDTOS.get(0).getPrincipleEdiTenure().toString(), "ZOMATO");
		}
		return eligibilityDTOS;
	}

	private LoanDetailsResponseDTO createFailureResponse() {
		LoanDetailsResponseDTO response = new LoanDetailsResponseDTO();
		LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
		List<LoanHistoryDTO> loanHistoryDTOs = new ArrayList<>();
		LoanApplicationDTO loanApplicationDTO = new LoanApplicationDTO();
		List<LoanEligibilityDTO> loanEligibilityDTOs = new ArrayList<>();
		loanDetailsDTO.setEligibility(loanEligibilityDTOs);
		loanDetailsDTO.setHistory(loanHistoryDTOs);
		loanDetailsDTO.setLoanApplication(loanApplicationDTO);
		loanDetailsDTO.setEligible( false);
		return response;
	}

	private boolean rejectedInLastNDays(LendingApplication lendingApplication, int nDays) {
		try {
			if (!"rejected".equals(lendingApplication.getStatus())) {
				return false;
			}
			Date rejectedTimestamp = null;
			if ("REJECTED".equalsIgnoreCase(lendingApplication.getManualKyc()) && lendingApplication.getKycApprovedDate() != null) {
				rejectedTimestamp = lendingApplication.getKycApprovedDate();
			} else if ("REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil()) && lendingApplication.getCibilApprovedDate() != null) {
				rejectedTimestamp = lendingApplication.getCibilApprovedDate();
			} else if ("REJECTED".equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus()) && lendingApplication.getPhysicalApprovedDate() != null) {
				rejectedTimestamp = lendingApplication.getPhysicalApprovedDate();
			}
			if (rejectedTimestamp != null) {
				Date nDaysBeforeTimestamp = new Date(System.currentTimeMillis() - (long) nDays * 24 * 3600 * 1000);
				if(rejectedTimestamp.compareTo(nDaysBeforeTimestamp) > 0) {
					logger.info("Application with id {} has been rejected in last {} days", lendingApplication.getId(), nDays);
					return true;
				}
			}
		} catch(Exception ex) {
			logger.error("Exception while checking if rejected in n days for application id {}, Exception is {}", lendingApplication.getId(), ex);
		}

		return false;
	}

	private List<LoanHistoryDTO> fetchLoanHistory(LendingApplication application, List<LendingPaymentSchedule> lendingPaymentScheduleList, LendingPaymentSchedule activeLoan, boolean repeatLoan, BpEnach enachSuccess, boolean showTarget, double targetTpv) {
		List<LoanHistoryDTO> loanHistoryList = new ArrayList<>();

		if(activeLoan == null && application != null && "approved".equals(application.getStatus()) && !"disbursed".equalsIgnoreCase(application.getLoanDisbursalStatus())) {
			LoanHistoryDTO history = new LoanHistoryDTO();

			history.setId(application.getId());
			history.setAmount(application.getLoanAmount());
			history.setStartDate(null);
			history.setEndDate(null);
			history.setStatus("INTRANSFER");
			if ("PREBOOK".equalsIgnoreCase(application.getLoanType())) {
				if (showTarget) {
					history.setLoanStatusHeader("Loan Approved");
					history.setLoanStatusTitle("Increase BharatPe QR Txns to Get Loan");
					history.setLoanStatusMessage("Your Application ID is " + application.getExternalLoanId() + ".\nJust Collect <b>Rs."+(int)targetTpv+"</b> more from your customers on BharatPe QR in next 10 days to transfer Loan in your Bank A/c.");
				} else {
					history.setLoanStatusHeader("Loan Approved");
					history.setLoanStatusTitle("Loan Transfer Initiated");
					history.setLoanStatusMessage("The amount will reflect in your bank account in next 7 days.");
				}
			} else {
				history.setLoanStatusHeader("Loan Approved");
				history.setLoanStatusTitle("Loan Approved");
				if (enachSuccess != null && repeatLoan) {
					history.setLoanStatusMessage("Net Banking / Debit Card Linked Successfully!\nAmount will reflect in your A/c in next 10 days.");
				} else {
					history.setLoanStatusMessage("The amount will reflect in your Bank A/c in the next 7-10 days. Keep transacting on BharatPe QR to get money in your Account faster.");
				}
			}
			history.setRepaid(0D);
			history.setDue(application.getRepayment());

			loanHistoryList.add(history);
		}

		for(LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentScheduleList) {
			LoanHistoryDTO history = new LoanHistoryDTO();
			Boolean showPaynow = false;
			history.setId(lendingPaymentSchedule.getId());
			history.setAmount(lendingPaymentSchedule.getLoanAmount());
			history.setStartDate(lendingPaymentSchedule.getStartDate());
			history.setStatus(lendingPaymentSchedule.getStatus());
			history.setLoanStatusTitle("");
			history.setLoanStatusMessage("");
			history.setProcessingFee(lendingPaymentSchedule.getLoanApplication() != null ? lendingPaymentSchedule.getLoanApplication().getProcessingFee() : 0D);
			history.setDisbursalAmount(lendingPaymentSchedule.getLoanApplication() != null ? lendingPaymentSchedule.getLoanApplication().getDisbursalAmount() : lendingPaymentSchedule.getLoanAmount());
			if("ACTIVE".equalsIgnoreCase(lendingPaymentSchedule.getStatus())){
				LendingEDISchedule lendingEDISchedule = lendingEDIScheduleDao.getLatestByLoanId(lendingPaymentSchedule.getId());
				if(lendingEDISchedule != null){
					showPaynow=true;
				}
			}
			history.setShowPaynow(showPaynow);
			if(LendingStatus.CLOSED.toString().equals(lendingPaymentSchedule.getStatus())) {
				history.setEndDate(lendingPaymentSchedule.getUpdatedAt());
			} else {
				history.setEndDate(null);
			}
			history.setRepaid(lendingPaymentSchedule.getPaidAmount());
			Double dueAmount = lendingPaymentSchedule.getTotalPayableAmount();
			if(lendingPaymentSchedule.getPaidAmount() != null && dueAmount != null) {
				dueAmount -= lendingPaymentSchedule.getPaidAmount();
			}
			history.setDue(dueAmount != null ? dueAmount : 0D);
			if (lendingPaymentSchedule.getRemainingInterestOnlyEdiCount() != null && lendingPaymentSchedule.getRemainingInterestOnlyEdiCount() > 0) {
				history.setEdi(lendingPaymentSchedule.getInterestOnlyEdiAmount());
			} else {
				history.setEdi(lendingPaymentSchedule.getEdiAmount());
			}
			loanHistoryList.add(history);
		}

		return loanHistoryList;
	}

	private LoanApplicationDTO fetchLoanApplication(Merchant merchant, LendingApplication application) {
		LoanApplicationDTO loanApplicationDTO = new LoanApplicationDTO();
		if(application != null) {
			LendingCategories lendingCategories = lendingCategoryDao.getByCategory(application.getCategory());
			LendingGstDetail lendingGstDetail =lendingGstDao.findByApplicationId(application.getId());
			logger.info("Open application found for merchant ID {}", merchant.getId());
			ShopDetailsDTO shopDetails = LoanUtil.prepareShopDetailsDTO(application,lendingGstDetail);
			SelectedLoanDTO selectedLoan = LoanUtil.prepareSelectedLoanDTO(application, lendingCategories);
			List<DocumentDTO> documents = fetchDocuments(application, merchant);

			loanApplicationDTO.setApplicationId(String.valueOf(application.getId()));
			loanApplicationDTO.setShopDetails(shopDetails);
			loanApplicationDTO.setSelectedLoan(selectedLoan);
			loanApplicationDTO.setDocuments(documents);
			loanApplicationDTO.setApplicationStatus(application.getStatus());

		} else {
			loanApplicationDTO.setShopDetails(new ShopDetailsDTO());
			loanApplicationDTO.setSelectedLoan(new SelectedLoanDTO());
			logger.info("No open lending application found for merchant id {}", merchant.getId());
		}
		return loanApplicationDTO;
	}

	private LendingPaymentSchedule getActiveLoan(List<LendingPaymentSchedule> lendingPaymentScheduleList) {
		if(lendingPaymentScheduleList == null || lendingPaymentScheduleList.size() == 0) {
			return null;
		}
		for(LendingPaymentSchedule schedule : lendingPaymentScheduleList) {
			if(LendingStatus.ACTIVE.toString().equals(schedule.getStatus())) {
				return schedule;
			}
		}
		return null;
	}

	private List<DocumentDTO> fetchDocuments(LendingApplication lendingApplication, Merchant merchant) {
		List<DocumentDTO> documents = new ArrayList<>();
		List<DocumentsIdProof> documentsIdProofList = documentsIdProofDao.findByMerchantAndLendingApplication(merchant, lendingApplication);
		for(DocumentsIdProof documentsIdProof : documentsIdProofList) {
			DocumentDTO document = new DocumentDTO();
			document.setId(documentsIdProof.getId());
			document.setProofType(documentsIdProof.getProofType());
			document.setSinglePageDocument(documentsIdProof.getSinglePage() != null && documentsIdProof.getSinglePage() == 0 ? false : true);
		}
		return documents;
	}

	public SettlementResponseDTO getSettlements(Merchant merchant, Long loanId) {
		LendingPaymentSchedule lendingPaymentSchedule;
		dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
		if (loanId != null) {
			lendingPaymentSchedule = lendingPaymentScheduleDao.findByIdAndMerchantId(loanId, merchant.getId());
		} else {
			lendingPaymentSchedule = lendingPaymentScheduleDao.getOldestActiveLoan(merchant.getId());
		}
		if (lendingPaymentSchedule == null){
			return new SettlementResponseDTO(false, "No Active Loan");
		}
		List<LendingLedger> lendingLedgers = lendingLedgerDao.findByLendingPaymentSchedule(lendingPaymentSchedule);
		List<SettlementResponseDTO.Settlement> settlementList = new ArrayList<>();
		for (LendingLedger lendingLedger : lendingLedgers) {
			if (lendingLedger.getAmount() > 0 && (lendingLedger.getAdjustmentMode() == null || !"TOPUP".equalsIgnoreCase(lendingLedger.getAdjustmentMode()))) {
				String mode = LoanUtil.settlementMode.getOrDefault(lendingLedger.getAdjustmentMode(), "QR Txns.");
				if (lendingLedger.getSettlement() != null && lendingLedger.getSettlement().getSettlementMode() != null && "BHARATSWIPE".equalsIgnoreCase(lendingLedger.getSettlement().getSettlementMode())) {
					mode = "Swipe Txns.";
				}
				settlementList.add(new SettlementResponseDTO.Settlement(dateFormat.format(lendingLedger.getDate()), lendingLedger.getAmount(), mode));
			}
		}
		if (settlementList.isEmpty()) {
			return new SettlementResponseDTO(false, "No Settlement Found");
		}
		settlementList.sort(Comparator.comparing(SettlementResponseDTO.Settlement::getDate).reversed());
		SettlementResponseDTO settlementResponseDTO = new SettlementResponseDTO();
		settlementResponseDTO.setSettlement(settlementList);
		return settlementResponseDTO;
	}

	public boolean isMerchantFromCreditLine(Merchant merchant) {
		CreditLineMerchant creditLineMerchant = creditLineMerchantDao.findByMerchantId(merchant.getId());
		return creditLineMerchant != null;
	}


	public ResponseDTO creditScore(Merchant merchant,RequestDTO<CreditScoreRequestDto> requestDTO,String clientIp){

		ResponseDTO responseDTO = new ResponseDTO(true, null, null,null);
		CreditScoreResponseDto creditScoreResponseDto = new CreditScoreResponseDto();
		creditScoreResponseDto.setTimeout(Boolean.FALSE);
		creditScoreResponseDto.setNTC(Boolean.FALSE);
		creditScoreResponseDto.setEligible(Boolean.FALSE);
		CreditScoreRequestDto creditScoreRequestDto=requestDTO.getPayload();
		String pancard = creditScoreRequestDto.getPanNumber();
		Experian experian = experianDao.getByMerchantId(merchant.getId());
		String key="bharatpe.in/creditscore";

		if(requestDTO.getPayload().getPanNumber() == null && experian == null) {
			creditScoreResponseDto.setPanNumber("null");
			creditScoreResponseDto.setPinCode(0);
			responseDTO.setData(creditScoreRequestDto);
			return  responseDTO;
		}

		MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchant.getId());
		List<LendingPartnerOffers> lendingPartnerOffers = lendingPartnerOffersDao.findByMerchantIdAndPartnerAndMobile(merchant.getId(), "ZOMATO", merchant.getMobile());
		MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
		List<MerchantStore> stores = merchantStoreDao.findByMerchant(merchant);

		String bankCode = eNachService.fetchBankCode(merchantBankDetail.getIfscCode().substring(0, 4), "BOTH");;
		Boolean sms = Boolean.FALSE;

		//Zomato Loan Offers
		boolean isZomato = false;
		if (lendingPartnerOffers != null && !lendingPartnerOffers.isEmpty()) {
			isZomato = true;
		}
		Integer pincode = creditScoreRequestDto.getPinCode() != null ? creditScoreRequestDto.getPinCode() : experian.getPincode();
		boolean yellowPincode=false;
		LendingCities lendingCity = null;
		LendingRedCities redCity = null;
		if(pincode != null){
			lendingCity = lendingCitiesDao.findActiveCityByPincode(pincode);
			redCity = lendingRedCitiesDao.findByPincode(pincode);
		}
		if(!isZomato && lendingCity == null && redCity == null){
			yellowPincode=true;
		}

		LendingApplication lendingApplicationList = lendingApplicationDao.getLatestPendingApplication(merchant.getId());
		LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.getOldestActiveLoan(merchant.getId());
		LendingApplication latestApplication = lendingApplicationDao.findTop1ByMerchantOrderByIdDesc(merchant);
		if(requestDTO.getPayload().getPanNumber() != null){
			if (experian != null ) {
				experian.setPancardNumber(requestDTO.getPayload().getPanNumber());
				experian.setBpScore((merchantSummary != null && merchantSummary.getBpScore() != null) ? merchantSummary.getBpScore() : 0D);
				experian.setPincode(requestDTO.getPayload().getPinCode());
				experian.setSkip(false);
				experianDao.save(experian);
				if(experian.getSource() == null || !experian.getSource().equals("CREDIT_SCORE")){
					sms = Boolean.TRUE;
				}
			} else {
				experian = experianDao.save(new Experian(merchant.getId(), clientIp, merchant.getLatitude() != null && merchant.getLatitude() <= 90 ? merchant.getLatitude() : null, merchant.getLongitude() != null && merchant.getLongitude() <= 90 ? merchant.getLongitude() : null, 0, pancard, (merchantSummary != null && merchantSummary.getBpScore() != null) ? merchantSummary.getBpScore() : 0D, 0, pincode));
				experian.setSource("CREDIT_SCORE");
				experianDao.save(experian);
				sms = Boolean.TRUE;
			}
		}

		List<LoanEligibilityDTO> loanEligibilityDTOs = new ArrayList<>(loanEligibleService.getNewLoanDetails(merchant, experian, merchantSummary, merchantBankDetail, requestDTO.getPayload().isSkip(), pancard, isZomato, "NORMAL", yellowPincode, bankCode));
		if (!experian.getRejected() && experian.getReason() != null) {
			lendingMerchantDropoffDao.save(new LendingMerchantDropoff(experian.getMerchantId(), "REGULAR", experian.getReason(), null));
		}
		boolean rejected = false;
		boolean noExperian = false;
		List<String> maskedMobiles = null;

		if (experian.getRejected()) {
			if(Objects.nonNull(experian.getReason()) && (experian.getReason().equalsIgnoreCase(ExperianConstants.FOS_APP) || experian.getReason().equalsIgnoreCase(ExperianConstants.MULTIPLE_PSP_APPS))) {
				rejected = false;
			}else{
				rejected = true;
			}
			creditScoreResponseDto.setMessage(experian.getReason());
		}
		if (experian.getRetryCount() == 1) {
			creditScoreResponseDto.setMessage(experian.getReason());
			creditScoreResponseDto.setTimeout(Boolean.TRUE);
		}
		if (experian.isNoExperian()) {
			noExperian = true;
			if (experian.getMaskedMobiles() != null && !experian.getMaskedMobiles().isEmpty()) {
				maskedMobiles = experian.getMaskedMobiles();
			}
		}
		if(experian.getResponse() != null && experian.getExperianScore()!= null && experian.getExperianScore() > 300D){
			//Fetch Zomato Loan
			if (isZomato && !rejected) {
				loanEligibilityDTOs.clear();
				loanEligibilityDTOs.addAll(fetchZomatoOffers(experian, lendingPartnerOffers));
			}
		}

		if (!rejected && !isZomato) {
			experian.setReason(null);
			experianDao.save(experian);
			if (bankCode == null && loanEligibilityDTOs.isEmpty()) {
				logger.info("Non enachable bank code, so rejecting ntb loan for merchant: {}", experian.getMerchantId());
				experian.setCategory("1N");
				experian.setColor(ExperianConstants.COLOR.RED.name());
				experian.setReason(ExperianConstants.ENACH);
				experianDao.save(experian);
			} else if (experian.getResponse() == null && loanEligibilityDTOs.isEmpty()) {
				logger.info("NTC merchant, so rejecting ntb loan for merchant: {}", experian.getMerchantId());
				experian.setCategory("1N");
				experian.setColor(ExperianConstants.COLOR.RED.name());
				experian.setReason(ExperianConstants.NTC);
				experianDao.save(experian);
			} else if (yellowPincode && loanEligibilityDTOs.isEmpty()) {
				logger.info("Yellow pincode, so rejecting ntb loan for merchant: {}", experian.getMerchantId());
				experian.setCategory("1N");
				experian.setColor(ExperianConstants.COLOR.RED.name());
				experian.setReason(ExperianConstants.YELLOW);
				experianDao.save(experian);
			} else if (bankCode != null && experian.getResponse() != null && !yellowPincode) {
				List<LoanEligibilityDTO> ntbLoans = newToBharatpeService.fetchBBSLoans(merchant, experian, yellowPincode, !loanEligibilityDTOs.isEmpty());
				if (!ntbLoans.isEmpty()) {
					if (loanEligibilityDTOs.isEmpty()) {
						loanEligibilityDTOs.addAll(ntbLoans);
					} else if (loanEligibilityDTOs.get(0).getAmount() < ntbLoans.get(0).getAmount()) {
						logger.info("Deleting Non NTB eligible loans for merchant: {}", merchant.getId());
						eligibleLoanDao.deleteNonNTB(merchant.getId());
						lendingMerchantDropoffDao.save(new LendingMerchantDropoff(experian.getMerchantId(), "REGULAR", "High Loan Amount For NTB", String.valueOf(loanEligibilityDTOs.get(0).getAmount())));
						loanEligibilityDTOs.clear();
						loanEligibilityDTOs.addAll(ntbLoans);
					} else {
						logger.info("Deleting NTB eligible loans for merchant: {}", merchant.getId());
						eligibleLoanDao.deleteByMerchantIdAndLoanType(merchant.getId(), "NTB");
					}
				}
			}
		}

		BankList bankList = bankListDao.findByBankCode(merchantBankDetail.getBankCode());
		boolean paymentsBank = bankList != null && bankList.getIsPaymentBank();
		LendingBlockedPancard lendingBlockedPancard = lendingBlockedPancardDao.getByPancardOrMerchanIdOrMobileNumber(experian.getPancardNumber(), merchant.getId(), merchant.getMobile());
		if (lendingBlockedPancard != null) {
			logger.info("Blocked pancard:{}", experian.getPancardNumber());
			loanEligibilityDTOs.clear();
			experian.setReason(ExperianConstants.BLOCKED_PANCARD);
			experian.setCategory("1N");
			experian.setColor(ExperianConstants.COLOR.RED.name());
			experianDao.save(experian);
		} else if (paymentsBank) {
			logger.info("Payments bank pancard:{}", experian.getPancardNumber());
			loanEligibilityDTOs.clear();
			experian.setReason(ExperianConstants.ENACH);
			experian.setCategory("1N");
			experian.setColor(ExperianConstants.COLOR.RED.name());
			experianDao.save(experian);
		}

		if(stores != null && !stores.isEmpty()) {
			loanEligibilityDTOs.clear();
		}
		if(latestApplication != null && "rejected".equals(latestApplication.getStatus())) {
			if("REJECTED".equalsIgnoreCase(latestApplication.getManualCibil()) || rejectedInLastNDays(latestApplication, 7)) {
				loanEligibilityDTOs.clear();
			}
		}
		if (!loanEligibilityDTOs.isEmpty()) {
			experian.setEligibleAmount(loanEligibilityDTOs.get(0).getAmount().doubleValue());
			experian.setEligibleTenure(loanEligibilityDTOs.get(0).getPrincipleEdiTenure().toString());
			experian.setLoanType(loanEligibilityDTOs.get(0).getLoanType());
			experianDao.save(experian);
		}
		if (experian.getEligibleAmount() != null && loanEligibilityDTOs.isEmpty()) {
			experian.setEligibleAmount(null);
			experian.setEligibleTenure(null);
			experian.setLoanType(null);
			experianDao.save(experian);
		}

		experian = experianDao.getByMerchantId(merchant.getId());// refreshing object after update
		loanUtil.auditExperian(experian);

		LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchant.getId());
		creditScoreResponseDto.setPanNumber(experian.getPancardNumber());
		creditScoreResponseDto.setPinCode(experian.getPincode());
		creditScoreResponseDto.setPanName(lendingPancard != null ? lendingPancard.getName() : merchant.getBeneficiaryName());
		creditScoreResponseDto.setScore(experian.getExperianScore());
		creditScoreResponseDto.setCreditDate(experian.getReportDate());
		creditScoreResponseDto.setBureau(experian.getBureau() != null ? experian.getBureau() : "EXPERIAN");
		creditScoreResponseDto.setMaskedMobiles(maskedMobiles);
		creditScoreResponseDto.setNoExperian(noExperian);
		if (rejected || experian.getReason() != null) {
			if("NTC".equals(experian.getReason())){
				creditScoreResponseDto.setNTC(Boolean.TRUE);
			}
			creditScoreResponseDto.setMessage(experian.getReason());
			responseDTO.setData(creditScoreResponseDto);
			if(sms){
				String message = "Dear "+creditScoreResponseDto.getPanName()+",\n"+
						"Your credit score couldn't be generated, please click here "+ key +" and try again.\n";
				sendSms(message,merchant);
			}
			return responseDTO;
		}
		if (lendingApplicationList!= null) {
			creditScoreResponseDto.setApplicationPending(Boolean.TRUE);
			creditScoreResponseDto.setEligible(Boolean.TRUE);
			creditScoreResponseDto.setEligibility(loanEligibilityDTOs);
			responseDTO.setData(creditScoreResponseDto);
			if(sms){
				String message = "Dear "+creditScoreResponseDto.getPanName()+",\n"+
						"Your Credit Score is generated and your current Score is "+experian.getExperianScore();
				sendSms(message,merchant);
			}
			return responseDTO;
		}

		if (lendingPaymentSchedule != null){
			creditScoreResponseDto.setActiveLoan(Boolean.TRUE);
			responseDTO.setData(creditScoreResponseDto);
			if(sms){
				String message = "Dear "+creditScoreResponseDto.getPanName()+",\n"+
						"Your Credit Score is generated and your current Score is "+experian.getExperianScore();
				sendSms(message,merchant);
			}
			return responseDTO;
		}
		if(experian.getExperianScore() == null || experian.getExperianScore().equals(0D) || experian.getExperianScore() < 300D ){
			creditScoreResponseDto.setNTC(Boolean.TRUE);
			loanEligibilityDTOs.clear();
			creditScoreResponseDto.setMessage("CRIF");
			responseDTO.setData(creditScoreResponseDto);
			if(sms){
				String message = "Dear "+creditScoreResponseDto.getPanName()+",\n"+
						"Your credit score couldn't be generated, please click here "+ key +" and try again.\n";
				sendSms(message,merchant);
			}
			return responseDTO;
		}

		if(!loanEligibilityDTOs.isEmpty()){
			experian.setSource("CREDIT_SCORE");
			experianDao.save(experian);
		}
		if(sms){
			String message = "Dear "+creditScoreResponseDto.getPanName()+",\n"+
					"Your Credit Score is generated and your current Score is "+experian.getExperianScore();
			sendSms(message,merchant);
		}

		redisNotificationService.sendNotificationForSeenOffer(merchant.getId(), loanEligibilityDTOs);
		creditScoreResponseDto.setEligible(!loanEligibilityDTOs.isEmpty());
		creditScoreResponseDto.setEligibility(loanEligibilityDTOs);
		responseDTO.setData(creditScoreResponseDto);

		return  responseDTO;
	}

	private void sendSms(String messageForSms, Merchant merchant) {
		List<String> mobiles=new LinkedList<>();

			mobiles.add(merchant.getMobile());
			smsServiceHandler.sendSMS(mobiles, messageForSms, NotificationProvider.SMS.GUPSHUP);
	}
}
