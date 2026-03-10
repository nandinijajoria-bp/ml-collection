package com.bharatpe.lending.service.impl;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.Constants.LoanRestructureStatus;
import com.bharatpe.lending.common.dao.LoanRestructureDataDao;
import com.bharatpe.lending.common.dto.LoanRestructureDto;
import com.bharatpe.lending.common.dto.LoanRestrutureResponseDto;
import com.bharatpe.lending.common.entity.LoanRestructureData;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;

import static com.bharatpe.lending.common.Constants.LoanRestructureStatus.*;

@Slf4j
@Service
public class CollectionServiceImpl {

    @Autowired
    private LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    private LoanRestructureDataDao loanRestructureDataDao;

    @Autowired
    private LoanRestructureServiceImpl loanRestructureService;

    public LoanRestrutureResponseDto initiateLoanRestructuring(LoanRestructureDto loanRestructureDto) {
        log.info("Initiating loan restructuring process");
        try {
            // Basic validation: check required fields
            String validationError = validateLoanRestructureRequest(loanRestructureDto);
            if (validationError != null) {
                log.error("Validation failed for loan restructuring request: {}", validationError);
                LoanRestrutureResponseDto.Data data = getLoanRestructureResponseData(loanRestructureDto.getMerchantId(), loanRestructureDto.getApplicationId(),
                        loanRestructureDto.getLan(), loanRestructureDto.getRequestId());
                return getLoanRestructureResponse(validationError, FAILED, false, data);
            }

            LoanRestructureData loanRestructureData = loanRestructureDataDao.findByMerchantIdAndLanAndRequestId(
                    loanRestructureDto.getMerchantId(), loanRestructureDto.getLan(), loanRestructureDto.getRequestId());

            if (loanRestructureData != null) {
                log.info("Loan restructuring request already exists for merchantId: {}, lan: {}, requestId: {}. Returning existing response.",
                        loanRestructureDto.getMerchantId(), loanRestructureDto.getLan(), loanRestructureDto.getRequestId());
                String message = "Loan restructuring request already exists for merchantId: " + loanRestructureDto.getMerchantId() +
                        ", lan: " + loanRestructureDto.getLan() + ", requestId: " + loanRestructureDto.getRequestId();
                LoanRestrutureResponseDto.Data data = getLoanRestructureResponseData(loanRestructureData.getMerchantId(), loanRestructureData.getApplicationId(),
                        loanRestructureData.getLan(), loanRestructureData.getRequestId());
                return getLoanRestructureResponse(message, loanRestructureData.getStatus(), true, data);
            }

            loanRestructureData = saveLoanRestructureData(loanRestructureDto, IN_PROGRESS);

            loanRestructureData = loanRestructureService.restructureLoan(loanRestructureData);

            loanRestructureData = loanRestructureDataDao.save(loanRestructureData);

            LoanRestrutureResponseDto.Data data = getLoanRestructureResponseData(loanRestructureData.getMerchantId(), loanRestructureData.getApplicationId(),
                    loanRestructureData.getLan(), loanRestructureData.getRequestId());
            String message = loanRestructureData.getStatus() == SUCCESS ? "Loan restructuring done successfully." :
                    (loanRestructureData.getStatus() == FAILED ? "Loan restructuring failed." : "Loan restructuring request accepted.");

            return getLoanRestructureResponse(message, loanRestructureData.getStatus(), true, data);
        } catch (Exception e) {
            log.error("Exception occurred while initiating loan restructuring message {} {}", e.getMessage(), Arrays.asList(e.getStackTrace()));
            String errorMessage = "An error occurred while initiating loan restructuring: " + e.getMessage();
                LoanRestrutureResponseDto.Data data = getLoanRestructureResponseData(loanRestructureDto.getMerchantId(), loanRestructureDto.getApplicationId(),
                        loanRestructureDto.getLan(), loanRestructureDto.getRequestId());
            return getLoanRestructureResponse(errorMessage, PENDING, true, data);
        }
    }

    public LoanRestrutureResponseDto getStatusForLoanRestructuring(Long applicationId, Long lan, Long requestId) {
        log.info("Fetching status for loan restructuring process");
        // Basic validation: check required fields
        String validationError = validateLoanRestructureStatusRequest(applicationId, lan, requestId);
        if (validationError != null) {
            log.error("Validation failed for loan restructuring request: {}", validationError);
            LoanRestrutureResponseDto.Data data = getLoanRestructureResponseData(null, applicationId, lan, requestId);
            return getLoanRestructureResponse(validationError, FAILED, false, data);
        }

        LoanRestructureData loanRestructureData = loanRestructureDataDao.findByLanAndRequestId(lan, requestId);

        if (loanRestructureData == null) {
            log.error("No loan restructuring data found for lan: {}, requestId: {}", lan, requestId);
            LoanRestrutureResponseDto.Data data = getLoanRestructureResponseData(null, applicationId, lan, requestId);
            return getLoanRestructureResponse("No loan restructuring data found!" + requestId, FAILED, false, data);
        }

        LoanRestrutureResponseDto.Data data = getLoanRestructureResponseData(loanRestructureData.getMerchantId(), loanRestructureData.getApplicationId(),
                loanRestructureData.getLan(), loanRestructureData.getRequestId());
        if (loanRestructureData.getStatus() == SUCCESS) {
            log.info("Loan restructuring process completed successfully for lan: {}, requestId: {}", lan, requestId);
            return getLoanRestructureResponse("Loan restructuring done successfully.", SUCCESS, true, data);
        }

        if (loanRestructureData.getStatus() == FAILED) {
            log.info("Loan restructuring process failed for lan: {}, requestId: {}", lan, requestId);
            return getLoanRestructureResponse("Loan restructuring failed.", FAILED, true, data);
        }

        if (loanRestructureData.getStatus() == PENDING || loanRestructureData.getStatus() == INITIATED) {
            log.info("Loan restructuring process is still in progress for lan: {}, requestId: {}", lan, requestId);
            loanRestructureData.setStatus(IN_PROGRESS);
            loanRestructureDataDao.save(loanRestructureData);
            loanRestructureService.processLoanRestructureAsync(loanRestructureData);
        }

        return getLoanRestructureResponse("Loan restructuring is in progress", IN_PROGRESS, true, data);
    }

    private LoanRestrutureResponseDto.Data getLoanRestructureResponseData(Long merchantId, Long applicationId, Long lan, Long requestId) {
        LoanRestrutureResponseDto.Data data = new LoanRestrutureResponseDto.Data();
        data.setLan(lan);
        data.setMerchantId(merchantId);
        data.setApplicationId(applicationId);
        data.setRequestId(requestId);

        return data;
    }

    private String validateLoanRestructureStatusRequest(Long applicationId, Long lan, Long requestId) {
        log.info("Validating loan restructuring status request for applicationId: {}, lan: {}, requestId: {}", applicationId, lan, requestId);
        if (applicationId == null || lan == null || requestId == null) {
            return "Missing required fields: applicationId, lan, or requestId";
        }

        LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(applicationId);
        if (lendingPaymentSchedule == null) {
            return "No lending payment schedule found for applicationId: " + applicationId;
        }
        if ("1LMS".equals(lendingPaymentSchedule.getLmsSource())) {
            return "1LMS Loan is not eligible for restructuring for applicationId: " + applicationId;
        }
        if (!LoanStatus.ACTIVE.name().equals(lendingPaymentSchedule.getStatus())) {
            return "Loan is not active for applicationId: " + applicationId;
        }

        if (!Lender.TRILLIONLOANS.name().equals(lendingPaymentSchedule.getNbfc())) {
            return "Loan is not from eligible NBFC for applicationId: " + applicationId;
        }
        return null; // Return null if valid
    }

    private String validateLoanRestructureRequest(LoanRestructureDto loanRestructureDto) {
        log.info("Validating loan restructuring request: {}", loanRestructureDto);
        if (loanRestructureDto.getMerchantId() == null || loanRestructureDto.getApplicationId() == null
                || loanRestructureDto.getLan() == null || loanRestructureDto.getRequestId() == null) {
            return "Missing required fields: merchantId, applicationId lan, or requestId";
        }

        LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(loanRestructureDto.getApplicationId());
        if (lendingPaymentSchedule == null) {
            return "No lending payment schedule found for applicationId: " + loanRestructureDto.getApplicationId();
        }
        if ("1LMS".equals(lendingPaymentSchedule.getLmsSource())) {
            return "1LMS Loan is not eligible for restructuring for applicationId: " + loanRestructureDto.getApplicationId();
        }
        if (!LoanStatus.ACTIVE.name().equals(lendingPaymentSchedule.getStatus())) {
            return "Loan is not active for applicationId: " + loanRestructureDto.getApplicationId();
        }

        if (!Lender.TRILLIONLOANS.name().equals(lendingPaymentSchedule.getNbfc())) {
            return "Loan is not from eligible NBFC for applicationId: " + loanRestructureDto.getApplicationId();
        }

        return null; // Return null if valid
    }

    private LoanRestrutureResponseDto getLoanRestructureResponse(String message, LoanRestructureStatus status,
                                                                 boolean isSuccess, LoanRestrutureResponseDto.Data data) {
        return LoanRestrutureResponseDto.builder()
                .success(isSuccess)
                .message(message)
                .status(status.name())
                .data(data)
                .build();
    }

    private LoanRestructureData saveLoanRestructureData(LoanRestructureDto loanRestructureDto, LoanRestructureStatus status) {
        LoanRestructureData loanRestructureData = new LoanRestructureData();
        loanRestructureData.setMerchantId(loanRestructureDto.getMerchantId());
        loanRestructureData.setApplicationId(loanRestructureDto.getApplicationId());
        loanRestructureData.setLan(loanRestructureDto.getLan());
        loanRestructureData.setRequestId(loanRestructureDto.getRequestId());
        loanRestructureData.setStatus(status);

        LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(loanRestructureDto.getApplicationId());
        if (lendingPaymentSchedule != null) {
            loanRestructureData.setLoanId(lendingPaymentSchedule.getId());
        }

        return loanRestructureDataDao.save(loanRestructureData);
    }
}
