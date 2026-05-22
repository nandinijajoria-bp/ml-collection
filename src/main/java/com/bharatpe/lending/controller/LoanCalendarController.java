package com.bharatpe.lending.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dto.CalendarViewResponseDTO;
import com.bharatpe.lending.dto.FailedTransactionResponseDTO;
import com.bharatpe.lending.dto.LoanTransactionHistoryResponseDTO;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.service.LoanCalendarService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Date;

@Slf4j
@RestController
@RequestMapping("/lending/calendar")
public class LoanCalendarController {

    @Autowired
    private LoanCalendarService loanCalendarService;

    @GetMapping(value = "/instalments", produces = "application/json")
    public ResponseEntity<ApiResponse<CalendarViewResponseDTO>> getCalendarView(
            @RequestAttribute(required = false) BasicDetailsDto merchant,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        if (merchant == null || merchant.getId() == null) {
            log.info("Instalment calendar: merchant missing from token");
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "merchant not found"));
        }
        Long merchantId = merchant.getId();

        log.info("Received instalment history request for merchantId: {}, month: {}, year: {}", merchantId, month, year);

        try {
            CalendarViewResponseDTO response = loanCalendarService.getCalendarViewData(merchantId, month, year);
            return ResponseEntity.ok(new ApiResponse<>(response));
        } catch (RuntimeException e) {
            log.error("Error fetching instalment history for merchantId: {} ,msg={}, stackTrace={}", merchantId, e.getMessage(), Arrays.asList(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(false, e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching instalment history for merchantId: {} , msg={}, stackTrcae={}", merchantId, e.getMessage(), Arrays.asList(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(false, "Error fetching instalment history: " + e.getMessage()));
        }
    }


    @GetMapping(value = "/transactions", produces = "application/json")
    public ResponseEntity<ApiResponse<LoanTransactionHistoryResponseDTO>> getTransactionHistoryByDate(
            @RequestAttribute(required = false) BasicDetailsDto merchant,
            @RequestParam String date) {
        if (merchant == null || merchant.getId() == null) {
            log.info("Transaction history: merchant missing from token");
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "merchant not found"));
        }
        Long merchantId = merchant.getId();

        if (date == null || date.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "date is required"));
        }
        Date parsedDate = DateTimeUtil.parseDate(date.trim(), "yyyy-MM-dd");
        if (parsedDate == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "date must be yyyy-MM-dd"));
        }
        Date calendarDay = DateTimeUtil.getStartTimeFromDateTime(parsedDate);

        log.info("Received transaction history request for merchantId: {}, date: {}", merchantId, date);

        try {
            LoanTransactionHistoryResponseDTO response = loanCalendarService.getTransactionsForDate(merchantId, calendarDay);
            return ResponseEntity.ok(new ApiResponse<>(response));
        } catch (RuntimeException e) {
            log.error("Error fetching transaction history for merchantId: {}", merchantId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(false, e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching transaction history for merchantId: {}", merchantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error fetching transaction history: " + e.getMessage()));
        }
    }

    @GetMapping(value = "/transaction/failure-details", produces = "application/json")
    public ResponseEntity<ApiResponse<FailedTransactionResponseDTO>> getTransactionFailureDetails(
            @RequestAttribute(required = false) BasicDetailsDto merchant,
            @RequestParam String date) {

        // 1. Token Validation
        if (merchant == null || merchant.getId() == null) {
            log.info("Failure details: merchant missing from token");
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "merchant not found"));
        }
        Long merchantId = merchant.getId();

        // 2. Date Validation
        if (date == null || date.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "date is required"));
        }

        // 3. Date Parsing (consistent with your other endpoints)
        Date parsedDate = DateTimeUtil.parseDate(date.trim(), "yyyy-MM-dd");
        if (parsedDate == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "date must be yyyy-MM-dd"));
        }

        log.info("Fetching failure details for merchantId: {}, date: {}", merchantId, date);

        try {
            FailedTransactionResponseDTO response = loanCalendarService.getFailureDetails(merchantId, parsedDate);
            return ResponseEntity.ok(new ApiResponse<>(response));
        } catch (Exception e) {
            log.error("Error fetching failure details for merchantId: {}", merchantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error: " + e.getMessage()));
        }
    }
}