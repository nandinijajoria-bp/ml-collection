package com.bharatpe.lending.controller;

import com.bharatpe.lending.constant.DataApiFlowConstants;
import com.bharatpe.lending.dto.underwriting.ApiResponse;
import com.bharatpe.lending.dto.underwriting.ExperianRequestWrapper;
import com.bharatpe.lending.dto.underwriting.LendingRiskVariablesSnapshotRequestWrapper;
import com.bharatpe.lending.dto.underwriting.PancardRequestWrapper;
import com.bharatpe.lending.dto.underwriting.SearchRequestDTO;
import com.bharatpe.lending.dto.underwriting.read.*;
import com.bharatpe.lending.dto.underwriting.write.ExperianWriteDto;
import com.bharatpe.lending.dto.underwriting.write.LendingPancardWriteDto;
import com.bharatpe.lending.lendingplatform.nbfc.service.database.LendingApplicationDetailsService;
import com.bharatpe.lending.service.IExperianService;
import com.bharatpe.lending.service.ILendingPancardService;
import com.bharatpe.lending.service.ILendingRiskVariablesSnapshotService;
import com.bharatpe.lending.service.LendingApplicationService;
import com.bharatpe.lending.service.LendingPaymentScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/data")
@RequiredArgsConstructor
public class LendingDataController {

    private final IExperianService experianService;
    private final ILendingPancardService lendingPancardService;
    private final ILendingRiskVariablesSnapshotService lendingRiskVariablesSnapshotService;
    private final LendingApplicationService lendingApplicationService;
    private final LendingPaymentScheduleService lendingPaymentScheduleService;
    private final LendingApplicationDetailsService lendingApplicationDetailsService;

    @PostMapping(value = "/experian", consumes = "application/json", produces = "application/json;charset=UTF-8")
    public ResponseEntity<ApiResponse<?>> handleExperian(
            @RequestBody ExperianRequestWrapper wrapper,
            @RequestParam String httpMethod,
            @RequestHeader(name = "consistency_level", required = false, defaultValue = "EVENTUAL") ConsistencyLevel consistencyLevel) {

        boolean useStrongConsistency = consistencyLevel == ConsistencyLevel.STRONG;

        if ("POST".equalsIgnoreCase(httpMethod)) {
            return ResponseEntity.ok(experianService.saveExperian(wrapper.getExperianWriteDto()));
        }

        if ("GET".equalsIgnoreCase(httpMethod)) {
            return ResponseEntity.ok(experianService.searchDynamic(wrapper.getSearchRequestDTO(), useStrongConsistency));
        }

        return ResponseEntity.badRequest().body(
                ApiResponse.builder()
                        .success(false)
                        .message("Invalid HTTP Method")
                        .errorCode(DataApiFlowConstants.INVALID_HTTP_METHOD)
                        .build()
        );
    }

    @PostMapping(value = "/lending-pancard", consumes = "application/json", produces = "application/json;charset=UTF-8")
    public ResponseEntity<ApiResponse<?>> handlePancard(
            @RequestBody PancardRequestWrapper wrapper,
            @RequestParam String httpMethod,
            @RequestHeader(name = "consistency_level", required = false, defaultValue = "EVENTUAL") ConsistencyLevel consistencyLevel) {

        boolean useStrongConsistency = consistencyLevel == ConsistencyLevel.STRONG;

        if ("POST".equalsIgnoreCase(httpMethod)) {
            return ResponseEntity.ok(
                    lendingPancardService.savePancardDetails(wrapper.getLendingPancardWriteDto())
            );
        }

        if ("GET".equalsIgnoreCase(httpMethod)) {
            return ResponseEntity.ok(
                    lendingPancardService.searchDynamic(wrapper.getSearchRequestDTO(), useStrongConsistency)
            );
        }

        return ResponseEntity.badRequest().body(
                ApiResponse.builder()
                        .success(false)
                        .message("Invalid HTTP Method")
                        .errorCode(DataApiFlowConstants.INVALID_HTTP_METHOD)
                        .build()
        );
    }

    @PostMapping(value = "/lending-risk-variables-snapshot", consumes = "application/json", produces = "application/json;charset=UTF-8")
    public ResponseEntity<ApiResponse<?>> handleLendingRiskVariablesSnapshot(
            @RequestBody LendingRiskVariablesSnapshotRequestWrapper wrapper,
            @RequestParam String httpMethod,
            @RequestHeader(name = "consistency_level", required = false, defaultValue = "EVENTUAL") ConsistencyLevel consistencyLevel) {

        boolean useStrongConsistency = consistencyLevel == ConsistencyLevel.STRONG;

        if ("POST".equalsIgnoreCase(httpMethod)) {
            return ResponseEntity.ok(
                    lendingRiskVariablesSnapshotService.saveRiskVariablesSnapshot(
                            wrapper.getLendingRiskVariablesSnapshotWriteDto()
                    )
            );
        }

        if ("GET".equalsIgnoreCase(httpMethod)) {
            return ResponseEntity.ok(
                    lendingRiskVariablesSnapshotService.searchDynamic(
                            wrapper.getSearchRequestDTO(),
                            useStrongConsistency
                    )
            );
        }

        return ResponseEntity.badRequest().body(
                ApiResponse.builder()
                        .success(false)
                        .message("Invalid HTTP Method")
                        .errorCode(DataApiFlowConstants.INVALID_HTTP_METHOD)
                        .build()
        );
    }


    @PostMapping(value = "/lending-application", consumes = "application/json", produces = "application/json;charset=UTF-8")
    public ResponseEntity<ApiResponse<?>> searchLendingApplications(
            @RequestBody SearchRequestDTO request,
            @RequestHeader(name = "consistency_level", required = false, defaultValue = "EVENTUAL") ConsistencyLevel consistencyLevel) {
        boolean useStrongConsistency = consistencyLevel == ConsistencyLevel.STRONG;
        return ResponseEntity.ok(lendingApplicationService.searchDynamic(request, useStrongConsistency));
    }

    @PostMapping(value = "/lending-payment-schedule", consumes = "application/json", produces = "application/json;charset=UTF-8")
    public ResponseEntity<ApiResponse<?>> searchLendingPaymentSchedule(
            @RequestBody SearchRequestDTO request,
            @RequestHeader(name = "consistency_level", required = false, defaultValue = "EVENTUAL") ConsistencyLevel consistencyLevel) {
        boolean useStrongConsistency = consistencyLevel == ConsistencyLevel.STRONG;
        return ResponseEntity.ok(lendingPaymentScheduleService.searchDynamic(request, useStrongConsistency));
    }

    @PostMapping(value = "/lending-application-details", consumes = "application/json", produces = "application/json;charset=UTF-8")
    public ResponseEntity<ApiResponse<?>> searchLendingApplicationDetails(
            @RequestBody SearchRequestDTO request,
            @RequestHeader(name = "consistency_level", required = false, defaultValue = "EVENTUAL") ConsistencyLevel consistencyLevel) {
        boolean useStrongConsistency = consistencyLevel == ConsistencyLevel.STRONG;
        return ResponseEntity.ok(lendingApplicationDetailsService.searchDynamic(request, useStrongConsistency));
    }

    public enum ConsistencyLevel {
        STRONG, EVENTUAL
    }
}
