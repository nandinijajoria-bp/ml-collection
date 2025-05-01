package com.bharatpe.lending.lendingplatform.lms.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LmsPaymentDetails;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.PaymentStatusV3ResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

import static com.bharatpe.lending.common.util.HmacCalculatorUtil.logger;

@RequiredArgsConstructor
@Service
public class PaymentStatusService {
    private final LendingApplicationDao lendingApplicationDao;

    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public PaymentStatusV3ResponseDTO handleOneLmsSource(LmsPaymentDetails lmsPaymentDetails, String orderId, BasicDetailsDto merchant) {
        LendingApplication lendingApplication = lendingApplicationDao.findByExternalLoanId(lmsPaymentDetails.getBpLoanId());

        if (ObjectUtils.isEmpty(lendingApplication)) {
            logger.error("No application found for externalLoanId: {}", lmsPaymentDetails.getBpLoanId());
            return new PaymentStatusV3ResponseDTO(false, "Application not found");
        }

        // Validate merchant ID
        if (!lendingApplication.getMerchantId().equals(merchant.getId())) {
            logger.error("Merchant ID mismatch for orderId: {}. Expected: {}, Found: {}", orderId, merchant.getId(), lendingApplication.getMerchantId());
            return new PaymentStatusV3ResponseDTO(false, "Merchant ID mismatch");
        }

        // No need to call the PG Service, as the lms_payment table is updated when the LoanPaymentDetail status is successful.
        // This table's status is set to success when we receive the callback from the new 1LMS flow, as the 1LMS is our source of truth.

        // Note: The Bank Reference Number is currently set to the TerminalOrderID from 1LMS
        return createPaymentStatusResponse(lmsPaymentDetails.getAdjustmentMode(), lmsPaymentDetails.getSentToLms().name(),
                lmsPaymentDetails.getTerminalOrderId(), lmsPaymentDetails.getUpdatedAt(), lmsPaymentDetails.getAmount().doubleValue(), orderId);
    }

    private PaymentStatusV3ResponseDTO createPaymentStatusResponse(String source, String status, String bankRefNo, Date updatedAt, Double amount, String orderId) {
        PaymentStatusV3ResponseDTO.Data data = new PaymentStatusV3ResponseDTO.Data();
        data.setPaymentMode(source);
        data.setPaymentStatus(status);
        data.setReferenceNumber(bankRefNo);
        data.setTransferTime(dateFormat.format(updatedAt));
        data.setAmount(amount);
        data.setOrderId(orderId);
        return new PaymentStatusV3ResponseDTO(true, null, data);
    }
}
