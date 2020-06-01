package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.MerchantCategory;
import com.bharatpe.common.enums.Status.GeneralStatus;
import com.bharatpe.common.enums.Status.LendingStatus;
import com.bharatpe.common.handlers.EmailHandler;
import com.bharatpe.lending.common.dao.LendingPartnerOffersDao;
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
	MerchantStaticVpaDao merchantStaticVpaDao;

	@Autowired
	LendingClosedAuditDao lendingClosedAuditDao;

	@Autowired
	MerchantSummaryLendingDao merchantSummaryLendingDao;

	@Autowired
	LendingPrebookLoansDao lendingPrebookLoansDao;

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

//	@Transactional
	public LoanDetailsResponseDTO fetchLoanDetails(Merchant merchant, RequestDTO<IneligibleRequestDTO> requestDTO, String clientIp) {
		LoanDetailsResponseDTO response = new LoanDetailsResponseDTO();
		try {
			MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchant.getId());
			MerchantSummaryLending merchantSummaryLending = merchantSummaryLendingDao.findByMerchantId(merchant.getId());
			LendingPrebookLoans lendingPrebookLoans = lendingPrebookLoansDao.findByMerchantId(merchant.getId());
			List<LendingPartnerOffers> lendingPartnerOffers = lendingPartnerOffersDao.findByMerchantIdAndPartnerAndMobile(merchant.getId(), "ZOMATO", merchant.getMobile());
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
			if (requestDTO.getPayload().getPanCard() != null) {
				experianDao.deleteByMerchantId(merchant.getId());
				panCard = requestDTO.getPayload().getPanCard();
				if (ExperianConstants.LOCKDOWN) {
					experian = experianDao.save(new Experian(merchant.getId(), clientIp, merchant.getLatitude(), merchant.getLongitude(), 0, requestDTO.getPayload().getPanCard(), (merchantSummaryLending != null && merchantSummaryLending.getBpScore() != null) ? merchantSummaryLending.getBpScore() : 0D, experian != null ? experian.getRetryCount() : 0, requestDTO.getPayload().getPincode()));
				} else {
					experian = experianDao.save(new Experian(merchant.getId(), clientIp, merchant.getLatitude(), merchant.getLongitude(), 0, requestDTO.getPayload().getPanCard(), (merchantSummary != null && merchantSummary.getBpScore() != null) ? merchantSummary.getBpScore() : 0D, experian != null ? experian.getRetryCount() : 0, requestDTO.getPayload().getPincode()));
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
				return response;
			}
			if (EXPERIAN_ENABLED) {
				if (experian != null && experian.getRejected() && LoanUtil.getDateDiffInDays(experian.getCreatedAt(), new Date()) < 30) {
					rejected = true;
					rejectReason = experian.getReason();
				} else if (experian != null && experian.getRejected() && LoanUtil.getDateDiffInDays(experian.getCreatedAt(), new Date()) >= 30) {
					experian.setRejected(false);
					experian.setReason(null);
					experian.setCreatedAt(new Date());
					experianDao.save(experian);
				}
			} else {
				panCard = requestDTO.getPayload().getPanCard();
			}
			List<LendingPaymentSchedule> lendingPaymentScheduleList = lendingPaymentScheduleDao.findByMerchantIdOrderByIdDesc(merchant.getId());
			boolean repeatLoan = lendingPaymentScheduleList != null && lendingPaymentScheduleList.size() > 0;

			LendingPaymentSchedule activeLoan = getActiveLoan(lendingPaymentScheduleList);

			List<LendingApplication> lendingApplicationList = lendingApplicationDao.fetchLatestOpenApplication(merchant);
			
			LendingApplication lendingApplication = null;
			if(lendingApplicationList != null && !lendingApplicationList.isEmpty()) {
				lendingApplication = lendingApplicationList.get(0);
			}
			boolean showTarget = false;
			boolean lockdownOpened = false;
			double targetTpv = 0d;
			String lockdownEnd = "";
			String targetEnd = "";
			try {
				// 4 may to 13may target and 24 april to 3 may tpv (lockdown end date - 3may)
				if (lendingApplication != null && lendingApplication.getLoanType() != null && lendingApplication.getLoanType().equalsIgnoreCase("PREBOOK")) {
					LendingPrebookTarget lendingPrebookTarget = lendingPrebookTargetDao.findByMerchantIdAndApplicationId(lendingApplication.getMerchant().getId(), lendingApplication.getId());
					if (lendingPrebookTarget != null) {
						DateTime lockdownEndDate = new DateTime(lendingPrebookTarget.getLockdownEndDate()).plusDays(1);
						DateTime targetAchieveDate = new DateTime(lendingPrebookTarget.getTargetAchieveDate()).plusDays(1);
						lockdownEnd = new SimpleDateFormat("dd MMM").format(lockdownEndDate.toDate());
						targetEnd = new SimpleDateFormat("dd MMM").format(lendingPrebookTarget.getTargetAchieveDate());
						if (lockdownEndDate.isBeforeNow() && targetAchieveDate.isAfterNow()) {
							//if set 2, if 3 may to 24april tpv >= target -> loan transfer initiated(approved) or cpv(pending) else (target - tpv from 3 may to today) >= 0 target screen else loan transfer initiated(approved) or cpv(pending)
							//if set 1, target - (tpv from 3 may to today) >=0 target screen else loan transfer initiated(approved) or cpv(pending)
							lockdownOpened = true;
							if (lendingPrebookTarget.getSegment().equalsIgnoreCase("2")) {
								Calendar c = Calendar.getInstance();
								c.setTime(lendingPrebookTarget.getLockdownEndDate());
								c.add(Calendar.DAY_OF_MONTH, -9);
								Date startDate = c.getTime();
								double tpv = ((BigDecimal) paymentTransactionNewDao.getAmount(startDate, lockdownEndDate.toDate(), lendingApplication.getMerchant().getId())).doubleValue();
								if (tpv < lendingPrebookTarget.getTarget()) {
									double currentTpv = ((BigDecimal) paymentTransactionNewDao.getAmount(lendingPrebookTarget.getLockdownEndDate(), new Date(), lendingApplication.getMerchant().getId())).doubleValue();
									if (lendingPrebookTarget.getTarget() - currentTpv > 0) {
										showTarget = true;
										targetTpv = lendingPrebookTarget.getTarget() - currentTpv;
									}
								}
							} else {
								double currentTpv = ((BigDecimal) paymentTransactionNewDao.getAmount(lendingPrebookTarget.getLockdownEndDate(), new Date(), lendingApplication.getMerchant().getId())).doubleValue();
								if (lendingPrebookTarget.getTarget() - currentTpv > 0) {
									showTarget = true;
									targetTpv = lendingPrebookTarget.getTarget() - currentTpv;
								}
							}
						} else if (targetAchieveDate.isBeforeNow()) {
							//if rejected show loan offer expired, else if 3 may to 13 may tpv >= target then loan transfer initiated(approved) or cpv(pending)
							lockdownOpened = true;
						}
					}
				}
			} catch (Exception e) {
				logger.error("Exception while calculating prebook target for merchant: {}", merchant.getId());
				logger.error("Exception---", e);
			}

			List<LoanHistoryDTO> orignalHistoryDTOs = fetchLoanHistory(lendingApplication, lendingPaymentScheduleList, activeLoan, repeatLoan, enachSuccess, showTarget, lockdownOpened, targetTpv, lockdownEnd, targetEnd, isZomato);
			List<LoanHistoryDTO> loanHistoryDTOs = orignalHistoryDTOs;
			LoanApplicationDTO loanApplicationDTO = fetchLoanApplication(merchant, lendingApplication);
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
			List<LoanEligibilityDTO> loanEligibilityDTOs = new ArrayList<>();
			String bankCode = null;
			try {
				if (requestDTO.getMeta().getAppVersion() != null && Integer.parseInt(requestDTO.getMeta().getAppVersion()) >= 238) {
					bankCode = eNachService.fetchBankCode(merchantBankDetail.getIfscCode().substring(0, 4), "BOTH");
				} else {
					bankCode = eNachService.fetchBankCode(merchantBankDetail.getIfscCode().substring(0, 4), "NET");
				}
			} catch (Exception e) {
				logger.error("Exception while checking enach bank code:", e);
			}
			if(lendingApplication != null) {
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
					if (ExperianConstants.LOCKDOWN && lockdownOpened && !isZomato) {
						loanApplicationDTO.setStatusTitle("Loan Offer Expired!");
						loanApplicationDTO.setStatusMessage("We regret to inform you that we are unable to process your loan since you did not meet your QR Transaction target.");
					}
				} else if ("approved".equals(lendingApplication.getStatus()) && !"disbursed".equalsIgnoreCase(lendingApplication.getLoanDisbursalStatus())) {
					eligibleFlag = false;
					accountDetails = true;
					LendingEnach lendingEnach = lendingEnachDao.findByMerchantIdAndApplicationId(merchant.getId(), lendingApplication.getId());
					if ((enachSuccess == null || (enachSuccess.getIdentifier() != null && "LIQUILOANS".equalsIgnoreCase(enachSuccess.getIdentifier()))) && (lendingEnach == null || !lendingEnach.getSkip()) && !(lendingEnach != null && lendingEnach.getStatus() != null && lendingEnach.getStatus()) && bankCode != null && requestDTO.getMeta().getAppVersion() != null && Integer.parseInt(requestDTO.getMeta().getAppVersion()) >= 237) {
						if (enachServiceToUse != null && enachServiceToUse.equalsIgnoreCase("digio")) {
							enach = "bharatpe://enachdigio";//set deep link for enach digio
						} else {
							enach = "bharatpe://enachtp";//set deep link for enach techprocess
						}
						skipEnatch = true;
					}
					if (ExperianConstants.LOCKDOWN  && !isZomato && !"TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())) {
						loanApplicationDTO.setStatusHeader("Loan Approved");
						loanApplicationDTO.setStatusTitle("Loan Transfer Post Lockdown");
						loanApplicationDTO.setStatusMessage("Your Application ID is " + lendingApplication.getExternalLoanId() + ". The amount will reflect in your bank account within <b>10 days</b> after Lockdown ends.");
					} else {
						loanApplicationDTO.setStatusHeader("Loan Approved");
						loanApplicationDTO.setStatusTitle("Loan Transfer Initiated");
						if (enachSuccess != null && !"LIQUILOANS".equalsIgnoreCase(enachSuccess.getIdentifier()) && repeatLoan) {
							loanApplicationDTO.setStatusMessage("Net Banking / Debit Card Linked Successfully!\nAmount will reflect in your A/c in 24 hours.");
						} else {
							loanApplicationDTO.setStatusMessage("The amount will reflect in your bank account within 48 hours.");
						}
					}
				} else if ("pending_verification".equals(lendingApplication.getStatus())) {
					LendingEnach lendingEnach = lendingEnachDao.findByMerchantIdAndApplicationId(merchant.getId(), lendingApplication.getId());
					try {
						//enach not success and not skipped and bankcode enachable and app version >= 237
						if ((enachSuccess == null || (enachSuccess.getIdentifier() != null && "LIQUILOANS".equalsIgnoreCase(enachSuccess.getIdentifier()))) && (lendingEnach == null || !lendingEnach.getSkip()) && bankCode != null && requestDTO.getMeta().getAppVersion() != null && Integer.parseInt(requestDTO.getMeta().getAppVersion()) >= 237) {
							if (enachServiceToUse != null && enachServiceToUse.equalsIgnoreCase("digio")) {
								enach = "bharatpe://enachdigio";//set deep link for enach digio
							} else {
								enach = "bharatpe://enachtp";//set deep link for enach techprocess
							}
						}
					} catch (Exception e) {// exception due to undefined app version
						logger.error("Exception while checking enach bank", e);
						if ((enachSuccess == null || (enachSuccess.getIdentifier() != null && "LIQUILOANS".equalsIgnoreCase(enachSuccess.getIdentifier()))) && (lendingEnach == null || !lendingEnach.getSkip()) && bankCode != null) {
							if (enachServiceToUse != null && enachServiceToUse.equalsIgnoreCase("digio")) {
								enach = "bharatpe://enachdigio";//set deep link for enach digio
							} else {
								enach = "bharatpe://enachtp";//set deep link for enach techprocess
							}
						}
					}
					eligibleFlag = false;
					loanHistoryDTOs = null;
					if (enach != null) {
						loanApplicationDTO.setStatusHeader("Details Submitted");//enach screen
						if (lendingPrebookLoans != null || "TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())) {
							skipEnatch = false;
						}
					} else {
						loanApplicationDTO.setStatusHeader("Loan Applied Successfully");
					}
					if (ExperianConstants.LOCKDOWN  && !isZomato && !"TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())) {
						if (lockdownOpened && showTarget) {
							loanApplicationDTO.setStatusTitle("Increase BharatPe QR Txns to Get Loan");
							loanApplicationDTO.setStatusMessage("Your Application ID is " + lendingApplication.getExternalLoanId() + ".\nJust Collect <b>Rs."+(int)targetTpv+"</b> more from your customers on BharatPe QR in 10 days(between <b>"+lockdownEnd+" - "+targetEnd+"</b>) to transfer Loan in your Bank A/c");
						} else if (lockdownOpened) {
							loanApplicationDTO.setStatusTitle("Physical Verification Pending");
							loanApplicationDTO.setStatusMessage("Your Application ID is " + lendingApplication.getExternalLoanId() + ". Our executive will visit you for verification. Please keep a cheque of your bank A/c & a proof of ownership ready. Your loan will be disbursed within 24 hours after verification.");
						} else {
							if (enachSuccess != null && !"LIQUILOANS".equalsIgnoreCase(enachSuccess.getIdentifier())) {
								accountDetails = true;
								loanApplicationDTO.setStatusTitle("Loan Transfer Post Lockdown");
								loanApplicationDTO.setStatusMessage("Your Application ID is " + lendingApplication.getExternalLoanId() + ". The amount will reflect in your bank account within <b>10 days</b> after Lockdown ends.");
							} else if (lendingEnach != null && !lendingEnach.getSkip()) {
								loanApplicationDTO.setStatusTitle("Net Banking / Debit Card could not be Linked!");
								loanApplicationDTO.setStatusMessage("Your Application ID is " + lendingApplication.getExternalLoanId() + ". Our agent will visit you within <b>3 days</b> after Lockdown opens for physical verification.");
							} else {
								loanApplicationDTO.setStatusTitle("Physical Verification Pending");
								loanApplicationDTO.setStatusMessage("Your Application ID is " + lendingApplication.getExternalLoanId() + ". Our agent will visit you within <b>3 days</b> after Lockdown opens for physical verification.");
							}
						}
					} else {
						loanApplicationDTO.setStatusHeader("Loan Approved");
						if (enachSuccess != null && !"LIQUILOANS".equalsIgnoreCase(enachSuccess.getIdentifier())) {
							accountDetails = true;
							loanApplicationDTO.setStatusTitle("Net Banking / Debit Card Linked Successfully!");
							loanApplicationDTO.setStatusMessage("Your Application ID is " + lendingApplication.getExternalLoanId() + ". Loan will be transferred in 24-48 hours after document verification.");
						} else if (lendingEnach != null && !lendingEnach.getSkip()) {
							loanApplicationDTO.setStatusTitle("Net Banking / Debit Card could not be Linked!");
							loanApplicationDTO.setStatusMessage("Your Application ID is " + lendingApplication.getExternalLoanId() + ". Our executive will visit you for verification. Please keep a cheque of your bank A/c & a proof of ownership ready. Your loan will be disbursed within 24 hours after verification.");
						} else {
							loanApplicationDTO.setStatusTitle("Application submitted successfully!");
							loanApplicationDTO.setStatusMessage("Your Application ID is " + lendingApplication.getExternalLoanId() + ". Our executive will visit you for verification. Please keep a cheque of your bank A/c & a proof of ownership ready. Your loan will be disbursed within 24 hours after verification.");
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
						if ((enachSuccess == null || (enachSuccess.getIdentifier() != null && "LIQUILOANS".equalsIgnoreCase(enachSuccess.getIdentifier()))) && (lendingEnach == null || !lendingEnach.getSkip()) && !(lendingEnach != null && lendingEnach.getStatus() != null && lendingEnach.getStatus()) && bankCode != null && requestDTO.getMeta().getAppVersion() != null && Integer.parseInt(requestDTO.getMeta().getAppVersion()) >= 237) {
							if (enachServiceToUse != null && enachServiceToUse.equalsIgnoreCase("digio")) {
								enach = "bharatpe://enachdigio";//set deep link for enach digio
							} else {
								enach = "bharatpe://enachtp";//set deep link for enach techprocess
							}
							loanDetailsDTO.getTopupLoan().get(0).setEnach(enach);
							experian = experianDao.getByMerchantId(merchant.getId());
							if ("approved".equalsIgnoreCase(loanApplicationDTO.getApplicationStatus()) && (experian == null || (experian.getColor() != null && !"AMBER".equalsIgnoreCase(experian.getColor())))) {
								loanDetailsDTO.getTopupLoan().get(0).setSkipEnatch(true);
							} else {
								loanDetailsDTO.getTopupLoan().get(0).setSkipEnatch(false);
							}
						}
					} else if ("TOPUP".equalsIgnoreCase(lendingApplication.getLoanType()) && !StringUtils.isEmpty(loanApplicationDTO.getApplicationStatus()) && "draft".equalsIgnoreCase(loanApplicationDTO.getApplicationStatus())) {
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
			
			if(lendingApplication != null && !eligibleFlag) {
				LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
				loanDetailsDTO.setEligibility(loanEligibilityDTOs);
				loanDetailsDTO.setHistory(loanHistoryDTOs);
				loanDetailsDTO.setLoanApplication(loanApplicationDTO);
				loanDetailsDTO.setEligible(true);
				loanDetailsDTO.setRejected(rejected);
				loanDetailsDTO.setRejectReason(rejectReason);
				loanDetailsDTO.setPanCard(panCard);
				loanDetailsDTO.setEnach(enach);
				loanDetailsDTO.setAccountDetails(accountDetails);
				loanDetailsDTO.setSkipEnatch(skipEnatch);
				loanDetailsDTO.setZomato(isZomato);
				response.setDetails(loanDetailsDTO);
				response.setSuccess(true);
				return response;
			}
			
			if (pincode != null && lendingCity == null) {
				PincodeCityStateMapping pincodeCityStateMapping = pincodeCityStateMappingDao.findByPincode(pincode);
				lendingClosedAuditDao.save(new LendingClosedAudit(merchant.getId(), panCard, pincode, "OGL"));
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
			
//			if((isValidFOSMerchant(merchant.getReferalCode()) || isValidDIYMerchant(merchant)) && !rejected) {
				if (EXPERIAN_ENABLED && experian != null && !rejected) {
					try {
						loanEligibilityDTOs.addAll(loanEligibleService.getNewLoanDetails(merchant, experian, merchantSummary, merchantBankDetail, requestDTO.getPayload().isSkip(), requestDTO.getPayload().getPanCard(), merchantSummaryLending, isZomato));
						experianAuditTrailDao.save(ExperianAuditTrail.createObject(experian));
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
					if (isZomato && !rejected) {
						loanEligibilityDTOs.clear();
						skipEnatch = false;
						loanEligibilityDTOs.addAll(fetchZomatoOffers(experian, lendingPartnerOffers));
					}
				} else if (!EXPERIAN_ENABLED && merchantSummary != null){
					loanEligibilityDTOs.addAll(fetchEligibleLoans(merchantSummary.getLoanType(), merchant));
				}
//			}
			
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
			response.setDetails(loanDetailsDTO);
			response.setSuccess(true);
			
		} catch(Exception ex) {
			logger.error("Exception while checking loan details for merchant id {}, Exception is {}", merchant.getId(), ex);
			return createFailureResponse();
		}
		return response;
	}

	private List<LoanEligibilityDTO> fetchZomatoOffers(Experian experian, List<LendingPartnerOffers> lendingPartnerOffers) {
		if (loanEligibleService.isNTC(experian) || lendingPartnerOffers.isEmpty()) {
			return new ArrayList<>();
		}
		List<LoanEligibilityDTO> eligibilityDTOS = new ArrayList<>();
		List<String> categorySeen = new ArrayList<>();
		for (LendingPartnerOffers lendingPartnerOffer : lendingPartnerOffers) {
			if (categorySeen.contains(lendingPartnerOffer.getCategory())) {
				continue;
			}
			LendingCategories lendingCategories = lendingCategoryDao.getByCategory(lendingPartnerOffer.getCategory());
			if (lendingCategories == null) {
				logger.error("Invalid Zomato category:{} for merchant:{}", lendingPartnerOffer.getCategory(), experian.getMerchantId());
				continue;
			}
			eligibleLoanDao.deleteByMerchantId(experian.getMerchantId());
			eligibilityDTOS.add(loanEligibleService.calculateLoanBreakup(lendingCategories, 0, null, experian.getMerchantId(), experian.getId(), lendingPartnerOffer.getLoanAmount(), experian.getColor(), "2", "ZOMATO", true));
			categorySeen.add(lendingPartnerOffer.getCategory());
		}
		if (!eligibilityDTOS.isEmpty()) {
			eligibilityDTOS.sort(Comparator.comparing(LoanEligibilityDTO::getAmount).thenComparing(LoanEligibilityDTO::getPrincipleEdiTenure).reversed());
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
				LoanBreakupDetail breakup = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategoryDetail);
				
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
	
	private List<LoanHistoryDTO> fetchLoanHistory(LendingApplication application, List<LendingPaymentSchedule> lendingPaymentScheduleList, LendingPaymentSchedule activeLoan, boolean repeatLoan, LendingEnach enachSuccess, boolean showTarget, boolean lockdownOpened, double targetTpv, String lockdownEnd, String targetEnd, boolean isZomato) {
		List<LoanHistoryDTO> loanHistoryList = new ArrayList<>();

		if(activeLoan == null && application != null && "approved".equals(application.getStatus()) && !"disbursed".equalsIgnoreCase(application.getLoanDisbursalStatus())) {
			LoanHistoryDTO history = new LoanHistoryDTO();

			history.setId(application.getId());
			history.setAmount(application.getLoanAmount());
			history.setStartDate(null);
			history.setEndDate(null);
			history.setStatus("INTRANSFER");
			if (ExperianConstants.LOCKDOWN && !isZomato && !"TOPUP".equalsIgnoreCase(application.getLoanType())) {
				if (lockdownOpened && showTarget) {
					history.setLoanStatusHeader("Loan Approved");
					history.setLoanStatusTitle("Increase BharatPe QR Txns to Get Loan");
					history.setLoanStatusMessage("Your Application ID is " + application.getExternalLoanId() + ".\nJust Collect <b>Rs."+(int)targetTpv+"</b> more from your customers on BharatPe QR in 10 days(between <b>"+lockdownEnd+" - "+targetEnd+"</b>) to transfer Loan in your Bank A/c");
				} else if (lockdownOpened) {
					history.setLoanStatusHeader("Loan Approved");
					history.setLoanStatusTitle("Loan Transfer Initiated");
					if ("PREBOOK".equalsIgnoreCase(application.getLoanType())) {
						history.setLoanStatusMessage("The amount will reflect in your bank account in next 7 days.");
					} else {
						history.setLoanStatusMessage("The amount will reflect in your bank account within 24-48 hours.");
					}
				} else {
					history.setLoanStatusHeader("Loan Pre-Booked Successfully");
					history.setLoanStatusTitle("Loan Transfer Post Lockdown");
					history.setLoanStatusMessage("Your Application ID is " + application.getExternalLoanId() + ". The amount will reflect in your bank account within <b>10 days</b> after Lockdown ends.");
				}
			} else {
				history.setLoanStatusHeader("Loan Approved");
				history.setLoanStatusTitle("Loan Transfer Initiated");
				if (enachSuccess != null && !"LIQUILOANS".equalsIgnoreCase(enachSuccess.getIdentifier()) && repeatLoan) {
					history.setLoanStatusMessage("Net Banking / Debit Card Linked Successfully!\nAmount will reflect in your A/c in 24 hours.");
				} else {
					history.setLoanStatusMessage("The amount will reflect in your bank account within 48 hours.");
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
			history.setEdi(lendingPaymentSchedule.getEdiAmount());
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
}
