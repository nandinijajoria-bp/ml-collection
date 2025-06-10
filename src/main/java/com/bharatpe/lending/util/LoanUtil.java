package com.bharatpe.lending.util;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.MerchantScoreSnapshotDao;
import com.bharatpe.common.dao.MerchantSummarySnapshotDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.utils.CurrencyUtils;
import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.query.dao.ForeClosureConfigDao;
import com.bharatpe.lending.common.query.dao.PenaltyFeeConfigDaoSlave;
import com.bharatpe.lending.common.query.entity.ForeClosureConfig;
import com.bharatpe.lending.common.query.entity.LendingApplicationSlave;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.query.entity.PenaltyFeeConfigSlave;
import com.bharatpe.lending.common.service.MongoLogPublisher;
import com.bharatpe.lending.common.service.PennyDropService;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.PincodeCityStateMappingDTO;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;

import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.*;

import com.bharatpe.lending.entity.LmsStageHistory;
import com.bharatpe.lending.entity.NachMandateEligibilityConfig;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.enums.EnachMode;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.DsHandler;
import com.bharatpe.lending.handlers.LaunchLabsHandler;
import com.bharatpe.lending.handlers.MerchantScoreException;
import com.bharatpe.lending.handlers.MerchantScoreHandler;
import com.bharatpe.lending.handlers.MerchantSummaryExceptionHandler;
import com.bharatpe.lending.loanV2.dto.BankAccountDetails;
import com.bharatpe.lending.loanV2.service.ExcessNachService;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.revamp.dto.EnachModeDTO;
import com.bharatpe.lending.loanV3.revamp.enums.LendingViewStates;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.loanV3.revamp.util.DateUtils;
import com.bharatpe.lending.loanV3.revamp.util.LoanUtilV3;
import com.bharatpe.lending.loanV3.services.gateway.NbfcLenderGateway;
import com.bharatpe.lending.loanV3.services.associations.AbflForeclosureFetchService;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.NachBounceChargesService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;


import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.bharatpe.lending.common.enums.PerpetualDpdAdjusted.Y;
import static com.bharatpe.lending.constant.LendingConstants.PENNYDROP_LOCK_PREFIX;
import static com.bharatpe.lending.enums.Lender.*;
import static com.bharatpe.lending.loanV3.enums.piramal.PaymentTypePiramal.LPC_WO_GST;
import static com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant.*;


@Component
public class LoanUtil {
	private static final Logger logger = LoggerFactory.getLogger(LoanUtil.class);
	public static final int NO_OF_DAYS_IN_A_MONTH = 30;
	public static final int COOL_OFF_PERIOD_DAYS = 3;
	public static final String CLOSURE = "ANY";
	private static final String RECEIVABLE = "RECEIVABLE";
	private static final Set<String> FORECLOSURE_COOLING_OFF_SUPPORTED_LENDER = new HashSet<>(Arrays.asList(Lender.PAYU.name(), Lender.OXYZO.name(), Lender.ABFL.name(), PIRAMAL.name()));
	private static final Set<String> FORECLOSURE_CHARGES_SUPPORTED_LENDER = new HashSet<>(Arrays.asList(Lender.ABFL.name(), PIRAMAL.name()));
	public static final Set<String> LENDER_FORECLOSURE_DATE_CHECK = new HashSet<>(Arrays.asList(Lender.ABFL.name(), Lender.PIRAMAL.name()));

	@Autowired
	MongoLogPublisher mongoLogPublisher;

	@Autowired
	BQPublisherUtil bqPublisherUtil;

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
	PennyDropService pennyDropService;

	@Autowired
	APIGatewayService apiGatewayService;

	@Value("${ldc.foreclose.amount.date.diff:2}")
	long ldcForecloseAmountDateDiff;

	@Value("${is.fore.closure.charges.allowed:true}")
	boolean isForeClosureChargesAllowed;

	@Value("${whitelisted.fore.closure.charges.lenders:LIQUILOANS_P2P,LIQUILOANS_P2P_OF,TRILLIONLOANS,LIQUILOANS_NBFC,PAYU}")
	String foreClosureChargesWhitelistedLenders;

	@Value("${skip.nach.disabled.lenders:ABFL}")
	private Set<String> skipNachDisabledLenders;

	@Value("${skip.nach.disabled.merchants:}")
	private Set<Integer> skipNachDisabledMerchant;

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
	NachMandateEligibilityConfigDao nachMandateEligibilityConfigDao;

	@Autowired
	LendingBBSDao lendingBBSDao;

	@Autowired
	LendingBBSSnapshotDao lendingBBSSnapshotDao;

	@Autowired
	LaunchLabsHandler launchLabsHandler;

//	@Autowired
//	MerchantScoreDao merchantScoreDao;

	@Autowired
	MerchantScoreSnapshotDao merchantScoreSnapshotDao;

	@Autowired
	LendingPrepaymentDao lendingPrepaymentDao;

	@Autowired
	@Qualifier("ConfluentKafkaTemplate")
	KafkaTemplate<String, Object> confluentKafkaTemplate;

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
	LendingEligibleLoanDao eligibleLoanDao;

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
	AutoPayUPIDao autoPayUPIDao;

	@Autowired
	LendingLedgerDao lendingLedgerDao;

	String INTERNAL_MERCHANTS = "internal_merchants";

	@Autowired
	LendingResubmitTaskDao lendingResubmitTaskDao;

	@Autowired
	LendingApplicationDao lendingApplicationDao;

	@Autowired
	LendingApplicationDetailsDao lendingApplicationDetailsDao;

	@Autowired
	LendingApplicationServiceV2 lendingApplicationServiceV2;

	@Autowired
	private LoanDashboardService loanDashboardService;

	@Autowired
	LendingCollectionExcessDao lendingCollectionExcessDao;

	@Autowired
	ExcessNachService excessNachService;

	@Autowired
	LendingAuditTrialDao lendingAuditTrialDao;

    @Autowired
	ForeClosureConfigDao foreClosureDao;

	@Autowired
	LendingLoanInsuranceDao lendingLoanInsuranceDao;

	@Value("${update.ifsc.piramal:false}")
	boolean updateIfscForPiramal;

	@Value("${aggregation.flow.experimentId:}")
	String isAggregationFlowApplicableExperimentId;

	public List<String> allowedRiskGroupsNachWaiver = Arrays.asList("R1", "R2", "R3", "R4");

	List<Long> derogMerchants = new ArrayList();

	List<Long> customEnabledMerchants = new ArrayList();

	List<Long> reNachEnabledMerchants = new ArrayList();

	List<Long> rteEligibleMerchants = new ArrayList();

	List<Long> bankStatementEligibleMerchants = new ArrayList<>();

	List<Long> abflExcludedMerchants = new ArrayList<>();

	List<Long> gst3bEligibleMerchants = new ArrayList<>();

	List<Long> accountAggregatorEligibleMerchants = new ArrayList<>();

	Map<Long, String> forceLendersForMerchants = new HashMap<>();

	Map<String, String> rejectedLenderMapping = new HashMap<>();

	List<Long> updateLeadLenderClientIdsList = new ArrayList();


	@Autowired
	LendingDisbursalModeConfigDao lendingDisbursalModeConfigDao;

	@Autowired
	LenderAssociationStageFactory lenderAssociationStageFactory;

	@Autowired
	AbflForeclosureFetchService abflForeclosureFetchService;

	@Autowired
	LmsStageHistoryDao lmsStageHistoryDao;

	@Autowired
	EasyLoanUtil easyLoanUtil;

	@Autowired
	NachBounceChargesService nachBounceChargesService;

	@Value("${payu.nach.bounce.charge:500}")
	Integer payUNachBounceCharge;

	@Value("${eligibleLoan.creation.skip.rollout:0}")
	Integer eligibleLoanCreationSkipRollout;

	@Autowired
	PenalChargesDao penalChargesDao;

	@Autowired
	LendingPaymentScheduleLendingCommonDao lendingPaymentScheduleLendingCommonDao;


	@Value("${fore.closure.charges.rollout.date:2024-04-10 00:00}")
	String foreClosureChargesRolloutDate;

	@Value("${fore.closure.charges.rollout.date.LIQUILOANS_P2P:2024-04-10 00:00}")
	String liquiloansp2pForeClosureChargesRolloutDate;
	@Value("${fore.closure.charges.rollout.date.LIQUILOANS_P2P_OF:2024-04-10 00:00}")
	String liquiloansp2pofForeClosureChargesRolloutDate;
	@Value("${fore.closure.charges.rollout.date.TRILLIONLOANS:2024-04-10 00:00}")
	String trillionloansForeClosureChargesRolloutDate;
	@Value("${fore.closure.charges.rollout.date.LIQUILOANS_NBFC:2024-04-10 00:00}")
	String liquiloansnbfcForeClosureChargesRolloutDate;

	@Value("${fore.closure.charges.rollout.date.PAYU:2024-09-10 00:00}")
	String payuForeClosureChargesRolloutDate;


	@Value("${fore.closure.charges.rollout.date.CREDITSAISON:2024-10-01 00:00}")
	String creditSaisonForeClosureChargesRolloutDate;

	@Value("${fore.closure.charges.rollout.date.OXYZO:2025-01-16 00:00}")
	String oxyzoForeClosureChargesRolloutDate;

	@Value("${penalty.charges.rollout.date.PIRAMAL:2025-03-25 00:00}")
	String piramalPenaltyChargesRolloutDate;

    @Value("${fore.closure.charges.rollout.date.ABFL:2025-02-20 00:00}")
	String abflForeClosureChargesRolloutDate;

	@Value("${autopay.upi.lenders:}")
	String autoPayUpiLenders;


	@Value("${max.loan.amount.autopayupi:50000}")
	Double maxLoanAmountForAutoPayUPI;


	@Value("${excluded.error.codes}")
	private String excludedErrorCodes;

	private final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

	@Value("${lender.aggregation.screens:}")
	String lenderAggregationScreens;

	@Value("#{'${api.token:}'.split(',')}")
	List<String> apiTokens;

	@Autowired
	LendingLenderPricingDao lendingLenderPricingDao;

	@Value("#{'${lender.pricing.eligible.merchants.percent}'.split(',')}")
	private List<Integer> lenderPricingEligibleMerchantsPercent;

	@Value("${auto.pay.upi.internal.merchant:-}")
	private List<String> autoPauUpiInternalMerchant;

	@Value("${deprecated.merchant.references:true}")
    private boolean hasDeprecatedMerchantReferences;
	@Autowired
	ObjectMapper objectMapper;
	@Autowired
	NbfcLenderGateway nbfcLenderGateway;

	@Value("${nbfc.foreclosure.charge:api/v3/lender/post-charges}")
	String nbfcChargePosting;
	@Value("${nbfc.baseurl.v3.api:https://api-nbfc-uat.bharatpe.in/}")
	String nbfcBaseUrl;
	@Value("${fore.closure.charges.rollout.date.PIRAMAL:2025-02-20 00:00}")
	String piramalForeClosureChargesRolloutDate;
	@Autowired
	PenaltyFeeLedgerDao penaltyFeeLedgerDao;
	@Autowired
	PenaltyFeeConfigDaoSlave penaltyFeeConfigDaoSlave;

	@PostConstruct
	public void init(){
		skipNachDisabledLenders = skipNachDisabledLenders.stream().map(String::trim).collect(Collectors.toSet());
	}

	public List<Long> loadDerogEffectedMerchants() {
		if (!ObjectUtils.isEmpty(derogMerchants)) {
			return derogMerchants;
		}

		String filePath = "/MerchantList/derog_merchant";

		derogMerchants = readCsvFile(filePath);
		return derogMerchants;
	}

	public List<Long> customEnabledTopupMerchants() {
		if (!ObjectUtils.isEmpty(customEnabledMerchants)) {
			return customEnabledMerchants;
		}

		String filePath = "/MerchantList/custom_enabled_merchants";

		customEnabledMerchants = readCsvFile(filePath);
		return customEnabledMerchants;
	}

	public List<Long> reNachEnabledMerchants() {
		if (!ObjectUtils.isEmpty(reNachEnabledMerchants)) {
			return reNachEnabledMerchants;
		}

		String filePath = "/MerchantList/renach_enabled_merchants";

		reNachEnabledMerchants = readCsvFile(filePath);
		return reNachEnabledMerchants;
	}

	public List<Long> getLenderClientIdList() {
		if (!ObjectUtils.isEmpty(updateLeadLenderClientIdsList)) {
			return updateLeadLenderClientIdsList;
		}

		String filePath = "/MerchantList/updateLeadLenderClientIds";

		updateLeadLenderClientIdsList = readCsvFile(filePath);
		return updateLeadLenderClientIdsList;
	}

	private boolean derogTopUpEnable(Long merchantId) {
		LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
		logger.info("DEROG_EFFECTED_MERCHANT_FLOW: merchant_id: {} application_id: {} type: {}", merchantId, lendingApplication.getId(), lendingApplication.getLoanType());
		return LoanType.TOPUP.name().equals(lendingApplication.getLoanType());
	}

	public List<Long> rteEligibleMerchant() {
		if (!ObjectUtils.isEmpty(rteEligibleMerchants)) {
			return rteEligibleMerchants;
		}

		String filePath = "/MerchantList/rte_eligible_merchants";

		rteEligibleMerchants = readCsvFile(filePath);
		return rteEligibleMerchants;
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
		put("EXCESS_SETLMNT", "Excess Credit Adjusted");
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
			bqPublisherUtil.publish("lending","experian_audit_trail", experianAuditTrail);
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

	public boolean isApplicationEligibleForAutoPayUpi(String lender, Long merchantId, Double loanAmount) {

		if (autoPayUpiLenders.contains(lender))
		{
			return true;
		}
        return  isAutoPayUpiInternalMerchant(merchantId);
	}

	private boolean isAutoPayUpiInternalMerchant(Long merchantId) {
		return autoPauUpiInternalMerchant.contains(merchantId);
	}


	/*
	feature-ML-820 : New logic implemented for displaying message based on TAT days

	public String getApplicationTatMessage(LendingApplicationSlave lendingApplication){
		if(ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(lendingApplication.getStatus())
				&& !NACH_STATUS_APPROVED.equalsIgnoreCase(lendingApplication.getNachStatus())
		){
			return PENDING_APPLICATION_TAT_TEXT;
		}
		int tat = getApplicationTAT(lendingApplication);
		return tat < 1 ? TAT_BREACH_TEXT : TRANSFER_DAYS_TEXT_PREFIX + tat + "-" + (tat + 1) + TRANSFER_DAYS_TEXT_SUFFIX;
	}

	public String getApplicationTatMessage(LendingApplication lendingApplication){
		if(ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(lendingApplication.getStatus())
				&& !NACH_STATUS_APPROVED.equalsIgnoreCase(lendingApplication.getNachStatus())
		){
			return PENDING_APPLICATION_TAT_TEXT;
		}
		int tat = getApplicationTAT(lendingApplication);
		return tat < 1 ? TAT_BREACH_TEXT : TRANSFER_DAYS_TEXT_PREFIX + tat + "-" + (tat + 1) + TRANSFER_DAYS_TEXT_SUFFIX;
	}

	public int getApplicationTAT(LendingApplicationSlave lendingApplication) {
		int tat = -1;
		LendingApplicationPriority lendingApplicationPriority = lendingApplicationPriorityDao.findByApplicationId(lendingApplication.getId());
		if (lendingApplicationPriority != null && lendingApplicationPriority.getTat() != null && lendingApplicationPriority.getTatStartTime() != null) {
			int adjustedTat = lendingApplicationPriority.getTat();
			if("approved".equalsIgnoreCase(lendingApplication.getNachStatus()) && Objects.nonNull(lendingApplication.getLmsStage())){
				//for lms applications
				adjustedTat = (int)Math.ceil((double)lendingApplicationPriority.getTat()/2 - 1);
			}
			else if("approved".equalsIgnoreCase(lendingApplication.getStatus()) && Objects.isNull(lendingApplication.getLmsStage())){
				//for auto disbursal applications
				adjustedTat = 2;
			}
			tat = (int) (adjustedTat - (getDateDiffInDays(lendingApplicationPriority.getTatStartTime(), new Date())));
		}
		return tat;
	}

	public int getApplicationTAT(LendingApplication lendingApplication) {
		int tat = -1;
		LendingApplicationPriority lendingApplicationPriority = lendingApplicationPriorityDao.findByApplicationId(lendingApplication.getId());
		if (lendingApplicationPriority != null && lendingApplicationPriority.getTat() != null && lendingApplicationPriority.getTatStartTime() != null) {
			int adjustedTat = lendingApplicationPriority.getTat();
			if("approved".equalsIgnoreCase(lendingApplication.getNachStatus()) && Objects.nonNull(lendingApplication.getLmsStage())){
				//for lms applications
				adjustedTat = (int)Math.ceil((double)lendingApplicationPriority.getTat()/2 - 1);
			}
			else if("approved".equalsIgnoreCase(lendingApplication.getStatus()) && Objects.isNull(lendingApplication.getLmsStage())){
				//for auto disbursal applications
				adjustedTat = 2;
			}
			tat = (int) (adjustedTat - (getDateDiffInDays(lendingApplicationPriority.getTatStartTime(), new Date())));
		}
		return tat;
	}
	 */

	public String getApplicationTatMessage(LendingApplication lendingApplication) {
		if (ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(lendingApplication.getStatus())
				&& (ObjectUtils.isEmpty(lendingApplication.getNachStatus())
				|| !"approved".equalsIgnoreCase(lendingApplication.getNachStatus()))) {
			return PENDING_APPLICATION_TAT_TEXT;
		}

		int tat = getApplicationTAT(lendingApplication);
		logger.info("tat for applicationId {} : {}", lendingApplication.getId(), tat);

		if (tat < 0) {
			return INVALID_CASE;
		}

		// Phase 1: Initial 7 Days
		if (tat <= 7) {
			if (tat == 7) {
				return INITIAL_PHASE_LAST_DAY;
			} else {
				return String.format(INITIAL_PHASE, (7 - tat));
			}
		}

		// Phase 2: 8th to 13th Day
		if (tat <= 13) {
			if (tat == 13) {
				return FIRST_TAT_BREACH_PHASE_LAST_DAY;
			} else {
				return String.format(FIRST_TAT_BREACH_PHASE, (13 - tat));
			}
		}

		// Phase 3: Beyond 13th Day
		return SECOND_TAT_BREACH_PHASE;
	}

	public int getApplicationTAT(LendingApplication lendingApplication) {
		LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
		if ("approved".equalsIgnoreCase(lendingApplication.getNachStatus())) {
			if (!ObjectUtils.isEmpty(lendingApplicationDetails) && !ObjectUtils.isEmpty(lendingApplicationDetails.getLeadAcceptanceTime())) {
				return (int) getDateDiffInDays(lendingApplicationDetails.getLeadAcceptanceTime(), new Date());
			} else if (!ObjectUtils.isEmpty(lendingApplication.getAgreementAt())) {
				return (int) getDateDiffInDays(lendingApplication.getAgreementAt(), new Date());
			}
		}
		return -1; // NACH approval date or Agreement at date not found
	}

	public String getApplicationTatMessage(LendingApplicationSlave lendingApplication) {
		if (ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(lendingApplication.getStatus())
				&& (ObjectUtils.isEmpty(lendingApplication.getNachStatus())
				|| !"approved".equalsIgnoreCase(lendingApplication.getNachStatus()))) {
			return PENDING_APPLICATION_TAT_TEXT;
		}

		int tat = getApplicationTAT(lendingApplication);
		logger.info("tat for applicationId {} : {}", lendingApplication.getId(), tat);

		if (tat < 0) {
			return INVALID_CASE;
		}

		// Phase 1: Initial 7 Days
		if (tat <= 7) {
			if (tat == 7) {
				return INITIAL_PHASE_LAST_DAY;
			} else {
				return String.format(INITIAL_PHASE, (7 - tat));
			}
		}

		// Phase 2: 8th to 13th Day
		if (tat <= 13) {
			if (tat == 13) {
				return FIRST_TAT_BREACH_PHASE_LAST_DAY;
			} else {
				return String.format(FIRST_TAT_BREACH_PHASE, (13 - tat));
			}
		}

		// Phase 3: Beyond 13th Day
		return SECOND_TAT_BREACH_PHASE;
	}

	public int getApplicationTAT(LendingApplicationSlave lendingApplication) {
		LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
		if ("approved".equalsIgnoreCase(lendingApplication.getNachStatus())) {
			if (!ObjectUtils.isEmpty(lendingApplicationDetails) && !ObjectUtils.isEmpty(lendingApplicationDetails.getLeadAcceptanceTime())) {
				return (int) getDateDiffInDays(lendingApplicationDetails.getLeadAcceptanceTime(), new Date());
			} else if (!ObjectUtils.isEmpty(lendingApplication.getAgreementAt())) {
				return (int) getDateDiffInDays(lendingApplication.getAgreementAt(), new Date());
			}
		}
		return -1; // NACH approval date or Agreement at date not found
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
			mongoLogPublisher.publish("Lending", "merchant_sms_analysis", merchant.getId().toString(), new ArrayList<MerchantSmsAnalysis>() {{
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

	public boolean checkPennyDropV2(Long merchantId, Long applicationId) {
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

			if (ObjectUtils.isEmpty(merchantBankDetail)) {
				return false;
			}

			final LendingPennydrop pennydropInLast15Days = lendingPennydropDao.findByMerchantIdAndAccountNumberInLast15Days(merchantId,
			merchantBankDetail.getAccountNumber());

			if (!ObjectUtils.isEmpty(pennydropInLast15Days)) {
				if (pennydropInLast15Days.getStatus().equalsIgnoreCase("SUCCESS")) {
					logger.info("Penny drop success for merchant:{}", merchantId);
					return true;
				}

				if (pennydropInLast15Days.getStatus().equalsIgnoreCase("PENDING")) {
					logger.info("Penny drop is pending for merchant:{} and pennydropId : {}", merchantId, pennydropInLast15Days.getId());
					return false;
				}

				if (pennydropInLast15Days.getStatus().equalsIgnoreCase("FAILED")) {
					logger.info("Penny drop failed for merchant:{}", merchantId);
					return false;
				}
			}

			String lockRedisKey = PENNYDROP_LOCK_PREFIX  + merchantId;

			// take lock for pennydrop to avoid parallel pennydrop calls
			if (!lendingCache.acquireLock(lockRedisKey)) {
				logger.info("Unable to take lock on pennydrop key " + lockRedisKey);
				return false;
			}

			// if no entry in last 15 days then initiatePennyDrop
			pennyDropService.initiateNewPennyDrop(merchantBankDetail, merchantId, applicationId);


			logger.info("Releasing lock for penndrop key " + lockRedisKey);
			lendingCache.releaseLock(lockRedisKey);

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
		LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchant.getId(), Collections.singletonList("ACTIVE"));
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
				lendingRiskVariablesSnapshot.setBbs2(lendingRiskVariables.getBbs2());
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
				lendingRiskVariablesSnapshot.setInferredPincodeOffer(lendingRiskVariables.getInferredPincodeOffer());
				lendingRiskVariablesSnapshot.setBankBasedOffer(lendingRiskVariables.getBankBasedOffer());
				lendingRiskVariablesSnapshot.setGst3bBasedOffer(lendingRiskVariables.getGst3bBasedOffer());
				lendingRiskVariablesSnapshot.setComputeSource(lendingRiskVariables.getComputeSource());
				lendingRiskVariablesSnapshot.setAggregateId(lendingRiskVariables.getAggregateId());
				lendingRiskVariablesSnapshot.setMonthlyIncome(lendingRiskVariables.getMonthlyIncome());
				lendingRiskVariablesSnapshot.setTpv6Mon(lendingRiskVariables.getTpv6Mon());
				lendingRiskVariablesSnapshot.setGst3bBasedAffectedOffer(lendingRiskVariables.getGst3bBasedAffectedOffer());
				lendingRiskVariablesSnapshot.setAaBasedAffectedOffer(lendingRiskVariables.getAaBasedAffectedOffer());
				lendingRiskVariablesSnapshot.setAaBasedOffer(lendingRiskVariables.getAaBasedOffer());
				lendingRiskVariablesSnapshot.setBankBasedAffectedOffer(lendingRiskVariables.getBankBasedAffectedOffer());
				lendingRiskVariablesSnapshot.setApprovalRate(lendingRiskVariables.getApprovalRate());
				lendingRiskVariablesSnapshot.setClientIdentifier(lendingRiskVariables.getClientIdentifier());
				lendingRiskVariablesSnapshot.setSummaryTpv60d(lendingRiskVariables.getSummaryTpv60d());
				lendingRiskVariablesSnapshot.setRejectedLenders(lendingRiskVariables.getRejectedLenders());
				logger.info("setting data for minTvrCount & newContactRefLogic for applicationId: {}",lendingApplication.getId());
				lendingRiskVariablesSnapshot.setMinTvrCount(lendingRiskVariables.getMinTvrCount());
				lendingRiskVariablesSnapshot.setNewContactReferenceLogic(lendingRiskVariables.getNewContactReferenceLogic());
				lendingRiskVariablesSnapshot.setStpFlag(lendingRiskVariables.getStpFlag());
				lendingRiskVariablesSnapshot.setLoanCategory(lendingRiskVariables.getLoanCategory());
				lendingRiskVariablesSnapshot.setMetaData(lendingRiskVariables.getMetaData());
				lendingRiskVariablesSnapshotDao.save(lendingRiskVariablesSnapshot);
			}
		} catch (Exception e) {
			logger.error("Exception in createRiskVariablesSnapshot for application:{}", lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
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

	public Date parseRolloutDate(String stringDate) {

		try {
			SimpleDateFormat sdf = new SimpleDateFormat(YYYY_MM_DD_HH_MM_SS);
			return sdf.parse(stringDate);
		} catch (Exception e) {
			logger.info("Exception occurred while parsing date for string : {}", stringDate);
		}
		return null;
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

	public LendingNachBankResponseDTO getEnachBankMode(Long merchantId) {
		final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
		BankDetailsDto merchantBankDetail = null;
		if (bankDetailsDtoOptional.isPresent())
			merchantBankDetail = bankDetailsDtoOptional.get();
		if (merchantBankDetail == null) return null;
		LendingNachBankResponseDTO lendingNachBank = enachHandler.findByIfsc(merchantBankDetail.getIfsc().substring(0, 4));
		if (updateIfscForPiramal && isInternalMerchant(merchantId)) {
			logger.info("setting ifsc as BCBM for {}", merchantId);
			lendingNachBank = enachHandler.findByIfsc("BCBM");
		}
		logger.info("lendingNachBank for {} : {}", merchantId, lendingNachBank);
		if(!ObjectUtils.isEmpty(lendingNachBank))return lendingNachBank;
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

		Double excessCollectionBalance = excessNachService.getExcessCollectionBalanceAmount(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId());

		return (int) Math.ceil(lendingPaymentSchedule.getLoanAmount() + (Objects.nonNull(lendingPaymentSchedule.getDuePenalty()) ? lendingPaymentSchedule.getDuePenalty() : 0)
				- (lendingPaymentSchedule.getPaidPrinciple() != null ? lendingPaymentSchedule.getPaidPrinciple() : 0)
				+ (lendingPaymentSchedule.getDueInterest() != null ? lendingPaymentSchedule.getDueInterest() : 0)
				- advanceEdiAmount - excessCollectionBalance);
	}


	public int getForeclosureAmount(LendingPaymentSchedule lendingPaymentSchedule, Double excessCollectionBalance) {
		if (lendingPaymentSchedule == null || lendingPaymentSchedule.getStatus().equals("CLOSED")) {
			return 0;
		}
		LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId());
		double advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ? lendingPrepayment.getAdvanceEdiAmount() : 0d;

		Double extraInterestofPerpetualDpdLoan = fetchExtraEdiInterestCollectionForPerpetualDpdLoan(lendingPaymentSchedule.getId());

		return (int) Math.ceil(lendingPaymentSchedule.getLoanAmount() + (Objects.nonNull(lendingPaymentSchedule.getDuePenalty()) ? lendingPaymentSchedule.getDuePenalty() : 0)
		- (lendingPaymentSchedule.getPaidPrinciple() != null ? lendingPaymentSchedule.getPaidPrinciple() : 0)
		+ (lendingPaymentSchedule.getDueInterest() != null ? lendingPaymentSchedule.getDueInterest() : 0)
		- advanceEdiAmount - excessCollectionBalance - extraInterestofPerpetualDpdLoan);
	}

	public int getForeclosureAmount(LendingPaymentScheduleSlave lendingPaymentSchedule) {
		if (lendingPaymentSchedule == null || lendingPaymentSchedule.getStatus().equals("CLOSED")) {
			return 0;
		}
		LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId());
		double advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ? lendingPrepayment.getAdvanceEdiAmount() : 0d;

		Double excessCollectionBalance = excessNachService.getExcessCollectionBalanceAmount(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId());
		Double extraInterestofPerpetualDpdLoan = fetchExtraEdiInterestCollectionForPerpetualDpdLoan(lendingPaymentSchedule.getId());

		return (int) Math.ceil(lendingPaymentSchedule.getLoanAmount()
				- (lendingPaymentSchedule.getPaidPrinciple() != null ? lendingPaymentSchedule.getPaidPrinciple() : 0)
				+ (lendingPaymentSchedule.getDueInterest() != null ? lendingPaymentSchedule.getDueInterest() : 0)
				- advanceEdiAmount - excessCollectionBalance - extraInterestofPerpetualDpdLoan);
	}

	//This method returns the foreclosure amount in Big Decimal and take argument lendingPaymentScheduleSlave Object
	public BigDecimal getForeclosureAmountBD(LendingPaymentScheduleSlave lendingPaymentSchedule) {
		if (lendingPaymentSchedule == null || lendingPaymentSchedule.getStatus().equals("CLOSED")) {
			return BigDecimal.ZERO;
		}
		LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId());
		BigDecimal advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ?
				BigDecimal.valueOf(lendingPrepayment.getAdvanceEdiAmount()) : BigDecimal.ZERO;

		BigDecimal excessCollectionBalance = new BigDecimal(excessNachService.getExcessCollectionBalanceAmount(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId()).toString());
		BigDecimal extraInterestofPerpetualDpdLoan = BigDecimal.valueOf(fetchExtraEdiInterestCollectionForPerpetualDpdLoan(lendingPaymentSchedule.getId()));

		BigDecimal loanAmount = BigDecimal.valueOf(lendingPaymentSchedule.getLoanAmount());

		BigDecimal paidPrinciple = lendingPaymentSchedule.getPaidPrinciple() != null ?
				BigDecimal.valueOf(lendingPaymentSchedule.getPaidPrinciple()) : BigDecimal.ZERO;

		BigDecimal dueInterest = lendingPaymentSchedule.getDueInterest() != null ?
				BigDecimal.valueOf(lendingPaymentSchedule.getDueInterest()) : BigDecimal.ZERO;


		BigDecimal foreclosureAmount = loanAmount.subtract(paidPrinciple)
				.add(dueInterest)
				.subtract(advanceEdiAmount)
				.subtract(excessCollectionBalance)
				.subtract(extraInterestofPerpetualDpdLoan);

		return foreclosureAmount.setScale(0, RoundingMode.CEILING);
	}

	//This method returns the foreclosure amount in Big Decimal and take argument lendingPaymentSchedule object
	public BigDecimal getForeclosureAmountBD(LendingPaymentSchedule lendingPaymentSchedule) {
		if (lendingPaymentSchedule == null || lendingPaymentSchedule.getStatus().equals("CLOSED")) {
			return BigDecimal.ZERO;
		}
		LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId());
		BigDecimal advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ?
                BigDecimal.valueOf(lendingPrepayment.getAdvanceEdiAmount()) : BigDecimal.ZERO;

		BigDecimal excessCollectionBalance = new BigDecimal(excessNachService.getExcessCollectionBalanceAmount(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId()).toString());
		BigDecimal extraInterestofPerpetualDpdLoan = BigDecimal.valueOf(fetchExtraEdiInterestCollectionForPerpetualDpdLoan(lendingPaymentSchedule.getId()));

		BigDecimal loanAmount = BigDecimal.valueOf(lendingPaymentSchedule.getLoanAmount());

		BigDecimal paidPrinciple = lendingPaymentSchedule.getPaidPrinciple() != null ?
                BigDecimal.valueOf(lendingPaymentSchedule.getPaidPrinciple()) : BigDecimal.ZERO;

		BigDecimal dueInterest = lendingPaymentSchedule.getDueInterest() != null ?
                BigDecimal.valueOf(lendingPaymentSchedule.getDueInterest()) : BigDecimal.ZERO;


		BigDecimal foreclosureAmount = loanAmount.subtract(paidPrinciple)
				.add(dueInterest)
				.subtract(advanceEdiAmount)
				.subtract(excessCollectionBalance)
				.subtract(extraInterestofPerpetualDpdLoan);

		return foreclosureAmount.setScale(0, RoundingMode.CEILING);
	}

	public double getForeclosureAmountForLdc (LendingPaymentSchedule lendingPaymentSchedule) {

		double prevLoanUnpaidAmount = 0;

		final LdcForeclosureDetailsApiResponseDTO ldcForeclosureDetails =
		apiGatewayService.getLdcForeclosureDetails(lendingPaymentSchedule.getApplicationId());

		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

		String dateString = format.format(addDays(new Date(), ldcForecloseAmountDateDiff));

		final LdcForeclosureDetailsApiResponseDTO.ForeclosureData foreclosureData = ldcForeclosureDetails.getData().getData().get(dateString);

		logger.info("foreclosure amount picked for date : {} {}", dateString, foreclosureData);

		prevLoanUnpaidAmount = foreclosureData.getTotalOutstandingAmount();
		return prevLoanUnpaidAmount;
	}

	public double getForeclosureAmountForLdc (LendingPaymentScheduleSlave lendingPaymentSchedule) {

		double prevLoanUnpaidAmount = 0;

		final LdcForeclosureDetailsApiResponseDTO ldcForeclosureDetails =
				apiGatewayService.getLdcForeclosureDetails(lendingPaymentSchedule.getApplicationId());

		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

		String dateString = format.format(addDays(new Date(), ldcForecloseAmountDateDiff));

		final LdcForeclosureDetailsApiResponseDTO.ForeclosureData foreclosureData = ldcForeclosureDetails.getData().getData().get(dateString);

		logger.info("foreclosure amount picked for date : {} {}", dateString, foreclosureData);

		double duePenalty = Objects.nonNull(lendingPaymentSchedule.getDuePenalty()) ? lendingPaymentSchedule.getDuePenalty() : 0;
		prevLoanUnpaidAmount = foreclosureData.getTotalOutstandingAmount() + duePenalty;
		return prevLoanUnpaidAmount;
	}

	public double getForeClosureAmountForLender(LendingPaymentSchedule lendingPaymentSchedule) {
		Double netForeclosureAtLender = 0d;
		ILenderAssociationService iLenderAssociationService = lenderAssociationStageFactory.getStageAssociatedLenderService(LenderAssociationStages.FORECLOSURE_FETCH.name())
				.getLenderAssociationService(lendingPaymentSchedule.getNbfc());
		if (!ObjectUtils.isEmpty(iLenderAssociationService)) {
			LenderForeclosureDetailsDTO lenderForeclosureDetailsDTO = (LenderForeclosureDetailsDTO) iLenderAssociationService.invoke(lendingPaymentSchedule.getApplicationId(), null);
			netForeclosureAtLender = (lenderForeclosureDetailsDTO == null || lenderForeclosureDetailsDTO.getForeclosureAmount() == null) ? 0 : lenderForeclosureDetailsDTO.getForeclosureAmount();
		}
		return netForeclosureAtLender;
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
				confluentKafkaTemplate.send(LendingConstants.APPLICATION_EVENT_TOPIC, lendingApplication.getId().toString(), request);
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
			logger.info("Data published to DS for application Id : {} {}", request, lendingApplication.getId());
			executorService.execute(() -> {
				confluentKafkaTemplate.send(LendingConstants.APPLICATION_DS_EVENT_TOPIC, lendingApplication.getId().toString(), request);
			});
		} catch (Exception e) {
			logger.error("Exception while publishing DS Data for application:{}", lendingApplication.getId(), e);
		}
	}

	public int getIoHalfPF(LendingPaymentSchedule lendingPaymentSchedule) {
		int foreclosureAmount = getForeclosureAmount(lendingPaymentSchedule);
		return (int) Math.ceil(foreclosureAmount * 0.05);
	}

	//This method returns the PF value in Big Decimal
	public BigDecimal getIoHalfPFBD(LendingPaymentSchedule lendingPaymentSchedule) {
		BigDecimal foreclosureAmount = new BigDecimal(String.valueOf(getForeclosureAmountBD(lendingPaymentSchedule)));
		return foreclosureAmount.multiply(new BigDecimal("0.05")).setScale(0, RoundingMode.CEILING);
	}


	public int getIoHalfPF(LendingPaymentScheduleSlave lendingPaymentSchedule) {
		int foreclosureAmount = getForeclosureAmount(lendingPaymentSchedule);
		return (int) Math.ceil(foreclosureAmount * 0.05);
	}

	public BigDecimal getIoHalfPFBD(LendingPaymentScheduleSlave lendingPaymentSchedule) {
		BigDecimal foreclosureAmount = new BigDecimal(String.valueOf(getForeclosureAmountBD(lendingPaymentSchedule)));
		return foreclosureAmount.multiply(new BigDecimal("0.05")).setScale(0, RoundingMode.CEILING);
	}


	public LendingEligibleLoan calculateLoanBreakup(
			GlobalLimitResponse.OfferDetail tenureDetail, Long merchantId, String loanType, Double amount, String offerType,
			Double version, boolean skipEligibleLoanDbEntryCreation
	) {

		Integer ediAmount = (int) Math.ceil(((amount + (amount * (tenureDetail.getInterestRate() / 100) * tenureDetail.getTenure()))) / tenureDetail.getEdiCount());
		Integer repayment = Math.round((tenureDetail.getEdiCount() * ediAmount));

		Integer sevenDayEdiAmount = (int) Math.ceil(((amount + (amount * (tenureDetail.getInterestRate() / 100) * tenureDetail.getTenure()))) / (30 * tenureDetail.getTenure()));
		Integer sevenDayRepayment = Math.round((30 * tenureDetail.getTenure() * sevenDayEdiAmount));
		List<LendingEligibleLoan> eligibleLoanList = new ArrayList<>();

		BigDecimal processingFee;
		BigDecimal amountBD = new BigDecimal(amount);
		BigDecimal processingFeeRateBD = BigDecimal.valueOf(tenureDetail.getProcessingFee());
		if(tenureDetail.getProcessingFee() != null){
			processingFee = amountBD.multiply(processingFeeRateBD).setScale(0, RoundingMode.CEILING);
		}
		else{
			throw new NullPointerException("processing fee cannot be null in tenure details");
		}


		LendingEligibleLoan eligibleLoan = LendingEligibleLoan.builder()
				.loanType(loanType)
				.offerType(offerType)
				.amount(amount)
				.repayment(repayment)
				.rateOfInterest(tenureDetail.getInterestRate())
				.initialRoi(tenureDetail.getInitialRoi())
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
				.processingFee(processingFee.intValue())
				.version(version)
				.clubV2Amount(tenureDetail.getClubV2Amount())
				.processingFeeRate(tenureDetail.getProcessingFee())
				.build();


		LendingEligibleLoan sevenDayEligibleLoanOffer = LendingEligibleLoan.builder()
				.loanType(loanType)
				.offerType(offerType)
				.amount(amount)
				.repayment(sevenDayRepayment)
				.rateOfInterest(tenureDetail.getInterestRate())
				.initialRoi(tenureDetail.getInitialRoi())
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
				.processingFee(processingFee.intValue())
				.version(version)
				.clubV2Amount(tenureDetail.getClubV2Amount())
				.processingFeeRate(tenureDetail.getProcessingFee())
				.build();
		eligibleLoanList.add(eligibleLoan);
		eligibleLoanList.add(sevenDayEligibleLoanOffer);
		if(easyLoanUtil.percentScaleUp(merchantId, eligibleLoanCreationSkipRollout) && skipEligibleLoanDbEntryCreation){
			logger.info("skipping eligible_loan entry creation for {}", merchantId);
			return sevenDayEligibleLoanOffer;
		}
		eligibleLoanDao.saveAll(eligibleLoanList);
		eligibleLoanDao.flush();
		return sevenDayEligibleLoanOffer;
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
			if (!hasDeprecatedMerchantReferences) {
				dsHandler.getMerchantReferences(merchantId, minScore, toBeShown, lendingApplication.getId());
			}
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
		if (lender.equals("LIQUILOANS_NBFC") || "TRILLIONLOANS".equalsIgnoreCase(lender)) {
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
			finalLender = ABFL.name();
		}
		if (lender.equals("PIRAMAL")) {
			finalLender = Lender.PIRAMAL.name();
		}
		if("USFB".equalsIgnoreCase(lender)) {
			finalLender = Lender.USFB.name();
		}
		if("MUTHOOT".equalsIgnoreCase(lender)){
			finalLender = Lender.MUTHOOT.name();
		}
		if("CAPRI".equalsIgnoreCase(lender)) {
			finalLender = Lender.CAPRI.name();
		}
		if("PAYU".equalsIgnoreCase(lender)) {
			finalLender = Lender.PAYU.name();
		}
		if("CREDITSAISON".equalsIgnoreCase(lender)) {
			finalLender = Lender.CREDITSAISON.name();
		}
		if("SMFG".equalsIgnoreCase(lender)) {
			finalLender = Lender.SMFG.name();
		}
		if(UGRO.name().equalsIgnoreCase(lender)) {
			finalLender = UGRO.name();
		}
		if("OXYZO".equalsIgnoreCase(lender)) {
			finalLender = Lender.OXYZO.name();
		}
		return finalLender;
	}

	public boolean isNachToBeRefunded(LendingApplication lendingApplication) {
		if (!ObjectUtils.isEmpty(lendingApplication.getNachLender()))
			return lendingApplication.getNachLender().equals("BHARATPE");
		return false;
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
		if(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()) && Lender.TRILLIONLOANS.name().equalsIgnoreCase(lendingApplication.getLender()) && isBTApplication(lendingApplication)) {
			logger.info("skip nach flow is not applicable for BT application {}", lendingApplication.getId());
			return false;
		}
		String finalLender = enachServiceLenderMapper(lender);
		if(checkIfNachSkipDisabled(finalLender, lendingApplication.getMerchantId())){
			return false;
		}
		if ("SMALL_TICKET".equals(lendingApplication.getLoanType())) {
			setIsNachSkip(lendingApplication);
			return Boolean.TRUE;
		}
		MerchantNachDetailsResponseDTO approvedNachDetails = enachHandler.findByMerchantIdAndLender(lendingApplication.getMerchantId(), finalLender);

		if (ObjectUtils.isEmpty(approvedNachDetails)) {
			return Boolean.FALSE;
		}

		if (approvedNachDetails.getNachAmount() >= lendingApplication.getLoanAmount()) {
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

	private boolean checkIfNachSkipDisabled(String finalLender, Long merchantId) {
		return !StringUtils.isEmpty(finalLender)
				&& skipNachDisabledLenders.contains(finalLender)
					&& skipNachDisabledMerchant.contains((int)(merchantId % 10));
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
			

			logger.info("changing lender from : {} to {}", lendingApplication.getLender(), newLender);
			lendingApplication.setLender(newLender);
			lendingApplication.setNachStatus(null);
			lendingApplication.setNachLender(enachServiceLenderMapper(newLender));
			lendingApplication.setStatus("pending_verification");
			lendingApplicationDao.save(lendingApplication);
			lendingResubmitTaskDao.save(resubmitTask);
			loanDashboardService.deleteLoanDashboardCache(lendingApplication.getMerchantId());
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

			if (Arrays.asList("R1", "R2").contains(riskGroup) && loanApplicationAmountTolastLoanAmountRatio <= 1.75) {
				return Boolean.TRUE;
			}

			if (Arrays.asList("R3").contains(riskGroup) && loanApplicationAmountTolastLoanAmountRatio <= 1.5) {
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

			if (Arrays.asList("R1", "R2").contains(riskGroup) && topupLoanAmountToActiveLoanAmountRatio <= 1.75) {
				return Boolean.TRUE;
			}

			if (Arrays.asList("R3").contains(riskGroup) && topupLoanAmountToActiveLoanAmountRatio <= 1.5) {
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

	private List<Long> readCsvFile(String filePath) {
		List<Long> merchantList = new ArrayList<>();
		try {

			logger.info("Reading file on path {}", filePath);

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

	public List<Long> bankStatementEligibleMerchants() {
		if (!ObjectUtils.isEmpty(bankStatementEligibleMerchants)) {
			return bankStatementEligibleMerchants;
		}
		bankStatementEligibleMerchants = readUnderWritingEligibleMerchantsCsvFile("/MerchantList/bank_statement_merchants");
		return bankStatementEligibleMerchants;
	}

	public List<Long> gst3bEligibleMerchants() {
		if (!ObjectUtils.isEmpty(gst3bEligibleMerchants)) {
			return gst3bEligibleMerchants;
		}
		gst3bEligibleMerchants = readUnderWritingEligibleMerchantsCsvFile("/MerchantList/gst3b_merchants");
		return gst3bEligibleMerchants;
	}

	public List<Long> accountAggregatorEligibleMerchants() {
		if (!ObjectUtils.isEmpty(accountAggregatorEligibleMerchants)) {
			return accountAggregatorEligibleMerchants;
		}
		accountAggregatorEligibleMerchants = readUnderWritingEligibleMerchantsCsvFile("/MerchantList/account_aggregator_merchants");
		return accountAggregatorEligibleMerchants;
	}

	private List<Long> readUnderWritingEligibleMerchantsCsvFile(String filePath) {
		List<Long> merchantList = new ArrayList<>();
		try {
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
			logger.info("exception while reading underwriting eligible merchant csv file: {} {}", e, e.getMessage());
		}
		return merchantList;
	}

	private List<Long> readabflExcludedMerchantsCsvFile() {
		List<Long> merchantList = new ArrayList<>();
		try {

			String filePath = "/MerchantList/abfl_excluded_merchants";
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
			logger.info("exception while reading abfl_excluded_merchants csv file: {} {}", e, e.getMessage());
		}
		return merchantList;
	}


	public List<Long> abflExcludedMerchants() {
		if (!ObjectUtils.isEmpty(abflExcludedMerchants)) {
			return abflExcludedMerchants;
		}
		abflExcludedMerchants = readabflExcludedMerchantsCsvFile();
		return abflExcludedMerchants;
  }

	public static EdiModel getEdiModal(LendingApplication lendingApplication) {
		return lendingApplication.getPayableDays() % 30 == 0 ?
				EdiModel.SEVEN_DAY_MODEL : EdiModel.SIX_DAY_MODEL;
	}

	public static EdiModel getEdiModal(LendingApplicationSlave lendingApplication) {
		return lendingApplication.getPayableDays() % 30 == 0 ?
				EdiModel.SEVEN_DAY_MODEL : EdiModel.SIX_DAY_MODEL;
	}

	public LendingEnum.LENDER percentLenderTrafficForAA(Long merchantId, Integer[] percentages) {
		try {
			logger.info("checking lender assignment for merchant: {} with lender traffic percentages : {}", merchantId, percentages);
			List<LendingEnum.LENDER> lenders = Arrays.asList(LendingEnum.LENDER.LDC, LendingEnum.LENDER.LIQUILOANS_P2P, LendingEnum.LENDER.LIQUILOANS_P2P_OF, LendingEnum.LENDER.TRILLIONLOANS);
			Double maxNumber = 50000000D;

			Integer percentage = (int) Math.ceil((merchantId / maxNumber) * percentages[3]);
			Integer index = Arrays.binarySearch(percentages, percentage);
			logger.info("index for merchantId : {} with percentage : {} is : {}", merchantId, percentage, index);
			index = index < 0 ? (Math.abs(index) - 1) : index;
			return lenders.get(index);
		} catch (Exception e) {
			logger.error("Exception in assigning lender for AA for merchantId : {}", merchantId);
			return null;
		}
	}

	private Map<Long, String> readForcefulLenderMerchantsCsvFile() {
		Map<Long, String> merchantIdLenderMap = new HashMap<>();
		try {

			String filePath = "/MerchantList/force_assign_lender_merchants";
			InputStream inputStream = this.getClass().getResourceAsStream(filePath);
			File file = new File("/tmp/force_assigned_lender_merchants.csv");
			FileUtils.copyInputStreamToFile(inputStream, file);
			List<String[]> fileRows;
			Reader fileReader = new FileReader(file);
			CSVReader csvReader = new CSVReader(fileReader);
			fileRows = csvReader.readAll();
			for(String[] row : fileRows) {
				merchantIdLenderMap.put(Long.parseLong(row[0]), row[1]);
			}
			FileUtil.deleteFile(file.toPath());
		} catch (Exception e) {
			logger.info("exception while reading force assign lender merchants csv file: {} {}", e, e.getMessage());
		}
		return merchantIdLenderMap;
	}

	public Map<Long, String> forcefulLenderMerchantList() {
        if(!ObjectUtils.isEmpty(forceLendersForMerchants)) {
			return forceLendersForMerchants;
		}
		forceLendersForMerchants = readForcefulLenderMerchantsCsvFile();
		return forceLendersForMerchants;
	}

	public void checkForPendingDisbursalStageSkip(LendingApplication lendingApplication, String requestId){
		try{
			if (LendingConstants.PENDING_DISBURSAL.equalsIgnoreCase(lendingApplication.getLmsStage())) {
				LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
				if(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)){
					logger.info("lending_risk_variable_snapshot not found for {}", lendingApplication.getId());
					return;
				}
				LendingDisbursalModeConfig lendingDisbursalModeConfig = lendingDisbursalModeConfigDao.findTop1ByLenderAndPlatformAndLoanTypeAndStatusOrderByIdDesc(
						lendingApplication.getLender(), "LMS", lendingRiskVariablesSnapshot.getRiskSegment().name(), "ACTIVE"
				);
				if(!ObjectUtils.isEmpty(lendingDisbursalModeConfig)){
					logger.info("skipping PENDING_DISBURSAL stage for {}", lendingApplication.getId());

					lendingApplication.setLmsStage(LendingConstants.SEND_TO_NBFC);
					lendingApplicationDao.save(lendingApplication);

					publishForDisbursal(lendingApplication, false, requestId);

					LmsStageHistory lmsStageHistory = new LmsStageHistory();
					lmsStageHistory.setLendingApplicationId(lendingApplication.getId());
					lmsStageHistory.setLmsStage(LendingConstants.PENDING_DISBURSAL_SKIPPED);
					LmsStageHistory stageSavedEntity = lmsStageHistoryDao.saveAndFlush(lmsStageHistory);

				}
			}
		}
		catch(Exception e){
			logger.error("error on Pending disbursal skip check for {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
		}
	}

	public void publishForDisbursal(LendingApplication lendingApplication,
									Boolean generateReportFlag, String requestId){

		LoanDisbursalDto loanDisbursalDto = new LoanDisbursalDto();
		logger.info("Publishing application_id: {} of loan pending for disbursal to kafka "
						+ "requestId: {}, generateReportFlg: {}",
				lendingApplication.getId(), requestId, generateReportFlag);
		loanDisbursalDto.setApplicationId(lendingApplication.getId());
		loanDisbursalDto.setMerchantId(lendingApplication.getMerchantId());
		loanDisbursalDto.setGenerateReport(generateReportFlag);
		loanDisbursalDto.setRequestId(requestId);
		logger.info("loanDisbursalDto for {} : {}", lendingApplication.getId(), loanDisbursalDto);
		confluentKafkaTemplate.send(
				Objects.requireNonNull(LendingConstants.PUBLISH_LOAN_DISBURSAL_KAFKA_TOPIC),
				lendingApplication.getId().toString(),
				loanDisbursalDto
		);
	}

	public boolean verifyPennyDrop(Long merchantId, Map<String, Object> response) {
		try {
			logger.info("Checking penny drop for merchant:{}", merchantId);

			MerchantDetailsDto merchantDetailsDto = merchantService.fetchMerchantDetails(merchantId);
			BasicDetailsDto basicDetailsDto = merchantDetailsDto.getMerchantDetail();
			if (ObjectUtils.isEmpty(basicDetailsDto)) {
				return false;
			}
			BankDetailsDto bankDetailsDto = merchantDetailsDto.getBankDetail();
			if (ObjectUtils.isEmpty(bankDetailsDto)) {
				return false;
			}

			final LendingPennydrop pennydropInLast15Days = lendingPennydropDao.findByMerchantIdAndAccountNumberInLast15Days(merchantId,
					bankDetailsDto.getAccountNumber());

			if (!ObjectUtils.isEmpty(pennydropInLast15Days)) {
				if (pennydropInLast15Days.getStatus().equalsIgnoreCase("SUCCESS")) {
					logger.info("Penny drop success for merchant:{}", merchantId);
					response.put("penny_drop_failed", false);
					response.put("message", "penny drop success");
					return true;
				}

				if (pennydropInLast15Days.getStatus().equalsIgnoreCase("PENDING")) {
					logger.info("Penny drop is pending for merchant:{} and penny dropId : {}", merchantId, pennydropInLast15Days.getId());
					response.put("penny_drop_failed", false);
					response.put("message", "penny drop pending, please try again after sometime");
					return false;
				}

				if (pennydropInLast15Days.getStatus().equalsIgnoreCase("FAILED")) {
					logger.info("Penny drop failed for merchant:{}", merchantId);
					response.put("penny_drop_failed", true);
					response.put("message", "We are unable to verify your bank account. Re-apply after confirming that your bank account is working and your information is correct.");

					LendingApplication lendingApplication = lendingApplicationDao.findLatestDraftApplication(merchantId, "draft");
					lendingApplication.setStatus("deleted");
					lendingApplication.setManualCibilReason("PENNY_DROP_FAILED");
					lendingApplicationDao.save(lendingApplication);

					LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
					lendingAuditTrial.setApplicationId(lendingApplication.getId());
					lendingAuditTrial.setMerchantId(lendingApplication.getMerchantId());
					lendingAuditTrial.setLoanId(lendingApplication.getExternalLoanId());
					lendingAuditTrial.setUserId(Long.parseLong("0"));
					lendingAuditTrial.setOldStatus("draft");
					lendingAuditTrial.setNewStatus("deleted");
					lendingAuditTrial.setType("APP_STATUS");
					lendingAuditTrialDao.save(lendingAuditTrial);
					return false;
				}
			}
		} catch (Exception e) {
			logger.error("Exception in penny drop for merchant:{}", merchantId, e);
		}
		return false;
	}

    public void savePenalCharges(LendingPaymentSchedule loan, Double penaltyAdjusted) {
		try {
			PenalCharges penalCharge = penalChargesDao.findByLoanId(loan.getId());
			if (ObjectUtils.isEmpty(penalCharge)) {
				return;
			}
			double nachBounceAdjusted = 0;
			double netPenaltyAdjusted = 0;
			if (Objects.nonNull(penalCharge.getDueNachBounce())) {
				nachBounceAdjusted = penalCharge.getDueNachBounce() < penaltyAdjusted ? penalCharge.getDueNachBounce() : penaltyAdjusted;
				netPenaltyAdjusted = penaltyAdjusted - nachBounceAdjusted;
				double paidNachBounce = Objects.nonNull(penalCharge.getPaidNachBounce()) ? penalCharge.getPaidNachBounce() + nachBounceAdjusted : nachBounceAdjusted;
				penalCharge.setDueNachBounce((double) Math.round(penalCharge.getDueNachBounce() - nachBounceAdjusted));
				penalCharge.setPaidNachBounce((double) Math.round(paidNachBounce));
			}

			if (Objects.nonNull(penalCharge.getDuePenalty())) {
				double paidPenalty = Objects.nonNull(penalCharge.getPaidPenalty()) ? penalCharge.getPaidPenalty() + netPenaltyAdjusted : netPenaltyAdjusted;
				penalCharge.setPaidPenalty((double) Math.round(paidPenalty));
				penalCharge.setDuePenalty((double) Math.round(penalCharge.getDuePenalty() - netPenaltyAdjusted));
			}
			penalChargesDao.save(penalCharge);
		} catch (Exception e) {
			logger.error("Exception occured while saving penal charge for loan: {} {} {}", loan.getId(), Arrays.asList(e.getStackTrace()), e);
		}
    }

	public boolean checkIfForeClosureChargesApplicable(Date loanCreatedAt, String lender)  {
		return isForeClosureChargesAllowed && foreClosureChargesWhitelistedLenders.contains(lender) && checkForeClosureChargesEligibility(loanCreatedAt, lender);
	}

	public boolean checkIfForeClosureChargesApplicableKfs(Date loanCreatedAt, String lender)  {
		if("PIRAMAL".equalsIgnoreCase(lender)) {
			return checkChargesEligibility(loanCreatedAt, lender);
		}
		return isForeClosureChargesAllowed && foreClosureChargesWhitelistedLenders.contains(lender) && checkForeClosureChargesEligibility(loanCreatedAt, lender);
	}

	public boolean checkForeClosureChargesEligibility(Date createdAt, String lender)  {
        try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
			String rolloutDate = getLenderForeClosureRolloutDate(lender);
			if (!StringUtils.isEmpty(rolloutDate)) {
				Date date = sdf.parse(rolloutDate);
				if (createdAt.after(date)) {
					return true;
				}
			}
		} catch (Exception e) {
			logger.info("An exception occured while checking fore closure charges eligibilty");
		}
		return false;
	}

	private String getLenderForeClosureRolloutDate(String lender) {
		String date = null;
		if (!StringUtils.isEmpty(lender)) {
			switch (lender) {
				case "LIQUILOANS_P2P":
					date = liquiloansp2pForeClosureChargesRolloutDate;
					break;
				case "LIQUILOANS_P2P_OF":
					date = liquiloansp2pofForeClosureChargesRolloutDate;
					break;
				case "LIQUILOANS_NBFC":
					date = liquiloansnbfcForeClosureChargesRolloutDate;
					break;
				case "TRILLIONLOANS":
					date = trillionloansForeClosureChargesRolloutDate;
					break;
				case "PAYU":
					date = payuForeClosureChargesRolloutDate;
					break;
				case "CREDITSAISON":
					date = creditSaisonForeClosureChargesRolloutDate;
					break;
				case "OXYZO":
					date = oxyzoForeClosureChargesRolloutDate;
					break;
				case "PIRAMAL":
					date = piramalForeClosureChargesRolloutDate;
					break;
				case "ABFL":
					date = abflForeClosureChargesRolloutDate;
					break;
				default:
					break;
			}
		}
		return date;
	}

	private String getLenderChargesRolloutDate(String lender) {
		String date = null;
		if (!StringUtils.isEmpty(lender)) {
            if (lender.equals("PIRAMAL")) {
                date = piramalPenaltyChargesRolloutDate;
            }
		}
		return date;
	}

	public boolean checkChargesEligibility(Date createdAt, String lender)  {
		try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
			String rolloutDate = getLenderChargesRolloutDate(lender);
			if (!StringUtils.isEmpty(rolloutDate)) {
				Date date = sdf.parse(rolloutDate);
				if (createdAt.after(date)) {
					return true;
				}
			}
		} catch (Exception e) {
			logger.info("An exception occured while checking fore closure charges eligibilty");
		}
		return false;
	}

	public ForeClosureDetailDTO calculateForeClosureCharges(LendingPaymentSchedule activeLoan, PaymentDetailsResponseDTO.Data data, Double outstandingPrincipal) {
		ForeClosureDetailDTO foreClosureDetailDTO = new ForeClosureDetailDTO();
		logger.info("going to hit foreclosure config db with lender {} and tenure {}",activeLoan.getNbfc(),activeLoan.getLoanApplication().getTenureInMonths());
		List<ForeClosureConfig> foreClosureConfigList = foreClosureDao.findByLenderAndTenure(activeLoan.getNbfc(),activeLoan.getLoanApplication().getTenureInMonths());
        double duration = calculateDurationInMonths(activeLoan.getStartDate());
		double foreclosureAmount = data.getPrincipalDueAmount();
		double principleOutstanding = (activeLoan.getLoanAmount() - activeLoan.getPaidPrinciple() - activeLoan.getDuePrinciple());

		double advancePerpetualDuePrinciple = fetchExtraEdiPrincipleCollectionForPerpetualDpdLoan(activeLoan.getId());
		logger.info("Advance perpetual due principle {} for loanId {}", advancePerpetualDuePrinciple, activeLoan.getId());

		if(advancePerpetualDuePrinciple > 0) {
			logger.info("Adding advance perpetual due principle {} to principle outstanding for loanId {}", advancePerpetualDuePrinciple, activeLoan.getId());
			principleOutstanding += advancePerpetualDuePrinciple;
		}

		if(FORECLOSURE_COOLING_OFF_SUPPORTED_LENDER.contains(activeLoan.getNbfc()) && checkLoanCoolOffPeriod(activeLoan.getCreatedAt())){
			return null;
		}

		if(LENDER_FORECLOSURE_DATE_CHECK.contains(activeLoan.getNbfc())){
			if(isTodayIsLoanLastDay(activeLoan)) return null;

			if (outstandingPrincipal != null && outstandingPrincipal != 0d){
				logger.info("Using principle outstanding {} for loan: {}",outstandingPrincipal, activeLoan.getId());
				principleOutstanding = outstandingPrincipal;
			}
		}

		if(!CollectionUtils.isEmpty(foreClosureConfigList)) {
			ForeClosureConfig foreClosureConfig = getApplicableForeclosureConfig(foreClosureConfigList, duration);
			if(foreClosureConfig != null) {
                foreClosureDetailDTO.setId(foreClosureConfig.getId());
				foreClosureDetailDTO.setPrincipalOutstanding(foreclosureAmount);
				Double minAmount = foreClosureConfig.getMinAmount();
				if(minAmount == null) minAmount = 0.0;
                logger.info("loan is {} and min amount is {} and foreclosure config rate  is {}  ",activeLoan,minAmount, foreClosureConfig.getRate());
                foreClosureDetailDTO.setForeclosureCharges(Math.max(Math.ceil(( principleOutstanding * foreClosureConfig.getRate())/100.0) , minAmount));
				foreClosureDetailDTO.setGst(Math.ceil((foreClosureDetailDTO.getForeclosureCharges() * foreClosureConfig.getGst())/100.0));
				logger.info("going to return fore closure charges {} ",foreClosureDetailDTO);
				return foreClosureDetailDTO;
			}
		}
		logger.info("fore closure charges not applicable for loanId {} and nbfc {}",activeLoan.getId(),activeLoan.getNbfc());
        return null;
	}

	private  ForeClosureConfig getApplicableForeclosureConfig(List<ForeClosureConfig> foreClosureConfigList, double duration) {
		for (ForeClosureConfig foreClosureConfig : foreClosureConfigList) {
			if ((foreClosureConfig.getDurationFrom() < duration && foreClosureConfig.getDurationTo() >= duration) ||
					(foreClosureConfig.getDurationTo() >= duration && foreClosureConfig.getDurationFrom() == 0 && duration == 0)) {
				return foreClosureConfig;
			}
		}
		return null;
	}


	private double calculateDurationInDays(Date date) {
		logger.info("inside calculate duration for loan {}",date);
		Date currentDate = new Date();
		// Convert milliseconds to days
		long differenceInMillis = currentDate.getTime() - date.getTime();
		return TimeUnit.MILLISECONDS.toDays(differenceInMillis);
	}

	private  double calculateDurationInMonths(Date date) {
		return calculateDurationInDays(date) / NO_OF_DAYS_IN_A_MONTH;
	}


	public boolean isEligibilityErrorResponse(GlobalLimitResponse globalLimitResponse) {
		if(Objects.nonNull(globalLimitResponse) && !globalLimitResponse.isSuccess() && Objects.nonNull(globalLimitResponse.getErrorCode())) {

            return !getExcludedErrorCode().contains(globalLimitResponse.getErrorCode());
        }
		return false;
	}

	public Long getRefreshCountDownMinutes(GlobalLimitResponse globalLimitResponse){
		return (!ObjectUtils.isEmpty(globalLimitResponse)
				&& !ObjectUtils.isEmpty(globalLimitResponse.getData())
				&& !ObjectUtils.isEmpty(globalLimitResponse.getData().getRefreshCountDownMinutes()))
				? globalLimitResponse.getData().getRefreshCountDownMinutes() : null;
	}

	private List<String> getExcludedErrorCode() {
		List<String> excludedCodes = new ArrayList<>();
		if (StringUtils.hasLength(excludedErrorCodes)) {
			try {
				excludedCodes = Arrays.asList(excludedErrorCodes.split(","));
			} catch (Exception e) {
				logger.error("Error in parsing excluded error code list ",e);
			}
		}
		return excludedCodes;
	}

	public String getLenderRejectedMapping(String lender) {
		if (!ObjectUtils.isEmpty(rejectedLenderMapping)) {
			return rejectedLenderMapping.getOrDefault(lender, lender);
		}
		rejectedLenderMapping.put(MUTHOOT.name(), "MFL");
		rejectedLenderMapping.put(ABFL.name(), "ABFL");
		rejectedLenderMapping.put(PIRAMAL.name(), "PIRAMAL");
		rejectedLenderMapping.put(CAPRI.name(), "CAPRI");
		rejectedLenderMapping.put(PAYU.name(), "PAYU");
		rejectedLenderMapping.put(CREDITSAISON.name(), "CREDITSAISON");
		rejectedLenderMapping.put(SMFG.name(), "SMFG");
		rejectedLenderMapping.put(OXYZO.name(), "OXYZO");
		return rejectedLenderMapping.getOrDefault(lender, lender);
	}

	public LendingLoanInsurance getInsuranceDetails(Long applicationId, String lender, String status) {
		if (ObjectUtils.isEmpty(applicationId) || ObjectUtils.isEmpty(lender) || ObjectUtils.isEmpty(status)) {
			return null;
		}

		return lendingLoanInsuranceDao.findByApplicationIdAndLenderAndStatus(
				applicationId,
				lender,
				status);
	}
	public List<EnachModeDTO> getEnachModes(Long merchantId) {
		LendingNachBankResponseDTO lendingNachBankResponse = getEnachBankMode(merchantId);

		if (Objects.isNull(lendingNachBankResponse)) {
			return null;
		}
//		String availableEnachModes = "UPI, NB_DC, ADHAAR";
		StringBuilder enachModes = new StringBuilder();
		if (lendingNachBankResponse.getUpiMandate() != null) {
			if (lendingNachBankResponse.getUpiMandate()) {
				enachModes.append("UPI,");
			}
		} else {
			logger.error("UPI Mandate mode for Nach Bank is null");
		}
		if(EnachMode.BOTH.name().equalsIgnoreCase(lendingNachBankResponse.getMode()))
			enachModes.append("NB_DC");
		else if (EnachMode.NB_DC.name().equalsIgnoreCase(lendingNachBankResponse.getMode()))
			enachModes.append("NB_DC");
		else if(EnachMode.ADHAAR.name().equalsIgnoreCase(lendingNachBankResponse.getMode()))
			enachModes.append("ADHAAR");

		String availableEnachModes = enachModes.toString();
		return Arrays.stream(availableEnachModes.split(","))
				.filter(mode -> (!Objects.equals(mode.trim(), "")))
				.map(mode -> new EnachModeDTO(mode.trim(), true, null))
				.collect(Collectors.toList());
	}

	public double fetchExtraEdiInterestCollectionForPerpetualDpdLoan(Long lpsId){
		LendingLedger lendingLedger = fetchAdvanceEdi(lpsId);
		if(!ObjectUtils.isEmpty(lendingLedger)){
			return Math.abs(lendingLedger.getInterest());
		}
		return 0d;
	}

	public double fetchExtraEdiPrincipleCollectionForPerpetualDpdLoan(Long lpsId){
		LendingLedger lendingLedger = fetchAdvanceEdi(lpsId);
		if(!ObjectUtils.isEmpty(lendingLedger)){
			return Math.abs(lendingLedger.getPrinciple());
		}
		return 0d;
	}

	public LendingLedger fetchAdvanceEdi(Long lpsId) {
		Optional<LendingPaymentScheduleLendingCommon> lendingPaymentScheduleLendingCommon = lendingPaymentScheduleLendingCommonDao.findById(lpsId);
		if(lendingPaymentScheduleLendingCommon.isPresent() && Y.name().equalsIgnoreCase(lendingPaymentScheduleLendingCommon.get().getPerpetualDpdAdjusted())) {
			logger.info("checking for collection of extra interest for perpetual dpd loan id : {}", lendingPaymentScheduleLendingCommon.get().getId());
			return lendingLedgerDao.findAdvanceEdiDueOfPerpetualDpdLoan(lpsId, DateTimeUtil.getCurrentDayStartTime());
		}
		return null;
	}

	public boolean checkLoanCoolOffPeriod(Date createdAt) {
		double	durationInDays = calculateDurationInDays(createdAt);
		if(durationInDays < COOL_OFF_PERIOD_DAYS) return true;
		return false;
	}

	public boolean isTodayIsLoanLastDay(LendingPaymentSchedule activeLoan) {
		logger.info("Checking for last day of the loan: {} with EDI Remaining Count: {}", activeLoan.getId(), activeLoan.getEdiRemainingCount());
		Date loanClosingDate = activeLoan.getTentativeClosingDate();
		Date todayDate = new Date();
		return (DateUtils.isSameDay(loanClosingDate, todayDate) || todayDate.after(loanClosingDate) || activeLoan.getEdiRemainingCount() <= 0);
	}

	public boolean isApplicableForAggregationFlow(Long merchantId, Long applicationId){
		try{
			ExperimentConfigResponseDTO experimentConfigResponseDTO = launchLabsHandler.experimentConfig(Long.valueOf(isAggregationFlowApplicableExperimentId), merchantId);
			if(Objects.nonNull(experimentConfigResponseDTO) && lenderAggregationScreens.contains(experimentConfigResponseDTO.getVariationId())){
				logger.info("lender aggregation flow applicable for merchantId {}", merchantId);
				if(!ObjectUtils.isEmpty(applicationId)){
					LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
					lendingAuditTrial.setApplicationId(applicationId);
					lendingAuditTrial.setMerchantId(merchantId);
					lendingAuditTrial.setType(LendingViewStates.LENDER_AGGREGATION.name());
					lendingAuditTrial.setLoanId("BPL"+applicationId);
					lendingAuditTrial.setOldStatus(experimentConfigResponseDTO.getVariationId());
					lendingAuditTrialDao.save(lendingAuditTrial);
				}
				return true;
			}
		} catch (Exception ex) {
			logger.error("Exception occurred while deciding aggregation flow :{}", ex.getMessage());
		}
		logger.info("lender aggregation flow not applicable for merchantId {}", merchantId);
		return false;
	}

	public String getLenderAggregationScreen(Long applicationId){
		try{
			LendingAuditTrial lendingAuditTrial = lendingAuditTrialDao.findByApplicationIdAndType(applicationId, LendingViewStates.LENDER_AGGREGATION.name());
			if(!ObjectUtils.isEmpty(lendingAuditTrial)){
				return lendingAuditTrial.getOldStatus();
			}
		} catch (Exception ex){
			logger.error("Exception occurred while fetching aggregation screen for applicationId {} {}, {}", applicationId, ex.getMessage(), Arrays.asList(ex.getStackTrace()));
		}
		return null;
	}

	public boolean checkIfUpiAutoPayNotRequired(LendingApplication lendingApplication) {
		logger.info("checking if UPI autopay not required for applicationId : {}", lendingApplication.getId());
		if ( lendingApplication.getLender() == null || lendingApplication.getId() == null)  {
			return true;
		}
		NachMandateEligibilityConfig nachMandateEligibilityConfig = nachMandateEligibilityConfigDao.findNachMandateEligibilityConfigLenderAndLoanAmountWise(lendingApplication.getLender(), lendingApplication.getLoanAmount());
        logger.info("nachMandateEligibilityConfig {}",nachMandateEligibilityConfig);
		return (ObjectUtils.isEmpty(nachMandateEligibilityConfig) || !nachMandateEligibilityConfig.getUpiAutopayRequired());
	}

	public Boolean validateToken(String token) {
		return apiTokens.contains(token);
	}

	public boolean isLenderPricingApplicableMerchant(Long merchantId){
		return LoanUtil.isRolledOutByPercentage(String.valueOf(merchantId), (lenderPricingEligibleMerchantsPercent));
	}

	public MaxPricingValuesDTO getMaxPricingValues(LendingRiskVariables lendingRiskVariables, Integer tenure){
		String riskSegment = lendingRiskVariables.getRiskSegment();
		String riskGroup = lendingRiskVariables.getRiskGroup();
		String pincodeColor = lendingRiskVariables.getPincodeColor().name();
		String rejectedLenders = lendingRiskVariables.getRejectedLenders();
		LendingLenderPricingDao.MaxValuesDto maxValuesDto= null;
		logger.info("query params -> {} {} {} {} {}", riskGroup, riskSegment, tenure, pincodeColor, rejectedLenders);

		if (StringUtils.isEmpty(rejectedLenders)) {
			//fetch only active max pricing here.
			maxValuesDto = lendingLenderPricingDao.findMaxInterestRateBySegmentAndRiskGroupAndTenureAndPincodeColorAndStatus(riskSegment, riskGroup, tenure, pincodeColor, "ACTIVE");
		} else {
			Set<String> rejectedLendersList = StringUtils.commaDelimitedListToSet(lendingRiskVariables.getRejectedLenders());
			maxValuesDto = lendingLenderPricingDao.findMaxInterestRateByRiskVariables(riskSegment, riskGroup, tenure, pincodeColor, rejectedLendersList, "ACTIVE");

		}
		if(ObjectUtils.isEmpty(maxValuesDto)
		|| ObjectUtils.isEmpty(maxValuesDto.getMaxApr())
				|| ObjectUtils.isEmpty(maxValuesDto.getMaxIrr())
				|| ObjectUtils.isEmpty(maxValuesDto.getMaxProcessingFeeRate())
				|| ObjectUtils.isEmpty(maxValuesDto.getMaxInterestRate())){
			logger.info("max pricing values not found values -> {} {} {} {}", maxValuesDto.getMaxApr(), maxValuesDto.getMaxIrr(), maxValuesDto.getMaxProcessingFeeRate(), maxValuesDto.getMaxInterestRate());
			return null;
		}
		MaxPricingValuesDTO maxPricingValuesDTO = new MaxPricingValuesDTO();
		maxPricingValuesDTO.setMaxProcessingFeeRate(maxValuesDto.getMaxProcessingFeeRate());
		maxPricingValuesDTO.setMaxApr(maxValuesDto.getMaxApr());
		maxPricingValuesDTO.setMaxIrr(maxValuesDto.getMaxIrr());
		maxPricingValuesDTO.setMaxInterestRate(maxValuesDto.getMaxInterestRate());
		logger.info("fetched max pricing values -> {}", maxPricingValuesDTO);
		return maxPricingValuesDTO;
	}

	public Double getAmountPaidThroughQr(LendingPaymentSchedule lendingPaymentSchedule) {
		return lendingLedgerDao.findSettlementAmount(lendingPaymentSchedule.getId());
	}

	public Double getAmountPaidThroughQrPer(LendingPaymentSchedule lendingPaymentSchedule) {
		Double settlementAmount = getAmountPaidThroughQr(lendingPaymentSchedule);
		if (!ObjectUtils.isEmpty(settlementAmount) && !ObjectUtils.isEmpty(settlementAmount)) {
			return (settlementAmount / lendingPaymentSchedule.getPaidAmount()) * 100;
		}
		return 0D;
	}
	public Double getAmountPaidThroughQr(LendingPaymentScheduleSlave lendingPaymentSchedule) {
		return lendingLedgerDao.findSettlementAmount(lendingPaymentSchedule.getId());
	}

	public Double getAmountPaidThroughQrPer(LendingPaymentScheduleSlave lendingPaymentSchedule) {
		Double settlementAmount = getAmountPaidThroughQr(lendingPaymentSchedule);
		if (!ObjectUtils.isEmpty(settlementAmount) && !ObjectUtils.isEmpty(settlementAmount)) {
			return (settlementAmount / lendingPaymentSchedule.getPaidAmount()) * 100;
		}
		return 0D;
	}

	public void setEligibleLoan(LendingEligibleLoan eligibleLoan, Double interestRate, BigDecimal processingFee, Double loanAmount){
		eligibleLoan.setRateOfInterest(interestRate);
		Double interestAmt = (eligibleLoan.getAmount() * (eligibleLoan.getRateOfInterest() * eligibleLoan.getTenureInMonths()) / 100) ;
		Double ediAmount = Math.ceil((eligibleLoan.getAmount() + interestAmt) / eligibleLoan.getEdiCount());
		Double repayment = ediAmount * eligibleLoan.getEdiCount();
		eligibleLoan.setProcessingFee(processingFee.intValue());
		eligibleLoan.setRepayment(repayment.intValue());
		eligibleLoan.setEdi(ediAmount.intValue());
		eligibleLoan.setIrr(lendingApplicationServiceV2.getApr(eligibleLoan.getEdiCount(), Double.valueOf(eligibleLoan.getEdi()), loanAmount, eligibleLoan.getMerchantId(), null));
		eligibleLoan.setApr(lendingApplicationServiceV2.getApr(eligibleLoan.getEdiCount(), Double.valueOf(eligibleLoan.getEdi()), loanAmount - processingFee.intValue(), eligibleLoan.getMerchantId(), null));
		logger.info("eligibleLoan values -> {}, {}, {}, {}, {}, {}", eligibleLoan.getApr(), eligibleLoan.getIrr(), eligibleLoan.getProcessingFee(), eligibleLoan.getRateOfInterest(), eligibleLoan.getRepayment(), eligibleLoan.getEdi());
		eligibleLoanDao.save(eligibleLoan);
	}


	public static boolean isRolledOutByPercentage(String value, List<Integer> validDigits) {
		// Extract the last digit of the number
		int num = Integer.parseInt(value);
		int lastDigit = num % 10;

		return validDigits.contains(lastDigit);

	}

	public static boolean isRolledOutByPercentage(String value, Integer percentage) {
		// Percentage not provided or <= 0, not rolled out
		if(percentage == null || percentage <= 0) return false;
		// 100% rollout
		if(percentage >= 100) return true;
		// Blank value cannot be rolled out
		if(StringUtils.isEmpty(value)) return false;
		// extracting last two digit of value
		int lastTwoDigits;
		try {
			lastTwoDigits = Integer.parseInt(value.substring(Math.max(value.length() - 2, 0)));
		} catch (NumberFormatException e) {
			logger.error("value is not a number: {}", value);
			// Invalid value, not rolled out
			return false;
		}
		// Check if the last two digits are less than the percentage
		return lastTwoDigits < percentage;
	}

	public boolean checkIfUPIAutoPayIsActive(long merchantId, String lender, Long applicationId)  {
		logger.info("checkIfUPIAutoPayAllowed: Checking for UPI autopay mandate merchant : {} and lender :{}", merchantId, lender);
		try {
			AutoPayUPI autoPayUpi = autoPayUPIDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
			if (autoPayUpi != null && autoPayUpi.getStatus() != null  && AutoPayStatusEnum.ACTIVE.name().equalsIgnoreCase(autoPayUpi.getStatus().name())) {
				logger.info("checkIfUPIAutoPayIsActive: autopay exist for merchnatId :{}, applicationId : {}, lender : {}", merchantId, applicationId, lender);
				return true;
			}
		} catch (Exception e) {
			logger.info("checkIfUPIAutoPayAllowed: Exception in mandate fetch from DB application :{} merchant : {} and lender :{}",applicationId, merchantId, lender);
		}
		return false;
	}

	public boolean checkIfUpiAutoPayNachNotRequired(LendingApplication lendingApplication) {
		if (!checkIfUPIAutoPayIsActive(lendingApplication.getMerchantId(), lendingApplication.getLender(), lendingApplication.getId())) {
			return false;
		}
		NachMandateEligibilityConfig nachMandateEligibilityConfig = nachMandateEligibilityConfigDao.findNachMandateEligibilityConfigLenderAndLoanAmountWise(lendingApplication.getLender(), lendingApplication.getLoanAmount());

		return (ObjectUtils.isEmpty(nachMandateEligibilityConfig) || !nachMandateEligibilityConfig.getUpiAutopayNachRequired());
	}

	public void piramalPenaltyPosting(LendingApplicationLenderDetails lendingApplicationLenderDetails, PenaltyFeeLedger penaltyFeeLedger, double amount, String type) {
		PiramalForeclosureChargesRequestDto piramalForeclosureChargesRequestDto = createPiramalPostChargesDto(lendingApplicationLenderDetails, penaltyFeeLedger,amount,type);
		logger.info("Piramal: posting penalty  to lender {}", piramalForeclosureChargesRequestDto);
		try {
			NbfcResponseDto nbfcResponseDto = nbfcLenderGateway.invoke(objectMapper.writeValueAsString(piramalForeclosureChargesRequestDto), NbfcResponseDto.class,nbfcBaseUrl+nbfcChargePosting);
			logger.info("Piramal: response penalty  posting request :{} and response : {}", objectMapper.writeValueAsString(piramalForeclosureChargesRequestDto), nbfcResponseDto);
			if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
				logger.info("Piramal: penalty charges posted to lender{}",nbfcResponseDto);
				penaltyFeeLedger.setIsPosted(Boolean.TRUE);
				penaltyFeeLedgerDao.save(penaltyFeeLedger);
			}
			else {
				penaltyFeeLedger.setPostingStatus("FAILED");
				penaltyFeeLedgerDao.save(penaltyFeeLedger);
				logger.info("Piramal: penalty  posting failed to request {} response {}",piramalForeclosureChargesRequestDto, nbfcResponseDto);
			}
		} catch (JsonProcessingException e) {
			logger.error("error in posting the penalty");
		}
	}

	private PiramalForeclosureChargesRequestDto createPiramalPostChargesDto(LendingApplicationLenderDetails lendingApplicationLenderDetails, PenaltyFeeLedger penaltyFeeLedger, double amount, String type) {
		return PiramalForeclosureChargesRequestDto.builder()
				.applicationId(lendingApplicationLenderDetails.getApplicationId())
				.productName("LENDING")
				.lender(Lender.PIRAMAL.name())
				.payload(PiramalForeclosureChargesRequestDto.Payload.builder()
						.productId("BRTPE")
						.uniqueReferenceId(String.valueOf(penaltyFeeLedger.getId()))
						.loanAccountNumber(lendingApplicationLenderDetails.getLan())
						.adviseType(RECEIVABLE)
						.adviseAmount(amount)
						.adviseDate(penaltyFeeLedger.getCreatedAt())
						.feeTypeCode(type)
						.isTopup(false)
						.build())
				.build();
	}


	public LendingApplication fetchParentApplication(Long applicationId){
		LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(applicationId);
		if(ObjectUtils.isEmpty(lendingApplicationDetails)) {
			logger.info("No lending application details found for applicationId : {}", applicationId);
			throw new RuntimeException("unable to fetch lending application details " + applicationId);
		}
		LendingApplication prevApplication = lendingApplicationDao.findById(lendingApplicationDetails.getPrevAppId()).orElse(null);
		if(ObjectUtils.isEmpty(prevApplication)) {
			logger.info("No previous lending application found for topup application with applicationId : {} and prevAppId : {}", applicationId, lendingApplicationDetails.getPrevAppId());
			throw new RuntimeException("unable to fetch parent application " + applicationId);
		}
		return prevApplication;

	}

	public Double calculatePiramalPenalty(LendingPaymentSchedule activeLoan) {
		try {
			List<PenaltyFeeConfigSlave> penaltyFeeConfigSlaveList = penaltyFeeConfigDaoSlave.findByVersionAndStatusAndLenderOrderByMinAmountAsc(2, true, activeLoan.getNbfc());
			if (penaltyFeeConfigSlaveList.isEmpty()) {
				logger.info("No penalty fee config found for loan: {} with lender: {}", activeLoan.getId(), activeLoan.getNbfc());
				return 0d;
			}
			double rate = penaltyFeeConfigSlaveList.get(0).getPenalty();
			double penalty = (activeLoan.getOverdueAmount() * rate) / 100;
			// AS per lender confirmation we are removing gst from penalty
//         += penalty * 0.18;
			penalty = Math.ceil(penalty);
			return penalty;
		}catch (Exception e){
			logger.error("Exception occurred while calculating piramal penalty for loan: {} exception: {} , {}", activeLoan, Arrays.asList(e.getStackTrace()), e.getMessage());
		}
		return 0d;
	}
	public Date getLastPenaltyDate(LocalDate currentDate) {
		LocalDate lastMonthEndDate;
		if (currentDate.getMonthValue() == 3) {
			// If current month is March, we need February's end date (28th)
			lastMonthEndDate = currentDate.minusMonths(1).withDayOfMonth(28);
		} else {
			// For all other months, we need previous month's 30th
			lastMonthEndDate = currentDate.minusMonths(1).withDayOfMonth(30);
		}

		try {
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			String formattedDate = dateFormat.format(Date.from(lastMonthEndDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
			return dateFormat.parse(formattedDate);
		} catch (ParseException e) {
			logger.error("Error parsing date: {}", e.getMessage(), e);
			return null;// or handle the exception as needed
		}
	}

	public void postPiramalPenalty(LendingPaymentSchedule activeLoan, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
		try {
			LocalDate currentDate = LocalDate.now();
			Date lastPenaltyDate = getLastPenaltyDate(currentDate);
			PenaltyFeeLedger penaltyFeeLedgerApplied = penaltyFeeLedgerDao.findTop1PenaltyFeeOrderByIdDesc(activeLoan.getId());
			// NEED TO GET THE LIST AS AT THE TIME OF CLOSURE WE CAN HAVE MUTIPLE UNPOSTED PENALTIES
			List<PenaltyFeeLedger> penaltyFeeLedgerList = new ArrayList<>();
			if (lastPenaltyDate != null) {
				penaltyFeeLedgerList = penaltyFeeLedgerDao.findPenaltyFeeByCreatedAtAfterOrderByIdDesc(
						activeLoan.getId(), lastPenaltyDate);
			}
			if (penaltyFeeLedgerList == null && penaltyFeeLedgerApplied != null)
				penaltyFeeLedgerList.add(penaltyFeeLedgerApplied);
			logger.info("penalty to be posted is  {}", penaltyFeeLedgerList);
			if (penaltyFeeLedgerList != null && !penaltyFeeLedgerList.isEmpty()) {
				for (PenaltyFeeLedger penalty : penaltyFeeLedgerList) {
					if (penalty == null || penalty.getIsPosted()) {
						logger.info("No unpaid penalty exist for loanId {} ", activeLoan.getId());
						return;
					}
					if (penalty != null && penalty.getAmount() != null && !penalty.getIsPosted()) {
						piramalPenaltyPosting(lendingApplicationLenderDetails, penalty, penalty.getAmount() * -1, LPC_WO_GST.name());
					}
				}
			}
		}catch (Exception e){
			logger.error("Piramal : Error in posting penalty to lender {} {}",Arrays.asList(e.getStackTrace()),e.getMessage());
		}
	}

	public void savePenalCharges(LendingPaymentSchedule lendingPaymentSchedule, boolean isNachBounce, double penaltyFee, double nachBounceCharge) {
		try {
			logger.info("Saving Penal Charges for loan: {} with penaltyFee: {}, is nach bounce: {}", lendingPaymentSchedule.getId(), penaltyFee, isNachBounce);
			PenalCharges penalCharge = penalChargesDao.findByLoanId(lendingPaymentSchedule.getId());
			double nachCharge = isNachBounce ? nachBounceCharge : 0;
			if (Objects.isNull(penalCharge)) {
				penalCharge = new PenalCharges(lendingPaymentSchedule.getId(), lendingPaymentSchedule.getMerchantId(),
						lendingPaymentSchedule.getNbfc(), penaltyFee, 0, nachCharge, 0);
			} else {
                penalCharge.setDuePenalty(penalCharge.getDuePenalty() + penaltyFee);
				penalCharge.setDueNachBounce(penalCharge.getDueNachBounce() + nachCharge);
			}

			penalChargesDao.save(penalCharge);
		} catch (Exception e) {
			logger.info("Exception occurred while saving penal charges for loan: {}, {}", lendingPaymentSchedule.getId(), e.getMessage(), e);
		}
	}

	private Boolean isBTApplication(LendingApplication lendingApplication) {
		try {
			LendingApplication prevApplication = fetchParentApplication(lendingApplication.getId());
			if(!ObjectUtils.isEmpty(prevApplication) && LoanUtilV3.LIQUILOANS_BT_LENDERS.contains(prevApplication.getLender())) {
				return true;
			}
		} catch (Exception e) {
			logger.error("Exception in checking BT application for applicationId {} {}", lendingApplication.getId(), Arrays.asList(e.getStackTrace()));
		}
		return false;
	}
}

