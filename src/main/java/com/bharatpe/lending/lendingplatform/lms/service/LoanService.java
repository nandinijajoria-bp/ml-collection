package com.bharatpe.lending.lendingplatform.lms.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LmsLoanStatusDao;
import com.bharatpe.lending.common.dto.KafkaAudit;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LmsLoanStatus;
import com.bharatpe.lending.common.enums.FunnelEnums;
import com.bharatpe.lending.common.enums.LenderOffDays;
import com.bharatpe.lending.common.enums.LoanSettlementMechanism;
import com.bharatpe.lending.common.service.FunnelService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.NbfcStatusApiResponseDTO;
import com.bharatpe.lending.dto.PostPayoutAuditDto;
import com.bharatpe.lending.dto.PostPayoutRequestDto;
import com.bharatpe.lending.dto.PostPayoutResponseDto;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.lendingplatform.lms.constant.Constants;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.LiquiloansAsyncService;
import com.bharatpe.lending.service.LiquiloansService;
import com.bharatpe.lending.service.VerifyOTPService;
import com.bharatpe.lending.util.DisbursalStageMapping;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class LoanService {

    @Autowired
    private LiquiloansService liquiloansService;

    @Autowired
    private LendingApplicationDao lendingApplicationDao;

    @Autowired
    private LoanDashboardService loanDashboardService;

    @Autowired
    private FunnelService funnelService;

    @Autowired
    private LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    private VerifyOTPService verifyOTPService;

    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    @Qualifier("ConfluentKafkaTemplate")
    KafkaTemplate<String, Object> confluentKafkaTemplate;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private APIGatewayService apiGatewayService;

    @Autowired
    private LendingKfsDao lendingKfsDao;

    @Autowired
    private LmsLoanCreationService lmsLoanCreationService;

    @Autowired
    private LmsLoanStatusDao lmsLoanStatusDao;

    @Autowired
    @Lazy
    private LiquiloansAsyncService liquiloansAsyncService;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    private final Logger logger = LoggerFactory.getLogger(LoanService.class);

    public ResponseEntity<PostPayoutResponseDto> populatePostPayoutSchedule(PostPayoutRequestDto postPayoutRequestDto) {
        LendingApplication lendingApplication = lendingApplicationDao.findByExternalLoanId(postPayoutRequestDto.getApplicationId());

        if (ObjectUtils.isEmpty(lendingApplication)) {
            logger.error("Loan application not found for application id {}", postPayoutRequestDto.getApplicationId());
            return handleFailure(new PostPayoutResponseDto(), new PostPayoutAuditDto(),
                    new KafkaAudit<>("easy_loan", "lending", "post_payout", null),
                    "Invalid applicationId", HttpStatus.BAD_REQUEST);
        }

        try {
            return processPostPayoutSchedule(postPayoutRequestDto, lendingApplication);
        } catch (Exception e) {
            logger.error("Unexpected error in populatePostPayoutSchedule: {}", Arrays.toString(e.getStackTrace()));
            logger.info("Changing loan_disbursal_status back to 'PENDING'");
            rollbackLendingApplicationStatus(lendingApplication);
            return handleFailure(new PostPayoutResponseDto(), new PostPayoutAuditDto(),
                    new KafkaAudit<>("easy_loan", "lending", "post_payout", null),
                    "Unexpected server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<PostPayoutResponseDto> processPostPayoutSchedule(PostPayoutRequestDto postPayoutRequestDto, LendingApplication lendingApplication) {
        PostPayoutResponseDto responseDto = initializeResponse(postPayoutRequestDto);
        KafkaAudit<PostPayoutAuditDto> kafkaAudit = new KafkaAudit<>("easy_loan", "lending", "post_payout", null);
        PostPayoutAuditDto auditDto = createAuditDto(postPayoutRequestDto);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY); // Include all fields
        objectMapper.configure(MapperFeature.USE_STD_BEAN_NAMING, true); // Use standard Java field names

        Map<String, Object> disbursalRequestMap = objectMapper.convertValue(postPayoutRequestDto, new TypeReference<Map<String, Object>>() {});
        insertLoanDetails(lendingApplication.getExternalLoanId(), disbursalRequestMap);

        // Remove the previous loan details for top-up cases
        Optional<LendingPaymentSchedule> prevScheduleOptional = updatePreviousLoan(lendingApplication);
        LendingPaymentSchedule prevSchedule = prevScheduleOptional.orElse(null);

        if (!ObjectUtils.isEmpty(postPayoutRequestDto.getUtr())) {
            String utr = postPayoutRequestDto.getUtr().substring(0, Math.min(postPayoutRequestDto.getUtr().length(), 50));
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), com.bharatpe.lending.common.enums.Status.ACTIVE.name(), postPayoutRequestDto.getLender());
            lendingApplicationLenderDetails.setUtrNo(utr);
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        }

        BasicDetailsDto basicDetails = fetchMerchantBasicDetails(lendingApplication, postPayoutRequestDto, responseDto, auditDto, kafkaAudit);
        if (ObjectUtils.isEmpty(basicDetails)) {
            return new ResponseEntity<>(responseDto, HttpStatus.BAD_REQUEST);
        }

        String disbursalStage = determineDisbursalStage(lendingApplication, postPayoutRequestDto);
        updateLendingApplicationWithNbfcDetails(lendingApplication, postPayoutRequestDto, disbursalStage);

        if (isLenderMismatch(lendingApplication, postPayoutRequestDto)) {
            logger.error("Lender mismatch or loan not found for {}", lendingApplication.getMerchantId());
            return handleFailure(responseDto, auditDto, kafkaAudit, "Lender mismatch or loan not found", HttpStatus.BAD_REQUEST);
        }

        try {
        if (Constants.DISBURSED_LOAN.equalsIgnoreCase(disbursalStage)) {
            return processDisbursedLoan(postPayoutRequestDto, lendingApplication, prevSchedule, responseDto, auditDto, kafkaAudit, basicDetails);
        } else if (Constants.DISBURSAL_STATUS_UNKNOWN.equalsIgnoreCase(disbursalStage)) {
            logger.info("Unknown application status {} for the application id {}", postPayoutRequestDto.getDisbursedAmount(), lendingApplication.getId());
            return handleFailure(responseDto, auditDto, kafkaAudit, "UNKNOWN status code", HttpStatus.BAD_REQUEST);
        } else {
            return processNonDisbursedLoan(lendingApplication, postPayoutRequestDto, responseDto, auditDto, kafkaAudit, prevSchedule);
        }
        } catch (Exception e) {
            logger.error("Unexpected error in processPostPayoutSchedule: {}", Arrays.toString(e.getStackTrace()));
            rollbackLendingApplicationStatus(lendingApplication);
            return handleFailure(responseDto, auditDto, kafkaAudit, "Unexpected server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void insertLoanDetails(String externalLoanId, Map<String, Object> disbursalRequestMap) {
        LmsLoanStatus lmsLoanStatus = lmsLoanStatusDao.findLoanByBpLoanIdAndStatus(externalLoanId, "INIT");

        logger.info("Checking if LmsLoanStatus exists for bpLoanId: {} with INIT status", externalLoanId);

        if (ObjectUtils.isEmpty(lmsLoanStatus)) {
            LmsLoanStatus newLmsLoanStatus = new LmsLoanStatus();
            newLmsLoanStatus.setBpLoanId(externalLoanId);
            newLmsLoanStatus.setStatus("INIT");
            newLmsLoanStatus.setDisbursalRequest(disbursalRequestMap);
            newLmsLoanStatus.setCreatedAt(new Date());

            lmsLoanStatusDao.save(newLmsLoanStatus);
        } else {
            // Already exists — skip
            log.info("LmsLoanStatus already exists for bpLoanId: {} with INIT status. Skipping insert.", externalLoanId);
        }
    }
    private void rollbackLendingApplicationStatus(LendingApplication lendingApplication) {
        if (!Constants.DISBURSED_LOAN.equalsIgnoreCase(lendingApplication.getLoanDisbursalStatus())) {
            lendingApplication.setDisburseTimestamp(null);
            lendingApplication.setLoanDisbursalStatus("PENDING");
            lendingApplicationDao.save(lendingApplication);
            logger.info("Rolled back lendingApplication status to PENDING for applicationId: {}", lendingApplication.getId());
        }
    }

    private PostPayoutResponseDto initializeResponse(PostPayoutRequestDto request) {
        PostPayoutResponseDto response = new PostPayoutResponseDto();
        response.setStatus("SUCCESS");
        response.setApplicationId(request.getApplicationId());
        response.setNbfcId(request.getNbfcId());
        return response;
    }

    private PostPayoutAuditDto createAuditDto(PostPayoutRequestDto request) {
        PostPayoutAuditDto auditDto = new PostPayoutAuditDto();
        auditDto.setPostPayoutRequest(request);
        auditDto.setExternalLoanId(request.getApplicationId());
        auditDto.setStatus(request.getLoanDisbursalStatus().toUpperCase());
        auditDto.setLender(request.getLender().toUpperCase());
        return auditDto;
    }

    private BasicDetailsDto fetchMerchantBasicDetails(LendingApplication lendingApplication, PostPayoutRequestDto request, PostPayoutResponseDto response, PostPayoutAuditDto auditDto, KafkaAudit<PostPayoutAuditDto> kafkaAudit) {
        Optional<BasicDetailsDto> basicDetailsOptional = merchantService.fetchMerchantBasicDetails(lendingApplication.getMerchantId());
        if (!basicDetailsOptional.isPresent()) {
            logger.error("Merchant details not found for merchant id {} and application {}", lendingApplication.getMerchantId(), lendingApplication.getId());
            auditDto.setExternalLoanId(lendingApplication.getExternalLoanId());
            handleFailure(response, auditDto, kafkaAudit, "Invalid data", HttpStatus.BAD_REQUEST);
            return null;
        }
        return basicDetailsOptional.get();
    }

    private String determineDisbursalStage(LendingApplication lendingApplication, PostPayoutRequestDto request) {
        return DisbursalStageMapping.getDisbursedStage(lendingApplication.getLender().toUpperCase(), request.getLoanDisbursalStatus().toUpperCase());
    }

    private void updateLendingApplicationWithNbfcDetails(LendingApplication application, PostPayoutRequestDto request, String disbursalStage) {
        if (application.getLender().equalsIgnoreCase(request.getLender().toUpperCase()) && ObjectUtils.isEmpty(application.getNbfcId())) {
            NbfcStatusApiResponseDTO nbfcStatus = apiGatewayService.getNbfcStatus(application.getId());
            logger.info("nbfcStatusApiResponseDTO for applicationId : {} {}", application.getId(), nbfcStatus);
            if (!ObjectUtils.isEmpty(nbfcStatus) && nbfcStatus.getSuccess() && "SUCCESS".equalsIgnoreCase(nbfcStatus.getStatus())) {
                application.setNbfcId(nbfcStatus.getLoanId());
                application.setLoanDisbursalStatus(disbursalStage);
                application.setSendToNbfc("YES");

                if (ObjectUtils.isEmpty(application.getNbfcSendDate())) {
                    application.setNbfcSendDate(new Date());
                }
            }
        }
    }

    private boolean isLenderMismatch(LendingApplication application, PostPayoutRequestDto request) {
        return ObjectUtils.isEmpty(application.getNbfcId()) ||
                !application.getNbfcId().equalsIgnoreCase(request.getNbfcId()) ||
                !application.getLender().equalsIgnoreCase(request.getLender().toUpperCase());
    }

    private ResponseEntity<PostPayoutResponseDto> processDisbursedLoan(PostPayoutRequestDto request, LendingApplication application, LendingPaymentSchedule prevSchedule, PostPayoutResponseDto response, PostPayoutAuditDto postPayoutAuditDto, KafkaAudit<PostPayoutAuditDto> kafkaAudit, BasicDetailsDto basicDetails) throws ParseException {
        LendingPaymentSchedule schedule = lendingPaymentScheduleDao.findByMerchantIdAndApplicationId(application.getMerchantId(), application.getId());

        if (null != schedule) {
            logger.error("Loan payment schedule already exists for loanId {} and merchantId {}.", request.getApplicationId(), basicDetails);
            response.setStatus("SUCCESS");
            response.setLoanStartDate(schedule.getStartDate());
            response.setNextEdiDate(schedule.getStartDate());
            response.setMessage("Duplicate Request");
            return new ResponseEntity<>(response, HttpStatus.OK);
        }

        // if difference in disbursal amount in request and disbursal amount in application > 10 then fail the request
        if (isDisbursalAmountMismatch(application, request)) {
            application.setLoanDisbursalStatus("AMOUNT_MISMATCH");
            lendingApplicationDao.save(application);
            logger.error("Disbursal amount mismatch for {}", request.getApplicationId());
            response.setStatus("FAILED");
            response.setMessage("Disbursal amount mismatch");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        //Updating lending_application table
        logger.info("Changing loan_disbursal_status to 'DISBURSED' for application_id: {}", application.getId());
        updateLendingApplicationForDisbursal(application, request);

        submitLoanDashboardEvent(application);
        //creating entry in lending_payment_schedule table
        LendingPaymentSchedule newSchedule = createLendingPaymentSchedule(application, basicDetails);

        if (EXCLUDED_LENDERS.contains(newSchedule.getNbfc())) {
            newSchedule.setSettlementMechanism(LoanSettlementMechanism.EDI_BY_EDI.name());
        }

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd 00:00:00");

        Date tomorrow = new Date(application.getDisburseTimestamp().getTime() + (1000 * 60 * 60 * 24));
        //checking if next day is Sunday or not because we don't cut edi on Sunday

        if (tomorrow.getDay() == 0 && !EXCLUDED_LENDERS.contains(application.getLender())) {
            tomorrow = new Date(tomorrow.getTime() + (1000 * 60 * 60 * 24));
        }

        tomorrow = format.parse(format.format(tomorrow));
        newSchedule.setStartDate(tomorrow);
        response.setLoanStartDate(tomorrow);

        newSchedule.setNextEdiDate(tomorrow); //Check if can be kept null
        response.setNextEdiDate(tomorrow);

        Date tenativeLoanEndDate = getDateAfterNMonths(application.getDisburseTimestamp(), application.getTenureInMonths());
        if (tenativeLoanEndDate == null) {
            response.setStatus("FAILED");
            response.setMessage("Unable to compute tentative closing date");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        newSchedule.setTentativeClosingDate(tenativeLoanEndDate);
        response.setLoanStartDate(newSchedule.getStartDate());
        response.setNextEdiDate(newSchedule.getNextEdiDate());

        if (prevSchedule != null && Arrays.asList("INACTIVE_TOPUP", "ACTIVE").contains(prevSchedule.getStatus())) {
            try {
                logger.info("Closing previous loan for application id {}", application.getId());
                verifyOTPService.closePreviousLoanAfterSuccessfulTopupCreation(application.getId());
            } catch (Exception e) {
                logger.error("Exception while closing previous loan while making Top-up application id {}, active lps is {}, e{}", application.getId(), newSchedule.getId(), Arrays.asList(e.getStackTrace()));
            }
        }

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY); // Include all fields
        objectMapper.configure(MapperFeature.USE_STD_BEAN_NAMING, true); // Use standard Java field names

        Map<String, Object> disbursalResponseMap = objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {});

        //posting details to ONE-LMS
        logger.info("Posting loan details to lending connector for bpLoanId: {}", application.getExternalLoanId());
        boolean loanCreationStatus = lmsLoanCreationService.processLoanRequest(application, newSchedule, disbursalResponseMap);
        if(!loanCreationStatus){
            response.setStatus("FAILED");
            response.setMessage("Unable to create loan at 1LMS");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        logger.info("Received success response from connector for bpLoanId: {}", application.getExternalLoanId());

// TODO: Implement EDI schedule creation for backdated loans
//       Currently, this is only applicable for ABFL, hence not used.
//       Uncomment and implement the following methods when needed:
//       createEdiSchedule(lendingPaymentSchedule);  // RPS needed here?
//       createEdiException(lendingPaymentSchedule);
//       Note: TOPUP and PDPD cases are not handled yet.

        log.info("Disbursed loan successful for applicationId: {}", application.getId());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Updates the database with the disbursed loan details.
     *
     * @param newSchedule         the new lending payment schedule
     * @param application         the lending application
     * @param lmsLoanStatus the loan migration status
     */
    @Transactional
    private void updateDBForDisbursedLoan(LendingPaymentSchedule newSchedule, LendingApplication application, LmsLoanStatus lmsLoanStatus) {
        logger.info("Updating database for disbursed loan: applicationId={}, scheduleId={}, lmsLoanStatusId={}",
                application.getId(), newSchedule.getId(), lmsLoanStatus.getId());
        lendingApplicationDao.save(application);
        lendingPaymentScheduleDao.save(newSchedule);
        lmsLoanStatusDao.save(lmsLoanStatus);
        logger.info("Database update completed for disbursed loan: applicationId={}", application.getId());
    }

    // Try-catch block only for generateWelcomeDocAndNotify to handle exceptions from previous usage,
    // No additional method required as per analysis with prev usage .
private void executeSmsAndPaymentLink(LendingApplication application, LendingPaymentSchedule finalLendingPaymentSchedule, BasicDetailsDto finalBasicDetailDto) {
    LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(application.getId());

    if (ObjectUtils.isEmpty(lendingKfs)) {
        executorService.execute(() -> liquiloansService.sendSms(application, finalLendingPaymentSchedule));
    } else {
        executorService.execute(() -> liquiloansService.sendWPAndSMSNotification(application, true));
        executorService.execute(() -> {
            try {
                liquiloansAsyncService.generateWelcomeDocAndNotify(application, finalBasicDetailDto, lendingKfs);
            } catch (Exception e) {
                logger.error("Error generating welcome document for application id {}: {}", application.getId(), e.getMessage());
            }
        });
    }

    executorService.execute(() -> apiGatewayService.globalLimitTxn(application.getMerchantId(), "DEBIT", finalLendingPaymentSchedule.getLoanAmount()));
    liquiloansService.savePaymentLink(application.getMerchantId().toString(), application.getExternalLoanId());
}

    private void submitLoanDashboardEvent(LendingApplication lendingApplication) {
        LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(lendingApplication.getMerchantId(), lendingApplication);
        String apiVersion = loanDashboardApiVersion.getApiVersion();
        Long merchantId = lendingApplication.getMerchantId();
        Long applicationId = lendingApplication.getId();
        String loanType = lendingApplication.getLoanType();
        FunnelEnums.StageId stageId = FunnelEnums.StageId.DISBURSAL;
        FunnelEnums.StageEvent stageEvent = FunnelEnums.StageEvent.COMPLETED;
        String timestamp = LocalDateTime.now().toString();

        if (LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(apiVersion)) {
            funnelService.submitEventV3(merchantId, null, applicationId, loanType, stageId, stageEvent, timestamp, LoanDetailsConstant.FUNNEL_VERSION_TAG);
        } else {
            funnelService.submitEvent(merchantId, null, applicationId, loanType, stageId, stageEvent, timestamp);
        }
    }

    private boolean isDisbursalAmountMismatch(LendingApplication application, PostPayoutRequestDto request) {
        return Math.abs(application.getDisbursalAmount() - Math.ceil(request.getDisbursedAmount())) > 10 &&
                !(LoanType.TOPUP.name().equalsIgnoreCase(application.getLoanType()) &&
                        Lender.TRILLIONLOANS.name().equalsIgnoreCase(application.getLender()));
    }

    private void updateLendingApplicationForDisbursal(LendingApplication application, PostPayoutRequestDto request) {
        application.setLoanDisbursalStatus(Constants.DISBURSED_LOAN);
        application.setDisburseTimestamp(getDisburseTimestamp(request.getDisbursalDate(), new Date()));
        application.setAccountType(determineAccountType(application.getLender()));
    }

    private String determineAccountType(String lender) {
        return Arrays.asList("HINDON", "MAMTA", "LIQUILOANS_NBFC", "TRILLIONLOANS").contains(lender) ? "NBFC_FUNDS" : "INVESTOR_FUNDS";
    }

    private ResponseEntity<PostPayoutResponseDto> processNonDisbursedLoan(LendingApplication application, PostPayoutRequestDto request, PostPayoutResponseDto response, PostPayoutAuditDto auditDto, KafkaAudit<PostPayoutAuditDto> kafkaAudit, LendingPaymentSchedule prevSchedule) {
        application.setLoanDisbursalStatus(determineDisbursalStage(application, request));
        lendingApplicationDao.save(application);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    private static final List<String> EXCLUDED_LENDERS = Arrays.asList(
            "TRILLIONLOANS"
    );

    private LendingPaymentSchedule createLendingPaymentSchedule(LendingApplication lendingApplication, BasicDetailsDto basicDetailsDto) {
        LendingPaymentSchedule lendingPaymentSchedule = new LendingPaymentSchedule();

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
        lendingPaymentSchedule.setLmsSource(Constants.ONE_LMS);
        lendingPaymentSchedule.setCreatedAt(new Date());
        lendingPaymentSchedule.setUpdatedAt(new Date());
        lendingPaymentSchedule.setOffDay(lendingApplication.getPayableDays() % 30 == 0 ?
                LenderOffDays.valueOf(lendingApplication.getLender()).getOffDay() : LendingConstants.SIX_DAY_MODEL_OFF_DAY);

        return lendingPaymentSchedule;
    }


    private ResponseEntity<PostPayoutResponseDto> handleFailure(PostPayoutResponseDto responseDto,
                                                                PostPayoutAuditDto auditDto,
                                                                KafkaAudit<PostPayoutAuditDto> kafkaAudit,
                                                                String message,
                                                                HttpStatus status) {
        responseDto.setStatus("FAILED");
        responseDto.setMessage(message);
        auditDto.setPostPayoutResponse(responseDto);
        kafkaAudit.setData(auditDto);
        pushKafkaAudit(kafkaAudit);
        return new ResponseEntity<>(responseDto, status);
    }

    public void pushKafkaAudit(KafkaAudit<?> kafkaAudit) {
        try {
            logger.info("pushing kafka event for {}", kafkaAudit);
            confluentKafkaTemplate.send("easyloan_audit_data", kafkaAudit);
        } catch (Exception e) {
            logger.error("error while sending audit data {} {}", kafkaAudit, Arrays.asList(e.getStackTrace()));
        }
    }

    private Optional<LendingPaymentSchedule> updatePreviousLoan(LendingApplication lendingApplication) {
        LendingApplication previousDisbursedApplication = lendingApplicationDao.getLastDisbursedLoan(lendingApplication.getMerchantId());

        // Returning null can lead to NullPointerException; using Optional indicates potential absence of a value.
        if (ObjectUtils.isEmpty(previousDisbursedApplication)) {
            return Optional.empty();
        }

        LendingPaymentSchedule prevLendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(previousDisbursedApplication.getId());
        logger.info("Previous LPS {} for application id {}", prevLendingPaymentSchedule, lendingApplication.getId());
        return Optional.ofNullable(prevLendingPaymentSchedule);
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

    public Date getDisburseTimestamp(Date utrTimestamp, Date webhookTimestamp){
        if(ObjectUtils.isEmpty(utrTimestamp))return webhookTimestamp;
        return utrTimestamp;
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
}