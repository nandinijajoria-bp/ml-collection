package com.bharatpe.lending.loanV3.services.associationsV2.creditsaison.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.PenaltyFeeLedgerDao;
import com.bharatpe.lending.common.entity.PenaltyFeeLedger;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcResponseDto;
import com.bharatpe.lending.loanV3.dto.request.creditsasion.CreditSasionCreateChargeRequestDTO;
import com.bharatpe.lending.loanV3.services.gateway.NbfcLenderGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class CreditSaisonPostChargeService {

    private static String NACH_BOUNCE_CHARGE_CODE = "CQBNCCHR";
    private static String PENAL_CHARGE_CODE = "LPP";
    private String NBFC_POST_CHARGE_URI = "api/v3/lender/post-charges";
    @Autowired
    private PenaltyFeeLedgerDao penaltyFeeLedgerDao;

    @Autowired
    private LendingApplicationDao lendingApplicationDao;

    @Autowired
    private NbfcLenderGateway nbfcLenderGateway;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${nbfc.collection.service.base.url:https://api-nbfc.bharatpemoney.com/}")
    private String nbfcCollectionServiceBaseUrl;

    public void postPendingChargesToLender(LendingPaymentSchedule activeLoan, Map<PenaltyFeeLedger, Double> paidNachBounceMap,
                                           Map<PenaltyFeeLedger, Double> paidPenalChargeMap) {
        log.info("In postPendingChargesToLender with loanId: {}, paidNachBounceMap: {}, paidPenalChargeMap: {}",
                activeLoan.getId(), paidNachBounceMap, paidPenalChargeMap);
        List<CreditSasionCreateChargeRequestDTO.PartnerCharges> partnerChargesList = new ArrayList<>();

        addToPartnerChargesList(NACH_BOUNCE_CHARGE_CODE, paidNachBounceMap, partnerChargesList);
        addToPartnerChargesList(PENAL_CHARGE_CODE, paidPenalChargeMap, partnerChargesList);

        if (CollectionUtils.isEmpty(partnerChargesList)) {
            log.error("In postPendingChargesToLender: Charges list sent was empty!!");
            return;
        }
        boolean isSuccess = postToLender(activeLoan, partnerChargesList);

        List<PenaltyFeeLedger> penaltyFeeLedgerList = new ArrayList<>();
        updatePostingStatus(paidNachBounceMap, penaltyFeeLedgerList, isSuccess);
        updatePostingStatus(paidPenalChargeMap, penaltyFeeLedgerList, isSuccess);

        if (CollectionUtils.isEmpty(penaltyFeeLedgerList)) {
            log.error("Some Error occurred while updated the posting status for charges.");
        }
        penaltyFeeLedgerDao.saveAll(penaltyFeeLedgerList);
        log.info("Charges posting block completed!!");
    }

    private void addToPartnerChargesList(String chargeType,Map<PenaltyFeeLedger, Double> paidChargeMap,
                                         List<CreditSasionCreateChargeRequestDTO.PartnerCharges> partnerChargesList) {

        for (Map.Entry<PenaltyFeeLedger, Double> entry : paidChargeMap.entrySet()) {
            partnerChargesList.add(CreditSasionCreateChargeRequestDTO.PartnerCharges.builder()
                    .component(chargeType)
                    .amt(Math.round(entry.getValue() * 100.0) / 100.0) // Rounding off to 2 decimal places
                    .build());
        }
    }

    private boolean postToLender(LendingPaymentSchedule activeLoan, List<CreditSasionCreateChargeRequestDTO.PartnerCharges> partnerChargesList) {
        log.info("In postToLender with loanId: {}, partnerChargesList: {}", activeLoan.getId(), partnerChargesList);
        int retryCount = 0;
        int maxRetryAttempts = 1;
        try{
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(activeLoan.getApplicationId(), activeLoan.getMerchantId());

            NBFCRequestDTO nbfcRequestDTO = NBFCRequestDTO.builder()
                    .applicationId(activeLoan.getApplicationId())
                    .productName("LENDING")
                    .lender(Lender.CREDITSAISON.name())
                    .payload(CreditSasionCreateChargeRequestDTO.builder()
                            .partnerLoanId(lendingApplication.getExternalLoanId())
                            .chargeDate(getChargeDateTime())
                            .partnerCharges(partnerChargesList)
                            .build())
                    .build();

            log.info("CreditSaison: posting penalty charges to lender for loanID: {}, creditSaisonChargeRequestDTO: {}",
                    activeLoan.getId(), nbfcRequestDTO);

            while (retryCount < maxRetryAttempts) {
                retryCount++;
                String request = objectMapper.writeValueAsString(nbfcRequestDTO);

                NbfcResponseDto nbfcResponseDto = nbfcLenderGateway.invoke(request, NbfcResponseDto.class, nbfcCollectionServiceBaseUrl + NBFC_POST_CHARGE_URI);
                log.info("CreditSaison: response penalty charges posting request :{} and response : {}", request, nbfcResponseDto);

                if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                    log.info("CreditSaison: penalty charges posted to lender: {}", nbfcResponseDto);
                    return true;
                }
            }
        }
        catch (Exception e){
            log.error("Error in Posting Penalty Charge to Lender for loan: {} and error: {}: {}", activeLoan.getId(),
                    e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private String getChargeDateTime() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        return now.format(formatter);
    }

    private void updatePostingStatus(Map<PenaltyFeeLedger, Double> paidChargeMap, List<PenaltyFeeLedger> penaltyFeeLedgerList, boolean isSuccess) {

        for (Map.Entry<PenaltyFeeLedger, Double> entry : paidChargeMap.entrySet()) {
            PenaltyFeeLedger penaltyFeeLedger = entry.getKey();
            double dueAmount = Math.abs(penaltyFeeLedger.getAmount());
            double paidAmtTillDate = Objects.isNull(penaltyFeeLedger.getPaidAmount()) ? 0 : penaltyFeeLedger.getPaidAmount();

            log.info("IN updatePostingStatus Credit Saision: dueAmount : {} paidAmtTillDate : {}", dueAmount, paidAmtTillDate);
            if (dueAmount == paidAmtTillDate) {
                penaltyFeeLedger.setIsPosted(isSuccess);
            }
            if (isSuccess) {
                double postedAmtTillDate = Objects.isNull(penaltyFeeLedger.getPostedAmount()) ? 0 : penaltyFeeLedger.getPostedAmount();
                penaltyFeeLedger.setPostedAmount(postedAmtTillDate + entry.getValue());
            }

            penaltyFeeLedger.setPostingStatus(isSuccess ? "SUCCESS" : "FAILED");
            penaltyFeeLedgerList.add(penaltyFeeLedger);
        }
    }
}
