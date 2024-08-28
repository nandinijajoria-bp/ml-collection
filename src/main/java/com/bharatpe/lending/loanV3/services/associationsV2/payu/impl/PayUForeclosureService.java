package com.bharatpe.lending.loanV3.services.associationsV2.payu.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.payu.PayUForeclosureDetailsRequestDTO;
import com.bharatpe.lending.loanV3.dto.request.payu.PayUForeclosureRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.payu.PayUCommonResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.payu.PayUForeclosureDetailsResponseDTO;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Optional;

@Slf4j
@Service
public class PayUForeclosureService {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    public Double getForeclosureDetails(Long applicationId) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, Lender.PAYU.name());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("lender record not exist of PAYU for {}", applicationId);
            return null;
        }
        NBFCRequestDTO nbfcRequestDto = NBFCRequestDTO.builder()
                .productName("LENDING")
                .lender(Lender.PAYU.name())
                .applicationId(applicationId)
                .payload(PayUForeclosureDetailsRequestDTO.builder()
                        .applicationId(lendingApplicationLenderDetails.getLeadId())
                        .loanId(Integer.valueOf(lendingApplicationLenderDetails.getLan()))
                        .build())
                .build();
        NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.FORECLOSURE_FETCH);
        try {
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {

                PayUCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDto.getData(), PayUCommonResponseDTO.class);

                PayUForeclosureDetailsResponseDTO foreclosureResponse =  objectMapper.convertValue(commonResponseDTO.getApiResponse(), PayUForeclosureDetailsResponseDTO.class);

                if ("SUCCESS".equalsIgnoreCase(commonResponseDTO.getApiStatus())) {
                    return (double) foreclosureResponse.getOutstandingLoanBalance();
                }

            }
        } catch (Exception e) {
            log.error("exception occurred while fetching foreclosure details of PAYU for {} {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return 0D;
    }

    public NBFCRequestDTO getForeclosureReceiptRequest(Long applicationId, LendingLedger lendingLedger) {
        try {
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, Lender.PAYU.name());
            String paymentDate = DateTimeUtil.getDateInFormat(lendingLedger.getDate(), "yyyy-MM-dd");
            LinkedHashMap<String, Object> identifier = new LinkedHashMap<>();
            identifier.put("loanId", lendingApplicationLenderDetails.getLan());
            return  NBFCRequestDTO.builder()
                    .applicationId(applicationId)
                    .lender(Lender.PAYU.name())
                    .productName("LENDING")
                    .payload(PayUForeclosureRequestDTO.builder()
                            .applicationId(lendingApplicationLenderDetails.getLeadId())
                            .loanId(Integer.valueOf(lendingApplicationLenderDetails.getLan()))
                            .utr(lendingApplicationLenderDetails.getUtrNo())
                            .amount(lendingLedger.getAmount())
                            .paymentMode(payuPaymentModeMapping(lendingLedger.getAdjustmentMode()))
                            .referenceId(lendingLedger.getId().toString())
                            .transactionDate(paymentDate)
                            .note("Foreclosure")
                            .build())
                    .identifier(identifier)
                    .build();
        } catch (Exception e) {
            log.info("Exception in generating foreclosure receipt payload of Payu for {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String payuPaymentModeMapping(String paymentMode) {
        switch (paymentMode) {
            case "UPI":
            case "FP":
            case "NB":
            case "DC":
                return "BP_PG";
            case "BHARATPE_NACH":
            case "UPI_AUTOPAY":
                return "BP_MANDATE";
            case "EXCESS_NACH_ADJUSTED":
                return "BP_EXCESS_CONSUMPTION";
            case "DIRECT_TRANSFER":
            case "EXCEPTION":
            case "BP_BT":
                return "BP_MANUAL_NEFT_IMPS_RTGS";
            case "SETTLEMENT":
                return "BP_MIS";
            case "SCHEME1":
                return "BP_ADJUSTMENT";
            case "TOPUP":
                return "BP_TOPUP_BT";
            default:
                return null;
        }
    }



}
