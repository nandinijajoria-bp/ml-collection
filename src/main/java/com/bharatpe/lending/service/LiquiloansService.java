package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.Loan;
import com.bharatpe.common.enums.Status;
import com.bharatpe.common.utils.NotificationUtil;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.BharatPeEnachResponseDTO;
import com.bharatpe.lending.common.dto.KafkaAudit;
import com.bharatpe.lending.common.dto.NotificationPayloadDto;
import com.bharatpe.lending.common.entity.LiquiloansDirectDisbursalRawResponse;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.common.enums.LoanSettlementMechanism;
import com.bharatpe.lending.common.enums.VpaTrackingStatus;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.LendingNotificationService;
import com.bharatpe.lending.common.service.SherlocLoanStatusChangeService;
import com.bharatpe.lending.common.service.merchant.constants.Constants;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.entity.LoanAgreement;
import com.bharatpe.lending.entity.LoanPaymentOrder;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LendingPayoutType;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV3.dto.LenderEdIScheduleResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.PiramalGetLoanResponseDto;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.loanV3.services.associationsV2.AssociationServiceUtil;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl.PiramalGetLoanDetails;
import com.bharatpe.lending.util.DisbursalStageMapping;
import com.bharatpe.lending.util.Finance;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.bharatpe.lending.common.enums.VpaTrackingStatus.DISBURSED;
import static com.bharatpe.lending.constant.KfsConstants.KFS_S3_KEY_PREFIX;
import static com.bharatpe.lending.constant.KfsConstants.SANCTION_LOAN_AGREEMENT_S3_KEY_PREFIX;

@Component
public class LiquiloansService {

    private final Logger logger = LoggerFactory.getLogger(LiquiloansService.class);

    @Autowired
    LendingHmacCalculator lendingHmacCalculator;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LoanAgreementDao loanAgreementDao;

//    @Autowired
//    MerchantDao merchantDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    Environment env;

    @Autowired
    DisbursalSettlementDao disbursalSettlementDao;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    VerifyOTPService verifyOTPService;

//    @Autowired
//    SmsServiceHandler smsServiceHandler;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Value("${aws.s3.loan.agreement.bucket}")
    private String bucket;

//    @Autowired
//    ValidateDao validateDao;
//
//    @Autowired
//    SettlementScheduleDao settlementScheduleDao;

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    @Qualifier("ConfluentKafkaTemplate")
    KafkaTemplate<String, Object> confluentKafkaTemplate;

//    @Autowired
//    CreditApplicationDao creditApplicationDao;

//    @Autowired
//    MerchantDocumentProofOcrDaoSlave merchantDocumentProofOcrDaoSlave;

//    @Autowired
//	MerchantSummaryDao merchantSummaryDao;

    @Autowired
    ExperianDao experianDao;

//    @Autowired
//    CreditApplicationAddressDao creditApplicationAddressDao;

    @Autowired
    LiquiloansDirectDisbursalRawResponseDao liquiloansDirectDisbursalRawResponseDao;

//    @Autowired
//    LendingTlDetailsDao lendingTlDetailsDao;

    @Autowired
    MerchantUpdateService merchantUpdateService;

    @Autowired
    LendingEDIScheduleDao lendingEDIScheduleDao;

    @Autowired
    LdcVirtualAccountDao ldcVirtualAccountDao;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LendingCategoryDao lendingCategoryDao;

    @Autowired
    RedisNotificationService redisNotificationService;

    @Autowired
    LenderVirtualAccountDao lenderVirtualAccountDao;

    @Autowired
    EnachHandler enachHandler;

    @Autowired
    LendingNotificationService lendingNotificationService;

    @Autowired
    LendingEdiExceptionDao lendingEdiExceptionDao;

    @Autowired
    LoanPaymentOrderDao loanPaymentOrderDao;

    @Autowired
    PaymentService paymentService;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    NotificationUtil notificationUtil;

    @Autowired
    LendingVpaDetailsDao lendingVpaDetailsDao;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    private static String secretKey;

    private static String SID;

    @Value("${loan.redemption.topic}")
    String TOPIC;

    @Autowired
    MerchantSummaryHandler merchantSummaryHandler;

    @Autowired
    MerchantService merchantService;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    LiquiloansService liquiloansService;

    @Autowired
    LendingGstDao lendingGstDao;

    @Autowired
    FunnelService funnelService;

    @Autowired
    @Lazy
    LiquiloansAsyncService liquiloansAsyncService;

    @Autowired
    private LoanDashboardService loanDashboardService;

    @Value("${enable.backdated.loan:true}")
    Boolean backdatedLoanEnabled;

    @Value("${backdated.loan.eligible.lenders:ABFL}")
    String backDatedLoanEligibleLenders;

    ExecutorService executorService = Executors.newFixedThreadPool(10);
    @Autowired
    private LendingLedgerDao lendingLedgerDao;

    @Autowired
    private PiramalGetLoanDetails piramalGetLoanDetails;

    @Autowired
    private SherlocLoanStatusChangeService sherlocLoanStatusChangeService;

    @Autowired
    AssociationServiceUtil associationServiceUtil;

    public void publishForDisbursal(Long lendingAppId) {

        Map<String, String> payloadMap = new HashMap<>();
        try {
            logger.info("Publishing aaplication_id: {} of loan pending for disbursal to kafka", lendingAppId);
            payloadMap.put("lending_application_id", lendingAppId.toString());
            kafkaTemplate.send(Objects.requireNonNull(env.getProperty("kafka.topic.lending.payout")), lendingAppId.toString(), payloadMap);
        } catch (Exception e) {
            logger.error("Error publishing lending application: {} to kafka for disbursal", lendingAppId);
        }
    }


    public ResponseDTO checkLoanStatus(LiquiloanCallbackRequestDTO callbackRequestDto, LiquiloansDirectDisbursalRawResponse liquiloansDirectDisbursalRawResponse) {
        logger.info("Fetching lending application for given application_id:{} and nbfc_id:{}", callbackRequestDto.getApplicationId(), callbackRequestDto.getNbfcId());
        liquiloansDirectDisbursalRawResponse.setApiName("APPROVELOAN");
        liquiloansDirectDisbursalRawResponse.setRequest(callbackRequestDto.toString());
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndNbfcId(Long.parseLong(callbackRequestDto.getApplicationId()), callbackRequestDto.getNbfcId());
            if (callbackRequestDto.getNbfcId() == null && lendingApplication == null) {
                lendingApplication = lendingApplicationDao.findByMamtaLoan(Long.parseLong(callbackRequestDto.getApplicationId()));
            }
            if (lendingApplication == null) {
                logger.info("Approve loan not found for loanId:{}", callbackRequestDto.getApplicationId());
                return new ResponseDTO(false, "loan application not found", null, null);
            }
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndStatus(lendingApplication.getMerchantId(), "ACTIVE");
            if (lendingPaymentSchedule != null) {
                logger.info("Merchant Has Already Active Loan for merchant:{}", lendingApplication.getMerchantId());
                return new ResponseDTO(false, "Merchant Has Already Active Loan", null, null);
            }

            List<LdcVirtualAccount> ldcVirtualAccount = ldcVirtualAccountDao.getByMerchantId(lendingApplication.getMerchantId());
            if (ldcVirtualAccount.isEmpty()) {
                List<LenderVirtualAccount> lendingVirtualAccount = lenderVirtualAccountDao.getByMerchantId(lendingApplication.getMerchantId());
                if (lendingVirtualAccount.isEmpty()) {
                    logger.info("LDC Virtual account not found for merchant:{}", lendingApplication.getMerchantId());
                    return new ResponseDTO(false, "ldc virtual account not found", null, null);
                }
            }
            liquiloansDirectDisbursalRawResponse.setMerchantId(lendingApplication.getMerchantId());
            liquiloansDirectDisbursalRawResponse.setApplicationId(lendingApplication.getId());
            liquiloansDirectDisbursalRawResponse.setLoanId(lendingApplication.getExternalLoanId());
            liquiloansDirectDisbursalRawResponse.setLiquiloanId(lendingApplication.getNbfcId());
            lendingApplication.setLoanDisbursalStatus("PROCESSING");
            lendingApplicationDao.save(lendingApplication);
            updateLendingVpaStage(lendingApplication, VpaTrackingStatus.PROCESSING.name());
            if (lendingApplication.getLoanType().equals(LoanType.HALF_TOPUP.name()) || lendingApplication.getLoanType().equals(LoanType.IO_TOPUP.name())) {
                logger.info("Creating LPS directly for applicationId:{}", lendingApplication.getId());
                populateLendingPaymentSchedule(new LiquidatePostPayoutStatusUpdateRequestDTO(String.valueOf(lendingApplication.getId()), String.valueOf(lendingApplication.getMerchantId()), "SUCCESS"));
            } else {
                publishForDisbursal(lendingApplication.getId());
            }
            return new ResponseDTO(true, null, null, null);
        } catch (Exception e) {
            logger.error("Error occured while updating lending application disbursal status", e);
            return new ResponseDTO(false, "Error occurred while updating loan", null, null);
        }
    }

    public void updateLendingVpaStage(LendingApplication lendingApplication, String stage) {
        LendingVpaDetails lendingVpaDetails = lendingVpaDetailsDao.findTopByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
        if (!ObjectUtils.isEmpty(lendingVpaDetails)) {
            lendingVpaDetails.setStatus(stage);
            lendingVpaDetailsDao.save(lendingVpaDetails);
        }
    }


    public ResponseEntity<String> populateLendingPaymentSchedule(LiquidatePostPayoutStatusUpdateRequestDTO postPayoutRequestDto) {
        logger.info("Create LPS request:{}", postPayoutRequestDto);
        LendingApplication lendingApplication = null;
        LendingPaymentSchedule lendingPaymentSchedule = null;
        Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(Long.valueOf(postPayoutRequestDto.getMerchantId()));
        try {
            logger.info("Fetching merchant for the merchant id {}", postPayoutRequestDto.getMerchantId());
//            Optional<Merchant> merchant = merchantDao.findById(Long.parseLong(postPayoutRequestDto.getMerchantId()));
            if (!basicDetailsDto.isPresent()) {
                logger.error("Merchant not found for the merchant id {}", postPayoutRequestDto.getMerchantId());
                return new ResponseEntity<>("Invalid merchantId", HttpStatus.BAD_REQUEST);
            }
            logger.info("Fetching loan application on the basis of application id and merchant");
            lendingApplication =
              lendingApplicationDao.findByIdAndMerchantId(Long.parseLong(postPayoutRequestDto.getApplicationId()),
                basicDetailsDto.get().getId());


//    		if(lendingApplication==null || !lendingApplication.getLoanDisbursalStatus().equals("PROCESSING") || !lendingApplication.getDisbursalPartner().equals("BHARATPE")){
//    			logger.error("Loan application for loanId {} and merchantId {} not found.",postPayoutRequestDto.getApplicationId(),merchant);
//    			return new ResponseEntity<>("Invalid applicationId", HttpStatus.BAD_REQUEST);
//    		}
            if (lendingApplication == null) {
                logger.error("Loan application for loanId {} and merchantId {} not found.", postPayoutRequestDto.getApplicationId(), basicDetailsDto);
                return new ResponseEntity<>("Invalid applicationId", HttpStatus.BAD_REQUEST);
            }
            logger.info("Changing loan_disbursal_status to 'DISBURSED'");
            lendingApplication.setLoanDisbursalStatus("DISBURSED");
            lendingApplication.setDisburseTimestamp(new Date());
            lendingApplication.setAccountType("HINDON".equals(lendingApplication.getLender()) || "MAMTA".equals(lendingApplication.getLender()) || "LIQUILOANS_NBFC".equals(lendingApplication.getLender()) ? "NBFC_FUNDS" : "INVESTOR_FUNDS");
            lendingApplicationDao.save(lendingApplication);
            updateLendingVpaStage(lendingApplication, DISBURSED.name());

            lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndApplicationId(basicDetailsDto.get().getId(), lendingApplication.getId());
            if (lendingPaymentSchedule != null) {
                logger.error("Loan payment schedule already exist for loanId {} and merchantId {}.", postPayoutRequestDto.getApplicationId(), basicDetailsDto);
                return new ResponseEntity<>("Duplicate Request", HttpStatus.BAD_REQUEST);
            }

            lendingPaymentSchedule = new LendingPaymentSchedule();

            logger.info("Popualting data into lending_payment_schedule table for applicationId: {}", lendingApplication.getId());

            lendingPaymentSchedule.setLoanApplication(lendingApplication);
            lendingPaymentSchedule.setLoanType("NORMAL");
            lendingPaymentSchedule.setMerchantId(basicDetailsDto.get().getId());
            lendingPaymentSchedule.setLoanAmount(lendingApplication.getLoanAmount());
            lendingPaymentSchedule.setMobile(basicDetailsDto.get().getMobile());
            lendingPaymentSchedule.setEdiAmount(lendingApplication.getEdi());
            lendingPaymentSchedule.setStatus("ACTIVE");
            lendingPaymentSchedule.setNbfc(lendingApplication.getLender());
            lendingPaymentSchedule.setEdiCount(Integer.parseInt(lendingApplication.getPayableDays().toString()));
            lendingPaymentSchedule.setOverdueEdiCount(0);
            lendingPaymentSchedule.setDueAmount(0D);
            lendingPaymentSchedule.setDueInterest(0D);
            lendingPaymentSchedule.setDuePrinciple(0D);
            lendingPaymentSchedule.setIncentiveAmount(0D);
            lendingPaymentSchedule.setEdiRemainingCount(Integer.parseInt(lendingApplication.getPayableDays().toString()));
            lendingPaymentSchedule.setOverdueAmount(0D);
            lendingPaymentSchedule.setPaidAmount(0D);
            lendingPaymentSchedule.setPaidPrinciple(0D);
            lendingPaymentSchedule.setPaidInterest(0D);
            lendingPaymentSchedule.setTotalCashbackAmount(0D);
            lendingPaymentSchedule.setTotalPayableAmount(lendingApplication.getRepayment());
            lendingPaymentSchedule.setCreatedAt(new Date());
            lendingPaymentSchedule.setUpdatedAt(new Date());
            lendingPaymentSchedule.setOffDay(lendingApplication.getPayableDays() % 30 == 0 ?
                     LenderOffDays.valueOf(lendingApplication.getLender()).getOffDay() : LendingConstants.SIX_DAY_MODEL_OFF_DAY);
//    		String construct=lendingApplication.getLoanConstruct();
//    		lendingPaymentSchedule.setLoanConstruct(construct);

            Date date = new Date();

            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd 00:00:00");

            //getting tommorow's date

            Date tomorrow = new Date(date.getTime() + (1000 * 60 * 60 * 24));
            //checking if next day is Sunday or not because we don't cut edi on Sunday
            if (tomorrow.getDay() == 0) {
                tomorrow = new Date(tomorrow.getTime() + (1000 * 60 * 60 * 24));
            }
            tomorrow = format.parse(format.format(tomorrow));

            //getting date after one month
//    		Date oneMonthLaterDate=getDateAfterNMonths(date, 1);
//    		if(oneMonthLaterDate.getDay()==0) {
//    			oneMonthLaterDate = new Date(oneMonthLaterDate.getTime() + (1000 * 60 * 60 * 24));
//    		}
//    		oneMonthLaterDate=format.parse(format.format(oneMonthLaterDate));


//    		if(construct.equals("CONSTRUCT_1")) {
            lendingPaymentSchedule.setStartDate(tomorrow);
//    		}
//    		else if(construct.equals("CONSTRUCT_2") || construct.equals("CONSTRUCT_3")) {
//
//    			lendingPaymentSchedule.setStartDate(oneMonthLaterDate);
//    			lendingPaymentSchedule.setInterestOnlyStartDate(tomorrow);
//    			lendingPaymentSchedule.setInterestOnlyEdiAmount(lendingApplication.getIoEdi());
//    			lendingPaymentSchedule.setInterestOnlyEdiCount(lendingApplication.getIoPayableDays());
//    			lendingPaymentSchedule.setRemainingInterestOnlyEdiCount(lendingApplication.getIoPayableDays());
//    		}
//    		else {
//    			logger.error("Wrong construct type found for applicationId: {}", lendingApplication.getId());
//    			return new ResponseEntity<>("Wrong construct type found in application", HttpStatus.BAD_REQUEST);
//    		}

            lendingPaymentSchedule.setNextEdiDate(tomorrow);

            Date tenativeLoanEndDate = getDateAfterNMonths(date, lendingApplication.getTenureInMonths());
            if (tenativeLoanEndDate == null) {
                return new ResponseEntity<>("Error occured", HttpStatus.BAD_REQUEST);
            }
            lendingPaymentSchedule.setTentativeClosingDate(tenativeLoanEndDate);
            lendingPaymentSchedule = lendingPaymentScheduleDao.save(lendingPaymentSchedule);
        } catch (Exception e) {
            logger.error("Error occured while populating data into lending_payment_schedule table {}", Arrays.toString(e.getStackTrace()));
            logger.info("Changing loan_disbursal_status back to 'PENDING'");
            if (lendingApplication != null) {
                lendingApplication.setDisburseTimestamp(null);
                lendingApplication.setLoanDisbursalStatus("PENDING");
                lendingApplicationDao.save(lendingApplication);
                updateLendingVpaStage(lendingApplication, VpaTrackingStatus.PROCESSING.name());
                if (lendingPaymentSchedule != null) {
                    lendingPaymentScheduleDao.delete(lendingPaymentSchedule);
                }
            }
            return new ResponseEntity<>("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        createEdiSchedule(lendingPaymentSchedule);
        createEdiException(lendingPaymentSchedule);
        LendingApplication finalLendingApplication = lendingApplication;
        LendingPaymentSchedule finalLendingPaymentSchedule = lendingPaymentSchedule;

        executorService.execute(() -> sendSms(finalLendingApplication, finalLendingPaymentSchedule));

        if (lendingApplication.getProcessingFee() > 0 && lendingApplication.getProcessingFee() != null) {
            executorService.execute(() -> createGSTInvoice(finalLendingApplication));
        }
        BharatPeEnachResponseDTO bharatPeEnach = enachHandler.isSuccess(lendingApplication.getMerchantId(), lendingApplication.getId());
//        if (bharatPeEnach != null) {
//            executorService.execute(() -> initiateEnachCashback(finalLendingPaymentSchedule));
//        }
        executorService.execute(() -> apiGatewayService.globalLimitTxn(finalLendingApplication.getMerchantId(), "DEBIT", finalLendingPaymentSchedule.getLoanAmount()));
//        executorService.execute(() -> pushRedemptionInKafka(finalLendingApplication));
        if (lendingApplication.getDisbursalAmount() > 0 && (lendingApplication.getLoanType().equals(LoanType.HALF_TOPUP.name()) || lendingApplication.getLoanType().equals(LoanType.IO_TOPUP.name()))) {
            prepayDisbursalAmount(lendingPaymentSchedule, lendingApplication.getDisbursalAmount());
        }
        loanUtil.publishApplicationEvent(lendingApplication);
        return new ResponseEntity<>("Ok", HttpStatus.OK);
    }

    public LendingPaymentSchedule updatePreviousLoan(LendingApplication lendingApplication) {

        LendingApplication previousDisbursedApplication=lendingApplicationDao.getLastDisbursedLoan(lendingApplication.getMerchantId());

        logger.info("previousDisbursedApplication {} for an application id {}",previousDisbursedApplication,lendingApplication.getId());

        if (!ObjectUtils.isEmpty(previousDisbursedApplication)) {
            LendingPaymentSchedule  prevLendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(previousDisbursedApplication.getId());
            logger.info("previous LPS {} of an application id {}",prevLendingPaymentSchedule,lendingApplication.getId());
            return prevLendingPaymentSchedule;
        }
        return null;
    }

    public ResponseEntity<PostPayoutResponseDto> populatePostPayoutSchedule(PostPayoutRequestDto postPayoutRequestDto) {

        if (ObjectUtils.isEmpty(postPayoutRequestDto) || ObjectUtils.isEmpty(postPayoutRequestDto.getApplicationId())
          || ObjectUtils.isEmpty(postPayoutRequestDto.getNbfcId())
          || ObjectUtils.isEmpty(postPayoutRequestDto.getLender())
          || ObjectUtils.isEmpty(postPayoutRequestDto.getDisbursedAmount())
          || ObjectUtils.isEmpty(postPayoutRequestDto.getLoanDisbursalStatus()) ) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
        logger.info(" postPayoutRequestDto {} :", postPayoutRequestDto);
        PostPayoutResponseDto postPayoutResponseDto = new PostPayoutResponseDto();
        postPayoutResponseDto.setStatus("SUCCESS");
        postPayoutResponseDto.setApplicationId(postPayoutRequestDto.getApplicationId());
        postPayoutResponseDto.setNbfcId(postPayoutRequestDto.getNbfcId());
        KafkaAudit<PostPayoutAuditDto> kafkaAudit = new KafkaAudit<>("easy_loan", "lending", "post_payout", null);
        PostPayoutAuditDto postPayoutAuditDto = new PostPayoutAuditDto();
        postPayoutAuditDto.setPostPayoutRequest(postPayoutRequestDto);
        postPayoutAuditDto.setExternalLoanId(postPayoutRequestDto.getApplicationId());
        postPayoutAuditDto.setStatus(postPayoutRequestDto.getLoanDisbursalStatus().toUpperCase());
        postPayoutAuditDto.setLender(postPayoutRequestDto.getLender().toUpperCase());
        logger.info("Create LPS request:{}", postPayoutRequestDto);
        LendingApplication lendingApplication = null;
        LendingPaymentSchedule lendingPaymentSchedule = null;
        BasicDetailsDto basicDetailsDto = null;
        LendingPaymentSchedule prevLendingPaymentSchedule=null;
        boolean loanStatusFlag = false;
//        Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(Long.valueOf(postPayoutRequestDto.getMerchantId()));
        try {
//            logger.info("Fetching merchant for the merchant id {}", postPayoutRequestDto.getMerchantId());
//            Optional<Merchant> merchant = merchantDao.findById(Long.parseLong(postPayoutRequestDto.getMerchantId()));
//            if (!basicDetailsDto.isPresent()) {
//                logger.error("Merchant not found for the merchant id {}", postPayoutRequestDto.getMerchantId());
//                return new ResponseEntity<>("Invalid merchantId", HttpStatus.BAD_REQUEST);
//            }
            logger.info("Fetching loan application on the basis of application id {}", postPayoutRequestDto.getApplicationId());
            lendingApplication =
              lendingApplicationDao.findByExternalLoanId(postPayoutRequestDto.getApplicationId());

            if (lendingApplication == null) {
                logger.error("Loan application for loanId {} not found.", postPayoutRequestDto.getApplicationId());
                postPayoutResponseDto.setStatus("FAILED");
                postPayoutResponseDto.setMessage("Invalid applicationId");
                postPayoutAuditDto.setPostPayoutResponse(postPayoutResponseDto);
                kafkaAudit.setData(postPayoutAuditDto);
                pushKafkaAudit(kafkaAudit);
                return new ResponseEntity<>(postPayoutResponseDto, HttpStatus.BAD_REQUEST);
            }

            prevLendingPaymentSchedule = updatePreviousLoan(lendingApplication);
            logger.info("prevLendingPaymentSchedule {}",prevLendingPaymentSchedule);


            // save the utr if the request contains it, saving beforehand so that in case of some error we have the UTR to keep track of it
            if (!ObjectUtils.isEmpty(postPayoutRequestDto.getUtr())) {
                saveDisbursalUtr(lendingApplication.getId(), postPayoutRequestDto.getLender(), postPayoutRequestDto.getUtr());
            }

            Optional<BasicDetailsDto> basicDetailsDtoOptional = merchantService.fetchMerchantBasicDetails(lendingApplication.getMerchantId());
            if (!basicDetailsDtoOptional.isPresent()) {
                logger.error("Merchant details or gst details not found for the merchant id {}  and application {}", lendingApplication.getMerchantId(), lendingApplication.getId());
                postPayoutResponseDto.setStatus("FAILED");
                postPayoutResponseDto.setMessage("Invalid data");
                postPayoutAuditDto.setPostPayoutResponse(postPayoutResponseDto);
                postPayoutAuditDto.setExternalLoanId(lendingApplication.getExternalLoanId());
                kafkaAudit.setData(postPayoutAuditDto);
                pushKafkaAudit(kafkaAudit);
                return new ResponseEntity<>(postPayoutResponseDto, HttpStatus.BAD_REQUEST);
            }
            basicDetailsDto = basicDetailsDtoOptional.get();
            postPayoutAuditDto.setApplicationId(lendingApplication.getId());
            postPayoutAuditDto.setMerchantId(lendingApplication.getMerchantId());

            String disbursalStage =  DisbursalStageMapping.getDisbursedStage(lendingApplication.getLender().toUpperCase(),postPayoutRequestDto.getLoanDisbursalStatus().toUpperCase());


            if (lendingApplication.getLender().equalsIgnoreCase(postPayoutRequestDto.getLender().toUpperCase()) && ObjectUtils.isEmpty(lendingApplication.getNbfcId())) {
                // check status and populate lending_application with the nbfcId
                final NbfcStatusApiResponseDTO nbfcStatusApiResponseDTO = apiGatewayService.getNbfcStatus(lendingApplication.getId());

                logger.info("nbfcStatusApiResponseDTO for applicationId : {} {}", lendingApplication.getId(), nbfcStatusApiResponseDTO);

                if (!ObjectUtils.isEmpty(nbfcStatusApiResponseDTO)
                    && nbfcStatusApiResponseDTO.getSuccess()
                    && "SUCCESS".equalsIgnoreCase(nbfcStatusApiResponseDTO.getStatus())) {

                    lendingApplication.setNbfcId(nbfcStatusApiResponseDTO.getLoanId());

                    lendingApplication.setLoanDisbursalStatus(disbursalStage);
                    lendingApplication.setSendToNbfc("YES");

                    // if earlier due to some reason this nbfc send date was missed add it
                    if (ObjectUtils.isEmpty(lendingApplication.getNbfcSendDate()))
                        lendingApplication.setNbfcSendDate(new Date());
                }
            }

            if (ObjectUtils.isEmpty(lendingApplication.getNbfcId()) || !lendingApplication.getNbfcId().equalsIgnoreCase(postPayoutRequestDto.getNbfcId()) ||
                    !lendingApplication.getLender().equalsIgnoreCase(postPayoutRequestDto.getLender().toUpperCase())) {
                logger.error("lender mismatch or loan not found for {}", lendingApplication.getMerchantId());
                postPayoutResponseDto.setStatus("FAILED");
                postPayoutResponseDto.setMessage("lender mismatch or loan not found");
                postPayoutAuditDto.setPostPayoutResponse(postPayoutResponseDto);
                kafkaAudit.setData(postPayoutAuditDto);
                pushKafkaAudit(kafkaAudit);
                return new ResponseEntity<>(postPayoutResponseDto, HttpStatus.BAD_REQUEST);
            }

            if ("DISBURSED".equalsIgnoreCase(disbursalStage)) {
                logger.info("status of application is {}", disbursalStage);

                lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndApplicationId(lendingApplication.getMerchantId(), lendingApplication.getId());
                if (lendingPaymentSchedule != null) {
                    logger.error("Loan payment schedule already exist for loanId {} and merchantId {}.", postPayoutRequestDto.getApplicationId(), basicDetailsDto);
                    postPayoutResponseDto.setStatus("SUCCESS");
                    postPayoutResponseDto.setLoanStartDate(lendingPaymentSchedule.getStartDate());
                    postPayoutResponseDto.setNextEdiDate(lendingPaymentSchedule.getStartDate());
                    postPayoutResponseDto.setMessage("Duplicate Request");
                    postPayoutAuditDto.setPostPayoutResponse(postPayoutResponseDto);
                    kafkaAudit.setData(postPayoutAuditDto);
                    pushKafkaAudit(kafkaAudit);
                    return new ResponseEntity<>(postPayoutResponseDto, HttpStatus.OK);
                }

                // if difference in disbursal amount in request and disbursal amount in application > 10 then fail the request
                if (Math.abs(lendingApplication.getDisbursalAmount() - Math.ceil(postPayoutRequestDto.getDisbursedAmount())) > 10) {
                    lendingApplication.setLoanDisbursalStatus("AMOUNT_MISMATCH");
                    lendingApplicationDao.save(lendingApplication);
                    logger.error("disbursal amt mismtach for {}", postPayoutRequestDto.getApplicationId());
                    postPayoutResponseDto.setStatus("FAILED");
                    postPayoutResponseDto.setMessage("disbursal amount mismatch");
                    postPayoutAuditDto.setPostPayoutResponse(postPayoutResponseDto);
                    kafkaAudit.setData(postPayoutAuditDto);
                    pushKafkaAudit(kafkaAudit);
                    return new ResponseEntity<>(postPayoutResponseDto, HttpStatus.BAD_REQUEST);
                }

                logger.info("Changing loan_disbursal_status to 'DISBURSED' application_id : {}", lendingApplication.getId());

                lendingApplication.setLoanDisbursalStatus("DISBURSED");
                lendingApplication.setDisburseTimestamp(getDisburseTimestamp(postPayoutRequestDto.getDisbursalDate(), new Date()));
                lendingApplication.setAccountType("HINDON".equals(lendingApplication.getLender()) || "MAMTA".equals(lendingApplication.getLender()) || "LIQUILOANS_NBFC".equals(lendingApplication.getLender()) || "TRILLIONLOANS".equals(lendingApplication.getLender()) ? "NBFC_FUNDS" : "INVESTOR_FUNDS");

                lendingApplicationDao.save(lendingApplication);

                LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(lendingApplication.getMerchantId(), lendingApplication);
                if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
                    funnelService.submitEventV3(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
                            FunnelEnums.StageId.DISBURSAL, FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString(), LoanDetailsConstant.FUNNEL_VERSION_TAG);
                }
                else{
                    funnelService.submitEvent(lendingApplication.getMerchantId(), null, lendingApplication.getId(),
                            FunnelEnums.StageId.DISBURSAL, FunnelEnums.StageEvent.COMPLETED, LocalDateTime.now().toString());
                }

                lendingPaymentSchedule = new LendingPaymentSchedule();

                logger.info("Populating data into lending_payment_schedule table for applicationId: {}", lendingApplication.getId());

                lendingPaymentSchedule.setLoanApplication(lendingApplication);
                lendingPaymentSchedule.setLoanType("NORMAL");
                lendingPaymentSchedule.setMerchantId(lendingApplication.getMerchantId());
                lendingPaymentSchedule.setLoanAmount(lendingApplication.getLoanAmount());
                lendingPaymentSchedule.setMobile(basicDetailsDto.getMobile());
                lendingPaymentSchedule.setEdiAmount(lendingApplication.getEdi());
                lendingPaymentSchedule.setStatus("ACTIVE");
                lendingPaymentSchedule.setNbfc(lendingApplication.getLender());
                lendingPaymentSchedule.setEdiCount(Integer.parseInt(lendingApplication.getPayableDays().toString()));
                lendingPaymentSchedule.setOverdueEdiCount(0);
                lendingPaymentSchedule.setDueAmount(0D);
                lendingPaymentSchedule.setDueInterest(0D);
                lendingPaymentSchedule.setDuePrinciple(0D);
                lendingPaymentSchedule.setIncentiveAmount(0D);
                lendingPaymentSchedule.setEdiRemainingCount(Integer.parseInt(lendingApplication.getPayableDays().toString()));
                lendingPaymentSchedule.setOverdueAmount(0D);
                lendingPaymentSchedule.setPaidAmount(0D);
                lendingPaymentSchedule.setPaidPrinciple(0D);
                lendingPaymentSchedule.setPaidInterest(0D);
                lendingPaymentSchedule.setTotalCashbackAmount(0D);
                lendingPaymentSchedule.setTotalPayableAmount(lendingApplication.getRepayment());
                lendingPaymentSchedule.setCreatedAt(new Date());
                lendingPaymentSchedule.setUpdatedAt(new Date());
                lendingPaymentSchedule.setOffDay(lendingApplication.getPayableDays() % 30 == 0 ?
                        LenderOffDays.valueOf(lendingApplication.getLender()).getOffDay() : LendingConstants.SIX_DAY_MODEL_OFF_DAY);

                if (Lender.ABFL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) || Lender.PIRAMAL.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc())
                        || Lender.TRILLIONLOANS.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc())
                        || Lender.MUTHOOT.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) || Lender.CAPRI.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc())) {
                    lendingPaymentSchedule.setSettlementMechanism(LoanSettlementMechanism.EDI_BY_EDI.name());
                }

                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd 00:00:00");

                //getting tommorow's date

                Date tomorrow = new Date(lendingApplication.getDisburseTimestamp().getTime() + (1000 * 60 * 60 * 24));
//                Date tomorrow = new Date();
                //checking if next day is Sunday or not because we don't cut edi on Sunday
                if (tomorrow.getDay() == 0 && !Arrays.asList("ABFL", "PIRAMAL", "TRILLIONLOANS", "MUTHOOT", "CAPRI").contains(lendingApplication.getLender())) {
                    tomorrow = new Date(tomorrow.getTime() + (1000 * 60 * 60 * 24));
                }
                tomorrow = format.parse(format.format(tomorrow));
                lendingPaymentSchedule.setStartDate(tomorrow);
                postPayoutResponseDto.setLoanStartDate(tomorrow);

                lendingPaymentSchedule.setNextEdiDate(tomorrow);
                postPayoutResponseDto.setNextEdiDate(tomorrow);

                Date tenativeLoanEndDate = getDateAfterNMonths(lendingApplication.getDisburseTimestamp(), lendingApplication.getTenureInMonths());
                if (tenativeLoanEndDate == null) {
                    postPayoutResponseDto.setStatus("FAILED");
                    postPayoutResponseDto.setMessage("unable to compute tentative closing date");
                    return new ResponseEntity<>(postPayoutResponseDto, HttpStatus.BAD_REQUEST);
                }
                lendingPaymentSchedule.setTentativeClosingDate(tenativeLoanEndDate);
                lendingPaymentSchedule = lendingPaymentScheduleDao.save(lendingPaymentSchedule);
                if (!ObjectUtils.isEmpty(prevLendingPaymentSchedule)
                        && prevLendingPaymentSchedule.getStatus().equalsIgnoreCase("INACTIVE_TOPUP")) {
                    try {
                        logger.info("while closing loan for previous loan for id {}",lendingApplication.getId());
                        verifyOTPService.closePreviousLoanAfterSuccessfulTopupCreation(lendingApplication.getId());
                    } catch (Exception e) {
                        logger.error("exception while closing previous loan while making Top-up application {}, active lps is {}, e{}", lendingApplication.getId(),prevLendingPaymentSchedule.getId(), Arrays.asList(e.getStackTrace()));
                    }
                }
                loanStatusFlag = true;
                logger.info("setting loan flag status as true in populatePostPayoutSchedule for merchantId : {}",lendingPaymentSchedule.getMerchantId());
            }

            else if ("UNKNOWN".equalsIgnoreCase(disbursalStage)) {
                logger.info("unknown application status {} for the application id {}", postPayoutRequestDto.getDisbursedAmount(), lendingApplication.getId());
                postPayoutResponseDto.setStatus("FAILED");
                postPayoutResponseDto.setMessage("UNKNOWN status code");
                postPayoutAuditDto.setPostPayoutResponse(postPayoutResponseDto);
                kafkaAudit.setData(postPayoutAuditDto);
                pushKafkaAudit(kafkaAudit);
                return new ResponseEntity<>(postPayoutResponseDto, HttpStatus.BAD_REQUEST);
            }
            else {
                if ("DISBURSED".equalsIgnoreCase(lendingApplication.getLoanDisbursalStatus()) &&
                        !"FAILED".equalsIgnoreCase(disbursalStage)) {
                    logger.info("Loan already marked disbursed for application : {}", lendingApplication.getId());
                    postPayoutAuditDto.setPostPayoutResponse(postPayoutResponseDto);
                    kafkaAudit.setData(postPayoutAuditDto);
                    pushKafkaAudit(kafkaAudit);
                    return new ResponseEntity<>(postPayoutResponseDto, HttpStatus.OK);
                }

                lendingApplication.setLoanDisbursalStatus(disbursalStage);
                lendingApplicationDao.save(lendingApplication);
                logger.info("known application status {} for the application id {} is set to {}", postPayoutRequestDto.getLoanDisbursalStatus(), lendingApplication.getId(), lendingApplication.getLoanDisbursalStatus());
                if (!ObjectUtils.isEmpty(prevLendingPaymentSchedule) && "INACTIVE_TOPUP".equalsIgnoreCase(prevLendingPaymentSchedule.getStatus())
                        && "FAILED".equalsIgnoreCase(lendingApplication.getLoanDisbursalStatus())
                        && "TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())) {
                    prevLendingPaymentSchedule.setStatus("ACTIVE");
                    lendingPaymentScheduleDao.save(prevLendingPaymentSchedule);
                }
                postPayoutAuditDto.setPostPayoutResponse(postPayoutResponseDto);
                kafkaAudit.setData(postPayoutAuditDto);
                pushKafkaAudit(kafkaAudit);
                return new ResponseEntity<>(postPayoutResponseDto, HttpStatus.OK);
            }
        } catch (Exception e) {
            logger.error("Error occured while populating data into lending_payment_schedule table {}", Arrays.toString(e.getStackTrace()));
            logger.info("Changing loan_disbursal_status back to 'PENDING'");
            if (lendingApplication != null) {
                if (!"DISBURSED".equalsIgnoreCase(lendingApplication.getLoanDisbursalStatus())) {
                    lendingApplication.setDisburseTimestamp(null);
                    lendingApplication.setLoanDisbursalStatus("PENDING");
                    lendingApplicationDao.save(lendingApplication);
                }
            }
            postPayoutResponseDto.setStatus("FAILED");
            postPayoutResponseDto.setMessage("Error occurred while populating data into lending_payment_schedule table" + Arrays.toString(e.getStackTrace()));
            postPayoutAuditDto.setPostPayoutResponse(postPayoutResponseDto);
            kafkaAudit.setData(postPayoutAuditDto);
            pushKafkaAudit(kafkaAudit);
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        long diffInDisbursalDates = 0;
        try {
            diffInDisbursalDates
                    =  new DateTimeUtil().getDateDiffInDays(
                    DateTimeUtil.getStartTimeFromDateTime(Optional.ofNullable(postPayoutRequestDto.getDisbursalDate()).orElse(new Date())),
                    new Date());
        } catch (Exception e) {
            logger.error("exception occurred while computing diff days for {} {}", lendingApplication.getId(), e.getMessage());
        }
        if (backDatedLoanEligibleLenders.contains(lendingPaymentSchedule.getNbfc()) && backdatedLoanEnabled &&
                diffInDisbursalDates > 0) {
            lendingApplication.setDisburseTimestamp(postPayoutRequestDto.getDisbursalDate());
            lendingApplication = lendingApplicationDao.save(lendingApplication);
            logger.info("adjusting LPS as this is a backdated disbursal loan {}", lendingPaymentSchedule.getId());
            updateBackdatedLPS(lendingPaymentSchedule, postPayoutRequestDto.getDisbursalDate(), lendingApplication);
        }
        createEdiSchedule(lendingPaymentSchedule);
        createEdiException(lendingPaymentSchedule);
        if (backDatedLoanEligibleLenders.contains(lendingPaymentSchedule.getNbfc()) && backdatedLoanEnabled &&
                diffInDisbursalDates > 0) {
            createDuesInLedgerAndAdjustDueInLPS(lendingPaymentSchedule, 0);
        }

        postPayoutResponseDto.setLoanStartDate(lendingPaymentSchedule.getStartDate());
        postPayoutResponseDto.setNextEdiDate(lendingPaymentSchedule.getNextEdiDate());
        postPayoutAuditDto.setPostPayoutResponse(postPayoutResponseDto);
        kafkaAudit.setData(postPayoutAuditDto);
        pushKafkaAudit(kafkaAudit);

        LendingApplication finalLendingApplication = lendingApplication;
        LendingPaymentSchedule finalLendingPaymentSchedule = lendingPaymentSchedule;
        final BasicDetailsDto finalBasicDetailDto = basicDetailsDto;


        LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
        if(ObjectUtils.isEmpty(lendingKfs)){
            executorService.execute(() -> sendSms(finalLendingApplication, finalLendingPaymentSchedule));
        } else{
            LendingApplication finalLendingApplication1 = lendingApplication;
            executorService.execute(() -> liquiloansService.sendWPAndSMSNotification(finalLendingApplication1,true));
                executorService.execute(() -> {
                    try {
                        liquiloansAsyncService.generateWelcomeDocAndNotify(finalLendingApplication, finalBasicDetailDto, lendingKfs);
                    } catch (Exception e) {
                        logger.error("error generating welcome document for {}", finalLendingApplication.getId());
                    }
                });
        }

//        if (lendingApplication.getProcessingFee() > 0 && lendingApplication.getProcessingFee() != null) {
//            executorService.execute(() -> createGSTInvoice(finalLendingApplication));
//        }
//
//        BharatPeEnachResponseDTO bharatPeEnach = enachHandler.isSuccess(lendingApplication.getMerchantId(), lendingApplication.getId());
//        if (bharatPeEnach != null) {
//            executorService.execute(() -> initiateEnachCashback(finalLendingPaymentSchedule));
//        }
        executorService.execute(() -> apiGatewayService.globalLimitTxn(finalLendingApplication.getMerchantId(), "DEBIT", finalLendingPaymentSchedule.getLoanAmount()));
        if(Arrays.asList("CAPRI").contains(lendingApplication.getLender())) {
            executorService.execute(() -> saveSignedDocsForLender(finalLendingApplication));
        }
//        executorService.execute(() -> pushRedemptionInKafka(finalLendingApplication));
        if (lendingApplication.getDisbursalAmount() > 0 && (lendingApplication.getLoanType().equals(LoanType.HALF_TOPUP.name()) || lendingApplication.getLoanType().equals(LoanType.IO_TOPUP.name()))) {
            prepayDisbursalAmount(lendingPaymentSchedule, lendingApplication.getDisbursalAmount());
        }
        loanUtil.publishApplicationEvent(lendingApplication);

        if(loanStatusFlag)
        {
                Long merchantId = lendingPaymentSchedule.getMerchantId();
                logger.info("sending loan flag status in populatePostPayoutSchedule for merchantId : {}",merchantId);
                sherlocLoanStatusChangeService.pushLoanStatusChangeEventToKafka(merchantId, lendingPaymentSchedule.getStatus());
        }
        return new ResponseEntity<>(postPayoutResponseDto, HttpStatus.OK);
    }

    public LendingPaymentSchedule backDateLoan(LendingPaymentSchedule lendingPaymentSchedule, int offset, Date disbursementDate, LendingApplication lendingApplication, int daysToMove) {
        lendingApplication.setDisburseTimestamp(disbursementDate);
        lendingApplication = lendingApplicationDao.save(lendingApplication);
        updateBackdatedLPS(lendingPaymentSchedule, disbursementDate, lendingApplication);
        moveEdiScheduleDates(lendingPaymentSchedule,daysToMove);
        createDuesInLedgerAndAdjustDueInLPS(lendingPaymentSchedule, offset);
        // audit this loan
        return lendingPaymentSchedule;
    }


    public ApiResponse createBackDatedLoan(BackdatedLoanDTO backdatedLoanDTO) {
        // calculate diff via lps start date - 1
        LendingApplication lendingApplication = lendingApplicationDao.findByExternalLoanId(backdatedLoanDTO.getApplicationId());
        if (ObjectUtils.isEmpty(lendingApplication)) {
            return new ApiResponse(false, "lending app not found !");
        }
        LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(lendingApplication.getId());
        if (ObjectUtils.isEmpty(lendingApplication)) {
            return new ApiResponse(false, "lending payment schedule not found !");
        }
        long diffInDisbursalDates =  new DateTimeUtil().getDateDiffInDays(DateTimeUtil.getStartTimeFromDateTime(backdatedLoanDTO.getDisbursalDate()), DateTimeUtil.addDays(lendingPaymentSchedule.getStartDate(), -1));
        if (diffInDisbursalDates <= 0) {
            return new ApiResponse(false, "no backdated disbursal date found in request !");
        }
        int daysToBeSkipped = lendingPaymentSchedule.getEdiCount() - lendingPaymentSchedule.getEdiRemainingCount();
        backDateLoan(lendingPaymentSchedule, daysToBeSkipped,
                backdatedLoanDTO.getDisbursalDate(), lendingApplication, (int) diffInDisbursalDates);
        return new ApiResponse(true, "shifted loan as per disbursal date");
    }

    public void moveEdiScheduleDates(LendingPaymentSchedule lendingPaymentSchedule, int daysToMove) {
        final List<LendingEDISchedule> lendingEDISchedules = lendingEDIScheduleDao.findByLendingPaymentSchedule(lendingPaymentSchedule);
        logger.info("moving edi schedule by {}", daysToMove);
        for (LendingEDISchedule lendingEDISchedule : lendingEDISchedules) {
            final Date existingDate = lendingEDISchedule.getDate();
            lendingEDISchedule.setDate(DateTimeUtil.addDays(existingDate, -daysToMove));
            lendingEDIScheduleDao.save(lendingEDISchedule);
            logger.info("schedule {} ->  {}",existingDate, lendingEDISchedule.getDate() );
        }
    }

    private void updateBackdatedLPS(LendingPaymentSchedule lendingPaymentSchedule, Date disbursementDate, LendingApplication lendingApplication) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd 00:00:00");
        Date nextDate = new Date(disbursementDate.getTime() + (1000 * 60 * 60 * 24));
        try {
            nextDate = format.parse(format.format(nextDate));
        } catch (ParseException e) {
            logger.error("exception occurred while parsing the date for {} {}", lendingPaymentSchedule.getId(), e.getMessage());
        }
        lendingPaymentSchedule.setStartDate(nextDate);
        // next edi date with always be tomorrow
        Date tenativeLoanEndDate = DateTimeUtil.addDays(disbursementDate,lendingApplication.getPayableDays().intValue() + 1);
        lendingPaymentSchedule.setTentativeClosingDate(tenativeLoanEndDate);
        lendingPaymentScheduleDao.save(lendingPaymentSchedule);
    }

    private void createDuesInLedgerAndAdjustDueInLPS(LendingPaymentSchedule lendingPaymentSchedule, int offset) {
        List<LendingEDISchedule> lendingEDISchedules = lendingEDIScheduleDao.findByLendingPaymentSchedule(lendingPaymentSchedule);
        Double dueAmount = lendingPaymentSchedule.getDueAmount();
        Double duePrinciple = lendingPaymentSchedule.getDuePrinciple();
        Double dueInterest = lendingPaymentSchedule.getDueInterest();
        List<LendingLedger> lendingLedgerList = new ArrayList<>();
        for (LendingEDISchedule lendingEDISchedule : lendingEDISchedules) {
//            logger.info("{} {}", lendingEDISchedule.getDate(), lendingEDISchedule.getInstallmentNumber());
            if (lendingEDISchedule.getDate().after(new Date()))
                break;
            if (lendingEDISchedule.getInstallmentNumber() <= offset)
                continue;
            dueAmount+= lendingEDISchedule.getTotalEdi();
            dueInterest+= lendingEDISchedule.getInterest();
            duePrinciple+= lendingEDISchedule.getPrinciple();
            LendingLedger lendingLedger =  createLendingLedger(lendingPaymentSchedule, new Date(),
                    Status.LendingTransactionType.EDI.toString(),
                    -lendingEDISchedule.getTotalEdi().doubleValue(), -lendingEDISchedule.getPrinciple(),
                    -lendingEDISchedule.getInterest(), -0D,
                    0D, null);
            lendingLedgerList.add(lendingLedger);
        }
        lendingPaymentSchedule.setDueAmount(dueAmount);
        lendingPaymentSchedule.setDueInterest(dueInterest);
        lendingPaymentSchedule.setDuePrinciple(duePrinciple);
        lendingPaymentSchedule.setEdiRemainingCount(lendingPaymentSchedule.getEdiRemainingCount() -  lendingLedgerList.size());
        lendingLedgerDao.saveAll(lendingLedgerList);
        lendingPaymentScheduleDao.save(lendingPaymentSchedule);
    }

    private LendingLedger createLendingLedger(LendingPaymentSchedule lendingPaymentSchedule, Date date, String txnType, Double amount, Double principle, Double interest, Double otherCharges, Double penalty, String description) {
        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setMerchantId(lendingPaymentSchedule.getMerchantId());
        if (lendingPaymentSchedule.getMerchantStoreId() != null && lendingPaymentSchedule.getMerchantStoreId() > 0) {
            lendingLedger.setMerchantStoreId(lendingPaymentSchedule.getMerchantStoreId());
        }
        lendingLedger.setLendingPaymentSchedule(lendingPaymentSchedule);
        lendingLedger.setDate(date);
        lendingLedger.setTxnType(txnType);
        lendingLedger.setAmount(amount);
        lendingLedger.setInterest(interest);
        lendingLedger.setOtherCharges(otherCharges);
        lendingLedger.setPenalty(penalty);
        lendingLedger.setPrinciple(principle);
        lendingLedger.setDescription(description);
        return lendingLedger;
    }


    public void pushKafkaAudit(KafkaAudit kafkaAudit) {
        try {
            logger.info("pushing kafka event for {}", kafkaAudit);
            kafkaTemplate.send("easyloan_audit_data",kafkaAudit);
        } catch (Exception e) {
            logger.error("error while sending audit data {} {}", kafkaAudit, Arrays.asList(e.getStackTrace()));
        }
    }

    private void prepayDisbursalAmount(LendingPaymentSchedule lendingPaymentSchedule, Double disbursalAmount) {
        try {
            logger.info("Recast loan prepayment for loanId:{} and amount:{}", lendingPaymentSchedule.getId(), disbursalAmount);
            LoanPaymentOrder loanPaymentOrder = createPaymentOrder(lendingPaymentSchedule, disbursalAmount, null, "AUTO_PREPAYMENT");
            PaymentCallbackRequestDTO paymentCallbackRequestDTO = new PaymentCallbackRequestDTO();
            paymentCallbackRequestDTO.setAmount(disbursalAmount);
            paymentCallbackRequestDTO.setStatus("SUCCESS");
            paymentCallbackRequestDTO.setOrderId(loanPaymentOrder.getOrderId());
            paymentService.handleCallback(paymentCallbackRequestDTO);
        } catch (Exception e) {
            logger.error("Exception in auto prepayment for loanId:{}", lendingPaymentSchedule.getId(), e);
        }
    }

    private LoanPaymentOrder createPaymentOrder(LendingPaymentSchedule lendingPaymentSchedule, Double amount, String bankRefNo, String source) {
        LoanPaymentOrder order = new LoanPaymentOrder();
        order.setMerchantId(lendingPaymentSchedule.getMerchantId());
        order.setOwner("lending_payment_schedule");
        order.setOwnerId(lendingPaymentSchedule.getId());
        order.setAmount(amount);
        order.setStatus("PENDING");
        order.setSource(source);
        order.setBankRefNo(bankRefNo);
        order = loanPaymentOrderDao.save(order);
        String orderId = "LOAN" + (10000000L + order.getId());
        order.setOrderId(orderId);
        return loanPaymentOrderDao.save(order);
    }

    public void pushRedemptionInKafka(LendingApplication lendingApplication, Double refundedAmount) {

        LendingCategories selectedCategoriesData = lendingCategoryDao.getByCategory(lendingApplication.getCategory());
        if (Objects.nonNull(selectedCategoriesData) && apiGatewayService.eligibleForProcessingFee(lendingApplication.getMerchantId())) {
            int processingFee = (int) Math.ceil(lendingApplication.getLoanAmount() * Double.parseDouble(selectedCategoriesData.getProcessingFee()));
            if (processingFee > 0) {
                logger.info("redeeming club offer for merchant:{} and amount:{}", lendingApplication.getMerchantId(), processingFee);
                Map<String, Object> body = new HashMap<>();
                body.put("merchant_id", lendingApplication.getMerchantId());
                body.put("ref_txn_id", lendingApplication.getId());
                body.put("amount", processingFee);
                body.put("narration", "Loan Arranger Fee");
                body.put("source_module", "LOAN");

                kafkaTemplate.send(TOPIC, body);
            }
        }
        if (apiGatewayService.checkClubV2(lendingApplication.getMerchantId())) {
            logger.info("redeeming club offer for merchant:{} and amount:{}", lendingApplication.getMerchantId(), refundedAmount);
            Map<String, Object> body = new HashMap<>();
            body.put("merchant_id", lendingApplication.getMerchantId());
            body.put("ref_txn_id", lendingApplication.getId());
            body.put("amount", refundedAmount);
            body.put("narration", "Loan timely repayment cashback");
            body.put("source_module", "LOAN");

            kafkaTemplate.send(TOPIC, body);
        }
    }

    private void createEdiException(LendingPaymentSchedule lendingPaymentSchedule) {
        try {
            LendingEdiException lendingEdiException = new LendingEdiException();
            lendingEdiException.setLoanId(String.valueOf(lendingPaymentSchedule.getId()));
            lendingEdiExceptionDao.save(lendingEdiException);
        } catch (Exception e) {
            logger.error("Error while saving lending edi exception for loanId:{}", lendingPaymentSchedule.getId(), e);
        }
    }

    public void initiateEnachCashback(LendingPaymentSchedule lendingPaymentSchedule) {
        logger.info("Enach success on loanId:{}, processing Rs.100 cashback for merchant:{}", lendingPaymentSchedule.getId(), lendingPaymentSchedule.getMerchantId());
        Double cashbackAmount = 100D;
        String orderId = "NACH_CASHBACK" + System.currentTimeMillis();
        LendingPayoutRequest lendingPayoutRequest = new LendingPayoutRequest(lendingPaymentSchedule.getId(), orderId, cashbackAmount, LendingPayoutType.LENDING_INCENTIVE, lendingPaymentSchedule.getMerchantId(), "NACH_CASHBACK");
        LendingPayoutResponse lendingPayoutResponse = apiGatewayService.lendingPayout(lendingPayoutRequest);
        if (lendingPayoutResponse != null) {

            MerchantDetailsDto merchantDetailsDTO =  merchantService.fetchMerchantDetails(lendingPaymentSchedule.getMerchantId(), Collections.singletonList(Constants.MerchantUtil.Scope.BANK_DETAIL));
            BasicDetailsDto basicDetailsDto = merchantDetailsDTO.getMerchantDetail();
            BankDetailsDto merchantBankDetail = merchantDetailsDTO.getBankDetail();

            if (ObjectUtils.isEmpty(basicDetailsDto) || ObjectUtils.isEmpty(merchantDetailsDTO)) {
                return;
            }
            String identifier = "LENDING_CASHBACK_PUSH";
            String deeplink = notificationUtil.getDeeplink(basicDetailsDto.getSettlementType(), "LOAN_DASHBOARD");
            Map<String, Object> templateParams = new HashMap<>();
            templateParams.put("beneficiary_name", getBeneficiaryName(merchantBankDetail.getBeneficiaryName()));
            NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
            notificationPayloadDto.setTemplateIdentifier(identifier);
            notificationPayloadDto.setMobile(basicDetailsDto.getMobile());
            notificationPayloadDto.setClientName("LENDING");
            notificationPayloadDto.setPushTitle("₹ 100 deposited in your bank!");
            notificationPayloadDto.setPushDeepLink(deeplink);
            notificationPayloadDto.setTemplateParams(templateParams);
            lendingNotificationService.notify(notificationPayloadDto);
        }
    }

    private void createGSTInvoice(LendingApplication lendingApplication) {
        try {
            Long merchantId = lendingApplication.getMerchantId();
            Long applicationId = lendingApplication.getId();
            Map<String, Long> detailMap = new HashMap<String, Long>() {{
                put("merchantId", merchantId);
                put("applicationId", applicationId);
            }};
            confluentKafkaTemplate.send("create_gst_invoice", merchantId.toString(), detailMap);
            logger.info("Pushed " + detailMap + " to topic create_gst_invoice");
        } catch (Exception e) {
            logger.error("Exception while pushing to topic create_gst_invoice for application:{}", lendingApplication.getId(), e);
        }
    }


//    public void changeDeductionFromInstantToDaily(Merchant merchant) {
//
//        logger.info("Changing settlement from instant to daily for merchant {}", merchant.getId());
//        List<PayloadDTO> merchantPayload = new ArrayList<>();
//        merchantPayload.add(new PayloadDTO("set", "settlementtype", "DAILY"));
//
//        List<Validate> validateList = validateDao.findByMobile(merchant.getMobile());
//        for (Validate validate : validateList) {
//            validate.setSettlement("daily");
//        }
//        SettlementSchedule settlementSchedule = settlementScheduleDao.findTop1ByMerchantIdAndStatus(merchant.getId(), "PENDING");
//        if (settlementSchedule != null) {
//            settlementSchedule.setSettlementDate(new Date());
//            settlementSchedule.setMoveDaily("YES");
//            settlementScheduleDao.save(settlementSchedule);
//        }
//        boolean merchantUpdated = merchantUpdateService.curlMerchantPartialUpdateAPI(merchant.getId(), merchantPayload);
//        if (!merchantUpdated) {
//            logger.info("Error while updating merchant info!");
//        }
//        if (!validateList.isEmpty()) {
//            validateDao.saveAll(validateList);
//        }
//
//    }

    private void sendSms(LendingApplication lendingApplication, LendingPaymentSchedule lendingPaymentSchedule) {
        try {
            String sms1;
            String sms2 = null;
            String shortUrl = "";
            LoanAgreement loanAgreement = loanAgreementDao.findByApplicationIdAndType(lendingApplication.getId(), "agreement");
            MerchantDetailsDto merchantDetailsDTO =  merchantService.fetchMerchantDetails(lendingPaymentSchedule.getMerchantId(), Collections.singletonList(Constants.MerchantUtil.Scope.BANK_DETAIL));
            BasicDetailsDto basicDetailsDto = merchantDetailsDTO.getMerchantDetail();
            BankDetailsDto merchantBankDetail = merchantDetailsDTO.getBankDetail();
            if (ObjectUtils.isEmpty(basicDetailsDto) ) {
                return;
            }
//            Merchant merchant = lendingApplication.getMerchant();
            if (merchantBankDetail == null) {
                return;
            }
            if (loanAgreement != null) {
                String fileName = loanAgreement.getAgreementName();
                try {
                    shortUrl = getShorturl(fileName, loanAgreement);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            String identifier = "LENDING_AGREEMENT_SMS";
            Map<String, Object> templateParams = new HashMap<>();
            List<String> loanTypes = new ArrayList<>();
            loanTypes.add(LoanType.HALF_TOPUP.name());
            loanTypes.add(LoanType.IO_TOPUP.name());
            if (loanTypes.contains(lendingApplication.getLoanType())) {
                identifier = "LENDING_AGREEMENT_RECAST_WHATSAPP";
            }
            templateParams.put("disbursal_amount", lendingApplication.getDisbursalAmount());
            templateParams.put("beneficiary_name", getBeneficiaryName(merchantBankDetail.getBeneficiaryName()));
            templateParams.put("shortUrl", shortUrl);
            NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
            notificationPayloadDto.setTemplateIdentifier(identifier);
            notificationPayloadDto.setMobile(basicDetailsDto.getMobile());
            notificationPayloadDto.setClientName("LENDING");
            notificationPayloadDto.setTemplateParams(templateParams);
            lendingNotificationService.notify(notificationPayloadDto);

            //For SMS 2
            identifier = null;
            templateParams = new HashMap<>();
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/MM/yyyy");
            templateParams.put("start_date", simpleDateFormat.format(lendingPaymentSchedule.getStartDate()));
            if ("CONSTRUCT_1".equals(lendingApplication.getLoanConstruct())) {
                identifier = "LENDING_REPAYING_INFO_4_SMS";
                templateParams.put("edi", lendingApplication.getEdi());
            } else if ("CONSTRUCT_2".equals(lendingApplication.getLoanConstruct())) {
                identifier = "LENDING_REPAYING_INFO__2_SMS";
                templateParams.put("edi", lendingApplication.getEdi());
            } else if ("CONSTRUCT_3".equals(lendingApplication.getLoanConstruct())) {
                identifier = "LENDING_REPAYING_INFO_3_SMS";
                templateParams.put("interest_only_edi_amount", lendingPaymentSchedule.getInterestOnlyEdiAmount());
                templateParams.put("edi", lendingApplication.getEdi());
            }

            notificationPayloadDto = new NotificationPayloadDto();
            notificationPayloadDto.setTemplateIdentifier(identifier);
            notificationPayloadDto.setMobile(basicDetailsDto.getMobile());
            notificationPayloadDto.setClientName("LENDING");
            notificationPayloadDto.setTemplateParams(templateParams);


            if (identifier != null) {
                lendingNotificationService.notify(notificationPayloadDto);
            }

            List<String> mobiles = new ArrayList<>();
            mobiles.add(basicDetailsDto.getMobile());
//			whatsappNotificationService.send(merchant, null, sms1, mobiles, null);
            if (!loanTypes.contains(lendingApplication.getLoanType())
              && lendingApplication.getProcessingFee() != null && lendingApplication.getProcessingFee() > 0) {
                identifier = "LENDING_DISBURSED_4_SMS";
                templateParams = new HashMap<>();
                templateParams.put("beneficiary_name", getBeneficiaryName(merchantBankDetail.getBeneficiaryName()));
                templateParams.put("disbursal_amount", lendingApplication.getDisbursalAmount());
                templateParams.put("processing_fee", lendingApplication.getProcessingFee());
                notificationPayloadDto = new NotificationPayloadDto();
                notificationPayloadDto.setTemplateIdentifier(identifier);
                notificationPayloadDto.setMobile(basicDetailsDto.getMobile());
                notificationPayloadDto.setClientName("LENDING");
                notificationPayloadDto.setTemplateParams(templateParams);
                lendingNotificationService.notify(notificationPayloadDto);
//				whatsappNotificationService.send(merchant, null, newMessage, new ArrayList<String>() {{
//					add(lendingApplication.getMerchant().getMobile());
//				}}, null);
            }
        } catch (Exception e) {
            logger.error("Exception while sending disbursal sms---", e);
        }
    }

    public Date getDateAfterNMonths(Date startDate, int month) {

        try {
            logger.info("Getting date after {} month", month);

            Calendar myCal = Calendar.getInstance();
            myCal.setTime(startDate);
            myCal.add(Calendar.MONTH, +month);
            Date tentativeEndDate = myCal.getTime();
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd 00:00:00");
            return format.parse(format.format(tentativeEndDate));
        } catch (Exception e) {
            logger.error("Error occured while catculating date post N month", e);
            return null;
        }


    }


    public ResponseDTO populateSettlementDetails(LiquiloanSettlementRequestDTO settlementRequest, LiquiloansDirectDisbursalRawResponse liquiloansDirectDisbursalRawResponse) {
        logger.info("Insertng disbursal settlement details");
        liquiloansDirectDisbursalRawResponse.setApiName("SETTLEMENT");
        liquiloansDirectDisbursalRawResponse.setRequest(settlementRequest.toString());
        try {
            String requestBody = objectMapper.writeValueAsString(settlementRequest);
            Date transferDate = new SimpleDateFormat("yyyy-MM-dd").parse(settlementRequest.getTransferDate());

            for (LiquiloanSettlementRequestDTO.LoanData loanDetail : settlementRequest.getLoanDetails()) {

                DisbursalSettlement disbursalSettlement = new DisbursalSettlement();
                disbursalSettlement.setAmount(Double.parseDouble(loanDetail.getAmount()));
                disbursalSettlement.setLoanId(loanDetail.getLoanId());
                disbursalSettlement.setNbfc("LIQUILOANS");
                disbursalSettlement.setRequestBody(requestBody);
                disbursalSettlement.setTransferDate(transferDate);
                disbursalSettlement.setUrn(loanDetail.getUrn());
                disbursalSettlement.setUtrNumber(settlementRequest.getUtrNumber());
                disbursalSettlementDao.save(disbursalSettlement);

                logger.info("Populating 'disbursal_settlement_id' field in table 'lending_payment_schedule' for loan id {}", loanDetail.getLoanId());
                if (!updateDisbursalSettlementIdInLendingPaymentSchedule(loanDetail.getLoanId(), loanDetail.getUrn(), disbursalSettlement.getId(), liquiloansDirectDisbursalRawResponse)) {
                    return new ResponseDTO(false, "Error occured while processing settlemet details", null, null);
                }

            }

        } catch (Exception e) {
            logger.error("Error occured while populating disbursal settlement details", e);
            return new ResponseDTO(false, "Error occured while processing settlemet details", null, null);
        }

        return new ResponseDTO(true, null, null, null);
    }

    public boolean updateDisbursalSettlementIdInLendingPaymentSchedule(String loanId, String urnId, Integer settlementId, LiquiloansDirectDisbursalRawResponse liquiloansDirectDisbursalRawResponse) {
        if (urnId.contains("CL")) {
            logger.info("Settlement for credit loan:{}", urnId);
            return true;
        }
        logger.info("Fetching lending application for the externa loan id {} and nbfc id {}", urnId, loanId);
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByExternalLoanIdNbfcIdAndStatus(urnId, loanId, "approved");

            if (lendingApplication == null) {
                logger.error("Lending application not found");
                return false;
            }

            logger.info("Fetching lending payment schedule details for lending appliation {}", lendingApplication.getId());
            liquiloansDirectDisbursalRawResponse.setMerchantId(lendingApplication.getMerchantId());
            liquiloansDirectDisbursalRawResponse.setApplicationId(lendingApplication.getId());
            liquiloansDirectDisbursalRawResponse.setLoanId(lendingApplication.getExternalLoanId());
            liquiloansDirectDisbursalRawResponse.setLiquiloanId(lendingApplication.getNbfcId());

            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndApplicationId(lendingApplication.getMerchantId(), lendingApplication.getId());

            if (lendingPaymentSchedule == null) {
                logger.error("Lending payment schedule not found");
                return false;
            }
            lendingPaymentSchedule.setDisbursalSettlementId(settlementId);
            lendingPaymentScheduleDao.save(lendingPaymentSchedule);
        } catch (Exception e) {
            logger.error("error occured while updating 'disbursal_settlement_id' In lending_payment_schedule table", e);
            return false;
        }
        return true;
    }

    public String getShorturl(String fileName, LoanAgreement loanAgreement) throws UnsupportedEncodingException {
        String tempUrl = "";
        try {
            tempUrl = s3BucketHandler.getPreSignedPublicURL(fileName, bucket);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        String url = "https://bharatpe.in/yourls-api.php?signature=a872b1348e&action=shorturl&format=json&keyword=&url=" + URLEncoder.encode(tempUrl, "UTF-8");
        String response = "";
        try {
            Instant start = Instant.now();
            response = restTemplate.getForObject(url, String.class);
            logger.info("shorturl response : {}", response);
            Instant end = Instant.now();
            logger.info("Time Taken by shorturl API : {} miliseconds", Duration.between(start, end).toMillis());
        } catch (Exception e) {
            logger.error("exception while shorturl API : {}, Exception is {}", url, e);
        }
        JsonNode rootNode = null;
        try {
            rootNode = objectMapper.readTree(response);
        } catch (Exception e) {
            logger.error("Exception while parsing short url---", e);
        }
        if (rootNode != null && rootNode.path("status") != null && rootNode.path("status").textValue().equals("success")) {
            String shortUrl = rootNode.path("shorturl").textValue();
            loanAgreement.setShortUrl(shortUrl);
            loanAgreementDao.save(loanAgreement);
            return shortUrl;
        }
        return " ";
    }

    @SuppressWarnings(value = "unchecked")
//    public void createLead(LendingPaymentSchedule lendingPaymentSchedule, LendingTlDetails lendingTlDetails) {
//        try {
//            MerchantDetailsDto merchantDetailsDTO =  merchantService.fetchMerchantDetails(lendingPaymentSchedule.getMerchantId(), Collections.singletonList(Constants.MerchantUtil.Scope.BANK_DETAIL));
//            BasicDetailsDto basicDetailsDto = merchantDetailsDTO.getMerchantDetail();
//            BankDetailsDto merchantBankDetail = merchantDetailsDTO.getBankDetail();
//            if (ObjectUtils.isEmpty(basicDetailsDto)) {
//                return;
//            }
//            CreditApplication creditApplication = creditApplicationDao.findTop1ByMerchantIdOrderByIdDesc(lendingTlDetails.getMerchantId());
//            List<MerchantDocumentProofOcrSlave> documents = merchantDocumentProofOcrDaoSlave.findByMerchantIdAndApplicationId(creditApplication.getMerchantId(), creditApplication.getId());
//            List<IfscSlave> ifscList = ifscDaoSlave.findByIfsc(merchantBankDetail.getIfsc());
////			MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(lendingTlDetails.getMerchantId());
//            MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(lendingTlDetails.getMerchantId());
//            if (ObjectUtils.isEmpty(merchantResponseDTO)) {
//                throw new MerchantSummaryExceptionHandler(lendingTlDetails.getMerchantId().toString());
//            }
//            Experian experian = experianDao.getByMerchantId(lendingTlDetails.getMerchantId());
//            CreditApplicationAddress creditApplicationAddress = creditApplicationAddressDao.findByMerchantIdAndApplicationId(creditApplication.getMerchantId(), creditApplication.getId());
//            MerchantDocumentProofOcrSlave pancard = null;
//            MerchantDocumentProofOcrSlave poa = null;
//            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
//            for (MerchantDocumentProofOcrSlave document : documents) {
//                if ("pancard".equalsIgnoreCase(document.getProofType())) {
//                    pancard = document;
//                } else {
//                    poa = document;
//                }
//            }
//            if (pancard == null || poa == null) {
//                logger.info("pancard/poa not found for credit_application id :{}", creditApplication.getId());
//                return;
//            }
//            Map<String, Object> request = new LinkedHashMap<>();
//            request.put("SID", getSID());
//            request.put("urn", lendingTlDetails.getExternalLoanId());
//            request.put("loan_type", "PL");
//            request.put("amount", lendingPaymentSchedule.getLoanAmount().toString());
//            request.put("application_date", simpleDateFormat.format(lendingPaymentSchedule.getCreatedAt()));
//            Map<String, Object> schemeDetails = new LinkedHashMap<>();
//            schemeDetails.put("scheme_id", "0");
//            schemeDetails.put("installment_frequency", "Daily");
//            schemeDetails.put("installment_tenure", lendingTlDetails.getPayableDays().toString());
//            schemeDetails.put("processing_fees_value", "0");
//
//            schemeDetails.put("roi_percentage", Double.toString(lendingTlDetails.getInterestRate() * 12));
//            schemeDetails.put("installment_amount", lendingTlDetails.getEdi().toString());
//            schemeDetails.put("xirr", "22.2");
//            request.put("scheme_details", schemeDetails);
//            Map<String, Object> personalDetails = new LinkedHashMap<>();
//            personalDetails.put("pan", pancard.getProofNumber());
//            personalDetails.put("full_name", pancard.getName());
//            if ("male".equalsIgnoreCase(poa.getGender())) {
//                personalDetails.put("gender", "Male");
//            } else if ("female".equalsIgnoreCase(poa.getGender())) {
//                personalDetails.put("gender", "Female");
//            } else {
//                personalDetails.put("gender", "");
//            }
//            if (pancard.getDob() != null) {
//                personalDetails.put("dob", simpleDateFormat.format(new SimpleDateFormat("dd/MM/yyyy").parse(pancard.getDob())));
//            } else {
//                personalDetails.put("dob", "");
//            }
//            personalDetails.put("email", "lending@bharatpe.in");
//            personalDetails.put("contact_number", basicDetailsDto.getMobile().substring(2));
//            personalDetails.put("aadhaar_number", poa.getProofNumber());
//            request.put("personal_details", personalDetails);
//            Map<String, Object> addressDetails = new LinkedHashMap<>();
//            addressDetails.put("full_address", poa.getAddress().replace("/", " "));
//            addressDetails.put("pincode", poa.getPincode());
//            addressDetails.put("city", poa.getCity());
//            addressDetails.put("state", poa.getState());
//            request.put("address_details", addressDetails);
//            Map<String, Object> bankingDetails = new LinkedHashMap<>();
//            bankingDetails.put("bank_name", ifscList.get(0).getBank());
//            bankingDetails.put("branch_name", ifscList.get(0).getBranch());
//            bankingDetails.put("ifsc", merchantBankDetail.getIfsc());
//            bankingDetails.put("account_number", merchantBankDetail.getAccountNumber());
//            bankingDetails.put("account_holder_name", merchantBankDetail.getBeneficiaryName().trim());
//            bankingDetails.put("account_type", "Saving");
//            request.put("banking_details", bankingDetails);
//            Map<String, Object> incomeDetails = new LinkedHashMap<>();
//            incomeDetails.put("occupation", "SelfEmployed");
//            incomeDetails.put("name_of_company", basicDetailsDto.getBussinessName().trim());
//            incomeDetails.put("monthly_income", merchantResponseDTO.getTpv1Mon().toString());
//            request.put("income_details", incomeDetails);
//            Map<String, Object> kycDetails = new LinkedHashMap<>();
//            kycDetails.put("file_name", "test.zip");
//            kycDetails.put("document_path", "test.zip");
//            request.put("kyc_details", kycDetails);
//            Map<String, Object> udf1 = new LinkedHashMap<>();
//            udf1.put("state", creditApplicationAddress.getState());
//            udf1.put("city", creditApplicationAddress.getCity());
//            udf1.put("pin_code", creditApplicationAddress.getPincode());
//            request.put("UDF1", udf1);
//            Map<String, Object> udf2 = new LinkedHashMap<>();
//            udf2.put("type_of_poa", poa.getProofType());
//            udf2.put("poa_number", poa.getProofNumber());
//            udf2.put("shop_category", basicDetailsDto.getBussinessCategory());
//            String businessAddress = creditApplicationAddress.getShopNumber() + " " + creditApplicationAddress.getStreetAddress() + " " + creditApplicationAddress.getArea() + " " + creditApplicationAddress.getCity() + " " + creditApplicationAddress.getState();
//            udf2.put("business_address", businessAddress.replace("/", " "));
//            request.put("UDF2", udf2);
//            Map<String, Object> udf3 = new LinkedHashMap<>();
//            udf3.put("lender", "LIQUILOANS");
//            udf3.put("loan_category", creditApplication.getLoanType());
//            udf3.put("repayment_type", "EDI");
//            udf3.put("is_ntc", "NO");
//            if (experian.getExperianScore() != null) {
//                double score = experian.getExperianScore() - experian.getBpScore();
//                udf3.put("customer_score", (int) score);
//            } else {
//                udf3.put("customer_score", experian.getBpScore().intValue());
//            }
//            udf3.put("bp_segment", experian.getColor());
//            request.put("UDF3", udf3);
//
//            try {
//                String checksumString = getChecksumString(request) + getSecretKey();
//                logger.info("Checksum String is {}", checksumString);
//                request.put("Checksum", lendingHmacCalculator.calculateHMACHexEncoded(checksumString, getSecretKey()));
//                HttpHeaders headers = new HttpHeaders();
//                headers.setContentType(MediaType.APPLICATION_JSON);
//                headers.setCacheControl(CacheControl.noCache());
//                String requestString = objectMapper.writeValueAsString(request);
//                logger.info("Request to liquiloans : {}", requestString);
//                LiquiloansDirectDisbursalRawResponse bean = new LiquiloansDirectDisbursalRawResponse();
//                bean.setMerchantId(lendingPaymentSchedule.getMerchantId());
//                bean.setApiName("CreateLeadV2");
//                bean.setApplicationId(lendingTlDetails.getId());
//                bean.setRequest(requestString);
//                bean.setLoanId(lendingTlDetails.getExternalLoanId());
//                bean.setStatus("PENDING");
//                bean = liquiloansDirectDisbursalRawResponseDao.save(bean);
//                HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(request, headers);
//                String responseString;
//                boolean isSuccess = false;
//                Map<String, Object> responseMap = null;
//                try {
//                    long startTime = System.currentTimeMillis();
//                    responseMap = restTemplate.postForObject(Objects.requireNonNull(env.getProperty("liquiloans.createLead.api")), requestEntity, Map.class);
//                    logger.info("Response from Liquiloans : {}", responseMap);
//                    logger.info("Liquiloans create lead api response time : {}ms, response {}", System.currentTimeMillis() - startTime, responseMap);
//                    responseString = objectMapper.writeValueAsString(responseMap);
//                    isSuccess = true;
//                } catch (HttpClientErrorException e) {
//                    logger.info("Response form Liquiloans : {}", e.getResponseBodyAsString());
//                    responseString = e.getResponseBodyAsString();
//                    logger.error("Error in api call in liquiloans create lead api for {}, {}", request, e);
//                } catch (Exception ex) {
//                    responseString = ex.getMessage();
//                    logger.error("Error in api call in liquiloans create lead api for {}, {}", request, ex);
//                }
//                if (isSuccess) {
//                    if (responseMap != null && (responseMap.get("status") == null || !"true".equalsIgnoreCase(responseMap.get("status").toString()))) {
//                        isSuccess = false;
//                    }
//                }
//                if (isSuccess && responseMap != null && responseMap.get("data") != null) {
//                    String nbfcId = ((Map<String, Object>) responseMap.get("data")).get("loan_id").toString();
//                    bean.setLiquiloanId(nbfcId);
//                    lendingTlDetailsDao.updateNbfcId(lendingTlDetails.getId(), nbfcId);
//                }
//                bean.setResponse(responseString);
//                bean.setStatus(isSuccess ? "SUCCESS" : "FAILED");
//                liquiloansDirectDisbursalRawResponseDao.save(bean);
//            } catch (Exception e) {
//                logger.error("Exception in create lead api", e);
//            }
//        } catch (Exception e) {
//            logger.error("Exception in create lead api for loan_id: {}", lendingTlDetails.getExternalLoanId());
//            logger.error("Exception---", e);
//        }
//    }

    private String getSecretKey() {
        if (secretKey == null) {
            secretKey = env.getProperty("liquiloan.secret");
        }
        return secretKey;
    }

    private String getSID() {
        if (SID == null) {
            SID = env.getProperty("liquiloan.sid");
        }
        return SID;
    }

    private String getChecksumString(Map<String, Object> request) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : request.entrySet()) {
            if (request.get(entry.getKey()) == null) {
                continue;
            }
            if (request.get(entry.getKey()) instanceof List || request.get(entry.getKey()) instanceof Map) {
                map.put(entry.getKey(), objectMapper.writeValueAsString(request.get(entry.getKey())));
            } else {
                map.put(entry.getKey(), request.get(entry.getKey()));
            }
        }
        return StringUtils.collectionToDelimitedString(map.values(), "||");
    }

    public void createEdiSchedule(LendingPaymentSchedule paymentSchedule) {
        if (null != paymentSchedule.getNbfc() && paymentSchedule.getNbfc().equalsIgnoreCase("PIRAMAL")) {
            boolean flag;
            int retry = 0;
            do {
                flag = constructPiramalEDISchedule(paymentSchedule);
                retry++;
            } while (!flag && retry < 3);
            if(!flag) {
                constructBharatPeEDISchedule(paymentSchedule);
            }
        } else if (!ObjectUtils.isEmpty(paymentSchedule) && Arrays.asList("USFB", "TRILLIONLOANS", "MUTHOOT", "CAPRI").contains(paymentSchedule.getNbfc()))  {
            boolean success = constructLenderEDISchedule(paymentSchedule);
            if(!success) {
                logger.info("creating bharatPe edi schedule as failed to create lender edi schedule for {}", paymentSchedule.getApplicationId());
                constructBharatPeEDISchedule(paymentSchedule);
            }
        } else {
            constructBharatPeEDISchedule(paymentSchedule);
        }
    }

    public void constructBharatPeEDISchedule(LendingPaymentSchedule paymentSchedule) {
        try {
            List<LendingEDISchedule> scheduleList = lendingEDIScheduleDao.findByLendingPaymentSchedule(paymentSchedule);
            if (scheduleList != null && !scheduleList.isEmpty()) {
                logger.info("EDI schedule already exist for Loan ID {}.", paymentSchedule.getId());
                return;
            }
            logger.info("Creating EDI schedule for Loan ID {}.", paymentSchedule.getId());
            int installmentNo = 1;
            int ediCount = paymentSchedule.getEdiCount();
            Double openingBalance = paymentSchedule.getLoanAmount();
            String construct = paymentSchedule.getLoanConstruct();
            double totalInterest = 0D;
            Double totalPrincipal = 0D;
            List<LendingEDISchedule> ediSchedules = new ArrayList<>();
            double procFee = paymentSchedule.getLoanApplication() == null ? 0D : paymentSchedule.getLoanApplication().getProcessingFee();
            Long storeId = paymentSchedule.getMerchantStoreId() == null ? null : paymentSchedule.getMerchantStoreId();
            if (procFee > 0D) {
                ediSchedules.add(createProcFeeSchedule(paymentSchedule, storeId));
            }
            if ("CONSTRUCT_2".equals(construct) || "CONSTRUCT_3".equals(construct)) {
                if ("CONSTRUCT_3".equals(construct)) {
                    int ioInstallmentNo = 1;
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(paymentSchedule.getInterestOnlyStartDate());
                    while (ioInstallmentNo <= paymentSchedule.getInterestOnlyEdiCount()) {
                        if (cal.get(Calendar.DAY_OF_WEEK) == getOffDayNumber(paymentSchedule.getOffDay())) {
                            cal.add(Calendar.DAY_OF_MONTH, 1);
                        } else {
                            LendingEDISchedule currentSchedule = new LendingEDISchedule();
                            currentSchedule.setConstruct(construct);
                            currentSchedule.setDate(cal.getTime());
                            currentSchedule.setEdiType("Principal Morat");
                            currentSchedule.setInstallmentNumber(installmentNo);
                            currentSchedule.setOpeningBalance(openingBalance);
                            currentSchedule.setInterest(paymentSchedule.getInterestOnlyEdiAmount());
                            currentSchedule.setPrinciple(0D);
                            currentSchedule.setProcessingFee(0D);
                            currentSchedule.setTotalEdi(paymentSchedule.getInterestOnlyEdiAmount().intValue());
                            currentSchedule.setOtherCharges(0D);
                            currentSchedule.setMerchantId(paymentSchedule.getMerchantId());
                            currentSchedule.setLoanApplication(paymentSchedule.getLoanApplication());
                            currentSchedule.setLendingPaymentSchedule(paymentSchedule);
                            currentSchedule.setMerchantStoreId(storeId);
                            ediSchedules.add(currentSchedule);

                            totalInterest = totalInterest + paymentSchedule.getInterestOnlyEdiAmount();

                            installmentNo++;
                            ioInstallmentNo++;

                            cal.add(Calendar.DAY_OF_MONTH, 1);
                        }
                    }
                }
            }
            Calendar cal = Calendar.getInstance();
            cal.setTime(paymentSchedule.getStartDate());
            Double reducingInterestRateDaily = Finance.rate(ediCount, paymentSchedule.getEdiAmount().intValue(), paymentSchedule.getLoanAmount());
            int normalEdIinstallmentNo = 1;
            while (normalEdIinstallmentNo <= ediCount) {
                if (cal.get(Calendar.DAY_OF_WEEK) == getOffDayNumber(paymentSchedule.getOffDay())) {
                    cal.add(Calendar.DAY_OF_MONTH, 1);
                } else {
                    Double principal = round(Finance.ppmt(reducingInterestRateDaily, normalEdIinstallmentNo, ediCount, -1 * paymentSchedule.getLoanAmount()));
                    double interest = round(paymentSchedule.getEdiAmount().intValue() - principal);

                    if (Lender.PIRAMAL.name().equalsIgnoreCase(paymentSchedule.getNbfc())) {
                        interest = roundToWhole(interest);
                        principal = paymentSchedule.getEdiAmount().intValue() - interest;
                    }

                    if (normalEdIinstallmentNo == ediCount) {
                        if (!paymentSchedule.getLoanAmount().equals(totalPrincipal + principal)) {
                            double diff = paymentSchedule.getLoanAmount() - (totalPrincipal + principal);
                            principal = round(paymentSchedule.getLoanAmount() - totalPrincipal);
                            interest = round(interest - diff < 0 ? 0 : interest - diff);
                        }
                    }
                    totalPrincipal = totalPrincipal + principal;
                    totalInterest = totalInterest + interest;
                    LendingEDISchedule currentSchedule = new LendingEDISchedule();
                    currentSchedule.setConstruct(construct);
                    currentSchedule.setDate(cal.getTime());
                    currentSchedule.setEdiType("Regular");
                    currentSchedule.setInstallmentNumber(installmentNo);
                    currentSchedule.setOpeningBalance(round(openingBalance));
                    currentSchedule.setInterest(interest);
                    currentSchedule.setPrinciple(principal);
                    currentSchedule.setProcessingFee(0D);
                    currentSchedule.setTotalEdi((int) (principal + interest));
                    currentSchedule.setOtherCharges(0D);
                    currentSchedule.setMerchantId(paymentSchedule.getMerchantId());
                    currentSchedule.setLoanApplication(paymentSchedule.getLoanApplication());
                    currentSchedule.setLendingPaymentSchedule(paymentSchedule);
                    currentSchedule.setMerchantStoreId(storeId);
                    ediSchedules.add(currentSchedule);
                    openingBalance = openingBalance - principal;
                    installmentNo++;
                    normalEdIinstallmentNo++;
                    cal.add(Calendar.DAY_OF_MONTH, 1);
                }
            }
            lendingEDIScheduleDao.saveAll(ediSchedules);
            paymentSchedule.setInterest(totalInterest);
            paymentSchedule.setOtherCharges(0D);
            paymentSchedule.setTentativeClosingDate(cal.getTime());
            lendingPaymentScheduleDao.save(paymentSchedule);
        } catch (Exception ex) {
            logger.error("Exception while creating schedule for Loan ID {}, Exception is {}", paymentSchedule.getId(), ex);
        }
    }

    public boolean constructPiramalEDISchedule(LendingPaymentSchedule paymentSchedule) {
        try {
            List<LendingEDISchedule> scheduleList = lendingEDIScheduleDao.findByLendingPaymentSchedule(paymentSchedule);
            if (scheduleList != null && !scheduleList.isEmpty()) {
                logger.info("EDI schedule already exist for Loan ID {}.", paymentSchedule.getId());
                return true;
            }
            logger.info("Creating EDI schedule for Loan ID {}.", paymentSchedule.getId());
            PiramalGetLoanResponseDto piramalGetLoanResponseDto = piramalGetLoanDetails.getLoanDetails(paymentSchedule.getLoanApplication().getId());
            logger.info("response from piramal loan details for application id : {} is : {}", paymentSchedule.getLoanApplication().getId(), piramalGetLoanResponseDto);
            if (ObjectUtils.isEmpty(piramalGetLoanResponseDto)) {
                logger.info("failure response from piramal get loan api");
                return false;
            }
            int ediCount = piramalGetLoanResponseDto.getLoanTenor();
            Double openingBalance = piramalGetLoanResponseDto.getLoanAmount().doubleValue();
            List<LendingEDISchedule> ediSchedules = new ArrayList<>();
            double procFee = paymentSchedule.getLoanApplication() == null ? 0D : paymentSchedule.getLoanApplication().getProcessingFee();
            Long storeId = paymentSchedule.getMerchantStoreId() == null ? null : paymentSchedule.getMerchantStoreId();
            if (procFee > 0D) {
                ediSchedules.add(createProcFeeSchedule(paymentSchedule, storeId));
            }
            Calendar calendar = Calendar.getInstance();
            for (int arr_i = 1; arr_i < piramalGetLoanResponseDto.getRepaymentSchedule().size(); arr_i++) {
                PiramalGetLoanResponseDto.LoanSchedule loanSchedule = piramalGetLoanResponseDto.getRepaymentSchedule().get(arr_i);
                LendingEDISchedule currentSchedule = new LendingEDISchedule();
                calendar.setTimeInMillis(loanSchedule.getScheduledDate());
                currentSchedule.setDate(calendar.getTime());
                currentSchedule.setEdiType("Regular");
                currentSchedule.setInstallmentNumber(arr_i);
                currentSchedule.setOpeningBalance(loanSchedule.getEndBalance().doubleValue());
                currentSchedule.setInterest(loanSchedule.getScheduledInterest().doubleValue());
                currentSchedule.setPrinciple(loanSchedule.getScheduledPrincipal().doubleValue());
                currentSchedule.setProcessingFee(0D);
                currentSchedule.setTotalEdi(loanSchedule.getScheduledTotal().intValue());
                currentSchedule.setOtherCharges(0D);
                currentSchedule.setMerchantId(paymentSchedule.getMerchantId());
                currentSchedule.setLoanApplication(paymentSchedule.getLoanApplication());
                currentSchedule.setLendingPaymentSchedule(paymentSchedule);
                currentSchedule.setMerchantStoreId(storeId);
                ediSchedules.add(currentSchedule);
            }
            lendingEDIScheduleDao.saveAll(ediSchedules);
            paymentSchedule.setInterest(piramalGetLoanResponseDto.getTotalInterestPayable().doubleValue());
            paymentSchedule.setOtherCharges(0D);
            paymentSchedule.setTentativeClosingDate(piramalGetLoanResponseDto.getMaturityDate());
            lendingPaymentScheduleDao.save(paymentSchedule);
            return true;
        } catch (Exception ex) {
            logger.error("Exception while creating schedule for Loan ID {}, Exception is {}", paymentSchedule.getId(), ex);
            return false;
        }
    }

    public boolean constructLenderEDISchedule(LendingPaymentSchedule paymentSchedule) {
        try {
            List<LendingEDISchedule> scheduleList = lendingEDIScheduleDao.findByLendingPaymentSchedule(paymentSchedule);
            if (scheduleList != null && !scheduleList.isEmpty()) {
                logger.info("EDI schedule already exist for Loan ID {}.", paymentSchedule.getId());
                return true;
            }
            logger.info("Creating EDI schedule of {} for Loan ID {}.", paymentSchedule.getNbfc(), paymentSchedule.getId());
            LenderEdIScheduleResponseDTO lenderEdIScheduleResponse = null;
            int retry = 0;
            do {
                lenderEdIScheduleResponse = associationServiceUtil.invokeRepaymentScheduleService(paymentSchedule.getNbfc(), paymentSchedule.getLoanApplication().getId());
                retry++;
            } while (ObjectUtils.isEmpty(lenderEdIScheduleResponse) && retry < 5);
            logger.info("response from {} repayment schedule for application id : {} is : {}", paymentSchedule.getNbfc(), paymentSchedule.getLoanApplication().getId(), lenderEdIScheduleResponse);
            if (ObjectUtils.isEmpty(lenderEdIScheduleResponse)) {
                logger.info("failure response from {} repayment schedule api for {}", paymentSchedule.getNbfc(), paymentSchedule.getLoanApplication().getId());
                return false;
            }
            List<LendingEDISchedule> ediSchedules = new ArrayList<>();
            double procFee = paymentSchedule.getLoanApplication() == null ? 0D : paymentSchedule.getLoanApplication().getProcessingFee();
            Long storeId = paymentSchedule.getMerchantStoreId() == null ? null : paymentSchedule.getMerchantStoreId();
            if (procFee > 0D) {
                ediSchedules.add(createProcFeeSchedule(paymentSchedule, storeId));
            }
            for (int arr_i = 1; arr_i < lenderEdIScheduleResponse.getRepaymentSchedule().size(); arr_i++) {
                LenderEdIScheduleResponseDTO.RepaymentSchedule loanSchedule = lenderEdIScheduleResponse.getRepaymentSchedule().get(arr_i);
                LendingEDISchedule currentSchedule = new LendingEDISchedule();
                currentSchedule.setDate(loanSchedule.getDueDate());
                currentSchedule.setEdiType("Regular");
                currentSchedule.setInstallmentNumber(arr_i);
                currentSchedule.setOpeningBalance(loanSchedule.getOpeningBalance());
                currentSchedule.setInterest(loanSchedule.getInterest());
                currentSchedule.setPrinciple(loanSchedule.getPrincipal());
                currentSchedule.setProcessingFee(0D);
                currentSchedule.setTotalEdi(loanSchedule.getTotalEdi());
                currentSchedule.setOtherCharges(0D);
                currentSchedule.setMerchantId(paymentSchedule.getMerchantId());
                currentSchedule.setLoanApplication(paymentSchedule.getLoanApplication());
                currentSchedule.setLendingPaymentSchedule(paymentSchedule);
                currentSchedule.setMerchantStoreId(storeId);
                ediSchedules.add(currentSchedule);
            }
            lendingEDIScheduleDao.saveAll(ediSchedules);
            paymentSchedule.setInterest(lenderEdIScheduleResponse.getTotalInterestPayable());
            paymentSchedule.setOtherCharges(0D);
            paymentSchedule.setTentativeClosingDate(lenderEdIScheduleResponse.getLoanMaturityDate());
            if (Arrays.asList(Lender.TRILLIONLOANS.name(), Lender.MUTHOOT.name(), Lender.CAPRI.name()).contains(paymentSchedule.getNbfc())) {
                LendingApplication lendingApplication = paymentSchedule.getLoanApplication();
                Double totalPayableAmount = lendingApplication.getLoanAmount() + lenderEdIScheduleResponse.getTotalInterestPayable();
                paymentSchedule.setTotalPayableAmount(totalPayableAmount);
                lendingApplication.setRepayment(totalPayableAmount);
                lendingApplicationDao.save(lendingApplication);
            }
            lendingPaymentScheduleDao.save(paymentSchedule);
            return true;
        } catch (Exception ex) {
            logger.error("Exception while creating schedule of {} for Loan ID {}, Exception is {}", paymentSchedule.getNbfc(), paymentSchedule.getId(), ex);
            return false;
        }
    }

    public Integer getOffDayNumber(String dayOff) {
        return (dayOff != null && !"".equals(dayOff)) ? Loan.OffDay.valueOf(dayOff.toUpperCase()).getDayNumber() : -1;
    }

    private LendingEDISchedule createProcFeeSchedule(LendingPaymentSchedule paymentSchedule, Long storeId) {
        Double procFee = paymentSchedule.getLoanApplication().getProcessingFee();
        Calendar cal = Calendar.getInstance();
        if (paymentSchedule.getInterestOnlyStartDate() != null) {
            cal.setTime(paymentSchedule.getInterestOnlyStartDate());
        } else {
            cal.setTime(paymentSchedule.getStartDate());
        }
        cal.add(Calendar.DAY_OF_MONTH, -1);
        LendingEDISchedule schedule = new LendingEDISchedule();
        schedule.setConstruct(paymentSchedule.getLoanConstruct());
        schedule.setDate(cal.getTime());
        schedule.setEdiType("");
        schedule.setInstallmentNumber(0);
        schedule.setInterest(0D);
        schedule.setOpeningBalance(paymentSchedule.getLoanAmount());
        schedule.setPrinciple(0D);
        schedule.setOtherCharges(0D);
        schedule.setProcessingFee(procFee);
        schedule.setTotalEdi(procFee.intValue());
        schedule.setMerchantId(paymentSchedule.getMerchantId());
        schedule.setLoanApplication(paymentSchedule.getLoanApplication());
        schedule.setLendingPaymentSchedule(paymentSchedule);
        schedule.setMerchantStoreId(storeId);
        return schedule;
    }

    private static double round(double value) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private static double roundToWhole(double value) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(0, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private String getBeneficiaryName(String beneficiaryName) {
        if (beneficiaryName.length() > 25) {
            beneficiaryName = beneficiaryName.substring(0, 25);
        }
        return beneficiaryName;
    }

    public void sendWPAndSMSNotification(LendingApplication lendingApplication, Boolean autoNotify) {
        if (Lender.PIRAMAL.name().equalsIgnoreCase(lendingApplication.getLender()) && autoNotify) {
            logger.info("skipped communication for Piramal for application {}", lendingApplication.getId());
            return;
        }
        logger.info("notification send request received for SMS and WhatsApp for application->{}", lendingApplication.getId());
        String shortUrl = null;
        try {
//            LoanAgreement loanAgreement = loanAgreementDao.findByApplicationIdAndType(lendingApplication.getId(), "agreement");
            MerchantDetailsDto merchantDetailsDTO = merchantService.fetchMerchantDetails(lendingApplication.getMerchantId(), Collections.singletonList(Constants.MerchantUtil.Scope.BANK_DETAIL));
            BasicDetailsDto basicDetailsDto = merchantDetailsDTO.getMerchantDetail();
            BankDetailsDto merchantBankDetail = merchantDetailsDTO.getBankDetail();
            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
            if (ObjectUtils.isEmpty(basicDetailsDto)) {
                return;
            }
            if (merchantBankDetail == null) {
                return;
            }
//            if (loanAgreement != null) {
//                String fileName = loanAgreement.getAgreementName();
//                try {
//                    logger.info("Fetching agreement URL for merchant->{}", lendingApplication.getMerchantId());
//                    shortUrl = liquiloansService.getShorturl(fileName, loanAgreement);
//                    logger.info("Short URL->{}", shortUrl);
//                } catch (UnsupportedEncodingException e) {
//                    e.printStackTrace();
//                }
//            }
            if(ObjectUtils.isEmpty(lendingKfs)){
                logger.info("Lending kfs is null");
                return;
            }
            String identifierWP = env.getProperty("kfs.wp.notification");
            String identifierSMS = env.getProperty("kfs.sms.notification");
            String message = env.getProperty("kfs.template.notification");

            String loanAgreementName = Optional.ofNullable(lendingKfs.getSanctionLoanAgreementDocFile()).orElse(SANCTION_LOAN_AGREEMENT_S3_KEY_PREFIX + lendingKfs.getApplicationId());
            String kfsName = Optional.ofNullable(lendingKfs.getKfsDocFile()).orElse(KFS_S3_KEY_PREFIX + lendingKfs.getApplicationId());

            String loanAgreementUrl = s3BucketHandler.getPreSignedPublicURL(loanAgreementName, "loan-document");
            String kfsDocUrl = s3BucketHandler.getPreSignedPublicURL(kfsName, "loan-document");

            String loanAgreementShortUrl = apiGatewayService.getShortUrl(loanAgreementUrl);
            if(loanAgreementShortUrl == null || loanAgreementShortUrl.isEmpty() || loanAgreementShortUrl.trim().isEmpty())throw new Exception("Unable to create short URL for Sanction Loan Agreement doc link for : " + lendingApplication.getId());
            String kfsDocShortUrl = apiGatewayService.getShortUrl(kfsDocUrl);
            if(kfsDocShortUrl == null || kfsDocShortUrl.isEmpty() || kfsDocShortUrl.trim().isEmpty())throw new Exception("Unable to create short URL for KFS doc link for : " + lendingApplication.getId());

            Map<String, Object> templateParams = new HashMap<>();
            templateParams.put("Merchant Name", basicDetailsDto.getBeneficiaryName());
            templateParams.put("Link 1", loanAgreementShortUrl);
            templateParams.put("Link 2", kfsDocShortUrl);

            //FOR SMS
            NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
            notificationPayloadDto.setTemplateIdentifier(identifierSMS);
            notificationPayloadDto.setClientName("LENDING");
            notificationPayloadDto.setMobile(basicDetailsDto.getMobile());//basicDetailsDto.getMobile()
            notificationPayloadDto.setTemplateParams(templateParams);

            logger.info("payload for SMS: {}", notificationPayloadDto);

            // FOR WHATSAPP
            NotificationPayloadDto notificationPayloadDto2 = new NotificationPayloadDto();
            notificationPayloadDto2.setTemplateIdentifier(identifierWP);
            notificationPayloadDto2.setClientName("LENDING");
            notificationPayloadDto2.setMobile(basicDetailsDto.getMobile());
            notificationPayloadDto2.setTemplateParams(templateParams);

            logger.info("payload for WHATSAPP: {}", notificationPayloadDto2);

            executorService.execute(() -> lendingNotificationService.notify(notificationPayloadDto));
            lendingKfs.setSmsSendAt(new Date());
            executorService.execute(() -> lendingNotificationService.notify(notificationPayloadDto2));
            lendingKfs.setWhatsappSendAt(new Date());

            message = message.replace("{Merchant Name}", basicDetailsDto.getBeneficiaryName());
            message = message.replace("{{Link 1}}", loanAgreementShortUrl);
            message = message.replace("{{Link 2}}", kfsDocShortUrl);
            logger.info("message->{}", message);
            lendingKfs.setMessage(message);
            lendingKfsDao.save(lendingKfs);
        } catch (Exception ex) {
            logger.error("Exception occurred while sending notification for merchant->{}, {}, {}", lendingApplication.getMerchantId(), ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
    }

    public String testNotification(Long applicationId){
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
        if(lendingApplication.isPresent()){
            liquiloansService.sendWPAndSMSNotification(lendingApplication.get(), true);
            return "SUCCESS";
        }
        return "FAILED";
    }

    public Date getDisburseTimestamp(Date utrTimestamp, Date webhookTimestamp){
        if(ObjectUtils.isEmpty(utrTimestamp))return webhookTimestamp;
        int daysDiff = daysDifference(utrTimestamp, webhookTimestamp);
        if(daysDiff == 1  && webhookTimestamp.getHours() < 4)return utrTimestamp;
        return webhookTimestamp;
    }

    public static int daysDifference(Date d1, Date d2){
        Calendar dayOne = Calendar.getInstance();
        dayOne.setTime(d1);
        Calendar dayTwo = Calendar.getInstance();
        dayTwo.setTime(d2);
        if (dayOne.get(Calendar.YEAR) == dayTwo.get(Calendar.YEAR)) {
            return Math.abs(dayOne.get(Calendar.DAY_OF_YEAR) - dayTwo.get(Calendar.DAY_OF_YEAR));
        } else {
            if (dayTwo.get(Calendar.YEAR) > dayOne.get(Calendar.YEAR)) {
                //swap them
                Calendar temp = dayOne;
                dayOne = dayTwo;
                dayTwo = temp;
            }
            int extraDays = 0;
            int dayOneOriginalYearDays = dayOne.get(Calendar.DAY_OF_YEAR);
            while (dayOne.get(Calendar.YEAR) > dayTwo.get(Calendar.YEAR)) {
                dayOne.add(Calendar.YEAR, -1);
                // getActualMaximum() important for leap years
                extraDays += dayOne.getActualMaximum(Calendar.DAY_OF_YEAR);
            }
            return extraDays - dayTwo.get(Calendar.DAY_OF_YEAR) + dayOneOriginalYearDays ;
        }
    }

    private void saveDisbursalUtr(Long applicationId, String lender, String utr) {

        // trim utr string length to 50
        utr = utr.substring(0, Math.min(utr.length(), 50));

        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(applicationId, com.bharatpe.lending.common.enums.Status.ACTIVE.name(), lender);

        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            logger.info("lendingApplicationLenderDetails not found for applicationId : {} and lender : {}", applicationId, lender);
            lendingApplicationLenderDetails = new LendingApplicationLenderDetails();
            lendingApplicationLenderDetails.setApplicationId(applicationId);
            lendingApplicationLenderDetails.setLender(lender.toUpperCase());
            lendingApplicationLenderDetails.setStatus(com.bharatpe.lending.common.enums.Status.ACTIVE.name());
        }

        lendingApplicationLenderDetails.setUtrNo(utr);
        lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
    }

    private Boolean saveSignedDocsForLender(LendingApplication lendingApplication) {
        try {
            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(lendingApplication.getId());
            if (ObjectUtils.isEmpty(lendingKfs)) {
                logger.info("lendingKfs not found for application {}", lendingApplication.getId());
                return false;
            }
            if(!ObjectUtils.isEmpty(lendingKfs.getSignedKfsDocUrl()) && !ObjectUtils.isEmpty(lendingKfs.getSignedSanctionDocUrl())) {
                logger.info("Signed docs already exists for applicationId : {}", lendingApplication.getId());
            }
            Boolean success = associationServiceUtil.invokeFetchSignedDocsService(lendingApplication.getLender(), lendingApplication);
            if(success) {
                logger.info("Successfully fetched and saved signed docs of {} for application {}", lendingApplication.getLender(), lendingApplication.getId());
            }
            return success;
        } catch (Exception ex) {
            logger.error("Exception while saving signed docs of {} for applicationId {}, Exception is {} {}", lendingApplication.getLender(), lendingApplication.getId(), ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            return false;
        }
    }
}