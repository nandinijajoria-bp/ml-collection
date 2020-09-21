package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.MerchantCategory;
import com.bharatpe.common.enums.Status.GeneralStatus;
import com.bharatpe.common.enums.Status.LendingStatus;
import com.bharatpe.common.handlers.EmailHandler;
import com.bharatpe.lending.common.dao.CreditLineMerchantDao;
import com.bharatpe.lending.common.dao.LendingPartnerOffersDao;
import com.bharatpe.lending.common.entity.CreditLineMerchant;
import com.bharatpe.lending.common.entity.LendingPartnerOffers;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.dto.LoanDetailsResponseDTO.LoanDetailsDTO;
import com.bharatpe.lending.entity.LendingPrebookTarget;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanCalculationUtil.LoanBreakupDetail;
import com.bharatpe.lending.util.LoanUtil;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class LoanDetailsService {
	private Logger logger = LoggerFactory.getLogger(LoanDetailsService.class);
	
	List<String> validAgentCities = Arrays.asList("Bangalore", "Hyderabad", "Pune", "Delhi", "Noida", "Gurgaon", "Mumbai", "Visakhapatnam", "Vijaywada", "New Delhi");
	List<String> validDIYCities = Arrays.asList("Bengaluru", "Pune", "Delhi", "Noida", "Gurgaon", "Faridabad", "Ghaziabad", "Thane", "Mumbai","Hyderabad", "Visakhapatnam", "Vijaywada", "New Delhi");
	
	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;
	
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
	DocumentsIdProofDao documentsIdProofDao;
	
	@Autowired
	AgentDao agentDao;
	
	@Autowired
	MerchantAddressDao merchantAddressDao;
	
	@Autowired
	LendingAuditTrialDao lendingAuditTrialDao;
	
	@Autowired
	MerchantStoreDao merchantStoreDao;

	@Autowired
	ExperianDao experianDao;

	@Autowired
	LoanEligibleService loanEligibleService;

	@Autowired
	ExperianAuditTrailDao experianAuditTrailDao;

	@Value("${experian.enable:true}")
	Boolean EXPERIAN_ENABLED;

	@Autowired
	EmailHandler emailHandler;

	@Autowired
	LendingCitiesDao lendingCitiesDao;

	@Autowired
	ENachService eNachService;

	@Autowired
	LendingEnachDao lendingEnachDao;
	
	@Autowired
	TopupLoanEligibleService topupLoanEligibleService;

	@Autowired
	PincodeCityStateMappingDao pincodeCityStateMappingDao;

	@Autowired
	LendingClosedAuditDao lendingClosedAuditDao;

	@Autowired
	MerchantSummaryLendingDao merchantSummaryLendingDao;

	@Value("${enach.provider}")
	private String enachServiceToUse;

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

//	@Transactional
	public LoanDetailsResponseDTO fetchLoanDetails(Merchant merchant, RequestDTO<IneligibleRequestDTO> requestDTO, String clientIp) {
		LoanDetailsResponseDTO response = new LoanDetailsResponseDTO();
		try {
			if(isMerchantFromCreditLine(merchant)) {
				response.setDeeplink("bharatpe://dynamic?key=credit-line");
				response.setSuccess(true);
				response.setMessage("Credit line merchant");
				return response;
			}
			MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchant.getId());
			MerchantSummaryLending merchantSummaryLending = merchantSummaryLendingDao.findByMerchantId(merchant.getId());
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
			boolean skipEnatch = true;
			String enach = null;
			List<String> maskedMobiles = null;
			String rejectReason = null;
			String panCard = null;
			String tempClosed = null;
			LendingEnach enachSuccess = lendingEnachDao.findSuccessEnach(merchant.getId());
			Experian experian = experianDao.getByMerchantId(merchant.getId());
			List<MerchantStore> stores = merchantStoreDao.findByMerchant(merchant);
			Integer pincode = null;
			LendingCities lendingCity = null;
//			if (experian != null && experian.getPincode() == null) {
//				logger.error("pincode bug, deleting experian entry for merchant:{}", merchant.getId());
//				emailHandler.sendEmail(new ArrayList<String>(){{add("khushal.virmani@bharatpe.com");
//					add("mihit@bharatpe.com");add("anubhav.mathur@bharatpe.com");}}, "Pincode Bug", "merchant id: " + merchant.getId() + ", mid:" + merchant.getMid());
//				experianDao.deleteByMerchantId(merchant.getId());
//				LoanDetailsResponseDTO response1 = new LoanDetailsResponseDTO();
//				response1.setSuccess(false);
//				response1.setMessage("Pincode not found");
//				return response1;
//			}
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
				//experianDao.deleteByMerchantId(merchant.getId());
				panCard = requestDTO.getPayload().getPanCard();
				if (experian != null) {
					experian.setPancardNumber(requestDTO.getPayload().getPanCard());
					experian.setBpScore((merchantSummary != null && merchantSummary.getBpScore() != null) ? merchantSummary.getBpScore() : 0D);
					experian.setPincode(requestDTO.getPayload().getPincode());
					experianDao.save(experian);
				} else {
					experian = experianDao.save(new Experian(merchant.getId(), clientIp, merchant.getLatitude() != null && merchant.getLatitude() <= 90 ? merchant.getLatitude() : null, merchant.getLongitude() != null && merchant.getLongitude() <= 90 ? merchant.getLongitude() : null, 0, requestDTO.getPayload().getPanCard(), (merchantSummary != null && merchantSummary.getBpScore() != null) ? merchantSummary.getBpScore() : 0D, experian != null ? experian.getRetryCount() : 0, requestDTO.getPayload().getPincode()));
				}
			}
			if (experian != null && experian.getPancardNumber() != null) {
				panCard = experian.getPancardNumber();
				if (ExperianConstants.LOCKDOWN) {
					if (merchantSummaryLending != null && merchantSummaryLending.getBpScore() != null) {
						experian.setBpScore(merchantSummaryLending.getBpScore());
					}
				} else {
					if (merchantSummary != null && merchantSummary.getBpScore() != null) {
						experian.setBpScore(merchantSummary.getBpScore());
					}
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
			if (lendingCity == null && redCity == null) {
				skipEnatch = false;
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
				response.setDetails(loanDetailsDTO);
				response.setSuccess(true);
				if (experian != null) {
					experianAuditTrailDao.save(ExperianAuditTrail.createObject(experian));
				}
				return response;
			}
			if (EXPERIAN_ENABLED) {
				if (experian != null && experian.getRejected() && experian.getRejectedDate() != null && LoanUtil.getDateDiffInDays(experian.getRejectedDate(), new Date()) < 30) {
					rejected = true;
					rejectReason = experian.getReason();
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
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
			List<LoanEligibilityDTO> loanEligibilityDTOs = new ArrayList<>();
			String bankCode = null;
			try {
				if (requestDTO.getMeta() != null && requestDTO.getMeta().getAppVersion() != null && !requestDTO.getMeta().getAppVersion().equalsIgnoreCase("undefined") &&  Integer.parseInt(requestDTO.getMeta().getAppVersion()) >= 238) {
					bankCode = eNachService.fetchBankCode(merchantBankDetail.getIfscCode().substring(0, 4), "BOTH");
				} else {
					bankCode = eNachService.fetchBankCode(merchantBankDetail.getIfscCode().substring(0, 4), "NET");
				}
			} catch (Exception e) {
				logger.info("Exception while checking enach bank code:", e);
				bankCode = eNachService.fetchBankCode(merchantBankDetail.getIfscCode().substring(0, 4), "BOTH");
			}
			if(lendingApplication != null) {
				LendingEnach lendingEnach = lendingEnachDao.findByMerchantIdAndApplicationId(merchant.getId(), lendingApplication.getId());
				if ((enachSuccess != null && repeatLoan) || (enachSuccess != null && lendingApplication.getLoanAmount() < 100000) || (lendingApplication.getPhysicalVerificationStatus() != null && !lendingApplication.getPhysicalVerificationStatus().equalsIgnoreCase("null"))) {
					loanApplicationDTO.setSelfVerification(false);
				}
				if("rejected".equals(lendingApplication.getStatus())) {
					List<LendingAuditTrial> auditTrialList = lendingAuditTrialDao.findByMerchantIdAndApplicationIdAndNewStatus(lendingApplication.getMerchant().getId(), lendingApplication.getId(), "REJECTED");
					LendingAuditTrial auditTrial = null;
					if(auditTrialList != null && !auditTrialList.isEmpty()){
						auditTrial = auditTrialList.get(0);
					}
					if("REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil()) || rejectedInLastNDays(auditTrial, 7)) {
						eligibleFlag = false;
						loanHistoryDTOs = null;
						loanApplicationDTO.setStatusHeader("Loan Application Submitted");
						loanApplicationDTO.setStatusTitle("Verification Failed!");
						if("REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil())) {
							loanApplicationDTO.setStatusMessage("We regret to inform you that we are unable to process your application as it does not meet the guidelines for document assessment. Please write to us on  support@bharatpe.com to apply again.");
						} else {
							Date rejecetdAt = auditTrial.getCreatedAt();
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
					if ((enachSuccess == null || (enachSuccess.getIdentifier() != null && "LIQUILOANS".equalsIgnoreCase(enachSuccess.getIdentifier()))) && (lendingEnach == null || !lendingEnach.getSkip()) && !(lendingEnach != null && lendingEnach.getStatus() != null && lendingEnach.getStatus()) && bankCode != null && requestDTO.getMeta().getAppVersion() != null && !requestDTO.getMeta().getAppVersion().equalsIgnoreCase("undefined") && Integer.parseInt(requestDTO.getMeta().getAppVersion()) >= 237) {
						if (enachServiceToUse != null && enachServiceToUse.equalsIgnoreCase("digio")) {
							enach = "bharatpe://enachdigio";//set deep link for enach digio
						} else {
							enach = "bharatpe://enachtp";//set deep link for enach techprocess
						}
						skipEnatch = true;
					}
					if ("PREBOOK".equalsIgnoreCase(lendingApplication.getLoanType())) {
						loanApplicationDTO.setStatusHeader("Loan Approved");
						loanApplicationDTO.setStatusTitle("Loan Transfer Post Lockdown");
						loanApplicationDTO.setStatusMessage("Your Application ID is " + lendingApplication.getExternalLoanId() + ". The amount will reflect in your bank account within <b>10 days</b> after Lockdown ends.");
					} else {
						loanApplicationDTO.setStatusHeader("Loan Approved");
						loanApplicationDTO.setStatusTitle("Loan Approved");
						if (enachSuccess != null && !"LIQUILOANS".equalsIgnoreCase(enachSuccess.getIdentifier()) && repeatLoan) {
							loanApplicationDTO.setStatusMessage("Net Banking / Debit Card Linked Successfully!\nAmount will reflect in your A/c in 24 hours.");
						} else {
							loanApplicationDTO.setStatusMessage("The amount will reflect in your Bank A/c in the next 7-10 days. Keep transacting on BharatPe QR to get money in your Account faster.");
						}
					}
				} else if ("pending_verification".equals(lendingApplication.getStatus())) {
					try {
						//enach not success and not skipped and bankcode enachable and app version >= 237
						if ((enachSuccess == null || (enachSuccess.getIdentifier() != null && "LIQUILOANS".equalsIgnoreCase(enachSuccess.getIdentifier()))) && (lendingEnach == null || !lendingEnach.getSkip()) && bankCode != null && requestDTO.getMeta() != null && requestDTO.getMeta().getAppVersion() != null && !requestDTO.getMeta().getAppVersion().equalsIgnoreCase("undefined") && Integer.parseInt(requestDTO.getMeta().getAppVersion()) >= 237) {
							if (enachServiceToUse != null && enachServiceToUse.equalsIgnoreCase("digio")) {
								enach = "bharatpe://enachdigio";//set deep link for enach digio
							} else {
								enach = "bharatpe://enachtp";//set deep link for enach techprocess
							}
						}
					} catch (Exception e) {// exception due to undefined app version
						logger.info("Exception while checking enach bank", e);
						if ((enachSuccess == null || (enachSuccess.getIdentifier() != null && "LIQUILOANS".equalsIgnoreCase(enachSuccess.getIdentifier()))) && (lendingEnach == null || !lendingEnach.getSkip()) && bankCode != null) {
							if (enachServiceToUse != null && enachServiceToUse.equalsIgnoreCase("digio")) {
								enach = "bharatpe://enachdigio";//set deep link for enach digio
							} else {
								enach = "bharatpe://enachtp";//set deep link for enach techprocess
							}
						}
					}
					if(enachSuccess != null && "OGL".equalsIgnoreCase(lendingApplication.getLoanType())) {
						enach = null;
						skipEnatch = true;
					}
					if ("NTB".equalsIgnoreCase(lendingApplication.getLoanType()) && enachSuccess == null) {
						skipEnatch = false;
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
						if (enachSuccess != null && !"LIQUILOANS".equalsIgnoreCase(enachSuccess.getIdentifier())) {
							accountDetails = true;
							loanApplicationDTO.setStatusTitle("Net Banking / Debit Card Linked Successfully!");
							loanApplicationDTO.setStatusMessage("Your Application ID is " + lendingApplication.getExternalLoanId() + ". Loan will be transferred in 24-48 hours after document verification.");
						} else if (lendingEnach != null && !lendingEnach.getSkip()) {
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
			
			if(activeLoan != null) {
				logger.info("Active loan found for merchant with ID {}", merchant.getId());
				LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
				loanDetailsDTO.setEligibility(loanEligibilityDTOs);
				loanDetailsDTO.setHistory(orignalHistoryDTOs);
				loanDetailsDTO.setEligible(true);
				loanDetailsDTO.setRejected(rejected);
				loanDetailsDTO.setRejectReason(rejectReason);
				loanDetailsDTO.setPanCard(panCard);
				loanDetailsDTO.setAccountDetails(accountDetails);
				loanDetailsDTO.setZomato(isZomato);
				if(!(pincode != null && lendingCity == null) && !isZomato) {
					List<LoanEligibilityDTO> topupLoans = topupLoanEligibleService.fetchTopupLoans(merchant, experian, merchantSummary, merchantBankDetail, lendingPaymentScheduleList, bankCode);
					loanDetailsDTO.setTopupLoan(topupLoans == null || topupLoans.isEmpty() ? null : topupLoans);
					if(!topupLoans.isEmpty() && lendingApplication != null && !StringUtils.isEmpty(loanApplicationDTO.getApplicationStatus()) && ("pending_verification".equalsIgnoreCase(loanApplicationDTO.getApplicationStatus()) || "approved".equalsIgnoreCase(loanApplicationDTO.getApplicationStatus()) || "rejected".equalsIgnoreCase(loanApplicationDTO.getApplicationStatus())) && "TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())) {
						loanDetailsDTO.setLoanApplication(loanApplicationDTO);
						double prevLoanUnpaidAmount = (activeLoan.getLoanAmount() - activeLoan.getPaidPrinciple()) + activeLoan.getDueInterest();
						Integer disbursementAmount = loanDetailsDTO.getLoanApplication().getSelectedLoan().getDisbursementAmount() - (int) prevLoanUnpaidAmount;
						loanDetailsDTO.getLoanApplication().getSelectedLoan().setDisbursementAmount(disbursementAmount);
						LendingEnach lendingEnach = lendingEnachDao.findByMerchantIdAndApplicationId(merchant.getId(), lendingApplication.getId());
						if ((enachSuccess == null || (enachSuccess.getIdentifier() != null && "LIQUILOANS".equalsIgnoreCase(enachSuccess.getIdentifier()))) && (lendingEnach == null || !lendingEnach.getSkip()) && !(lendingEnach != null && lendingEnach.getStatus() != null && lendingEnach.getStatus()) && bankCode != null && requestDTO.getMeta().getAppVersion() != null && !requestDTO.getMeta().getAppVersion().equalsIgnoreCase("undefined") && Integer.parseInt(requestDTO.getMeta().getAppVersion()) >= 237) {
							if (enachServiceToUse != null && enachServiceToUse.equalsIgnoreCase("digio")) {
								enach = "bharatpe://enachdigio";//set deep link for enach digio
							} else {
								enach = "bharatpe://enachtp";//set deep link for enach techprocess
							}
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
				response.setDetails(loanDetailsDTO);
				response.setSuccess(true);
				return response;
			}
//			if (merchant.getId().equals(2411647L)) {
//				enach = "bharatpe://enachdigio";
//			}
//			if (enach == null) {
//				if (enachSuccess != null) {
//					enach = "success";
//				} else if (lendingEnach != null && lendingEnach.getSkip() != null && lendingEnach.getSkip()) {
//					enach = "skipped";
//				} else {
//					enach = "false";
//				}
//			}
			
			if(lendingApplication != null && !eligibleFlag) {
				LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
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
				response.setDetails(loanDetailsDTO);
				response.setSuccess(true);
				return response;
			}
			PincodeCityStateMapping pincodeCityStateMapping = null;
			if ((pincode != null && lendingCity == null && redCity != null) || (lendingCity != null && lendingCity.getCategoriesAllowed() != null && !lendingCity.getCategoriesAllowed().contains(merchant.getBusinessCategory()))) {
				pincodeCityStateMapping = pincodeCityStateMappingDao.findByPincode(pincode);
				lendingClosedAuditDao.save(new LendingClosedAudit(merchant.getId(), panCard, pincode, "OGL"));
				if (experian != null) {
					experian.setEligibleAmount(null);
					experian.setEligibleTenure(null);
					experian.setLoanType(null);
					experian.setReason(ExperianConstants.OGL);
					experianDao.save(experian);
					experianAuditTrailDao.save(ExperianAuditTrail.createObject(experian));
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
				loanDetailsDTO.setZomato(isZomato);
				loanDetailsDTO.setSkipEnatch(skipEnatch);
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
			
//			if((isValidFOSMerchant(merchant.getReferalCode()) || isValidDIYMerchant(merchant)) && !rejected) {
				if (EXPERIAN_ENABLED && experian != null && !rejected) {
					try {
						loanEligibilityDTOs.addAll(loanEligibleService.getNewLoanDetails(merchant, experian, merchantSummary, merchantBankDetail, requestDTO.getPayload().isSkip(), requestDTO.getPayload().getPanCard(), merchantSummaryLending, isZomato,"NORMAL", yellowPincode));
					} catch (Exception e) {
						logger.error("Exception fetching eligible loan for merchant: {}", merchant.getId());
						logger.error("Exception---", e);
						emailHandler.sendEmail(new ArrayList<String>(){{add("khushal.virmani@bharatpe.com");}}, "Eligible Loan Exception", "");
					}
					if (experian.getRejected()) {
						rejected = true;
						rejectReason = experian.getReason();
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
					//fetching OGL loans
					if (yellowPincode && !rejected && experian.getReason() == null && merchantSummary != null && merchantSummary.getBpScore() != null) {
						logger.info("Yellow pincode found for merchant:{}", merchant.getId());
						loanEligibilityDTOs.clear();
						eligibleLoanDao.deleteByMerchantId(experian.getMerchantId());
						loanEligibilityDTOs.addAll(fetchOglOffers(experian, merchantSummary, merchant, bankCode));
					}
					//fetching NTB loans
					if (!rejected && loanEligibilityDTOs.isEmpty()) {
						experian.setReason(null);
						experianDao.save(experian);
						if (bankCode == null) {
							logger.info("Non enachable bank code, so rejecting ntb loan for merchant: {}", experian.getMerchantId());
							experian.setCategory("1N");
							experian.setColor(ExperianConstants.COLOR.RED.name());
							experian.setReason(ExperianConstants.ENACH);
							experianDao.save(experian);
						} else if (experian.getResponse() == null) {
							logger.info("NTC merchant, so rejecting ntb loan for merchant: {}", experian.getMerchantId());
							experian.setCategory("1N");
							experian.setColor(ExperianConstants.COLOR.RED.name());
							experian.setReason(ExperianConstants.NTC);
							experianDao.save(experian);
						} else {
							loanEligibilityDTOs.addAll(newToBharatpeService.fetchBBSLoans(merchant, experian, yellowPincode));
						}
					}
					if (experian.getEligibleAmount() != null && loanEligibilityDTOs.isEmpty()) {
						experian.setEligibleAmount(null);
						experian.setEligibleTenure(null);
						experian.setLoanType(null);
						experianDao.save(experian);
					}
					experian = experianDao.getByMerchantId(merchant.getId());// refreshing object after update
					experianAuditTrailDao.save(ExperianAuditTrail.createObject(experian));
					//send instant notification
					redisNotificationService.sendNotificationForSeenOffer(merchant.getId(), loanEligibilityDTOs);
				}
//			}
			boolean ogl = false;
			if(lendingApplication != null 
					&& (("rejected".equalsIgnoreCase(lendingApplication.getStatus()) && !"REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil()))
					|| "approved".equalsIgnoreCase(lendingApplication.getStatus()))) {
				loanApplicationDTO.setShowReapply(true);
//				loanHistoryDTOs = null;
				loanApplicationDTO.setApplicationId(null);
			}
			if(loanHistoryDTOs.isEmpty() && loanEligibilityDTOs.isEmpty()) {
				eligibleFlag = false;	
			}
			if (eligibleFlag && isYesBank(merchant, merchantBankDetail)) {
				tempClosed = "YBL";
				lendingClosedAuditDao.save(new LendingClosedAudit(merchant.getId(), panCard, pincode, "YBL"));
			} else if (eligibleFlag && isLoanClosed(experian, merchant)  && !isZomato) {
				tempClosed = "CORONA";
				lendingClosedAuditDao.save(new LendingClosedAudit(merchant.getId(), panCard, pincode, "CORONA"));
			} else if (experian != null && experian.getReason() != null && experian.getReason().equalsIgnoreCase(ExperianConstants.FRAUD)  && !isZomato) {
				tempClosed = "FRAUD";
				eligibleFlag = true;
				lendingClosedAuditDao.save(new LendingClosedAudit(merchant.getId(), panCard, pincode, "FRAUD"));
			} else if (eligibleFlag && lendingCity != null && !lendingCity.getNtcAllowed() && loanEligibleService.isNTC(experian)) {
				lendingClosedAuditDao.save(new LendingClosedAudit(merchant.getId(), panCard, pincode, "OGL"));
				eligibleFlag = false;
				ogl = true;

			} else if (!eligibleFlag) {
				tempClosed = "INELIGIBLE";
				lendingClosedAuditDao.save(new LendingClosedAudit(merchant.getId(), panCard, pincode, "INELIGIBLE"));
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
			loanDetailsDTO.setTempClosed(tempClosed);
			loanDetailsDTO.setAccountDetails(accountDetails);
			loanDetailsDTO.setSkipEnatch(skipEnatch);
			loanDetailsDTO.setZomato(isZomato);
			loanDetailsDTO.setOgl(ogl);
			loanDetailsDTO.setPincode(pincode);
			loanDetailsDTO.setZomato(isZomato);
			loanDetailsDTO.setSkipEnatch(skipEnatch);
			if (pincodeCityStateMapping != null && !StringUtils.isEmpty(pincodeCityStateMapping.getCity())) {
				loanDetailsDTO.setCity(pincodeCityStateMapping.getCity());
			} else {
				loanDetailsDTO.setCity(" ");
			}
			response.setDetails(loanDetailsDTO);
			response.setSuccess(true);
			
		} catch(Exception ex) {
			logger.error("Exception while checking loan details for merchant id {}, Exception is {}", merchant.getId(), ex);
			return createFailureResponse();
		}
		return response;
	}

	private List<LoanEligibilityDTO> fetchOglOffers(Experian experian, MerchantSummary merchantSummary, Merchant merchant, String bankCode) {
		boolean ntc = loanEligibleService.isNTC(experian);
		if (bankCode == null) {
			logger.info("Non enachable bank code, so rejecting ogl loan for merchant: {}", experian.getMerchantId());
			experian.setCategory("1N");
			experian.setColor(ExperianConstants.COLOR.RED.name());
			experian.setReason(ExperianConstants.ENACH);
			experianDao.save(experian);
			return new ArrayList<>();
		}
		if ((ntc && merchantSummary.getBpScore() < 15) || (!ntc && merchantSummary.getBpScore() < 13)) {
			logger.info("Low bp score, so rejecting ogl loan for merchant: {}", experian.getMerchantId());
			experian.setCategory("1N");
			experian.setColor(ExperianConstants.COLOR.RED.name());
			experian.setReason(ExperianConstants.LOW_BP_SCORE);
			experianDao.save(experian);
			return new ArrayList<>();
		}
		if (merchant.getBusinessCategory() == null || "Food_and_Drink".equalsIgnoreCase(merchant.getBusinessCategory())) {
			logger.info("F&B category, so rejecting ogl loan for merchant: {}", experian.getMerchantId());
			experian.setCategory("1N");
			experian.setColor(ExperianConstants.COLOR.RED.name());
			experian.setReason(ExperianConstants.BUSINESS_CATEGORY);
			experianDao.save(experian);
			return new ArrayList<>();
		}
		PaymentTransactionNew firstTransaction = paymentTransactionNewDao.getFirstTransaction(merchant.getId());
		if (firstTransaction == null || LoanUtil.getDateDiffInDays(firstTransaction.getCreatedAt(), new Date()) < 90) {
			logger.info("Vintage less than 3 months, so rejecting ogl loan for merchant: {}", experian.getMerchantId());
			experian.setCategory("1N");
			experian.setColor(ExperianConstants.COLOR.RED.name());
			experian.setReason(ExperianConstants.VINTAGE);
			experianDao.save(experian);
			return new ArrayList<>();
		}
		List<LendingCategories> categories = lendingCategoryDao.findByBureau("OGL");
		if (categories.isEmpty()) {
			logger.info("No OGL lending category found, so rejecting ogl loan for merchant: {}", experian.getMerchantId());
			experian.setCategory("1N");
			experian.setColor(ExperianConstants.COLOR.RED.name());
			experian.setReason(ExperianConstants.OGL);
			experianDao.save(experian);
			return new ArrayList<>();
		}
		List<LoanEligibilityDTO> eligibilityDTOS = new ArrayList<>();
		for (LendingCategories category : categories) {
			if ((ntc && category.getCategory().contains("NTC")) || (!ntc && category.getCategory().contains("ETC")) && category.getLoanConstruct().equalsIgnoreCase("CONSTRUCT_1")) {
				eligibilityDTOS.add(loanEligibleService.calculateLoanBreakup(category, 0, null, experian.getMerchantId(), experian.getId(), category.getMaxTpvAmount(), experian.getColor(), "2", "OGL", false, false));
			}
		}
		if (!eligibilityDTOS.isEmpty()) {
			eligibilityDTOS.sort(Comparator.comparing(LoanEligibilityDTO::getAmount, Comparator.reverseOrder()).thenComparing(LoanEligibilityDTO::getEdi));
			experianDao.updateEligibleAmount(experian.getId(), eligibilityDTOS.get(0).getAmount().doubleValue(), eligibilityDTOS.get(0).getPrincipleEdiTenure().toString(), "OGL");
		}
		return eligibilityDTOS;
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

	private boolean isLoanClosed(Experian experian, Merchant merchant) {
//		List<String> closedCategories = Arrays.asList("7","8","19","20");
//		List<ExperianAuditTrail> experianAuditTrailList = experianAuditTrailDao.findByMerchantId(merchant.getId());
//		List<LendingClosedAudit> lendingClosedAuditList = lendingClosedAuditDao.findByMerchantIdAndType(merchant.getId(), "CORONA");
//		if (lendingClosedAuditList != null && !lendingClosedAuditList.isEmpty()) {
//			return true;
//		}
//		if (experianAuditTrailList != null && experianAuditTrailList.size() > 1) {
//			return false;
//		}
//		if (experian != null && experian.getColor() != null && experian.getColor().equalsIgnoreCase(ExperianConstants.COLOR.AMBER.name())) {
//			return true;
//		}
//		return experian != null && experian.getCategory() != null && closedCategories.contains(experian.getCategory());
		return false;
	}

	private boolean isYesBank(Merchant merchant, MerchantBankDetail merchantBankDetail) {
//		try {
//			if(merchantBankDetail == null) {
//				logger.error("No merchant bank detail found for merchant id {}", merchant.getId());
//				return false;
//			}
//			if(StringUtils.isEmpty(merchantBankDetail.getIfscCode())) {
//				logger.error("IFSC is empty for merchant bank detail id {} and merchant ID {}", merchantBankDetail.getId(), merchant.getId());
//				return false;
//			}
//			if (merchantBankDetail.getIfscCode().substring(0,4).equalsIgnoreCase("YESB")) {
//				return true;
//			}
////			List<MerchantStaticVpa> merchantStaticVpas = merchantStaticVpaDao.findAllByMerchant(merchant.getId());
////			if (merchantStaticVpas != null && !merchantStaticVpas.isEmpty()) {
////				for (MerchantStaticVpa merchantStaticVpa : merchantStaticVpas) {
////					if (merchantStaticVpa.getFullVpa().contains("icic")) {
////						return false;
////					}
////				}
////				return true;
////			}
//		} catch(Exception ex) {
//			logger.error("Exception while checking if merchant's bank is yes bank with merchant id {}, Exception is {}", merchant.getId(), ex);
//		}
		return false;
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

	private boolean rejectedInLastNDays(LendingAuditTrial auditTrial, int nDays) {
		try {
			if(auditTrial == null) {
				return false;
			}
			
			Date rejectedTimestamp = auditTrial.getCreatedAt();
			Date nDaysBeforeTimestamp = new Date(System.currentTimeMillis() - (long) nDays * 24 * 3600 * 1000);
			
			if(rejectedTimestamp.compareTo(nDaysBeforeTimestamp) > 0) {
				logger.info("Application with id {} has been rejected in last {} days", auditTrial.getApplicationId(), nDays);
				return true;
			}
			
		} catch(Exception ex) {
			logger.error("Exception while checking if rejected in n days for application id {}, Exception is {}", auditTrial.getApplicationId(), ex);
		}
		
		return false;
	}

	private Boolean isValidFOSMerchant(String referalCode) {
		boolean responseFlag = false;
		
		if(!StringUtils.isEmpty(referalCode)) {
			Agent agent = agentDao.fetchByReferalCode(referalCode);
			if(agent != null && validAgentCities.contains(agent.getCity())) {
				responseFlag = true;
			} else {
				logger.info("Not valid FOS Merchant with referral code {}, returning false.", referalCode);
			}
		}
		return responseFlag;
	}
	
	private Boolean isValidDIYMerchant(Merchant merchant) {
		boolean responseFlag = false;
		
		MerchantAddress merchantAddress = merchantAddressDao.findBymerchantIdAndType(merchant.getId(), "SELF");
		if(merchantAddress != null && validDIYCities.contains(merchantAddress.getCity())) {
			responseFlag = true;
		} else {
			logger.info("Not valid DIY Merchant with merchant id {}, returning false.", merchant.getId());
		}
		
		return responseFlag;
	}
	
//	private Boolean isInvalidAgentReferalCode(String agentReferalCode) {
//		Boolean flag = false;
//		
//		Agent agent = agentDao.fetchByReferalCode(agentReferalCode);
//		if(agent != null) {
//			flag = true;
//		}
//		
//		return flag;
//	}
	
	private List<LoanEligibilityDTO> fetchEligibleLoans(String loanType, Merchant merchant) {
		
		List<LoanEligibilityDTO> availableLoanDTOList = new ArrayList<>();
		
//		List<AvailableLoan> availableLoanList = null;
		List<AvailableLoan> availableLoanList = availableLoanDao.findByMerchantIdAndTypeAndLoanConstructOrderByAmountDesc(merchant.getId(), loanType, "CONSTRUCT_3");;
		
//		if(merchant.getId() % 2 == 0) {
//			availableLoanList = availableLoanDao.findByMerchantIdAndTypeAndLoanConstructOrderByAmountDesc(merchant.getId(), loanType, "CONSTRUCT_2");
//		} else {
//			availableLoanList = availableLoanDao.findByMerchantIdAndTypeAndLoanConstructOrderByAmountDesc(merchant.getId(), loanType, "CONSTRUCT_3");
//		}
		if(availableLoanList == null || availableLoanList.isEmpty()) {
			availableLoanList = availableLoanDao.findByMerchantIdAndTypeAndLoanConstructOrderByAmountDesc(merchant.getId(), loanType, "CONSTRUCT_1");
			
			if(availableLoanList != null && !availableLoanList.isEmpty()) {
				availableLoanList = sort(availableLoanList);
			}
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
				loanEligibilityDTO.setList(LoanCalculationUtil.prepareLabels(breakup, breakup.getIoOrFreeEdiTenure()));
				loanEligibilityDTO.setType(breakup.getType());
				loanEligibilityDTO.setPrincipleEdiTenure(breakup.getPrincipleEdiTenure());
				loanEligibilityDTO.setOptionEnable(true);

				availableLoanDTOList.add(loanEligibilityDTO);
			} else {
				logger.error("No lending category found for merchant {} and category {}", merchant.getId(), availableLoan.getCategory());
			}
		}
		return availableLoanDTOList;
	}

	private List<AvailableLoan> sort(List<AvailableLoan> availableLoanList) {
		List<AvailableLoan> sortedAvailableLoans = new ArrayList<>();
		try {
			String maxCategory = null;
			
//			1st loan in the list is with the maximum amount
			for(AvailableLoan current : availableLoanList) {
				if(maxCategory == null) {
					if(MerchantCategory.AA3.toString().equals(current.getCategory()) || MerchantCategory.AA1.toString().equals(current.getCategory())) {
						maxCategory = MerchantCategory.AA.toString();
						break;
					} else if (MerchantCategory.BB6.toString().equals(current.getCategory()) || MerchantCategory.BB3.toString().equals(current.getCategory())) {
						maxCategory = MerchantCategory.BB.toString();
						break;
					} else if (MerchantCategory.CC12.toString().equals(current.getCategory()) || MerchantCategory.CC6.toString().equals(current.getCategory()) || MerchantCategory.CC3.toString().equals(current.getCategory())) {
						maxCategory = MerchantCategory.CC.toString();
						break;
					}
				}
			}
			
			if(maxCategory == null) {
				logger.info("Max category is null, returning all loans.");
				return availableLoanList;
			}
			
			for(AvailableLoan current : availableLoanList) {
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
	
	private List<LoanHistoryDTO> fetchLoanHistory(LendingApplication application, List<LendingPaymentSchedule> lendingPaymentScheduleList, LendingPaymentSchedule activeLoan, boolean repeatLoan, LendingEnach enachSuccess, boolean showTarget, double targetTpv) {
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
					history.setLoanStatusMessage("The amount will reflect in your bank account within 48-72 hours.");
				}
			} else {
				history.setLoanStatusHeader("Loan Approved");
				history.setLoanStatusTitle("Loan Approved");
				if (enachSuccess != null && !"LIQUILOANS".equalsIgnoreCase(enachSuccess.getIdentifier()) && repeatLoan) {
					history.setLoanStatusMessage("Net Banking / Debit Card Linked Successfully!\nAmount will reflect in your A/c in 24 hours.");
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
			history.setId(lendingPaymentSchedule.getId());
			history.setAmount(lendingPaymentSchedule.getLoanAmount());
			history.setStartDate(lendingPaymentSchedule.getStartDate());
			history.setStatus(lendingPaymentSchedule.getStatus());
			history.setLoanStatusTitle("");
			history.setLoanStatusMessage("");
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
			logger.info("Open application found for merchant ID {}", merchant.getId());
	        ShopDetailsDTO shopDetails = LoanUtil.prepareShopDetailsDTO(application);
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
	
	private boolean isPaymentBank(Merchant merchant, MerchantBankDetail merchantBankDetail) {
//		try {
//			if(merchantBankDetail == null) {
//				logger.error("No merchnat bank detail found for merchant id {}", merchant.getId());
//				return true;
//			}
//
//			if(StringUtils.isEmpty(merchantBankDetail.getIfscCode())) {
//				logger.error("IFSC is empty for merchant bank detail id {} and merchant ID {}", merchantBankDetail.getId(), merchant.getId());
//				return true;
//			}
//
//			List<BankList> nonPaymentBankList = bankListDao.fetchNonPaymentBankList(merchantBankDetail.getIfscCode().substring(0,4));
//
//			if (nonPaymentBankList == null || nonPaymentBankList.size() == 0) {
//				return false;
//			} else {
//				logger.info("IFSC {} is of Payment bank, returning true", merchantBankDetail.getIfscCode());
//				return true;
//			}
//		} catch(Exception ex) {
//			logger.error("Exception while checking if merchant's bank is payment bank with merchant id {}, Exception is {}", merchant.getId(), ex);
//		}
		return false;
	}

	public SettlementResponseDTO getSettlements(Merchant merchant) {
		LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.getOldestActiveLoan(merchant.getId());
		if (lendingPaymentSchedule == null){
			return new SettlementResponseDTO(false, "No Active Loan");
		}
		List<LendingLedger> lendingLedgers = lendingLedgerDao.findByLendingPaymentSchedule(lendingPaymentSchedule);
		List<SettlementResponseDTO.Settlement> settlementList = new ArrayList<>();
		for (LendingLedger lendingLedger : lendingLedgers) {
			if (lendingLedger.getAmount() > 0 && (lendingLedger.getAdjustmentMode() == null || !"TOPUP".equalsIgnoreCase(lendingLedger.getAdjustmentMode()))) {
				settlementList.add(new SettlementResponseDTO.Settlement(lendingLedger.getDate(), lendingLedger.getAmount(), LoanUtil.settlementMode.getOrDefault(lendingLedger.getAdjustmentMode(), "QR Txns.")));
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
}
