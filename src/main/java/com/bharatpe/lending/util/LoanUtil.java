package com.bharatpe.lending.util;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.service.MongoPublisher;
import com.bharatpe.common.utils.CurrencyUtils;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.PincodeColor;
import com.bharatpe.lending.common.enums.RiskSegment;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.PincodeCityStateMappingDTO;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.handlers.MerchantScoreException;
import com.bharatpe.lending.handlers.MerchantScoreHandler;
import com.bharatpe.lending.handlers.MerchantSummaryExceptionHandler;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import com.bharatpe.lending.service.APIGatewayService;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Component
public class LoanUtil {
	private static final Logger logger = LoggerFactory.getLogger(LoanUtil.class);

	@Autowired
	MongoPublisher mongoPublisher;

	@Autowired
	LendingCovidCitiesDao lendingCovidCitiesDao;

	@Autowired
	LoanDetailsServiceV2 loanDetailsServiceV2;

	@Autowired
	DsHandler dsHandler;
	@Autowired
	LendingApplicationPriorityDao lendingApplicationPriorityDao;

	@Autowired
	LendingPennydropDao lendingPennydropDao;

	@Autowired
	APIGatewayService apiGatewayService;

	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;

	@Autowired
	EnachHandler enachHandler;

	@Autowired
	LendingPincodesDao lendingPincodesDao;

//	@Autowired
//	MerchantSummaryDao merchantSummaryDao;

	@Autowired
	MerchantSummarySnapshotDao merchantSummarySnapshotDao;

	@Autowired
	ExperianDao experianDao;

	@Autowired
	ExperianSnapshotDao experianSnapshotDao;

	@Autowired
	LendingBBSDao lendingBBSDao;

	@Autowired
	LendingBBSSnapshotDao lendingBBSSnapshotDao;

//	@Autowired
//	MerchantScoreDao merchantScoreDao;

	@Autowired
	MerchantScoreSnapshotDao merchantScoreSnapshotDao;

	@Autowired
	LendingPrepaymentDao lendingPrepaymentDao;

	@Autowired
	KafkaTemplate<String, Object> kafkaTemplate;

	@Autowired
	LendingRiskVariablesDao lendingRiskVariablesDao;

	@Autowired
	MerchantGstOfferDao merchantGstOfferDao;

	@Autowired
	MerchantGstOfferSnapshotDao merchantGstOfferSnapshotDao;

	@Autowired
	LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

	@Autowired
	LendingShopDocumentsDao lendingShopDocumentsDao;

	@Autowired
	EligibleLoanDao eligibleLoanDao;

	@Autowired
	MerchantSummaryHandler merchantSummaryHandler;

	ExecutorService executorService = Executors.newFixedThreadPool(10);

	SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	@Autowired
	MerchantService merchantService;

	@Autowired
	MerchantScoreHandler merchantScoreHandler;

	@Autowired
	LendingCache lendingCache;

	@Value("${merchant.references.min.score}")
	Integer minScore;

	@Autowired
	LoanDpdDao loanDpdDao;

	@Autowired
	LendingLedgerDao lendingLedgerDao;

	String INTERNAL_MERCHANTS = "internal_merchants";

	@Autowired
	LendingResubmitTaskDao lendingResubmitTaskDao;

	@Autowired
	LendingApplicationDao lendingApplicationDao;

	@Autowired
	LendingApplicationDetailsDao lendingApplicationDetailsDao;

	public List<String> allowedRiskGroupsNachWaiver = Arrays.asList("R1", "R2", "R3", "R4");

	List<Long> derogMerchants = new ArrayList();

	public List<Long> loadDerogEffectedMerchants() {
		if (!ObjectUtils.isEmpty(derogMerchants)) {
			return derogMerchants;
		}
		derogMerchants = readCsvFile();
		return derogMerchants;
	}

	private boolean derogTopUpEnable(Long merchantId) {
		LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
		logger.info("DEROG_EFFECTED_MERCHANT_FLOW: merchant_id: {} application_id: {} type: {}", merchantId, lendingApplication.getId(), lendingApplication.getLoanType());
		return LoanType.TOPUP.name().equals(lendingApplication.getLoanType());
	}


	public static Map<String, Object> prepareSelectedLoanForClient(LendingApplication application, LendingCategories lendingCategories) {
		Map<String, Object> selectedLoan = new LinkedHashMap<>();

		selectedLoan.put("amount", application.getLoanAmount().intValue());
		selectedLoan.put("category", application.getCategory());
		selectedLoan.put("construct", application.getLoanConstruct());
		selectedLoan.put("tenure", application.getTenure());
		selectedLoan.put("id", application.getId());
		selectedLoan.put("finance_charge", application.getProcessingFee().intValue());
		selectedLoan.put("edi", application.getEdi());
		selectedLoan.put("edi_duration", application.getPayableDays());
		selectedLoan.put("interest_rate", application.getInterestRate());
		selectedLoan.put("repayment", application.getRepayment().intValue());
		selectedLoan.put("disbursement_amount", application.getLoanAmount().intValue() - application.getProcessingFee().intValue());
		selectedLoan.put("interest_amount", application.getRepayment().intValue() - application.getLoanAmount().intValue());
		//selectedLoan.put("installment_details", prepareLabels(application, lendingCategories != null ? lendingCategories.getIoTenureMonths().intValue() : 0));
		selectedLoan.put("installment_details", new ArrayList<>());
		selectedLoan.put("lender", application.getLender());
		return selectedLoan;
	}

	public static SelectedLoanDTO prepareSelectedLoanDTO(LendingApplication application, LendingCategories lendingCategories) {
		SelectedLoanDTO selectedLoan = new SelectedLoanDTO();

		selectedLoan.setId(application.getId());
		selectedLoan.setAmount(application.getLoanAmount().intValue());
		selectedLoan.setCategory(application.getCategory());
		selectedLoan.setConstruct(application.getLoanConstruct());
		selectedLoan.setTenure(application.getTenure());
		selectedLoan.setFincanceCharge(application.getProcessingFee().intValue());
		selectedLoan.setEdi(application.getEdi());
		selectedLoan.setEdiDuration(application.getPayableDays());
		selectedLoan.setInterestRate(application.getInterestRate());
		selectedLoan.setRepayment(application.getRepayment() != null ? application.getRepayment().intValue() : 0);
		selectedLoan.setDisbursementAmount((application.getLoanAmount().intValue() - application.getProcessingFee().intValue()));
		if (application.getRepayment() != null) {
			selectedLoan.setInterestAmount(application.getRepayment().intValue() - application.getLoanAmount().intValue());
		} else {
			selectedLoan.setInterestAmount(0);
		}
		if (lendingCategories != null) {
			selectedLoan.setInstallmentDetails(prepareLabels(application, lendingCategories.getIoTenureMonths().intValue()));
		}

		return selectedLoan;
	}

	//for credit line
	public static SelectedLoanDTO prepareSelectedLoanDTO(CreditApplication application) {


		SelectedLoanDTO selectedLoan = new SelectedLoanDTO();

		selectedLoan.setId(application.getId());
		selectedLoan.setAmount(application.getAmount().intValue());
		selectedLoan.setCategory(application.getCategory());

		return selectedLoan;
	}

	public static Map<String, Object> prepareShopDetailsForClient(LendingApplication application, LendingGstDetail lendingGstDetail) {
		Map<String, Object> shopDetails = new LinkedHashMap<>();

		shopDetails.put("business_name", application.getBusinessName());
		shopDetails.put("shop_number", application.getShopNumber());
		shopDetails.put("street_address", application.getStreetAddress());
		shopDetails.put("area", application.getArea());
		shopDetails.put("landmark", application.getLandmark());
		shopDetails.put("pincode", application.getPincode());
		shopDetails.put("city", application.getCity());
		shopDetails.put("state", application.getState());
		shopDetails.put("alternate_mobile", application.getAlternateMobile());
		if (lendingGstDetail != null) {
			shopDetails.put("gstNumber", lendingGstDetail.getGstNumber() != null ? lendingGstDetail.getGstNumber() : "");
			shopDetails.put("entityType", lendingGstDetail.getEntityType() != null ? lendingGstDetail.getEntityType() : "");
			shopDetails.put("salary", lendingGstDetail.getSalary() != null ? lendingGstDetail.getSalary() : "");
			shopDetails.put("hasGST", lendingGstDetail.getGst() != null ? lendingGstDetail.getGst() : "");
			shopDetails.put("experience", lendingGstDetail.getExperience() != null ? lendingGstDetail.getExperience() : "");
			shopDetails.put("businessCategory", lendingGstDetail.getBusinessCategory() != null ? lendingGstDetail.getBusinessCategory() : "");
			shopDetails.put("shopType", lendingGstDetail.getShopType() != null ? lendingGstDetail.getShopType() : "");
		}
		return shopDetails;
	}

	public static ShopDetailsDTO prepareShopDetailsDTO(LendingApplication application, LendingGstDetail lendingGstDetail) {
		ShopDetailsDTO shopDetails = new ShopDetailsDTO();

		shopDetails.setBusinessName(application.getBusinessName());
		shopDetails.setShopNumber(application.getShopNumber());
		shopDetails.setStreetAddress(application.getStreetAddress());
		shopDetails.setArea(application.getArea());
		shopDetails.setLandmark(application.getLandmark());
		if (application.getPincode() != null) {
			shopDetails.setPincode(application.getPincode().toString());
		}
		shopDetails.setCity(application.getCity());
		shopDetails.setState(application.getState());
		if (lendingGstDetail != null) {
			shopDetails.setEntityType(lendingGstDetail.getEntityType());
			shopDetails.setGstNumber(lendingGstDetail.getGstNumber());
			shopDetails.setHasGST(lendingGstDetail.getGst());
			shopDetails.setBusinessCategory(lendingGstDetail.getBusinessCategory());
			shopDetails.setSalary(lendingGstDetail.getSalary() != null ? String.valueOf(lendingGstDetail.getSalary()) : "");
			shopDetails.setExperience(lendingGstDetail.getExperience());
			shopDetails.setShopType(lendingGstDetail.getShopType());
		}
		return shopDetails;
	}

	//for credit line
	public static ShopDetailsDTO prepareShopDetailsDTO(CreditApplication application, CreditApplicationAddress creditApplicationAddress) {

		ShopDetailsDTO shopDetails = new ShopDetailsDTO();
		if (creditApplicationAddress != null) {
			shopDetails.setBusinessName(application.getBusinessName());
			shopDetails.setShopNumber(creditApplicationAddress.getShopNumber());
			shopDetails.setStreetAddress(creditApplicationAddress.getStreetAddress());
			shopDetails.setArea(creditApplicationAddress.getArea());
			shopDetails.setLandmark(creditApplicationAddress.getLandmark());
			if (creditApplicationAddress.getPincode() != null) {
				shopDetails.setPincode(creditApplicationAddress.getPincode().toString());
			}
			shopDetails.setCity(creditApplicationAddress.getCity());
			shopDetails.setState(creditApplicationAddress.getState());
			shopDetails.setAlternateContact(application.getAlternateMobile());
		} else {
			logger.warn("Shop details not available for application {}", application.getId());
		}
		return shopDetails;
	}

	private static List<LabelDTO> prepareLabels(LendingApplication application, int months) {
		List<LabelDTO> list = new ArrayList<>();

		if ("CONSTRUCT_1".equals(application.getLoanConstruct())) {

		} else if ("CONSTRUCT_2".equals(application.getLoanConstruct())) {
			list.add(new LabelDTO("EDI for 1st Month", "ZERO"));
			list.add(new LabelDTO("EDI for Next " + (application.getTenureInMonths() - 1) + " Months", "₹" + CurrencyUtils.formatInt(application.getEdi().intValue()) + "/day"));
			list.add(new LabelDTO("No Deduction on Sundays", ""));
		} else if ("CONSTRUCT_3".equals(application.getLoanConstruct())) {
			if (months > 1) {
				list.add(new LabelDTO("EDI for 1st " + months + " Months", "₹" + CurrencyUtils.formatInt(application.getIoEdi().intValue()) + "/day"));
			} else {
				list.add(new LabelDTO("EDI for 1st Month", "₹" + CurrencyUtils.formatInt(application.getIoEdi().intValue()) + "/day"));
			}
			list.add(new LabelDTO("EDI for Next " + (application.getTenureInMonths() - months) + " Months", "₹" + CurrencyUtils.formatInt(application.getEdi().intValue()) + "/day"));
			list.add(new LabelDTO("No Deduction on Sundays", ""));
		} else {
			logger.error("Construct {} not defined, throwing Exception", application.getLoanConstruct());
			throw new RuntimeException("Construct not defined.");
		}

		return list;
	}

	public static long getDateDiffInDays(Date startTime, Date endTime) {
		long diff = endTime.getTime() - startTime.getTime();
		return TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
	}

	public static long getDateDiffInHour(Date startTime, Date endTime) {
		long diff = endTime.getTime() - startTime.getTime();
		return TimeUnit.HOURS.convert(diff, TimeUnit.MILLISECONDS);
	}

	public static Date addDays(Date date, Long days) {
		Calendar cal = Calendar.getInstance();

		cal.setTime(date);
		cal.add(Calendar.DATE, days.intValue());

		return cal.getTime();
	}

	public static Map<String, String> settlementMode = new HashMap<String, String>() {{
		put("SETTLEMENT", "QR Txns.");
		put("EXTERNAL_NACH", "NACH");
		put("INTEREST_ACCOUNT", "Investment A/c");
		put("REFUND", "Refund");
		put("BHARATPE_NACH", "NACH");
		put("UPI", "UPI");
		put("SCHEME2", "Waiver");
		put("SCHEME1", "Waiver");
		put("SCHEME3", "Waiver");
		put("FP", "Investment A/c");
		put("UNSETTLED", "QR Txns.");
		put("DIRECT_TRANSFER", "Offline");
		put("EXCEPTION", "Offline");
		put("QR_SETTLEMENT", "QR Txns.");
		put("DC", "Debit Card");
		put("NB", "Net Banking");
		put("CC", "Credit Card");
		put("BP", "BP Balance");
		put("BT", "Bank Transfer");
		put("AUTO_FP", "Investment A/c");
		put("ADVANCE_EDI", "Advance EDI");
	}};

	public static List<JsonNode> jsonNodeArrayUtil(JsonNode nodeData) {
		List<JsonNode> resp = new ArrayList<>();
		if (nodeData != null && !nodeData.asText().equals("\"\"")) {
			if (nodeData.isObject()) {
				resp.add(nodeData);
			} else {
				for (JsonNode node : nodeData) {
					resp.add(node);
				}
			}
		}
		return resp;
	}

	public static int getEdiDays(int tenure) {
		switch (tenure) {
			case 1:
				return 26;
			case 3:
				return 77;
			case 6:
				return 155;
			case 9:
				return 234;
			case 12:
				return 311;
			default:
				return 388;//15 months
		}
	}

	public void auditExperian(Experian experian) {
		if (experian == null) {
			return;
		}
		try {
			ExperianAuditTrail experianAuditTrail = ExperianAuditTrail.createObject(experian);
			experianAuditTrail.setId(System.nanoTime());
			mongoPublisher.publish("Lending", "experian_audit_trail", experianAuditTrail.getMerchantId().toString(), new ArrayList<ExperianAuditTrail>() {{
				add(experianAuditTrail);
			}});
		} catch (Exception e) {
			logger.error("Exception in mongo publish", e);
		}
	}

	public static String getRandomNumberString() {
		Random rnd = new Random();
		int number = rnd.nextInt(999);
		return String.format("%03d", number);
	}

//	public boolean isCpvCity(Integer pincode) {
//		if (pincode == null) {
//			return false;
//		}
//		PincodeCityStateMappingSlave pincodeCityStateMapping = pincodeCityStateMappingDaoSlave.findByPincode(pincode);
//		return pincodeCityStateMapping != null && LendingConstants.CPV_CITIES.contains(pincodeCityStateMapping.getCity());
//	}

	public int getApplicationTAT(Long applicationId) {
		int tat = -1;
		LendingApplicationPriority lendingApplicationPriority = lendingApplicationPriorityDao.findByApplicationId(applicationId);
		if (lendingApplicationPriority != null && lendingApplicationPriority.getTat() != null && lendingApplicationPriority.getTatStartTime() != null) {
			tat = (int) (lendingApplicationPriority.getTat() - (getDateDiffInDays(lendingApplicationPriority.getTatStartTime(), new Date())));
		}
		return tat;
	}

	public static String getFirstName(String name) {
		if (name == null) {
			return "";
		}
		int lastIndexOfSpace = name.lastIndexOf(" ");
		if (lastIndexOfSpace != -1) {
			return name.substring(0, lastIndexOfSpace);
		} else {
			lastIndexOfSpace = name.lastIndexOf(".");
			if (lastIndexOfSpace != -1) {
				return name.substring(0, lastIndexOfSpace);
			} else {
				return name;
			}
		}
	}

	public static String getLastName(String name) {
		if (name == null) {
			return "";
		}
		int lastIndexOfSpace = name.lastIndexOf(" ");
		if (lastIndexOfSpace != -1) {
			return name.substring(lastIndexOfSpace + 1);
		} else {
			lastIndexOfSpace = name.lastIndexOf(".");
			if (lastIndexOfSpace != -1) {
				return name.substring(lastIndexOfSpace + 1);
			} else {
				return name;
			}
		}
	}

	public Boolean isCovidCities(Integer pinCode) {
		if (pinCode == null) {
			return false;
		}
		LendingCovidCities covidCities = lendingCovidCitiesDao.findByPincode(pinCode);
		return covidCities != null;
	}

	public void publishSmsAnalysisData(BasicDetailsDto merchant) {
		if (merchant == null) {
			return;
		}
		try {
			logger.info("Publish merchant_sms_analysis data in mongo for merchant:{}", merchant.getId());
			MerchantSmsAnalysis merchantSmsAnalysis = new MerchantSmsAnalysis(merchant.getMid());
			mongoPublisher.publish("Lending", "merchant_sms_analysis", merchant.getId().toString(), new ArrayList<MerchantSmsAnalysis>() {{
				add(merchantSmsAnalysis);
			}});
		} catch (Exception e) {
			logger.error("Exception in mongo publish merchant_sms_analysis for merchant:{}", merchant.getId(), e);
		}
	}

//	public void publishSmsAnalysisData(Merchant merchant) {
//		if (merchant == null) {
//			return;
//		}
//		try {
//			logger.info("Publish merchant_sms_analysis data in mongo for merchant:{}", merchant.getId());
//			MerchantSmsAnalysis merchantSmsAnalysis = new MerchantSmsAnalysis(merchant.getMid());
//			mongoPublisher.publish("Lending", "merchant_sms_analysis", merchant.getId().toString(), new ArrayList<MerchantSmsAnalysis>(){{add(merchantSmsAnalysis);}});
//		} catch (Exception e) {
//			logger.error("Exception in mongo publish merchant_sms_analysis for merchant:{}", merchant.getId(), e);
//		}
//	}

	public boolean checkPennyDrop(Long merchantId) {
		try {
			logger.info("Checking penny drop for merchant:{}", merchantId);
			Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(merchantId);
			if (ObjectUtils.isEmpty(basicDetailsDto)) {
				return false;
			}
			final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
			BankDetailsDto merchantBankDetail = null;
			if (bankDetailsDtoOptional.isPresent())
				merchantBankDetail = bankDetailsDtoOptional.get();
			//			Integer settlement = settlementDao.getCountSettlementLast15Days(merchant.getId(), merchantBankDetail.getAccountNumber());
//			if (settlement > 0) {
//				logger.info("Penny drop success for merchant:{}", merchant.getId());
//				return true;
//			}

			if (ObjectUtils.isEmpty(merchantBankDetail)) {
				return false;
			}
			LendingPennydrop successPennyDrop = lendingPennydropDao.isSuccess(merchantId, merchantBankDetail.getAccountNumber());
			if (successPennyDrop != null) {
				logger.info("Penny drop success for merchant:{}", merchantId);
				return true;
			}
			LendingPennydrop failedPennyDrop = lendingPennydropDao.isFailed(merchantId, merchantBankDetail.getAccountNumber());
			if (failedPennyDrop != null) {
				logger.info("Penny drop failed for merchant:{}", merchantId);
				return false;
			}
			return apiGatewayService.checkPennyDrop(merchantBankDetail, merchantId, basicDetailsDto.get().getMobile());
		} catch (Exception e) {
			logger.error("Exception in penny drop for merchant:{}", merchantId, e);
		}
		return false;
	}

	public static int calculateDPD(Double ediAmount, Double dueAmount) {
		if (dueAmount < ediAmount) return 0;
		return (int) Math.round(dueAmount / ediAmount);
	}

	public boolean hasActiveLoan(BasicDetailsDto merchant) {
		LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchant.getId(), "ACTIVE");
		return activeLoan != null;
	}

	public boolean isEnachDone(Long merchantId) {
		boolean enachDone = false;
		MerchantNachDetailsResponseDTO enachSuccess = enachHandler.findSuccessEnach(merchantId);
		final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
		BankDetailsDto merchantBankDetail = null;
		if (bankDetailsDtoOptional.isPresent())
			merchantBankDetail = bankDetailsDtoOptional.get();
		if (merchantBankDetail != null && enachSuccess != null && enachSuccess.getAccountNumber() != null && enachSuccess.getAccountNumber().equals(merchantBankDetail.getAccountNumber())) {
			enachDone = true;
		}
		return enachDone;
	}

	public boolean isEnachDone(Long merchantId, Long applicationId) {
		boolean enachDone = false;
		MerchantNachDetailsResponseDTO enachSuccess = enachHandler.findSuccessEnach(merchantId, applicationId);
		final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
		BankDetailsDto merchantBankDetail = null;
		if (bankDetailsDtoOptional.isPresent())
			merchantBankDetail = bankDetailsDtoOptional.get();
		if (merchantBankDetail != null && enachSuccess != null && enachSuccess.getAccountNumber() != null && enachSuccess.getAccountNumber().equals(merchantBankDetail.getAccountNumber())) {
			enachDone = true;
		}
		return enachDone;
	}

	public boolean isOGL(Integer pincode) {
		if (pincode == null) return false;
		PincodeCityStateMappingDTO pincodeCityStateMapping = merchantService.findByPincode(pincode);
		if (pincodeCityStateMapping == null) return true;
		LendingPincodes lendingPincodes = lendingPincodesDao.findByPincode(pincode);
		return lendingPincodes == null || lendingPincodes.getColor().equals(PincodeColor.RED);
	}

	public List<LendingPaymentSchedule> getPreviousLoans(Long merchantId) {
		return lendingPaymentScheduleDao.findByMerchantIdAndCreditLoanOrderByIdDesc(merchantId, false);
	}

	public void createApplicationSnapshot(LendingApplication lendingApplication, BasicDetailsDto merchant) {
		logger.info("Creating snapshots for application:{}", lendingApplication.getId());
		createMerchantSummarySnapshot(lendingApplication);
		createExperianSnapshot(lendingApplication);
		createBBSSnapshot(lendingApplication);
		createMerchantScoreSnapshot(lendingApplication);
		createRiskVariablesSnapshot(lendingApplication);
		createBureauDrsSnapshot(lendingApplication, merchant);
		createMerchantGstOfferSnapshot(lendingApplication);
	}

	private void createMerchantGstOfferSnapshot(LendingApplication lendingApplication) {
		try {
			List<MerchantGstOffer> merchantGstOfferList = merchantGstOfferDao.findByMerchantIdAndToBeConsidered(lendingApplication.getMerchantId(), true);
			if (ObjectUtils.isEmpty(merchantGstOfferList)) {
				return;
			}
			List<MerchantGstOfferSnapshot> merchantGstOfferSnapshotList = merchantGstOfferList.stream()
					.map(MerchantGstOfferSnapshot::createObject)
					.peek(merchantGstOfferSnapshot -> merchantGstOfferSnapshot.setApplicationId(lendingApplication.getId()))
					.collect(Collectors.toList());
			merchantGstOfferSnapshotDao.saveAll(merchantGstOfferSnapshotList);
		} catch (Exception e) {
			logger.error("Exception occurred while creating merchantGstOfferSnapshot for applicationId: {}", lendingApplication.getId(), e);
		}
	}

	// pushing data to DE team for creating snapshot
	private void createBureauDrsSnapshot(LendingApplication lendingApplication, BasicDetailsDto merchant) {
		Map<String, Object> applicationData = new HashMap<>();
		applicationData.put("applicationId", lendingApplication.getId());
		applicationData.put("merchantId", lendingApplication.getMerchantId());
		applicationData.put("mobile", merchant.getMobile().substring(2));
		applicationData.put("createdAt", new Date());
		applicationData.put("updatedAt", new Date());

		KafkaAudit<Map<String, Object>> kafkaAudit = new KafkaAudit<>("easy_loan", "lending", "application_snapshot", applicationData);
		dsHandler.pushKafkaAudit(kafkaAudit);
	}

	private void createRiskVariablesSnapshot(LendingApplication lendingApplication) {
		try {
			LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingApplication.getMerchantId());
			if (lendingRiskVariables != null) {
				LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = new LendingRiskVariablesSnapshot();
				lendingRiskVariablesSnapshot.setApplicationId(lendingApplication.getId());
				lendingRiskVariablesSnapshot.setMerchantId(lendingRiskVariables.getMerchantId());
				lendingRiskVariablesSnapshot.setBpScore(lendingRiskVariables.getBpScore());
				lendingRiskVariablesSnapshot.setBbs(lendingRiskVariables.getBbs());
				lendingRiskVariablesSnapshot.setVintage(lendingRiskVariables.getVintage());
				lendingRiskVariablesSnapshot.setBureauScore(lendingRiskVariables.getBureauScore());
				lendingRiskVariablesSnapshot.setRiskColor(lendingRiskVariables.getRiskColor());
				logger.info("risk segment found in lendingRiskVariables: {} for merchantId: {}", lendingRiskVariables.getRiskSegment(), lendingRiskVariables.getMerchantId());
				lendingRiskVariablesSnapshot.setRiskSegment(Objects.nonNull(lendingRiskVariables.getRiskSegment())?RiskSegment.valueOf(lendingRiskVariables.getRiskSegment()):null);
				logger.info("risk segment found in lendingRiskVariablesSnapshot: {} for merchantId: {}", lendingRiskVariablesSnapshot.getRiskSegment(), lendingRiskVariables.getMerchantId());
				lendingRiskVariablesSnapshot.setDecisionId(lendingRiskVariables.getDecisionId());
				lendingRiskVariablesSnapshot.setPincode(lendingRiskVariables.getPincode());
				lendingRiskVariablesSnapshot.setPincodeColor(lendingRiskVariables.getPincodeColor());
				lendingRiskVariablesSnapshot.setTpvOffer(lendingRiskVariables.getTpvOffer());
				lendingRiskVariablesSnapshot.setBureauOffer(lendingRiskVariables.getBureauOffer());
				lendingRiskVariablesSnapshot.setRegularLimit(lendingRiskVariables.getRegularLimit());
				lendingRiskVariablesSnapshot.setNtbLimit(lendingRiskVariables.getNtbLimit());
				lendingRiskVariablesSnapshot.setFinalOffer(lendingRiskVariables.getFinalOffer());
				lendingRiskVariablesSnapshot.setLoanType(lendingRiskVariables.getLoanType());
				lendingRiskVariablesSnapshot.setRepeatLoan(lendingRiskVariables.getRepeatLoan());
				lendingRiskVariablesSnapshot.setTpvRejection(lendingRiskVariables.getTpvRejection());
				lendingRiskVariablesSnapshot.setBureauRejection(lendingRiskVariables.getBureauRejection());
				lendingRiskVariablesSnapshot.setRiskRejection(lendingRiskVariables.getRiskRejection());
				lendingRiskVariablesSnapshot.setExperianRejection(lendingRiskVariables.getExperianRejection());
				lendingRiskVariablesSnapshot.setUnderwritingVersion(lendingRiskVariables.getUnderwritingVersion());
				logger.info("risk group found in lendingRiskVariables: {} for merchantId: {}", lendingRiskVariables.getRiskGroup(), lendingRiskVariables.getMerchantId());
				lendingRiskVariablesSnapshot.setRiskGroup(lendingRiskVariables.getRiskGroup());
				logger.info("risk group found in lendingRiskVariablesSnapshot: {} for merchantId: {}", lendingRiskVariablesSnapshot.getRiskGroup(), lendingRiskVariables.getMerchantId());
				lendingRiskVariablesSnapshot.setLoanSegment(lendingRiskVariables.getLoanSegment());
				lendingRiskVariablesSnapshot.setClubV2Amount(lendingRiskVariables.getClubV2Amount());
				lendingRiskVariablesSnapshot.setClubV2(lendingRiskVariables.getClubV2());
				lendingRiskVariablesSnapshot.setDrsScore(lendingRiskVariables.getDrsScore());
				lendingRiskVariablesSnapshot.setDrsScoreActive(lendingRiskVariables.getDrsScoreActive());
				lendingRiskVariablesSnapshot.setSmallTicketLimit(lendingRiskVariables.getSmallTicketLimit());
				lendingRiskVariablesSnapshot.setSmallTicketRejection(lendingRiskVariables.getSmallTicketRejection());
				lendingRiskVariablesSnapshot.setMonthlyNfi(lendingRiskVariables.getMonthlyNfi());
				lendingRiskVariablesSnapshot.setMonthlyTpv(lendingRiskVariables.getMonthlyTpv());
				lendingRiskVariablesSnapshot.setReferenceCount(lendingRiskVariables.getReferenceCount());
				lendingRiskVariablesSnapshot.setTenure(lendingRiskVariables.getTenure());
				lendingRiskVariablesSnapshot.setRoi(lendingRiskVariables.getRoi());
				lendingRiskVariablesSnapshot.setDsTpv(lendingRiskVariables.getDsTpv());
				lendingRiskVariablesSnapshot.setSummaryTpv(lendingRiskVariables.getSummaryTpv());
				lendingRiskVariablesSnapshot.setUniqueCustomer1mon(lendingRiskVariables.getUniqueCustomer1mon());
				lendingRiskVariablesSnapshot.setPilotIdentifier(lendingRiskVariables.getPilotIdentifier());
				lendingRiskVariablesSnapshot.setDpd30Count(lendingRiskVariables.getDpd30Count());
				lendingRiskVariablesSnapshot.setDpd60Count(lendingRiskVariables.getDpd60Count());
				lendingRiskVariablesSnapshot.setGstOffer(lendingRiskVariables.getGstOffer());
				lendingRiskVariablesSnapshot.setGstAffectedOffer(lendingRiskVariables.getGstAffectedOffer());

				lendingRiskVariablesSnapshotDao.save(lendingRiskVariablesSnapshot);
			}
		} catch (Exception e) {
			logger.error("Exception in createRiskVariablesSnapshot for application:{}", lendingApplication.getId(), e);
		}
	}

	public void createMerchantScoreSnapshot(LendingApplication lendingApplication) {
		try {
//			MerchantScore merchantScore = merchantScoreDao.findByMerchantId(lendingApplication.getMerchantId());
			final MerchantScoreResponseDto merchantScoreResponseDto = merchantScoreHandler.getMerchantScore(lendingApplication.getMerchantId());
			if (ObjectUtils.isEmpty(merchantScoreResponseDto)) {
				throw new MerchantScoreException(lendingApplication.getMerchantId().toString());
			}
			MerchantScoreSnapshot merchantScoreSnapshot = new MerchantScoreSnapshot();
			merchantScoreSnapshot.setApplication_id(lendingApplication.getId());
			merchantScoreSnapshot.setMerchant_id(lendingApplication.getMerchantId());
			merchantScoreSnapshot.setMerchant_store_id(merchantScoreResponseDto.getMerchantStoreId());
			merchantScoreSnapshot.setBusiness_category(merchantScoreResponseDto.getBusinessCategory());
			merchantScoreSnapshot.setVintage(merchantScoreResponseDto.getVintage());
			merchantScoreSnapshot.setVintage_score(merchantScoreResponseDto.getVintageScore());
			merchantScoreSnapshot.setDaily_tpv(merchantScoreResponseDto.getDailyTpv());
			merchantScoreSnapshot.setDaily_tpv_score(merchantScoreResponseDto.getDailyTpvScore());
			merchantScoreSnapshot.setTpv_3_month(merchantScoreResponseDto.getTpv3Month());
			merchantScoreSnapshot.setTpv_3_month_score(merchantScoreResponseDto.getTpv3MonthScore());
			merchantScoreSnapshot.setActive_days(merchantScoreResponseDto.getActiveDays());
			merchantScoreSnapshot.setActive_days_score(merchantScoreResponseDto.getActiveDaysScore());
			merchantScoreSnapshot.setUnique_customer(merchantScoreResponseDto.getUniqueCustomer());
			merchantScoreSnapshot.setUnique_customer_score(merchantScoreResponseDto.getUniqueCustomerScore());
			merchantScoreSnapshot.setTrajectory_score(merchantScoreResponseDto.getTrajectoryScore());
			merchantScoreSnapshot.setFinalScore(merchantScoreResponseDto.getFinalScore());
			merchantScoreSnapshotDao.save(merchantScoreSnapshot);
		} catch (Exception e) {
			logger.error("Exception in createMerchantScoreSnapshot for application:{}", lendingApplication.getId(), e);
		}
	}

	public void createBBSSnapshot(LendingApplication lendingApplication) {
		try {
			LendingBBS lendingBBS = lendingBBSDao.findByMerchantId(lendingApplication.getMerchantId());
			if (lendingBBS != null) {
				LendingBBSSnapshot lendingBBSSnapshot = LendingBBSSnapshot.createObject(lendingBBS);
				lendingBBSSnapshot.setApplicationId(lendingApplication.getId());
				lendingBBSSnapshotDao.save(lendingBBSSnapshot);
			}
		} catch (Exception e) {
			logger.error("Exception in createBBSSnapshot for application:{}", lendingApplication.getId(), e);
		}
	}

	private void createExperianSnapshot(LendingApplication lendingApplication) {
		try {
			Experian experian = experianDao.getByMerchantId(lendingApplication.getMerchantId());
			if (experian != null) {
				ExperianSnapshot experianSnapshot = new ExperianSnapshot();
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
				experianSnapshot.setBureau(experian.getBureau());
				experianSnapshot.setApplicationId(lendingApplication.getId());
				experianSnapshotDao.save(experianSnapshot);
			}
		} catch (Exception e) {
			logger.error("Exception in createExperianSnapshot for application:{}", lendingApplication.getId(), e);
		}
	}

	public void createMerchantSummarySnapshot(LendingApplication application) {
		try {
//			MerchantSummary summary =  merchantSummaryDao.getByMerchantId(merchant.getId());
			MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(application.getMerchantId());
			if (ObjectUtils.isEmpty(merchantResponseDTO)) {
				throw new MerchantSummaryExceptionHandler(application.getMerchantId().toString());
			}
			if (merchantResponseDTO != null) {
				MerchantSummarySnapshot snapshot = new MerchantSummarySnapshot();
				snapshot.setApplication(application.getId());
				snapshot.setMerchantId(application.getMerchantId());
				snapshot.setLastTransactionDate(merchantResponseDTO.getLastTransactionDate());
				snapshot.setTotalTxnCount(merchantResponseDTO.getDailyTxnCount());
				snapshot.setTotalTxnAmount(merchantResponseDTO.getDailyTxnAmount());
				snapshot.setCategory(merchantResponseDTO.getCategory());
				snapshot.setAvgTpv(merchantResponseDTO.getAverageTpv());
				snapshot.setAdjustedTpv(merchantResponseDTO.getAdjustedTpv());
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
				merchantSummarySnapshotDao.save(snapshot);
			}
		} catch (Exception ex) {
			logger.error("Exception in createMerchantSummarySnapshot for application:{}", application.getId(), ex);
		}
	}

	public BankAccountDetails getAccountDetails(Long merchantId) {
		logger.info("Getting bank account details for merchant:{}", merchantId);
		try {
			final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
			BankDetailsDto merchantBankDetail = null;
			if (bankDetailsDtoOptional.isPresent())
				merchantBankDetail = bankDetailsDtoOptional.get();
			if (merchantBankDetail == null) return null;

			return BankAccountDetails.builder()
					.beneficiaryName(merchantBankDetail.getBeneficiaryName())
					.bankName(merchantBankDetail.getBankName())
					.accountNumber("XXXX " + merchantBankDetail.getAccountNumber().substring(merchantBankDetail.getAccountNumber().length() - 4))
					.branchName("")
					.ifsc(merchantBankDetail.getIfsc())
					.bankLogo(merchantBankDetail.getIfscLogo()).build();

		} catch (Exception e) {
			logger.error("Exception in getAccountDetails for merchant:{}", merchantId);
		}
		return null;
	}

	public boolean isBankAccLinked(Long merchantId) {
		final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
		BankDetailsDto merchantBankDetail = null;
		if (bankDetailsDtoOptional.isPresent())
			merchantBankDetail = bankDetailsDtoOptional.get();
		return merchantBankDetail != null;
	}

	public String getBeneficiaryName(Long merchantId) {
		final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
		BankDetailsDto merchantBankDetail = null;
		if (bankDetailsDtoOptional.isPresent())
			merchantBankDetail = bankDetailsDtoOptional.get();
		return merchantBankDetail != null ? merchantBankDetail.getBeneficiaryName() : null;
	}

	public boolean isRepeatLoan(Long merchantId) {
		List<LendingPaymentSchedule> prevLoans = lendingPaymentScheduleDao.findByMerchantIdAndStatusAndCreditLoan(merchantId, "CLOSED", false);
		return !prevLoans.isEmpty();
	}

	public MerchantNachDetailsResponseDTO getSuccessNach(Long merchantId, Long applicationId) {
		MerchantNachDetailsResponseDTO enachSuccess = enachHandler.findSuccessEnach(merchantId, applicationId);
		final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
		BankDetailsDto merchantBankDetail = null;
		if (bankDetailsDtoOptional.isPresent())
			merchantBankDetail = bankDetailsDtoOptional.get();
		if (merchantBankDetail != null && enachSuccess != null && enachSuccess.getAccountNumber() != null && enachSuccess.getAccountNumber().equals(merchantBankDetail.getAccountNumber())) {
			return enachSuccess;
		}
		return null;
	}

	public boolean isDIY(BasicDetailsDto merchant) {
		return (merchant.getMerchantType() != null && "DIY".equals(merchant.getMerchantType())) || merchant.getReferalCode() == null;
	}

	public boolean isLowPriority(Long applicationId) {
		LendingApplicationPriority lendingApplicationPriority = lendingApplicationPriorityDao.findByApplicationId(applicationId);
		return lendingApplicationPriority != null && (lendingApplicationPriority.getCurrentPriority().equals("P4") || lendingApplicationPriority.getCurrentPriority().equals("P5") || lendingApplicationPriority.getCurrentPriority().equals("P6"));
	}

	public boolean isEnachBank(Long merchantId) {
		final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
		BankDetailsDto merchantBankDetail = null;
		if (bankDetailsDtoOptional.isPresent())
			merchantBankDetail = bankDetailsDtoOptional.get();
		if (merchantBankDetail == null) return true;
		LendingNachBankResponseDTO lendingNachBank = enachHandler.findByIfsc(merchantBankDetail.getIfsc().substring(0, 4));
		return lendingNachBank != null;
	}

	public String getEnachBankMode(Long merchantId) {
		final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
		BankDetailsDto merchantBankDetail = null;
		if (bankDetailsDtoOptional.isPresent())
			merchantBankDetail = bankDetailsDtoOptional.get();
		if (merchantBankDetail == null) return null;
		LendingNachBankResponseDTO lendingNachBank = enachHandler.findByIfsc(merchantBankDetail.getIfsc().substring(0, 4));
		logger.info("lendingNachBank for {} : {}", merchantId, lendingNachBank);
		if(!ObjectUtils.isEmpty(lendingNachBank))return lendingNachBank.getMode();
		return null;
	}

	public boolean isNachSkipped(Long merchantId, Long applicationId) {
		BharatPeEnachResponseDTO bharatPeEnach = enachHandler.isSkipped(merchantId, applicationId);
		return bharatPeEnach != null;
	}

	public boolean cpvRequired(LendingApplication lendingApplication) {
		Experian experian = experianDao.getByMerchantId(lendingApplication.getMerchantId());
		boolean enachSuccess = "APPROVED".equalsIgnoreCase(lendingApplication.getNachStatus()) && "ENACH".equalsIgnoreCase(lendingApplication.getNachType());
		if (enachSuccess) {
			if ("BHARAT_SWIPE".equals(lendingApplication.getLoanType()) || "OGL".equals(lendingApplication.getLoanType())) {
				return false;
			}
			boolean isNTC = isNTC(experian);
			if (!isRepeatLoan(lendingApplication.getMerchantId())) {
				if (isNTC && lendingApplication.getLoanAmount() <= 50000D) {
					return false;
				} else if (!isNTC && lendingApplication.getLoanAmount() <= 100000D) {//etc
					return false;
				}
			} else return lendingApplication.getLoanAmount() > 300000D;
		}
		return true;
	}

	public boolean isNTC(Experian experian) {
		if (experian == null || experian.getCategory() == null) {
			return true;
		}
		List<String> ntcCategories = Arrays.asList("1N", "2N", "3N", "4N");
		return ntcCategories.contains(experian.getCategory());
	}

	public List<NachableBanksDTO> getEnachBanks() {
		return enachHandler.getEnachBankList();
	}

	public static Date loanRejectionDate(LendingApplication lendingApplication) {
		Date rejectedTimestamp = null;
		if ("REJECTED".equalsIgnoreCase(lendingApplication.getManualKyc()) && lendingApplication.getKycApprovedDate() != null) {
			rejectedTimestamp = lendingApplication.getKycApprovedDate();
		} else if ("REJECTED".equalsIgnoreCase(lendingApplication.getManualCibil()) && lendingApplication.getCibilApprovedDate() != null) {
			rejectedTimestamp = lendingApplication.getCibilApprovedDate();
		} else if ("REJECTED".equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus()) && lendingApplication.getPhysicalApprovedDate() != null) {
			rejectedTimestamp = lendingApplication.getPhysicalApprovedDate();
		}
		return rejectedTimestamp;
	}

	public int getForeclosureAmount(LendingPaymentSchedule lendingPaymentSchedule) {
		if (lendingPaymentSchedule == null || lendingPaymentSchedule.getStatus().equals("CLOSED")) {
			return 0;
		}
		LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId());
		double advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ? lendingPrepayment.getAdvanceEdiAmount() : 0d;
		return (int) Math.ceil(lendingPaymentSchedule.getLoanAmount() - (lendingPaymentSchedule.getPaidPrinciple() != null ? lendingPaymentSchedule.getPaidPrinciple() : 0) + (lendingPaymentSchedule.getDueInterest() != null ? lendingPaymentSchedule.getDueInterest() : 0) - advanceEdiAmount);
	}

	public void publishApplicationEvent(LendingApplication lendingApplication) {
		try {
			Map<String, Object> request = new HashMap<String, Object>() {{
				put("merchantId", lendingApplication.getMerchantId());
				put("applicationId", lendingApplication.getId());
				put("status", lendingApplication.getStatus());
				put("disbursalStatus", lendingApplication.getLoanDisbursalStatus());
				put("createdAt", simpleDateFormat.format(lendingApplication.getCreatedAt()));
				put("updatedAt", simpleDateFormat.format(lendingApplication.getUpdatedAt()));
			}};
			executorService.execute(() -> {
				kafkaTemplate.send(LendingConstants.APPLICATION_EVENT_TOPIC, lendingApplication.getId().toString(), request);
			});
			logger.info("Lending application event update for applicationId:{}", lendingApplication.getId());
		} catch (Exception e) {
			logger.error("Exception in publishApplicationEvent for application:{}", lendingApplication.getId(), e);
		}
	}

	public void publishDSData(LendingApplication lendingApplication) {
		try {
			logger.info("Publishing DS Data for application:{}", lendingApplication.getId());
			List<LendingShopDocuments> lendingShopDocuments = lendingShopDocumentsDao.findByMerchantIdAndApplicationId(lendingApplication.getMerchantId(), lendingApplication.getId());
			String imageFrontLat = null;
			String imageFrontLng = null;
			String proof_front_side = null;
			String proof_stock_side = null;
			for (LendingShopDocuments lendingShopDocument : lendingShopDocuments) {
				if (lendingShopDocument.getProofType().equalsIgnoreCase("shop-stock")) {
					proof_stock_side = !StringUtils.isEmpty(lendingShopDocument.getProofFrontSide()) ? lendingShopDocument.getProofFrontSide() : null;
				}
				if (lendingShopDocument.getProofType().equalsIgnoreCase("shop-front")) {
					imageFrontLat = !StringUtils.isEmpty(lendingShopDocument.getLatitude()) ? lendingShopDocument.getLatitude() : null;
					imageFrontLng = !StringUtils.isEmpty(lendingShopDocument.getLongitude()) ? lendingShopDocument.getLongitude() : null;
					proof_front_side = !StringUtils.isEmpty(lendingShopDocument.getProofFrontSide()) ? lendingShopDocument.getProofFrontSide() : null;
				}
			}
			Map<String, Object> request = new HashMap<>();
			request.put("merchantId", lendingApplication.getMerchantId());
			request.put("applicationId", lendingApplication.getId());
			request.put("createdAt", simpleDateFormat.format(lendingApplication.getCreatedAt()));
			request.put("updatedAt", simpleDateFormat.format(lendingApplication.getUpdatedAt()));
			request.put("latitude", !StringUtils.isEmpty(lendingApplication.getLatitude()) ? lendingApplication.getLatitude() : null);
			request.put("longitude", !StringUtils.isEmpty(lendingApplication.getLongitude()) ? lendingApplication.getLongitude() : null);
			request.put("image_front_lat", imageFrontLat);
			request.put("image_front_lon", imageFrontLng);
			request.put("pincode", lendingApplication.getPincode());
			request.put("business_name", lendingApplication.getBusinessName());
			request.put("street_address", lendingApplication.getStreetAddress());
			request.put("area", lendingApplication.getArea());
			request.put("shop_number", lendingApplication.getShopNumber());
			request.put("proof_front_side", proof_front_side);
			request.put("proof_stock_side", proof_stock_side);
			executorService.execute(() -> {
				kafkaTemplate.send(LendingConstants.APPLICATION_DS_EVENT_TOPIC, lendingApplication.getId().toString(), request);
			});
		} catch (Exception e) {
			logger.error("Exception while publishing DS Data for application:{}", lendingApplication.getId(), e);
		}
	}

	public int getIoHalfPF(LendingPaymentSchedule lendingPaymentSchedule) {
		int foreclosureAmount = getForeclosureAmount(lendingPaymentSchedule);
		return (int) Math.ceil(foreclosureAmount * 0.05);
	}


	public LoanEligibilityDTO calculateLoanBreakup(GlobalLimitResponse.OfferDetail tenureDetail, Long merchantId, String loanType, Double amount, String offerType, Double version) {

		Integer ediAmount = (int) Math.ceil(((amount + (amount * (tenureDetail.getInterestRate() / 100) * tenureDetail.getTenure()))) / tenureDetail.getEdiCount());
		Integer repayment = Math.round((tenureDetail.getEdiCount() * ediAmount));

		Integer sevenDayEdiAmount = (int) Math.ceil(((amount + (amount * (tenureDetail.getInterestRate() / 100) * tenureDetail.getTenure()))) / (30 * tenureDetail.getTenure()));
		Integer sevenDayRepayment = Math.round((30 * tenureDetail.getTenure() * sevenDayEdiAmount));
		List<EligibleLoan> eligibleLoanList = new ArrayList<>();

		EligibleLoan eligibleLoan = EligibleLoan.builder()
				.loanType(loanType)
				.offerType(offerType)
				.amount(amount)
				.repayment(repayment)
				.rateOfInterest(tenureDetail.getInterestRate())
				.edi(ediAmount)
				.tenure(tenureDetail.getTenure() + " Months")
				.tenureInMonths(tenureDetail.getTenure())
				.merchantId(merchantId)
				.status("ACTIVE")
				.offerType(offerType)
				.ediFreeDays(0)
				.ioEdi(0)
				.ioEdiDays(0)
				.ediCount(tenureDetail.getEdiCount())
				.processingFee((int) Math.ceil(amount * tenureDetail.getProcessingFee()))
				.version(version)
				.clubV2Amount(tenureDetail.getClubV2Amount())
				.processingFeeRate(tenureDetail.getProcessingFee())
				.build();
		EligibleLoan sevenDayEligibleLoanOffer = EligibleLoan.builder()
				.loanType(loanType)
				.offerType(offerType)
				.amount(amount)
				.repayment(sevenDayRepayment)
				.rateOfInterest(tenureDetail.getInterestRate())
				.edi(sevenDayEdiAmount)
				.tenure(tenureDetail.getTenure() + " Months")
				.tenureInMonths(tenureDetail.getTenure())
				.merchantId(merchantId)
				.status("ACTIVE")
				.offerType(offerType)
				.ediFreeDays(0)
				.ioEdi(0)
				.ioEdiDays(0)
				.ediCount(tenureDetail.getTenure() * 30)
				.processingFee((int) Math.ceil(amount * tenureDetail.getProcessingFee()))
				.version(version)
				.clubV2Amount(tenureDetail.getClubV2Amount())
				.processingFeeRate(tenureDetail.getProcessingFee())
				.build();
		eligibleLoanList.add(eligibleLoan);
		eligibleLoanList.add(sevenDayEligibleLoanOffer);
		eligibleLoanDao.saveAll(eligibleLoanList);
		eligibleLoanDao.flush();
		return null;
	}

	public boolean isInternalMerchant(Long merchantId) {
		return BooleanUtils.toBoolean(lendingCache.contains(INTERNAL_MERCHANTS, merchantId));
	}

	public void callingDeForReferences(Long merchantId, LendingApplication lendingApplication) {
		try {
			int ttl = 45;
			Long referencesLimit = loanDetailsServiceV2.getReferenceLimit(lendingApplication);
			Integer toBeShown = loanDetailsServiceV2.getToBeShownReferences(referencesLimit);
			logger.info("async threadpool flow executed for getMerchantReferences.");
			dsHandler.getMerchantReferences(merchantId, minScore, toBeShown, lendingApplication.getId());
			String deReferencesCacheKey = LendingConstants.GET_MERCHANTS_REFERENCES_CACHE_KEY + merchantId;
			cacheDeReferencesData(deReferencesCacheKey, ttl);
			logger.info("successfully caching of merchant references from de completed");
		} catch (Exception e) {
			logger.error("exception occurred while callingDeForReferences {}", e.getMessage());
		}
	}

	private void cacheDeReferencesData(String key, int ttl) {
		try {
			AddCacheDto addCacheDto = new AddCacheDto();
			addCacheDto.setKey(key);
			addCacheDto.setValue(Boolean.TRUE);
			addCacheDto.setTtl(ttl);
			lendingCache.add(addCacheDto, TimeUnit.MINUTES);
			logger.info("caching of merchant references from de completed key: {}", key);
		} catch (Exception e) {
			logger.error("exception occurred while caching merchant References for {},{}", key, e.getMessage());
		}
	}


	public String enachServiceLenderMapper(String lender) {
		String finalLender = null;
		if (ObjectUtils.isEmpty(lender)) {
			return null;
		}
		if (lender.equals("MAMTA0") || lender.equals("MAMTA1") || lender.equals("MAMTA2") || lender.equals("MAMTA")) {
			finalLender = Lender.MAMTA.name();
		}
		if (lender.equals("LIQUILOANS_P2P") || lender.equals("LIQUILOANS")) {
			finalLender = Lender.LIQUILOANS.name();
		}
		if (lender.equals("LIQUILOANS_NBFC")) {
			finalLender = "TRILLIONS";
		}
		if (lender.equals("LIQUILOANS_P2P_OF")) {
			finalLender = Lender.LIQUILOANS_P2P_OF.name();
		}
		if (lender.equals("HINDON")) {
			finalLender = Lender.HINDON.name();
		}
		if (lender.equals("LDC")) {
			finalLender = Lender.LDC.name();
		}
		if (lender.equals("ABFL")) {
			finalLender = Lender.ABFL.name();
		}
		return finalLender;
	}

	public boolean isNachToBeRefunded(LendingApplication lendingApplication) {
		return lendingApplication.getNachLender().equals("BHARATPE");
	}

	public static double roundUp(double loanAmount) {
		if (loanAmount < 20000) {
			return (Math.ceil(loanAmount / 1000.0) * 1000);
		} else if (loanAmount < 100000) {
			return (Math.ceil(loanAmount / 5000.0) * 5000);
		} else {
			return (Math.ceil(loanAmount / 10000.0) * 10000);
		}
	}

	public Boolean isEligibleForNachSkip(LendingApplication lendingApplication, String lender) {

		if (ObjectUtils.isEmpty(lender)) return false;

		if ("SMALL_TICKET".equals(lendingApplication.getLoanType())) {
			setIsNachSkip(lendingApplication);
			return Boolean.TRUE;
		}
		MerchantNachDetailsResponseDTO approvedNachDetails = enachHandler.findByMerchantIdAndLender(lendingApplication.getMerchantId(), enachServiceLenderMapper(lender));


		if (ObjectUtils.isEmpty(approvedNachDetails)) {
			return Boolean.FALSE;
		}

		if (approvedNachDetails.getNachAmount() >= lendingApplication.getLoanAmount()) {
			setIsNachSkip(lendingApplication);
			return Boolean.TRUE;
		}

		//Todo: remove this condition after derog after merchants cases are over
		List<Long> derogMerchants = loadDerogEffectedMerchants();
		if (derogMerchants.contains(lendingApplication.getMerchantId()) && derogTopUpEnable(lendingApplication.getMerchantId())) {
			setIsNachSkip(lendingApplication);
			return Boolean.TRUE;
		}
		if (skipNachForRepeatLoans(lendingApplication, approvedNachDetails)) {
			setIsNachSkip(lendingApplication);
			return Boolean.TRUE;
		}

		if (skipNachForTopUpLoans(lendingApplication, approvedNachDetails)) {
			setIsNachSkip(lendingApplication);
			return Boolean.TRUE;
		}

		return Boolean.FALSE;
	}

	public void setIsNachSkip(LendingApplication lendingApplication){
		LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
		if (!ObjectUtils.isEmpty(lendingApplicationDetails)) {
			lendingApplicationDetails.setIsNachSkip(Boolean.TRUE);
			if(!ObjectUtils.isEmpty(lendingApplication.getAgreementAt())
			&& ObjectUtils.isEmpty(lendingApplicationDetails.getLeadAcceptanceTime()))lendingApplicationDetails.setLeadAcceptanceTime(lendingApplication.getAgreementAt());
			lendingApplicationDetailsDao.save(lendingApplicationDetails);
		}
	}

	public MerchantNachDetailsResponseDTO getSuccessNach(Long merchantId, String lender) {
		MerchantNachDetailsResponseDTO enachSuccess = enachHandler.findByMerchantIdAndLender(merchantId, enachServiceLenderMapper(lender));
		final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
		BankDetailsDto merchantBankDetail = null;
		if (bankDetailsDtoOptional.isPresent())
			merchantBankDetail = bankDetailsDtoOptional.get();
		if (merchantBankDetail != null && enachSuccess != null && enachSuccess.getAccountNumber() != null && enachSuccess.getAccountNumber().equals(merchantBankDetail.getAccountNumber())) {
			return enachSuccess;
		}
		return null;
	}


	public Boolean putApplicationInResignAndRenach(LendingApplication lendingApplication, String newLender) {
		logger.info("putting application in resgin and renach for applicationId : {} and newLender : {}", lendingApplication.getId(), newLender);
		try {
			LendingResubmitTask resubmitTask = lendingResubmitTaskDao.findTopByApplicationIdAndMerchantId(lendingApplication.getId()
					, lendingApplication.getMerchantId());
			// if already a re-sign request exists then return success
			if (Objects.nonNull(resubmitTask) && Objects.nonNull(resubmitTask.getResign()) && resubmitTask.getResign() &&
					Objects.nonNull(resubmitTask.getResignDone()) && !resubmitTask.getResignDone()) {
				logger.info("Already a re-sign task exists for the applicationId : {}", lendingApplication.getId());
				return true;
			}
			if (Objects.isNull(resubmitTask)) {
				resubmitTask = new LendingResubmitTask();
				resubmitTask.setMerchantId(lendingApplication.getMerchantId());
				resubmitTask.setApplicationId(lendingApplication.getId());
			}
			resubmitTask.setResign(Boolean.TRUE);
			resubmitTask.setResignDone(Boolean.FALSE);
			resubmitTask.setResignReason("LENDER_CHANGE_FROM_" + lendingApplication.getLender() + "_TO_" + newLender);
			Boolean renach = apiGatewayService.cancelEnach(lendingApplication.getMerchantId(), lendingApplication.getId());
			if (!renach) return false;

			logger.info("changing lender from : {} to {}", lendingApplication.getLender(), newLender);
			lendingApplication.setLender(newLender);
			lendingApplication.setNachStatus(null);
			lendingApplication.setNachLender(null);
			lendingApplication.setStatus("pending_verification");
			lendingApplicationDao.save(lendingApplication);
			lendingResubmitTaskDao.save(resubmitTask);
			return true;
		} catch (Exception e) {
			logger.error("exception in updating lender for {}, {}", lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
		}
		return false;
	}

	public Boolean skipNachForRepeatLoans(LendingApplication lendingApplication, MerchantNachDetailsResponseDTO approvedNachDetails) {

		logger.info("Checking for nach Waiver:{}", lendingApplication.getId());

		Long merchantId = lendingApplication.getMerchantId();
		try {
			LendingPaymentSchedule lastLoan =
					lendingPaymentScheduleDao.findTop1ByMerchantIdAndStatusAndCreditLoanOrderByIdDesc(merchantId, "CLOSED", false);
			LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
			Integer maxDpd = getMaxDpdInLastLoan(merchantId, lastLoan);
			String riskSegment=null;
			String riskGroup=null;
			if(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)){
				logger.info("No Snapshot for application:{}", lendingApplication.getId());
				LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingApplication.getMerchantId());
				riskSegment=lendingRiskVariables.getRiskSegment();
				riskGroup=lendingRiskVariables.getRiskGroup();
			}
			riskSegment=ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)?riskSegment:lendingRiskVariablesSnapshot.getRiskSegment().name();
			riskGroup=ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)?riskGroup:lendingRiskVariablesSnapshot.getRiskGroup();
			logger.info("recieved risk group in lending risk variable snapshot: {} and maxDpd: {} and riskSegment:{}", riskGroup, maxDpd, riskSegment);

			if (ObjectUtils.isEmpty(lastLoan)) {
				return Boolean.FALSE;
			}

			if (!"REPEAT".equalsIgnoreCase(riskSegment)) {
				return Boolean.FALSE;
			}

			Double loanApplicationAmountTolastLoanAmountRatio = lendingApplication.getLoanAmount() / approvedNachDetails.getNachAmount();

			logger.info("received risk group in lending risk variable snapshot: {} and riskSegment:{} and topupLoanToActiveLoanAmountRatio : {} for applicationId : {}", riskGroup, riskSegment, loanApplicationAmountTolastLoanAmountRatio ,lendingApplication.getId());

			if (Arrays.asList("R1", "R2", "R3").contains(riskGroup) && loanApplicationAmountTolastLoanAmountRatio <= 1.5) {
				return Boolean.TRUE;
			}

			Double settlementAmount = lendingLedgerDao.findSettlementAmount(lastLoan.getId());
			double qrPaidRatio = (settlementAmount / lastLoan.getPaidAmount()) * 100;

			Integer ediPaidCount = lendingLedgerDao.findLedgerCountOnAmountGreaterThanEdiAmount(lastLoan.getId(), lastLoan.getEdiAmount());
			int paidCount = lastLoan.getEdiCount() - lastLoan.getEdiRemainingCount();
			logger.info("ediPaidCount:{} and paidCount:{} for merchant:{}", ediPaidCount, paidCount, lastLoan.getMerchantId());
			double ediPaidRatio = (ediPaidCount * 1.0 / paidCount) * 100;

//			if (isInternalMerchant(merchantId) && allowedRiskGroupsNachWaiver.contains(riskGroup)) {
//				logger.info("Nach Waiver is true for merchant:{}", merchantId);
//				return Boolean.TRUE;
//			}

			if (qrPaidRatio > 80 && ediPaidRatio > 65 && allowedRiskGroupsNachWaiver.contains(riskGroup)
					&& maxDpd <= 10) {
				logger.info("nach waiver is true for merchant:{}", merchantId);
				return Boolean.TRUE;
			}

		} catch (Exception e) {
			logger.error("Exception while check nach waiver for merchant:{} {} {}", merchantId, e.getMessage(), Arrays.asList(e.getStackTrace()));
		}
		return Boolean.FALSE;
	}

	public Boolean skipNachForTopUpLoans(LendingApplication lendingApplication, MerchantNachDetailsResponseDTO approvedNachDetails) {

		logger.info("Checking for nach Waiver on topup application:{}", lendingApplication.getId());

		Long merchantId = lendingApplication.getMerchantId();
		try {
			if (!"TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())) {
				return Boolean.FALSE;
			}

			if (!ObjectUtils.isEmpty(approvedNachDetails) && !approvedNachDetails.getNachLender().equals(enachServiceLenderMapper(lendingApplication.getLender()))) {
				logger.info("approvedNachDetails:{} and are not same as the required nachLender for applicationId : {}", approvedNachDetails.getNachLender(), lendingApplication.getId());
				return Boolean.FALSE;
			}

			LendingPaymentSchedule lastLoan =
			lendingPaymentScheduleDao.findTop1ByMerchantIdAndStatusAndCreditLoanOrderByIdDesc(merchantId, "ACTIVE", false);
			LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
			String riskSegment=null;
			String riskGroup=null;
			if(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)){
				logger.info("No Snapshot for application:{}", lendingApplication.getId());
				LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingApplication.getMerchantId());
				riskSegment=lendingRiskVariables.getRiskSegment();
				riskGroup=lendingRiskVariables.getRiskGroup();
			}
			riskSegment=ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)?riskSegment:lendingRiskVariablesSnapshot.getRiskSegment().name();
			riskGroup=ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)?riskGroup:lendingRiskVariablesSnapshot.getRiskGroup();


			if (ObjectUtils.isEmpty(lastLoan)) {
				logger.info("no lastLoan found for applicationId : {}", lendingApplication.getId());
				return Boolean.FALSE;
			}

			Double topupLoanAmountToActiveLoanAmountRatio = lendingApplication.getLoanAmount() / approvedNachDetails.getNachAmount();

			logger.info("recieved risk group in lending risk variable snapshot: {} and riskSegment:{} and topupLoanToActiveLoanAmountRatio : {} for applicationId : {}", riskGroup, riskSegment, topupLoanAmountToActiveLoanAmountRatio ,lendingApplication.getId());

			if (Arrays.asList("R1", "R2", "R3").contains(riskGroup) && topupLoanAmountToActiveLoanAmountRatio <= 1.5) {
				return Boolean.TRUE;
			}

		} catch (Exception e) {
			logger.error("Exception while check nach waiver for merchant:{} {} {}", merchantId, e.getMessage(), Arrays.asList(e.getStackTrace()));
		}
		return Boolean.FALSE;
	}


	public Integer getMaxDpdInLastLoan(Long merchantId, LendingPaymentSchedule lastLoan) {
		try {

			if (ObjectUtils.isEmpty(lastLoan)) {
				return 0;
			}
			return loanDpdDao.findMaxDpd(lastLoan.getId()).intValue();
		} catch (Exception e) {
			logger.error("Exception while checking max DPD for merchant:{}", merchantId, e);
		}
		return 0;
	}

	public Double calculateLatLonDistance(Double lat1, Double lon1 , Double lat2, Double lon2){
		logger.info("lat1:{} | lon1:{} | lat2:{} | lon2:{}", lat1, lon1, lat2, lon2);
		if(lat1 == -1 || lat2 == -1 || lon1 == -1 || lon2 == -1){
			return -1D;
		}else if (lat1 == 0D || lat2 == 0D || lon1 == 0D || lon2 == 0D){
			return  -1D;
		}

		int r = 6371;
		double x = Math.pow(Math.sin(Math.toRadians(lat2-lat1)/2), 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(Math.toRadians(lon2-lon1))/2,2);
		double result = r * (2 * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x))) * 1000;
		if(result<3000000){
			int n = 2;
			return Math.round(result * Math.pow(10, n)) / Math.pow(10, n);
		} else return -1D;
	}

	private List<Long> readCsvFile() {
		List<Long> merchantList = new ArrayList<>();
		try {

			String filePath = "/MerchantList/derog_merchant";
			InputStream inputStream = this.getClass().getResourceAsStream(filePath);
			Scanner sc = new Scanner(inputStream);
			sc.useDelimiter(",");
			while (sc.hasNext())
			{
				String value = sc.next();
				merchantList.add(Long.parseLong(value));
			}
			sc.close();  //closes the scanner
		} catch (Exception e) {
			logger.info("exception while reading derog csv file: {} {}", e, e.getMessage());
		}
		return merchantList;
	}

}

