package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.common.Constants.DeductionStatusEnum;
import com.bharatpe.lending.common.Kafka.Producer.ConfluentKafkaProducer;
import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.common.dao.LendingPullPaymentDao;
import com.bharatpe.lending.common.dao.LoanDpdDao;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.common.entity.LendingPullPayment;
import com.bharatpe.lending.common.query.dao.LendingPullPaymentDaoSlave;
import com.bharatpe.lending.common.query.dao.LendingPaymentScheduleDaoSlave;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.CommonConstants;
import com.bharatpe.lending.constant.PaymentConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.enums.Client;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanStatus;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.exceptions.InvalidRequestException;
import com.bharatpe.lending.lendingplatform.lms.constant.Constants;
import com.bharatpe.lending.config.AutoPayUPIErrorConfig;
import com.bharatpe.lending.loanV3.revamp.enums.UpiAutoPayStatus;
import com.bharatpe.lending.service.helper.MandateRegistrationHelper;
import com.bharatpe.lending.util.ErrorDescriptionMapper;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.bharatpe.lending.common.Constants.AutoPayCheckoutEnum.*;
import static com.bharatpe.lending.common.Constants.AutoPayStatusEnum.REVOKED;


@Service
@Slf4j
public class AutoPayUPIService {
    private static final String MANDATE_ORDER_TYPE = "MANDATE";
    private static final Double MANDATE_ORDER_AMOUNT = 1D;
    private static final String PAYMENT_MODE_TEXT = "Select Payment Mode";
    private static final int DEFAULT_FREQUENCY = 2;
    public static List<String> AUTO_PAY_UPI_APPLICABLE_LOAN_TYPES = Arrays.asList(LoanType.REGULAR.name(), LoanType.TOPUP.name(), LoanType.NTB.name());


    private static final int DEFAULT_FREQUENCY_FOR_NEW_APPLICATIONS = 1;
    @Autowired
    private LendingPullPaymentDao lendingPullPaymentDao;

    @Autowired
    private LendingPullPaymentDaoSlave lendingPullPaymentDaoSlave;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    MerchantService merchantService;

    @Autowired
    AutoPayUPIDao autoPayUPIDao;

    @Autowired
    MandateRegistrationHelper mandateRegistrationHelper;

    @Autowired
    private LendingPaymentScheduleDaoSlave lendingPaymentScheduleDaoSlave;

    @Autowired
    private ErrorDescriptionMapper errorDescriptionMapper;


    @Autowired
    LoanUtil loanUtil;

    @Value(("${pg.android.version.merchant.plugin:7.1.9}"))
    String androidVersionMerchantPlugin;

    @Value(("${pg.android.version.direct.cashfree:7.1.9}"))
    String androidVersionDirectCashfree;

    @Value("${pg.ios.version.direct.cashfree:310}")
    private String iosVersionDirectCashfree;

    @Value("${upi.autopay.tat.exceeded.wait.time:14}")
    Long upiAutopayTatExceededWaitTime;

    @Value("${upi.autopay.alt.tat.exceeded.wait.time:5}")
    Long upiAutopayAltTatExceededWaitTime;

    @Value(("${pg.ios.version:254}"))
    Long iosVersion;

    @Value("${redirection.deeplink.autopayupi:bharatpe://dynamic?key=easy-loans-v2-qa}")
    private String redirectionDeeplinkAutopayUpi;

    @Value("${secondary.mandate.redirection.deeplink.autopayupi:bharatpe://dynamic?key=loan-dashboard-qa}")
    private String redirectionDeeplinkAltAutopayUpi;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    LoanDpdDao loanDpdDao;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    AutoPayUPIErrorConfig autoPayUPIErrorConfig;

    @Autowired
    ConfluentKafkaProducer confluentKafkaProducer;

    @Value("${bottom.sheet.topic:max_home_page_upi_mandate}")
    private String bottomSheetTopic;

    @Value("${push_data.homepage.carousel.topic:max_home_page_merchant_carousel}")
    private String pushDataToHomePageCarouselTopic;

    @Value("${whitelisted.auto.pay.upi.mandate.plugin.lenders:false}")
    private String autoPayUPIMandatePluginLenders;

    @Value("${auto.pay.upi.mandate.plugin.enabled:false}")
    private boolean autoPayUPIMandatePluginEnabled;

    @Value("${auto.pay.upi.mandate.direct.cashfree.enabled:false}")
    private boolean autoPayUPIMandateDirectCashfreeEnabled;

    @Value("${allowed.lenders.for.direct.cashfree:false}")
    private List<String> allowedLendersForDirectCashfree;

    @Value("${autopay.upi.skip.lenders:MUTHOOT,UGRO,PAYU}")
    private List<String> autopayUpiSkipLenders;

    @Value("${auto.pay.upi.mandate.direct.cashfree.percent.rollout:10}")
    private int autoPayUPIMandateDirectCashfreePercentRollout;

    @Value("${minimum.autoPayUpi.expiry.months:42}")
    private int minimumAutoPayUpiExpiryMonths;

    @Value("${auto.pay.upi.mandate.direct.cashfree.percent.rollout.ios:10}")
    private int autoPayUPIMandateDirectCashfreePercentRolloutIos;

    @Value("${secondary.auto.pay.upi.mandate.rollout.percent:0}")
    private int secondaryAutoPayUpiMandateRolloutPercent ;

    public FetchTxnResponseDto fetchTransaction(BasicDetailsDto merchant, Long loanId,
                                                int pageNo, int pageSize) {

        final Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by("id").descending());

        FetchTxnResponseDto responseDto = new FetchTxnResponseDto();
        List<LendingPullPayment> fetchTxn = lendingPullPaymentDao.findByMerchantIdAndLoanId(merchant.getId(), loanId, pageable);
        if (fetchTxn.size() == 0) {
            return new FetchTxnResponseDto();
        }
        List<FetchTxnResponseDto.Presentment> presentments = new ArrayList<>();

        for (int pullPayment = 0; pullPayment < fetchTxn.size(); pullPayment++) {
            if (fetchTxn.get(pullPayment).getMerchantId().equals(merchant.getId())) {
                FetchTxnResponseDto.Presentment presentmentData = new FetchTxnResponseDto.Presentment();
                presentmentData.setDate(fetchTxn.get(pullPayment).getTxnDate());
                presentmentData.setPresentmentAmt(fetchTxn.get(pullPayment).getDeductedAmount());
                String status = fetchTxn.get(pullPayment).getStatus();
                presentmentData.setStatus(status);
                if (!"Success".equalsIgnoreCase(status)) {
                    String errorDescription = fetchTxn.get(pullPayment).getErrorDescription();
                    String mappedFailureReason = errorDescriptionMapper.mapToUserMessage(errorDescription);
                    presentmentData.setFailureReason(mappedFailureReason);
                }
                presentments.add(presentmentData);
            }
            responseDto.setData(presentments);
        }
        return responseDto;
    }

    public Boolean updateFrequencyForMandate(BasicDetailsDto merchant, UpdateFrequencyRequestDto dto) {
        boolean flag = false;
        LendingPaymentSchedule lps = lendingPaymentScheduleDao.findById(dto.getLoanId()).get();
        Long applicationId = lps.getApplicationId();
        AutoPayUPI entity = autoPayUPIDao.findTop1ByMerchantIdAndApplicationIdOrderByIdDesc(merchant.getId(), applicationId).get();
        if (entity != null) {
            log.info("entity application Id is {} ", entity.getApplicationId());
            if (entity.getMandateId() != null) {
                entity.setFrequency(dto.getFrequency());
                autoPayUPIDao.save(entity);
                flag = true;
            }
        }
        return flag;
    }

    public String handleMandatePgCallback(PgPaymentCallbackDTO request, AutoPayUPI autoPayUPI) {
        log.info("Received mandate callback request for order ID {} : {}", request.getMandate().getOrderId(), request);
        if(autoPayUPI == null){
            autoPayUPI = autoPayUPIDao.findTop1ByOrderId(request.getMandate().getOrderId());
        }
        try {
            if (autoPayUPI == null) {
                log.error("No order for order id {}", request.getOrderId());
                return "OK";
            }
            if (PaymentConstants.UPI_AUTOPAY_TERMINAL_STATES.contains(autoPayUPI.getStatus())) {
                log.info("Mandate for merchant id {} and order id {} has already reached terminal state", autoPayUPI.getMerchantId(), request.getOrderId());
                return "OK";
            }
            if (request.getPaymentStatus() != null) {
                if ("FAILURE".equalsIgnoreCase(request.getMandate().getStatus())) {
                    autoPayUPI.setStatus(AutoPayStatusEnum.FAILED);
                    if(request.getMandate() != null) {
                        autoPayUPI.setErrorMessage(request.getMandate().getErrorDescription());
                        autoPayUPI.setErrorCode(request.getMandate().getErrorCode());

                        if(request.getInternalErrorCode() != null){
                            autoPayUPI.setErrorCode(request.getInternalErrorCode());
                        }
                        if(request.getInternalErrorMessage() != null){
                            autoPayUPI.setErrorMessage(request.getInternalErrorMessage());
                        }
                    }
                } else {
                    autoPayUPI.setStatus(AutoPayStatusEnum.valueOf(request.getMandate().getStatus()));
                    if(AutoPayStatusEnum.ACTIVE.name().equalsIgnoreCase(request.getMandate().getStatus())) {
                        pushRemoveEvent(autoPayUPI.getMerchantId());
                    }
                }
                if (request.getMandate().getMandateId() != null) {
                    autoPayUPI.setMandateId(request.getMandate().getMandateId());
                }

                // Update umrn and payer VPA if available
                setAdditionalAutopayDetails(request, autoPayUPI);
                autoPayUPIDao.save(autoPayUPI);
            }
        } catch (Exception ex) {
            if (autoPayUPI != null) {
                autoPayUPIDao.save(autoPayUPI);
            }
            log.error("Exception in register callback for order id {}", request.getOrderId(), ex);
        }
        return "OK";
    }

    private void pushRemoveEvent(Long merchantId) {
        PushDataToHomepageCarouselDto pushDataToHomepageCarouselDto = new PushDataToHomepageCarouselDto();
        pushDataToHomepageCarouselDto.setEvent_id("LENDING_HOMEPAGE_CAROUSEL_" + merchantId);
        pushDataToHomepageCarouselDto.setMerchant_id(BigInteger.valueOf(merchantId));
        pushDataToHomepageCarouselDto.setEvent_type("remove");
        pushDataToHomepageCarouselDto.setClient("LENDING");

        confluentKafkaProducer.sendMessage(pushDataToHomePageCarouselTopic, pushDataToHomepageCarouselDto);
        log.info("Sent remove event of cic banner for merchant {}", merchantId);

        BottomSheetEvent bottomSheetEvent = new BottomSheetEvent();
        bottomSheetEvent.setEventId("Lending_Auto_Pay_" + merchantId);
        bottomSheetEvent.setMerchantId(BigInteger.valueOf(merchantId));
        bottomSheetEvent.setEventType("remove");
        bottomSheetEvent.setClient("LENDING");

        confluentKafkaProducer.sendMessage(bottomSheetTopic, bottomSheetEvent);
        log.info("Sent remove event of bottom sheet for merchant {}", merchantId);
    }

    public MandateUPIStatusResponse checkStatus(BasicDetailsDto merchant, String orderId) {
        log.info("Status check request for mandate register");
        AutoPayUPI mandateApplication =
                autoPayUPIDao.findByMerchantIdAndOrderId(merchant.getId(), orderId).get();

        log.info("mandate application is {} ", mandateApplication);
        if (mandateApplication == null) {
            throw new
                    InvalidRequestException
                    (String.format("Invalid application : %s", orderId));
        }

        if (mandateApplication.getStatus().equals(AutoPayStatusEnum.PENDING) ||
                mandateApplication.getStatus().equals(AutoPayStatusEnum.INIT))
        {
            Date createdMandateDate = mandateApplication.getCreatedAt();
            long diffMinutes = calculateTimeDiff(createdMandateDate);
            log.info("diffMinutes is {}", diffMinutes);
            if (diffMinutes >= upiAutopayTatExceededWaitTime) {
                mandateApplication.setStatus(AutoPayStatusEnum.FAILED);
                mandateApplication.setErrorCode("TAT_EXCEEDED");
                log.info("marking status for mandate register as failed due to tat for merchant id {} application id {}",
                        mandateApplication.getMerchantId(), mandateApplication.getApplicationId());
                autoPayUPIDao.save(mandateApplication);
                MandateUPIStatusResponse.Data data = new MandateUPIStatusResponse.Data(mandateApplication.getOrderId()
                        , mandateApplication.getApplicationId(), mandateApplication.getStatus());
                return new MandateUPIStatusResponse(data);
            }
        }

        if (AutoPayStatusEnum.PENDING.name().equalsIgnoreCase(String.valueOf(mandateApplication.getStatus()))) {
            log.info("status of application {} ", mandateApplication.getStatus());
            PgStatusResponse response =
                    apiGatewayService.checkPgStatusForMandate
                            (mandateApplication.getOrderId(),
                                    Lender.valueOf(mandateApplication.getLender()), mandateApplication.getMerchantId());


           if(response == null || response.getData() == null || response.getData() == null || response.getData().getMandate() == null) {
               log.error("No response from PG for mandateId:{} and response {} ", mandateApplication.getOrderId(),response);
               MandateUPIStatusResponse.Data data = new MandateUPIStatusResponse.Data(mandateApplication.getOrderId()
                       , mandateApplication.getApplicationId(), mandateApplication.getStatus());
               return new MandateUPIStatusResponse(data);
           }
            log.info(" PG response is {}", response.getData());
            if ("ACTIVE".equalsIgnoreCase(response.getData().getMandate().getStatus())) {
                log.info("Pg txn Status Check for mandateId:{}", mandateApplication.getOrderId());
                mandateApplication.setStatus(AutoPayStatusEnum.valueOf(response.getData().getMandate().getStatus()));
                handleMandatePgCallback(response.getData(), mandateApplication);
            } else if ("FAILURE".equalsIgnoreCase(response.getData().getPaymentStatus()) ||
                    "FAILURE".equalsIgnoreCase(response.getData().getMandate().getStatus())) {
                mandateApplication.setStatus(AutoPayStatusEnum.valueOf("FAILED"));
                mandateApplication.setErrorMessage(response.getData().getMandate().getErrorDescription() == null ? response.getData().getErrorDescription() : response.getData().getMandate().getErrorDescription() );
                mandateApplication.setErrorCode(response.getData().getMandate().getErrorCode() == null ? response.getData().getErrorCode() : response.getData().getMandate().getErrorCode());
                setAdditionalAutopayDetails(response.getData(), mandateApplication);

                if(response.getData().getInternalErrorCode() != null){
                    mandateApplication.setErrorCode(response.getData().getInternalErrorCode());
                }
                if(response.getData().getInternalErrorMessage() != null){
                    mandateApplication.setErrorMessage(response.getData().getInternalErrorMessage());
                }

                log.info("Pg txn Status FAILED/CANCELLED for orderId:{}", mandateApplication.getOrderId());
                autoPayUPIDao.save(mandateApplication);
            }

        }

        if("ACTIVE".equalsIgnoreCase(mandateApplication.getStatus().name())){
            log.info("Updating Lending Application Upi Auto Status for application id: {}", mandateApplication.getApplicationId());
            Optional<LendingApplication> optionalLendingApplication = lendingApplicationDao.findById(mandateApplication.getApplicationId());

            if (optionalLendingApplication.isPresent()) {
                log.info("Updating auto pay upi status for application id: {}", mandateApplication.getApplicationId());
                LendingApplication lendingApplication = optionalLendingApplication.get();
                lendingApplication.setUpiAutopayStatus(UpiAutoPayStatus.APPROVED.name());
                lendingApplicationDao.save(lendingApplication);
            }
        }

        MandateUPIStatusResponse.Data data = new MandateUPIStatusResponse.Data(mandateApplication.getOrderId()
                , mandateApplication.getApplicationId(), mandateApplication.getStatus());
        return new MandateUPIStatusResponse(data);
    }

    private void setAdditionalAutopayDetails(PgPaymentCallbackDTO request, AutoPayUPI autoPayUPI) {
        if (request.getMandate() != null && request.getMandate().getUmrn() != null) {
            autoPayUPI.setUmrn(request.getMandate().getUmrn());
        } else {
            log.error("UMRN not present in mandate for order id {}", request.getOrderId());
        }
        if (request.getPayments() != null && !request.getPayments().isEmpty() && request.getPayments().get(0).getPayerVpa() != null) {
            autoPayUPI.setPayerVpa(request.getPayments().get(0).getPayerVpa());
        } else {
            log.error("Payer VPA not present in payments for order id {}", request.getOrderId());
        }
    }

    public long calculateTimeDiff(Date createdMandateDate) {
        log.info("createdMandateDate is {}", createdMandateDate);
        SimpleDateFormat format = new SimpleDateFormat("yy/MM/dd HH:mm:ss");
        long diffMinutes=0l;
        Date date = new Date();
        log.info("date is {}", date);
        format.format(date);
        String currentDateTime = format.format(date);

        Date d1 = null;
        try {
            d1 = format.parse((currentDateTime));

            log.info("d1 {}", d1);
            long diff;
            diff = createdMandateDate.getTime() - d1.getTime();
            if (diff<0)
            {
                diff = Math.abs(diff);
            }
            log.info("diff is {}", diff);
            diffMinutes = diff / (60 * 1000);
            log.info("diff minutes is {}", diffMinutes);
            return diffMinutes;

        }
        catch (ParseException e) {
            log.error("e is {}", e);
        }
        return diffMinutes;
    }


    @Deprecated
    public UPIRegisterResponseDto registerUPI(BasicDetailsDto merchantBasicDetails, RequestDTO<UPIRegisterRequestDto> requestDto) {
        log.info("Received initiate UPI Register request  for merchant {} : {}", merchantBasicDetails.getId(), requestDto);
        Optional<LendingPaymentSchedule> activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndIdAndStatus(merchantBasicDetails.getId(), requestDto.getPayload().getLoanId(), "ACTIVE");

        UPIRegisterResponseDto.Data data = null;
        if (!activeLoan.isPresent()) {
            log.info("No active loan found for merchant id {}", merchantBasicDetails.getId());
            return new UPIRegisterResponseDto();
        }

        if (activeLoan.get().getNbfc().equalsIgnoreCase(Lender.LDC.name())) {
            List<String> statusList = new ArrayList<>();
            statusList.add(AutoPayStatusEnum.PENDING.name());
            statusList.add(AutoPayStatusEnum.SUCCESS.name());
            AutoPayUPI entity = autoPayUPIDao.findTop1ByApplicationIdAndStatusOrderByIdDesc(activeLoan.get().getApplicationId(), activeLoan.get().getNbfc(), statusList);
            if (entity != null) {
                log.info("For this application Id, mandate is already in progress {} ", activeLoan.get().getApplicationId());
                throw new InvalidRequestException(String.format(" For this application Id, mandate is already in progress: %s", activeLoan.get().getApplicationId()));
            }


            log.info("active loan merchantId {}", activeLoan.get().getMerchantId());

            if (merchantBasicDetails.getId().equals(activeLoan.get().getMerchantId())) {
                AutoPayUPI autoPayUPI = new AutoPayUPI();
                autoPayUPI.setAmount(1D);
                autoPayUPI.setMerchantId(merchantBasicDetails.getId());
                autoPayUPI.setLender(activeLoan.get().getLoanApplication().getLender());
                autoPayUPI.setStatus(AutoPayStatusEnum.INIT);
                autoPayUPI.setApplicationId(activeLoan.get().getApplicationId());
                autoPayUPI.setFrequency(DEFAULT_FREQUENCY);
                autoPayUPI.setGateway("CASHFREE");
                autoPayUPI = autoPayUPIDao.save(autoPayUPI);
                autoPayUPI.setOrderId("Auto-UPI" + autoPayUPI.getId());

                log.info("autoPayUPI Is {}", autoPayUPI);

                AutoPayUPIRegisterPgRequestDto registerPgRequest = new AutoPayUPIRegisterPgRequestDto();
                registerPgRequest.setLender(Lender.valueOf(activeLoan.get().getNbfc()));
                registerPgRequest.setPaymentPageHeaderText(PAYMENT_MODE_TEXT);
                registerPgRequest.setOrderAmount(MANDATE_ORDER_AMOUNT);
                registerPgRequest.setOrderType(MANDATE_ORDER_TYPE);
                registerPgRequest.setCustomerId(merchantBasicDetails.getId());
                registerPgRequest.setCustomerSubId(activeLoan.get().getMerchantStoreId());

                registerPgRequest.setNarration("Register mandate with orderId" + autoPayUPI.getOrderId());
                registerPgRequest.setOrderId(autoPayUPI.getOrderId());
                registerPgRequest.setCheckout("JUSPAY");

                Calendar currentTimeNow = Calendar.getInstance();
                System.out.println("Current time now : " + currentTimeNow.getTime());
                currentTimeNow.add(Calendar.MINUTE, 10);
                Date tenMinsFromNow = currentTimeNow.getTime();
                long epochMandateStartDate = tenMinsFromNow.getTime();
                registerPgRequest.setMandateStartDate(epochMandateStartDate);
                registerPgRequest.setRedirectURIDeeplink("bharatpe://dynamic?key=loan-dashboard&openfrom=pg&orderId=" + autoPayUPI.getOrderId());

               /* if (loanUtil.isInternalMerchant(merchantBasicDetails.getId()) ||
                        easyLoanUtil.percentScaleUp(merchantBasicDetails.getId(), apiGatewayService.upiPercent)) {
                    log.info("pg flow enabling for internal merchants with app version for merchant: {}", merchantBasicDetails.getId());
                    registerPgRequest.setCheckout("JUSPAY");
                }*/
                AutoPayRegisterPgResponseDto registerPgResponseDto = apiGatewayService.createPgTransaction(merchantBasicDetails.getId(), registerPgRequest);

                if (registerPgResponseDto != null && registerPgResponseDto.getStatusCode() != null
                        && "200".equalsIgnoreCase(registerPgResponseDto.getStatusCode())) {
                    autoPayUPI.setStatus(AutoPayStatusEnum.PENDING);
                    autoPayUPI.setPaymentURlDeepLink(registerPgResponseDto.getData().getPaymentURIDeeplink());
                    autoPayUPIDao.save(autoPayUPI);
                }
                data = new UPIRegisterResponseDto.Data(autoPayUPI.getAmount(), autoPayUPI.getOrderId(),
                        autoPayUPI.getPaymentURlDeepLink());
            }
        }

        return new UPIRegisterResponseDto(data);
    }

    public UPIRegisterResponseDto registerUPIForNewApplication(BasicDetailsDto merchantBasicDetails, AutoUPIMandateRegisterRequestDto requestDto, LendingApplication lendingApplication,String checkoutType) {
        log.info("Received initiate UPI Register request for new Application  for merchant {} : {}", merchantBasicDetails.getId(), requestDto);

        //Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(requestDto.getApplicationId());

        UPIRegisterResponseDto.Data data = null;
        if (lendingApplication == null) {
            log.info("No application found for merchant id {} with applicationId {}", merchantBasicDetails.getId(), requestDto.getApplicationId());
            return new UPIRegisterResponseDto();
        }

        if (!loanUtil.isApplicationEligibleForAutoPayUpi(lendingApplication.getLender(), lendingApplication.getMerchantId(), lendingApplication.getLoanAmount())) {
            log.info("not eligible for autopayUpi  merchant id {} with applicationId {}", merchantBasicDetails.getId(), requestDto.getApplicationId());
            return new UPIRegisterResponseDto();
        }

        Optional<BankDetailsDto> merchantBankDetail = merchantService.fetchMerchantBankDetails(merchantBasicDetails.getId());

        if (!merchantBankDetail.isPresent()) {
            log.error("failed to get merchantBankDetails for merchantId : {} and applicationId : {}", merchantBasicDetails.getId(), requestDto.getApplicationId());
            return new UPIRegisterResponseDto();
        }


            List<String> statusList = new ArrayList<>();
            statusList.add(AutoPayStatusEnum.PENDING.name());
            statusList.add(AutoPayStatusEnum.ACTIVE.name());
            AutoPayUPI autoPayUPIExistingEntity = autoPayUPIDao.findTop1ByApplicationIdAndStatusOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender(), statusList);
            if (autoPayUPIExistingEntity != null) {
                log.info("For this application Id, mandate is already in progress {} ", lendingApplication.getId());
                throw new InvalidRequestException(String.format(" For this application Id, mandate is already in progress: %s", lendingApplication.getId()));
            }

            if (merchantBasicDetails.getId().equals(lendingApplication.getMerchantId())) {
                String gateWay = DR_CASHFREE.name().equalsIgnoreCase(checkoutType) ? DR_CASHFREE.name() : JS_CASHFREE.name();
                AutoPayUPI autoPayUPI = createAutoPayUPIEntity(lendingApplication, DEFAULT_FREQUENCY_FOR_NEW_APPLICATIONS, gateWay, merchantBankDetail.get());

                //Doing only for Cashfree as per the requirement
//                autoPayUPI.setIsAutoPayUpiDeduction(Constants.DISBURSED_LOAN.equals(lendingApplication.getLoanDisbursalStatus())
//                        ? DeductionStatusEnum.HARD_QR_DEDUCTION.name() : DeductionStatusEnum.AUTO_PAY_UPI.name());
                autoPayUPI.setIsAutoPayUpiDeduction(DeductionStatusEnum.AUTO_PAY_UPI.name());
                autoPayUPI.setStandaloneAutopaySetup(Constants.DISBURSED_LOAN.equals(lendingApplication.getLoanDisbursalStatus()));
                autoPayUPI = autoPayUPIDao.save(autoPayUPI);
                autoPayUPI.setOrderId("Auto-UPI" + autoPayUPI.getId());
                autoPayUPI.setMaxMandateAmount(autoPayUPI.isStandaloneAutopaySetup() ? loanUtil.getMaxMandateAmount(merchantBasicDetails.getId()) : null);
                log.info("autoPayUPI Is {}", autoPayUPI);

                AutoPayUPIRegisterPgRequestDto registerPgRequest = new AutoPayUPIRegisterPgRequestDto();
                registerPgRequest.setLender(Lender.valueOf(lendingApplication.getLender()));
                registerPgRequest.setPaymentPageHeaderText(PAYMENT_MODE_TEXT);
                registerPgRequest.setOrderAmount(MANDATE_ORDER_AMOUNT);
                registerPgRequest.setOrderType(MANDATE_ORDER_TYPE);
                registerPgRequest.setCustomerId(merchantBasicDetails.getId());

                registerPgRequest.setNarration("Register mandate with orderId" + autoPayUPI.getOrderId());
                registerPgRequest.setOrderId(autoPayUPI.getOrderId());
                registerPgRequest.setCheckout(DR_CASHFREE.name().equalsIgnoreCase(checkoutType) ? "CASHFREE" : "JUSPAY");
                registerPgRequest.setAccountNumber(merchantBankDetail.get().getAccountNumber());
                registerPgRequest.setIfscCode(merchantBankDetail.get().getIfsc());

                Calendar currentTimeNow = Calendar.getInstance();
                System.out.println("Current time now : " + currentTimeNow.getTime());
                currentTimeNow.add(Calendar.MINUTE, 10);
                Date tenMinsFromNow = currentTimeNow.getTime();
                long epochMandateStartDate = tenMinsFromNow.getTime();
                registerPgRequest.setMandateStartDate(epochMandateStartDate);
                long mandateEndTime = mandateRegistrationHelper.getMandateEndTimeInMillis(epochMandateStartDate, lendingApplication.getLender(), lendingApplication.getTenureInMonths(), merchantBasicDetails.getId());
                registerPgRequest.setMandateEndDate(mandateEndTime);
                String upiAutopayRedirectUrl = "&wroute=upi-autopay";
                registerPgRequest.setRedirectURIDeeplink(redirectionDeeplinkAutopayUpi + upiAutopayRedirectUrl + "&openfrom=pg&orderId=" + autoPayUPI.getOrderId() + "&applicationId=" + lendingApplication.getId());
                registerPgRequest.setMaxMandateAmount(
                        autoPayUPI.isStandaloneAutopaySetup()
                                ? loanUtil.getMaxMandateAmount(merchantBasicDetails.getId())
                                : null
                );

                AutoPayRegisterPgResponseDto registerPgResponseDto = apiGatewayService.createPgTransaction(merchantBasicDetails.getId(), registerPgRequest);

                if (registerPgResponseDto != null && registerPgResponseDto.getStatusCode() != null
                  && "200".equalsIgnoreCase(registerPgResponseDto.getStatusCode())) {
                    autoPayUPI.setStatus(AutoPayStatusEnum.PENDING);
                    autoPayUPI.setPaymentURlDeepLink(registerPgResponseDto.getData().getPaymentURIDeeplink());
                    autoPayUPI.setMandateEndDate(new Date(registerPgRequest.getMandateEndDate()));
                    autoPayUPI.setMaxMandateAmount(registerPgRequest.getMaxMandateAmount());
                    autoPayUPIDao.save(autoPayUPI);
                }
                if(registerPgResponseDto == null){
                    autoPayUPI.setStatus(AutoPayStatusEnum.FAILED);
                    autoPayUPI.setErrorCode("API_ERROR");
                    autoPayUPI.setErrorCode("API_ERROR");
                    autoPayUPIDao.save(autoPayUPI);
                }
                data = new UPIRegisterResponseDto.Data(autoPayUPI.getAmount(), autoPayUPI.getOrderId(),
                  autoPayUPI.getPaymentURlDeepLink());
            }

        return new UPIRegisterResponseDto(data);
    }


    public UPIRegisterResponseDto registerUPIForNewApplicationMandatePlugin(BasicDetailsDto merchantBasicDetails, AutoUPIMandateRegisterRequestDto requestDto, LendingApplication lendingApplication) {
        log.info("Received initiate UPI Register request for new Application  for merchant {} : {}", merchantBasicDetails.getId(), requestDto);

        //Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(requestDto.getApplicationId());

        UPIRegisterResponseDto.Data data = null;
        if (lendingApplication == null) {
            log.info("No application found for merchant id {} with applicationId {}", merchantBasicDetails.getId(), requestDto.getApplicationId());
            return UPIRegisterResponseDto.mandateRegistrationFailureResponse();
        }

        if (!loanUtil.isApplicationEligibleForAutoPayUpi(lendingApplication.getLender(), lendingApplication.getMerchantId(), lendingApplication.getLoanAmount())) {
            log.info("not eligible for autopayUpi  merchant id {} with applicationId {}", merchantBasicDetails.getId(), requestDto.getApplicationId());
            return UPIRegisterResponseDto.mandateRegistrationFailureResponse();
        }

        Optional<BankDetailsDto> merchantBankDetail = merchantService.fetchMerchantBankDetails(merchantBasicDetails.getId());

        if (!merchantBankDetail.isPresent()) {
            log.error("failed to get merchantBankDetails for merchantId : {} and applicationId : {}", merchantBasicDetails.getId(), requestDto.getApplicationId());
            return UPIRegisterResponseDto.mandateRegistrationFailureResponse();
        }


        List<String> statusList = new ArrayList<>();
        statusList.add(AutoPayStatusEnum.PENDING.name());
        statusList.add(AutoPayStatusEnum.ACTIVE.name());
        AutoPayUPI autoPayUPIExistingEntity = autoPayUPIDao.findTop1ByApplicationIdAndStatusOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender(), statusList);
        if (autoPayUPIExistingEntity != null) {
            log.info("For this application Id, mandate is already in progress {} ", lendingApplication.getId());
            throw new InvalidRequestException(String.format(" For this application Id, mandate is already in progress: %s", lendingApplication.getId()));
        }

        if (merchantBasicDetails.getId().equals(lendingApplication.getMerchantId())) {
            AutoPayUPI autoPayUPI = createAutoPayUPIEntity(lendingApplication, DEFAULT_FREQUENCY_FOR_NEW_APPLICATIONS, UNITY.name(), merchantBankDetail.get());
            autoPayUPI.setIsAutoPayUpiDeduction(DeductionStatusEnum.AUTO_PAY_UPI.name());
            autoPayUPI = autoPayUPIDao.save(autoPayUPI);
            autoPayUPI.setOrderId("Auto-UPI" + autoPayUPI.getId());

            log.info("autoPayUPI Is {}", autoPayUPI);


            AutoPayUPIRegisterPgRequestDtoNew registerPgRequest = new AutoPayUPIRegisterPgRequestDtoNew();
            registerPgRequest.setOrderId(autoPayUPI.getOrderId());
            registerPgRequest.setOrderAmount(MANDATE_ORDER_AMOUNT);
            registerPgRequest.setPaymentPageHeaderText(PAYMENT_MODE_TEXT);
            registerPgRequest.setRedirectURIDeeplink(redirectionDeeplinkAutopayUpi + "&wroute=key-factor-statement&openfrom=pg&orderId=" + autoPayUPI.getOrderId() + "&applicationId=" + lendingApplication.getId());
            registerPgRequest.setNarration("Register mandate with orderId" + autoPayUPI.getOrderId());
            registerPgRequest.setCheckout("UNITY");
            registerPgRequest.setIsPgWebMode("false");
            BankDetail bankDetail = new BankDetail();
            bankDetail.setBankCode(merchantBankDetail.get().getBankCode());
            bankDetail.setIfscCode(merchantBankDetail.get().getIfsc());
            bankDetail.setAccountNumber(merchantBankDetail.get().getAccountNumber());
            registerPgRequest.setBankDetail(bankDetail);
            registerPgRequest.setOrderType(MANDATE_ORDER_TYPE);
            registerPgRequest.setCustomerId(merchantBasicDetails.getId());
//            registerPgRequest.setCustomerSubId(activeLoan.get().getMerchantStoreId());
            registerPgRequest.setLender(Lender.valueOf(lendingApplication.getLender()));
            Calendar currentTimeNow = Calendar.getInstance();
            System.out.println("Current time now : " + currentTimeNow.getTime());
            currentTimeNow.add(Calendar.MINUTE, 10);
            Date tenMinsFromNow = currentTimeNow.getTime();
            long epochMandateStartDate = tenMinsFromNow.getTime();
            registerPgRequest.setMandateStartDate(epochMandateStartDate);
            registerPgRequest.setMandateEndDate(epochMandateStartDate + 157680000000L);
            registerPgRequest.setMaxMandateAmount(15000.0);

            AutoPayRegisterPgResponseDto registerPgResponseDto = apiGatewayService.createPgTransaction(merchantBasicDetails.getId(), registerPgRequest);

            if (registerPgResponseDto != null && registerPgResponseDto.getStatusCode() != null
                    && "200".equalsIgnoreCase(registerPgResponseDto.getStatusCode())) {
                autoPayUPI.setStatus(AutoPayStatusEnum.PENDING);
                autoPayUPI.setPaymentURlDeepLink(registerPgResponseDto.getData().getPaymentURIDeeplink());
                autoPayUPI.setMandateEndDate(new Date(registerPgRequest.getMandateEndDate()));
                autoPayUPIDao.save(autoPayUPI);
            }
            if(registerPgResponseDto == null){
                autoPayUPI.setStatus(AutoPayStatusEnum.FAILED);
                autoPayUPI.setErrorCode("API_ERROR");
                autoPayUPI.setErrorCode("API_ERROR");
                autoPayUPIDao.save(autoPayUPI);
            }
            data = new UPIRegisterResponseDto.Data(autoPayUPI.getAmount(), autoPayUPI.getOrderId(),
                    autoPayUPI.getPaymentURlDeepLink());
        }

        return new UPIRegisterResponseDto(data);
    }

    public List<String> getAllowedLenderForUPIAutoPay(String autoPaayUpiAllowedLender) {
        List<String> allowedLender = new ArrayList<>();
        if (StringUtils.hasLength(autoPaayUpiAllowedLender)) {
            try {
                allowedLender = Arrays.asList(autoPaayUpiAllowedLender.split(","));
            } catch (Exception e) {
                log.error("Error in parsing allowedAutoPayUpiLender ",e);
            }
        }
        return allowedLender;
    }


    public UPIRegisterResponseDto registerMandate(BasicDetailsDto merchant, RequestDTO<AutoUPIMandateRegisterRequestDto> requestDto) {
        if (requestDto.getPayload() == null || requestDto.getMeta() == null || requestDto.getPayload().getApplicationId() == null) {
            log.info("missing param merchant {} requestDto {}", merchant.getId(), requestDto);
            return UPIRegisterResponseDto.mandateRegistrationFailureResponse();
        }
        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(requestDto.getPayload().getApplicationId());

        if (!lendingApplicationOptional.isPresent()) {
            log.info("No application found for merchant id {} with applicationId {}", merchant.getId(), requestDto.getPayload().getApplicationId());
            return UPIRegisterResponseDto.mandateRegistrationFailureResponse();
        }

        // overriding data with realtime data
        requestDto.getPayload().setLender(lendingApplicationOptional.get().getLender());
         switch (determineCheckoutType(requestDto,lendingApplicationOptional.get().getMerchantId())){
             case  "UNITY":
             return registerUPIForNewApplicationMandatePlugin(merchant, requestDto.getPayload(), lendingApplicationOptional.get());
             case  "DR_CASHFREE":
             return registerUPIForNewApplication(merchant, requestDto.getPayload(), lendingApplicationOptional.get(), DR_CASHFREE.name());
             default:
                 return registerUPIForNewApplication(merchant, requestDto.getPayload(), lendingApplicationOptional.get(),JS_CASHFREE.name());
         }
//        if(autoPayUPIMandatePluginEnabled && requestDto.getMeta() != null && "android".equalsIgnoreCase(requestDto.getMeta().getClient()) &&
//                getAllowedLenderForUPIAutoPay(autoPayUPIMandatePluginLenders).contains(requestDto.getPayload().getLender()) && isVersionGreaterOrEqual(requestDto.getMeta().getDeviceInfo().getAppVersion(), androidVersionMerchantPlugin)) {
//            return registerUPIForNewApplicationMandatePlugin(merchant, requestDto.getPayload(), lendingApplicationOptional.get());
//        }
//        return registerUPIForNewApplication(merchant, requestDto.getPayload(), lendingApplicationOptional.get());
    }

    public UPIRegisterResponseDto registerAltMandate(BasicDetailsDto merchant, AutoPayUPIAltMandateRegisterRequest requestDto) {
        if (requestDto == null) {
            log.info("Missing param merchant {} requestDto {}", merchant.getId(), requestDto);
            return UPIRegisterResponseDto.mandateRegistrationFailureResponse();
        }

        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(requestDto.getApplicationId());
        if (!lendingApplicationOptional.isPresent()) {
            log.info("No application found for merchantId {} with applicationId {}", merchant.getId(), requestDto.getApplicationId());
            return UPIRegisterResponseDto.mandateRegistrationFailureResponse();
        }

        LendingApplication lendingApplication = lendingApplicationOptional.get();
        if (!merchant.getId().equals(lendingApplication.getMerchantId())) {
            log.info("Merchant mismatch for merchantId {} with applicationId {}", merchant.getId(), requestDto.getApplicationId());
            return UPIRegisterResponseDto.mandateRegistrationFailureResponse();
        }

        List<AutoPayUPI> existingAutoPayUPI = autoPayUPIDao.findByApplicationIdAndAltMandateAndStatus(requestDto.getApplicationId(), true,
                Arrays.asList(AutoPayStatusEnum.PENDING.name(), AutoPayStatusEnum.ACTIVE.name(), AutoPayStatusEnum.INIT.name() ));
        if(existingAutoPayUPI != null && !existingAutoPayUPI.isEmpty()) {
            log.info("Alt mandate already exists for applicationId {}", requestDto.getApplicationId());
            return UPIRegisterResponseDto.mandateRegistrationFailureResponse();
        }

        BankDetailsDto bankDetailsDto = new BankDetailsDto();
        bankDetailsDto.setBeneficiaryName(requestDto.getAccountName());
        bankDetailsDto.setBankName(requestDto.getBankName());
        bankDetailsDto.setAccountNumber(requestDto.getAccountNumber());
        bankDetailsDto.setIfsc(requestDto.getIfsc());

        AutoPayUPI autoPayUPI = createAutoPayUPIEntity(lendingApplication, DEFAULT_FREQUENCY_FOR_NEW_APPLICATIONS, DR_CASHFREE.name(), bankDetailsDto);
        autoPayUPI.setIsAutoPayUpiDeduction(DeductionStatusEnum.AUTO_PAY_UPI.name());
        autoPayUPI.setStandaloneAutopaySetup(Constants.DISBURSED_LOAN.equals(lendingApplication.getLoanDisbursalStatus()));
        autoPayUPI.setAltMandate(true);
        autoPayUPI = autoPayUPIDao.save(autoPayUPI);
        autoPayUPI.setOrderId("Auto-UPI" + autoPayUPI.getId());
        autoPayUPI.setMaxMandateAmount(autoPayUPI.isStandaloneAutopaySetup() ? loanUtil.getMaxMandateAmount(merchant.getId()) : null);

        AutoPayUPIRegisterPgRequestDto registerPgRequest = new AutoPayUPIRegisterPgRequestDto();
        registerPgRequest.setLender(Lender.valueOf(lendingApplication.getLender()));
        registerPgRequest.setPaymentPageHeaderText(PAYMENT_MODE_TEXT);
        registerPgRequest.setOrderAmount(MANDATE_ORDER_AMOUNT);
        registerPgRequest.setOrderType(MANDATE_ORDER_TYPE);
        registerPgRequest.setCustomerId(merchant.getId());
        registerPgRequest.setNarration("Register mandate with orderId" + autoPayUPI.getOrderId());
        registerPgRequest.setOrderId(autoPayUPI.getOrderId());
        registerPgRequest.setCheckout("CASHFREE");
        registerPgRequest.setAccountNumber(bankDetailsDto.getAccountNumber());
        registerPgRequest.setIfscCode(bankDetailsDto.getIfsc());

        Calendar currentTimeNow = Calendar.getInstance();
        currentTimeNow.add(Calendar.MINUTE, 10);
        Date tenMinsFromNow = currentTimeNow.getTime();
        long epochMandateStartDate = tenMinsFromNow.getTime();
        registerPgRequest.setMandateStartDate(epochMandateStartDate);
        long mandateEndTime = mandateRegistrationHelper.getMandateEndTimeInMillis(epochMandateStartDate, lendingApplication.getLender(), lendingApplication.getTenureInMonths(), merchant.getId());
        registerPgRequest.setMandateEndDate(mandateEndTime);
        registerPgRequest.setRedirectURIDeeplink(redirectionDeeplinkAltAutopayUpi);
        registerPgRequest.setMaxMandateAmount(autoPayUPI.isStandaloneAutopaySetup() ? loanUtil.getMaxMandateAmount(merchant.getId()) : null);

        AutoPayRegisterPgResponseDto registerPgResponseDto = apiGatewayService.createPgTransaction(merchant.getId(), registerPgRequest);
        if (registerPgResponseDto != null && registerPgResponseDto.getStatusCode() != null
                && "200".equalsIgnoreCase(registerPgResponseDto.getStatusCode())) {
            autoPayUPI.setStatus(AutoPayStatusEnum.PENDING);
            autoPayUPI.setPaymentURlDeepLink(registerPgResponseDto.getData().getPaymentURIDeeplink());
            autoPayUPI.setMandateEndDate(new Date(registerPgRequest.getMandateEndDate()));
            autoPayUPI.setMaxMandateAmount(registerPgRequest.getMaxMandateAmount());
            autoPayUPIDao.save(autoPayUPI);
        }
        if (registerPgResponseDto == null) {
            autoPayUPI.setStatus(AutoPayStatusEnum.FAILED);
            autoPayUPI.setErrorCode("API_ERROR");
            autoPayUPIDao.save(autoPayUPI);
        }

        UPIRegisterResponseDto.Data data = new UPIRegisterResponseDto.Data(autoPayUPI.getAmount(), autoPayUPI.getOrderId(),
                autoPayUPI.getPaymentURlDeepLink());
        return new UPIRegisterResponseDto(data);
    }

    public void revokeMandate(LendingApplication loanApplicatioj, AutoPayUPI autoPayUpi) {
        AutoPayUPIMandatePgRequestDto request = new AutoPayUPIMandatePgRequestDto();
        request.setMandateId(autoPayUpi.getMandateId());
        request.setLender(Lender.valueOf((autoPayUpi.getLender())));
        if (!"LDC".equalsIgnoreCase(loanApplicatioj.getLender())) {
            String status = apiGatewayService.executeAutoPayUPIMandateRevokeWithPg(autoPayUpi.getMerchantId(), request, autoPayUpi);
            if (REVOKED.name().equalsIgnoreCase(status)) {
                autoPayUpi.setStatus(REVOKED);
                autoPayUPIDao.save(autoPayUpi);
            }
            if(status == null){
                autoPayUpi.setErrorMessage("MANDATE REVOKE FAILED API ERROR");
                autoPayUPIDao.save(autoPayUpi);
            }
        }
    }

    public static boolean isVersionGreaterOrEqual(String version1, String version2) {
        if (!version1.contains(".") && !version2.contains(".")) {
            int v1 = Integer.parseInt(version1);
            int v2 = Integer.parseInt(version2);
            return v1 >= v2;
        }
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int v1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int v2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (v1 < v2) {
                return false;
            } else if (v1 > v2) {
                return true;
            }
        }
        return true;
    }

    public String determineCheckoutType(RequestDTO<AutoUPIMandateRegisterRequestDto> requestDto, Long merchantId) {
        if (autoPayUPIMandatePluginEnabled && requestDto.getMeta() != null && Client.ANDROID.name().equalsIgnoreCase(requestDto.getMeta().getClient()) &&
                getAllowedLenderForUPIAutoPay(autoPayUPIMandatePluginLenders).contains(requestDto.getPayload().getLender()) &&
                isVersionGreaterOrEqual(requestDto.getMeta().getDeviceInfo().getAppVersion(), androidVersionMerchantPlugin)) {
            return UNITY.name();
        }

        if (autoPayUPIMandateDirectCashfreeEnabled && requestDto.getMeta() != null && Client.ANDROID.name().equalsIgnoreCase(requestDto.getMeta().getClient()) &&
                allowedLendersForDirectCashfree.contains(requestDto.getPayload().getLender()) &&
                isVersionGreaterOrEqual(requestDto.getMeta().getDeviceInfo().getAppVersion(), androidVersionDirectCashfree) && easyLoanUtil.percentScaleUp(merchantId, autoPayUPIMandateDirectCashfreePercentRollout)) {
            return DR_CASHFREE.name();
        }

        if (autoPayUPIMandateDirectCashfreeEnabled && requestDto.getMeta() != null && Client.IOS.name().equalsIgnoreCase(requestDto.getMeta().getClient()) &&
                allowedLendersForDirectCashfree.contains(requestDto.getPayload().getLender()) &&
                isVersionGreaterOrEqual(requestDto.getMeta().getDeviceInfo().getAppVersion(), iosVersionDirectCashfree) && easyLoanUtil.percentScaleUp(merchantId, autoPayUPIMandateDirectCashfreePercentRolloutIos)) {
            log.info("cashfree is selected as checkout type for ios client and merchantId {}", merchantId);
            return DR_CASHFREE.name();
        }

        return JS_CASHFREE.name();
    }

    public Boolean checkConsecutiveError(Long merchantId) {
        try {
            // Validate merchantId
            if (merchantId == null) {
                log.error("Merchant ID cannot be null");
                return false;
            }
            log.info("Checking consecutive errors for merchantId: {}", merchantId);

            String mode = "AUTOPAYUPI";
            Date now = new Date();
            Date todayStart = getStartOfDay(0);
            List<String> errors = lendingPullPaymentDaoSlave.findDistinctErrorsForDate(merchantId, mode, todayStart, now);
            if (errors == null || errors.isEmpty()) {
                log.info("No error found today for merchantId: {}", merchantId);
                return false;
            }
            log.info("Found {} unique error description(s) for merchantId: {}", errors.size(), merchantId);

            // Check each error for consecutive days
            for (String error_description : errors) {
                if (!StringUtils.hasText(error_description)) {
                    continue;
                }
                int requiredDays = autoPayUPIErrorConfig.getConsecutiveDaysForError(error_description);
                Date startDate = getStartOfDay(requiredDays - 1);
                log.info("Checking error '{}' for merchantId: {} from {} to {}", error_description, merchantId, startDate, now);
                // Count distinct dates where this error occurred in the date range
                Long dayCount = lendingPullPaymentDaoSlave.countDistinctDaysForError(merchantId, error_description, startDate, now);
                // If number of distinct dates equals requiredDays, it means the error came for all consecutive days
                if (dayCount != null && dayCount == requiredDays) {
                    log.info("Found consecutive error for merchantId: {}, error: {}, days: {}", merchantId, error_description, requiredDays);
                    return true;
                }
            }
            log.info("No consecutive errors found for merchantId: {}", merchantId);
            return false;
        } catch (Exception e) {
            log.error("Error while checking consecutive errors for merchantId: {}", merchantId, e);
            return false;
        }
    }

    private Date getStartOfDay(int daysAgo) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_MONTH, -daysAgo);
        return calendar.getTime();
    }

    public AutoPayRequiredDto isUPIAutoPayRequired(BasicDetailsDto merchant) {
        AutoPayRequiredDto autoPayRequired = new AutoPayRequiredDto();
        if (merchant == null || merchant.getId() == null) {
            autoPayRequired.setMessage("Merchant id is required");
            autoPayRequired.setUpiAutoPayRequired(false);
            return autoPayRequired;
        }

        LendingPaymentScheduleSlave lendingPaymentSchedule = lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatus(merchant.getId(), Collections.singletonList(LoanStatus.ACTIVE.name()));
        if (ObjectUtils.isEmpty(lendingPaymentSchedule) || lendingPaymentSchedule.getApplicationId() == null) {
            log.info("No active loan found for merchant id {}", merchant.getId());
            autoPayRequired.setMessage("No active loan found");
            autoPayRequired.setUpiAutoPayRequired(false);
            return autoPayRequired;
        }
        Optional<LendingApplication> activeApplication = lendingApplicationDao.findById(lendingPaymentSchedule.getApplicationId());
        if (!activeApplication.isPresent()) {
            log.error("No active application found for applicationId: {}", lendingPaymentSchedule.getApplicationId());
            autoPayRequired.setMessage("No active loan found");
            autoPayRequired.setUpiAutoPayRequired(false);
            return autoPayRequired;
        }
        log.info("Found active loan for merchant id {} and application id {} with lender {}", merchant.getId(), lendingPaymentSchedule.getApplicationId(), lendingPaymentSchedule.getNbfc());
        boolean dpdCheck = mandateRegistrationHelper.loanDpdChecks(lendingPaymentSchedule, activeApplication.get());
        AutoPayUPI autoPayUPI = autoPayUPIDao.findByApplicationIdAndStatusAndLender(lendingPaymentSchedule.getApplicationId(), AutoPayStatusEnum.ACTIVE.name(), activeApplication.get().getLender());
        if (ObjectUtils.isEmpty(autoPayUPI) && dpdCheck) {
            log.info("No active UPI AutoPay mandate found for merchant id {} and application id {}", merchant.getId(), lendingPaymentSchedule.getApplicationId());
            autoPayRequired.setMessage("No active UPI AutoPay mandate found");
            autoPayRequired.setUpiAutoPayRequired(true);
            return autoPayRequired;
        } else {
            log.info("Found active UPI AutoPay mandate for merchant id {} and application id {}", merchant.getId(), lendingPaymentSchedule.getApplicationId());
            autoPayRequired.setMessage("Active UPI AutoPay mandate found OR loan dpd checks failed");
            autoPayRequired.setUpiAutoPayRequired(false);
            return autoPayRequired;
        }

    }

    public AutoPayUPI cloneAutoPayUpiEntityForNewApplication(AutoPayUPI autoPayUpi, Long applicationId) {
        if(ObjectUtils.isEmpty(autoPayUpi) || Objects.equals(autoPayUpi.getApplicationId(), applicationId)) {
            return autoPayUpi;
        }
        AutoPayUPI clonedAutoPayUpi = new AutoPayUPI();
        BeanUtils.copyProperties(autoPayUpi, clonedAutoPayUpi, "id", "createdAt", "updatedAt");
        clonedAutoPayUpi.setApplicationId(applicationId);
        clonedAutoPayUpi.setIsAutoPayUpiDeduction(DeductionStatusEnum.AUTO_PAY_UPI.name());
        clonedAutoPayUpi.setStandaloneAutopaySetup(false);
        clonedAutoPayUpi.setReuseApplicationId(Optional.ofNullable(autoPayUpi.getReuseApplicationId()).orElse(autoPayUpi.getApplicationId()));
        return autoPayUPIDao.save(clonedAutoPayUpi);
    }

    public boolean isEligibleForUpiAutoPaySkip(LendingApplication lendingApplication, AutoPayUPI existingAutoPay) {
        log.info("Checking eligibility for UPI Autopay skip for application id: {}", lendingApplication.getId());
        if (ObjectUtils.isEmpty(existingAutoPay) || ObjectUtils.isEmpty(existingAutoPay.getMandateEndDate())) {
            log.info("Not eligible: No existing autopay or mandate end date.");
            return false;
        }
        if(lendingApplication.getId().equals(existingAutoPay.getApplicationId())){
            log.info("upiautopay is already done on current application: {} for merchant: {}, autopay_id is: {}",
                    lendingApplication.getId(), lendingApplication.getMerchantId(), existingAutoPay.getId());
            return true;
        }

        if (ObjectUtils.isEmpty(existingAutoPay.getAccountNumber())) {
            log.info("Not eligible: Existing autopay entity does not have account number for application id: {}", lendingApplication.getId());
            return false;
        }

        if(autopayUpiSkipLenders.contains(lendingApplication.getLender())){
            if(ObjectUtils.isEmpty(existingAutoPay.getUmrn())){
                log.info("Not eligible: Existing autopay entity does not have UMRN for application id: {}", lendingApplication.getId());
                return false;
            }

            if(ObjectUtils.isEmpty(existingAutoPay.getPayerVpa())){
                log.info("Not eligible: Existing autopay entity does not have Payer VPA for application id: {}", lendingApplication.getId());
                return false;
            }

            if(!ObjectUtils.isEmpty(existingAutoPay.getMaxMandateAmount()) && existingAutoPay.getMaxMandateAmount() < 15000D){
                log.info("Not eligible: Existing autopay entity max mandate amount is less than required for application id: {}", lendingApplication.getId());
                return false;
            }
        }

        Optional<BankDetailsDto> merchantBankDetailOpt = merchantService.fetchMerchantBankDetails(lendingApplication.getMerchantId());
        if (!merchantBankDetailOpt.isPresent() || ObjectUtils.isEmpty(merchantBankDetailOpt.get().getAccountNumber())) {
            log.info("Not eligible: No merchant bank details or account number for application id: {}", lendingApplication.getId());
            return false;
        }

        boolean isSameLender = lendingApplication.getLender().equalsIgnoreCase(existingAutoPay.getLender());
        boolean isSameBankAccount = merchantBankDetailOpt.get().getAccountNumber().equalsIgnoreCase(existingAutoPay.getAccountNumber());
        long differenceInMonths = (existingAutoPay.getMandateEndDate().getTime() - System.currentTimeMillis()) / (1000L * 60 * 60 * 24 * 30);

        if (differenceInMonths > minimumAutoPayUpiExpiryMonths && isSameLender && isSameBankAccount) {
            log.info("Eligible for UPI Autopay skip for application id {}", lendingApplication.getId());
            return true;
        }
        log.info("Not eligible for UPI Autopay skip for application id {}", lendingApplication.getId());
        return false;
    }

    private AutoPayUPI createAutoPayUPIEntity(LendingApplication application, int frequency, String gateway, BankDetailsDto merchantBankDetail) {
        AutoPayUPI autoPayUPI = AutoPayUPI.builder()
                .amount(MANDATE_ORDER_AMOUNT)
                .merchantId(application.getMerchantId())
                .lender(application.getLender())
                .status(AutoPayStatusEnum.INIT)
                .applicationId(application.getId())
                .frequency(frequency)
                .gateway(gateway)
                .build();
        if (!ObjectUtils.isEmpty(merchantBankDetail)) {
            autoPayUPI.setBankName(merchantBankDetail.getBankName());
            autoPayUPI.setBeneficiaryName(merchantBankDetail.getBeneficiaryName());
            autoPayUPI.setAccountNumber(merchantBankDetail.getAccountNumber());
            autoPayUPI.setIfscCode(merchantBankDetail.getIfsc());
            autoPayUPI.setAccountType(merchantBankDetail.getAccountType());
        }
        return autoPayUPI;
    }

    public UPIAltEligibilityDto checkAltMandateEligibility(Long applicationId, Long merchantId, String token) {
        log.info("Checking UPI Alt Mandate Eligibility for merchantId: {}, applicationId: {}", merchantId, applicationId);
        UPIAltEligibilityDto upiAltEligibility = new UPIAltEligibilityDto();
        UPIAltEligibilityDto.Data data = new UPIAltEligibilityDto.Data();

        if(easyLoanUtil.percentScaleUp(merchantId,secondaryAutoPayUpiMandateRolloutPercent)) {
            CollectionAltAccountResponse altMandateEligibility = apiGatewayService.checkAltAccountEligibility(merchantId, token);
            if (altMandateEligibility != null && altMandateEligibility.isAction()) {
                data.setEligible(true);
            }
            List<AutoPayUPI> autoPayUPI = autoPayUPIDao.findByApplicationIdAndAltMandateAndStatus(applicationId, true,
                    Arrays.asList(AutoPayStatusEnum.PENDING.name(), AutoPayStatusEnum.FAILED.name(), AutoPayStatusEnum.ACTIVE.name(), AutoPayStatusEnum.INIT.name()));
            if (autoPayUPI != null && !autoPayUPI.isEmpty()) {
                UPIAltEligibilityDto.UpiMandateDetails upiMandateDetails = getUpiMandateDetails(merchantId, autoPayUPI.get(0));
                data.setUpiMandateDetails(upiMandateDetails);
            }
            //  Set Previous Active mandate details
            List<AutoPayUPI> previousAutoPayUPI = autoPayUPIDao.findByApplicationIdAndAltMandateAndStatus(applicationId, false, Collections.singletonList(AutoPayStatusEnum.ACTIVE.name()));
            if (previousAutoPayUPI != null && !previousAutoPayUPI.isEmpty()) {
                log.info("Previous active mandate for merchantId: {} with AutoPayUPIId: {}", merchantId, previousAutoPayUPI.get(0).getId());
                data.setPrevUpiMandateDetails(getPrevUpiMandateDetails(previousAutoPayUPI.get(0)));
            }
        }

        upiAltEligibility.setData(data);
        log.info("UPI Alt Mandate Eligibility for merchantId: {} is {}", merchantId, upiAltEligibility);
        return upiAltEligibility;
    }

    private UPIAltEligibilityDto.UpiMandateDetails getUpiMandateDetails(Long merchantId, AutoPayUPI autoPayUPI) {
        UPIAltEligibilityDto.UpiMandateDetails upiMandateDetails = new UPIAltEligibilityDto.UpiMandateDetails();
        AutoPayStatusEnum status = autoPayUPI.getStatus();
        upiMandateDetails.setBankName(autoPayUPI.getBankName());
        upiMandateDetails.setAccountName(autoPayUPI.getBeneficiaryName());
        upiMandateDetails.setAccountNumber(autoPayUPI.getAccountNumber());
        upiMandateDetails.setIfsc(autoPayUPI.getIfscCode());

        if (AutoPayStatusEnum.PENDING.equals(status) || AutoPayStatusEnum.INIT.equals(status)) {
//          Checking status with PG
            MandateUPIStatusResponse mandateUPIStatusResponse = checkStatusForAltMandate(merchantId, autoPayUPI);
            if (mandateUPIStatusResponse.getData() != null && mandateUPIStatusResponse.getData().getStatus() != null) {
                status = mandateUPIStatusResponse.getData().getStatus();
            }
        }
//      Setting response status based on current status and PG response
        if(AutoPayStatusEnum.ACTIVE.equals(status)){
            upiMandateDetails.setStatus(CommonConstants.SUCCESS);
        } else if(AutoPayStatusEnum.FAILED.equals(status)){
            upiMandateDetails.setStatus(CommonConstants.FAILED);
        } else {
            upiMandateDetails.setStatus(CommonConstants.INPROGRESS);
        }
        return upiMandateDetails;
    }

    private MandateUPIStatusResponse checkStatusForAltMandate(Long merchantId, AutoPayUPI mandateApplication) {
        log.info("Status check request for alt mandate {}", mandateApplication);
        try {
            if (mandateApplication.getStatus().equals(AutoPayStatusEnum.PENDING) ||
                    mandateApplication.getStatus().equals(AutoPayStatusEnum.INIT)) {
                Date createdMandateDate = mandateApplication.getCreatedAt();
                long diffMinutes = calculateTimeDiff(createdMandateDate);
                log.info("diffMinutes is {} for merchantId: {}", diffMinutes, merchantId);
                if (diffMinutes >= upiAutopayAltTatExceededWaitTime) {
                    mandateApplication.setStatus(AutoPayStatusEnum.FAILED);
                    mandateApplication.setErrorCode("TAT_EXCEEDED");
                    log.info("Marking status for alt mandate register as failed due to tat for merchantId {} applicationId {}",
                            mandateApplication.getMerchantId(), mandateApplication.getApplicationId());
                    autoPayUPIDao.save(mandateApplication);
                    MandateUPIStatusResponse.Data data = new MandateUPIStatusResponse.Data(mandateApplication.getOrderId()
                            , mandateApplication.getApplicationId(), mandateApplication.getStatus());
                    return new MandateUPIStatusResponse(data);
                }
            }

            if (AutoPayStatusEnum.PENDING.name().equalsIgnoreCase(String.valueOf(mandateApplication.getStatus()))) {
                PgStatusResponse response =
                        apiGatewayService.checkPgStatusForMandate
                                (mandateApplication.getOrderId(),
                                        Lender.valueOf(mandateApplication.getLender()), mandateApplication.getMerchantId());

                if (response == null || response.getData() == null || response.getData().getMandate() == null) {
                    log.error("No response from PG for mandateId: {} and response: {} ", mandateApplication.getOrderId(), response);
                    MandateUPIStatusResponse.Data data = new MandateUPIStatusResponse.Data(mandateApplication.getOrderId()
                            , mandateApplication.getApplicationId(), mandateApplication.getStatus());
                    return new MandateUPIStatusResponse(data);
                }
                log.info("PG response is {}", response.getData());
                if ("ACTIVE".equalsIgnoreCase(response.getData().getMandate().getStatus())) {
                    log.info("Alt Mandate Pg txn Status Check for mandateId: {}", mandateApplication.getOrderId());
                    mandateApplication.setStatus(AutoPayStatusEnum.valueOf(response.getData().getMandate().getStatus()));
                    // Update umrn and payer VPA if available
                    setAdditionalAutopayDetails(response.getData(), mandateApplication);
                    if (response.getData().getMandate().getMandateId() != null) {
                        mandateApplication.setMandateId(response.getData().getMandate().getMandateId());
                    }
                } else if ("FAILURE".equalsIgnoreCase(response.getData().getPaymentStatus()) ||
                        "FAILURE".equalsIgnoreCase(response.getData().getMandate().getStatus())) {
                    mandateApplication.setStatus(AutoPayStatusEnum.valueOf("FAILED"));
                    mandateApplication.setErrorMessage(response.getData().getMandate().getErrorDescription() == null ? response.getData().getErrorDescription() : response.getData().getMandate().getErrorDescription());
                    mandateApplication.setErrorCode(response.getData().getMandate().getErrorCode() == null ? response.getData().getErrorCode() : response.getData().getMandate().getErrorCode());
                    setAdditionalAutopayDetails(response.getData(), mandateApplication);

                    if (response.getData().getInternalErrorCode() != null) {
                        mandateApplication.setErrorCode(response.getData().getInternalErrorCode());
                    }
                    if (response.getData().getInternalErrorMessage() != null) {
                        mandateApplication.setErrorMessage(response.getData().getInternalErrorMessage());
                    }

                    log.info("Alt mandate Pg txn Status FAILED/CANCELLED for orderId: {}", mandateApplication.getOrderId());
                }
                autoPayUPIDao.save(mandateApplication);
            }
        } catch (Exception e) {
            log.error("Exception while checking PG status for mandateId: {}, merchantId: {}, error: {}", mandateApplication.getOrderId(), merchantId, e.getMessage(), e);
        }

        MandateUPIStatusResponse.Data data = new MandateUPIStatusResponse.Data(mandateApplication.getOrderId()
                , mandateApplication.getApplicationId(), mandateApplication.getStatus());
        return new MandateUPIStatusResponse(data);
    }


    private UPIAltEligibilityDto.UpiMandateDetails getPrevUpiMandateDetails(AutoPayUPI previousAutoPayUPI) {
        UPIAltEligibilityDto.UpiMandateDetails previousMandateDetails = new UPIAltEligibilityDto.UpiMandateDetails();
        previousMandateDetails.setBankName(previousAutoPayUPI.getBankName());
        previousMandateDetails.setAccountNumber(previousAutoPayUPI.getAccountNumber());
        previousMandateDetails.setIfsc(previousAutoPayUPI.getIfscCode());
        previousMandateDetails.setAccountName(previousAutoPayUPI.getBeneficiaryName());
        previousMandateDetails.setStatus(CommonConstants.SUCCESS);
        return previousMandateDetails;
    }
}
