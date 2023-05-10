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
import com.bharatpe.lending.entity.AutoPayUPIEntity;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.exceptions.InvalidRequestException;
import com.bharatpe.lending.util.LoanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
public class AutoPayUPIService {
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


    public FetchTxnResponseDto fetchTransaction(BasicDetailsDto merchant, Long loanId) {

        FetchTxnResponseDto responseDto = new FetchTxnResponseDto();
        List<LendingPullPayment> fetchTxn = lendingPullPaymentDao.findByLoanId(loanId);
        List<FetchTxnResponseDto.Presentment> presentments = new ArrayList<>();

        for (int i = 0; i < fetchTxn.size(); i++) {
            if (fetchTxn.get(i).getMerchantId() == merchant.getId()) {
                FetchTxnResponseDto.Presentment presentmentData = new FetchTxnResponseDto.Presentment();
                presentmentData.setDate(fetchTxn.get(i).getTxnDate());
                presentmentData.setPresentmentAmt(fetchTxn.get(i).getDeductedAmount());
                presentmentData.setStatus(fetchTxn.get(i).getStatus());
                presentments.add(presentmentData);
            }
            responseDto.setData(presentments);
        }
        return responseDto;
    }

    public Boolean updateFrequencyForMandate(BasicDetailsDto merchant, UpdateFrequencyRequestDto dto) {
        boolean flag = false;
        Long loanId = dto.getLoanId();
        AutoPayUPIEntity entity = autoPayUPIDao.findByApplicationId(loanId);
        log.info("entity application Id is {} ", entity.getApplicationId());
        if (entity.getMerchantId() == merchant.getId()) {
            entity.setFrequency(dto.getFrequency());
            autoPayUPIDao.save(entity);
            flag = true;
        }
        return flag;
    }

    public String handleMandatePgCallback(PgPaymentCallbackDTO request) {
        log.info("Received payment callback request for order ID {} : {}", request.getOrderId(), request);
        if (Objects.nonNull(request) && Objects.isNull(request.getPayments())) {
            log.info("null payments object in pg callback for request: {}", request);
            return "OK";
        }
        AutoPayUPIEntity autoPayUPI = autoPayUPIDao.findByOrderId(request.getOrderId());
        try {
            if (autoPayUPI == null) {
                log.error("No order for order id {}", request.getOrderId());
                return "OK";
            }
            if (!"PENDING".equalsIgnoreCase(String.valueOf(autoPayUPI.getStatus()))) {
                log.info("Payment for merchant id {} and order id {} is already processed", autoPayUPI.getMerchantId(), request.getOrderId());
                return "OK";
            }
            if (request.getPaymentStatus() != null) {
                if ("FAILURE".equalsIgnoreCase(request.getPaymentStatus())) {
                    autoPayUPI.setStatus(AutoPayStatusEnum.FAILED);
                } else {
                    autoPayUPI.setStatus(AutoPayStatusEnum.valueOf(request.getPaymentStatus()));
                }
                autoPayUPIDao.save(autoPayUPI);
            }
        } catch (Exception ex) {
            if (autoPayUPI != null) {
                autoPayUPI.setStatus(AutoPayStatusEnum.PENDING);
                autoPayUPIDao.save(autoPayUPI);
            }
            log.error("Exception in register callback for order id {}", request.getOrderId(), ex);
        }
        return "OK";
    }

    public MandateUPIStatusResponse checkStatus(BasicDetailsDto merchant, String orderId) {
        log.info("Status check request for mandate register");
        AutoPayUPIEntity mandateApplication =
                autoPayUPIDao.findByMerchantIdAndOrderId(merchant.getId(), orderId);

        if (mandateApplication == null)
            throw new
                    InvalidRequestException
                    (String.format("Invalid application : %s", orderId));
        if ("PENDING".equalsIgnoreCase(String.valueOf(mandateApplication.getStatus()))) {
            log.info("pg status check for mandate register for merchant id {} application id {}",
                    mandateApplication.getMerchantId(), mandateApplication.getApplicationId());

            PgStatusResponse response =
                    apiGatewayService.checkPgStatusForMandate
                            (mandateApplication.getOrderId(),
                                    Lender.valueOf(mandateApplication.getLender()), mandateApplication.getMerchantId());

            if (response != null && response.getStatusCode() != null
                    && "200".equalsIgnoreCase(response.getStatusCode()) && Objects.nonNull(response.getData())
                    && "SUCCESS".equalsIgnoreCase(response.getData().getPaymentStatus())) {
                log.info("Pg txn Status Check for mandateId:{}", mandateApplication.getOrderId());
                handleMandatePgCallback(response.getData());
            } else if (response != null && response.getStatusCode() != null
                    && "200".equalsIgnoreCase(response.getStatusCode()) &&
                    Objects.nonNull(response.getData()) && (Status.TransactionStatus.FAILED.name().equalsIgnoreCase(response.getData().getPaymentStatus())
                    || Status.TransactionStatus.CANCELLED.name().equalsIgnoreCase(response.getData().getPaymentStatus()))) {
                mandateApplication.setStatus(AutoPayStatusEnum.valueOf(response.getData().getPaymentStatus()));
                autoPayUPIDao.save(mandateApplication);
                log.info("Pg txn Status FAILED/CANCELLED for orderId:{}", mandateApplication.getOrderId());
            }
        }
        return new MandateUPIStatusResponse(mandateApplication.getOrderId(), mandateApplication.getApplicationId(),
                mandateApplication.getStatus());
    }

    public UPIRegisterResponseDto registerUPI(BasicDetailsDto merchantBasicDetails, Long loanId, RequestDTO<UPIRegisterRequestDto> requestDto) {

        log.info("Received initiate UPI Register request  for merchant {} : {}", merchantBasicDetails.getId(), requestDto);
        Optional<LendingPaymentSchedule> activeLoan = lendingPaymentScheduleDao.findById(loanId);

        if (!activeLoan.isPresent()) {
            log.info("No active loan found for merchant id {}", merchantBasicDetails.getId());
            throw new InvalidRequestException(String.format("Invalid loan Id : %s", loanId));
        }

        AutoPayUPIEntity entity = autoPayUPIDao.findTop1ByApplicationIdAndStatusOrderByIdDesc(activeLoan.get().getId());
        if (entity != null) {
            log.info("For this application Id, mandate is already in progress {} ", activeLoan.get().getApplicationId());
            throw new InvalidRequestException(String.format(" For this application Id, mandate is already in progress: %s", activeLoan.get().getApplicationId()));
        }

        UPIRegisterResponseDto.Data data = null;
        log.info("merchant basic details merchantBasicDetails.getId(){}", merchantBasicDetails.getId());
        log.info("active loan merchantId {}", activeLoan.get().getMerchantId());

        if (merchantBasicDetails.getId().equals(activeLoan.get().getMerchantId())) {
            AutoPayUPIEntity autoPayUPI = new AutoPayUPIEntity();
            autoPayUPI.setAmount(1D);
            autoPayUPI.setMerchantId(merchantBasicDetails.getId());
            autoPayUPI.setLender(activeLoan.get().getLoanApplication().getLender());
//                autoPayUPI.setLender("LDC");
            autoPayUPI.setStatus(AutoPayStatusEnum.INIT);
            autoPayUPI.setApplicationId(activeLoan.get().getApplicationId());
            autoPayUPI = autoPayUPIDao.save(autoPayUPI);
            autoPayUPI.setOrderId("Auto-UPI" + autoPayUPI.getId());

            AutoPayUPIRegisterPgRequestDto registerPgRequest = new AutoPayUPIRegisterPgRequestDto();
            registerPgRequest.setLender(Lender.valueOf("LDC"));
//                registerPgRequest.setLender(Lender.valueOf(activeLoan.get().getNbfc()));
            registerPgRequest.setPaymentPageHeaderText("Select Payment Mode");
            registerPgRequest.setOrderAmount(1D);
            registerPgRequest.setOrderType("MANDATE");
            registerPgRequest.setCustomerId(merchantBasicDetails.getId());
            registerPgRequest.setCustomerSubId(activeLoan.get().getMerchantStoreId());
            registerPgRequest.setMandateStartDate(LocalDate.now());
//                registerPgRequest.setRedirectURIDeeplink("");
            registerPgRequest.setNarration("Payment for Order No" + autoPayUPI.getOrderId());
            registerPgRequest.setOrderId(autoPayUPI.getOrderId());

            if (loanUtil.isInternalMerchant(merchantBasicDetails.getId()) ||
                    easyLoanUtil.percentScaleUp(merchantBasicDetails.getId(), apiGatewayService.upiPercent)) {
                log.info("pg flow enabling for internal merchants with app version for merchant: {}", merchantBasicDetails.getId());

                Long appVersion = Objects.nonNull(requestDto.getMeta().getDeviceInfo().getAppVersion()) ?
                        Long.parseLong(requestDto.getMeta().getDeviceInfo().getAppVersion()) : 100L;

                if (Objects.equals(requestDto.getMeta().getClient(), "android")) {
                    if (appVersion >= androidVersion) {
                        registerPgRequest.setCheckout("JUSPAY");
                    } else {
                        registerPgRequest.setCheckout("BHARATPE");
                    }
                } else {
                    if (appVersion >= iosVersion) {
                        registerPgRequest.setCheckout("JUSPAY");
                    } else {
                        registerPgRequest.setCheckout("BHARATPE");
                    }
                }
            } else {
                registerPgRequest.setCheckout("BHARATPE");
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
