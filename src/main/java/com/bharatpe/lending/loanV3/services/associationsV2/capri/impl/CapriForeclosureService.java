package com.bharatpe.lending.loanV3.services.associationsV2.capri.impl;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.capri.CapriForeclosureDetailsRequestDTO;
import com.bharatpe.lending.loanV3.dto.request.capri.CapriForeclosureRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.capri.CapriForeclosureDetailsResponseDTO;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Optional;


@Slf4j
@Service
public class CapriForeclosureService {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    public Double getForeclosureDetails(Long applicationId) {
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, Lender.CAPRI.name());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("lender record not exist of CAPRI for {}", applicationId);
            return 0D;
        }
        NBFCRequestDTO nbfcRequestDto = NBFCRequestDTO.builder()
                .productName("LENDING")
                .lender(Lender.CAPRI.name())
                .applicationId(applicationId)
                .payload(CapriForeclosureDetailsRequestDTO.builder()
                        .loanId(lendingApplicationLenderDetails.getLan())
                        .transactionDate(DateTimeUtil.getDateInFormat(new Date(), "yyyy-MM-dd"))
                        .dateFormat("yyyy-MM-dd")
                        .locale("en")
                        .isTotalOutstandingInterest(Boolean.FALSE)
                        .includePreclosureReasoon(Boolean.FALSE)
                        .build())
                .build();
        NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.FORECLOSURE_FETCH);
        try {
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                CapriForeclosureDetailsResponseDTO response = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), CapriForeclosureDetailsResponseDTO.class);
                if(!ObjectUtils.isEmpty(response.getNetForeclosureAmount())) {
                    return response.getNetForeclosureAmount();
                }
            }
        } catch (Exception e) {
            log.info("exception occurred while parsing response data of Capri repayment schedule for {} {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return 0D;
    }

    public NBFCRequestDTO getForeclosureReceiptRequest(Long applicationId, LendingLedger lendingLedger) {
        try {
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, Lender.CAPRI.name());
            String paymentDate = DateTimeUtil.getDateInFormat(lendingLedger.getDate(), "dd-MM-yyyy");
            String txnId = Optional.ofNullable(lendingLedger.getTerminalOrderId()).orElse(String.valueOf(lendingLedger.getId()));
            LinkedHashMap<String, Object> identifier = new LinkedHashMap<>();
            identifier.put("loanId", lendingApplicationLenderDetails.getLan());
            NBFCRequestDTO nbfcRequestDTO = NBFCRequestDTO.builder()
                    .applicationId(applicationId)
                    .lender("CAPRI")
                    .productName("LENDING")
                    .payload(CapriForeclosureRequestDTO.builder()
                            .locale("en")
                            .transactionDate(paymentDate)
                            .dateFormat("dd-MM-yyyy")
                            .note("Foreclosure")
                            .receiptNumber(txnId)
                            .paymentTypeId(getPaymentTypeId(lendingLedger.getAdjustmentMode()))
                            .transactionAmount(lendingLedger.getAmount())
                            .build())
                    .identifier(identifier)
                    .build();
            return nbfcRequestDTO;
        } catch (Exception e) {
            log.info("Exception in generating foreclosure receipt payload of USFB for {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private Long getPaymentTypeId(String adjustmentMode) {
        switch (adjustmentMode) {
            case "UPI":
                return 1016L;
            case "FP":
                return 1018L;
            case "DC":
                return 1017L;
            case "NB":
                return 1003L;
            case "SETTLEMENT":
                return 1020L;
            case "EXCESS_NACH_CREDIT":
                return 1013L;
            case "EXCESS_NACH_ADJUSTED":
            case "NACH":
                return 1019L;
            default:
                return 0L;
        }
    }

}
