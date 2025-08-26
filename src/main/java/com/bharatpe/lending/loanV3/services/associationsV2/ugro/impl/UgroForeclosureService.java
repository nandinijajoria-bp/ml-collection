package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.ugro.UgroClosureBreakupRequest;
import com.bharatpe.lending.loanV3.dto.request.ugro.UgroRepaymentCallbackRequest;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroClosureBreakupResponse;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;


@Service
@Slf4j
public class UgroForeclosureService {

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UgroConfig ugroConfig;

    public Double getForeclosureDetails(Long applicationId) {
        LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(applicationId, Status.ACTIVE.name(), Lender.UGRO.name());
        if (ObjectUtils.isEmpty(lendingApplication) || ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.error("UGRO: lending application / LALD not found for {}", applicationId);
            return 0D;
        }
        try {
            NBFCRequestDTO<?> nbfcRequestDto = NBFCRequestDTO.builder().productName("LENDING").lender(Lender.UGRO.name()).applicationId(applicationId).payload(UgroClosureBreakupRequest.builder().leadId(lendingApplicationLenderDetails.getLeadId()).loanId(lendingApplication.getNbfcId()).closureDate(String.valueOf(Instant.now().toEpochMilli())).build()).build();
            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.FORECLOSURE_FETCH, ugroConfig.getForeclosureDetailsTimeoutThreshold());
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                UgroClosureBreakupResponse response = objectMapper.convertValue(nbfcResponseDto.getData(), UgroClosureBreakupResponse.class);
                if (!ObjectUtils.isEmpty(response.getBreakup()) && !ObjectUtils.isEmpty(response.getBreakup().getTotal())) {
                    return response.getBreakup().getTotal();
                }
            }
        } catch (Exception e) {
            log.info("UGRO: exception occurred while parsing response data of foreclosure details for {} {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return 0D;
    }

    public NBFCRequestDTO<?> getForeclosureReceiptRequest(Long applicationId, LendingLedger lendingLedger) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(applicationId, Status.ACTIVE.name(), Lender.UGRO.name());
            if (ObjectUtils.isEmpty(lendingApplication) || ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.error("UGRO: lending application / LALD not found for {}", applicationId);
                return null;
            }

            String txnId = Optional.ofNullable(lendingLedger.getTerminalOrderId()).orElse(String.valueOf(lendingLedger.getId()));
            return NBFCRequestDTO.builder()
                    .applicationId(applicationId)
                    .lender(Lender.UGRO.name())
                    .productName("LENDING")
                    .payload(UgroRepaymentCallbackRequest.builder()
                            .loanId(lendingApplication.getNbfcId())
                            .leadId(lendingApplicationLenderDetails.getLeadId())
                            .paidAt(String.valueOf(Instant.now().toEpochMilli()))
                            .amount(lendingLedger.getAmount().toString())
                            .txnId(txnId)
                            .mode(lendingLedger.getAdjustmentMode())
                            .lan(lendingApplication.getNbfcId())
                            .bankRefNo(txnId)
                            .requestId(lendingApplication.getNbfcId() + "_" + txnId)
                            .intent(ugroConfig.getForeclosureIntent())
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("UGRO: Exception in generating foreclosure receipt payload for {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    public Double getForeclosurePrincipalOutstandingDetails(Long applicationId) {
        LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(applicationId, Status.ACTIVE.name(), Lender.UGRO.name());
        if (ObjectUtils.isEmpty(lendingApplication) || ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.error("UGRO: lending application / LALD not found for {}", applicationId);
            return 0D;
        }
        try {
            NBFCRequestDTO<?> nbfcRequestDto = NBFCRequestDTO.builder().productName("LENDING").lender(Lender.UGRO.name()).applicationId(applicationId).payload(UgroClosureBreakupRequest.builder().leadId(lendingApplicationLenderDetails.getLeadId()).loanId(lendingApplication.getNbfcId()).closureDate(String.valueOf(Instant.now().toEpochMilli())).build()).build();
            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.FORECLOSURE_FETCH);
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                UgroClosureBreakupResponse response = objectMapper.convertValue(nbfcResponseDto.getData(), UgroClosureBreakupResponse.class);
                if (!ObjectUtils.isEmpty(response.getBreakup()) && !ObjectUtils.isEmpty(response.getBreakup().getTotal())) {
                    return response.getBreakup().getPo();
                }
            }
        } catch (Exception e) {
            log.info("UGRO: exception occurred while parsing response data of foreclosure details for {} {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return 0D;
    }

}
