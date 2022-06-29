package com.bharatpe.lending.service;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.Status.LendingStatus;
import com.bharatpe.common.handlers.SmsServiceHandler;
import com.bharatpe.common.service.delayedqueue.DelayedMessagePublisher;
import com.bharatpe.common.utils.NotificationUtil;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.Handler.PartnersApiHandler;
import com.bharatpe.lending.common.bpnewmaster.dao.DocumentsIdProofDaoMaster;
import com.bharatpe.lending.common.bpnewmaster.entity.DocumentsIdProofMaster;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.dto.NotificationPayloadDto;
import com.bharatpe.lending.common.entity.LendingBharatswipeOffers;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.PincodeColor;
import com.bharatpe.lending.common.service.LendingNotificationService;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.slave.dao.BPEnachDaoSlave;
import com.bharatpe.lending.common.slave.dao.BharatPeEnachDaoSlave;
import com.bharatpe.lending.common.query.dao.LendingPartnerOffersDaoSlave;
import com.bharatpe.lending.common.slave.dao.MerchantDocumentProofDaoSlave;
import com.bharatpe.lending.common.slave.dao.PaymentTransactionNewDaoSlave;
import com.bharatpe.lending.common.slave.dao.PhonebookDaoSlave;
import com.bharatpe.lending.common.slave.dao.PincodeCityStateMappingDaoSlave;
import com.bharatpe.lending.common.slave.entity.BharatPeEnachSlave;
import com.bharatpe.lending.common.slave.entity.BpEnachSlave;
import com.bharatpe.lending.common.query.entity.LendingPartnerOffersSlave;
import com.bharatpe.lending.common.slave.entity.MerchantDocumentProofSlave;
import com.bharatpe.lending.common.slave.entity.PhonebookSlave;
import com.bharatpe.lending.common.slave.entity.PincodeCityStateMappingSlave;
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.dto.LoanDetailsResponseDTO.LoanDetailsDTO;
//import com.bharatpe.lending.entity.LendingBlockedPancard;
import com.bharatpe.lending.entity.LendingPrebookTarget;
import com.bharatpe.lending.entity.LoanAgreement;
import com.bharatpe.lending.entity.LoanPaymentOrder;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.handlers.MerchantSummaryExceptionHandler;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanUtil;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class LoanDetailsService {

	private final Logger logger = LoggerFactory.getLogger(LoanDetailsService.class);

//	@Autowired
//	MerchantSummaryDao merchantSummaryDao;

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
	DocumentsIdProofDaoMaster documentsIdProofDaoMaster;

	@Autowired
	LendingPancardDao lendingPancardDao;

	@Autowired
	LendingEDIScheduleDao lendingEDIScheduleDao;

	@Autowired
	MerchantStoreDao merchantStoreDao;

//	@Autowired
//	MerchantDao merchantDao;

	@Autowired
	ExperianDao experianDao;

	@Autowired
	LoanEligibleService loanEligibleService;

	@Autowired
	LoanUtil loanUtil;

	@Value("${experian.enable:true}")
	Boolean EXPERIAN_ENABLED;

	@Autowired
	LendingCitiesDao lendingCitiesDao;

	@Autowired
	ENachService eNachService;

	@Autowired
	PincodeCityStateMappingDaoSlave pincodeCityStateMappingDaoSlave;

	@Autowired
	LendingClosedAuditDao lendingClosedAuditDao;

	@Autowired
	LendingPrebookTargetDao lendingPrebookTargetDao;

	@Autowired
	PaymentTransactionNewDaoSlave paymentTransactionNewDaoSlave;

	@Autowired
	LendingPartnerOffersDaoSlave lendingPartnerOffersDaoSlave;

	@Autowired
	EligibleLoanDao eligibleLoanDao;

	@Autowired
	LendingLedgerDao lendingLedgerDao;

	@Autowired
	LendingRedCitiesDao lendingRedCitiesDao;

	@Autowired
	RedisNotificationService redisNotificationService;

//	@Autowired
//	CreditLineMerchantDao creditLineMerchantDao;

	@Autowired
	LendingBharatswipeOffersDao lendingBharatswipeOffersDao;

	@Autowired
	MerchantDocumentProofDaoSlave merchantDocumentProofDaoSlave;

	@Autowired
	APIGatewayService apiGatewayService;

	@Autowired
	BPEnachDaoSlave bpEnachDaoSlave;

	@Autowired
	PhonebookDaoSlave phonebookDaoSlave;

	@Autowired
	BharatPeEnachDaoSlave bharatPeEnachDaoSlave;

	@Autowired
	EnachErrorHandingService enachErrorHandingService;

	@Autowired
	LoanPaymentOrderDao loanPaymentOrderDao;

	@Autowired
	PartnersApiHandler partnersApiHandler;

	@Autowired
	LendingCache lendingCache;

	@Autowired
	DelayedMessagePublisher delayedMessagePublisher;

	@Autowired
	LendingNotificationService lendingNotificationService;

	@Autowired
	NotificationUtil notificationUtil;

	@Autowired
	MerchantSummaryHandler merchantSummaryHandler;

	@Autowired
	MerchantService merchantService;

	List<Long> exemptMerchant = Arrays.asList(2411647L, 1210933L, 4340760L, 2097359L, 7090157L, 6518986L, 1141505L, 3L, 3543643L, 9319451L, 8891247L, 2078363L);

	@Autowired
    LendingPincodesDao lendingPincodesDao;

	@Autowired
	LoanAgreementDao loanAgreementDao;

	@Autowired
	LiquiloansService liquiloansService;

	@Autowired
	SupportService supportService;
//	@Transactional


	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	public LoanDetailsResponseDTO fetchLoanDetails(BasicDetailsDto merchantBasicDetailsDto, RequestDTO<IneligibleRequestDTO> requestDTO, String clientIp, String token) {
		LoanDetailsResponseDTO response = new LoanDetailsResponseDTO();
		try {
//			if(isMerchantFromCreditLine(merchantBasicDetailsDto)) {
//				response.setDeeplink("bharatpe://dynamic?key=credit-line");
//				response.setSuccess(true);
//				response.setMessage("Credit line merchant");
//				return response;
//			}
//			MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchantBasicDetailsDto.getId());
			MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(merchantBasicDetailsDto.getId());
			if (ObjectUtils.isEmpty(merchantResponseDTO)) {
				throw new MerchantSummaryExceptionHandler(merchantBasicDetailsDto.getId().toString());
			}
			List<LendingPartnerOffersSlave> lendingPartnerOffers = lendingPartnerOffersDaoSlave.findByMerchantIdAndPartnerAndMobile(merchantBasicDetailsDto.getId(), "ZOMATO", merchantBasicDetailsDto.getMobile());
			LendingRedCities redCity = null;
			boolean isZomato = false;
			if (lendingPartnerOffers != null && !lendingPartnerOffers.isEmpty()) {
				isZomato = true;
			}
//			if (requestDTO.getPayload().isSmsPermissionGiven()) {
//				logger.info("SMS permission given for merchant:{}", merchant.getId());
//				notifyNTBSMS(merchant);
//			}
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
			PincodeCityStateMappingSlave pincodeCityStateMapping = null;
			BpEnachSlave enachSuccess = bpEnachDaoSlave.findSuccessEnach(merchantBasicDetailsDto.getId());
			final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantBasicDetailsDto.getId());
			BankDetailsDto merchantBankDetail = null;
			if (bankDetailsDtoOptional.isPresent())
				merchantBankDetail = bankDetailsDtoOptional.get();			if (enachSuccess != null && enachSuccess.getAccountNumber() != null && !enachSuccess.getAccountNumber().equals(merchantBankDetail.getAccountNumber())) {
				enachSuccess = null;
			}
			Experian experian = experianDao.getByMerchantId(merchantBasicDetailsDto.getId());
//			Merchant merchant = merchantDao.getById(merchantBasicDetailsDto.getId());
//			List<MerchantStore> stores = merchantStoreDao.findByMerchantId(merchantBasicDetailsDto.getId());
			Integer pincode = null;
			LendingCities lendingCity = null;
			if (requestDTO.getPayload().getPanCard() != null) {
				if (requestDTO.getPayload().getPincode() == null) {
					logger.info("pincode bug for merchant:{}", merchantBasicDetailsDto.getId());
//					emailHandler.sendEmail(new ArrayList<String>(){{add("khushal.virmani@bharatpe.com");
//						add("mihit@bharatpe.com");add("anubhav.mathur@bharatpe.com");}}, "Pincode Bug", "merchant id: " + merchant.getId() + ", mid:" + merchant.getMid());
					LoanDetailsResponseDTO response1 = new LoanDetailsResponseDTO();
					response1.setSuccess(false);
					response1.setMessage("Pincode not found");
					return response1;
				}
				panCard = requestDTO.getPayload().getPanCard();
				if (experian == null) {
					experian = experianDao.save(new Experian(merchantBasicDetailsDto.getId(), clientIp, merchantBasicDetailsDto.getLatitude() != null && merchantBasicDetailsDto.getLatitude() <= 90 ? merchantBasicDetailsDto.getLatitude() : null, merchantBasicDetailsDto.getLongitude() != null && merchantBasicDetailsDto.getLongitude() <= 90 ? merchantBasicDetailsDto.getLongitude() : null, 0, requestDTO.getPayload().getPanCard(), (merchantResponseDTO != null && merchantResponseDTO.getBpScore() != null) ? merchantResponseDTO.getBpScore() : 0D, experian != null ? experian.getRetryCount() : 0, requestDTO.getPayload().getPincode()));
				} else if (experian != null && !experian.getPancardNumber().equalsIgnoreCase(panCard)) {
					logger.info("Found different pancard for merchant:{}, old pancard:{}, new pancard:{}", merchantBasicDetailsDto.getId(), experian.getPancardNumber(), panCard);
					experian.setPancardNumber(requestDTO.getPayload().getPanCard());
					experian.setBpScore((merchantResponseDTO != null && merchantResponseDTO.getBpScore() != null) ? merchantResponseDTO.getBpScore() : 0D);
					experian.setPincode(requestDTO.getPayload().getPincode());
					experian.setResponse(null);
					experian.setBureau(null);
					experian.setHitId(null);
					experian.setReportDate(null);
					experian.setExperianScore(null);
					experianDao.save(experian);
				} else if (requestDTO.getPayload().getPincode() != null) {
					logger.info("updating experian pincode:{} for merchant:{}", requestDTO.getPayload().getPincode(), merchantBasicDetailsDto.getId());
					experian.setPincode(requestDTO.getPayload().getPincode());
					experianDao.save(experian);
				}
			}
			if (experian != null && experian.getPancardNumber() != null) {
				panCard = experian.getPancardNumber();
				if (merchantResponseDTO != null && merchantResponseDTO.getBpScore() != null) {
					experian.setBpScore(merchantResponseDTO.getBpScore());
				}
			}
			if (requestDTO.getPayload().getPincode() != null) {
				pincode = requestDTO.getPayload().getPincode();
			} else if (experian != null && experian.getPincode() != null) {
				pincode = experian.getPincode();
			}

			if (pincode != null) {
				lendingCity = lendingCitiesDao.findActiveCityByPincode(pincode);
				pincodeCityStateMapping = pincodeCityStateMappingDaoSlave.findByPincode(pincode);
			}

			if("ORGANIZED".equalsIgnoreCase(merchantBasicDetailsDto.getCorrectMerchantType())) {
				LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
				loanDetailsDTO.setEligibility(new ArrayList<>());
				loanDetailsDTO.setHistory(new ArrayList<>());
				loanDetailsDTO.setEligible(false);
				loanDetailsDTO.setRejected(false);
				loanDetailsDTO.setRejectReason(null);
				loanDetailsDTO.setPanCard(null);
				loanDetailsDTO.setZomato(isZomato);
				loanDetailsDTO.setBharatPeClubMember(apiGatewayService.eligibleForProcessingFee(merchantBasicDetailsDto.getId()));
				response.setDetails(loanDetailsDTO);
				response.setSuccess(true);
				if (experian != null) {
					loanUtil.auditExperian(experian);
				}
				return response;
			}
			List<LendingPaymentSchedule> lendingPaymentScheduleList = lendingPaymentScheduleDao.findByMerchantIdAndCreditLoanOrderByIdDesc(merchantBasicDetailsDto.getId(),false);
			boolean repeatLoan = lendingPaymentScheduleList != null && lendingPaymentScheduleList.size() > 0;
			LendingPaymentSchedule activeLoan = getActiveLoan(lendingPaymentScheduleList);
			boolean isActiveLoan = activeLoan != null;

			boolean d2RMerchant = partnersApiHandler.isD2RMerchant(merchantBasicDetailsDto.getId());
			if (d2RMerchant && !isActiveLoan) {
				logger.info("D2R merchant:{}, rejecting", merchantBasicDetailsDto.getId());
				LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
				loanDetailsDTO.setEligibility(new ArrayList<>());
				loanDetailsDTO.setHistory(new ArrayList<>());
				loanDetailsDTO.setEligible(false);
				loanDetailsDTO.setRejected(false);
				loanDetailsDTO.setRejectReason("D2R");
				loanDetailsDTO.setPanCard(null);
				loanDetailsDTO.setZomato(isZomato);
				loanDetailsDTO.setBharatPeClubMember(false);
				response.setDetails(loanDetailsDTO);
				response.setSuccess(true);
				if (experian != null) {
					experian.setReason(ExperianConstants.D2R);
					experian.setEligibleAmount(null);
					experian.setLoanType(null);
					experian.setEligibleTenure(null);
					experianDao.save(experian);
					loanUtil.auditExperian(experian);
				}
				return response;
			}
			LendingApplication lendingApplicationCheck = lendingApplicationDao.findTopByMerchantIdAndLoanDisbursalStatusNullOrderByIdDesc(merchantBasicDetailsDto.getId());
			if (Objects.isNull(lendingApplicationCheck) && EXPERIAN_ENABLED) {
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
			} else if(Objects.isNull(panCard)){
				panCard = requestDTO.getPayload().getPanCard();
			}
			List<LendingApplication> lendingApplicationList = lendingApplicationDao.fetchLatestOpenApplication(merchantBasicDetailsDto.getId());

			LendingApplication lendingApplication = null;
			if(lendingApplicationList != null && !lendingApplicationList.isEmpty()) {
				lendingApplication = lendingApplicationList.get(0);
			}
			boolean showTarget = false;
			double targetTpv = 0d;
			try {
				// 4 may to 13may target and 24 april to 3 may tpv (lockdown end date - 3may)
				if (lendingApplication != null && lendingApplication.getLoanType() != null && lendingApplication.getLoanType().equalsIgnoreCase("PREBOOK") && "approved".equals(lendingApplication.getStatus()) && !"disbursed".equalsIgnoreCase(lendingApplication.getLoanDisbursalStatus())) {
					LendingPrebookTarget lendingPrebookTarget = lendingPrebookTargetDao.findByMerchantIdAndApplicationId(merchantBasicDetailsDto.getId(), lendingApplication.getId());
					if (lendingPrebookTarget != null && !lendingPrebookTarget.getTargetAchieved()) {
						// check last 10 days transaction tpv from lockdown end date/approved date
						DateTime lockdownEndDate = new DateTime(lendingPrebookTarget.getLockdownEndDate()).plusDays(1);
						Calendar c = Calendar.getInstance();
						c.setTime(lendingPrebookTarget.getLockdownEndDate());
						c.add(Calendar.DAY_OF_MONTH, -9);
						Date startDate = c.getTime();
						double tpv = ((BigDecimal) paymentTransactionNewDaoSlave.getAmount(startDate, lockdownEndDate.toDate(), merchantBasicDetailsDto.getId())).doubleValue();
						if (tpv < lendingPrebookTarget.getTarget()) {
							// check last 10 days transaction tpv from today
							c.setTime(new Date());
							c.add(Calendar.DAY_OF_MONTH, -9);
							double currentTpv = ((BigDecimal) paymentTransactionNewDaoSlave.getAmount(c.getTime(), new Date(), merchantBasicDetailsDto.getId())).doubleValue();
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
				logger.error("Exception while calculating prebook target for merchant: {}", merchantBasicDetailsDto.getId());
				logger.error("Exception---", e);
			}
			List<LoanEligibilityDTO> loanEligibilityDTOs = new ArrayList<>();
			List<LoanHistoryDTO> orignalHistoryDTOs = fetchLoanHistory(lendingApplication, lendingPaymentScheduleList, activeLoan, repeatLoan, enachSuccess, showTarget, targetTpv);
			List<LoanHistoryDTO> loanHistoryDTOs = orignalHistoryDTOs;
			LoanApplicationDTO loanApplicationDTO = fetchLoanApplication(merchantBasicDetailsDto, lendingApplication);
			String bankCode = eNachService.fetchBankCode(merchantBankDetail.getIfsc().substring(0, 4), "BOTH");
			if(lendingApplication != null) {
				BharatPeEnachSlave enachSkipped = bharatPeEnachDaoSlave.isSkipped(merchantBasicDetailsDto.getId(), lendingApplication.getId());
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
						enach = apiGatewayService.getEnachProvider(token, merchantBasicDetailsDto.getId());
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

					BharatPeEnachSlave bharatPeEnach = bharatPeEnachDaoSlave.findByMerchantIdAndApplicationId(merchantBasicDetailsDto.getId(), lendingApplication.getId());

					if(Objects.nonNull(bharatPeEnach) && !bharatPeEnach.getSuccess()){

						EnachErrorMessageDTO enachMessage = enachErrorHandingService.enachErrorResponse(bharatPeEnach
						, merchantBasicDetailsDto.getId(), lendingApplication, experian);
						loanApplicationDTO.setEnachErrorResponse(enachMessage);
//						skipEnatch = enachMessage.getSkipEnach();
						skipEnatch = false;
					}

					//enach not success and not skipped and bankcode enachable
					if (enachSuccess == null && enachSkipped == null && bankCode != null) {
						enach = apiGatewayService.getEnachProvider(token, merchantBasicDetailsDto.getId());
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
				logger.info("Active loan found for merchant with ID {}", merchantBasicDetailsDto.getId());
				boolean syncContacts = false;
				Optional<PhonebookSlave> phonebook = phonebookDaoSlave.findTop1ByMerchantIdOrderByIdDesc(merchantBasicDetailsDto.getId());
				if (!phonebook.isPresent() || phonebook.get().getContactsCount() == null) {
					logger.info("Contacts not synced for merchant:{}", merchantBasicDetailsDto.getId());
					syncContacts = true;
				}
				LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
				loanDetailsDTO.setSyncContacts(syncContacts);
				loanDetailsDTO.setHistory(orignalHistoryDTOs);
				loanDetailsDTO.setEligible(true);
				loanDetailsDTO.setRejected(rejected);
				loanDetailsDTO.setRejectReason(rejectReason);
				loanDetailsDTO.setPanCard(panCard);
				loanDetailsDTO.setAccountDetails(accountDetails);
				loanDetailsDTO.setZomato(isZomato);
				loanDetailsDTO.setActiveLoan(isActiveLoan);
				loanDetailsDTO.setHasExperian(experian != null);
                loanDetailsDTO.setTopupLoan(null);
				loanDetailsDTO.setSkipEnatch(skipEnatch);
				loanDetailsDTO.setEnach(enach);
				loanDetailsDTO.setBharatPeClubMember(apiGatewayService.eligibleForProcessingFee(merchantBasicDetailsDto.getId()));
				loanDetailsDTO.setBureauScore(experian != null ? experian.getExperianScore() : null);
				loanDetailsDTO.setEligibility(loanEligibilityDTOs);
				response.setDetails(loanDetailsDTO);
				response.setSuccess(true);
				return response;
			}
			if (requestDTO.getPayload().isIOS() != null && requestDTO.getPayload().isIOS()) {
				logger.info("request from IOS for merchant:{}", merchantBasicDetailsDto.getId());
				enach = "bharatpe://enachtp";
			}

			//Covid check
			boolean covidCities = experian != null && loanUtil.isCovidCities(experian.getPincode());
//			boolean retry = shouldRetry(lendingApplication);
			if(lendingApplication != null && !eligibleFlag) {
				boolean syncContacts = false;
				Optional<PhonebookSlave> phonebook = phonebookDaoSlave.findTop1ByMerchantIdOrderByIdDesc(merchantBasicDetailsDto.getId());
				if (!phonebook.isPresent() || phonebook.get().getContactsCount() == null) {
					logger.info("Contacts not synced for merchant:{}", merchantBasicDetailsDto.getId());
					syncContacts = true;
				}
				LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
				loanDetailsDTO.setSyncContacts(syncContacts);
				loanDetailsDTO.setHistory(loanHistoryDTOs);
				loanDetailsDTO.setLoanApplication(loanApplicationDTO);
				loanDetailsDTO.setEligible(true);
				loanDetailsDTO.setRejected(rejected);
				loanDetailsDTO.setRejectReason(rejectReason);
				loanDetailsDTO.setPanCard(panCard);
				loanDetailsDTO.setPincode(pincode);
				loanDetailsDTO.setEnach(enach);
				loanDetailsDTO.setCovid(covidCities);
				loanDetailsDTO.setAccountDetails(accountDetails);
				loanDetailsDTO.setSkipEnatch(skipEnatch);
				loanDetailsDTO.setZomato(isZomato);
				loanDetailsDTO.setHasExperian(experian != null);
				loanDetailsDTO.setBharatPeClubMember(apiGatewayService.eligibleForProcessingFee(merchantBasicDetailsDto.getId()));
				loanDetailsDTO.setBureauScore(experian != null ? experian.getExperianScore() : null);
				loanDetailsDTO.setEligibility(loanEligibilityDTOs);
				response.setDetails(loanDetailsDTO);
				response.setSuccess(true);
				return response;
			}
			if (covidCities) {
				logger.info("Covid city for merchant:{}", merchantBasicDetailsDto.getId());
				experian.setEligibleAmount(null);
				experian.setEligibleTenure(null);
				experian.setLoanType(null);
				experian.setReason(ExperianConstants.COVID);
				experianDao.save(experian);
				loanUtil.auditExperian(experian);
				LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
				loanDetailsDTO.setEligibility(new ArrayList<>());
				loanDetailsDTO.setHistory(new ArrayList<>());
				loanDetailsDTO.setEligible(false);
				loanDetailsDTO.setRejected(false);
				loanDetailsDTO.setCovid(covidCities);
				loanDetailsDTO.setRejectReason(null);
				loanDetailsDTO.setPanCard(panCard);
				loanDetailsDTO.setOgl(false);
				loanDetailsDTO.setPincode(pincode);
				loanDetailsDTO.setHasExperian(experian != null);
				loanDetailsDTO.setZomato(isZomato);
				loanDetailsDTO.setSkipEnatch(skipEnatch);
				loanDetailsDTO.setBharatPeClubMember(apiGatewayService.eligibleForProcessingFee(merchantBasicDetailsDto.getId()));
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
			//OGL check
            LendingPincodes lendingPincodes = lendingPincodesDao.findByPincode(pincode);
			if (lendingPincodes == null || lendingPincodes.getColor().equals(PincodeColor.RED)) {
				lendingClosedAuditDao.save(new LendingClosedAudit(merchantBasicDetailsDto.getId(), panCard, pincode, "OGL"));
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
				loanDetailsDTO.setBharatPeClubMember(apiGatewayService.eligibleForProcessingFee(merchantBasicDetailsDto.getId()));
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

			LoanEligibilityDTO loanEligibilityDTO = getEligibilty(merchantBasicDetailsDto.getId(), experian);
			if (loanEligibilityDTO != null) {
				loanEligibilityDTOs.add(loanEligibilityDTO);
			}
//			experian = experianDao.getByMerchantId(merchant.getId());// refreshing object after update
			if (Objects.isNull(lendingApplicationCheck)&& experian != null && experian.getRejected()) {
				if(Objects.nonNull(experian.getReason()) && (experian.getReason().equalsIgnoreCase(ExperianConstants.FOS_APP) || experian.getReason().equalsIgnoreCase(ExperianConstants.MULTIPLE_PSP_APPS))) {
					rejected = false;
				}else{
					rejected = true;
					rejectReason = experian.getReason();
				}
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
			if (experian != null && experian.getReason() != null && experian.getReason().equalsIgnoreCase(ExperianConstants.FRAUD) && !isZomato) {
				tempClosed = "FRAUD";
				eligibleFlag = true;
				lendingClosedAuditDao.save(new LendingClosedAudit(merchantBasicDetailsDto.getId(), panCard, pincode, "FRAUD"));
			} else if (eligibleFlag && lendingCity != null && !lendingCity.getNtcAllowed() && loanEligibleService.isNTC(experian)) {
				lendingClosedAuditDao.save(new LendingClosedAudit(merchantBasicDetailsDto.getId(), panCard, pincode, "OGL"));
				eligibleFlag = false;
				ogl = true;
			} else if (!eligibleFlag && !rejected) {
				tempClosed = "INELIGIBLE";
				lendingClosedAuditDao.save(new LendingClosedAudit(merchantBasicDetailsDto.getId(), panCard, pincode, "INELIGIBLE"));
			}
//			if ("INELIGIBLE".equals(tempClosed)) {
//				String redisKey = "SMS_SYNC_" + merchant.getId();
//				Object smsSync = lendingCache.get(redisKey);
//				if (smsSync != null) {
//					logger.info("Syncing SMS for merchant:{}", merchant.getId());
//					tempClosed = "SMS";
//				}
//			}
			boolean hasExperian;
			if (panCard == null && pincode == null) {
				hasExperian = false;
				MerchantDocumentProofSlave merchantDocumentProof = merchantDocumentProofDaoSlave.findVerifiedProofType(merchantBasicDetailsDto.getId(), "pancard");
				if (merchantDocumentProof != null && merchantDocumentProof.getProofNumber() != null) {
					panCard = merchantDocumentProof.getProofNumber();
				}
				pincode = fetchPincode(merchantBasicDetailsDto.getId());
			} else {
				hasExperian = true;
			}

			LoanDetailsDTO loanDetailsDTO = new LoanDetailsDTO();
			loanDetailsDTO.setHistory(loanHistoryDTOs);
			loanDetailsDTO.setLoanApplication(loanApplicationDTO);
			loanDetailsDTO.setEligible(eligibleFlag);
			loanDetailsDTO.setRejected(rejected);
			loanDetailsDTO.setRejectReason(rejectReason);
			loanDetailsDTO.setPanCard(panCard);
			loanDetailsDTO.setNoExperian(noExperian);
			loanDetailsDTO.setCovid(covidCities);
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
			loanDetailsDTO.setBharatPeClubMember(apiGatewayService.eligibleForProcessingFee(merchantBasicDetailsDto.getId()));
			loanDetailsDTO.setBureauScore(experian != null ? experian.getExperianScore() : null);
			loanDetailsDTO.setEligibility(loanEligibilityDTOs);
			if(Objects.isNull(bankCode) && !loanEligibilityDTOs.isEmpty()){
				loanDetailsDTO.setMinAmount(50000D);
			}
			if (pincodeCityStateMapping != null && !StringUtils.isEmpty(pincodeCityStateMapping.getCity())) {
				loanDetailsDTO.setCity(pincodeCityStateMapping.getCity());
			} else {
				loanDetailsDTO.setCity(" ");
			}
			response.setDetails(loanDetailsDTO);
			response.setSuccess(true);

		} catch(Exception ex) {
			logger.error("Exception while checking loan details for merchant id {}", merchantBasicDetailsDto.getId(), ex);
			return createFailureResponse();
		}
		return response;
	}

	private boolean shouldRetry(LendingApplication lendingApplication) {
		try {
			if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getStatus())) {
				if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getManualCibil())) {
					return false;
				} else {
					Date rejectedTimestamp = LoanUtil.loanRejectionDate(lendingApplication);
					return rejectedTimestamp != null && LoanUtil.getDateDiffInDays(rejectedTimestamp, new Date()) > 7;
				}
			}
		} catch (Exception e) {
			logger.error("Exception in shouldRetry for application:{}", lendingApplication.getId(), e);
		}
		return false;
	}

	private LoanEligibilityDTO createEligibilty(Long merchantId) {
		try {
			//EligibleLoan eligibleLoan = eligibleLoanDao.findMaxLoan(merchantId);
			EligibleLoan eligibleLoan = eligibleLoanDao.findTopByMerchantId(merchantId, Sort.by(Sort.Direction.DESC,"amount"));
			if (eligibleLoan != null) {
				//LendingCategories lendingCategories = lendingCategoryDao.getByCategory(eligibleLoan.getCategory());
				AvailableLoan availableLoan = new AvailableLoan();
				availableLoan.setAmount(eligibleLoan.getAmount());
				logger.info("Calculating loan breakup for merchant:{}, loanType:{}", merchantId, eligibleLoan.getLoanType());
				//breakup = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategories, eligibleLoan.getLoanType());
				LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();
				loanEligibilityDTO.setAmount(eligibleLoan.getAmount().intValue());
				loanEligibilityDTO.setEdi(eligibleLoan.getEdi());
				loanEligibilityDTO.setInterestRate(eligibleLoan.getRateOfInterest());
				int processingFee;
				if (apiGatewayService.eligibleForProcessingFee(merchantId)) {
					processingFee = 0;
				} else {
					processingFee = (int) Math.ceil(eligibleLoan.getAmount() * eligibleLoan.getProcessingFee());
				}
				LoanCalculationUtil.LoanBreakupDetail breakup = new LoanCalculationUtil.LoanBreakupDetail();
				breakup.setEdi(eligibleLoan.getEdi());
				breakup.setRepayment(eligibleLoan.getRepayment());
				loanEligibilityDTO.setProcessingFee(processingFee);
				loanEligibilityDTO.setDisbursementAmount((int) (eligibleLoan.getAmount() - processingFee));
				loanEligibilityDTO.setTenure(eligibleLoan.getTenure());
				loanEligibilityDTO.setInterestAmount((int) (eligibleLoan.getRepayment() - eligibleLoan.getAmount()));
				loanEligibilityDTO.setRepayment(eligibleLoan.getRepayment());
				loanEligibilityDTO.setCategory(eligibleLoan.getCategory());
				loanEligibilityDTO.setEdiCount(eligibleLoan.getEdiCount());
				loanEligibilityDTO.setList(LoanCalculationUtil.prepareLabels(breakup, 0));
				loanEligibilityDTO.setConstruct(eligibleLoan.getLoanConstruct());
				return loanEligibilityDTO;
			} else {
				logger.error("Eligible loan null for merchant:{}", merchantId);
			}
		} catch (Exception e) {
			logger.error("Exception in create eligibility for merchant:{}", merchantId, e);
		}
		return null;
	}

	private LoanEligibilityDTO getEligibilty(Long merchantId, Experian experian) throws Exception {
		logger.info("Getting eligibility for merchant:{}", merchantId);
		Double eligibleAmount = 0D;
		GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(merchantId);
		if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
			logger.info("Global limit for merchant:{} is {}", merchantId, globalLimitResponse.getData().getGlobalLimit());
			eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
            experian = globalLimitResponse.getData().getExperian();
		}
		if (eligibleAmount > 0D) {
			return createEligibilty(merchantId);
		}
		logger.info("Eligibility not found for merchant:{}", merchantId);
		return null;
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


//	private LendingBharatswipeOffers getSwipeLoanOffer(Merchant merchant) {
//		LendingBharatswipeOffers lendingBharatswipeOffers=lendingBharatswipeOffersDao.findByMerchantId(merchant.getId());
//		if(lendingBharatswipeOffers!=null && !isOfferExpired(lendingBharatswipeOffers) && lendingBharatswipeOffers.getTpv()!=null && lendingBharatswipeOffers.getTpv()>0) {
//			return lendingBharatswipeOffers;
//		}
//		return null;
//	}

//	private List<LoanEligibilityDTO> fetchSwipeOffer(Merchant merchant,Experian experian,LendingBharatswipeOffers lendingBharatswipeOffers) {
//		logger.info("Fetching loan details for bharat swipe for merchant {}",merchant);
//		List<LendingCategories> lendingCategoriesList=lendingCategoryDao.findByBureau("BHARAT_SWIPE");
//		if(!lendingCategoriesList.isEmpty()) {
//			if(lendingBharatswipeOffers!=null) {
//				List<LoanEligibilityDTO> eligibilityDTOs = new ArrayList<>();
//				eligibleLoanDao.deleteByMerchantId(experian.getMerchantId());
//				for (LendingCategories lendingCategories : lendingCategoriesList) {
//					eligibilityDTOs.add(loanEligibleService.calculateLoanBreakup(lendingCategories, 0, null, experian.getMerchantId(), experian.getId(), lendingBharatswipeOffers.getLoanAmount(), experian.getColor(), "2", "BHARAT_SWIPE", false, false));
//				}
//				if (!eligibilityDTOs.isEmpty()) {
//					eligibilityDTOs.sort(Comparator.comparing(LoanEligibilityDTO::getAmount, Comparator.reverseOrder()).thenComparing(LoanEligibilityDTO::getEdi));
//					experianDao.updateEligibleAmount(experian.getId(), eligibilityDTOs.get(0).getAmount().doubleValue(), eligibilityDTOs.get(0).getPrincipleEdiTenure().toString(), "BHARAT_SWIPE");
//				}
//				return eligibilityDTOs;
//			}
//		}
//		return new ArrayList<>();
//	}

	public boolean isRegularLoanInEligible(Experian experian, Double amount){
		if (experian != null && experian.getPincode() != null && Objects.nonNull(amount) ) {
			PincodeCityStateMappingSlave pincodeCityStateMapping = pincodeCityStateMappingDaoSlave.findByPincode(experian.getPincode());
			Boolean cpvCity = (pincodeCityStateMapping != null && LendingConstants.CPV_CITIES.contains(pincodeCityStateMapping.getCity()));

			return !cpvCity || amount < 50000;
		}

		return false;
	}


//	public boolean repeatLoanGlobalCheck(Merchant merchant){
//
//		try {
//			logger.info("Checking repeat loan closure less than 65% for merchant:{}", merchant.getId());
//			LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findLatestLendingPaymentScheduleByMerchantId(merchant.getId());
//			if(Objects.nonNull(lendingPaymentSchedule) && "CLOSED".equalsIgnoreCase(lendingPaymentSchedule.getStatus())){
//				Integer ledger = lendingLedgerDao.findLedgerCountOnAmountGreaterThanEdiAmount(lendingPaymentSchedule.getId(), lendingPaymentSchedule.getEdiAmount());
//				if (Objects.nonNull(ledger)) {
//
//					int totalEdis = lendingPaymentSchedule.getEdiCount();
//					int onTimePaymentPercentage = 0;
//					if (totalEdis != 0) {
//						onTimePaymentPercentage = (ledger * 100) / totalEdis;
//
//						return onTimePaymentPercentage <= 65;
//					}
//				}
//			}
//		}catch (Exception ex){
//			logger.error("Error Occurred while checking repeatLoanGlobalCheck for merchantId: {}, Er :{}", merchant.getId(), ex);
//		}
//		return false;
//	}

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

	private List<LoanHistoryDTO> fetchLoanHistory(LendingApplication application, List<LendingPaymentSchedule> lendingPaymentScheduleList, LendingPaymentSchedule activeLoan, boolean repeatLoan, BpEnachSlave enachSuccess, boolean showTarget, double targetTpv) {
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

	private LoanApplicationDTO fetchLoanApplication(BasicDetailsDto merchant, LendingApplication application) {
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

	private List<DocumentDTO> fetchDocuments(LendingApplication lendingApplication, BasicDetailsDto merchant) {
		List<DocumentDTO> documents = new ArrayList<>();
		List<DocumentsIdProofMaster> documentsIdProofList = documentsIdProofDaoMaster.findByMerchantIdAndLendingApplicationId(merchant.getId(), lendingApplication.getId());
		for(DocumentsIdProofMaster documentsIdProof : documentsIdProofList) {
			DocumentDTO document = new DocumentDTO();
			document.setId(documentsIdProof.getId());
			document.setProofType(documentsIdProof.getProofType());
			document.setSinglePageDocument(documentsIdProof.getSinglePage() != null && documentsIdProof.getSinglePage() == 0 ? false : true);
		}
		return documents;
	}

	public SettlementResponseDTO getSettlements(BasicDetailsDto merchant, Long loanId) {
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

//	public boolean isMerchantFromCreditLine(BasicDetailsDto merchant) {
//		CreditLineMerchant creditLineMerchant = creditLineMerchantDao.findByMerchantId(merchant.getId());
//		return creditLineMerchant != null;
//	}


	public ResponseDTO creditScore(BasicDetailsDto merchantBasicDetails,RequestDTO<CreditScoreRequestDto> requestDTO,String clientIp) {
		try {
			ResponseDTO responseDTO = new ResponseDTO(true, null, null, null);
			CreditScoreResponseDto creditScoreResponseDto = new CreditScoreResponseDto();
			creditScoreResponseDto.setTimeout(Boolean.FALSE);
			creditScoreResponseDto.setNTC(Boolean.FALSE);
			creditScoreResponseDto.setEligible(Boolean.FALSE);
			CreditScoreRequestDto creditScoreRequestDto = requestDTO.getPayload();
			String pancard = creditScoreRequestDto.getPanNumber();
			Experian experian = experianDao.getByMerchantId(merchantBasicDetails.getId());
			String key = "bharatpe.in/creditscore";

			if (requestDTO.getPayload().getPanNumber() == null && experian == null) {
				creditScoreResponseDto.setPanNumber("null");
				creditScoreResponseDto.setPinCode(0);
				responseDTO.setData(creditScoreRequestDto);
				return responseDTO;
			}
			List<LoanEligibilityDTO> loanEligibilityDTOs = new ArrayList<>();

//			MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(merchant.getId());
			MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(merchantBasicDetails.getId());
			if (ObjectUtils.isEmpty(merchantResponseDTO)) {
				throw new MerchantSummaryExceptionHandler(merchantBasicDetails.getId().toString());
			}
			Boolean sms = Boolean.FALSE;
			Integer pincode = creditScoreRequestDto.getPinCode() != null ? creditScoreRequestDto.getPinCode() : experian.getPincode();
			LendingApplication lendingApplicationList = lendingApplicationDao.getLatestPendingApplication(merchantBasicDetails.getId());
			LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.getOldestActiveLoan(merchantBasicDetails.getId());
			LendingApplication latestApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchantBasicDetails.getId());
			if (requestDTO.getPayload().getPanNumber() != null) {
				if (experian != null) {
					experian.setPancardNumber(requestDTO.getPayload().getPanNumber());
					experian.setBpScore((merchantResponseDTO != null && merchantResponseDTO.getBpScore() != null) ? merchantResponseDTO.getBpScore() : 0D);
					experian.setPincode(requestDTO.getPayload().getPinCode());
					experian.setSkip(false);
					experianDao.save(experian);
					if (experian.getSource() == null || !experian.getSource().equals("CREDIT_SCORE")) {
						sms = Boolean.TRUE;
					}
				} else {
					experian = experianDao.save(new Experian(merchantBasicDetails.getId(), clientIp, merchantBasicDetails.getLatitude() != null && merchantBasicDetails.getLatitude() <= 90 ? merchantBasicDetails.getLatitude() : null, merchantBasicDetails.getLongitude() != null && merchantBasicDetails.getLongitude() <= 90 ? merchantBasicDetails.getLongitude() : null, 0, pancard, (merchantResponseDTO != null && merchantResponseDTO.getBpScore() != null) ? merchantResponseDTO.getBpScore() : 0D, 0, pincode));
					experian.setSource("CREDIT_SCORE");
					experianDao.save(experian);
					sms = Boolean.TRUE;
				}
			}
			Double eligibleAmount = 0D;
			GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(merchantBasicDetails.getId(), "CREDIT_SCORE");
			if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
				logger.info("Global limit for merchant:{} is {}", merchantBasicDetails.getId(), globalLimitResponse.getData().getGlobalLimit());
				experian = globalLimitResponse.getData().getExperian();
				eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
			}
			logger.info("Experian after global limit call for merchant:{} is {}", merchantBasicDetails.getId(), experian.toString());
			String tenure = null;
			Integer edi = null;
			if (eligibleAmount > 0D) {
				List<GlobalLimitResponse.OfferDetail> offerDetails = globalLimitResponse.getData().getOfferDetails();
				if (!offerDetails.isEmpty()) {
					GlobalLimitResponse.OfferDetail offerDetail = offerDetails.get(0);
					Integer ediAmount = (int) Math.ceil(((eligibleAmount + (eligibleAmount * (offerDetail.getInterestRate() / 100) * offerDetail.getTenure()))) / offerDetail.getEdiCount());
					tenure = offerDetail.getTenure() + "Months";
					LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();
					loanEligibilityDTO.setAmount(eligibleAmount.intValue());
					loanEligibilityDTO.setEdi(ediAmount);
					loanEligibilityDTO.setTenure(tenure);
					loanEligibilityDTOs.add(loanEligibilityDTO);
				}
			}

			boolean rejected = false;
			if (experian.getRejected()) {
				if (Objects.nonNull(experian.getReason()) && (experian.getReason().equalsIgnoreCase(ExperianConstants.FOS_APP) || experian.getReason().equalsIgnoreCase(ExperianConstants.MULTIPLE_PSP_APPS))) {
					rejected = false;
				} else {
					rejected = true;
				}
				creditScoreResponseDto.setMessage(experian.getReason());
			}
			if (latestApplication != null && "rejected".equals(latestApplication.getStatus())) {
				if ("REJECTED".equalsIgnoreCase(latestApplication.getManualCibil()) || rejectedInLastNDays(latestApplication, 7)) {
					loanEligibilityDTOs.clear();
					eligibleAmount = 0D;
					tenure = null;
					edi = null;
				}
			}

			LendingPancard lendingPancard = lendingPancardDao.findByMerchantId(merchantBasicDetails.getId());
			creditScoreResponseDto.setPanNumber(experian.getPancardNumber());
			creditScoreResponseDto.setPinCode(experian.getPincode());
			creditScoreResponseDto.setPanName(lendingPancard != null ? lendingPancard.getName() : merchantBasicDetails.getBeneficiaryName());
			creditScoreResponseDto.setScore(experian.getExperianScore());
			creditScoreResponseDto.setCreditDate(experian.getReportDate());
			creditScoreResponseDto.setBureau(experian.getBureau() != null ? experian.getBureau() : "EXPERIAN");
			creditScoreResponseDto.setNoExperian(false);
			creditScoreResponseDto.setActiveLoan(lendingPaymentSchedule != null);

			String identifier;

			// todo : remove and use apis to fetch info
//			Merchant merchant = merchantDao.getById(merchantBasicDetails.getId());

			String deeplink = notificationUtil.getDeeplink(merchantBasicDetails.getSettlementType(), "CREDIT_SCORE");
			Map<String, Object> templateParams = new HashMap<>();
			templateParams.put("pan_name", creditScoreResponseDto.getPanName());
			NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
			notificationPayloadDto.setMobile(merchantBasicDetails.getMobile());
			notificationPayloadDto.setClientName("LENDING");
			notificationPayloadDto.setPushDeepLink(deeplink);
			notificationPayloadDto.setPushTitle("BHARATPE");

			if (rejected || experian.getReason() != null) {
				if ("NTC".equals(experian.getReason())) {
					creditScoreResponseDto.setNTC(Boolean.TRUE);
				}
				creditScoreResponseDto.setMessage(experian.getReason());
				responseDTO.setData(creditScoreResponseDto);
//			if(sms){
//				identifier = "LENDING_CREDIT_SCORE_2_PUSH";
//				templateParams.put("key",key);
//				notificationPayloadDto.setTemplateParams(templateParams);
//				notificationPayloadDto.setTemplateIdentifier(identifier);
//				lendingNotificationService.notify(notificationPayloadDto);
//			}
				return responseDTO;
			}
			if (lendingApplicationList != null) {
				creditScoreResponseDto.setApplicationPending(Boolean.TRUE);
				creditScoreResponseDto.setEligible(Boolean.TRUE);
				creditScoreResponseDto.setEligibleAmount(eligibleAmount);
				creditScoreResponseDto.setEligibility(loanEligibilityDTOs);
				creditScoreResponseDto.setEdi(edi);
				creditScoreResponseDto.setTenure(tenure);
				responseDTO.setData(creditScoreResponseDto);
				if (sms) {
					identifier = "LENDING_CREDIT_SCORE_PUSH";
					templateParams.put("experian_score", experian.getExperianScore());
					notificationPayloadDto.setTemplateParams(templateParams);
					notificationPayloadDto.setTemplateIdentifier(identifier);
					lendingNotificationService.notify(notificationPayloadDto);
				}
				return responseDTO;
			}

			if (lendingPaymentSchedule != null) {
				creditScoreResponseDto.setActiveLoan(Boolean.TRUE);
				responseDTO.setData(creditScoreResponseDto);
				if (sms) {
					identifier = "LENDING_CREDIT_SCORE_PUSH";
					templateParams.put("experian_score", experian.getExperianScore());
					notificationPayloadDto.setTemplateParams(templateParams);
					notificationPayloadDto.setTemplateIdentifier(identifier);
					lendingNotificationService.notify(notificationPayloadDto);
				}
				return responseDTO;
			}
			if (experian.getExperianScore() == null || experian.getExperianScore().equals(0D) || experian.getExperianScore() < 300D) {
				creditScoreResponseDto.setNTC(Boolean.TRUE);
				creditScoreResponseDto.setMessage("CRIF");
				responseDTO.setData(creditScoreResponseDto);
//			if(sms){
//				identifier = "LENDING_CREDIT_SCORE_2_PUSH";
//				templateParams.put("key",key);
//				notificationPayloadDto.setTemplateParams(templateParams);
//				notificationPayloadDto.setTemplateIdentifier(identifier);
//				lendingNotificationService.notify(notificationPayloadDto);
//			}
				return responseDTO;
			}

			if (eligibleAmount > 0D) {
				experian.setSource("CREDIT_SCORE");
				experianDao.save(experian);
				redisNotificationService.sendNotificationForSeenOffer(merchantBasicDetails.getId());
			}
			if (sms) {
				String message = "Dear " + creditScoreResponseDto.getPanName() + ",\n" +
						"Your Credit Score is generated and your current Score is " + experian.getExperianScore();
				identifier = "LENDING_CREDIT_SCORE_PUSH";
				templateParams.put("experian_score", experian.getExperianScore());
				notificationPayloadDto.setTemplateParams(templateParams);
				notificationPayloadDto.setTemplateIdentifier(identifier);
				lendingNotificationService.notify(notificationPayloadDto);
			}
			creditScoreResponseDto.setEligible(eligibleAmount > 0D);
			creditScoreResponseDto.setEligibleAmount(eligibleAmount);
			creditScoreResponseDto.setEligibility(loanEligibilityDTOs);
			creditScoreResponseDto.setEdi(edi);
			creditScoreResponseDto.setTenure(tenure);
			responseDTO.setData(creditScoreResponseDto);
			return responseDTO;
		} catch (Exception ex) {
			logger.error("Error occured while checking credit score for merchantId : {} {}", merchantBasicDetails.getId(), ex);
		}
		return new ResponseDTO(Boolean.FALSE, "Something went wrong!");
	}

//	private void sendSms(String messageForSms, Merchant merchant) {
//		List<String> mobiles=new LinkedList<>();
//
//			mobiles.add(merchant.getMobile());
//			smsServiceHandler.sendSMS(mobiles, messageForSms, NotificationProvider.SMS.GUPSHUP);
//	}

	public CommonResponse getRepaymentHistory(BasicDetailsDto merchant, String lendingPaymentScheduleId) {


		try {
			List<LoanPaymentOrder> loanPaymentOrders = loanPaymentOrderDao.findByOwnerIdAndMerchantId(lendingPaymentScheduleId, merchant.getId());
			if(Objects.nonNull(loanPaymentOrders)){
				List<Map<String, Object>> repaymentHistoryList = new ArrayList<>();
				for(LoanPaymentOrder loanPaymentOrder: loanPaymentOrders){
					Map<String, Object> repaymentHistory = new HashMap<>();
					repaymentHistory.put("amount",loanPaymentOrder.getAmount());
					repaymentHistory.put("mode",LoanUtil.settlementMode.getOrDefault(loanPaymentOrder.getSource(), "UPI"));
					repaymentHistory.put("order_id",loanPaymentOrder.getOrderId());
					repaymentHistory.put("date",loanPaymentOrder.getCreatedAt());
					repaymentHistory.put("status",loanPaymentOrder.getStatus());

					repaymentHistoryList.add(repaymentHistory);
				}

				return new CommonResponse(true, "Repayment History", repaymentHistoryList);
			}
		} catch (Exception ex) {
			logger.error("Exception while repayment history for ApplicationId {} Error: {}", lendingPaymentScheduleId, ex);
		}
		return new CommonResponse(false, "SomeThing Went Wrong", null);
	}

//	public void notifyNTBSMS(Merchant merchant) {
//		try {
//			String redisKey = "SMS_SYNC_" + merchant.getId();
//			Object smsSync = lendingCache.get(redisKey);
//			if (smsSync != null) {
//				logger.info("already pushed sms sync for merchant:{}", merchant.getId());
//				return;
//			}
//			logger.info("Checking NTB SMS after 5 min for merchant:{}", merchant.getId());
//			String hashKey = merchant.getId() + "_" + UUID.randomUUID().toString();
//			delayedMessagePublisher.publish("notify_ntb_sms", merchant.getId().toString(), merchant.getId(), hashKey, 5*60);
//			AddCacheDto addCacheDto = new AddCacheDto();
//			addCacheDto.setKey(redisKey);
//			addCacheDto.setTtl(1);
//			addCacheDto.setValue(true);
//			lendingCache.add(addCacheDto);
//		} catch (Exception e) {
//			logger.error("Exception in NTB SMS Notify for merchant:{}", merchant.getId(), e);
//		}
//	}

	public SettlementV2ResponseDTO getSettlementsV2(BasicDetailsDto merchant, Long loanId) {
		LendingPaymentSchedule lendingPaymentSchedule;
		dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
		if (loanId != null) {
			lendingPaymentSchedule = lendingPaymentScheduleDao.findByIdAndMerchantId(loanId, merchant.getId());
		} else {
			lendingPaymentSchedule = lendingPaymentScheduleDao.getOldestActiveLoan(merchant.getId());
		}
		if (lendingPaymentSchedule == null){
			return new SettlementV2ResponseDTO(false, "No Active Loan");
		}
		List<LendingLedgerDao.SettlementDTO> settlementDTOList = lendingLedgerDao.getSettlements(loanId);
		List<SettlementV2ResponseDTO.Settlement> settlementList = new ArrayList<>();
		for(LendingLedgerDao.SettlementDTO settlementDTO : settlementDTOList) {
			SettlementV2ResponseDTO.Settlement settlement = new SettlementV2ResponseDTO.Settlement();
			settlement.setPaidAmount(settlementDTO.getPaid());
			settlement.setDate(dateFormat.format(settlementDTO.getDate()));
			settlement.setDueAmount(settlementDTO.getDue());
			settlementList.add(settlement);
		}
		if (settlementList.isEmpty()) {
			return new SettlementV2ResponseDTO(false, "No Settlement Found");
		}
		settlementList.sort(Comparator.comparing(SettlementV2ResponseDTO.Settlement::getDate).reversed());
		SettlementV2ResponseDTO settlementV2ResponseDTO = new SettlementV2ResponseDTO();
		settlementV2ResponseDTO.setSettlement(settlementList);
		settlementV2ResponseDTO.setLender(lendingPaymentSchedule.getNbfc());
		return settlementV2ResponseDTO;
	}

	public CommonResponse updateLendingGstDetails(Long applicationId,
												  UpdateLendingGstDetailsRequestDTO updateLendingGstDetailsRequestDTO) {

		LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(applicationId);

		if (ObjectUtils.isEmpty(lendingGstDetail)) {
			return new CommonResponse(false,
			"Lending gst details does not exist for the applicationId : " + applicationId);
		}

		if (Objects.nonNull(updateLendingGstDetailsRequestDTO.getArrivedScore()))
			lendingGstDetail.setArrivedScore(updateLendingGstDetailsRequestDTO.getArrivedScore());

		lendingGstDao.save(lendingGstDetail);

		return new CommonResponse(lendingGstDetail);
	}

	public DocumentDetailsDto documentDetails(LendingPaymentSchedule lendingPaymentSchedule) {
		DocumentDetailsDto documentDetailsDto = new DocumentDetailsDto();
		documentDetailsDto.setMessage("Fetched Document details");
		documentDetailsDto.setSuccess(true);
		DocumentDetailsDto.Data data = new DocumentDetailsDto.Data();
		LoanAgreement loanAgreement = loanAgreementDao.findByApplicationIdAndType(lendingPaymentSchedule.getApplicationId(), "agreement");
		String shortUrl = "";
		if (loanAgreement != null) {
			String fileName = loanAgreement.getAgreementName();
			try {
				shortUrl = liquiloansService.getShorturl(fileName, loanAgreement);
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
//		LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(applicationId);
		String nocUrl = supportService.getNocUrl(lendingPaymentSchedule);

		data.setAgreementUrl(shortUrl);
		data.setNocUrl(nocUrl);
		data.setSanctionUrl(null);
		documentDetailsDto.setData(data);
		return documentDetailsDto;
	}
}
