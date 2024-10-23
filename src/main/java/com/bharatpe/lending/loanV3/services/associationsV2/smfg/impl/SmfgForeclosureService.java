package com.bharatpe.lending.loanV3.services.associationsV2.smfg.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.config.SmfgConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.smfg.SmfgGetForeclosureDetailsRequest;
import com.bharatpe.lending.loanV3.dto.request.smfg.SmfgPostLoanReceiptRequest;
import com.bharatpe.lending.loanV3.dto.response.smfg.SmfgForeclosureDetailsResponse;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Optional;


@Slf4j
@Service
public class SmfgForeclosureService {

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    SmfgConfig smfgConfig;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    public Double getForeclosureDetails(Long applicationId) {
        LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("lender record not exist of SMFG for {}", applicationId);
            return 0D;
        }
        NBFCRequestDTO nbfcRequestDto = NBFCRequestDTO.builder()
                .productName("LENDING")
                .lender(Lender.SMFG.name())
                .applicationId(applicationId)
                .payload(SmfgGetForeclosureDetailsRequest.builder()
                        .authentication(SmfgGetForeclosureDetailsRequest.Authentication.builder()
                                .appName(smfgConfig.getLmsAppName())
                                .appPass(smfgConfig.getLmsAppPassword())
                                .deviceId(smfgConfig.getLmsGetForeclosureDeviceId())
                                .ipAddress(smfgConfig.getLmsStaticIpAddress())
                                .latitude(smfgConfig.getLmsLatitude())
                                .longitude(smfgConfig.getLmsLongitude()).build())
                        .basicInfo(SmfgGetForeclosureDetailsRequest.BasicInfo.builder()
                                .landId(lendingApplication.getNbfcId()).build())
                        .build()).build();
        NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.FORECLOSURE_FETCH);
        try {
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                SmfgForeclosureDetailsResponse response = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), SmfgForeclosureDetailsResponse.class);
                if (!ObjectUtils.isEmpty(response.getData()) && !ObjectUtils.isEmpty(response.getData().getForeclosureAmt())) {
                    return response.getData().getForeclosureAmt();
                }
            }
        } catch (Exception e) {
            log.info("exception occurred while parsing response data of SMFG foreclosure details for {} {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return 0D;
    }

    public NBFCRequestDTO getForeclosureReceiptRequest(Long applicationId, LendingLedger lendingLedger) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
            String paymentDate = DateTimeUtil.getDateInFormat(lendingLedger.getDate(), "dd-MMM-yyyy").toUpperCase();
            String txnId = Optional.ofNullable(lendingLedger.getTerminalOrderId()).orElse(String.valueOf(lendingLedger.getId()));
            return NBFCRequestDTO.builder()
                    .applicationId(applicationId)
                    .lender(Lender.SMFG.name())
                    .productName("LENDING")
                    .payload(SmfgPostLoanReceiptRequest.builder()
                           .authentication(SmfgPostLoanReceiptRequest.Authentication.builder()
                                    .appName(smfgConfig.getLmsAppName())
                                    .appPass(smfgConfig.getLmsAppPassword())
                                    .ipAddress(smfgConfig.getLmsStaticIpAddress())
                                   .deviceId(smfgConfig.getLmsMakePaymentDeviceId()).build())
                            .basicinfo(SmfgPostLoanReceiptRequest.BasicInfo.builder()
                                    .prospectId(lendingApplication.getNbfcId())
                                    .depositAmt(lendingLedger.getAmount())
                                    .valueDate(paymentDate)
                                    .transactionDate(paymentDate)
                                    .instrNo(txnId)
                                    .newfield1(txnId)
                                    .instrType(smfgConfig.getOnlinePaymentType())
                                    .towards(smfgConfig.getForeclosureTowards()).build())
                            .build())
                    .build();
        } catch (Exception e) {
            log.info("Exception in generating foreclosure receipt payload of SMFG for {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

}