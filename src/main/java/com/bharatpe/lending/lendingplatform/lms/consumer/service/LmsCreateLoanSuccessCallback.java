package com.bharatpe.lending.lendingplatform.lms.consumer.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LmsLoanStatusDao;
import com.bharatpe.lending.common.dto.KafkaAudit;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LmsLoanStatus;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.PostPayoutAuditDto;
import com.bharatpe.lending.dto.PostPayoutRequestDto;
import com.bharatpe.lending.dto.PostPayoutResponseDto;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.lendingplatform.lms.constant.Constants;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.LiquiloansAsyncService;
import com.bharatpe.lending.service.LiquiloansService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LmsCreateLoanSuccessCallback {

    private final ObjectMapper objectMapper;
    private final LmsLoanStatusDao lmsLoanStatusDao;
    private final LendingApplicationDao lendingApplicationDao;
    private final MerchantService merchantService;
    private final LendingPaymentScheduleDao lendingPaymentScheduleDao;
    private final LendingKfsDao lendingKfsDao;
    private final LiquiloansService liquiloansService;
    private final APIGatewayService apiGatewayService;

    @Autowired
    @Lazy
    private LiquiloansAsyncService liquiloansAsyncService;

    @Autowired
    private LendingApplicationLenderDetailsDao laldDao;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Autowired
    @Qualifier("LoanApiKafkaTemplate")
    KafkaTemplate<String, Object> confluentKafkaTemplate;

    public void saveSuccessLoanDetails(String bpLoanId) {
        try {
            //Fetching PostPayoutResponse & PostPayoutRequest from lms_loan_status Table
            LmsLoanStatus lmsLoanStatus = lmsLoanStatusDao.findLatestByBpLoanId(bpLoanId);

            Map<String, Object> disbursalRequestMap = lmsLoanStatus.getDisbursalRequest();
            PostPayoutRequestDto postPayoutRequestDto = objectMapper.convertValue(disbursalRequestMap, PostPayoutRequestDto.class);

            Map<String, Object> disbursalResponseMap = lmsLoanStatus.getDisbursalResponse();
            PostPayoutResponseDto postPayoutResponseDto = objectMapper.convertValue(disbursalResponseMap, PostPayoutResponseDto.class);

            //Fetching lending_application details on bpLoanId
            LendingApplication lendingApplication = lendingApplicationDao.findByExternalLoanId(bpLoanId);
            //Updating lending_application
            updateLendingApplicationForDisbursal(lendingApplication, postPayoutRequestDto);

            Optional<BasicDetailsDto> basicDetailsOptional = merchantService.fetchMerchantBasicDetails(lendingApplication.getMerchantId());
            BasicDetailsDto basicDetails = basicDetailsOptional.get();

            //Updating lending_payment_schedule
            LendingPaymentSchedule newSchedule = createLendingPaymentSchedule(lendingApplication, basicDetails, postPayoutResponseDto);

            PostPayoutAuditDto postPayoutAuditDto = createAuditDto(postPayoutRequestDto);
            KafkaAudit<PostPayoutAuditDto> kafkaAudit = new KafkaAudit<>("easy_loan", "lending", "post_payout", null);
            postPayoutAuditDto.setPostPayoutResponse(postPayoutResponseDto);
            kafkaAudit.setData(postPayoutAuditDto);
            pushKafkaAudit(kafkaAudit);

            executeSmsAndPaymentLink(lendingApplication, newSchedule, basicDetails);

            LendingApplicationLenderDetails lendingApplicationLenderDetails =
                    laldDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(
                            lendingApplication.getId(),
                            Status.ACTIVE.name(),
                            lendingApplication.getLender());
            lendingApplicationLenderDetails.setLan(postPayoutResponseDto.getNbfcId());
            lendingApplicationLenderDetails.setLoanCreationTimestamp(postPayoutResponseDto.getLoanStartDate());
            lendingApplicationLenderDetails.setDrawDownStatus(LenderAssociationStatus.DRAWDOWN_COMPLETED.name());
            lendingApplicationLenderDetails.setLeadSubStatus(LeadSubStatus.SUCCESS);

            lmsLoanStatus.setStatus("SUCCESS");
            lmsLoanStatus.setUpdatedAt(new Date());
            updateDBForDisbursedLoan(newSchedule, lendingApplication, lmsLoanStatus, lendingApplicationLenderDetails);
            log.info("Disbursed loan successful for applicationId: {}", lendingApplication.getId());
        }catch (Exception e) {
            log.error("Exception in processing loan creation callback: {}", e.getMessage(), e);
        }
    }

    private void updateLendingApplicationForDisbursal(LendingApplication application, PostPayoutRequestDto request) {
        application.setLoanDisbursalStatus(Constants.DISBURSED_LOAN);
        application.setDisburseTimestamp(getDisburseTimestamp(request.getDisbursalDate(), new Date()));
        application.setAccountType(Constants.NBFC_FUNDS);
    }

    public Date getDisburseTimestamp(Date utrTimestamp, Date webhookTimestamp){
        if(ObjectUtils.isEmpty(utrTimestamp))return webhookTimestamp;
        return utrTimestamp;
    }

    private PostPayoutAuditDto createAuditDto(PostPayoutRequestDto request) {
        PostPayoutAuditDto auditDto = new PostPayoutAuditDto();
        auditDto.setPostPayoutRequest(request);
        auditDto.setExternalLoanId(request.getApplicationId());
        auditDto.setStatus(request.getLoanDisbursalStatus().toUpperCase());
        auditDto.setLender(request.getLender().toUpperCase());
        return auditDto;
    }

    private LendingPaymentSchedule createLendingPaymentSchedule(LendingApplication lendingApplication, BasicDetailsDto basicDetailsDto, PostPayoutResponseDto postPayoutResponseDto) {
        LendingPaymentSchedule lendingPaymentSchedule = new LendingPaymentSchedule();

        log.info("Populating data into lending_payment_schedule table for applicationId: {}", lendingApplication.getId());

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
        lendingPaymentSchedule.setSettlementMechanism(LoanSettlementMechanism.EDI_BY_EDI.name());
        lendingPaymentSchedule.setStartDate(postPayoutResponseDto.getLoanStartDate());
        lendingPaymentSchedule.setNextEdiDate(postPayoutResponseDto.getNextEdiDate());
        lendingPaymentSchedule.setTentativeClosingDate(getDateAfterNMonths(lendingApplication.getDisburseTimestamp(), lendingApplication.getTenureInMonths()));

        return lendingPaymentSchedule;
    }

    private Date getDateAfterNMonths(Date startDate, int month) {

        try {
            log.info("Getting date after {} month", month);

            Calendar myCal = Calendar.getInstance();
            myCal.setTime(startDate);
            myCal.add(Calendar.MONTH, +month);
            Date tentativeEndDate = myCal.getTime();
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd 00:00:00");
            return format.parse(format.format(tentativeEndDate));
        } catch (Exception e) {
            log.error("Error occured while catculating date post N month", e);
            return null;
        }
    }

    public void pushKafkaAudit(KafkaAudit<?> kafkaAudit) {
        try {
            log.info("pushing kafka event for {}", kafkaAudit);
            confluentKafkaTemplate.send("easyloan_audit_data", kafkaAudit);
        } catch (Exception e) {
            log.error("error while sending audit data {} {}", kafkaAudit, Arrays.asList(e.getStackTrace()));
        }
    }

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
                    log.error("Error generating welcome document for application id {}: {}", application.getId(), e.getMessage());
                }
            });
        }

        executorService.execute(() -> apiGatewayService.globalLimitTxn(application.getMerchantId(), "DEBIT", finalLendingPaymentSchedule.getLoanAmount()));
        liquiloansService.savePaymentLink(application.getMerchantId().toString(), application.getExternalLoanId());
    }

    /**
     * Updates the database with the disbursed loan details.
     *
     * @param newSchedule         the new lending payment schedule
     * @param application         the lending application
     * @param loanMigrationStatus the loan migration status
     */
    @Transactional
    private void updateDBForDisbursedLoan(LendingPaymentSchedule newSchedule, LendingApplication application, LmsLoanStatus loanMigrationStatus, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        log.info("Updating database for disbursed loan: applicationId={}, scheduleId={}, migrationStatusId={}",
                application.getId(), newSchedule.getId(), loanMigrationStatus.getId());
        lendingApplicationDao.save(application);
        lendingPaymentScheduleDao.save(newSchedule);
        lmsLoanStatusDao.save(loanMigrationStatus);
        laldDao.save(lendingApplicationLenderDetails);
        log.info("Database update completed for disbursed loan: applicationId={}", application.getId());
    }
}
