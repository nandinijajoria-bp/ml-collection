package com.bharatpe.lending.util;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.service.MongoPublisher;
import com.bharatpe.common.utils.CurrencyUtils;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.PincodeColor;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.BPEnachDao;
import com.bharatpe.lending.dao.BankListDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.LabelDTO;
import com.bharatpe.lending.dto.MerchantSmsAnalysis;
import com.bharatpe.lending.dto.SelectedLoanDTO;
import com.bharatpe.lending.dto.ShopDetailsDTO;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.bharatpe.lending.service.APIGatewayService;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@Component
public class LoanUtil {
	private static final Logger logger = LoggerFactory.getLogger(LoanUtil.class);

	@Autowired
	MongoPublisher mongoPublisher;

	@Autowired
	LendingCovidCitiesDao lendingCovidCitiesDao;

	@Autowired
	PincodeCityStateMappingDao pincodeCityStateMappingDao;

	@Autowired
	LendingApplicationPriorityDao lendingApplicationPriorityDao;

	@Autowired
	MerchantBankDetailDao merchantBankDetailDao;

	@Autowired
	LendingPennydropDao lendingPennydropDao;

	@Autowired
	APIGatewayService apiGatewayService;

	@Autowired
	LendingPaymentScheduleDao lendingPaymentScheduleDao;

	@Autowired
	BPEnachDao bpEnachDao;

	@Autowired
	LendingPincodesDao lendingPincodesDao;

	@Autowired
	MerchantSummaryDao merchantSummaryDao;

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

	@Autowired
	MerchantScoreDao merchantScoreDao;

	@Autowired
	MerchantScoreSnapshotDao merchantScoreSnapshotDao;

	@Autowired
	IfscDao ifscDao;

	@Autowired
	BankListDao bankListDao;

	@Autowired
	LendingNachBankDao lendingNachBankDao;

	@Autowired
	BharatPeEnachDao bharatPeEnachDao;

	@Autowired
    LendingPrepaymentDao lendingPrepaymentDao;

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    LendingShopDocumentsDao lendingShopDocumentsDao;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

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
		selectedLoan.put("installment_details", prepareLabels(application, lendingCategories != null ? lendingCategories.getIoTenureMonths().intValue() : 0));
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
			shopDetails.put("shopType",lendingGstDetail.getShopType() != null ? lendingGstDetail.getShopType() : "");
		}
		return shopDetails;
	}

	public static ShopDetailsDTO prepareShopDetailsDTO(LendingApplication application,LendingGstDetail lendingGstDetail) {
		ShopDetailsDTO shopDetails = new ShopDetailsDTO();

		shopDetails.setBusinessName(application.getBusinessName());
		shopDetails.setShopNumber(application.getShopNumber());
		shopDetails.setStreetAddress(application.getStreetAddress());
		shopDetails.setArea(application.getArea());
		shopDetails.setLandmark(application.getLandmark());
		if(application.getPincode() != null) {
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
	public static ShopDetailsDTO prepareShopDetailsDTO(CreditApplication application, CreditApplicationAddress creditApplicationAddress){

		ShopDetailsDTO shopDetails = new ShopDetailsDTO();
		if(creditApplicationAddress!=null) {
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
		}
		else {
			logger.warn("Shop details not available for application {}",application.getId());
		}
		return shopDetails;
	}

	private static List<LabelDTO> prepareLabels(LendingApplication application, int months) {
		List<LabelDTO> list = new ArrayList<>();

		if("CONSTRUCT_1".equals(application.getLoanConstruct())) {

		} else if("CONSTRUCT_2".equals(application.getLoanConstruct())) {
			list.add(new LabelDTO("EDI for 1st Month", "ZERO"));
			list.add(new LabelDTO("EDI for Next " + (application.getTenureInMonths() - 1) + " Months", "₹" + CurrencyUtils.formatInt(application.getEdi().intValue()) + "/day"));
			list.add(new LabelDTO("No Deduction on Sundays", ""));
		} else if("CONSTRUCT_3".equals(application.getLoanConstruct())) {
			if (months > 1) {
				list.add(new LabelDTO("EDI for 1st "+months+" Months", "₹" + CurrencyUtils.formatInt(application.getIoEdi().intValue()) + "/day"));
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

	public static Map<String , String> settlementMode = new HashMap<String , String>() {{
		put("SETTLEMENT", "QR Txns.");
		put("EXTERNAL_NACH", "NACH");
		put("INTEREST_ACCOUNT", "Investment A/c");
		put("REFUND", "Refund");
		put("BHARATPE_NACH", "NACH");
		put("UPI","UPI");
		put("SCHEME2","Waiver");
		put("SCHEME1","Waiver");
		put("SCHEME3","Waiver");
		put("FP","Investment A/c");
		put("UNSETTLED","QR Txns.");
		put("DIRECT_TRANSFER","Offline");
		put("EXCEPTION","Offline");
		put("QR_SETTLEMENT","QR Txns.");
		put("DC","Debit Card");
		put("NB","Net Banking");
		put("CC","Credit Card");
		put("BP","BP Balance");
		put("BT","Bank Transfer");
		put("AUTO_FP","Investment A/c");
		put("ADVANCE_EDI","Advance EDI");
	}};

	public static List<JsonNode> jsonNodeArrayUtil(JsonNode nodeData){
        List<JsonNode> resp = new ArrayList<>();
        if(nodeData != null && !nodeData.asText().equals("\"\"")){
            if(nodeData.isObject()){
                resp.add(nodeData);
            } else {
                for(JsonNode node: nodeData){
                    resp.add(node);
                }
            }
        }
        return resp;
    }

	public static int getEdiDays(int tenure){
		switch (tenure){
			case 1: return 26;
			case 3: return 77;
			case 6: return 155;
			case 9: return 234;
			case 12: return 311;
			default: return 388;//15 months
		}
	}

	public void auditExperian(Experian experian) {
		if (experian == null) {
			return;
		}
		try {
			ExperianAuditTrail experianAuditTrail = ExperianAuditTrail.createObject(experian);
			experianAuditTrail.setId(System.nanoTime());
			mongoPublisher.publish("Lending", "experian_audit_trail", experianAuditTrail.getMerchantId().toString(), new ArrayList<ExperianAuditTrail>(){{add(experianAuditTrail);}});
		} catch (Exception e) {
			logger.error("Exception in mongo publish", e);
		}
	}

	public static String getRandomNumberString() {
		Random rnd = new Random();
		int number = rnd.nextInt(999);
		return String.format("%03d", number);
	}

	public boolean isCpvCity(Integer pincode) {
		if (pincode == null) {
			return false;
		}
		PincodeCityStateMapping pincodeCityStateMapping = pincodeCityStateMappingDao.findByPincode(pincode);
		return pincodeCityStateMapping != null && LendingConstants.CPV_CITIES.contains(pincodeCityStateMapping.getCity());
	}

	public int getApplicationTAT(Long applicationId) {
		int tat = -1;
		LendingApplicationPriority lendingApplicationPriority = lendingApplicationPriorityDao.findByApplicationId(applicationId);
		if (lendingApplicationPriority != null && lendingApplicationPriority.getTat() != null && lendingApplicationPriority.getTatStartTime() != null) {
			tat = (int)(lendingApplicationPriority.getTat() - (getDateDiffInDays(lendingApplicationPriority.getTatStartTime(), new Date())));
		}
		return tat;
	}

	public static String getFirstName(String name){
		if (name == null) {
			return "";
		}
		int lastIndexOfSpace = name.lastIndexOf(" ");
		if (lastIndexOfSpace != -1){
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

	public static String getLastName(String name){
		if (name == null) {
			return "";
		}
		int lastIndexOfSpace = name.lastIndexOf(" ");
		if (lastIndexOfSpace != -1){
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

	public Boolean isCovidCities(Integer pinCode){
		if(pinCode == null){
			return false;
		}
		LendingCovidCities covidCities = lendingCovidCitiesDao.findByPincode(pinCode);
		return covidCities != null;
	}

	public void publishSmsAnalysisData(Merchant merchant) {
		if (merchant == null) {
			return;
		}
		try {
			logger.info("Publish merchant_sms_analysis data in mongo for merchant:{}", merchant.getId());
			MerchantSmsAnalysis merchantSmsAnalysis = new MerchantSmsAnalysis(merchant.getMid());
			mongoPublisher.publish("Lending", "merchant_sms_analysis", merchant.getId().toString(), new ArrayList<MerchantSmsAnalysis>(){{add(merchantSmsAnalysis);}});
		} catch (Exception e) {
			logger.error("Exception in mongo publish merchant_sms_analysis for merchant:{}", merchant.getId(), e);
		}
	}

	public boolean checkPennyDrop(Merchant merchant) {
		try {
			logger.info("Checking penny drop for merchant:{}", merchant.getId());
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
//			Integer settlement = settlementDao.getCountSettlementLast15Days(merchant.getId(), merchantBankDetail.getAccountNumber());
//			if (settlement > 0) {
//				logger.info("Penny drop success for merchant:{}", merchant.getId());
//				return true;
//			}
			LendingPennydrop successPennyDrop = lendingPennydropDao.isSuccess(merchant.getId(), merchantBankDetail.getAccountNumber());
			if (successPennyDrop != null) {
				logger.info("Penny drop success for merchant:{}", merchant.getId());
				return true;
			}
			LendingPennydrop failedPennyDrop = lendingPennydropDao.isFailed(merchant.getId(), merchantBankDetail.getAccountNumber());
			if (failedPennyDrop != null) {
				logger.info("Penny drop failed for merchant:{}", merchant.getId());
				return false;
			}
			return apiGatewayService.checkPennyDrop(merchantBankDetail, merchant);
		} catch (Exception e) {
			logger.error("Exception in penny drop for merchant:{}", merchant.getId(), e);
		}
		return false;
	}

	public static int calculateDPD(Double ediAmount, Double dueAmount) {
		if (dueAmount < ediAmount) return 0;
		return (int)Math.round(dueAmount/ediAmount);
	}

	public boolean hasActiveLoan(Merchant merchant) {
		LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchant.getId(), "ACTIVE");
		return activeLoan != null;
	}

	public boolean isEnachDone(Merchant merchant) {
		boolean enachDone = false;
		BpEnach enachSuccess = bpEnachDao.findSuccessEnach(merchant.getId());
		MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
		if (merchantBankDetail != null && enachSuccess != null && enachSuccess.getAccountNumber() != null && enachSuccess.getAccountNumber().equals(merchantBankDetail.getAccountNumber())) {
			enachDone = true;
		}
		return enachDone;
	}

	public boolean isOGL(Integer pincode) {
		if (pincode == null) return false;
		PincodeCityStateMapping pincodeCityStateMapping = pincodeCityStateMappingDao.findByPincode(pincode);
		if (pincodeCityStateMapping == null) return true;
		LendingPincodes lendingPincodes = lendingPincodesDao.findByPincode(pincode);
		return lendingPincodes == null || lendingPincodes.getColor().equals(PincodeColor.RED);
	}

	public List<LendingPaymentSchedule> getPreviousLoans(Long merchantId) {
		return lendingPaymentScheduleDao.findByMerchantIdAndCreditLoanOrderByIdDesc(merchantId, false);
	}

	public void createApplicationSnapshot(LendingApplication lendingApplication) {
		logger.info("Creating snapshots for application:{}", lendingApplication.getId());
		createMerchantSummarySnapshot(lendingApplication.getMerchant(), lendingApplication);
		createExperianSnapshot(lendingApplication.getMerchant(), lendingApplication);
		createBBSSnapshot(lendingApplication);
		createMerchantScoreSnapshot(lendingApplication);
		createRiskVariablesSnapshot(lendingApplication);
	}

    private void createRiskVariablesSnapshot(LendingApplication lendingApplication) {
        try {
            LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(lendingApplication.getMerchant().getId());
            if (lendingRiskVariables != null) {
                LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = LendingRiskVariablesSnapshot.createObject(lendingRiskVariables);
                lendingRiskVariablesSnapshot.setApplicationId(lendingApplication.getId());
                lendingRiskVariablesSnapshotDao.save(lendingRiskVariablesSnapshot);
            }
        } catch (Exception e) {
            logger.error("Exception in createRiskVariablesSnapshot for application:{}", lendingApplication.getId(), e);
        }
    }

    public void createMerchantScoreSnapshot(LendingApplication lendingApplication) {
		try {
			MerchantScore merchantScore = merchantScoreDao.findByMerchantId(lendingApplication.getMerchant().getId());
			if (merchantScore != null) {
				MerchantScoreSnapshot merchantScoreSnapshot = MerchantScoreSnapshot.createObject(merchantScore);
				merchantScoreSnapshot.setApplication_id(lendingApplication.getId());
				merchantScoreSnapshotDao.save(merchantScoreSnapshot);
			}
		} catch (Exception e) {
			logger.error("Exception in createMerchantScoreSnapshot for application:{}", lendingApplication.getId(), e);
		}
	}

	public void createBBSSnapshot(LendingApplication lendingApplication) {
		try {
			LendingBBS lendingBBS = lendingBBSDao.findByMerchantId(lendingApplication.getMerchant().getId());
			if (lendingBBS != null) {
				LendingBBSSnapshot lendingBBSSnapshot = LendingBBSSnapshot.createObject(lendingBBS);
				lendingBBSSnapshot.setApplicationId(lendingApplication.getId());
				lendingBBSSnapshotDao.save(lendingBBSSnapshot);
			}
		} catch (Exception e) {
			logger.error("Exception in createBBSSnapshot for application:{}", lendingApplication.getId(), e);
		}
	}

	private void createExperianSnapshot(Merchant merchant,LendingApplication lendingApplication) {
		try {
			Experian experian = experianDao.getByMerchantId(merchant.getId());
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

	public void createMerchantSummarySnapshot(Merchant merchant, LendingApplication application) {
		try {
			MerchantSummary summary =  merchantSummaryDao.getByMerchantId(merchant.getId());
			if (summary != null) {
				MerchantSummarySnapshot snapshot = new MerchantSummarySnapshot();
				snapshot.setApplication(application.getId());
				snapshot.setMerchant(merchant);
				snapshot.setLastTransactionDate(summary.getLastTransactionDate());
				snapshot.setTotalTxnCount(summary.getDailyTxnCount());
				snapshot.setTotalTxnAmount(summary.getDailyTxnAmount());
				snapshot.setCategory(summary.getCategory());
				snapshot.setAvgTpv(summary.getAvgTpv());
				snapshot.setAdjustedTpv(summary.getAdjustedTpv());
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
				snapshot.setUniqueCustomer1mon(summary.getUniqueCustomer1mon());
				merchantSummarySnapshotDao.save(snapshot);
			}
		} catch(Exception ex) {
			logger.error("Exception in createMerchantSummarySnapshot for application:{}", application.getId(), ex);
		}
	}

	public BankAccountDetails getAccountDetails(Long merchantId) {
		logger.info("Getting bank account details for merchant:{}", merchantId);
		try {
			MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchantId, "ACTIVE");
			if (merchantBankDetail == null) return null;
			Ifsc ifsc = ifscDao.findTop1ByIfscOrderByIdDesc(merchantBankDetail.getIfscCode());
			if (ifsc != null) {
				BankList bankList = bankListDao.findByBankCode(ifsc.getBankCode());
				return BankAccountDetails.builder()
						.beneficiaryName(merchantBankDetail.getBeneficiaryName())
						.bankName(ifsc.getBank())
						.accountNumber("XXXX " + merchantBankDetail.getAccountNumber().substring(merchantBankDetail.getAccountNumber().length()-4))
						.branchName(ifsc.getBranch())
						.ifsc(merchantBankDetail.getIfscCode())
						.bankLogo(bankList != null ? bankList.getImageUrl() : null).build();
			}
		} catch (Exception e) {
			logger.error("Exception in getAccountDetails for merchant:{}", merchantId);
		}
		return null;
	}

	public boolean isBankAccLinked(Long merchantId) {
		MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchantId, "ACTIVE");
		return merchantBankDetail != null;
	}

	public String getBeneficiaryName(Long merchantId) {
		MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchantId, "ACTIVE");
		return merchantBankDetail != null ? merchantBankDetail.getBeneficiaryName() : null;
	}

	public boolean isRepeatLoan(Long merchantId) {
		List<LendingPaymentSchedule> prevLoans = lendingPaymentScheduleDao.findByMerchantIdAndStatusAndCreditLoan(merchantId,"CLOSED", false);
		return !prevLoans.isEmpty();
	}

	public BpEnach getSuccessNach(Merchant merchant) {
		BpEnach enachSuccess = bpEnachDao.findSuccessEnach(merchant.getId());
		MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchant.getId(), "ACTIVE");
		if (merchantBankDetail != null && enachSuccess != null && enachSuccess.getAccountNumber() != null && enachSuccess.getAccountNumber().equals(merchantBankDetail.getAccountNumber())) {
			return enachSuccess;
		}
		return null;
	}

	public boolean isDIY(Merchant merchant) {
		return (merchant.getMerchantType() != null && "DIY".equals(merchant.getMerchantType())) || merchant.getReferalCode() == null;
	}

	public boolean isLowPriority(Long applicationId) {
		LendingApplicationPriority lendingApplicationPriority = lendingApplicationPriorityDao.findByApplicationId(applicationId);
		return lendingApplicationPriority != null && (lendingApplicationPriority.getCurrentPriority().equals("P4") || lendingApplicationPriority.getCurrentPriority().equals("P5") || lendingApplicationPriority.getCurrentPriority().equals("P6"));
	}

	public boolean isEnachBank(Long merchantId) {
		MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchantId, "ACTIVE");
		if (merchantBankDetail == null) return true;
		LendingNachBank lendingNachBank = lendingNachBankDao.findByIfscAndMode(merchantBankDetail.getIfscCode().substring(0, 4));
		return lendingNachBank != null;
	}

	public boolean isNachSkipped(Long merchantId, Long applicationId) {
		BharatPeEnach bharatPeEnach =  bharatPeEnachDao.isSkipped(merchantId, applicationId);
		return bharatPeEnach != null;
	}

	public boolean cpvRequired(LendingApplication lendingApplication) {
		Experian experian = experianDao.getByMerchantId(lendingApplication.getMerchant().getId());
		boolean enachSuccess = "APPROVED".equalsIgnoreCase(lendingApplication.getNachStatus()) && "ENACH".equalsIgnoreCase(lendingApplication.getNachType());
		if(enachSuccess) {
			if ("BHARAT_SWIPE".equals(lendingApplication.getLoanType()) || "OGL".equals(lendingApplication.getLoanType())) {
				return false;
			}
			boolean isNTC = isNTC(experian);
			if(!isRepeatLoan(lendingApplication.getMerchant().getId())) {
				if(isNTC && lendingApplication.getLoanAmount() <= 50000D) {
					return false;
				} else if(!isNTC && lendingApplication.getLoanAmount() <= 100000D) {//etc
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
		List<String> ntcCategories = Arrays.asList("1N","2N","3N","4N");
		return ntcCategories.contains(experian.getCategory());
	}

	public List<BankList> getEnachBanks() {
		return bankListDao.findNachBankList();
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
        LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(lendingPaymentSchedule.getMerchant().getId(), lendingPaymentSchedule.getId());
        double advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ? lendingPrepayment.getAdvanceEdiAmount() : 0d;
		return (int) Math.ceil(lendingPaymentSchedule.getLoanAmount() - (lendingPaymentSchedule.getPaidPrinciple() != null ? lendingPaymentSchedule.getPaidPrinciple() : 0) + (lendingPaymentSchedule.getDueInterest() != null ? lendingPaymentSchedule.getDueInterest() : 0) - advanceEdiAmount);
	}

	public void publishApplicationEvent(LendingApplication lendingApplication) {
	    try {
            Map<String,Object> request = new HashMap<String, Object>(){{
                put("merchantId", lendingApplication.getMerchant().getId());
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
            List<LendingShopDocuments> lendingShopDocuments = lendingShopDocumentsDao.findByMerchantIdAndApplicationId(lendingApplication.getMerchant().getId(), lendingApplication.getId());
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
            request.put("merchantId", lendingApplication.getMerchant().getId());
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
}
