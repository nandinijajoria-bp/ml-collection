package com.bharatpe.lending.lendingplatform.lms.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.query.dao.ForeClosureConfigDao;
import com.bharatpe.lending.common.query.entity.ForeClosureConfig;
import com.bharatpe.lending.dto.ForeClosureDetailDTO;
import com.bharatpe.lending.dto.PaymentDetailsResponseDTO;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.lendingplatform.lms.client.LendingPlatformHttpClient;
import com.bharatpe.lending.lendingplatform.lms.dto.request.LenderForeclosureDetailsRequest;
import com.bharatpe.lending.lendingplatform.lms.dto.response.ApiResponse;
import com.bharatpe.lending.lendingplatform.lms.dto.response.ForeclosureDetailsResponse;
import com.bharatpe.lending.lendingplatform.lms.dto.response.LenderForeclosureDetailsResponse;
import com.bharatpe.lending.lendingplatform.lms.dto.response.LoanDetailsResponse;
import com.bharatpe.lending.loanV3.factory.LenderAssociationStageFactory;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.ApiEndPointConstants.FETCH_LENDER_FORECLOSURE;
import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.ApiEndPointConstants.GET_FORECLOSURE_AMOUNT;
import static com.bharatpe.lending.lendingplatform.lms.util.ConversionUtil.safeBigDecimalToDouble;
import static com.bharatpe.lending.lendingplatform.lms.util.ConversionUtil.safeBigDecimalToInt;

@Service
@Slf4j
public class ForeclosureService {

    @Autowired
    private LendingPlatformHttpClient lendingPlatformHttpClient;

    @Autowired
    private LmsLoanDetailsService loanDetailsService;

    @Autowired
    private LmsLoanDetailsService lmsloanDetailsService;

    @Autowired
    private LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    private ForeClosureConfigDao foreClosureDao;

    @Autowired
    private LenderAssociationStageFactory lenderAssociationStageFactory;

//    @Value("${newflow.loan.end-date:2023-10-31}")
//    private String configuredLoanEndDate;


    public static final int NO_OF_DAYS_IN_A_MONTH = 30;
    public static final int COOL_OFF_PERIOD_DAYS = 3;

    Logger logger = LoggerFactory.getLogger(ForeclosureService.class);

    public PaymentDetailsResponseDTO.Data getPaymentDatails(LendingPaymentSchedule activeLoan) {
        PaymentDetailsResponseDTO.Data data = new PaymentDetailsResponseDTO.Data();
        LoanDetailsResponse loanDetailsResponse = lmsloanDetailsService.getLoanSummaryFromOneLms(activeLoan.getLoanApplication().getExternalLoanId());

        Double loanAmount = activeLoan.getLoanAmount();
        double overdueAmount = safeBigDecimalToDouble(loanDetailsResponse.getLoanSummary().getOverdueInstalmentAmount());
        double penaltyFee = safeBigDecimalToDouble(loanDetailsResponse.getLoanSummary().getOverdueOtherCharges());
        Integer overdueDays = loanDetailsResponse.getLoanSummary().getOverdueInstalmentCount();
        Integer loanAmountAsInt = (int) Math.ceil(loanAmount);
        Integer overDueAmountAsInt = (int) Math.ceil(overdueAmount);


        Double netForeclosureAtLender = 0d;
        double finalForeclosureAtLender = 0d;
        data= new PaymentDetailsResponseDTO.Data(loanAmountAsInt, overDueAmountAsInt, overdueDays, true, activeLoan.getEdiRemainingCount(), overDueAmountAsInt.doubleValue());
        data.setExcessBalance((double) loanDetailsResponse.getLoanSummary().getExcessPayable());
        int lmsForeclosureAmount = getForeclosureAmount(activeLoan);
        netForeclosureAtLender = getLenderForeclosureAmount(activeLoan);
        int finalForeclosureAmount = Math.max(lmsForeclosureAmount, Double.valueOf(Math.ceil(netForeclosureAtLender)).intValue());
        data.setPrincipalDueAmount(finalForeclosureAmount);
        data.setLenderPrincipalDueAmount(netForeclosureAtLender);
        data.setForeClosureAmountAtLender(netForeclosureAtLender);
        data.setForeClosureAmount(Double.valueOf(finalForeclosureAmount));
        //getting foreclosure charges and checking cool off period
        data.setForeClosureDetail(calculateForeClosureCharges(activeLoan,data,loanDetailsResponse));
        log.info("fore closure charges {} for loanId {}",data.getForeClosureDetail(),activeLoan.getId());
        if(data.getForeClosureDetail() != null) {
            data.setForeClosureAmount(data.getForeClosureDetail().getPrincipalOutstanding() +
                    data.getForeClosureDetail().getForeclosureCharges()+data.getForeClosureDetail().getGst());
        }

        logger.info("netForeclosureAtLender {} and principalDue {} at nbfc for loan {}", netForeclosureAtLender, finalForeclosureAmount, activeLoan.getId());

        Double paidPrinciple = (double) safeBigDecimalToInt(loanDetailsResponse.getLoanSummary().getPaidPrincipalAmount());
        Double dueInterest = safeBigDecimalToDouble(loanDetailsResponse.getLoanSummary().getOverdueInterest());

        // Check if today is the loan's end date; if true, hide the foreclosure option
        if (isLoanLastDayToday(loanDetailsResponse)) {
            data.setHideForeclosure(true);
        }
        Double pendingAmount = loanAmount - paidPrinciple + dueInterest;
        data.setPaidAmount(activeLoan.getPaidAmount());
        data.setPendingAmount(pendingAmount);
        data.setPaidPrinciple(paidPrinciple);
        data.setRepaymentAmount(activeLoan.getTotalPayableAmount());
        data.setPenaltyFee((int) Math.ceil(penaltyFee));
        data.setTotalDue(Math.ceil(overdueAmount + penaltyFee));
        data.setTotalExcessBalance(loanDetailsResponse.getLoanSummary().getExcessPayable());
        data.setNetPayable(Math.max(Math.ceil(overdueAmount + penaltyFee - data.getTotalExcessBalance()), 0)); // this is for today's due
        logger.info("payment details data {} at for loan {}", data, activeLoan.getId());
        return data;
    }

    public int getForeclosureAmount(LendingPaymentSchedule activeLoan) {
        try {
            LendingApplication lendingApplication = loanDetailsService.getLendingApplicationByApplicationId(activeLoan.getApplicationId());
            Map<String, String> requestParams = new HashMap<>();
            requestParams.put("bpLoanId", lendingApplication.getExternalLoanId());
            ApiResponse<ForeclosureDetailsResponse> foreclosureResponse = lendingPlatformHttpClient.sendGetRequestWithParams(GET_FORECLOSURE_AMOUNT,
                    requestParams,
                    ForeclosureDetailsResponse.class);
            if (!ObjectUtils.isEmpty(foreclosureResponse) && foreclosureResponse.isSuccess() && !ObjectUtils.isEmpty(foreclosureResponse.getData())
                    && !ObjectUtils.isEmpty(foreclosureResponse.getData().getForeclosureAmount())) {
                log.info("Foreclosure Amount fetched successfully. Merchant ID: {}", activeLoan.getMerchantId());
                return (int) Math.ceil(foreclosureResponse.getData().getForeclosureAmount().doubleValue());
            }
            log.error("Loan request failed: Empty or invalid response received for Merchant ID: {}", activeLoan.getMerchantId());
            throw new RuntimeException("Loan initiation failed: Invalid response from lending platform.");   //TODO - check & add exceptions
        } catch (Exception e) {
            log.error("Exception occurred while initiating loan request: {}", e.getMessage(), e);
            throw new RuntimeException("Error during loan request initiation: " + e.getMessage(), e); //TODO - check & add exceptions
        }
    }

    public Double getLenderForeclosureAmount(LendingPaymentSchedule activeLoan) {
        Double netForeclosureAtLender = 0d;
        log.info("Fetching foreclosure amount at lender side for applicationId:{}",activeLoan.getApplicationId());

        LenderForeclosureDetailsRequest lenderForeclosureRequest = createLenderForeclosureRequest(activeLoan);
        ApiResponse<LenderForeclosureDetailsResponse> lenderForeclosureResponse = lendingPlatformHttpClient.sendPostRequest(FETCH_LENDER_FORECLOSURE, lenderForeclosureRequest, LenderForeclosureDetailsResponse
                .class);
        if(!ObjectUtils.isEmpty(lenderForeclosureResponse) && lenderForeclosureResponse.isSuccess()
                && !ObjectUtils.isEmpty(lenderForeclosureResponse.getData())){
            netForeclosureAtLender = lenderForeclosureResponse.getData().getForeclosureAmount();
        }

        log.info("Foreclosure amount at lender side for applicationId:{} is {}",activeLoan.getApplicationId(), netForeclosureAtLender);
        return netForeclosureAtLender;
    }

    public ForeClosureDetailDTO calculateForeClosureCharges(LendingPaymentSchedule activeLoan, PaymentDetailsResponseDTO.Data data, LoanDetailsResponse loanDetailsResponse) {
        ForeClosureDetailDTO foreClosureDetailDTO = new ForeClosureDetailDTO();
        logger.info("going to hit foreclosure config db with lender {} and tenure {}",activeLoan.getNbfc(),activeLoan.getLoanApplication().getTenureInMonths());
        List<ForeClosureConfig> foreClosureConfigList = foreClosureDao.findByLenderAndTenure(activeLoan.getNbfc(),activeLoan.getLoanApplication().getTenureInMonths());
        double duration = calculateDurationInMonths(activeLoan.getStartDate());

        if(checkLoanCoolOffPeriod(activeLoan.getStartDate())){
            return null;
        }

        if(!CollectionUtils.isEmpty(foreClosureConfigList)) {
            ForeClosureConfig foreClosureConfig = getApplicableForeclosureConfig(foreClosureConfigList, duration);
            if(foreClosureConfig != null) {
                foreClosureDetailDTO.setId(foreClosureConfig.getId());
                foreClosureDetailDTO.setPrincipalOutstanding(data.getPrincipalDueAmount());
                Double minAmount = foreClosureConfig.getMinAmount();
                if(minAmount == null) minAmount = 0.0;
                logger.info("loan is {} and min amount is {} and foreclosure config rate  is {}  ",activeLoan,minAmount, foreClosureConfig.getRate());
                foreClosureDetailDTO.setForeclosureCharges(Math.max(Math.ceil(((loanDetailsResponse.getLoanSummary().getLoanAmount() - safeBigDecimalToInt(loanDetailsResponse.getLoanSummary().getPendingPrincipal()) - safeBigDecimalToInt(loanDetailsResponse.getLoanSummary().getOverduePrincipal()) * foreClosureConfig.getRate()) / 100.0)), minAmount));
                foreClosureDetailDTO.setGst(Math.ceil((foreClosureDetailDTO.getForeclosureCharges() * foreClosureConfig.getGst())/100.0));
                logger.info("going to return fore closure charges {} ",foreClosureDetailDTO);
                return foreClosureDetailDTO;
            }
        }
        logger.info("fore closure charges not applicable for loanId {} and nbfc {}",activeLoan.getId(),activeLoan.getNbfc());
        return null;
    }

    private boolean isLoanLastDayToday(LoanDetailsResponse loanDetailsResponse) {
        logger.info("Checking for last day of the loan: {} with loan end date: {}", loanDetailsResponse.getBpLoanId(), loanDetailsResponse.getLoanSummary().getLoanEndDate());

        // Convert loanEndDate (Date) to LocalDate
        LocalDate loanEndDate = loanDetailsResponse.getLoanSummary()
                .getLoanEndDate()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        //return loanEndDate.equals(LocalDate.parse(configuredLoanEndDate));
        return loanEndDate.equals(LocalDate.now());
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

    public boolean checkLoanCoolOffPeriod(Date createdAt) {
        double	durationInDays = calculateDurationInDays(createdAt);
        if(durationInDays < COOL_OFF_PERIOD_DAYS) return true;
        return false;
    }

    private  double calculateDurationInMonths(Date date) {
        return calculateDurationInDays(date) / NO_OF_DAYS_IN_A_MONTH;
    }

    private double calculateDurationInDays(Date date) {
        logger.info("inside calculate duration for loan {}",date);
        Date currentDate = new Date();
        // Convert milliseconds to days
        long differenceInMillis = currentDate.getTime() - date.getTime();
        return TimeUnit.MILLISECONDS.toDays(differenceInMillis);
    }

    private LenderForeclosureDetailsRequest createLenderForeclosureRequest(LendingPaymentSchedule activeLoan) {
        LoanDetailsResponse loanDetailsResponse = lmsloanDetailsService.getLoanSummaryFromOneLms(activeLoan.getLoanApplication().getExternalLoanId());

        LendingApplicationLenderDetails lald = lendingApplicationLenderDetailsDao
                .findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(
                        activeLoan.getApplicationId(), Status.ACTIVE.name());

        return LenderForeclosureDetailsRequest.builder()
                .bpLoanId(activeLoan.getLoanApplication().getExternalLoanId())
                .applicationId(activeLoan.getApplicationId().toString())
                .lender(Lender.valueOf(activeLoan.getNbfc()).name())
                .leadId(lald.getLeadId())
                .clientId(lald.getCccId())
                .loanAccountNumber(lald.getLan())
                .transactionDate(new Date())
                .outstandingPrinciple(BigDecimal.valueOf(safeBigDecimalToInt(loanDetailsResponse.getLoanSummary().getPendingPrincipal())))
                .outstandingInterest(BigDecimal.valueOf(loanDetailsResponse.getLoanSummary().getPendingInterest().doubleValue()))
                .build();
    }
}
