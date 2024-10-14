package com.bharatpe.lending.loanV3.services.associationsV2.creditsaison.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.config.CreditSaisonConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;
import com.bharatpe.lending.loanV3.dto.request.creditsasion.CreditSasionCreateChargeRequestDTO;
import com.bharatpe.lending.loanV3.dto.request.creditsasion.CreditSasionRepaymentRequestDTO;
import com.bharatpe.lending.loanV3.services.gateway.NbfcLenderGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;



@Slf4j
@Service
public class CreditSaisonForeclosureService {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Lazy
    @Autowired
    CreditSaisonConfig  csConfig;

    @Autowired
    NbfcLenderGateway nbfcLenderGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${nbfc.baseurl.v3.api:https://api-nbfc-uat.bharatpe.in/}")
    String nbfcBaseUrl;


    @Value("${nbfc.foreclosure.charge:api/v3/lender/post-charges}")
    String nbfcForeClosureChargePosting;



    public NBFCRequestDTO getForeclosureReceiptRequest(Long applicationId, LendingLedger lendingLedger) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findById(applicationId).orElse(null);
            if(ObjectUtils.isEmpty(lendingApplication)) {
                log.info("CS: application not found for given applicationId while foreclosure: {}", applicationId);
                return null;
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(applicationId, Status.ACTIVE.name(),lendingApplication.getLender());
            if(ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
                log.info("CS: lender details of CreditSaison not found for applicationId {}", lendingApplication.getId());
            }

            LinkedHashMap<String, Object> identifier = new LinkedHashMap<>();
            identifier.put("loanId", lendingApplicationLenderDetails.getLan());

            String paymentDate = DateTimeUtil.getDateInFormat(lendingLedger.getDate(), "yyyy-MM-dd'T'HH:mm:ss");
            String chargePaymentDate = DateTimeUtil.getDateInFormat(lendingLedger.getDate(), "dd-MM-yyyy HH:mm:ss");
            Double charges = lendingLedger.getOtherCharges() > 0.0 ? Math.abs(lendingLedger.getOtherCharges())/1.18 : null;

            if(!ObjectUtils.isEmpty(charges)) {
                try {
                    NBFCRequestDTO nbfcRequestDTO = NBFCRequestDTO.builder()
                            .lender(lendingApplicationLenderDetails.getLender())
                            .productName("LENDING")
                            .applicationId(lendingApplication.getId())
                            .payload(CreditSasionCreateChargeRequestDTO.builder()
                                    .partnerLoanId(lendingApplication.getExternalLoanId())
                                    .partnerCharges(Arrays.asList(
                                            CreditSasionCreateChargeRequestDTO.PartnerCharges.builder()
                                                    .amt(charges)
                                                    .component("FCC")
                                                    .build()
                                    ))
                                    .chargeDate(chargePaymentDate)
                                    .build())
                            .identifier(identifier)
                            .build();
                    nbfcLenderGateway.invoke(objectMapper.writeValueAsString(nbfcRequestDTO), NbfcResponseDto.class,nbfcBaseUrl+nbfcForeClosureChargePosting);
                } catch (Exception ex) {
                    log.info("CS: Exception while posting charges for foreclosure applicationId {}", lendingApplication.getId());
                }

            }
            log.info("CS: Calling foreclosure for applicationId {}", lendingApplication.getId());


            CreditSasionRepaymentRequestDTO creditSasionRepaymentRequestDTO =  CreditSasionRepaymentRequestDTO.builder()
                    .partnerLoanId(lendingApplication.getExternalLoanId())
                    .utr(lendingLedger.getTerminalOrderId())
                    .paidAmt(lendingLedger.getAmount())
                    .customerDebitDate(paymentDate)
                    .customerModeOfPay("ONLINE")
                    .tag("FORECLOSURE")
                    .productCode("BPT")
                    .build();

            if(!ObjectUtils.isEmpty(charges)){
                creditSasionRepaymentRequestDTO.setPartnerCharges(
                        Arrays.asList(
                                CreditSasionRepaymentRequestDTO.PartnerCharges.builder()
                                        .amt(charges)
                                        .component("FCC")
                                        .build()
                ));
            }

            NBFCRequestDTO nbfcRequestDTO = NBFCRequestDTO.builder()
                    .lender(lendingApplicationLenderDetails.getLender())
                    .productName(csConfig.getLendingProduct())
                    .applicationId(lendingApplication.getId())
                    .payload(creditSasionRepaymentRequestDTO)
                    .identifier(identifier)
                    .build();


            return nbfcRequestDTO;
        } catch (Exception e) {
            log.info("CS: Exception in generating foreclosure receipt payload of CreditSaison for {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

}
