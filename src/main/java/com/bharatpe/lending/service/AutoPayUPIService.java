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
import java.util.*;


@Service
@Slf4j
public class AutoPayUPIService {
    private static final String MANDATE_ORDER_TYPE = "MANDATE";
    private static final Double MANDATE_ORDER_AMOUNT = 1D;
    private static final String PAYMENT_MODE_TEXT = "Select Payment Mode";
    private static final int DEFAULT_FREQUENCY = 2;
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

        if (mandateApplication.equals(AutoPayStatusEnum.PENDING) ||
                mandateApplication.getStatus().equals(AutoPayStatusEnum.INIT))
        {
            Date createdMandateDate = mandateApplication.getCreatedAt();
            long diffMinutes = calculateTimeDiff(createdMandateDate);
            log.info("diffMinutes is {}", diffMinutes);
            if (diffMinutes >= 30L) {
                mandateApplication.setStatus(AutoPayStatusEnum.FAILED);
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

            log.info(" PG response is {}", response.getData());

            if ("ACTIVE".equalsIgnoreCase(response.getData().getMandate().getStatus())) {
                log.info("Pg txn Status Check for mandateId:{}", mandateApplication.getOrderId());
                mandateApplication.setStatus(AutoPayStatusEnum.valueOf(response.getData().getMandate().getStatus()));
                handleMandatePgCallback(response.getData());
            } else if ("FAILURE".equalsIgnoreCase(response.getData().getPaymentStatus()) ||
                    "FAILURE".equalsIgnoreCase(response.getData().getMandate().getStatus())) {
                mandateApplication.setStatus(AutoPayStatusEnum.valueOf(response.getData().getMandate().getStatus()));
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
            AutoPayUPI entity = autoPayUPIDao.findTop1ByApplicationIdAndStatusOrderByIdDesc(activeLoan.get().getApplicationId(), statusList);
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
}
