package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.common.Constants.DeductionStatusEnum;
import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.common.dao.LendingPullPaymentDao;
import com.bharatpe.lending.common.dao.LoanDpdDao;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.common.entity.LendingPullPayment;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.exceptions.InvalidRequestException;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

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

    private static final int DEFAULT_FREQUENCY_FOR_NEW_APPLICATIONS = 1;
    @Autowired
    private LendingPullPaymentDao lendingPullPaymentDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    MerchantService merchantService;

    @Autowired
    AutoPayUPIDao autoPayUPIDao;

    @Autowired
    LoanUtil loanUtil;

    @Value(("${pg.android.version.merchant.plugin:7.1.9}"))
    String androidVersionMerchantPlugin;

    @Value(("${pg.android.version.direct.cashfree:7.1.9}"))
    String androidVersionDirectCashfree;

    @Value(("${pg.ios.version:254}"))
    Long iosVersion;

    @Value("${redirection.deeplink.autopayupi:bharatpe://dynamic?key=easy-loans-v2-qa}")
    private String redirectionDeeplinkAutopayUpi;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    LoanDpdDao loanDpdDao;

    @Autowired
    APIGatewayService apiGatewayService;

    @Value("${whitelisted.auto.pay.upi.mandate.plugin.lenders:false}")
    private String autoPayUPIMandatePluginLenders;

    @Value("${auto.pay.upi.mandate.plugin.enabled:false}")
    private boolean autoPayUPIMandatePluginEnabled;

    @Value("${auto.pay.upi.mandate.direct.cashfree.enabled:false}")
    private boolean autoPayUPIMandateDirectCashfreeEnabled;

    @Value("${allowed.lenders.for.direct.cashfree:false}")
    private String allowedLendersForDirectCashfree;

    @Value("${auto.pay.upi.mandate.direct.cashfree.percent.rollout:10}")
    private int autoPayUPIMandateDirectCashfreePercentRollout;

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
                presentmentData.setStatus(fetchTxn.get(pullPayment).getStatus());
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

    public String handleMandatePgCallback(PgPaymentCallbackDTO request) {
        log.info("Received mandate callback request for order ID {} : {}", request.getMandate().getOrderId(), request);
        AutoPayUPI autoPayUPI = autoPayUPIDao.findTop1ByOrderId(request.getMandate().getOrderId());
        try {
            if (autoPayUPI == null) {
                log.error("No order for order id {}", request.getOrderId());
                return "OK";
            }
            if (AutoPayStatusEnum.PENDING == autoPayUPI.getStatus()) {
                log.info("Mandate for merchant id {} and order id {} is already processed", autoPayUPI.getMerchantId(), request.getOrderId());
                return "OK";
            }
            if (request.getPaymentStatus() != null) {
                if ("FAILURE".equalsIgnoreCase(request.getMandate().getStatus())) {
                    autoPayUPI.setStatus(AutoPayStatusEnum.FAILED);
                    if(request.getMandate() != null) {
                        autoPayUPI.setErrorMessage(request.getMandate().getErrorDescription());
                        autoPayUPI.setErrorCode(request.getMandate().getErrorCode());
                    }
                } else {
                    autoPayUPI.setStatus(AutoPayStatusEnum.valueOf(request.getMandate().getStatus()));
                }
                if (request.getMandate().getMandateId() != null) {
                    autoPayUPI.setMandateId(request.getMandate().getMandateId());
                }
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
            if (diffMinutes >= 15L) {
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
                handleMandatePgCallback(response.getData());
            } else if ("FAILURE".equalsIgnoreCase(response.getData().getPaymentStatus()) ||
                    "FAILURE".equalsIgnoreCase(response.getData().getMandate().getStatus())) {
                mandateApplication.setStatus(AutoPayStatusEnum.valueOf(response.getData().getMandate().getStatus()));
                mandateApplication.setErrorMessage(response.getData().getMandate().getErrorDescription() == null ? response.getData().getErrorDescription() : response.getData().getMandate().getErrorDescription() );
                mandateApplication.setErrorCode(response.getData().getMandate().getErrorCode() == null ? response.getData().getErrorCode() : response.getData().getMandate().getErrorCode());
                log.info("Pg txn Status FAILED/CANCELLED for orderId:{}", mandateApplication.getOrderId());
                autoPayUPIDao.save(mandateApplication);
            }

        }

        MandateUPIStatusResponse.Data data = new MandateUPIStatusResponse.Data(mandateApplication.getOrderId()
                , mandateApplication.getApplicationId(), mandateApplication.getStatus());
        return new MandateUPIStatusResponse(data);
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
            statusList.add(AutoPayStatusEnum.SUCCESS.name());
            AutoPayUPI autoPayUPIExistingEntity = autoPayUPIDao.findTop1ByApplicationIdAndStatusOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender(), statusList);
            if (autoPayUPIExistingEntity != null) {
                log.info("For this application Id, mandate is already in progress {} ", lendingApplication.getId());
                throw new InvalidRequestException(String.format(" For this application Id, mandate is already in progress: %s", lendingApplication.getId()));
            }

            if (merchantBasicDetails.getId().equals(lendingApplication.getMerchantId())) {
                AutoPayUPI autoPayUPI = new AutoPayUPI();
                autoPayUPI.setAmount(1D);
                autoPayUPI.setMerchantId(merchantBasicDetails.getId());
                autoPayUPI.setLender(lendingApplication.getLender());
                autoPayUPI.setStatus(AutoPayStatusEnum.INIT);
                autoPayUPI.setApplicationId(lendingApplication.getId());
                autoPayUPI.setGateway(DR_CASHFREE.name().equalsIgnoreCase(checkoutType) ? DR_CASHFREE.name() : "CASHFREE");
                autoPayUPI.setFrequency(DEFAULT_FREQUENCY_FOR_NEW_APPLICATIONS);
                autoPayUPI.setIsAutoPayUpiDeduction(DeductionStatusEnum.AUTO_PAY_UPI.name());
                autoPayUPI = autoPayUPIDao.save(autoPayUPI);
                autoPayUPI.setOrderId("Auto-UPI" + autoPayUPI.getId());

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
                registerPgRequest.setMandateEndDate(epochMandateStartDate + 157680000000L);
                registerPgRequest.setRedirectURIDeeplink(redirectionDeeplinkAutopayUpi + "&wroute=key-factor-statement&openfrom=pg&orderId=" + autoPayUPI.getOrderId() + "&applicationId=" + lendingApplication.getId());
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
        statusList.add(AutoPayStatusEnum.SUCCESS.name());
        AutoPayUPI autoPayUPIExistingEntity = autoPayUPIDao.findTop1ByApplicationIdAndStatusOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender(), statusList);
        if (autoPayUPIExistingEntity != null) {
            log.info("For this application Id, mandate is already in progress {} ", lendingApplication.getId());
            throw new InvalidRequestException(String.format(" For this application Id, mandate is already in progress: %s", lendingApplication.getId()));
        }

        if (merchantBasicDetails.getId().equals(lendingApplication.getMerchantId())) {
            AutoPayUPI autoPayUPI = new AutoPayUPI();
            autoPayUPI.setAmount(1D);
            autoPayUPI.setMerchantId(merchantBasicDetails.getId());
            autoPayUPI.setLender(lendingApplication.getLender());
            autoPayUPI.setStatus(AutoPayStatusEnum.INIT);
            autoPayUPI.setApplicationId(lendingApplication.getId());
            autoPayUPI.setFrequency(DEFAULT_FREQUENCY_FOR_NEW_APPLICATIONS);
            autoPayUPI.setGateway("UNITY");
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
        if (autoPayUPIMandatePluginEnabled && requestDto.getMeta() != null && "android".equalsIgnoreCase(requestDto.getMeta().getClient()) &&
                getAllowedLenderForUPIAutoPay(autoPayUPIMandatePluginLenders).contains(requestDto.getPayload().getLender()) &&
                isVersionGreaterOrEqual(requestDto.getMeta().getDeviceInfo().getAppVersion(), androidVersionMerchantPlugin)) {
            return UNITY.name();
        }

        if (autoPayUPIMandateDirectCashfreeEnabled && requestDto.getMeta() != null && "android".equalsIgnoreCase(requestDto.getMeta().getClient()) &&
                getAllowedLenderForUPIAutoPay(allowedLendersForDirectCashfree).contains(requestDto.getPayload().getLender()) &&
                isVersionGreaterOrEqual(requestDto.getMeta().getDeviceInfo().getAppVersion(), androidVersionDirectCashfree) && easyLoanUtil.percentScaleUp(merchantId, autoPayUPIMandateDirectCashfreePercentRollout)) {
            return DR_CASHFREE.name();
        }

        return JS_CASHFREE.name();
    }
}
