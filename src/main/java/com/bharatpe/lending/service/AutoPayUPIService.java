package com.bharatpe.lending.service;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.enums.Status;
import com.bharatpe.lending.common.dao.LendingPullPaymentDao;
import com.bharatpe.lending.common.dao.LoanDpdDao;
import com.bharatpe.lending.common.entity.LendingPullPayment;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.constant.AutoPayStatusEnum;
import com.bharatpe.lending.dao.AutoPayUPIDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.entity.AutoPayUPI;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.exceptions.InvalidRequestException;
import com.bharatpe.lending.service.validator.AutoPayUPIServiceValidator;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@Slf4j
public class AutoPayUPIService {
    private static final String MANDATE_ORDER_TYPE = "MANDATE";
    private static final Double MANDATE_ORDER_AMOUNT = 1D;
    private static final String PAYMENT_MODE_TEXT = "Select Payment Mode";
    @Autowired
    private LendingPullPaymentDao lendingPullPaymentDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    AutoPayUPIDao autoPayUPIDao;

    @Autowired
    LoanUtil loanUtil;

    @Value(("${pg.android.version:324}"))
    Long androidVersion;

    @Value(("${pg.ios.version:254}"))
    Long iosVersion;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    LoanDpdDao loanDpdDao;

    @Autowired
    APIGatewayService apiGatewayService;


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
        log.info("Received mandate callback request for order ID {} : {}", request.getOrderId(), request);
        AutoPayUPI autoPayUPI = autoPayUPIDao.findByOrderId(request.getOrderId());
        try {
            if (autoPayUPI == null) {
                log.error("No order for order id {}", request.getOrderId());
                return "OK";
            }
            if (!"PENDING".equalsIgnoreCase(String.valueOf(autoPayUPI.getStatus()))) {
                log.info("Mandate for merchant id {} and order id {} is already processed", autoPayUPI.getMerchantId(), request.getOrderId());
                return "OK";
            }
            if (request.getPaymentStatus() != null) {
                if ("FAILURE".equalsIgnoreCase(request.getMandate().getStatus())) {
                    autoPayUPI.setStatus(AutoPayStatusEnum.FAILED);
                } else {
                    autoPayUPI.setStatus(AutoPayStatusEnum.valueOf(request.getPaymentStatus()));
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

        if (AutoPayStatusEnum.PENDING.name().equalsIgnoreCase(String.valueOf(mandateApplication.getStatus()))) {
            log.info("status of application {} ", mandateApplication.getStatus());
            PgStatusResponse response =
                    apiGatewayService.checkPgStatusForMandate
                            (mandateApplication.getOrderId(),
                                    Lender.valueOf(mandateApplication.getLender()), mandateApplication.getMerchantId());

            log.info(" PG response is {}", response.getData());

            if ("ACTIVE".equalsIgnoreCase(response.getData().getMandate().getStatus())) {
                log.info("Pg txn Status Check for mandateId:{}", mandateApplication.getOrderId());
                mandateApplication.setStatus(AutoPayStatusEnum.valueOf(response.getData().getMandate().getStatus()));
                handleMandatePgCallback(response.getData());
            } else if (Objects.nonNull(response.getData()) && (Status.TransactionStatus.FAILED.name().equalsIgnoreCase(response.getData().getPaymentStatus())
                    || Status.TransactionStatus.CANCELLED.name().equalsIgnoreCase(response.getData().getPaymentStatus()))) {
                mandateApplication.setStatus(AutoPayStatusEnum.valueOf(response.getData().getMandate().getStatus()));
                log.info("Pg txn Status FAILED/CANCELLED for orderId:{}", mandateApplication.getOrderId());
            }
            autoPayUPIDao.save(mandateApplication);
        }

        return new MandateUPIStatusResponse(mandateApplication.getOrderId(), mandateApplication.getApplicationId(),
                mandateApplication.getStatus());
    }


    public UPIRegisterResponseDto registerUPI(BasicDetailsDto merchantBasicDetails, RequestDTO<UPIRegisterRequestDto> requestDto) {
        log.info("Received initiate UPI Register request  for merchant {} : {}", merchantBasicDetails.getId(), requestDto);
        Optional<LendingPaymentSchedule> activeLoan = lendingPaymentScheduleDao.findByMerchantIdAndIdAndStatus(merchantBasicDetails.getId(),requestDto.getPayload().getLoanId(),"ACTIVE");

        if (!activeLoan.isPresent()) {
            log.info("No active loan found for merchant id {}", merchantBasicDetails.getId());
            return new UPIRegisterResponseDto();
        }

        List<String> statusList = new ArrayList<>();
        statusList.add(AutoPayStatusEnum.PENDING.name());
        statusList.add(AutoPayStatusEnum.SUCCESS.name());
        AutoPayUPI entity = autoPayUPIDao.findTop1ByApplicationIdAndStatusOrderByIdDesc(activeLoan.get().getApplicationId(),statusList);
        if (entity != null) {
            log.info("For this application Id, mandate is already in progress {} ", activeLoan.get().getApplicationId());
            throw new InvalidRequestException(String.format(" For this application Id, mandate is already in progress: %s", activeLoan.get().getApplicationId()));
        }

        UPIRegisterResponseDto.Data data = null;
        log.info("active loan merchantId {}", activeLoan.get().getMerchantId());

        if (merchantBasicDetails.getId().equals(activeLoan.get().getMerchantId())) {
            AutoPayUPI autoPayUPI = new AutoPayUPI();
            autoPayUPI.setAmount(1D);
            autoPayUPI.setMerchantId(merchantBasicDetails.getId());
            autoPayUPI.setLender(activeLoan.get().getLoanApplication().getLender());
            autoPayUPI.setStatus(AutoPayStatusEnum.INIT);
            autoPayUPI.setApplicationId(activeLoan.get().getApplicationId());
            autoPayUPI = autoPayUPIDao.save(autoPayUPI);
            autoPayUPI.setOrderId("Auto-UPI" + autoPayUPI.getId());

            AutoPayUPIRegisterPgRequestDto registerPgRequest = new AutoPayUPIRegisterPgRequestDto();
            registerPgRequest.setLender(Lender.valueOf(activeLoan.get().getNbfc()));
            registerPgRequest.setPaymentPageHeaderText(PAYMENT_MODE_TEXT);
            registerPgRequest.setOrderAmount(MANDATE_ORDER_AMOUNT);
            registerPgRequest.setOrderType(MANDATE_ORDER_TYPE);
            registerPgRequest.setCustomerId(merchantBasicDetails.getId());
            registerPgRequest.setCustomerSubId(activeLoan.get().getMerchantStoreId());
            registerPgRequest.setMandateStartDate(LocalDate.now());
            registerPgRequest.setNarration("Register mandate with orderId"+autoPayUPI.getOrderId());
            registerPgRequest.setOrderId(autoPayUPI.getOrderId());

            //mandate date is set of 10 years ahead, since till that time loan will be closed.
            String currentDate = String.valueOf(LocalDate.now());
            LocalDate mandateEndDate = LocalDate.parse(currentDate).plusYears(10);
            log.info("mandate end date is {}", mandateEndDate);
            registerPgRequest.setRedirectURIDeeplink("bharatpe://dynamic?key=loan-dashboard-dev&openfrom=pg&orderId=" + autoPayUPI.getOrderId());

            ZoneId zoneId = ZoneId.systemDefault();
            long epoch = mandateEndDate.atStartOfDay(zoneId).toEpochSecond() * 1000L;
            registerPgRequest.setMandateEndDate(epoch);

            if (loanUtil.isInternalMerchant(merchantBasicDetails.getId()) ||
                    easyLoanUtil.percentScaleUp(merchantBasicDetails.getId(), apiGatewayService.upiPercent)) {
                log.info("pg flow enabling for internal merchants with app version for merchant: {}", merchantBasicDetails.getId());
                registerPgRequest.setCheckout("JUSPAY");
            }
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

        return new UPIRegisterResponseDto(data);
    }
}
