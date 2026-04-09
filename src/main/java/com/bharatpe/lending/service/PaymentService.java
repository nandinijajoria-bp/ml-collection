package com.bharatpe.lending.service;

import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.LendingEDIScheduleDao;
import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingEDISchedule;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.enums.Status;
import com.bharatpe.common.service.LoyaltyService;
import com.bharatpe.common.utils.NotificationUtil;
import com.bharatpe.lending.collection.core.dto.internal.LoanClosureDTO;
import com.bharatpe.lending.collection.core.dto.internal.LoanPaymentDetailDTO;
import com.bharatpe.lending.collection.core.service.LoanClosurePostingService;
import com.bharatpe.lending.collection.core.service.LoanClosureService;
import com.bharatpe.lending.collection.core.service.LoanPaymentLedgerAdjustmentService;
import com.bharatpe.lending.collection.core.service.LoanPaymentService;
import com.bharatpe.lending.collection.core.utils.LoanPaymentUtil;
import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.common.Constants.DeductionStatusEnum;
import com.bharatpe.lending.common.Handler.LendingPayoutsHandler;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.*;
import com.bharatpe.lending.common.exception.DuplicateTransactionException;
import com.bharatpe.lending.common.query.dao.ForeClosureConfigDao;
import com.bharatpe.lending.common.query.dao.LendingPullPaymentDaoSlave;
import com.bharatpe.lending.common.query.dao.LoanDpdDaoSlave;
import com.bharatpe.lending.common.query.dao.LoanPaymentOrderSlaveDao;
import com.bharatpe.lending.common.query.entity.ForeClosureConfig;
import com.bharatpe.lending.common.query.entity.LoanPaymentOrderSlave;
import com.bharatpe.lending.common.service.*;
import com.bharatpe.lending.common.service.merchant.constants.Constants;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.CreditConstants;
import com.bharatpe.lending.constant.PaymentConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.LoanPaymentOrderDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.dto.ResponseDTO;
import com.bharatpe.lending.entity.LoanPaymentOrder;
import com.bharatpe.lending.enums.*;
import com.bharatpe.lending.lendingplatform.lending.util.RolloutUtil;
import com.bharatpe.lending.lendingplatform.lms.service.ForeclosureService;
import com.bharatpe.lending.lendingplatform.lms.service.PaymentAsynchronousService;
import com.bharatpe.lending.lendingplatform.lms.service.PaymentStatusService;
import com.bharatpe.lending.handlers.EmiHandler;
import com.bharatpe.lending.loanV2.service.ExcessNachService;
import com.bharatpe.lending.loanV3.config.CreditSaisonConfig;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;
import com.bharatpe.lending.loanV3.dto.trillions.TrillionForeclosureRequestDto;
import com.bharatpe.lending.loanV3.revamp.constants.LoanDetailsConstant;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.loanV3.services.LenderForeclosureCachingService;
import com.bharatpe.lending.loanV3.services.associationsV2.AssociationServiceUtil;
import com.bharatpe.lending.loanV3.services.gateway.NbfcLenderGateway;
import com.bharatpe.lending.util.LoanUtil;
import com.bharatpe.lending.util.PaymentLinkUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.bharatpe.lending.common.enums.CollectionTaskType.*;
import static com.bharatpe.lending.common.enums.LoanSettlementMechanism.EDI_BY_EDI;
import static com.bharatpe.lending.common.enums.PerpetualDpdAdjusted.Y;
import static com.bharatpe.lending.constant.CommonConstants.OK;
import static com.bharatpe.lending.constant.CreditConstants.PaymentStatus.SUCCESS;
import static com.bharatpe.lending.constant.LendingConstants.AUTO_PAY_SETTLEMENT;
import static com.bharatpe.lending.constant.LendingConstants.UPI_AUTOPAY_ADJUSTMENT_MODE;
import static com.bharatpe.lending.constant.PaymentConstants.*;
import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.ONE_LMS;

@Service
@Slf4j
public class PaymentService {

    private static final String UPI_AUTO_PAY = "UPI_AUTO_PAY";
    public static final Set<String> LENDER_FORECLOSURE_AGREEMENT_DATE_CHECK = new HashSet<>(Arrays.asList(Lender.ABFL.name(), Lender.PIRAMAL.name()));
    public static final String WARNING_RED_ICON = "https://d30gqtvesfc1d5.cloudfront.net/hubble/Home_PNG/alert-triangle-1759990132728.png";
    public static final String LOAN_REPAY_IMAGE_ICON = "https://d30gqtvesfc1d5.cloudfront.net/hubble/easy_loans/pending_payment_banner-1769683731144.png";
    public static final String NOTIFICATION_BOTTOM_SHEET = "BOTTOM_SHEET";
    public static final String DPD_EVENT_CATEGORY = "LENDING_DPD";
    Logger logger = LoggerFactory.getLogger(PaymentService.class);

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LendingPaymentScheduleLendingCommonDao lendingPaymentScheduleLendingCommonDao;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LendingPayoutsHandler lendingPayoutsHandler;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    NbfcLenderGateway nbfcLenderGateway;

    @Autowired
    LoyaltyService loyaltyService;

    @Autowired
    EmiHandler emiHandler;

//	@Autowired
//	MerchantDao merchantDao;

    @Autowired
    AutoPayUPIService autoPayUPIService;
    @Autowired
    LoanPaymentOrderDao loanPaymentOrderDao;

    @Autowired
    LoanPaymentOrderSlaveDao loanPaymentOrderSlaveDao;

    @Autowired
    LendingEDIScheduleDao lendingEDIScheduleDao;

    @Autowired
    RedisNotificationService redisNotificationService;

    @Autowired
    NBFCService nbfcService;

    @Autowired
    LendingAdjustedEDIScheduleDao lendingAdjustedEDIScheduleDao;

    @Autowired
    LoanDpdDao loanDpdDao;

    @Autowired
    LoanDpdDaoSlave loanDpdDaoSlave;

    @Autowired
    ExcessNachService excessNachService;

    @Autowired
    LendingNotificationService lendingNotificationService;

    @Autowired
    LendingPrepaymentDao lendingPrepaymentDao;

    @Autowired
    LendingPrepaymentAuditDao lendingPrepaymentAuditDao;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    NotificationUtil notificationUtil;

    @Autowired
    LendingInterestWaiverDao lendingInterestWaiverDao;
    @Value(("${pg.android.version:324}"))
    Long androidVersion;

    @Value(("${pg.ios.version:254}"))
    Long iosVersion;

    @Value("${pg.emi-order-id.prefix:QA_EMI_LOAN_}")
    String pgEmiOrderIdPrefix;

    @Value("${pg.emi.callback.enable:true}")
    boolean pgEmiCallbackEnable;
    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Value("${trillionloans.receipt.posting.payment.id.rollout.percent:1}")
    int receiptPostingPaymentIdRolloutPercent;

    ExecutorService notificationExecutor = Executors.newFixedThreadPool(10);

    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    @Autowired
    MerchantService merchantService;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    @Qualifier("CollectionLowLatencyKafkaTemplate")
    KafkaTemplate confluentKafkaTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LendingCollectionAuditService lendingCollectionAuditService;

    @Autowired
    LendingCollectionAuditDao lendingCollectionAuditDao;

    @Autowired
    LiquiloansService liquiloansService;

    @Autowired
    DateTimeUtil dateTimeUtil;

    @Autowired
    PaymentSettlementService paymentSettlementService;

    @Autowired
    LendingRefundAuditDao lendingRefundAuditDao;

    @Autowired
    ForeclosureService foreclosureService;

    @Autowired
    private PaymentLinkUtil paymentLinkUtil;

    @Autowired
    private FunnelService funnelService;

    @Autowired
    private LoanDashboardService loanDashboardService;

    @Autowired
    LendingCollectionExcessDao lendingCollectionExcessDao;

    @Value("${loan.payment.order.pending.transaction.time.window:30}")
    int loanPaymentOrderPendingTransactionTimeWindow;

    @Value("${loan.payment.order.pending.transaction.link.time.window:30}")
    int loanPaymentOrderPendingTransactionTimeWindowViaLink;

    @Value("${nbfc.trillion.foreclosure.topic:trillion-foreclose-loan}")
    String nbfcTrillionForeclosureTopic;

    @Autowired
    private LendingPullPaymentDao lendingPullPaymentDao;

    @Value("${nbfc.baseurl.v3.api:https://api-nbfc-uat.bharatpe.in/}")
    String nbfcBaseUrl;

    @Value("${nbfc.baseurl.v3.foreclosure:api/v3/lender/foreclosure}")
    String nbfcURI;

    @Value("${nbfc.collection.service.base.url:https://api-nbfc.bharatpemoney.com/}")
    String nbfcCollectionServiceBaseUrl;

    @Value("${nbfc.foreclosure.charge:api/v3/lender/post-charges}")
    String nbfcForeClosureChargePosting;

    @Value("${easy.loans.pg.redirection.url:easy-loans}")
    String pgRedirectionUrl;

    @Value("${payu.nach.bounce.charge:500}")
    Integer payUNachBounceCharge;

    @Autowired
    LendingPullPaymentDaoSlave lendingPullPaymentDaoSlave;

    @Autowired
    AssociationServiceUtil associationServiceUtil;

    @Autowired
    PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    @Autowired
    SherlocLoanStatusChangeService sherlocLoanStatusChangeService;

    @Autowired
    ForeClosureConfigDao foreClosureConfigDao;

    @Autowired
    LoanForeClosureChargesDao loanForeClosureChargesDao;

    @Autowired
    LoanPaymentService loanPaymentService;

    @Value("${nbfc.usfb.foreclosure.topic:usfb-foreclose-loan}")
    String nbfcUsfbForeclosureTopic;

    @Value("${nbfc.capri.foreclosure.topic:capri-foreclose-loan}")
    String nbfcCapriForeclosureTopic;

    @Value("${nbfc.liquiloans.foreclosure.charges.topic:penalty_fee_on_nbfc}")
    String nbfcLiquiLoansForeclosureTopic;

    @Value("${nbfc.trilionloans.foreclosure.charges.topic:post_charges_trillion}")
    String nbfcTrilionLoansForeclosureChargesTopic;

    @Value("${nbfc.payu.foreclosure.topic:payu-foreclose-loan}")
    String nbfcPayuForeclosureTopic;

    @Value("${nbfc.muthoot.foreclosure.topic:muthoot-loan-receipt}")
    String nbfcMuthootForeclosureTopic;

    @Lazy
    @Autowired
    CreditSaisonConfig csConfig;

    @Autowired
    LoanPaymentUtil loanPaymentUtil;
    @Autowired
    ForeClosureAmountInfoDao foreClosureAmountInfoDao;

    @Autowired
    LoanClosureService loanClosureService;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LoanClosurePostingService loanClosurePostingService;
    @Autowired
    LendingCache lendingCache;

    @Value("${autopay.upi.lock.timeout:10}")
    int autoPayUpiLockTimeout;

    @Value("${previous.mandate.failed:7}")
    int previouMandateFailed;

    @Value("${auto.pay.upi.dpd.penalty.enabled:false}")
    boolean autoPayUpiDpdPenaltyEnabled;

    @Autowired
    AutoPayUPIDao autoPayUPIDao;

    @Autowired
    UgroConfig ugroConfig;

    @Autowired
    LoanPaymentLedgerAdjustmentService loanPaymentLedgerAdjustmentService;

    @Autowired
    NachBounceChargesService nachBounceChargesService;

    @Autowired
    PaymentAsynchronousService paymentAsynchronousService;

    @Autowired
    RolloutUtil rolloutUtil;

    @Autowired
    PaymentStatusService paymentStatusService;

    @Autowired
    LmsPaymentDetailsDao lmsPaymentDetailsDao;

    @Autowired
    LenderForeclosureCachingService lenderForeclosureCachingService;

    @Value("${lms.previous.mandate.failed:3}")
    private int lmsPreviousMandateFailed;

    @Value("${extra.payment.max.edi.count:5}")
    Integer extraPaymentEdiCount;

    @Autowired
    LendingNotificationService notificationService;

    @Value("${dpd.notification.app.deeplink:}")
    String dpdNotificationAppDeeplink;

    @Autowired
    PostDisbursalNotificationDao postDisbursalNotificationDao;

    public PaymentDetailsResponseDTO getPaymentDetails(BasicDetailsDto merchant, Boolean showForeClosureDetails) {
        logger.info("Received payment details request for merchant id {}", merchant.getId());
        try {
            LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchant.getId(),  Arrays.asList("ACTIVE", "DECEASED"));
            if(activeLoan == null) {
                logger.info("No active loan found for merchant id {}", merchant.getId());
                return new PaymentDetailsResponseDTO("No active loan found.");
            }
            return getPaymentDetailsForActiveLoan(activeLoan, showForeClosureDetails);
        } catch(Exception ex) {
            logger.error("Execption while fetching payment details for merchant id {}, Exception is {} {}", merchant.getId(), ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        return new PaymentDetailsResponseDTO("Something went wrong.");
    }

    public PaymentDetailsResponseDTO getPaymentDetails(Long merchantId, Boolean showForeClosureDetails) {
        logger.info("Received payment details request for merchant id {}", merchantId);
        try {
            LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId,  Arrays.asList("ACTIVE", "DECEASED"));
            if(activeLoan == null) {
                logger.info("No active loan found for merchant id {}", merchantId);
                return new PaymentDetailsResponseDTO("No active loan found.");
            }
            return getPaymentDetailsForActiveLoan(activeLoan, showForeClosureDetails);
        } catch(Exception ex) {
            logger.error("Execption while fetching payment details for merchant id {}, Exception is {} {}", merchantId, ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        return new PaymentDetailsResponseDTO("Something went wrong.");
    }


    public PaymentDetailsResponseDTO getPaymentDetailsForActiveLoan(LendingPaymentSchedule activeLoan, Boolean showForeClosureDetails)  {
        if("1LMS".equalsIgnoreCase(activeLoan.getLmsSource())) {
            log.info("Fetching payment details for 1LMS applicationID: {}", activeLoan.getApplicationId());
            PaymentDetailsResponseDTO.Data data = foreclosureService.getPaymentDatails(activeLoan);
            return new PaymentDetailsResponseDTO(data);
        }

        LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(activeLoan.getMerchantId(), activeLoan.getId());
        double advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ? lendingPrepayment.getAdvanceEdiAmount() : 0d;
        Integer loanAmount = activeLoan.getLoanAmount().intValue();
        Integer overdueAmount = activeLoan.getDueAmount().intValue();
        Integer penaltyFee = Objects.nonNull(activeLoan.getDuePenalty()) ? activeLoan.getDuePenalty().intValue() : 0;
        Integer overdueDays = (activeLoan.getDueAmount().intValue()/activeLoan.getEdiAmount().intValue());

        Double excessCollectionBalance = excessNachService.getExcessCollectionBalanceAmount(activeLoan.getMerchantId(), activeLoan.getId());
        if (excessCollectionBalance == null) {
            excessCollectionBalance = 0.0;
        }   else {
            excessCollectionBalance = Math.floor(excessCollectionBalance);
        }

        Integer ediHolidayInterestAmount = getEDIHolidayInterestAmount(activeLoan);
        boolean isPayable = true;
        if(overdueDays < 2) {
            isPayable = false;
        }
        Double netForeclosureAtLender = 0d;
        Double principalOutstandingLender = 0d;
        double finalForeclosureAtLender = 0d;
        PaymentDetailsResponseDTO.Data data= new PaymentDetailsResponseDTO.Data(loanAmount, overdueAmount, overdueDays, isPayable, activeLoan.getEdiRemainingCount(), activeLoan.getEdiAmount());
        data.setExcessBalance(excessCollectionBalance);
        if(Boolean.TRUE.equals(showForeClosureDetails)) {
            //show foreclosure details --
            int principalDueAmount = Math.max(loanUtil.getForeclosureAmount(activeLoan, excessCollectionBalance), 0);
            if (activeLoan.getTentativeClosingDate().before(new Date())) {
                double totalPayable = activeLoan.getEdiAmount() * activeLoan.getEdiCount();
                int extraAmount = (int)Math.ceil(totalPayable - (activeLoan.getPaidAmount() + principalDueAmount + advanceEdiAmount));
                if (extraAmount > 0d) {
                    logger.info("Need to get extra amount:{} for loanId:{}", extraAmount, activeLoan.getId());
                    principalDueAmount += extraAmount;//adding extra amount in foreclosure amount
                }
            }
            // this is the recorded in db and excess is added here just to record (net_payable + excess already available) = net foreclosure at our side
            double netForeclosureAtBp=principalDueAmount + advanceEdiAmount + excessCollectionBalance;
            data.setForeClosureAmountAtBp(netForeclosureAtBp);
            LenderForeclosureDetailsDTO lenderForeclosureDetailsDTO = null;
            int retry = 0;
            while (retry < 3) {
                try {
                    lenderForeclosureDetailsDTO = lenderForeclosureCachingService.getLenderForeclosureAmount(activeLoan.getNbfc(), activeLoan.getApplicationId(), activeLoan.getMerchantId());
                    if (lenderForeclosureDetailsDTO != null && lenderForeclosureDetailsDTO.getForeclosureAmount() != null) {  // skip retry
                        netForeclosureAtLender = lenderForeclosureDetailsDTO.getForeclosureAmount();
                        principalOutstandingLender = lenderForeclosureDetailsDTO.getPrincipalOutstanding();
                        logger.info("principalDue {} and foreclosure amount {}  at lender for loan {}", principalOutstandingLender, netForeclosureAtLender, activeLoan.getId());
                        break;
                    }
                } catch (Exception e) {
                    logger.error("Exception while fetching foreclosure details for merchantId: {} {}", activeLoan.getMerchantId(), Arrays.asList(e.getStackTrace()));
                }
                retry++;
            }
            if(netForeclosureAtLender != null && netForeclosureAtLender > 0 && "PIRAMAL".equalsIgnoreCase(activeLoan.getNbfc()) ){
                logger.info("Checking for unposted piramal penalty for loanId:{}", activeLoan.getId());
                double unpostedPiramalPenalty = checkUnpostedPiramalPenalty(activeLoan.getId());
                unpostedPiramalPenalty = unpostedPiramalPenalty * -1;
                if(unpostedPiramalPenalty > 0) {
                    logger.info("Adding to net foreclosureAtlender Unposted piramal penalty for loanId:{} is {}", activeLoan.getId(), unpostedPiramalPenalty);
                    netForeclosureAtLender += unpostedPiramalPenalty;
                }
            }
            if (netForeclosureAtLender == null) netForeclosureAtLender = 0d;
            finalForeclosureAtLender = netForeclosureAtLender;
            netForeclosureAtLender = Math.max(netForeclosureAtLender - excessCollectionBalance, 0);

            principalDueAmount = principalDueAmount + ediHolidayInterestAmount;
            logger.info("principalDue {} and {} due amt at bharatpe for loan {}", principalDueAmount, overdueAmount, activeLoan.getId());
            principalDueAmount = Math.max(principalDueAmount, Double.valueOf(Math.ceil(netForeclosureAtLender)).intValue());

            data.setPrincipalDueAmount(principalDueAmount);
            data.setLenderPrincipalDueAmount(netForeclosureAtLender);
            data.setForeClosureAmountAtLender(finalForeclosureAtLender);
            data.setForeClosureAmount(Double.valueOf(principalDueAmount));
            logger.info("netForeclosureAtLender {} and principalDue {} at nbfc for loan {}", netForeclosureAtLender, principalDueAmount, activeLoan.getId());

            Date loanEligibilityDate = activeLoan.getLoanApplication().getCreatedAt();
            if(LENDER_FORECLOSURE_AGREEMENT_DATE_CHECK.contains(activeLoan.getNbfc())){
                loanEligibilityDate = activeLoan.getLoanApplication().getAgreementAt();
            }

            if(loanUtil.checkIfForeClosureChargesApplicable(loanEligibilityDate, activeLoan.getNbfc())){
                log.info("loanId {} is eligible for fore closure charges ",activeLoan.getId());
                data.setForeClosureDetail(loanUtil.calculateForeClosureCharges(activeLoan,data,principalOutstandingLender));
                log.info("fore closure charges {} for loanId {}",data.getForeClosureDetail(),activeLoan.getId());
                if(data.getForeClosureDetail() != null) {
                    data.setForeClosureAmount(data.getForeClosureDetail().getPrincipalOutstanding() +
                            data.getForeClosureDetail().getForeclosureCharges()+data.getForeClosureDetail().getGst());
                }
            }


            if(principalDueAmount == Double.valueOf(Math.ceil(netForeclosureAtLender)).intValue()){
                log.info("Checking for pending penalty that is not posted for loanId {} ",activeLoan.getId());
                int penaltyNotPostedToLender = nachBounceChargesService.checkIfPendingNachPenalty(activeLoan,false);
                data.setPrincipalDueAmount(data.getPrincipalDueAmount()+penaltyNotPostedToLender);
                data.setForeClosureAmount(data.getForeClosureAmount()+penaltyNotPostedToLender);
            }
            Double pendingNachCharges =  nachBounceChargesService.getNachCharges(activeLoan);
            if(pendingNachCharges != 0d){
                log.info("loanId {} adding nach bounce charges to penalty and foreclosure amount",activeLoan.getId());
                penaltyFee += pendingNachCharges.intValue();
                data.setPrincipalDueAmount(data.getPrincipalDueAmount()+pendingNachCharges.intValue());
                data.setForeClosureAmount(data.getForeClosureAmount()+pendingNachCharges.intValue());
                data.setForeClosureAmountAtBp(data.getForeClosureAmountAtBp() + pendingNachCharges.intValue());
            }

            data.setForeclosurePenaltyFee(0.0);
            if("PIRAMAL".equalsIgnoreCase(activeLoan.getNbfc()) && !loanUtil.checkLoanCoolOffPeriod(activeLoan.getStartDate())) data.setForeclosurePenaltyFee(loanUtil.calculatePiramalPenalty(activeLoan));
            data.setPrincipalDueAmount((int) (data.getPrincipalDueAmount()+data.getForeclosurePenaltyFee()));
            data.setForeClosureAmount(data.getForeClosureAmount()+data.getForeclosurePenaltyFee());

        }
        Double paidPrinciple = activeLoan.getPaidPrinciple() != null
                ? activeLoan.getPaidPrinciple()
                : 0d;
        Double dueInterest = activeLoan.getDueInterest() != null ? activeLoan.getDueInterest()
                : 0d;
        Double pendingAmount = loanAmount - paidPrinciple + dueInterest;

        if(loanUtil.isTodayIsLoanLastDay(activeLoan)){
            data.setHideForeclosure(true);
        }
        data.setPaidAmount(activeLoan.getPaidAmount());
        data.setPendingAmount(pendingAmount);
        data.setPaidPrinciple(paidPrinciple);
        data.setRepaymentAmount(activeLoan.getTotalPayableAmount());
        data.setPenaltyFee(penaltyFee);
        data.setTotalDue(overdueAmount + penaltyFee);
        data.setTotalExcessBalance(Math.min(excessCollectionBalance + advanceEdiAmount, data.getTotalDue()));
        data.setNetPayable(Math.max(data.getTotalDue() - data.getTotalExcessBalance(), 0));  // this is for today's due

        // LC-2061
        double maxPayable = data.getNetPayable();
        if (loanPaymentUtil.checkExtraPaymentAfterRolloutDate(activeLoan.getCreatedAt())
                && loanPaymentUtil.checkExtraPaymentRolloutPercentage(activeLoan.getId())) {
            logger.info("Checking extra payment allowed for loanId: {}", activeLoan.getId());
            maxPayable = calculateMaxAmount(activeLoan, maxPayable);
            logger.info("Extra payment allowed. calculated maxPayable: {} for loanId: {}", maxPayable, activeLoan.getId());
            if (maxPayable > data.getNetPayable()) {
                maxPayable = Math.max(maxPayable - excessCollectionBalance, 0);
                logger.info("Extra payment allowed after adjusting excess balance. maxPayable: {} and extrabalance:{} for loanId: {}", maxPayable, excessCollectionBalance, activeLoan.getId());
            }
            logger.info("Extra payment allowed. maxPayable after adjusting excess balance: {} for loanId: {}", maxPayable, activeLoan.getId());
            maxPayable = Math.max(maxPayable, data.getNetPayable());
            logger.info("Extra payment allowed. maxPayable: {} for loanId: {}", maxPayable, activeLoan.getId());
        }

        data.setMaxPayable(maxPayable);
        logger.info("payment details data {} at for loan {}", data, activeLoan.getId());
        return new PaymentDetailsResponseDTO(data);
    }

    private double checkUnpostedPiramalPenalty(Long loanId) {
        double unpostedPenalty = 0.0;
        if(loanId != null) {
            PenaltyFeeLedger penaltyFeeLedger = penaltyFeeLedgerDao.findTop1PenaltyFeeOrderByIdDesc(loanId);
            if(penaltyFeeLedger != null && penaltyFeeLedger.getIsPosted() != null  && !penaltyFeeLedger.getIsPosted()) {
                unpostedPenalty = penaltyFeeLedger.getAmount();
                logger.info("unposted piramal penalty fee {} for loanId {}", unpostedPenalty, loanId);
            }
        }
        return unpostedPenalty;
    }

    private double calculateMaxAmount(LendingPaymentSchedule activeLoan, double maxPayable) {
        try {
            if (activeLoan.getEdiRemainingCount() == 0) {
                logger.info("No extra payment allowed as edi remaining count is 0 for loanId: {}", activeLoan.getId());
                return maxPayable;
            }
            double maxExtraAllowedAmount = activeLoan.getEdiAmount() * extraPaymentEdiCount;
            double duePenalty = Objects.nonNull(activeLoan.getDuePenalty()) ? activeLoan.getDuePenalty() : 0;
            double netReceivable = activeLoan.getTotalPayableAmount() + duePenalty - activeLoan.getPaidPrinciple() - activeLoan.getPaidInterest();
            logger.info("Calculating max amount for extra payment for loanId: {}, maxExtraAllowedAmount: {}, netReceivable: {}", activeLoan.getId(), maxExtraAllowedAmount, netReceivable);

            // note this can be greater than foreclosure amount - but we must ensure not foreclose the loan at our end
            return Math.min(maxExtraAllowedAmount, netReceivable);
        } catch (Exception e) {
            logger.error("Error in calculating max amount for extra payment for loanId: {}, error: {} stack: {}", activeLoan.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }

        return maxPayable;
    }

    public InitiatePaymentResponseDTO initiatePaymentV2(BasicDetailsDto merchantBasicDetails, RequestDTO<InitiatePaymentRequestDTO> request) {
        logger.info("Received initiate payment request  for merchant {} : {}", merchantBasicDetails.getId(), request);
        try {
            LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantBasicDetails.getId(),  Arrays.asList("ACTIVE", "DECEASED"));
            if(activeLoan == null) {
                logger.info("No active loan found for merchant id {}", merchantBasicDetails.getId());
                return new InitiatePaymentResponseDTO("No active loan found.");
            }
            Integer amount = request.getPayload().getAmount();
            double penaltyFee = Objects.nonNull(activeLoan.getDuePenalty()) ? activeLoan.getDuePenalty() : 0;
            if(amount < 1 ) {
                logger.info("Amount is less than 1 for merchant id {}", merchantBasicDetails.getId());
                return new InitiatePaymentResponseDTO("Amount is less than 1");
            }
            String paymentType = request.getPayload().getPaymentType();
            // LC-2061 - surplus payment is allowed
//            if (PaymentType.CUSTOM_AMOUNT.name().equalsIgnoreCase(paymentType) && amount > (activeLoan.getDueAmount().intValue() + penaltyFee)) {
//                logger.info("custom amount:{} more than due amount:{} for merchant:{}", amount, activeLoan.getDueAmount().intValue(), merchantBasicDetails.getId());
//                return new InitiatePaymentResponseDTO("Custom amount should be less than due amount");
//            }
            if (PaymentType.DUE_AMOUNT.name().equalsIgnoreCase(paymentType) && amount > (activeLoan.getDueAmount().intValue() + penaltyFee)) {
                logger.info("Due Amount in request :{} more than due amount:{} for merchant:{}", amount, activeLoan.getDueAmount().intValue(), merchantBasicDetails.getId());
                return new InitiatePaymentResponseDTO("No dues left.");
            }


            Date checkPendingAfterTime = dateTimeUtil.getDatePlusMinutes(dateTimeUtil.getCurrentDate(), -1 * loanPaymentOrderPendingTransactionTimeWindow);

            // fetch pending transactions in the last loanPaymentOrderPendingTransactionTimeWindow minutes
            final LoanPaymentOrderSlave pendingTransaction =
              loanPaymentOrderSlaveDao.findTopByOwnerIdAndMerchantIdAndStatusInAndCreatedAtGreaterThan(activeLoan.getId(), activeLoan.getMerchantId(),
                checkPendingAfterTime);

            if (!ObjectUtils.isEmpty(pendingTransaction)) {
                logger.info("Already a pending transaction exist for loanId : {} with LPO id : {}", activeLoan.getId(), pendingTransaction.getId());
                return new InitiatePaymentResponseDTO("Previous transaction is pending.");
            }

            Long appVersion = getAppVersion(request);
            logger.info("app version and client name in pg flow: {} {}",appVersion, request.getMeta().getClient());
            if (Objects.equals(request.getMeta().getClient(), "android")) {
                if (appVersion < androidVersion) {
                    logger.info("app version of android: {} for merchant: {}",appVersion, merchantBasicDetails.getId());
                    return new InitiatePaymentResponseDTO("App version of android is less. Please update");
                }
            } else {
                if (appVersion < iosVersion) {
                    logger.info("app version of ios: {} for merchant: {}",appVersion, merchantBasicDetails.getId());
                    return new InitiatePaymentResponseDTO("App version of ios is less. Please update");
                }
            }

            if (PaymentType.ADVANCE_EDI.name().equalsIgnoreCase(paymentType)) {
                Integer advanceEdiCount = request.getPayload().getAdvanceEdiCount();
                if (advanceEdiCount == null) {
                    logger.info("advance edi count is not present for merchant:{}", merchantBasicDetails.getId());
                    return new InitiatePaymentResponseDTO("Advance edi count not present");
                }
                if (advanceEdiCount > activeLoan.getEdiRemainingCount()) {
                    logger.info("advance edi count is more than remaining edi count for merchant:{}", merchantBasicDetails.getId());
                    return new InitiatePaymentResponseDTO("Advance edi count should be less than remaining edi count");
                }
                Integer advanceEdiAmount = activeLoan.getDueAmount().intValue() + (request.getPayload().getAdvanceEdiCount() * activeLoan.getEdiAmount().intValue());
                if (!amount.equals(advanceEdiAmount)) {
                    logger.info("advance edi amount:{} is not matching for merchant:{}", advanceEdiAmount, merchantBasicDetails.getId());
                    return new InitiatePaymentResponseDTO("Advance edi amount is not correct");
                }
            }
            //Creating temporary loan payment order
            String preOrderId = "LPS" + activeLoan.getId() + System.currentTimeMillis();
            LoanPaymentOrder order = new LoanPaymentOrder();

            // TODO : remove this and use api
//			Merchant merchant = merchantDao.getById(merchantBasicDetails.getId());

            order.setMerchantId(merchantBasicDetails.getId());

            order.setOwner("lending_payment_schedule");
            order.setOwnerId(activeLoan.getId());
            order.setOrderId(preOrderId);
            order.setAmount(Double.valueOf(amount));
            order.setStatus(CreditConstants.PaymentStatus.INIT.name());
            if (request.getPayload().getSource() != null) {
                order.setSource(request.getPayload().getSource().name());
            }

            if(PaymentType.FORECLOSURE.name().equalsIgnoreCase(paymentType) && loanUtil.isTodayIsLoanLastDay(activeLoan)){
                logger.info("Foreclosure not allowed on last day of the loan: {}", activeLoan.getId());
                return new InitiatePaymentResponseDTO("Foreclosure not allowed");
            }

            if (PaymentType.FORECLOSURE.name().equalsIgnoreCase(paymentType)
                    || PaymentType.ADVANCE_EDI.name().equalsIgnoreCase(paymentType)
            ) {
                order.setDescription(paymentType);
            }

            if (PaymentType.CUSTOM_AMOUNT.name().equalsIgnoreCase(paymentType)) {
                boolean surplusAmount = amount - (activeLoan.getDueAmount().intValue() + penaltyFee) > 1;
                String customDesc = PaymentType.CUSTOM_AMOUNT.name() + (surplusAmount ? "_SURPLUS" : "");
                order.setDescription(customDesc);
            }

            order = loanPaymentOrderDao.save(order);
            String orderId = "LOAN" + (10000000L + order.getId());
            order.setOrderId(orderId);
            boolean paymentSuccess = false;
            Boolean otpFlow = null;
            String authMode = null;
            String accountNumber = null;
            String ifsc = null;
            PgCreateTransactionRequestDTO pgCreateTransactionRequestDTO = new PgCreateTransactionRequestDTO();
            pgCreateTransactionRequestDTO.setOrderAmount(amount.doubleValue());
            pgCreateTransactionRequestDTO.setOrderId(orderId);
            pgCreateTransactionRequestDTO.setNarration("Payment for Order No " + orderId);
            pgCreateTransactionRequestDTO.setPaymentPageHeaderText(PaymentConstants.PG_PAGE_HEADER_TEXT);
            if (activeLoan.getLoanApplication() != null && !StringUtils.isEmpty(activeLoan.getLoanApplication().getCkycId())) {//new loan flow
                pgCreateTransactionRequestDTO.setRedirectURIDeeplink("bharatpe://dynamic?key=" + pgRedirectionUrl + "&wroute=payment-status&wid=" + orderId);
            } else {
                pgCreateTransactionRequestDTO.setRedirectURIDeeplink("bharatpe://dynamic?key=loan&txnID=" + orderId);
            }
            pgCreateTransactionRequestDTO.setAllowedModes(Arrays.asList("CC", "DC","NB","BP","UPI","FP"));
            pgCreateTransactionRequestDTO.setLender(Lender.valueOf(activeLoan.getNbfc()));

            if (loanUtil.isInternalMerchant(merchantBasicDetails.getId()) ||
                    easyLoanUtil.percentScaleUp(merchantBasicDetails.getId(), apiGatewayService.pgPercent)) {
                logger.info("pg flow enabling for internal merchants with app version for merchant: {}",merchantBasicDetails.getId());
                if (Objects.equals(request.getMeta().getClient(), "android")) {
                    if (appVersion >= androidVersion) {
                        pgCreateTransactionRequestDTO.setCheckout("JUSPAY");
                    } else {
                        pgCreateTransactionRequestDTO.setCheckout("BHARATPE");
                    }
                } else {
                    if (appVersion >= iosVersion) {
                        pgCreateTransactionRequestDTO.setCheckout("JUSPAY");
                    } else {
                        pgCreateTransactionRequestDTO.setCheckout("BHARATPE");
                    }
                }
            } else {
                pgCreateTransactionRequestDTO.setCheckout("BHARATPE");
            }

            PgCreateTransactionResponseDTO response = apiGatewayService.createPgTransaction(merchantBasicDetails.getId(), pgCreateTransactionRequestDTO);

            if(response != null && response.getStatusCode() != null && "200".equalsIgnoreCase(response.getStatusCode())) {
                paymentSuccess = true;
            }
            if (!paymentSuccess) {
                order.setStatus(CreditConstants.PaymentStatus.FAILED.name());
                order.setDescription("Unable to initiate txn");
                loanPaymentOrderDao.save(order);
                return new InitiatePaymentResponseDTO("Something went wrong.");
            }
            order.setStatus(CreditConstants.PaymentStatus.PENDING.name());
            loanPaymentOrderDao.save(order);

            if (PaymentType.FORECLOSURE.name().equalsIgnoreCase(paymentType) && request.getPayload().getForeClosureDetail() != null) {
                saveLoanForeClosureCharges(merchantBasicDetails, order.getId(), activeLoan.getId(), request.getPayload().getForeClosureDetail());
            }
            if (PaymentType.FORECLOSURE.name().equalsIgnoreCase(paymentType) && (request.getPayload().getForeClosureAmountAtLender() != 0 ||  request.getPayload().getForeClosureAmountAtBP() != 0)) {
                saveForeClosureAmountInfo(merchantBasicDetails, order.getId(), activeLoan.getId(), request.getPayload().getForeClosureAmountAtBP(),request.getPayload().getForeClosureAmountAtLender());
            }
            InitiatePaymentResponseDTO.Data data = new InitiatePaymentResponseDTO.Data(order.getVpa(), order.getUpiIntent(), order.getShortLink(), order.getOrderId(), otpFlow, authMode, accountNumber, ifsc, null);
            data.setPaymentLink(response.getData().getPaymentURIDeeplink());
            return new InitiatePaymentResponseDTO(data);
        } catch(Exception ex) {
            logger.error("Exception while initiating payment for merchant id {} {} {}", merchantBasicDetails.getId(), ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        return new InitiatePaymentResponseDTO("Something went wrong.");
    }

    // equivalent to with error handling
    // Long appVersion = Objects.nonNull(request.getMeta().getDeviceInfo().getAppVersion()) ? Long.parseLong(request.getMeta().getDeviceInfo().getAppVersion()) : 100L;
    public long getAppVersion(RequestDTO<InitiatePaymentRequestDTO> request) {
        try {
            if (request == null || request.getMeta() == null
                || request.getMeta().getDeviceInfo() == null
                || StringUtils.isEmpty(request.getMeta().getDeviceInfo().getAppVersion())
            ) return 100L; // default version if meta or device info is not present

            String appVersion = request.getMeta().getDeviceInfo().getAppVersion();
            try {
                return Long.parseLong(appVersion);
            } catch (NumberFormatException numberFormatException) {
                log.error("numberFormatException for {}", request.getMeta());
                return Long.parseLong(convertVersionCode(appVersion));
            }
        } catch (Exception e) {
            log.error("getAppVersion for {}", request);
        }
        return 100L; // default version if not provided or parsing fails
    }

    public String convertVersionCode(String appVersion) {
        if (StringUtils.isEmpty(appVersion)) {
            return "100";
        }
        String finalVersionResult = appVersion.replace(".", "");
        log.info("Converted version:{} for version {} ",appVersion, finalVersionResult);
        return finalVersionResult;
    }

    private void saveLoanForeClosureCharges(BasicDetailsDto merchantBasicDetails, long orderId, long loanId, ForeClosureDetailDTO foreClosureDetail) {
        Optional<ForeClosureConfig> foreClosureConfig = foreClosureConfigDao.findById(foreClosureDetail.getId());
        LoanForeClosureCharges loanForeClosureCharges = LoanForeClosureCharges.builder()
                .foreclosureGridId(foreClosureDetail.getId())
                .orderId(orderId)  // TODO:clarify lpo id  or lpo orderId
                .loanId(loanId)
                .merchantId(merchantBasicDetails.getId())
                .amount(foreClosureDetail.getForeclosureCharges())
                .tax(foreClosureDetail.getGst())
                .status(CreditConstants.PaymentStatus.PENDING.name())
                .build();

        if (foreClosureConfig.isPresent()) {
            loanForeClosureCharges.setRate(foreClosureConfig.get().getRate());
            loanForeClosureCharges.setTaxRate(foreClosureConfig.get().getGst());
            loanForeClosureCharges.setMinAmount(foreClosureConfig.get().getMinAmount());
        } else {
            logger.error("ForeClosure charges config missing for mid : {} loanId : {} , loanPaymentOrderId : {} configId : {}",merchantBasicDetails.getId(), loanId, orderId, foreClosureDetail.getId());
        }
        loanForeClosureChargesDao.save(loanForeClosureCharges);
    }


    private void saveForeClosureAmountInfo(BasicDetailsDto merchantBasicDetails, long orderId, long loanId, Double foreClosureAmountAtBP, Double foreClosureAmountAtLender) {
        try {
            ForeClosureAmountInfo foreClosureAmountInfo = ForeClosureAmountInfo.builder()
                    .orderId(orderId)
                    .loanId(loanId)
                    .ledgerId(0L)
                    .merchantId(merchantBasicDetails.getId())
                    .foreclosureAmountAtBP(foreClosureAmountAtBP)
                    .foreclosureAmountAtLender(foreClosureAmountAtLender)
                    .foreclosureAmountDiff(foreClosureAmountAtLender - foreClosureAmountAtBP)
                    .build();
            foreClosureAmountInfoDao.save(foreClosureAmountInfo);
        }catch (Exception e){
            log.error("going to save details in loanforeclosureamount info for loanId {} orderId {} foreClosureAmountAtBP {} and foreClosureAmountAtLender {}",loanId,orderId,foreClosureAmountAtBP,foreClosureAmountAtLender);
        }
    }

    public InitiatePaymentResponseDTO initiatePayment(BasicDetailsDto merchantBasicDetails, RequestDTO<InitiatePaymentRequestDTO> request, String token) {
        logger.info("Received initiate payment request  for merchant {} : {}", merchantBasicDetails.getId(), request);
        try {
            LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantBasicDetails.getId(),  Arrays.asList("ACTIVE", "DECEASED"));
            if(activeLoan == null) {
                logger.info("No active loan found for merchant id {}", merchantBasicDetails.getId());
                return new InitiatePaymentResponseDTO("No active loan found.");
            }
            if (request.getPayload().getType() != null && request.getPayload().getType().equals(CreditConstants.PaymentMode.BT)) {
                LendingVirtualAccount lendingVirtualAccount = apiGatewayService.createLendingVAN(merchantBasicDetails.getId(), activeLoan.getId());
                if (lendingVirtualAccount != null) {
                    final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantBasicDetails.getId());
                    BankDetailsDto merchantBankDetail = null;
                    if (bankDetailsDtoOptional.isPresent())
                        merchantBankDetail = bankDetailsDtoOptional.get();
                    InitiatePaymentResponseDTO.Data data = new InitiatePaymentResponseDTO.Data(null, null, null, null, null, null, lendingVirtualAccount.getAccountNumber(), lendingVirtualAccount.getIfsc(), merchantBankDetail.getBeneficiaryName());
                    return new InitiatePaymentResponseDTO(data);
                }
                return new InitiatePaymentResponseDTO("Something went wrong.");
            }
            Integer overdueAmount = activeLoan.getDueAmount().intValue();
            Integer principalDueAmount = loanUtil.getForeclosureAmount(activeLoan);
            Integer ediHolidayInterestAmount = getEDIHolidayInterestAmount(activeLoan);
            Integer amount = 0;
            if("CUSTOM".equalsIgnoreCase(request.getPayload().getPaymentType())) {
                amount = request.getPayload().getAmount();
            } else if("PRINCIPAL".equalsIgnoreCase(request.getPayload().getPaymentType()) || "TOTAL_AMOUNT".equalsIgnoreCase(request.getPayload().getPaymentType())) {
                amount = principalDueAmount + ediHolidayInterestAmount;
            } else {
                amount = overdueAmount;
            }
            List<String> psps = Arrays.asList("com.google.android.apps.nbu.paisa.user","net.one97.paytm","in.org.npci.upiapp","com.csam.icici.bank.imobile","com.mobikwik_new","com.myairtelapp","com.phonepe.app","com.olacabs.customer");
            if(amount < 1 || amount > 100000) {
                logger.info("Amount not between 1-100000 for merchant id {}", merchantBasicDetails.getId());
                return new InitiatePaymentResponseDTO("Amount should be between 1-100000.");
            }
            if (amount > 2000 && request.getPayload().getVpa() == null && request.getPayload().getType() == null) {
                logger.info("VPA missing for merchant id {}", merchantBasicDetails.getId());
                return new InitiatePaymentResponseDTO("VPA missing");
            }

            LoanPaymentOrder order = new LoanPaymentOrder();

            // TODO : remove this and use api
//			Merchant merchant = merchantDao.getById(merchantBasicDetails.getId());
            order.setMerchantId(merchantBasicDetails.getId());

            order.setOwner("lending_payment_schedule");
            order.setOwnerId(activeLoan.getId());
            order.setAmount(Double.valueOf(amount));
            order.setStatus("INIT");

            if (PaymentType.FORECLOSURE.name().equalsIgnoreCase(request.getPayload().getPaymentType())) {
                if(loanUtil.isTodayIsLoanLastDay(activeLoan)){
                    logger.info("Foreclosure not allowed on last day of the loan: {}", activeLoan.getId());
                    return new InitiatePaymentResponseDTO("Foreclosure not allowed");
                }
                order.setDescription(PaymentType.FORECLOSURE.name());
            }

            if (request.getPayload().getSource() != null) {
                order.setSource(request.getPayload().getSource().name());
            }
            order = loanPaymentOrderDao.save(order);
            String orderId = "LOAN" + (10000000L + order.getId());
            order.setOrderId(orderId);
            boolean paymentSuccess = false;
            Boolean otpFlow = null;
            String authMode = null;
            String accountNumber = null;
            String ifsc = null;
            if (request.getPayload().getType() != null && request.getPayload().getType().equals(CreditConstants.PaymentMode.BPB)) {
                Map<String, Object> result = apiGatewayService.initiateTxn(request.getMeta(), request.getSimInfo(), Double.valueOf(amount), null, orderId, token, "BharatPe Loans", request.getPayload().getSource().name());
                paymentSuccess = result.containsKey("success") ?  (Boolean) result.get("success") : false;
                otpFlow = result.containsKey("otp_flow") ? (Boolean) result.get("otp_flow") : null;
                authMode = result.containsKey("auth_mode") ? (String) result.get("auth_mode") : null;
            } else { //UPI
                Map vpaResponse = apiGatewayService.createVPA(merchantBasicDetails, Double.valueOf(amount), orderId, request.getPayload().getVpa());
                if(vpaResponse != null && vpaResponse.get("status") != null && "OK".equalsIgnoreCase((String) vpaResponse.get("status"))) {
                    paymentSuccess = true;
                    order.setVpa((String) vpaResponse.get("bharatpeTxnId"));
                    order.setShortLink((String) vpaResponse.get("paymentLink"));
                    order.setUpiIntent((String) vpaResponse.get("upiString"));
                    order.setMid((String) vpaResponse.get("mid"));
                }
            }
            if (!paymentSuccess) {
                order.setStatus("FAILED");
                order.setDescription("Unable to initiate txn");
                loanPaymentOrderDao.save(order);
                return new InitiatePaymentResponseDTO("Something went wrong.");
            }
            order.setStatus("PENDING");
            loanPaymentOrderDao.save(order);

            if (PaymentType.FORECLOSURE.name().equalsIgnoreCase(request.getPayload().getPaymentType()) && request.getPayload().getForeClosureDetail() != null) {
                saveLoanForeClosureCharges(merchantBasicDetails, order.getId(), activeLoan.getId(), request.getPayload().getForeClosureDetail());
            }
            if (PaymentType.FORECLOSURE.name().equalsIgnoreCase(request.getPayload().getPaymentType()) && (request.getPayload().getForeClosureAmountAtLender() != 0 || request.getPayload().getForeClosureAmountAtBP()!= 0)) {
                saveForeClosureAmountInfo(merchantBasicDetails, order.getId(), activeLoan.getId(), request.getPayload().getForeClosureAmountAtBP(),request.getPayload().getForeClosureAmountAtLender());
            }
            InitiatePaymentResponseDTO.Data data = new InitiatePaymentResponseDTO.Data(order.getVpa(), order.getUpiIntent(), order.getShortLink(), order.getOrderId(), otpFlow, authMode, accountNumber, ifsc, null);
            data.setPsps(psps);
            return new InitiatePaymentResponseDTO(data);
        } catch(Exception ex) {
            logger.error("Exception while initiating payment for merchant id {} {} {}", merchantBasicDetails.getId(), ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        return new InitiatePaymentResponseDTO("Something went wrong.");
    }

    public String handleCallback(PaymentCallbackRequestDTO request) {
        logger.info("Received payment callback request for order ID {} : {}", request.getOrderId(), request);
        try {
            LoanPaymentOrder order = loanPaymentOrderDao.findByOrderId(request.getOrderId());
            if(order == null) {
                logger.error("No order for order id {}", request.getOrderId());
                return "OK";
            }
            if(!"PENDING".equalsIgnoreCase(order.getStatus())) {
                logger.info("Payment for merchant id {} and order id {} is already processed", order.getMerchantId(), request.getOrderId());
                return "OK";
            }
            if(request.getAmount() == null || request.getAmount() <= 0D) {
                logger.error("Invalid amount received for merchant {} and amount {}", order.getMerchantId(), request.getAmount());
                return "OK";
            }
            Optional<LendingPaymentSchedule> activeLoan = lendingPaymentScheduleDao.findById(order.getOwnerId());
            if(!activeLoan.isPresent()) {
                logger.error("No active loan found for id {}", order.getOwnerId());
                return "OK";
            }
            if(order.getAmount()  - request.getAmount() < -1 || order.getAmount() - request.getAmount() > 1) {
                logger.error("Amount mismatch for the merchant {} and order id {}", order.getMerchantId(), request.getOrderId());
                order.setStatus("FAILED");
                order.setDescription("Amount mismatch");
                loanPaymentOrderDao.save(order);
                loanClosureService.updateForeclosureChargesStatus(order.getStatus(), order.getId());
                return "OK";
            }

            String transferType = null;
            // we explicitly set nach lender in description in nach flow
            if ("BHARATPE_NACH".equals(order.getSource()) && StringUtils.hasLength(order.getDescription())
                    && !"BHARATPE".equalsIgnoreCase(order.getDescription())) {
                transferType = "EXTERNAL";
            }

            try {
                adjustLoanBalance(activeLoan.get(), request.getAmount(), request.getBankReferenceNumber(), order.getSource(),
                        PaymentType.ADVANCE_EDI.name().equalsIgnoreCase(order.getDescription()), transferType, request.getTerminalOrderId(), order.getId(),
                        PaymentType.FORECLOSURE.name().equalsIgnoreCase(order.getDescription()));
                order.setBankRefNo(request.getBankReferenceNumber());
                order.setStatus("SUCCESS");
                loanPaymentOrderDao.save(order);
                loanClosureService.updateForeclosureChargesStatus(order.getStatus(), order.getId());
            }  catch (DuplicateTransactionException e) {
                logger.error("Duplicate transaction for order id {} : {}", request.getOrderId(), e.getMessage());
                order.setStatus("DUPLICATE");
                order.setTerminalOrderId(request.getTerminalOrderId());
                order.setDescription("Duplicate transaction");
                loanPaymentOrderDao.save(order);
            }
        } catch(Exception ex) {
            logger.error("Exception in payment callback for order id {} {} {}", request.getOrderId(), ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        return "OK";
    }


    public String handlePgCallback(PgPaymentCallbackDTO request) {
        log.info("Pg callback reciverd from pg {}",request);
        if (pgEmiCallbackEnable && request != null && request.getOrderId() != null && request.getOrderId().startsWith(pgEmiOrderIdPrefix)) {
            log.info("Callback received for EMI LOAN orderId: {}", request.getOrderId());
            emiHandler.handleEmiPgCallback(request);
            return "OK";
        }
        if (request.getEvent() != null && request.getMandate() !=null) {
            if ("MANDATE".equalsIgnoreCase(request.getEvent())
                    || (request.getOrderId()!=null && request.getOrderId().startsWith("Auto-UPI"))
                    || (request.getMandate().getOrderId() != null && request.getMandate().getOrderId().equalsIgnoreCase(request.getOrderId()))
            ) {
                logger.info("Mandate Object found for this request merchantId{}", request.getMandate().getCustomerId());
                return autoPayUPIService.handleMandatePgCallback(request, null);
            } else if ("transaction".equalsIgnoreCase(request.getEvent()) && !request.getMandate().getOrderId().equalsIgnoreCase(request.getOrderId())) {
                log.info("inside settlement of amount of autopay upi presentment");
                try {
                    log.info("mandate presentment transaction {}", request.getMandate().getOrderId());
                    if (request.getOrderId().startsWith("LENDING")) {
                        request.setOrderId( request.getOrderId().replaceFirst("LENDING", ""));
                    }
                    Optional<LendingPullPayment> optionalLendingPullPayment = lendingPullPaymentDao.findById(Long.valueOf(request.getOrderId()));
                    if (!optionalLendingPullPayment.isPresent()) {
                        logger.error("Order not found in mandate settlement transaction for orderId {}",request.getOrderId());
                        return "OK";
                    }
                    LendingPullPayment lendingPullPayment = optionalLendingPullPayment.get();
                    if("SUCCESS".equalsIgnoreCase( lendingPullPayment.getStatus())){
                        logger.info("lendingPullPayment status is success for id {} and loanId {}",lendingPullPayment.getId(),lendingPullPayment.getLoanId());
                        return "OK";
                    }

                    boolean pullPaymentInProcess = checkIfPullPaymentInProcess(lendingPullPayment);

                    if (pullPaymentInProcess) {
                        logger.info("lendingPullPayment processing is in progress for id {} and loanId {}", lendingPullPayment.getId(), lendingPullPayment.getLoanId());
                        return "OK";
                    }

                    Optional<LendingPaymentSchedule> optionalLendingPaymentSchedule = lendingPaymentScheduleDao.findById(lendingPullPayment.getLoanId());
                    if(!optionalLendingPaymentSchedule.isPresent()){
                        logger.error("LPS not found in mandate settlement transaction for request {}",request);
                        return "OK";
                    }
                    LendingPaymentSchedule lendingPaymentSchedule = optionalLendingPaymentSchedule.get();
                    if (lendingPullPayment != null && !"LDC".equalsIgnoreCase(lendingPaymentSchedule.getNbfc())) {
                        if ("SUCCESS".equalsIgnoreCase(request.getPaymentStatus())) {
                            Long loanId = lendingPullPayment.getLoanId();
                            String lockKey = AUTO_PAY_SETTLEMENT + loanId;
                            if (lendingCache.acquireLock(lockKey, autoPayUpiLockTimeout)) {
                                log.info("Acquired lock on lockKey {} , loanId {}",lockKey,loanId);
                                handleUpiAutoPaySucessOrder(request, lendingPullPayment);
                                lendingPullPayment.setStatus(request.getPaymentStatus());
                                lendingPullPaymentDao.save(lendingPullPayment);
                                //if(autoPayUpiDpdPenaltyEnabled)  confluentKafkaTemplate.send("autopayupi-real-time-dpd", lendingPullPayment.getId());
                            } else {
                                log.info("lock could not be acquired on lockKey {} , loanId {}",lockKey,loanId);
                                return "OK";
                            }
                        } else {
                            if((ONE_LMS).equalsIgnoreCase(lendingPaymentSchedule.getLmsSource())){
                                logger.info("1LMS autopay mandate failed for loanId {} and lendingPullPaymentId {}",lendingPullPayment.getLoanId(),lendingPullPayment.getId());
                                lendingPullPayment.setStatus(request.getPaymentStatus());
                                lendingPullPayment.setErrorDescription(request.getErrorDescription());
                                lendingPullPaymentDao.save(lendingPullPayment);
                                if("FAILURE".equalsIgnoreCase(request.getPaymentStatus()) && "AUTOPAYUPI".equalsIgnoreCase(lendingPullPayment.getMode())){
                                    log.info("autoPay-upi payment failed for 1LMS lendingPullPayment id {} and loanId {}",lendingPullPayment.getId(),lendingPullPayment.getLoanId());
                                    lendingNotificationService.sendAutoPayUpiFailureComm(request.getOrderAmount(),lendingPullPayment.getMerchantId());
                                }
                                List<LendingPullPayment> lendingPullPaymentList =   lendingPullPaymentDao.findPaymentsForConsecutiveCheck(lendingPullPayment.getLoanId(),lendingPullPayment.getId(),lmsPreviousMandateFailed);
                                log.info("consecutive records for id {} lendingPullPaymentList size is {}",lendingPullPayment.getId(),lendingPullPaymentList.size());
                                if(checkConsecutiveFailures(lendingPullPaymentList,lmsPreviousMandateFailed) ){
                                    log.info("Consecutive failures found for mandateId {} and loanId {}",lendingPullPayment.getId(),lendingPullPayment.getLoanId());
                                    List<String> statusList = new ArrayList<>();
                                    statusList.add(AutoPayStatusEnum.ACTIVE.name());
                                    AutoPayUPI autoPayUPI = autoPayUPIDao.findTop1ByApplicationIdAndStatusOrderByIdDesc(lendingPaymentSchedule.getApplicationId(), lendingPaymentSchedule.getNbfc(), statusList);
                                    autoPayUPI.setIsAutoPayUpiDeduction(DeductionStatusEnum.HARD_QR_DEDUCTION.name());
                                    autoPayUPIDao.save(autoPayUPI);
                                }
                                return OK;
                            }
                            lendingPullPayment.setErrorDescription(request.getErrorDescription());
                            lendingPullPayment.setStatus("PENDING".equalsIgnoreCase(request.getPaymentStatus()) ? request.getPaymentStatus() : "FAILED");

                            updateErrorCodeErrorDescription(request, lendingPullPayment);

                            lendingPullPaymentDao.save(lendingPullPayment);
                            if("FAILURE".equalsIgnoreCase(request.getPaymentStatus()) && "AUTOPAYUPI".equalsIgnoreCase(lendingPullPayment.getMode())){
                                log.info("autoPay-upi payment failed for lendingPullPayment id {} and loanId {}",lendingPullPayment.getId(),lendingPullPayment.getLoanId());
                                lendingNotificationService.sendAutoPayUpiFailureComm(request.getOrderAmount(),lendingPullPayment.getMerchantId());
                            }
                            // if(!"PENDING".equalsIgnoreCase(request.getPaymentStatus()) && autoPayUpiDpdPenaltyEnabled) confluentKafkaTemplate.send("autopayupi-real-time-dpd", lendingPullPayment.getId());
                            List<LendingPullPayment> lendingPullPaymentList =   lendingPullPaymentDao.findPaymentsForConsecutiveCheck(lendingPullPayment.getLoanId(),lendingPullPayment.getId(),previouMandateFailed -1);
                            log.info("consecutive records for id {} lendingPullPaymentList size is {}",lendingPullPayment.getId(),lendingPullPaymentList.size());
                            if(checkConsecutiveFailures(lendingPullPaymentList,previouMandateFailed -1) ){
                                log.info("Consecutive failures found for mandateId {} and loanId {}",lendingPullPayment.getId(),lendingPullPayment.getLoanId());
                                List<String> statusList = new ArrayList<>();
                                statusList.add(AutoPayStatusEnum.ACTIVE.name());
                                AutoPayUPI autoPayUPI = autoPayUPIDao.findTop1ByApplicationIdAndStatusOrderByIdDesc(lendingPaymentSchedule.getApplicationId(), lendingPaymentSchedule.getNbfc(), statusList);
                                autoPayUPI.setIsAutoPayUpiDeduction(DeductionStatusEnum.HARD_QR_DEDUCTION.name());
                                autoPayUPIDao.save(autoPayUPI);
                            }
                            return "OK";
                        }
                    }
                    return "OK";
                } catch (Exception e){
                    logger.error("Exception occurred while progressing autopay callback {} {}",e.getMessage(), Arrays.asList(e.getStackTrace()));
                }
            }
        }

        else {
            logger.info("Received payment callback request for order ID {} : {}", request.getOrderId(), request);
            if (Objects.isNull(request.getPayments())) {
                logger.info("null payments object in pg callback for request: {}", request);
                return "OK";
            }
            if (!ObjectUtils.isEmpty(request.getPayments()) && request.getPayments().get(0).getStatus().equalsIgnoreCase("SUCCESS")
                    && request.getPayments().get(0).getAccountType().equalsIgnoreCase("UNKNOWN")) {
                logger.info("unknown account type in pg callback for request: {}", request);
                return "OK";
            }
            LoanPaymentOrder order = loanPaymentOrderDao.findByOrderId(request.getOrderId());
            try {
                if (order == null) {
                    logger.error("No order for order id {}", request.getOrderId());
                    return "OK";
                }
                logger.info("status of order saved in DB: {} for orderId: {}", order.getOrderId(), order.getStatus());
                if (!"PENDING".equalsIgnoreCase(order.getStatus())) {
                    logger.info("Payment for merchant id {} and order id {} is already processed", order.getMerchantId(), request.getOrderId());
                    return "OK";
                }

                int lockTxn = loanPaymentOrderDao.updateStatusForPendingTxn(CreditConstants.PaymentStatus.CALLBACK_RECEIVED.name(), order.getId());
                if (lockTxn != 1) {
                    logger.info("Unable to take lock on loan payment order:{} ", order.getId());
                    return "OK";
                }

                if (request.getPaymentAmount() == null || request.getPaymentAmount() <= 0D) {
                    logger.error("Invalid amount received for merchant {} and amount {}", order.getMerchantId(), request.getPaymentAmount());
                    return "OK";
                }
                Optional<LendingPaymentSchedule> activeLoan = lendingPaymentScheduleDao.findById(order.getOwnerId());
                if (!activeLoan.isPresent()) {
                    logger.error("No active loan found for id {}", order.getOwnerId());
                    return "OK";
                }
                if (order.getAmount() - request.getPaymentAmount() < -1 || order.getAmount() - request.getPaymentAmount() > 1) {
                    logger.error("Amount mismatch for the merchant {} and order id {}", order.getMerchantId(), request.getOrderId());
                    order.setStatus("FAILED");
                    order.setDescription("Amount mismatch");
                    loanPaymentOrderDao.save(order);
                    return "OK";
                }
                if (Objects.nonNull(request.getPayments()) && !request.getPayments().isEmpty() && Objects.nonNull(request.getPayments().get(0)) && Objects.nonNull(request.getPayments().get(0).getMode())) {
                    order.setSource(request.getPayments().get(0).getMode());
                }
                if (request.getPaymentStatus() != null && !ObjectUtils.isEmpty(request.getPayments())) {
                    if ("FAILURE".equalsIgnoreCase(request.getPayments().get(0).getStatus())) {
                        order.setStatus(Status.TransactionStatus.FAILED.name());
//                    order.setDescription(response.getData().getErrorDescription());
                    } else {
                        order.setStatus(request.getPayments().get(0).getStatus());
                    }
                    if ("SUCCESS".equalsIgnoreCase(request.getPayments().get(0).getStatus())) {
                        String accountType = null;
                        String terminalOrderId = null;
                        if (Objects.nonNull(request.getPayments()) && !request.getPayments().isEmpty() && Objects.nonNull(request.getPayments().get(0)) && Objects.nonNull(request.getPayments().get(0).getAccountType())) {
                            accountType = request.getPayments().get(0).getAccountType();
                        }

                        if (Objects.nonNull(request.getPayments()) && !request.getPayments().isEmpty() && Objects.nonNull(request.getPayments().get(0)) && Objects.nonNull(request.getPayments().get(0).getFinalGateway())) {
                            order.setFinalGateway(request.getPayments().get(0).getFinalGateway());
                        }
                        if (Objects.nonNull(request.getPayments()) && !request.getPayments().isEmpty() && Objects.nonNull(request.getPayments().get(0)) && Objects.nonNull(request.getPayments().get(0).getTerminalOrderId())) {
                            terminalOrderId = request.getPayments().get(0).getTerminalOrderId();
                            order.setTerminalOrderId(terminalOrderId);
                        }

                        order.setCheckoutType(request.getCheckoutType());
                        order.setBankRefNo(request.getPaymentRefId());

                        try {
                            adjustLoanBalance(activeLoan.get(), request.getPaymentAmount(), request.getPaymentRefId(), order.getSource(),
                                    PaymentType.ADVANCE_EDI.name().equalsIgnoreCase(order.getDescription()), accountType, terminalOrderId, order.getId(),
                                    PaymentType.FORECLOSURE.name().equalsIgnoreCase(order.getDescription()));
                        }  catch (DuplicateTransactionException e) {
                            logger.error("Duplicate transaction for order id {} : {}", request.getOrderId(), e.getMessage());
                            order.setStatus("DUPLICATE");
                            order.setTerminalOrderId(terminalOrderId);
                            order.setDescription("Duplicate transaction");
                        }
                    }
                }
                loanPaymentOrderDao.save(order);
            } catch (Exception ex) {
                if (order != null) {
                    order.setStatus("PENDING");
                    loanPaymentOrderDao.save(order);
                }
                logger.error("Exception in payment callback for order id {} {} {}", request.getOrderId(), ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            }
            logger.info("final order id : {}  callback payments status is pg callback for request: {}", order.getOrderId(), order.getStatus());
            if (order != null && !CreditConstants.PaymentStatus.PENDING.name().equalsIgnoreCase(order.getStatus())) {
                loanClosureService.updateForeclosureChargesStatus(order.getStatus(), order.getId());
            }
        }
        return "OK";
    }

    private void updateErrorCodeErrorDescription(PgPaymentCallbackDTO request, LendingPullPayment lendingPullPayment) {
        if(request != null && !"PENDING".equalsIgnoreCase(request.getPaymentStatus())  && request.getInternalErrorCode() != null) {
            lendingPullPayment.setErrorCode(request.getInternalErrorCode());
        }
//        if(request != null && !"PENDING".equalsIgnoreCase(request.getPaymentStatus())  && request.getInternalErrorMessage() != null) {
//            lendingPullPayment.setErrorDescription(request.getInternalErrorMessage());
//        }
    }

    private boolean checkIfPullPaymentInProcess(LendingPullPayment lendingPullPayment) {
        try {
            // true : means payment is in process for the given pull payment
            // false : means no payment is in process for the given pull payment
            Long loanId = lendingPullPayment.getLoanId();
            String lockKey = AUTO_PAY_SETTLEMENT + loanId;
            if (lendingCache.isKeyExist(lockKey)) {
                logger.info("lendingPullPayment processing is in progress for id {} and loanId {}", lendingPullPayment.getId(), lendingPullPayment.getLoanId());
                return true;
            }
            return false;
        }catch (Exception e){
            logger.error("Exception in checkIfPullPaymentInProcess {} {}", e.getMessage(),Arrays.asList(e.getStackTrace()));
            return true;
        }
    }

    private void sendSMS(LendingPaymentSchedule loan, Double amount, boolean isLoanClosed) {
        try {
            Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(loan.getMerchantId());
            if (ObjectUtils.isEmpty(basicDetailsDto)) {
                return;
            }

//			Merchant merchant = loan.getMerchant();
            String identifier = "LENDING_PAYMENT_PUSH";
            Map<String,Object> templateParams = new HashMap<>();
            templateParams.put("amount",amount.intValue());
            String deeplink = notificationUtil.getDeeplink(basicDetailsDto.get().getSettlementType(), "LOAN_DASHBOARD");
            NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
            notificationPayloadDto.setPushTitle("Payment received!");
            notificationPayloadDto.setTemplateIdentifier(identifier);
            notificationPayloadDto.setMobile(basicDetailsDto.get().getMobile());
            notificationPayloadDto.setPushDeepLink(deeplink);
            notificationPayloadDto.setClientName("LENDING");
            notificationPayloadDto.setTemplateParams(templateParams);
            lendingNotificationService.notify(notificationPayloadDto);
            if(isLoanClosed) {
                if(apiGatewayService.sendCommunicationForNewOffer(loan)) {
                    return;
                }
                identifier = "LENDING_PAYMENT_2_PUSH";
                notificationPayloadDto.setTemplateIdentifier(identifier);
                notificationPayloadDto.setPushTitle("The loan is closed successfully");
                lendingNotificationService.notify(notificationPayloadDto);
            }
        } catch(Exception ex) {
            logger.error("Exception while sending payment SMS to merchant {}, Exception is {}");
        }
    }

    private String getDescription(String bankRRN, boolean preclosure, boolean preclosureWithCharges) {
        String preclosureDescription = (preclosureWithCharges) ? "PRECLOSER_WITH_CHARGES_UPI : " : "PRECLOSER_UPI : ";
        return preclosure ? preclosureDescription + bankRRN : "PREPAYMENT : " + bankRRN;
    }

    private LendingLedger createLendingLedger(LendingPaymentSchedule lendingPaymentSchedule, Double amount, Double principle,
                                              Double interest, String description, String source, String transferType, String terminalOrderId, Double penaltyFee) {
        return createLendingLedger(lendingPaymentSchedule, amount, principle, interest, description, source, transferType, terminalOrderId, penaltyFee, null);
    }
    private LendingLedger createLendingLedger(LendingPaymentSchedule lendingPaymentSchedule, Double amount, Double principle,
                                     Double interest, String description, String source, String transferType, String terminalOrderId, Double penaltyFee, Double otherCharges) {
        if(amount == 0) {
            return null;
        }

        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setMerchantId(lendingPaymentSchedule.getMerchantId());
        if(lendingPaymentSchedule.getMerchantStoreId() != null && lendingPaymentSchedule.getMerchantStoreId() > 0) {
            lendingLedger.setMerchantStoreId(lendingPaymentSchedule.getMerchantStoreId());
        }

        lendingLedger.setLendingPaymentSchedule(lendingPaymentSchedule);
        lendingLedger.setDate(getCurrenntDate());
        lendingLedger.setTxnType("EDI");
        lendingLedger.setAmount(amount);
        lendingLedger.setInterest(interest);
        lendingLedger.setOtherCharges((otherCharges != null) ? otherCharges : 0);
        lendingLedger.setPenalty(penaltyFee);
        lendingLedger.setPrinciple(principle);
        if (source != null) {
            lendingLedger.setAdjustmentMode(source);
        } else {
            lendingLedger.setAdjustmentMode("UPI");
        }

        if(!ObjectUtils.isEmpty(source) && source.equals("BHARATPE_NACH")){
            Boolean isBpNachDone = false;
            isBpNachDone = loanUtil.isNachToBeRefunded(lendingPaymentSchedule.getLoanApplication());
            if(!isBpNachDone){
                transferType = "EXTERNAL";
            }
        }

        if (!ObjectUtils.isEmpty(source) && source.toUpperCase().contains("UPI")) {
            transferType = "EXTERNAL";
        }

        if(!ObjectUtils.isEmpty(source) && "LMS_PRECLOSURE".equals(source)){
            transferType = "EXTERNAL";
            if(amount > 0)    description = "PRECLOSER_IMPS/NEFT";
            lendingLedger.setAdjustmentMode("DIRECT_TRANSFER");
        }

        lendingLedger.setDescription(description);
        lendingLedger.setTerminalOrderId(terminalOrderId);
        lendingLedger.setTransferType(Objects.nonNull(transferType) && transferType.equals("EXTERNAL") ?
                CollectionTransferTypeEnum.DIRECT_TRANSFER_LENDER.name() : CollectionTransferTypeEnum.TRANSFER_BY_BP.name());

        lendingLedgerDao.save(lendingLedger);
        lendingCollectionAuditService.sendCollectionAudit(lendingLedger);

        if(amount > 0 && principle > 0) {
            logger.info("Credit principle:{} in lending global limit for merchant:{}", principle, lendingLedger.getMerchantId());
            notificationExecutor.execute(() -> apiGatewayService.globalLimitTxn(lendingLedger.getMerchantId(), "CREDIT", principle));
        }
        return lendingLedger;

    }

    private Date getCurrenntDate() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        return cal.getTime();
    }

    private Integer getEDIHolidayInterestAmount(LendingPaymentSchedule lps) {
        try {
            List<LendingEDISchedule> lendingEDISchedules = lendingEDIScheduleDao.getByLoanIdAndEdiType(lps.getId(), "EDIHOLIDAY");
            if (lendingEDISchedules != null && !lendingEDISchedules.isEmpty()) {
                return lendingEDISchedules.stream().mapToInt(LendingEDISchedule::getTotalEdi).sum();
            }
        } catch(Exception ex) {
            logger.error("Exception in getEDIHolidayInterestAmount for Loan ID {}, Exception is {}", lps.getId(), ex);
        }
        return 0;
    }

    public PaymentStatusResponseDTO getStatus(String orderId, BasicDetailsDto merchant) {
        logger.info("Received status check request for orderId:{}", orderId);
        try {
            LoanPaymentOrder order = loanPaymentOrderDao.findByOrderId(orderId);
            if (order == null || !order.getMerchantId().equals(merchant.getId())) {
                logger.info("No order found for orderId:{}", orderId);
                return new PaymentStatusResponseDTO(false, "Order not found");
            }
            return new PaymentStatusResponseDTO(order.getStatus(), orderId, order.getAmount(), order.getBankRefNo(), order.getUpdatedAt());
        } catch (Exception e) {
            logger.error("Exception in payment status check", e);
            return new PaymentStatusResponseDTO(false, "Something went wrong");
        }
    }

    public PaymentStatusResponseDTO getStatusV2(String orderId, BasicDetailsDto merchant) {
        logger.info("Received status check request for orderId:{}", orderId);
        try {
            LoanPaymentOrder order = loanPaymentOrderDao.findByOrderId(orderId);
            if (order == null || !order.getMerchantId().equals(merchant.getId())) {
                logger.info("No order found for orderId:{}", orderId);
                return new PaymentStatusResponseDTO(false, "Order not found");
            }
            Optional<LendingPaymentSchedule> activeLoan = lendingPaymentScheduleDao.findById(order.getOwnerId());
            Lender lender = Lender.valueOf(activeLoan.get().getNbfc());
            if("PENDING".equalsIgnoreCase(order.getStatus())) {
                logger.info("pg status check for merchant id {} and order id {}", order.getMerchantId(), order.getOrderId());
                PgStatusResponse response = apiGatewayService.checkPgStatus(order.getOrderId(), lender, order.getMerchantId());
                if (response != null && response.getStatusCode() != null && "200".equalsIgnoreCase(response.getStatusCode()) && Objects.nonNull(response.getData()) && "SUCCESS".equalsIgnoreCase(response.getData().getPaymentStatus())) {
                    logger.info("Pg txn Status SUCCESS for orderId:{}", order.getOrderId());
                    handlePgCallback(response.getData());
                    order = loanPaymentOrderDao.findByOrderId(orderId);
                } else if (response != null && response.getStatusCode() != null && "200".equalsIgnoreCase(response.getStatusCode()) && Objects.nonNull(response.getData()) && (Status.TransactionStatus.FAILED.name().equalsIgnoreCase(response.getData().getPaymentStatus()) || Status.TransactionStatus.CANCELLED.name().equalsIgnoreCase(response.getData().getPaymentStatus()))) {
                    order.setStatus(response.getData().getPaymentStatus());
                    loanPaymentOrderDao.save(order);
                    logger.info("Pg txn Status FAILED/CANCELLED for orderId:{}", order.getOrderId());
                }
            }

            return new PaymentStatusResponseDTO(order.getStatus(), orderId, order.getAmount(), order.getBankRefNo(), order.getUpdatedAt());
        } catch (Exception e) {
            logger.error("Exception in payment status check", e);
            return new PaymentStatusResponseDTO(false, "Something went wrong");
        }
    }

    public ResponseDTO getPaymentModes(RequestDTO<CreditSpendRequestDTO> requestDTO, String token) {
        if (requestDTO.getPayload().getAmount() > 100000) {
            ResponseDTO responseDTO = new ResponseDTO();
            List<PaymentDetailDto> paymentDetails = new ArrayList<PaymentDetailDto>(){{add(getBankTransferMode());}};
            responseDTO.setSuccess(true);
            responseDTO.setData(paymentDetails);
            return responseDTO;
        }
        List<PaymentDetailDto> paymentDetails = apiGatewayService.getPaymentModes(requestDTO, token);
        for (PaymentDetailDto paymentDetail : paymentDetails) {
            if (paymentDetail.getPsps() != null && !paymentDetail.getPsps().isEmpty()) {
                paymentDetail.getPsps().removeIf(psps -> psps.equalsIgnoreCase("com.phonepe.app"));
            }
        }
        paymentDetails.add(getBankTransferMode());
        paymentDetails.add(getGPAYMode());
        paymentDetails.add(getPhonePeMode());
        ResponseDTO responseDTO = new ResponseDTO();
        paymentDetails.removeIf(paymentDetailDto -> (paymentDetailDto.getBalance() != null && paymentDetailDto.getBalance() < requestDTO.getPayload().getAmount()));
        if (paymentDetails.isEmpty()) {
            responseDTO.setSuccess(false);
            responseDTO.setMessage("No Payment Mode Found");
        } else {
            responseDTO.setSuccess(true);
            responseDTO.setData(paymentDetails);
        }
        return responseDTO;
    }

    private PaymentDetailDto getBankTransferMode() {
        PaymentDetailDto paymentDetailDto = new PaymentDetailDto();
        paymentDetailDto.setName("Pay by Account Transfer");
        paymentDetailDto.setType("BT");
        paymentDetailDto.setFundSource("BT");
        paymentDetailDto.setAuthRequired(false);
        paymentDetailDto.setEnable(true);
        paymentDetailDto.setInitiate_sb(false);
        paymentDetailDto.setDefault(false);
        return paymentDetailDto;
    }

    private PaymentDetailDto getGPAYMode() {
        PaymentDetailDto paymentDetailDto = new PaymentDetailDto();
        paymentDetailDto.setName("Pay Using Google Pay");
        paymentDetailDto.setType("UPI");
        paymentDetailDto.setFundSource("UPI");
        paymentDetailDto.setAmountLimit(100000D);
        paymentDetailDto.setAuthRequired(false);
        paymentDetailDto.setEnable(true);
        paymentDetailDto.setInitiate_sb(false);
        paymentDetailDto.setDefault(false);
        return paymentDetailDto;
    }

    private PaymentDetailDto getPhonePeMode() {
        PaymentDetailDto paymentDetailDto = new PaymentDetailDto();
        paymentDetailDto.setName("Pay Using PhonePe");
        paymentDetailDto.setType("UPI");
        paymentDetailDto.setFundSource("UPI");
        paymentDetailDto.setAmountLimit(100000D);
        paymentDetailDto.setAuthRequired(false);
        paymentDetailDto.setEnable(true);
        paymentDetailDto.setInitiate_sb(false);
        paymentDetailDto.setDefault(false);
        return paymentDetailDto;
    }

    public ResponseDTO resendOTP(RequestDTO<PaymentResendOTP> requestDTO, BasicDetailsDto merchant, String token) {
        LoanPaymentOrder loanPaymentOrder = loanPaymentOrderDao.findByOrderId(requestDTO.getPayload().getOrderId());
        if (loanPaymentOrder == null) {
            return new ResponseDTO(false, "Order not found");
        }
        Map<String, Object> result = apiGatewayService.sendOTP(requestDTO, token);
        Boolean success = (Boolean) result.get("success");
        if (success) {
            return new ResponseDTO(true, "success");
        }
        return new ResponseDTO(false, "Unable to resend otp");
    }

    private void adjustLoanBalance(LendingPaymentSchedule activeLoan, Double amount, String bankRefNo, String source,
                                   boolean advanceEdi, String transferType, String terminalOrderId, Long orderId, boolean foreClosure) {
        logger.info("Adjusting Balance for loanId:{} and amount:{} and advanceEdi:{}", activeLoan.getId(), amount, advanceEdi);

        if (!ObjectUtils.isEmpty(activeLoan) && "1LMS".equalsIgnoreCase(activeLoan.getLmsSource())) {
            logger.info("OneLms# started the settlement of order : {} loanId :{}", orderId, activeLoan.getId());
            paymentAsynchronousService.postPaymentDetails(activeLoan, amount, source, terminalOrderId, orderId, foreClosure);
            return;
        }

        Integer principalDueAmount = loanUtil.getForeclosureAmount(activeLoan);
        List<String> waiverList = Arrays.asList(WaiverType.EXCEPTION.name(), WaiverType.DECEASED_SCHEME.name(), WaiverType.SCHEME1.name(), WaiverType.SCHEME.name());
        if ("UPI_AUTOPAY".equals(source) || "LMS_PRECLOSURE".equals(source)) {
            transferType="EXTERNAL";
        }

        if (loanPaymentUtil.checkIfNewSettlementAllowed(activeLoan.getCreatedAt())  && !(Objects.nonNull(source) && waiverList.contains(source)) ) {
            log.info("NewSettlement# started the settlement of order : {} loanId :{}", orderId, activeLoan.getId());
            if("BHARATPE_NACH".equals(source) && transferType == null && !loanUtil.isNachToBeRefunded(activeLoan.getLoanApplication())) {
                    transferType = "EXTERNAL";
            }

            if (source == null) {
                source = "UPI";
                transferType = "EXTERNAL";
            }

            if (transferType == null) {
                logger.info("Setting transfer type as EXTERNAL for loanId: {}", activeLoan.getId());
                transferType = "EXTERNAL";
            }

            if("PIRAMAL".equalsIgnoreCase(activeLoan.getNbfc())) imposePenalCharges(orderId,activeLoan);
            loanPaymentService.adjustMoney(activeLoan, LoanPaymentDetailDTO.builder()
                    .adjustExcessNach(false)
                    .otherAmount(LoanPaymentUtil.roundOffAmountIfRequired(activeLoan.getNbfc(), amount))
                    .orderId(orderId)
                    .description(getDescription(bankRefNo, false, false))
                    .source(StringUtils.hasLength(source) ? source : "UPI")
                    .transferType("EXTERNAL".equalsIgnoreCase(transferType) ? CollectionTransferTypeEnum.DIRECT_TRANSFER_LENDER.name() : CollectionTransferTypeEnum.TRANSFER_BY_BP.name())
                    .bankRefNo(bankRefNo)
                    .terminalOrderId(terminalOrderId)
                    .foreCloser(foreClosure)
                    .updateGlobalTxnlimit(true)
                    .build());

//            if (activeLoan.getLoanApplication() != null && activeLoan.getLoanApplication().getProcessingFee() != null && activeLoan.getLoanApplication().getProcessingFee() > 0) {
//                redisNotificationService.sendRepaymentNudge(activeLoan.getMerchantId(), activeLoan.getLoanApplication().getProcessingFee());
//            }
            double finalAmount = amount;
            // Todo: fix when opening  for roll out
            notificationExecutor.execute(() -> sendSMS(activeLoan, finalAmount, false));
            lendingCollectionAuditService.sendReceiptPosting(activeLoan.getId(), activeLoan.getNbfc());
            log.info("NewSettlement# completed the settlement of order : {} loanId :{}", orderId, activeLoan.getId());
            return;
        }


        LoanForeClosureCharges loanForeClosureCharges = loanForeClosureChargesDao.findByOrderId(orderId);
        boolean preclosureWithCharges = false;
        double foreclosureChargesAmount = 0.0;
        if(loanForeClosureCharges != null) {
            if (loanForeClosureCharges.getTax() == null) loanForeClosureCharges.setTax(0.0);
            foreclosureChargesAmount = loanForeClosureCharges.getAmount() + loanForeClosureCharges.getTax();
            preclosureWithCharges = true;
            logger.info("foreclosure charges exist for the orderId {} and charges : {} amount : {}",orderId, loanForeClosureCharges, foreclosureChargesAmount);
        }

        if (EDI_BY_EDI.name().equalsIgnoreCase(activeLoan.getSettlementMechanism())) {
            logger.info("Adjusting Mechanism for loanId: {} is {}", activeLoan.getId(), activeLoan.getSettlementMechanism());
            adjustLoanBalanceEdiByEdi(activeLoan, amount, bankRefNo, source, transferType, terminalOrderId, orderId, foreclosureChargesAmount, loanForeClosureCharges);
            return;
        }

        LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(activeLoan.getMerchantId(), activeLoan.getId());
        double advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ? lendingPrepayment.getAdvanceEdiAmount() : 0d;
        Integer ediHolidayInterestAmount = getEDIHolidayInterestAmount(activeLoan);
        List<LendingCollectionExcess> lendingCollectionExcessList = lendingCollectionExcessDao.findByMerchantIdAndLoanIdAndStatusOrderByIdAsc(activeLoan.getMerchantId(), activeLoan.getId(), "ACTIVE");
        Double excessCollectionBalance = 0D;
        for(LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList){
            if(lendingCollectionExcess.getAmount() > 0){
                excessCollectionBalance += lendingCollectionExcess.getAmount();
            }
        }

        Double paidInterestAmount = 0D;
        Double paidPrincipalAmount = 0D;
        boolean preclosure = false;
        boolean advanceAdjusted = false;

        boolean excessCollectionAdjusted = false;
        double penaltyFee = 0;
        boolean loanStatusFlag = false;

        logger.info("Preclosure amount for loanId:{} is:{}", activeLoan.getId(), (principalDueAmount + ediHolidayInterestAmount));
        logger.info("Advance EDI amount for loanId:{} is:{}", activeLoan.getId(), advanceEdiAmount);
        logger.info("Excess collection balance for loanId:{} is:{}", activeLoan.getId(), excessCollectionBalance);
        logger.info("Due amount for loanId:{} is due amount:{} due principle:{} due interest:{}", activeLoan.getId(), activeLoan.getDueAmount(), activeLoan.getDuePrinciple(), activeLoan.getDueInterest());

        if (Objects.nonNull(source) && waiverList.contains(source) &&
                (activeLoan.getNbfc().equalsIgnoreCase(Lender.ABFL.name()) || activeLoan.getNbfc().equalsIgnoreCase(Lender.PIRAMAL.name()))) {
            waiverSettlement(activeLoan, amount, bankRefNo, source, transferType, terminalOrderId, excessCollectionBalance, lendingCollectionExcessList);
            return;
        }

        String requestId = UUID.randomUUID().toString().replaceAll("-", "");
        Boolean postChargesToLender = false;
        Integer pendingNachCharges = nachBounceChargesService.getNachCharges(activeLoan).intValue();
        if(principalDueAmount + ediHolidayInterestAmount + pendingNachCharges - amount <= 1D) {
            logger.info("Received pre closure amount:{} for loan:{}", amount, activeLoan.getId());
            penaltyFee = Objects.nonNull(activeLoan.getDuePenalty()) ? activeLoan.getDuePenalty() : 0;
            if(pendingNachCharges != 0d){
                log.info("Found Nach Bounce Charges for loan: {}, nbfc: {} ", activeLoan.getId(), activeLoan.getNbfc());
                nachBounceChargesService.createCharges(activeLoan, requestId);
                penaltyFee += pendingNachCharges;
                postChargesToLender = true;
            }
            paidInterestAmount = (activeLoan.getDueInterest() != null ? activeLoan.getDueInterest() : 0) + ediHolidayInterestAmount;
            paidPrincipalAmount = amount - paidInterestAmount + advanceEdiAmount + excessCollectionBalance - penaltyFee - foreclosureChargesAmount;
            double extraPrinciple = (activeLoan.getPaidPrinciple() + paidPrincipalAmount) - activeLoan.getLoanAmount();
            if (extraPrinciple > 0) {
                logger.info("Extra principle received for loanId:{} and extra amount:{}", activeLoan.getId(), extraPrinciple);
                paidPrincipalAmount -= extraPrinciple;
                paidInterestAmount += extraPrinciple;
            }
            logger.info("Adjusted breakup amount for loan:{} is principle:{} and interest:{} and penalty: {} and foreclosureCharges : {}", activeLoan.getId(),
                    paidPrincipalAmount, paidInterestAmount, penaltyFee, foreclosureChargesAmount);
            String description = (preclosureWithCharges) ? "PREPAYMENT_WITH_CHARGES" : "PREPAYMENT";
            if(activeLoan.getDueAmount() >= 0) {
                createLendingLedger(activeLoan, -1 * Math.abs(amount - activeLoan.getDueAmount() + advanceEdiAmount + excessCollectionBalance) ,
                        -1 * Math.abs(amount - activeLoan.getDueAmount() - ediHolidayInterestAmount + advanceEdiAmount + excessCollectionBalance - penaltyFee - foreclosureChargesAmount),
                        Double.valueOf(ediHolidayInterestAmount), description, source, transferType, terminalOrderId, -1*penaltyFee, -1*foreclosureChargesAmount);
            } else {
                createLendingLedger(activeLoan, -1 * (amount + advanceEdiAmount + excessCollectionBalance),
                        -1 * (amount - ediHolidayInterestAmount + advanceEdiAmount + excessCollectionBalance - penaltyFee - foreclosureChargesAmount),
                        Double.valueOf(ediHolidayInterestAmount), description, source, transferType, terminalOrderId, -1*penaltyFee, -1*foreclosureChargesAmount);
            }

            activeLoan.setPaidAmount(activeLoan.getPaidAmount() + amount + advanceEdiAmount + excessCollectionBalance);
            activeLoan.setPaidInterest((activeLoan.getPaidInterest() != null ? activeLoan.getPaidInterest() : 0) + paidInterestAmount);
            activeLoan.setPaidPrinciple((activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0) + paidPrincipalAmount);
            activeLoan.setPaidPenalty((activeLoan.getPaidPenalty() != null ? activeLoan.getPaidPenalty() : 0) + penaltyFee);

            activeLoan.setDueAmount(0D);
            activeLoan.setDueInterest(0D);
            activeLoan.setDuePrinciple(0D);
            activeLoan.setDuePenalty(0D);

            activeLoan.setStatus("CLOSED");
            activeLoan.setClosingDate(new Date());
            preclosure = true;
            if(excessCollectionBalance > 0D)excessCollectionAdjusted = true;
            if (lendingPrepayment != null && advanceEdiAmount > 0d) {
                advanceAdjusted = true;
                lendingPrepayment.setAdvanceEdiCount(0);
                lendingPrepayment.setAdvanceEdiAmount(0D);
                lendingPrepaymentDao.save(lendingPrepayment);
            }
            loanStatusFlag=true;
            log.info("setting loan flag as true at AdjustLoanBalance for merchantId :{}",activeLoan.getMerchantId());
        }
        else {
            double balance=amount;
            if(balance>0D && activeLoan.getDueOtherCharges()!=null && activeLoan.getDueOtherCharges()>0D) {
                Double paidAmount=balance>=activeLoan.getDueOtherCharges()?activeLoan.getDueOtherCharges():balance;
                activeLoan.setDueOtherCharges(activeLoan.getDueOtherCharges()-paidAmount);
                activeLoan.setDueAmount(activeLoan.getDueAmount()-paidAmount);
                activeLoan.setPaidAmount(activeLoan.getPaidAmount()+paidAmount);
                activeLoan.setPaidOtherCharges(activeLoan.getPaidOtherCharges()+paidAmount);
                balance-=paidAmount;
                logger.info("Adjusted due other charges of amount:{} for loan:{}", paidAmount, activeLoan.getId());
            }

            if(balance>0D && activeLoan.getDueInterest()!=null && activeLoan.getDueInterest()>0D) {
                Double paidAmount=balance>=activeLoan.getDueInterest()?activeLoan.getDueInterest():balance;
                activeLoan.setDueInterest(activeLoan.getDueInterest()-paidAmount);
                activeLoan.setDueAmount(activeLoan.getDueAmount()-paidAmount);
                activeLoan.setPaidInterest((activeLoan.getPaidInterest() != null ? activeLoan.getPaidInterest() : 0)+paidAmount);
                activeLoan.setPaidAmount(activeLoan.getPaidAmount()+paidAmount);
                paidInterestAmount+=paidAmount;
                balance-=paidAmount;
                logger.info("Adjusted due interest of amount:{} for loan:{}", paidAmount, activeLoan.getId());
            }
            if(balance>0D && activeLoan.getDuePrinciple()!=null && activeLoan.getDuePrinciple()>0D) {
                Double paidAmount=balance>=activeLoan.getDuePrinciple()?activeLoan.getDuePrinciple():balance;
                activeLoan.setDuePrinciple(activeLoan.getDuePrinciple()-paidAmount);
                activeLoan.setDueAmount(activeLoan.getDueAmount()-paidAmount);
                activeLoan.setPaidPrinciple((activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0)+paidAmount);
                activeLoan.setPaidAmount(activeLoan.getPaidAmount()+paidAmount);
                paidPrincipalAmount+=paidAmount;
                balance-=paidAmount;
                logger.info("Adjusted due principle of amount:{} for loan:{}", paidAmount, activeLoan.getId());
            }
            if(balance>0D && activeLoan.getDuePenalty()!=null && activeLoan.getDuePenalty()>0D) {
                Double paidAmount=balance>=activeLoan.getDuePenalty()?activeLoan.getDuePenalty():balance;
                penaltyFee = paidAmount;
                activeLoan.setDuePenalty(activeLoan.getDuePenalty()-paidAmount);
                activeLoan.setPaidPenalty((Objects.nonNull(activeLoan.getPaidPenalty()) ? activeLoan.getPaidPenalty() : 0d) + paidAmount);
                balance-=paidAmount;
                logger.info("Adjusted due penalty of amount:{} for loan:{}", paidAmount, activeLoan.getId());
            }

            advanceAdjusted = adjustAdvanceEdi(activeLoan, balance, advanceEdi);
            if(balance > 0D && !advanceAdjusted) {
                logger.info("Adjusting extra amount:{} for loan:{}", balance, activeLoan.getId());
                int adjustedEdiCount = 0;
                int adjustedIOEdiCount = 0;
                double extraAmount = 0d;
                double principle = 0d;
                double interest = 0d;
                List<LendingAdjustedEDISchedule> lendingAdjustedEDISchedules = lendingAdjustedEDIScheduleDao.findByLoanId(activeLoan.getId());
                if (!lendingAdjustedEDISchedules.isEmpty()) {
                    logger.info("Found adjusted edi schedule for loan:{}", activeLoan.getId());
                    lendingAdjustedEDISchedules.sort(Comparator.comparing(LendingAdjustedEDISchedule::getInstallmentNumber));
                    int ediPaidCount = lendingAdjustedEDISchedules.size() - activeLoan.getEdiRemainingCount();
                    logger.info("Edi Paid count:{} for loan:{}", ediPaidCount, activeLoan.getId());
                    for (LendingAdjustedEDISchedule ediSchedule : lendingAdjustedEDISchedules) {
                        if (balance <= 0d) {
                            break;
                        }
                        if (ediSchedule.getInstallmentNumber() <= ediPaidCount) {
                            continue;
                        }
                        if (balance >= ediSchedule.getTotalEdi()) {
                            balance -= ediSchedule.getTotalEdi();
                            principle += ediSchedule.getPrinciple();
                            interest += ediSchedule.getInterest();
                            adjustedEdiCount++;
                        } else if (balance <= ediSchedule.getInterest()) {
                            interest += balance;
                            extraAmount += balance;
                            balance = 0d;
                        } else {
                            interest += ediSchedule.getInterest();
                            principle += balance - ediSchedule.getInterest();
                            extraAmount += balance;
                            balance = 0d;
                        }
                    }
                } else {
                    List<LendingEDISchedule> ediSchedules = lendingEDIScheduleDao.findByLendingPaymentSchedule(activeLoan);
                    ediSchedules.sort(Comparator.comparing(LendingEDISchedule::getInstallmentNumber));
                    int ediPaidCount = activeLoan.getEdiCount() - activeLoan.getEdiRemainingCount();
                    if (activeLoan.getInterestOnlyEdiCount() != null && activeLoan.getInterestOnlyEdiCount() > 0 && activeLoan.getRemainingInterestOnlyEdiCount() != null) {
                        ediPaidCount = (activeLoan.getInterestOnlyEdiCount() + activeLoan.getEdiCount()) - (activeLoan.getRemainingInterestOnlyEdiCount() + activeLoan.getEdiRemainingCount());
                    }
                    logger.info("Edi Paid count:{} for loan:{}", ediPaidCount, activeLoan.getId());
                    for (LendingEDISchedule ediSchedule : ediSchedules) {
                        if (balance <= 0d) {
                            break;
                        }
                        if (ediSchedule.getInstallmentNumber() <= ediPaidCount) {
                            continue;
                        }
                        if (balance >= ediSchedule.getTotalEdi()) {
                            logger.info("Adjusting full installment:{} for loanId:{}", ediSchedule.getInstallmentNumber(), activeLoan.getId());
                            balance -= ediSchedule.getTotalEdi();
                            principle += ediSchedule.getPrinciple();
                            interest += ediSchedule.getInterest();
                            if (ediSchedule.getEdiType() != null && ediSchedule.getEdiType().equalsIgnoreCase("Principal Morat")) {
                                adjustedIOEdiCount++;
                            } else {
                                adjustedEdiCount++;
                            }
                        } else if (balance <= ediSchedule.getInterest()) {
                            logger.info("Adjusting interest:{} for installment:{} for loanId:{}", balance, ediSchedule.getInstallmentNumber(), activeLoan.getId());
                            interest += balance;
                            extraAmount += balance;
                            balance = 0d;
                        } else {
                            logger.info("Adjusting interest:{} and principle:{} for installment:{} for loanId:{}", ediSchedule.getInterest(), (balance - ediSchedule.getInterest()), ediSchedule.getInstallmentNumber(), activeLoan.getId());
                            interest += ediSchedule.getInterest();
                            principle += balance - ediSchedule.getInterest();
                            extraAmount += balance;
                            balance = 0d;
                        }
                    }
                }
                logger.info("Adjusted principle:{} interest:{} extra amount:{} adjustedEdiCount:{} adjustedIOEdiCount:{} for loan:{}", principle, interest, extraAmount, adjustedEdiCount, adjustedIOEdiCount, activeLoan.getId());
                paidPrincipalAmount += principle;
                paidInterestAmount += interest;

                if (amount > (paidPrincipalAmount + paidInterestAmount + penaltyFee)) {
                    double remainingAmount = amount - (paidPrincipalAmount + paidInterestAmount + penaltyFee);
                    logger.info("Balance remaining:{} for loan:{} after adjustment, adjusting this in principle", remainingAmount, activeLoan.getId());
                    principle += remainingAmount;
                    paidPrincipalAmount += remainingAmount;
                }
                if (activeLoan.getRemainingInterestOnlyEdiCount() != null && adjustedIOEdiCount > 0) {
                    activeLoan.setRemainingInterestOnlyEdiCount(activeLoan.getRemainingInterestOnlyEdiCount() - adjustedIOEdiCount);
                }
                activeLoan.setEdiRemainingCount(activeLoan.getEdiRemainingCount() - adjustedEdiCount);
                activeLoan.setAdjustedPaidAmount(activeLoan.getAdjustedPaidAmount() != null ? activeLoan.getAdjustedPaidAmount() + extraAmount : extraAmount);
                activeLoan.setPaidAmount(activeLoan.getPaidAmount() + principle + interest);
                activeLoan.setPaidPrinciple(activeLoan.getPaidPrinciple() + principle);
                activeLoan.setPaidInterest(activeLoan.getPaidInterest() + interest);
                createLendingLedger(activeLoan, -1*(principle + interest), -1*principle, -1*interest,
                        "PREPAYMENT", source, transferType, terminalOrderId, 0d);
                int extraEdiCount = activeLoan.getAdjustedPaidAmount() != null ? (int) (activeLoan.getAdjustedPaidAmount()/activeLoan.getEdiAmount()) : 0;
                if (extraEdiCount > 0) {
                    activeLoan.setEdiRemainingCount(activeLoan.getEdiRemainingCount() - extraEdiCount);
                    activeLoan.setAdjustedPaidAmount(activeLoan.getAdjustedPaidAmount() % activeLoan.getEdiAmount());
                }
                if (activeLoan.getEdiRemainingCount() == 0 && activeLoan.getAdjustedDueAmount() != null && activeLoan.getAdjustedDueAmount() > 0D) {
                    int newScheduleCount = createAdjustedSchedule(activeLoan, activeLoan.getAdjustedDueAmount());
                    activeLoan.setEdiRemainingCount(activeLoan.getEdiRemainingCount() + newScheduleCount);
                    activeLoan.setAdjustedDueAmount(0D);
                }
            }
        }
        if (penaltyFee > 0.5) {
            PenaltyFeeLedger penaltyFeeLedger = new PenaltyFeeLedger(activeLoan.getMerchantId(), activeLoan.getId(), penaltyFee, source, false, activeLoan.getNbfc());
            penaltyFeeLedgerDao.save(penaltyFeeLedger);
            loanUtil.savePenalCharges(activeLoan, penaltyFee);
        }
        if (advanceAdjusted) {
            logger.info("Reducing adjusted amount due to advance EDI for loanId:{}, old amount:{}, new amount:{}", activeLoan.getId(), amount, (paidPrincipalAmount + paidInterestAmount));
            amount = (paidPrincipalAmount + paidInterestAmount);
        }
        logger.info("Adjusted breakup amount for loan:{} is principle:{} and interest:{}", activeLoan.getId(), paidPrincipalAmount, paidInterestAmount);

        LendingLedger lendingLedger = createLendingLedger(activeLoan, amount, excessCollectionAdjusted ? paidPrincipalAmount - excessCollectionBalance : paidPrincipalAmount, paidInterestAmount,  getDescription(bankRefNo,
                preclosure, preclosureWithCharges), source, transferType, terminalOrderId, penaltyFee, foreclosureChargesAmount);
        if(excessCollectionAdjusted){
            logger.info("Adjusting excess collection for loan in ledger : {}, amount : {}", activeLoan.getId(), excessCollectionBalance);
            createLendingLedgerForExcessCollectionOnForeclosure(activeLoan, lendingCollectionExcessList);
            settleExcessCollectionBalance(activeLoan.getId(), lendingCollectionExcessList);
        }

        if(loanForeClosureCharges != null && lendingLedger != null) {
            loanForeClosureCharges.setLedgerId(lendingLedger.getId());
            loanForeClosureChargesDao.save(loanForeClosureCharges);
        }
        ForeClosureAmountInfo foreClosureAmountInfo = foreClosureAmountInfoDao.findByOrderId(orderId);
        if(foreClosureAmountInfo!= null && lendingLedger != null) {
            try {
                foreClosureAmountInfo.setLedgerId(lendingLedger.getId());
                foreClosureAmountInfoDao.save(foreClosureAmountInfo);
            }catch (Exception e){
                log.error("error occured while saving ledgerId for loanID {} in foreclosure amount info",activeLoan.getId());
            }
        }
        lendingPaymentScheduleDao.save(activeLoan);

        if (activeLoan.getStatus().equalsIgnoreCase(Status.LendingStatus.CLOSED.toString())) {
            if ("LDC".equals(activeLoan.getNbfc())) {
                nbfcService.pushCloseLoanEventToKafka(activeLoan.getApplicationId());
            }
        }

//        if (activeLoan.getLoanApplication() != null && activeLoan.getLoanApplication().getProcessingFee() != null && activeLoan.getLoanApplication().getProcessingFee() > 0) {
//            redisNotificationService.sendRepaymentNudge(activeLoan.getMerchantId(), activeLoan.getLoanApplication().getProcessingFee());
//        }
        boolean isLoanClosed = "CLOSED".equalsIgnoreCase(activeLoan.getStatus());

        Double finalAmount = amount;
        notificationExecutor.execute(() -> sendSMS(activeLoan, finalAmount, isLoanClosed));
        logger.info("going to post charges for loanId {} and nbfc {}", activeLoan.getId(), activeLoan.getNbfc());
        if (isLoanClosed && preclosure && !ObjectUtils.isEmpty(lendingLedger)) {
            if (activeLoan.getNbfc().equalsIgnoreCase(Lender.ABFL.name())) {
                sendForeclosureEvent(activeLoan.getApplicationId(), activeLoan.getMobile(), lendingLedger, orderId);
            }
            else if (activeLoan.getNbfc().equalsIgnoreCase(Lender.PIRAMAL.name())) {
                loanClosurePostingService.postForeclosureReceiptPiramal(activeLoan, lendingLedger, LoanClosureDTO.builder().foreclosureCharges(foreclosureChargesAmount).postCharges(postChargesToLender).build());
            }
            else if (Arrays.asList("USFB", "CAPRI", "CREDITSAISON", Lender.UGRO.name(), Lender.MUTHOOT.name()).contains(activeLoan.getNbfc())) {
                postForeclosureReceipt(activeLoan, lendingLedger);
            } else if (Lender.TRILLIONLOANS.name().equalsIgnoreCase(activeLoan.getNbfc())){
                sendForeclosureEventTrillionLoans(activeLoan.getApplicationId(), lendingLedger, orderId);
            } else if (Lender.PAYU.name().equalsIgnoreCase(activeLoan.getNbfc())){
                loanClosurePostingService.sendForeclosureEventPayu(activeLoan.getApplicationId(), lendingLedger, orderId,postChargesToLender,requestId);
            }else if (Lender.LIQUILOANS_P2P.name().equalsIgnoreCase(activeLoan.getNbfc())
                      || Lender.LIQUILOANS_P2P_OF.name().equalsIgnoreCase(activeLoan.getNbfc())
                      || Lender.LIQUILOANS_NBFC.name().equalsIgnoreCase(activeLoan.getNbfc())){
                sendForeclosureChargesEventLiquiLoans(activeLoan.getApplicationId(), activeLoan.getId(), lendingLedger.getId(), activeLoan.getNbfc(), orderId);
            }
        }

        if(loanStatusFlag)
        {
            Long merchantId = activeLoan.getMerchantId();
            log.info("sending loan flag event in adjustLoanBalance for merchantId : {}",merchantId);
            sherlocLoanStatusChangeService.pushLoanStatusChangeEventToKafka(merchantId, activeLoan.getStatus());
        }

//		if(isLoanClosed) {
//			List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());
//			notificationExecutor.execute(() -> {
//				LoyaltyServiceRequest requestBean = new LoyaltyServiceRequest.LoyaltyServiceRequestBuilder(activeLoan.getMerchantId(), LoyaltyTransactionType.PRE_LOAN_CLOSURE)
//						.amount(finalAmount)
//						.merchantStoreId(null)
//						.transactionId(activeLoan.getId())
//						.build();
//				loyaltyService.pushToKafka(requestBean);
//				if(topupLoans.contains(activeLoan.getLoanApplication().getLoanType())){
//					LendingPaymentSchedule topupLoan = lendingPaymentScheduleDao.findTopupLoan(activeLoan.getMerchantId());
//					if(topupLoan != null) {
//						refundProcessingFee(topupLoan);
//					}
//				}else{
//					refundProcessingFee(activeLoan);
//				}
//				if (activeLoan.getDueAmount() < 0) {
//					logger.info("Extra amount:{} received for loanId:{}, initiating refund", activeLoan.getDueAmount(), activeLoan.getId());
//					refundExtraAmount(activeLoan);
//				}
//			});
//		}
    }

    private void sendForeclosureChargesEventLiquiLoans(long applicationId,long loanId, long lendingLedgerId, String lender, long orderId) {
        String postingStatus = "FAILURE";
        LoanForeClosureCharges loanForeClosureCharges = loanForeClosureChargesDao.findByOrderId(orderId);
        if (loanForeClosureCharges == null ) {
            logger.info("No fore closure charges exist for the orderId {}",orderId);
            return;
        }
        try{
            LiquiLoansForeclosureChargesRequestDto liquiLoansForeclosureChargesRequestDto = LiquiLoansForeclosureChargesRequestDto.builder()
                    .loanId(loanId)
                    .applicationId(applicationId)
                    .lender(lender)
                    .chargeDate(loanForeClosureCharges.getCreatedAt())
                    //.chargeDate(new Date())
                    .chargeAmount(loanForeClosureCharges.getAmount()+loanForeClosureCharges.getTax())
                    .chargeId(String.valueOf(loanForeClosureCharges.getId()))
                    .chargeType(8)  // defined by lender
                    .build();
            logger.info(" {}  foreclosure charges event Sending {}",lender, liquiLoansForeclosureChargesRequestDto);
            Object metadata = confluentKafkaTemplate.send(nbfcLiquiLoansForeclosureTopic, objectMapper.writeValueAsString(liquiLoansForeclosureChargesRequestDto)).get();
            logger.info(" {}  foreclosure charges event sent {}",lender, objectMapper.writeValueAsString(liquiLoansForeclosureChargesRequestDto));
            postingStatus = "POSTED";
        } catch (Exception e) {
            logger.error("error occurred while sending foreclosure event {}", e.getMessage());
        }
        loanForeClosureCharges.setChargePostingStatus(postingStatus);
        loanForeClosureChargesDao.save(loanForeClosureCharges);
    }

    public void postForeclosureReceipt(LendingPaymentSchedule activeLoan, LendingLedger lendingLedger) {
        try {
            logger.info("inside the post foreclosure of {} for {}", activeLoan.getNbfc(), activeLoan.getApplicationId());
            NBFCRequestDTO nbfcRequest = associationServiceUtil.foreclosureReceiptRequest(activeLoan.getNbfc(), activeLoan.getApplicationId(), lendingLedger, null);
            if(ObjectUtils.isEmpty(nbfcRequest)) {
                log.info("Error in generating request for foreclosure receipt of {} for {}", activeLoan.getNbfc(), activeLoan.getApplicationId());
                return;
            }
            logger.info("foreclosure receipt request for {} {}", activeLoan.getNbfc(), nbfcRequest);
            LendingCollectionAudit lendingCollectionAudit = lendingCollectionAuditDao.findByLedgerID(lendingLedger.getId(),1);
            lendingCollectionAudit.setStatus("SUCCESS");
            lendingCollectionAuditDao.save(lendingCollectionAudit);
            confluentKafkaTemplate.send(getLenderForeclsoureReceiptTopic(activeLoan.getNbfc()), objectMapper.readValue(objectMapper.writeValueAsString(nbfcRequest), new TypeReference<Map<String, Object>>() {}));
            log.info("foreclosure event sent for application {} {}", activeLoan.getApplicationId(), nbfcRequest);
        } catch (Exception e){
            logger.error("Exception {} while posting the foreclosure receipt for application id {} {}",e.getMessage(),activeLoan.getApplicationId(), e);
        }
    }

    private String getLenderForeclsoureReceiptTopic(String lender) {
        switch (lender) {
            case "USFB":
                return nbfcUsfbForeclosureTopic;
            case "CAPRI":
                return nbfcCapriForeclosureTopic;
            case "PAYU":
                return nbfcPayuForeclosureTopic;
            case "CREDITSAISON":
                return csConfig.getNbfcCreditsaisonForeclosureTopic();
            case "UGRO":
                return ugroConfig.getForeclosureTopic();
            case "MUTHOOT":
                return nbfcMuthootForeclosureTopic;
            default:
                return null;
        }
    }


    private boolean adjustAdvanceEdi(LendingPaymentSchedule activeLoan, double balance, boolean advanceEdi) {
        if (advanceEdi && balance > 0D) {
            logger.info("Adjusting balance:{} as advance edi for loanId:{}", balance, activeLoan.getId());
            int advanceEdiCount = 0;
            if (balance % activeLoan.getEdiAmount() == 0) {
                advanceEdiCount = (int)(balance/activeLoan.getEdiAmount());
            } else if (Math.ceil(balance) % activeLoan.getEdiAmount() == 0) {
                advanceEdiCount = (int)(Math.ceil(balance)/activeLoan.getEdiAmount());
                balance = Math.ceil(balance);
            } else if (Math.floor(balance) % activeLoan.getEdiAmount() == 0) {
                advanceEdiCount = (int)(Math.floor(balance)/activeLoan.getEdiAmount());
                balance = Math.floor(balance);
            }
            if (advanceEdiCount > 0) {
                LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(activeLoan.getMerchantId(), activeLoan.getId());
                if (lendingPrepayment != null) {
                    lendingPrepayment.setAdvanceEdiAmount(lendingPrepayment.getAdvanceEdiAmount() + balance);
                    lendingPrepayment.setAdvanceEdiCount(lendingPrepayment.getAdvanceEdiCount() + advanceEdiCount);
                } else {
                    lendingPrepayment = new LendingPrepayment(activeLoan.getMerchantId(), activeLoan.getId(), balance, advanceEdiCount);
                }
                lendingPrepaymentDao.save(lendingPrepayment);
                lendingPrepaymentAuditDao.save(new LendingPrepaymentAudit(activeLoan.getMerchantId(), activeLoan.getId(), balance, advanceEdiCount));
                logger.info("Advance EDI adjustment successful for loanId:{} and amount:{}", activeLoan.getId(), balance);
                return true;
            } else {
                logger.error("Advance edi balance:{} is not correct for loanId:{}, adjusting as prepayment",balance, activeLoan.getId());
            }
        }
        return false;
    }

    private void refundExtraAmount(LendingPaymentSchedule lendingPaymentSchedule) {
        try {
            Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(lendingPaymentSchedule.getMerchantId());
            if (ObjectUtils.isEmpty(basicDetailsDto)) {
                return;
            }
            logger.info("Refund due amount:{} for loan:{}", lendingPaymentSchedule.getDueAmount(), lendingPaymentSchedule.getId());
            String orderId = "ECOLLECT_REFUND" + System.currentTimeMillis();
            Double refundAmount = -1 * lendingPaymentSchedule.getDueAmount();
            Double principle = -1 * lendingPaymentSchedule.getDuePrinciple();
            Double interest = -1 * lendingPaymentSchedule.getDueInterest();
            LendingPayoutRequest lendingPayoutRequest = new LendingPayoutRequest(lendingPaymentSchedule.getId(), orderId, refundAmount, LendingPayoutType.LENDING_ECOLLECT_REFUND, lendingPaymentSchedule.getMerchantId(), "ECOLLECT_REFUND");
            LendingPayoutResponse lendingPayoutResponse = apiGatewayService.lendingPayout(lendingPayoutRequest);
            if (lendingPayoutResponse != null) {
                String bankRefNo = lendingPayoutResponse.getData() != null ? lendingPayoutResponse.getData().getBankReferenceNo() : null;
                createRefundLedger(lendingPaymentSchedule, DateTimeUtil.getCurrentDayStartTime(), "LOAN_REFUND", -refundAmount, -principle, -interest, 0D, 0D, bankRefNo, "REFUND");
                lendingPaymentSchedule.setDueAmount(lendingPaymentSchedule.getDueAmount() + refundAmount);
                lendingPaymentSchedule.setDueInterest(lendingPaymentSchedule.getDueInterest() + interest);
                lendingPaymentSchedule.setDuePrinciple(lendingPaymentSchedule.getDuePrinciple() + principle);
                lendingPaymentScheduleDao.save(lendingPaymentSchedule);
                final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(lendingPaymentSchedule.getMerchantId());
                BankDetailsDto merchantBankDetail = null;
                if (bankDetailsDtoOptional.isPresent())
                    merchantBankDetail = bankDetailsDtoOptional.get();
                String identifier = "LENDING_REFUND_2_SMS";
                Map<String,Object> templateParams = new HashMap<>();
                templateParams.put("beneficiary_name",getBeneficiaryName(merchantBankDetail.getBeneficiaryName()));
                templateParams.put("refund_amount",refundAmount);
                templateParams.put("bank_name",merchantBankDetail.getBankName());
                NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
                notificationPayloadDto.setTemplateIdentifier(identifier);
                notificationPayloadDto.setMobile(basicDetailsDto.get().getMobile());
                notificationPayloadDto.setClientName("LENDING");
                notificationPayloadDto.setTemplateParams(templateParams);
                lendingNotificationService.notify(notificationPayloadDto);
            }
        } catch (Exception e) {
            logger.error("Exception in ECOLLECT Refund for loanId:{}", lendingPaymentSchedule.getId(), e);
        }
    }

    public void createRefundLedger(LendingPaymentSchedule lendingPaymentSchedule, Date date, String txnType, Double amount, Double principle, Double interest, Double otherCharges, Double penalty, String description, String adjustmentMode) {
        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setMerchantId(lendingPaymentSchedule.getMerchantId());
        if(lendingPaymentSchedule.getMerchantStoreId() != null && lendingPaymentSchedule.getMerchantStoreId() > 0){
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
        lendingLedger.setAdjustmentMode(adjustmentMode);
        lendingLedger.setTransferType(CollectionTransferTypeEnum.TRANSFER_BY_BP.name());
        lendingLedgerDao.save(lendingLedger);
        lendingCollectionAuditService.sendCollectionAudit(lendingLedger);
    }

    public void refundProcessingFee(LendingPaymentSchedule lendingPaymentSchedule) {
        try {
            logger.info("enter refund processing fee for merchant: {}", lendingPaymentSchedule.getMerchantId());
            LendingPayoutResponseDTO checkRefunded = lendingPayoutsHandler.findTopByMerchantIdAndOwnerIdAndStatusAndOrderIdLike(lendingPaymentSchedule.getMerchantId(),
                    lendingPaymentSchedule.getId(), "PF_CASHBACK");
            if(checkRefunded != null){
                return;
            }
            MerchantDetailsDto merchantDetailsDTO =  merchantService.fetchMerchantDetails(lendingPaymentSchedule.getMerchantId(), Collections.singletonList(Constants.MerchantUtil.Scope.BANK_DETAIL));
            BasicDetailsDto basicDetailsDto = merchantDetailsDTO.getMerchantDetail();
            BankDetailsDto merchantBankDetail = merchantDetailsDTO.getBankDetail();
            if (ObjectUtils.isEmpty(basicDetailsDto)) {
                return;
            }
            Date compareToDate = new SimpleDateFormat("dd/MM/yyyy").parse("16/07/2022");
            boolean isClubV2 = apiGatewayService.checkClubV2(lendingPaymentSchedule.getMerchantId());
            if (lendingPaymentSchedule.getStatus().equals("CLOSED") && lendingPaymentSchedule.getLoanApplication() != null
                    && lendingPaymentSchedule.getLoanApplication().getProcessingFee() != null
                    && lendingPaymentSchedule.getLoanApplication().getProcessingFee() > 0D
                    && (isClubV2 || lendingPaymentSchedule.getLoanApplication().getAgreementAt().before(compareToDate))) {
                logger.info("refund processing fee before 16th june or club member for merchant: {}", lendingPaymentSchedule.getMerchantId());
                BigInteger maxDpd = loanDpdDaoSlave.findMaxDpd(lendingPaymentSchedule.getId());
                long dpd = LoanUtil.getDateDiffInDays(lendingPaymentSchedule.getTentativeClosingDate(), lendingPaymentSchedule.getClosingDate());
                LendingLedger lendingLedger = lendingLedgerDao.getForClosedLedger(lendingPaymentSchedule.getId());
                if (maxDpd.intValue() <= 5 &&  dpd <= 5 && (dpd >= -5 || Objects.isNull(lendingLedger))) {
                    logger.info("Closing dpd is between 5 days for loanId:{}, processing fee refund for amount:{}", lendingPaymentSchedule.getId(), lendingPaymentSchedule.getLoanApplication().getProcessingFee());
                    Double cashbackAmt = lendingPaymentSchedule.getLoanApplication().getProcessingFee();
                    Double cashbackAmount = lendingPaymentSchedule.getLoanApplication().getAgreementAt().before(compareToDate)?cashbackAmt:Math.min(cashbackAmt, 1500);
                    String orderId = "PF_CASHBACK" + System.currentTimeMillis();
                    LendingPayoutRequest lendingPayoutRequest = new LendingPayoutRequest(lendingPaymentSchedule.getId(), orderId, cashbackAmount, LendingPayoutType.LENDING_INCENTIVE, lendingPaymentSchedule.getMerchantId(), "PF_CASHBACK");
                    LendingPayoutResponse lendingPayoutResponse = apiGatewayService.lendingPayout(lendingPayoutRequest);
                    if(Objects.nonNull(lendingPayoutResponse) && lendingPayoutResponse.isSuccess()){
                        liquiloansService.pushRedemptionInKafka(lendingPaymentSchedule.getLoanApplication(), cashbackAmount);
                    }
                    if (lendingPayoutResponse != null) {
                        String identifier = "LENDING_ARRANGER_REFUND_2_SMS";
                        Map<String,Object> templateParams = new HashMap<>();
                        templateParams.put("beneficiary_name",getBeneficiaryName(merchantBankDetail.getBeneficiaryName()));
                        templateParams.put("cashback_amount",merchantBankDetail.getBeneficiaryName());
                        templateParams.put("bank_name",merchantBankDetail.getBeneficiaryName());

                        NotificationPayloadDto notificationPayloadDto = new NotificationPayloadDto();
                        notificationPayloadDto.setTemplateIdentifier(identifier);
                        notificationPayloadDto.setMobile(basicDetailsDto.getMobile());
                        notificationPayloadDto.setClientName("LENDING");
                        notificationPayloadDto.setTemplateParams(templateParams);
                        lendingNotificationService.notify(notificationPayloadDto);
                        identifier = "LENDING_ARRANGER_FEE_REFUND_PUSH";
                        String deeplink = notificationUtil.getDeeplink(basicDetailsDto.getSettlementType(),"LOAN_DASHBOARD");
                        notificationPayloadDto.setPushDeepLink(deeplink);
                        notificationPayloadDto.setPushTitle("Arranger Fee refund!");
                        notificationPayloadDto.setTemplateIdentifier(identifier);
                        lendingNotificationService.notify(notificationPayloadDto);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Exception in PF Refund for loanId:{}", lendingPaymentSchedule.getId(), e);
        }
    }

    public ResponseDTO verifyPayment(RequestDTO<PaymentResendOTP> requestDTO, BasicDetailsDto merchant, String token) {
        LoanPaymentOrder loanPaymentOrder = loanPaymentOrderDao.findByOrderId(requestDTO.getPayload().getOrderId());
        if (loanPaymentOrder == null) {
            return new ResponseDTO(false, "Order not found");
        }
        if(!"PENDING".equalsIgnoreCase(loanPaymentOrder.getStatus())) {
            logger.info("Payment for merchant id {} and order id {} is already processed", loanPaymentOrder.getMerchantId(), loanPaymentOrder.getOrderId());
            return new ResponseDTO(false, "Duplicate request");
        }
        Optional<LendingPaymentSchedule> activeLoan = lendingPaymentScheduleDao.findById(loanPaymentOrder.getOwnerId());
        if(!activeLoan.isPresent()) {
            logger.error("No active loan found for id {}", loanPaymentOrder.getOwnerId());
            return new ResponseDTO(false, "Active Loan not found");
        }
        try {
            Map<String, Object> result = apiGatewayService.verifyTxn(requestDTO, token);
            Boolean success = (Boolean) result.get("success");
            if (success) {
                Double paymentAmount = (Double) result.get("amount");
                String paymentStatus = (String) result.get("status");
                String orderId = (String) result.get("order_id");
                if (CreditConstants.PaymentStatus.FAILED.name().equals(paymentStatus) || !orderId.equals(loanPaymentOrder.getOrderId()) || !loanPaymentOrder.getAmount().equals(paymentAmount)) {
                    loanPaymentOrder.setStatus("FAILED");
                    loanPaymentOrderDao.save(loanPaymentOrder);
                } else if (SUCCESS.name().equals(paymentStatus)) {
                    adjustLoanBalance(activeLoan.get(), loanPaymentOrder.getAmount(), null, loanPaymentOrder.getSource(),
                            PaymentType.ADVANCE_EDI.name().equalsIgnoreCase(loanPaymentOrder.getDescription()),"INTERNAL", null,loanPaymentOrder.getId(),
                            PaymentType.FORECLOSURE.name().equalsIgnoreCase(loanPaymentOrder.getDescription()));
                    loanPaymentOrder.setStatus("SUCCESS");
                    loanPaymentOrderDao.save(loanPaymentOrder);
                }
                return new ResponseDTO(true, "success");
            } else {
                logger.info("BPB verification failed for loan payment order:{}", loanPaymentOrder.getOrderId());
                loanPaymentOrder.setStatus("FAILED");
                loanPaymentOrderDao.save(loanPaymentOrder);
            }
        } catch (Exception e) {
            logger.error("Exception in payment verify", e);
        }
        return new ResponseDTO(false, "Payment verification Failed");
    }

    public int createAdjustedSchedule(LendingPaymentSchedule lendingPaymentSchedule, double amount) {
        try {
            logger.info("Creating Adjusted Edi Schedule for loan:{} and amount:{}", lendingPaymentSchedule.getId(), amount);
            if (amount <= 0) {
                logger.error("Adjusted Amount is less than 0");
                return 0;
            }
            List<LendingAdjustedEDISchedule> ediScheduleList = new ArrayList<>();
            int installmentNo = 1;
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            int ediCount = (int) (amount/lendingPaymentSchedule.getEdiAmount());
            double extraAmount = amount % lendingPaymentSchedule.getEdiAmount();
            while (installmentNo <= ediCount) {
                if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                    calendar.add(Calendar.DAY_OF_MONTH, 1);
                    continue;
                }
                LendingAdjustedEDISchedule ediSchedule = new LendingAdjustedEDISchedule();
                ediSchedule.setMerchantId(lendingPaymentSchedule.getMerchantId());
                ediSchedule.setMerchantStoreId(lendingPaymentSchedule.getMerchantStoreId());
                ediSchedule.setLoanId(lendingPaymentSchedule.getId());
                ediSchedule.setApplicationId(lendingPaymentSchedule.getLoanApplication() != null ? lendingPaymentSchedule.getLoanApplication().getId() : null);
                ediSchedule.setDate(calendar.getTime());
                ediSchedule.setInstallmentNumber(installmentNo);
                ediSchedule.setTotalEdi(lendingPaymentSchedule.getEdiAmount());
                ediSchedule.setPrinciple(lendingPaymentSchedule.getEdiAmount());
                ediSchedule.setInterest(0D);
                ediScheduleList.add(ediSchedule);
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                installmentNo++;
            }
            if (extraAmount > 0) {
                if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                    calendar.add(Calendar.DAY_OF_MONTH, 1);
                }
                LendingAdjustedEDISchedule ediSchedule = new LendingAdjustedEDISchedule();
                ediSchedule.setMerchantId(lendingPaymentSchedule.getMerchantId());
                ediSchedule.setMerchantStoreId(lendingPaymentSchedule.getMerchantStoreId());
                ediSchedule.setLoanId(lendingPaymentSchedule.getId());
                ediSchedule.setApplicationId(lendingPaymentSchedule.getLoanApplication() != null ? lendingPaymentSchedule.getLoanApplication().getId() : null);
                ediSchedule.setDate(calendar.getTime());
                ediSchedule.setInstallmentNumber(installmentNo);
                ediSchedule.setTotalEdi(extraAmount);
                ediSchedule.setPrinciple(extraAmount);
                ediSchedule.setInterest(0D);
                ediScheduleList.add(ediSchedule);
            }
            if (!ediScheduleList.isEmpty()) {
                lendingAdjustedEDIScheduleDao.saveAll(ediScheduleList);
                return ediScheduleList.size();
            }
        } catch (Exception e) {
            logger.error("Exception while creating adjusted schedule", e);
        }
        return 0;
    }

    private String getBeneficiaryName(String beneficiaryName) {
        if(beneficiaryName.length() > 25) {
            beneficiaryName = beneficiaryName.substring(0,25);
        }
        return beneficiaryName;
    }

    public PaymentStatusV3ResponseDTO getStatusV3(String orderId, BasicDetailsDto merchant) {
        logger.info("Received status check request for orderId : {}", orderId);
        try {
            // Fetch LMS payment details
            LmsPaymentDetails lmsPaymentDetails = lmsPaymentDetailsDao.findByTerminalOrderId(orderId);
            if (ObjectUtils.isEmpty(lmsPaymentDetails)) {
                logger.warn("No LMS payment details found for orderId in new flow: {}", orderId);
                return handleExistingFlow(orderId, merchant);
            }
            logger.info("Payment details found for orderId: {}, terminalOrderId: in new 1LMS Flow {}", orderId, lmsPaymentDetails.getTerminalOrderId());
            return paymentStatusService.handleOneLmsSource(lmsPaymentDetails, orderId, merchant);

        } catch (IllegalArgumentException ex) {
            logger.error("Invalid input for orderId: {}. Error: {}", orderId, ex.getMessage());
            return new PaymentStatusV3ResponseDTO(false, "Invalid input");
        } catch (Exception ex) {
            logger.error("Exception while checking status for orderId: {}. Error: {}", orderId, ex.getMessage(), ex);
            return new PaymentStatusV3ResponseDTO(false, "Something went wrong");
        }
    }
    private PaymentStatusV3ResponseDTO handleExistingFlow(String orderId, BasicDetailsDto merchant) {
        dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        LoanPaymentOrder order = loanPaymentOrderDao.findByOrderId(orderId);
        if (order == null || !order.getMerchantId().equals(merchant.getId())) {
            logger.info("No order found for orderId:{}", orderId);
            return new PaymentStatusV3ResponseDTO(false, "Order not found");
        }
        Optional<LendingPaymentSchedule> activeLoan = lendingPaymentScheduleDao.findById(order.getOwnerId());
        Lender lender = Lender.valueOf(activeLoan.get().getNbfc());
        if ("PENDING".equalsIgnoreCase(order.getStatus())) {
            logger.info("pg status check for merchant id {} and order id {}", order.getMerchantId(), order.getOrderId());
            PgStatusResponse response = apiGatewayService.checkPgStatus(order.getOrderId(), lender, order.getMerchantId());
            if (response != null && response.getStatusCode() != null && "200".equalsIgnoreCase(response.getStatusCode()) && Objects.nonNull(response.getData()) && "SUCCESS".equalsIgnoreCase(response.getData().getPaymentStatus())) {
                logger.info("Pg txn Status SUCCESS for orderId:{}", order.getOrderId());
                handlePgCallback(response.getData());
                order = loanPaymentOrderDao.findByOrderId(orderId);
            } else if (response != null && response.getStatusCode() != null && "200".equalsIgnoreCase(response.getStatusCode()) && Objects.nonNull(response.getData()) && (Status.TransactionStatus.FAILED.name().equalsIgnoreCase(response.getData().getPaymentStatus())
                    || Status.TransactionStatus.FAILURE.name().equalsIgnoreCase(response.getData().getPaymentStatus())
                    || Status.TransactionStatus.CANCELLED.name().equalsIgnoreCase(response.getData().getPaymentStatus()))) {
                order.setStatus(response.getData().getPaymentStatus());
                loanPaymentOrderDao.save(order);
                logger.info("Pg txn Status FAILED/CANCELLED for orderId:{}", order.getOrderId());
            }
        }
        PaymentStatusV3ResponseDTO.Data data = new PaymentStatusV3ResponseDTO.Data();
        data.setPaymentMode(order.getSource());
        data.setPaymentStatus(order.getStatus());
        data.setReferenceNumber(order.getBankRefNo());
        data.setTransferTime(dateFormat.format(order.getUpdatedAt()));
        data.setAmount(order.getAmount());
        data.setOrderId(orderId);
        return new PaymentStatusV3ResponseDTO(true, null, data);
    }

    public ResponseDTO applyWaiver(Long loanId, Long merchantId, WaiverType waiverType, Long userId) {
        LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByIdAndMerchantId(loanId, merchantId);
        if(Objects.isNull(lendingPaymentSchedule) || !"ACTIVE".equalsIgnoreCase(lendingPaymentSchedule.getStatus())) {
            logger.error("No active loan found for id {}", loanId);
            return new ResponseDTO(false, "No active loan found");
        }
        Integer foreClosureAmount = loanUtil.getForeclosureAmount(lendingPaymentSchedule) + getEDIHolidayInterestAmount(lendingPaymentSchedule);
        LoanPaymentOrder order = createOrder(lendingPaymentSchedule,waiverType.name(), foreClosureAmount);
        PaymentCallbackRequestDTO paymentCallbackRequestDTO = new PaymentCallbackRequestDTO();
        paymentCallbackRequestDTO.setAmount(order.getAmount());
        paymentCallbackRequestDTO.setStatus("SUCCESS");
        paymentCallbackRequestDTO.setOrderId(order.getOrderId());
        handleCallback(paymentCallbackRequestDTO);

        //waiver audit
        LendingInterestWaiver lendingInterestWaiver = new LendingInterestWaiver();
        lendingInterestWaiver.setAmount(foreClosureAmount);

        // check added for waiver of high tpv cases where applicationId does not exists
        if (!ObjectUtils.isEmpty(lendingPaymentSchedule.getApplicationId()))
            lendingInterestWaiver.setApplicationId(lendingPaymentSchedule.getApplicationId());

        lendingInterestWaiver.setMerchantId(lendingPaymentSchedule.getMerchantId());
        lendingInterestWaiver.setPaymentId(lendingPaymentSchedule.getId());
        lendingInterestWaiver.setSchemeName(waiverType.name());
        lendingInterestWaiver.setUserId(userId);
        lendingInterestWaiverDao.save(lendingInterestWaiver);

        lendingPaymentSchedule = lendingPaymentScheduleDao.findByIdAndMerchantId(loanId, merchantId);
        if("CLOSED".equalsIgnoreCase(lendingPaymentSchedule.getStatus())) {
            lendingPaymentSchedule.setSettlementStatus(waiverType.name());
            lendingPaymentScheduleDao.save(lendingPaymentSchedule);
            return new ResponseDTO(true, "Waiver applied successfully");
        } else {
            logger.error("Unable to settle loan:{}", lendingPaymentSchedule.getId());
        }
        return new ResponseDTO(false, "Something went wrong");
    }

    private LoanPaymentOrder createOrder(LendingPaymentSchedule lendingPaymentSchedule, String source, Integer foreclosureAmount) {
        logger.info("Creating Order for loan Id : {}", lendingPaymentSchedule.getId());
        LoanPaymentOrder order = new LoanPaymentOrder();
        order.setMerchantId(lendingPaymentSchedule.getMerchantId());
        order.setOwner("lending_payment_schedule");
        order.setOwnerId(lendingPaymentSchedule.getId());
        order.setAmount(foreclosureAmount.doubleValue());
        order.setStatus("PENDING");
        order.setSource(source);
        order = loanPaymentOrderDao.save(order);
        String orderId = "LOAN" + (10000000L + order.getId());
        order.setOrderId(orderId);
        return loanPaymentOrderDao.save(order);
    }

    public LoanRefundsResponseDTO getRefunds(Long loanId) {
        Optional<LendingPaymentSchedule> optionalLps = lendingPaymentScheduleDao.findById(loanId);
        LoanRefundsResponseDTO loanRefundsResponseDTO = new LoanRefundsResponseDTO();
        if(optionalLps .isPresent()) {
            List<LendingPayoutResponseDTO> lendingPayoutsList = lendingPayoutsHandler.findByOwnerIdAndTypeAndCreatedAtGTE(loanId, "REFUND", "2021-08-09");

            if (!ObjectUtils.isEmpty(lendingPayoutsList)) {
                logger.info("number of refunds: {} for loanId: {}", loanId, lendingPayoutsList.size());

                List<LoanRefundsResponseDTO.Refund> loanRefundList = new ArrayList<>();

                for (LendingPayoutResponseDTO lendingPayouts : lendingPayoutsList) {
                    LoanRefundsResponseDTO.Refund loanRefund = new LoanRefundsResponseDTO.Refund(loanId, lendingPayouts.getAmount(),
                            lendingPayouts.getCreatedAt(), lendingPayouts.getPaymentType());
                    loanRefundList.add(loanRefund);
                }

                loanRefundsResponseDTO.setRefundList(loanRefundList);
                loanRefundsResponseDTO.setSuccess(true);
            }
            return loanRefundsResponseDTO;
        }
        loanRefundsResponseDTO.setMessage("No loan found with id:" + loanId);
        loanRefundsResponseDTO.setSuccess(false);
        return loanRefundsResponseDTO;
    }

    @Transactional
    public ResponseDTO createEntryInLedger(LedgerEntryDTO ledgerEntryDTO) {
        if(Objects.isNull(ledgerEntryDTO.getType()) || Objects.isNull(ledgerEntryDTO.getAmount()) || Objects.isNull(ledgerEntryDTO.getPrinciple()) || Objects.isNull(ledgerEntryDTO.getInterest()) || Objects.isNull(ledgerEntryDTO.getLoanId())) {
            return new ResponseDTO(false, "Invalid Request");
        }
        Double amount = ledgerEntryDTO.getAmount();
        Double principle = ledgerEntryDTO.getPrinciple();
        Double interest = ledgerEntryDTO.getInterest();
        if(amount < 0 || principle < 0 || interest < 0 || amount != principle + interest) {
            return new ResponseDTO(false, "Invalid Request");
        }
        LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByIdAndMerchantId(ledgerEntryDTO.getLoanId(), ledgerEntryDTO.getMerchantId());
        if(Objects.isNull(lendingPaymentSchedule)) {
            return new ResponseDTO(false, "Loan Id doesn't exist");
        }
        if (!"ACTIVE".equalsIgnoreCase(lendingPaymentSchedule.getStatus())) {
            return new ResponseDTO(false, "Loan is not Active");
        }
        if("CREDIT".equalsIgnoreCase(ledgerEntryDTO.getType()) && ledgerEntryDTO.getAmount() > loanUtil.getForeclosureAmount(lendingPaymentSchedule)) {
            return new ResponseDTO(false, "Amount greater than foreclosure amount");
        }
        if("DEBIT".equalsIgnoreCase(ledgerEntryDTO.getType()) && ledgerEntryDTO.getAmount() > lendingPaymentSchedule.getPaidAmount()) {
            return new ResponseDTO(false, "Amount greater than paid amount");
        }

        if("DEBIT".equalsIgnoreCase(ledgerEntryDTO.getType())) {
            amount = amount * -1;
            principle = principle * -1;
            interest = interest * -1;
        }
        lendingPaymentSchedule.setPaidAmount(lendingPaymentSchedule.getPaidAmount() + amount);
        lendingPaymentSchedule.setPaidPrinciple(lendingPaymentSchedule.getPaidPrinciple() + principle);
        lendingPaymentSchedule.setPaidInterest(lendingPaymentSchedule.getPaidInterest() + interest);
        lendingPaymentSchedule.setDueAmount(lendingPaymentSchedule.getDueAmount() - amount);
        lendingPaymentSchedule.setDuePrinciple(lendingPaymentSchedule.getDuePrinciple() - principle);
        lendingPaymentSchedule.setDueInterest(lendingPaymentSchedule.getDueInterest() - interest);
        lendingPaymentScheduleDao.save(lendingPaymentSchedule);
        LendingLedger lendingLedger = new LendingLedger();
        lendingLedger.setAmount(amount);
        lendingLedger.setInterest(interest);
        lendingLedger.setPrinciple(principle);
        lendingLedger.setLendingPaymentSchedule(lendingPaymentSchedule);
        lendingLedger.setMerchantId(lendingPaymentSchedule.getMerchantId());
        lendingLedger.setDate(DateTimeUtil.getCurrentDayStartTime());
        lendingLedger.setAdjustmentMode("MANUAL_"+ledgerEntryDTO.getType());
        lendingLedger.setDescription(ledgerEntryDTO.getDescription());
        lendingLedgerDao.save(lendingLedger);
        lendingCollectionAuditService.sendCollectionAudit(lendingLedger);
        return new ResponseDTO(true, "Entry Created");
    }

    public void sendForeclosureEvent(Long applicationId, String mobile, LendingLedger lendingLedger, Long orderId) {
        logger.info("Send Foreclosure Event: applicationId: {}, mobile: {}, lendingLedger: {}, orderId: {}", applicationId, mobile, lendingLedger, orderId);
        String status = "SUCCESS";
        Double charge = 0.0;
        Double chargeTax = 0.0;
        String postingStatus = "FAILURE";
        LoanForeClosureCharges loanForeClosureCharges = loanForeClosureChargesDao.findByOrderId(orderId);
        logger.info("LoanForeclosureCharges Record: {}", loanForeClosureCharges);

        try{
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(applicationId, com.bharatpe.lending.common.enums.Status.ACTIVE.name());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                logger.info("no lending app details record found for the app {}", applicationId);
                return;
            }
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
            if (!lendingApplication.isPresent()) {
                logger.error("no lending application record found for the app {}", applicationId);
                return;
            }
            String txnId = Optional.ofNullable(lendingLedger.getTerminalOrderId()).orElse(String.valueOf(lendingLedger.getId()));
            Date txnDate = LoanPaymentUtil.getNonFutureTransactionDate(lendingLedger.getDate());

            if(loanForeClosureCharges != null) {
                logger.info("Creating foreclosurecharges post charge request");
                ForeclosureChargesRequestDto foreclosureChargesRequestDto = ForeclosureChargesRequestDto.builder()
                        .applicationId(applicationId)
                        .productName("LENDING")
                        .lender(Lender.ABFL.name())
                        .payload(ForeclosureChargesRequestDto.Payload.builder()
                                         .accountId(lendingApplicationLenderDetails.getAccountId())
                                         .uniqueId(PaymentAdjustmentModes.getAdjustedModeAbbr(lendingLedger.getAdjustmentMode()) + "_" + TransferTypeModes.getTransferTypeAbbr(lendingLedger.getTransferType()) + "_" + "FC" + "_" + txnId)
                                         .dealNo(lendingApplicationLenderDetails.getDealNo())
                                         .loanNo(lendingApplicationLenderDetails.getLan())
                                         .transactionId(String.valueOf(lendingLedger.getId()))
                                         .chargeType("R")
                                         .businessPartnerType("CS")
                                         .chargeAmount(String.valueOf(loanForeClosureCharges.getAmount()))
                                         .taxInclusive("N")
                                         .finalAmount(String.valueOf(loanForeClosureCharges.getAmount()))
                                         .chargeCode("112")
                                         .build())
                        .build();
                logger.info("ABFL: posting foreclosure charges to lender {}", foreclosureChargesRequestDto);
                NbfcResponseDto nbfcResponseDto = nbfcLenderGateway.invoke(objectMapper.writeValueAsString(foreclosureChargesRequestDto), NbfcResponseDto.class, nbfcCollectionServiceBaseUrl + nbfcForeClosureChargePosting);
                log.info("ABFL: response foreclosure charges posting request :{} and response : {}", objectMapper.writeValueAsString(foreclosureChargesRequestDto), nbfcResponseDto);

                if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                    log.info("ABFL: foreclosure charges posted to lender{}",nbfcResponseDto);
                    charge = loanForeClosureCharges.getAmount();
                    chargeTax = loanForeClosureCharges.getTax();
                    postingStatus = "POSTED";
                } else {
                    // Bhuvnesh :- if charge posting is failed then cancel foreclosure posting
                    // and make lendingCollectionAudit entry as failed
                    log.info("ABFL: foreclosure charges posting failed to request {} response {}", foreclosureChargesRequestDto, nbfcResponseDto);
                    throw new Exception("Foreclosure failed");
                }
            }

            ForeclosureRequestDto foreclosureRequestDto = ForeclosureRequestDto.builder()
                    .applicationId(applicationId)
                    .lender(Lender.ABFL.name())
                    .productName("LENDING")
                    .topup(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.get().getLoanType()))
                    .payload(ForeclosureRequestDto.Payload.builder()
                            .accountId(lendingApplicationLenderDetails.getAccountId())
                            .dealNo(lendingApplicationLenderDetails.getDealNo())
                            .loanNo(lendingApplicationLenderDetails.getLan())
                            .uniqueId(PaymentAdjustmentModes.getAdjustedModeAbbr(lendingLedger.getAdjustmentMode()) + "_" + TransferTypeModes.getTransferTypeAbbr(lendingLedger.getTransferType()) + "_" + txnId)
                            .loanReceiptDetails(ForeclosureRequestDto.LoanReceiptDetails.builder()
                                    .receiptAmount(lendingLedger.getAmount())
                                    .paidByContactNo(mobile.substring(2))
                                    .transactionRefNumber(String.valueOf(lendingLedger.getId()))
                                    .receiptDateTime(txnDate)
                                    .build())
                            .build())
                    .build();
            logger.info("foreclosure event sent {}", foreclosureRequestDto);
            confluentKafkaTemplate.send("foreclose-loan", objectMapper.readValue(objectMapper.writeValueAsString(foreclosureRequestDto), new TypeReference<Map<String, Object>>() {
            }));
            logger.info("ABFL: updating LCA for foreclosed event for application id : {} ", lendingApplicationLenderDetails.getApplicationId());
        } catch (Exception e) {
            logger.error("error occurred while sending foreclosure event {}", e.getMessage());
            status = "FAILED";
        }

        logger.info("ABFL: updating LCA for foreclosed event for application id : {}  and status is {}", applicationId, status);
        LendingCollectionAudit lendingCollectionAudit = lendingCollectionAuditDao.findByLedgerID(lendingLedger.getId(),1);
        if (lendingCollectionAudit != null) {
            lendingCollectionAudit.setStatus(status);
            lendingCollectionAuditDao.save(lendingCollectionAudit);
            logger.info("ABFL: updated LCA for foreclosed event for application id : {} and status :{} ", applicationId, status);
        }
        if (loanForeClosureCharges != null) {
            loanForeClosureCharges.setChargePostingStatus(postingStatus);
            loanForeClosureChargesDao.save(loanForeClosureCharges);
        }
    }

    public void sendForeclosureEventTrillionLoans(Long applicationId, LendingLedger lendingLedger, Long orderId) {
        String status = "SUCCESS";
        Double charge = 0.0;
        Double chargeTax = 0.0;
        String postingStatus = "FAILURE";
        LoanForeClosureCharges loanForeClosureCharges = loanForeClosureChargesDao.findByOrderId(orderId);
        try{
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(applicationId, com.bharatpe.lending.common.enums.Status.ACTIVE.name());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                logger.info("TrillionLoans: no lending app details record found for the app {}", applicationId);
                return;
            }
            if(loanForeClosureCharges != null) {
                TrilionLoansForeclosureChargesRequestDto trilionLoansForeclosureChargesRequestDto = TrilionLoansForeclosureChargesRequestDto.builder()
                        .applicationId(applicationId)
                        .productName("LENDING")
                        .lender(Lender.TRILLIONLOANS.name())
                        .payload(TrilionLoansForeclosureChargesRequestDto.Payload.builder()
                                .lan(lendingApplicationLenderDetails.getLan())
                                .amount(loanForeClosureCharges.getAmount() + loanForeClosureCharges.getTax())
                                .chargeId("5")
                                .dueDate(loanForeClosureCharges.getCreatedAt())
                                .build())
                        .build();
                logger.info("TrillionLoans: posting foreclosure charges to lender {}", trilionLoansForeclosureChargesRequestDto);
                NbfcResponseDto nbfcResponseDto = nbfcLenderGateway.invoke(objectMapper.writeValueAsString(trilionLoansForeclosureChargesRequestDto), NbfcResponseDto.class, nbfcCollectionServiceBaseUrl + nbfcForeClosureChargePosting);
                log.info("TrillionLoans: response foreclosure charges posting request :{} and response : {}", objectMapper.writeValueAsString(trilionLoansForeclosureChargesRequestDto), nbfcResponseDto);

                if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                    log.info("TrillionLoans: foreclosure charges posted to lender{}",nbfcResponseDto);
                    charge = loanForeClosureCharges.getAmount();
                    chargeTax = loanForeClosureCharges.getTax();
                    postingStatus = "POSTED";
                } else {
                    // Bhuvnesh :- if charge posting is failed then cancel foreclosure posting
                    // and make lendingCollectionAudit entry as failed
                    log.info("TrillionLoans: foreclosure charges posting failed to request {} response {}",trilionLoansForeclosureChargesRequestDto, nbfcResponseDto);
                    throw new Exception("Foreclosure failed");
                }
            }

            String txnId = Optional.ofNullable(lendingLedger.getTerminalOrderId()).orElse(String.valueOf(lendingLedger.getId()));
            Integer paymentTypeId = 1;
            if (lendingLedger.getLendingPaymentSchedule() != null
                    && easyLoanUtil.percentScaleUp(lendingLedger.getLendingPaymentSchedule().getId(), receiptPostingPaymentIdRolloutPercent)) {
                paymentTypeId = tlPaymentTypeIdMap.getOrDefault(lendingLedger.getAdjustmentMode(), TL_DEFAULT_PAYMENT_TYPE_ID);
            }
            TrillionForeclosureRequestDto trillionForeclosureRequestDto = TrillionForeclosureRequestDto.builder()
                    .applicationId(applicationId)
                    .lender(Lender.TRILLIONLOANS.name())
                    .productName("LENDING")
                    .payload(TrillionForeclosureRequestDto.Payload.builder()
                            .loanAccounts(lendingApplicationLenderDetails.getLan())
                            .note("Foreclosure")
                            .preClosureReasonId(192)
                            .transactionAmount(String.valueOf(Math.ceil(lendingLedger.getAmount())))
                            .transactionDate(lendingLedger.getDate())
                            .paymentTypeId(paymentTypeId)
                            .interestWaiverAmount(0.0)
                            .receiptNumber(txnId)
                            .chargeDiscountDetails(new ArrayList<>())
                            .waiveCharges(new ArrayList<>())
                            .build())
                    .build();
            logger.info("TrillionLoans: foreclosure event sent {}", trillionForeclosureRequestDto);
            confluentKafkaTemplate.send(nbfcTrillionForeclosureTopic, objectMapper.readValue(objectMapper.writeValueAsString(trillionForeclosureRequestDto), new TypeReference<Map<String, Object>>() {}));
            logger.info("TrillionLoans: updating LCA for foreclosed event for application id : {} ", lendingApplicationLenderDetails.getApplicationId());
        } catch (Exception e) {
            logger.error("error occurred while sending foreclosure event {}", e.getMessage());
            status = "FAILED";
        }

        logger.info("TrillionLoans: updating LCA for foreclosed event for application id : {}  and status is {}", applicationId, status);
        LendingCollectionAudit lendingCollectionAudit = lendingCollectionAuditDao.findByLedgerID(lendingLedger.getId(),1);
        if (lendingCollectionAudit != null) {
            lendingCollectionAudit.setStatus(status);
            lendingCollectionAuditDao.save(lendingCollectionAudit);
            logger.info("TrillionLoans: updated LCA for foreclosed event for application id : {} and status :{} ", applicationId, status);
        }
        if (loanForeClosureCharges != null) {
            loanForeClosureCharges.setChargePostingStatus(postingStatus);
            loanForeClosureChargesDao.save(loanForeClosureCharges);
        }
    }

    private void adjustLoanBalanceEdiByEdi(LendingPaymentSchedule activeLoan, Double amount, String bankRefNo, String source, String transferType, String terminalOrderId, Long orderId, double foreclosureChargesAmount, LoanForeClosureCharges loanForeClosureCharges) {
        logger.info("Adjusting Balance for loanId:{} and amount:{}", activeLoan.getId(), amount);
        Integer foreclosureAmount = loanUtil.getForeclosureAmount(activeLoan);
        Double paidInterestAmount = 0D;
        Double paidPrincipalAmount = 0D;
        Double remainingBalance = amount;
        Double penaltyFee = 0d;
        boolean preclosure = false;
        boolean excessCollectionAdjusted = false;
        boolean preclosureWithCharges =  foreclosureChargesAmount > 0.0;
        Double excessCollectionBalance = 0D;
        List<LendingCollectionExcess> lendingCollectionExcessList = lendingCollectionExcessDao.findByMerchantIdAndLoanIdAndStatusOrderByIdAsc(activeLoan.getMerchantId(), activeLoan.getId(), "ACTIVE");
        for(LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList){
            if(lendingCollectionExcess.getAmount() > 0){
                excessCollectionBalance += lendingCollectionExcess.getAmount();
            }
        }
        boolean loanStatusFlag = false;

        logger.info("Excess collection balance for loanId:{} is:{}", activeLoan.getId(), excessCollectionBalance);
        logger.info("Preclosure amount for loanId:{} is:{}", activeLoan.getId(), foreclosureAmount);
        logger.info("Due amount for loanId:{} is due amount:{} due principle:{} due interest:{}", activeLoan.getId(), activeLoan.getDueAmount(), activeLoan.getDuePrinciple(), activeLoan.getDueInterest());


        List<String> waiverList = Arrays.asList(WaiverType.EXCEPTION.name(), WaiverType.DECEASED_SCHEME.name(), WaiverType.SCHEME1.name(), WaiverType.SCHEME.name());
        if (Objects.nonNull(source) && waiverList.contains(source) &&
                (activeLoan.getNbfc().equalsIgnoreCase(Lender.ABFL.name()) || activeLoan.getNbfc().equalsIgnoreCase(Lender.PIRAMAL.name()))) {
            waiverSettlement(activeLoan, amount, bankRefNo, source, transferType, terminalOrderId, excessCollectionBalance, lendingCollectionExcessList);
            return;
        }
        String requestId = UUID.randomUUID().toString().replaceAll("-", "");
        Boolean postChargesToLender = false;
        Integer pendingNachCharges = nachBounceChargesService.getNachCharges(activeLoan).intValue();
        if(foreclosureAmount + pendingNachCharges - amount <= 1D) {
            logger.info("Received pre closure amount:{} for loan:{}", amount, activeLoan.getId());
            paidInterestAmount = (activeLoan.getDueInterest() != null ? activeLoan.getDueInterest() : 0);
            penaltyFee = Objects.nonNull(activeLoan.getDuePenalty()) ? activeLoan.getDuePenalty() : 0;

            if(pendingNachCharges != 0d){
                log.info("Found Nach Bounce Charges for loan: {}, nbfc: {} ", activeLoan.getId(), activeLoan.getNbfc());
                nachBounceChargesService.createCharges(activeLoan, requestId);
                penaltyFee += pendingNachCharges;
                postChargesToLender = true;
            }
            paidPrincipalAmount = amount - paidInterestAmount + excessCollectionBalance - penaltyFee - foreclosureChargesAmount;
            remainingBalance = (activeLoan.getPaidPrinciple() + paidPrincipalAmount) - activeLoan.getLoanAmount();

            paymentSettlementService.settlePreclosureLoanPayment(activeLoan.getId(), activeLoan.getEdiCount(), activeLoan.getEdiRemainingCount(), activeLoan.getSettleAllPrinciple(), amount + excessCollectionBalance);

            logger.info("Adjusted breakup amount for loan:{} is principle:{} and interest:{} and foreclosureCharges : {}", activeLoan.getId(), paidPrincipalAmount, paidInterestAmount, foreclosureChargesAmount);
            String description = (preclosureWithCharges) ? "PREPAYMENT_WITH_CHARGES" : "PREPAYMENT";

            if(activeLoan.getDueAmount() >= 0) {
                createLendingLedger(activeLoan, -1 * Math.abs(amount - activeLoan.getDueAmount() + excessCollectionBalance) ,
                  -1 * Math.abs(amount - activeLoan.getDueAmount() + excessCollectionBalance - penaltyFee - foreclosureChargesAmount),
                  0d, description, source, transferType, terminalOrderId, -1*penaltyFee, -1*foreclosureChargesAmount);
            } else {
                createLendingLedger(activeLoan, -1 * (amount + excessCollectionBalance), -1 * (amount + excessCollectionBalance -penaltyFee -foreclosureChargesAmount),
                        0d, description, source, transferType, terminalOrderId, -1*penaltyFee, -1*foreclosureChargesAmount);
            }

            activeLoan.setPaidAmount(activeLoan.getPaidAmount() + amount + excessCollectionBalance);
            activeLoan.setPaidInterest((activeLoan.getPaidInterest() != null ? activeLoan.getPaidInterest() : 0) + paidInterestAmount);
            activeLoan.setPaidPrinciple((activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0) + paidPrincipalAmount);
            activeLoan.setPaidPenalty((activeLoan.getPaidPenalty() != null ? activeLoan.getPaidPenalty() : 0) + penaltyFee);

            activeLoan.setDueAmount(0D);
            activeLoan.setDueInterest(0D);
            activeLoan.setDuePrinciple(0D);
            activeLoan.setDuePenalty((Objects.nonNull(activeLoan.getDuePenalty()) ? activeLoan.getDuePenalty() : 0) - penaltyFee);

            activeLoan.setStatus("CLOSED");
            activeLoan.setClosingDate(new Date());
            preclosure = true;
            if(excessCollectionBalance > 0D) excessCollectionAdjusted = true;
            loanStatusFlag=true;
            log.info("setting loan flag as true at AdjustLoanBalanceEdiByEdi for merchantId :{}",activeLoan.getMerchantId());
        }
        else {
            final SettleLoanPaymentDTO settleLoanPaymentDTO = paymentSettlementService.settleLoanPayment(activeLoan.getId(), activeLoan.getEdiCount(), activeLoan.getEdiRemainingCount(), activeLoan.getSettleAllPrinciple(), remainingBalance);
            paidPrincipalAmount = settleLoanPaymentDTO.getPaidPrinciple();
            paidInterestAmount = settleLoanPaymentDTO.getPaidInterest();
            double duePenalty = Objects.nonNull(activeLoan.getDuePenalty()) ? activeLoan.getDuePenalty() : 0;
            penaltyFee = settleLoanPaymentDTO.getRemainingBalance() > duePenalty ? duePenalty : settleLoanPaymentDTO.getRemainingBalance();
            remainingBalance = settleLoanPaymentDTO.getRemainingBalance() - penaltyFee;

            activeLoan.setDuePrinciple(activeLoan.getDuePrinciple() - paidPrincipalAmount);
            activeLoan.setDueInterest(activeLoan.getDueInterest() - paidInterestAmount);
            activeLoan.setDueAmount(activeLoan.getDueAmount() - (paidPrincipalAmount + paidInterestAmount));
            activeLoan.setDuePenalty(duePenalty - penaltyFee);
            activeLoan.setSettleAllPrinciple(settleLoanPaymentDTO.getSettleAllPrincipalFirst());

            activeLoan.setPaidPrinciple((activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0) + paidPrincipalAmount);
            activeLoan.setPaidInterest((activeLoan.getPaidInterest() != null ? activeLoan.getPaidInterest() : 0) + paidInterestAmount);
            activeLoan.setPaidAmount(activeLoan.getPaidAmount() + paidPrincipalAmount + paidInterestAmount);
            activeLoan.setPaidPenalty((activeLoan.getPaidPenalty() != null ? activeLoan.getPaidPenalty() : 0) + penaltyFee);
        }

        logger.info("Adjusted breakup amount for loan:{} is principle:{} and interest:{}", activeLoan.getId(), paidPrincipalAmount, paidInterestAmount);

        LendingLedger lendingLedger = createLendingLedger(
                activeLoan, excessCollectionAdjusted ? paidPrincipalAmount + paidInterestAmount + penaltyFee + foreclosureChargesAmount - excessCollectionBalance : paidPrincipalAmount + paidInterestAmount + penaltyFee + foreclosureChargesAmount,
          excessCollectionAdjusted ? paidPrincipalAmount - excessCollectionBalance : paidPrincipalAmount , paidInterestAmount,  getDescription(bankRefNo, preclosure, preclosureWithCharges), source, transferType, terminalOrderId, penaltyFee, foreclosureChargesAmount
        );
        if(excessCollectionAdjusted) {
            logger.info("Adjusting excess collection for loan in ledger : {}, amount : {}", activeLoan.getId(), excessCollectionBalance);
            createLendingLedgerForExcessCollectionOnForeclosure(activeLoan, lendingCollectionExcessList);
            settleExcessCollectionBalance(activeLoan.getId(), lendingCollectionExcessList);
        }

        if (penaltyFee > 0.5) {
            PenaltyFeeLedger penaltyFeeLedger = new PenaltyFeeLedger(activeLoan.getMerchantId(), activeLoan.getId(), penaltyFee, source, false, activeLoan.getNbfc());
            penaltyFeeLedgerDao.save(penaltyFeeLedger);
            loanUtil.savePenalCharges(activeLoan, penaltyFee);
        }

        if (Objects.nonNull(activeLoan.getSettleAllPrinciple()) && activeLoan.getSettleAllPrinciple()) {
            // switch back to IPC if all due is paid
            if (BigDecimal.valueOf(activeLoan.getDueAmount()).setScale(2, RoundingMode.HALF_UP).doubleValue() <= 0) {
                activeLoan.setSettleAllPrinciple(false);
            }
        }

        if(loanForeClosureCharges != null && lendingLedger != null) {
            loanForeClosureCharges.setLedgerId(lendingLedger.getId());
            loanForeClosureChargesDao.save(loanForeClosureCharges);
            log.info("Setting ledger id in foreclosure");
        }

        ForeClosureAmountInfo foreClosureAmountInfo = foreClosureAmountInfoDao.findByOrderId(orderId);
        if(foreClosureAmountInfo!= null && lendingLedger != null) {
            try {
                foreClosureAmountInfo.setLedgerId(lendingLedger.getId());
                foreClosureAmountInfoDao.save(foreClosureAmountInfo);
            }catch (Exception e){
                log.error("error occured while saving ledgerId for loanID {} in foreclosure amount info",activeLoan.getId());
            }
        }
        lendingPaymentScheduleDao.save(activeLoan);

        if (activeLoan.getStatus().equalsIgnoreCase(Status.LendingStatus.CLOSED.toString())) {
            if ("LDC".equals(activeLoan.getLoanApplication().getLender())) {
                nbfcService.pushCloseLoanEventToKafka(activeLoan.getApplicationId());
            }
        }

        boolean isLoanClosed = "CLOSED".equalsIgnoreCase(activeLoan.getStatus());

        Double finalAmount = amount;

        notificationExecutor.execute(() -> sendSMS(activeLoan, finalAmount, isLoanClosed));

        if (isLoanClosed && preclosure && !ObjectUtils.isEmpty(lendingLedger)) {
            if (activeLoan.getNbfc().equalsIgnoreCase(Lender.ABFL.name())) {
                sendForeclosureEvent(activeLoan.getApplicationId(), activeLoan.getMobile(), lendingLedger, orderId);
            }
            else if (activeLoan.getNbfc().equalsIgnoreCase(Lender.PIRAMAL.name())) {
                loanClosurePostingService.postForeclosureReceiptPiramal(activeLoan,lendingLedger,LoanClosureDTO.builder().foreclosureCharges(foreclosureChargesAmount).postCharges(postChargesToLender).build());
            }
            else if (activeLoan.getNbfc().equalsIgnoreCase(Lender.TRILLIONLOANS.name())) {
                sendForeclosureEventTrillionLoans(activeLoan.getApplicationId(), lendingLedger, orderId);
            }
            else if (Lender.PAYU.name().equalsIgnoreCase(activeLoan.getNbfc())) {
                loanClosurePostingService.sendForeclosureEventPayu(activeLoan.getApplicationId(), lendingLedger, orderId, postChargesToLender, requestId);
            }
            else if (Arrays.asList("USFB", "CAPRI", "CREDITSAISON", Lender.UGRO.name(), Lender.MUTHOOT.name()).contains(activeLoan.getNbfc())) {
                postForeclosureReceipt(activeLoan, lendingLedger);
            } else if (Lender.LIQUILOANS_P2P.name().equalsIgnoreCase(activeLoan.getNbfc())
                    || Lender.LIQUILOANS_P2P_OF.name().equalsIgnoreCase(activeLoan.getNbfc())
                    || Lender.LIQUILOANS_NBFC.name().equalsIgnoreCase(activeLoan.getNbfc())){
                sendForeclosureChargesEventLiquiLoans(activeLoan.getApplicationId(), activeLoan.getId(),lendingLedger.getId(), activeLoan.getNbfc(), orderId);
            }
        }

        if (remainingBalance > 0.05) {
            logger.info("Received more amount than due for loanId : {} , extraAmount : {}", activeLoan.getId(), remainingBalance);
            LendingRefundAudit lendingRefundAudit = new LendingRefundAudit();
            lendingRefundAudit.setDueAmount(activeLoan.getDueAmount());
            lendingRefundAudit.setLoanId(activeLoan.getId());
            lendingRefundAudit.setMerchantId(activeLoan.getMerchantId());
            lendingRefundAudit.setMode(source);
            lendingRefundAudit.setBankRefNo(bankRefNo);
            lendingRefundAudit.setRefundAmount(remainingBalance);
            lendingRefundAudit.setOrderAmount(amount);
            lendingRefundAuditDao.save(lendingRefundAudit);
        }
        if(loanStatusFlag)
        {
            Long merchantId = activeLoan.getMerchantId();
            log.info("sending loan flag status in adjustLoanBalanceEdiByEdi for merchantId : {} ",merchantId);
            sherlocLoanStatusChangeService.pushLoanStatusChangeEventToKafka(merchantId, activeLoan.getStatus());
        }
    }

    private void waiverSettlement(LendingPaymentSchedule activeLoan, Double amount, String bankRefNo, String source,
                                  String transferType, String terminalOrderId, Double excessCollectionBalance, List<LendingCollectionExcess> lendingCollectionExcessList) {

        createLendingLedger(activeLoan, -1 * (amount + excessCollectionBalance), -1 * (amount + excessCollectionBalance),
                0d, "PREPAYMENT", source, transferType, terminalOrderId, 0d);
        activeLoan.setStatus("CLOSED");
        activeLoan.setClosingDate(new Date());
        //settle excess collection balance only
        if(excessCollectionBalance > 0D) {
            activeLoan.setPaidAmount(activeLoan.getPaidAmount() + excessCollectionBalance);
            activeLoan.setPaidPrinciple((activeLoan.getPaidPrinciple() != null ? activeLoan.getPaidPrinciple() : 0) + excessCollectionBalance);

            logger.info("Adjusting excess collection for loan in ledger : {}, amount : {}", activeLoan.getId(), excessCollectionBalance);
            createLendingLedgerForExcessCollectionOnForeclosure(activeLoan, lendingCollectionExcessList);
            settleExcessCollectionBalance(activeLoan.getId(), lendingCollectionExcessList);

        }
        LendingLedger lendingLedger = createLendingLedger(
                activeLoan,  amount + excessCollectionBalance,
                amount + excessCollectionBalance, 0d,  getDescription(bankRefNo, true, false), source,
                transferType, terminalOrderId, 0d);

        lendingPaymentScheduleDao.save(activeLoan);
    }

    public PaymentDetailsResponseDTO getPaymentDetails(Long merchantId,String externalLoanId) {
        logger.info("Received payment details request for merchant id:{} and externalLoanId:{}", merchantId,externalLoanId);
        try {
            LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndExternalLoanIdAndStatus(merchantId, externalLoanId, Arrays.asList("ACTIVE", "DECEASED"));
            if(activeLoan == null) {
                logger.info("No active loan found for merchant id:{} and externalLoanId:{}",merchantId,externalLoanId);
                return new PaymentDetailsResponseDTO("NO ACTIVE LOAN");
            }
            PaymentDetailsResponseDTO paymentDetailsResponseDTO=getPaymentDetailsForActiveLoan(activeLoan, true);
            // add mobile number of merchant
            Optional<BasicDetailsDto> merchantBasicDetails=merchantService.fetchMerchantBasicDetails(merchantId);
            merchantBasicDetails.ifPresent(basicDetailsDto -> paymentDetailsResponseDTO.getData().setMobile(basicDetailsDto.getMobile()));
            //Record event through funnel service
            try {
                FunnelEventDto funnelEventDto = FunnelEventDto.builder()
                        .merchantId(merchantId)
                        .stageId(FunnelEnums.StageId.PAYMENT_LINK)
                        .stageEvent(FunnelEnums.StageEvent.PAYMENT_LINK_TRIGGERED)
                        .eventSubmissionTime(LocalDateTime.now().withNano(0))
                        .source(FunnelEnums.Source.BACKEND)
                        .build();
                LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchantId);
                if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
                    funnelEventDto.setVersion(LoanDetailsConstant.FUNNEL_VERSION_TAG);
                }
                notificationExecutor.execute(() -> funnelService.submitEvent(funnelEventDto));
            }
            catch (Exception e){
                logger.error("Exception in submitting funnel service event for merchantId:{},Exception:{}",merchantId,e.getMessage());
            }
            return paymentDetailsResponseDTO;
        } catch(Exception ex) {
            logger.error("Exception while fetching payment details for merchantId:{},Exception is:{}",merchantId, ex);
        }
        return new PaymentDetailsResponseDTO("Something went wrong.");
    }

    public InitiatePaymentResponseDTO initiatePaymentThroughLink(Long merchantId,String externalLoanId, RequestDTO<InitiatePaymentRequestDTO> request) {
        logger.info("Received initiate payment request  for merchantId {} : {}", merchantId, request);
        try {
            LendingPaymentSchedule activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndExternalLoanIdAndStatus(merchantId, externalLoanId, Arrays.asList("ACTIVE", "DECEASED"));
            if(activeLoan == null) {
                logger.info("No active loan found for merchant id {}", merchantId);
                return new InitiatePaymentResponseDTO("NO ACTIVE LOAN");
            }
            Integer amount = request.getPayload().getAmount();
            double penaltyFee = Objects.nonNull(activeLoan.getDuePenalty()) ? activeLoan.getDuePenalty() : 0;
            if(amount < 1 ) {
                logger.info("Amount is less than 1 for merchant id {}", merchantId);
                return new InitiatePaymentResponseDTO("Amount is less than 1");
            }
            String paymentType = request.getPayload().getPaymentType();
            //TODO: Add Due penalty handling too if enabling this
//            if (PaymentType.CUSTOM_AMOUNT.name().equalsIgnoreCase(paymentType) && amount > activeLoan.getDueAmount().intValue()) {
//                logger.info("custom amount:{} more than due amount:{} for merchant:{}", amount, activeLoan.getDueAmount().intValue(), merchantId);
//                return new InitiatePaymentResponseDTO("Custom amount should be less than due amount");
//            }
//            if (PaymentType.DUE_AMOUNT.name().equalsIgnoreCase(paymentType) && amount > activeLoan.getDueAmount().intValue()) {
//                logger.info("Due Amount in request :{} more than due amount:{} for merchant:{}", amount, activeLoan.getDueAmount().intValue(), merchantId);
//                return new InitiatePaymentResponseDTO("No dues left.");
//            }
//            // foreclosure and advance payment, extra payment not allowed
              // LC-2061 - surplus payment is allowed
//            if (amount > (Math.ceil(activeLoan.getDueAmount()) + penaltyFee)) {
//                logger.info("Due Amount in request :{} more than due amount:{} for merchant:{}", amount, (Math.ceil(activeLoan.getDueAmount()) + penaltyFee), merchantId);
//                return new InitiatePaymentResponseDTO("Amount grater than current dues.");
//            }


            Date checkPendingAfterTime = dateTimeUtil.getDatePlusMinutes(dateTimeUtil.getCurrentDate(), -1 * loanPaymentOrderPendingTransactionTimeWindowViaLink);

            // fetch pending transactions in the last loanPaymentOrderPendingTransactionTimeWindow minutes
            final LoanPaymentOrderSlave pendingTransaction =
                    loanPaymentOrderSlaveDao.findTopByOwnerIdAndMerchantIdAndStatusInAndCreatedAtGreaterThan(activeLoan.getId(), activeLoan.getMerchantId(),
                            checkPendingAfterTime);

            if (!ObjectUtils.isEmpty(pendingTransaction)) {
                logger.info("Already a pending transaction exist for loanId : {} with LPO id : {}", activeLoan.getId(), pendingTransaction.getId());
                return new InitiatePaymentResponseDTO("Previous transaction is pending. Try after " + loanPaymentOrderPendingTransactionTimeWindowViaLink + " minutes.");
            }

            if (PaymentType.ADVANCE_EDI.name().equalsIgnoreCase(paymentType)) {
                Integer advanceEdiCount = request.getPayload().getAdvanceEdiCount();
                if (advanceEdiCount == null) {
                    logger.info("advance edi count is not present for merchant:{}", merchantId);
                    return new InitiatePaymentResponseDTO("Advance edi count not present");
                }
                if (advanceEdiCount > activeLoan.getEdiRemainingCount()) {
                    logger.info("advance edi count is more than remaining edi count for merchant:{}", merchantId);
                    return new InitiatePaymentResponseDTO("Advance edi count should be less than remaining edi count");
                }
                Integer advanceEdiAmount = activeLoan.getDueAmount().intValue() + (request.getPayload().getAdvanceEdiCount() * activeLoan.getEdiAmount().intValue());
                if (!amount.equals(advanceEdiAmount)) {
                    logger.info("advance edi amount:{} is not matching for merchant:{}", advanceEdiAmount, merchantId);
                    return new InitiatePaymentResponseDTO("Advance edi amount is not correct");
                }
            }
            LoanPaymentOrder order = new LoanPaymentOrder();
            order.setMerchantId(merchantId);
            order.setOwner("lending_payment_schedule");
            order.setOwnerId(activeLoan.getId());
            order.setAmount(Double.valueOf(amount));
            order.setStatus(CreditConstants.PaymentStatus.INIT.name());
            if (request.getPayload().getSource() != null) {
                order.setSource(request.getPayload().getSource().name());
            }
            if (PaymentType.ADVANCE_EDI.name().equalsIgnoreCase(paymentType)) {
                order.setDescription(PaymentType.ADVANCE_EDI.name());
            }

            order = loanPaymentOrderDao.save(order);
            String orderId = "LOAN" + (10000000L + order.getId());
            order.setOrderId(orderId);
            boolean paymentSuccess = false;
            PgCreateTransactionRequestDTO pgCreateTransactionRequestDTO = new PgCreateTransactionRequestDTO();
            pgCreateTransactionRequestDTO.setOrderAmount(amount.doubleValue());
            pgCreateTransactionRequestDTO.setOrderId(orderId);
            pgCreateTransactionRequestDTO.setNarration("Payment for Order No "+orderId);
            pgCreateTransactionRequestDTO.setPaymentPageHeaderText(PaymentConstants.PG_PAGE_HEADER_TEXT);
            pgCreateTransactionRequestDTO.setAllowedModes(Arrays.asList("CC", "DC","NB","BP","UPI","FP"));
            pgCreateTransactionRequestDTO.setLender(Lender.valueOf(activeLoan.getNbfc()));
            pgCreateTransactionRequestDTO.setRedirectURI(paymentLinkUtil.getPGRedirectionUrl(merchantId,externalLoanId,orderId));
            pgCreateTransactionRequestDTO.setPgWebMode(true);
            pgCreateTransactionRequestDTO.setCheckout("JUSPAY");
            PgCreateTransactionResponseDTO response = apiGatewayService.createPgTransaction(merchantId, pgCreateTransactionRequestDTO);
            if(response != null && response.getStatusCode() != null && "200".equalsIgnoreCase(response.getStatusCode())) {
                paymentSuccess = true;
            }
            if (!paymentSuccess) {
                order.setStatus(CreditConstants.PaymentStatus.FAILED.name());
                order.setDescription("Unable to initiate txn");
                loanPaymentOrderDao.save(order);
                return new InitiatePaymentResponseDTO("Something went wrong.");
            }
            order.setStatus(CreditConstants.PaymentStatus.PENDING.name());
            loanPaymentOrderDao.save(order);
            InitiatePaymentResponseDTO.Data data = new InitiatePaymentResponseDTO.Data(order.getVpa(), order.getUpiIntent(), order.getShortLink(), order.getOrderId(), null, null, null, null, null);
            data.setPaymentLink(response.getData().getPaymentURI());

            // send funnel Event for pg web view initiation
            try {
                FunnelEventDto funnelEventDto = FunnelEventDto.builder()
                        .merchantId(merchantId)
                        .stageId(FunnelEnums.StageId.PAYMENT_LINK)
                        .stageEvent(FunnelEnums.StageEvent.PG_WEB_VIEW_TRIGGERED)
                        .eventSubmissionTime(LocalDateTime.now().withNano(0))
                        .orderId(orderId)
                        .source(FunnelEnums.Source.BACKEND)
                        .build();
                LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchantId);
                if(LoanDetailsConstant.VERSION_V2.equalsIgnoreCase(loanDashboardApiVersion.getApiVersion())){
                    funnelEventDto.setVersion(LoanDetailsConstant.FUNNEL_VERSION_TAG);
                }
                notificationExecutor.execute(() -> funnelService.submitEvent(funnelEventDto));
            }
            catch (Exception e){
                logger.error("Exception in submitting funnel service event for merchantId:{},Exception:{}",merchantId,e.getMessage());
            }
            return new InitiatePaymentResponseDTO(data);
        } catch(Exception ex) {
            logger.error("Exception while initiating payment for merchant id:{},Exception:{} {}",merchantId, ex.getMessage(), Arrays.asList(ex.getStackTrace()));
        }
        return new InitiatePaymentResponseDTO("Something went wrong.");
    }

    public PaymentStatusV3ResponseDTO getPaymentStatusForPaymentLink(String orderId, Long merchantId) {
        logger.info("Received status check request for orderId:{}", orderId);
        try {
            dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
            LoanPaymentOrder order = loanPaymentOrderDao.findByOrderId(orderId);
            if (order == null || !order.getMerchantId().equals(merchantId)) {
                logger.info("No order found for orderId:{}", orderId);
                return new PaymentStatusV3ResponseDTO(false, "Order not found");
            }
            Optional<LendingPaymentSchedule> activeLoan = lendingPaymentScheduleDao.findById(order.getOwnerId());
            Lender lender = Lender.valueOf(activeLoan.get().getNbfc());
            if(CreditConstants.PaymentStatus.PENDING.name().equalsIgnoreCase(order.getStatus())) {
                logger.info("pg status check for merchant id {} and order id {}", order.getMerchantId(), order.getOrderId());
                PgStatusResponse response = apiGatewayService.checkPgStatus(order.getOrderId(), lender, order.getMerchantId());
                if (response != null && response.getStatusCode() != null && "200".equalsIgnoreCase(response.getStatusCode()) && Objects.nonNull(response.getData()) && "SUCCESS".equalsIgnoreCase(response.getData().getPaymentStatus())) {
                    logger.info("Pg txn Status SUCCESS for orderId:{}", order.getOrderId());
                    handlePgCallback(response.getData());
                    order = loanPaymentOrderDao.findByOrderId(orderId);
                } else if (response != null && response.getStatusCode() != null && "200".equalsIgnoreCase(response.getStatusCode()) && Objects.nonNull(response.getData()) && (Status.TransactionStatus.FAILED.name().equalsIgnoreCase(response.getData().getPaymentStatus())
                        || Status.TransactionStatus.FAILURE.name().equalsIgnoreCase(response.getData().getPaymentStatus())
                        || Status.TransactionStatus.CANCELLED.name().equalsIgnoreCase(response.getData().getPaymentStatus()))) {
                    order.setStatus(response.getData().getPaymentStatus());
                    loanPaymentOrderDao.save(order);
                    logger.info("Pg txn Status FAILED/CANCELLED for orderId:{}", order.getOrderId());
                }
            }
            PaymentStatusV3ResponseDTO.Data data = new PaymentStatusV3ResponseDTO.Data();
            data.setPaymentMode(order.getSource());
            data.setPaymentStatus(order.getStatus());
            data.setReferenceNumber(order.getBankRefNo());
            data.setTransferTime(dateFormat.format(order.getUpdatedAt()));
            data.setAmount(order.getAmount());
            data.setOrderId(orderId);
            return new PaymentStatusV3ResponseDTO(true, null, data);
        } catch (Exception e) {
            logger.error("Exception in payment status check", e);
            return new PaymentStatusV3ResponseDTO(false, "Something went wrong");
        }
    }

    private void settleExcessCollectionBalance(Long loanId, List<LendingCollectionExcess> lendingCollectionExcessList){
        if(ObjectUtils.isEmpty(lendingCollectionExcessList))return;
        logger.info("settling excess collection upon foreclosure for loanId:{}, {}", loanId, lendingCollectionExcessList);
        for(LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList){
            lendingCollectionExcess.setDeductedAmount(lendingCollectionExcess.getDeductedAmount() + lendingCollectionExcess.getAmount());
            lendingCollectionExcess.setAmount(0D);
            lendingCollectionExcess.setDeductionCount(lendingCollectionExcess.getDeductionCount() + 1);
            lendingCollectionExcess.setStatus("CLOSED");
            lendingCollectionExcessDao.save(lendingCollectionExcess);
        }
    }

    private void createLendingLedgerForExcessCollectionOnForeclosure(LendingPaymentSchedule activeLoan, List<LendingCollectionExcess> lendingCollectionExcessList){
        if(ObjectUtils.isEmpty(lendingCollectionExcessList))return;
        List<LendingLedger> lendingLedgersListExcessCollection = new ArrayList<>();
        for(LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList){
            String desc = lendingCollectionExcess.getTerminalOrderId() + EXCESS_NACH_TERMINAL_ORDER_ID_SUFFIX + (lendingCollectionExcess.getDeductionCount() + 1);
            String transferType = (CollectionTransferTypeEnum.TRANSFER_BY_BP.name().equalsIgnoreCase(lendingCollectionExcess.getTransferType())) ?  CollectionTransferTypeEnum.TRANSFER_BY_BP.name() : "EXTERNAL";
            String adjustmentMode = LoanPaymentUtil.getExcessAdjustedModeDesc(lendingCollectionExcess.getMode());
            LendingLedger excessCollectionLedger = createLendingLedger(activeLoan, lendingCollectionExcess.getAmount(),
                    lendingCollectionExcess.getAmount(), 0d,  desc,
                    adjustmentMode, transferType, desc, 0D
            );
            lendingLedgersListExcessCollection.add(excessCollectionLedger);
        }
    }

    private boolean checkIfNewPaymentFlowApplicable(String nbfc) {
        return "TRILLIONLOANS".equalsIgnoreCase(nbfc);
    }



    private void handleUpiAutoPaySucessOrder(PgPaymentCallbackDTO request, LendingPullPayment lendingPullPayment) {
        try{
            Optional<LendingPaymentSchedule> optionalLPS =
                    lendingPaymentScheduleDao.findById(lendingPullPayment.getLoanId());
            if (!optionalLPS.isPresent()) {
                log.error("loan does not exist with id:{}", lendingPullPayment.getLoanId());
                return;
            }
            List<PgPaymentCallbackDTO.Payments> list =
                    request.getPayments().stream().
                            filter(payments -> payments.getBreakupType().equals("PG_AMOUNT")).
                            collect(Collectors.toList());
            log.info("payment list is {}", list);
            LendingPaymentSchedule lendingPaymentSchedule = optionalLPS.get();
            if("1LMS".equalsIgnoreCase(lendingPaymentSchedule.getLmsSource())){

                log.info("adjusted or order amount for loanPayment order entity is {} for loanId {}", lendingPullPayment.getDeductedAmount(),lendingPaymentSchedule.getId());
                if (request.getPaymentRefId() != null) {
                    LoanPaymentOrder order = createOrder(lendingPaymentSchedule, lendingPullPayment.getDeductedAmount(), request.getPaymentRefId(), UPI_AUTOPAY_ADJUSTMENT_MODE);


                    if (!list.isEmpty()) {
                        lendingPullPayment.setTerminalOrderId(list.get(0).getTerminalOrderId());
                        order.setTerminalOrderId(list.get(0).getTerminalOrderId());
                        order.setFinalGateway(list.get(0).getFinalGateway());
                    }
                    lendingPullPaymentDao.save(lendingPullPayment);
                    order.setCheckoutType(request.getCheckoutType());
                    loanPaymentOrderDao.save(order);

                    // TODO : call handle callback method
                    log.info("going to call handle callback method for order {} and loanDetails {}",order,lendingPaymentSchedule);
                    handleCallback(convertToPgPaymentCallbackDTO(order));
                }
                return ;
            }
            Double orderAmount = lendingPullPayment.getDeductedAmount();

            log.info("adjusted or order amount for loanPayment order amount is {} for loanId {}", orderAmount, lendingPaymentSchedule.getId());
            if (request.getPaymentRefId() != null) {
                LoanPaymentOrder order = createOrder(lendingPaymentSchedule, orderAmount, request.getPaymentRefId(), UPI_AUTOPAY_ADJUSTMENT_MODE);

                if (!list.isEmpty()) {
                    lendingPullPayment.setTerminalOrderId(list.get(0).getTerminalOrderId());
                    order.setTerminalOrderId(list.get(0).getTerminalOrderId());
                    order.setFinalGateway(list.get(0).getFinalGateway());
                }
                order.setCheckoutType(request.getCheckoutType());
                lendingPullPaymentDao.save(lendingPullPayment);
                loanPaymentOrderDao.save(order);
                // TODO : call handle callback method
                log.info("going to call handle callback method for order {} and loanDetails {} terminal_order_id: {},  pullPayment {}",order,lendingPaymentSchedule, order.getTerminalOrderId(), lendingPullPayment);
                handleCallback(convertToPgPaymentCallbackDTO(order));
            }
        } catch (Exception ex) {
            log.error("Exception Occur while handling callback loanId {} ex {},", lendingPullPayment.getLoanId(), ex.getMessage());
            lendingPullPayment.setStatus("ERROR");  // handle it properly
            lendingPullPaymentDao.save(lendingPullPayment);
        }
    }

    private PaymentCallbackRequestDTO convertToPgPaymentCallbackDTO(LoanPaymentOrder order) {
        PaymentCallbackRequestDTO paymentCallbackRequestDTO = new PaymentCallbackRequestDTO();
        paymentCallbackRequestDTO.setAmount(order.getAmount());
        paymentCallbackRequestDTO.setBankReferenceNumber(order.getBankRefNo());
        paymentCallbackRequestDTO.setTerminalOrderId(order.getTerminalOrderId());
        paymentCallbackRequestDTO.setStatus("SUCCESS");
        paymentCallbackRequestDTO.setOrderId(order.getOrderId());
        return paymentCallbackRequestDTO;
    }
    private LoanPaymentOrder createOrder(LendingPaymentSchedule lendingPaymentSchedule, Double amount, String bankRefNo, String source) {
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

    public boolean checkConsecutiveFailures(List<LendingPullPayment> lendingPullPaymentList, int x) {
        if (lendingPullPaymentList.isEmpty()) {
            return false;
        }

        int failedCount = 0;
        for (LendingPullPayment pullPayment : lendingPullPaymentList) {
            String status = pullPayment.getStatus(); // Assuming status is a String

            if (status.equals("FAILURE") || status.equals("FAILED") || status.equals("CANCELLED")) {
                log.info("failed count incremented for pullpaymentId {} and loanId {}", pullPayment.getId(), pullPayment.getLoanId());
                failedCount++;
                if (failedCount >= x) {
                    return true;
                }
            } else {
                failedCount = 0; // Reset
            }
        }

        return false;
    }

    private void imposePenalCharges(Long orderId, LendingPaymentSchedule activeLoan) {
        log.info("Imposing real time penal charges for orderId:{} and loanId: {}", orderId, activeLoan.getId());
        try {
            Optional<LoanPaymentOrder> optionalLoanPaymentOrder = loanPaymentOrderDao.findById(orderId);
            LoanPaymentOrder loanPaymentOrder = optionalLoanPaymentOrder.orElse(null);
            log.info("Loan Payment Order for orderId {} and loanId: {} is: {}", orderId, activeLoan.getId(), loanPaymentOrder);
            if (loanPaymentOrder != null && "FORECLOSURE".equalsIgnoreCase(loanPaymentOrder.getDescription()) && "PIRAMAL".equalsIgnoreCase(activeLoan.getNbfc()) && !loanUtil.checkLoanCoolOffPeriod(activeLoan.getStartDate())) {
                log.info("Imposing real time penal charges for orderId:{} and loanId: {} for PIRAMAL", orderId, activeLoan.getId());
                double penaltyFee = loanUtil.calculatePiramalPenalty(activeLoan);
                log.info("Calculated penalty fee for PIRAMAL loan: {} is: {}", activeLoan.getId(), penaltyFee);
                if (penaltyFee > 0) {
                    log.info("Creating penalty ledger for PIRAMAL loan: {} with penalty fee: {}", activeLoan.getId(), penaltyFee);
                    loanPaymentLedgerAdjustmentService.creatingPenaltyInPenaltyLedger(activeLoan, penaltyFee, "Penalty Fee", false);
                    loanPaymentLedgerAdjustmentService.createPenaltyLedger(activeLoan, penaltyFee, "PENALTY FEE");
                    loanUtil.savePenalCharges(activeLoan, false, penaltyFee, 0);
                    double lastOverDueAmount = Math.min(activeLoan.getOverdueAmount(), activeLoan.getDueAmount());
                    int overdueEdiCount = 0;
                    double overdueAmount = 0;
                    double existingPenaltyAmount = Objects.nonNull(activeLoan.getTotalPenaltyAmount()) ? activeLoan.getTotalPenaltyAmount() : 0;
                    existingPenaltyAmount += penaltyFee;

                    penaltyFee += Objects.nonNull(activeLoan.getDuePenalty()) ? activeLoan.getDuePenalty() : 0;

                    logger.info("Total Penalty Fee after Penalty on Nach Bounce for loan: {}: {}", activeLoan.getId(), penaltyFee);

                    activeLoan.setDuePenalty(penaltyFee);
                    activeLoan.setTotalPenaltyAmount(existingPenaltyAmount);
                    activeLoan.setOverdueAmount(overdueAmount);
                    activeLoan.setOverdueEdiCount(overdueEdiCount);
                    activeLoan.setLastOverDueAmount(lastOverDueAmount);
                    lendingPaymentScheduleDao.save(activeLoan);
                }
            }
        }catch (Exception e){
            logger.error("Exception in imposing real time penal charges for orderId:{}, stacktrace {} Exception:{}",orderId,Arrays.asList(e.getStackTrace()),e.getMessage());
        }
    }

    public void shiftFromPDP(PerpetualMigrationDTO dto) {
        logger.info("Received request to shift from PDP for merchantId: {}, LoanId: {}", dto.getMerchantId(), dto.getLoanId());

        if (dto.isReverse()) {
            logger.info("Reversing PDP shift for merchantId: {}, LoanId: {}", dto.getMerchantId(), dto.getLoanId());
            reversePDPShift(dto.getLoanId(), dto.getMerchantId());
            return;
        }

        LoanPaymentOrder order = null;
        try {
            Optional<LendingPaymentScheduleLendingCommon> loanOptional = lendingPaymentScheduleLendingCommonDao.findById(dto.getLoanId());
            boolean pdpLoan = loanOptional.isPresent() && Y.name().equalsIgnoreCase(loanOptional.get().getPerpetualDpdAdjusted());

            if (!pdpLoan) {
                logger.info("Loan is not in PDP state for merchant id: {} and loanId: {}", dto.getMerchantId(), dto.getLoanId());
                return;
            }

            LendingPaymentScheduleLendingCommon activeLoan = loanOptional.get();

            order = lockPaymentTemporary(dto.getLoanId(), dto.getMerchantId());

            if (order == null) {
                logger.info("Unable to lock payment for loanId: {} for merchantId: {}", dto.getLoanId(), dto.getMerchantId());
                return;
            }

            changeTransferDateForPDPLoan(activeLoan);

            activeLoan.setPerpetualDpdAdjusted("Z");
            lendingPaymentScheduleLendingCommonDao.save(activeLoan);
        } catch (Exception e) {
            logger.error("Exception while shifting from PDP for loanId: {}, Exception: {} {}", dto.getLoanId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        } finally {
            if (order != null) {
                order.setStatus(CreditConstants.PaymentStatus.FAILED.name());
                loanPaymentOrderDao.save(order);
            }
        }
    }

    private void reversePDPShift(long loanId, long merchantId) {
        Optional<LendingPaymentScheduleLendingCommon> loanOptional = lendingPaymentScheduleLendingCommonDao.findById(loanId);
        boolean pdpLoan = loanOptional.isPresent() && "Z".equalsIgnoreCase(loanOptional.get().getPerpetualDpdAdjusted());
        if (!pdpLoan) {
            logger.info("Loan is not in PDP state for merchant id: {} and loanId: {}", merchantId, loanId);
            return;
        }
        LendingPaymentScheduleLendingCommon activeLoan = loanOptional.get();
        logger.info("Reversing PDP shift for loanId: {} for merchantId: {}", loanId, merchantId);
        activeLoan.setPerpetualDpdAdjusted(Y.name());
        lendingPaymentScheduleLendingCommonDao.save(activeLoan);
    }

    private void changeTransferDateForPDPLoan(LendingPaymentScheduleLendingCommon activeLoan) {
        List<LendingLedger> advanceLedgerList = lendingLedgerDao.findAdvanceEdiLedgerList(activeLoan.getId(), DateTimeUtil.getCurrentDayStartTime());
        if (!CollectionUtils.isEmpty(advanceLedgerList)) {
            logger.info("Advance ledger found for loanId: {} with size: {}", activeLoan.getId(), advanceLedgerList.size());
            advanceLedgerList.stream()
                    .filter(_ledger -> _ledger.getDate() != null
                            && _ledger.getAmount() > 0
                            && _ledger.getDate().after(_ledger.getCreatedAt()))
                    .forEach(_ledger -> {
                        logger.info("Changing transfer date for ledgerId: {} from {} to {}", _ledger.getId(), _ledger.getDate(), DateTimeUtil.getCurrentDayStartTime());
                        _ledger.setDate(DateTimeUtil.getCurrentDayStartTime());
                        lendingLedgerDao.save(_ledger);
                    });
        }


        logger.info("Transfer date changed for all advance ledgers for loanId: {}", activeLoan.getId());
        List<LendingCollectionAudit> lendingCollectionAuditList = lendingCollectionAuditDao.getAllByLoanIdAndStatus(activeLoan.getId(), "PENDING");
        if (!CollectionUtils.isEmpty(lendingCollectionAuditList)) {
            logger.info("Pending collection audit found for loanId: {} with size: {}", activeLoan.getId(), lendingCollectionAuditList.size());
            lendingCollectionAuditList.stream()
                    .filter(_lca -> _lca.getTransferDate() != null
                            && _lca.getAmount() > 0
                            && _lca.getTransferDate().after(_lca.getCreatedAt()))
                    .forEach(lendingCollectionAudit -> {
                        logger.info("Changing transfer date for collection auditId: {} from {} to {}", lendingCollectionAudit.getId(), lendingCollectionAudit.getTransferDate(), DateTimeUtil.getCurrentDayStartTime());
                        lendingCollectionAudit.setTransferDate(DateTimeUtil.getCurrentDayStartTime());
                        lendingCollectionAuditDao.save(lendingCollectionAudit);
                    });
        }


        List<LendingCollectionExcess> lendingCollectionExcessList = lendingCollectionExcessDao.findByMerchantIdAndLoanIdAndStatusOrderByIdAsc(activeLoan.getMerchantId(), activeLoan.getId(), "ACTIVE");
        if (!CollectionUtils.isEmpty(lendingCollectionExcessList)) {
            log.info("Active excess collection found for loanId: {} with size: {}", activeLoan.getId(), lendingCollectionExcessList.size());
            lendingCollectionExcessList.stream()
                    .filter(_lce -> _lce.getCreditDate() != null
                            && _lce.getExcessNachCreditAmount() > 0
                            && _lce.getCreditDate().after(_lce.getCreatedAt()))
                    .forEach(lendingCollectionExcess -> {
                        logger.info("Changing transfer date for excess collection id: {} from {} to {}", lendingCollectionExcess.getId(), lendingCollectionExcess.getCreditDate(), DateTimeUtil.getCurrentDayStartTime());
                        lendingCollectionExcess.setCreditDate(DateTimeUtil.getCurrentDayStartTime());
                        lendingCollectionExcessDao.save(lendingCollectionExcess);
                    });
        }


    }

    private LoanPaymentOrder lockPaymentTemporary(long loanId, long merchantId) {
        Date checkPendingAfterTime = dateTimeUtil.getDatePlusMinutes(dateTimeUtil.getCurrentDate(), -1 * loanPaymentOrderPendingTransactionTimeWindow);

        // fetch pending transactions in the last loanPaymentOrderPendingTransactionTimeWindow minutes
        final LoanPaymentOrderSlave pendingTransaction =
                loanPaymentOrderSlaveDao.findTopByOwnerIdAndMerchantIdAndStatusInAndCreatedAtGreaterThan(loanId, merchantId, checkPendingAfterTime);

        if (!ObjectUtils.isEmpty(pendingTransaction)) {
            logger.info("Already a pending transaction exist for loanId : {} with LPO id : {}", loanId, pendingTransaction.getId());
            return null;
        }

        LoanPaymentOrder order = new LoanPaymentOrder();
        order.setMerchantId(merchantId);
        order.setOwner("lending_payment_schedule");
        order.setOwnerId(loanId);
        order.setAmount(0.0);
        order.setStatus(CreditConstants.PaymentStatus.INIT.name());
        order.setSource("MIGRATION");
        order.setDescription("MIGRATION");
        order = loanPaymentOrderDao.save(order);
        String orderId = "LOAN" + (10000000L + order.getId());
        order.setOrderId(orderId);
        loanPaymentOrderDao.save(order);
        return order;
    }

    public void initiateOnDemandPresentment(long loanId) {
        try {
            CollectionTaskDto dto = CollectionTaskDto.builder()
                    .loanId(loanId)
                    .type(ON_DEMAND_AUTOPAY_PRESENTMENT)
                    .build();
            log.info("Sending on demand presentment for loan id {}", dto);
            confluentKafkaTemplate.send("ondemand-collections-operations", objectMapper.readValue(objectMapper.writeValueAsString(dto), new TypeReference<Map<String, Object>>() {}));
        } catch (Exception e) {
            log.error("initiateOnDemandPresentment exception for loan id {} {} {}", loanId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    public void initiateOnDemandNachPresentment(long loanId) {
        try {
            CollectionTaskDto dto = CollectionTaskDto.builder()
                    .loanId(loanId)
                    .type(ON_DEMAND_NACH_PRESENTMENT)
                    .build();
            log.info("Sending on demand nach presentment for loan id {}", dto);
            confluentKafkaTemplate.send("ondemand-collections-operations", objectMapper.readValue(objectMapper.writeValueAsString(dto), new TypeReference<Map<String, Object>>() {}));
        } catch (Exception e) {
            log.error("initiateOnDemandNachPresentment exception for loan id {} {} {}", loanId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    public void addLien(Long loanId) {
        try {
            CollectionTaskDto dto = CollectionTaskDto.builder()
                    .loanId(loanId)
                    .type(ON_DEMAND_LIEN_ADD)
                    .build();
            log.info("Adding lien for loan id {}", dto);
            confluentKafkaTemplate.send("ondemand-collections-operations", objectMapper.readValue(objectMapper.writeValueAsString(dto), new TypeReference<Map<String, Object>>() {}));
        } catch (Exception e) {
            log.error("Lien add exception for loan id {} {} {}", loanId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    public void removeLien(Long loanId) {
        try {
            CollectionTaskDto dto = CollectionTaskDto.builder()
                    .loanId(loanId)
                    .type(ON_DEMAND_LIEN_REMOVE)
                    .build();
            log.info("Removing lien for loan id {}", dto);
            confluentKafkaTemplate.send("ondemand-collections-operations", objectMapper.readValue(objectMapper.writeValueAsString(dto), new TypeReference<Map<String, Object>>() {}));
        } catch (Exception e) {
            log.error("Lien remove exception for loan id {} {} {}", loanId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    public void migrateAutopayMandate(long loanId) {
        try {
            CollectionTaskDto dto = CollectionTaskDto.builder()
                    .loanId(loanId)
                    .type(AUTOPAY_PRESENTMENT_MIGRATION)
                    .build();
            log.info("Sending autopay presentment for loan id {}", dto);
            confluentKafkaTemplate.send("ondemand-collections-operations", objectMapper.readValue(objectMapper.writeValueAsString(dto), new TypeReference<Map<String, Object>>() {}));
        } catch (Exception e) {
            log.error("migrateAutopayMandate exception for loan id {} {} {}", loanId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    public void notifyViaAppBottomSheet(Long loanId, Long merchantId) {
        try {
            logger.info("Notifying merchant for loanId:{}", loanId);
            long timeInMillis = Calendar.getInstance().getTimeInMillis();
            String eventId = "LENDING_DPD_BS_" + loanId;
//            String eventCategory = "LENDING_DPD";
            String eventCategory = null;
            String eventTag = eventId + "_" + timeInMillis;

            KafkaAppBottomSheetNotificationDTO dto = KafkaAppBottomSheetNotificationDTO.builder()
                    .eventId(eventId)
                    .eventType("add")
                    .client("LENDING")
                    .image(LOAN_REPAY_IMAGE_ICON)
                    .label("Action required")
                    .labelIcon(WARNING_RED_ICON)
                    .heading("Clear your dues immediately")
                    .subHeadings(Arrays.asList(
                            "Your loan payment is overdue.\n Continued delay may result in:\n  ● Negative impact on your Bureau score\n  ● Loss of top-up loan eligibility\n  ● Late payment charges"
                    ))
                    .submitCta(KafkaAppBottomSheetNotificationDTO.SubmitCta.builder()
                            .text("Pay now")
                            .deeplink(dpdNotificationAppDeeplink)
                            .build())
                    .priority(200)
                    .startTime(timeInMillis)
                    .frequency(0) // frequency in minutes - setting low value to visible on each launch
                    .merchantId(merchantId)
                    .storeId(null)
                    .category(eventCategory)
                    .eventTag(eventTag)
                    .closeOnBackdrop(false)
                    .build();
            notificationService.sendBottomSheetNotificationOnHomepage(dto);
            logIntoDbNotification(loanId, merchantId, dto);
        } catch (Exception e) {
            logger.error("Error occurred while notifying merchant for loanId:{}", loanId, e);
        }
    }

    public void removeAppBottomSheet(Long loanId, Long merchantId) {
        try {
            logger.info("Removing DPD bottom sheet for loanId:{}", loanId);
            removeAppBottomSheet(loanId);
        } catch (Exception e) {
            logger.error("Error occurred while notifying merchant for merchantId:{}, error:{}, stack:{}", loanId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    private void logIntoDbNotification(long loanId, long merchantId, KafkaAppBottomSheetNotificationDTO event) {
        try {
            PostDisbursalNotification notification = PostDisbursalNotification.builder()
                    .loanId(loanId)
                    .merchantId(merchantId)
                    .active(true)
                    .type(DPD_EVENT_CATEGORY)
                    .mode(NOTIFICATION_BOTTOM_SHEET)
                    .data(objectMapper.writeValueAsString(event))
                    .build();
            postDisbursalNotificationDao.save(notification);
        } catch (Exception e) {
            logger.error("Error occurred while logging notification into DB for loanId:{} error:{} and stack :{}", loanId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    public void removeAppBottomSheet(long loanId) {
        try {
            postDisbursalNotificationDao.findActiveByLoanIdAndType(loanId, DPD_EVENT_CATEGORY).stream()
                    .filter(_event -> (_event != null && NOTIFICATION_BOTTOM_SHEET.equalsIgnoreCase(_event.getMode())))
                    .forEach(notification -> {
                        try {
                            logger.info("Removing app bottom sheet for loanId:{} notificationId:{}", loanId, notification.getId());
                            KafkaAppBottomSheetNotificationDTO dto = objectMapper.readValue(notification.getData(), KafkaAppBottomSheetNotificationDTO.class);
                            dto.setEventType("remove");
                            notificationService.sendBottomSheetNotificationOnHomepage(dto);
                            notification.setActive(false);
                            postDisbursalNotificationDao.save(notification);
                            logger.info("Removed app bottom sheet for loanId:{} notificationId:{}", loanId, notification.getId());
                        } catch (Exception e) {
                            logger.error("Error occurred while removing app bottom sheet for loanId:{} notificationId:{}, error:{} stack:{}", loanId, notification.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
                        }
                    });
        } catch (Exception e) {
            logger.error("Error occurred while removing app bottom sheet for loanId:{}, error:{} stack:{}", loanId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }
}